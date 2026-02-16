# Circuit API Reference (v0.32.0)

Comprehensive API reference for all Circuit artifacts and public types.

---

## Core Types (circuit-runtime)

### Screen

The route/configuration for a UI destination. Screens are the key used to look up Presenter and Ui factories.

```kotlin
// Package: com.slack.circuit.runtime.screen
public interface Screen

// Parcelable on Android for state saving
@Parcelize
data class DetailScreen(val id: String) : Screen

// Singleton screen
@Parcelize
data object HomeScreen : Screen
```

### CircuitUiState

Marker interface for the immutable state snapshot emitted by a Presenter.

```kotlin
// Package: com.slack.circuit.runtime
public interface CircuitUiState
```

Convention: Always include `eventSink: (Event) -> Unit` as the last property.

```kotlin
data class State(
    val title: String,
    val isLoading: Boolean,
    val eventSink: (Event) -> Unit,
) : CircuitUiState
```

### CircuitUiEvent

Marker interface for events flowing from UI to Presenter.

```kotlin
// Package: com.slack.circuit.runtime
public interface CircuitUiEvent
```

Convention: Use sealed interface nested inside the Screen.

```kotlin
sealed interface Event : CircuitUiEvent {
    data object Refresh : Event
    data class ItemClicked(val id: String) : Event
}
```

### CircuitContext

Context object providing access to additional data during Presenter/Ui creation. Allows storing and retrieving tagged objects.

```kotlin
// Package: com.slack.circuit.runtime
public interface CircuitContext {
    val parent: CircuitContext?
    fun <T> tag(key: KClass<T>): T?
    fun <T> putTag(key: KClass<T>, value: T)
}
```

### PopResult

Marker interface for results passed back when popping a screen.

```kotlin
// Package: com.slack.circuit.runtime.screen
public interface PopResult
```

### NavEvent

Sealed interface representing navigation actions. Used for nested navigation with `onNavEvent`.

```kotlin
// Package: com.slack.circuit.foundation
public sealed interface NavEvent : CircuitUiEvent {
    /** Corresponds to Navigator.goTo */
    public data class GoTo(val screen: Screen) : NavEvent

    /** Corresponds to Navigator.pop */
    public data class Pop(val result: PopResult? = null) : NavEvent

    /** Corresponds to Navigator.resetRoot */
    public data class ResetRoot(
        val newRoot: Screen,
        val options: Navigator.StateOptions,
    ) : NavEvent {
        public constructor(
            newRoot: Screen,
            saveState: Boolean = false,
            restoreState: Boolean = false,
            clearState: Boolean = false,
        ) : this(newRoot, Navigator.StateOptions(save = saveState, restore = restoreState, clear = clearState))
    }
}
```

---

## Navigator (circuit-runtime)

Core navigation interface. See [circuit-navigation-expert/reference.md](../circuit-navigation-expert/reference.md) for the complete Navigator API reference including:
- Full `Navigator` interface with `StateOptions`
- Extension functions (`popUntil`, `onNavEvent`)
- `SaveableBackStack` complete API
- `NavigableCircuitContent` parameters
- `CircuitContent` overloads
- `NavEvent` sealed interface
- `rememberCircuitNavigator` / `rememberSaveableBackStack`
- `PopResult` / `rememberAnsweringNavigator`
- `InterceptingNavigator` / `NavigationInterceptor`
- `GestureNavigationDecorationFactory`
- `SharedElementTransitionLayout`
- `BackStackRecordLocalProvider`

### Quick Reference

```kotlin
interface Navigator {
    fun goTo(screen: Screen): Boolean
    fun pop(result: PopResult? = null): Screen?
    fun resetRoot(newRoot: Screen, options: StateOptions = StateOptions.Default): ImmutableList<Screen>
    fun peek(): Screen?
    fun peekBackStack(): ImmutableList<BackStack.Record>
}
```

---

## Presenter (circuit-runtime-presenter)

### Presenter Interface

