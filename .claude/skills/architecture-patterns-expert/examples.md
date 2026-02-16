# Architecture Patterns Examples

## 1. Complete Feature Module Structure

A feature module for "Product Catalog" with `:api` and `:impl` split. This is the standard structure for any new feature.

### Directory Layout

```
features/
  product-catalog/
    api/
      build.gradle.kts
      src/commonMain/kotlin/com/example/feature/productcatalog/
        ProductCatalogScreen.kt
        ProductRepository.kt
        Product.kt
        ProductId.kt
    impl/
      build.gradle.kts
      src/commonMain/kotlin/com/example/feature/productcatalog/
        data/
          ProductRemoteDataSource.kt
          ProductLocalDataSource.kt
          ProductRepositoryImpl.kt
          dto/
            ProductDto.kt
            ProductResponseDto.kt
          entity/
            ProductEntity.kt
          mapper/
            ProductMapper.kt
        presentation/
          ProductCatalogPresenter.kt
          ProductCatalogUi.kt
          components/
            ProductCard.kt
            ProductGrid.kt
      src/commonTest/kotlin/com/example/feature/productcatalog/
        data/
          ProductRepositoryImplTest.kt
          FakeProductRemoteDataSource.kt
          FakeProductLocalDataSource.kt
        presentation/
          ProductCatalogPresenterTest.kt
```

### :api build.gradle.kts

```kotlin
plugins {
    id("com.example.kmp.feature.api")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.model)
        }
    }
}
```

### :impl build.gradle.kts

```kotlin
plugins {
    id("com.example.kmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.features.productCatalog.api)
            implementation(projects.core.network)
            implementation(projects.core.database)
            implementation(libs.store5)
        }
    }
}
```

### :api types

```kotlin
// ProductId.kt
@JvmInline
value class ProductId(val value: String)

// Product.kt
data class Product(
    val id: ProductId,
    val name: String,
    val description: String,
    val price: Double,
    val imageUrl: String,
    val category: String,
    val inStock: Boolean,
)

// ProductRepository.kt
interface ProductRepository {
    fun getProducts(): Flow<List<Product>>
    fun getProduct(id: ProductId): Flow<Product>
    suspend fun refreshProducts()
}

// ProductCatalogScreen.kt
@Parcelize
data object ProductCatalogScreen : Screen {
    data class State(
        val content: ContentState,
        val searchQuery: String,
        val eventSink: (Event) -> Unit = {},
    ) : CircuitUiState

    sealed interface ContentState {
        data object Loading : ContentState
        data class Success(val products: ImmutableList<Product>) : ContentState
        data class Error(val message: String) : ContentState
    }

    sealed interface Event : CircuitUiEvent {
        data class Search(val query: String) : Event
        data class ProductClicked(val id: ProductId) : Event
        data object Refresh : Event
        data object Retry : Event
    }
}
```

---

## 2. Repository with Store5

Full repository implementation using Store5 with Fetcher, SourceOfTruth, and proper error mapping.

