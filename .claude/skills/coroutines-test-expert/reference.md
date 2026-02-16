# Coroutines Test API Reference

Complete API reference for `kotlinx-coroutines-test` 1.10.2, Turbine 1.2.1, and Molecule 2.2.0.

---

## kotlinx-coroutines-test

### runTest

Top-level entry point for coroutine tests. Creates a `TestScope` and executes the test body.

```kotlin
@ExperimentalCoroutinesApi
fun runTest(
    context: CoroutineContext = EmptyCoroutineContext,
    timeout: Duration = 60.seconds,  // DEFAULT_TIMEOUT
    testBody: suspend TestScope.() -> Unit
): TestResult
```

**Parameters:**
- `context` -- Additional coroutine context. Can include a `TestDispatcher` to override the default `StandardTestDispatcher`. If a `TestCoroutineScheduler` is provided, it will be shared.
- `timeout` -- Wall-clock timeout for the entire test. Defaults to 60 seconds. Does NOT count virtual time.
- `testBody` -- The test body executed in a `TestScope`.

**Behavior:**
1. Creates `TestScope` with `StandardTestDispatcher` (unless overridden via `context`).
2. Executes `testBody`.
3. Calls `advanceUntilIdle()` to drain all pending work.
4. Checks for uncaught exceptions in child coroutines.
5. Cancels `backgroundScope`.

```kotlin
// With custom dispatcher
runTest(UnconfinedTestDispatcher()) { ... }

// With custom timeout
runTest(timeout = 10.seconds) { ... }

// With both
runTest(
    context = UnconfinedTestDispatcher(),
    timeout = 30.seconds
) { ... }
```

---

### TestScope

A `CoroutineScope` designed for testing. Provides access to virtual time control.

```kotlin
@ExperimentalCoroutinesApi
interface TestScope : CoroutineScope {
    val testScheduler: TestCoroutineScheduler
    val backgroundScope: CoroutineScope
}
```

**Extension Properties:**

| Property | Type | Description |
|----------|------|-------------|
| `currentTime` | `Long` | Current virtual time in milliseconds |
| `testTimeSource` | `TimeSource.WithComparableMarks` | Scheduler as a `TimeSource` for `measureTime` |

**Extension Functions:**

| Function | Signature | Description |
|----------|-----------|-------------|
| `advanceUntilIdle()` | `fun TestScope.advanceUntilIdle()` | Runs all pending tasks, advancing virtual time as needed. Stops when only `backgroundScope` tasks remain. |
| `advanceTimeBy(delayTimeMillis)` | `fun TestScope.advanceTimeBy(delayTimeMillis: Long)` | Advances virtual clock by given ms, executing tasks scheduled in that window. |
| `advanceTimeBy(delayTime)` | `fun TestScope.advanceTimeBy(delayTime: Duration)` | Duration overload. |
| `runCurrent()` | `fun TestScope.runCurrent()` | Executes only tasks scheduled at the current virtual time. Does not advance time. |

**Constructor:**

```kotlin
@ExperimentalCoroutinesApi
fun TestScope(context: CoroutineContext = EmptyCoroutineContext): TestScope
```

Creates a standalone `TestScope`. Adds `TestCoroutineScheduler` and `StandardTestDispatcher` if not present in `context`.

---

### backgroundScope

```kotlin
val TestScope.backgroundScope: CoroutineScope
```

A scope for launching background work that should not block test completion.

**Key behaviors:**
- Coroutines run during `advanceTimeBy()` and `runCurrent()`.
- `advanceUntilIdle()` does NOT wait for `backgroundScope`-only tasks.
- All coroutines cancelled when the test finishes.
- Exceptions reported at test end, not during test body.

**When to use:**
- `stateIn()` / `shareIn()` calls that create infinite collectors.
- Long-running data observation loops.
- Any coroutine that would cause `advanceUntilIdle()` to hang.

---

### StandardTestDispatcher

```kotlin
@ExperimentalCoroutinesApi
fun StandardTestDispatcher(
    scheduler: TestCoroutineScheduler? = null,
    name: String? = null
): TestDispatcher
```

A `TestDispatcher` that queues all dispatched coroutines. They do not execute until explicitly advanced.

**Parameters:**
- `scheduler` -- The `TestCoroutineScheduler` to use. If `null`, creates a new one or reuses one from the context.
- `name` -- Optional name for debugging.

**Behavior:**
- `launch { }` does NOT execute the block immediately.
- Requires `advanceUntilIdle()`, `advanceTimeBy()`, or `runCurrent()` to execute queued work.
- Default dispatcher for `runTest`.

---

### UnconfinedTestDispatcher

```kotlin
@ExperimentalCoroutinesApi
fun UnconfinedTestDispatcher(
    scheduler: TestCoroutineScheduler? = null,
    name: String? = null
): TestDispatcher
```

A `TestDispatcher` that enters coroutines eagerly, similar to `Dispatchers.Unconfined`.

**Parameters:**
- `scheduler` -- Must share the same scheduler as the test. Pass `testScheduler` when creating inside `runTest`.
- `name` -- Optional name for debugging.

