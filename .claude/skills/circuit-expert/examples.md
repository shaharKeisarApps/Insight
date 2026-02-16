# Circuit Implementation Examples (v0.32.0)

Production-ready examples based on CatchUp patterns and Circuit best practices.

---

## 1. Complete Screen (CatchUp Pattern)

Screen definition + `@AssistedInject` Presenter + `@CircuitInject` Factory + `@CircuitInject` UI. This is the standard pattern for every screen.

```kotlin
package com.example.feature.home

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.foundation.NavEvent
import com.slack.circuit.foundation.onNavEvent
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.screen.Screen
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.parcelize.Parcelize

// -- Screen definition --
@Parcelize
data object HomeScreen : Screen {
    data class State(
        val items: ImmutableList<Item>,
        val isLoading: Boolean,
        val eventSink: (Event) -> Unit = {},
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data object Refresh : Event
        data class ItemClicked(val id: String) : Event
        data class NestedNavEvent(val navEvent: NavEvent) : Event
    }
}

// -- Presenter with @AssistedInject for Navigator --
@AssistedInject
class HomePresenter(
    @Assisted private val navigator: Navigator,
    private val repo: ItemRepository,
) : Presenter<HomeScreen.State> {

    // @CircuitInject goes on the @AssistedFactory, NOT on the class
    @CircuitInject(HomeScreen::class, AppScope::class)
    @AssistedFactory
    fun interface Factory {
        fun create(navigator: Navigator): HomePresenter
    }

    @Composable
    override fun present(): HomeScreen.State {
        var items by rememberRetained { mutableStateOf(emptyList<Item>()) }
        var isLoading by rememberRetained { mutableStateOf(true) }

        LaunchedEffect(Unit) {
            items = repo.getItems()
            isLoading = false
        }

        return HomeScreen.State(
            items = items.toImmutableList(),
            isLoading = isLoading,
        ) { event ->
            when (event) {
                is HomeScreen.Event.ItemClicked -> navigator.goTo(DetailScreen(event.id))
                is HomeScreen.Event.Refresh -> {
                    isLoading = true
                    // Will re-trigger LaunchedEffect or use separate coroutine
                }
                is HomeScreen.Event.NestedNavEvent -> navigator.onNavEvent(event.navEvent)
            }
        }
    }
}

// -- UI with @CircuitInject directly on composable --
@CircuitInject(HomeScreen::class, AppScope::class)
@Composable
fun HomeUi(state: HomeScreen.State, modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier) { padding ->
        if (state.isLoading) {
            LoadingIndicator(Modifier.padding(padding))
        } else {
            LazyColumn(Modifier.padding(padding)) {
                items(state.items, key = { it.id }) { item ->
                    ItemRow(
                        item = item,
                        onClick = { state.eventSink(HomeScreen.Event.ItemClicked(item.id)) },
                    )
                }
            }
        }
    }
}
```

---

## 2. App Entry with Navigation (CatchUp MainActivity Pattern)

Full app setup with `CircuitCompositionLocals`, `ContentWithOverlays`, `rememberSaveableBackStack`, `rememberCircuitNavigator`, `NavigableCircuitContent`, and `GestureNavigationDecorationFactory`.

```kotlin
package com.example.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import com.slack.circuit.backstack.rememberSaveableBackStack
import com.slack.circuit.foundation.Circuit
import com.slack.circuit.foundation.CircuitCompositionLocals
import com.slack.circuit.foundation.NavigableCircuitContent
import com.slack.circuit.foundation.rememberCircuitNavigator
import com.slack.circuit.overlay.ContentWithOverlays
import com.slack.circuitx.gesturenavigation.GestureNavigationDecorationFactory
import dev.zacsweers.metro.Inject

@Inject
class MainActivity(
    private val circuit: Circuit,
) : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AppTheme {
                CircuitCompositionLocals(circuit) {
                    ContentWithOverlays {
                        val backStack = rememberSaveableBackStack(HomeScreen)
                        val navigator = rememberCircuitNavigator(
                            backStack = backStack,
                            onRootPop = ::finish,  // Finish activity when popping root
                        )

                        NavigableCircuitContent(
                            navigator = navigator,
                            backStack = backStack,
                            decoratorFactory = remember(navigator) {
                                GestureNavigationDecorationFactory(
                                    onBackInvoked = navigator::pop,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
```

---

## 3. Circuit Module with Metro (CatchUp CircuitModule Pattern)

DI module that provides the `Circuit` instance using Metro's `@ContributesTo`, `@Multibinds`, and `@Provides`.

