# Coroutines Test Examples

Production-ready test examples using kotlinx-coroutines-test 1.10.2, Turbine 1.2.1, and Molecule 2.2.0.

---

## 1. Basic runTest -- Testing a Suspend Function

Testing a repository that internally uses `delay` and `withContext`.

```kotlin
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals

class UserRepositoryTest {

    private val fakeApi = FakeUserApi()
    private val repository = UserRepository(api = fakeApi)

    @Test
    fun fetchUserReturnsUserFromApi() = runTest {
        // Arrange
        fakeApi.respondWith(User(id = "1", name = "Alice"))

        // Act -- suspend fun with internal delay(500) + withContext(IO)
        val user = repository.fetchUser("1")

        // Assert -- runTest auto-advances virtual time through delays
        assertEquals("Alice", user.name)
        assertEquals(500, currentTime) // virtual time advanced by 500ms
    }

    @Test
    fun fetchUserThrowsOnNetworkError() = runTest {
        fakeApi.respondWithError(NetworkException("timeout"))

        val result = runCatching { repository.fetchUser("1") }

        assertTrue(result.isFailure)
        assertIs<NetworkException>(result.exceptionOrNull())
    }
}
```

---

## 2. Flow Testing with Turbine -- Repository Observation

Testing a repository that exposes a `Flow` of items.

