package com.keisardev.insight.core.model

data class ModelInfo(
    val id: String,
    val name: String,
    val description: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val fileName: String,
)
