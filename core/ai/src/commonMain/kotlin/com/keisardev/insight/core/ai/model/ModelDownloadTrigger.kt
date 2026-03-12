package com.keisardev.insight.core.ai.model

interface ModelDownloadTrigger {
    fun startDownloadService()
    fun stopDownloadService()
}
