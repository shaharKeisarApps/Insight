---
name: coroutines-expert
description: Elite Kotlin Coroutines expertise for KMP apps. Use when implementing async operations, Flow patterns, structured concurrency, cancellation handling, or concurrent data processing. Triggers on coroutine scope design, Flow operators, parallelism, error handling in async code, or dispatcher selection.
---

# Coroutines Expert Skill

## Core Concepts

Kotlin Coroutines provide:
- **Suspend functions**: Non-blocking async operations
- **Coroutine Scopes**: Structured concurrency boundaries
- **Dispatchers**: Thread pool management
- **Flow**: Cold async streams
- **Channels**: Hot async streams

## Coroutine Scopes

### Scope Hierarchy

```kotlin
// Application scope - lives for app lifetime
@SingleIn(AppScope::class)
@Inject
class ApplicationCoroutineScope @Inject constructor() : CoroutineScope {
    override val coroutineContext = SupervisorJob() + Dispatchers.Default
}

// Feature/Screen scope - tied to screen lifecycle
class FeatureScope(
    parentScope: CoroutineScope,
) : CoroutineScope {
    override val coroutineContext = parentScope.coroutineContext + SupervisorJob()
}

// Request scope - single operation
suspend fun processRequest() = coroutineScope {
    // Child coroutines automatically cancelled if this scope fails
}
```

### Scope in Circuit Presenter

```kotlin
@CircuitInject(ProfileScreen::class, AppScope::class)
@Composable
fun ProfilePresenter(
    navigator: Navigator,
    userRepository: UserRepository,
): ProfileScreen.State {
    // Circuit handles scope - no need to create your own
    var user by rememberRetained { mutableStateOf<User?>(null) }
    
    // LaunchedEffect provides coroutine scope
    LaunchedEffect(Unit) {
        userRepository.observeCurrentUser().collect { result ->
            user = result.getOrNull()
        }
    }
    
    return ProfileScreen.State(user = user)
}
```

### CoroutineScope Provider (DI)

```kotlin
@ContributesTo(AppScope::class)
interface CoroutineScopesModule {
    
    @Provides
    @SingleIn(AppScope::class)
    @Named("app")
    fun provideAppScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    @Provides
    @Named("io")
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
    
    @Provides
    @Named("default")
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
    
    @Provides
    @Named("main")
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main
}
```

## Dispatchers

### Dispatcher Selection

```kotlin
// CPU-intensive work
withContext(Dispatchers.Default) {
    val result = heavyComputation(data)
}

// I/O operations (network, disk)
withContext(Dispatchers.IO) {
    val data = api.fetchData()
    database.save(data)
}

// UI updates (Android/Desktop)
withContext(Dispatchers.Main) {
    view.updateUI(result)
}

// Unconfined - immediate execution (use sparingly)
withContext(Dispatchers.Unconfined) {
    // Runs in caller's thread initially
}
```

### Multiplatform Dispatchers

```kotlin
// commonMain
expect val ioDispatcher: CoroutineDispatcher

// androidMain
actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

// iosMain / nativeMain
actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default

// Usage in repository
class UserRepositoryImpl @Inject constructor(
    private val api: UserApi,
    private val db: UserDatabase,
    @Named("io") private val ioDispatcher: CoroutineDispatcher,
) : UserRepository {
    
    override suspend fun getUser(id: String): Either<DomainError, User> =
        withContext(ioDispatcher) {
            api.getUser(id).map { it.toDomain() }
        }
}
```

## Flow Patterns

### Basic Flow Operations

```kotlin
// Create flows
val flow1 = flowOf(1, 2, 3)
val flow2 = listOf(1, 2, 3).asFlow()
val flow3 = flow { 
    emit(1)
    delay(100)
    emit(2)
}

// Transform
val transformed = flow
    .map { it * 2 }
    .filter { it > 5 }
    .take(10)
    .distinctUntilChanged()
```

### StateFlow and SharedFlow

```kotlin
// StateFlow - always has current value
class UserViewModel @Inject constructor(
    private val userRepository: UserRepository,
) {
    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()
    
    fun loadUser(id: String) {
        viewModelScope.launch {
            _state.value = UiState.Loading
            userRepository.getUser(id).fold(
                ifLeft = { _state.value = UiState.Error(it.message) },
                ifRight = { _state.value = UiState.Success(it) },
            )
        }
    }
}

// SharedFlow - for events (no initial value)
class EventBus @Inject constructor() {
    private val _events = MutableSharedFlow<AppEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<AppEvent> = _events.asSharedFlow()
    
    suspend fun emit(event: AppEvent) {
        _events.emit(event)
    }
}
```

### Flow Combining

