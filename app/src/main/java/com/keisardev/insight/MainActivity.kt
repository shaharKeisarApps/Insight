package com.keisardev.insight

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.keisardev.insight.core.common.di.AppScope
import com.keisardev.insight.core.designsystem.theme.InsightTheme
import com.keisardev.insight.di.ActivityKey
import com.keisardev.insight.feature.aichat.AiChatScreen
import com.keisardev.insight.feature.expenses.ExpensesScreen
import com.keisardev.insight.feature.income.IncomeScreen
import com.keisardev.insight.feature.reports.ReportsScreen
import com.keisardev.insight.feature.settings.SettingsScreen
import com.slack.circuit.backstack.rememberSaveableBackStack
import com.slack.circuit.foundation.Circuit
import com.slack.circuit.foundation.CircuitCompositionLocals
import com.slack.circuit.foundation.NavigableCircuitContent
import com.slack.circuit.foundation.rememberCircuitNavigator
import com.slack.circuit.runtime.resetRoot
import com.slack.circuit.runtime.screen.Screen
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding

@ActivityKey(MainActivity::class)
@ContributesIntoMap(AppScope::class, binding = binding<Activity>())
@Inject
class MainActivity(
    private val circuit: Circuit,
) : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val darkTheme = isSystemInDarkTheme()

            // Enable edge-to-edge with theme-aware system bar styling (NowInAndroid pattern)
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                ) { darkTheme },
                navigationBarStyle = SystemBarStyle.auto(
                    LIGHT_SCRIM,
                    DARK_SCRIM,
                ) { darkTheme },
            )

            InsightTheme(darkTheme = darkTheme) {
                CircuitCompositionLocals(circuit) {
                    MainContent()
                }
            }
        }
    }

    companion object {
        // Scrim colors for navigation bar (from NowInAndroid)
        private val LIGHT_SCRIM = android.graphics.Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        private val DARK_SCRIM = android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}

@Composable
fun MainContent() {
    // Create a saveable back stack that persists across config changes and process death
    val backStack = rememberSaveableBackStack(root = ExpensesScreen)
    val navigator = rememberCircuitNavigator(backStack) {
        // Called when the root screen is popped - typically exit the app
    }

    // Derive current destination from the back stack's root screen
    val currentDestination by remember {
        derivedStateOf {
            // Find which destination matches the root of the current back stack
            val rootScreen = backStack.firstOrNull()?.screen
            AppDestinations.entries.find { it.screen::class == rootScreen?.let { s -> s::class } }
                ?: AppDestinations.EXPENSES
        }
    }

    NavigationSuiteScaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        navigationSuiteItems = {
            AppDestinations.entries.forEach { destination ->
                item(
                    modifier = Modifier.testTag("nav_${destination.name.lowercase()}"),
                    icon = {
                        Icon(
                            destination.icon,
                            contentDescription = destination.label,
                        )
                    },
                    label = { Text(destination.label) },
                    selected = destination == currentDestination,
                    onClick = {
                        // Use resetRoot with saveState/restoreState for multiple back stacks
                        // This saves the current back stack and restores the destination's back stack
                        navigator.resetRoot(
                            newRoot = destination.screen,
                            saveState = { true },
                            restoreState = { true },
                        )
                    },
                )
            }
        },
    ) {
        // SharedTransitionLayout enables shared element transitions between screens
        // Provides SharedTransitionScope to all nested screens
        SharedTransitionLayout {
            // NavigableCircuitContent handles the back stack navigation and transitions
            NavigableCircuitContent(
                navigator = navigator,
                backStack = backStack,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            )
        }
    }
}

enum class AppDestinations(
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
