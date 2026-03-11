package com.keisardev.insight.core.ai.service

import com.keisardev.insight.core.common.di.AppScope
import com.keisardev.insight.core.common.CurrencyProvider
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
import java.util.Locale
import kotlin.time.Clock
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
    private val currencyProvider: CurrencyProvider,
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
        // Fast path: keyword-based category matching (no LLM needed)
        val keywordMatch = matchCategoryByKeyword(description, availableCategories)
        if (keywordMatch != null) return keywordMatch

        // Slow path: LLM-based suggestion with fuzzy matching
        val categoryNames = availableCategories.joinToString(", ") { it.name }
        val prompt = buildString {
            append("Categories: $categoryNames\n")
            append("Expense: \"$description\"\n")
            append("Category:")
        }

        return try {
            ensureModelLoaded()
            val result = LlamaBridge.generate(prompt).trim()
            // Exact match first
            availableCategories.find { it.name.equals(result, ignoreCase = true) }
                // Fuzzy: check if response contains a category name
                ?: availableCategories.find { result.contains(it.name, ignoreCase = true) }
                // Fuzzy: check if any category name appears at the start
                ?: availableCategories.find { result.startsWith(it.name, ignoreCase = true) }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Fast keyword-based category matching. Maps common expense/income descriptions
     * to categories without invoking the LLM. Returns null if no confident match.
     */
    private fun matchCategoryByKeyword(
        description: String,
        categories: List<Category>,
    ): Category? {
        val lower = description.lowercase()
        val categoryMap = categories.associateBy { it.name.lowercase() }

        // Map of keywords → expected category names (lowercase)
        val keywordToCategory = mapOf(
            // Food
            "lunch" to "food", "dinner" to "food", "breakfast" to "food",
            "coffee" to "food", "restaurant" to "food", "groceries" to "food",
            "food" to "food", "snack" to "food", "pizza" to "food",
            "meal" to "food", "eat" to "food", "drink" to "food",
            // Transport
            "uber" to "transport", "taxi" to "transport", "bus" to "transport",
            "train" to "transport", "gas" to "transport", "fuel" to "transport",
            "parking" to "transport", "metro" to "transport", "flight" to "transport",
            "transport" to "transport", "car" to "transport",
            // Shopping
            "clothes" to "shopping", "shoes" to "shopping", "amazon" to "shopping",
            "shop" to "shopping", "shopping" to "shopping", "store" to "shopping",
            "buy" to "shopping", "purchase" to "shopping",
            // Entertainment
            "movie" to "entertainment", "netflix" to "entertainment",
            "game" to "entertainment", "concert" to "entertainment",
            "spotify" to "entertainment", "entertainment" to "entertainment",
            "music" to "entertainment", "show" to "entertainment",
            // Bills
            "rent" to "bills", "electricity" to "bills", "water" to "bills",
            "internet" to "bills", "phone" to "bills", "bill" to "bills",
            "insurance" to "bills", "utility" to "bills", "subscription" to "bills",
            // Health
            "doctor" to "health", "medicine" to "health", "pharmacy" to "health",
            "hospital" to "health", "health" to "health", "gym" to "health",
            "dental" to "health", "medical" to "health",
        )

        for ((keyword, categoryName) in keywordToCategory) {
            if (lower.contains(keyword)) {
                return categoryMap[categoryName]
            }
        }
        return null
    }

    private suspend fun getCurrencySymbol(): String = currencyProvider.getCurrencySymbol()

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
                        appendLine("Total spent: $currencySymbol${String.format(Locale.US, "%.2f", total)}")
                        if (breakdown.isNotEmpty()) {
                            breakdown.entries.sortedByDescending { it.value }.forEach { (cat, amt) ->
                                appendLine("- ${cat.name}: $currencySymbol${String.format(Locale.US, "%.2f", amt)}")
                            }
                        }
                    }
                    QuestionType.INCOME_QUERY -> {
                        val total = incomeRepository.observeMonthlyTotal(monthStart, monthEnd).first()
                        val breakdown = incomeRepository.observeTotalByCategory(monthStart, monthEnd).first()
                        val recent = incomeRepository.observeAllIncome().first().take(5)
                        appendLine("Total earned: $currencySymbol${String.format(Locale.US, "%.2f", total)}")
                        if (breakdown.isNotEmpty()) {
                            breakdown.entries.sortedByDescending { it.value }.forEach { (cat, amt) ->
                                appendLine("- ${cat.name}: $currencySymbol${String.format(Locale.US, "%.2f", amt)}")
                            }
                        }
                        if (recent.isNotEmpty()) {
                            appendLine("Recent income:")
                            recent.forEach { income ->
                                val desc = if (income.description.isNotBlank()) " - ${income.description}" else ""
                                appendLine("- ${income.date}: ${income.category.name}, $currencySymbol${String.format(Locale.US, "%.2f", income.amount)}$desc")
                            }
                        }
                    }
                    QuestionType.CATEGORY_BREAKDOWN -> {
                        val breakdown = expenseRepository.observeTotalByCategory(monthStart, monthEnd).first()
                        if (breakdown.isNotEmpty()) {
                            appendLine("Expense categories:")
                            breakdown.entries.sortedByDescending { it.value }.forEach { (cat, amt) ->
                                appendLine("- ${cat.name}: $currencySymbol${String.format(Locale.US, "%.2f", amt)}")
                            }
                        }
                        val incomeBreakdown = incomeRepository.observeTotalByCategory(monthStart, monthEnd).first()
                        if (incomeBreakdown.isNotEmpty()) {
                            appendLine("Income categories:")
                            incomeBreakdown.entries.sortedByDescending { it.value }.forEach { (cat, amt) ->
                                appendLine("- ${cat.name}: $currencySymbol${String.format(Locale.US, "%.2f", amt)}")
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
                                appendLine("- ${expense.date}: ${expense.category.name}, $currencySymbol${String.format(Locale.US, "%.2f", expense.amount)}$desc")
                            }
                        }
                        if (recentIncomes.isNotEmpty()) {
                            appendLine("Recent income:")
                            recentIncomes.forEach { income ->
                                val desc = if (income.description.isNotBlank()) " - ${income.description}" else ""
                                appendLine("- ${income.date}: ${income.category.name}, $currencySymbol${String.format(Locale.US, "%.2f", income.amount)}$desc")
                            }
                        }
                    }
                    QuestionType.COMPARISON -> {
                        val expTotal = expenseRepository.observeMonthlyTotal(monthStart, monthEnd).first()
                        val incTotal = incomeRepository.observeMonthlyTotal(monthStart, monthEnd).first()
                        val prevExpTotal = expenseRepository.observeMonthlyTotal(prevMonthStart, prevMonthEnd).first()
                        val prevIncTotal = incomeRepository.observeMonthlyTotal(prevMonthStart, prevMonthEnd).first()
                        appendLine("This month: spent $currencySymbol${String.format(Locale.US, "%.2f", expTotal)}, earned $currencySymbol${String.format(Locale.US, "%.2f", incTotal)}")
                        appendLine("Last month (${prevMonthEnd.month}): spent $currencySymbol${String.format(Locale.US, "%.2f", prevExpTotal)}, earned $currencySymbol${String.format(Locale.US, "%.2f", prevIncTotal)}")
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

                        appendLine("Total spent: $currencySymbol${String.format(Locale.US, "%.2f", expTotal)}")
                        appendLine("Total earned: $currencySymbol${String.format(Locale.US, "%.2f", incTotal)}")
                        val net = incTotal - expTotal
                        if (net >= 0) appendLine("Saved: $currencySymbol${String.format(Locale.US, "%.2f", net)}")
                        else appendLine("Overspent: $currencySymbol${String.format(Locale.US, "%.2f", -net)}")

                        if (prevExpTotal > 0 || prevIncTotal > 0) {
                            appendLine("Last month (${prevMonthEnd.month}): spent $currencySymbol${String.format(Locale.US, "%.2f", prevExpTotal)}, earned $currencySymbol${String.format(Locale.US, "%.2f", prevIncTotal)}")
                        }
                        if (catBreakdown.isNotEmpty()) {
                            appendLine("Expense categories:")
                            catBreakdown.entries.sortedByDescending { it.value }.forEach { (cat, amt) ->
                                appendLine("- ${cat.name}: $currencySymbol${String.format(Locale.US, "%.2f", amt)}")
                            }
                        }
                        if (incCatBreakdown.isNotEmpty()) {
                            appendLine("Income categories:")
                            incCatBreakdown.entries.sortedByDescending { it.value }.forEach { (cat, amt) ->
                                appendLine("- ${cat.name}: $currencySymbol${String.format(Locale.US, "%.2f", amt)}")
                            }
                        }
                        if (recentExpenses.isNotEmpty()) {
                            appendLine("Recent expenses:")
                            recentExpenses.forEach { expense ->
                                val desc = if (expense.description.isNotBlank()) " - ${expense.description}" else ""
                                appendLine("- ${expense.date}: ${expense.category.name}, $currencySymbol${String.format(Locale.US, "%.2f", expense.amount)}$desc")
                            }
                        }
                        if (recentIncomes.isNotEmpty()) {
                            appendLine("Recent income:")
                            recentIncomes.forEach { income ->
                                val desc = if (income.description.isNotBlank()) " - ${income.description}" else ""
                                appendLine("- ${income.date}: ${income.category.name}, $currencySymbol${String.format(Locale.US, "%.2f", income.amount)}$desc")
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
        val hasActionTrigger = ACTION_TRIGGER_REGEX.containsMatchIn(lower)

        // Check multi-word phrases first to resolve ambiguous words like "paid".
        // "got paid 2000" = income, "I paid $50" = expense
        val hasIncomePhrase = INCOME_PHRASES.any { lower.contains(it) }
        val hasExpensePhrase = EXPENSE_PHRASES.any { lower.contains(it) }

        // Single-word type signals — disambiguate expense vs income
        val hasIncomeSignal = hasIncomePhrase || INCOME_SIGNAL_REGEX.containsMatchIn(lower)
        val hasExpenseSignal = hasExpensePhrase || EXPENSE_SIGNAL_REGEX.containsMatchIn(lower)

        // "paid" alone (not part of a phrase) defaults to expense: "paid $50 for dinner"
        val hasPaid = PAID_REGEX.containsMatchIn(lower)
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
            val signalRegex = if (action == IntentAction.ADD_INCOME) INCOME_SIGNAL_REGEX else EXPENSE_SIGNAL_REGEX
            signalRegex.find(lower)?.value
        }

        // Income type detection
        val incomeType = if (action == IntentAction.ADD_INCOME &&
            RECURRING_REGEX.containsMatchIn(lower)
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

        /** Bare regex: also matches standalone numbers. Only used with explicit action triggers ("add 50 for lunch"). */
        private val AMOUNT_REGEX_BARE = Regex("""\$\s*(\d+(?:\.\d+)?)|(\d+(?:\.\d+)?)\s*(?:dollars?|bucks?|\$)?""")

        /** Words to strip when extracting the description/subject of the record. */
        private val STRIP_REGEX = Regex(
            """\b(?:add|record|log|create|an?|the|for|of|my|i|we|please|can\s+you|could\s+you|expense|income|spent|paid|bought|got|received|earned|salary|payment|earning|been|was|were|cost|purchase)\b""",
        )

        // Pre-compiled word-boundary regex patterns (avoids re-compilation per call)
        private val ACTION_TRIGGER_REGEX = ACTION_TRIGGERS.joinToString("|") { "\\b$it\\b" }.toRegex()
        private val INCOME_SIGNAL_REGEX = INCOME_SIGNALS.joinToString("|") { "\\b$it\\b" }.toRegex()
        private val EXPENSE_SIGNAL_REGEX = EXPENSE_SIGNALS.joinToString("|") { "\\b$it\\b" }.toRegex()
        private val RECURRING_REGEX = RECURRING_KEYWORDS.joinToString("|") { "\\b$it\\b" }.toRegex()
        private val PAID_REGEX = "\\bpaid\\b".toRegex()

        // Phase 4: Question type classification keywords
        private val EXPENSE_QUERY_KEYWORDS = listOf("total", "spend", "spent", "balance", "expense")
        private val INCOME_QUERY_KEYWORDS = listOf("earn", "income", "salary")
        private val CATEGORY_KEYWORDS = listOf("categor", "biggest", "top", "breakdown", "most")
        private val RECENT_KEYWORDS = listOf("recent", "latest", "last few", "last 5", "last 10")
        private val COMPARISON_KEYWORDS = listOf("compared", "last month", "previous", "vs", "versus")

        private const val LLM_TIMEOUT_MS = 60_000L

        /** Stop patterns that indicate the model is generating beyond the answer. */
        private val STOP_PATTERNS = listOf(
            "\nUser:", "\nAssistant:", "\nHuman:", "\nAI:",
            "\nQ:", "\nA:",
            "<|im_end|>", "<|endoftext|>", "<|end|>",
            "<end_of_turn>", "<start_of_turn>",
            "\n###", "\n---",
            "\nQuestion:", "\nAnswer:", "\nQUESTION:",
            "\nNote:", "\nInstructions:",
            "\nPrevious conversation:",
        )
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
        return "Added expense: $currencySymbol${String.format(Locale.US, "%.2f", amount)} in ${category.name} on $today$desc"
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
        return "Added income: $currencySymbol${String.format(Locale.US, "%.2f", amount)} in ${incomeCategory.name} on $today$desc"
    }

    private fun buildSystemPrompt(
        today: LocalDate,
        currencySymbol: String,
        financialSummary: String,
    ): String = buildString {
        appendLine("You are a personal finance assistant. Answer questions using ONLY the data below.")
        appendLine()
        if (financialSummary.isNotBlank()) {
            appendLine(financialSummary)
        } else {
            appendLine("Financial data (${today.month} ${today.year}): No records yet.")
        }
        appendLine()
        appendLine("Rules: Use only the data above. 1-2 sentences. No greetings or sign-offs.")
    }

    private fun buildHistoryContext(
        history: List<ChatMessage>,
    ): String = buildString {
        if (history.isEmpty()) return@buildString
        // Use Q/A format to avoid triggering "User:"/"Assistant:" continuation in small models
        appendLine("Previous conversation:")
        history.forEach { msg ->
            val prefix = if (msg.role == ChatRole.USER) "Q" else "A"
            appendLine("$prefix: ${msg.content}")
        }
    }

    /** Light cleanup: only truncate at stop tokens. Safe for streaming. */
    private fun truncateAtStopTokens(text: String): String {
        var result = text
        for (pattern in STOP_PATTERNS) {
            val idx = result.indexOf(pattern)
            if (idx > 0) result = result.substring(0, idx)
        }
        return result
    }

    /** Full cleanup: truncate + strip prefixes/filler. Use only on final response. */
    private fun cleanResponse(raw: String): String {
        var cleaned = truncateAtStopTokens(raw).trim()

        // Strip leading role/label prefixes the model may echo
        val prefixes = listOf("Assistant:", "Answer:", "Response:", "AI:", "User:", "A:", "Q:")
        for (prefix in prefixes) {
            if (cleaned.startsWith(prefix, ignoreCase = true)) {
                cleaned = cleaned.substring(prefix.length).trim()
                break
            }
        }
        // Strip common filler preambles that reference internal context
        val fillerPrefixes = listOf(
            "Based on the data above, ",
            "Based on the data provided, ",
            "Based on the financial data, ",
            "According to the data, ",
            "According to your data, ",
            "Based on your data, ",
        )
        for (filler in fillerPrefixes) {
            if (cleaned.startsWith(filler, ignoreCase = true)) {
                cleaned = cleaned.substring(filler.length).replaceFirstChar { it.uppercase() }
                break
            }
        }
        return cleaned
    }

    /**
     * Build a direct, data-driven answer for structured question types.
     * Returns null for GENERAL questions that need the LLM.
     */
    private suspend fun buildDirectAnswer(
        questionType: QuestionType,
        today: LocalDate,
        currencySymbol: String,
    ): String? {
        val monthStart = LocalDate(today.year, today.month, 1)
        val monthEnd = today
        val prevMonthEnd = monthStart.minus(DatePeriod(days = 1))
        val prevMonthStart = LocalDate(prevMonthEnd.year, prevMonthEnd.month, 1)

        return try {
            when (questionType) {
                QuestionType.EXPENSE_QUERY -> {
                    val total = expenseRepository.observeMonthlyTotal(monthStart, monthEnd).first()
                    val breakdown = expenseRepository.observeTotalByCategory(monthStart, monthEnd).first()
                    buildString {
                        append("You spent $currencySymbol${String.format(Locale.US, "%.2f", total)} this month")
                        if (breakdown.isNotEmpty()) {
                            append(" across ${breakdown.size} ${if (breakdown.size == 1) "category" else "categories"}.")
                            breakdown.entries.sortedByDescending { it.value }.forEach { (cat, amt) ->
                                append("\n- ${cat.name}: $currencySymbol${String.format(Locale.US, "%.2f", amt)}")
                            }
                        } else {
                            append(". No expenses recorded yet.")
                        }
                    }
                }
                QuestionType.INCOME_QUERY -> {
                    val total = incomeRepository.observeMonthlyTotal(monthStart, monthEnd).first()
                    val breakdown = incomeRepository.observeTotalByCategory(monthStart, monthEnd).first()
                    buildString {
                        append("You earned $currencySymbol${String.format(Locale.US, "%.2f", total)} this month")
                        if (breakdown.isNotEmpty()) {
                            append(".")
                            breakdown.entries.sortedByDescending { it.value }.forEach { (cat, amt) ->
                                append("\n- ${cat.name}: $currencySymbol${String.format(Locale.US, "%.2f", amt)}")
                            }
                        } else {
                            append(". No income recorded yet.")
                        }
                    }
                }
                QuestionType.CATEGORY_BREAKDOWN -> {
                    val expBreakdown = expenseRepository.observeTotalByCategory(monthStart, monthEnd).first()
                    val incBreakdown = incomeRepository.observeTotalByCategory(monthStart, monthEnd).first()
                    buildString {
                        if (expBreakdown.isNotEmpty()) {
                            appendLine("Expense categories this month:")
                            expBreakdown.entries.sortedByDescending { it.value }.forEach { (cat, amt) ->
                                appendLine("- ${cat.name}: $currencySymbol${String.format(Locale.US, "%.2f", amt)}")
                            }
                        }
                        if (incBreakdown.isNotEmpty()) {
                            appendLine("Income categories this month:")
                            incBreakdown.entries.sortedByDescending { it.value }.forEach { (cat, amt) ->
                                appendLine("- ${cat.name}: $currencySymbol${String.format(Locale.US, "%.2f", amt)}")
                            }
                        }
                        if (expBreakdown.isEmpty() && incBreakdown.isEmpty()) {
                            append("No records yet this month.")
                        }
                    }.trim()
                }
                QuestionType.RECENT_ITEMS -> {
                    val recentExp = expenseRepository.observeAllExpenses().first().take(10)
                    val recentInc = incomeRepository.observeAllIncome().first().take(5)
                    buildString {
                        if (recentExp.isNotEmpty()) {
                            appendLine("Recent expenses:")
                            recentExp.forEach { e ->
                                val desc = if (e.description.isNotBlank()) " - ${e.description}" else ""
                                appendLine("- ${e.date}: ${e.category.name}, $currencySymbol${String.format(Locale.US, "%.2f", e.amount)}$desc")
                            }
                        }
                        if (recentInc.isNotEmpty()) {
                            appendLine("Recent income:")
                            recentInc.forEach { i ->
                                val desc = if (i.description.isNotBlank()) " - ${i.description}" else ""
                                appendLine("- ${i.date}: ${i.category.name}, $currencySymbol${String.format(Locale.US, "%.2f", i.amount)}$desc")
                            }
                        }
                        if (recentExp.isEmpty() && recentInc.isEmpty()) {
                            append("No recent records found.")
                        }
                    }.trim()
                }
                QuestionType.COMPARISON -> {
                    val expTotal = expenseRepository.observeMonthlyTotal(monthStart, monthEnd).first()
                    val incTotal = incomeRepository.observeMonthlyTotal(monthStart, monthEnd).first()
                    val prevExpTotal = expenseRepository.observeMonthlyTotal(prevMonthStart, prevMonthEnd).first()
                    val prevIncTotal = incomeRepository.observeMonthlyTotal(prevMonthStart, prevMonthEnd).first()
                    buildString {
                        appendLine("This month (${today.month}): spent $currencySymbol${String.format(Locale.US, "%.2f", expTotal)}, earned $currencySymbol${String.format(Locale.US, "%.2f", incTotal)}")
                        append("Last month (${prevMonthEnd.month}): spent $currencySymbol${String.format(Locale.US, "%.2f", prevExpTotal)}, earned $currencySymbol${String.format(Locale.US, "%.2f", prevIncTotal)}")
                        val netChange = (incTotal - expTotal) - (prevIncTotal - prevExpTotal)
                        if (netChange > 0) append("\nYou're doing $currencySymbol${String.format(Locale.US, "%.2f", netChange)} better than last month.")
                        else if (netChange < 0) append("\nYou're $currencySymbol${String.format(Locale.US, "%.2f", -netChange)} behind last month.")
                    }
                }
                QuestionType.GENERAL -> null // Fall through to LLM
            }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun chat(
        message: String,
        history: List<ChatMessage>,
    ): String {
        if (!isEnabled) {
            return "On-device AI is not available. No model file found."
        }

        val today = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
        val currencySymbol = getCurrencySymbol()
        val intent = classifyIntent(message)

        // Intent-classified ADD commands need mutex for LLM category suggestion
        if (intent != null) {
            return mutex.withLock {
                withContext(Dispatchers.IO) {
                    try {
                        withTimeout(LLM_TIMEOUT_MS) {
                            ensureModelLoaded()
                            when (intent.action) {
                                IntentAction.ADD_EXPENSE -> executeAddExpense(intent, currencySymbol)
                                IntentAction.ADD_INCOME -> executeAddIncome(intent, currencySymbol)
                            }
                        }
                    } catch (e: TimeoutCancellationException) {
                        "Sorry, the request timed out. Please try again."
                    } catch (e: Exception) {
                        "Sorry, I encountered an error: ${e.message ?: "Unknown error"}"
                    }
                }
            }
        }

        // Data-lookup questions: build answer directly from DB (no LLM needed)
        val questionType = classifyQuestionType(message)
        val directAnswer = buildDirectAnswer(questionType, today, currencySymbol)
        if (directAnswer != null) return directAnswer

        // GENERAL questions: fall through to LLM
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    withTimeout(LLM_TIMEOUT_MS) {
                        ensureModelLoaded()
                        val financialSummary = buildFinancialSummaryForType(today, currencySymbol, questionType)
                        val systemPrompt = buildSystemPrompt(today, currencySymbol, financialSummary)
                        val historyContext = buildHistoryContext(history)
                        val raw = LlamaBridge.generateWithContext(
                            systemPrompt,
                            historyContext,
                            message,
                        )
                        cleanResponse(raw)
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

        // Data-lookup questions: emit direct answer instantly (no LLM)
        val questionType = classifyQuestionType(message)
        val directAnswer = buildDirectAnswer(questionType, today, currencySymbol)
        if (directAnswer != null) {
            trySend(directAnswer)
            close()
            return@callbackFlow
        }

        // GENERAL questions: stream tokens from LlamaBridge
        val accumulated = StringBuilder()

        launch(Dispatchers.IO) {
            mutex.withLock {
                try {
                    withTimeout(LLM_TIMEOUT_MS) {
                        ensureModelLoaded()
                        val financialSummary = buildFinancialSummaryForType(today, currencySymbol, questionType)
                        val systemPrompt = buildSystemPrompt(today, currencySymbol, financialSummary)
                        val historyContext = buildHistoryContext(history)

                        LlamaBridge.generateStreamWithContext(
                            systemPrompt,
                            historyContext,
                            message,
                            object : GenStream {
                                override fun onDelta(text: String) {
                                    accumulated.append(text)
                                    // Apply light stop-token truncation during streaming
                                    // to prevent garbage from appearing in real-time
                                    trySend(truncateAtStopTokens(accumulated.toString()))
                                }

                                override fun onComplete() {
                                    // Apply full cleanup (prefixes, filler) on final emission
                                    trySend(cleanResponse(accumulated.toString()))
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
