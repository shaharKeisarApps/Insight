---
name: compose-stability-expert
description: Expert guidance on Compose stability and recomposition optimization. Use for fixing unnecessary recompositions, understanding @Stable/@Immutable, configuring stability config files, reading compiler reports, and optimizing lambda captures.
---

# Compose Stability Expert Skill

## Overview

Compose stability determines whether a Composable can be **skipped** during recomposition. When all parameters to a Composable are stable and unchanged, the Compose compiler generates code to skip the function entirely. Instability is the primary cause of unnecessary recomposition and UI jank in Compose apps. This skill covers the full stability system: compiler inference rules, manual annotations, configuration files, compiler reports, lambda captures, collection stability, and strong skipping mode.

**Stack**: Compose Multiplatform 1.10.0, Kotlin 2.3.10 (K2 Compose compiler plugin), Circuit MVI, kotlinx.collections.immutable.

## When to Use

- **Diagnosing recomposition**: Identifying why a Composable recomposes when it should skip
- **Fixing unstable types**: Making data classes, collections, and third-party types stable
- **Reading compiler reports**: Interpreting `-classes.txt` and `-composables.txt` reports
- **Configuring stability**: Setting up and maintaining the stability configuration file
- **Lambda optimization**: Fixing lambda captures that cause unnecessary recomposition
- **Circuit State stability**: Ensuring Circuit `CircuitUiState` implementations are fully stable
- **Strong skipping mode**: Understanding Compose 2.x (K2) default behavior and its limits

## Quick Reference

For API details and Gradle configuration, see [reference.md](reference.md).
For 10 production examples with problem/fix pairs, see [examples.md](examples.md).

## Why Stability Matters

Compose recomposes a Composable when any of its inputs change. If the compiler cannot prove that all parameters are stable, the Composable is marked **not skippable** and will recompose on every parent recomposition, even if its inputs are identical.

In a typical app with 50-100 Composables on screen, a single unstable parameter high in the tree can trigger cascading recompositions through the entire subtree. This manifests as:
- Dropped frames during scrolling (especially `LazyColumn`)
- Sluggish response to user input
- Excessive CPU usage on low-end devices
- Battery drain from constant recomposition work

## Stability Rules

A type is **stable** when the Compose compiler can prove:
1. The result of `equals` for two instances will always be the same for the same inputs.
2. If a public property changes, Compose is notified (via the Snapshot system).
3. All public properties are themselves stable.

### Automatically Stable Types

| Type | Why Stable |
|------|-----------|
| Primitives (`Int`, `Long`, `Float`, `Double`, `Boolean`, `Char`, `Byte`, `Short`) | Immutable value types |
| `String` | Immutable |
| Enum classes | Immutable, finite set of instances |
| Function types (`() -> Unit`, `(T) -> R`) | Stable by definition in Compose compiler |
| `MutableState<T>` / `State<T>` | Snapshot-observable; changes notify Compose |
| `data class` with only stable `val` properties | Compiler infers stability from all fields |
| `data object` | Singleton, no mutable state |

### Automatically Unstable Types

| Type | Why Unstable | Fix |
|------|-------------|-----|
| `List<T>`, `Set<T>`, `Map<K,V>` | Interfaces -- could be backed by mutable implementations | Use `ImmutableList<T>`, `ImmutableSet<T>`, `ImmutableMap<K,V>` |
| `var` properties in a class | Mutable without snapshot notification | Use `val` or back with `mutableStateOf` |
| Classes from external modules | Compiler cannot analyze binary stability | Add to stability config file or annotate |
| `data class` with any unstable field | One unstable field makes the entire class unstable | Fix the unstable field |
| `Array<T>` | Mutable, no structural equality | Convert to `ImmutableList<T>` |

## @Stable vs @Immutable

Both annotations are stability markers, but they have different contracts.

### @Immutable

```kotlin
@Immutable
data class UserProfile(
    val id: String,
    val name: String,
    val avatarUrl: String,
)
```

**Contract**: All properties are `val` and their values will **never** change after construction. Every property type must also be immutable.

**When to use**: Pure value types where no property will ever mutate. This is the stricter annotation.

### @Stable

```kotlin
@Stable
class CounterState {
    var count by mutableStateOf(0)
        private set

    fun increment() { count++ }
}
```

**Contract**: If a public property changes, Compose will be notified via the Snapshot system. The `equals` result for two instances is consistent over time.

**When to use**: Classes with observable mutable state (backed by `mutableStateOf`). Also valid on truly immutable classes as a weaker alternative to `@Immutable`.

### Decision Table

