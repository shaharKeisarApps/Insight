# Circuit Testing Examples (v0.32.0)

## Shared Test Infrastructure

The examples below share these common screen definitions and fakes:

```kotlin
// --- Screens ---

@Parcelize
data object HomeScreen : Screen {
    data class State(
        val items: ImmutableList<Item>,
        val isLoading: Boolean,
        val error: String? = null,
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data class ItemClicked(val id: String) : Event
        data object RefreshClicked : Event
        data class DeleteItem(val id: String) : Event
    }
}

@Parcelize
data class DetailScreen(val itemId: String) : Screen {
    data class State(
        val item: Item?,
        val isLoading: Boolean,
        val isDeleted: Boolean = false,
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data object BackClicked : Event
        data object DeleteClicked : Event
        data object EditClicked : Event
    }

    sealed interface Result : PopResult {
        data class ItemDeleted(val itemId: String) : Result
    }
}

@Parcelize
data class SearchScreen(val query: String = "") : Screen {
    data class State(
        val query: String,
        val results: ImmutableList<Item>,
        val isSearching: Boolean,
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data class QueryChanged(val query: String) : Event
        data class ResultClicked(val id: String) : Event
    }
}

data class Item(val id: String, val title: String, val description: String)

// --- Fakes ---

class FakeItemRepository(
    private var items: List<Item> = emptyList(),
    private var shouldFail: Boolean = false,
) {
    private val _itemsFlow = MutableStateFlow(items)

    fun itemsFlow(): Flow<List<Item>> = _itemsFlow

    suspend fun getItem(id: String): Item? {
        if (shouldFail) throw IOException("Network error")
        return items.find { it.id == id }
    }

    suspend fun deleteItem(id: String) {
        if (shouldFail) throw IOException("Network error")
        items = items.filter { it.id != id }
        _itemsFlow.value = items
    }

    suspend fun search(query: String): List<Item> {
        if (shouldFail) throw IOException("Network error")
        return items.filter { it.title.contains(query, ignoreCase = true) }
    }

    fun emit(newItems: List<Item>) {
        items = newItems
        _itemsFlow.value = newItems
    }

    fun setShouldFail(fail: Boolean) {
        shouldFail = fail
    }
}

// --- Sample Data ---

val item1 = Item(id = "1", title = "First Item", description = "Description 1")
val item2 = Item(id = "2", title = "Second Item", description = "Description 2")
val item3 = Item(id = "3", title = "Third Item", description = "Description 3")
```

---

## Example 1: Basic Presenter Test

Testing loading-to-success state transitions with `Presenter.test{}`.

