# Store5 Examples (v5.1.0-alpha08)

Production-ready examples using Metro DI, Circuit MVI, Ktor 3.4.0, and SQLDelight 2.2.1.

---

## 1. Basic Store with Ktor Fetcher + SQLDelight SourceOfTruth + Metro DI

```kotlin
// -- Keys --
@JvmInline
value class ProductId(val value: String)

// -- Network DTO (Ktor response) --
@Serializable
data class ProductDto(
    val id: String,
    val name: String,
    val price: Double,
    val description: String,
    val imageUrl: String?,
)

// -- Domain model (UI consumes this) --
data class Product(
    val id: String,
    val name: String,
    val price: Double,
    val description: String,
    val imageUrl: String?,
    val fetchedAt: Long,
)

// -- Metro DI Module --
@ContributesTo(AppScope::class)
interface ProductStoreModule {

    companion object {

        @Provides
        @SingleIn(AppScope::class)
        fun provideProductStore(
            httpClient: HttpClient,
            database: AppDatabase,
        ): Store<ProductId, Product> {
            val queries = database.productQueries
            return StoreBuilder.from(
                fetcher = Fetcher.of("fetchProduct") { key: ProductId ->
                    httpClient.get("/api/products/${key.value}").body<ProductDto>()
                },
                sourceOfTruth = SourceOfTruth.of(
                    reader = { key: ProductId ->
                        queries.selectById(key.value)
                            .asFlow()
                            .mapToOneOrNull(Dispatchers.IO)
                            .map { entity ->
                                entity?.let {
                                    Product(
                                        id = it.id,
                                        name = it.name,
                                        price = it.price,
                                        description = it.description,
                                        imageUrl = it.imageUrl,
                                        fetchedAt = it.fetchedAt,
                                    )
                                }
                            }
                    },
                    writer = { _: ProductId, dto: ProductDto ->
                        queries.upsert(
                            id = dto.id,
                            name = dto.name,
                            price = dto.price,
                            description = dto.description,
                            imageUrl = dto.imageUrl,
                            fetchedAt = Clock.System.now().toEpochMilliseconds(),
                        )
                    },
                    delete = { key -> queries.deleteById(key.value) },
                    deleteAll = { queries.deleteAll() },
                ),
            ).build()
        }
    }
}
```

---

## 2. Full Repository Wrapping Store

```kotlin
interface ProductRepository {
    fun getProduct(id: String, forceRefresh: Boolean = false): Flow<StoreReadResponse<Product>>
    fun observeProduct(id: String): Flow<Product?>
    suspend fun refreshProduct(id: String): Product
    suspend fun clearProduct(id: String)
    suspend fun clearAll()
}

@Inject
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class RealProductRepository(
    private val store: Store<ProductId, Product>,
) : ProductRepository {

    override fun getProduct(
        id: String,
        forceRefresh: Boolean,
    ): Flow<StoreReadResponse<Product>> {
        val key = ProductId(id)
        return if (forceRefresh) {
            store.stream(StoreReadRequest.fresh(key))
        } else {
            store.stream(StoreReadRequest.cached(key, refresh = true))
        }
    }

    override fun observeProduct(id: String): Flow<Product?> {
        return store.stream(StoreReadRequest.cached(ProductId(id), refresh = false))
            .mapNotNull { response ->
                when (response) {
                    is StoreReadResponse.Data -> response.value
                    else -> null
                }
            }
            .distinctUntilChanged()
    }

    override suspend fun refreshProduct(id: String): Product {
        return store.fresh(ProductId(id))
    }

    override suspend fun clearProduct(id: String) {
        store.clear(ProductId(id))
    }

    override suspend fun clearAll() {
        store.clear()
    }
}
```

---

## 3. Circuit Presenter Consuming Store Stream