**Behavior:**
- `launch { }` executes eagerly until first suspension.
- After suspension, resumes based on scheduler advancement.
- Useful for immediately collecting StateFlow/SharedFlow emissions.

**Critical**: When creating inside `runTest`, always pass `testScheduler`:
```kotlin
runTest {
    val dispatcher = UnconfinedTestDispatcher(testScheduler) // CORRECT
    // val dispatcher = UnconfinedTestDispatcher()           // WRONG: separate scheduler
}
```

---

### TestCoroutineScheduler

```kotlin
@ExperimentalCoroutinesApi
class TestCoroutineScheduler {
    val currentTime: Long
    val timeSource: TimeSource.WithComparableMarks

    fun advanceTimeBy(delayTimeMillis: Long)
    fun advanceTimeBy(delayTime: Duration)
    fun advanceUntilIdle()
    fun runCurrent()
}
```

The virtual clock and task scheduler. Shared between all `TestDispatcher` instances in a test.

| Method | Description |
|--------|-------------|
| `currentTime` | Current virtual time in ms (starts at 0) |
| `timeSource` | `TimeSource` for `measureTime {}` blocks |
| `advanceTimeBy(ms)` | Move clock forward, run tasks in window |
| `advanceTimeBy(duration)` | Duration overload |
| `advanceUntilIdle()` | Run all queued tasks, advance time as needed |
| `runCurrent()` | Run tasks at current time only |

---

### Dispatchers.setMain / resetMain

```kotlin
@ExperimentalCoroutinesApi
fun Dispatchers.setMain(dispatcher: CoroutineDispatcher)

@ExperimentalCoroutinesApi
fun Dispatchers.resetMain()
```

Overrides `Dispatchers.Main` for unit tests. Required when production code uses `Dispatchers.Main` (ViewModels, Android components).

**Rules:**
- Call `setMain` in `@BeforeTest`.
- Call `resetMain` in `@AfterTest`.
- The dispatcher passed to `setMain` should be the same `TestDispatcher` used in `runTest`.

---

## Turbine 1.2.1

### Flow.test

```kotlin
suspend fun <T> Flow<T>.test(
    timeout: Duration? = null,
    name: String? = null,
    validate: suspend TurbineTestContext<T>.() -> Unit
)
```

Primary API. Collects the flow in a background coroutine and runs `validate` block for assertions.

**Parameters:**
- `timeout` -- Per-event timeout (default: 3 seconds). How long `awaitItem()` etc. wait.
- `name` -- Optional name for error messages.
- `validate` -- Lambda receiving `TurbineTestContext<T>` (extends `ReceiveTurbine<T>`).

**Behavior:**
- Flow collection starts automatically.
- `ensureAllEventsConsumed()` is called when `validate` completes.
- If unconsumed events remain, test fails with `AssertionError`.

```kotlin
flowOf(1, 2, 3).test {
    assertEquals(1, awaitItem())
    assertEquals(2, awaitItem())
    assertEquals(3, awaitItem())
    awaitComplete()
}
```

---

### Flow.testIn

```kotlin
fun <T> Flow<T>.testIn(
    scope: CoroutineScope,
    timeout: Duration? = null,
    name: String? = null
): ReceiveTurbine<T>
```

Starts collecting a flow and returns a `ReceiveTurbine<T>` for later assertions. Useful when you need multiple turbines or assertions outside a lambda.

**Parameters:**
- `scope` -- The `CoroutineScope` to collect in (typically `backgroundScope` in `runTest`).
- `timeout` -- Per-event timeout.
- `name` -- Optional name.

**Caller responsibility:**
- Must explicitly cancel or consume terminal events.
- No auto `ensureAllEventsConsumed()`.

```kotlin
runTest {
    val turbine = myFlow.testIn(backgroundScope)
    // ... do stuff ...
    assertEquals(expected, turbine.awaitItem())
    turbine.cancelAndIgnoreRemainingEvents()
}
```

---

### ReceiveTurbine<T>

Interface for consuming and asserting on flow events.

#### Suspending Methods

| Method | Signature | Description |
|--------|-----------|-------------|
| `awaitItem()` | `suspend fun awaitItem(): T` | Suspends until an item is emitted. Returns the item. Throws on timeout. |
| `awaitError()` | `suspend fun awaitError(): Throwable` | Suspends until an error terminates the flow. Returns the `Throwable`. |
| `awaitComplete()` | `suspend fun awaitComplete()` | Suspends until the flow completes successfully. |
| `awaitEvent()` | `suspend fun awaitEvent(): Event<T>` | Suspends until any event. Returns `Event.Item`, `Event.Error`, or `Event.Complete`. |
| `skipItems(count)` | `suspend fun skipItems(count: Int)` | Suspends until `count` items have been skipped. |

#### Non-Suspending Methods

| Method | Signature | Description |
|--------|-----------|-------------|
| `expectNoEvents()` | `fun expectNoEvents()` | Asserts no unconsumed events exist right now. |
| `expectMostRecentItem()` | `fun expectMostRecentItem(): T` | Returns the most recently received item without suspending. Throws if no items received. |
| `ensureAllEventsConsumed()` | `fun ensureAllEventsConsumed()` | Asserts all received events have been consumed. Auto-called by `.test {}`. |