```kotlin
package com.example.di

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.slack.circuit.foundation.Circuit
import com.slack.circuit.foundation.LocalCircuit
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.ui.Ui
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provides

@ContributesTo(AppScope::class)
interface CircuitModule {

    // Declare multibindings for factory sets
    @Multibinds fun presenterFactories(): Set<Presenter.Factory>
    @Multibinds fun uiFactories(): Set<Ui.Factory>

    companion object {

        @Provides
        fun provideCircuit(
            presenterFactories: Set<Presenter.Factory>,
            uiFactories: Set<Ui.Factory>,
        ): Circuit {
            return Circuit.Builder()
                .addPresenterFactories(presenterFactories)
                .addUiFactories(uiFactories)
                .setOnUnavailableContent { screen, modifier ->
                    // Debug screen for missing factories (dev builds only)
                    val circuit = LocalCircuit.current
                    Box(modifier.fillMaxSize().background(Color.Red.copy(alpha = 0.2f))) {
                        Column(Modifier.align(Alignment.Center)) {
                            BasicText(
                                text = "No factory for: $screen",
                                style = TextStyle(color = Color.Red),
                            )
                        }
                    }
                }
                .build()
        }
    }
}
```

---

## 4. Tab Navigation with resetRoot

> **Moved**: See [circuit-navigation-expert/examples.md](../circuit-navigation-expert/examples.md) Example 2 for a complete tab navigation implementation with `StateOptions.SaveAndRestore`.

Quick pattern:
```kotlin
navigator.resetRoot(
    newRoot = targetTab,
    options = Navigator.StateOptions.SaveAndRestore,
)
```

---

## 5. Overlay Usage (BottomSheetOverlay from circuitx-overlays)

Using `BottomSheetOverlay` from the circuitx-overlays library with `LocalOverlayHost`.

```kotlin
package com.example.feature.picker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
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

@Parcelize
data object FilterScreen : Screen {
    data class State(
        val selectedCategory: String?,
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data object PickCategory : Event
    }
}

// Presenter without Navigator: use @Inject + @CircuitInject directly on class
@CircuitInject(FilterScreen::class, AppScope::class)
@Inject
class FilterPresenter(
    private val categoryRepo: CategoryRepository,
) : Presenter<FilterScreen.State> {

    @Composable
    override fun present(): FilterScreen.State {
        var selectedCategory by rememberRetained { mutableStateOf<String?>(null) }
        val overlayHost = LocalOverlayHost.current
        val scope = rememberCoroutineScope()
        val categories = rememberRetained { categoryRepo.getCategories() }

        return FilterScreen.State(
            selectedCategory = selectedCategory,
        ) { event ->
            when (event) {
                is FilterScreen.Event.PickCategory -> {
                    scope.launch {
                        val result = overlayHost.show(
                            BottomSheetOverlay(
                                model = categories,
                                onDismiss = { null },  // Returns null if dismissed
                                skipPartiallyExpandedState = true,
                            ) { items, navigator ->
                                // Content inside the bottom sheet
                                LazyColumn {
                                    items(items) { category ->
                                        ListItem(
                                            headlineContent = { Text(category.name) },
                                            modifier = Modifier.clickable {
                                                navigator.finish(category.name)
                                            },
                                        )
                                    }
                                }
                            }
                        )
                        if (result != null) {
                            selectedCategory = result
                        }
                    }
                }
            }
        }
    }
}
```

---

## 6. Custom Overlay (AlertDialog with Typed Result)

Custom overlay using `Overlay<Result>` directly for a confirmation dialog.

```kotlin
package com.example.overlay

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.slack.circuit.overlay.Overlay
import com.slack.circuit.overlay.OverlayNavigator

// Custom overlay with a typed result
class ConfirmDeleteOverlay(
    private val itemName: String,
) : Overlay<ConfirmDeleteOverlay.Result> {

    enum class Result { CONFIRM, CANCEL }

    @Composable
    override fun Content(navigator: OverlayNavigator<Result>) {
        AlertDialog(
            onDismissRequest = { navigator.finish(Result.CANCEL) },
            title = { Text("Delete \"$itemName\"?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { navigator.finish(Result.CONFIRM) }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { navigator.finish(Result.CANCEL) }) {
                    Text("Cancel")
                }
            },
        )
    }
}

// Usage in a Presenter (inside an event handler with coroutine scope)
// scope.launch {
//     val result = overlayHost.show(ConfirmDeleteOverlay(item.name))
//     if (result == ConfirmDeleteOverlay.Result.CONFIRM) {
//         repository.delete(item.id)
//         items = items.filter { it.id != item.id }
//     }
// }
```

Using the pre-built `AlertDialogOverlay` from circuitx-overlays:

