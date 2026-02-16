# Coroutines Core Examples (kotlinx-coroutines 1.10.2)

All examples are KMP-compatible (`commonMain`) and use Metro DI for injection.

---

## 1. DispatcherProvider -- Injectable Dispatchers

```kotlin
// commonMain - Interface
interface DispatcherProvider {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
    val mainImmediate: CoroutineDispatcher
}

// commonMain - Production implementation
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultDispatcherProvider : DispatcherProvider {
    override val main: CoroutineDispatcher get() = Dispatchers.Main
    override val io: CoroutineDispatcher get() = Dispatchers.IO
    override val default: CoroutineDispatcher get() = Dispatchers.Default
    override val mainImmediate: CoroutineDispatcher get() = Dispatchers.Main.immediate
}
```

**Test replacement:**

```kotlin
// commonTest
class TestDispatcherProvider(
    testDispatcher: TestDispatcher = StandardTestDispatcher()
) : DispatcherProvider {
    override val main: CoroutineDispatcher = testDispatcher
    override val io: CoroutineDispatcher = testDispatcher
    override val default: CoroutineDispatcher = testDispatcher
    override val mainImmediate: CoroutineDispatcher = testDispatcher
}
```

---

## 2. Application-Scoped CoroutineScope via Metro

```kotlin
// commonMain - Provide a long-lived scope for the app
@ContributesTo(AppScope::class)
interface AppCoroutineScopeComponent {

    @Provides
    @SingleIn(AppScope::class)
    fun provideAppCoroutineScope(
        dispatchers: DispatcherProvider,
    ): CoroutineScope = CoroutineScope(
        SupervisorJob() + dispatchers.default + CoroutineName("AppScope")
    )
}
```

**Usage in a service:**

```kotlin
@Inject
@SingleIn(AppScope::class)
class SyncManager(
    private val appScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val repository: DataRepository,
) {
    fun startPeriodicSync(intervalMs: Long = 30_000L) {
        appScope.launch(dispatchers.io) {
            while (isActive) {
                repository.syncFromRemote()
                delay(intervalMs)
            }
        }
    }
}
```

---

## 3. Repository with Flow -- SQLDelight Reactive Queries

```kotlin
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class OfflineFirstUserRepository(
    private val userQueries: UserQueries,
    private val api: UserApi,
    private val dispatchers: DispatcherProvider,
) : UserRepository {

    // Reactive local data via SQLDelight
    override fun observeUsers(): Flow<List<User>> =
        userQueries.selectAll()
            .asFlow()
            .mapToList(dispatchers.io)
            .map { entities -> entities.map { it.toDomainModel() } }
            .distinctUntilChanged()

    // Fetch and cache
    override suspend fun refreshUsers(): Result<Unit> = withContext(dispatchers.io) {
        runCatching {
            val remoteUsers = api.fetchUsers()
            userQueries.transaction {
                userQueries.deleteAll()
                remoteUsers.forEach { user ->
                    userQueries.insert(user.toEntity())
                }
            }
        }
    }

    // Observe single user with remote fallback
    override fun observeUser(id: UserId): Flow<User?> =
        userQueries.selectById(id.value)
            .asFlow()
            .mapToOneOrNull(dispatchers.io)
            .map { it?.toDomainModel() }
            .onStart {
                // Trigger refresh in background, do not block emission
                withContext(dispatchers.io) {
                    runCatching { refreshUserFromRemote(id) }
                }
            }

    private suspend fun refreshUserFromRemote(id: UserId) {
        val remote = api.fetchUser(id.value)
        userQueries.upsert(remote.toEntity())
    }
}
```

---

## 4. Debounced Search with flatMapLatest

```kotlin
@Inject
class SearchPresenter(
    private val searchRepository: SearchRepository,
    private val dispatchers: DispatcherProvider,
) {
    private val queryFlow = MutableStateFlow("")
    private val _state = MutableStateFlow<SearchState>(SearchState.Idle)
    val state: StateFlow<SearchState> = _state.asStateFlow()

    fun init(scope: CoroutineScope) {
        queryFlow
            .debounce(300L)
            .distinctUntilChanged()
            .flatMapLatest { query ->
                if (query.isBlank()) {
                    flowOf(SearchState.Idle)
                } else {
                    searchRepository.search(query)
                        .map<List<SearchResult>, SearchState> { results ->
                            SearchState.Results(results)
                        }
                        .onStart { emit(SearchState.Loading) }
                        .catch { e -> emit(SearchState.Error(e.message ?: "Search failed")) }
                }
            }
            .flowOn(dispatchers.io)
            .onEach { _state.value = it }
            .launchIn(scope)
    }

    fun onQueryChanged(query: String) {
        queryFlow.value = query
    }
}

sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Results(val items: List<SearchResult>) : SearchState
    data class Error(val message: String) : SearchState
}
```

