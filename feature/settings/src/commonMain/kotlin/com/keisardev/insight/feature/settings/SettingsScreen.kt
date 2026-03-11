package com.keisardev.insight.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import com.keisardev.insight.feature.settings.generated.resources.Res
import com.keisardev.insight.feature.settings.generated.resources.*
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.keisardev.insight.core.common.di.AppScope
import com.keisardev.insight.core.designsystem.theme.InsightTheme
import com.keisardev.insight.core.model.CloudModelOption
import com.keisardev.insight.core.model.ModelInfo
import com.keisardev.insight.core.model.ModelState
import com.keisardev.insight.core.ui.util.AVAILABLE_CURRENCIES
import com.keisardev.insight.core.ui.util.currencyDisplayName
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import kotlinx.coroutines.launch
import com.keisardev.insight.core.common.parcelize.Parcelize
import com.keisardev.insight.core.common.parcelize.Parcelable

/**
 * AI mode selection for the settings screen.
 * This mirrors [com.keisardev.insight.core.ai.service.AiMode] but lives in commonMain
 * so it can be referenced from [SettingsScreen.State] on all platforms.
 */
enum class AiMode {
    /** Use on-device model via Llamatik. */
    LOCAL,
    /** Use cloud model via Koog/OpenAI. */
    CLOUD,
    /** Try local first, fall back to cloud if model unavailable. */
    AUTO,
}

@Parcelize
data object SettingsScreen : Screen, Parcelable {
    data class State(
        val showClearDataConfirmation: Boolean,
        val aiMode: AiMode,
        val isLocalModelAvailable: Boolean,
        val isCloudAvailable: Boolean,
        val modelState: ModelState,
        val showModelSetup: Boolean,
        val availableModels: List<ModelInfo>,
        val searchResults: List<ModelInfo>,
        val isSearching: Boolean,
        val searchQuery: String,
        val showModelSelection: Boolean,
        val currencyCode: String,
        val showCurrencyPicker: Boolean,
        val categoryCount: Int,
        val showCloudSetup: Boolean,
        val cloudProvider: String,
        val cloudApiKey: String,
        val cloudModelId: String,
        val cloudModels: List<CloudModelOption>,
        val isDevKeyAvailable: Boolean,
        val useDevKey: Boolean,
        val getModelsForProvider: (String) -> List<CloudModelOption>,
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data object OnClearDataClick : Event
        data object OnClearDataConfirm : Event
        data object OnClearDataDismiss : Event
        data class OnAiModeChange(val mode: AiMode) : Event
        data object OnShowModelSetup : Event
        data object OnDismissModelSetup : Event
        data class OnDownloadModel(val model: ModelInfo) : Event
        data object OnCancelDownload : Event
        data class OnSearchQueryChange(val query: String) : Event
        data object OnSearch : Event
        data class OnDeleteModel(val fileName: String) : Event
        data object OnChangeModel : Event
        data class OnSelectActiveModel(val fileName: String) : Event
        data object OnCurrencyClick : Event
        data class OnCurrencySelect(val currencyCode: String) : Event
        data object OnDismissCurrencyPicker : Event
        data object OnShowCloudSetup : Event
        data object OnDismissCloudSetup : Event
        data class OnSaveCloudSettings(
            val provider: String,
            val apiKey: String,
            val modelId: String,
        ) : Event
        data object OnActivateDevKey : Event
    }
}

/**
 * Platform-specific composable for the AI engine settings section.
 *
 * On Android, this renders the full AI settings UI including [AiEngineCard],
 * [ModelSetupBanner], [ModelSetupBottomSheet], and [CloudSetupBottomSheet].
 *
 * On iOS, this renders a placeholder since on-device AI is not available.
 */
@Composable
expect fun AiSettingsSection(
    state: SettingsScreen.State,
    modifier: Modifier,
)