```kotlin
// Package: com.slack.circuit.runtime.presenter
@Stable
public interface Presenter<UiState : CircuitUiState> {

    /** Composable function that produces the current UI state. */
    @Composable
    public fun present(): UiState

    /** Factory for creating Presenters. */
    @Stable
    public fun interface Factory {
        /**
         * Creates a Presenter for the given screen, or returns null if this factory
         * does not handle the screen.
         */
        public fun create(
            screen: Screen,
            navigator: Navigator,
            context: CircuitContext,
        ): Presenter<*>?
    }
}
```

### NonPausablePresenter

A Presenter that is never paused when another screen is pushed on top. Useful for presenters that must continue running (e.g., music playback).

```kotlin
// Package: com.slack.circuit.foundation
public interface NonPausablePresenter<UiState : CircuitUiState> : Presenter<UiState>
```

---

## Ui (circuit-runtime-ui)

### Ui Interface

```kotlin
// Package: com.slack.circuit.runtime.ui
@Stable
public interface Ui<UiState : CircuitUiState> {

    /** Composable function that renders the given state. */
    @Composable
    public fun Content(state: UiState, modifier: Modifier)

    /** Factory for creating Ui instances. */
    @Stable
    public fun interface Factory {
        /**
         * Creates a Ui for the given screen, or returns null if this factory
         * does not handle the screen.
         */
        public fun create(
            screen: Screen,
            context: CircuitContext,
        ): Ui<*>?
    }
}
```

---

## Circuit Configuration (circuit-foundation)

### Circuit Class

```kotlin
// Package: com.slack.circuit.foundation
@Stable
public class Circuit private constructor(builder: Builder) {

    /** The AnimatedNavDecorator.Factory used for animated transitions. */
    public val animatedNavDecoratorFactory: AnimatedNavDecorator.Factory?

    /** The default NavDecoration for non-animated transitions. */
    public val defaultNavDecoration: NavDecoration

    /** Whether presenters should use lifecycle-aware presentation. */
    public val presentWithLifecycle: Boolean

    /** Look up the next Presenter for a given screen. */
    public fun <UiState : CircuitUiState> nextPresenter(
        screen: Screen,
        navigator: Navigator,
        context: CircuitContext,
    ): Presenter<UiState>?

    /** Look up the next Ui for a given screen. */
    public fun <UiState : CircuitUiState> nextUi(
        screen: Screen,
        context: CircuitContext,
    ): Ui<UiState>?

    public class Builder {
        public fun addPresenterFactory(factory: Presenter.Factory): Builder
        public fun addPresenterFactory(vararg factories: Presenter.Factory): Builder
        public fun addPresenterFactories(factories: Iterable<Presenter.Factory>): Builder

        public fun addUiFactory(factory: Ui.Factory): Builder
        public fun addUiFactory(vararg factories: Ui.Factory): Builder
        public fun addUiFactories(factories: Iterable<Ui.Factory>): Builder

        public fun addBackStackRecordLocalProvider(provider: BackStackRecordLocalProvider): Builder
        public fun addBackStackRecordLocalProvider(vararg providers: BackStackRecordLocalProvider): Builder
        public fun addBackStackRecordLocalProviders(providers: Iterable<BackStackRecordLocalProvider>): Builder
        public fun clearBackStackRecordLocalProviders(): Builder

        public fun setDefaultNavDecoration(decoration: NavDecoration): Builder
        public fun setAnimatedNavDecoratorFactory(factory: AnimatedNavDecorator.Factory?): Builder
        public fun setOnUnavailableContent(content: @Composable (Screen, Modifier) -> Unit): Builder
        public fun eventListenerFactory(factory: EventListener.Factory?): Builder
        public fun presentWithLifecycle(enabled: Boolean): Builder

        public fun build(): Circuit
    }
}
```

### CircuitContent

Runs a single Presenter/Ui pairing for a given screen. Three overloads: standalone, with `onNavEvent`, and with `navigator`. See [circuit-navigation-expert/reference.md](../circuit-navigation-expert/reference.md) for complete overload signatures.

```kotlin
@Composable
public fun CircuitContent(
    screen: Screen,
    modifier: Modifier = Modifier,
    onNavEvent: (NavEvent) -> Unit = {},  // For embedded screens
    circuit: Circuit = requireNotNull(LocalCircuit.current),
)
```