| Scenario | Annotation |
|----------|-----------|
| Data class, all `val`, all stable types | None needed (compiler infers) |
| Data class from external module | Add to stability config file |
| Data class with `List<T>` field you changed to `ImmutableList<T>` | None needed (compiler infers) |
| Class with `mutableStateOf` backed properties | `@Stable` |
| Interface implemented by stable classes | `@Stable` on the interface |
| Third-party class you cannot modify | Stability config file |

## Stability Configuration File

The stability configuration file tells the compiler to treat specified types as stable without requiring source annotations.

### Format

```
// Comments start with //
// Fully qualified class names, one per line
// * matches a single package segment
// ** matches any depth of packages

kotlinx.datetime.Instant
kotlinx.datetime.LocalDate
kotlinx.collections.immutable.*
com.myapp.core.model.**
```

### Gradle Setup (K2 Compose Compiler Plugin)

```kotlin
// build.gradle.kts (module level)
composeCompiler {
    stabilityConfigurationFile =
        project.rootDir.resolve("compose-stability-config.txt")
}
```

### What to Include

1. **kotlinx.collections.immutable types** -- `ImmutableList`, `ImmutableMap`, etc. are stable but come from an external module.
2. **kotlinx.datetime types** -- `Instant`, `LocalDate`, etc. are immutable value types.
3. **Your own model package** -- If all models in `com.myapp.core.model` are immutable data classes, use a wildcard.
4. **Ktor/network types** -- `HttpStatusCode`, `Url`, etc. used in state.
5. **Circuit Screen types** -- Screen objects used as keys.

### What NOT to Include

- Mutable types (adding them to the config is a lie to the compiler and causes correctness bugs).
- Types with `var` properties.
- Types you do not control that might change behavior between versions.

## Compiler Reports

### Enabling Reports

```kotlin
composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose-reports")
    metricsDestination = layout.buildDirectory.dir("compose-metrics")
}
```

Then build:

```bash
./gradlew :feature:home:compileKotlin
```

### Report Files

| File | Content |
|------|---------|
| `*-classes.txt` | Stability of each class: `stable`, `unstable`, or `runtime` |
| `*-composables.txt` | Each composable: `restartable`, `skippable`, parameters and their stability |
| `*-composables.csv` | Machine-parseable composable data |
| `*-module.json` | Module-level metrics summary |

### Reading the Reports

In `-composables.txt`:
```
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun HomeContent(
  stable items: ImmutableList<Item>
  stable isLoading: Boolean
  stable onRefresh: Function0<Unit>
  stable modifier: Modifier? = @static Companion
)
```

- `restartable skippable` = good. The composable can skip when inputs unchanged.
- `restartable` without `skippable` = bad. The composable will always recompose when its parent does.
- `stable` before a parameter = that parameter is stable.
- `unstable` before a parameter = that parameter prevents skipping.

## Lambda Stability

Lambdas are a common source of instability. In Compose 2.x (K2) with strong skipping, lambdas are automatically remembered. However, understanding the mechanism prevents subtle bugs.

### Why Lambdas Can Cause Recomposition

```kotlin
// PROBLEM: New lambda instance every recomposition
// Without strong skipping, this prevents ItemRow from skipping.
items.forEach { item ->
    ItemRow(
        item = item,
        onClick = { viewModel.onItemClicked(item.id) } // new lambda each time
    )
}
```

### Fixes

1. **Strong skipping (default in K2)**: The compiler auto-remembers lambdas. This handles most cases.
2. **Manual remember** (pre-K2 or when capturing unstable values):
   ```kotlin
   val onClick = remember(item.id) { { viewModel.onItemClicked(item.id) } }
   ```
3. **Method reference**: `onClick = viewModel::onItemClicked` (if signature matches).
4. **Circuit eventSink pattern**: Pass events through a single `eventSink: (Event) -> Unit` lambda in State, which is stable because it is a function type.

## Collection Stability

`kotlin.collections.List<T>` is an interface. The compiler cannot guarantee the backing implementation is immutable. Always use `kotlinx.collections.immutable` at Compose boundaries.

### Boundary Pattern

```kotlin
// In Presenter: convert at the state boundary
return HomeScreen.State(
    items = items.toImmutableList(),   // List<T> -> ImmutableList<T>
    tags = tags.toImmutableSet(),      // Set<T> -> ImmutableSet<T>
    metadata = meta.toImmutableMap(),  // Map<K,V> -> ImmutableMap<K,V>
)
```

Internal logic (repositories, use cases) can use regular `List<T>`. Only convert to `ImmutableList<T>` at the point where state is created for Compose consumption.