**In a Circuit Presenter:**

```kotlin
class SearchScreen {
    data class State(
        val query: String = "",
        val searchState: SearchState = SearchState.Idle,
        val eventSink: (Event) -> Unit = {},
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data class QueryChanged(val query: String) : Event
    }
}

class SearchPresenter
@AssistedInject
constructor(
    @Assisted private val navigator: Navigator,
    private val searchRepository: SearchRepository,
    private val dispatchers: DispatcherProvider,
) : Presenter<SearchScreen.State> {

    @CircuitInject(SearchScreen::class, AppScope::class)
    @AssistedFactory
    fun interface Factory {
        fun create(navigator: Navigator): SearchPresenter
    }

    @Composable
    override fun present(): SearchScreen.State {
        val scope = rememberCoroutineScope()
        var query by remember { mutableStateOf("") }
        var searchState by remember { mutableStateOf<SearchState>(SearchState.Idle) }

        LaunchedEffect(Unit) {
            snapshotFlow { query }
                .debounce(300L)
                .distinctUntilChanged()
                .flatMapLatest { q ->
                    if (q.isBlank()) {
                        flowOf(SearchState.Idle)
                    } else {
                        searchRepository.search(q)
                            .map<List<SearchResult>, SearchState> { SearchState.Results(it) }
                            .onStart { emit(SearchState.Loading) }
                            .catch { emit(SearchState.Error(it.message ?: "Failed")) }
                    }
                }
                .flowOn(dispatchers.io)
                .collect { searchState = it }
        }

        return SearchScreen.State(
            query = query,
            searchState = searchState,
        ) { event ->
            when (event) {
                is SearchScreen.Event.QueryChanged -> query = event.query
            }
        }
    }
}
```

---

## 5. Combining Multiple Flows

```kotlin
@Inject
class DashboardStateProducer(
    private val userRepo: UserRepository,
    private val settingsRepo: SettingsRepository,
    private val networkMonitor: NetworkMonitor,
) {
    fun observeDashboard(): Flow<DashboardState> = combine(
        userRepo.observeCurrentUser(),
        settingsRepo.observeSettings(),
        networkMonitor.isOnline,
    ) { user, settings, isOnline ->
        DashboardState(
            userName = user?.displayName ?: "Guest",
            theme = settings.theme,
            isOffline = !isOnline,
            showPremiumFeatures = user?.isPremium == true,
        )
    }.distinctUntilChanged()
}

data class DashboardState(
    val userName: String,
    val theme: Theme,
    val isOffline: Boolean,
    val showPremiumFeatures: Boolean,
)
```

**Merging heterogeneous event streams:**

```kotlin
@Inject
class NotificationAggregator(
    private val chatNotifications: ChatNotificationSource,
    private val systemAlerts: SystemAlertSource,
    private val reminderService: ReminderService,
) {
    fun observeAllNotifications(): Flow<Notification> = merge(
        chatNotifications.messages.map { Notification.Chat(it) },
        systemAlerts.alerts.map { Notification.System(it) },
        reminderService.reminders.map { Notification.Reminder(it) },
    )
}
```

---

## 6. SupervisorScope for Parallel Fetches with Independent Failure