### NavigableCircuitContent

Manages navigation with a back stack and navigator. See [circuit-navigation-expert/reference.md](../circuit-navigation-expert/reference.md) for complete parameter documentation.

```kotlin
@Composable
public fun NavigableCircuitContent(
    navigator: Navigator,
    backStack: SaveableBackStack,
    modifier: Modifier = Modifier,
    circuit: Circuit = requireNotNull(LocalCircuit.current),
    decoratorFactory: AnimatedNavDecorator.Factory? = circuit.animatedNavDecoratorFactory,
)
```

### CircuitCompositionLocals

Provides the Circuit instance to the composition via `LocalCircuit`.

```kotlin
// Package: com.slack.circuit.foundation

@Composable
public fun CircuitCompositionLocals(
    circuit: Circuit,
    content: @Composable () -> Unit,
)
```

### LocalCircuit

CompositionLocal providing access to the current Circuit instance.

```kotlin
// Package: com.slack.circuit.foundation
public val LocalCircuit: ProvidableCompositionLocal<Circuit?>
```

### rememberCircuitNavigator

Creates and remembers a Navigator backed by the given back stack.

```kotlin
// Package: com.slack.circuit.foundation

@Composable
public fun rememberCircuitNavigator(
    backStack: SaveableBackStack,
    onRootPop: () -> Unit = {},
): Navigator
```

### rememberSaveableBackStack

Creates and remembers a SaveableBackStack with an initial root screen.

```kotlin
// Package: com.slack.circuit.backstack

@Composable
public fun rememberSaveableBackStack(root: Screen): SaveableBackStack

@Composable
public fun rememberSaveableBackStack(
    initialScreens: List<Screen>,
): SaveableBackStack
```

### NavDecoration

Interface for decorating navigation transitions.

```kotlin
// Package: com.slack.circuit.backstack
public interface NavDecoration {
    @Composable
    public fun <T> DecoratedContent(
        args: ImmutableList<T>,
        backStackDepth: Int,
        modifier: Modifier,
        content: @Composable (T) -> Unit,
    )
}
```

### NavigatorDefaults

Default NavDecoration implementations.

```kotlin
// Package: com.slack.circuit.foundation
public object NavigatorDefaults {
    /** Empty decoration with no animation. */
    public val EmptyDecoration: NavDecoration
    /** Default animated decoration. */
    public val DefaultDecoration: NavDecoration
}
```

### AnimatedNavDecorator / AnimatedScreenTransform

```kotlin
// Package: com.slack.circuit.foundation.animation
public interface AnimatedNavDecorator {
    public fun interface Factory {
        public fun create(): AnimatedNavDecorator
    }
}

public interface AnimatedScreenTransform {
    fun AnimatedContentTransitionScope<*>.enterTransition(): EnterTransition
    fun AnimatedContentTransitionScope<*>.exitTransition(): ExitTransition
}
```

---

## State Retention (circuit-retained)

### rememberRetained

Retains a value across configuration changes (but NOT process death). Backed by ViewModel on Android.

```kotlin
// Package: com.slack.circuit.retained

@Composable
public fun <T : Any> rememberRetained(vararg inputs: Any?, init: () -> T): T

@Composable
public fun <T : Any> rememberRetained(
    vararg inputs: Any?,
    key: String? = null,
    init: () -> T,
): T
```

### rememberRetainedSaveable

Retains a value across configuration changes AND process death (requires Parcelable/Serializable).

```kotlin
// Package: com.slack.circuit.retained

@Composable
public fun <T : Any> rememberRetainedSaveable(
    vararg inputs: Any?,
    saver: Saver<T, out Any> = autoSaver(),
    key: String? = null,
    init: () -> T,
): T
```

### collectAsRetainedState

Collects a Flow and retains the latest value across configuration changes.

```kotlin
// Package: com.slack.circuit.retained

@Composable
public fun <T> Flow<T>.collectAsRetainedState(
    initial: T,
): State<T>

@Composable
public fun <T> StateFlow<T>.collectAsRetainedState(): State<T>
```