```kotlin
// Combine - emits when ANY flow emits (with latest values)
val combined = combine(
    userFlow,
    settingsFlow,
    notificationsFlow,
) { user, settings, notifications ->
    DashboardState(user, settings, notifications)
}

// Zip - pairs emissions 1:1
val zipped = flow1.zip(flow2) { a, b -> a + b }

// Merge - combines into single stream
val merged = merge(flow1, flow2, flow3)

// FlatMapConcat - sequential processing
val sequential = ids.asFlow()
    .flatMapConcat { id -> 
        repository.observeItem(id) 
    }

// FlatMapMerge - concurrent processing
val concurrent = ids.asFlow()
    .flatMapMerge(concurrency = 4) { id ->
        repository.observeItem(id)
    }

// FlatMapLatest - cancels previous on new emission
val latest = searchQuery
    .flatMapLatest { query ->
        repository.search(query)
    }
```

### Flow Error Handling

```kotlin
val safeFlow = repository.observeData()
    .catch { e ->
        // Handle error, optionally emit fallback
        emit(emptyList())
        // Or rethrow
        // throw e
    }
    .retry(retries = 3) { cause ->
        // Return true to retry
        cause is IOException
    }
    .retryWhen { cause, attempt ->
        if (cause is IOException && attempt < 3) {
            delay(1000 * (attempt + 1)) // Exponential backoff
            true
        } else {
            false
        }
    }
```

### Flow Lifecycle

```kotlin
val flow = repository.observeData()
    .onStart { 
        emit(Loading)
        analytics.trackFlowStarted()
    }
    .onEach { data ->
        logger.d("Received: $data")
    }
    .onCompletion { cause ->
        if (cause != null) {
            logger.e("Flow failed: $cause")
        } else {
            logger.d("Flow completed normally")
        }
    }
```

### CallbackFlow (Bridging Callbacks)

```kotlin
fun observeLocationUpdates(): Flow<Location> = callbackFlow {
    val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            trySend(location)
        }
        
        override fun onError(error: Exception) {
            close(error)
        }
    }
    
    locationManager.addListener(listener)
    
    awaitClose {
        locationManager.removeListener(listener)
    }
}
```

## Parallel Execution

### Async/Await

```kotlin
suspend fun loadDashboard(): Dashboard = coroutineScope {
    // Launch parallel operations
    val userDeferred = async { userRepository.getUser() }
    val statsDeferred = async { statsRepository.getStats() }
    val notificationsDeferred = async { notificationsRepository.getCount() }
    
    // Await all results
    Dashboard(
        user = userDeferred.await(),
        stats = statsDeferred.await(),
        notifications = notificationsDeferred.await(),
    )
}
```

### Parallel Collection Processing

```kotlin
// Process list items in parallel
suspend fun processItems(items: List<Item>): List<Result> = coroutineScope {
    items.map { item ->
        async { processItem(item) }
    }.awaitAll()
}

// With concurrency limit
suspend fun processItemsLimited(
    items: List<Item>,
    concurrency: Int = 4,
): List<Result> {
    val semaphore = Semaphore(concurrency)
    return coroutineScope {
        items.map { item ->
            async {
                semaphore.withPermit {
                    processItem(item)
                }
            }
        }.awaitAll()
    }
}

// Using Flow for backpressure
suspend fun processItemsFlow(items: List<Item>): List<Result> =
    items.asFlow()
        .flatMapMerge(concurrency = 4) { item ->
            flow { emit(processItem(item)) }
        }
        .toList()
```

### Arrow Parallel Operations

```kotlin
// parZip - parallel with Either error handling
suspend fun loadData(): Either<DomainError, Data> = either {
    parZip(
        { userRepository.getUser().bind() },
        { settingsRepository.getSettings().bind() },
    ) { user, settings ->
        Data(user, settings)
    }
}

// parMap - parallel list processing
suspend fun enrichUsers(ids: List<String>): Either<DomainError, List<EnrichedUser>> = either {
    ids.parMap { id ->
        val user = userRepository.getUser(id).bind()
        val activity = activityRepository.getActivity(id).bind()
        EnrichedUser(user, activity)
    }
}
```

## Cancellation

### Cooperative Cancellation

```kotlin
suspend fun processLargeData(data: List<Item>) {
    data.forEach { item ->
        // Check for cancellation
        ensureActive()
        // Or use yield() to check and give other coroutines a chance
        yield()
        
        processItem(item)
    }
}

// Check cancellation explicitly
suspend fun longRunningTask() {
    while (isActive) {
        doWork()
        delay(1000)
    }
}
```

### NonCancellable Operations

```kotlin
suspend fun saveData(data: Data) {
    try {
        // Normal cancellable work
        processData(data)
    } finally {
        // Ensure cleanup completes even if cancelled
        withContext(NonCancellable) {
            database.save(data)
            analytics.track("data_saved")
        }
    }
}
```

### Timeout

