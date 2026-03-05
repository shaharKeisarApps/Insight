package com.keisardev.insight.core.ai.service

import com.keisardev.insight.core.model.Category
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Represents a message in the chat conversation history.
 */
data class ChatMessage(
    val role: ChatRole,
    val content: String,
)

/**
 * Role of a chat message participant.
 */
enum class ChatRole {
    USER,
    ASSISTANT,
}

/**
 * Service interface for AI-powered features.
 */
interface AiService {
    /**
     * Whether AI features are available and enabled.
     */
    val isEnabled: Boolean

    /**
     * Suggests the most appropriate category for an expense based on its description.
     *
     * @param description The expense description (e.g., "Uber to airport")
     * @param availableCategories List of available categories to choose from
     * @return The suggested category, or null if AI is disabled or suggestion failed
     */
    suspend fun suggestCategory(
        description: String,
        availableCategories: List<Category>,
    ): Category?

    /**
     * Processes a chat message and returns the AI response.
     * The AI has access to expense data and can answer questions about spending patterns.
     *
     * @param message The user's message/question
     * @param history Optional conversation history for multi-turn conversations
     * @return The AI's response
     */
    suspend fun chat(
        message: String,
        history: List<ChatMessage> = emptyList(),
    ): String

    /**
     * Streams a chat response token-by-token.
     * Each emission contains the accumulated text so far.
     *
     * Default implementation wraps [chat] for backends that don't support streaming.
     */
    fun chatStream(
        message: String,
        history: List<ChatMessage> = emptyList(),
    ): Flow<String> = flow { emit(chat(message, history)) }
}
