# Circuit Navigation API Reference (v0.32.0)

Comprehensive API reference for Circuit's navigation system across all navigation artifacts.

---

## Navigator Interface (circuit-runtime)

The core navigation contract injected into Presenters via `@AssistedInject`. Provides imperative navigation: push, pop, and root replacement.

```kotlin
// Package: com.slack.circuit.runtime
@Stable
public interface Navigator {

    /**
     * Navigate to the given [screen], pushing it onto the back stack.
     * Returns false if the screen is already the top of the stack (no-op).
     */
    public fun goTo(screen: Screen): Boolean

    /**
     * Pop the top screen off the back stack.
     * Returns the popped screen, or null if at root.
     * Optionally passes [result] to the previous screen via PopResult.
     */
    public fun pop(result: PopResult? = null): Screen?

    /**
     * Replace the entire back stack with [newRoot].
     * Returns the list of screens that were removed.
     * [options] controls state saving/restoring behavior for multi-backstack.
     */
    public fun resetRoot(
        newRoot: Screen,
        options: StateOptions = StateOptions.Default,
    ): List<Screen>

    /**
     * Peek at the current top screen without modifying the stack.
     * Returns null if the stack is empty.
     */
    public fun peek(): Screen?

    /**
     * Returns an immutable snapshot of all records in the back stack.
     */
    public fun peekBackStack(): ImmutableList<BackStack.Record>

    /**
     * State options controlling save/restore behavior during resetRoot.
     */
    public data class StateOptions(
        /** Whether to save the outgoing root's back stack state. */
        val save: Boolean = false,
        /** Whether to restore the incoming root's previously saved state. */
        val restore: Boolean = false,
        /** Whether to clear saved states not matching the new root. */
        val clear: Boolean = false,
    ) {
        public companion object {
            /** No state saving. Used for auth flows and single-stack navigation. */
            public val Default: StateOptions = StateOptions()
            /** Save outgoing and restore incoming. Used for tab navigation. */
            public val SaveAndRestore: StateOptions = StateOptions(save = true, restore = true)
        }
    }

    /** No-op Navigator that does nothing. Useful as a default parameter. */
    public companion object {
        public val NoOp: Navigator = object : Navigator {
            override fun goTo(screen: Screen): Boolean = false
            override fun pop(result: PopResult?): Screen? = null
            override fun resetRoot(newRoot: Screen, options: StateOptions): List<Screen> = emptyList()
            override fun peek(): Screen? = null
            override fun peekBackStack(): ImmutableList<BackStack.Record> = persistentListOf()
        }
    }
}
```

### Method Reference

| Method | Returns | Description |
|--------|---------|-------------|
| `goTo(screen)` | `Boolean` | Push screen onto back stack. Returns false if already top (no-op). |
| `pop(result?)` | `Screen?` | Pop top screen. Returns popped screen or null if at root. |
| `resetRoot(newRoot, options)` | `List<Screen>` | Replace entire stack with new root. Returns removed screens. |
| `peek()` | `Screen?` | Current top screen without modifying the stack. |
| `peekBackStack()` | `ImmutableList<Record>` | Immutable snapshot of all back stack records. |

### StateOptions Values

| Value | save | restore | clear | Use Case |
|-------|------|---------|-------|----------|
| `Default` | false | false | false | Auth flows, single-stack navigation |
| `SaveAndRestore` | true | true | false | Tab navigation with state preservation |
| Custom | varies | varies | varies | Advanced: e.g. save + clear for tab reset |

### Navigator Usage Rules

1. **Never call Navigator methods during composition** -- Always inside event handlers or `LaunchedEffect`.
2. **goTo returns false if screen is already top** -- Use this to prevent duplicate pushes.
3. **pop returns null at root** -- Check return value to know if pop succeeded.
4. **resetRoot clears everything** -- The returned list contains all removed screens.
5. **Use NoOp for previews and default params** -- Avoids crashes in `@Preview` composables.

---

## Navigator Extension Functions (circuit-foundation)

