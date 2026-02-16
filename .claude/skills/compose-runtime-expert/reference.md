# Compose Runtime API Reference

> Compose Multiplatform 1.10.0 | Kotlin 2.3.10 | Compose Compiler (K2)

---

## 1. State APIs

### mutableStateOf

Creates an observable state holder. The default policy is `structuralEqualityPolicy()`.

```kotlin
fun <T> mutableStateOf(
    value: T,
    policy: SnapshotMutationPolicy<T> = structuralEqualityPolicy()
): MutableState<T>
```

- Returns `MutableState<T>` which implements `State<T>` (read) and has a settable `value`.
- Reads are tracked by the Snapshot system; writes trigger recomposition of readers.
- Supports property delegation: `var name by mutableStateOf("default")`.

### mutableStateListOf

Creates a `SnapshotStateList<T>` that tracks mutations at the list level.

```kotlin
fun <T> mutableStateListOf(vararg elements: T): SnapshotStateList<T>
```

- Individual element mutations (add, remove, set) invalidate readers.
- More efficient than `mutableStateOf(listOf(...))` because it avoids full list equality checks.
- Thread-safe within Snapshot boundaries.

### mutableStateMapOf

Creates a `SnapshotStateMap<K, V>` that tracks mutations at the map level.

```kotlin
fun <K, V> mutableStateMapOf(vararg pairs: Pair<K, V>): SnapshotStateMap<K, V>
```

- Put, remove, and clear operations invalidate readers.
- Iteration is snapshot-isolated.

### derivedStateOf

Caches a computation that depends on other State objects. Re-evaluates only when dependencies change.

```kotlin
fun <T> derivedStateOf(
    policy: SnapshotMutationPolicy<T> = structuralEqualityPolicy(),
    calculation: () -> T
): State<T>
```

- The `calculation` block is tracked by the Snapshot system; only State reads inside it count as dependencies.
- Result is cached until a dependency changes.
- Must be wrapped in `remember {}` inside a Composable to survive recomposition.
- Use for expensive filtering, sorting, or transformations derived from state.

### produceState

Converts a suspend/callback-based data source into Compose `State<T>`.

```kotlin
@Composable
fun <T> produceState(
    initialValue: T,
    vararg keys: Any?,
    producer: suspend ProduceStateScope<T>.() -> Unit
): State<T>
```

- Launches a coroutine scoped to the composition.
- The coroutine cancels and restarts when any `key` changes.
- Inside `producer`, set `value` to emit new state.
- `ProduceStateScope` extends `MutableState<T>` and `CoroutineScope`.
- `awaitDispose {}` can be used for cleanup inside the producer.

---

## 2. Snapshot System

### Snapshot.takeMutableSnapshot

Creates an isolated mutable snapshot for transactional state modifications.

```kotlin
fun takeMutableSnapshot(
    readObserver: ((Any) -> Unit)? = null,
    writeObserver: ((Any) -> Unit)? = null
): MutableSnapshot
```

- All state reads/writes inside `snapshot.enter {}` are isolated from the global snapshot.
- Call `snapshot.apply()` to commit changes to the global snapshot.
- Call `snapshot.dispose()` when done (even if apply fails).
- `apply()` returns `SnapshotApplyResult` (`Success` or `Failure`).
- `Failure` occurs on write conflicts (two snapshots modified same state object).

### Snapshot.withMutableSnapshot

Convenience function that takes, enters, applies, and disposes a snapshot.

```kotlin
fun <R> Snapshot.Companion.withMutableSnapshot(block: () -> R): R
```

- Throws if apply fails (write conflict).
- Suitable for simple atomic state updates.

### snapshotFlow

Converts Compose state reads into a cold `Flow<T>`.

```kotlin
fun <T> snapshotFlow(block: () -> T): Flow<T>
```

- Runs `block` in a read-only snapshot.
- Re-runs `block` whenever any state read inside it changes.
- Emits new values only when result differs from previous (using `equals`).
- Conflates intermediate values (only latest is emitted).
- Operates on the `Snapshot.global` read observer.

### Snapshot.registerApplyObserver

Registers a callback invoked after any snapshot is applied (state committed).

```kotlin
fun registerApplyObserver(
    observer: (Set<Any>, Snapshot) -> Unit
): ObserverHandle
```

- The `Set<Any>` contains all state objects that were modified in the applied snapshot.
- Returns an `ObserverHandle` with a `dispose()` method for cleanup.
- This is how the Recomposer detects that recomposition is needed.