### produceRetainedState

Produces a retained state value asynchronously.

```kotlin
// Package: com.slack.circuit.retained

@Composable
public fun <T> produceRetainedState(
    initialValue: T,
    vararg keys: Any?,
    producer: suspend ProduceStateScope<T>.() -> Unit,
): State<T>
```

### produceAndCollectAsRetainedState (v0.32.0)

Combined produce and collect with retention.

```kotlin
// Package: com.slack.circuit.retained

@Composable
public fun <T> produceAndCollectAsRetainedState(
    initialValue: T,
    flow: Flow<T>,
): State<T>
```

### RetainedStateHolder

Interface for managing retained state. Used internally by Circuit.

```kotlin
// Package: com.slack.circuit.retained
public interface RetainedStateHolder {
    @Composable
    public fun RetainedStateProvider(key: String, content: @Composable () -> Unit)
    public fun removeState(key: String)
}
```

---

## Overlay (circuit-overlay)

### Overlay

Fun interface representing composable content shown on top of other content via an OverlayHost.

```kotlin
// Package: com.slack.circuit.overlay

@Stable
public fun interface Overlay<Result : Any> {
    @Composable
    public fun Content(navigator: OverlayNavigator<Result>)
}
```

### OverlayNavigator

Fun interface used by Overlay instances to signal completion with a result.

```kotlin
// Package: com.slack.circuit.overlay

@Stable
public fun interface OverlayNavigator<Result : Any> {
    /** Finishes the overlay and delivers the result. */
    public fun finish(result: Result)
}
```

### OverlayHost

Interface that manages showing overlays and suspending until they return a result.

```kotlin
// Package: com.slack.circuit.overlay

@Stable
public interface OverlayHost {
    /** The current overlay data, or null if no overlay is showing. */
    public val currentOverlayData: OverlayHostData<Any>?

    /** Shows an overlay and suspends until it finishes with a result. */
    public suspend fun <Result : Any> show(overlay: Overlay<Result>): Result
}
```

### OverlayHostData

Data associated with a currently-showing overlay.

```kotlin
// Package: com.slack.circuit.overlay
public interface OverlayHostData<Result : Any> {
    public val overlay: Overlay<Result>
    public fun finish(result: Result)
}
```

### ContentWithOverlays

Composable that enables the overlay system. Must wrap content that uses overlays.

```kotlin
// Package: com.slack.circuit.overlay

@Composable
public fun ContentWithOverlays(
    overlayHost: OverlayHost = rememberOverlayHost(),
    content: @Composable () -> Unit,
)
```

### rememberOverlayHost

Creates and remembers an OverlayHost instance.

```kotlin
// Package: com.slack.circuit.overlay

@Composable
public fun rememberOverlayHost(): OverlayHost
```

### OverlayEffect

Composable effect for showing an overlay from within a Presenter's `present()`.

```kotlin
// Package: com.slack.circuit.overlay

@Composable
public fun OverlayEffect(
    overlayHost: OverlayHost = LocalOverlayHost.current,
    body: suspend OverlayHost.() -> Unit,
)
```

### LocalOverlayHost

CompositionLocal providing access to the current OverlayHost.

```kotlin
// Package: com.slack.circuit.overlay
public val LocalOverlayHost: ProvidableCompositionLocal<OverlayHost>
```

### LocalOverlayState

CompositionLocal providing the current overlay state.

```kotlin
// Package: com.slack.circuit.overlay
public val LocalOverlayState: ProvidableCompositionLocal<OverlayState>
```

### OverlayState

Enum representing the current state of the overlay system.

```kotlin
// Package: com.slack.circuit.overlay
public enum class OverlayState {
    UNAVAILABLE,  // No OverlayHost is available in this composition
    HIDDEN,       // OverlayHost exists but no overlay is showing
    SHOWING,      // An overlay is currently being displayed
}
```

### OverlayScope

Scope interface for overlay operations.

```kotlin
// Package: com.slack.circuit.overlay
public interface OverlayScope
```