```kotlin
@Inject
class HomeDataLoader(
    private val userRepo: UserRepository,
    private val feedRepo: FeedRepository,
    private val promoRepo: PromotionRepository,
    private val dispatchers: DispatcherProvider,
) {
    suspend fun loadHomeScreen(): HomeData = withContext(dispatchers.io) {
        supervisorScope {
            val userDeferred = async { userRepo.getCurrentUser() }
            val feedDeferred = async { feedRepo.getLatestFeed() }
            val promoDeferred = async { promoRepo.getActivePromotions() }

            // Each can fail independently
            val user = runCatching { userDeferred.await() }.getOrNull()
            val feed = runCatching { feedDeferred.await() }.getOrDefault(emptyList())
            val promos = runCatching { promoDeferred.await() }.getOrDefault(emptyList())

            HomeData(
                user = user,
                feedItems = feed,
                promotions = promos,
                hasErrors = user == null, // partial failure is OK
            )
        }
    }
}

data class HomeData(
    val user: User?,
    val feedItems: List<FeedItem>,
    val promotions: List<Promotion>,
    val hasErrors: Boolean,
)
```

---

## 7. StateFlow in Circuit Presenter with stateIn

```kotlin
class ProfileScreen {
    data class State(
        val profile: ProfileData?,
        val isLoading: Boolean,
        val error: String?,
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data object Refresh : Event
        data object Retry : Event
    }
}

class ProfilePresenter
@AssistedInject
constructor(
    @Assisted private val navigator: Navigator,
    private val profileRepo: ProfileRepository,
    private val dispatchers: DispatcherProvider,
) : Presenter<ProfileScreen.State> {

    @CircuitInject(ProfileScreen::class, AppScope::class)
    @AssistedFactory
    fun interface Factory {
        fun create(navigator: Navigator): ProfilePresenter
    }

    @Composable
    override fun present(): ProfileScreen.State {
        var refreshTrigger by remember { mutableIntStateOf(0) }

        val profileState by produceState<AsyncResult<ProfileData>>(
            initialValue = AsyncResult.Loading,
            key1 = refreshTrigger,
        ) {
            value = AsyncResult.Loading
            profileRepo.observeProfile()
                .flowOn(dispatchers.io)
                .catch { e -> value = AsyncResult.Error(e) }
                .collect { profile -> value = AsyncResult.Success(profile) }
        }

        return ProfileScreen.State(
            profile = (profileState as? AsyncResult.Success)?.data,
            isLoading = profileState is AsyncResult.Loading,
            error = (profileState as? AsyncResult.Error)?.error?.message,
        ) { event ->
            when (event) {
                ProfileScreen.Event.Refresh,
                ProfileScreen.Event.Retry -> refreshTrigger++
            }
        }
    }
}

sealed interface AsyncResult<out T> {
    data object Loading : AsyncResult<Nothing>
    data class Success<T>(val data: T) : AsyncResult<T>
    data class Error(val error: Throwable) : AsyncResult<Nothing>
}
```

**ViewModel approach (alternative to Circuit):**

```kotlin
@Inject
class ProfileViewModel(
    private val profileRepo: ProfileRepository,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val refreshTrigger = MutableSharedFlow<Unit>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    ).apply { tryEmit(Unit) }

    val state: StateFlow<ProfileUiState> = refreshTrigger
        .flatMapLatest {
            profileRepo.observeProfile()
                .map<ProfileData, ProfileUiState> { ProfileUiState.Success(it) }
                .onStart { emit(ProfileUiState.Loading) }
                .catch { emit(ProfileUiState.Error(it.message ?: "Failed")) }
        }
        .flowOn(dispatchers.io)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ProfileUiState.Loading,
        )

    fun refresh() {
        refreshTrigger.tryEmit(Unit)
    }
}

sealed interface ProfileUiState {
    data object Loading : ProfileUiState
    data class Success(val data: ProfileData) : ProfileUiState
    data class Error(val message: String) : ProfileUiState
}
```

---

## 8. Channel for One-Shot UI Events

