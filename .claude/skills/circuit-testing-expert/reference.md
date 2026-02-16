# Circuit Testing API Reference (v0.32.0)

## Gradle Dependencies

```toml
[versions]
circuit = "0.32.0"
kotlin = "2.1.0"
coroutines = "1.10.1"
turbine = "1.2.0"
compose = "1.7.6"

[libraries]
circuit-test = { module = "com.slack.circuit:circuit-test", version.ref = "circuit" }
circuit-foundation = { module = "com.slack.circuit:circuit-foundation", version.ref = "circuit" }
circuit-overlay = { module = "com.slack.circuit:circuit-overlay", version.ref = "circuit" }
kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
```

```kotlin
// build.gradle.kts
kotlin {
    sourceSets {
        commonTest.dependencies {
            implementation(libs.circuit.test)
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
        // For Compose UI tests (Android or Desktop)
        androidUnitTest.dependencies {
            implementation(libs.compose.ui.test.junit4)
            implementation(libs.compose.ui.test.manifest)
        }
    }
}
```

---

## Key Imports

```kotlin
// Core test utilities
import com.slack.circuit.test.test
import com.slack.circuit.test.presenterTestOf
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.TestEventSink

// Optional utilities
import com.slack.circuit.test.RecordingEventListener

// Overlay testing
import com.slack.circuit.overlay.test.FakeOverlayHost

// Coroutines + Turbine
import kotlinx.coroutines.test.runTest
import app.cash.turbine.test

// Kotlin test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

// Circuit core (used in test setup)
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.foundation.Circuit
```

---

## Presenter.test{} Extension

The primary way to test a Circuit Presenter. Internally powered by Molecule and Turbine.

### Signature

```kotlin
suspend fun <S : CircuitUiState> Presenter<S>.test(
    timeout: Duration = 1.seconds,
    name: String? = null,
    block: suspend CircuitReceiveTurbine<S>.() -> Unit,
)
```

### Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `timeout` | `Duration` | `1.seconds` | Timeout for waiting on state emissions |
| `name` | `String?` | `null` | Optional name for debugging Turbine output |
| `block` | `suspend CircuitReceiveTurbine<S>.() -> Unit` | -- | Test body with Turbine assertions |

### CircuitReceiveTurbine Methods

| Method | Return Type | Description |
|--------|-------------|-------------|
| `awaitItem()` | `S` | Suspends until the next distinct state is emitted. Throws on timeout. |
| `expectNoEvents()` | `Unit` | Asserts no state emissions have occurred. Non-suspending. |
| `cancelAndIgnoreRemainingEvents()` | `Unit` | Cancels the Turbine and ignores any unconsumed items. Use at test end. |
| `ensureAllEventsConsumed()` | `Unit` | Asserts that all emitted states have been consumed. Fails if pending items exist. |
| `skipItems(count: Int)` | `Unit` | Skips the specified number of state emissions. |
| `awaitError()` | `Throwable` | Suspends until an error is emitted. |
| `expectMostRecentItem()` | `S` | Returns the most recent emitted item without consuming intermediate items. |

### Key Behaviors

- **Deduplication**: Consecutive identical state objects (by `equals`) are collapsed into a single emission. If your presenter re-emits the same state, `awaitItem()` will not return a second time.
- **Molecule-backed**: The presenter's `@Composable present()` function runs inside Molecule, so Compose State, remember, LaunchedEffect, and other Compose APIs work normally.
- **Turbine-backed**: All assertion methods follow Turbine semantics. Unconsumed items at the end of the test block cause a failure unless explicitly handled.

---

## presenterTestOf

A top-level function for testing arbitrary `@Composable` functions that return `CircuitUiState`, without needing a `Presenter` instance.

### Signature

```kotlin
suspend fun <S : CircuitUiState> presenterTestOf(
    timeout: Duration = 1.seconds,
    name: String? = null,
    presentFunction: @Composable () -> S,
    block: suspend CircuitReceiveTurbine<S>.() -> Unit,
)
```

### Usage