```kotlin
// ProductRemoteDataSource.kt
@Inject
class ProductRemoteDataSource(private val httpClient: HttpClient) {
    suspend fun getProducts(): List<ProductDto> {
        return httpClient.get("api/v1/products").body()
    }

    suspend fun getProduct(id: String): ProductDto {
        return httpClient.get("api/v1/products/$id").body()
    }
}

// ProductLocalDataSource.kt
@Inject
class ProductLocalDataSource(private val database: AppDatabase) {
    private val queries get() = database.productQueries

    fun observeProducts(): Flow<List<ProductEntity>> =
        queries.selectAll().asFlow().mapToList(Dispatchers.IO)

    fun observeProduct(id: String): Flow<ProductEntity?> =
        queries.selectById(id).asFlow().mapToOneOrNull(Dispatchers.IO)

    suspend fun upsertProducts(products: List<ProductEntity>) {
        database.transaction {
            products.forEach { queries.upsert(it) }
        }
    }

    suspend fun upsertProduct(product: ProductEntity) {
        queries.upsert(product)
    }

    suspend fun deleteAll() {
        queries.deleteAll()
    }
}

// ProductMapper.kt
object ProductMapper {
    fun ProductDto.toDomain(): Product = Product(
        id = ProductId(id),
        name = name,
        description = description.orEmpty(),
        price = priceInCents / 100.0,
        imageUrl = imageUrl.orEmpty(),
        category = category,
        inStock = stockCount > 0,
    )

    fun ProductDto.toEntity(): ProductEntity = ProductEntity(
        id = id,
        name = name,
        description = description.orEmpty(),
        price_in_cents = priceInCents,
        image_url = imageUrl.orEmpty(),
        category = category,
        stock_count = stockCount,
    )

    fun ProductEntity.toDomain(): Product = Product(
        id = ProductId(id),
        name = name,
        description = description,
        price = price_in_cents / 100.0,
        imageUrl = image_url,
        category = category,
        inStock = stock_count > 0,
    )
}

// ProductRepositoryImpl.kt
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class ProductRepositoryImpl(
    private val store: Store<Unit, List<Product>>,
    private val singleStore: Store<ProductId, Product>,
) : ProductRepository {

    override fun getProducts(): Flow<List<Product>> =
        store.stream(StoreReadRequest.cached(Unit, refresh = false))
            .filterNot { it is StoreReadResponse.Loading || it is StoreReadResponse.NoNewData }
            .map { response ->
                when (response) {
                    is StoreReadResponse.Data -> response.value
                    is StoreReadResponse.Error -> throw response.unwrapError()
                    else -> emptyList()
                }
            }

    override fun getProduct(id: ProductId): Flow<Product> =
        singleStore.stream(StoreReadRequest.cached(id, refresh = true))
            .filterNot { it is StoreReadResponse.Loading || it is StoreReadResponse.NoNewData }
            .mapNotNull { response ->
                when (response) {
                    is StoreReadResponse.Data -> response.value
                    is StoreReadResponse.Error -> throw response.unwrapError()
                    else -> null
                }
            }

    override suspend fun refreshProducts() {
        store.fresh(Unit)
    }
}

// Metro module providing the Store instances
@ContributesTo(AppScope::class)
interface ProductDataModule {
    companion object {
        @Provides
        @SingleIn(AppScope::class)
        fun provideProductListStore(
            remote: ProductRemoteDataSource,
            local: ProductLocalDataSource,
        ): Store<Unit, List<Product>> = StoreBuilder.from(
            fetcher = Fetcher.of("fetchProducts") {
                remote.getProducts().map { it.toDomain() }
            },
            sourceOfTruth = SourceOfTruth.of(
                reader = { local.observeProducts().map { entities -> entities.map { it.toDomain() } } },
                writer = { _, products -> local.upsertProducts(products.map { it.toEntity() }) },
                deleteAll = { local.deleteAll() },
            ),
        ).build()

        @Provides
        @SingleIn(AppScope::class)
        fun provideProductDetailStore(
            remote: ProductRemoteDataSource,
            local: ProductLocalDataSource,
        ): Store<ProductId, Product> = StoreBuilder.from(
            fetcher = Fetcher.of("fetchProduct") { key ->
                remote.getProduct(key.value).toDomain()
            },
            sourceOfTruth = SourceOfTruth.of(
                reader = { key -> local.observeProduct(key.value).map { it?.toDomain() } },
                writer = { _, product -> local.upsertProduct(product.toEntity()) },
            ),
        ).build()
    }
}

// Extension to unwrap StoreReadResponse errors
private fun StoreReadResponse.Error.unwrapError(): Throwable = when (this) {
    is StoreReadResponse.Error.Exception -> error
    is StoreReadResponse.Error.Message -> RuntimeException(message)
    is StoreReadResponse.Error.Custom<*> -> RuntimeException("Unknown error: $error")
}
```

---

## 3. Use Case Pattern

Domain use case that combines data from multiple repositories to produce a composite result.