@CircuitInject(SettingsScreen::class, AppScope::class)
@Composable
fun SettingsUi(state: SettingsScreen.State, modifier: Modifier = Modifier) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(Res.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsItem(
                        icon = Icons.Default.Category,
                        title = stringResource(Res.string.settings_manage_categories),
                        subtitle = stringResource(Res.string.settings_categories_count, state.categoryCount),
                        onClick = {
                            scope.launch {
                                snackbarHostState.showSnackbar(getString(Res.string.settings_coming_soon))
                            }
                        },
                    )
                    HorizontalDivider()
                    SettingsItem(
                        icon = Icons.Default.AttachMoney,
                        title = stringResource(Res.string.settings_currency),
                        subtitle = currencyDisplayName(state.currencyCode),
                        onClick = { state.eventSink(SettingsScreen.Event.OnCurrencyClick) },
                    )
                    HorizontalDivider()
                    SettingsItem(
                        icon = Icons.Default.CurrencyExchange,
                        title = stringResource(Res.string.settings_multi_currency),
                        subtitle = stringResource(Res.string.settings_multi_currency_subtitle),
                        onClick = {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    getString(Res.string.settings_multi_currency_snackbar)
                                )
                            }
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            AiSettingsSection(
                state = state,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                SettingsItem(
                    icon = Icons.Default.Delete,
                    title = stringResource(Res.string.settings_clear_data_title),
                    subtitle = stringResource(Res.string.settings_clear_data_subtitle),
                    onClick = { state.eventSink(SettingsScreen.Event.OnClearDataClick) },
                    isDestructive = true,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = stringResource(Res.string.settings_about),
                    subtitle = stringResource(Res.string.settings_version),
                    onClick = {
                        scope.launch {
                            snackbarHostState.showSnackbar(getString(Res.string.settings_version_snackbar))
                        }
                    },
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(Res.string.settings_built_with),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (state.showClearDataConfirmation) {
        AlertDialog(
            onDismissRequest = { state.eventSink(SettingsScreen.Event.OnClearDataDismiss) },
            title = { Text(stringResource(Res.string.settings_clear_data_dialog_title)) },
            text = { Text(stringResource(Res.string.settings_clear_data_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = { state.eventSink(SettingsScreen.Event.OnClearDataConfirm) }
                ) {
                    Text(stringResource(Res.string.settings_clear_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { state.eventSink(SettingsScreen.Event.OnClearDataDismiss) }) {
                    Text(stringResource(Res.string.settings_cancel))
                }
            },
        )
    }

    // Currency picker dialog
    if (state.showCurrencyPicker) {
        AlertDialog(
            onDismissRequest = { state.eventSink(SettingsScreen.Event.OnDismissCurrencyPicker) },
            title = { Text(stringResource(Res.string.settings_select_currency)) },
            text = {
                Column {
                    AVAILABLE_CURRENCIES.forEach { code ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    state.eventSink(SettingsScreen.Event.OnCurrencySelect(code))
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = currencyDisplayName(code),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (code == state.currencyCode) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.weight(1f),
                            )
                            if (code == state.currencyCode) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = stringResource(Res.string.settings_selected),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { state.eventSink(SettingsScreen.Event.OnDismissCurrencyPicker) },
                ) {
                    Text(stringResource(Res.string.settings_cancel))
                }
            },
        )
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isDestructive) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(24.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isDestructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview
@Composable
private fun PreviewSettingsUi() {
    InsightTheme {
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
                categoryCount = 14,
                showCloudSetup = false,
                cloudProvider = "OPENAI",
                cloudApiKey = "",
                cloudModelId = "gpt-4o-mini",
                cloudModels = listOf(
                    CloudModelOption("gpt-4o-mini", "GPT-4o Mini", "Fast and affordable"),
                ),
                isDevKeyAvailable = false,
                useDevKey = false,
                getModelsForProvider = { emptyList() },
                eventSink = {},
            )
        )
    }
}

@Preview
@Composable
private fun PreviewSettingsUiWithDialog() {
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
                categoryCount = 14,
                showCloudSetup = false,
                cloudProvider = "OPENAI",
                cloudApiKey = "sk-test123",
                cloudModelId = "gpt-4o-mini",
                cloudModels = emptyList(),
                isDevKeyAvailable = false,
                useDevKey = false,
                getModelsForProvider = { emptyList() },
                eventSink = {},
            )
        )
    }
}