```kotlin
@Test
fun `test composable state directly`() = runTest {
    val repo = FakeItemRepository()

    presenterTestOf(
        presentFunction = {
            val items by repo.itemsFlow().collectAsRetainedState(initial = emptyList())
            HomeScreen.State(
                items = items.toImmutableList(),
                isLoading = items.isEmpty(),
                eventSink = {},
            )
        }
    ) {
        val loading = awaitItem()
        assertTrue(loading.isLoading)

        repo.emit(listOf(item1))

        val loaded = awaitItem()
        assertFalse(loaded.isLoading)
        assertEquals(1, loaded.items.size)
    }
}
```

### When to Use presenterTestOf vs Presenter.test{}

| Scenario | Use |
|----------|-----|
| Testing a `Presenter` class | `presenter.test {}` |
| Testing a standalone composable function | `presenterTestOf {}` |
| Testing with complex DI setup | `presenter.test {}` with fakes |
| Quick prototyping / inline state logic | `presenterTestOf {}` |

---

## FakeNavigator

A test double for `Navigator` that records all navigation events for assertion.

### Constructors

```kotlin
// With a single initial screen
FakeNavigator(initialScreen: Screen)

// With a pre-built back stack
FakeNavigator(backStack: SaveableBackStack)
```

### Navigation Await Methods

These methods suspend until the corresponding navigation event occurs or timeout is reached.

| Method | Return Type | Description |
|--------|-------------|-------------|
| `awaitNextScreen()` | `Screen` | Returns the screen from the next `goTo` call. Convenience for `awaitNextGoTo().screen`. |
| `awaitNextGoTo()` | `NavEvent.GoTo` | Returns the full `GoTo` event including the screen. |
| `awaitNextPop()` | `NavEvent.Pop` | Returns the `Pop` event. Includes optional `PopResult`. |
| `awaitResetRoot()` | `NavEvent.ResetRoot` | Returns the `ResetRoot` event with the new root screen. |

### Negative Assertion Methods

These methods assert that no events of the given type have been recorded. Non-suspending.

| Method | Description |
|--------|-------------|
| `expectNoGoToEvents()` | Asserts no `goTo` calls were made. |
| `expectNoPopEvents()` | Asserts no `pop` calls were made. |
| `expectNoResetRootEvents()` | Asserts no `resetRoot` calls were made. |

### Back Stack Inspection

| Method | Return Type | Description |
|--------|-------------|-------------|
| `peek()` | `Screen?` | Returns the top screen on the back stack without removing it. |
| `peekBackStack()` | `ImmutableList<Screen>` | Returns a snapshot of the current back stack. |

### resetRoot Behavior

When `resetRoot(newRoot)` is called on `FakeNavigator`:
- The back stack is cleared
- `newRoot` becomes the only item on the stack
- `awaitResetRoot()` returns the event
- Subsequent `peek()` returns `newRoot`

### Pop with Result

```kotlin
// In presenter
navigator.pop(result = DetailScreen.Result.ItemDeleted(itemId))

// In test
val popEvent = navigator.awaitNextPop()
val result = popEvent.result
assertIs<DetailScreen.Result.ItemDeleted>(result)
assertEquals("123", result.itemId)
```

---

## TestEventSink

A test implementation of an event sink that records events for assertion. Primarily used in Compose UI tests.

### Constructor

```kotlin
TestEventSink<E : CircuitUiEvent>()
```

### Methods

| Method | Description |
|--------|-------------|
| `assertEvent(expected: E)` | Asserts the next recorded event equals `expected`. Removes it from the queue. |
| `assertEvents(vararg expected: E)` | Asserts multiple events in order. |
| `assertNoEvents()` | Asserts no events have been recorded. |

### How It Works

`TestEventSink` implements the `(E) -> Unit` function type, so it can be passed directly as an `eventSink` lambda in state objects:

```kotlin
val eventSink = TestEventSink<MyScreen.Event>()
val state = MyScreen.State(
    // ... state fields ...
    eventSink = eventSink,
)
```

When UI code calls `state.eventSink(SomeEvent)`, the event is recorded in the sink's internal queue.

---

## RecordingEventListener

Records Circuit lifecycle events for verifying presenter and UI creation order.