```kotlin
// Package: com.slack.circuit.foundation

/**
 * Pop screens until [predicate] returns true.
 * The screen matching the predicate is NOT popped (exclusive).
 * If no screen matches, pops everything to root.
 */
public fun Navigator.popUntil(predicate: (Screen) -> Boolean) {
    val backStack = peekBackStack()
    for (record in backStack) {
        if (predicate(record.screen)) break
        pop()
    }
}

/**
 * Handle a [NavEvent] by delegating to the appropriate Navigator method.
 * Used in CircuitContent's onNavEvent callback to forward child nav events to parent.
 */
public fun Navigator.onNavEvent(event: NavEvent) {
    when (event) {
        is NavEvent.GoTo -> goTo(event.screen)
        is NavEvent.Pop -> pop(event.result)
        is NavEvent.ResetRoot -> resetRoot(event.newRoot, event.options)
    }
}
```

**popUntil note**: The predicate receives each `Screen` from top to bottom. The first screen where `predicate` returns `true` becomes the new top. Common pattern: `navigator.popUntil { false }` pops all screens to root.

---

## NavEvent (circuit-foundation)

Sealed interface for navigation events emitted by `CircuitContent` via `onNavEvent`.

```kotlin
public sealed interface NavEvent {
    public data class GoTo(val screen: Screen) : NavEvent
    public data class Pop(val result: PopResult? = null) : NavEvent
    public data class ResetRoot(
        val newRoot: Screen,
        val options: Navigator.StateOptions = Navigator.StateOptions.Default,
    ) : NavEvent
}
```

---

## NavigableCircuitContent (circuit-foundation)

Primary composable for rendering a navigation stack with transitions.

```kotlin
@Composable
public fun NavigableCircuitContent(
    navigator: Navigator,
    backStack: SaveableBackStack,
    modifier: Modifier = Modifier,
    circuit: Circuit = requireNotNull(LocalCircuit.current),
    providedValues: ImmutableMap<out BackStack.Record, BackStackRecordLocalProvider> = persistentMapOf(),
    decoratorFactory: NavDecorator.Factory = NavDecorator.Factory.DefaultDecoratorFactory,
    unavailableRoute: @Composable (Screen, Modifier) -> Unit = circuit.defaultUnavailableRoute,
)
```

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `navigator` | `Navigator` | required | Navigator controlling the back stack |
| `backStack` | `SaveableBackStack` | required | Back stack holding screen records |
| `providedValues` | `ImmutableMap<Record, BackStackRecordLocalProvider>` | empty | Per-entry CompositionLocals |
| `decoratorFactory` | `NavDecorator.Factory` | `DefaultDecoratorFactory` | Screen transition decorator factory |
| `unavailableRoute` | `@Composable` | Default error UI | Fallback when no Ui found for screen |

---

## CircuitContent (circuit-foundation)

Renders a single screen without its own back stack. Three overloads:

```kotlin
// Standalone -- isolated screen, no nav
@Composable
public fun CircuitContent(screen: Screen, modifier: Modifier = Modifier, circuit: Circuit = ...)

// With onNavEvent -- forward child nav events to parent
@Composable
public fun CircuitContent(screen: Screen, modifier: Modifier = Modifier, onNavEvent: (NavEvent) -> Unit, circuit: Circuit = ...)

// With Navigator -- inject custom navigator (rare)
@Composable
public fun CircuitContent(screen: Screen, navigator: Navigator, modifier: Modifier = Modifier, circuit: Circuit = ...)
```

| Overload | When to Use |
|----------|-------------|
| Standalone | Isolated screen with no navigation |
| onNavEvent | Embedded in pager/list -- forward nav to parent |
| Navigator | Custom Navigator injection (rare) |

---

## SaveableBackStack (circuit-backstack)

Manages an ordered stack of screen records with state saving/restoration support for multi-backstack (tab navigation).

