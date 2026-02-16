---
name: navigation-expert
description: KMP navigation paradigm selector and cross-cutting navigation concerns. Covers paradigm detection, shared element transitions, adaptive layout navigation, auth flow architecture, and navigation performance. Routes to circuit-navigation-expert or viewmodel-nav3-expert for paradigm-specific patterns.
---

# Navigation Expert Skill

## Overview

Paradigm selector and cross-cutting navigation expertise for KMP. Helps choose between Circuit Navigator and Navigation 3 + ViewModel, detect existing paradigms in a project, and implement shared concerns like shared element transitions, adaptive layouts, auth flows, and navigation performance.

For paradigm-specific deep dives:
- **Circuit Navigator**: [circuit-navigation-expert](../circuit-navigation-expert/SKILL.md)
- **Navigation 3 + ViewModel**: [viewmodel-nav3-expert](../viewmodel-nav3-expert/SKILL.md)

## When to Use

- Choosing between Circuit Navigator and Navigation 3 for a new project
- Detecting which paradigm an existing project uses
- Implementing shared element transitions (both paradigms)
- Building adaptive layouts that change navigation based on screen size
- Designing auth flow architecture (login gates, logout graph swap)
- Optimizing navigation performance (lazy init, stack depth, profiling)

## Paradigm Default

> **Default: Circuit MVI** -- To change, update this section and the agent spec.

Both Circuit Navigator and Navigation 3 are **fully KMP** (Android, iOS, Desktop, Web). The choice is purely a matter of preference and team familiarity -- not platform capability.

### How Paradigm Is Selected

1. **Project already uses one?** Keep it. Check for existing `Screen`/`Presenter` (Circuit) or `NavKey`/`NavDisplay` (Nav3) in the codebase. Never switch a project's paradigm mid-flight.
2. **New project, no preference stated?** Use **Circuit MVI** (the current default).
3. **Developer explicitly requests Nav3?** Use Navigation 3 + ViewModel.

### Changing the Default

To switch the default paradigm from Circuit to Nav3 (or vice versa):
1. Change the `> **Default:**` line above
2. Update `spec-mobile-kmp.md` directive 3 ("recommended default")
3. Update `MEMORY.md` "Dual Paradigm Architecture" section

### Quick Paradigm Detection

```kotlin
// Check for Circuit indicators:
// - @Parcelize data class/object ... : Screen
// - class ...Presenter ... : Presenter<...>
// - @CircuitInject
// - NavigableCircuitContent
// - rememberCircuitNavigator

// Check for Nav3 indicators:
// - @Serializable data class/object ... : NavKey
// - class ...ViewModel ... : ViewModel()
// - NavDisplay
// - rememberNavBackStack
// - entryProvider { entry<...> { } }
```

## Decision Matrix

| Concern | Circuit Navigator | Navigation 3 | Notes |
|---------|:-:|:-:|-------|
| MVI with Presenter | Preferred | -- | Navigator injected via @AssistedInject |
| ViewModel + StateFlow | -- | Preferred | ViewModel scoping via entry decorators |
| Bottom sheets, dialogs | Preferred | Manual | Circuit has first-class OverlayHost |
| Tab navigation | Preferred | Manual | resetRoot with SaveAndRestore vs separate backstacks |
| Deep links | Yes | Yes | Both support custom URI routing |
| All KMP targets | Yes | Yes | Both work on Android, iOS, Desktop, Web |
| Type-safe arguments | Screen data class | @Serializable NavKey | Both are type-safe |
| Lifecycle-scoped state | rememberRetained | ViewModel + SavedStateHandle | Both survive config changes |
| Result passing | PopResult + rememberAnsweringNavigator | Shared ViewModel / callback | Circuit has first-class API |
| Shared element transitions | SharedElementTransitionLayout | SharedTransitionLayout | Both via Compose foundation |
| Gesture navigation | GestureNavigationDecorationFactory | predictivePopTransitionSpec | Circuit has richer API |
| Adaptive layouts | Nested NavigableCircuitContent | SceneStrategy (TwoPane, ListDetail) | Nav3 has built-in strategies |
| Navigation testing | FakeNavigator | Callback-based (no fake needed) | Circuit has dedicated test artifact |
| Multiple backstacks | resetRoot + SaveAndRestore | Multiple rememberNavBackStack | Both supported, different approaches |
| Navigation interception | InterceptingNavigator | Manual (no built-in) | Circuit has dedicated interceptor API |
| API stability | Stable (0.32.0) | Alpha (1.0.0-alpha01) | Nav3 APIs may change |

## Shared Element Transitions (Both Paradigms)

Shared element transitions work through Compose's `SharedTransitionLayout` / `SharedTransitionScope` APIs. Circuit additionally provides `SharedElementTransitionLayout` for tighter integration.

### Setup

```kotlin
// Circuit: Use circuit-shared-elements artifact
SharedElementTransitionLayout {
    ContentWithOverlays {
        NavigableCircuitContent(navigator, backStack, ...)
    }
}

// Nav3: Use Compose foundation SharedTransitionLayout
SharedTransitionLayout {
    NavDisplay(backStack = backStack, ...) {
        // entries access SharedTransitionScope
    }
}
```

