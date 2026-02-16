---
name: circuit-testing-expert
description: Expert guidance on testing Circuit screens, presenters, navigation, and overlays. Use for Presenter.test{}, FakeNavigator, TestEventSink, snapshot testing, and integration testing patterns.
---

# Circuit Testing Expert Skill (v0.32.0)

## Overview

Circuit provides first-class testing support via the `circuit-test` artifact. Test Presenters in isolation using `Presenter.test{}` (backed by Molecule + Turbine), verify navigation with `FakeNavigator`, and assert UI events with `TestEventSink`.

## When to Use

- Testing Presenter business logic in isolation
- Verifying navigation calls (goTo, pop, resetRoot)
- Testing overlay interactions
- Testing event handling (eventSink)
- Integration testing with real Circuit wiring
- Snapshot/screenshot testing of Circuit UIs

## Quick Reference

For detailed testing APIs, see [reference.md](reference.md).
For complete test examples, see [examples.md](examples.md).

## Testing Artifacts

```toml
[libraries]
circuit-test = { module = "com.slack.circuit:circuit-test", version.ref = "circuit" }
```

```kotlin
commonTest.dependencies {
    implementation(libs.circuit.test)
    implementation(libs.kotlin.test)
    implementation(libs.kotlinx.coroutines.test)
    implementation(libs.turbine)
}
```

## Core Testing Pattern

### 1. Presenter Test with Presenter.test{}

```kotlin
@Test
fun `loading then success`() = runTest {
    val fakeRepo = FakeItemRepository(items = listOf(item1, item2))
    val navigator = FakeNavigator(HomeScreen)

    val presenter = HomePresenter(
        navigator = navigator,
        repo = fakeRepo,
    )

    presenter.test {
        // First emission: loading
        val loading = awaitItem()
        assertTrue(loading.isLoading)
        assertTrue(loading.items.isEmpty())

        // Second emission: loaded
        val loaded = awaitItem()
        assertFalse(loaded.isLoading)
        assertEquals(2, loaded.items.size)

        // Trigger navigation event
        loaded.eventSink(HomeScreen.Event.ItemClicked("1"))

        // Verify navigation
        assertEquals(DetailScreen("1"), navigator.awaitNextScreen())
    }
}
```

### CRITICAL: Multiple Snapshot Writes in Event Handlers

**Molecule's `RecompositionMode.Immediate` (used by `Presenter.test{}`) triggers a separate state emission for EACH `rememberRetained`/`mutableStateOf` write.** When one event handler modifies N state variables, Turbine sees N emissions.

**Problem pattern:**
```kotlin
// Event handler modifies 3 states → 3 emissions
is Event.SubmitForm -> {
    usernameError = validateUsername(username)  // emission 1
    emailError = validateEmail(email)          // emission 2
    passwordError = validatePassword(password) // emission 3
}
```

**Fix 1: Isolate the state change under test.** Fill all VALID fields before submit so only ONE validation error is set (ONE state change → ONE emission):
```kotlin
// Test only emailError by making username/password valid
s.eventSink(Event.UsernameChanged("validuser"))
val s1 = awaitItem()
s1.eventSink(Event.PasswordChanged("validpass123"))
val s2 = awaitItem()
s2.eventSink(Event.EmailChanged("invalid"))
val s3 = awaitItem()
s3.eventSink(Event.SubmitForm)
val error = awaitItem() // Only emailError changes → 1 emission
assertEquals("Invalid email", error.emailError)
```

**Fix 2: Consume intermediate emissions** when an event must modify multiple states (e.g., clearing field + error):
```kotlin
// Event handler: email = newValue; emailError = null (2 writes → 2 emissions)
state.eventSink(Event.EmailChanged("a"))
var latest = awaitItem()
if (latest.emailError != null) latest = awaitItem()
assertNull(latest.emailError)
```

### 2. FakeNavigator Assertions

```kotlin
// Verify specific navigation events
navigator.awaitNextScreen()           // Returns the Screen from goTo
navigator.awaitNextGoTo()             // Returns NavEvent.GoTo
navigator.awaitNextPop()              // Returns NavEvent.Pop
navigator.awaitResetRoot()            // Returns NavEvent.ResetRoot

// Negative assertions
navigator.expectNoGoToEvents()
navigator.expectNoPopEvents()
navigator.expectNoResetRootEvents()

// Back stack inspection
navigator.peek()
navigator.peekBackStack()
```

### 3. TestEventSink for UI Testing

```kotlin
@Test
fun `button click sends event`() {
    val eventSink = TestEventSink<HomeScreen.Event>()
    val state = HomeScreen.State(
        items = persistentListOf(item1),
        isLoading = false,
        eventSink = eventSink,
    )

    composeTestRule.setContent {
        HomeUi(state)
    }

    composeTestRule.onNodeWithText("Item 1").performClick()
    eventSink.assertEvent(HomeScreen.Event.ItemClicked("1"))
}
```

## Testing Decision Matrix

| What to Test | Tool | Approach |
|-------------|------|----------|
| Presenter logic | `Presenter.test{}` | Create presenter with fakes, assert state emissions |
| Navigation calls | `FakeNavigator` | Assert goTo/pop/resetRoot after events |
| UI rendering | Compose Test | Set state directly, assert composable output |
| UI events | `TestEventSink` | Pass to state, assert events after interactions |
| Overlays | `FakeOverlayHost` | Mock overlay results |
| Full integration | `Circuit.Builder` in test | Wire real presenters + UIs |

## Core Rules

1. **Test Presenters and UIs independently** -- Presenter tests verify state logic; UI tests verify rendering.
2. **Use `FakeNavigator` with initial screen** -- `FakeNavigator(MyScreen)` sets up proper back stack.
3. **`awaitItem()` only emits on state changes** -- Circuit test uses Turbine which deduplicates identical emissions.
4. **Always consume all events** -- Call `ensureAllEventsConsumed()` or `cancelAndIgnoreRemainingEvents()` at test end.
5. **Use `runTest` from kotlinx-coroutines-test** -- All presenter tests need a test coroutine scope.
6. **Fake dependencies, not the Presenter** -- Create real Presenter instances with fake repositories/services.

## Common Pitfalls

1. **Not awaiting state after event** -- After `eventSink(event)`, call `awaitItem()` to get the updated state.
2. **Expecting duplicate emissions** -- `Presenter.test{}` deduplicates; if state doesn't change, no new emission.
3. **Using deprecated FakeNavigator APIs** -- `assertIsEmpty` and `expectNoEvents` are deprecated; use type-specific methods.
4. **Testing navigation in UI tests** -- Navigation is a Presenter concern. Test it in Presenter tests, not UI tests.
5. **Forgetting `cancelAndIgnoreRemainingEvents()`** -- Turbine requires consuming or explicitly ignoring remaining items.

## See Also

- [circuit-expert](../circuit-expert/SKILL.md) -- Core Circuit Screen/Presenter/Ui patterns
- [coroutines-test-expert](../coroutines-test-expert/SKILL.md) -- runTest, TestScope, virtual time
- [testing-expert](../testing-expert/SKILL.md) -- Broader testing strategy and test pyramid
