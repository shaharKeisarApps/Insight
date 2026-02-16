# Architecture Patterns API Reference

## Architectural Decision Matrix

Use this matrix to select the right pattern for each feature. Score each row for your feature, then follow the recommended pattern combination.

### Presentation Pattern Selection

| Factor | Circuit MVI | ViewModel + Nav3 |
|--------|-------------|------------------|
| Team familiarity with Compose Runtime | Required | Not required |
| Presenter is `@Composable` | Yes | No (plain class) |
| State retention mechanism | `rememberRetained` (Circuit) | `SavedStateHandle` / ViewModel scope |
| Navigation | Circuit Navigator + BackStack | Nav3 NavController |
| Testing approach | `Presenter.test {}` (Molecule) | `runTest` + Turbine |
| Overlay support | Built-in `OverlayHost` | Manual dialog/sheet management |
| Shared element transitions | `SharedElementTransitionLayout` | Manual implementation |
| Lifecycle awareness | Compose lifecycle | Android Lifecycle + `collectAsStateWithLifecycle` |
| Recommended when | New KMP projects, Slack/CatchUp style | Teams migrating from MVVM, heavy Android Lifecycle usage |

### Data Pattern Selection

| Factor | Store5 | Plain Repository |
|--------|--------|------------------|
| Offline support needed | Yes (built-in) | Manual implementation |
| Caching strategy | Automatic (memory + SoT) | Manual |
| Multiple data sources | Fetcher + SourceOfTruth | Custom orchestration |
| Real-time updates | Flow-based Fetcher | Manual WebSocket handling |
| Write operations | `MutableStore` | Direct repository methods |
| Conflict resolution | Built-in `Converter` | Manual |
| Recommended when | Offline-first, cache-heavy features | Simple one-shot API calls, no caching |

### Domain Layer Decision

| Condition | Include Domain Layer | Skip Domain Layer |
|-----------|---------------------|-------------------|
| Multiple repositories combined | Yes | -- |
| Complex business rules | Yes | -- |
| Reusable across presenters | Yes | -- |
| Simple CRUD / pass-through | -- | Yes |
| Single repository, no transformation | -- | Yes |
| Only data mapping needed | -- | Yes (map in repository) |

## Layer Responsibilities

### Data Layer

| Component | Responsibility | Depends On | Tested With |
|-----------|---------------|------------|-------------|
| `RemoteDataSource` | HTTP calls via Ktor, returns DTOs | Ktor `HttpClient` | Ktor `MockEngine` |
| `LocalDataSource` | Database CRUD via SQLDelight/Room, returns entities | SQLDelight `Database` | In-memory driver |
| `Repository` | Orchestrates remote + local, maps to domain models | DataSource interfaces | Fake DataSources |
| `Store` (Store5) | Fetcher + SourceOfTruth pipeline | API + DAO | `TestStoreBuilder` |
| `Mapper` | DTO/Entity to/from Domain model conversion | Pure functions | Unit tests |

### Domain Layer (When Present)

| Component | Responsibility | Depends On | Tested With |
|-----------|---------------|------------|-------------|
| `UseCase` / `Interactor` | Single business operation | Repository interfaces | Fake Repositories |
| `DomainModel` | Business entity (no framework deps) | Nothing | N/A (data class) |
| `DomainError` | Typed error hierarchy | Nothing | N/A (sealed class) |
| `Validator` | Input validation rules | Pure functions | Unit tests |

### Presentation Layer

| Component | Responsibility | Depends On | Tested With |
|-----------|---------------|------------|-------------|
| `Screen` (Circuit) | Route + State + Event definitions | `circuit-runtime` | N/A (type definitions) |
| `Presenter` (Circuit) | Business logic, state production | Repository / UseCase | `Presenter.test {}` |
| `ViewModel` (Lifecycle) | State management, coroutine scope | Repository / UseCase | `runTest` + Turbine |
| `Ui` (Compose) | Rendering, event dispatch | State type only | Compose `TestRule` |
| `Mapper` (UI) | Domain model to display model | Pure functions | Unit tests |

## Dependency Rules Matrix

This table defines which modules can depend on which. Read as "row depends on column".

| Module | `:core:model` | `:core:network` | `:core:database` | `:feature:X:api` | `:feature:X:impl` | `:feature:Y:api` | `:feature:Y:impl` | `:app` |
|--------|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| `:core:model` | -- | No | No | No | No | No | No | No |
| `:core:network` | Yes | -- | No | No | No | No | No | No |
| `:core:database` | Yes | No | -- | No | No | No | No | No |
| `:feature:X:api` | Yes | No | No | -- | No | Maybe | No | No |
| `:feature:X:impl` | Yes | Yes | Yes | Yes | -- | Maybe | **No** | No |
| `:app` | Yes | Yes | Yes | Yes | Yes | Yes | Yes | -- |

