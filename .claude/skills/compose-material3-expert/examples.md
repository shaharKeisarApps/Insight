# Material 3 Implementation Examples (Compose Multiplatform 1.10.0)

Production-quality examples using Metro DI. All code is Kotlin Multiplatform (commonMain) unless noted.

---

## 1. Custom App Theme with Dynamic Color (KMP)

Full `MaterialTheme` wrapper supporting dark/light mode, dynamic color on Android with `expect`/`actual`, and custom typography with a Google Font.

```kotlin
package com.example.designsystem.theme

// -- commonMain/Color.kt --

import androidx.compose.ui.graphics.Color

// Light scheme
val md_theme_light_primary = Color(0xFF6750A4)
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer = Color(0xFFEADDFF)
val md_theme_light_onPrimaryContainer = Color(0xFF21005D)
val md_theme_light_secondary = Color(0xFF625B71)
val md_theme_light_onSecondary = Color(0xFFFFFFFF)
val md_theme_light_secondaryContainer = Color(0xFFE8DEF8)
val md_theme_light_onSecondaryContainer = Color(0xFF1D192B)
val md_theme_light_tertiary = Color(0xFF7D5260)
val md_theme_light_onTertiary = Color(0xFFFFFFFF)
val md_theme_light_tertiaryContainer = Color(0xFFFFD8E4)
val md_theme_light_onTertiaryContainer = Color(0xFF31111D)
val md_theme_light_error = Color(0xFFB3261E)
val md_theme_light_onError = Color(0xFFFFFFFF)
val md_theme_light_errorContainer = Color(0xFFF9DEDC)
val md_theme_light_onErrorContainer = Color(0xFF410E0B)
val md_theme_light_surface = Color(0xFFFFFBFE)
val md_theme_light_onSurface = Color(0xFF1C1B1F)
val md_theme_light_surfaceVariant = Color(0xFFE7E0EC)
val md_theme_light_onSurfaceVariant = Color(0xFF49454F)
val md_theme_light_outline = Color(0xFF79747E)
val md_theme_light_outlineVariant = Color(0xFFCAC4D0)
val md_theme_light_surfaceContainer = Color(0xFFF3EDF7)

// Dark scheme
val md_theme_dark_primary = Color(0xFFD0BCFF)
val md_theme_dark_onPrimary = Color(0xFF381E72)
val md_theme_dark_primaryContainer = Color(0xFF4F378B)
val md_theme_dark_onPrimaryContainer = Color(0xFFEADDFF)
val md_theme_dark_secondary = Color(0xFFCCC2DC)
val md_theme_dark_onSecondary = Color(0xFF332D41)
val md_theme_dark_secondaryContainer = Color(0xFF4A4458)
val md_theme_dark_onSecondaryContainer = Color(0xFFE8DEF8)
val md_theme_dark_tertiary = Color(0xFFEFB8C8)
val md_theme_dark_onTertiary = Color(0xFF492532)
val md_theme_dark_tertiaryContainer = Color(0xFF633B48)
val md_theme_dark_onTertiaryContainer = Color(0xFFFFD8E4)
val md_theme_dark_error = Color(0xFFF2B8B5)
val md_theme_dark_onError = Color(0xFF601410)
val md_theme_dark_errorContainer = Color(0xFF8C1D18)
val md_theme_dark_onErrorContainer = Color(0xFFF9DEDC)
val md_theme_dark_surface = Color(0xFF1C1B1F)
val md_theme_dark_onSurface = Color(0xFFE6E1E5)
val md_theme_dark_surfaceVariant = Color(0xFF49454F)
val md_theme_dark_onSurfaceVariant = Color(0xFFCAC4D0)
val md_theme_dark_outline = Color(0xFF938F99)
val md_theme_dark_outlineVariant = Color(0xFF49454F)
val md_theme_dark_surfaceContainer = Color(0xFF211F26)

val AppLightColorScheme = lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,
    secondary = md_theme_light_secondary,
    onSecondary = md_theme_light_onSecondary,
    secondaryContainer = md_theme_light_secondaryContainer,
    onSecondaryContainer = md_theme_light_onSecondaryContainer,
    tertiary = md_theme_light_tertiary,
    onTertiary = md_theme_light_onTertiary,
    tertiaryContainer = md_theme_light_tertiaryContainer,
    onTertiaryContainer = md_theme_light_onTertiaryContainer,
    error = md_theme_light_error,
    onError = md_theme_light_onError,
    errorContainer = md_theme_light_errorContainer,
    onErrorContainer = md_theme_light_onErrorContainer,
    surface = md_theme_light_surface,
    onSurface = md_theme_light_onSurface,
    surfaceVariant = md_theme_light_surfaceVariant,
    onSurfaceVariant = md_theme_light_onSurfaceVariant,
    outline = md_theme_light_outline,
    outlineVariant = md_theme_light_outlineVariant,
    surfaceContainer = md_theme_light_surfaceContainer,
)

val AppDarkColorScheme = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    error = md_theme_dark_error,
    onError = md_theme_dark_onError,
    errorContainer = md_theme_dark_errorContainer,
    onErrorContainer = md_theme_dark_onErrorContainer,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    outline = md_theme_dark_outline,
    outlineVariant = md_theme_dark_outlineVariant,
    surfaceContainer = md_theme_dark_surfaceContainer,
)
```