```kotlin
// Package: com.slack.circuit.backstack
@Stable
public class SaveableBackStack(
    initialScreens: List<Screen>,
    /** Key used for saveable state registration. */
    val key: String = "default",
) {
    /** All records in the stack, from bottom to top. */
    public val records: List<BackStack.Record>

    /** The top (most recent) record. */
    public val topRecord: BackStack.Record?

    /** The root (bottom) record. */
    public val rootRecord: BackStack.Record?

    /** Whether the stack has exactly one entry (the root). */
    public val isAtRoot: Boolean

    /** Whether the stack is empty. Should never be true in normal usage. */
    public val isEmpty: Boolean

    /** Number of records in the stack. */
    public val size: Int

    /** Push a screen onto the top of the stack. */
    public fun push(screen: Screen)

    /** Push a screen with optional state saving of current top. */
    public fun push(screen: Screen, saveState: Boolean)

    /** Pop the top record. Returns null if at root. */
    public fun pop(): BackStack.Record?

    /** Pop until predicate matches on new top (exclusive). */
    public fun popUntil(predicate: (BackStack.Record) -> Boolean)

    /** Replace the top record with a new screen. */
    public fun replaceTop(screen: Screen)

    /** Replace entire stack with new root. Optionally save/restore state. */
    public fun resetRoot(
        newRoot: Screen,
        saveState: Boolean = false,
        restoreState: Boolean = false,
    ): List<BackStack.Record>

    /** Save current stack state for later restoration. */
    public fun saveState(key: String = this.key)

    /** Restore previously saved state. Returns true if found. */
    public fun restoreState(key: String = this.key): Boolean
}
```

### Methods Summary

| Method | Returns | Description |
|--------|---------|-------------|
| `push(screen)` | Unit | Add screen to top of stack |
| `push(screen, saveState)` | Unit | Add screen, optionally save current state |
| `pop()` | `Record?` | Remove and return top, null if at root |
| `popUntil(predicate)` | Unit | Pop until predicate matches (exclusive) |
| `replaceTop(screen)` | Unit | Swap current top with new screen |
| `resetRoot(newRoot, ...)` | `List<Record>` | Replace entire stack, return removed records |
| `saveState(key)` | Unit | Persist current stack to saved state |
| `restoreState(key)` | Boolean | Restore previously saved stack |

**Note**: `Navigator.resetRoot()` with `StateOptions.SaveAndRestore` delegates to `SaveableBackStack.resetRoot(saveState=true, restoreState=true)` internally. You rarely call `SaveableBackStack` methods directly.

---

## BackStack.Record (circuit-backstack)

```kotlin
public interface BackStack {
    public interface Record {
        public val key: String    // Unique key for state scoping
        public val screen: Screen // The screen this record represents
    }
}
```

---

## rememberCircuitNavigator (circuit-foundation)

Creates a `Navigator` that controls the given `SaveableBackStack`. Remembers it across recompositions.

```kotlin
// Package: com.slack.circuit.foundation
@Composable
public fun rememberCircuitNavigator(
    backStack: SaveableBackStack,
    onRootPop: (() -> Unit)? = null,
): Navigator
```

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `backStack` | `SaveableBackStack` | required | The back stack this navigator controls |
| `onRootPop` | `(() -> Unit)?` | null | Called when `pop()` is invoked at root. If null, pop at root is a no-op. |

**Notes:**
- `onRootPop` is critical for nested `NavigableCircuitContent` -- without it, the user cannot exit the sub-flow by pressing back.
- For top-level navigation, leave `onRootPop` as null (pressing back at root does nothing).
- The returned Navigator delegates all stack operations to the `SaveableBackStack`.

---

## rememberSaveableBackStack (circuit-backstack)

```kotlin
@Composable public fun rememberSaveableBackStack(root: Screen): SaveableBackStack
@Composable public fun rememberSaveableBackStack(screens: List<Screen>): SaveableBackStack
```

Last screen in list = top of stack. First = root.

---

## PopResult (circuit-runtime)

Marker interface for typed results passed back when popping a screen.

```kotlin
// Package: com.slack.circuit.runtime
public interface PopResult
```

Convention: Define as a data class implementing `PopResult` inside the answering screen:

```kotlin
@Parcelize
data object PickerScreen : Screen {
    // ... State, Event ...

    @Parcelize
    data class Result(val selectedId: String) : PopResult
}
```

---

## rememberAnsweringNavigator (circuit-foundation)

