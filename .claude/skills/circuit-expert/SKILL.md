---
name: circuit-expert
description: Expert guidance on Circuit MVI architecture for KMP. Use for creating screens, presenters, UI, navigation, overlays, state retention, and integrating with Metro DI. Covers Circuit 0.32.0 with all artifacts.
---

# Circuit Expert Skill (v0.32.0)

## Overview

Circuit is a lightweight, extensible MVI framework for Kotlin Multiplatform by Slack. It separates UI (Compose) from logic (Presenters) using Unidirectional Data Flow. Circuit works with Compose Multiplatform on Android, iOS, Desktop, JS, and wasmJs.

## When to Use

- **Creating screens**: Define Screen, State, Event types
- **Implementing logic**: Create Presenters with business logic
- **Building UI**: Create Composable UIs
- **Navigation**: Using Navigator for screen transitions
- **Overlays**: Bottom sheets, dialogs via OverlayHost
- **State retention**: Survive config changes with rememberRetained
- **Testing**: Presenter.test{}, FakeNavigator
- **Shared elements**: Cross-screen element transitions

## Quick Reference

For detailed API reference, see [reference.md](reference.md).
For production examples (from CatchUp), see [examples.md](examples.md).
For overlay-specific patterns, see [circuit-overlays-expert](../circuit-overlays-expert/SKILL.md).
For testing patterns, see [circuit-testing-expert](../circuit-testing-expert/SKILL.md).
For file structure and previews, see [circuit-file-structure](../circuit-file-structure/SKILL.md).

## Core Pattern (5 Components)

1. **Screen**: Route definition (data class/object implementing `Screen`)
2. **State**: Stable snapshot (`data class State(..., val eventSink: (Event) -> Unit) : CircuitUiState`) -- `CircuitUiState` is already `@Stable`, do NOT add `@Immutable`
3. **Event**: Sealed interface of user actions (`sealed interface Event : CircuitUiEvent`)
4. **Presenter**: Business logic (`@Composable fun present(): State`)
5. **Ui**: Rendering (`@Composable fun Content(state: State, modifier: Modifier)`)

## Complete Screen Example (CatchUp Pattern)

```kotlin
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

// Presenter with Metro @AssistedInject
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
                is HomeScreen.Event.Refresh -> { isLoading = true; /* refresh */ }
                is HomeScreen.Event.NestedNavEvent -> navigator.onNavEvent(event.navEvent)
            }
        }
    }
}

// UI
@CircuitInject(HomeScreen::class, AppScope::class)
@Composable
fun HomeUi(state: HomeScreen.State, modifier: Modifier = Modifier) {
    Scaffold(modifier) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            items(state.items) { item ->
                ItemRow(item, onClick = { state.eventSink(HomeScreen.Event.ItemClicked(item.id)) })
            }
        }
    }
}
```

## Navigation

Circuit uses an imperative Navigator API injected into Presenters via `@AssistedInject`.

| Method | Use |
|--------|-----|
| `goTo(screen)` | Push screen onto back stack |
| `pop(result?)` | Pop top screen, optional PopResult |
| `resetRoot(newRoot, options)` | Replace entire stack (tabs, auth) |
| `peek()` / `peekBackStack()` | View stack without modifying |

> **Deep dive**: For tab navigation, result passing, nested navigation, shared elements, interception, auth flows, gesture navigation, and testing -- see [circuit-navigation-expert](../circuit-navigation-expert/SKILL.md).

## App Setup (CatchUp Pattern)

```kotlin
// In Activity
CircuitCompositionLocals(circuit) {
    ContentWithOverlays {
        val backStack = rememberSaveableBackStack(HomeScreen)
        val navigator = rememberCircuitNavigator(backStack)
        NavigableCircuitContent(
            navigator,
            backStack,
            decoratorFactory = GestureNavigationDecorationFactory(
                onBackInvoked = navigator::pop,
            ),
        )
    }
}
```

## State Retention

| API | Survives Config Change | Survives Process Death | Use Case |
|-----|----------------------|----------------------|----------|
| `rememberRetained { }` | Yes | No | In-memory state (ViewModel-backed on Android) |
| `rememberRetainedSaveable { }` | Yes | Yes | Small serializable state |
| `collectAsRetainedState(flow)` | Yes | No | Flow collection with retention |
| `produceRetainedState(initial) { }` | Yes | No | Async production with retention |
| `produceAndCollectAsRetainedState` | Yes | No | Combined produce + collect (v0.32.0) |

## Artifacts