```kotlin
// -- commonMain/Type.kt --

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val AppTypography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp,
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)
```

```kotlin
// -- commonMain/Shape.kt --

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
```

```kotlin
// -- commonMain/Theme.kt --

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

// expect/actual for dynamic color support
@Composable
expect fun platformColorScheme(useDarkTheme: Boolean): ColorScheme

@Composable
fun AppTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = platformColorScheme(useDarkTheme)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
```

```kotlin
// -- androidMain/Theme.android.kt --

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun platformColorScheme(useDarkTheme: Boolean): ColorScheme {
    val context = LocalContext.current
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && useDarkTheme ->
            dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !useDarkTheme ->
            dynamicLightColorScheme(context)
        useDarkTheme -> AppDarkColorScheme
        else -> AppLightColorScheme
    }
}
```

```kotlin
// -- iosMain/Theme.ios.kt (also desktopMain, wasmJsMain) --

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

@Composable
actual fun platformColorScheme(useDarkTheme: Boolean): ColorScheme {
    return if (useDarkTheme) AppDarkColorScheme else AppLightColorScheme
}
```

---

## 2. Responsive Scaffold with Adaptive Navigation

Scaffold using `TopAppBar` with scroll behavior. Switches between `NavigationBar` (compact), `NavigationRail` (medium), and `PermanentNavigationDrawer` (expanded) based on `WindowSizeClass`.

```kotlin
package com.example.feature.shell

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll

data class NavDestination(
    val label: String,
    val icon: ImageVector,
    val route: String,
)

val destinations = listOf(
    NavDestination("Home", Icons.Default.Home, "home"),
    NavDestination("Search", Icons.Default.Search, "search"),
    NavDestination("Profile", Icons.Default.Person, "profile"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(
    widthSizeClass: WindowWidthSizeClass,
    content: @Composable (selectedRoute: String) -> Unit,
) {
    var selectedIndex by remember { mutableStateOf(0) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> {
            // Phone: Bottom NavigationBar
            Scaffold(
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                topBar = {
                    LargeTopAppBar(
                        title = { Text(destinations[selectedIndex].label) },
                        scrollBehavior = scrollBehavior,
                        actions = {
                            IconButton(onClick = { /* settings */ }) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings")
                            }
                        },
                    )
                },
                bottomBar = {
                    NavigationBar {
                        destinations.forEachIndexed { index, dest ->
                            NavigationBarItem(
                                selected = selectedIndex == index,
                                onClick = { selectedIndex = index },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label) },
                            )
                        }
                    }
                },
            ) { padding ->
                content(destinations[selectedIndex].route)
            }
        }

        WindowWidthSizeClass.Medium -> {
            // Tablet portrait: NavigationRail + content
            Row(Modifier.fillMaxSize()) {
                NavigationRail {
                    destinations.forEachIndexed { index, dest ->
                        NavigationRailItem(
                            selected = selectedIndex == index,
                            onClick = { selectedIndex = index },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) },
                        )
                    }
                }
                Scaffold(
                    modifier = Modifier
                        .weight(1f)
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    topBar = {
                        LargeTopAppBar(
                            title = { Text(destinations[selectedIndex].label) },
                            scrollBehavior = scrollBehavior,
                        )
                    },
                ) { padding ->
                    content(destinations[selectedIndex].route)
                }
            }
        }

        WindowWidthSizeClass.Expanded -> {
            // Desktop / tablet landscape: PermanentNavigationDrawer
            PermanentNavigationDrawer(
                drawerContent = {
                    destinations.forEachIndexed { index, dest ->
                        NavigationDrawerItem(
                            label = { Text(dest.label) },
                            selected = selectedIndex == index,
                            onClick = { selectedIndex = index },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                        )
                    }
                },
            ) {
                Scaffold(
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                    topBar = {
                        LargeTopAppBar(
                            title = { Text(destinations[selectedIndex].label) },
                            scrollBehavior = scrollBehavior,
                        )
                    },
                ) { padding ->
                    content(destinations[selectedIndex].route)
                }
            }
        }
    }
}
```