Key constraints:
- `:feature:X:impl` **NEVER** depends on `:feature:Y:impl`
- `:feature:X:api` may depend on `:feature:Y:api` for shared types (e.g., `UserId`)
- Only `:app` sees all `:impl` modules (for Metro DI wiring)

## Module Structure Templates

### Minimal Feature (No Domain Layer)

```
features/
  product/
    api/
      build.gradle.kts
      src/commonMain/kotlin/com/example/feature/product/
        ProductRepository.kt        // interface
        ProductScreen.kt            // Screen + State + Event
        Product.kt                  // domain model
    impl/
      build.gradle.kts
      src/commonMain/kotlin/com/example/feature/product/
        ProductRepositoryImpl.kt    // @ContributesBinding
        ProductPresenter.kt         // @CircuitInject
        ProductUi.kt                // @CircuitInject
        data/
          ProductApi.kt             // Ktor API interface
          ProductDao.kt             // SQLDelight DAO
          ProductDto.kt             // Network DTO
          ProductEntity.kt          // Database entity
          ProductMapper.kt          // DTO/Entity <-> Domain
      src/commonTest/kotlin/com/example/feature/product/
        ProductPresenterTest.kt
        ProductRepositoryTest.kt
        FakeProductApi.kt
        FakeProductDao.kt
```

### Full Feature (With Domain Layer)

```
features/
  checkout/
    api/
      build.gradle.kts
      src/commonMain/kotlin/com/example/feature/checkout/
        CheckoutRepository.kt
        CheckoutScreen.kt
        Cart.kt
        CartItem.kt
        CheckoutError.kt           // sealed interface
    impl/
      build.gradle.kts
      src/commonMain/kotlin/com/example/feature/checkout/
        domain/
          CalculateTotalUseCase.kt
          ValidateCartUseCase.kt
          ApplyDiscountUseCase.kt
        data/
          CheckoutRepositoryImpl.kt
          CheckoutApi.kt
          CheckoutDao.kt
          CheckoutDto.kt
          CheckoutMapper.kt
        presentation/
          CheckoutPresenter.kt
          CheckoutUi.kt
          CartSummaryUi.kt
      src/commonTest/kotlin/com/example/feature/checkout/
        domain/
          CalculateTotalUseCaseTest.kt
          ValidateCartUseCaseTest.kt
        data/
          CheckoutRepositoryImplTest.kt
          FakeCheckoutApi.kt
          FakeCheckoutDao.kt
        presentation/
          CheckoutPresenterTest.kt
```

## Data Flow Diagrams

### Circuit MVI Flow

```
                    +-----------+
                    |   User    |
                    +-----+-----+
                          |
                    click / gesture
                          |
                          v
                    +-----------+
                    |    UI     |  @Composable fun Content(state, modifier)
                    +-----+-----+
                          |
               state.eventSink(Event.X)
                          |
                          v
                  +---------------+
                  |   Presenter   |  @Composable fun present(): State
                  +-------+-------+
                      /       \
                     /         \
           LaunchedEffect    rememberRetained
                  |               |
                  v               v
          +---------------+  +---------+
          |  Repository   |  | Local   |
          |  (Use Case)   |  | State   |
          +-------+-------+  +---------+
                  |
            Flow<Data>
                  |
                  v
          +---------------+
          |   new State   | --> UI re-renders
          +---------------+
```

### ViewModel + Nav3 Flow

```
                    +-----------+
                    |   User    |
                    +-----+-----+
                          |
                    click / gesture
                          |
                          v
                    +-----------+
                    |    UI     |  @Composable fun Screen(viewModel)
                    +-----+-----+
                          |
               viewModel.onEvent(Event.X)
                          |
                          v
                  +---------------+
                  |  ViewModel    |  class MyViewModel : ViewModel()
                  +-------+-------+
                          |
                    viewModelScope.launch
                          |
                          v
                  +---------------+
                  |  Repository   |
                  +-------+-------+
                          |
                   _uiState.update { }
                          |
                          v
                  +---------------+
                  |  StateFlow    | --> collectAsStateWithLifecycle -> UI
                  +---------------+
```

### Store5 Data Pipeline

