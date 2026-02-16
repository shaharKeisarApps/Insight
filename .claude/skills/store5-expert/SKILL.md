---
name: store5-expert
description: Elite Store5 caching expertise for KMP. Use for implementing offline-first data repositories, caching, conflict resolution, MutableStore write-back, and reactive data pipelines with Circuit + SQLDelight + Ktor.
---

# Store5 Expert Skill (v5.1.0-alpha08)

## Overview

Store5 is the standard reactive, offline-first data loading library for Kotlin Multiplatform. It unifies network fetching (`Fetcher`), persistent storage (`SourceOfTruth`), and in-memory caching into a single reactive pipeline that emits `StoreReadResponse` to the UI layer. Store5 eliminates manual cache invalidation, deduplicates in-flight requests, and provides a structured approach to data consistency across layers.

In this project, Store5 integrates with **Ktor 3.4.0** for network fetching, **SQLDelight 2.2.1** for persistent storage, **Metro DI** for dependency injection, and **Circuit MVI** for presenter consumption.

## When to Use

- **Offline Support**: App must display data without network connectivity. Store serves cached data from SourceOfTruth while background-refreshing.
- **Caching**: Minimize redundant network calls. Store deduplicates concurrent requests for the same key and serves from memory/disk cache.
- **Data Consistency**: Ensure a single source of truth (database) that all consumers observe reactively.
- **Conflict Resolution**: Track write failures with `Bookkeeper` and retry synchronization when connectivity returns.
- **Optimistic Updates**: Write locally first via `MutableStore`, then sync to server, rolling back on failure.
- **Reactive Data Pipelines**: Compose-friendly `Flow<StoreReadResponse>` drives loading/data/error states in presenters.

## Quick Reference

See [reference.md](reference.md) for complete API signatures and builder options.
See [examples.md](examples.md) for production-ready implementations with Metro, Circuit, Ktor, and SQLDelight.

## Core Components

### 1. Fetcher

The network data source. Defines how to retrieve fresh data from a remote API. Two factory methods:

- **`Fetcher.of(name) { key -> ... }`** -- Suspend function returning a single value. Use for standard REST calls via Ktor.
- **`Fetcher.ofFlow(name) { key -> ... }`** -- Returns a `Flow`. Use for WebSocket streams, SSE, or polling.

The `name` parameter is required and used for logging/debugging.

```kotlin
Fetcher.of("fetchProduct") { key: ProductId ->
    httpClient.get("/api/products/${key.value}").body<ProductDto>()
}
```

### 2. SourceOfTruth (SoT)

The persistent storage layer -- typically SQLDelight. Provides reactive reads and suspend writes. The `reader` must return `Flow<T?>` so Store can observe database changes and re-emit to all collectors.

```kotlin
SourceOfTruth.of(
    reader = { key -> queries.selectById(key.value).asFlow().mapToOneOrNull(dispatchers.io) },
    writer = { key, entity -> queries.upsert(entity) },
    delete = { key -> queries.deleteById(key.value) },
    deleteAll = { queries.deleteAll() }
)
```

### 3. Store

The read-only pipeline connecting Fetcher and SourceOfTruth with an in-memory cache. Built via `StoreBuilder.from()`. Consumers call `store.stream(request)` to get a `Flow<StoreReadResponse<Output>>`.

### 4. MutableStore

Extends Store with write-back capabilities. Requires a `Converter` (to transform between network/local/output types) and an `Updater` (to push local changes to the server). Optionally accepts a `Bookkeeper` for tracking sync failures.

### 5. Converter

Transforms data between three model layers:
- **Network** -- DTO from the API (e.g., `ProductDto`)
- **Local** -- Entity stored in the database (e.g., `ProductEntity`)
- **Output** -- Domain model consumed by the UI (e.g., `Product`)

Defines `fromNetworkToLocal(network): Local` and `fromOutputToLocal(output): Local`.

### 6. Validator

Determines whether cached data is still valid. Receives the `Output` item and returns `true` (valid) or `false` (stale, re-fetch). Use for time-based staleness or version-based invalidation.

```kotlin
Validator.by { item: Product ->
    item.fetchedAt + 5.minutes > Clock.System.now()
}
```

### 7. Bookkeeper