```kotlin
@Parcelize
data class ProductScreen(val productId: String) : Screen {

    sealed interface State : CircuitUiState {
        data object Loading : State
        data class Success(
            val product: Product,
            val eventSink: (Event) -> Unit,
        ) : State
        data class Error(
            val message: String?,
            val eventSink: (Event) -> Unit,
        ) : State
    }

    sealed interface Event : CircuitUiEvent {
        data object Refresh : Event
        data object Retry : Event
        data object NavigateBack : Event
    }
}

@AssistedInject
class ProductPresenter(
    @Assisted private val screen: ProductScreen,
    @Assisted private val navigator: Navigator,
    private val repository: ProductRepository,
) : Presenter<ProductScreen.State> {

    @CircuitInject(ProductScreen::class, AppScope::class)
    @AssistedFactory
    fun interface Factory {
        fun create(
            screen: ProductScreen,
            navigator: Navigator,
        ): ProductPresenter
    }

    @Composable
    override fun present(): ProductScreen.State {
        var uiState by rememberRetained {
            mutableStateOf<ProductScreen.State>(ProductScreen.State.Loading)
        }
        var refreshTrigger by rememberRetained { mutableStateOf(0) }

        LaunchedEffect(refreshTrigger) {
            repository.getProduct(
                id = screen.productId,
                forceRefresh = refreshTrigger > 0,
            ).collect { response ->
                uiState = when (response) {
                    is StoreReadResponse.Loading -> {
                        // Keep showing previous data during refresh if available
                        val current = uiState
                        if (current is ProductScreen.State.Success) current
                        else ProductScreen.State.Loading
                    }
                    is StoreReadResponse.Data -> ProductScreen.State.Success(
                        product = response.value,
                        eventSink = ::onEvent,
                    )
                    is StoreReadResponse.Error -> ProductScreen.State.Error(
                        message = response.errorMessageOrNull() ?: "Unknown error",
                        eventSink = ::onEvent,
                    )
                    is StoreReadResponse.Initial,
                    is StoreReadResponse.NoNewData -> uiState
                }
            }
        }

        fun onEvent(event: ProductScreen.Event) {
            when (event) {
                ProductScreen.Event.Refresh -> refreshTrigger++
                ProductScreen.Event.Retry -> refreshTrigger++
                ProductScreen.Event.NavigateBack -> navigator.pop()
            }
        }

        return uiState
    }
}
```

---

## 4. MutableStore for CRUD Operations

```kotlin
// -- Network write response --
@Serializable
data class ProductWriteResponse(
    val id: String,
    val success: Boolean,
)

// -- Updater: pushes local changes to API --
@Inject
@SingleIn(AppScope::class)
class ProductUpdater(
    private val httpClient: HttpClient,
) : Updater<ProductId, Product, ProductWriteResponse> {

    override suspend fun post(
        key: ProductId,
        value: Product,
    ): UpdaterResult<ProductWriteResponse> {
        return try {
            val response = httpClient.put("/api/products/${key.value}") {
                contentType(ContentType.Application.Json)
                setBody(
                    ProductDto(
                        id = value.id,
                        name = value.name,
                        price = value.price,
                        description = value.description,
                        imageUrl = value.imageUrl,
                    )
                )
            }.body<ProductWriteResponse>()
            UpdaterResult.Success.Typed(response)
        } catch (e: Exception) {
            UpdaterResult.Error.Exception(e)
        }
    }
}

// -- Converter: DTO <-> Entity <-> Domain --
class ProductConverter : Converter<ProductDto, ProductEntity, Product> {
    override fun fromNetworkToLocal(network: ProductDto): ProductEntity =
        ProductEntity(
            id = network.id,
            name = network.name,
            price = network.price,
            description = network.description,
            imageUrl = network.imageUrl,
            fetchedAt = Clock.System.now().toEpochMilliseconds(),
        )

    override fun fromOutputToLocal(output: Product): ProductEntity =
        ProductEntity(
            id = output.id,
            name = output.name,
            price = output.price,
            description = output.description,
            imageUrl = output.imageUrl,
            fetchedAt = output.fetchedAt,
        )
}

// -- Metro DI for MutableStore --
@ContributesTo(AppScope::class)
interface MutableProductStoreModule {

    companion object {

        @Provides
        @SingleIn(AppScope::class)
        fun provideMutableProductStore(
            httpClient: HttpClient,
            database: AppDatabase,
            updater: ProductUpdater,
        ): MutableStore<ProductId, Product> {
            val queries = database.productQueries
            return MutableStoreBuilder.from(
                fetcher = Fetcher.of("fetchProduct") { key: ProductId ->
                    httpClient.get("/api/products/${key.value}").body<ProductDto>()
                },
                sourceOfTruth = SourceOfTruth.of(
                    reader = { key: ProductId ->
                        queries.selectById(key.value)
                            .asFlow()
                            .mapToOneOrNull(Dispatchers.IO)
                            .map { it?.toDomain() }
                    },
                    writer = { _: ProductId, entity: ProductEntity ->
                        queries.upsert(
                            id = entity.id,
                            name = entity.name,
                            price = entity.price,
                            description = entity.description,
                            imageUrl = entity.imageUrl,
                            fetchedAt = entity.fetchedAt,
                        )
                    },
                    delete = { key -> queries.deleteById(key.value) },
                    deleteAll = { queries.deleteAll() },
                ),
                converter = ProductConverter(),
            ).build(updater = updater)
        }
    }
}

// -- Repository using MutableStore --
@Inject
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class MutableProductRepository(
    private val store: MutableStore<ProductId, Product>,
) {

    suspend fun updateProduct(product: Product): StoreWriteResponse {
        return store.write(
            StoreWriteRequest.of(
                key = ProductId(product.id),
                value = product,
            )
        )
    }

    suspend fun deleteProduct(id: String) {
        store.clear(ProductId(id))
    }
}
```

