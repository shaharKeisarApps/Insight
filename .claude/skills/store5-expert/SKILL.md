---
name: store5-expert
description: Elite Store5 caching expertise for KMP offline-first apps. Use when implementing caching strategies, connecting network/database layers, handling StoreResponse states, configuring freshness policies, or building reactive data pipelines. Triggers on repository implementation, caching decisions, offline-first patterns, or data synchronization questions.
---

# Store5 Expert Skill

## Core Concepts

Store5 provides a reactive caching layer with three core components:

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Fetcher   │────▶│    Store    │◀────│   Source    │
│  (Network)  │     │   (Cache)   │     │  of Truth   │
└─────────────┘     └─────────────┘     │ (Database)  │
                           │            └─────────────┘
                           ▼
                    ┌─────────────┐
                    │ StoreResponse│
                    │ Loading/Data │
                    │ /Error/NoNew │
                    └─────────────┘
```

## Installation

```kotlin
// build.gradle.kts
dependencies {
    implementation("org.mobilenativefoundation.store:store5:5.1.0-alpha03")
}
```

## Basic Store Setup

### Simple Store (Network Only)

```kotlin
private val store: Store<String, User> = StoreBuilder
    .from(Fetcher.of { userId: String ->
        api.getUser(userId)
    })
    .build()

// Usage
val user = store.fresh("user-123")  // Force network
val cached = store.get("user-123")  // Cache or network
```

### Store with Source of Truth (Offline-First)

```kotlin
class UserRepositoryImpl @Inject constructor(
    private val api: UserApi,
    private val db: AppDatabase,
) : UserRepository {
    
    private val store: Store<String, User> = StoreBuilder.from(
        fetcher = Fetcher.of { userId: String ->
            api.getUser(userId).also { response ->
                // Optional: transform network response
            }
        },
        sourceOfTruth = SourceOfTruth.of(
            reader = { userId: String ->
                db.userQueries
                    .getById(userId)
                    .asFlow()
                    .mapToOneOrNull()
                    .map { it?.toDomain() }
            },
            writer = { userId: String, user: User ->
                db.userQueries.upsert(user.toEntity())
            },
            delete = { userId: String ->
                db.userQueries.deleteById(userId)
            },
            deleteAll = {
                db.userQueries.deleteAll()
            },
        ),
    ).build()
    
    override fun observeUser(userId: String): Flow<StoreResponse<User>> =
        store.stream(StoreReadRequest.cached(userId, refresh = true))
    
    override suspend fun getUser(userId: String): User =
        store.get(userId)
    
    override suspend fun refreshUser(userId: String): User =
        store.fresh(userId)
    
    override suspend fun clearUser(userId: String) {
        store.clear(userId)
    }
}
```

## StoreResponse Handling

### Response Types

```kotlin
sealed class StoreResponse<out T> {
    // Loading state - network request in progress
    data class Loading(val origin: Origin) : StoreResponse<Nothing>()
    
    // Data available
    data class Data<T>(val value: T, val origin: Origin) : StoreResponse<T>()
    
    // Error occurred
    sealed class Error : StoreResponse<Nothing>() {
        data class Exception(val error: Throwable, val origin: Origin) : Error()
        data class Message(val message: String, val origin: Origin) : Error()
    }
    
    // No new data (validator returned false, data unchanged)
    data class NoNewData(val origin: Origin) : StoreResponse<Nothing>()
}

enum class Origin {
    Cache,      // From SourceOfTruth
    Fetcher,    // From network
    Initial     // Initial empty state
}
```

### Pattern: Convert to UI State

```kotlin
@CircuitInject(ProfileScreen::class, AppScope::class)
@Composable
fun ProfilePresenter(
    screen: ProfileScreen,
    navigator: Navigator,
    userRepository: UserRepository,
): ProfileScreen.State {
    var state by rememberRetained { 
        mutableStateOf<ProfileScreen.State>(ProfileScreen.State.Loading) 
    }
    
    LaunchedEffect(screen.userId) {
        userRepository.observeUser(screen.userId).collect { response ->
            state = response.toUiState(
                currentState = state,
                onRetry = { /* refresh logic */ },
            )
        }
    }
    
    return state
}

