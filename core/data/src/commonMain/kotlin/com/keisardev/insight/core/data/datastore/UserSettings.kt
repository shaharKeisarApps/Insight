package com.keisardev.insight.core.data.datastore

import kotlinx.serialization.Serializable

@Serializable
data class UserSettings(
    val aiMode: AiModeProto = AiModeProto.AUTO,
    val currencyCode: String = "DEVICE",
    val activeModelFileName: String = "",
    val cloudSettings: CloudSettings = CloudSettings(),
) {
    @Serializable
    enum class AiModeProto {
        LOCAL,
        CLOUD,
        AUTO,
    }

    @Serializable
    enum class CloudProviderProto {
        OPENAI,
        GEMINI,
    }

    @Serializable
    data class CloudSettings(
        val provider: CloudProviderProto = CloudProviderProto.OPENAI,
        val apiKey: String = "",
        val selectedModelId: String = "",
        val useDevKey: Boolean = false,
    )
}