---

## 5. Store with Converter -- Network DTO to Domain Model

```kotlin
// Three-layer model separation:
// Network: UserDto (from API)
// Local:   UserEntity (SQLDelight generated)
// Output:  User (domain model for UI)

@Serializable
data class UserDto(
    val id: String,
    val email: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("avatar_url") val avatarUrl: String?,
    @SerialName("created_at") val createdAt: String,
)

// SQLDelight-generated entity (from .sq file)
// data class UserEntity(val id: String, val email: String, ...)

data class User(
    val id: String,
    val email: String,
    val fullName: String,
    val avatarUrl: String?,
    val createdAt: Instant,
    val fetchedAt: Long,
)

class UserConverter : Converter<UserDto, UserEntity, User> {
    override fun fromNetworkToLocal(network: UserDto): UserEntity =
        UserEntity(
            id = network.id,
            email = network.email,
            fullName = network.fullName,
            avatarUrl = network.avatarUrl,
            createdAt = Instant.parse(network.createdAt).toEpochMilliseconds(),
            fetchedAt = Clock.System.now().toEpochMilliseconds(),
        )

    override fun fromOutputToLocal(output: User): UserEntity =
        UserEntity(
            id = output.id,
            email = output.email,
            fullName = output.fullName,
            avatarUrl = output.avatarUrl,
            createdAt = output.createdAt.toEpochMilliseconds(),
            fetchedAt = output.fetchedAt,
        )
}

// Store with Converter applied via toMutableStoreBuilder
@ContributesTo(AppScope::class)
interface UserStoreModule {

    companion object {

        @Provides
        @SingleIn(AppScope::class)
        fun provideUserStore(
            httpClient: HttpClient,
            database: AppDatabase,
        ): Store<UserId, User> {
            val queries = database.userQueries
            return StoreBuilder.from(
                fetcher = Fetcher.of("fetchUser") { key: UserId ->
                    httpClient.get("/api/users/${key.value}").body<UserDto>()
                },
                sourceOfTruth = SourceOfTruth.of(
                    reader = { key: UserId ->
                        queries.selectById(key.value)
                            .asFlow()
                            .mapToOneOrNull(Dispatchers.IO)
                            .map { entity ->
                                entity?.let {
                                    User(
                                        id = it.id,
                                        email = it.email,
                                        fullName = it.fullName,
                                        avatarUrl = it.avatarUrl,
                                        createdAt = Instant.fromEpochMilliseconds(it.createdAt),
                                        fetchedAt = it.fetchedAt,
                                    )
                                }
                            }
                    },
                    writer = { _: UserId, dto: UserDto ->
                        val entity = UserConverter().fromNetworkToLocal(dto)
                        queries.upsert(
                            id = entity.id,
                            email = entity.email,
                            fullName = entity.fullName,
                            avatarUrl = entity.avatarUrl,
                            createdAt = entity.createdAt,
                            fetchedAt = entity.fetchedAt,
                        )
                    },
                    delete = { key -> queries.deleteById(key.value) },
                    deleteAll = { queries.deleteAll() },
                ),
            ).build()
        }
    }
}
```

