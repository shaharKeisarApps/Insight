# Store5 API Reference (v5.1.0-alpha08)

## StoreBuilder

Entry point for creating `Store` instances.

```kotlin
// From Fetcher + SourceOfTruth (standard pattern)
fun <Key : Any, Network : Any, Output : Any> StoreBuilder.Companion.from(
    fetcher: Fetcher<Key, Network>,
    sourceOfTruth: SourceOfTruth<Key, Network, Output>
): StoreBuilder<Key, Output>

// From Fetcher only (no persistence, memory cache only)
fun <Key : Any, Output : Any> StoreBuilder.Companion.from(
    fetcher: Fetcher<Key, Output>
): StoreBuilder<Key, Output>
```

### Builder Configuration

```kotlin
StoreBuilder.from(fetcher, sourceOfTruth)
    .scope(coroutineScope)                  // CoroutineScope for sharing
    .cachePolicy(memoryPolicy)              // Memory eviction policy
    .disableCache()                         // Disable in-memory cache entirely
    .validator(validator)                   // Cache staleness validation
    .build()                                // Returns Store<Key, Output>
```

### Converting to MutableStoreBuilder

```kotlin
StoreBuilder.from(fetcher, sourceOfTruth)
    .toMutableStoreBuilder(converter)       // Returns MutableStoreBuilder
    .build(
        updater = updater,                  // Required: pushes writes to network
        bookkeeper = bookkeeper             // Optional: tracks sync failures
    )                                       // Returns MutableStore<Key, Output>
```

---

## Fetcher

Retrieves data from network/remote sources.

```kotlin
// Suspend function (single value) -- most common
fun <Key : Any, Network : Any> Fetcher.Companion.of(
    name: String,                           // Required: logging/debugging identifier
    fetch: suspend (key: Key) -> Network
): Fetcher<Key, Network>

// Flow-based (streaming, WebSockets, SSE)
fun <Key : Any, Network : Any> Fetcher.Companion.ofFlow(
    name: String,
    flowFactory: (key: Key) -> Flow<Network>
): Fetcher<Key, Network>
```

### FetcherResult

```kotlin
sealed class FetcherResult<out Network : Any> {
    data class Data<Network : Any>(
        val value: Network,
        val origin: String                  // Debugging label
    ) : FetcherResult<Network>()

    sealed class Error : FetcherResult<Nothing>() {
        data class Exception(val error: Throwable) : Error()
        data class Message(val message: String) : Error()
        data class Custom<E : Any>(val error: E) : Error()
    }
}
```

---

## SourceOfTruth

Persistent storage layer (SQLDelight, DataStore, file system).

```kotlin
// Standard factory -- reader returns Flow<Output?>
fun <Key : Any, Local : Any, Output : Any> SourceOfTruth.Companion.of(
    reader: (key: Key) -> Flow<Output?>,            // Reactive read (Flow)
    writer: suspend (key: Key, local: Local) -> Unit, // Persist data
    delete: suspend (key: Key) -> Unit,             // Delete by key
    deleteAll: suspend () -> Unit                   // Delete all data
): SourceOfTruth<Key, Local, Output>

// Non-flow reader variant -- reader is suspend returning nullable
fun <Key : Any, Local : Any, Output : Any> SourceOfTruth.Companion.of(
    nonFlowReader: suspend (key: Key) -> Output?,   // One-shot read
    writer: suspend (key: Key, local: Local) -> Unit,
    delete: suspend (key: Key) -> Unit,
    deleteAll: suspend () -> Unit
): SourceOfTruth<Key, Local, Output>
```

> **Note**: When the `Local` type equals the `Output` type (no Converter needed), use the two-type-parameter variant where `Local` and `Output` are the same.

---

## Store (Read-Only)

```kotlin
interface Store<Key : Any, Output : Any> {
    // Stream responses reactively
    fun stream(request: StoreReadRequest<Key>): Flow<StoreReadResponse<Output>>

    // One-shot get (suspends until Data or throws on Error)
    suspend fun get(key: Key): Output

    // Force fresh fetch (suspends until Data or throws on Error)
    suspend fun fresh(key: Key): Output

    // Clear cached data for a key
    suspend fun clear(key: Key)

    // Clear all cached data
    suspend fun clear()
}
```

---

## MutableStore