---

## 3. Form Screen with Validation

`OutlinedTextField` with validation, error states, supporting text, suffix icons, and proper button hierarchy (Filled for submit, TextButton for cancel).

```kotlin
package com.example.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import dev.zacsweers.metro.AppScope
import kotlinx.parcelize.Parcelize

@Parcelize
data object EditProfileScreen : Screen {
    data class State(
        val name: String,
        val email: String,
        val password: String,
        val nameError: String?,
        val emailError: String?,
        val passwordError: String?,
        val isPasswordVisible: Boolean,
        val isSubmitting: Boolean,
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data class NameChanged(val value: String) : Event
        data class EmailChanged(val value: String) : Event
        data class PasswordChanged(val value: String) : Event
        data object TogglePasswordVisibility : Event
        data object Submit : Event
        data object Cancel : Event
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@CircuitInject(EditProfileScreen::class, AppScope::class)
@Composable
fun EditProfileUi(state: EditProfileScreen.State, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text("Edit Profile") })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // Name field
            OutlinedTextField(
                value = state.name,
                onValueChange = { state.eventSink(EditProfileScreen.Event.NameChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Full Name") },
                placeholder = { Text("Enter your full name") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                trailingIcon = {
                    if (state.name.isNotEmpty()) {
                        IconButton(onClick = {
                            state.eventSink(EditProfileScreen.Event.NameChanged(""))
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                supportingText = {
                    if (state.nameError != null) {
                        Text(
                            text = state.nameError,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                isError = state.nameError != null,
                singleLine = true,
            )

            // Email field
            OutlinedTextField(
                value = state.email,
                onValueChange = { state.eventSink(EditProfileScreen.Event.EmailChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Email") },
                placeholder = { Text("you@example.com") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                supportingText = {
                    if (state.emailError != null) {
                        Text(
                            text = state.emailError,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        Text("We'll never share your email")
                    }
                },
                isError = state.emailError != null,
                singleLine = true,
            )

            // Password field with visibility toggle
            OutlinedTextField(
                value = state.password,
                onValueChange = { state.eventSink(EditProfileScreen.Event.PasswordChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Password") },
                trailingIcon = {
                    IconButton(onClick = {
                        state.eventSink(EditProfileScreen.Event.TogglePasswordVisibility)
                    }) {
                        Icon(
                            imageVector = if (state.isPasswordVisible) {
                                Icons.Default.VisibilityOff
                            } else {
                                Icons.Default.Visibility
                            },
                            contentDescription = if (state.isPasswordVisible) {
                                "Hide password"
                            } else {
                                "Show password"
                            },
                        )
                    }
                },
                supportingText = {
                    if (state.passwordError != null) {
                        Text(
                            text = state.passwordError,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        Text("At least 8 characters")
                    }
                },
                isError = state.passwordError != null,
                visualTransformation = if (state.isPasswordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                singleLine = true,
            )

            Spacer(Modifier.height(8.dp))

            // Button hierarchy: Filled for primary action, TextButton for cancel
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(
                    onClick = { state.eventSink(EditProfileScreen.Event.Cancel) },
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = { state.eventSink(EditProfileScreen.Event.Submit) },
                    enabled = !state.isSubmitting,
                ) {
                    Text(if (state.isSubmitting) "Saving..." else "Save")
                }
            }
        }
    }
}
```

