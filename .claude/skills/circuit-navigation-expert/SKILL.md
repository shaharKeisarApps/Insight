---
name: circuit-navigation-expert
description: Deep dive into Circuit Navigator for KMP. Covers Navigator API, back stack management, tab navigation, nested navigation, shared element transitions, navigation interception, auth flows, result passing, and gesture navigation. Use for all Circuit navigation patterns beyond basic goTo/pop.
---

# Circuit Navigation Expert Skill (v0.32.0)

## Overview

Deep expertise in Circuit's navigation system for Kotlin Multiplatform. Circuit Navigator provides an imperative API injected into Presenters via `@AssistedInject`. This skill covers the full Navigator API, back stack management, tab navigation with multiple backstacks, nested navigation, shared element transitions, navigation interception, auth flows, result passing, adaptive layouts, and cross-platform gesture navigation.

## When to Use

- Implementing tab navigation with state preservation across tabs
- Passing results between screens (PopResult + rememberAnsweringNavigator)
- Building nested navigation sub-flows (checkout wizard, onboarding)
- Adding shared element transitions between screens
- Intercepting navigation for analytics, auth guards, or A/B routing
- Implementing auth flows with resetRoot graph swap
- Building adaptive two-pane layouts with nested NavigableCircuitContent
- Adding gesture navigation (predictive back Android, swipe iOS)
- Testing navigation with FakeNavigator
- Understanding back stack internals (SaveableBackStack, Record)

## Quick Reference

For full API details, see [reference.md](reference.md).
For production examples (13 patterns), see [examples.md](examples.md).
For Circuit core patterns (Screen, Presenter, Ui), see [circuit-expert](../circuit-expert/SKILL.md).
For overlay patterns, see [circuit-overlays-expert](../circuit-overlays-expert/SKILL.md).
For testing patterns, see [circuit-testing-expert](../circuit-testing-expert/SKILL.md).

## Navigator API Summary

| Method | Signature | Use |
|--------|-----------|-----|
| `goTo` | `fun goTo(screen: Screen): Boolean` | Push screen. Returns false if already top. |
| `pop` | `fun pop(result: PopResult? = null): Screen?` | Pop top. Optional result for previous screen. |
| `resetRoot` | `fun resetRoot(newRoot: Screen, options: StateOptions): List<Screen>` | Replace entire stack (tabs, auth flows). |
| `peek` | `fun peek(): Screen?` | Current top screen. |
| `peekBackStack` | `fun peekBackStack(): ImmutableList<BackStack.Record>` | Full stack snapshot. |
| `popUntil` | Extension: `fun Navigator.popUntil(predicate: (Screen) -> Boolean)` | Pop until predicate matches (exclusive). |

### StateOptions (v0.31.0+)

```kotlin
data class StateOptions(
    val save: Boolean = false,
    val restore: Boolean = false,
    val clear: Boolean = false,
) {
    companion object {
        val Default: StateOptions = StateOptions()
        val SaveAndRestore: StateOptions = StateOptions(save = true, restore = true)
    }
}
```

- `Default` -- Single back stack. No state saving.
- `SaveAndRestore` -- Multiple backstacks. Saves outgoing tab state, restores incoming tab state.

## App Setup Pattern (Gold Standard)

The correct nesting order for a Circuit app:

```kotlin
CircuitCompositionLocals(circuit) {          // 1. Provide Circuit instance
    ContentWithOverlays {                     // 2. Enable overlay system
        SharedElementTransitionLayout {       // 3. Enable shared elements (optional)
            val backStack = rememberSaveableBackStack(HomeScreen)
            val navigator = rememberCircuitNavigator(backStack)
            NavigableCircuitContent(          // 4. Render navigation stack
                navigator = navigator,
                backStack = backStack,
                decoratorFactory = GestureNavigationDecorationFactory(
                    onBackInvoked = navigator::pop,
                ),
            )
        }
    }
}
```

**Rules:**
- `CircuitCompositionLocals` must be outermost -- provides `LocalCircuit`
- `ContentWithOverlays` must wrap `NavigableCircuitContent` -- provides `LocalOverlayHost`
- `SharedElementTransitionLayout` (if used) wraps everything inside `ContentWithOverlays`
- `GestureNavigationDecorationFactory` provides predictive back (Android 14+) and swipe-back (iOS)

## Nested Navigation

Two approaches for nesting screens:

### 1. CircuitContent + onNavEvent (Embedded Screen)

For embedding a screen inside a parent (pager, list-detail) without its own back stack:

```kotlin
CircuitContent(
    screen = ChildScreen(id),
    onNavEvent = { navEvent ->
        // Forward to parent's navigator
        navigator.onNavEvent(navEvent)
    },
)
```

### 2. Nested NavigableCircuitContent (Sub-Flow)

For a multi-step sub-flow (checkout, onboarding) with its own back stack:

```kotlin
val nestedBackStack = rememberSaveableBackStack(FirstStepScreen)
val nestedNavigator = rememberCircuitNavigator(
    backStack = nestedBackStack,
    onRootPop = {
        // Called when user pops past the sub-flow root
        navigator.pop() // or state.eventSink(FlowComplete)
    },
)
NavigableCircuitContent(
    navigator = nestedNavigator,
    backStack = nestedBackStack,
)
```

## Shared Element Transitions

Circuit provides `SharedElementTransitionLayout` via the `circuit-shared-elements` artifact.