### Key APIs (Compose Foundation)

- `SharedTransitionLayout` -- Provides `SharedTransitionScope`
- `Modifier.sharedElement(state, animatedVisibilityScope)` -- Shared element animation
- `Modifier.sharedBounds(state, animatedVisibilityScope)` -- Shared bounds (container transform)
- `rememberSharedContentState(key)` -- State holder keyed by string

## Adaptive Layout Navigation

Navigation strategy should change based on window size class:

| Window Size | Strategy | Implementation |
|-------------|----------|---------------|
| Compact | Single stack | Standard navigation |
| Medium | List + detail side-by-side | Two-pane with optional sliding |
| Expanded | Persistent rail + two-pane | NavigationRail + content area |

### Circuit Approach

Use nested `NavigableCircuitContent` or `CircuitContent` with `onNavEvent` for the detail pane. The parent Presenter detects window size and switches between single-pane and two-pane layout.

### Nav3 Approach

Use `SceneStrategy`:
- `SinglePaneSceneStrategy()` -- Default, one entry visible
- `TwoPaneSceneStrategy()` -- Side-by-side on wide screens
- `ListDetailSceneStrategy()` -- Material 3 adaptive pattern

## Auth Flow Architecture

Both paradigms support auth flow via graph swapping:

### Pattern

1. **App Launch**: Show splash/loading screen
2. **Auth Check**: Verify token/session
3. **Authenticated**: Navigate to main app graph
4. **Unauthenticated**: Navigate to login graph
5. **Logout**: Swap back to login graph, clearing all state

### Circuit Implementation

```kotlin
// Login success:
navigator.resetRoot(newRoot = HomeScreen) // StateOptions.Default: clean slate

// Logout:
navigator.resetRoot(newRoot = LoginScreen) // Clears entire home stack
```

### Nav3 Implementation

```kotlin
// Login success:
backStack.clear()
backStack.add(HomeRoute)

// Logout:
backStack.clear()
backStack.add(LoginRoute)
```

## Navigation Performance

### Guidelines

1. **Lazy screen registration** -- Don't eagerly create all Presenter/ViewModel factories at startup
2. **Stack depth** -- Keep back stack under 20 entries; consider `popUntil` for deep chains
3. **State size** -- Screen arguments should be IDs, not full data objects
4. **Transition animations** -- Use hardware-accelerated animations; avoid complex Canvas operations during transitions
5. **Profiling** -- Use Layout Inspector to identify overdraw during transitions
6. **Memory** -- Monitor retained state size per screen; clear large retained objects on pop

### Circuit-Specific

- `GestureNavigationDecorationFactory` uses platform-native animations (efficient)
- `SharedElementTransitionLayout` can be expensive; measure impact
- `NonPausablePresenter` keeps running in background -- use sparingly

### Nav3-Specific

- `rememberViewModelStoreNavEntryDecorator` creates ViewModelStore per entry -- cleanup is automatic on pop
- `SceneStrategy` transitions are animated by default -- custom `TransitionSpec` can improve performance
- `predictivePopTransitionSpec` enables gesture-driven transitions

## Paradigm Routing Table

| Task | Go To |
|------|-------|
| Navigator API (goTo, pop, resetRoot, peek) | [circuit-navigation-expert](../circuit-navigation-expert/SKILL.md) |
| Tab navigation with Circuit | [circuit-navigation-expert](../circuit-navigation-expert/SKILL.md) |
| PopResult / rememberAnsweringNavigator | [circuit-navigation-expert](../circuit-navigation-expert/SKILL.md) |
| Nested navigation sub-flows | [circuit-navigation-expert](../circuit-navigation-expert/SKILL.md) |
| InterceptingNavigator | [circuit-navigation-expert](../circuit-navigation-expert/SKILL.md) |
| GestureNavigationDecorationFactory | [circuit-navigation-expert](../circuit-navigation-expert/SKILL.md) |
| NavDisplay / entryProvider | [viewmodel-nav3-expert](../viewmodel-nav3-expert/SKILL.md) |
| ViewModel + Metro DI factories | [viewmodel-nav3-expert](../viewmodel-nav3-expert/SKILL.md) |
| SavedStateHandle with Nav3 | [viewmodel-nav3-expert](../viewmodel-nav3-expert/SKILL.md) |
| SceneStrategy (TwoPane, ListDetail) | [viewmodel-nav3-expert](../viewmodel-nav3-expert/SKILL.md) |
| Deep link routing | [deep-links-expert](../deep-links-expert/SKILL.md) |
| Overlay navigation | [circuit-overlays-expert](../circuit-overlays-expert/SKILL.md) |

## See Also

- [circuit-navigation-expert](../circuit-navigation-expert/SKILL.md) -- Circuit Navigator deep dive
- [viewmodel-nav3-expert](../viewmodel-nav3-expert/SKILL.md) -- Navigation 3 + ViewModel deep dive
- [circuit-expert](../circuit-expert/SKILL.md) -- Circuit core patterns
- [deep-links-expert](../deep-links-expert/SKILL.md) -- Deep link routing
- [compose-animation-expert](../compose-animation-expert/SKILL.md) -- Transition animations
