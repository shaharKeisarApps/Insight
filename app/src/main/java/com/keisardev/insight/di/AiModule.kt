package com.keisardev.insight.di

import com.keisardev.insight.BuildConfig
import com.keisardev.insight.core.ai.config.AiConfig
import com.keisardev.insight.core.ai.config.CloudProvider
import com.keisardev.insight.core.common.di.AppScope
import com.keisardev.insight.core.data.datastore.UserSettings
import com.keisardev.insight.core.data.datastore.UserSettingsRepository
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Implementation of [AiConfig] backed by DataStore [UserSettings.CloudSettings].
 *
 * Reads the user's cloud configuration (provider, API key, model) from DataStore.
 * The developer key from BuildConfig serves as a hidden backdoor (activated via 5-tap).
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class AiConfigImpl(
    private val userSettingsRepository: UserSettingsRepository,
) : AiConfig {

    companion object {
        /** Set to false to completely disable the dev key backdoor. */
        const val DEV_KEY_ENABLED = true
    }

    @Volatile
    private var cachedSettings: UserSettings.CloudSettings? = null

    private val devKey: String?
        get() {
            if (!DEV_KEY_ENABLED) return null
            return BuildConfig.OPENAI_API_KEY.takeIf { it.isNotBlank() }
        }

    private fun getSettings(): UserSettings.CloudSettings {
        cachedSettings?.let { return it }
        val settings = runBlocking {
            userSettingsRepository.observeSettings().first().cloudSettings
        }
        cachedSettings = settings
        return settings
    }

    fun updateSnapshot(cloudSettings: UserSettings.CloudSettings) {
        cachedSettings = cloudSettings
    }

    suspend fun sync() {
        cachedSettings = userSettingsRepository.observeSettings().first().cloudSettings
    }

    private val effectiveApiKey: String?
        get() {
            val settings = getSettings()
            if (settings.useDevKey && devKey != null) return devKey
            return settings.apiKey.takeIf { it.isNotBlank() }
        }

    override val cloudProvider: CloudProvider
        get() = when (getSettings().provider) {
            UserSettings.CloudProviderProto.OPENAI -> CloudProvider.OPENAI
            UserSettings.CloudProviderProto.GEMINI -> CloudProvider.GEMINI
        }

    override val openAiApiKey: String?
        get() = if (cloudProvider == CloudProvider.OPENAI) effectiveApiKey else null

    override val geminiApiKey: String?
        get() = if (cloudProvider == CloudProvider.GEMINI) effectiveApiKey else null

    override val selectedModelId: String?
        get() = getSettings().selectedModelId.takeIf { it.isNotBlank() }

    override val hasUserApiKey: Boolean
        get() = getSettings().apiKey.isNotBlank()

    override val useDevKey: Boolean
        get() = getSettings().useDevKey && devKey != null

    override val hasDevKey: Boolean
        get() = devKey != null

    override val isAiEnabled: Boolean
        get() = effectiveApiKey != null
}
