# Testing API Reference

## kotlinx-coroutines-test 1.10.2

### runTest

The standard entry point for all coroutine tests. Provides a `TestScope` with virtual time control and automatic cleanup.

```kotlin
import kotlinx.coroutines.test.runTest

@Test
fun `my coroutine test`() = runTest {
    // `this` is TestScope
    // delay() advances virtual time, not real time
    val result = myRepository.fetchData()
    assertEquals(expected, result)
}
```

**Key behaviors:**
- Automatically advances virtual time for `delay()` calls.
- Fails if coroutines launched in the scope are not completed by the end of the block.
- Default timeout: 60 seconds of real time (configurable via `timeout` parameter).
- Use `backgroundScope` for coroutines that should not block test completion.

```kotlin
@Test
fun `test with custom timeout`() = runTest(timeout = 10.seconds) {
    // Fails if not complete within 10 real seconds
}
```

### runTest with Custom Dispatcher

```kotlin
@Test
fun `test with unconfined dispatcher`() = runTest(UnconfinedTestDispatcher()) {
    // All coroutines dispatch immediately
}
```

---

## TestScope

`TestScope` is the receiver of `runTest`. It provides virtual time control and coroutine management.

### advanceTimeBy(delayTimeMillis: Long)

Advances virtual time by exactly the specified amount. Coroutines with delays up to that point execute.

```kotlin
@Test
fun `advance time precisely`() = runTest {
    var value = "initial"
    launch {
        delay(500)
        value = "updated"
    }
    advanceTimeBy(499)
    assertEquals("initial", value)
    advanceTimeBy(1)
    assertEquals("updated", value)
}
```

### advanceUntilIdle()

Runs all pending coroutines until nothing is left to execute. Advances virtual time as needed.

```kotlin
@Test
fun `flush all pending work`() = runTest {
    var step = 0
    launch {
        delay(100); step = 1
        delay(200); step = 2
    }
    advanceUntilIdle()
    assertEquals(2, step)
}
```

### runCurrent()

Executes only coroutines scheduled for the current virtual time, without advancing the clock.

```kotlin
@Test
fun `execute only immediate work`() = runTest {
    var immediate = false
    var delayed = false
    launch { immediate = true }
    launch { delay(100); delayed = true }
    runCurrent()
    assertTrue(immediate)
    assertFalse(delayed)
}
```

### backgroundScope

A scope for coroutines that should not prevent test completion. Useful for long-running collectors.

```kotlin
@Test
fun `background scope does not block`() = runTest {
    val values = mutableListOf<Int>()
    backgroundScope.launch {
        flowOf(1, 2, 3).collect { values.add(it) }
    }
    advanceUntilIdle()
    assertEquals(listOf(1, 2, 3), values)
}
```

### currentTime

Access the current virtual time in milliseconds.

```kotlin
@Test
fun `track virtual clock`() = runTest {
    assertEquals(0L, currentTime)
    advanceTimeBy(1000)
    assertEquals(1000L, currentTime)
}
```

---

## Test Dispatchers

### UnconfinedTestDispatcher

Coroutines dispatch immediately upon launch, similar to `Dispatchers.Unconfined`.

```kotlin
@Test
fun `immediate dispatch`() = runTest(UnconfinedTestDispatcher()) {
    var value = 0
    launch { value = 1 }
    assertEquals(1, value) // Already executed
}
```

**When to use:** `stateIn`/`shareIn` flows that need an immediate collector.

**When NOT to use:** Timing-dependent behavior (debounce, delay, timeout).

### StandardTestDispatcher

The default inside `runTest`. Coroutines are queued and require explicit advancement.

```kotlin
@Test
fun `queued dispatch`() = runTest {
    var value = 0
    launch { value = 1 }
    assertEquals(0, value)      // Not yet executed
    advanceUntilIdle()
    assertEquals(1, value)      // Now executed
}
```

### Comparison

| Feature | StandardTestDispatcher | UnconfinedTestDispatcher |
|---------|----------------------|--------------------------|
| Execution | Queued, manual advance | Immediate |
| Time control | Full | Limited |
| Determinism | High | Lower |
| Primary use case | Most tests | `stateIn`/`shareIn` collectors |

---

## Turbine 1.2.1

Turbine (`app.cash.turbine`) provides structured Flow testing with timeout-guarded assertions.

### test {}

The primary API. Collects a Flow and provides assertion methods on emissions.

