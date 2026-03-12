package com.keisardev.insight.shared.di

import com.keisardev.insight.core.ai.model.ModelDownloadTrigger

/**
 * iOS no-op implementation of [ModelDownloadTrigger].
 *
 * iOS has no foreground service requirement — downloads run in-process via Ktor.
 */
class IosModelDownloadTrigger : ModelDownloadTrigger {
    override fun startDownloadService() {} // No-op on iOS
    override fun stopDownloadService() {} // No-op on iOS
}
