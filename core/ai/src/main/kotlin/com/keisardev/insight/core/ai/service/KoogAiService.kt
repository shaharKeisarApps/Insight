package com.keisardev.insight.core.ai.service

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.tools
import ai.koog.agents.ext.agent.chatAgentStrategy
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.llm.LLModel
import com.keisardev.insight.core.ai.config.AiConfig
import com.keisardev.insight.core.ai.config.CloudModelRegistry
import com.keisardev.insight.core.ai.config.CloudProvider
import com.keisardev.insight.core.ai.tools.ExpenseTools
import com.keisardev.insight.core.ai.tools.FinancialSummaryTools
import com.keisardev.insight.core.ai.tools.IncomeTools
import com.keisardev.insight.core.common.di.AppScope
import com.keisardev.insight.core.common.CurrencyProvider
import com.keisardev.insight.core.data.datastore.UserSettingsRepository
import com.keisardev.insight.core.data.repository.CategoryRepository
import com.keisardev.insight.core.data.repository.ExpenseRepository
import com.keisardev.insight.core.data.repository.FinancialSummaryRepository
import com.keisardev.insight.core.data.repository.IncomeCategoryRepository
import com.keisardev.insight.core.data.repository.IncomeRepository
import com.keisardev.insight.core.model.Category
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Implementation of [AiService] using JetBrains Koog framework.
 *
 * Supports multiple cloud providers (OpenAI and Gemini) with dynamic executor switching.
 * The executor is cached and reused until the provider/key combination changes.
 */
