package com.keisardev.insight.core.ai.repository

import com.keisardev.insight.core.model.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository for managing AI chat conversations.
 *
 * This repository follows the same patterns as other repositories in the app,
 * providing a reactive interface for chat functionality while encapsulating
 * the AI service implementation details.
 *
 * Example usage:
 * ```kotlin
 * class AiChatPresenter @AssistedInject constructor(
 *     private val chatRepository: ChatRepository,
 * ) : Presenter<State> {
 *
 *     @Composable
 *     override fun present(): State {
 *         val messages by chatRepository.observeMessages()
 *             .collectAsRetainedState(initial = emptyList())
 *         val isLoading by chatRepository.isLoading
 *             .collectAsRetainedState(initial = false)
 *
 *         return State(messages, isLoading) { event ->
 *             when (event) {
 *                 is SendMessage -> scope.launch {
 *                     chatRepository.sendMessage(event.content)
 *                 }
 *             }
 *         }
 *     }
 * }
 * ```
 */
interface ChatRepository {

    /**
     * Whether AI features are available and enabled.
     */
    val isEnabled: Boolean

    /**
     * Observable loading state. True when the AI is processing a message.
     */
    val isLoading: StateFlow<Boolean>

    /**
     * Observes all messages in the current chat session.
     *
     * The Flow emits a new list whenever messages are added.
     * Messages are ordered chronologically (oldest first).
     */
    fun observeMessages(): Flow<List<ChatMessage>>

    /**
     * Sends a user message and receives an AI response.
     *
     * This method:
     * 1. Adds the user message to the conversation
     * 2. Sends the message along with conversation history to the AI
     * 3. Adds the AI response to the conversation
     *
     * @param content The user's message content
     * @return The AI's response message
     * @throws IllegalStateException if AI is not enabled
     */
    suspend fun sendMessage(content: String): ChatMessage

    /**
     * Sends a user message and streams the AI response token-by-token.
     *
     * Emits [ChatMessage] instances with progressively longer content as tokens arrive.
     * The UI can observe this flow to display streaming text in real time.
     *
     * @param content The user's message content
     * @return Flow of ChatMessage with progressively updated assistant content
     */
    fun sendMessageStream(content: String): Flow<ChatMessage>

    /**
     * Clears all messages from the current chat session.
     * Optionally adds a welcome message after clearing.
     *
     * @param addWelcomeMessage If true, adds a welcome message after clearing
     */
    suspend fun clearHistory(addWelcomeMessage: Boolean = true)
}