```kotlin
// Throws TimeoutCancellationException
val result = withTimeout(5000) {
    api.fetchData()
}

// Returns null on timeout
val result = withTimeoutOrNull(5000) {
    api.fetchData()
}
```

## Structured Concurrency

### SupervisorJob (Failure Isolation)

```kotlin
// Without supervisor - one failure cancels siblings
coroutineScope {
    launch { task1() }  // If this fails...
    launch { task2() }  // ...this gets cancelled
}

// With supervisor - failures are isolated
supervisorScope {
    launch { task1() }  // If this fails...
    launch { task2() }  // ...this continues
}

// SupervisorJob in scope
class MyService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    fun startTasks() {
        scope.launch { task1() }  // Independent
        scope.launch { task2() }  // Independent
    }
}
```

### Exception Handling

```kotlin
// CoroutineExceptionHandler - last resort
val handler = CoroutineExceptionHandler { _, exception ->
    logger.e("Uncaught exception: $exception")
}

val scope = CoroutineScope(SupervisorJob() + handler + Dispatchers.Default)

// Try-catch in coroutine
scope.launch {
    try {
        riskyOperation()
    } catch (e: Exception) {
        handleError(e)
    }
}

// runCatching
scope.launch {
    runCatching { riskyOperation() }
        .onSuccess { result -> handleSuccess(result) }
        .onFailure { error -> handleError(error) }
}
```

## Testing Coroutines

### runTest

```kotlin
class UserRepositoryTest {
    
    @Test
    fun `getUser returns user from api`() = runTest {
        val api = FakeUserApi()
        val repository = UserRepositoryImpl(api)
        
        api.setUser(testUser)
        
        val result = repository.getUser("123")
        
        assertThat(result.isRight()).isTrue()
    }
}
```

### TestDispatcher

```kotlin
class ViewModelTest {
    
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
    fun `state updates after loading`() = runTest {
        val viewModel = createViewModel()
        
        viewModel.loadData()
        
        // Advance time to execute coroutines
        advanceUntilIdle()
        
        assertThat(viewModel.state.value).isInstanceOf<Success>()
    }
}
```

### Testing Flows

```kotlin
@Test
fun `flow emits loading then data`() = runTest {
    val repository = FakeRepository()
    
    repository.observeData().test {
        assertThat(awaitItem()).isEqualTo(Loading)
        
        repository.emitData(testData)
        
        assertThat(awaitItem()).isEqualTo(Success(testData))
        
        cancelAndIgnoreRemainingEvents()
    }
}
```

## Best Practices

### Inject Dispatchers

```kotlin
// ✅ Testable - dispatcher can be replaced
class Repository @Inject constructor(
    @Named("io") private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun getData() = withContext(ioDispatcher) { ... }
}

// ❌ Not testable - hardcoded dispatcher
class Repository {
    suspend fun getData() = withContext(Dispatchers.IO) { ... }
}
```

### Use Appropriate Scope

```kotlin
// ✅ Respect lifecycle
class Presenter(private val scope: CoroutineScope) {
    fun loadData() {
        scope.launch { ... }  // Cancelled when scope is cancelled
    }
}

// ❌ Leaks coroutines
class Presenter {
    fun loadData() {
        GlobalScope.launch { ... }  // Never cancelled!
    }
}
```

### Handle Errors at Boundaries

```kotlin
// ✅ Handle at collection point
flow.catch { emit(fallback) }
    .collect { updateUi(it) }

// ❌ Swallow errors silently
flow.collect { 
    try { updateUi(it) } 
    catch (e: Exception) { /* ignored */ }
}
```

## Anti-Patterns

❌ **Don't use GlobalScope**
```kotlin
// WRONG
GlobalScope.launch { ... }

// RIGHT
viewModelScope.launch { ... }
// or
applicationScope.launch { ... }
```

❌ **Don't block in suspend functions**
```kotlin
// WRONG
suspend fun getData(): Data {
    Thread.sleep(1000)  // Blocks thread!
    return data
}

// RIGHT
suspend fun getData(): Data {
    delay(1000)  // Suspends, doesn't block
    return data
}
```

❌ **Don't ignore cancellation**
```kotlin
// WRONG
suspend fun process(items: List<Item>) {
    items.forEach { processItem(it) }  // Never checks cancellation
}

// RIGHT
suspend fun process(items: List<Item>) {
    items.forEach { 
        ensureActive()
        processItem(it) 
    }
}
```

## References

- Kotlin Coroutines Guide: https://kotlinlang.org/docs/coroutines-guide.html
- Flow Documentation: https://kotlinlang.org/docs/flow.html
- Structured Concurrency: https://elizarov.medium.com/structured-concurrency-722d765aa952
- Coroutines Best Practices: https://developer.android.com/kotlin/coroutines/coroutines-best-practices
