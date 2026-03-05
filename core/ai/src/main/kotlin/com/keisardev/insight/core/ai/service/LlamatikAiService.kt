package com.keisardev.insight.core.ai.service

import com.keisardev.insight.core.common.di.AppScope
import com.keisardev.insight.core.ai.di.ModelsDir
import com.keisardev.insight.core.data.datastore.UserSettingsRepository
import com.keisardev.insight.core.data.repository.CategoryRepository
import com.keisardev.insight.core.data.repository.ExpenseRepository
import com.keisardev.insight.core.data.repository.IncomeCategoryRepository
import com.keisardev.insight.core.data.repository.IncomeRepository
import com.keisardev.insight.core.model.Category
import com.keisardev.insight.core.model.Expense
import com.keisardev.insight.core.model.Income
import com.keisardev.insight.core.model.IncomeType
import com.llamatik.library.platform.GenStream
import com.llamatik.library.platform.LlamaBridge
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import okio.FileSystem
import okio.Path

/**
 * Implementation of [AiService] using Llamatik for on-device LLM inference.
 *
 * Uses llama.cpp under the hood via Llamatik's [LlamaBridge] to run models
 * locally on the device without requiring network access or API keys.
 *
 * Thread safety is ensured via [Mutex] since LlamaBridge uses native resources
 * that are not thread-safe.
 *
 * Place a GGUF model file at `{filesDir}/models/model.gguf` to enable.
 */