```
          store.stream(StoreReadRequest.cached(key, refresh = true))
                          |
                          v
               +---------------------+
               |    Memory Cache     |  (hit? -> emit Data immediately)
               +----------+----------+
                          |
                     cache miss
                          |
                          v
               +---------------------+
               |  Source of Truth     |  (SQLDelight / Room)
               |  reader Flow<T?>    |  (emit cached data if present)
               +----------+----------+
                          |
                    null / stale
                          |
                          v
               +---------------------+
               |      Fetcher        |  (Ktor HTTP call)
               |  suspend (Key) -> T |
               +----------+----------+
                          |
                   network response
                          |
                          v
               +---------------------+
               |  Source of Truth     |  (writer: save to DB)
               |  writer(Key, T)     |
               +----------+----------+
                          |
                  DB Flow re-emits
                          |
                          v
               +---------------------+
               |  StoreReadResponse  |  Loading -> Data -> NoNewData
               +---------------------+
```

## Error Propagation Rules

```
Layer               Error Type              Action
-----               ----------              ------
Network (Ktor)      HttpException           Catch in RemoteDataSource
                    IOException             Map to AppError.Network.*
                    Timeout                 Map to AppError.Network.Timeout
                                            NEVER let raw exceptions escape

Database            SQLException            Catch in LocalDataSource
                    CorruptionException     Map to AppError.Data.*
                                            Log + report to analytics

Repository          (receives AppError)     Combine network + DB errors
                                            Return Either<AppError, T> or
                                            emit to Flow and let Store5 handle

Use Case            AppError                Add validation errors
                    ValidationError         Combine from multiple repos
                                            Return Either<AppError, T>

Presenter           AppError                Map to State.Error(appError)
                                            Provide retry callback
                                            NEVER show raw error messages

UI                  State.Error             Render user-friendly message
                                            Show retry button
                                            NEVER catch exceptions
```

## State Modeling Patterns

### Sealed Content State (Recommended)

```kotlin
sealed interface ContentState<out T> {
    data object Loading : ContentState<Nothing>
    data class Success<T>(val data: T) : ContentState<T>
    data class Error(val error: AppError, val retry: () -> Unit) : ContentState<Nothing>
}

data class ProductListState(
    val content: ContentState<ImmutableList<Product>>,
    val searchQuery: String,
    val eventSink: (ProductListEvent) -> Unit,
) : CircuitUiState
```

### Multiple Independent Sections

When a screen has multiple independent data sections, model each separately:

```kotlin
data class DashboardState(
    val profile: ContentState<UserProfile>,
    val recentOrders: ContentState<ImmutableList<Order>>,
    val recommendations: ContentState<ImmutableList<Product>>,
    val eventSink: (DashboardEvent) -> Unit,
) : CircuitUiState
```

### Derived State

Compute derived values as properties on the state, not in the UI:

```kotlin
data class CartState(
    val items: ImmutableList<CartItem>,
    val eventSink: (CartEvent) -> Unit,
) : CircuitUiState {
    val totalPrice: String get() = items.sumOf { it.price * it.quantity }.formatCurrency()
    val isEmpty: Boolean get() = items.isEmpty()
    val itemCount: Int get() = items.sumOf { it.quantity }
}
```

## Convention Plugin IDs

| Plugin ID | Applies To | Includes |
|-----------|-----------|----------|
| `com.example.kmp.library` | All shared KMP modules | Kotlin Multiplatform, Android library, Detekt, testing deps |
| `com.example.kmp.feature` | Feature `:impl` modules | Above + Compose, Circuit codegen, Metro |
| `com.example.kmp.feature.api` | Feature `:api` modules | `kmp.library` + core:model dependency |
| `com.example.android.app` | Root Android app | Android application, Metro graph, all `:impl` deps |

## Checklist: New Feature Setup

1. [ ] Create `:feature:name:api` module with convention plugin `kmp.feature.api`
2. [ ] Define `Screen` object with `State`, `Event` types in `:api`
3. [ ] Define repository `interface` in `:api`
4. [ ] Define domain model `data class` in `:api`
5. [ ] Create `:feature:name:impl` module with convention plugin `kmp.feature`
6. [ ] Implement `RepositoryImpl` with `@ContributesBinding`
7. [ ] Implement `Presenter` with `@CircuitInject`
8. [ ] Implement `Ui` composable with `@CircuitInject`
9. [ ] Add `:feature:name:impl` dependency to `:app` module
10. [ ] Write `PresenterTest` in `commonTest` with fake repository
11. [ ] Write `RepositoryTest` in `commonTest` with fake data sources
12. [ ] Verify build: `./gradlew :feature:name:impl:allTests`