```kotlin
// Domain models (in :feature:checkout:api)
data class CartSummary(
    val items: List<CartItem>,
    val subtotal: Double,
    val discount: Double,
    val tax: Double,
    val total: Double,
    val appliedPromoCode: String?,
)

data class CartItem(
    val product: Product,
    val quantity: Int,
    val lineTotal: Double,
)

// Use case (in :feature:checkout:impl/domain/)
@Inject
class CalculateCartSummaryUseCase(
    private val cartRepository: CartRepository,
    private val productRepository: ProductRepository,
    private val promoRepository: PromoRepository,
    private val taxService: TaxService,
) {
    operator fun invoke(promoCode: String? = null): Flow<Either<AppError, CartSummary>> =
        cartRepository.getCartItems()
            .combine(productRepository.getProducts()) { cartEntries, products ->
                val productMap = products.associateBy { it.id }
                cartEntries.mapNotNull { entry ->
                    productMap[entry.productId]?.let { product ->
                        CartItem(
                            product = product,
                            quantity = entry.quantity,
                            lineTotal = product.price * entry.quantity,
                        )
                    }
                }
            }
            .map { items ->
                suspendRunCatching {
                    val subtotal = items.sumOf { it.lineTotal }
                    val discount = promoCode?.let { code ->
                        promoRepository.calculateDiscount(code, subtotal)
                    } ?: 0.0
                    val taxableAmount = subtotal - discount
                    val tax = taxService.calculateTax(taxableAmount)

                    Either.Right(
                        CartSummary(
                            items = items,
                            subtotal = subtotal,
                            discount = discount,
                            tax = tax,
                            total = taxableAmount + tax,
                            appliedPromoCode = promoCode,
                        )
                    )
                }.getOrElse { error ->
                    when (error) {
                        is PromoCodeExpiredException -> Either.Left(AppError.Business.PromoExpired(promoCode!!))
                        is PromoCodeInvalidException -> Either.Left(AppError.Business.PromoInvalid(promoCode!!))
                        else -> Either.Left(AppError.Network.Unknown(error))
                    }
                }
            }
}

// Test
class CalculateCartSummaryUseCaseTest {
    private val fakeCartRepo = FakeCartRepository()
    private val fakeProductRepo = FakeProductRepository()
    private val fakePromoRepo = FakePromoRepository()
    private val fakeTaxService = FakeTaxService(taxRate = 0.08)

    private val useCase = CalculateCartSummaryUseCase(
        cartRepository = fakeCartRepo,
        productRepository = fakeProductRepo,
        promoRepository = fakePromoRepo,
        taxService = fakeTaxService,
    )

    @Test
    fun `calculates total with discount and tax`() = runTest {
        fakeProductRepo.products = listOf(buildProduct(id = "1", price = 100.0))
        fakeCartRepo.items = listOf(CartEntry(productId = ProductId("1"), quantity = 2))
        fakePromoRepo.discountPercent = 0.10 // 10% off

        useCase(promoCode = "SAVE10").test {
            val result = awaitItem()
            assertTrue(result.isRight())
            val summary = result.getOrThrow()
            assertEquals(200.0, summary.subtotal, 0.01)
            assertEquals(20.0, summary.discount, 0.01)
            assertEquals(14.4, summary.tax, 0.01)   // (200 - 20) * 0.08
            assertEquals(194.4, summary.total, 0.01) // 180 + 14.4
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

---

## 4. Circuit Presenter with Repository

Complete Circuit presenter handling loading, success, and error states with refresh and retry.

```kotlin
@AssistedInject
class ProductCatalogPresenter(
    @Assisted private val navigator: Navigator,
    private val productRepository: ProductRepository,
) : Presenter<ProductCatalogScreen.State> {

    @CircuitInject(ProductCatalogScreen::class, AppScope::class)
    @AssistedFactory
    fun interface Factory {
        fun create(navigator: Navigator): ProductCatalogPresenter
    }

    @Composable
    override fun present(): ProductCatalogScreen.State {
        var searchQuery by rememberRetained { mutableStateOf("") }
        var refreshing by rememberRetained { mutableStateOf(false) }

        val productsResult by produceRetainedState<ContentState>(
            initialValue = ContentState.Loading,
        ) {
            productRepository.getProducts()
                .catch { error ->
                    value = ContentState.Error(
                        error.toUserMessage()
                    )
                }
                .collect { products ->
                    value = ContentState.Success(products.toImmutableList())
                    refreshing = false
                }
        }

        // Filter products based on search query
        val filteredContent = remember(productsResult, searchQuery) {
            when (val content = productsResult) {
                is ContentState.Success -> {
                    if (searchQuery.isBlank()) content
                    else ContentState.Success(
                        content.products.filter {
                            it.name.contains(searchQuery, ignoreCase = true)
                        }.toImmutableList()
                    )
                }
                else -> content
            }
        }

        return ProductCatalogScreen.State(
            content = filteredContent,
            searchQuery = searchQuery,
        ) { event ->
            when (event) {
                is ProductCatalogScreen.Event.Search -> {
                    searchQuery = event.query
                }
                is ProductCatalogScreen.Event.ProductClicked -> {
                    navigator.goTo(ProductDetailScreen(event.id))
                }
                is ProductCatalogScreen.Event.Refresh -> {
                    refreshing = true
                    // LaunchedEffect in presenter would trigger refresh
                }
                is ProductCatalogScreen.Event.Retry -> {
                    // Reset to loading, which re-triggers the produceRetainedState
                }
            }
        }
    }
}

