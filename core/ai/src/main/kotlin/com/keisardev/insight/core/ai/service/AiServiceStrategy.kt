package com.keisardev.insight.core.ai.service

import com.keisardev.insight.core.ai.config.CloudModelRegistry
import com.keisardev.insight.core.ai.config.CloudProvider
import com.keisardev.insight.core.common.di.AppScope
import com.keisardev.insight.core.data.datastore.UserSettings
import com.keisardev.insight.core.data.datastore.UserSettingsRepository
import com.keisardev.insight.core.model.Category
import com.keisardev.insight.core.model.CloudModelOption
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Selects which AI backend to use: on-device (Llamatik) or cloud (Koog/OpenAI).
 */
enum class AiMode {
    /** Use on-device model via Llamatik. */
    LOCAL,
    /** Use cloud model via Koog/OpenAI. */
    CLOUD,
    /** Try local first, fall back to cloud if model unavailable. */
    AUTO,
}

/**
 * Strategy that delegates to [LlamatikAiService] or [KoogAiService]
 * based on the selected [AiMode] and model availability.
 *
 * The mode is persisted via Proto DataStore and survives app restarts.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class AiServiceStrategy(
    private val llamatikAiService: LlamatikAiService,
    private val koogAiService: KoogAiService,
    private val userSettingsRepository: UserSettingsRepository,
) : AiService {

    @Volatile var mode: AiMode = AiMode.AUTO
    @Volatile private var modeSynced = false

    private suspend fun syncModeIfNeeded() {
        if (modeSynced) return
        val settings = userSettingsRepository.observeSettings().first()
        mode = settings.aiMode.toAiMode()
        modeSynced = true
    }

    val isLocalAvailable: Boolean
        get() = llamatikAiService.isEnabled

    val isCloudAvailable: Boolean
        get() = koogAiService.isEnabled

    val hasDevKey: Boolean
        get() = koogAiService.hasDevKey

    fun observeAiMode(): Flow<AiMode> =
        userSettingsRepository.observeSettings().map { it.aiMode.toAiMode() }

    suspend fun setMode(newMode: AiMode) {
        mode = newMode
        userSettingsRepository.updateAiMode(newMode.toProto())
    }

    fun getAvailableCloudModels(providerName: String): List<CloudModelOption> {
        val provider = try {
            CloudProvider.valueOf(providerName)
        } catch (_: IllegalArgumentException) {
            CloudProvider.OPENAI
        }
        return CloudModelRegistry.modelsForProvider(provider)
    }

    suspend fun refreshCloudConfig() {
        modeSynced = false
    }

    private val activeService: AiService
        get() = when (mode) {
            AiMode.LOCAL -> llamatikAiService
            AiMode.CLOUD -> koogAiService
            AiMode.AUTO -> if (llamatikAiService.isEnabled) llamatikAiService else koogAiService
        }

    override val isEnabled: Boolean
        get() = activeService.isEnabled

    override suspend fun suggestCategory(
        description: String,
        availableCategories: List<Category>,
    ): Category? {
        syncModeIfNeeded()
        return activeService.suggestCategory(description, availableCategories)
    }

    override suspend fun chat(
        message: String,
        history: List<ChatMessage>,
    ): String {
        syncModeIfNeeded()
        return activeService.chat(message, history)
    }

    override fun chatStream(
        message: String,
        history: List<ChatMessage>,
    ): Flow<String> = flow {
        syncModeIfNeeded()
        activeService.chatStream(message, history).collect { emit(it) }
    }
}

private fun UserSettings.AiModeProto.toAiMode(): AiMode = when (this) {
    UserSettings.AiModeProto.LOCAL -> AiMode.LOCAL
    UserSettings.AiModeProto.CLOUD -> AiMode.CLOUD
    UserSettings.AiModeProto.AUTO -> AiMode.AUTO
}

private fun AiMode.toProto(): UserSettings.AiModeProto = when (this) {
    AiMode.LOCAL -> UserSettings.AiModeProto.LOCAL
    AiMode.CLOUD -> UserSettings.AiModeProto.CLOUD
    AiMode.AUTO -> UserSettings.AiModeProto.AUTO
}