Wraps a Navigator to intercept `PopResult` from a target screen. When the target pops with a result, the `onAnswer` callback fires.

```kotlin
// Package: com.slack.circuit.foundation
@Composable
public fun <T : PopResult> rememberAnsweringNavigator(
    navigator: Navigator,
    onAnswer: (T) -> Unit,
): Navigator
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `navigator` | `Navigator` | The underlying navigator to delegate to |
| `onAnswer` | `(T) -> Unit` | Callback fired when the pushed screen pops with a result of type T |

### Usage Pattern

```kotlin
// In the calling Presenter:
val answeringNavigator = rememberAnsweringNavigator<PickerScreen.Result>(navigator) { result ->
    selectedItem = result.selectedId
}

// Navigate using the ANSWERING navigator, not the raw navigator
answeringNavigator.goTo(PickerScreen)

// In the picker Presenter:
navigator.pop(result = PickerScreen.Result(selectedId = "abc"))
```

**Critical**: You MUST navigate with `answeringNavigator.goTo(...)`, NOT `navigator.goTo(...)`. Using the raw navigator bypasses the result callback entirely.

---

## InterceptingNavigator / NavigationInterceptor (circuitx-navigation)

Provides navigation interception for analytics, auth guards, A/B routing, and logging.

### NavigationInterceptor

```kotlin
// Package: com.slack.circuitx.navigation
public interface NavigationInterceptor {

    /**
     * Intercept a goTo navigation event.
     * Return Skipped to pass through, Rewrite to change destination, or Consumed to swallow.
     */
    public fun interceptGoTo(
        screen: Screen,
        context: NavigationContext,
    ): InterceptedGoToResult = InterceptedGoToResult.Skipped

    /**
     * Intercept a pop navigation event.
     */
    public fun interceptPop(
        result: PopResult?,
        context: NavigationContext,
    ): InterceptedPopResult = InterceptedPopResult.Skipped

    /**
     * Intercept a resetRoot navigation event.
     */
    public fun interceptResetRoot(
        newRoot: Screen,
        options: Navigator.StateOptions,
        context: NavigationContext,
    ): InterceptedResetRootResult = InterceptedResetRootResult.Skipped
}
```

### InterceptedGoToResult

```kotlin
public sealed interface InterceptedGoToResult {
    /** Interceptor does not handle this event. Pass to next interceptor. */
    public data object Skipped : InterceptedGoToResult
    /** Rewrite the destination to a different screen. */
    public data class Rewrite(val screen: Screen) : InterceptedGoToResult
    /** Consume the event entirely. No navigation occurs. */
    public data object Consumed : InterceptedGoToResult
}
```

### InterceptedPopResult

```kotlin
public sealed interface InterceptedPopResult {
    public data object Skipped : InterceptedPopResult
    public data object Consumed : InterceptedPopResult
}
```

### InterceptedResetRootResult

```kotlin
public sealed interface InterceptedResetRootResult {
    public data object Skipped : InterceptedResetRootResult
    public data class Rewrite(
        val newRoot: Screen,
        val options: Navigator.StateOptions = Navigator.StateOptions.Default,
    ) : InterceptedResetRootResult
    public data object Consumed : InterceptedResetRootResult
}
```

### NavigationContext

```kotlin
public interface NavigationContext {
    /** The current back stack snapshot at the time of interception. */
    val backStack: ImmutableList<BackStack.Record>
}
```

### NavigationEventListener

Passive observer for navigation events. Does not modify behavior. Use for analytics/logging.

```kotlin
public interface NavigationEventListener {
    public fun onGoTo(screen: Screen) {}
    public fun onPop(result: PopResult?, poppedScreen: Screen?) {}
    public fun onResetRoot(newRoot: Screen, options: Navigator.StateOptions, oldScreens: List<Screen>) {}
}
```

### rememberInterceptingNavigator

```kotlin
@Composable
public fun rememberInterceptingNavigator(
    navigator: Navigator,
    interceptors: List<NavigationInterceptor>,
    listeners: List<NavigationEventListener> = emptyList(),
): Navigator
```

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `navigator` | `Navigator` | required | Underlying navigator to delegate to |
| `interceptors` | `List<NavigationInterceptor>` | required | Ordered list of interceptors (first match wins) |
| `listeners` | `List<NavigationEventListener>` | `emptyList()` | Passive listeners notified after navigation |

---

## GestureNavigationDecorationFactory (circuitx-gesture-navigation)

```kotlin
public class GestureNavigationDecorationFactory(
    private val onBackInvoked: () -> Unit,  // Typically navigator::pop
    private val fallback: NavDecorator.Factory = AnimatedNavDecorator.Factory.DefaultDecoratorFactory,
) : NavDecorator.Factory
```

| Platform | Behavior |
|----------|----------|
| Android API 34+ | Predictive back animations |
| Android < 34 | Animated slide (fallback) |
| iOS | Interactive swipe-to-pop |
| Desktop/Web | Animated slide (fallback) |

---

## NavDecorator / AnimatedNavDecorator / AnimatedScreenTransform (circuit-foundation)

### NavDecorator

Interface for decorating screen transitions in `NavigableCircuitContent`.

```kotlin
// Package: com.slack.circuit.foundation
public interface NavDecorator {
    @Composable
    public fun DecoratedContent(
        args: Args,
        backStackDepth: Int,
        modifier: Modifier,
        content: @Composable (Args) -> Unit,
    )