---

## 6. Store with Validator -- Time-Based Cache Staleness

```kotlin
@ContributesTo(AppScope::class)
interface ValidatedProductStoreModule {

    companion object {

        @Provides
        @SingleIn(AppScope::class)
        fun provideValidatedProductStore(
            httpClient: HttpClient,
            database: AppDatabase,
        ): Store<ProductId, Product> {
            val queries = database.productQueries
            return StoreBuilder.from(
                fetcher = Fetcher.of("fetchProduct") { key: ProductId ->
                    httpClient.get("/api/products/${key.value}").body<ProductDto>()
                },
                sourceOfTruth = SourceOfTruth.of(
                    reader = { key: ProductId ->
                        queries.selectById(key.value)
                            .asFlow()
                            .mapToOneOrNull(Dispatchers.IO)
                            .map { it?.toDomain() }
                    },
                    writer = { _: ProductId, dto: ProductDto ->
                        queries.upsert(
                            id = dto.id,
                            name = dto.name,
                            price = dto.price,
                            description = dto.description,
                            imageUrl = dto.imageUrl,
                            fetchedAt = Clock.System.now().toEpochMilliseconds(),
                        )
                    },
                    delete = { key -> queries.deleteById(key.value) },
                    deleteAll = { queries.deleteAll() },
                ),
            )
                .validator(
                    Validator.by { product: Product ->
                        val age = Clock.System.now() -
                            Instant.fromEpochMilliseconds(product.fetchedAt)
                        age < 5.minutes // Data older than 5 minutes triggers re-fetch
                    }
                )
                .cachePolicy(
                    MemoryPolicy.builder<ProductId, Product>()
                        .setMaxSize(50)
                        .setExpireAfterWrite(10.minutes.inWholeMilliseconds)
                        .build()
                )
                .build()
        }
    }
}
```

---

## 7. Bookkeeper Pattern for Sync Status Tracking

```kotlin
// SQLDelight schema for bookkeeping table:
// bookkeeper.sq:
// CREATE TABLE IF NOT EXISTS sync_status (
//     key TEXT NOT NULL PRIMARY KEY,
//     last_failed_sync INTEGER
// );
//
// selectByKey:
// SELECT last_failed_sync FROM sync_status WHERE key = ?;
//
// upsert:
// INSERT OR REPLACE INTO sync_status (key, last_failed_sync) VALUES (?, ?);
//
// deleteByKey:
// DELETE FROM sync_status WHERE key = ?;
//
// deleteAll:
// DELETE FROM sync_status;

@ContributesTo(AppScope::class)
interface BookkeptProductStoreModule {

    companion object {

        @Provides
        @SingleIn(AppScope::class)
        fun provideBookkeeper(
            database: AppDatabase,
        ): Bookkeeper<ProductId> {
            val queries = database.syncStatusQueries
            return Bookkeeper.by(
                getLastFailedSync = { key: ProductId ->
                    queries.selectByKey(key.value)
                        .executeAsOneOrNull()
                        ?.last_failed_sync
                },
                setLastFailedSync = { key: ProductId, timestamp: Long ->
                    queries.upsert(key.value, timestamp)
                    true
                },
                clear = { key: ProductId ->
                    queries.deleteByKey(key.value)
                    true
                },
                clearAll = {
                    queries.deleteAll()
                    true
                },
            )
        }

        @Provides
        @SingleIn(AppScope::class)
        fun provideBookkeptMutableStore(
            httpClient: HttpClient,
            database: AppDatabase,
            updater: ProductUpdater,
            bookkeeper: Bookkeeper<ProductId>,
        ): MutableStore<ProductId, Product> {
            val queries = database.productQueries
            return MutableStoreBuilder.from(
                fetcher = Fetcher.of("fetchProduct") { key: ProductId ->
                    httpClient.get("/api/products/${key.value}").body<ProductDto>()
                },
                sourceOfTruth = SourceOfTruth.of(
                    reader = { key: ProductId ->
                        queries.selectById(key.value)
                            .asFlow()
                            .mapToOneOrNull(Dispatchers.IO)
                            .map { it?.toDomain() }
                    },
                    writer = { _: ProductId, entity: ProductEntity ->
                        queries.upsert(
                            id = entity.id,
                            name = entity.name,
                            price = entity.price,
                            description = entity.description,
                            imageUrl = entity.imageUrl,
                            fetchedAt = entity.fetchedAt,
                        )
                    },
                    delete = { key -> queries.deleteById(key.value) },
                    deleteAll = { queries.deleteAll() },
                ),
                converter = ProductConverter(),
            ).build(
                updater = updater,
                bookkeeper = bookkeeper, // Tracks failed syncs
            )
        }
    }
}
```

