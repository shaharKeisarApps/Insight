# Circuit Overlays API Reference

> Circuit Version: 0.32.0

## Core Overlay API (`circuit-overlay`)

### Overlay

The foundational fun interface for all overlays. Defines composable content that receives an `OverlayNavigator` to emit a typed result.

```kotlin
@Stable
public fun interface Overlay<Result : Any> {
    @Composable
    public fun Content(navigator: OverlayNavigator<Result>)
}
```

- `Result` must extend `Any` (non-nullable)
- Implement this to create custom overlays
- The `Content` composable is rendered by `ContentWithOverlays` when the overlay is shown

### OverlayNavigator

Fun interface provided to overlay content to signal completion with a typed result.

```kotlin
@Stable
public fun interface OverlayNavigator<Result : Any> {
    public fun finish(result: Result)
}
```

- Call `finish(result)` exactly once to dismiss the overlay and resume the caller
- Calling `finish()` resumes the suspended `OverlayHost.show()` coroutine with the given result

### OverlayHost

Interface for showing overlays. The `show()` function suspends until the overlay calls `navigator.finish()`.

```kotlin
@Stable
@SubclassOptInRequired(ReadOnlyOverlayApi::class)
public interface OverlayHost {
    public val currentOverlayData: OverlayHostData<Any>?
    public suspend fun <Result : Any> show(overlay: Overlay<Result>): Result
}
```

- `currentOverlayData` -- the currently displayed overlay's data, or null
- `show()` -- suspends until the overlay finishes, returning the typed result
- Internally uses a `Mutex` -- only one overlay can be shown at a time; subsequent calls queue

### OverlayHostData

Tracks data for the currently displayed overlay.

```kotlin
@Stable
public interface OverlayHostData<Result : Any> {
    public val overlay: Overlay<Result>
    public fun finish(result: Result)
}
```

### rememberOverlayHost

Creates and remembers an `OverlayHost` instance.

```kotlin
@Composable
public fun rememberOverlayHost(): OverlayHost
```

- Typically used internally by `ContentWithOverlays`
- Can be used directly when custom overlay host management is needed

### ContentWithOverlays

Wrapper composable that enables overlay support by providing `LocalOverlayHost` and `LocalOverlayState`.

```kotlin
@Composable
public fun ContentWithOverlays(
    modifier: Modifier = Modifier,
    overlayHost: OverlayHost = rememberOverlayHost(),
    content: @Composable () -> Unit,
)
```

- `modifier` -- modifier applied to the root layout
- `overlayHost` -- the OverlayHost instance (defaults to `rememberOverlayHost()`)
- `content` -- the main content; overlays render on top of this
- Provides `LocalOverlayHost` and `LocalOverlayState` to the composition tree

### LocalOverlayHost

CompositionLocal providing the current `OverlayHost`.

```kotlin
public val LocalOverlayHost: ProvidableCompositionLocal<OverlayHost>
```

- Access via `LocalOverlayHost.current`
- Throws error if no `ContentWithOverlays` wrapper is present

### LocalOverlayState

CompositionLocal providing the current `OverlayState`.

```kotlin
public val LocalOverlayState: ProvidableCompositionLocal<OverlayState>
```

- Access via `LocalOverlayState.current`
- Use to conditionally enable/disable UI based on overlay visibility

### OverlayState

Enum representing the current state of the overlay system.

```kotlin
public enum class OverlayState {
    UNAVAILABLE,  // No ContentWithOverlays wrapper present
    HIDDEN,       // Wrapper present, no overlay showing
    SHOWING,      // An overlay is currently displayed
}
```

### OverlayEffect

Composable effect for declaratively showing overlays. The lambda receives `OverlayScope` which provides both `CoroutineScope` and `OverlayHost`.

```kotlin
@Composable
public fun OverlayEffect(
    key: Any? = Unit,
    content: suspend OverlayScope.() -> Unit,
)
```

