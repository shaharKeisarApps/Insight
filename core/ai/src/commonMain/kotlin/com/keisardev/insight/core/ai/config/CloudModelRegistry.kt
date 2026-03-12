package com.keisardev.insight.core.ai.config

import com.keisardev.insight.core.model.CloudModelOption

object CloudModelRegistry {

    private data class ModelEntry(
        val id: String,
        val displayName: String,
        val description: String,
    )

    private val openAiModels = listOf(
        ModelEntry(
            id = "gpt-4o-mini",
            displayName = "GPT-4o Mini",
            description = "Fast and affordable",
        ),
        ModelEntry(
            id = "gpt-4o",
            displayName = "GPT-4o",
            description = "High intelligence",
        ),
        ModelEntry(
            id = "gpt-4.1",
            displayName = "GPT-4.1",
            description = "Latest GPT-4 series",
        ),
        ModelEntry(
            id = "gpt-5-nano",
            displayName = "GPT-5 Nano",
            description = "Most cost-effective",
        ),
        ModelEntry(
            id = "gpt-5-mini",
            displayName = "GPT-5 Mini",
            description = "Balanced",
        ),
    )

    private val geminiModels = listOf(
        ModelEntry(
            id = "gemini-2.0-flash",
            displayName = "Gemini 2.0 Flash",
            description = "Fast and versatile",
        ),
        ModelEntry(
            id = "gemini-2.5-flash",
            displayName = "Gemini 2.5 Flash",
            description = "Latest fast model",
        ),
        ModelEntry(
            id = "gemini-2.5-pro",
            displayName = "Gemini 2.5 Pro",
            description = "Most capable",
        ),
    )

    fun modelsForProvider(provider: CloudProvider): List<CloudModelOption> {
        val entries = when (provider) {
            CloudProvider.OPENAI -> openAiModels
            CloudProvider.GEMINI -> geminiModels
        }
        return entries.map { CloudModelOption(it.id, it.displayName, it.description) }
    }

    fun defaultModelId(provider: CloudProvider): String = when (provider) {
        CloudProvider.OPENAI -> "gpt-4o-mini"
        CloudProvider.GEMINI -> "gemini-2.0-flash"
    }
}