### Constructor

```kotlin
RecordingEventListener()
```

### Recorded Event Types

| Event | When Fired |
|-------|------------|
| `PresenterCreated(screen)` | A presenter is created for a screen |
| `UiCreated(screen)` | A UI is created for a screen |
| `PresenterDisposed(screen)` | A presenter is disposed |
| `UiDisposed(screen)` | A UI is disposed |
| `StateEmitted(screen, state)` | A presenter emits new state |

### Usage

```kotlin
val eventListener = RecordingEventListener()

val circuit = Circuit.Builder()
    .addPresenterFactory(...)
    .addUiFactory(...)
    .eventListener(eventListener)
    .build()

// After navigation
val events = eventListener.events
assertIs<RecordingEventListener.Event.PresenterCreated>(events[0])
assertEquals(HomeScreen, events[0].screen)
```

---

## FakeOverlayHost

A test double for `OverlayHost` that allows controlling overlay results in presenter tests.

### Setup

```kotlin
val overlayHost = FakeOverlayHost()
```

### Controlling Overlay Results

```kotlin
// Pre-configure the result for an overlay
overlayHost.setResult(ConfirmationOverlay.Result.Confirmed)

// In the presenter under test, when it calls:
//   val result = overlayHost.show(ConfirmationOverlay(...))
// It will immediately receive ConfirmationOverlay.Result.Confirmed
```

### Asserting Overlay Was Shown

```kotlin
// After the presenter triggers an overlay
val overlay = overlayHost.awaitNextOverlay()
assertIs<ConfirmationOverlay>(overlay)
assertEquals("Delete item?", overlay.message)
```

### Usage in Presenter Tests

```kotlin
@Test
fun `delete with confirmation`() = runTest {
    val overlayHost = FakeOverlayHost()
    overlayHost.setResult(ConfirmationOverlay.Result.Confirmed)

    val presenter = DetailPresenter(
        navigator = FakeNavigator(DetailScreen("1")),
        repo = fakeRepo,
        overlayHost = overlayHost,
    )

    presenter.test {
        val state = awaitItem()
        state.eventSink(DetailScreen.Event.DeleteClicked)

        // Verify overlay was shown
        val overlay = overlayHost.awaitNextOverlay()
        assertIs<ConfirmationOverlay>(overlay)

        // State should update after confirmation
        val updated = awaitItem()
        assertTrue(updated.isDeleted)
    }
}
```

---

## Testing Utilities Summary

| Class/Function | Package | Purpose |
|----------------|---------|---------|
| `Presenter.test{}` | `com.slack.circuit.test` | Test a Presenter with Turbine assertions |
| `presenterTestOf` | `com.slack.circuit.test` | Test a composable state function directly |
| `FakeNavigator` | `com.slack.circuit.test` | Record and assert navigation events |
| `TestEventSink` | `com.slack.circuit.test` | Record and assert UI events in Compose tests |
| `RecordingEventListener` | `com.slack.circuit.test` | Record Circuit lifecycle events |
| `FakeOverlayHost` | `com.slack.circuit.overlay.test` | Control overlay results in tests |
| `CircuitReceiveTurbine` | `com.slack.circuit.test` | Turbine wrapper with Circuit-specific methods |

---

## Test Coroutine Best Practices

### Always use runTest

```kotlin
@Test
fun `my presenter test`() = runTest {
    // Test body runs in TestScope
    presenter.test {
        // Turbine + Molecule run inside this scope
    }
}
```

### Advancing Time

```kotlin
@Test
fun `debounced search`() = runTest {
    presenter.test {
        val initial = awaitItem()
        initial.eventSink(SearchScreen.Event.QueryChanged("hello"))

        // Advance past debounce delay
        advanceTimeBy(300)

        val searching = awaitItem()
        assertTrue(searching.isSearching)
    }
}
```

### UnconfinedTestDispatcher for Immediate Dispatch

```kotlin
@Test
fun `immediate dispatch`() = runTest(UnconfinedTestDispatcher()) {
    presenter.test {
        // All coroutines dispatch immediately
        val state = awaitItem()
        // ...
    }
}
```