### Snapshot.registerGlobalWriteObserver

Registers a callback invoked on every state write in the global snapshot.

```kotlin
fun registerGlobalWriteObserver(
    observer: (Any) -> Unit
): ObserverHandle
```

- Called synchronously during the write operation.
- The parameter is the state object being written to.
- Useful for debugging or building custom reactivity systems.
- High frequency; keep the callback fast.

---

## 3. SnapshotMutationPolicy

Controls equality checking and conflict resolution for `mutableStateOf`.

```kotlin
interface SnapshotMutationPolicy<T> {
    fun equivalent(a: T, b: T): Boolean
    fun merge(previous: T, current: T, applied: T): T? = null
}
```

### structuralEqualityPolicy

```kotlin
fun <T> structuralEqualityPolicy(): SnapshotMutationPolicy<T>
```

- Uses `a == b` (Kotlin structural equality / `equals()`).
- Default policy for `mutableStateOf`.
- Skips notification if new value equals current value.

### referentialEqualityPolicy

```kotlin
fun <T> referentialEqualityPolicy(): SnapshotMutationPolicy<T>
```

- Uses `a === b` (reference identity).
- State is considered changed even if content is equal but a new instance is assigned.
- Useful for mutable data classes where `equals` is unreliable.

### neverEqualPolicy

```kotlin
fun <T> neverEqualPolicy(): SnapshotMutationPolicy<T>
```

- Always treats the value as changed; always triggers recomposition.
- Use when every assignment must invalidate readers regardless of value.
- Appropriate for event-like state (counters, timestamps).

### merge Function

- Called during snapshot apply when a write conflict is detected.
- `previous`: value before both snapshots.
- `current`: value in the snapshot being applied.
- `applied`: value already committed by another snapshot.
- Return merged value, or `null` to signal conflict (apply fails).

---

## 4. Side Effects

### SideEffect

Post-commit side effect that runs after every successful composition/recomposition.

```kotlin
@Composable
@NonRestartableComposable
fun SideEffect(effect: () -> Unit)
```

- Runs synchronously after the composition is committed to the Applier.
- Not scoped to a coroutine; no suspension allowed.
- Re-runs on every recomposition (no keys).
- Use for synchronizing Compose state to non-Compose systems (analytics, logging).

### LaunchedEffect

Launches a coroutine scoped to the composition with key-based restart.

```kotlin
@Composable
fun LaunchedEffect(vararg keys: Any?, block: suspend CoroutineScope.() -> Unit)
```

- Coroutine starts when the effect enters composition.
- Coroutine cancels when the effect leaves composition.
- Coroutine cancels and restarts when any `key` changes.
- The `CoroutineScope` uses the composition's coroutine context.

### DisposableEffect

Lifecycle-aware effect with explicit cleanup.

```kotlin
@Composable
fun DisposableEffect(vararg keys: Any?, effect: DisposableEffectScope.() -> DisposableEffectResult)
```

- `effect` block runs when entering composition or when keys change.
- Must return `onDispose { ... }` for cleanup.
- `onDispose` runs when leaving composition or before re-running on key change.
- Cleanup runs before the new effect block executes.

### rememberCoroutineScope

Provides a `CoroutineScope` tied to the composition point.

```kotlin
@Composable
fun rememberCoroutineScope(): CoroutineScope
```

- The scope cancels when the Composable leaves composition.
- Use for launching coroutines from event callbacks (onClick, etc.).
- Do not use for effects that should restart on key changes (use `LaunchedEffect` instead).

### rememberUpdatedState

Captures the latest value of a parameter without restarting a long-running effect.

```kotlin
@Composable
fun <T> rememberUpdatedState(newValue: T): State<T>
```

- Returns a `State<T>` whose `value` is always the latest `newValue`.
- Use inside `LaunchedEffect` or `DisposableEffect` to read current values without restarting the effect.

---

## 5. Remember

### remember

Stores a value across recompositions.

```kotlin
@Composable
inline fun <T> remember(calculation: @DisallowComposableCalls () -> T): T
@Composable
inline fun <T> remember(vararg keys: Any?, calculation: @DisallowComposableCalls () -> T): T
```

- Without keys: value is computed once and cached for the lifetime of the call site in composition.
- With keys: value is recomputed when any key changes.
- The `@DisallowComposableCalls` annotation prevents calling Composable functions inside the calculation.

### rememberSaveable