Tracks synchronization failures for `MutableStore`. When a write to the server fails, the Bookkeeper records the timestamp. On next stream, Store can detect unsynced items and retry. Provides `getLastFailedSync`, `setLastFailedSync`, `clear`, and `clearAll`.

## Store Pipeline

Data flows through the pipeline in this order:

```
Request (StoreReadRequest)
    |
    v
[Memory Cache] -- hit? --> emit Data(origin=Cache)
    |  miss
    v
[SourceOfTruth] -- has data? --> emit Data(origin=SourceOfTruth)
    |  also triggers Fetcher if refresh=true or no cached data
    v
[Fetcher] -- network call
    |
    v
[Converter] -- fromNetworkToLocal (if configured)
    |
    v
[SourceOfTruth] -- writer stores the data
    |
    v
[SourceOfTruth reader] -- re-emits via Flow
    |
    v
[Validator] -- isValid? (if configured, invalid triggers re-fetch)
    |
    v
[Memory Cache] -- updated
    |
    v
UI receives Data(origin=Fetcher)
```

## StoreReadResponse Types

> **CRITICAL**: The class is `StoreReadResponse`, NOT `StoreResponse`. This was renamed.

| Type | Properties | When Emitted |
|------|-----------|--------------|
| `Initial` | (none) | Sentinel before any data. Always first emission. |
| `Loading(origin)` | `origin: StoreReadResponseOrigin` | Fetcher or SoT is actively loading. |
| `Data(value, origin)` | `value: Output`, `origin` | Data available. Origin indicates source (Cache, SourceOfTruth, Fetcher). |
| `NoNewData(origin)` | `origin` | Fetcher completed but returned no new data (304, empty). |
| `Error.Exception(error, origin)` | `error: Throwable`, `origin` | Throwable-based failure. |
| `Error.Message(message, origin)` | `message: String`, `origin` | String-based failure message. |
| `Error.Custom<E>(error, origin)` | `error: E`, `origin` | Typed custom error. |

### Utility Extensions

- `dataOrNull()` -- Extract value or null
- `requireData()` -- Extract value or throw
- `errorMessageOrNull()` -- Get error message string
- `throwIfError()` -- Rethrow if Error.Exception

## StoreReadRequest Types

| Factory Method | Behavior |
|---------------|----------|
| `cached(key, refresh = false)` | Return from memory/SoT cache. If `refresh=true`, also trigger Fetcher in background. |
| `fresh(key)` | Skip all caches, force network fetch. Still writes result to SoT and cache. |
| `skipMemory(key, refresh)` | Bypass memory cache, read from SoT first, optionally refresh from network. |
| `localOnly(key)` | Only read from memory cache and SoT. Never trigger Fetcher. For airplane mode. |
| `freshWithFallBackToSourceOfTruth(key)` | Force fresh, but if Fetcher fails, fall back to SoT data instead of emitting error. |

## Integration with Circuit

Circuit Presenters consume Store streams via `collectAsRetainedState` or manual collection in `LaunchedEffect`. Map `StoreReadResponse` to a UI state sealed interface:

```kotlin
@Composable
override fun present(): ProductScreen.State {
    var uiState by rememberRetained { mutableStateOf<UiState>(UiState.Loading) }

    LaunchedEffect(Unit) {
        repository.getProduct(screen.id).collect { response ->
            uiState = when (response) {
                is StoreReadResponse.Loading -> UiState.Loading
                is StoreReadResponse.Data -> UiState.Success(response.value)
                is StoreReadResponse.Error -> UiState.Error(response.errorMessageOrNull())
                else -> uiState // Initial, NoNewData -- keep current state
            }
        }
    }

    return ProductScreen.State(uiState = uiState) { event -> /* ... */ }
}
```

## Integration with SQLDelight

SQLDelight provides the ideal `SourceOfTruth` implementation. Use `.asFlow().mapToOneOrNull()` for the reader and direct query calls for writer/delete:

```kotlin
SourceOfTruth.of(
    reader = { key: ProductId ->
        productQueries.selectById(key.value)
            .asFlow()
            .mapToOneOrNull(dispatchers.io)
    },
    writer = { _, entity: ProductEntity ->
        productQueries.upsert(
            id = entity.id,
            name = entity.name,
            price = entity.price,
            updatedAt = entity.updatedAt
        )
    },
    delete = { key -> productQueries.deleteById(key.value) },
    deleteAll = { productQueries.deleteAll() }
)
```