- `key` -- recomposes when key changes (like `LaunchedEffect`)
- `content` -- suspend lambda scoped to `OverlayScope`
- Inside the lambda, call `show(overlay)` directly (no need for `overlayHost.show()`)
- Preferred over manual `scope.launch { overlayHost.show(...) }` pattern

### OverlayScope

Scope provided to `OverlayEffect` lambda. Combines `CoroutineScope` and `OverlayHost`.

```kotlin
public interface OverlayScope : CoroutineScope, OverlayHost
```

- Provides `show()` from `OverlayHost`
- Provides coroutine context from `CoroutineScope`
- Enables calling `show(overlay)` directly without separate overlay host reference

### AnimatedOverlay

Abstract overlay with built-in enter/exit animation support.

```kotlin
public abstract class AnimatedOverlay<Result : Any>(
    public val enterTransition: EnterTransition,
    public val exitTransition: ExitTransition,
) : Overlay<Result> {

    @Composable
    public abstract fun AnimatedVisibilityScope.AnimatedContent(
        navigator: OverlayNavigator<Result>,
        transitionController: OverlayTransitionController,
    )
}
```

- `enterTransition` -- animation when overlay appears
- `exitTransition` -- animation when overlay dismisses
- Override `AnimatedContent` to define the overlay UI with animation scope
- `transitionController` allows programmatic control of transition progress

### OverlayTransitionController

Controls overlay transition progress, used for predictive back gestures.

```kotlin
public interface OverlayTransitionController {
    public suspend fun seek(progress: Float)
    public suspend fun cancel()
}
```

- `seek(progress)` -- set transition progress (0.0 to 1.0), used by predictive back
- `cancel()` -- cancel the current transition and return to showing state

### @ReadOnlyOverlayApi

Opt-in annotation for OverlayHost subclassing. Indicates the API is read-only and not intended for consumer implementation.

```kotlin
@RequiresOptIn(message = "This API is read-only and not intended for external implementation.")
public annotation class ReadOnlyOverlayApi
```

---

## CircuitX Overlays (`circuitx-overlays`)

### BottomSheetOverlay

Overlay showing a `ModalBottomSheet` with a model and typed result.

