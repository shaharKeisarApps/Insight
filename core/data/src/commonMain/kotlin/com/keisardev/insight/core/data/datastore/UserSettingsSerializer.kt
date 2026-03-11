package com.keisardev.insight.core.data.datastore

import androidx.datastore.core.okio.OkioSerializer
import kotlinx.serialization.json.Json
import okio.BufferedSink
import okio.BufferedSource

object UserSettingsSerializer : OkioSerializer<UserSettings> {
    override val defaultValue: UserSettings = UserSettings()

    override suspend fun readFrom(source: BufferedSource): UserSettings {
        return try {
            Json.decodeFromString(source.readUtf8())
        } catch (_: Exception) {
            defaultValue
        }
    }

    override suspend fun writeTo(t: UserSettings, sink: BufferedSink) {
        sink.writeUtf8(Json.encodeToString(UserSettings.serializer(), t))
    }
}
