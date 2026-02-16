package com.keisardev.insight.core.data.datastore

import kotlinx.coroutines.flow.Flow

interface UserSettingsRepository {
    fun observeSettings(): Flow<UserSettings>
    suspend fun updateAiMode(mode: UserSettings.AiModeProto)
    suspend fun updateCurrency(currencyCode: String)
}
