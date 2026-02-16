---
name: coroutines-test-expert
description: Expert guidance on Testing Kotlin Coroutines. Use for TestScope, runTest, Turbine, Molecule, and controlling virtual time in KMP tests.
---

# Coroutines Test Expert Skill

## Overview

Testing coroutines requires controlling time, dispatchers, and flow emissions. This skill covers `kotlinx-coroutines-test` (1.10.2), `app.cash.turbine` (1.2.1), and `cashapp/molecule` (2.2.0) for testing Circuit Presenters.

## When to Use

- **Unit Testing**: Testing suspend functions, Flows, StateFlows, SharedFlows.
- **Time Control**: Skipping delays with virtual time (`advanceTimeBy`, `advanceUntilIdle`).
- **Flow Assertions**: Verifying emissions, errors, and completions with Turbine.
- **Presenter Testing**: Testing Circuit `Presenter` logic via Molecule + Turbine.
- **Concurrency Testing**: Verifying parallel operations, timeouts, retries.

## Core Concepts

### 1. runTest

The standard entry point for coroutine tests. Replaces `runBlocking` for test bodies.

- Creates a `TestScope` with a `StandardTestDispatcher` by default.
- Auto-advances virtual time for top-level `delay` calls in the test body.
- Calls `advanceUntilIdle()` before completing the test to run all pending tasks.
- Fails the test if uncaught exceptions occur in child coroutines.
- Default timeout: 60 seconds (configurable via `timeout` parameter).

```kotlin
@Test
fun basicTest() = runTest {
    val result = repository.fetchData() // suspend fun with internal delay
    assertEquals(expected, result)
    // Virtual time was advanced automatically
}
```

### 2. TestDispatcher: Standard vs Unconfined

Two dispatcher implementations with different execution semantics:

**StandardTestDispatcher** (default in `runTest`):
- Queues coroutine resumptions; requires explicit `advanceUntilIdle()`, `advanceTimeBy()`, or `runCurrent()` to execute.
- Best for: precise control over execution order and timing.
- Child `launch` blocks do NOT execute until you advance the scheduler.

**UnconfinedTestDispatcher**:
- Enters child coroutines eagerly (executes until first suspension).
- Best for: tests where exact dispatch order is not critical.
- Useful for collecting StateFlow/SharedFlow emissions immediately.

```kotlin
// Standard: explicit advancement required
@Test fun standard() = runTest {
    var executed = false
    launch { executed = true }
    assertFalse(executed)       // not yet
    advanceUntilIdle()
    assertTrue(executed)        // now
}

// Unconfined: eager execution
@Test fun unconfined() = runTest(UnconfinedTestDispatcher()) {
    var executed = false
    launch { executed = true }
    assertTrue(executed)        // immediately
}
```

### 3. TestScope Time Control

`TestScope` provides these time control methods (all operate on the underlying `TestCoroutineScheduler`):

| Method | Behavior |
|--------|----------|
| `advanceTimeBy(ms)` | Moves virtual clock forward, runs tasks scheduled within that window |
| `advanceTimeBy(duration)` | Duration overload of the above |
| `advanceUntilIdle()` | Runs all pending tasks, advancing time as needed |
| `runCurrent()` | Runs only tasks scheduled at the current virtual time |
| `currentTime` | Returns current virtual time in milliseconds |
| `testTimeSource` | Returns scheduler as a `TimeSource` for measuring durations |
| `testScheduler` | Direct access to `TestCoroutineScheduler` |
| `backgroundScope` | Scope for background work, auto-cancelled when test ends |

### 4. backgroundScope

A `CoroutineScope` within `TestScope` for launching long-running background work:

- Coroutines in `backgroundScope` execute during `advanceTimeBy` and `runCurrent`.
- `advanceUntilIdle()` stops once only `backgroundScope` coroutines remain (prevents hanging).
- All `backgroundScope` coroutines are cancelled when the test completes.
- Failures are reported at test end, not immediately.
- Essential for `stateIn`, `shareIn`, and infinite collection loops.

```kotlin
@Test fun stateFlowInBackground() = runTest {
    val flow = repository.observeItems()
    val stateFlow = flow.stateIn(backgroundScope, SharingStarted.Eagerly, emptyList())
    advanceUntilIdle()
    assertEquals(listOf(item1), stateFlow.value)
}
```