```kotlin
flowOf(1, 2, 3).test {
    assertEquals(1, awaitItem())
    assertEquals(2, awaitItem())
    assertEquals(3, awaitItem())
    awaitComplete()
}
```

### testIn(scope)

Starts collection in the given `CoroutineScope` and returns a `ReceiveTurbine` for later assertions.

```kotlin
@Test
fun `test in background`() = runTest {
    val turbine = myFlow.testIn(backgroundScope)
    // Perform actions that cause emissions
    assertEquals(expected, turbine.awaitItem())
    turbine.cancelAndIgnoreRemainingEvents()
}
```

### Core Assertions

| Method | Description |
|--------|-------------|
| `awaitItem()` | Waits for the next emission. Fails on timeout (3s default). |
| `awaitComplete()` | Waits for normal Flow completion. |
| `awaitError()` | Waits for an error terminal event. Returns the `Throwable`. |
| `expectNoEvents()` | Asserts no events have been received (no emission, error, or completion). |
| `cancelAndIgnoreRemainingEvents()` | Cancels collection and ignores unconsumed events. |
| `cancelAndConsumeRemainingEvents()` | Cancels and returns all remaining events as a list. |
| `skipItems(count)` | Skips `count` items. Fails if fewer items are emitted. |
| `ensureNoMoreEvents()` | Asserts that no more events will be emitted. |

### awaitItem()

```kotlin
myFlow.test {
    val first = awaitItem()
    assertEquals(Loading, first)
    val second = awaitItem()
    assertIs<Success>(second)
}
```

### awaitError()

```kotlin
failingFlow.test {
    val error = awaitError()
    assertIs<IOException>(error)
}
```

### expectNoEvents()

```kotlin
idleFlow.test {
    expectNoEvents()               // Nothing emitted yet
    triggerEmission()
    assertEquals(expected, awaitItem())
}
```

### turbineScope

Creates a scope where multiple flows can be tested concurrently.

```kotlin
@Test
fun `test multiple flows`() = runTest {
    turbineScope {
        val flow1 = flowA.testIn(backgroundScope)
        val flow2 = flowB.testIn(backgroundScope)
        assertEquals(1, flow1.awaitItem())
        assertEquals("a", flow2.awaitItem())
        flow1.cancelAndIgnoreRemainingEvents()
        flow2.cancelAndIgnoreRemainingEvents()
    }
}
```

### Custom Timeout

```kotlin
slowFlow.test(timeout = 10.seconds) {
    val item = awaitItem()         // Waits up to 10 seconds
}
```

### Turbine with runTest

Turbine integrates with virtual time. Delays inside tested Flows are advanced automatically.

```kotlin
@Test
fun `debounced flow`() = runTest {
    val input = MutableSharedFlow<String>()
    val debounced = input.debounce(300)
    debounced.test {
        input.emit("a")
        advanceTimeBy(100)
        input.emit("b")
        advanceTimeBy(350)
        assertEquals("b", awaitItem())
        cancelAndIgnoreRemainingEvents()
    }
}
```

---

## Circuit Test Utilities

### Presenter.test {}

Circuit provides a `test` extension on `Presenter` that uses Molecule under the hood.

```kotlin
@Test
fun `presenter emits loading then success`() = runTest {
    val presenter = ProductListPresenter(fakeRepository)
    presenter.test {
        val loading = awaitItem()
        assertIs<State.Loading>(loading)

        val success = awaitItem()
        assertIs<State.Success>(success)
        assertEquals(2, success.products.size)
    }
}
```

### FakeNavigator

Circuit's `FakeNavigator` records navigation calls for verification.

```kotlin
@Test
fun `back event pops navigator`() = runTest {
    val navigator = FakeNavigator(ProductDetailScreen("1"))
    val presenter = ProductDetailPresenter(
        screen = ProductDetailScreen("1"),
        navigator = navigator,
        repository = fakeRepository,
    )
    presenter.test {
        val state = awaitItem()
        state.eventSink(Event.NavigateBack)
        navigator.awaitPop()
    }
}
```

### Key FakeNavigator Methods

| Method | Description |
|--------|-------------|
| `awaitPop()` | Waits for a `pop()` call. Fails on timeout. |
| `awaitNextScreen()` | Waits for a `goTo(screen)` call. Returns the screen. |
| `awaitResetRoot()` | Waits for a `resetRoot(screen)` call. |
| `expectNoEvents()` | Asserts no navigation events occurred. |