---

## 4. Bottom Sheet Pattern with Circuit Overlay

`ModalBottomSheet` shown via Circuit's `BottomSheetOverlay` from a Presenter, with `ListItem` content inside.

```kotlin
package com.example.feature.sort

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.overlay.LocalOverlayHost
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuitx.overlays.BottomSheetOverlay
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

enum class SortOption(val label: String) {
    NEWEST("Newest first"),
    OLDEST("Oldest first"),
    NAME_ASC("Name A-Z"),
    NAME_DESC("Name Z-A"),
    POPULAR("Most popular"),
}

@Parcelize
data object ItemListScreen : Screen {
    data class State(
        val currentSort: SortOption,
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data object ShowSortPicker : Event
    }
}

@CircuitInject(ItemListScreen::class, AppScope::class)
@Inject
class ItemListPresenter : Presenter<ItemListScreen.State> {

    @Composable
    override fun present(): ItemListScreen.State {
        var currentSort by rememberRetained { mutableStateOf(SortOption.NEWEST) }
        val overlayHost = LocalOverlayHost.current
        val scope = rememberCoroutineScope()

        return ItemListScreen.State(
            currentSort = currentSort,
        ) { event ->
            when (event) {
                is ItemListScreen.Event.ShowSortPicker -> {
                    scope.launch {
                        val result = overlayHost.show(
                            BottomSheetOverlay(
                                model = SortPickerModel(
                                    options = SortOption.entries,
                                    selected = currentSort,
                                ),
                                onDismiss = { null },
                                skipPartiallyExpandedState = true,
                            ) { model, navigator ->
                                SortPickerContent(
                                    model = model,
                                    onSelect = { navigator.finish(it) },
                                )
                            }
                        )
                        if (result != null) {
                            currentSort = result
                        }
                    }
                }
            }
        }
    }
}

data class SortPickerModel(
    val options: List<SortOption>,
    val selected: SortOption,
)

@Composable
private fun SortPickerContent(
    model: SortPickerModel,
    onSelect: (SortOption) -> Unit,
) {
    Column(Modifier.padding(bottom = 16.dp)) {
        Text(
            text = "Sort by",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        model.options.forEach { option ->
            ListItem(
                headlineContent = { Text(option.label) },
                leadingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.Sort,
                        contentDescription = null,
                    )
                },
                trailingContent = {
                    if (option == model.selected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                modifier = Modifier.clickable { onSelect(option) },
            )
        }
    }
}
```

### Standalone ModalBottomSheet (Without Circuit Overlay)

For cases where Circuit overlays are not used:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandaloneBottomSheetExample() {
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Button(onClick = { showSheet = true }) {
        Text("Show Options")
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
        ) {
            Column(Modifier.padding(bottom = 32.dp)) {
                ListItem(
                    headlineContent = { Text("Share") },
                    modifier = Modifier.clickable { showSheet = false },
                )
                ListItem(
                    headlineContent = { Text("Copy link") },
                    modifier = Modifier.clickable { showSheet = false },
                )
                ListItem(
                    headlineContent = { Text("Delete") },
                    modifier = Modifier.clickable { showSheet = false },
                )
            }
        }
    }
}
```

---

## 5. Snackbar Integration

`SnackbarHostState` with `LaunchedEffect` to show messages from presenter events. Includes undo action handling.

```kotlin
package com.example.feature.inbox

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import dev.zacsweers.metro.AppScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

@Parcelize
data object InboxScreen : Screen {
    data class State(
        val messages: ImmutableList<Message>,
        val snackbarMessage: SnackbarEvent?,
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data class DeleteMessage(val id: String) : Event
        data class UndoDelete(val id: String) : Event
        data object SnackbarShown : Event
    }
}