### 5. Turbine Deep Patterns

Turbine provides structured assertions for Flow emissions.

**`Flow.test {}`** -- Primary API. Collects the flow in a coroutine, runs assertions, auto-calls `ensureAllEventsConsumed()` at block end. Fails if unconsumed events remain.

**`Flow.testIn(scope)`** -- Returns a `ReceiveTurbine<T>` for use outside a lambda. Caller must explicitly cancel or consume terminal events.

**Standalone `Turbine<T>()`** -- For building fakes or capturing emissions from non-Flow sources. Supports `add(item)`, `close()`, and all `ReceiveTurbine` assertion methods.

Key assertion methods on `ReceiveTurbine<T>`:

| Method | Behavior |
|--------|----------|
| `awaitItem()` | Suspends until item emitted, returns it |
| `awaitError()` | Suspends until error, returns Throwable |
| `awaitComplete()` | Suspends until flow completes |
| `skipItems(count)` | Skips N item emissions |
| `expectNoEvents()` | Asserts nothing was emitted (non-suspending) |
| `expectMostRecentItem()` | Returns most recently received item (non-suspending) |
| `cancelAndIgnoreRemainingEvents()` | Cancels, ignores leftovers |
| `cancelAndConsumeRemainingEvents()` | Cancels, returns leftover events |
| `ensureAllEventsConsumed()` | Asserts all events consumed (auto-called by `.test`) |

### 6. Molecule for Circuit Presenter Testing

Test Circuit `Presenter` composable functions by wrapping them in `moleculeFlow` with `RecompositionMode.Immediate`, then asserting emissions via Turbine.

```kotlin
@Test fun presenterTest() = runTest {
    moleculeFlow(RecompositionMode.Immediate) {
        presenter.present()
    }.test {
        val initial = awaitItem()
        assertEquals(LoadingState, initial)

        val loaded = awaitItem()
        assertEquals(expectedData, loaded.items)
    }
}
```

**RecompositionMode.Immediate**: Recomposes eagerly whenever state changes. Use for unit tests.
**RecompositionMode.ContextClock**: Uses `MonotonicFrameClock` from coroutine context. Use for UI-synchronized updates (not for unit tests).

### 7. Common Pitfalls

| Pitfall | Cause | Fix |
|---------|-------|-----|
| Test hangs indefinitely | Missing `advanceUntilIdle()` with `StandardTestDispatcher` | Call `advanceUntilIdle()` or use `UnconfinedTestDispatcher` |
| StateFlow skips values | `StandardTestDispatcher` queues emissions | Use `UnconfinedTestDispatcher(testScheduler)` for collector |
| `stateIn` never completes | Uses `this` scope, `advanceUntilIdle` waits forever | Use `backgroundScope` for `stateIn`/`shareIn` |
| Turbine timeout | Emission never arrives within default 3s | Check dispatcher, advance time, or increase `timeout` |
| Unconsumed events error | Did not consume all Turbine events | Use `cancelAndIgnoreRemainingEvents()` or consume all |
| Wrong `testScheduler` | Created `UnconfinedTestDispatcher()` without linking scheduler | Pass `testScheduler`: `UnconfinedTestDispatcher(testScheduler)` |
| `Dispatchers.Main` crash | `Main` not available in unit tests (no Android) | Use `Dispatchers.setMain(testDispatcher)` in `@BeforeTest` |

### 8. Dispatchers.setMain / resetMain

For code that uses `Dispatchers.Main` (common in ViewModels), override it in tests:

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class MyViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun test() = runTest(testDispatcher) {
        // Dispatchers.Main now uses testDispatcher
    }
}
```

## Dependencies

```toml
# gradle/libs.versions.toml
[versions]
coroutines = "1.10.2"
turbine = "1.2.1"
molecule = "2.2.0"

[libraries]
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
molecule-runtime = { module = "app.cash.molecule:molecule-runtime", version.ref = "molecule" }
```

```kotlin
// build.gradle.kts (commonTest)
kotlin {
    sourceSets {
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
            implementation(libs.molecule.runtime)
        }
    }
}
```