    public fun interface Factory {
        public fun create(): NavDecorator

        public companion object {
            public val DefaultDecoratorFactory: Factory
        }
    }
}
```

### AnimatedNavDecorator

Default decorator with configurable enter/exit/pop transitions.

```kotlin
public open class AnimatedNavDecorator(
    private val enterTransition: EnterTransition = fadeIn() + slideInHorizontally { it },
    private val exitTransition: ExitTransition = fadeOut() + slideOutHorizontally { -it / 3 },
    private val popEnterTransition: EnterTransition = fadeIn() + slideInHorizontally { -it / 3 },
    private val popExitTransition: ExitTransition = fadeOut() + slideOutHorizontally { it },
) : NavDecorator
```

### AnimatedScreenTransform

Implement on Screen types for per-screen custom transitions.

```kotlin
public interface AnimatedScreenTransform {
    public val enterTransition: EnterTransition? get() = null
    public val exitTransition: ExitTransition? get() = null
    public val popEnterTransition: EnterTransition? get() = null
    public val popExitTransition: ExitTransition? get() = null
}
```

Transition resolution order:
1. If the Screen implements `AnimatedScreenTransform`, its transitions are used
2. Otherwise, the `AnimatedNavDecorator` default transitions apply
3. `GestureNavigationDecorationFactory` can wrap a custom factory as its `fallback`

Example:
```kotlin
@Parcelize
data class DetailScreen(val id: String) : Screen, AnimatedScreenTransform {
    override val enterTransition: EnterTransition get() = slideInHorizontally { it }
    override val exitTransition: ExitTransition get() = slideOutHorizontally { it }
}
```

---

## SharedElementTransitionLayout (circuit-shared-elements)

```kotlin
@Composable
public fun SharedElementTransitionLayout(
    modifier: Modifier = Modifier,
    content: @Composable SharedTransitionScope.() -> Unit,
)
```

Must wrap `ContentWithOverlays` + `NavigableCircuitContent`. Use `Modifier.sharedElement(rememberSharedContentState(key), animatedVisibilityScope)` inside screens.

---

## BackStackRecordLocalProvider (circuit-foundation)

```kotlin
public fun interface BackStackRecordLocalProvider {
    @Composable public fun providedValues(): ProvidedValues
}
// Register: Circuit.Builder().addBackStackRecordLocalProvider(provider)
```

---

## rememberAndroidScreenAwareNavigator (circuitx-android)

Android-specific navigator wrapper that handles `AndroidScreen` types by launching platform intents.

```kotlin
// Package: com.slack.circuitx.android
@Composable
public fun rememberAndroidScreenAwareNavigator(
    delegate: Navigator,
    context: Context = LocalContext.current,
): Navigator
```

Detects `AndroidScreen` instances on `goTo()` and routes them to Android's `startActivity` / `startActivityForResult` instead of the Circuit back stack.

```kotlin
// Example AndroidScreen:
class ShareScreen(val text: String) : AndroidScreen {
    override fun buildIntent(context: Context): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
    }
}

