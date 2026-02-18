package com.keisardev.insight.core.ai.service

import android.app.Application
import com.keisardev.insight.core.common.di.AppScope
import com.keisardev.insight.core.data.datastore.UserSettingsRepository
import com.keisardev.insight.core.data.repository.CategoryRepository
import com.keisardev.insight.core.data.repository.ExpenseRepository
import com.keisardev.insight.core.data.repository.IncomeCategoryRepository
import com.keisardev.insight.core.data.repository.IncomeRepository
import com.keisardev.insight.core.model.Category
import com.keisardev.insight.core.model.Expense
import com.keisardev.insight.core.model.Income
import com.keisardev.insight.core.model.IncomeType
import com.llamatik.library.platform.LlamaBridge
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
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
import java.io.File

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
    application: Application,
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val categoryRepository: CategoryRepository,
    private val incomeCategoryRepository: IncomeCategoryRepository,
    private val userSettingsRepository: UserSettingsRepository,
) : AiService {

    private val modelsDir = File(application.filesDir, "models")
    @Volatile private var _activeModelFileName: String = ""

    private val modelPath: String?
        get() {
            if (_activeModelFileName.isNotEmpty()) {
                val activeFile = File(modelsDir, _activeModelFileName)
                if (activeFile.exists()) return activeFile.absolutePath
            }
            return modelsDir.listFiles()?.firstOrNull { it.extension == "gguf" }?.absolutePath
        }

    private val mutex = Mutex()
    @Volatile private var isModelLoaded = false
    @Volatile private var loadedModelPath: String? = null

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
            LlamaBridge.initGenerateModel(path)
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

    private suspend fun buildFinancialSummary(today: LocalDate, currencySymbol: String): String {
        return try {
            val monthStart = LocalDate(today.year, today.month, 1)
            val monthEnd = today

            // Previous month
            val prevMonthEnd = monthStart.minus(DatePeriod(days = 1))
            val prevMonthStart = LocalDate(prevMonthEnd.year, prevMonthEnd.month, 1)

            val monthlyExpenseTotal = expenseRepository.observeMonthlyTotal(monthStart, monthEnd).first()
            val monthlyIncomeTotal = incomeRepository.observeMonthlyTotal(monthStart, monthEnd).first()
            val categoryBreakdown = expenseRepository.observeTotalByCategory(monthStart, monthEnd).first()
            val recentExpenses = expenseRepository.observeAllExpenses().first().take(10)
            val recentIncomes = incomeRepository.observeAllIncome().first().take(5)

            // Previous month totals for comparison
            val prevExpenseTotal = expenseRepository.observeMonthlyTotal(prevMonthStart, prevMonthEnd).first()
            val prevIncomeTotal = incomeRepository.observeMonthlyTotal(prevMonthStart, prevMonthEnd).first()

            // Income category breakdown
            val incomeCategoryBreakdown = incomeRepository.observeTotalByCategory(monthStart, monthEnd).first()

            buildString {
                appendLine("Here is your financial data:")
                appendLine("Currency: $currencySymbol")
                appendLine()
                appendLine("This month (${today.month} ${today.year}):")
                appendLine("- Total spent: $currencySymbol${String.format("%.2f", monthlyExpenseTotal)}")
                appendLine("- Total earned: $currencySymbol${String.format("%.2f", monthlyIncomeTotal)}")
                val net = monthlyIncomeTotal - monthlyExpenseTotal
                if (net >= 0) {
                    appendLine("- Saved: $currencySymbol${String.format("%.2f", net)}")
                } else {
                    appendLine("- Overspent by: $currencySymbol${String.format("%.2f", -net)}")
                }

                if (prevExpenseTotal > 0 || prevIncomeTotal > 0) {
                    appendLine()
                    appendLine("Previous month (${prevMonthEnd.month} ${prevMonthEnd.year}):")
                    appendLine("- Total spent: $currencySymbol${String.format("%.2f", prevExpenseTotal)}")
                    appendLine("- Total earned: $currencySymbol${String.format("%.2f", prevIncomeTotal)}")
                }

                if (categoryBreakdown.isNotEmpty()) {
                    appendLine()
                    appendLine("Expense categories this month:")
                    categoryBreakdown.entries
                        .sortedByDescending { it.value }
                        .forEach { (category, amount) ->
                            appendLine("- ${category.name}: $currencySymbol${String.format("%.2f", amount)}")
                        }
                }

                if (incomeCategoryBreakdown.isNotEmpty()) {
                    appendLine()
                    appendLine("Income categories this month:")
                    incomeCategoryBreakdown.entries
                        .sortedByDescending { it.value }
                        .forEach { (category, amount) ->
                            appendLine("- ${category.name}: $currencySymbol${String.format("%.2f", amount)}")
                        }
                }

                if (recentExpenses.isNotEmpty()) {
                    appendLine()
                    appendLine("Recent expenses:")
                    recentExpenses.forEach { expense ->
                        val desc = if (expense.description.isNotBlank()) " - ${expense.description}" else ""
                        appendLine("- ${expense.date}: ${expense.category.name}, $currencySymbol${String.format("%.2f", expense.amount)}$desc")
                    }
                }

                if (recentIncomes.isNotEmpty()) {
                    appendLine()
                    appendLine("Recent income:")
                    recentIncomes.forEach { income ->
                        val desc = if (income.description.isNotBlank()) " - ${income.description}" else ""
                        appendLine("- ${income.date}: ${income.category.name}, $currencySymbol${String.format("%.2f", income.amount)}$desc")
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

        // Extract amount via regex
        val amount = AMOUNT_REGEX.find(lower)?.let { match ->
            (match.groupValues[1].takeIf { it.isNotEmpty() }
                ?: match.groupValues[2].takeIf { it.isNotEmpty() })
                ?.toDoubleOrNull()
        }

        // Extract description: strip action/amount/type words, keep the rest.
        // If nothing remains, fall back to the first type-signal word found
        // (e.g. "salary" in "record income 1000 salary") as a category hint.
        val strippedDescription = lower
            .replace(AMOUNT_REGEX, "")
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

        /** Matches `$50`, `50 dollars`, `$50.99`, or bare numbers like `50` / `50.99`. */
        private val AMOUNT_REGEX = Regex("""\$\s*(\d+(?:\.\d+)?)|(\d+(?:\.\d+)?)\s*(?:dollars?|bucks?|\$)?""")

        /** Words to strip when extracting the description/subject of the record. */
        private val STRIP_REGEX = Regex(
            """\b(?:add|record|log|create|an?|the|for|of|my|i|we|expense|income|spent|paid|bought|got|received|earned|salary|payment|earning|been|was|were|cost|purchase)\b""",
        )

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
                                val financialSummary = buildFinancialSummary(today, currencySymbol)
                                val historyContext = buildString {
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
                                val systemPrompt = buildString {
                                    appendLine("You are a helpful financial assistant. Today is $today.")
                                    appendLine()
                                    appendLine("RULES:")
                                    appendLine("1. Answer questions using ONLY the financial data provided below.")
                                    appendLine("2. Format all amounts as $currencySymbol followed by the number (e.g., ${currencySymbol}123.45).")
                                    appendLine("3. Be concise and specific — use actual numbers from the data.")
                                    appendLine("4. If the data doesn't contain enough info to answer, say so.")
                                    appendLine("5. This month = ${LocalDate(today.year, today.month, 1)} to $today.")
                                }
                                val raw = LlamaBridge.generateWithContext(
                                    systemPrompt,
                                    historyContext,
                                    message,
                                )
                                raw.substringBefore("\nUser:")
                                    .substringBefore("\nAssistant:")
                                    .trim()
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

    fun shutdown() {
        if (isModelLoaded) {
            LlamaBridge.shutdown()
            isModelLoaded = false
        }
    }
}
