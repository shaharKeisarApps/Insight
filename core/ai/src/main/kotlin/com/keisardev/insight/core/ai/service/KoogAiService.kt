package com.keisardev.insight.core.ai.service

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.tools
import ai.koog.prompt.executor.clients.openai.OpenAIModels
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
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class KoogAiService @Inject constructor(
    private val aiConfig: AiConfig,
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
) : AiService {

    override val isEnabled: Boolean
        get() = aiConfig.isAiEnabled

    /**
     * Creates a fresh chat agent for each request.
     * AIAgent instances can only be run once, so we create a new one per message.
     */
    private fun createChatAgent(apiKey: String): AIAgent<String, String> {
        val toolRegistry = ToolRegistry {
            tools(ExpenseTools(expenseRepository, categoryRepository))
        }

        val today = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date

        return AIAgent(
            promptExecutor = simpleOpenAIExecutor(apiKey),
            llmModel = OpenAIModels.Chat.GPT5Nano,
            systemPrompt = """
                You are a helpful financial assistant for an expense tracking app.
                Your role is to help users understand their spending patterns and provide insights.

                Today's date is $today.

                Guidelines:
                - Be concise and helpful
                - Use the available tools to get expense data
                - When discussing money amounts, format them nicely (e.g., $123.45)
                - When asked about trends, use actual data from the tools
                - If you don't have enough data, let the user know

                You have access to tools that can:
                - Get total expenses for any date range
                - Get expenses broken down by category
                - Get recent expense entries with details
                - List available expense categories
            """.trimIndent(),
            toolRegistry = toolRegistry,
        )
    }

    override suspend fun suggestCategory(
        description: String,
        availableCategories: List<Category>,
    ): Category? {
        if (!isEnabled || description.isBlank()) return null

        val apiKey = aiConfig.openAiApiKey ?: return null
        val categoryNames = availableCategories.joinToString(", ") { it.name }

        return withContext(Dispatchers.IO) {
            try {
                // Create a simple agent just for categorization (no tools needed)
                val categorizationAgent = AIAgent(
                    promptExecutor = simpleOpenAIExecutor(apiKey),
                    llmModel = OpenAIModels.Chat.GPT4oMini,
                    systemPrompt = """
                        You are a categorization assistant. Given a list of expense categories and an expense description,
                        you must respond with ONLY the name of the most appropriate category.
                        Do not include any other text, explanation, or punctuation - just the category name exactly as provided.
                    """.trimIndent(),
                )

                val prompt = "Categories: [$categoryNames]\nExpense: \"$description\"\n\nCategory:"
                val result = categorizationAgent.run(prompt)

                // Find matching category (case-insensitive)
                availableCategories.find { category ->
                    category.name.equals(result.trim(), ignoreCase = true)
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    override suspend fun chat(message: String): String {
        if (!isEnabled) {
            return "AI features are not available. Please add your OpenAI API key to local.properties to enable AI features."
        }

        val apiKey = aiConfig.openAiApiKey ?: return "AI service is not configured properly."

        return withContext(Dispatchers.IO) {
            try {
                // Create a fresh agent for each message (AIAgent can only run once)
                val agent = createChatAgent(apiKey)
                agent.run(message)
            } catch (e: Exception) {
                "Sorry, I encountered an error: ${e.message ?: "Unknown error"}"
            }
        }
    }
}