data class SnackbarEvent(
    val message: String,
    val actionLabel: String? = null,
    val deletedId: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@CircuitInject(InboxScreen::class, AppScope::class)
@Composable
fun InboxUi(state: InboxScreen.State, modifier: Modifier = Modifier) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // React to snackbar events from the presenter
    LaunchedEffect(state.snackbarMessage) {
        val event = state.snackbarMessage ?: return@LaunchedEffect

        val result = snackbarHostState.showSnackbar(
            message = event.message,
            actionLabel = event.actionLabel,
            withDismissAction = true,
            duration = SnackbarDuration.Short,
        )

        when (result) {
            SnackbarResult.ActionPerformed -> {
                // Undo delete
                event.deletedId?.let {
                    state.eventSink(InboxScreen.Event.UndoDelete(it))
                }
            }
            SnackbarResult.Dismissed -> {
                // No action needed
            }
        }

        // Notify presenter the snackbar was consumed
        state.eventSink(InboxScreen.Event.SnackbarShown)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text("Inbox") })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            items(state.messages, key = { it.id }) { message ->
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { dismissValue ->
                        if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                            state.eventSink(InboxScreen.Event.DeleteMessage(message.id))
                            true
                        } else {
                            false
                        }
                    },
                )
                SwipeToDismissBox(
                    state = dismissState,
                    backgroundContent = { /* red background */ },
                ) {
                    MessageRow(message)
                }
            }
        }
    }
}
```

---

## 6. Card Grid with ElevatedCard

`LazyVerticalGrid` with `ElevatedCard` items showing proper content padding, typography tokens, and `tonalElevation`.

```kotlin
package com.example.feature.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import dev.zacsweers.metro.AppScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.parcelize.Parcelize

data class GalleryItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String,
)

@Parcelize
data object GalleryScreen : Screen {
    data class State(
        val items: ImmutableList<GalleryItem>,
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data class ItemClicked(val id: String) : Event
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@CircuitInject(GalleryScreen::class, AppScope::class)
@Composable
fun GalleryUi(state: GalleryScreen.State, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text("Gallery") })
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.items, key = { it.id }) { item ->
                GalleryCard(
                    item = item,
                    onClick = { state.eventSink(GalleryScreen.Event.ItemClicked(item.id)) },
                )
            }
        }
    }
}

@Composable
private fun GalleryCard(
    item: GalleryItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 2.dp,
        ),
    ) {
        // Image placeholder (use Coil AsyncImage in real code)
        // AsyncImage(
        //     model = item.imageUrl,
        //     contentDescription = item.title,
        //     modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        //     contentScale = ContentScale.Crop,
        // )

        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
```

---

## 7. Adaptive Navigation with WindowSizeClass

Full integration example that switches between `NavigationBar`, `NavigationRail`, and `PermanentNavigationDrawer` based on the current `WindowWidthSizeClass`. Uses Circuit for screen rendering.

```kotlin
package com.example.feature.adaptive

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.foundation.CircuitContent
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.screen.Screen
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.parcelize.Parcelize

// Tab definitions
enum class AppTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val screen: Screen,
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home, HomeTabScreen),
    SEARCH("Search", Icons.Filled.Search, Icons.Outlined.Search, SearchTabScreen),
    FAVORITES("Favorites", Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder, FavoritesTabScreen),
    PROFILE("Profile", Icons.Filled.Person, Icons.Outlined.Person, ProfileTabScreen),
}

@Parcelize data object HomeTabScreen : Screen
@Parcelize data object SearchTabScreen : Screen
@Parcelize data object FavoritesTabScreen : Screen
@Parcelize data object ProfileTabScreen : Screen

@Parcelize
data object AdaptiveShellScreen : Screen {
    data class State(
        val selectedTab: AppTab,
        val widthSizeClass: WindowWidthSizeClass,
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data class SelectTab(val tab: AppTab) : Event
    }
}

