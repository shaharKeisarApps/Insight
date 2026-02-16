package com.keisardev.insight.core.data.datastore

import androidx.datastore.core.DataStore
import com.keisardev.insight.core.common.di.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class UserSettingsRepositoryImpl(
    private val dataStore: DataStore<UserSettings>,
) : UserSettingsRepository {

    override fun observeSettings(): Flow<UserSettings> = dataStore.data

    override suspend fun updateAiMode(mode: UserSettings.AiModeProto) {
        dataStore.updateData { current ->
            current.copy(aiMode = mode)
        }
    }

    override suspend fun updateCurrency(currencyCode: String) {
        dataStore.updateData { current ->
            current.copy(currencyCode = currencyCode)
        }
    }
}