@SingleIn(AppScope::class)
@Inject
class LlamatikAiService(
    @ModelsDir private val modelsDir: Path,
    private val fileSystem: FileSystem,
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val categoryRepository: CategoryRepository,
    private val incomeCategoryRepository: IncomeCategoryRepository,
    private val userSettingsRepository: UserSettingsRepository,
) : AiService {

    @Volatile private var _activeModelFileName: String = ""

    private val modelPath: Path?
        get() {
            if (_activeModelFileName.isNotEmpty()) {
                val activePath = modelsDir / _activeModelFileName
                if (fileSystem.exists(activePath)) return activePath
            }
            return fileSystem.listOrNull(modelsDir)
                ?.firstOrNull { it.name.substringAfterLast('.') == "gguf" }
        }

    private val mutex = Mutex()
    @Volatile private var isModelLoaded = false
    @Volatile private var loadedModelPath: Path? = null

    override val isEnabled: Boolean
        get() = modelPath != null

    private suspend fun syncActiveModel() {
        try {
            val settings = userSettingsRepository.observeSettings().first()
            _activeModelFileName = settings.activeModelFileName
        } catch (_: Exception) { /* keep current */ }
    }

    private suspend fun ensureModelLoaded() {
        syncActiveModel()
        val path = modelPath ?: return
        // Reload if model path changed (e.g. new model downloaded)
        if (isModelLoaded && loadedModelPath != path) {
            LlamaBridge.shutdown()
            isModelLoaded = false
            loadedModelPath = null
        }
        if (isModelLoaded) return
        withContext(Dispatchers.IO) {
            LlamaBridge.initGenerateModel(path.toString())
        }
        isModelLoaded = true
        loadedModelPath = path
    }

    override suspend fun suggestCategory(
        description: String,
        availableCategories: List<Category>,
    ): Category? {
        if (!isEnabled || description.isBlank()) return null

        return mutex.withLock {
            withContext(Dispatchers.IO) {
                suggestCategoryInternal(description, availableCategories)
            }
        }
    }

    /** Inner logic without mutex — safe to call from [chat] which already holds the lock. */
    private suspend fun suggestCategoryInternal(
        description: String,
        availableCategories: List<Category>,
    ): Category? {
        val categoryNames = availableCategories.joinToString(", ") { it.name }
        val prompt = buildString {
            append("Given these expense categories: [$categoryNames]\n")
            append("Which category best fits this expense: \"$description\"?\n")
            append("Respond with ONLY the category name, nothing else.")
        }

        return try {
            ensureModelLoaded()
            val result = LlamaBridge.generate(prompt)
            availableCategories.find { category ->
                category.name.equals(result.trim(), ignoreCase = true)
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun getCurrencySymbol(): String {
        return try {
            val settings = userSettingsRepository.observeSettings().first()
            val code = settings.currencyCode
            if (code == "DEVICE") {
                java.util.Currency.getInstance(java.util.Locale.getDefault()).symbol
            } else {
                java.util.Currency.getInstance(code).symbol
            }
        } catch (_: Exception) {
            "$"
        }
    }

    // --- Phase 4: Question type classification for smart context injection ---

    private enum class QuestionType {
        EXPENSE_QUERY, INCOME_QUERY, CATEGORY_BREAKDOWN, RECENT_ITEMS, COMPARISON, GENERAL
    }

    private fun classifyQuestionType(message: String): QuestionType {
        val lower = message.lowercase()
        return when {
            COMPARISON_KEYWORDS.any { lower.contains(it) } -> QuestionType.COMPARISON
            CATEGORY_KEYWORDS.any { lower.contains(it) } -> QuestionType.CATEGORY_BREAKDOWN
            RECENT_KEYWORDS.any { lower.contains(it) } -> QuestionType.RECENT_ITEMS
            INCOME_QUERY_KEYWORDS.any { lower.contains(it) } -> QuestionType.INCOME_QUERY
            EXPENSE_QUERY_KEYWORDS.any { lower.contains(it) } -> QuestionType.EXPENSE_QUERY
            else -> QuestionType.GENERAL
        }
    }

    private suspend fun buildFinancialSummary(today: LocalDate, currencySymbol: String): String {
        return buildFinancialSummaryForType(today, currencySymbol, QuestionType.GENERAL)
    }

    private suspend fun buildFinancialSummaryForType(
        today: LocalDate,
        currencySymbol: String,
        questionType: QuestionType,
    ): String {
        return try {
            val monthStart = LocalDate(today.year, today.month, 1)
            val monthEnd = today
            val prevMonthEnd = monthStart.minus(DatePeriod(days = 1))
            val prevMonthStart = LocalDate(prevMonthEnd.year, prevMonthEnd.month, 1)

            buildString {
                appendLine("Financial data (${today.month} ${today.year}):")

                when (questionType) {
                    QuestionType.EXPENSE_QUERY -> {
                        val total = expenseRepository.observeMonthlyTotal(monthStart, monthEnd).first()
                        val breakdown = expenseRepository.observeTotalByCategory(monthStart, monthEnd).first()
                        appendLine("Total spent: $currencySymbol${String.format("%.2f", total)}")
                        if (breakdown.isNotEmpty()) {
                            breakdown.entries.sortedByDescending { it.value }.forEach { (cat, amt) ->
                                appendLine("- ${cat.name}: $currencySymbol${String.format("%.2f", amt)}")
                            }
                        }
                    }
                    QuestionType.INCOME_QUERY -> {
                        val total = incomeRepository.observeMonthlyTotal(monthStart, monthEnd).first()
                        val breakdown = incomeRepository.observeTotalByCategory(monthStart, monthEnd).first()
                        val recent = incomeRepository.observeAllIncome().first().take(5)
                        appendLine("Total earned: $currencySymbol${String.format("%.2f", total)}")
                        if (breakdown.isNotEmpty()) {
                            breakdown.entries.sortedByDescending { it.value }.forEach { (cat, amt) ->
                                appendLine("- ${cat.name}: $currencySymbol${String.format("%.2f", amt)}")
                            }
                        }
                        if (recent.isNotEmpty()) {
                            appendLine("Recent income:")
                            recent.forEach { income ->
                                val desc = if (income.description.isNotBlank()) " - ${income.description}" else ""
                                appendLine("- ${income.date}: ${income.category.name}, $currencySymbol${String.format("%.2f", income.amount)}$desc")
                            }
                        }
                    }
                    QuestionType.CATEGORY_BREAKDOWN -> {
                        val breakdown = expenseRepository.observeTotalByCategory(monthStart, monthEnd).first()
                        if (breakdown.isNotEmpty()) {
                            appendLine("Expense categories:")
                            breakdown.entries.sortedByDescending { it.value }.forEach { (cat, amt) ->
                                appendLine("- ${cat.name}: $currencySymbol${String.format("%.2f", amt)}")
                            }
                        }
                        val incomeBreakdown = incomeRepository.observeTotalByCategory(monthStart, monthEnd).first()
                        if (incomeBreakdown.isNotEmpty()) {
                            appendLine("Income categories:")
                            incomeBreakdown.entries.sortedByDescending { it.value }.forEach { (cat, amt) ->
                                appendLine("- ${cat.name}: $currencySymbol${String.format("%.2f", amt)}")
                            }
                        }
                    }
                    QuestionType.RECENT_ITEMS -> {
                        val recentExpenses = expenseRepository.observeAllExpenses().first().take(10)
                        val recentIncomes = incomeRepository.observeAllIncome().first().take(5)
                        if (recentExpenses.isNotEmpty()) {
                            appendLine("Recent expenses:")
                            recentExpenses.forEach { expense ->
                                val desc = if (expense.description.isNotBlank()) " - ${expense.description}" else ""
                                appendLine("- ${expense.date}: ${expense.category.name}, $currencySymbol${String.format("%.2f", expense.amount)}$desc")
                            }
                        }
                        if (recentIncomes.isNotEmpty()) {
                            appendLine("Recent income:")
                            recentIncomes.forEach { income ->
                                val desc = if (income.description.isNotBlank()) " - ${income.description}" else ""
                                appendLine("- ${income.date}: ${income.category.name}, $currencySymbol${String.format("%.2f", income.amount)}$desc")
                            }
                        }
                    }
                    QuestionType.COMPARISON -> {
                        val expTotal = expenseRepository.observeMonthlyTotal(monthStart, monthEnd).first()
                        val incTotal = incomeRepository.observeMonthlyTotal(monthStart, monthEnd).first()
                        val prevExpTotal = expenseRepository.observeMonthlyTotal(prevMonthStart, prevMonthEnd).first()
                        val prevIncTotal = incomeRepository.observeMonthlyTotal(prevMonthStart, prevMonthEnd).first()
                        appendLine("This month: spent $currencySymbol${String.format("%.2f", expTotal)}, earned $currencySymbol${String.format("%.2f", incTotal)}")
                        appendLine("Last month (${prevMonthEnd.month}): spent $currencySymbol${String.format("%.2f", prevExpTotal)}, earned $currencySymbol${String.format("%.2f", prevIncTotal)}")
                    }
                    QuestionType.GENERAL -> {
                        // Full data fetch (fallback)
                        val expTotal = expenseRepository.observeMonthlyTotal(monthStart, monthEnd).first()
                        val incTotal = incomeRepository.observeMonthlyTotal(monthStart, monthEnd).first()
                        val catBreakdown = expenseRepository.observeTotalByCategory(monthStart, monthEnd).first()
                        val recentExpenses = expenseRepository.observeAllExpenses().first().take(10)
                        val recentIncomes = incomeRepository.observeAllIncome().first().take(5)
                        val prevExpTotal = expenseRepository.observeMonthlyTotal(prevMonthStart, prevMonthEnd).first()
                        val prevIncTotal = incomeRepository.observeMonthlyTotal(prevMonthStart, prevMonthEnd).first()
                        val incCatBreakdown = incomeRepository.observeTotalByCategory(monthStart, monthEnd).first()

                        appendLine("Total spent: $currencySymbol${String.format("%.2f", expTotal)}")
                        appendLine("Total earned: $currencySymbol${String.format("%.2f", incTotal)}")
                        val net = incTotal - expTotal
                        if (net >= 0) appendLine("Saved: $currencySymbol${String.format("%.2f", net)}")
                        else appendLine("Overspent: $currencySymbol${String.format("%.2f", -net)}")

                        if (prevExpTotal > 0 || prevIncTotal > 0) {
                            appendLine("Last month (${prevMonthEnd.month}): spent $currencySymbol${String.format("%.2f", prevExpTotal)}, earned $currencySymbol${String.format("%.2f", prevIncTotal)}")
                        }
                        if (catBreakdown.isNotEmpty()) {
                            appendLine("Expense categories:")
                            catBreakdown.entries.sortedByDescending { it.value }.forEach { (cat, amt) ->
                                appendLine("- ${cat.name}: $currencySymbol${String.format("%.2f", amt)}")
                            }
                        }
                        if (incCatBreakdown.isNotEmpty()) {
                            appendLine("Income categories:")
                            incCatBreakdown.entries.sortedByDescending { it.value }.forEach { (cat, amt) ->
                                appendLine("- ${cat.name}: $currencySymbol${String.format("%.2f", amt)}")
                            }
                        }
                        if (recentExpenses.isNotEmpty()) {
                            appendLine("Recent expenses:")
                            recentExpenses.forEach { expense ->
                                val desc = if (expense.description.isNotBlank()) " - ${expense.description}" else ""
                                appendLine("- ${expense.date}: ${expense.category.name}, $currencySymbol${String.format("%.2f", expense.amount)}$desc")
                            }
                        }
                        if (recentIncomes.isNotEmpty()) {
                            appendLine("Recent income:")
                            recentIncomes.forEach { income ->
                                val desc = if (income.description.isNotBlank()) " - ${income.description}" else ""
                                appendLine("- ${income.date}: ${income.category.name}, $currencySymbol${String.format("%.2f", income.amount)}$desc")
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            ""
        }
    }

    private enum class IntentAction { ADD_EXPENSE, ADD_INCOME }

    private data class UserIntent(
        val action: IntentAction,
        val amount: Double?,
        val description: String?,
        val incomeType: IncomeType,
    )

    /**
     * Classify user intent using keyword matching.
     *
     * NOTE: Grammar-constrained generation via [LlamaBridge.generateJsonWithContext]
     * is NOT used because llama.cpp's grammar parser can crash with a native SIGABRT
     * (`Unexpected empty grammar stack after accepting piece`) on certain token
     * sequences produced by small models. Since this is a native crash, it cannot
     * be caught in Kotlin and kills the entire process.
     *
     * Category resolution is deferred to [executeAddExpense]/[executeAddIncome]
     * which use LLM-based suggestion as a fallback.
     */
    private fun classifyIntent(message: String): UserIntent? {
        val lower = message.lowercase()

        // Phase 3: Query override patterns — these indicate data QUERY, not an ADD action
        if (QUERY_OVERRIDES.any { lower.contains(it) }) return null

        // Explicit action triggers — words that explicitly indicate intent to create a record
        val hasActionTrigger = ACTION_TRIGGERS.any { "\\b$it\\b".toRegex().containsMatchIn(lower) }

        // Check multi-word phrases first to resolve ambiguous words like "paid".
        // "got paid 2000" = income, "I paid $50" = expense
        val hasIncomePhrase = INCOME_PHRASES.any { lower.contains(it) }
        val hasExpensePhrase = EXPENSE_PHRASES.any { lower.contains(it) }

        // Single-word type signals — disambiguate expense vs income
        val hasIncomeSignal = hasIncomePhrase ||
            INCOME_SIGNALS.any { "\\b$it\\b".toRegex().containsMatchIn(lower) }
        val hasExpenseSignal = hasExpensePhrase ||
            EXPENSE_SIGNALS.any { "\\b$it\\b".toRegex().containsMatchIn(lower) }

        // "paid" alone (not part of a phrase) defaults to expense: "paid $50 for dinner"
        val hasPaid = "\\bpaid\\b".toRegex().containsMatchIn(lower)
        val paidAsExpense = hasPaid && !hasIncomePhrase

        // Combine explicit and implicit triggers
        val hasImplicitAction = hasExpenseSignal || hasIncomeSignal || paidAsExpense

        if (!hasActionTrigger && !hasImplicitAction) return null

        val action = when {
            hasIncomePhrase -> IntentAction.ADD_INCOME
            hasIncomeSignal && !hasExpenseSignal && !paidAsExpense -> IntentAction.ADD_INCOME
            hasExpenseSignal || paidAsExpense -> IntentAction.ADD_EXPENSE
            // Neither signal: default to expense (more common)
            else -> IntentAction.ADD_EXPENSE
        }

        // Phase 3B: Use strict regex always; bare regex only when explicit action trigger present
        val amountRegex = if (hasActionTrigger) AMOUNT_REGEX_BARE else AMOUNT_REGEX_STRICT
        val amount = amountRegex.find(lower)?.let { match ->
            (match.groupValues[1].takeIf { it.isNotEmpty() }
                ?: match.groupValues[2].takeIf { it.isNotEmpty() })
                ?.toDoubleOrNull()
        }

        // Extract description: strip action/amount/type words, keep the rest.
        // If nothing remains, fall back to the first type-signal word found
        // (e.g. "salary" in "record income 1000 salary") as a category hint.
        val strippedDescription = lower
            .replace(amountRegex, "")
            .replace(STRIP_REGEX, "")
            .trim()
            .takeIf { it.isNotBlank() }

        val description = strippedDescription ?: run {
            // Fallback: find a type-signal word to use as category hint
            val signals = if (action == IntentAction.ADD_INCOME) INCOME_SIGNALS else EXPENSE_SIGNALS
            signals.firstOrNull { "\\b$it\\b".toRegex().containsMatchIn(lower) }
        }

        // Income type detection
        val incomeType = if (action == IntentAction.ADD_INCOME &&
            RECURRING_KEYWORDS.any { "\\b$it\\b".toRegex().containsMatchIn(lower) }
        ) {
            IncomeType.RECURRING
        } else {
            IncomeType.ONE_TIME
        }

        return UserIntent(
            action = action,
            amount = amount,
            description = description,
            incomeType = incomeType,
        )
    }

    private companion object {
        /** Words that indicate intent to create a new record. */
        private val ACTION_TRIGGERS = listOf("add", "record", "log", "create")

        /** Phrases that indicate a data QUERY, not an ADD action. Checked first to prevent false-positive ADD triggers. */
        private val QUERY_OVERRIDES = listOf(
            "what did i", "how much did i", "show me", "list my", "did i",
            "how many", "tell me", "what's my", "what is my", "am i",
        )

        /** Words that indicate the record is expense-related. */
        private val EXPENSE_SIGNALS = listOf("expense", "spent", "bought", "cost", "purchase")

        /** Words that indicate the record is income-related. */
        private val INCOME_SIGNALS = listOf("income", "earned", "salary", "received", "payment", "earning")

        /**
         * Multi-word phrases that override single-word signal classification.
         * "paid" alone is ambiguous — "I paid $50" = expense, "got paid 2000" = income.
         * These phrases are checked first to resolve the ambiguity.
         */
        private val INCOME_PHRASES = listOf("got paid", "been paid", "was paid", "were paid")
        private val EXPENSE_PHRASES = listOf("i paid", "we paid")

        private val RECURRING_KEYWORDS = listOf("recurring", "monthly", "salary", "regular", "subscription")

        /** Strict regex: requires `$` prefix or `dollars`/`bucks` suffix. Used for implicit triggers. */
        private val AMOUNT_REGEX_STRICT = Regex("""\$\s*(\d+(?:\.\d+)?)|(\d+(?:\.\d+)?)\s*(?:dollars?|bucks?)""")

        /** Bare regex: also matches standalone numbers. Only used with explicit action triggers. */
        private val AMOUNT_REGEX_BARE = Regex("""\$\s*(\d+(?:\.\d+)?)|(\d+(?:\.\d+)?)\s*(?:dollars?|bucks?|\$)?""")

        /** Words to strip when extracting the description/subject of the record. */
        private val STRIP_REGEX = Regex(
            """\b(?:add|record|log|create|an?|the|for|of|my|i|we|please|can\s+you|could\s+you|expense|income|spent|paid|bought|got|received|earned|salary|payment|earning|been|was|were|cost|purchase)\b""",
        )

        // Phase 4: Question type classification keywords
        private val EXPENSE_QUERY_KEYWORDS = listOf("total", "spend", "spent", "balance", "expense")
        private val INCOME_QUERY_KEYWORDS = listOf("earn", "income", "salary")
        private val CATEGORY_KEYWORDS = listOf("categor", "biggest", "top", "breakdown", "most")
        private val RECENT_KEYWORDS = listOf("recent", "latest", "last few", "last 5", "last 10")
        private val COMPARISON_KEYWORDS = listOf("compared", "last month", "previous", "vs", "versus")

        private const val LLM_TIMEOUT_MS = 60_000L
    }

    private suspend fun executeAddExpense(intent: UserIntent, currencySymbol: String): String {
        val amount = intent.amount
            ?: return "I couldn't determine the amount. Please specify how much the expense was."

        val categories = categoryRepository.observeAllCategories().first()

        // LLM-based category suggestion from description (no mutex — chat() already holds lock)
        val category = intent.description?.let { desc ->
            suggestCategoryInternal(desc, categories)
        } ?: return "I couldn't determine the category. Available: ${categories.joinToString(", ") { it.name }}"

        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val expense = Expense(
            amount = amount,
            category = category,
            description = intent.description ?: "",
            date = today,
            createdAt = Clock.System.now(),
        )
        expenseRepository.insertExpense(expense)

        val desc = if (!intent.description.isNullOrBlank()) " (${intent.description})" else ""
        return "Added expense: $currencySymbol${String.format("%.2f", amount)} in ${category.name} on $today$desc"
    }

    private suspend fun executeAddIncome(intent: UserIntent, currencySymbol: String): String {
        val amount = intent.amount
            ?: return "I couldn't determine the amount. Please specify how much the income was."

        val incomeCategories = incomeCategoryRepository.observeAllCategories().first()

        // Adapt IncomeCategory to Category for the generic suggestCategoryInternal
        val asCategoryList = incomeCategories.map { Category(it.id, it.name, it.icon, it.colorHex) }
        val incomeCategory = intent.description?.let { desc ->
            suggestCategoryInternal(desc, asCategoryList)
                ?.let { suggested -> incomeCategories.find { it.id == suggested.id } }
        } ?: return "I couldn't determine the category. Available: ${incomeCategories.joinToString(", ") { it.name }}"

        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val income = Income(
            amount = amount,
            incomeType = intent.incomeType,
            category = incomeCategory,
            description = intent.description ?: "",
            date = today,
            createdAt = Clock.System.now(),
        )
        incomeRepository.insertIncome(income)

        val desc = if (!intent.description.isNullOrBlank()) " (${intent.description})" else ""
        return "Added income: $currencySymbol${String.format("%.2f", amount)} in ${incomeCategory.name} on $today$desc"
    }

    private fun buildSystemPrompt(today: LocalDate, currencySymbol: String): String =
        "You are a finance assistant. Today: $today. Currency: $currencySymbol.\nAnswer in 1-3 sentences using only the provided data."

    private fun buildHistoryContext(
        financialSummary: String,
        history: List<ChatMessage>,
        currencySymbol: String,
    ): String = buildString {
        // Few-shot example to guide small models on expected format and brevity
        appendLine("User: How much did I spend this month?")
        appendLine("Assistant: You spent ${currencySymbol}450.00 this month across 3 categories.")
        if (financialSummary.isNotBlank()) {
            appendLine("User: What is my financial data?")
            appendLine("Assistant: $financialSummary")
        }
        history.forEach { msg ->
            val role = when (msg.role) {
                ChatRole.USER -> "User"
                ChatRole.ASSISTANT -> "Assistant"
            }
            appendLine("$role: ${msg.content}")
        }
    }

    private fun cleanResponse(raw: String): String = raw
        .substringBefore("\nUser:")
        .substringBefore("\nAssistant:")
        .substringBefore("\nHuman:")
        .substringBefore("\nAI:")
        .substringBefore("<|im_end|>")
        .substringBefore("<|endoftext|>")
        .substringBefore("\n###")
        .trim()

    override suspend fun chat(
        message: String,
        history: List<ChatMessage>,
    ): String {
        if (!isEnabled) {
            return "On-device AI is not available. No model file found."
        }

        // Phase 1: Gather context outside the mutex — no native resources needed
        val today = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
        val currencySymbol = getCurrencySymbol()
        val intent = classifyIntent(message)

        // Phase 2: Acquire mutex only for native LlamaBridge calls
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    withTimeout(LLM_TIMEOUT_MS) {
                        ensureModelLoaded()

                        when (intent?.action) {
                            IntentAction.ADD_EXPENSE -> executeAddExpense(intent, currencySymbol)
                            IntentAction.ADD_INCOME -> executeAddIncome(intent, currencySymbol)
                            null -> {
                                val questionType = classifyQuestionType(message)
                                val financialSummary = buildFinancialSummaryForType(today, currencySymbol, questionType)
                                val historyContext = buildHistoryContext(financialSummary, history, currencySymbol)
                                val systemPrompt = buildSystemPrompt(today, currencySymbol)
                                val raw = LlamaBridge.generateWithContext(
                                    systemPrompt,
                                    historyContext,
                                    message,
                                )
                                cleanResponse(raw)
                            }
                        }
                    }
                } catch (e: TimeoutCancellationException) {
                    "Sorry, the request timed out. Please try again with a simpler question."
                } catch (e: Exception) {
                    "Sorry, I encountered an error: ${e.message ?: "Unknown error"}"
                }
            }
        }
    }

    override fun chatStream(
        message: String,
        history: List<ChatMessage>,
    ): Flow<String> = callbackFlow {
        if (!isEnabled) {
            trySend("On-device AI is not available. No model file found.")
            close()
            return@callbackFlow
        }

        val today = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
        val currencySymbol = getCurrencySymbol()
        val intent = classifyIntent(message)

        // Intent-classified ADD commands: emit result instantly, no LLM needed
        if (intent != null) {
            mutex.withLock {
                withContext(Dispatchers.IO) {
                    try {
                        withTimeout(LLM_TIMEOUT_MS) {
                            ensureModelLoaded()
                            val result = when (intent.action) {
                                IntentAction.ADD_EXPENSE -> executeAddExpense(intent, currencySymbol)
                                IntentAction.ADD_INCOME -> executeAddIncome(intent, currencySymbol)
                            }
                            trySend(result)
                        }
                    } catch (e: TimeoutCancellationException) {
                        trySend("Sorry, the request timed out. Please try again with a simpler question.")
                    } catch (e: Exception) {
                        trySend("Sorry, I encountered an error: ${e.message ?: "Unknown error"}")
                    }
                }
            }
            close()
            return@callbackFlow
        }

        // Q&A path: stream tokens from LlamaBridge
        val accumulated = StringBuilder()

        launch(Dispatchers.IO) {
            mutex.withLock {
                try {
                    withTimeout(LLM_TIMEOUT_MS) {
                        ensureModelLoaded()
                        val questionType = classifyQuestionType(message)
                        val financialSummary = buildFinancialSummaryForType(today, currencySymbol, questionType)
                        val historyContext = buildHistoryContext(financialSummary, history, currencySymbol)
                        val systemPrompt = buildSystemPrompt(today, currencySymbol)

                        LlamaBridge.generateStreamWithContext(
                            systemPrompt,
                            historyContext,
                            message,
                            object : GenStream {
                                override fun onDelta(text: String) {
                                    accumulated.append(text)
                                    trySend(cleanResponse(accumulated.toString()))
                                }

                                override fun onComplete() {
                                    close()
                                }

                                override fun onError(message: String) {
                                    close(Exception(message))
                                }
                            },
                        )
                    }
                } catch (e: TimeoutCancellationException) {
                    trySend("Sorry, the request timed out. Please try again with a simpler question.")
                    close()
                } catch (e: Exception) {
                    trySend("Sorry, I encountered an error: ${e.message ?: "Unknown error"}")
                    close()
                }
            }
        }

        awaitClose()
    }

    fun shutdown() {
        if (isModelLoaded) {
            LlamaBridge.shutdown()
            isModelLoaded = false
        }
    }
}