```kotlin
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ItemRepositoryTest {

    private val fakeDao = FakeItemDao()
    private val repository = ItemRepository(dao = fakeDao)

    @Test
    fun observeItemsEmitsInitialThenUpdates() = runTest {
        fakeDao.setItems(listOf(Item("a"), Item("b")))

        repository.observeItems().test {
            // First emission: initial data
            val initial = awaitItem()
            assertEquals(2, initial.size)
            assertEquals("a", initial[0].id)

            // Simulate an insert
            fakeDao.insertItem(Item("c"))

            // Second emission: updated data
            val updated = awaitItem()
            assertEquals(3, updated.size)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeItemsEmitsErrorOnDatabaseFailure() = runTest {
        fakeDao.throwOnNextQuery(DatabaseException("corrupt"))

        repository.observeItems().test {
            val error = awaitError()
            assertIs<DatabaseException>(error)
        }
    }

    @Test
    fun observeItemsSkipsInitialAndAssertsMostRecent() = runTest {
        fakeDao.setItems(listOf(Item("a")))

        repository.observeItems().test {
            skipItems(1) // skip initial emission

            fakeDao.insertItem(Item("b"))
            val updated = awaitItem()
            assertEquals(2, updated.size)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

---

## 3. Debounce Testing -- Search with Virtual Time

Testing a search use case that debounces input by 300ms.

```kotlin
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SearchUseCaseTest {

    private val fakeSearchApi = FakeSearchApi()
    private val queryFlow = MutableStateFlow("")
    private val useCase = SearchUseCase(
        queryFlow = queryFlow,
        api = fakeSearchApi,
        debounceMs = 300L,
    )

    @Test
    fun debouncesRapidInput() = runTest {
        useCase.results.test {
            // Initial empty state
            assertEquals(emptyList(), awaitItem())

            // Type rapidly -- should not trigger search yet
            queryFlow.value = "k"
            advanceTimeBy(100)
            expectNoEvents() // debounce window not elapsed

            queryFlow.value = "ko"
            advanceTimeBy(100)
            expectNoEvents()

            queryFlow.value = "kot"
            advanceTimeBy(100)
            expectNoEvents() // still within 300ms of last change

            // Wait for debounce to complete (300ms from last input)
            advanceTimeBy(200) // total 300ms since "kot"

            val results = awaitItem()
            assertEquals(fakeSearchApi.resultsFor("kot"), results)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun emptyQueryClearsResults() = runTest {
        fakeSearchApi.setResults("hello", listOf(SearchResult("Hello World")))

        useCase.results.test {
            assertEquals(emptyList(), awaitItem()) // initial

            queryFlow.value = "hello"
            advanceTimeBy(300)
            assertEquals(1, awaitItem().size)

            queryFlow.value = ""
            advanceTimeBy(300)
            assertEquals(emptyList(), awaitItem()) // cleared

            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

---

## 4. Circuit Presenter Testing with Molecule

Testing a Circuit `Presenter` using `moleculeFlow(Immediate)` + Turbine.

```kotlin
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UserProfilePresenterTest {

    private val fakeUserRepo = FakeUserRepository()
    private val fakeAnalytics = FakeAnalytics()

    private fun createPresenter() = UserProfilePresenter(
        userId = "user-123",
        userRepository = fakeUserRepo,
        analytics = fakeAnalytics,
    )

    @Test
    fun presenterEmitsLoadingThenSuccess() = runTest {
        fakeUserRepo.setUser(User(id = "user-123", name = "Alice", email = "alice@test.com"))

        moleculeFlow(RecompositionMode.Immediate) {
            createPresenter().present()
        }.test {
            // First emission: loading state
            val loading = awaitItem()
            assertIs<UserProfileState.Loading>(loading)

            // Second emission: data loaded
            val success = awaitItem()
            assertIs<UserProfileState.Success>(success)
            assertEquals("Alice", success.user.name)
            assertEquals("alice@test.com", success.user.email)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun presenterEmitsErrorOnFailure() = runTest {
        fakeUserRepo.setError(UserNotFoundException("user-123"))

        moleculeFlow(RecompositionMode.Immediate) {
            createPresenter().present()
        }.test {
            // Loading
            assertIs<UserProfileState.Loading>(awaitItem())

            // Error
            val error = awaitItem()
            assertIs<UserProfileState.Error>(error)
            assertEquals("User not found", error.message)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun presenterHandlesRetryEvent() = runTest {
        fakeUserRepo.setError(NetworkException("offline"))

        moleculeFlow(RecompositionMode.Immediate) {
            createPresenter().present()
        }.test {
            assertIs<UserProfileState.Loading>(awaitItem())

            val error = awaitItem()
            assertIs<UserProfileState.Error>(error)

            // Fix the error and trigger retry via event sink
            fakeUserRepo.setUser(User(id = "user-123", name = "Alice", email = "a@t.com"))
            error.eventSink(UserProfileEvent.Retry)

            // Should go back to loading, then success
            assertIs<UserProfileState.Loading>(awaitItem())
            val success = awaitItem()
            assertIs<UserProfileState.Success>(success)
            assertEquals("Alice", success.user.name)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun presenterTracksScreenViewAnalytics() = runTest {
        fakeUserRepo.setUser(User(id = "user-123", name = "Alice", email = "a@t.com"))

        moleculeFlow(RecompositionMode.Immediate) {
            createPresenter().present()
        }.test {
            awaitItem() // loading
            awaitItem() // success

            assertTrue(fakeAnalytics.trackedEvents.any { it.name == "screen_view" })

            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

---

## 5. StateFlow with backgroundScope

Testing code that uses `stateIn` or `shareIn` which require `backgroundScope`.

```kotlin
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SharedFlowTest {

    private val fakeDao = FakeSettingsDao()
    private val repository = SettingsRepository(dao = fakeDao)

    @Test
    fun stateFlowEmitsLatestSettings() = runTest {
        fakeDao.setSettings(Settings(theme = "dark", locale = "en"))

        // stateIn needs backgroundScope, NOT this (TestScope)
        // Using `this` would cause advanceUntilIdle() to hang forever
        val settingsState = repository.observeSettings()
            .stateIn(backgroundScope, SharingStarted.Eagerly, Settings.DEFAULT)

        advanceUntilIdle() // let backgroundScope coroutine run

        assertEquals("dark", settingsState.value.theme)
        assertEquals("en", settingsState.value.locale)
    }

    @Test
    fun stateFlowUpdatesWhenSettingsChange() = runTest {
        fakeDao.setSettings(Settings(theme = "light", locale = "en"))

        val settingsState = repository.observeSettings()
            .stateIn(backgroundScope, SharingStarted.Eagerly, Settings.DEFAULT)

        advanceUntilIdle()
        assertEquals("light", settingsState.value.theme)

        // Update settings
        fakeDao.updateSettings(Settings(theme = "dark", locale = "en"))
        advanceUntilIdle()

        assertEquals("dark", settingsState.value.theme)
    }

    @Test
    fun collectStateFlowWithUnconfinedDispatcher() = runTest {
        val stateFlow = MutableStateFlow(0)
        val collected = mutableListOf<Int>()

        // UnconfinedTestDispatcher enters the collector eagerly
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            stateFlow.collect { collected.add(it) }
        }

        stateFlow.value = 1
        stateFlow.value = 2
        stateFlow.value = 3
        job.cancel()

        // Each emission processed immediately (no conflation)
        assertEquals(listOf(0, 1, 2, 3), collected)
    }
}
```

---

## 6. Timeout and Retry Testing with Virtual Time

Testing `withTimeout` and exponential retry logic.

```kotlin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RetryUseCaseTest {

    private val fakeApi = FakeApi()

    @Test
    fun retriesWithExponentialBackoff() = runTest {
        // Fail twice, succeed on third attempt
        fakeApi.failTimes(2)
        fakeApi.thenRespondWith("success")

        val useCase = RetryUseCase(
            api = fakeApi,
            initialDelayMs = 1000L,
            maxRetries = 3,
        )

        val result = useCase.execute()

        assertEquals("success", result)
        assertEquals(3, fakeApi.callCount) // 1 initial + 2 retries
        // Exponential backoff: 1000ms + 2000ms = 3000ms total delay
        assertEquals(3000, currentTime)
    }

    @Test
    fun failsAfterMaxRetries() = runTest {
        fakeApi.alwaysFail()

        val useCase = RetryUseCase(
            api = fakeApi,
            initialDelayMs = 500L,
            maxRetries = 3,
        )

        val error = assertFailsWith<MaxRetriesExceededException> {
            useCase.execute()
        }

        assertEquals(4, fakeApi.callCount) // 1 initial + 3 retries
        assertEquals(3, error.attempts)
    }

    @Test
    fun withTimeoutCancelsLongOperation() = runTest {
        fakeApi.respondAfterDelay(10_000) // 10 seconds

        val useCase = TimeoutUseCase(
            api = fakeApi,
            timeoutMs = 5_000L,
        )

        assertFailsWith<TimeoutException> {
            useCase.execute()
        }

        assertEquals(5000, currentTime) // cancelled at 5s, not 10s
    }

    @Test
    fun virtualTimeMeasurement() = runTest {
        val duration = testTimeSource.measureTime {
            fakeApi.respondAfterDelay(2_000)
            val result = fakeApi.call()
            assertEquals("ok", result)
        }

        assertEquals(2.seconds, duration)
    }
}
```

---

## 7. Parallel Async Testing -- Concurrent Operations

Testing multiple concurrent coroutines and `supervisorScope`.

```kotlin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ParallelFetchTest {

    private val fakeUserApi = FakeUserApi()
    private val fakeOrderApi = FakeOrderApi()
    private val useCase = DashboardUseCase(
        userApi = fakeUserApi,
        orderApi = fakeOrderApi,
    )

    @Test
    fun fetchesUserAndOrdersInParallel() = runTest {
        // Each API call takes 1000ms
        fakeUserApi.respondAfterDelay(1000, User("Alice"))
        fakeOrderApi.respondAfterDelay(1000, listOf(Order("O-1")))

        val dashboard = useCase.loadDashboard("user-1")

        // Both ran in parallel via async, so total time = max(1000, 1000) = 1000ms
        assertEquals(1000, currentTime)
        assertEquals("Alice", dashboard.user.name)
        assertEquals(1, dashboard.orders.size)
    }

    @Test
    fun supervisorScopeIsolatesFailures() = runTest {
        fakeUserApi.respondAfterDelay(1000, User("Alice"))
        fakeOrderApi.respondWithError(ApiException("order service down"))

        // supervisorScope: order failure does not cancel user fetch
        val result = useCase.loadDashboardGracefully("user-1")

        assertEquals("Alice", result.user.name)
        assertTrue(result.orders.isEmpty()) // graceful fallback
        assertEquals("order service down", result.orderError)
    }

    @Test
    fun multipleDispatchersShareScheduler() = runTest {
        val ioDispatcher = StandardTestDispatcher(testScheduler, name = "IO")
        val computeDispatcher = StandardTestDispatcher(testScheduler, name = "Compute")

        var ioResult = ""
        var computeResult = ""

        launch(ioDispatcher) {
            delay(1000)
            ioResult = "fetched"
        }
        launch(computeDispatcher) {
            delay(500)
            computeResult = "computed"
        }

        advanceTimeBy(500)
        assertEquals("", ioResult)         // not yet (needs 1000ms)
        assertEquals("computed", computeResult) // done at 500ms

        advanceTimeBy(500)
        assertEquals("fetched", ioResult)  // done at 1000ms
    }
}
```

---

## 8. Fake DispatcherProvider with Metro DI

Testing with a Metro DI test module that provides test dispatchers.

```kotlin
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

// --- Production code ---

interface DispatcherProvider {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
}

// --- Test infrastructure ---

@OptIn(ExperimentalCoroutinesApi::class)
class TestDispatcherProvider(
    scheduler: TestCoroutineScheduler,
) : DispatcherProvider {
    private val testDispatcher = StandardTestDispatcher(scheduler)
    override val main: CoroutineDispatcher = testDispatcher
    override val io: CoroutineDispatcher = testDispatcher
    override val default: CoroutineDispatcher = testDispatcher
}

// --- Usage in tests ---

@OptIn(ExperimentalCoroutinesApi::class)
class UserViewModelTest {

    @Test
    fun loadUserUpdatesState() = runTest {
        val dispatchers = TestDispatcherProvider(testScheduler)
        val fakeRepo = FakeUserRepository()
        fakeRepo.setUser(User("Alice"))

        val viewModel = UserViewModel(
            repository = fakeRepo,
            dispatchers = dispatchers,
        )

        viewModel.loadUser("1")
        advanceUntilIdle() // execute all work queued on test dispatchers

        assertEquals("Alice", viewModel.state.value.userName)
    }
}
```

---

## 9. testIn for Multiple Flows

Using `testIn` when you need to test multiple flows simultaneously.

```kotlin
import app.cash.turbine.testIn
import app.cash.turbine.turbineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationServiceTest {

    private val fakeNotificationSource = FakeNotificationSource()
    private val service = NotificationService(source = fakeNotificationSource)

    @Test
    fun unreadCountAndNotificationsStayInSync() = runTest {
        turbineScope {
            val notifications = service.notifications.testIn(backgroundScope)
            val unreadCount = service.unreadCount.testIn(backgroundScope)

            // Initial state
            assertEquals(emptyList(), notifications.awaitItem())
            assertEquals(0, unreadCount.awaitItem())

            // New notification arrives
            fakeNotificationSource.emit(Notification("n1", "Hello", read = false))

            assertEquals(1, notifications.awaitItem().size)
            assertEquals(1, unreadCount.awaitItem())

            // Mark as read
            service.markAsRead("n1")

            // Notifications list same size, but unread count drops
            val updated = notifications.awaitItem()
            assertEquals(1, updated.size)
            assertTrue(updated[0].read)
            assertEquals(0, unreadCount.awaitItem())

            notifications.cancelAndIgnoreRemainingEvents()
            unreadCount.cancelAndIgnoreRemainingEvents()
        }
    }
}
```

---

## 10. Standalone Turbine for Callback-Based APIs

Using standalone `Turbine<T>()` to test callback-based or listener-based APIs.

```kotlin
import app.cash.turbine.Turbine
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LocationTrackerTest {

    @Test
    fun trackerEmitsLocationUpdates() = runTest {
        val locationTurbine = Turbine<Location>()

        val tracker = LocationTracker(
            onLocationUpdate = { location -> locationTurbine.add(location) }
        )

        tracker.startTracking()

        // Simulate location updates from platform
        tracker.simulateLocation(Location(lat = 37.7749, lng = -122.4194))
        tracker.simulateLocation(Location(lat = 37.7750, lng = -122.4195))

        assertEquals(37.7749, locationTurbine.awaitItem().lat)
        assertEquals(37.7750, locationTurbine.awaitItem().lat)

        tracker.stopTracking()
        locationTurbine.close()
        locationTurbine.awaitComplete()
    }

    @Test
    fun trackerReportsErrors() = runTest {
        val errorTurbine = Turbine<Throwable>()

        val tracker = LocationTracker(
            onError = { error -> errorTurbine.add(error) }
        )

        tracker.startTracking()
        tracker.simulateError(PermissionDeniedException("location"))

        val error = errorTurbine.awaitItem()
        assertIs<PermissionDeniedException>(error)

        errorTurbine.cancelAndIgnoreRemainingEvents()
    }
}
```

---

## 11. Testing Dispatchers.Main Override

Full setup for ViewModels that use `Dispatchers.Main`.

```kotlin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class CounterViewModelTest {

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
    fun incrementUpdatesCount() = runTest(testDispatcher) {
        val viewModel = CounterViewModel()

        assertEquals(0, viewModel.count.value)

        viewModel.increment() // launches on Dispatchers.Main internally
        advanceUntilIdle()

        assertEquals(1, viewModel.count.value)
    }

    @Test
    fun multipleIncrementsAccumulate() = runTest(testDispatcher) {
        val viewModel = CounterViewModel()

        repeat(5) { viewModel.increment() }
        advanceUntilIdle()

        assertEquals(5, viewModel.count.value)
    }
}
```

---

## 12. Precise Time Control with runCurrent

Using `runCurrent()` for step-by-step execution control.

```kotlin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class PreciseTimingTest {

    @Test
    fun stepByStepExecution() = runTest {
        val events = mutableListOf<String>()

        launch {
            events.add("start")        // runs at time 0
            delay(1_000)
            events.add("after-1s")     // runs at time 1000
            delay(500)
            events.add("after-1.5s")   // runs at time 1500
            delay(5_000)
            events.add("after-6.5s")   // runs at time 6500
        }

        // Nothing executed yet (StandardTestDispatcher queues)
        assertEquals(emptyList(), events)

        // Execute tasks at current time (t=0)
        runCurrent()
        assertEquals(listOf("start"), events)
        assertEquals(0, currentTime)

        // Advance by 1 second
        advanceTimeBy(1_000)
        assertEquals(listOf("start", "after-1s"), events)
        assertEquals(1000, currentTime)

        // Advance by another 500ms
        advanceTimeBy(500)
        assertEquals(listOf("start", "after-1s", "after-1.5s"), events)
        assertEquals(1500, currentTime)

        // Advance to idle (runs remaining)
        advanceUntilIdle()
        assertEquals(4, events.size)
        assertEquals(6500, currentTime)
    }

    @Test
    fun measureVirtualDuration() = runTest {
        val duration = testTimeSource.measureTime {
            delay(2_500)
        }

        assertEquals(2_500, duration.inWholeMilliseconds)
        assertEquals(2500, currentTime)
    }
}
```

---

## 13. Circuit Presenter with Navigator Events

Testing a Circuit Presenter that handles navigation via `Navigator`.

```kotlin
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.slack.circuit.test.FakeNavigator
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ItemListPresenterTest {

    private val fakeRepository = FakeItemRepository()
    private val navigator = FakeNavigator()

    private fun createPresenter() = ItemListPresenter(
        repository = fakeRepository,
        navigator = navigator,
    )

    @Test
    fun tappingItemNavigatesToDetail() = runTest {
        fakeRepository.setItems(listOf(Item("item-1", "Widget")))

        moleculeFlow(RecompositionMode.Immediate) {
            createPresenter().present()
        }.test {
            // Skip loading
            assertIs<ItemListState.Loading>(awaitItem())

            // Get loaded state with event sink
            val loaded = awaitItem()
            assertIs<ItemListState.Loaded>(loaded)
            assertEquals(1, loaded.items.size)

            // Simulate tap on item
            loaded.eventSink(ItemListEvent.ItemClicked("item-1"))

            // Verify navigation happened
            val screen = navigator.awaitNextScreen()
            assertIs<ItemDetailScreen>(screen)
            assertEquals("item-1", screen.itemId)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun pullToRefreshReloadsData() = runTest {
        fakeRepository.setItems(listOf(Item("1", "Old")))

        moleculeFlow(RecompositionMode.Immediate) {
            createPresenter().present()
        }.test {
            assertIs<ItemListState.Loading>(awaitItem())
            val initial = awaitItem()
            assertIs<ItemListState.Loaded>(initial)
            assertEquals("Old", initial.items[0].name)

            // Update underlying data and trigger refresh
            fakeRepository.setItems(listOf(Item("1", "New")))
            initial.eventSink(ItemListEvent.Refresh)

            // Should see refreshing state, then updated data
            val refreshing = awaitItem()
            assertIs<ItemListState.Loaded>(refreshing)
            assertTrue(refreshing.isRefreshing)

            val refreshed = awaitItem()
            assertIs<ItemListState.Loaded>(refreshed)
            assertEquals("New", refreshed.items[0].name)
            assertFalse(refreshed.isRefreshing)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

---

## 14. Testing Periodic Polling with Virtual Time

Testing a use case that polls an API at regular intervals.

```kotlin
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class PollingUseCaseTest {

    private val fakeApi = FakeStatusApi()

    @Test
    fun pollsAtRegularIntervals() = runTest {
        var callCount = 0
        fakeApi.onCall = { callCount++; Status("ok-$callCount") }

        val useCase = PollingUseCase(
            api = fakeApi,
            intervalMs = 5_000L,
        )

        useCase.statusFlow.test {
            // First poll is immediate
            assertEquals("ok-1", awaitItem().message)

            // No new data before interval
            advanceTimeBy(4_999)
            expectNoEvents()

            // Second poll at 5000ms
            advanceTimeBy(1)
            assertEquals("ok-2", awaitItem().message)

            // Third poll at 10000ms
            advanceTimeBy(5_000)
            assertEquals("ok-3", awaitItem().message)

            assertEquals(3, callCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun stopsPollingOnCancellation() = runTest {
        var callCount = 0
        fakeApi.onCall = { callCount++; Status("ok") }

        val useCase = PollingUseCase(api = fakeApi, intervalMs = 1_000L)

        useCase.statusFlow.test {
            awaitItem() // first poll
            advanceTimeBy(1_000)
            awaitItem() // second poll
            cancel()    // stop collecting
        }

        // Advance time -- no more calls should happen
        advanceTimeBy(10_000)
        assertEquals(2, callCount)
    }
}
```

---

## 15. expectMostRecentItem for Rapid Emissions

Using `expectMostRecentItem()` when intermediate states are not important.

```kotlin
import app.cash.turbine.test
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RapidEmissionTest {

    @Test
    fun assertOnlyMostRecentValue() = runTest {
        val rapidFlow = flow {
            emit(1)
            emit(2)
            emit(3)
            delay(100) // small delay to let turbine buffer
            emit(4)
            emit(5)
        }

        rapidFlow.test {
            // Skip to the most recently buffered value
            delay(50) // let some emissions buffer
            val recent = expectMostRecentItem()
            // recent will be 3 (latest before the 100ms delay)

            // After the delay, more items arrive
            advanceTimeBy(100)
            val last = expectMostRecentItem()
            assertEquals(5, last)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
```
