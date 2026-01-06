package com.keisardev.metroditest.core.ai.service

import com.keisardev.metroditest.core.model.Category

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
     * @return The AI's response
     */
    suspend fun chat(message: String): String
}