```kotlin
class CheckoutPresenter
@AssistedInject
constructor(
    @Assisted private val navigator: Navigator,
    private val cartRepo: CartRepository,
    private val orderService: OrderService,
    private val dispatchers: DispatcherProvider,
) : Presenter<CheckoutScreen.State> {

    // Channel for one-shot events (navigation, snackbar)
    // NOT SharedFlow -- Channel guarantees delivery exactly once
    private val _events = Channel<CheckoutEvent>(Channel.BUFFERED)
    val events: Flow<CheckoutEvent> = _events.receiveAsFlow()

    @CircuitInject(CheckoutScreen::class, AppScope::class)
    @AssistedFactory
    fun interface Factory {
        fun create(navigator: Navigator): CheckoutPresenter
    }

    @Composable
    override fun present(): CheckoutScreen.State {
        val scope = rememberCoroutineScope()
        var isSubmitting by remember { mutableStateOf(false) }
        val cart by cartRepo.observeCart()
            .collectAsRetainedState(initial = Cart.EMPTY)

        return CheckoutScreen.State(
            cart = cart,
            isSubmitting = isSubmitting,
        ) { event ->
            when (event) {
                is CheckoutScreen.Event.PlaceOrder -> {
                    scope.launch(dispatchers.io) {
                        isSubmitting = true
                        orderService.placeOrder(cart)
                            .onSuccess { orderId ->
                                _events.send(CheckoutEvent.OrderPlaced(orderId))
                            }
                            .onFailure { error ->
                                _events.send(CheckoutEvent.ShowError(error.message ?: "Order failed"))
                            }
                        isSubmitting = false
                    }
                }
            }
        }
    }
}

sealed interface CheckoutEvent {
    data class OrderPlaced(val orderId: String) : CheckoutEvent
    data class ShowError(val message: String) : CheckoutEvent
}
```

---

## 9. Exception Handling -- Sealed Result with Retry

```kotlin
// commonMain - Generic network-safe flow wrapper
fun <T> Flow<T>.asNetworkResult(): Flow<NetworkResult<T>> =
    this
        .map<T, NetworkResult<T>> { NetworkResult.Success(it) }
        .onStart { emit(NetworkResult.Loading) }
        .retryWhen { cause, attempt ->
            if (cause is IOException && attempt < 3) {
                val backoffMs = (2.0.pow(attempt.toInt()) * 1000).toLong()
                delay(backoffMs)
                emit(NetworkResult.Loading) // Re-emit loading on retry
                true
            } else {
                false
            }
        }
        .catch { e -> emit(NetworkResult.Error(e)) }

sealed interface NetworkResult<out T> {
    data object Loading : NetworkResult<Nothing>
    data class Success<T>(val data: T) : NetworkResult<T>
    data class Error(val exception: Throwable) : NetworkResult<Nothing>
}
```

**Usage in a repository:**

```kotlin
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultArticleRepository(
    private val api: ArticleApi,
    private val articleQueries: ArticleQueries,
    private val dispatchers: DispatcherProvider,
) : ArticleRepository {

    override fun observeArticles(): Flow<NetworkResult<List<Article>>> =
        flow {
            // First emit cached data
            val cached = articleQueries.selectAll().executeAsList()
            if (cached.isNotEmpty()) {
                emit(cached.map { it.toDomain() })
            }
            // Then fetch fresh data
            val remote = api.fetchArticles()
            articleQueries.transaction {
                articleQueries.deleteAll()
                remote.forEach { articleQueries.insert(it.toEntity()) }
            }
            emit(remote.map { it.toDomain() })
        }
        .flowOn(dispatchers.io)
        .asNetworkResult()
}
```

---

## 10. Mutex for Thread-Safe Shared State

```kotlin
@Inject
@SingleIn(AppScope::class)
class InMemoryCache<K, V> {
    private val mutex = Mutex()
    private val cache = mutableMapOf<K, CacheEntry<V>>()

    suspend fun get(key: K): V? = mutex.withLock {
        val entry = cache[key] ?: return@withLock null
        if (entry.isExpired()) {
            cache.remove(key)
            null
        } else {
            entry.value
        }
    }

    suspend fun put(key: K, value: V, ttlMs: Long = 60_000L) = mutex.withLock {
        cache[key] = CacheEntry(
            value = value,
            expiresAt = currentTimeMillis() + ttlMs,
        )
    }

    suspend fun getOrPut(key: K, ttlMs: Long = 60_000L, factory: suspend () -> V): V {
        get(key)?.let { return it }
        val value = factory()
        put(key, value, ttlMs)
        return value
    }

    suspend fun invalidate(key: K) = mutex.withLock {
        cache.remove(key)
    }

    suspend fun clear() = mutex.withLock {
        cache.clear()
    }

    private data class CacheEntry<V>(
        val value: V,
        val expiresAt: Long,
    ) {
        fun isExpired(): Boolean = currentTimeMillis() > expiresAt
    }
}
```

---

## 11. Semaphore for Concurrency Limiting