```kotlin
public class BottomSheetOverlay<Model : Any, Result : Any> : Overlay<Result> {

    // Non-dismissible constructor (no outside tap dismiss)
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

    // Dismissible constructor (tap outside to dismiss)
    public constructor(
        model: Model,
        onDismiss: (() -> Result),
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

**Parameters:**
- `model` -- data passed to the sheet content
- `onDismiss` -- provides default result when dismissed by tapping outside (dismissible variant only)
- `sheetContainerColor` -- background color of the sheet
- `tonalElevation` -- tonal elevation for the sheet surface
- `sheetShape` -- shape of the sheet (rounded corners, etc.)
- `dragHandle` -- composable for the drag handle indicator
- `skipPartiallyExpandedState` -- skip the half-expanded state
- `isFocusable` -- whether the sheet can receive focus (non-dismissible only)
- `properties` -- `ModalBottomSheetProperties` for the sheet (dismissible only)
- `content` -- composable lambda receiving the model and navigator

### ModalBottomSheetOverlay

Modal variant of `BottomSheetOverlay` using Material 3's `ModalBottomSheet` internally. Follows the same pattern as `BottomSheetOverlay`.

### alertDialogOverlay()

Factory function creating a `BasicAlertDialogOverlay` that returns `DialogResult`.

```kotlin
public fun alertDialogOverlay(
    confirmButton: @Composable (OnClick) -> Unit,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    dismissButton: (@Composable (OnClick) -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
): BasicAlertDialogOverlay<*, DialogResult>
```

**Parameters:**
- `confirmButton` -- composable for confirm button, receives `OnClick` callback
- `icon` -- optional icon composable
- `title` -- optional title composable
- `text` -- optional body text composable
- `dismissButton` -- optional dismiss/cancel button composable
- `properties` -- `DialogProperties` for dialog configuration

### DialogResult

Enum representing the result of a dialog interaction.

```kotlin
public enum class DialogResult {
    Confirm,   // User clicked the confirm button
    Cancel,    // User clicked the dismiss/cancel button
    Dismiss,   // User dismissed by clicking outside or pressing back
}
```

### OnClick

Functional type for click callbacks in dialog overlays.

```kotlin
public fun interface OnClick {
    public operator fun invoke()
}
```

### BasicAlertDialogOverlay

Customizable AlertDialog overlay with a model and typed result.

```kotlin
public class BasicAlertDialogOverlay<Model : Any, Result : Any>(
    public val model: Model,
    public val onDismiss: () -> Result,
    public val dialogProperties: DialogProperties = DialogProperties(),
    public val content: @Composable (Model, OverlayNavigator<Result>) -> Unit,
) : Overlay<Result>
```

**Parameters:**
- `model` -- data model passed to the dialog content
- `onDismiss` -- provides result when dialog is dismissed externally
- `dialogProperties` -- dialog configuration
- `content` -- composable defining the dialog UI

### BasicDialogOverlay

Simple Dialog overlay with a model and typed result.

```kotlin
public class BasicDialogOverlay<Model : Any, Result : Any>(
    public val model: Model,
    public val onDismiss: () -> Result,
    public val dialogProperties: DialogProperties = DialogProperties(),
    public val content: @Composable (Model, OverlayNavigator<Result>) -> Unit,
) : Overlay<Result>
```

**Parameters:**
- `model` -- data model passed to the dialog content
- `onDismiss` -- provides result when dialog is dismissed externally
- `dialogProperties` -- dialog configuration
- `content` -- composable defining the dialog UI

### showFullScreenOverlay

Extension function on `OverlayHost` to show a full-screen overlay for a given `Screen`.

```kotlin
public expect suspend fun OverlayHost.showFullScreenOverlay(screen: Screen): PopResult?
```

- `screen` -- the Circuit `Screen` to render in the overlay
- Returns `PopResult?` -- the result from the screen, or null if dismissed
- Supports predictive back gestures (v0.29.0+)
- Internally uses `AnimatedOverlay` with `PredictiveBackEventHandler`
- Multiplatform: implemented for Android, JVM, Native, and Web targets

---

## Key Imports

### circuit-overlay (core)

```kotlin
import com.slack.circuit.overlay.ContentWithOverlays
import com.slack.circuit.overlay.LocalOverlayHost
import com.slack.circuit.overlay.LocalOverlayState
import com.slack.circuit.overlay.Overlay
import com.slack.circuit.overlay.OverlayEffect
import com.slack.circuit.overlay.OverlayHost
import com.slack.circuit.overlay.OverlayNavigator
import com.slack.circuit.overlay.OverlayScope
import com.slack.circuit.overlay.OverlayState
import com.slack.circuit.overlay.AnimatedOverlay
import com.slack.circuit.overlay.OverlayTransitionController
import com.slack.circuit.overlay.OverlayHostData
import com.slack.circuit.overlay.ReadOnlyOverlayApi
import com.slack.circuit.overlay.rememberOverlayHost
```

### circuitx-overlays (implementations)

```kotlin
import com.slack.circuitx.overlays.BottomSheetOverlay
import com.slack.circuitx.overlays.ModalBottomSheetOverlay
import com.slack.circuitx.overlays.alertDialogOverlay
import com.slack.circuitx.overlays.BasicAlertDialogOverlay
import com.slack.circuitx.overlays.BasicDialogOverlay
import com.slack.circuitx.overlays.DialogResult
import com.slack.circuitx.overlays.OnClick
import com.slack.circuitx.overlays.showFullScreenOverlay
```

---

## Gradle Dependencies

```toml
# gradle/libs.versions.toml
[versions]
circuit = "0.32.0"

[libraries]
circuit-overlay = { module = "com.slack.circuit:circuit-overlay", version.ref = "circuit" }
circuitx-overlays = { module = "com.slack.circuit:circuitx-overlays", version.ref = "circuit" }
```

```kotlin
// build.gradle.kts (shared module)
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.circuit.overlay)
            implementation(libs.circuitx.overlays)
        }
    }
}
```
