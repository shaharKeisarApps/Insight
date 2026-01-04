package com.keisardev.metroditest.screens

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
import androidx.compose.ui.unit.dp
import com.keisardev.metroditest.data.repository.ExpenseRepository
import com.keisardev.metroditest.di.AppScope
import com.slack.circuit.codegen.annotations.CircuitInject
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
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data object OnClearDataClick : Event
        data object OnClearDataConfirm : Event
        data object OnClearDataDismiss : Event
    }
}

@CircuitInject(SettingsScreen::class, AppScope::class)
class SettingsPresenter @Inject constructor(
    private val expenseRepository: ExpenseRepository,
) : Presenter<SettingsScreen.State> {

    @Composable
    override fun present(): SettingsScreen.State {
        var showConfirmation by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        return SettingsScreen.State(
            showClearDataConfirmation = showConfirmation,
        ) { event ->
            when (event) {
                SettingsScreen.Event.OnClearDataClick -> {
                    showConfirmation = true
                }
                SettingsScreen.Event.OnClearDataConfirm -> {
                    scope.launch {
                        expenseRepository.deleteAllExpenses()
                        showConfirmation = false
                    }
                }
                SettingsScreen.Event.OnClearDataDismiss -> {
                    showConfirmation = false
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

            Card(modifier = Modifier.fillMaxWidth()) {
                SettingsItem(
                    icon = Icons.Default.Delete,
                    title = "Clear All Data",
                    subtitle = "Delete all expenses",
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
                            snackbarHostState.showSnackbar("Expense Tracker MVP")
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

    // Clear data confirmation dialog
    if (state.showClearDataConfirmation) {
        AlertDialog(
            onDismissRequest = { state.eventSink(SettingsScreen.Event.OnClearDataDismiss) },
            title = { Text("Clear All Data?") },
            text = { Text("This will delete all your expenses. This action cannot be undone.") },
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
