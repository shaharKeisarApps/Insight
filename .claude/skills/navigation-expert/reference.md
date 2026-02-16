# Navigation Expert API Reference

Cross-cutting navigation APIs that apply to both Circuit Navigator and Navigation 3 paradigms. Covers shared element transitions, adaptive layouts, auth flow patterns, performance, and a paradigm comparison table.

For paradigm-specific API references:
- **Circuit Navigator**: [circuit-navigation-expert/reference.md](../circuit-navigation-expert/reference.md)
- **Navigation 3 + ViewModel**: [viewmodel-nav3-expert/reference.md](../viewmodel-nav3-expert/reference.md)

---

## Shared Element Transitions

### SharedTransitionLayout (Compose Foundation)

Top-level composable that provides a `SharedTransitionScope` to its content. All shared element and shared bounds modifiers must be called within this scope.

```kotlin
// Package: androidx.compose.animation
@Composable
fun SharedTransitionLayout(
    modifier: Modifier = Modifier,
    content: @Composable SharedTransitionScope.() -> Unit,
)
```

Available in Compose Multiplatform 1.10.0+.

### SharedTransitionScope

Receiver scope provided by `SharedTransitionLayout`. Exposes shared element and shared bounds APIs.

```kotlin
// Package: androidx.compose.animation
interface SharedTransitionScope {
    @Composable
    fun rememberSharedContentState(key: Any): SharedContentState

    fun Modifier.sharedElement(
        state: SharedContentState,
        animatedVisibilityScope: AnimatedVisibilityScope,
        boundsTransform: BoundsTransform = DefaultBoundsTransform,
        placeHolderSize: PlaceHolderSize = PlaceHolderSize.contentSize,
        renderInOverlayDuringTransition: Boolean = true,
        zIndexInOverlay: Float = 0f,
        clipInOverlayDuringTransition: OverlayClip = ParentClip,
    ): Modifier

    fun Modifier.sharedBounds(
        state: SharedContentState,
        animatedVisibilityScope: AnimatedVisibilityScope,
        enter: EnterTransition = fadeIn(),
        exit: ExitTransition = fadeOut(),
        boundsTransform: BoundsTransform = DefaultBoundsTransform,
        placeHolderSize: PlaceHolderSize = PlaceHolderSize.animatedSize,
        renderInOverlayDuringTransition: Boolean = true,
        zIndexInOverlay: Float = 0f,
        clipInOverlayDuringTransition: OverlayClip = ParentClip,
    ): Modifier
}
```

### SharedContentState

State holder for a shared element or shared bounds transition. Keyed by an arbitrary value (typically a string like `"image-${itemId}"`).

```kotlin
// Package: androidx.compose.animation
class SharedContentState(val key: Any)
```

Created via `rememberSharedContentState(key)` inside a `SharedTransitionScope`.

### sharedElement vs sharedBounds

| Aspect | `sharedElement` | `sharedBounds` |
|--------|-----------------|----------------|
| Content | Same visual element on both screens | Container that morphs between different content |
| Sizing | Crossfades content at matched size | Animates bounds, fades old/new content |
| Use case | Image, icon, avatar that appears on both screens | Card -> full screen, FAB -> sheet |
| PlaceHolderSize default | `contentSize` | `animatedSize` |
| Typical example | List thumbnail -> detail hero image | List card -> detail page container |

### AnimatedVisibilityScope

Both `sharedElement` and `sharedBounds` require an `AnimatedVisibilityScope`. How you obtain it depends on the paradigm:

- **Circuit**: `NavigableCircuitContent` provides `AnimatedVisibilityScope` to each screen's UI composable when using `SharedElementTransitionLayout`.
- **Nav3**: `NavDisplay` provides `AnimatedVisibilityScope` via the transition animation; access it through `LocalAnimatedVisibilityScope.current` or the entry content lambda.

---

## Circuit SharedElementTransitionLayout