### AnimatedOverlay

An Overlay with animation capabilities for enter/exit transitions.

```kotlin
// Package: com.slack.circuit.overlay
public interface AnimatedOverlay<Result : Any> : Overlay<Result>
```

### OverlayTransitionController

Controls overlay transition animations.

```kotlin
// Package: com.slack.circuit.overlay
public interface OverlayTransitionController {
    public companion object
}
```

---

## Common Overlays (circuitx-overlays)

### BottomSheetOverlay

Material 3 bottom sheet overlay.

```kotlin
// Package: com.slack.circuitx.overlays

public class BottomSheetOverlay<Model : Any, Result : Any> : Overlay<Result> {

    /** Constructor for non-dismissible bottom sheet. */
    public constructor(
        model: Model,
        sheetContainerColor: Color? = null,
        tonalElevation: Dp? = null,
        sheetShape: Shape? = null,
        dragHandle: @Composable (() -> Unit)? = null,
        skipPartiallyExpandedState: Boolean = false,
        isFocusable: Boolean = true,
        content: @Composable (Model, OverlayNavigator<Result>) -> Unit,
    )

    /** Constructor for dismissible bottom sheet (tapping outside dismisses). */
    public constructor(
        model: Model,
        onDismiss: () -> Result,
        sheetContainerColor: Color? = null,
        tonalElevation: Dp? = null,
        sheetShape: Shape? = null,
        dragHandle: @Composable (() -> Unit)? = null,
        skipPartiallyExpandedState: Boolean = false,
        properties: ModalBottomSheetProperties = DEFAULT_PROPERTIES,
        content: @Composable (Model, OverlayNavigator<Result>) -> Unit,
    )
}
```

### ModalBottomSheetOverlay

Alias/variant of BottomSheetOverlay using ModalBottomSheet internally.

### AlertDialogOverlay

Material 3 AlertDialog-based overlay.

```kotlin
// Package: com.slack.circuitx.overlays

public class AlertDialogOverlay<Model : Any, Result : Any>(
    model: Model,
    onDismissRequest: () -> Result,
    content: @Composable (Model, OverlayNavigator<Result>) -> Unit,
) : Overlay<Result>
```

### BasicAlertDialogOverlay

Basic (non-Material) AlertDialog overlay for custom layouts.

```kotlin
// Package: com.slack.circuitx.overlays

public class BasicAlertDialogOverlay<Model : Any, Result : Any>(
    model: Model,
    onDismissRequest: () -> Result,
    properties: DialogProperties = DialogProperties(),
    content: @Composable (Model, OverlayNavigator<Result>) -> Unit,
) : Overlay<Result>
```

### BasicDialogOverlay

Basic Dialog overlay (not AlertDialog) for fully custom dialog content.

```kotlin
// Package: com.slack.circuitx.overlays

public class BasicDialogOverlay<Model : Any, Result : Any>(
    model: Model,
    onDismissRequest: () -> Result,
    properties: DialogProperties = DialogProperties(),
    content: @Composable (Model, OverlayNavigator<Result>) -> Unit,
) : Overlay<Result>
```

### FullScreenOverlay

Overlay that takes over the full screen. Shows a separate Circuit screen as an overlay.

```kotlin
// Package: com.slack.circuitx.overlays

public class FullScreenOverlay<Result : Any>(
    screen: Screen,
    callbacks: Callbacks<Result>? = null,
) : Overlay<Result>
```

### showFullScreenOverlay

Extension function on OverlayHost to show a FullScreenOverlay.

```kotlin
// Package: com.slack.circuitx.overlays

public suspend fun <Result : Any> OverlayHost.showFullScreenOverlay(
    screen: Screen,
    callbacks: FullScreenOverlay.Callbacks<Result>? = null,
): Result
```

---

## Codegen (circuit-codegen-annotations)

### @CircuitInject

Annotation that generates Presenter.Factory or Ui.Factory implementations and contributes them to the DI graph.

```kotlin
// Package: com.slack.circuit.codegen.annotations

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
public expect annotation class CircuitInject(
    val screen: KClass<out Screen>,
    val scope: KClass<*>,
)
```

