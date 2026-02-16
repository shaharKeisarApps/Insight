package com.keisardev.insight.core.data.datastore

import kotlinx.serialization.Serializable

@Serializable
data class UserSettings(
    val aiMode: AiModeProto = AiModeProto.AUTO,
    val currencyCode: String = "DEVICE",
) {
    @Serializable
    enum class AiModeProto {
        LOCAL,
        CLOUD,
        AUTO,
    }
}