| Artifact | Purpose |
|----------|---------|
| `circuit-foundation` | High-level: Circuit, CircuitContent, NavigableCircuitContent |
| `circuit-runtime` | Core: Screen, Navigator, CircuitUiState, CircuitUiEvent |
| `circuit-runtime-presenter` | Presenter<S> interface, Presenter.Factory |
| `circuit-runtime-ui` | Ui<S> interface, Ui.Factory |
| `circuit-retained` | rememberRetained, collectAsRetainedState |
| `circuit-overlay` | Overlay, OverlayHost, OverlayNavigator, ContentWithOverlays |
| `circuit-codegen` + `circuit-codegen-annotations` | @CircuitInject KSP codegen |
| `circuit-test` | Presenter.test{}, FakeNavigator |
| `circuitx-overlays` | AlertDialogOverlay, BottomSheetOverlay, FullScreenOverlay |
| `circuitx-gesture-navigation` | Predictive back (Android), interactive pop (iOS) |
| `circuitx-effects` | ImpressionEffect, LaunchedImpressionEffect, ToastEffect |
| `circuitx-navigation` | InterceptingNavigator, NavigationInterceptor |
| `circuitx-android` | rememberAndroidScreenAwareNavigator |
| `circuit-shared-elements` | SharedElementTransitionLayout |

## Codegen Mode (Metro)

> **CRITICAL**: `@CircuitInject` does NOTHING without the `circuit-codegen` KSP processor.
> Without it, no `Presenter.Factory`/`Ui.Factory` adapters are generated and screens won't render.

```kotlin
// build.gradle.kts - FULL required setup
plugins {
    id("com.google.devtools.ksp")  // Required for circuit-codegen
}

ksp { arg("circuit.codegen.mode", "METRO") }

dependencies {
    // annotations (compile-time)
    implementation(libs.circuit.codegen.annotations)
    // KSP processor (generates Presenter.Factory/Ui.Factory with @ContributesIntoSet)
    add("kspAndroid", libs.circuit.codegen)
    add("kspIosArm64", libs.circuit.codegen)
    add("kspIosSimulatorArm64", libs.circuit.codegen)
}

// libs.versions.toml
// circuit-codegen = { module = "com.slack.circuit:circuit-codegen", version.ref = "circuit" }
```

## Core Rules

1. **Never call `navigator.goTo()` in composition** -- Always in event handlers or `LaunchedEffect`.
2. **Use `rememberRetained` for Presenter state** -- Survives config changes.
3. **Use `@AssistedInject` for Presenters needing Navigator** -- Metro provides the factory.
4. **Place `@CircuitInject` on the `@AssistedFactory`** -- Not on the Presenter class itself.
5. **Use `fun interface` for factories** -- Kotlin SAM conversion.
6. **Always provide `ContentWithOverlays`** -- Required for overlay system to work.
7. **Use `CircuitCompositionLocals(circuit)`** -- Provides Circuit instance via `LocalCircuit`.

## Common Pitfalls

1. **Missing `ContentWithOverlays`** -- Overlays won't display without this wrapper.
2. **Using `collectAsState` instead of `collectAsRetainedState`** -- Loses state on config changes.
3. **`resetRoot` with boolean params** -- Use `StateOptions` directly or the extension function. `StateOptions.SaveAndRestore` is the convenient preset for tab switching.
4. **`@CircuitInject` on Presenter class with `@AssistedInject`** -- Place it on the `@AssistedFactory` instead.
5. **`LocalOverlayNavigator`** -- Renamed to `LocalOverlayHost` in recent versions.
6. **Not declaring `@Multibinds`** -- Metro requires explicit multibinding declarations for Presenter.Factory and Ui.Factory sets.
7. **Adding `@Immutable` to State classes** -- `CircuitUiState` is already annotated with `@Stable`. Adding `@Immutable` is redundant and semantically wrong (State classes contain lambda `eventSink` which isn't immutable). The `@Stable` contract + `data class` equality is sufficient for Compose to skip recomposition.

## See Also

- [circuit-navigation-expert](../circuit-navigation-expert/SKILL.md) -- Navigator deep dive: tabs, results, nested nav, shared elements, interception
- [circuit-testing-expert](../circuit-testing-expert/SKILL.md) -- Testing Presenters with Molecule + Turbine
- [circuit-overlays-expert](../circuit-overlays-expert/SKILL.md) -- Dialogs, bottom sheets, OverlayHost
- [metro-expert](../metro-expert/SKILL.md) -- DI wiring for Circuit components
- [navigation-expert](../navigation-expert/SKILL.md) -- Circuit Navigator vs Nav3 comparison