## Circuit State Stability

Circuit `CircuitUiState` implementations are Compose state objects. Every field in a State class must be stable for the UI composable to skip.

```kotlin
// STABLE: all fields are stable types
data class State(
    val items: ImmutableList<Item>,        // ImmutableList is stable
    val isLoading: Boolean,                 // Primitive is stable
    val selectedId: String?,                // String? is stable
    val eventSink: (Event) -> Unit,         // Function type is stable
) : CircuitUiState
```

```kotlin
// UNSTABLE: List<Item> is unstable
data class State(
    val items: List<Item>,                  // UNSTABLE -- breaks skipping
    val isLoading: Boolean,
    val eventSink: (Event) -> Unit,
) : CircuitUiState
```

## Strong Skipping Mode (Compose 2.x / K2)

Strong skipping is **enabled by default** in Kotlin 2.0+ with the K2 Compose compiler plugin. It changes skipping behavior:

### What Strong Skipping Does

1. **Unstable parameters**: Composables with unstable parameters can still skip if the parameter values are **referentially equal** (`===`).
2. **Lambda auto-remember**: Lambdas passed to Composables are automatically wrapped in `remember` with their captures as keys.
3. **Reduced annotation burden**: Many cases that previously required `@Stable` or `ImmutableList` now skip automatically.

### What Strong Skipping Does NOT Fix

1. **New object allocations**: If you create a new `List<T>` instance every recomposition (even with same contents), referential equality fails and the composable recomposes.
2. **Expensive equals**: Strong skipping falls back to `===` for unstable types, not `==`. A structurally identical but referentially different object triggers recomposition.
3. **Correctness**: It does not make unstable types safe -- it just adds a referential check as a fast path.

### Best Practice Even With Strong Skipping

Continue using `ImmutableList<T>` and `@Stable` annotations. Strong skipping is a safety net, not a replacement for correct stability design. Relying on referential equality is fragile and can break when:
- A repository returns a new list instance on each call
- A `copy()` creates a new data class instance
- A mapping operation produces a new collection

## Common Pitfalls

1. **Using `List<T>` in CircuitUiState** -- Always use `ImmutableList<T>`. Even with strong skipping, new list instances from repository calls cause recomposition.

2. **Annotating mutable types as `@Immutable`** -- The compiler trusts the annotation. If the type mutates, UI will show stale data because Compose skips recomposition.

3. **Forgetting stability config for kotlinx.collections.immutable** -- `ImmutableList<T>` comes from an external module. Without the config entry, the compiler treats it as unstable.

4. **Unstable field in otherwise stable data class** -- One `List<T>` field makes the entire class unstable. Check all fields.

5. **Not reading compiler reports** -- Enable `reportsDestination` and audit `-composables.txt` for any `restartable` without `skippable`.

6. **Creating new collections in `present()`** -- `items.filter { ... }` creates a new list every recomposition. Use `derivedStateOf` or convert once with `toImmutableList()`.

7. **Passing unstable default parameters** -- `modifier: Modifier = Modifier` is fine (Companion is stable). But `items: List<Item> = emptyList()` creates a new instance each time. Use `items: ImmutableList<Item> = persistentListOf()`.

8. **Lambda capturing var from outer scope** -- Even with strong skipping auto-remember, if the captured variable changes, the lambda is recreated. This is correct behavior but can cause unexpected recompositions if the var changes frequently.

9. **Ignoring `-classes.txt` for model types** -- Check that all your model/domain classes appear as `stable` in the report. Fix any that show `unstable`.

10. **Using `@Stable` on data class that should be `@Immutable`** -- If all properties are `val` and truly immutable, prefer `@Immutable` for stronger guarantees. Use `@Stable` only when there are `mutableStateOf`-backed properties.

11. **Stability config wildcards too broad** -- `com.myapp.**` marks everything stable including mutable classes. Be specific: `com.myapp.core.model.**`.

12. **Not testing stability after refactoring** -- A seemingly innocent refactor (adding a `List<T>` field, changing a `val` to `var`) can silently break stability. Re-check compiler reports after model changes.

## Related Skills

- [compose-runtime-expert](../compose-runtime-expert/SKILL.md) -- Snapshot system, recomposition internals, derivedStateOf
- [performance-expert](../performance-expert/SKILL.md) -- Baseline Profiles, Macrobenchmarks, system tracing
- [circuit-expert](../circuit-expert/SKILL.md) -- Circuit MVI pattern, State design, Presenter lifecycle
- [compose-ui-expert](../compose-ui-expert/SKILL.md) -- LazyColumn optimization, Modifier best practices