### Codegen Modes

Configure in `build.gradle.kts`:

```kotlin
ksp { arg("circuit.codegen.mode", "<MODE>") }
```

| Mode | Value | DI Framework | Annotations Used |
|------|-------|--------------|------------------|
| Metro | `METRO` | Metro | `@ContributesBinding`, `@Inject` |
| Anvil | `ANVIL` | Dagger + Anvil | `@ContributesMultibinding`, `@Inject` |
| Hilt | `HILT` | Dagger + Hilt | `@HiltInstallIn`, `@Inject` |
| kotlin-inject | `KOTLIN_INJECT` | kotlin-inject | `@ContributesMultibinding`, `@Inject` |

### Metro Mode Generated Code Example

For a `@CircuitInject` annotated `@AssistedFactory`:

```kotlin
// Source
@AssistedInject
class HomePresenter(
    @Assisted private val navigator: Navigator,
    private val repo: ItemRepository,
) : Presenter<HomeScreen.State> {

    @CircuitInject(HomeScreen::class, AppScope::class)
    @AssistedFactory
    fun interface Factory {
        fun create(navigator: Navigator): HomePresenter
    }
}

// Generated (approximate)
@ContributesBinding(AppScope::class)
class HomePresenter_PresenterFactory @Inject constructor(
    private val factory: HomePresenter.Factory,
) : Presenter.Factory {
    override fun create(screen: Screen, navigator: Navigator, context: CircuitContext): Presenter<*>? {
        return when (screen) {
            is HomeScreen -> factory.create(navigator)
            else -> null
        }
    }
}
```

### Metro Mode @CircuitInject on Composable UI

```kotlin
// Source
@CircuitInject(HomeScreen::class, AppScope::class)
@Composable
fun HomeUi(state: HomeScreen.State, modifier: Modifier = Modifier) {
    // UI content
}

// Generated (approximate)
@ContributesBinding(AppScope::class)
class HomeUi_UiFactory @Inject constructor() : Ui.Factory {
    override fun create(screen: Screen, context: CircuitContext): Ui<*>? {
        return when (screen) {
            is HomeScreen -> ui<HomeScreen.State> { state, modifier -> HomeUi(state, modifier) }
            else -> null
        }
    }
}
```

---

## Effects (circuitx-effects)

### ImpressionEffect

A single-fire side effect that runs only once until forgotten or inputs change. Useful for analytics/logging.

```kotlin
// Package: com.slack.circuitx.effects

@Composable
public fun ImpressionEffect(vararg inputs: Any?, impression: () -> Unit)
```

### LaunchedImpressionEffect

Suspendable variant of ImpressionEffect.

```kotlin
// Package: com.slack.circuitx.effects

@Composable
public fun LaunchedImpressionEffect(vararg inputs: Any?, impression: suspend () -> Unit)
```

### rememberImpressionNavigator

Creates a navigator that re-runs the impression after navigation events.

```kotlin
// Package: com.slack.circuitx.effects

@Composable
public fun rememberImpressionNavigator(vararg inputs: Any?, impression: () -> Unit)
```

### ToastEffect

Effect for showing platform toasts.

```kotlin
// Package: com.slack.circuitx.effects

@Composable
public fun ToastEffect(message: String, duration: Int = Toast.LENGTH_SHORT)
```

---

## Navigation Extensions (circuitx-navigation, circuitx-gesture-navigation)

For complete API reference for navigation interceptors and gesture navigation, see [circuit-navigation-expert/reference.md](../circuit-navigation-expert/reference.md).

### Quick Reference

```kotlin
// Intercepting navigation
val interceptingNav = rememberInterceptingNavigator(navigator, listOf(interceptor))

// Gesture navigation (predictive back + swipe)
NavigableCircuitContent(
    decoratorFactory = GestureNavigationDecorationFactory(onBackInvoked = navigator::pop),
)
```

---

## Shared Elements (circuit-shared-elements)

For complete shared element transition API, see [circuit-navigation-expert/reference.md](../circuit-navigation-expert/reference.md).

