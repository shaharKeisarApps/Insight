package com.keisardev.insight.feature.aichat.fakes

import com.keisardev.insight.core.ai.model.ModelDownloadTrigger

class FakeModelDownloadTrigger : ModelDownloadTrigger {

    var startCallCount = 0
        private set
    var stopCallCount = 0
        private set

    override fun startDownloadService() {
        startCallCount++
    }

    override fun stopDownloadService() {
        stopCallCount++
    }
}