Stores a value that survives configuration changes and process death (Android).

```kotlin
@Composable
fun <T : Any> rememberSaveable(
    vararg inputs: Any?,
    stateSaver: Saver<T, out Any> = autoSaver(),
    key: String? = null,
    init: () -> T
): T
```

- Uses `SaveableStateRegistry` provided by the platform.
- `autoSaver()` works for Bundle-compatible types (primitives, String, Parcelable).
- Custom `Saver<T, S>` for complex types (see `mapSaver`, `listSaver`).
- `key` allows manual disambiguation when multiple calls exist at the same call site.

### RememberObserver

Interface for objects that need lifecycle awareness when stored via `remember`.

```kotlin
interface RememberObserver {
    fun onRemembered()
    fun onForgotten()
    fun onAbandoned()
}
```

- `onRemembered()`: Called when the object is successfully stored in composition.
- `onForgotten()`: Called when the object leaves composition (cleanup equivalent).
- `onAbandoned()`: Called when the composition that would have stored the object is discarded (e.g., during exception).
- This is the mechanism underlying `LaunchedEffect` and `DisposableEffect`.

---

## 6. CompositionLocal

### compositionLocalOf

Creates a dynamically-tracked CompositionLocal.

```kotlin
fun <T> compositionLocalOf(
    policy: SnapshotMutationPolicy<T> = structuralEqualityPolicy(),
    defaultFactory: () -> T
): ProvidableCompositionLocal<T>
```

- Reads are tracked by the Snapshot system.
- When the provided value changes, only Composables that read `current` recompose.
- Best for values that change frequently.
- `defaultFactory` is called if no provider exists up the tree.

### staticCompositionLocalOf

Creates a statically-provided CompositionLocal.

```kotlin
fun <T> staticCompositionLocalOf(defaultFactory: () -> T): ProvidableCompositionLocal<T>
```

- Reads are NOT tracked by the Snapshot system.
- When the provided value changes, the entire subtree under the provider recomposes.
- Best for values that rarely or never change (theme, DI container, platform config).

### CompositionLocalProvider

Provides values to the composition subtree.

```kotlin
@Composable
fun CompositionLocalProvider(vararg values: ProvidedValue<*>, content: @Composable () -> Unit)
```

- Use the `provides` infix function: `LocalTheme provides darkTheme`.
- Use `providesDefault` to provide a value only if no parent already provides one.
- Multiple locals can be provided simultaneously.

---

## 7. Composition & Recomposer

### Composition

Creates a composition bound to an Applier.

```kotlin
fun Composition(
    applier: Applier<*>,
    parent: CompositionContext
): Composition
```

- `Composition` interface has `setContent(@Composable () -> Unit)` and `dispose()`.
- The `parent` is typically a `Recomposer` or another `Composition` (subcomposition).

### Recomposer

Drives recomposition for one or more compositions.

```kotlin
class Recomposer(effectCoroutineContext: CoroutineContext) : CompositionContext
```

- Must be run on a `CoroutineScope` via `recomposer.runRecomposeAndApplyChanges()`.
- States: `Idle`, `PendingWork`, `ShuttingDown`, `ShutDown`.
- `recomposer.close()` initiates shutdown; compositions are disposed.
- `recomposer.currentState` is a `StateFlow<State>` for monitoring.

### AbstractApplier

Base class for building custom Appliers (non-UI Compose trees).

```kotlin
abstract class AbstractApplier<T>(val root: T) : Applier<T> {
    val current: T  // Current node being built
    abstract fun insertTopDown(index: Int, instance: T)
    abstract fun insertBottomUp(index: Int, instance: T)
    abstract fun remove(index: Int, count: Int)
    abstract fun move(from: Int, to: Int, count: Int)
    abstract fun onClear()
}
```

- `insertTopDown` vs `insertBottomUp`: Choose which direction is more efficient for your tree. Implement one and leave the other empty.
- `onClear()`: Called when the entire tree is being rebuilt.
- `current` tracks the node currently being populated by child `@Composable` calls.

---

## 8. Stability Annotations

### @Stable

Marks a type as stable for the Compose compiler.

```kotlin
@Target(AnnotationTarget.CLASS)
@StableMarker
annotation class Stable
```

Contract:
1. `equals` results for two instances will always return the same result for the same inputs.
2. When a public property changes, composition is notified (via Snapshot system).
3. All public properties are also stable.

Stable types enable **skipping** during recomposition.