```kotlin
import com.slack.circuitx.overlays.AlertDialogOverlay

// Alternative using the library overlay
scope.launch {
    val confirmed = overlayHost.show(
        AlertDialogOverlay(
            model = Unit,
            onDismissRequest = { false },
        ) { _, navigator ->
            AlertDialog(
                onDismissRequest = { navigator.finish(false) },
                title = { Text("Delete item?") },
                confirmButton = {
                    TextButton(onClick = { navigator.finish(true) }) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { navigator.finish(false) }) { Text("Cancel") }
                },
            )
        }
    )
    if (confirmed) {
        repository.delete(itemId)
    }
}
```

---

## 7. State Retention

Demonstrating `rememberRetained`, `collectAsRetainedState`, and `produceRetainedState` in a Presenter.

```kotlin
package com.example.feature.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.collectAsRetainedState
import com.slack.circuit.retained.produceRetainedState
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.retained.rememberRetainedSaveable
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.screen.Screen
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.parcelize.Parcelize

@Parcelize
data object DashboardScreen : Screen {
    data class State(
        val userName: String,
        val notifications: ImmutableList<Notification>,
        val stats: Stats?,
        val scrollPosition: Int,
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data class ScrollPositionChanged(val position: Int) : Event
    }
}

@AssistedInject
class DashboardPresenter(
    @Assisted private val navigator: Navigator,
    private val userRepo: UserRepository,
    private val notificationRepo: NotificationRepository,
    private val statsRepo: StatsRepository,
) : Presenter<DashboardScreen.State> {

    @CircuitInject(DashboardScreen::class, AppScope::class)
    @AssistedFactory
    fun interface Factory {
        fun create(navigator: Navigator): DashboardPresenter
    }

    @Composable
    override fun present(): DashboardScreen.State {
        // rememberRetained: survives config changes, NOT process death
        var userName by rememberRetained { mutableStateOf("") }

        // rememberRetainedSaveable: survives config changes AND process death
        var scrollPosition by rememberRetainedSaveable { mutableStateOf(0) }

        // collectAsRetainedState: collect a Flow with retention
        val notifications by notificationRepo
            .observeNotifications()
            .collectAsRetainedState(initial = emptyList<Notification>())

        // produceRetainedState: async production with retention
        val stats by produceRetainedState<Stats?>(initialValue = null) {
            value = statsRepo.loadStats()
        }

        // One-time load with rememberRetained + LaunchedEffect pattern
        rememberRetained {
            userName = userRepo.getCurrentUserName()
            Unit  // rememberRetained returns the init value
        }

        return DashboardScreen.State(
            userName = userName,
            notifications = notifications.toImmutableList(),
            stats = stats,
            scrollPosition = scrollPosition,
        ) { event ->
            when (event) {
                is DashboardScreen.Event.ScrollPositionChanged -> {
                    scrollPosition = event.position
                }
            }
        }
    }
}
```

---

## 8. Nested Navigation (CircuitContent with onNavEvent)

> **Moved**: See [circuit-navigation-expert/examples.md](../circuit-navigation-expert/examples.md) Examples 4-5 for nested navigation patterns with sub-flows and embedded screens.

Quick pattern:
```kotlin
CircuitContent(
    screen = childScreen,
    onNavEvent = { navigator.onNavEvent(it) },
)
```

---

## 9. Navigation with Results (PopResult Pattern)

> **Moved**: See [circuit-navigation-expert/examples.md](../circuit-navigation-expert/examples.md) Example 3 for complete PopResult implementation with `rememberAnsweringNavigator`.

Quick pattern:
```kotlin
val answeringNavigator = rememberAnsweringNavigator<MyResult>(navigator) { result ->
    selectedValue = result.value
}
answeringNavigator.goTo(PickerScreen)
```

---

## 10. Simple Presenter (No Assisted Injection)

For presenters that do NOT need `Navigator`, use `@Inject` + `@CircuitInject` directly on the class.

```kotlin
package com.example.feature.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.produceRetainedState
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.screen.Screen
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import kotlinx.parcelize.Parcelize

@Parcelize
data object AboutScreen : Screen {
    data class State(
        val appVersion: String,
        val buildDate: String,
        val isLoading: Boolean,
        val eventSink: (Event) -> Unit = {},
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent
    // No events needed for a static info screen
}

// No @AssistedInject needed -- @Inject + @CircuitInject directly on class
@CircuitInject(AboutScreen::class, AppScope::class)
@Inject
class AboutPresenter(
    private val appInfoProvider: AppInfoProvider,
) : Presenter<AboutScreen.State> {

    @Composable
    override fun present(): AboutScreen.State {
        val appInfo by produceRetainedState<AppInfo?>(initialValue = null) {
            value = appInfoProvider.getAppInfo()
        }

        return AboutScreen.State(
            appVersion = appInfo?.version ?: "",
            buildDate = appInfo?.buildDate ?: "",
            isLoading = appInfo == null,
        )
        // No eventSink needed since there are no events
    }
}

@CircuitInject(AboutScreen::class, AppScope::class)
@Composable
fun AboutUi(state: AboutScreen.State, modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier) { padding ->
        Column(Modifier.padding(padding)) {
            Text(
                text = "About",
                style = MaterialTheme.typography.headlineMedium,
            )
            if (!state.isLoading) {
                Text("Version: ${state.appVersion}")
                Text("Built: ${state.buildDate}")
            }
        }
    }
}
```