@AssistedInject
class AdaptiveShellPresenter(
    @Assisted private val navigator: Navigator,
    private val windowSizeProvider: WindowSizeProvider,
) : Presenter<AdaptiveShellScreen.State> {

    @CircuitInject(AdaptiveShellScreen::class, AppScope::class)
    @AssistedFactory
    fun interface Factory {
        fun create(navigator: Navigator): AdaptiveShellPresenter
    }

    @Composable
    override fun present(): AdaptiveShellScreen.State {
        val widthSizeClass = windowSizeProvider.currentWidthSizeClass()
        var selectedTab by rememberRetained { mutableStateOf(AppTab.HOME) }

        return AdaptiveShellScreen.State(
            selectedTab = selectedTab,
            widthSizeClass = widthSizeClass,
        ) { event ->
            when (event) {
                is AdaptiveShellScreen.Event.SelectTab -> {
                    selectedTab = event.tab
                }
            }
        }
    }
}

@CircuitInject(AdaptiveShellScreen::class, AppScope::class)
@Composable
fun AdaptiveShellUi(state: AdaptiveShellScreen.State, modifier: Modifier = Modifier) {
    when (state.widthSizeClass) {
        WindowWidthSizeClass.Compact -> CompactLayout(state, modifier)
        WindowWidthSizeClass.Medium -> MediumLayout(state, modifier)
        WindowWidthSizeClass.Expanded -> ExpandedLayout(state, modifier)
    }
}

@Composable
private fun CompactLayout(state: AdaptiveShellScreen.State, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = state.selectedTab == tab,
                        onClick = { state.eventSink(AdaptiveShellScreen.Event.SelectTab(tab)) },
                        icon = {
                            Icon(
                                imageVector = if (state.selectedTab == tab) tab.selectedIcon
                                else tab.unselectedIcon,
                                contentDescription = tab.label,
                            )
                        },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        CircuitContent(
            screen = state.selectedTab.screen,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
private fun MediumLayout(state: AdaptiveShellScreen.State, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxSize()) {
        NavigationRail {
            AppTab.entries.forEach { tab ->
                NavigationRailItem(
                    selected = state.selectedTab == tab,
                    onClick = { state.eventSink(AdaptiveShellScreen.Event.SelectTab(tab)) },
                    icon = {
                        Icon(
                            imageVector = if (state.selectedTab == tab) tab.selectedIcon
                            else tab.unselectedIcon,
                            contentDescription = tab.label,
                        )
                    },
                    label = { Text(tab.label) },
                )
            }
        }
        CircuitContent(
            screen = state.selectedTab.screen,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ExpandedLayout(state: AdaptiveShellScreen.State, modifier: Modifier = Modifier) {
    PermanentNavigationDrawer(
        modifier = modifier,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    text = "My App",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp),
                )
                AppTab.entries.forEach { tab ->
                    NavigationDrawerItem(
                        label = { Text(tab.label) },
                        selected = state.selectedTab == tab,
                        onClick = { state.eventSink(AdaptiveShellScreen.Event.SelectTab(tab)) },
                        icon = {
                            Icon(
                                imageVector = if (state.selectedTab == tab) tab.selectedIcon
                                else tab.unselectedIcon,
                                contentDescription = tab.label,
                            )
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                }
            }
        },
    ) {
        CircuitContent(
            screen = state.selectedTab.screen,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
```

---

## 8. Surface and Content Color Demonstration

Showing how `Surface` automatically provides correct `contentColor` for text/icon contrast.

```kotlin
package com.example.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun StatusBanner(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    // Surface with primaryContainer automatically sets contentColor to onPrimaryContainer
    // Text and Icon children automatically use onPrimaryContainer without explicit color
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Icon inherits onPrimaryContainer automatically
            Icon(Icons.Default.Star, contentDescription = null)
            Column {
                // Text inherits onPrimaryContainer automatically
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
fun ErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
) {
    // errorContainer -> onErrorContainer is automatic
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(12.dp),
            // No color parameter needed -- inherited from Surface's contentColor
        )
    }
}
```

---

## 9. TabRow with ScrollableTabRow

Content categories within a screen using tabs.

```kotlin
package com.example.feature.explore

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

val categories = listOf("For You", "Trending", "News", "Sports", "Tech", "Arts", "Science")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreTabsUi(modifier: Modifier = Modifier) {
    val pagerState = rememberPagerState(pageCount = { categories.size })
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text("Explore") })
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            // ScrollableTabRow for many categories
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
            ) {
                categories.forEachIndexed { index, category ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        text = { Text(category) },
                    )
                }
            }

            // HorizontalPager synced with tabs
            HorizontalPager(
                state = pagerState,
            ) { page ->
                // Content for each tab
                CategoryContent(category = categories[page])
            }
        }
    }
}

