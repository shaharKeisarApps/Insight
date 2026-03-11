package com.keisardev.insight.core.designsystem.theme

import androidx.compose.runtime.Composable

@Composable
expect fun InsightTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
)
