---
name: architecture-patterns-expert
description: Enterprise KMP architecture patterns for scalable, testable applications. Use for starting new features, restructuring existing code, making architecture decisions across data/domain/presentation layers, modularization, and dependency rules.
---

# Architecture Patterns Expert Skill

## Overview

Enterprise KMP architecture patterns for building scalable, testable, and maintainable applications. This skill covers the full application stack from the data layer through the optional domain layer to the presentation layer, including modularization strategies, dependency rules, KMP-specific patterns, error handling, state management, and testing at every layer.

## When to use

- **Starting new features**: Deciding on layer structure, module boundaries, and pattern selection.
- **Restructuring existing code**: Migrating from monolithic modules to clean architecture.
- **Architecture decisions**: Choosing between Circuit MVI vs ViewModel, domain layer vs direct repository access, expect/actual vs interface+DI.
- **Code review**: Verifying that dependency rules are not violated and layers are properly separated.
- **Onboarding**: Understanding how data flows through the application stack.

## Quick Reference

For decision matrices, layer responsibilities, and module templates, see [reference.md](reference.md).
For complete production code examples, see [examples.md](examples.md).

## Data Layer

### Repository Pattern

The Repository is the single source of truth for a domain concept. It hides data source details (network, database, cache) behind a clean interface that exposes domain models.

```kotlin
// Interface in :feature:xxx:api module
interface ProductRepository {
    fun getProducts(): Flow<List<Product>>
    fun getProduct(id: ProductId): Flow<Product>
    suspend fun refreshProducts()
}

// Implementation in :feature:xxx:impl module
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class ProductRepositoryImpl(
    private val remoteDataSource: ProductRemoteDataSource,
    private val localDataSource: ProductLocalDataSource,
) : ProductRepository {
    override fun getProducts(): Flow<List<Product>> =
        localDataSource.observeProducts().map { entities ->
            entities.map { it.toDomainModel() }
        }
}
```

### Store5 for Offline-First

Store5 is the preferred pattern for features that need caching and offline support. It combines a Fetcher (network), SourceOfTruth (database), and a built-in memory cache into a single reactive pipeline.

```kotlin
@Provides
@SingleIn(AppScope::class)
fun provideProductStore(
    api: ProductApi,
    dao: ProductDao,
): Store<ProductId, Product> = StoreBuilder.from(
    fetcher = Fetcher.of("fetchProduct") { key -> api.getProduct(key.value).toDomain() },
    sourceOfTruth = SourceOfTruth.of(
        reader = { key -> dao.observeProduct(key.value).map { it?.toDomain() } },
        writer = { _, product -> dao.upsert(product.toEntity()) },
        delete = { key -> dao.delete(key.value) },
        deleteAll = { dao.deleteAll() },
    ),
).build()
```

See [store5-expert](../store5-expert/SKILL.md) for the full Store5 API.

### Data Source Abstraction

Separate network and local data operations into dedicated classes. This makes each data source independently testable and swappable.

| Data Source | Backed By | Responsibility |
|-------------|-----------|----------------|
| `RemoteDataSource` | Ktor | HTTP calls, returns DTOs |
| `LocalDataSource` | SQLDelight / Room | CRUD operations, returns entities |
| `CacheDataSource` | In-memory / DataStore | Lightweight key-value caching |

### DTOs vs Domain Models

Network models (DTOs) and domain models are separate types. DTOs mirror the API contract. Domain models represent business concepts. Mapping happens at the repository boundary.

```
API response (JSON) -> DTO (data class) -> Repository.map() -> Domain Model -> Presenter/ViewModel
```

Never expose DTOs above the repository layer. Never let domain models leak network-specific fields (e.g., `_links`, pagination cursors).

## Domain Layer (Optional)

### Use Cases / Interactors

A Use Case is a single-responsibility class that encapsulates one piece of business logic. It consumes one or more repositories and produces a domain result.

```kotlin
class GetProductWithReviewsUseCase(
    private val productRepo: ProductRepository,
    private val reviewRepo: ReviewRepository,
) {
    operator fun invoke(id: ProductId): Flow<ProductWithReviews> =
        combine(
            productRepo.getProduct(id),
            reviewRepo.getReviews(id),
        ) { product, reviews ->
            ProductWithReviews(product, reviews)
        }
}
```

### When to Use a Domain Layer

| Condition | Use Domain Layer? |
|-----------|-------------------|
| Cross-repository logic (combine multiple data sources) | Yes |
| Complex business rules (validation, calculations, transformations) | Yes |
| Reusable operations (same logic from multiple presenters) | Yes |
| Simple CRUD (single repository, pass-through) | No -- go repository to presenter directly |
| Thin data mapping only | No -- put mappers in the repository |

### When to Skip