Circuit's wrapper around Compose's `SharedTransitionLayout` that integrates with `NavigableCircuitContent` and automatically provides `AnimatedVisibilityScope` to each screen.

```kotlin
// Package: com.slack.circuit.sharedelements
@Composable
fun SharedElementTransitionLayout(
    modifier: Modifier = Modifier,
    content: @Composable SharedTransitionScope.() -> Unit,
)
```

Must wrap `ContentWithOverlays` and `NavigableCircuitContent`:

```kotlin
SharedElementTransitionLayout {
    ContentWithOverlays {
        NavigableCircuitContent(navigator, backStack, ...)
    }
}
```

Dependency:

```toml
[libraries]
circuit-sharedelements = { module = "com.slack.circuit:circuit-sharedelements", version.ref = "circuit" }
```

Inside a Circuit UI composable, access the scope via `LocalSharedTransitionScope.current` and the visibility scope via `LocalAnimatedVisibilityScope.current` (both provided automatically by the layout).

---

## WindowSizeClass API

From `material3-window-size-class`, used to adapt navigation strategy based on screen dimensions.

### calculateWindowSizeClass

```kotlin
// Package: androidx.compose.material3.windowsizeclass
@Composable
fun calculateWindowSizeClass(activity: Activity): WindowSizeClass
```

For KMP (non-Android), use the `calculateWindowSizeClass()` overload that accepts `windowSize: DpSize`:

```kotlin
// Package: androidx.compose.material3.windowsizeclass
@Composable
fun calculateWindowSizeClass(windowSize: DpSize): WindowSizeClass
```

### WindowSizeClass

```kotlin
// Package: androidx.compose.material3.windowsizeclass
data class WindowSizeClass(
    val widthSizeClass: WindowWidthSizeClass,
    val heightSizeClass: WindowHeightSizeClass,
)
```

### WindowWidthSizeClass

| Class | Width | Navigation Strategy |
|-------|-------|-------------------|
| `Compact` | < 600dp | Single pane, standard back stack |
| `Medium` | 600dp - 839dp | List + detail side-by-side (optional) |
| `Expanded` | >= 840dp | Persistent rail/drawer + two-pane |

```kotlin
// Package: androidx.compose.material3.windowsizeclass
enum class WindowWidthSizeClass { Compact, Medium, Expanded }
```

### Navigation Strategy Decision

```kotlin
val windowSizeClass = calculateWindowSizeClass(activity)
val useListDetail = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact
```

Dependency:

```toml
[libraries]
material3-window-size-class = { module = "androidx.compose.material3:material3-window-size-class", version.ref = "compose-material3" }
```

---

## Auth Flow Patterns

### Circuit: resetRoot with StateOptions

```kotlin
// Package: com.slack.circuit.runtime
// Navigator.resetRoot controls full graph swaps

// Login success -- replace entire stack with home
navigator.resetRoot(newRoot = HomeScreen)
// StateOptions.Default: does NOT save outgoing state, does NOT restore incoming

// Logout -- replace entire stack with login
navigator.resetRoot(newRoot = LoginScreen)
// All home stack state is discarded (no saveState)

// Tab switch (for comparison -- saves/restores per-tab state)
navigator.resetRoot(
    newRoot = targetTab,
    saveState = true,
    restoreState = true,
)
```

`resetRoot` returns `List<Screen>` -- the screens that were removed from the stack.

### Nav3: backStack clear/rebuild

```kotlin
// NavBackStack is a MutableList<NavKey>

// Login success
backStack.clear()  // Removes all entries including start key
backStack.add(HomeRoute)

// Logout
backStack.clear()
backStack.add(LoginRoute)
```

`clear()` on `NavBackStack` removes all entries. The next `add()` establishes the new root.

### Token Refresh Interceptor Pattern

Both paradigms benefit from a central auth state observer that triggers navigation:

```kotlin
// Shared pattern -- works with either paradigm
class AuthStateManager @Inject constructor(
    private val tokenStore: TokenStore,
) {
    val authState: StateFlow<AuthState> = tokenStore.observeToken()
        .map { token ->
            when {
                token == null -> AuthState.Unauthenticated
                token.isExpired -> AuthState.TokenExpired
                else -> AuthState.Authenticated(token)
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, AuthState.Loading)
}

sealed interface AuthState {
    data object Loading : AuthState
    data class Authenticated(val token: AuthToken) : AuthState
    data object TokenExpired : AuthState
    data object Unauthenticated : AuthState
}
```

The top-level Presenter (Circuit) or ViewModel (Nav3) collects `authState` and calls `resetRoot` / `backStack.clear()` accordingly.

---

## Navigation Performance Guidelines

### Lazy Initialization

| Paradigm | Pattern | Benefit |
|----------|---------|---------|
| Circuit | Metro `@ContributesIntoMap` + `Presenter.Factory` | Factories created lazily via map lookup |
| Nav3 | `viewModel { factory.create(...) }` inside `entry<>` | ViewModel created only when entry is first composed |

Avoid eagerly constructing all Presenter or ViewModel instances at app startup. Both Metro's multibinding map and Nav3's `entry<>` block ensure lazy creation.

### Stack Depth Recommendations

| Metric | Recommendation | Reason |
|--------|---------------|--------|
| Max back stack depth | 20 entries | Memory for retained state / ViewModelStore grows linearly |
| Deep link chains | Use `popUntil` or rebuild stack | Avoid pushing 10+ screens from a deep link |
| Tab stacks | Independent per tab, max 10 each | Each tab has its own retained state budget |

### Argument Size Limits

Screen/NavKey arguments should contain **IDs and flags only**, never full data objects:

```kotlin
// WRONG -- large data in arguments
@Parcelize
data class DetailScreen(val item: FullItemData) : Screen  // FullItemData may be 10KB+

// RIGHT -- ID only, load data in Presenter/ViewModel
@Parcelize
data class DetailScreen(val itemId: String) : Screen
```

Large arguments cause:
- Slow serialization on every config change / process death
- Bundle size limits on Android (1MB TransactionTooLargeException)
- Memory pressure from duplicated data in the back stack

### Profiling Tools

| Tool | What it Measures | Platform |
|------|-----------------|----------|
| Layout Inspector | Overdraw, recomposition during transitions | Android |
| Compose Metrics | Stability, skippability of navigation composables | All |
| Android Profiler | Memory allocation during navigation | Android |
| LeakCanary | Retained state / ViewModel leaks | Android |
| Compose Tracing | Recomposition count per frame during transitions | All |

---

## Paradigm API Comparison Table

| Concept | Circuit Navigator (0.32.0) | Navigation 3 (1.0.0-alpha01) |
|---------|---------------------------|------------------------------|
| Route type | `Screen` (implements `Parcelable`) | `NavKey` (implements `@Serializable`) |
| Navigator | `Navigator` interface (imperative) | `NavBackStack<T>` (MutableList) |
| Push screen | `navigator.goTo(screen)` | `backStack.add(key)` |
| Pop screen | `navigator.pop()` | `backStack.removeLastOrNull()` |
| Replace root | `navigator.resetRoot(newRoot)` | `backStack.clear(); backStack.add(newRoot)` |
| Peek top | `navigator.peek()` | `backStack.lastOrNull()` |
| Full stack | `navigator.peekBackStack()` | `backStack.toList()` |
| Host composable | `NavigableCircuitContent` | `NavDisplay` |
| Single screen render | `CircuitContent` | (no direct equivalent) |
| Back stack creation | `rememberSaveableBackStack(root)` | `rememberNavBackStack(startKey)` |
| Navigator creation | `rememberCircuitNavigator(backStack)` | Callback lambdas (no navigator object) |
| Result passing | `PopResult` + `rememberAnsweringNavigator` | Shared ViewModel or callback |
| Tab save/restore | `resetRoot(saveState=true, restoreState=true)` | Multiple `rememberNavBackStack` instances |
| Adaptive layout | Nested `NavigableCircuitContent` | `SceneStrategy` (TwoPane, ListDetail) |
| ViewModel scoping | `rememberRetained` (Circuit-native) | `rememberViewModelStoreNavEntryDecorator` |
| Entry registration | `Presenter.Factory` + `Ui.Factory` via Metro | `entryProvider { entry<T> { } }` |
| Overlay support | `OverlayHost` + `ContentWithOverlays` | Manual (no built-in) |
| Transition control | `NavDecoratorFactory` | `TransitionSpec` / `PredictivePopTransitionSpec` |
| Gesture back | `GestureNavigationDecorationFactory` | `predictivePopTransitionSpec` |
| Testing | `FakeNavigator` (circuit-test) | Assert on backStack list directly |
| DI pattern | `@CircuitInject` + `@AssistedFactory` | `viewModel { factory.create(...) }` |

