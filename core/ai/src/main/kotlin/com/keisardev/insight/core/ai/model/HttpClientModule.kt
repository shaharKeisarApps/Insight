package com.keisardev.insight.core.ai.model

import com.keisardev.insight.core.common.di.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

@ContributesTo(AppScope::class)
interface HttpClientModule {
    companion object {
        @Provides
        @SingleIn(AppScope::class)
        fun provideHttpClient(): HttpClient = HttpClient(OkHttp)
    }
}