#### Cancellation Methods

| Method | Signature | Description |
|--------|-----------|-------------|
| `cancel()` | `suspend fun cancel()` | Cancels the turbine. |
| `cancelAndIgnoreRemainingEvents()` | `fun cancelAndIgnoreRemainingEvents()` | Cancels, discards any unconsumed events. Most common cleanup. |
| `cancelAndConsumeRemainingEvents()` | `fun cancelAndConsumeRemainingEvents(): List<Event<T>>` | Cancels, returns remaining events as a list. |

#### Other

| Method | Signature | Description |
|--------|-----------|-------------|
| `asChannel()` | `fun asChannel(): ReceiveChannel<T>` | Returns the underlying `ReceiveChannel`. |

---

### Standalone Turbine<T>

```kotlin
fun <T> Turbine(
    timeout: Duration? = null,
    name: String? = null
): Turbine<T>
```

A standalone turbine for capturing events from non-Flow sources (callbacks, fakes, etc.).

**Extends** `ReceiveTurbine<T>` with additional methods:

| Method | Signature | Description |
|--------|-----------|-------------|
| `add(item)` | `fun add(item: T)` | Adds an item event (non-suspending). |
| `close(cause?)` | `fun close(cause: Throwable? = null)` | Closes with optional error. |
| `asChannel()` | `fun asChannel(): Channel<T>` | Returns the underlying `Channel` (read-write). |
| `takeEvent()` | `fun takeEvent(): Event<T>` | Non-suspending. Asserts an event was received. |
| `takeItem()` | `fun takeItem(): T` | Non-suspending. Asserts an item was received. |
| `takeComplete()` | `fun takeComplete()` | Non-suspending. Asserts completion. |
| `takeError()` | `fun takeError(): Throwable` | Non-suspending. Asserts error. |

```kotlin
val turbine = Turbine<String>()
turbine.add("hello")
assertEquals("hello", turbine.awaitItem())
turbine.close()
turbine.awaitComplete()
```

---

### turbineScope

```kotlin
suspend fun turbineScope(
    timeout: Duration? = null,
    validate: suspend TurbineContext.() -> Unit
)
```

Creates a scope where multiple flows can be tested simultaneously. Auto-validates all turbines at scope exit.

```kotlin
runTest {
    turbineScope {
        val turbine1 = flow1.testIn(backgroundScope)
        val turbine2 = flow2.testIn(backgroundScope)
        assertEquals(a, turbine1.awaitItem())
        assertEquals(b, turbine2.awaitItem())
    }
}
```

---

## Molecule 2.2.0

### moleculeFlow

```kotlin
fun <T> moleculeFlow(
    mode: RecompositionMode,
    body: @Composable () -> T
): Flow<T>
```

Creates a cold `Flow<T>` that runs a `@Composable` function and emits each recomposition result.

**Parameters:**
- `mode` -- Controls when recomposition occurs.
- `body` -- The composable function producing values.

**Returns:** A `Flow<T>` that emits whenever the composable's return value changes.

---

### RecompositionMode

```kotlin
enum class RecompositionMode {
    Immediate,
    ContextClock
}
```

| Mode | Description | Use Case |
|------|-------------|----------|
| `Immediate` | Recomposes eagerly whenever state changes. No frame clock needed. | Unit tests, background processing |
| `ContextClock` | Uses `MonotonicFrameClock` from coroutine context. Behaves like Jetpack Compose. | UI-synchronized updates, integration tests |

**For unit tests, always use `RecompositionMode.Immediate`.**

---

### launchMolecule

```kotlin
fun CoroutineScope.launchMolecule(
    mode: RecompositionMode,
    body: @Composable () -> T
): StateFlow<T>
```

Launches molecule in the given scope and returns a `StateFlow<T>`. For production use, not typically used in tests (use `moleculeFlow` + Turbine instead).

---

### Testing Circuit Presenters with Molecule

The standard pattern for testing a Circuit `Presenter`:

```kotlin
@Test fun presenterEmitsStates() = runTest {
    moleculeFlow(RecompositionMode.Immediate) {
        presenter.present()
    }.test {
        // Assert each state emission
        val state = awaitItem()
        // ...
    }
}
```

**Why `moleculeFlow` + Turbine over `launchMolecule`:**
- `moleculeFlow` returns a cold `Flow`, composable with Turbine's `.test {}`.
- `launchMolecule` returns `StateFlow` which conflates and may skip intermediate states.
- Turbine's timeout and assertion API is richer than raw `StateFlow.value` checks.

---

## Import Cheatsheet

```kotlin
// kotlinx-coroutines-test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain

// Turbine
import app.cash.turbine.test
import app.cash.turbine.testIn
import app.cash.turbine.turbineScope
import app.cash.turbine.Turbine
import app.cash.turbine.Event

// Molecule
import app.cash.molecule.moleculeFlow
import app.cash.molecule.RecompositionMode

// kotlin.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertFailsWith
import kotlin.test.BeforeTest
import kotlin.test.AfterTest
```
