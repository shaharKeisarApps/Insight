package com.keisardev.metroditest

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.keisardev.metroditest.di.ActivityKey
import com.keisardev.metroditest.di.AppScope
import com.keisardev.metroditest.screens.FavoritesScreen
import com.keisardev.metroditest.screens.HomeScreen
import com.keisardev.metroditest.screens.ProfileScreen
import com.keisardev.metroditest.ui.theme.MetroDITestTheme
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
        enableEdgeToEdge()

        setContent {
            MetroDITestTheme {
                CircuitCompositionLocals(circuit) {
                    MainContent()
                }
            }
        }
    }
}

@Composable
fun MainContent() {
    // Create a saveable back stack that persists across config changes and process death
    val backStack = rememberSaveableBackStack(root = HomeScreen)
    val navigator = rememberCircuitNavigator(backStack) {
        // Called when the root screen is popped - typically exit the app
    }

    // Derive current destination from the back stack's root screen
    val currentDestination by remember {
        derivedStateOf {
            // Find which destination matches the root of the current back stack
            val rootScreen = backStack.firstOrNull()?.screen
            AppDestinations.entries.find { it.screen::class == rootScreen?.let { s -> s::class } }
                ?: AppDestinations.HOME
        }
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach { destination ->
                item(
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
        // NavigableCircuitContent handles the back stack navigation and transitions
        NavigableCircuitContent(
            navigator = navigator,
            backStack = backStack,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
    val screen: Screen,
) {
    HOME("Home", Icons.Default.Home, HomeScreen),
    FAVORITES("Favorites", Icons.Default.Favorite, FavoritesScreen),
    PROFILE("Profile", Icons.Default.AccountBox, ProfileScreen),
}