```kotlin
@Inject
@SingleIn(AppScope::class)
class BatchImageUploader(
    private val imageApi: ImageApi,
    private val dispatchers: DispatcherProvider,
) {
    // Limit to 3 concurrent uploads
    private val uploadSemaphore = Semaphore(permits = 3)

    suspend fun uploadAll(images: List<ImageFile>): List<UploadResult> =
        withContext(dispatchers.io) {
            images.map { image ->
                async {
                    uploadSemaphore.withPermit {
                        runCatching { imageApi.upload(image) }
                            .fold(
                                onSuccess = { UploadResult.Success(image.id, it.url) },
                                onFailure = { UploadResult.Failed(image.id, it.message) },
                            )
                    }
                }
            }.awaitAll()
        }
}

sealed interface UploadResult {
    val imageId: String
    data class Success(override val imageId: String, val url: String) : UploadResult
    data class Failed(override val imageId: String, val error: String?) : UploadResult
}
```

---

## 12. callbackFlow -- Wrapping Platform Callbacks

```kotlin
// commonMain - expect declaration
expect class LocationProvider {
    fun observeLocation(): Flow<Location>
}

// androidMain - actual with callbackFlow
actual class LocationProvider(
    private val fusedClient: FusedLocationProviderClient,
) {
    actual fun observeLocation(): Flow<Location> = callbackFlow {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            10_000L, // 10 seconds
        ).build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    trySend(Location(location.latitude, location.longitude))
                }
            }
        }

        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())

        // CRITICAL: always provide awaitClose for cleanup
        awaitClose {
            fusedClient.removeLocationUpdates(callback)
        }
    }
}
```

---

## 13. Network Connectivity Monitor with SharedFlow

```kotlin
// commonMain
interface NetworkMonitor {
    val isOnline: StateFlow<Boolean>
    val connectionEvents: SharedFlow<ConnectionEvent>
}

sealed interface ConnectionEvent {
    data object Connected : ConnectionEvent
    data object Disconnected : ConnectionEvent
    data class ConnectionChanged(val type: ConnectionType) : ConnectionEvent
}

enum class ConnectionType { WIFI, CELLULAR, ETHERNET, UNKNOWN }

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultNetworkMonitor(
    private val connectivitySource: PlatformConnectivitySource,
    appScope: CoroutineScope,
) : NetworkMonitor {

    private val _connectionEvents = MutableSharedFlow<ConnectionEvent>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val connectionEvents: SharedFlow<ConnectionEvent> = _connectionEvents.asSharedFlow()

    override val isOnline: StateFlow<Boolean> = connectivitySource
        .observeConnectivity()
        .map { it != ConnectionType.UNKNOWN }
        .onEach { online ->
            val event = if (online) ConnectionEvent.Connected else ConnectionEvent.Disconnected
            _connectionEvents.emit(event)
        }
        .stateIn(
            scope = appScope,
            started = SharingStarted.Eagerly,  // App-level, always active
            initialValue = true,
        )
}
```

---

## 14. Polling with Cancellation Support

```kotlin
/**
 * Creates a flow that polls a suspend function at a fixed interval.
 * Respects structured concurrency -- automatically stops when scope is cancelled.
 */
fun <T> tickerFlow(
    intervalMs: Long,
    initialDelayMs: Long = 0L,
): Flow<Unit> = flow {
    delay(initialDelayMs)
    while (true) {
        emit(Unit)
        delay(intervalMs)
    }
}

// Usage in a repository
@Inject
class LivePriceRepository(
    private val api: PriceApi,
    private val dispatchers: DispatcherProvider,
) {
    fun observePrices(symbols: List<String>): Flow<Map<String, Price>> =
        tickerFlow(intervalMs = 5_000L)
            .map { api.fetchPrices(symbols) }
            .retry(retries = Long.MAX_VALUE) { cause ->
                cause is IOException && run {
                    delay(10_000L) // Back off on network errors
                    true
                }
            }
            .flowOn(dispatchers.io)
            .conflate() // Drop stale prices if collector is slow
}
```

---

## 15. withTimeout for Deadline-Based Operations