// Usage:
val androidNavigator = rememberAndroidScreenAwareNavigator(navigator)
// Then use androidNavigator instead of navigator:
// androidNavigator.goTo(ShareScreen("Hello!")) // Launches share sheet
// androidNavigator.goTo(DetailScreen("123"))   // Normal Circuit navigation
```

---

## Dependencies (libs.versions.toml)

```toml
[versions]
circuit = "0.32.0"

[libraries]
circuit-foundation = { module = "com.slack.circuit:circuit-foundation", version.ref = "circuit" }
circuit-runtime = { module = "com.slack.circuit:circuit-runtime", version.ref = "circuit" }
circuit-backstack = { module = "com.slack.circuit:circuit-backstack", version.ref = "circuit" }
circuit-codegen-annotations = { module = "com.slack.circuit:circuit-codegen-annotations", version.ref = "circuit" }
circuit-retained = { module = "com.slack.circuit:circuit-retained", version.ref = "circuit" }
circuit-overlay = { module = "com.slack.circuit:circuit-overlay", version.ref = "circuit" }
circuitx-navigation = { module = "com.slack.circuit:circuitx-navigation", version.ref = "circuit" }
circuitx-gesture-navigation = { module = "com.slack.circuit:circuitx-gesture-navigation", version.ref = "circuit" }
circuit-shared-elements = { module = "com.slack.circuit:circuit-shared-elements", version.ref = "circuit" }
circuitx-android = { module = "com.slack.circuit:circuitx-android", version.ref = "circuit" }
circuit-test = { module = "com.slack.circuit:circuit-test", version.ref = "circuit" }
```

| Feature | Additional Dependencies |
|---------|------------------------|
| Navigation interception | + `circuitx-navigation` |
| Gesture navigation | + `circuitx-gesture-navigation` |
| Shared elements | + `circuit-shared-elements` |
| Android intents | + `circuitx-android` |
| Testing | + `circuit-test` |

---

## Key Imports by Feature

### Basic Navigation

```kotlin
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.runtime.PopResult
import com.slack.circuit.foundation.NavigableCircuitContent
import com.slack.circuit.foundation.CircuitContent
import com.slack.circuit.foundation.NavEvent
import com.slack.circuit.foundation.onNavEvent
import com.slack.circuit.foundation.rememberCircuitNavigator
import com.slack.circuit.foundation.rememberAnsweringNavigator
import com.slack.circuit.backstack.rememberSaveableBackStack
import com.slack.circuit.backstack.SaveableBackStack
```

### Gesture Navigation

```kotlin
import com.slack.circuitx.gesturenavigation.GestureNavigationDecorationFactory
```

### Navigation Interception

```kotlin
import com.slack.circuitx.navigation.NavigationInterceptor
import com.slack.circuitx.navigation.InterceptedGoToResult
import com.slack.circuitx.navigation.InterceptedPopResult
import com.slack.circuitx.navigation.InterceptedResetRootResult
import com.slack.circuitx.navigation.NavigationContext
import com.slack.circuitx.navigation.NavigationEventListener
import com.slack.circuitx.navigation.rememberInterceptingNavigator
```

### Shared Elements

```kotlin
import com.slack.circuit.sharedelements.SharedElementTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.rememberSharedContentState
import androidx.compose.animation.sharedElement
import androidx.compose.animation.sharedBounds
```

### Circuit Setup

```kotlin
import com.slack.circuit.foundation.Circuit
import com.slack.circuit.foundation.CircuitCompositionLocals
import com.slack.circuit.foundation.LocalCircuit
import com.slack.circuit.overlay.ContentWithOverlays
import com.slack.circuit.overlay.LocalOverlayHost
```

### Transitions

```kotlin
import com.slack.circuit.foundation.AnimatedNavDecorator
import com.slack.circuit.foundation.AnimatedScreenTransform
import com.slack.circuit.foundation.NavDecorator
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
```

### Testing

```kotlin
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.test
```