For simple features where the presenter reads from a single repository and maps to UI state, the domain layer adds ceremony without value. The dependency rule still applies: the presenter depends on the repository interface, never on the data layer implementation.

## Presentation Layer

### Circuit MVI (Recommended Default)

Circuit enforces Unidirectional Data Flow: the Presenter produces an immutable State, the UI renders it and sends Events back to the Presenter.

```
User action -> Event -> Presenter -> new State -> UI re-renders
```

See [circuit-expert](../circuit-expert/SKILL.md) for the full Circuit pattern.

### ViewModel + Nav3 (Alternative)

For teams migrating from Android ViewModel patterns, the Lifecycle ViewModel with Nav3 navigation provides a familiar approach.

```
User action -> ViewModel function -> StateFlow update -> collectAsStateWithLifecycle -> UI
```

See [viewmodel-nav3-expert](../viewmodel-nav3-expert/SKILL.md) and [lifecycle-viewmodel-expert](../lifecycle-viewmodel-expert/SKILL.md) for details.

### Unidirectional Data Flow (UDF)

Both patterns enforce UDF. The critical rule: **events flow up, state flows down**. The UI never mutates state directly. Every state change originates from the presenter/viewmodel in response to an event or data emission.

## Modularization

### :api / :impl Split

Every feature is split into two modules. The `:api` module contains interfaces, Screen definitions, domain models, and navigation keys. The `:impl` module contains implementations, Presenters/ViewModels, UI composables, and DI bindings.

```
features/
  auth/
    api/       -> AuthRepository interface, AuthScreen, User model
    impl/      -> AuthRepositoryImpl, AuthPresenter, AuthUi, Metro bindings
```

Features depend only on other features' `:api` modules. Never depend on another feature's `:impl`.

See [modularization-expert](../modularization-expert/SKILL.md) for convention plugins and dependency rules.

### Feature Modules

Each feature is self-contained: it owns its screens, presenters, repositories, and data sources. Cross-feature communication happens through `:api` interfaces or shared navigation events.

### Convention Plugins

Shared build logic lives in `build-logic/` as Gradle convention plugins. Standard plugins include `kmp.library`, `kmp.feature`, and `android.app`.

See [gradle-plugin-expert](../gradle-plugin-expert/SKILL.md) for implementation details.

## Dependency Rule

The dependency rule is the single most important architectural constraint:

```
Presentation  ->  Domain  ->  Data
(Presenter)      (UseCase)   (Repository Interface)
     |                              ^
     |______________________________|
       (direct, when no domain layer)
```

- **Domain layer** has NO framework dependencies. Pure Kotlin only. No Android, no Compose, no Ktor.
- **Data layer** depends on domain (implements repository interfaces). Never depends on presentation.
- **Presentation layer** depends on domain (or repository interfaces directly). Never imports data layer classes.
- **DI (Metro)** wires everything at the app module level. The app module is the only module that sees all `:impl` modules.

## KMP-Specific Patterns

### expect/actual

Use for platform-specific implementations that have no meaningful interface abstraction:

```kotlin
// In commonMain
expect fun platformName(): String

// In androidMain
actual fun platformName(): String = "Android ${Build.VERSION.SDK_INT}"

// In iosMain
actual fun platformName(): String = "iOS ${UIDevice.currentDevice.systemVersion}"
```

### Interface + DI (Preferred for Testability)

When the platform-specific behavior can be expressed as an interface, prefer interface + Metro `@ContributesBinding` over expect/actual. This approach is easier to test because you can provide fakes without actual implementations.

```kotlin
// In commonMain
interface FileStorage {
    suspend fun read(path: String): ByteArray
    suspend fun write(path: String, data: ByteArray)
}

// In androidMain
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AndroidFileStorage(private val context: Context) : FileStorage { ... }
```

### Platform Abstraction

Common abstractions that need platform-specific backing:

| Abstraction | Android | iOS | Usage |
|-------------|---------|-----|-------|
| `PlatformContext` | `Application` | `NSObject` | App-level context |
| `Dispatchers.IO` | `Dispatchers.IO` | Custom pool | Background work |
| `FileSystem` | `okio.FileSystem.SYSTEM` | `okio.FileSystem.SYSTEM` | File I/O |
| `SecureStorage` | `EncryptedSharedPreferences` | `Keychain` | Secrets |

## Error Handling Strategy

### Sealed Class Hierarchy

Define a project-wide error hierarchy in a shared `:core:model` module:

```kotlin
sealed interface AppError {
    sealed interface Network : AppError {
        data object NoConnection : Network
        data object Timeout : Network
        data class Server(val code: Int, val message: String) : Network
    }
    sealed interface Data : AppError {
        data object NotFound : Data
        data class Corruption(val cause: Throwable) : Data
    }
    sealed interface Validation : AppError {
        data class InvalidField(val field: String, val reason: String) : Validation
    }
}
```

