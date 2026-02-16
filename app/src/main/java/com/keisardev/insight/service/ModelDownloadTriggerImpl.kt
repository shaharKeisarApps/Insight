package com.keisardev.insight.service

import android.app.Application
import android.content.Intent
import com.keisardev.insight.core.ai.model.ModelDownloadTrigger
import com.keisardev.insight.core.common.di.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class ModelDownloadTriggerImpl(
    private val application: Application,
) : ModelDownloadTrigger {

    override fun startDownloadService() {
        val intent = Intent(application, ModelDownloadService::class.java)
        application.startForegroundService(intent)
    }

    override fun stopDownloadService() {
        val intent = Intent(application, ModelDownloadService::class.java)
        application.stopService(intent)
    }
}
