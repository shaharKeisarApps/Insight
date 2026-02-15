package com.keisardev.insight.core.ai.service

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.tools
import ai.koog.agents.ext.agent.chatAgentStrategy
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import com.keisardev.insight.core.ai.config.AiConfig
import com.keisardev.insight.core.ai.tools.ExpenseTools
import com.keisardev.insight.core.common.di.AppScope
import com.keisardev.insight.core.data.repository.CategoryRepository
import com.keisardev.insight.core.data.repository.ExpenseRepository
import com.keisardev.insight.core.model.Category
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Implementation of [AiService] using JetBrains Koog framework.
 *
 * Performance optimizations:
 * - Prompt executor is lazily created once and reused (expensive to create)
 * - AIAgent instances are created per request (cheap to create, single-use design)
 * - Conversation history is passed to maintain context across messages
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class KoogAiService(
    private val aiConfig: AiConfig,
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
) : AiService {

    /**
     * Lazily initialized prompt executor - reused across all agent instances.
     * Creating executors is expensive (HTTP clients, connection pools).
     */
    private val promptExecutor: SingleLLMPromptExecutor? by lazy {
        aiConfig.openAiApiKey?.let { simpleOpenAIExecutor(it) }
    }

    /**
     * Tool registry for chat agents - created once and reused.
     */
    private val chatToolRegistry: ToolRegistry by lazy {
        ToolRegistry {
            tools(ExpenseTools(expenseRepository, categoryRepository))
        }
    }

    override val isEnabled: Boolean
        get() = aiConfig.isAiEnabled

    /**
     * Creates a chat agent with conversation history support.
     * AIAgent instances are designed for single-run execution.
     *
     * @param history Previous conversation messages to include in context
     */
    private fun createChatAgent(history: List<ChatMessage>): AIAgent<String, String> {
        val executor = promptExecutor
            ?: throw IllegalStateException("AI service not configured - missing API key")

        val today = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date

        // Build conversation history context
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
            llmModel = OpenAIModels.Chat.GPT4oMini,
            strategy = chatAgentStrategy(),
            systemPrompt = """
                You are a helpful financial assistant for an expense tracking app.
                Your role is to help users understand their spending patterns and provide insights.

                Today's date is $today.
                $historyContext

                ## CRITICAL: Tool Selection Rules

                When the user mentions a SPECIFIC ITEM or KEYWORD (like "pizza", "coffee", "uber", "groceries", "rent", etc.):
                → ALWAYS use searchExpenses FIRST. It's case-insensitive and will find matches regardless of capitalization.

                Examples requiring searchExpenses:
                - "How much did I spend on pizza?" → searchExpenses(keyword="pizza")
                - "Show me my Coffee expenses" → searchExpenses(keyword="coffee")
                - "Did I buy groceries?" → searchExpenses(keyword="groceries")

                Use date-range tools (getTotalExpenses, getExpensesByCategory, getExpensesByDateRange) ONLY for:
                - "How much did I spend this month?" → date range query
                - "What are my expenses by category?" → category breakdown
                - "Show me last week's expenses" → date range query

                ## Guidelines
                - Be concise and helpful
                - Format money nicely (e.g., ${'$'}123.45)
                - If no results found, mention that the search was case-insensitive
                - If you don't have enough data, let the user know

                ## Available Tools
                - searchExpenses: Find expenses by keyword (case-insensitive) - USE THIS FOR ITEM QUERIES
                - getTotalExpenses: Get total spending for a date range
                - getExpensesByCategory: Get spending breakdown by category
                - getExpensesByDateRange: List expenses within dates
                - getRecentExpenses: Get recent expense entries
                - getCategories: List available categories
            """.trimIndent(),
            toolRegistry = chatToolRegistry,
        )
    }

    /**
     * Creates a lightweight agent for category suggestion.
     * Uses GPT5Nano - the most cost-effective model for simple classification tasks.
     */
    private fun createCategorizationAgent(): AIAgent<String, String> {
        val executor = promptExecutor
            ?: throw IllegalStateException("AI service not configured - missing API key")

        return AIAgent(
            promptExecutor = executor,
            llmModel = OpenAIModels.Chat.GPT5Nano,
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
        if (promptExecutor == null) return null

        val categoryNames = availableCategories.joinToString(", ") { it.name }

        return withContext(Dispatchers.IO) {
            try {
                val agent = createCategorizationAgent()
                val prompt = "Categories: [$categoryNames]\nExpense: \"$description\"\n\nCategory:"
                val result = agent.run(prompt)

                // Find matching category (case-insensitive)
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
            return "AI features are not available. Please add your OpenAI API key to local.properties to enable AI features."
        }

        if (promptExecutor == null) {
            return "AI service is not configured properly."
        }

        return withContext(Dispatchers.IO) {
            try {
                val agent = createChatAgent(history)
                agent.run(message)
            } catch (e: Exception) {
                "Sorry, I encountered an error: ${e.message ?: "Unknown error"}"
            }
        }
    }
}