```kotlin
SharedElementTransitionLayout {
    ContentWithOverlays {
        NavigableCircuitContent(navigator, backStack, ...)
    }
}
```

Dependency: `circuit-shared-elements = { module = "com.slack.circuit:circuit-shared-elements", version.ref = "circuit" }`

---

## BackStack (circuit-backstack)

For complete BackStack and SaveableBackStack API, see [circuit-navigation-expert/reference.md](../circuit-navigation-expert/reference.md).

### Quick Reference

```kotlin
val backStack = rememberSaveableBackStack(HomeScreen)
// Properties: records, topRecord, rootRecord, isAtRoot, isEmpty
// Methods: push, pop, popUntil, replaceTop, resetRoot, saveState, restoreState
```

### BackStackRecordLocalProvider

Provides CompositionLocal values scoped to individual back stack records.

```kotlin
public fun interface BackStackRecordLocalProvider {
    public fun providedValuesFor(record: BackStack.Record): ProvidedValues
}
```

---

## EventListener (circuit-foundation)

### EventListener Interface

Callbacks for observing the Circuit lifecycle. Useful for logging, analytics, and debugging.

```kotlin
// Package: com.slack.circuit.foundation

public interface EventListener {
    public fun start() {}
    public fun onBeforeCreatePresenter(screen: Screen, navigator: Navigator, context: CircuitContext) {}
    public fun onAfterCreatePresenter(screen: Screen, navigator: Navigator, presenter: Presenter<*>?, context: CircuitContext) {}
    public fun onBeforeCreateUi(screen: Screen, context: CircuitContext) {}
    public fun onAfterCreateUi(screen: Screen, ui: Ui<*>?, context: CircuitContext) {}
    public fun onStartPresent() {}
    public fun onDisposePresent() {}
    public fun onStartContent() {}
    public fun onDisposeContent() {}
    public fun onState(state: CircuitUiState) {}
    public fun onUnavailableContent(screen: Screen, presenter: Presenter<*>?, ui: Ui<*>?, context: CircuitContext) {}
    public fun dispose() {}

    /** Factory for creating EventListeners. */
    public fun interface Factory {
        public fun create(screen: Screen): EventListener
    }

    public companion object {
        /** A no-op EventListener. */
        public val NONE: EventListener
    }
}
```

---

## Testing (circuit-test)

### Presenter.test

Extension function for testing presenters using Turbine.

```kotlin
// Package: com.slack.circuit.test

public suspend fun <UiState : CircuitUiState> Presenter<UiState>.test(
    timeout: Duration = 1.seconds,
    name: String? = null,
    validate: suspend TurbineTestContext<UiState>.() -> Unit,
)
```

### FakeNavigator

Test fake that implements Navigator with real back stack behavior and event recording.

```kotlin
// Package: com.slack.circuit.test

public class FakeNavigator(
    root: Screen,
) : Navigator {
    // Implements all Navigator methods
    // Records events for assertion

    /** Awaits the next screen navigated to via goTo(). */
    public suspend fun awaitNextScreen(): Screen

    /** Awaits the next pop event. */
    public suspend fun awaitPop(): PopEvent

    /** Awaits the next resetRoot event. */
    public suspend fun awaitResetRoot(): ResetRootEvent

    /** Asserts no pending navigation events. */
    public fun expectNoEvents()
}
```

---

## Key Imports

