package com.keisardev.insight.feature.aichat.fakes

import com.keisardev.insight.core.ai.repository.ChatRepository
import com.keisardev.insight.core.model.ChatMessage
import com.keisardev.insight.core.model.ChatRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow

class FakeChatRepository(
    override val isEnabled: Boolean = true,
) : ChatRepository {

    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())

    var sendMessageCallCount = 0
        private set
    var lastSentContent: String? = null
        private set
    var clearHistoryCallCount = 0
        private set

    var responseContent: String = "AI response"

    fun setMessages(messages: List<ChatMessage>) {
        _messages.value = messages
    }

    override fun observeMessages(): Flow<List<ChatMessage>> = _messages

    override suspend fun sendMessage(content: String): ChatMessage {
        sendMessageCallCount++
        lastSentContent = content

        val userMessage = ChatMessage(
            id = "user-${_messages.value.size}",
            content = content,
            role = ChatRole.USER,
        )
        val assistantMessage = ChatMessage(
            id = "assistant-${_messages.value.size + 1}",
            content = responseContent,
            role = ChatRole.ASSISTANT,
        )
        _messages.value = _messages.value + userMessage + assistantMessage
        return assistantMessage
    }

    override fun sendMessageStream(content: String): Flow<ChatMessage> = flow {
        emit(sendMessage(content))
    }

    override suspend fun clearHistory(addWelcomeMessage: Boolean) {
        clearHistoryCallCount++
        if (addWelcomeMessage) {
            _messages.value = listOf(
                ChatMessage(
                    id = "welcome",
                    content = "Welcome! How can I help?",
                    role = ChatRole.ASSISTANT,
                )
            )
        } else {
            _messages.value = emptyList()
        }
    }
}
