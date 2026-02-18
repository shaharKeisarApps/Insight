package com.keisardev.insight.core.model

data class InstalledModel(
    val fileName: String,
    val displayName: String,
    val filePath: String,
    val sizeBytes: Long,
    val isActive: Boolean,
)

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
        val installedModels: List<InstalledModel> = emptyList(),
        val activeModelFileName: String = "",
    ) : ModelState
    data class Error(val message: String) : ModelState
}