```kotlin
@Inject
class PaymentProcessor(
    private val paymentGateway: PaymentGateway,
    private val dispatchers: DispatcherProvider,
) {
    suspend fun processPayment(payment: Payment): PaymentResult =
        withContext(dispatchers.io) {
            // Hard deadline: payment must complete within 30 seconds
            val authorization = withTimeoutOrNull(30_000L) {
                paymentGateway.authorize(payment)
            } ?: return@withContext PaymentResult.TimedOut

            // Capture can take longer, but still has a deadline
            val capture = withTimeoutOrNull(60_000L) {
                paymentGateway.capture(authorization)
            }

            when {
                capture != null -> PaymentResult.Success(capture.transactionId)
                else -> {
                    // Authorization succeeded but capture timed out -- void the auth
                    runCatching { paymentGateway.void(authorization) }
                    PaymentResult.TimedOut
                }
            }
        }
}

sealed interface PaymentResult {
    data class Success(val transactionId: String) : PaymentResult
    data object TimedOut : PaymentResult
    data class Failed(val reason: String) : PaymentResult
}
```

---

## 16. distinctUntilChangedBy for Selective Updates

```kotlin
data class UserProfile(
    val id: String,
    val name: String,
    val avatarUrl: String,
    val lastSeen: Long,  // Changes frequently, often irrelevant
)

@Inject
class ProfilePresenter(
    private val userRepo: UserRepository,
) {
    // Only re-emit when name or avatar changes, ignore lastSeen updates
    fun observeDisplayProfile(): Flow<DisplayProfile> =
        userRepo.observeProfile()
            .distinctUntilChangedBy { Pair(it.name, it.avatarUrl) }
            .map { DisplayProfile(it.name, it.avatarUrl) }
}

data class DisplayProfile(val name: String, val avatarUrl: String)
```

---

## 17. Anti-Patterns and Fixes

### DO NOT: Use GlobalScope

```kotlin
// BAD -- breaks structured concurrency, leaks coroutines
GlobalScope.launch {
    repository.sync()
}

// GOOD -- inject a scoped CoroutineScope
@Inject
class SyncService(private val appScope: CoroutineScope) {
    fun sync() {
        appScope.launch { repository.sync() }
    }
}
```

### DO NOT: Catch CancellationException

```kotlin
// BAD -- swallows cancellation, coroutine becomes a zombie
try {
    longRunningOperation()
} catch (e: Exception) {
    logger.error("Failed", e)
}

// GOOD -- rethrow CancellationException
try {
    longRunningOperation()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    logger.error("Failed", e)
}

// BEST -- use runCatching carefully (it catches CancellationException!)
// If using runCatching, rethrow cancellation:
runCatching { longRunningOperation() }
    .onFailure { if (it is CancellationException) throw it }
    .getOrElse { fallbackValue }
```

### DO NOT: Hardcode Dispatchers

```kotlin
// BAD -- untestable
suspend fun fetchData() = withContext(Dispatchers.IO) {
    api.fetch()
}

// GOOD -- inject dispatchers
suspend fun fetchData() = withContext(dispatchers.io) {
    api.fetch()
}
```

### DO NOT: Collect Flow in the Wrong Scope

```kotlin
// BAD -- in Android, this leaks if Activity/Fragment is destroyed
lifecycleScope.launch {
    viewModel.state.collect { updateUi(it) }
}

// GOOD -- use repeatOnLifecycle or collectAsStateWithLifecycle in Compose
lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.state.collect { updateUi(it) }
    }
}

// BEST in Compose (ViewModel paradigm)
@Composable
fun MyScreen(viewModel: MyViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // ...
}
```

### DO NOT: Use stateIn(Eagerly) for Screen-Level State

```kotlin
// BAD -- keeps collecting even when screen is gone
val state: StateFlow<UiState> = dataFlow
    .stateIn(viewModelScope, SharingStarted.Eagerly, UiState.Loading)

// GOOD -- stops 5s after last subscriber leaves
val state: StateFlow<UiState> = dataFlow
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)
```

### DO NOT: Forget awaitClose in callbackFlow

```kotlin
// BAD -- resource leak
fun observeClicks(): Flow<Click> = callbackFlow {
    button.setOnClickListener { trySend(Click) }
    // Missing awaitClose!
}

// GOOD
fun observeClicks(): Flow<Click> = callbackFlow {
    button.setOnClickListener { trySend(Click) }
    awaitClose { button.setOnClickListener(null) }
}
```
