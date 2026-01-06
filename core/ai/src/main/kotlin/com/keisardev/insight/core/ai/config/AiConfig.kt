package com.keisardev.insight.core.ai.config

/**
 * Configuration interface for AI features.
 * Implementation should be provided by the app module with the actual API key.
 */
interface AiConfig {
    /**
     * Whether AI features are enabled (API key is present and valid).
     */
    val isAiEnabled: Boolean

    /**
     * The OpenAI API key, or null if not configured.
     */
    val openAiApiKey: String?
}