// Extension for clean mapping
fun <T> StoreResponse<T>.toUiState(
    currentState: UiState<T>,
    transform: (T) -> UiState<T> = { UiState.Success(it) },
    onRetry: () -> Unit,
): UiState<T> = when (this) {
    is StoreResponse.Loading -> when (currentState) {
        is UiState.Success -> currentState.copy(isRefreshing = true)
        else -> UiState.Loading
    }
    is StoreResponse.Data -> transform(value).let {
        if (it is UiState.Success) it.copy(
            isFromCache = origin == StoreResponse.Origin.Cache
        ) else it
    }
    is StoreResponse.Error.Exception -> UiState.Error(
        message = error.message ?: "Unknown error",
        onRetry = onRetry,
    )
    is StoreResponse.Error.Message -> UiState.Error(
        message = message,
        onRetry = onRetry,
    )
    is StoreResponse.NoNewData -> currentState
}
```

### Pattern: Convert to Either

```kotlin
fun <T> Flow<StoreResponse<T>>.toEitherFlow(): Flow<Either<DataError, T>> =
    this.filterNot { it is StoreResponse.Loading || it is StoreResponse.NoNewData }
        .map { response ->
            when (response) {
                is StoreResponse.Data -> response.value.right()
                is StoreResponse.Error.Exception -> 
                    DataError.fromThrowable(response.error).left()
                is StoreResponse.Error.Message -> 
                    DataError.Unknown(response.message).left()
                else -> DataError.Unknown("Unexpected state").left()
            }
        }

// Usage in repository
override fun observeUser(userId: String): Flow<Either<DataError, User>> =
    store.stream(StoreReadRequest.cached(userId, refresh = true))
        .toEitherFlow()
```

## Advanced Patterns

### Store with Validator (Freshness)

```kotlin
private val store = StoreBuilder.from(
    fetcher = Fetcher.of { key -> api.getData(key) },
    sourceOfTruth = sourceOfTruth,
)
.validator(Validator.by { item: CachedData ->
    // Data is fresh if updated within last hour
    val age = Clock.System.now() - item.fetchedAt
    age < 1.hours
})
.build()

// When validator returns false:
// - Cached data still emitted (stale)
// - Fetcher triggered automatically
// - Fresh data emitted when available
```

### Store with Converter (Type Transformation)

```kotlin
// Network type → Local type → Domain type
private val store = StoreBuilder.from<String, NetworkUser, User, DbUser>(
    fetcher = Fetcher.of { userId -> api.getUser(userId) },
    sourceOfTruth = SourceOfTruth.of(
        reader = { userId -> db.userQueries.getById(userId).asFlow().mapToOneOrNull() },
        writer = { _, dbUser -> db.userQueries.upsert(dbUser) },
    ),
    converter = Converter.Builder<NetworkUser, User, DbUser>()
        .fromNetworkToLocal { networkUser -> networkUser.toDomain() }
        .fromLocalToOutput { dbUser -> dbUser.toDomain() }
        .fromOutputToLocal { user -> user.toEntity() }
        .build(),
).build()
```

### Mutable Store (Write-Through)

```kotlin
private val mutableStore: MutableStore<String, User> = MutableStoreBuilder.from(
    fetcher = Fetcher.of { key -> api.getUser(key) },
    sourceOfTruth = SourceOfTruth.of(
        reader = { key -> db.userQueries.getById(key).asFlow().mapToOneOrNull() },
        writer = { key, user -> db.userQueries.upsert(user) },
    ),
).build()

// Write operations
suspend fun updateUser(user: User): StoreWriteResponse {
    return mutableStore.write(
        request = StoreWriteRequest.of(
            key = user.id,
            value = user,
        )
    )
}

// Handle write response
when (val response = updateUser(updatedUser)) {
    is StoreWriteResponse.Success -> {
        // Written to SOT, may trigger sync
    }
    is StoreWriteResponse.Error.Exception -> {
        // Handle error
    }
    is StoreWriteResponse.Error.Message -> {
        // Handle error message
    }
}
```

### Paged Store

```kotlin
data class PageKey(
    val query: String,
    val page: Int,
    val pageSize: Int = 20,
)

