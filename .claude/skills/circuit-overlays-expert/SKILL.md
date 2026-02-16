---
name: circuit-overlays-expert
description: Expert guidance on Circuit overlays for dialogs, bottom sheets, and full-screen transient UI. Use for OverlayHost, OverlayEffect, typed overlay results, and overlay testing.
---

# Circuit Overlays Expert

> Circuit Version: 0.32.0
> Trigger: Questions about overlays, dialogs, bottom sheets, full-screen overlays, OverlayHost, OverlayEffect in Circuit

## Overview

Overlays are Circuit's first-class navigation mechanism for transient UI that requires a result -- dialogs, bottom sheets, pickers, and full-screen content. Unlike back-stack navigation, overlays are one-off request/result flows that suspend until the user completes an action and returns a typed result. They do not survive process death.

## When to Use Overlays

- **Bottom sheets**: Pickers, share sheets, option menus, input prompts
- **Confirmation dialogs**: Delete confirmation, destructive action approval
- **Alert dialogs**: Error messages, informational notices
- **Full-screen content**: Image viewers, detail screens shown over current content
- **Any transient UI** that needs to return a typed result to the caller

## Core Types

| Type | Package | Purpose |
|------|---------|---------|
| `Overlay<Result>` | `circuit-overlay` | Fun interface defining overlay content |
| `OverlayNavigator<Result>` | `circuit-overlay` | Fun interface to finish overlay with result |
| `OverlayHost` | `circuit-overlay` | Interface to show overlays (suspend) |
| `OverlayEffect` | `circuit-overlay` | Composable for showing overlays declaratively |
| `OverlayScope` | `circuit-overlay` | Scope combining CoroutineScope + OverlayHost |
| `OverlayState` | `circuit-overlay` | Enum: UNAVAILABLE, HIDDEN, SHOWING |
| `ContentWithOverlays` | `circuit-overlay` | Wrapper composable enabling overlay support |
| `AnimatedOverlay` | `circuit-overlay` | Abstract overlay with enter/exit transitions |

## Setup

Wrap your root content with `ContentWithOverlays` to provide `LocalOverlayHost` and `LocalOverlayState`:

```kotlin
ContentWithOverlays {
    CircuitCompositionLocals(circuit) {
        NavigableCircuitContent(navigator, backStack)
    }
}
```

This is required. Without it, `LocalOverlayHost.current` throws an error.

## Using Overlays in Presenters (Imperative)

Access the overlay host and launch a coroutine to show the overlay:

```kotlin
@CircuitInject(MyScreen::class, AppScope::class)
@Composable
fun MyPresenter(): MyScreen.State {
    val overlayHost = LocalOverlayHost.current
    val scope = rememberStableCoroutineScope()

    return MyScreen.State(
        eventSink = { event ->
            when (event) {
                is ShowOptions -> scope.launch {
                    val result = overlayHost.show(BottomSheetOverlay(...))
                    // Handle result
                }
            }
        }
    )
}
```

## Using OverlayEffect (Declarative -- Recommended)

`OverlayEffect` is the modern, declarative API. It scopes both coroutine and overlay host:

```kotlin
if (state.showConfirmation) {
    OverlayEffect(state.showConfirmation) {
        val result = show(alertDialogOverlay(
            confirmButton = { onClick -> TextButton(onClick = onClick::invoke) { Text("OK") } },
            dismissButton = { onClick -> TextButton(onClick = onClick::invoke) { Text("Cancel") } },
            title = { Text("Confirm?") },
            text = { Text("Are you sure?") },
        ))
        state.eventSink(ConfirmationResult(result))
    }
}
```

## Built-in Overlays (circuitx-overlays)

| Overlay | Purpose |
|---------|---------|
| `BottomSheetOverlay` | Modal bottom sheet with model and typed result |
| `ModalBottomSheetOverlay` | Modal variant of bottom sheet |
| `alertDialogOverlay()` | Factory function returning AlertDialog overlay with DialogResult |
| `BasicAlertDialogOverlay` | Customizable AlertDialog overlay |
| `BasicDialogOverlay` | Simple Dialog overlay |
| `FullScreenOverlay` | Full-screen overlay via `showFullScreenOverlay()` |

## OverlayState

Track overlay visibility via `LocalOverlayState`:

```kotlin
val overlayState = LocalOverlayState.current
// UNAVAILABLE - no ContentWithOverlays wrapper
// HIDDEN - wrapper present, no overlay showing
// SHOWING - an overlay is currently displayed
```

## AnimatedOverlay

Extend `AnimatedOverlay` for overlays with custom enter/exit transitions:

```kotlin
class MyAnimatedOverlay : AnimatedOverlay<Result>(
    enterTransition = fadeIn(),
    exitTransition = fadeOut()
) {
    @Composable
    override fun AnimatedVisibilityScope.AnimatedContent(
        navigator: OverlayNavigator<Result>,
        transitionController: OverlayTransitionController,
    ) { /* content */ }
}
```

## Predictive Back Support

`FullScreenOverlay` (v0.29.0+) supports predictive back gestures via `PredictiveBackEventHandler` and `OverlayTransitionController.seek(progress)`.

## Core Rules

1. **Always wrap with ContentWithOverlays** -- overlays do not work without it
2. **Overlays are one-shot** -- they suspend and return a single result
3. **Only one overlay at a time** -- OverlayHost uses a Mutex internally; sequential `show()` calls queue
4. **Overlays don't survive process death** -- use `PopResult` for navigation results that must persist
5. **Use OverlayEffect for declarative usage** -- prefer over manual `scope.launch { overlayHost.show(...) }`
6. **Always provide onDismiss for dismissible sheets** -- BottomSheetOverlay requires it for outside-tap dismissal
7. **Use rememberStableCoroutineScope** -- not `rememberCoroutineScope` -- for launching overlays in presenters

## Common Pitfalls

- Forgetting `ContentWithOverlays` wrapper causes runtime crash
- Using `rememberCoroutineScope()` instead of `rememberStableCoroutineScope()` leads to scope issues
- Not handling the dismiss case in BottomSheetOverlay leads to suspended coroutines
- Trying to show multiple overlays simultaneously -- they queue, not stack
- Using overlays for persistent navigation -- use Circuit's Navigator/BackStack instead

## Dependencies

```toml
# gradle/libs.versions.toml
[versions]
circuit = "0.32.0"

[libraries]
circuit-overlay = { module = "com.slack.circuit:circuit-overlay", version.ref = "circuit" }
circuitx-overlays = { module = "com.slack.circuit:circuitx-overlays", version.ref = "circuit" }
```

```kotlin
// build.gradle.kts
commonMain.dependencies {
    implementation(libs.circuit.overlay)
    implementation(libs.circuitx.overlays)
}
```

## See Also

- [circuit-expert](../circuit-expert/SKILL.md) -- Core Circuit Screen/Presenter/Ui patterns
- [compose-material3-expert](../compose-material3-expert/SKILL.md) -- M3 dialog and bottom sheet styling
- [circuit-testing-expert](../circuit-testing-expert/SKILL.md) -- Testing overlays in Presenter tests
