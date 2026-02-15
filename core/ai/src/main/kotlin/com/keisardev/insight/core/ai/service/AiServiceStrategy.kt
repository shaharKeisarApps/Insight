package com.keisardev.insight.core.ai.service

import com.keisardev.insight.core.common.di.AppScope
import com.keisardev.insight.core.model.Category
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

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
 * Defaults to [AiMode.AUTO]: prefers on-device inference, falls back to cloud.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class AiServiceStrategy(
    private val llamatikAiService: LlamatikAiService,
    private val koogAiService: KoogAiService,
) : AiService {

    var mode: AiMode = AiMode.AUTO

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
    ): Category? = activeService.suggestCategory(description, availableCategories)

    override suspend fun chat(
        message: String,
        history: List<ChatMessage>,
    ): String = activeService.chat(message, history)
}