```kotlin
// Wrap your navigation hierarchy
SharedElementTransitionLayout {
    ContentWithOverlays {
        NavigableCircuitContent(navigator, backStack, ...)
    }
}

// In Screen A (list item):
Image(
    painter = painter,
    modifier = Modifier.sharedElement(
        rememberSharedContentState(key = "image-${item.id}"),
        animatedVisibilityScope = this@AnimatedVisibilityScope,
    ),
)

// In Screen B (detail):
Image(
    painter = painter,
    modifier = Modifier.sharedElement(
        rememberSharedContentState(key = "image-${item.id}"),
        animatedVisibilityScope = this@AnimatedVisibilityScope,
    ),
)
```

Dependency: `circuit-shared-elements = { module = "com.slack.circuit:circuit-shared-elements", version.ref = "circuit" }`

## Navigation Interception

Use `InterceptingNavigator` from `circuitx-navigation` for analytics, auth guards, and A/B routing:

```kotlin
val interceptingNavigator = rememberInterceptingNavigator(
    navigator = navigator,
    interceptors = listOf(analyticsInterceptor, authInterceptor),
)
```

Interceptors can:
- **Rewrite**: Change the destination screen
- **Consume**: Swallow the navigation event entirely
- **Skip**: Pass through to the next interceptor

## Multiple Backstacks (Tab Navigation)

Use `resetRoot` with `StateOptions.SaveAndRestore`:

```kotlin
navigator.resetRoot(
    newRoot = targetTab,
    options = Navigator.StateOptions.SaveAndRestore,
)
```

This saves the current tab's back stack and restores the target tab's previous state. Each tab maintains its own independent navigation history.

## Auth Flow Pattern

Use `resetRoot` with `StateOptions.Default` to swap the entire navigation graph:

```kotlin
// After login succeeds:
navigator.resetRoot(newRoot = HomeScreen) // Clears login stack entirely

// On logout:
navigator.resetRoot(newRoot = LoginScreen) // Clears home stack entirely
```

No state saving needed -- you want a clean slate when switching between auth states.

## Navigation Testing

Use `FakeNavigator` from `circuit-test`:

```kotlin
val fakeNavigator = FakeNavigator(HomeScreen)
val presenter = MyPresenter(navigator = fakeNavigator, ...)

presenter.test {
    val state = awaitItem()
    state.eventSink(MyScreen.Event.GoToDetail("123"))

    val nextScreen = fakeNavigator.awaitNextScreen()
    assertThat(nextScreen).isEqualTo(DetailScreen("123"))
}
```

For result testing:
```kotlin
// Simulate a pop with result
fakeNavigator.pop(result = PickerResult("selected"))
// The presenter's rememberAnsweringNavigator callback will fire
```

See [circuit-testing-expert](../circuit-testing-expert/SKILL.md) for comprehensive testing patterns.

## Core Rules

1. **Never call Navigator methods in composition** -- Always in event handlers or `LaunchedEffect`.
2. **Use `StateOptions.SaveAndRestore` for tabs** -- Preserves per-tab state on tab switch.
3. **Use `StateOptions.Default` for auth flows** -- Clean slate when swapping auth graphs.
4. **Wrap with `ContentWithOverlays`** -- Required for `LocalOverlayHost` to work.
5. **Use `onRootPop` for nested sub-flows** -- Called when popping past the nested root.
6. **Use `rememberAnsweringNavigator` for results** -- Wraps Navigator to intercept PopResult.
7. **Forward `NavEvent` from embedded screens** -- Use `navigator.onNavEvent(event)` in parent.
8. **Use `GestureNavigationDecorationFactory`** -- Provides platform-appropriate back gestures.

## Common Pitfalls

1. **Wrong `ContentWithOverlays` placement** -- Must wrap `NavigableCircuitContent`, not the other way around.
2. **Missing `SharedElementTransitionLayout`** -- Must wrap `ContentWithOverlays` if using shared elements.
3. **Using `navigator` instead of `answeringNavigator`** -- Results will be lost if you navigate to the picker with the raw navigator instead of the answering wrapper.
4. **Forgetting `onRootPop` in nested navigation** -- Without it, popping the sub-flow root does nothing visible.
5. **Using boolean `resetRoot` params directly** -- Prefer `StateOptions.SaveAndRestore` or `StateOptions.Default` for clarity.
6. **Calling `resetRoot` without tracking selected tab** -- Always update your local tab state alongside `resetRoot`.
7. **Nesting `NavigableCircuitContent` without separate `BackStack`** -- Each nested `NavigableCircuitContent` MUST have its own `rememberSaveableBackStack`.
8. **Using `popUntil` with wrong predicate direction** -- The screen matching the predicate is NOT popped (exclusive).

## See Also

- [circuit-expert](../circuit-expert/SKILL.md) -- Core Circuit patterns (Screen, Presenter, Ui, State)
- [circuit-testing-expert](../circuit-testing-expert/SKILL.md) -- Testing with FakeNavigator, Presenter.test{}
- [circuit-overlays-expert](../circuit-overlays-expert/SKILL.md) -- OverlayHost, BottomSheet, Dialog overlays
- [navigation-expert](../navigation-expert/SKILL.md) -- Paradigm selector, shared element transitions, adaptive layouts
- [deep-links-expert](../deep-links-expert/SKILL.md) -- Deep link routing for Circuit screens
