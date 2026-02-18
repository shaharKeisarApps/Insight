package com.keisardev.insight.core.ai.config

import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.llm.LLModel
import com.keisardev.insight.core.model.CloudModelOption

object CloudModelRegistry {

    private data class ModelEntry(
        val id: String,
        val displayName: String,
        val description: String,
        val llModel: LLModel,
    )

    private val openAiModels = listOf(
        ModelEntry(
            id = "gpt-4o-mini",
            displayName = "GPT-4o Mini",
            description = "Fast and affordable",
            llModel = OpenAIModels.Chat.GPT4oMini,
        ),
        ModelEntry(
            id = "gpt-4o",
            displayName = "GPT-4o",
            description = "High intelligence",
            llModel = OpenAIModels.Chat.GPT4o,
        ),
        ModelEntry(
            id = "gpt-4.1",
            displayName = "GPT-4.1",
            description = "Latest GPT-4 series",
            llModel = OpenAIModels.Chat.GPT4_1,
        ),
        ModelEntry(
            id = "gpt-5-nano",
            displayName = "GPT-5 Nano",
            description = "Most cost-effective",
            llModel = OpenAIModels.Chat.GPT5Nano,
        ),
        ModelEntry(
            id = "gpt-5-mini",
            displayName = "GPT-5 Mini",
            description = "Balanced",
            llModel = OpenAIModels.Chat.GPT5Mini,
        ),
    )

    private val geminiModels = listOf(
        ModelEntry(
            id = "gemini-2.0-flash",
            displayName = "Gemini 2.0 Flash",
            description = "Fast and versatile",
            llModel = GoogleModels.Gemini2_0Flash,
        ),
        ModelEntry(
            id = "gemini-2.5-flash",
            displayName = "Gemini 2.5 Flash",
            description = "Latest fast model",
            llModel = GoogleModels.Gemini2_5Flash,
        ),
        ModelEntry(
            id = "gemini-2.5-pro",
            displayName = "Gemini 2.5 Pro",
            description = "Most capable",
            llModel = GoogleModels.Gemini2_5Pro,
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

    fun findLLModel(modelId: String): LLModel? {
        return (openAiModels + geminiModels).find { it.id == modelId }?.llModel
    }

    fun cheapestModel(provider: CloudProvider): LLModel = when (provider) {
        CloudProvider.OPENAI -> OpenAIModels.Chat.GPT5Nano
        CloudProvider.GEMINI -> GoogleModels.Gemini2_0Flash
    }
}