## Integration with Ktor

Ktor `HttpClient` powers the Fetcher. Always inject the client via Metro DI and map responses to DTOs:

```kotlin
Fetcher.of("fetchProduct") { key: ProductId ->
    httpClient.get("/api/products/${key.value}").body<ProductDto>()
}
```

For paginated endpoints, use `Fetcher.ofFlow` with a `flow { }` builder that handles pagination internally.

## Core Rules

1. **Always use typed Keys.** Define `@JvmInline value class ProductId(val value: String)` instead of raw `String`. This prevents key collisions across different Store instances.
2. **Use `StoreReadResponse`, never `StoreResponse`.** The type was renamed. Using the old name will not compile.
3. **Provide all four SoT callbacks.** Always implement `reader`, `writer`, `delete`, and `deleteAll` on `SourceOfTruth.of()`. Missing `delete`/`deleteAll` prevents Store from cleaning up stale data.
4. **Scope Store as singleton.** Use `@SingleIn(AppScope::class)` with Metro. Creating multiple Store instances for the same data source causes cache fragmentation and duplicated network calls.
5. **Never call Store directly from UI.** Always wrap Store in a Repository that maps `StoreReadResponse` to domain-specific types and hides caching details.
6. **Handle all response types.** In `when` blocks, always handle `Initial`, `Loading`, `Data`, `NoNewData`, and all `Error` subtypes. Use `else` only for `Initial`/`NoNewData` no-ops.
7. **Use `cached(key, refresh = true)` for pull-to-refresh.** This serves cached data immediately while fetching fresh data in the background, giving the user instant feedback.
8. **Use `fresh()` sparingly.** It bypasses all caches and hits the network. Reserve for explicit user actions like "force refresh" or post-mutation revalidation.
9. **Provide a Converter when network and local models differ.** Never reuse DTOs as database entities. Separate concerns with Network/Local/Output model layers.
10. **Close or scope the Store's CoroutineScope.** If using a custom scope via `StoreBuilder.scope()`, ensure it is cancelled when the owning component is destroyed.

## Common Pitfalls

| Pitfall | Consequence | Fix |
|---------|-------------|-----|
| Using `StoreResponse` instead of `StoreReadResponse` | Compilation error | Use `StoreReadResponse` everywhere |
| Forgetting `name` parameter in `Fetcher.of()` | Compilation error (required param) | Always provide a descriptive string name |
| SoT reader not returning `Flow` | Store cannot observe changes reactively | Use `.asFlow().mapToOneOrNull()` from SQLDelight |
| Not handling `Error.Exception` vs `Error.Message` | Lost error context | Match on all `Error` subtypes in `when` |
| Creating Store per-screen instead of per-scope | Duplicated caches, wasted memory | `@SingleIn(AppScope::class)` in Metro |
| Using `flow.first()` instead of `stream().collect` | Misses subsequent updates (SoT changes, refreshes) | Always collect the full stream |
| Ignoring `NoNewData` | UI flickers if treated as loading | Keep current state on `NoNewData` |
| Not providing `delete`/`deleteAll` on SoT | `store.clear()` silently fails | Always implement all four SoT callbacks |

## Gradle Dependencies

```toml
# gradle/libs.versions.toml
[versions]
store = "5.1.0-alpha08"

[libraries]
store5 = { module = "org.mobilenativefoundation.store:store5", version.ref = "store" }
store-cache = { module = "org.mobilenativefoundation.store:cache5", version.ref = "store" }
```

```kotlin
// build.gradle.kts (shared module)
commonMain.dependencies {
    implementation(libs.store5)
    implementation(libs.store.cache)
}
```

## See Also

- [ktor-client-expert](../ktor-client-expert/SKILL.md) -- Network fetcher configuration for Store5
- [kotlinx-serialization-expert](../kotlinx-serialization-expert/SKILL.md) -- JSON parsing for Store converters
- [sqldelight-expert](../sqldelight-expert/SKILL.md) -- Local persistence as Store source of truth
- [coroutines-core-expert](../coroutines-core-expert/SKILL.md) -- Flow patterns for Store streams