---

## 11. Testing with Presenter.test{} and FakeNavigator

Testing a presenter using `circuit-test` artifacts.

```kotlin
package com.example.feature.home

import com.google.common.truth.Truth.assertThat
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.test
import kotlinx.coroutines.test.runTest
import org.junit.Test

class HomePresenterTest {

    private val fakeRepo = FakeItemRepository()

    @Test
    fun `initial state shows loading then items`() = runTest {
        val navigator = FakeNavigator(HomeScreen)
        val presenter = HomePresenter(
            navigator = navigator,
            repo = fakeRepo,
        )

        presenter.test {
            // First emission: loading
            val loadingState = awaitItem()
            assertThat(loadingState.isLoading).isTrue()
            assertThat(loadingState.items).isEmpty()

            // Second emission: loaded
            val loadedState = awaitItem()
            assertThat(loadedState.isLoading).isFalse()
            assertThat(loadedState.items).hasSize(3)
        }
    }

    @Test
    fun `item click navigates to detail`() = runTest {
        val navigator = FakeNavigator(HomeScreen)
        val presenter = HomePresenter(
            navigator = navigator,
            repo = fakeRepo,
        )

        presenter.test {
            skipItems(1) // Skip loading state
            val state = awaitItem()

            // Simulate click
            state.eventSink(HomeScreen.Event.ItemClicked("item-1"))

            // Assert navigation
            val nextScreen = navigator.awaitNextScreen()
            assertThat(nextScreen).isEqualTo(DetailScreen("item-1"))
        }
    }

    @Test
    fun `refresh triggers reload`() = runTest {
        val navigator = FakeNavigator(HomeScreen)
        val presenter = HomePresenter(
            navigator = navigator,
            repo = fakeRepo,
        )

        presenter.test {
            skipItems(1)
            val state = awaitItem()

            state.eventSink(HomeScreen.Event.Refresh)

            val refreshingState = awaitItem()
            assertThat(refreshingState.isLoading).isTrue()
        }
    }
}

// Fake repository for testing
class FakeItemRepository : ItemRepository {
    override suspend fun getItems(): List<Item> {
        return listOf(
            Item("item-1", "Item 1"),
            Item("item-2", "Item 2"),
            Item("item-3", "Item 3"),
        )
    }
}
```

---

## 12. OverlayEffect in Presenter

Using `OverlayEffect` composable for showing overlays declaratively in a Presenter.

```kotlin
package com.example.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.overlay.OverlayEffect
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuitx.overlays.BottomSheetOverlay
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import kotlinx.parcelize.Parcelize

@Parcelize
data object OnboardingScreen : Screen {
    data class State(
        val hasAcceptedTerms: Boolean,
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data object Continue : Event
    }
}

@CircuitInject(OnboardingScreen::class, AppScope::class)
@Inject
class OnboardingPresenter(
    private val prefs: PreferencesRepository,
) : Presenter<OnboardingScreen.State> {

    @Composable
    override fun present(): OnboardingScreen.State {
        var hasAcceptedTerms by rememberRetained { mutableStateOf(false) }
        var showTerms by rememberRetained { mutableStateOf(!prefs.hasAcceptedTerms()) }

        // OverlayEffect: shows overlay declaratively within the Presenter
        if (showTerms) {
            OverlayEffect {
                val accepted = show(
                    BottomSheetOverlay(
                        model = "Terms of Service content...",
                        onDismiss = { false },
                    ) { terms, navigator ->
                        TermsContent(
                            text = terms,
                            onAccept = { navigator.finish(true) },
                            onDecline = { navigator.finish(false) },
                        )
                    }
                )
                hasAcceptedTerms = accepted
                showTerms = false
                if (accepted) {
                    prefs.setAcceptedTerms(true)
                }
            }
        }

        return OnboardingScreen.State(
            hasAcceptedTerms = hasAcceptedTerms,
        ) { event ->
            when (event) {
                is OnboardingScreen.Event.Continue -> {
                    if (!hasAcceptedTerms) {
                        showTerms = true
                    }
                }
            }
        }
    }
}
```
