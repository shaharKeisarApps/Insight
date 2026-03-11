package com.keisardev.insight.feature.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
actual fun AiSettingsSection(
    state: SettingsScreen.State,
    modifier: Modifier,
) {
    // On-device AI and cloud AI configuration are not available on iOS.
    Card(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "AI features are not available on this platform.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}
