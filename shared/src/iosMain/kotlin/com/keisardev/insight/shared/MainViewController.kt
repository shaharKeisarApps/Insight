package com.keisardev.insight.shared

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.ComposeUIViewController
import com.keisardev.insight.core.designsystem.theme.InsightTheme
import com.keisardev.insight.feature.aichat.AiChatScreen
import com.keisardev.insight.feature.expenses.ExpensesScreen
import com.keisardev.insight.feature.income.IncomeScreen
import com.keisardev.insight.feature.reports.ReportsScreen
import com.keisardev.insight.feature.settings.SettingsScreen
import com.keisardev.insight.shared.di.IosAppGraph
import com.slack.circuit.backstack.rememberSaveableBackStack
import com.slack.circuit.foundation.CircuitCompositionLocals
import com.slack.circuit.foundation.NavigableCircuitContent
import com.slack.circuit.foundation.rememberCircuitNavigator
import com.slack.circuit.runtime.resetRoot
import com.slack.circuit.runtime.screen.Screen
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

private val appGraph: IosAppGraph by lazy {
    createGraphFactory<IosAppGraph.Factory>().create().also { graph ->
        // Seed default categories on first launch
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            graph.categoryRepository.seedDefaultCategories()
            graph.incomeCategoryRepository.seedDefaultCategories()
        }
    }
}

fun MainViewController(): UIViewController = ComposeUIViewController {
    InsightTheme {
        CircuitCompositionLocals(appGraph.circuit) {
            IosMainContent()
        }
    }
}

@Composable
private fun IosMainContent() {
    val backStack = rememberSaveableBackStack(root = ExpensesScreen)
    val navigator = rememberCircuitNavigator(backStack) { }

    val currentDestination by remember {
        derivedStateOf {
            val rootScreen = backStack.firstOrNull()?.screen
            IosAppDestinations.entries.find { it.screen::class == rootScreen?.let { s -> s::class } }
                ?: IosAppDestinations.EXPENSES
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.systemBars,
        bottomBar = {
            NavigationBar {
                IosAppDestinations.entries.forEach { destination ->
                    NavigationBarItem(
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                        selected = destination == currentDestination,
                        onClick = {
                            navigator.resetRoot(
                                newRoot = destination.screen,
                                saveState = { true },
                                restoreState = { true },
                            )
                        },
                    )
                }
            }
        },
    ) { paddingValues ->
        NavigableCircuitContent(
            navigator = navigator,
            backStack = backStack,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        )
    }
}

private enum class IosAppDestinations(
    val label: String,
    val icon: ImageVector,
    val screen: Screen,
) {
    EXPENSES("Expenses", Icons.Default.Receipt, ExpensesScreen),
    INCOME("Income", Icons.Default.AccountBalanceWallet, IncomeScreen),
    REPORTS("Reports", Icons.Default.BarChart, ReportsScreen),
    AI_CHAT("AI Chat", Icons.Default.Psychology, AiChatScreen),
    SETTINGS("Settings", Icons.Default.Settings, SettingsScreen),
}
