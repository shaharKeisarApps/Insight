package com.keisardev.insight.core.ai.repository

import com.keisardev.insight.core.ai.service.AiService
import com.keisardev.insight.core.ai.service.ChatMessage as ServiceChatMessage
import com.keisardev.insight.core.ai.service.ChatRole as ServiceChatRole
import com.keisardev.insight.core.common.di.AppScope
import com.keisardev.insight.core.model.ChatMessage
import com.keisardev.insight.core.model.ChatRole
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

/**
 * Implementation of [ChatRepository] that manages in-memory chat state
 * and delegates AI interactions to [AiService].
 *
 * This implementation:
 * - Maintains chat history in memory (suitable for single-session chat)
 * - Passes full conversation history to AI for context-aware responses
 * - Uses mutex for thread-safe message updates
 * - Provides reactive state via StateFlow
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class ChatRepositoryImpl @Inject constructor(
    private val aiService: AiService,
) : ChatRepository {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val _isLoading = MutableStateFlow(false)
    private val mutex = Mutex()

    override val isEnabled: Boolean
        get() = aiService.isEnabled

    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    override fun observeMessages(): Flow<List<ChatMessage>> = _messages.asStateFlow()

    override suspend fun sendMessage(content: String): ChatMessage {
        check(isEnabled) { "AI features are not enabled" }

        val userMessage = ChatMessage(
            id = generateMessageId("user"),
            content = content.trim(),
            role = ChatRole.USER,
            timestamp = Clock.System.now(),
        )

        // Add user message immediately
        mutex.withLock {
            _messages.update { it + userMessage }
        }

        _isLoading.value = true

        return try {
            // Convert history to service format (exclude the message we just added)
            val historyForService = _messages.value
                .filter { it.role != ChatRole.SYSTEM } // System messages are for UI only
                .dropLast(1) // Don't include the current message in history
                .map { it.toServiceMessage() }

            // Get AI response with conversation history
            val responseContent = aiService.chat(
                message = content.trim(),
                history = historyForService,
            )

            val aiMessage = ChatMessage(
                id = generateMessageId("assistant"),
                content = responseContent,
                role = ChatRole.ASSISTANT,
                timestamp = Clock.System.now(),
            )

            // Add AI response
            mutex.withLock {
                _messages.update { it + aiMessage }
            }

            aiMessage
        } finally {
            _isLoading.value = false
        }
    }

    override suspend fun clearHistory(addWelcomeMessage: Boolean) {
        mutex.withLock {
            _messages.value = if (addWelcomeMessage && isEnabled) {
                listOf(createWelcomeMessage())
            } else {
                emptyList()
            }
        }
    }

    private fun createWelcomeMessage(): ChatMessage = ChatMessage(
        id = "welcome",
        content = buildString {
            appendLine("Hi! I'm your financial assistant. Ask me anything about your expenses, like:")
            appendLine()
            appendLine("• \"How much did I spend this month?\"")
            appendLine("• \"What's my biggest expense category?\"")
            appendLine("• \"Show me my recent expenses\"")
        },
        role = ChatRole.SYSTEM,
        timestamp = Clock.System.now(),
    )

    private fun generateMessageId(prefix: String): String =
        "${prefix}_${Clock.System.now().toEpochMilliseconds()}"

    private fun ChatMessage.toServiceMessage(): ServiceChatMessage = ServiceChatMessage(
        role = when (role) {
            ChatRole.USER -> ServiceChatRole.USER
            ChatRole.ASSISTANT -> ServiceChatRole.ASSISTANT
            ChatRole.SYSTEM -> ServiceChatRole.ASSISTANT // System treated as assistant for API
        },
        content = content,
    )
}
