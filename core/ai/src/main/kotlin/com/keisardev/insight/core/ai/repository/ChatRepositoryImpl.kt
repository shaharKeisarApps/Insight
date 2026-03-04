package com.keisardev.insight.core.ai.repository

import com.keisardev.insight.core.ai.service.AiService
import com.keisardev.insight.core.ai.service.ChatMessage as ServiceChatMessage
import com.keisardev.insight.core.ai.service.ChatRole as ServiceChatRole
import com.keisardev.insight.core.common.di.AppScope
import com.keisardev.insight.core.database.ChatMessageEntity
import com.keisardev.insight.core.database.ChatMessageLocalDataSource
import com.keisardev.insight.core.model.ChatMessage
import com.keisardev.insight.core.model.ChatRole
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Implementation of [ChatRepository] that manages in-memory chat state
 * and delegates AI interactions to [AiService].
 *
 * Dual-write pattern:
 * - In-memory [_messages] StateFlow for real-time UI (sub-millisecond updates)
 * - [ChatMessageLocalDataSource] for persistence across process death
 * - User messages persisted immediately; assistant messages on stream completion only
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class ChatRepositoryImpl(
    private val aiService: AiService,
    private val localDataSource: ChatMessageLocalDataSource,
) : ChatRepository {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val _isLoading = MutableStateFlow(false)
    private val mutex = Mutex()
    private val messageCounter = AtomicLong(0)
    private val hydrated = AtomicBoolean(false)

    override val isEnabled: Boolean
        get() = aiService.isEnabled

    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    override fun observeMessages(): Flow<List<ChatMessage>> = _messages.asStateFlow()

    private suspend fun hydrateIfNeeded() {
        if (hydrated.getAndSet(true)) return
        try {
            val entities = localDataSource.observeAll().first()
            if (entities.isNotEmpty()) {
                val messages = entities.map { it.toDomainModel() }
                _messages.value = messages
            }
        } catch (_: Exception) {
            // First launch or empty DB — no-op
        }
    }

    override suspend fun sendMessage(content: String): ChatMessage {
        hydrateIfNeeded()

        if (!isEnabled) {
            return ChatMessage(
                id = generateMessageId("assistant"),
                content = "AI features are not currently available. Please check your settings.",
                role = ChatRole.ASSISTANT,
                timestamp = Clock.System.now(),
            )
        }

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
        persistMessage(userMessage)

        _isLoading.value = true

        return try {
            // Convert history to service format (exclude the message we just added)
            val historyForService = _messages.value
                .filter { it.role != ChatRole.SYSTEM } // System messages are for UI only
                .dropLast(1) // Don't include the current message in history
                .map { it.toServiceMessage() }

            // Get AI response with conversation history
            val responseContent = withTimeout(60_000L) {
                aiService.chat(
                    message = content.trim(),
                    history = historyForService,
                )
            }

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
            persistMessage(aiMessage)
            trimOldMessages()

            aiMessage
        } catch (e: TimeoutCancellationException) {
            val timeoutMessage = ChatMessage(
                id = generateMessageId("assistant"),
                content = "The request timed out. Please try again with a simpler question.",
                role = ChatRole.ASSISTANT,
                timestamp = Clock.System.now(),
            )
            mutex.withLock {
                _messages.update { it + timeoutMessage }
            }
            persistMessage(timeoutMessage)
            timeoutMessage
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val errorMessage = ChatMessage(
                id = generateMessageId("assistant"),
                content = "Sorry, I encountered an error: ${e.message ?: "Unknown error"}. Please try again.",
                role = ChatRole.ASSISTANT,
                timestamp = Clock.System.now(),
            )
            mutex.withLock {
                _messages.update { it + errorMessage }
            }
            persistMessage(errorMessage)
            errorMessage
        } finally {
            _isLoading.value = false
        }
    }

    override fun sendMessageStream(content: String): Flow<ChatMessage> = flow {
        hydrateIfNeeded()

        if (!isEnabled) {
            val msg = ChatMessage(
                id = generateMessageId("assistant"),
                content = "AI features are not currently available. Please check your settings.",
                role = ChatRole.ASSISTANT,
                timestamp = Clock.System.now(),
            )
            emit(msg)
            return@flow
        }

        val userMessage = ChatMessage(
            id = generateMessageId("user"),
            content = content.trim(),
            role = ChatRole.USER,
            timestamp = Clock.System.now(),
        )

        mutex.withLock {
            _messages.update { it + userMessage }
        }
        persistMessage(userMessage)

        val placeholderId = generateMessageId("assistant")
        val placeholder = ChatMessage(
            id = placeholderId,
            content = "",
            role = ChatRole.ASSISTANT,
            timestamp = Clock.System.now(),
        )

        mutex.withLock {
            _messages.update { it + placeholder }
        }

        _isLoading.value = true

        try {
            val historyForService = _messages.value
                .filter { it.role != ChatRole.SYSTEM }
                .dropLast(2) // Exclude current user message and placeholder
                .map { it.toServiceMessage() }

            var lastText = ""
            aiService.chatStream(
                message = content.trim(),
                history = historyForService,
            ).collect { text ->
                lastText = text
                _messages.update { list ->
                    list.map { if (it.id == placeholderId) it.copy(content = text) else it }
                }
                emit(
                    _messages.value.first { it.id == placeholderId },
                )
            }

            // Persist final assistant message on stream completion (not per-token)
            if (lastText.isNotBlank()) {
                persistMessage(
                    _messages.value.first { it.id == placeholderId },
                )
                trimOldMessages()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val errorText = "Sorry, I encountered an error: ${e.message ?: "Unknown error"}. Please try again."
            _messages.update { list ->
                list.map { if (it.id == placeholderId) it.copy(content = errorText) else it }
            }
            val errorMsg = _messages.value.first { it.id == placeholderId }
            persistMessage(errorMsg)
            emit(errorMsg)
        } finally {
            _isLoading.value = false
        }
    }

    override suspend fun clearHistory(addWelcomeMessage: Boolean) {
        localDataSource.deleteAll()
        mutex.withLock {
            _messages.value = if (addWelcomeMessage && isEnabled) {
                listOf(createWelcomeMessage())
            } else {
                emptyList()
            }
        }
    }

    private suspend fun persistMessage(message: ChatMessage) {
        try {
            localDataSource.insert(message.toEntity())
        } catch (_: Exception) {
            // Persistence failure shouldn't break chat
        }
    }

    private suspend fun trimOldMessages() {
        try {
            if (localDataSource.count() > MAX_PERSISTED_MESSAGES) {
                // Reload from DB with limit, delete all, re-insert recent
                val recent = localDataSource.observeAll().first().takeLast(MAX_PERSISTED_MESSAGES.toInt())
                localDataSource.deleteAll()
                recent.forEach { localDataSource.insert(it) }
            }
        } catch (_: Exception) {
            // Best-effort trim
        }
    }

    private fun createWelcomeMessage(): ChatMessage = ChatMessage(
        id = "welcome",
        content = buildString {
            appendLine("Hi! I'm your financial assistant. Ask me anything about your finances, like:")
            appendLine()
            appendLine("• \"How much did I spend this month?\"")
            appendLine("• \"What's my biggest expense category?\"")
            appendLine("• \"How much did I earn this month?\"")
            appendLine("• \"Am I saving money?\"")
            appendLine("• \"Show me my recent expenses\"")
        },
        role = ChatRole.SYSTEM,
        timestamp = Clock.System.now(),
    )

    private fun generateMessageId(prefix: String): String =
        "${prefix}_${Clock.System.now().toEpochMilliseconds()}_${messageCounter.incrementAndGet()}"

    private fun ChatMessage.toServiceMessage(): ServiceChatMessage = ServiceChatMessage(
        role = when (role) {
            ChatRole.USER -> ServiceChatRole.USER
            ChatRole.ASSISTANT -> ServiceChatRole.ASSISTANT
            ChatRole.SYSTEM -> ServiceChatRole.ASSISTANT // System treated as assistant for API
        },
        content = content,
    )

    private fun ChatMessage.toEntity(): ChatMessageEntity = ChatMessageEntity(
        id = id,
        content = content,
        role = role.name,
        timestamp = timestamp.toEpochMilliseconds(),
    )

    private fun ChatMessageEntity.toDomainModel(): ChatMessage = ChatMessage(
        id = id,
        content = content,
        role = ChatRole.valueOf(role),
        timestamp = Instant.fromEpochMilliseconds(timestamp),
    )

    private companion object {
        const val MAX_PERSISTED_MESSAGES = 100L
    }
}