```kotlin
// Core
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.runtime.screen.PopResult
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitContext
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter

// Foundation
import com.slack.circuit.foundation.Circuit
import com.slack.circuit.foundation.CircuitCompositionLocals
import com.slack.circuit.foundation.CircuitContent
import com.slack.circuit.foundation.NavigableCircuitContent
import com.slack.circuit.foundation.LocalCircuit
import com.slack.circuit.foundation.NavEvent
import com.slack.circuit.foundation.NonPausablePresenter
import com.slack.circuit.foundation.rememberCircuitNavigator
import com.slack.circuit.foundation.onNavEvent

// BackStack
import com.slack.circuit.backstack.rememberSaveableBackStack
import com.slack.circuit.backstack.SaveableBackStack
import com.slack.circuit.backstack.BackStack
import com.slack.circuit.backstack.NavDecoration

// Retained
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.retained.rememberRetainedSaveable
import com.slack.circuit.retained.collectAsRetainedState
import com.slack.circuit.retained.produceRetainedState

// Overlay
import com.slack.circuit.overlay.Overlay
import com.slack.circuit.overlay.OverlayHost
import com.slack.circuit.overlay.OverlayNavigator
import com.slack.circuit.overlay.ContentWithOverlays
import com.slack.circuit.overlay.LocalOverlayHost
import com.slack.circuit.overlay.LocalOverlayState
import com.slack.circuit.overlay.OverlayState
import com.slack.circuit.overlay.OverlayEffect
import com.slack.circuit.overlay.rememberOverlayHost

// CircuitX Overlays
import com.slack.circuitx.overlays.BottomSheetOverlay
import com.slack.circuitx.overlays.AlertDialogOverlay
import com.slack.circuitx.overlays.BasicAlertDialogOverlay
import com.slack.circuitx.overlays.BasicDialogOverlay
import com.slack.circuitx.overlays.FullScreenOverlay

// CircuitX Effects
import com.slack.circuitx.effects.ImpressionEffect
import com.slack.circuitx.effects.LaunchedImpressionEffect
import com.slack.circuitx.effects.ToastEffect

// CircuitX Navigation
import com.slack.circuitx.navigation.NavigationInterceptor
import com.slack.circuitx.navigation.rememberInterceptingNavigator

// CircuitX Gesture Navigation
import com.slack.circuitx.gesturenavigation.GestureNavigationDecorationFactory

// CircuitX Android
import com.slack.circuitx.android.rememberAndroidScreenAwareNavigator

// Shared Elements
import com.slack.circuit.sharedelements.SharedElementTransitionLayout

// Codegen
import com.slack.circuit.codegen.annotations.CircuitInject

// Test
import com.slack.circuit.test.test
import com.slack.circuit.test.FakeNavigator

// Ui
import com.slack.circuit.runtime.ui.Ui

// Metro (DI)
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
```

---

## Gradle Dependencies (libs.versions.toml)

```toml
[versions]
circuit = "0.32.0"

[libraries]
# Core
circuit-foundation = { module = "com.slack.circuit:circuit-foundation", version.ref = "circuit" }
circuit-runtime = { module = "com.slack.circuit:circuit-runtime", version.ref = "circuit" }
circuit-runtime-presenter = { module = "com.slack.circuit:circuit-runtime-presenter", version.ref = "circuit" }
circuit-runtime-ui = { module = "com.slack.circuit:circuit-runtime-ui", version.ref = "circuit" }

# State Retention
circuit-retained = { module = "com.slack.circuit:circuit-retained", version.ref = "circuit" }

# Overlay
circuit-overlay = { module = "com.slack.circuit:circuit-overlay", version.ref = "circuit" }

# Codegen
circuit-codegen = { module = "com.slack.circuit:circuit-codegen", version.ref = "circuit" }
circuit-codegen-annotations = { module = "com.slack.circuit:circuit-codegen-annotations", version.ref = "circuit" }

# Testing
circuit-test = { module = "com.slack.circuit:circuit-test", version.ref = "circuit" }

# Extensions
circuitx-overlays = { module = "com.slack.circuit:circuitx-overlays", version.ref = "circuit" }
circuitx-gesture-navigation = { module = "com.slack.circuit:circuitx-gesture-navigation", version.ref = "circuit" }
circuitx-effects = { module = "com.slack.circuit:circuitx-effects", version.ref = "circuit" }
circuitx-navigation = { module = "com.slack.circuit:circuitx-navigation", version.ref = "circuit" }
circuitx-android = { module = "com.slack.circuit:circuitx-android", version.ref = "circuit" }

# Shared Elements
circuit-shared-elements = { module = "com.slack.circuit:circuit-shared-elements", version.ref = "circuit" }

# BackStack
circuit-backstack = { module = "com.slack.circuit:circuit-backstack", version.ref = "circuit" }
```