@Composable
private fun CategoryContent(category: String) {
    // Placeholder -- replace with actual content
    Text(
        text = "Content for $category",
        modifier = Modifier.padding(all = 16.dp),
    )
}
```

---

## 10. Metro DI Module for Theme Provider

Providing the app theme configuration via Metro DI so it can be injected into the Circuit composition setup.

```kotlin
package com.example.di

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

// Interface for theme configuration
interface ThemeConfig {
    val supportsDynamicColor: Boolean
}

// Interface for window size (KMP abstraction)
interface WindowSizeProvider {
    @Composable
    fun currentWidthSizeClass(): WindowWidthSizeClass
}

@ContributesTo(AppScope::class)
interface ThemeModule {
    companion object {
        @Provides
        @SingleIn(AppScope::class)
        fun provideThemeConfig(): ThemeConfig = object : ThemeConfig {
            override val supportsDynamicColor: Boolean = true
        }
    }
}

// Platform-specific WindowSizeProvider implementations use expect/actual
// androidMain:
//   @ContributesBinding(AppScope::class)
//   @Inject
//   class AndroidWindowSizeProvider(private val activity: Activity) : WindowSizeProvider { ... }
//
// desktopMain:
//   @ContributesBinding(AppScope::class)
//   @Inject
//   class DesktopWindowSizeProvider : WindowSizeProvider { ... }
```

---

## Common Patterns Quick Reference

### Correct color usage

```kotlin
// WRONG: Hardcoded colors
Text(text = "Hello", color = Color.Red)

// RIGHT: Theme tokens
Text(text = "Hello", color = MaterialTheme.colorScheme.error)
```

### Correct typography usage

```kotlin
// WRONG: Manual text styling
Text(text = "Title", fontSize = 22.sp, fontWeight = FontWeight.Normal)

// RIGHT: Theme tokens (includes fontSize, weight, lineHeight, letterSpacing)
Text(text = "Title", style = MaterialTheme.typography.titleLarge)
```

### Content color inheritance

```kotlin
// WRONG: Manually setting icon/text colors inside Surface
Surface(color = MaterialTheme.colorScheme.primaryContainer) {
    Icon(icon, tint = MaterialTheme.colorScheme.onPrimaryContainer) // Redundant
    Text(text, color = MaterialTheme.colorScheme.onPrimaryContainer) // Redundant
}

// RIGHT: Let Surface set LocalContentColor automatically
Surface(color = MaterialTheme.colorScheme.primaryContainer) {
    Icon(icon, contentDescription = null) // Uses onPrimaryContainer automatically
    Text(text) // Uses onPrimaryContainer automatically
}
```

### Scaffold padding

```kotlin
// WRONG: Ignoring padding from Scaffold
Scaffold(topBar = { TopAppBar(...) }) { _ ->
    LazyColumn { /* content renders behind top bar */ }
}

// RIGHT: Apply padding
Scaffold(topBar = { TopAppBar(...) }) { padding ->
    LazyColumn(Modifier.padding(padding)) { /* correct offset */ }
}
```

### Scroll behavior connection

```kotlin
// WRONG: Scroll behavior without nestedScroll
val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
Scaffold(topBar = { LargeTopAppBar(scrollBehavior = scrollBehavior) }) { ... }
// Top bar never collapses!

// RIGHT: Connect via nestedScroll modifier
val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
Scaffold(
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = { LargeTopAppBar(title = { Text("Title") }, scrollBehavior = scrollBehavior) },
) { padding ->
    LazyColumn(Modifier.padding(padding)) { /* scroll triggers collapse */ }
}
```
