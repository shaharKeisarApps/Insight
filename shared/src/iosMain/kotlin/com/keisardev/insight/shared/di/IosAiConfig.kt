package com.keisardev.insight.shared.di

import kotlin.concurrent.Volatile
import com.keisardev.insight.core.ai.config.AiConfig
import com.keisardev.insight.core.ai.config.CloudProvider
import com.keisardev.insight.core.data.datastore.UserSettings
import com.keisardev.insight.core.data.datastore.UserSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * iOS implementation of [AiConfig].
 *
 * No build-time dev key on iOS — users configure cloud API keys through Settings screen.
 */
class IosAiConfig(
    private val userSettingsRepository: UserSettingsRepository,
) : AiConfig {

    @Volatile
    private var cachedSettings: UserSettings.CloudSettings? = null

    private fun getSettings(): UserSettings.CloudSettings {
        cachedSettings?.let { return it }
        val settings = runBlocking {
            userSettingsRepository.observeSettings().first().cloudSettings
        }
        cachedSettings = settings
        return settings
    }

    override val cloudProvider: CloudProvider
        get() = when (getSettings().provider) {
            UserSettings.CloudProviderProto.OPENAI -> CloudProvider.OPENAI
            UserSettings.CloudProviderProto.GEMINI -> CloudProvider.GEMINI
        }

    override val openAiApiKey: String?
        get() = if (cloudProvider == CloudProvider.OPENAI) {
            getSettings().apiKey.takeIf { it.isNotBlank() }
        } else null

    override val geminiApiKey: String?
        get() = if (cloudProvider == CloudProvider.GEMINI) {
            getSettings().apiKey.takeIf { it.isNotBlank() }
        } else null

    override val selectedModelId: String?
        get() = getSettings().selectedModelId.takeIf { it.isNotBlank() }

    override val hasUserApiKey: Boolean
        get() = getSettings().apiKey.isNotBlank()

    override val useDevKey: Boolean = false
    override val hasDevKey: Boolean = false

    override val isAiEnabled: Boolean
        get() = getSettings().apiKey.isNotBlank()
}