---

## 8. Testing a Store-Backed Repository

```kotlin
class FakeProductStore : Store<ProductId, Product> {

    private val data = mutableMapOf<ProductId, Product>()
    var streamBehavior: (StoreReadRequest<ProductId>) -> Flow<StoreReadResponse<Product>> = { request ->
        flow {
            emit(StoreReadResponse.Loading(StoreReadResponseOrigin.Fetcher))
            val product = data[request.key]
            if (product != null) {
                emit(StoreReadResponse.Data(product, StoreReadResponseOrigin.Cache))
            } else {
                emit(
                    StoreReadResponse.Error.Message(
                        "Not found",
                        StoreReadResponseOrigin.Fetcher,
                    )
                )
            }
        }
    }

    fun seed(vararg products: Product) {
        products.forEach { data[ProductId(it.id)] = it }
    }

    override fun stream(
        request: StoreReadRequest<ProductId>,
    ): Flow<StoreReadResponse<Product>> = streamBehavior(request)

    override suspend fun get(key: ProductId): Product =
        data[key] ?: throw NoSuchElementException("No product for $key")

    override suspend fun fresh(key: ProductId): Product =
        data[key] ?: throw NoSuchElementException("No product for $key")

    override suspend fun clear(key: ProductId) { data.remove(key) }
    override suspend fun clear() { data.clear() }
}

class ProductRepositoryTest {

    private val fakeStore = FakeProductStore()
    private val repository = RealProductRepository(fakeStore)

    private val testProduct = Product(
        id = "prod-1",
        name = "Widget",
        price = 9.99,
        description = "A fine widget",
        imageUrl = null,
        fetchedAt = Clock.System.now().toEpochMilliseconds(),
    )

    @Test
    fun getProduct_emitsCachedData() = runTest {
        fakeStore.seed(testProduct)

        val responses = repository.getProduct("prod-1").toList()

        // First emission: Loading
        assertIs<StoreReadResponse.Loading>(responses[0])
        // Second emission: Data from cache
        val data = assertIs<StoreReadResponse.Data<Product>>(responses[1])
        assertEquals("Widget", data.value.name)
        assertEquals(StoreReadResponseOrigin.Cache, data.origin)
    }

    @Test
    fun getProduct_emitsErrorWhenNotFound() = runTest {
        // No data seeded

        val responses = repository.getProduct("missing-id").toList()

        assertIs<StoreReadResponse.Loading>(responses[0])
        val error = assertIs<StoreReadResponse.Error.Message>(responses[1])
        assertEquals("Not found", error.message)
    }

    @Test
    fun observeProduct_filtersToDataOnly() = runTest {
        fakeStore.seed(testProduct)

        val products = repository.observeProduct("prod-1").take(1).toList()

        assertEquals(1, products.size)
        assertEquals("Widget", products[0]?.name)
    }

    @Test
    fun clearProduct_removesFromStore() = runTest {
        fakeStore.seed(testProduct)

        repository.clearProduct("prod-1")

        assertFailsWith<NoSuchElementException> {
            fakeStore.get(ProductId("prod-1"))
        }
    }
}
```

---

## 9. Error Handling -- Mapping StoreReadResponse.Error to AppError

