package com.keisardev.insight.core.model

sealed interface ModelState {
    data object NotInstalled : ModelState
    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : ModelState
    data class Ready(
        val modelName: String,
        val filePath: String,
        val sizeBytes: Long,
    ) : ModelState
    data class Error(val message: String) : ModelState
}