### @Immutable

Marks a type as immutable (all properties are `val` and also immutable).

```kotlin
@Target(AnnotationTarget.CLASS)
@StableMarker
annotation class Immutable
```

- Stricter than `@Stable`: guarantees no property will ever change after construction.
- Kotlin `data class` with only `val` primitive/String fields is automatically inferred as stable by the compiler without this annotation.

### @StableMarker

Meta-annotation for creating custom stability markers.

```kotlin
@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class StableMarker
```

### Compiler Stability Inference

The Compose compiler (K2 plugin) automatically determines stability:
- **Stable by default**: Primitives, String, function types (`() -> Unit`, `(T) -> R`), enum classes.
- **Stable if all fields stable**: `data class` with only stable `val` properties.
- **Unstable**: Classes with `var` properties, collections (`List<T>`, `Map<K, V>`), types from external modules without stability info.
- **Stability configuration file**: Override compiler inference for external types (see examples).

---

## 9. Key & MovableContent

### key

Provides an identity for a composition scope, allowing Compose to distinguish items.

```kotlin
@Composable
inline fun <T> key(vararg keys: Any?, block: @Composable () -> T): T
```

- Without `key`, Compose matches items by position (index).
- With `key`, Compose matches by the provided identity, preserving state across reordering.
- Critical for lists where items can be reordered, inserted, or removed.

### movableContentOf

Creates content that can move between locations in the tree without losing state.

```kotlin
fun movableContentOf(content: @Composable () -> Unit): @Composable () -> Unit
fun <P> movableContentOf(content: @Composable (P) -> Unit): @Composable (P) -> Unit
```

- State stored via `remember` inside the content is preserved when moved.
- Effects are not restarted when moved.
- If the content appears in a new location while still present in the old location within the same composition, it is copied (not moved).

### movableContentWithReceiverOf

Same as `movableContentOf` but with a receiver scope.

```kotlin
fun <R> movableContentWithReceiverOf(
    content: @Composable R.() -> Unit
): @Composable R.() -> Unit
```

---

## 10. Compiler Annotations

### @Composable

Marks a function as a Composable function.

- The compiler transforms the function to include a `Composer` parameter and group tracking.
- Composable functions can only be called from other Composable functions.
- Lambda parameters annotated with `@Composable` create composable lambdas.

### @NonRestartableComposable

Prevents the compiler from generating a restart group for this function.

```kotlin
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
annotation class NonRestartableComposable
```

- The function cannot be individually recomposed; its parent will recompose instead.
- Use for small inline-like Composables that do not read state directly.
- `SideEffect` uses this annotation.

### @ReadOnlyComposable

Marks a Composable that only reads from `CompositionLocal` and does not emit into the tree.

```kotlin
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
annotation class ReadOnlyComposable
```

- Allows the compiler to skip group generation entirely.
- Common for theme accessor properties (`MaterialTheme.colorScheme`).

### @DisallowComposableCalls

Prevents calling `@Composable` functions inside the annotated lambda.

```kotlin
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE, AnnotationTarget.TYPE_PARAMETER)
annotation class DisallowComposableCalls
```

- Used on the `calculation` lambda of `remember` to prevent accidental composition inside the factory.

---

## 11. Circuit Integration Notes

Circuit uses Compose runtime for Presenters. Key implications:

- `Presenter.present()` is `@Composable`, so all state/effect/remember APIs apply.
- `collectAsRetainedState()` uses `rememberSaveable` under the hood with Circuit's retained state registry.
- `rememberRetained {}` survives configuration changes via Circuit's `RetainedStateRegistry`.
- Presenters participate in Compose's recomposition cycle driven by the Recomposer.
- `Molecule` (used by Circuit) creates a `Recomposer` and runs it in a coroutine, emitting the return value as a `Flow`.

---

## 12. Molecule Integration Notes

Molecule leverages the Compose runtime to turn `@Composable` functions into reactive streams.

```kotlin
fun <T> CoroutineScope.launchMolecule(
    mode: RecompositionMode,
    body: @Composable () -> T
): StateFlow<T>
```

- `RecompositionMode.Immediate`: Recomposes synchronously on state change (testing, presenters).
- `RecompositionMode.ContextClock`: Recomposes on frame clock ticks (UI-aligned).
- Internally creates a `Recomposer` and `Composition` without an Applier (uses `Unit` applier).
- All Compose runtime APIs (state, effects, remember) work inside Molecule bodies.
