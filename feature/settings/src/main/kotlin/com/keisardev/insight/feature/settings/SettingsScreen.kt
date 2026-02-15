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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.keisardev.insight.core.ai.service.AiMode
import com.keisardev.insight.core.ai.service.AiServiceStrategy
import com.keisardev.insight.core.common.di.AppScope
import com.keisardev.insight.core.data.repository.ExpenseRepository
import com.keisardev.insight.core.data.repository.IncomeRepository
import com.keisardev.insight.core.designsystem.theme.InsightTheme
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.screen.Screen
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

@Parcelize
data object SettingsScreen : Screen {
    data class State(
        val showClearDataConfirmation: Boolean,
        val aiMode: AiMode,
        val isLocalModelAvailable: Boolean,
        val isCloudAvailable: Boolean,
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data object OnClearDataClick : Event
        data object OnClearDataConfirm : Event
        data object OnClearDataDismiss : Event
        data class OnAiModeChange(val mode: AiMode) : Event
    }
}

@CircuitInject(SettingsScreen::class, AppScope::class)
@Inject
class SettingsPresenter(
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val aiServiceStrategy: AiServiceStrategy,
) : Presenter<SettingsScreen.State> {

    @Composable
    override fun present(): SettingsScreen.State {
        var showConfirmation by rememberRetained { mutableStateOf(false) }
        var aiMode by rememberRetained { mutableStateOf(aiServiceStrategy.mode) }
        val scope = rememberCoroutineScope()

        return SettingsScreen.State(
            showClearDataConfirmation = showConfirmation,
            aiMode = aiMode,
            isLocalModelAvailable = aiServiceStrategy.isLocalAvailable,
            isCloudAvailable = aiServiceStrategy.isCloudAvailable,
        ) { event ->
            when (event) {
                SettingsScreen.Event.OnClearDataClick -> {
                    showConfirmation = true
                }
                SettingsScreen.Event.OnClearDataConfirm -> {
                    scope.launch {
                        expenseRepository.deleteAllExpenses()
                        incomeRepository.deleteAllIncome()
                        showConfirmation = false
                    }
                }
                SettingsScreen.Event.OnClearDataDismiss -> {
                    showConfirmation = false
                }
                is SettingsScreen.Event.OnAiModeChange -> {
                    aiMode = event.mode
                    aiServiceStrategy.mode = event.mode
                }
            }
        }
    }
}

@CircuitInject(SettingsScreen::class, AppScope::class)
@Composable
fun SettingsUi(state: SettingsScreen.State, modifier: Modifier = Modifier) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsItem(
                        icon = Icons.Default.Category,
                        title = "Manage Categories",
                        subtitle = "7 categories",
                        onClick = {
                            scope.launch {
                                snackbarHostState.showSnackbar("Coming soon")
                            }
                        },
                    )
                    HorizontalDivider()
                    SettingsItem(
                        icon = Icons.Default.AttachMoney,
                        title = "Currency",
                        subtitle = "USD ($)",
                        onClick = {
                            scope.launch {
                                snackbarHostState.showSnackbar("Coming soon")
                            }
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            AiEngineCard(
                aiMode = state.aiMode,
                isLocalAvailable = state.isLocalModelAvailable,
                isCloudAvailable = state.isCloudAvailable,
                onModeChange = { state.eventSink(SettingsScreen.Event.OnAiModeChange(it)) },
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                SettingsItem(
                    icon = Icons.Default.Delete,
                    title = "Clear All Data",
                    subtitle = "Delete all expenses and income",
                    onClick = { state.eventSink(SettingsScreen.Event.OnClearDataClick) },
                    isDestructive = true,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "About",
                    subtitle = "Version 1.0.0",
                    onClick = {
                        scope.launch {
                            snackbarHostState.showSnackbar("Insight v1.0.0")
                        }
                    },
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Built with Circuit + Metro DI",
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
            title = { Text("Clear All Data?") },
            text = { Text("This will delete all your expenses and income. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { state.eventSink(SettingsScreen.Event.OnClearDataConfirm) }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { state.eventSink(SettingsScreen.Event.OnClearDataDismiss) }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun AiEngineCard(
    aiMode: AiMode,
    isLocalAvailable: Boolean,
    isCloudAvailable: Boolean,
    onModeChange: (AiMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = "AI Engine",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth(),
            ) {
                val options = AiMode.entries
                options.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = aiMode == mode,
                        onClick = { onModeChange(mode) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = options.size,
                        ),
                        icon = {
                            SegmentedButtonDefaults.Icon(active = aiMode == mode) {
                                Icon(
                                    imageVector = when (mode) {
                                        AiMode.LOCAL -> Icons.Default.PhoneAndroid
                                        AiMode.CLOUD -> Icons.Default.Cloud
                                        AiMode.AUTO -> Icons.Default.SmartToy
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                                )
                            }
                        },
                    ) {
                        Text(
                            text = when (mode) {
                                AiMode.LOCAL -> "On-Device"
                                AiMode.CLOUD -> "Cloud"
                                AiMode.AUTO -> "Auto"
                            },
                        )
                    }
                }
            }

            // Status description
            val statusText = when (aiMode) {
                AiMode.LOCAL -> if (isLocalAvailable) {
                    "Using on-device model — no internet needed"
                } else {
                    "No local model installed — place a .gguf file in models/"
                }
                AiMode.CLOUD -> if (isCloudAvailable) {
                    "Using OpenAI cloud API"
                } else {
                    "Cloud API not configured — add API key to local.properties"
                }
                AiMode.AUTO -> when {
                    isLocalAvailable -> "On-device model active — cloud as fallback"
                    isCloudAvailable -> "Cloud API active — no local model found"
                    else -> "No AI backend available"
                }
            }

            val isWarning = when (aiMode) {
                AiMode.LOCAL -> !isLocalAvailable
                AiMode.CLOUD -> !isCloudAvailable
                AiMode.AUTO -> !isLocalAvailable && !isCloudAvailable
            }

            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isWarning) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
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

@Preview(showBackground = true)
@Composable
private fun PreviewSettingsUi() {
    InsightTheme {
        SettingsUi(
            state = SettingsScreen.State(
                showClearDataConfirmation = false,
                aiMode = AiMode.AUTO,
                isLocalModelAvailable = true,
                isCloudAvailable = true,
                eventSink = {},
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewSettingsUiWithDialog() {
    InsightTheme {
        SettingsUi(
            state = SettingsScreen.State(
                showClearDataConfirmation = true,
                aiMode = AiMode.CLOUD,
                isLocalModelAvailable = false,
                isCloudAvailable = true,
                eventSink = {},
            )
        )
    }
}