data class PagedResult<T>(
    val items: List<T>,
    val nextPage: Int?,
    val totalCount: Int,
)

private val pagedStore: Store<PageKey, PagedResult<Item>> = StoreBuilder.from(
    fetcher = Fetcher.of { key: PageKey ->
        api.search(
            query = key.query,
            page = key.page,
            size = key.pageSize,
        ).let { response ->
            PagedResult(
                items = response.items,
                nextPage = if (response.hasMore) key.page + 1 else null,
                totalCount = response.total,
            )
        }
    },
    sourceOfTruth = SourceOfTruth.of(
        reader = { key ->
            db.itemQueries.search(
                query = "%${key.query}%",
                limit = key.pageSize.toLong(),
                offset = (key.page * key.pageSize).toLong(),
            ).asFlow().mapToList().map { items ->
                PagedResult(items, null, items.size) // Simplified for cache
            }
        },
        writer = { key, result ->
            db.transaction {
                if (key.page == 0) {
                    // Clear previous search results on first page
                    db.itemQueries.deleteByQuery(key.query)
                }
                result.items.forEach { db.itemQueries.upsert(it) }
            }
        },
    ),
).build()
```

### Store with Memory Cache Policy

```kotlin
private val store = StoreBuilder.from(
    fetcher = fetcher,
    sourceOfTruth = sourceOfTruth,
)
.cachePolicy(
    MemoryPolicy.builder<String, User>()
        .setMaxSize(100)  // Max 100 items in memory
        .setExpireAfterWrite(30.minutes)  // Expire after 30 min
        .build()
)
.build()
```

## StoreReadRequest Options

```kotlin
// Get from cache, refresh in background
store.stream(StoreReadRequest.cached(key, refresh = true))

// Get from cache only (no network)
store.stream(StoreReadRequest.cached(key, refresh = false))

// Skip cache, force network (still writes to SOT)
store.stream(StoreReadRequest.fresh(key))

// Skip cache, skip SOT write
store.stream(StoreReadRequest.skipMemory(key, refresh = true))
```

## Complete Repository Example

```kotlin
interface UserRepository {
    fun observeUser(userId: String): Flow<Either<DataError, User>>
    fun observeUsers(): Flow<Either<DataError, List<User>>>
    suspend fun refreshUser(userId: String): Either<DataError, User>
    suspend fun updateUser(user: User): Either<DataError, Unit>
    suspend fun clearCache()
}

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class UserRepositoryImpl(
    private val api: UserApi,
    private val db: AppDatabase,
) : UserRepository {
    
    // Single user store
    private val userStore: Store<String, User> = StoreBuilder.from(
        fetcher = Fetcher.of { userId: String ->
            api.getUser(userId).getOrThrow()
        },
        sourceOfTruth = SourceOfTruth.of(
            reader = { userId ->
                db.userQueries.getById(userId).asFlow().mapToOneOrNull().map { it?.toDomain() }
            },
            writer = { _, user -> db.userQueries.upsert(user.toEntity()) },
            delete = { userId -> db.userQueries.deleteById(userId) },
            deleteAll = { db.userQueries.deleteAll() },
        ),
    )
    .validator(Validator.by { user ->
        user.fetchedAt > Clock.System.now() - 5.minutes
    })
    .build()
    
    // List store (different key type)
    private val usersStore: Store<Unit, List<User>> = StoreBuilder.from(
        fetcher = Fetcher.of { _: Unit ->
            api.getUsers().getOrThrow()
        },
        sourceOfTruth = SourceOfTruth.of(
            reader = { _ ->
                db.userQueries.getAll().asFlow().mapToList().map { list ->
                    list.map { it.toDomain() }
                }
            },
            writer = { _, users ->
                db.transaction {
                    db.userQueries.deleteAll()
                    users.forEach { db.userQueries.upsert(it.toEntity()) }
                }
            },
        ),
    ).build()
    
    // Mutable store for writes
    private val mutableUserStore: MutableStore<String, User> = MutableStoreBuilder.from(
        fetcher = Fetcher.of { userId: String -> api.getUser(userId).getOrThrow() },
        sourceOfTruth = SourceOfTruth.of(
            reader = { userId ->
                db.userQueries.getById(userId).asFlow().mapToOneOrNull().map { it?.toDomain() }
            },
            writer = { _, user -> db.userQueries.upsert(user.toEntity()) },
        ),
    ).build()
    
    override fun observeUser(userId: String): Flow<Either<DataError, User>> =
        userStore.stream(StoreReadRequest.cached(userId, refresh = true))
            .toEitherFlow()
    
    override fun observeUsers(): Flow<Either<DataError, List<User>>> =
        usersStore.stream(StoreReadRequest.cached(Unit, refresh = true))
            .toEitherFlow()
    
    override suspend fun refreshUser(userId: String): Either<DataError, User> =
        Either.catch { userStore.fresh(userId) }
            .mapLeft { DataError.fromThrowable(it) }
    
    override suspend fun updateUser(user: User): Either<DataError, Unit> =
        Either.catch {
            // Optimistic update: write locally first
            mutableUserStore.write(
                StoreWriteRequest.of(key = user.id, value = user)
            )
            // Then sync to server
            api.updateUser(user.id, user.toRequest()).bind()
        }.mapLeft { DataError.fromThrowable(it) }
    
    override suspend fun clearCache() {
        userStore.clear()
        usersStore.clear()
    }
}
```

## Testing Stores

```kotlin
class UserRepositoryTest {
    
