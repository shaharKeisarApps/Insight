package com.keisardev.insight.di

import com.keisardev.insight.BuildConfig
import com.keisardev.insight.core.ai.config.AiConfig
import com.keisardev.insight.core.common.di.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Implementation of [AiConfig] that reads the API key from BuildConfig.
 * The API key is injected at build time from local.properties.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class AiConfigImpl : AiConfig {

    override val isAiEnabled: Boolean
        get() = BuildConfig.OPENAI_API_KEY.isNotBlank()

    override val openAiApiKey: String?
        get() = BuildConfig.OPENAI_API_KEY.takeIf { it.isNotBlank() }
}
