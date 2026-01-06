package com.keisardev.insight.core.model

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Represents a message in an AI chat conversation.
 *
 * This is the domain model used throughout the app for chat functionality.
 * It combines both the role information needed for AI context and the
 * UI metadata needed for display.
 */
data class ChatMessage(
    val id: String,
    val content: String,
    val role: ChatRole,
    val timestamp: Instant = Clock.System.now(),
) {
    /**
     * Convenience property for UI rendering.
     */
    val isUser: Boolean get() = role == ChatRole.USER
}

/**
 * Role of a chat message participant.
 */
enum class ChatRole {
    /** Message from the user */
    USER,

    /** Message from the AI assistant */
    ASSISTANT,

    /** System message (e.g., welcome message) */
    SYSTEM,
}