---

## Compose UI Testing

### ComposeTestRule

Android Compose tests use `createComposeRule()` or `createAndroidComposeRule<Activity>()`.

```kotlin
@get:Rule
val composeTestRule = createComposeRule()

@Test
fun `button is displayed and clickable`() {
    composeTestRule.setContent {
        MyButton(text = "Submit", onClick = {})
    }
    composeTestRule.onNodeWithText("Submit")
        .assertIsDisplayed()
        .performClick()
}
```

### Node Finders

| Finder | Description |
|--------|-------------|
| `onNodeWithText("text")` | Find by visible text |
| `onNodeWithContentDescription("desc")` | Find by accessibility content description |
| `onNodeWithTag("tag")` | Find by `Modifier.testTag("tag")` |
| `onAllNodesWithText("text")` | Find all nodes matching text |
| `onRoot()` | The root semantic node |

### Actions

| Action | Description |
|--------|-------------|
| `performClick()` | Click the node |
| `performTextInput("text")` | Type text into a text field |
| `performTextClearance()` | Clear text field |
| `performScrollTo()` | Scroll to make the node visible |
| `performTouchInput { swipeLeft() }` | Gesture input |

### Assertions

| Assertion | Description |
|-----------|-------------|
| `assertIsDisplayed()` | Node is rendered and visible |
| `assertIsNotDisplayed()` | Node exists but is not visible |
| `assertDoesNotExist()` | Node is not in the tree at all |
| `assertIsEnabled()` | Node is interactive |
| `assertIsNotEnabled()` | Node is disabled |
| `assertTextEquals("text")` | Exact text match |
| `assertTextContains("partial")` | Partial text match |
| `assertHasClickAction()` | Node responds to clicks |

### waitForIdle / waitUntil

```kotlin
composeTestRule.waitForIdle()              // Wait for all pending compositions

composeTestRule.waitUntil(timeoutMillis = 5000) {
    composeTestRule.onAllNodesWithText("Loaded")
        .fetchSemanticsNodes()
        .isNotEmpty()
}
```

### Testing State Directly

Test composables in isolation by passing state directly instead of using presenters.

```kotlin
@Test
fun `error state shows retry button`() {
    composeTestRule.setContent {
        ProductListUi(
            state = ProductListScreen.State.Error(
                error = AppError.Network.NoConnection(),
                eventSink = {},
            ),
        )
    }
    composeTestRule.onNodeWithText("No internet connection").assertIsDisplayed()
    composeTestRule.onNodeWithText("Try Again").assertIsDisplayed()
}
```

---

## MockK (JVM/Android Only)

MockK is used in `androidUnitTest` or `jvmTest` when platform-specific mocking is required. Prefer fakes in `commonTest`.

### mockk

```kotlin
val mockApi = mockk<ProductApi>()
```

### every / coEvery

```kotlin
every { mockApi.getBaseUrl() } returns "https://api.example.com"

coEvery { mockApi.fetchProducts() } returns listOf(product1, product2)

coEvery { mockApi.fetchProducts() } throws IOException("Network error")
```

### verify / coVerify

```kotlin
verify(exactly = 1) { mockApi.getBaseUrl() }

coVerify { mockApi.fetchProducts() }

coVerify(exactly = 0) { mockApi.deleteProduct(any()) }
```

### slot / capture

```kotlin
val idSlot = slot<String>()

coEvery { mockApi.fetchProduct(capture(idSlot)) } returns product

// After calling the method under test:
assertEquals("123", idSlot.captured)
```

### relaxed

```kotlin
val mockAnalytics = mockk<Analytics>(relaxed = true)
// All methods return default values without explicit stubbing
```

---

## Version Summary

| Library | Version | Module |
|---------|---------|--------|
| kotlinx-coroutines-test | 1.10.2 | `org.jetbrains.kotlinx:kotlinx-coroutines-test` |
| Turbine | 1.2.1 | `app.cash.turbine:turbine` |
| Circuit Test | (matches Circuit version) | `com.slack.circuit:circuit-test` |
| Compose UI Test | (matches Compose version) | `androidx.compose.ui:ui-test-junit4` |
| MockK | 1.13.16 | `io.mockk:mockk` (JVM only) |

### Version Catalog Entries

```toml
[versions]
kotlinx-coroutines = "1.10.2"
turbine = "1.2.1"

[libraries]
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinx-coroutines" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
```