```kotlin
// -- App-level error hierarchy --
sealed class AppError {
    data class Network(
        val message: String,
        val cause: Throwable? = null,
    ) : AppError()

    data class NotFound(val resourceId: String) : AppError()

    data class Server(
        val statusCode: Int,
        val message: String,
    ) : AppError()

    data class Unknown(val message: String) : AppError()
}

// -- Result wrapper for UI state --
sealed interface DataResult<out T> {
    data object Loading : DataResult<Nothing>
    data class Success<T>(val data: T) : DataResult<T>
    data class Failure(val error: AppError) : DataResult<Nothing>
}

// -- Extension to map StoreReadResponse to DataResult --
fun <T> StoreReadResponse<T>.toDataResult(
    resourceId: String = "",
): DataResult<T>? = when (this) {
    is StoreReadResponse.Initial -> null // Skip sentinel
    is StoreReadResponse.Loading -> DataResult.Loading
    is StoreReadResponse.Data -> DataResult.Success(value)
    is StoreReadResponse.NoNewData -> null // Keep current state
    is StoreReadResponse.Error -> DataResult.Failure(toAppError(resourceId))
}

// -- Map Store errors to typed AppError --
fun StoreReadResponse.Error.toAppError(
    resourceId: String = "",
): AppError = when (this) {
    is StoreReadResponse.Error.Exception -> {
        when (val cause = error) {
            is io.ktor.client.plugins.ClientRequestException -> {
                when (cause.response.status.value) {
                    404 -> AppError.NotFound(resourceId)
                    in 400..499 -> AppError.Network(
                        message = cause.message,
                        cause = cause,
                    )
                    in 500..599 -> AppError.Server(
                        statusCode = cause.response.status.value,
                        message = cause.message,
                    )
                    else -> AppError.Unknown(cause.message ?: "Request failed")
                }
            }
            is io.ktor.client.plugins.HttpRequestTimeoutException ->
                AppError.Network("Request timed out", cause)
            is kotlinx.io.IOException ->
                AppError.Network("No internet connection", cause)
            else -> AppError.Unknown(cause.message ?: "Unknown error")
        }
    }
    is StoreReadResponse.Error.Message ->
        AppError.Unknown(message)
    is StoreReadResponse.Error.Custom<*> ->
        AppError.Unknown("Custom error: $error")
}

// -- Circuit Presenter using DataResult --
@AssistedInject
class ProductDetailPresenter(
    @Assisted private val screen: ProductScreen,
    @Assisted private val navigator: Navigator,
    private val repository: ProductRepository,
) : Presenter<ProductScreen.State> {

    @CircuitInject(ProductScreen::class, AppScope::class)
    @AssistedFactory
    fun interface Factory {
        fun create(
            screen: ProductScreen,
            navigator: Navigator,
        ): ProductDetailPresenter
    }

    @Composable
    override fun present(): ProductScreen.State {
        var result by rememberRetained {
            mutableStateOf<DataResult<Product>>(DataResult.Loading)
        }

        LaunchedEffect(Unit) {
            repository.getProduct(screen.productId).collect { response ->
                response.toDataResult(resourceId = screen.productId)?.let {
                    result = it
                }
            }
        }

        return when (val current = result) {
            is DataResult.Loading -> ProductScreen.State.Loading
            is DataResult.Success -> ProductScreen.State.Success(
                product = current.data,
                eventSink = { event ->
                    when (event) {
                        ProductScreen.Event.NavigateBack -> navigator.pop()
                        ProductScreen.Event.Refresh -> { /* trigger refresh */ }
                        ProductScreen.Event.Retry -> { /* trigger retry */ }
                    }
                },
            )
            is DataResult.Failure -> ProductScreen.State.Error(
                message = when (val err = current.error) {
                    is AppError.Network -> err.message
                    is AppError.NotFound -> "Product not found"
                    is AppError.Server -> "Server error (${err.statusCode})"
                    is AppError.Unknown -> err.message
                },
                eventSink = { event ->
                    when (event) {
                        ProductScreen.Event.NavigateBack -> navigator.pop()
                        ProductScreen.Event.Retry -> { /* trigger retry */ }
                        ProductScreen.Event.Refresh -> { /* trigger refresh */ }
                    }
                },
            )
        }
    }
}
```
