package com.keisardev.insight.core.ai.service

import com.keisardev.insight.core.common.di.AppScope
import com.keisardev.insight.core.model.Category
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * No-op cloud AI service for iOS.
 * Koog's reflection-based tool API is JVM-only.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class IosCloudAiService : CloudAiService {
    override val hasDevKey: Boolean = false
    override val isEnabled: Boolean = false

    override suspend fun suggestCategory(
        description: String,
        availableCategories: List<Category>,
    ): Category? = null

    override suspend fun chat(
        message: String,
        history: List<ChatMessage>,
    ): String = "Cloud AI is not available on this platform."

    override fun chatStream(
        message: String,
        history: List<ChatMessage>,
    ): Flow<String> = emptyFlow()
}