@SingleIn(AppScope::class)
@Inject
class KoogAiService(
    private val aiConfig: AiConfig,
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val incomeRepository: IncomeRepository,
    private val incomeCategoryRepository: IncomeCategoryRepository,
    private val financialSummaryRepository: FinancialSummaryRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val currencyProvider: CurrencyProvider,
) : AiService {

    /** Key used to detect when the executor needs to be recreated. */
    @Volatile
    private var executorKey: String = ""

    @Volatile
    private var cachedExecutor: SingleLLMPromptExecutor? = null

    private fun getExecutor(): SingleLLMPromptExecutor? {
        val provider = aiConfig.cloudProvider
        val apiKey = when (provider) {
            CloudProvider.OPENAI -> aiConfig.openAiApiKey
            CloudProvider.GEMINI -> aiConfig.geminiApiKey
        } ?: return null

        val newKey = "${provider.name}:$apiKey"
        if (newKey == executorKey && cachedExecutor != null) {
            return cachedExecutor
        }

        val executor = when (provider) {
            CloudProvider.OPENAI -> simpleOpenAIExecutor(apiKey)
            CloudProvider.GEMINI -> simpleGoogleAIExecutor(apiKey)
        }
        cachedExecutor = executor
        executorKey = newKey
        return executor
    }

    private fun getModel(): LLModel {
        val modelId = aiConfig.selectedModelId
        if (modelId != null) {
            CloudModelRegistry.findLLModel(modelId)?.let { return it }
        }
        return CloudModelRegistry.findLLModel(
            CloudModelRegistry.defaultModelId(aiConfig.cloudProvider),
        )!!
    }

    private suspend fun getCurrencySymbol(): String = currencyProvider.getCurrencySymbol()

    private fun createToolRegistry(currencySymbol: String): ToolRegistry {
        return ToolRegistry {
            tools(ExpenseTools(expenseRepository, categoryRepository, currencySymbol))
            tools(IncomeTools(incomeRepository, incomeCategoryRepository, currencySymbol))
            tools(FinancialSummaryTools(financialSummaryRepository, currencySymbol))
        }
    }

    val hasDevKey: Boolean
        get() = aiConfig.hasDevKey

    override val isEnabled: Boolean
        get() = aiConfig.isAiEnabled

    private fun createChatAgent(history: List<ChatMessage>, currencySymbol: String): AIAgent<String, String> {
        val executor = getExecutor()
            ?: throw IllegalStateException("AI service not configured - missing API key")

        val today = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date

        val historyContext = if (history.isNotEmpty()) {
            val formattedHistory = history.joinToString("\n") { msg ->
                val role = when (msg.role) {
                    ChatRole.USER -> "User"
                    ChatRole.ASSISTANT -> "Assistant"
                }
                "$role: ${msg.content}"
            }
            "\n\nPrevious conversation:\n$formattedHistory\n"
        } else {
            ""
        }

        return AIAgent(
            promptExecutor = executor,
            llmModel = getModel(),
            strategy = chatAgentStrategy(),
            systemPrompt = """
                You are a helpful financial assistant for a personal finance tracking app.
                Your role is to help users understand their spending patterns, income, and overall financial health.

                Today's date is $today.
                This month's date range: from ${LocalDate(today.year, today.month, 1)} to $today.
                Format all amounts using the user's currency symbol: $currencySymbol (do NOT use ${'$'} unless that IS the user's currency).
                $historyContext

                ## CRITICAL: Tool Selection Rules

                When the user mentions a SPECIFIC ITEM or KEYWORD (like "pizza", "coffee", "uber", "salary", etc.):
                → Use searchExpenses for expense-related keywords, searchIncome for income-related keywords.
                  These searches are case-insensitive and will find matches regardless of capitalization.

                Examples:
                - "How much did I spend on pizza?" → searchExpenses(keyword="pizza")
                - "Show me my salary income" → searchIncome(keyword="salary")
                - "Did I get any freelance income?" → searchIncome(keyword="freelance")

                For OVERALL FINANCIAL HEALTH queries (income vs expenses, savings, net balance):
                → Use getFinancialSummary. It gives income, expenses, net balance, savings rate, and breakdowns.

                Examples:
                - "Am I saving money?" → getFinancialSummary
                - "What's my income vs expenses?" → getFinancialSummary
                - "How am I doing financially?" → getFinancialSummary

                Use date-range tools for period-specific queries:
                - "How much did I spend this month?" → getTotalExpenses
                - "How much did I earn this month?" → getTotalIncome
                - "What are my expenses by category?" → getExpensesByCategory

                ## Guidelines
                - Be concise and helpful
                - Format money using the currency symbol $currencySymbol (e.g., ${currencySymbol}123.45)
                - If no results found, mention that the search was case-insensitive
                - If you don't have enough data, let the user know

                When the user asks to ADD, CREATE, or RECORD an expense or income:
                → Use addExpense for expenses, addIncome for income.
                  First get the categories list if unsure which category to use.

                Examples:
                - "Add a $50 dinner expense" → addExpense(amount=50.0, categoryName="Food", description="dinner")
                - "Record $100 from investment" → addIncome(amount=100.0, categoryName="Investment")
                - "Add income of 2000 salary" → addIncome(amount=2000.0, categoryName="Salary", incomeType="RECURRING")

                ## Available Tools
                ### Expense Tools
                - addExpense: Add a new expense entry (amount, category, optional description/date)
                - searchExpenses: Find expenses by keyword (case-insensitive)
                - getTotalExpenses: Get total spending for a date range
                - getExpensesByCategory: Get spending breakdown by category
                - getExpensesByDateRange: List expenses within dates
                - getRecentExpenses: Get recent expense entries
                - getCategories: List available expense categories

                ### Income Tools
                - addIncome: Add a new income entry (amount, category, optional description/date/type)
                - searchIncome: Find income by keyword (case-insensitive)
                - getTotalIncome: Get total income for a date range
                - getIncomeByCategory: Get income breakdown by category
                - getIncomeByDateRange: List income within dates
                - getRecentIncome: Get recent income entries
                - getIncomeCategories: List available income categories

                ### Financial Summary Tools
                - getFinancialSummary: Get complete financial overview (income, expenses, net balance, savings rate)
            """.trimIndent(),
            toolRegistry = createToolRegistry(currencySymbol),
        )
    }

    private fun createCategorizationAgent(): AIAgent<String, String> {
        val executor = getExecutor()
            ?: throw IllegalStateException("AI service not configured - missing API key")

        return AIAgent(
            promptExecutor = executor,
            llmModel = CloudModelRegistry.cheapestModel(aiConfig.cloudProvider),
            systemPrompt = """
                You are a categorization assistant. Given a list of expense categories and an expense description,
                you must respond with ONLY the name of the most appropriate category.
                Do not include any other text, explanation, or punctuation - just the category name exactly as provided.
            """.trimIndent(),
        )
    }

    override suspend fun suggestCategory(
        description: String,
        availableCategories: List<Category>,
    ): Category? {
        if (!isEnabled || description.isBlank()) return null
        if (getExecutor() == null) return null

        val categoryNames = availableCategories.joinToString(", ") { it.name }

        return withContext(Dispatchers.IO) {
            try {
                val agent = createCategorizationAgent()
                val prompt = "Categories: [$categoryNames]\nExpense: \"$description\"\n\nCategory:"
                val result = agent.run(prompt)

                availableCategories.find { category ->
                    category.name.equals(result.trim(), ignoreCase = true)
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    override suspend fun chat(
        message: String,
        history: List<ChatMessage>,
    ): String {
        if (!isEnabled) {
            return "AI features are not available. Set up a cloud provider in Settings or download an on-device model."
        }

        if (getExecutor() == null) {
            return "AI service is not configured properly."
        }

        return withContext(Dispatchers.IO) {
            try {
                val currencySymbol = getCurrencySymbol()
                val agent = createChatAgent(history, currencySymbol)
                withTimeout(45_000L) {
                    agent.run(message)
                }
            } catch (e: TimeoutCancellationException) {
                "Sorry, the request timed out. Please try again with a simpler question."
            } catch (e: Exception) {
                "Sorry, I encountered an error: ${e.message ?: "Unknown error"}"
            }
        }
    }
}