Extends Store with write-back capabilities.

```kotlin
interface MutableStore<Key : Any, Output : Any> : Store<Key, Output> {
    // Write data (locally + sync to network via Updater)
    suspend fun <Response : Any> write(
        request: StoreWriteRequest<Key, Output, Response>
    ): StoreWriteResponse

    // Stream write responses for a flow of write requests
    fun <Response : Any> stream(
        requestStream: Flow<StoreWriteRequest<Key, Output, Response>>
    ): Flow<StoreWriteResponse>
}
```

### StoreWriteRequest

```kotlin
data class StoreWriteRequest<Key : Any, Output : Any, Response : Any>(
    val key: Key,
    val value: Output,
    val created: Long = Clock.System.now().toEpochMilliseconds()
) {
    companion object {
        fun <Key : Any, Output : Any, Response : Any> of(
            key: Key,
            value: Output
        ): StoreWriteRequest<Key, Output, Response>
    }
}
```

### StoreWriteResponse

```kotlin
sealed class StoreWriteResponse {
    object Success : StoreWriteResponse()
    sealed class Error : StoreWriteResponse() {
        data class Exception(val error: Throwable) : Error()
        data class Message(val message: String) : Error()
    }
}
```

---

## StoreReadRequest

Defines how data should be retrieved from the Store.

```kotlin
data class StoreReadRequest<out Key : Any>(
    val key: Key,
    val skippedCaches: Int,                 // Bitmask of skipped cache layers
    val refresh: Boolean,                   // Whether to trigger Fetcher
    val fallBackToSourceOfTruth: Boolean     // Fall back to SoT on Fetcher error
) {
    companion object {
        // Return cached data; optionally refresh from network in background
        fun <Key : Any> cached(
            key: Key,
            refresh: Boolean = false
        ): StoreReadRequest<Key>

        // Skip all caches, force network fetch
        fun <Key : Any> fresh(
            key: Key,
            fallBackToSourceOfTruth: Boolean = false
        ): StoreReadRequest<Key>

        // Bypass memory cache, read from SoT first
        fun <Key : Any> skipMemory(
            key: Key,
            refresh: Boolean = true
        ): StoreReadRequest<Key>

        // Only read from local sources (memory + SoT), never fetch
        fun <Key : Any> localOnly(
            key: Key
        ): StoreReadRequest<Key>

        // Force fresh, but fall back to SoT data if Fetcher fails
        fun <Key : Any> freshWithFallBackToSourceOfTruth(
            key: Key
        ): StoreReadRequest<Key>
    }
}
```

---

## StoreReadResponse

> **CRITICAL**: Named `StoreReadResponse`, NOT `StoreResponse`.

```kotlin
sealed class StoreReadResponse<out Output> {
    // Sentinel emitted before any data
    object Initial : StoreReadResponse<Nothing>()

    // Source is actively loading
    data class Loading(
        override val origin: StoreReadResponseOrigin
    ) : StoreReadResponse<Nothing>()

    // Data available from cache, SoT, or Fetcher
    data class Data<Output>(
        val value: Output,
        override val origin: StoreReadResponseOrigin
    ) : StoreReadResponse<Output>()

    // Fetcher completed but no new data (e.g., 304 Not Modified)
    data class NoNewData(
        override val origin: StoreReadResponseOrigin
    ) : StoreReadResponse<Nothing>()

    // Error hierarchy
    sealed class Error : StoreReadResponse<Nothing>() {
        data class Exception(
            val error: Throwable,
            override val origin: StoreReadResponseOrigin
        ) : Error()

        data class Message(
            val message: String,
            override val origin: StoreReadResponseOrigin
        ) : Error()

        data class Custom<E : Any>(
            val error: E,
            override val origin: StoreReadResponseOrigin
        ) : Error()
    }

    abstract val origin: StoreReadResponseOrigin
}
```

### StoreReadResponseOrigin

```kotlin
enum class StoreReadResponseOrigin {
    Cache,              // In-memory cache
    SourceOfTruth,      // Persistent storage (SQLDelight)
    Fetcher,            // Network call
    Initial             // Before any source has responded
}
```

### Utility Extensions

