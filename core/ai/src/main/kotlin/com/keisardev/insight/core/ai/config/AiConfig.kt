package com.keisardev.insight.core.ai.config

enum class CloudProvider {
    OPENAI,
    GEMINI,
}

/**
 * Configuration interface for AI features.
 * Implementation should be provided by the app module with the actual API key.
 */
interface AiConfig {
    val isAiEnabled: Boolean
    val openAiApiKey: String?
    val geminiApiKey: String?
    val cloudProvider: CloudProvider
    val selectedModelId: String?
    val hasUserApiKey: Boolean
    val useDevKey: Boolean
    val hasDevKey: Boolean
}