### suspendRunCatching in Data Layer

Never use `runCatching` in suspend functions (it catches `CancellationException`). Use a safe wrapper:

```kotlin
suspend inline fun <T> suspendRunCatching(block: () -> T): Result<T> =
    try { Result.success(block()) }
    catch (e: CancellationException) { throw e }
    catch (e: Exception) { Result.failure(e) }
```

See [error-handling-expert](../error-handling-expert/SKILL.md) for the full error propagation strategy.

### Circuit State Models

```kotlin
data class State(
    val content: ContentState,
    val eventSink: (Event) -> Unit = {},
) : CircuitUiState

sealed interface ContentState {
    data object Loading : ContentState
    data class Success(val data: ImmutableList<Item>) : ContentState
    data class Error(val error: AppError, val retry: () -> Unit) : ContentState
}
```

## State Management

### Circuit

- Use `rememberRetained` for in-memory state that survives configuration changes.
- Use `eventSink` lambda in the State for type-safe event dispatch.
- State is always immutable. Never use `MutableList` or `MutableMap` in State types.

### ViewModel

- Use `StateFlow` with `stateIn(WhileSubscribed(5_000))` for lifecycle-aware collection.
- Use `collectAsStateWithLifecycle()` in Compose to observe the flow.

### Immutable Collections

Always use `ImmutableList` / `ImmutableMap` from `kotlinx.collections.immutable` in state types. Mutable collections defeat Compose's stability inference, causing unnecessary recompositions.

```kotlin
// GOOD
data class State(val items: ImmutableList<Item>) : CircuitUiState

// BAD -- causes recomposition on every frame
data class State(val items: List<Item>) : CircuitUiState
```

## Testing Strategy

| Layer | What to Test | How |
|-------|-------------|-----|
| Repository | Data transformation, cache behavior, error mapping | Fake data sources, no mocking framework |
| Presenter | State transitions in response to events | `Presenter.test {}`, `FakeNavigator` |
| ViewModel | State emissions from coroutine flows | `runTest` + Turbine |
| Use Case | Business logic combining multiple sources | Fake repositories |
| UI | Rendering, interaction, accessibility | Compose test rule + `TestEventSink` |

See [testing-expert](../testing-expert/SKILL.md) for comprehensive testing patterns.

## Core Rules

1. **Dependency rule is non-negotiable.** Domain never imports framework types. Presentation never imports data layer implementations.
2. **Repository is the truth boundary.** All data mapping (DTO to domain) happens inside the repository. Nothing above it sees DTOs.
3. **State is immutable.** Use `data class` with `ImmutableList`/`ImmutableMap`. Never mutate state objects.
4. **Events flow up, state flows down.** UI dispatches events; presenter/viewmodel produces state.
5. **Interface + DI over expect/actual.** Prefer `@ContributesBinding` for testability. Use expect/actual only when no interface makes sense.
6. **Feature modules are self-contained.** Each feature owns its screens, logic, data sources, and DI bindings.
7. **Skip the domain layer when it adds no value.** Simple CRUD features can go repository to presenter directly.

## Common Pitfalls

| Pitfall | Why it is dangerous | Fix |
|---------|--------------------|----|
| DTO types in presenter layer | Couples UI to API contract; breaks when API changes | Map to domain models in the repository |
| Mutable collections in state | Defeats Compose stability; causes recomposition storms | Use `ImmutableList` / `ImmutableMap` |
| Domain layer for simple CRUD | Adds unnecessary boilerplate with no benefit | Skip it; depend on repository interface directly |
| Feature `:impl` depending on another feature `:impl` | Creates circular dependencies; breaks incremental compilation | Depend only on `:api` modules |
| `runCatching` in suspend functions | Catches `CancellationException`, breaking structured concurrency | Use `suspendRunCatching` |
| Navigator calls in composition | Causes navigation on every recomposition | Call `navigator.goTo()` only in event handlers or `LaunchedEffect` |
| Business logic in UI composables | Untestable; violates separation of concerns | Move all logic to presenter/viewmodel |

## Related Skills

- [circuit-expert](../circuit-expert/SKILL.md) -- Circuit MVI pattern and navigation
- [viewmodel-nav3-expert](../viewmodel-nav3-expert/SKILL.md) -- ViewModel + Nav3 alternative
- [metro-expert](../metro-expert/SKILL.md) -- Dependency injection and graph wiring
- [store5-expert](../store5-expert/SKILL.md) -- Offline-first caching with Store5
- [modularization-expert](../modularization-expert/SKILL.md) -- Module structure and convention plugins
- [error-handling-expert](../error-handling-expert/SKILL.md) -- Error propagation across layers
- [testing-expert](../testing-expert/SKILL.md) -- Testing at every architectural layer