// UI
@CircuitInject(ProductCatalogScreen::class, AppScope::class)
@Composable
fun ProductCatalogUi(state: ProductCatalogScreen.State, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            SearchTopBar(
                query = state.searchQuery,
                onQueryChange = { state.eventSink(ProductCatalogScreen.Event.Search(it)) },
            )
        },
    ) { padding ->
        when (val content = state.content) {
            is ProductCatalogScreen.ContentState.Loading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is ProductCatalogScreen.ContentState.Success -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(160.dp),
                    modifier = Modifier.padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(content.products, key = { it.id.value }) { product ->
                        ProductCard(
                            product = product,
                            onClick = {
                                state.eventSink(ProductCatalogScreen.Event.ProductClicked(product.id))
                            },
                        )
                    }
                }
            }
            is ProductCatalogScreen.ContentState.Error -> {
                ErrorScreen(
                    message = content.message,
                    onRetry = { state.eventSink(ProductCatalogScreen.Event.Retry) },
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

// Presenter test
class ProductCatalogPresenterTest {
    private val fakeRepo = FakeProductRepository()
    private val fakeNavigator = FakeNavigator(ProductCatalogScreen)

    @Test
    fun `initial load shows loading then products`() = runTest {
        fakeRepo.products = listOf(
            buildProduct(id = "1", name = "Widget"),
            buildProduct(id = "2", name = "Gadget"),
        )

        val presenter = ProductCatalogPresenter(fakeNavigator, fakeRepo)
        presenter.test {
            val loading = awaitItem()
            assertEquals(ProductCatalogScreen.ContentState.Loading, loading.content)

            val success = awaitItem()
            val content = success.content as ProductCatalogScreen.ContentState.Success
            assertEquals(2, content.products.size)
        }
    }

    @Test
    fun `clicking product navigates to detail`() = runTest {
        fakeRepo.products = listOf(buildProduct(id = "42"))

        val presenter = ProductCatalogPresenter(fakeNavigator, fakeRepo)
        presenter.test {
            awaitItem() // Loading
            val success = awaitItem()
            success.eventSink(ProductCatalogScreen.Event.ProductClicked(ProductId("42")))

            assertEquals(ProductDetailScreen(ProductId("42")), fakeNavigator.awaitNextScreen())
        }
    }

    @Test
    fun `search filters products`() = runTest {
        fakeRepo.products = listOf(
            buildProduct(id = "1", name = "Blue Widget"),
            buildProduct(id = "2", name = "Red Gadget"),
        )

        val presenter = ProductCatalogPresenter(fakeNavigator, fakeRepo)
        presenter.test {
            awaitItem() // Loading
            awaitItem() // All products

            val state = awaitItem()
            state.eventSink(ProductCatalogScreen.Event.Search("Widget"))

            val filtered = awaitItem()
            val content = filtered.content as ProductCatalogScreen.ContentState.Success
            assertEquals(1, content.products.size)
            assertEquals("Blue Widget", content.products.first().name)
        }
    }
}
```

---

## 5. ViewModel with Repository

The same product catalog feature implemented with ViewModel pattern instead of Circuit.

```kotlin
// ViewModel
class ProductCatalogViewModel(
    private val productRepository: ProductRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val searchQuery = savedStateHandle.getStateFlow("search", "")

    val uiState: StateFlow<ProductCatalogUiState> =
        combine(
            productRepository.getProducts()
                .map<List<Product>, ProductLoadState> { ProductLoadState.Success(it) }
                .onStart { emit(ProductLoadState.Loading) }
                .catch { emit(ProductLoadState.Error(it.toUserMessage())) },
            searchQuery,
        ) { loadState, query ->
            when (loadState) {
                is ProductLoadState.Loading -> ProductCatalogUiState(
                    content = ProductCatalogUiState.Content.Loading,
                    searchQuery = query,
                )
                is ProductLoadState.Error -> ProductCatalogUiState(
                    content = ProductCatalogUiState.Content.Error(loadState.message),
                    searchQuery = query,
                )
                is ProductLoadState.Success -> {
                    val filtered = if (query.isBlank()) loadState.products
                        else loadState.products.filter {
                            it.name.contains(query, ignoreCase = true)
                        }
                    ProductCatalogUiState(
                        content = ProductCatalogUiState.Content.Success(filtered.toImmutableList()),
                        searchQuery = query,
                    )
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ProductCatalogUiState(
                content = ProductCatalogUiState.Content.Loading,
                searchQuery = "",
            ),
        )

    fun onSearch(query: String) {
        savedStateHandle["search"] = query
    }

    fun onRefresh() {
        viewModelScope.launch {
            productRepository.refreshProducts()
        }
    }
}

// UI State (standalone data class, no eventSink)
data class ProductCatalogUiState(
    val content: Content,
    val searchQuery: String,
) {
    sealed interface Content {
        data object Loading : Content
        data class Success(val products: ImmutableList<Product>) : Content
        data class Error(val message: String) : Content
    }
}

private sealed interface ProductLoadState {
    data object Loading : ProductLoadState
    data class Success(val products: List<Product>) : ProductLoadState
    data class Error(val message: String) : ProductLoadState
}

// Compose screen
@Composable
fun ProductCatalogScreen(
    viewModel: ProductCatalogViewModel = viewModel(),
    onProductClick: (ProductId) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            SearchTopBar(
                query = state.searchQuery,
                onQueryChange = viewModel::onSearch,
            )
        },
    ) { padding ->
        when (val content = state.content) {
            is ProductCatalogUiState.Content.Loading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is ProductCatalogUiState.Content.Success -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(160.dp),
                    modifier = Modifier.padding(padding),
                ) {
                    items(content.products, key = { it.id.value }) { product ->
                        ProductCard(product = product, onClick = { onProductClick(product.id) })
                    }
                }
            }
            is ProductCatalogUiState.Content.Error -> {
                ErrorScreen(
                    message = content.message,
                    onRetry = viewModel::onRefresh,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

// ViewModel test
class ProductCatalogViewModelTest {
    private val fakeRepo = FakeProductRepository()

    @Test
    fun `initial load emits loading then success`() = runTest {
        fakeRepo.products = listOf(buildProduct(id = "1", name = "Widget"))
        val viewModel = ProductCatalogViewModel(fakeRepo, SavedStateHandle())

        viewModel.uiState.test {
            val loading = awaitItem()
            assertIs<ProductCatalogUiState.Content.Loading>(loading.content)

            val success = awaitItem()
            val content = assertIs<ProductCatalogUiState.Content.Success>(success.content)
            assertEquals(1, content.products.size)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `search filters products`() = runTest {
        fakeRepo.products = listOf(
            buildProduct(id = "1", name = "Blue Widget"),
            buildProduct(id = "2", name = "Red Gadget"),
        )
        val viewModel = ProductCatalogViewModel(fakeRepo, SavedStateHandle())

        viewModel.uiState.test {
            awaitItem() // Loading
            awaitItem() // All products

            viewModel.onSearch("Gadget")

            val filtered = awaitItem()
            val content = assertIs<ProductCatalogUiState.Content.Success>(filtered.content)
            assertEquals(1, content.products.size)
            assertEquals("Red Gadget", content.products.first().name)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

---

## 6. Data Layer: DTO to Domain Mapping

Network DTO, database entity, and domain model with bidirectional mappers. Shows the full data transformation pipeline.

```kotlin
// ---- Network DTO (matches API JSON contract) ----
@Serializable
data class ProductDto(
    val id: String,
    val name: String,
    val description: String? = null,
    @SerialName("price_cents")
    val priceInCents: Long,
    @SerialName("image_url")
    val imageUrl: String? = null,
    val category: String,
    @SerialName("stock_count")
    val stockCount: Int,
    @SerialName("created_at")
    val createdAt: String, // ISO 8601
    val tags: List<String> = emptyList(),
    @SerialName("_links")
    val links: LinksDto? = null, // API-specific, not exposed to domain
)

@Serializable
data class LinksDto(
    val self: String,
    val reviews: String? = null,
)

// ---- Database Entity (matches SQLDelight schema) ----
// Generated by SQLDelight from .sq file:
// CREATE TABLE product (
//   id TEXT NOT NULL PRIMARY KEY,
//   name TEXT NOT NULL,
//   description TEXT NOT NULL DEFAULT '',
//   price_in_cents INTEGER NOT NULL,
//   image_url TEXT NOT NULL DEFAULT '',
//   category TEXT NOT NULL,
//   stock_count INTEGER NOT NULL DEFAULT 0,
//   created_at TEXT NOT NULL,
//   tags TEXT NOT NULL DEFAULT '[]'
// );

// ---- Domain Model (pure business type) ----
data class Product(
    val id: ProductId,
    val name: String,
    val description: String,
    val price: Double,
    val imageUrl: String,
    val category: String,
    val inStock: Boolean,
    val createdAt: Instant,
    val tags: List<String>,
)

// ---- Mappers ----
object ProductMapper {

    // Network -> Domain
    fun ProductDto.toDomain(): Product = Product(
        id = ProductId(id),
        name = name,
        description = description.orEmpty(),
        price = priceInCents / 100.0,
        imageUrl = imageUrl.orEmpty(),
        category = category,
        inStock = stockCount > 0,
        createdAt = Instant.parse(createdAt),
        tags = tags,
    )

    // Network -> Database
    fun ProductDto.toEntity(): ProductEntity = ProductEntity(
        id = id,
        name = name,
        description = description.orEmpty(),
        price_in_cents = priceInCents,
        image_url = imageUrl.orEmpty(),
        category = category,
        stock_count = stockCount.toLong(),
        created_at = createdAt,
        tags = Json.encodeToString(tags),
    )

    // Database -> Domain
    fun ProductEntity.toDomain(): Product = Product(
        id = ProductId(id),
        name = name,
        description = description,
        price = price_in_cents / 100.0,
        imageUrl = image_url,
        category = category,
        inStock = stock_count > 0,
        createdAt = Instant.parse(created_at),
        tags = Json.decodeFromString(tags),
    )

    // Domain -> Database (for local-first writes)
    fun Product.toEntity(): ProductEntity = ProductEntity(
        id = id.value,
        name = name,
        description = description,
        price_in_cents = (price * 100).toLong(),
        image_url = imageUrl,
        category = category,
        stock_count = if (inStock) 1 else 0,
        created_at = createdAt.toString(),
        tags = Json.encodeToString(tags),
    )
}

// ---- Mapper Tests ----
class ProductMapperTest {
    @Test
    fun `DTO to domain maps price from cents to dollars`() {
        val dto = ProductDto(
            id = "1",
            name = "Widget",
            priceInCents = 1999,
            category = "Tools",
            stockCount = 5,
            createdAt = "2025-01-15T10:00:00Z",
        )

        val domain = dto.toDomain()

        assertEquals(19.99, domain.price, 0.001)
        assertTrue(domain.inStock)
        assertEquals(ProductId("1"), domain.id)
    }

    @Test
    fun `DTO to domain handles null optional fields`() {
        val dto = ProductDto(
            id = "2",
            name = "Gadget",
            description = null,
            priceInCents = 500,
            imageUrl = null,
            category = "Electronics",
            stockCount = 0,
            createdAt = "2025-06-01T12:00:00Z",
        )

        val domain = dto.toDomain()

        assertEquals("", domain.description)
        assertEquals("", domain.imageUrl)
        assertFalse(domain.inStock)
    }

    @Test
    fun `round-trip DTO to entity to domain preserves data`() {
        val dto = ProductDto(
            id = "3",
            name = "Doohickey",
            description = "A fine doohickey",
            priceInCents = 4250,
            imageUrl = "https://img.example.com/3.jpg",
            category = "Misc",
            stockCount = 12,
            createdAt = "2025-03-20T08:30:00Z",
            tags = listOf("sale", "new"),
        )

        val entity = dto.toEntity()
        val domain = entity.toDomain()

        assertEquals(dto.id, domain.id.value)
        assertEquals(dto.name, domain.name)
        assertEquals(42.50, domain.price, 0.001)
        assertEquals(dto.tags, domain.tags)
    }
}
```

---

## 7. expect/actual vs Interface+DI

The same problem -- reading secure storage -- solved both ways, showing the trade-offs.

### Approach A: expect/actual

```kotlin
// commonMain
expect class SecureStorage {
    suspend fun getString(key: String): String?
    suspend fun putString(key: String, value: String)
    suspend fun remove(key: String)
}

// androidMain
actual class SecureStorage(private val context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    actual suspend fun getString(key: String): String? =
        withContext(Dispatchers.IO) { prefs.getString(key, null) }

    actual suspend fun putString(key: String, value: String) =
        withContext(Dispatchers.IO) { prefs.edit().putString(key, value).apply() }

    actual suspend fun remove(key: String) =
        withContext(Dispatchers.IO) { prefs.edit().remove(key).apply() }
}

// iosMain
actual class SecureStorage {
    actual suspend fun getString(key: String): String? {
        val query = keychainQuery(key)
        // ... Keychain read implementation
        return result
    }

    actual suspend fun putString(key: String, value: String) {
        // ... Keychain write implementation
    }

    actual suspend fun remove(key: String) {
        // ... Keychain delete implementation
    }
}

// Usage in presenter -- HARD TO TEST because SecureStorage is a concrete class
class AuthPresenter(private val secureStorage: SecureStorage) {
    // Testing requires providing actual platform SecureStorage
    // Cannot substitute a fake without a wrapper interface
}
```

**Pros**: Simple, no extra interface, direct platform access.
**Cons**: Concrete class in commonMain. Tests in commonTest cannot provide a fake without creating an intermediate interface anyway. Constructor varies by platform (Android needs `Context`).

### Approach B: Interface + DI (Recommended)

```kotlin
// commonMain -- pure interface
interface SecureStorage {
    suspend fun getString(key: String): String?
    suspend fun putString(key: String, value: String)
    suspend fun remove(key: String)
}

// androidMain -- Metro binds it automatically
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AndroidSecureStorage(
    private val context: Context,
) : SecureStorage {
    private val prefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            "secure_prefs",
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override suspend fun getString(key: String): String? =
        withContext(Dispatchers.IO) { prefs.getString(key, null) }

    override suspend fun putString(key: String, value: String) =
        withContext(Dispatchers.IO) { prefs.edit().putString(key, value).apply() }

    override suspend fun remove(key: String) =
        withContext(Dispatchers.IO) { prefs.edit().remove(key).apply() }
}

// iosMain
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosSecureStorage : SecureStorage {
    override suspend fun getString(key: String): String? {
        // Keychain read
        return result
    }

    override suspend fun putString(key: String, value: String) {
        // Keychain write
    }

    override suspend fun remove(key: String) {
        // Keychain delete
    }
}

// commonTest -- trivial fake
class FakeSecureStorage : SecureStorage {
    private val store = mutableMapOf<String, String>()

    override suspend fun getString(key: String): String? = store[key]
    override suspend fun putString(key: String, value: String) { store[key] = value }
    override suspend fun remove(key: String) { store.remove(key) }
}

// Usage in presenter -- EASY TO TEST
class AuthPresenter(private val secureStorage: SecureStorage) {
    // In tests, just pass FakeSecureStorage()
}
```

**Pros**: Interface in commonMain. Trivially testable in commonTest with fakes. Metro auto-wires the correct platform binding. Constructor differences are hidden by DI.
**Cons**: Slightly more boilerplate (interface + two implementations + fake).

### Decision Rule

| Use expect/actual when... | Use Interface + DI when... |
|--------------------------|---------------------------|
| Simple value/function with no dependencies | Class with injected dependencies |
| No need to fake in tests | Needs to be faked for testing |
| Platform API is identical (just different imports) | Platform APIs differ significantly |
| Top-level functions or simple factories | Full service classes |
| Examples: `platformName()`, `Parcelable` | Examples: `SecureStorage`, `FileSystem`, `Analytics` |

---

## 8. Multi-Module Wiring with Metro

How the app-level DependencyGraph pulls in feature modules via `@ContributesTo`. This is the only place all `:impl` modules converge.

```kotlin
// ---- Scope Definitions (in :core:di) ----
abstract class AppScope private constructor()
abstract class UserScope private constructor()

// ---- App-level Graph (in :app module) ----
@DependencyGraph(AppScope::class)
interface AppGraph {
    val circuit: Circuit

    fun inject(app: MyApplication)

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Application,
            @Provides @Named("baseUrl") baseUrl: String,
        ): AppGraph
    }
}

// ---- Circuit Wiring Module (in :core:circuit-wiring) ----
@ContributesTo(AppScope::class)
interface CircuitModule {
    @Multibinds fun presenterFactories(): Set<Presenter.Factory>
    @Multibinds fun uiFactories(): Set<Ui.Factory>

    companion object {
        @Provides
        @SingleIn(AppScope::class)
        fun provideCircuit(
            presenterFactories: Set<Presenter.Factory>,
            uiFactories: Set<Ui.Factory>,
        ): Circuit = Circuit.Builder()
            .addPresenterFactories(presenterFactories)
            .addUiFactories(uiFactories)
            .build()
    }
}

// ---- Network Module (in :core:network) ----
@ContributesTo(AppScope::class)
interface NetworkModule {
    companion object {
        @Provides
        @SingleIn(AppScope::class)
        fun provideHttpClient(
            @Named("baseUrl") baseUrl: String,
            json: Json,
        ): HttpClient = HttpClient {
            install(ContentNegotiation) { json(json) }
            install(Logging) { level = LogLevel.HEADERS }
            defaultRequest { url(baseUrl) }
        }

        @Provides
        @SingleIn(AppScope::class)
        fun provideJson(): Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}

// ---- Database Module (in :core:database) ----
@ContributesTo(AppScope::class)
interface DatabaseModule {
    companion object {
        @Provides
        @SingleIn(AppScope::class)
        fun provideDatabase(driver: SqlDriver): AppDatabase =
            AppDatabase(driver)
    }
}

// ---- Feature: Product Catalog Data Module (in :feature:product-catalog:impl) ----
@ContributesTo(AppScope::class)
interface ProductCatalogDataModule {
    companion object {
        @Provides
        @SingleIn(AppScope::class)
        fun provideProductListStore(
            remote: ProductRemoteDataSource,
            local: ProductLocalDataSource,
        ): Store<Unit, List<Product>> = StoreBuilder.from(
            fetcher = Fetcher.of("fetchProducts") {
                remote.getProducts().map { it.toDomain() }
            },
            sourceOfTruth = SourceOfTruth.of(
                reader = { local.observeProducts().map { list -> list.map { it.toDomain() } } },
                writer = { _, products -> local.upsertProducts(products.map { it.toEntity() }) },
            ),
        ).build()
    }
}

// Note: ProductRepositoryImpl uses @ContributesBinding, so it's auto-wired:
// @Inject @SingleIn(AppScope::class) @ContributesBinding(AppScope::class)
// class ProductRepositoryImpl(...) : ProductRepository

// Note: ProductCatalogPresenter uses @CircuitInject, which generates
// @ContributesIntoSet for Presenter.Factory -- auto-wired into Circuit.

// ---- Feature: Auth Module (in :feature:auth:impl) ----
@ContributesTo(AppScope::class)
interface AuthDataModule {
    companion object {
        @Provides
        @SingleIn(AppScope::class)
        fun provideAuthApi(httpClient: HttpClient): AuthApi =
            AuthApiImpl(httpClient)
    }
}

// ---- Feature: Settings Module (in :feature:settings:impl) ----
// Using only @ContributesBinding and @CircuitInject -- no explicit module needed.
// Metro auto-discovers all @ContributesBinding and @ContributesIntoSet bindings.

// ---- App Module build.gradle.kts ----
// This is where ALL :impl modules converge
plugins {
    id("com.example.android.app")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Core
            implementation(projects.core.model)
            implementation(projects.core.network)
            implementation(projects.core.database)
            implementation(projects.core.circuitWiring)

            // Features -- ONLY :impl modules here
            implementation(projects.features.productCatalog.impl)
            implementation(projects.features.auth.impl)
            implementation(projects.features.settings.impl)
            implementation(projects.features.checkout.impl)
        }
    }
}

// ---- Application class (Android) ----
class MyApplication : Application() {
    lateinit var appGraph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        appGraph = createGraphFactory<AppGraph.Factory>().create(
            application = this,
            baseUrl = BuildConfig.API_BASE_URL,
        )
        appGraph.inject(this)
    }
}

// ---- Activity ----
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val circuit = (application as MyApplication).appGraph.circuit

        setContent {
            AppTheme {
                CircuitCompositionLocals(circuit) {
                    ContentWithOverlays {
                        val backStack = rememberSaveableBackStack(ProductCatalogScreen)
                        val navigator = rememberCircuitNavigator(backStack)
                        NavigableCircuitContent(
                            navigator = navigator,
                            backStack = backStack,
                            decoratorFactory = GestureNavigationDecorationFactory(
                                onBackInvoked = navigator::pop,
                            ),
                        )
                    }
                }
            }
        }
    }
}
```

### How It All Connects

```
AppGraph (AppScope)
  |
  +-- CircuitModule         (@ContributesTo)  -> provides Circuit
  |     |
  |     +-- presenterFactories (Set<Presenter.Factory>)
  |     |     +-- ProductCatalogPresenter.Factory  (@CircuitInject -> @ContributesIntoSet)
  |     |     +-- AuthPresenter.Factory            (@CircuitInject -> @ContributesIntoSet)
  |     |     +-- SettingsPresenter.Factory         (@CircuitInject -> @ContributesIntoSet)
  |     |
  |     +-- uiFactories (Set<Ui.Factory>)
  |           +-- ProductCatalogUi.Factory          (@CircuitInject -> @ContributesIntoSet)
  |           +-- AuthUi.Factory                    (@CircuitInject -> @ContributesIntoSet)
  |           +-- SettingsUi.Factory                (@CircuitInject -> @ContributesIntoSet)
  |
  +-- NetworkModule         (@ContributesTo)  -> provides HttpClient, Json
  +-- DatabaseModule        (@ContributesTo)  -> provides AppDatabase
  +-- ProductCatalogDataModule (@ContributesTo) -> provides Store
  +-- AuthDataModule        (@ContributesTo)  -> provides AuthApi
  |
  +-- Auto-discovered bindings:
        +-- ProductRepositoryImpl  (@ContributesBinding) -> binds ProductRepository
        +-- AuthRepositoryImpl     (@ContributesBinding) -> binds AuthRepository
        +-- SettingsRepositoryImpl (@ContributesBinding) -> binds SettingsRepository
        +-- AndroidSecureStorage   (@ContributesBinding) -> binds SecureStorage
```

Metro discovers all `@ContributesTo`, `@ContributesBinding`, and `@ContributesIntoSet` annotations at compile time across all modules included in the `:app` dependency graph. No manual registration or module lists required.