```kotlin
fun <Output> StoreReadResponse<Output>.dataOrNull(): Output?
fun <Output> StoreReadResponse<Output>.requireData(): Output
fun StoreReadResponse<*>.errorMessageOrNull(): String?
fun <Output> StoreReadResponse<Output>.throwIfError()
fun <Output> StoreReadResponse<Output>.errorOrNull(): Throwable?
```

---

## Converter

Transforms between Network, Local, and Output model layers.

```kotlin
interface Converter<Network : Any, Local : Any, Output : Any> {
    fun fromNetworkToLocal(network: Network): Local
    fun fromOutputToLocal(output: Output): Local
}
```

### Implementation

```kotlin
class ProductConverter : Converter<ProductDto, ProductEntity, Product> {
    override fun fromNetworkToLocal(network: ProductDto): ProductEntity =
        ProductEntity(
            id = network.id,
            name = network.name,
            price = network.price,
            updatedAt = Clock.System.now().toEpochMilliseconds()
        )

    override fun fromOutputToLocal(output: Product): ProductEntity =
        ProductEntity(
            id = output.id,
            name = output.name,
            price = output.price,
            updatedAt = output.updatedAt
        )
}
```

---

## Validator

Cache staleness check. Return `false` to trigger re-fetch.

```kotlin
interface Validator<Output : Any> {
    suspend fun isValid(item: Output): Boolean

    companion object {
        fun <Output : Any> by(
            validator: suspend (item: Output) -> Boolean
        ): Validator<Output>
    }
}
```

### Usage

```kotlin
Validator.by { product: Product ->
    val age = Clock.System.now() - Instant.fromEpochMilliseconds(product.fetchedAt)
    age < 5.minutes  // true = valid, false = stale (re-fetch)
}
```

---

## Updater

Pushes local writes to the network (used by MutableStore).

```kotlin
interface Updater<Key : Any, Output : Any, Response : Any> {
    suspend fun post(key: Key, value: Output): UpdaterResult<Response>
}
```

### UpdaterResult

```kotlin
sealed class UpdaterResult<out Response : Any> {
    sealed class Success<Response : Any> : UpdaterResult<Response>() {
        data class Typed<Response : Any>(val value: Response) : Success<Response>()
        object Untyped : Success<Nothing>()
    }

    sealed class Error : UpdaterResult<Nothing>() {
        data class Exception(val error: Throwable) : Error()
        data class Message(val message: String) : Error()
    }
}
```

---

## Bookkeeper

Tracks sync failures for MutableStore conflict resolution.

```kotlin
interface Bookkeeper<Key : Any> {
    suspend fun getLastFailedSync(key: Key): Long?
    suspend fun setLastFailedSync(key: Key, timestamp: Long): Boolean
    suspend fun clear(key: Key): Boolean
    suspend fun clearAll(): Boolean

    companion object {
        fun <Key : Any> by(
            getLastFailedSync: suspend (key: Key) -> Long?,
            setLastFailedSync: suspend (key: Key, timestamp: Long) -> Boolean,
            clear: suspend (key: Key) -> Boolean,
            clearAll: suspend () -> Boolean
        ): Bookkeeper<Key>
    }
}
```

---

## MemoryPolicy (Cache Configuration)

```kotlin
val memoryPolicy = MemoryPolicy.builder<Key, Output>()
    .setMaxSize(100)                               // Max items in cache
    .setExpireAfterWrite(10.minutes.inWholeMilliseconds) // TTL after write
    .setExpireAfterAccess(5.minutes.inWholeMilliseconds) // TTL after last access
    .build()

// Apply to Store
StoreBuilder.from(fetcher, sourceOfTruth)
    .cachePolicy(memoryPolicy)
    .build()
```

---

## MutableStoreBuilder

Alternative entry point that creates MutableStore directly.

```kotlin
fun <Key : Any, Network : Any, Local : Any, Output : Any> MutableStoreBuilder.Companion.from(
    fetcher: Fetcher<Key, Network>,
    sourceOfTruth: SourceOfTruth<Key, Local, Output>,
    converter: Converter<Network, Local, Output>
): MutableStoreBuilder<Key, Network, Local, Output>
```

### Build with Updater and Bookkeeper

```kotlin
MutableStoreBuilder.from(fetcher, sourceOfTruth, converter)
    .build(
        updater = updater,          // Required
        bookkeeper = bookkeeper     // Optional
    )
```