    private val fakeApi = FakeUserApi()
    private val testDb = createTestDatabase()
    private lateinit var repository: UserRepositoryImpl
    
    @BeforeTest
    fun setup() {
        repository = UserRepositoryImpl(fakeApi, testDb)
    }
    
    @Test
    fun `observeUser emits cache then network`() = runTest {
        // Seed cache
        testDb.userQueries.upsert(cachedUser.toEntity())
        fakeApi.setUser(freshUser)
        
        repository.observeUser("123").test {
            // First: cached data
            val cached = awaitItem()
            assertThat(cached.getOrNull()).isEqualTo(cachedUser)
            
            // Second: fresh data from network
            val fresh = awaitItem()
            assertThat(fresh.getOrNull()).isEqualTo(freshUser)
            
            cancelAndIgnoreRemainingEvents()
        }
    }
    
    @Test
    fun `observeUser returns cache on network error`() = runTest {
        testDb.userQueries.upsert(cachedUser.toEntity())
        fakeApi.setError(IOException("No network"))
        
        repository.observeUser("123").test {
            // Should still get cached data
            val result = awaitItem()
            assertThat(result.getOrNull()).isEqualTo(cachedUser)
            
            // Error should come next (or be filtered depending on impl)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

## Anti-Patterns

❌ **Don't ignore StoreResponse.Loading in UI**
```kotlin
// WRONG - UI flickers or shows stale content
.filter { it is StoreResponse.Data }

// RIGHT - Handle loading state properly
.collect { response ->
    when (response) {
        is StoreResponse.Loading -> showLoading()
        is StoreResponse.Data -> showData(response.value)
        is StoreResponse.Error -> showError(response)
    }
}
```

❌ **Don't create store instances per-call**
```kotlin
// WRONG - creates new store each time
fun getUser(id: String) = StoreBuilder.from(...).build().get(id)

// RIGHT - reuse single store instance
private val store = StoreBuilder.from(...).build()
fun getUser(id: String) = store.get(id)
```

❌ **Don't forget to handle NoNewData**
```kotlin
// WRONG - might miss current state
is StoreResponse.NoNewData -> { /* nothing */ }

// RIGHT - preserve current state
is StoreResponse.NoNewData -> state // Keep showing current data
```

## References

- Store5 GitHub: https://github.com/MobileNativeFoundation/Store
- Store5 Documentation: https://mobilenativefoundation.github.io/Store/
- KMP Offline-First Guide: https://touchlab.co/kmp-offline-first