```kotlin
class HomePresenterTest {

    @Test
    fun `emits loading then success with items`() = runTest {
        val repo = FakeItemRepository(items = listOf(item1, item2))
        val navigator = FakeNavigator(HomeScreen)

        val presenter = HomePresenter(
            navigator = navigator,
            repo = repo,
        )

        presenter.test {
            // First emission: loading state
            val loading = awaitItem()
            assertTrue(loading.isLoading)
            assertTrue(loading.items.isEmpty())
            assertNull(loading.error)

            // Second emission: items loaded
            val loaded = awaitItem()
            assertFalse(loaded.isLoading)
            assertEquals(2, loaded.items.size)
            assertEquals("First Item", loaded.items[0].title)
            assertEquals("Second Item", loaded.items[1].title)
            assertNull(loaded.error)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emits empty state when no items`() = runTest {
        val repo = FakeItemRepository(items = emptyList())
        val navigator = FakeNavigator(HomeScreen)

        val presenter = HomePresenter(
            navigator = navigator,
            repo = repo,
        )

        presenter.test {
            // Loading state may or may not appear depending on timing
            val state = expectMostRecentItem()
            assertFalse(state.isLoading)
            assertTrue(state.items.isEmpty())

            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

---

## Example 2: Navigation Test

Testing goTo, pop, and resetRoot via `FakeNavigator`.

```kotlin
class NavigationPresenterTest {

    @Test
    fun `item click navigates to detail screen`() = runTest {
        val repo = FakeItemRepository(items = listOf(item1, item2))
        val navigator = FakeNavigator(HomeScreen)

        val presenter = HomePresenter(
            navigator = navigator,
            repo = repo,
        )

        presenter.test {
            // Wait for loaded state
            val loaded = expectMostRecentItem()
            assertFalse(loaded.isLoading)

            // Trigger navigation
            loaded.eventSink(HomeScreen.Event.ItemClicked("2"))

            // Assert goTo was called with correct screen
            val nextScreen = navigator.awaitNextScreen()
            assertIs<DetailScreen>(nextScreen)
            assertEquals("2", nextScreen.itemId)

            // Verify no extra navigation events
            navigator.expectNoGoToEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `back click pops navigator`() = runTest {
        val repo = FakeItemRepository(items = listOf(item1))
        val navigator = FakeNavigator(DetailScreen("1"))

        val presenter = DetailPresenter(
            navigator = navigator,
            repo = repo,
        )

        presenter.test {
            val state = expectMostRecentItem()

            // Trigger back
            state.eventSink(DetailScreen.Event.BackClicked)

            // Assert pop
            val popEvent = navigator.awaitNextPop()
            assertNull(popEvent.result)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `delete pops with result`() = runTest {
        val repo = FakeItemRepository(items = listOf(item1))
        val navigator = FakeNavigator(DetailScreen("1"))

        val presenter = DetailPresenter(
            navigator = navigator,
            repo = repo,
        )

        presenter.test {
            val state = expectMostRecentItem()

            // Trigger delete (assume overlay confirms automatically)
            state.eventSink(DetailScreen.Event.DeleteClicked)

            // Assert pop with result
            val popEvent = navigator.awaitNextPop()
            val result = popEvent.result
            assertIs<DetailScreen.Result.ItemDeleted>(result)
            assertEquals("1", result.itemId)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `reset root after logout`() = runTest {
        val navigator = FakeNavigator(HomeScreen)

        val presenter = SettingsPresenter(navigator = navigator)

        presenter.test {
            val state = awaitItem()

            state.eventSink(SettingsScreen.Event.LogoutClicked)

            // Assert reset root to login
            val resetEvent = navigator.awaitResetRoot()
            assertIs<LoginScreen>(resetEvent.newRoot)

            // Back stack should only contain the new root
            assertEquals(1, navigator.peekBackStack().size)
            assertEquals(LoginScreen, navigator.peek())

            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

---

## Example 3: Event Handling Test

Testing multiple events and the resulting state changes.

```kotlin
class EventHandlingPresenterTest {

    @Test
    fun `multiple events produce correct state sequence`() = runTest {
        val repo = FakeItemRepository(items = listOf(item1, item2, item3))
        val navigator = FakeNavigator(HomeScreen)

        val presenter = HomePresenter(
            navigator = navigator,
            repo = repo,
        )

        presenter.test {
            // Wait for loaded state
            val loaded = expectMostRecentItem()
            assertEquals(3, loaded.items.size)

            // Delete first item
            loaded.eventSink(HomeScreen.Event.DeleteItem("1"))

            // State updates with item removed
            val afterDelete = awaitItem()
            assertEquals(2, afterDelete.items.size)
            assertFalse(afterDelete.items.any { it.id == "1" })

            // Delete second item
            afterDelete.eventSink(HomeScreen.Event.DeleteItem("2"))

            val afterSecondDelete = awaitItem()
            assertEquals(1, afterSecondDelete.items.size)
            assertEquals("3", afterSecondDelete.items[0].id)

            // Navigate to remaining item
            afterSecondDelete.eventSink(HomeScreen.Event.ItemClicked("3"))
            assertEquals(DetailScreen("3"), navigator.awaitNextScreen())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refresh reloads items`() = runTest {
        val repo = FakeItemRepository(items = listOf(item1))
        val navigator = FakeNavigator(HomeScreen)

        val presenter = HomePresenter(
            navigator = navigator,
            repo = repo,
        )

        presenter.test {
            val initial = expectMostRecentItem()
            assertEquals(1, initial.items.size)

            // Add items to repo, then refresh
            repo.emit(listOf(item1, item2, item3))
            initial.eventSink(HomeScreen.Event.RefreshClicked)

            val refreshed = awaitItem()
            assertEquals(3, refreshed.items.size)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

---

## Example 4: Error State Test

Testing how the presenter handles repository failures.

```kotlin
class ErrorStatePresenterTest {

    @Test
    fun `repository failure emits error state`() = runTest {
        val repo = FakeItemRepository(shouldFail = true)
        val navigator = FakeNavigator(HomeScreen)

        val presenter = HomePresenter(
            navigator = navigator,
            repo = repo,
        )

        presenter.test {
            // May see loading first
            val loading = awaitItem()
            assertTrue(loading.isLoading)

            // Then error
            val error = awaitItem()
            assertFalse(error.isLoading)
            assertTrue(error.items.isEmpty())
            assertEquals("Network error", error.error)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `retry after error succeeds`() = runTest {
        val repo = FakeItemRepository(shouldFail = true)
        val navigator = FakeNavigator(HomeScreen)

        val presenter = HomePresenter(
            navigator = navigator,
            repo = repo,
        )

        presenter.test {
            // Get to error state
            val error = expectMostRecentItem()
            assertEquals("Network error", error.error)

            // Fix the repo, then retry
            repo.setShouldFail(false)
            repo.emit(listOf(item1, item2))
            error.eventSink(HomeScreen.Event.RefreshClicked)

            val success = awaitItem()
            assertFalse(success.isLoading)
            assertNull(success.error)
            assertEquals(2, success.items.size)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `detail screen handles missing item`() = runTest {
        val repo = FakeItemRepository(items = emptyList())
        val navigator = FakeNavigator(DetailScreen("nonexistent"))

        val presenter = DetailPresenter(
            navigator = navigator,
            repo = repo,
        )

        presenter.test {
            val state = expectMostRecentItem()
            assertNull(state.item)
            assertFalse(state.isLoading)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

---

## Example 5: UI Compose Test

Testing UI rendering and event emission with `TestEventSink`.

```kotlin
class HomeUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `displays loading indicator when loading`() {
        val eventSink = TestEventSink<HomeScreen.Event>()
        val state = HomeScreen.State(
            items = persistentListOf(),
            isLoading = true,
            eventSink = eventSink,
        )

        composeTestRule.setContent {
            HomeUi(state)
        }

        composeTestRule.onNodeWithTag("loading_indicator").assertIsDisplayed()
        composeTestRule.onNodeWithTag("item_list").assertDoesNotExist()
        eventSink.assertNoEvents()
    }

    @Test
    fun `displays items when loaded`() {
        val eventSink = TestEventSink<HomeScreen.Event>()
        val state = HomeScreen.State(
            items = persistentListOf(item1, item2),
            isLoading = false,
            eventSink = eventSink,
        )

        composeTestRule.setContent {
            HomeUi(state)
        }

        composeTestRule.onNodeWithText("First Item").assertIsDisplayed()
        composeTestRule.onNodeWithText("Second Item").assertIsDisplayed()
        composeTestRule.onNodeWithTag("loading_indicator").assertDoesNotExist()
    }

    @Test
    fun `clicking item sends ItemClicked event`() {
        val eventSink = TestEventSink<HomeScreen.Event>()
        val state = HomeScreen.State(
            items = persistentListOf(item1, item2),
            isLoading = false,
            eventSink = eventSink,
        )

        composeTestRule.setContent {
            HomeUi(state)
        }

        composeTestRule.onNodeWithText("First Item").performClick()
        eventSink.assertEvent(HomeScreen.Event.ItemClicked("1"))
    }

    @Test
    fun `clicking refresh sends RefreshClicked event`() {
        val eventSink = TestEventSink<HomeScreen.Event>()
        val state = HomeScreen.State(
            items = persistentListOf(item1),
            isLoading = false,
            eventSink = eventSink,
        )

        composeTestRule.setContent {
            HomeUi(state)
        }

        composeTestRule.onNodeWithContentDescription("Refresh").performClick()
        eventSink.assertEvent(HomeScreen.Event.RefreshClicked)
    }

    @Test
    fun `displays error message when error present`() {
        val eventSink = TestEventSink<HomeScreen.Event>()
        val state = HomeScreen.State(
            items = persistentListOf(),
            isLoading = false,
            error = "Something went wrong",
            eventSink = eventSink,
        )

        composeTestRule.setContent {
            HomeUi(state)
        }

        composeTestRule.onNodeWithText("Something went wrong").assertIsDisplayed()
    }
}
```

---

## Example 6: Retained State Test

Verifying that state is retained across presenter test boundaries using `presenterTestOf`.

```kotlin
class RetainedStateTest {

    @Test
    fun `state survives recomposition with rememberRetained`() = runTest {
        val repo = FakeItemRepository(items = listOf(item1, item2))
        val navigator = FakeNavigator(HomeScreen)

        val presenter = HomePresenter(
            navigator = navigator,
            repo = repo,
        )

        presenter.test {
            // Initial load
            val loaded = expectMostRecentItem()
            assertEquals(2, loaded.items.size)

            // Simulate user action that modifies local state
            loaded.eventSink(HomeScreen.Event.DeleteItem("1"))
            val afterDelete = awaitItem()
            assertEquals(1, afterDelete.items.size)

            // The state is retained -- re-collecting should not reset
            // If presenter uses rememberRetained, the deletion persists
            // across configuration changes
            ensureAllEventsConsumed()
        }
    }

    @Test
    fun `retained counter increments persist`() = runTest {
        presenterTestOf(
            presentFunction = {
                var count by rememberRetained { mutableIntStateOf(0) }
                CounterScreen.State(
                    count = count,
                    eventSink = { event ->
                        when (event) {
                            CounterScreen.Event.Increment -> count++
                            CounterScreen.Event.Decrement -> count--
                        }
                    },
                )
            }
        ) {
            val initial = awaitItem()
            assertEquals(0, initial.count)

            initial.eventSink(CounterScreen.Event.Increment)
            val incremented = awaitItem()
            assertEquals(1, incremented.count)

            incremented.eventSink(CounterScreen.Event.Increment)
            val twice = awaitItem()
            assertEquals(2, twice.count)

            twice.eventSink(CounterScreen.Event.Decrement)
            val decremented = awaitItem()
            assertEquals(1, decremented.count)

            ensureAllEventsConsumed()
        }
    }
}
```

---

## Example 7: Overlay Test

Testing overlay interaction in a presenter using `FakeOverlayHost`.

```kotlin
class OverlayPresenterTest {

    @Test
    fun `delete shows confirmation overlay and proceeds on confirm`() = runTest {
        val repo = FakeItemRepository(items = listOf(item1))
        val navigator = FakeNavigator(DetailScreen("1"))
        val overlayHost = FakeOverlayHost()

        // Pre-configure overlay to return Confirmed
        overlayHost.setResult(ConfirmationOverlay.Result.Confirmed)

        val presenter = DetailPresenter(
            navigator = navigator,
            repo = repo,
            overlayHost = overlayHost,
        )

        presenter.test {
            val state = expectMostRecentItem()
            assertNotNull(state.item)

            // Trigger delete -- presenter will show overlay
            state.eventSink(DetailScreen.Event.DeleteClicked)

            // Verify the overlay was shown
            val overlay = overlayHost.awaitNextOverlay()
            assertIs<ConfirmationOverlay>(overlay)
            assertEquals("Delete this item?", overlay.message)

            // After confirmation, presenter should delete and pop
            val popEvent = navigator.awaitNextPop()
            val result = popEvent.result
            assertIs<DetailScreen.Result.ItemDeleted>(result)
            assertEquals("1", result.itemId)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `delete shows confirmation overlay and cancels on dismiss`() = runTest {
        val repo = FakeItemRepository(items = listOf(item1))
        val navigator = FakeNavigator(DetailScreen("1"))
        val overlayHost = FakeOverlayHost()

        // Pre-configure overlay to return Dismissed
        overlayHost.setResult(ConfirmationOverlay.Result.Dismissed)

        val presenter = DetailPresenter(
            navigator = navigator,
            repo = repo,
            overlayHost = overlayHost,
        )

        presenter.test {
            val state = expectMostRecentItem()

            // Trigger delete
            state.eventSink(DetailScreen.Event.DeleteClicked)

            // Overlay was shown
            val overlay = overlayHost.awaitNextOverlay()
            assertIs<ConfirmationOverlay>(overlay)

            // After dismissal, no navigation should happen
            navigator.expectNoPopEvents()
            navigator.expectNoGoToEvents()

            // State should remain unchanged
            val currentState = expectMostRecentItem()
            assertFalse(currentState.isDeleted)
            assertNotNull(currentState.item)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `edit shows bottom sheet overlay`() = runTest {
        val repo = FakeItemRepository(items = listOf(item1))
        val navigator = FakeNavigator(DetailScreen("1"))
        val overlayHost = FakeOverlayHost()

        overlayHost.setResult(
            EditOverlay.Result.Saved(
                title = "Updated Title",
                description = "Updated Description",
            )
        )

        val presenter = DetailPresenter(
            navigator = navigator,
            repo = repo,
            overlayHost = overlayHost,
        )

        presenter.test {
            val state = expectMostRecentItem()

            state.eventSink(DetailScreen.Event.EditClicked)

            val overlay = overlayHost.awaitNextOverlay()
            assertIs<EditOverlay>(overlay)

            // State should reflect the edit
            val updated = awaitItem()
            assertEquals("Updated Title", updated.item?.title)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

---

## Example 8: Integration Test

Full `Circuit.Builder` test with real Presenter and UI wired together.

```kotlin
class CircuitIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `full circuit integration - home to detail`() {
        val repo = FakeItemRepository(items = listOf(item1, item2))

        val circuit = Circuit.Builder()
            .addPresenterFactory { screen, navigator, _ ->
                when (screen) {
                    is HomeScreen -> HomePresenter(
                        navigator = navigator,
                        repo = repo,
                    )
                    is DetailScreen -> DetailPresenter(
                        navigator = navigator,
                        repo = repo,
                    )
                    else -> null
                }
            }
            .addUiFactory { screen, _ ->
                when (screen) {
                    is HomeScreen -> ui<HomeScreen.State> { state, modifier ->
                        HomeUi(state, modifier)
                    }
                    is DetailScreen -> ui<DetailScreen.State> { state, modifier ->
                        DetailUi(state, modifier)
                    }
                    else -> null
                }
            }
            .build()

        composeTestRule.setContent {
            CircuitCompositionLocals(circuit) {
                NavigableCircuitContent(
                    navigator = rememberCircuitNavigator(
                        rememberSaveableBackStack(HomeScreen),
                    ),
                    backStack = rememberSaveableBackStack(HomeScreen),
                )
            }
        }

        // Verify home screen loads with items
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule
                .onAllNodesWithText("First Item")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeTestRule.onNodeWithText("First Item").assertIsDisplayed()
        composeTestRule.onNodeWithText("Second Item").assertIsDisplayed()

        // Navigate to detail
        composeTestRule.onNodeWithText("First Item").performClick()

        // Verify detail screen
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule
                .onAllNodesWithText("Description 1")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeTestRule.onNodeWithText("First Item").assertIsDisplayed()
        composeTestRule.onNodeWithText("Description 1").assertIsDisplayed()
    }

    @Test
    fun `event listener records lifecycle events`() {
        val repo = FakeItemRepository(items = listOf(item1))
        val eventListener = RecordingEventListener()

        val circuit = Circuit.Builder()
            .addPresenterFactory { screen, navigator, _ ->
                when (screen) {
                    is HomeScreen -> HomePresenter(navigator = navigator, repo = repo)
                    else -> null
                }
            }
            .addUiFactory { screen, _ ->
                when (screen) {
                    is HomeScreen -> ui<HomeScreen.State> { state, modifier ->
                        HomeUi(state, modifier)
                    }
                    else -> null
                }
            }
            .eventListener(eventListener)
            .build()

        composeTestRule.setContent {
            CircuitCompositionLocals(circuit) {
                NavigableCircuitContent(
                    navigator = rememberCircuitNavigator(
                        rememberSaveableBackStack(HomeScreen),
                    ),
                    backStack = rememberSaveableBackStack(HomeScreen),
                )
            }
        }

        composeTestRule.waitForIdle()

        // Verify lifecycle events were recorded
        val events = eventListener.events
        assertTrue(events.isNotEmpty())

        // Presenter should be created before UI
        val presenterCreated = events.filterIsInstance<RecordingEventListener.Event.PresenterCreated>()
        val uiCreated = events.filterIsInstance<RecordingEventListener.Event.UiCreated>()

        assertTrue(presenterCreated.isNotEmpty())
        assertTrue(uiCreated.isNotEmpty())
        assertEquals(HomeScreen, presenterCreated.first().screen)
        assertEquals(HomeScreen, uiCreated.first().screen)
    }

    @Test
    fun `back navigation returns to previous screen`() {
        val repo = FakeItemRepository(items = listOf(item1))

        val circuit = Circuit.Builder()
            .addPresenterFactory { screen, navigator, _ ->
                when (screen) {
                    is HomeScreen -> HomePresenter(navigator = navigator, repo = repo)
                    is DetailScreen -> DetailPresenter(navigator = navigator, repo = repo)
                    else -> null
                }
            }
            .addUiFactory { screen, _ ->
                when (screen) {
                    is HomeScreen -> ui<HomeScreen.State> { state, modifier ->
                        HomeUi(state, modifier)
                    }
                    is DetailScreen -> ui<DetailScreen.State> { state, modifier ->
                        DetailUi(state, modifier)
                    }
                    else -> null
                }
            }
            .build()

        composeTestRule.setContent {
            CircuitCompositionLocals(circuit) {
                val backStack = rememberSaveableBackStack(HomeScreen)
                val navigator = rememberCircuitNavigator(backStack)
                NavigableCircuitContent(
                    navigator = navigator,
                    backStack = backStack,
                )
            }
        }

        // Navigate to detail
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule
                .onAllNodesWithText("First Item")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithText("First Item").performClick()

        // Verify on detail screen
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule
                .onAllNodesWithText("Description 1")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Go back
        composeTestRule.onNodeWithContentDescription("Back").performClick()

        // Verify back on home screen
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule
                .onAllNodesWithText("First Item")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithText("First Item").assertIsDisplayed()
    }
}
```
