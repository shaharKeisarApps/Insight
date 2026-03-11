package com.keisardev.insight.core.data.datastore

import kotlinx.coroutines.flow.Flow

interface UserSettingsRepository {
    fun observeSettings(): Flow<UserSettings>
    suspend fun updateAiMode(mode: UserSettings.AiModeProto)
    suspend fun updateCurrency(currencyCode: String)
    suspend fun updateActiveModel(fileName: String)
    suspend fun updateCloudSettings(cloudSettings: UserSettings.CloudSettings)
    suspend fun updateUseDevKey(useDevKey: Boolean)
}