---

## Dependencies (Shared / Cross-Cutting)

### Shared Element Transitions

```toml
[versions]
circuit = "0.32.0"
compose = "1.10.0"

[libraries]
# Circuit shared elements
circuit-sharedelements = { module = "com.slack.circuit:circuit-sharedelements", version.ref = "circuit" }

# Compose foundation (includes SharedTransitionLayout)
compose-foundation = { module = "org.jetbrains.compose.foundation:foundation", version.ref = "compose" }
```

### Adaptive Layout

```toml
[libraries]
material3-window-size-class = { module = "androidx.compose.material3:material3-window-size-class", version.ref = "compose-material3" }

# Nav3 adaptive strategies (if using Nav3)
androidx-navigation3-ui = { module = "androidx.navigation3:navigation3-ui", version.ref = "navigation3" }
```

### Circuit Navigator (paradigm-specific)

```toml
[versions]
circuit = "0.32.0"

[libraries]
circuit-foundation = { module = "com.slack.circuit:circuit-foundation", version.ref = "circuit" }
circuit-runtime = { module = "com.slack.circuit:circuit-runtime", version.ref = "circuit" }
circuit-backstack = { module = "com.slack.circuit:circuit-backstack", version.ref = "circuit" }
circuit-overlay = { module = "com.slack.circuit:circuit-overlay", version.ref = "circuit" }
circuitx-gesture-navigation = { module = "com.slack.circuit:circuitx-gesture-navigation", version.ref = "circuit" }
```

### Navigation 3 (paradigm-specific)

```toml
[versions]
navigation3 = "1.0.0-alpha01"
lifecycle-viewmodel-navigation3 = "2.8.0-alpha01"

[libraries]
androidx-navigation3-runtime = { module = "androidx.navigation3:navigation3-runtime", version.ref = "navigation3" }
androidx-navigation3-ui = { module = "androidx.navigation3:navigation3-ui", version.ref = "navigation3" }
androidx-lifecycle-viewmodel-navigation3 = { module = "androidx.lifecycle:lifecycle-viewmodel-navigation3", version.ref = "lifecycle-viewmodel-navigation3" }
```

---

## Key Imports (Organized by Concern)

### Shared Element Transitions

```kotlin
// Compose foundation shared transitions
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope

// Circuit shared elements
import com.slack.circuit.sharedelements.SharedElementTransitionLayout
import com.slack.circuit.sharedelements.LocalSharedTransitionScope
import com.slack.circuit.sharedelements.LocalAnimatedVisibilityScope
```

### Adaptive Layout

```kotlin
// WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass

// Nav3 scene strategies
import androidx.navigation3.ui.SinglePaneSceneStrategy
import androidx.navigation3.ui.TwoPaneSceneStrategy
import androidx.navigation3.ui.ListDetailSceneStrategy
```

### Auth Flow

```kotlin
// Circuit resetRoot
import com.slack.circuit.runtime.Navigator

// Nav3 backStack manipulation
import androidx.navigation3.runtime.rememberNavBackStack

// Shared auth state
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
```

### Metro DI (shared across both paradigms)

```kotlin
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.ContributesIntoMap
```
