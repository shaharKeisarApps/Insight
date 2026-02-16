package com.keisardev.insight.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.keisardev.insight.core.ai.service.AiMode
import com.keisardev.insight.core.designsystem.theme.InsightTheme
import com.keisardev.insight.core.model.ModelState

// ==================== DEFAULT STATE ====================

@PreviewTest
@Preview(showBackground = true, name = "Settings Default - Light")
@Composable
fun SettingsDefaultLightPreview() {
    InsightTheme(darkTheme = false) {
        SettingsUi(
            state = SettingsScreen.State(
                showClearDataConfirmation = false,
                aiMode = AiMode.AUTO,
                isLocalModelAvailable = true,
                isCloudAvailable = true,
                modelState = ModelState.Ready(
                    modelName = "SmolLM2 360M",
                    filePath = "",
                    sizeBytes = 387_000_000L,
                ),
                showModelSetup = false,
                availableModels = emptyList(),
                searchResults = emptyList(),
                isSearching = false,
                searchQuery = "",
                showModelSelection = false,
                currencyCode = "USD",
                showCurrencyPicker = false,
                eventSink = {},
            ),
        )
    }
}

@PreviewTest
@Preview(
    showBackground = true,
    name = "Settings Default - Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun SettingsDefaultDarkPreview() {
    InsightTheme(darkTheme = true) {
        SettingsUi(
            state = SettingsScreen.State(
                showClearDataConfirmation = false,
                aiMode = AiMode.AUTO,
                isLocalModelAvailable = true,
                isCloudAvailable = true,
                modelState = ModelState.Ready(
                    modelName = "SmolLM2 360M",
                    filePath = "",
                    sizeBytes = 387_000_000L,
                ),
                showModelSetup = false,
                availableModels = emptyList(),
                searchResults = emptyList(),
                isSearching = false,
                searchQuery = "",
                showModelSelection = false,
                currencyCode = "USD",
                showCurrencyPicker = false,
                eventSink = {},
            ),
        )
    }
}

// ==================== CLOUD MODE ====================

@PreviewTest
@Preview(showBackground = true, name = "Settings Cloud Mode")
@Composable
fun SettingsCloudModePreview() {
    InsightTheme {
        SettingsUi(
            state = SettingsScreen.State(
                showClearDataConfirmation = false,
                aiMode = AiMode.CLOUD,
                isLocalModelAvailable = false,
                isCloudAvailable = true,
                modelState = ModelState.NotInstalled,
                showModelSetup = false,
                availableModels = emptyList(),
                searchResults = emptyList(),
                isSearching = false,
                searchQuery = "",
                showModelSelection = false,
                currencyCode = "EUR",
                showCurrencyPicker = false,
                eventSink = {},
            ),
        )
    }
}

// ==================== LOCAL MODE - NO MODEL ====================

@PreviewTest
@Preview(showBackground = true, name = "Settings Local No Model")
@Composable
fun SettingsLocalNoModelPreview() {
    InsightTheme {
        SettingsUi(
            state = SettingsScreen.State(
                showClearDataConfirmation = false,
                aiMode = AiMode.LOCAL,
                isLocalModelAvailable = false,
                isCloudAvailable = false,
                modelState = ModelState.NotInstalled,
                showModelSetup = false,
                availableModels = emptyList(),
                searchResults = emptyList(),
                isSearching = false,
                searchQuery = "",
                showModelSelection = false,
                currencyCode = "USD",
                showCurrencyPicker = false,
                eventSink = {},
            ),
        )
    }
}

// ==================== CLEAR DATA DIALOG ====================

@PreviewTest
@Preview(showBackground = true, name = "Settings Clear Data Dialog")
@Composable
fun SettingsClearDataDialogPreview() {
    InsightTheme {
        SettingsUi(
            state = SettingsScreen.State(
                showClearDataConfirmation = true,
                aiMode = AiMode.CLOUD,
                isLocalModelAvailable = false,
                isCloudAvailable = true,
                modelState = ModelState.NotInstalled,
                showModelSetup = false,
                availableModels = emptyList(),
                searchResults = emptyList(),
                isSearching = false,
                searchQuery = "",
                showModelSelection = false,
                currencyCode = "USD",
                showCurrencyPicker = false,
                eventSink = {},
            ),
        )
    }
}
