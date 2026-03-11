package com.keisardev.insight.core.ui.util

import kotlin.math.roundToInt

fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> {
            val gb = bytes / 1_000_000_000.0
            "${((gb * 10).roundToInt() / 10.0)} GB"
        }
        bytes >= 1_000_000 -> "${(bytes / 1_000_000.0).roundToInt()} MB"
        bytes >= 1_000 -> "${(bytes / 1_000.0).roundToInt()} KB"
        else -> "$bytes B"
    }
}
