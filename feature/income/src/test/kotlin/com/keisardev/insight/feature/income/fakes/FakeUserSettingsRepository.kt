package com.keisardev.insight.feature.income.fakes

import com.keisardev.insight.core.data.datastore.UserSettings
import com.keisardev.insight.core.data.datastore.UserSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeUserSettingsRepository : UserSettingsRepository {
    private val settings = MutableStateFlow(UserSettings())

    override fun observeSettings(): Flow<UserSettings> = settings

    override suspend fun updateAiMode(mode: UserSettings.AiModeProto) {
        settings.value = settings.value.copy(aiMode = mode)
    }

    override suspend fun updateCurrency(currencyCode: String) {
        settings.value = settings.value.copy(currencyCode = currencyCode)
    }

    override suspend fun updateActiveModel(fileName: String) {
        settings.value = settings.value.copy(activeModelFileName = fileName)
    }

    override suspend fun updateCloudSettings(cloudSettings: UserSettings.CloudSettings) {
        settings.value = settings.value.copy(cloudSettings = cloudSettings)
    }

    override suspend fun updateUseDevKey(useDevKey: Boolean) {
        settings.value = settings.value.copy(
            cloudSettings = settings.value.cloudSettings.copy(useDevKey = useDevKey),
        )
    }
}
