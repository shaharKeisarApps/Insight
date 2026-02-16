---
name: lifecycle-viewmodel-expert
description: Expert guidance on Lifecycle and ViewModel for KMP. Use for lifecycle-aware components, viewModelScope, SavedStateHandle, collectAsStateWithLifecycle, and understanding when to use ViewModel vs Circuit Presenter.
---

# Lifecycle & ViewModel Expert Skill

## Overview

AndroidX Lifecycle components are now fully KMP-compatible (version 2.9.6+). The `lifecycle-viewmodel`, `lifecycle-runtime`, and `lifecycle-runtime-compose` artifacts all support Android, iOS, Desktop, Web (JS/Wasm), and native targets. This means `ViewModel`, `viewModelScope`, `SavedStateHandle`, `collectAsStateWithLifecycle`, and `repeatOnLifecycle` work across all Kotlin Multiplatform targets.

## When to Use

- **ViewModel for Navigation 3 entries**: Nav3 `rememberViewModelStoreNavEntryDecorator` scopes a ViewModel per back-stack entry.
- **Lifecycle-aware collection**: Always prefer `collectAsStateWithLifecycle` over `collectAsState` in Compose for Flow observation.
- **Process death restoration**: Use `SavedStateHandle` inside a ViewModel to persist small UI state across process death (Android) or app relaunch.
- **viewModelScope**: Use the built-in `viewModelScope` for coroutine work tied to ViewModel lifetime.

## Quick Reference

For detailed APIs and annotations, see [reference.md](reference.md).
For production code examples, see [examples.md](examples.md).

## Decision Matrix: ViewModel vs Circuit Presenter

| Criterion | Circuit Presenter | ViewModel |
|-----------|-------------------|-----------|
| **Recommended default** | Yes -- when using full Circuit MVI pattern | No -- use only when needed |
| **State retention (config changes)** | `rememberRetained` (backed by ViewModel on Android) | Built-in via ViewModelStore |
| **Process death survival** | `rememberRetainedSaveable` | `SavedStateHandle` |
| **Navigation scoping** | Automatic per-screen via Circuit back stack | Manual via Nav3 `rememberViewModelStoreNavEntryDecorator` |
| **DI integration (Metro)** | `@AssistedInject` with Navigator | `@AssistedInject` with `ViewModelAssistedFactory` + `CreationExtras` |
| **KMP support** | Full (Compose Multiplatform) | Full (AndroidX Lifecycle 2.9.6 KMP) |
| **Best for Navigation 3** | No -- Nav3 expects ViewModel scoping | Yes -- first-class Nav3 support |
| **Testability** | Molecule / Turbine (Compose-native) | JUnit + coroutines-test |
| **Coroutine scope** | Compose coroutine scope (`LaunchedEffect`, `rememberCoroutineScope`) | `viewModelScope` (auto-cancelled on clear) |

### When to Use Circuit Presenter (Recommended Default)

- Full Circuit MVI pattern with Screen/State/Event
- Screens managed by Circuit Navigator
- Overlays (bottom sheets, dialogs) via `OverlayNavigator`
- Tab navigation with `resetRoot`

### When to Use ViewModel

- Navigation 3 entry-scoped state management
- Need `SavedStateHandle` for robust process death restoration
- Integrating with existing AndroidX ViewModel-based libraries
- Standalone Compose screens outside Circuit (no Presenter)
- Long-running operations that must outlive the Composable (e.g., uploads)

## Core Rules

1. **Always collect flows lifecycle-aware**: Use `collectAsStateWithLifecycle()` in Composables instead of `collectAsState()`. This pauses collection when the lifecycle drops below `STARTED`.
2. **Use viewModelScope for coroutines**: Never create unscoped `CoroutineScope` in a ViewModel. Always use the built-in `viewModelScope`.
3. **Inject dependencies via Metro factory**: Use `@AssistedInject` + `ViewModelAssistedFactory` for ViewModels that need runtime parameters; use `@Inject` + `@ViewModelKey` + `@ContributesIntoMap` for simple ViewModels.
4. **Scope to navigation entry, not Activity**: In Nav3, always use `rememberViewModelStoreNavEntryDecorator` so each back-stack entry gets its own ViewModelStore.
5. **Do not hold View/Context references**: ViewModels outlive Activities and Fragments. Holding references causes memory leaks.
6. **Prefer SavedStateHandle for small data**: Use `SavedStateHandle` for primitive types, Strings, and Parcelable/Serializable objects. Do not store large data (bitmaps, lists of 1000+ items).

## AndroidX Lifecycle KMP Version

- **Target version**: `2.9.6`
- **Artifacts**: `lifecycle-viewmodel`, `lifecycle-runtime`, `lifecycle-runtime-compose`, `lifecycle-viewmodel-savedstate`, `lifecycle-viewmodel-compose`
- **KMP targets**: Android, JVM (Desktop), iOS (arm64, x64, simulatorArm64), macOS, Linux, JS, wasmJs

## Best Practices

- Use `collectAsStateWithLifecycle` with explicit `minActiveState` when you need `RESUMED` instead of the default `STARTED`.
- Combine `SavedStateHandle` with `StateFlow` via `savedStateHandle.getStateFlow(key, default)` for reactive saved state.
- In Navigation 3, always include both `rememberSaveableStateHolderNavEntryDecorator()` and `rememberViewModelStoreNavEntryDecorator()` in your `entryDecorators`.
- Test ViewModels with `kotlinx-coroutines-test` and `Dispatchers.setMain(UnconfinedTestDispatcher())`.
- Use `ProcessLifecycleOwner` only for app-wide lifecycle events (foreground/background), never for per-screen state.

## Common Pitfalls

1. **Leaking ViewModels without proper scoping**: If you create a ViewModel at Activity scope but need per-screen scope, every screen shares the same instance. Always scope to the navigation entry.
2. **Not cancelling viewModelScope work**: `viewModelScope` auto-cancels on `onCleared()`, but if you create child scopes manually, you must cancel them yourself.
3. **Using `collectAsState` instead of `collectAsStateWithLifecycle`**: The non-lifecycle-aware variant keeps collecting in the background even when the UI is not visible, wasting resources and potentially causing crashes.
4. **Storing large objects in SavedStateHandle**: The Android transaction buffer limit (~1MB shared across all saved state) can cause `TransactionTooLargeException`.
5. **Mixing Circuit Presenter and ViewModel for the same screen**: Choose one pattern per screen. A Presenter already manages state retention via `rememberRetained`.
6. **Forgetting `rememberViewModelStoreNavEntryDecorator` in Nav3**: Without this decorator, ViewModels are not scoped to entries and will leak or be shared incorrectly.

## See Also

- [viewmodel-nav3-expert](../viewmodel-nav3-expert/SKILL.md) -- Full ViewModel + Nav3 paradigm guide
- [coroutines-core-expert](../coroutines-core-expert/SKILL.md) -- StateFlow, viewModelScope patterns
