---
name: coroutines-core-expert
description: Expert guidance on Kotlin Coroutines Core for KMP. Use for structured concurrency, Flow, StateFlow/SharedFlow, Channels, exception handling, dispatcher injection, and async operations.
---

# Coroutines Core Expert Skill (kotlinx-coroutines 1.10.2)

## Overview

This skill covers the runtime execution of Kotlin Coroutines in KMP: structured concurrency, Flow (cold and hot), Channels, dispatchers, exception handling, and dispatcher injection via Metro DI. All patterns target `commonMain` unless explicitly noted.

## When to Use

- **Async Operations**: `launch`, `async`, `withContext`, `withTimeout`
- **Reactive Streams**: `Flow`, `StateFlow`, `SharedFlow`
- **Concurrency Primitives**: `Mutex`, `Semaphore`, `Channel`
- **Scope Management**: `coroutineScope`, `supervisorScope`, `SupervisorJob`
- **Dispatcher Injection**: Providing dispatchers via Metro DI for testability

## Quick Reference

For API signatures, see [reference.md](reference.md).
For production examples with Metro DI, see [examples.md](examples.md).

## Core Concepts

### 1. Structured Concurrency

Every coroutine runs inside a `CoroutineScope`. Cancellation and failure propagate through the scope hierarchy.

**Key rules:**
- A parent Job waits for all children to complete
- Cancelling a parent cancels all children
- An unhandled exception in a child cancels the parent (unless `SupervisorJob`)
- Never use `GlobalScope` -- it breaks structured concurrency

```kotlin
// coroutineScope waits for all children and propagates failures
suspend fun fetchUserData(): UserData = coroutineScope {
    val profile = async { profileRepo.fetch() }
    val settings = async { settingsRepo.fetch() }
    UserData(profile.await(), settings.await())
}
```

**Job vs SupervisorJob:**

| Behavior | `Job` | `SupervisorJob` |
|----------|-------|-----------------|
| Child failure | Cancels parent and siblings | Only the failed child is cancelled |
| Use case | All-or-nothing operations | Independent parallel tasks |
| Scope builder | `coroutineScope {}` | `supervisorScope {}` |

### 2. Dispatchers

| Dispatcher | Purpose | KMP Availability |
|------------|---------|-----------------|
| `Dispatchers.Main` | UI thread | Android: yes. iOS/Desktop: needs platform setup. |
| `Dispatchers.IO` | Blocking I/O | All platforms (Native: 64-thread pool since 1.8.0) |
| `Dispatchers.Default` | CPU-intensive work | All platforms |
| `Dispatchers.Unconfined` | No thread confinement | All platforms -- avoid in production |

**NEVER hardcode dispatchers.** Always inject via a `DispatcherProvider` interface:

```kotlin
// commonMain
interface DispatcherProvider {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
}
```

**KMP platform notes:**
- `Dispatchers.Main` on iOS requires `kotlinx-coroutines-core` (auto-provided since 1.6.0)
- `Dispatchers.IO` on Native uses a fixed thread pool (max 2048 threads, default parallelism 64)
- `Dispatchers.Main.immediate` dispatches immediately if already on the main thread

### 3. Flow (Cold Streams)

A `Flow` is a cold asynchronous stream. It only runs when collected.

**Builder patterns:**
- `flow { emit(value) }` -- suspend lambda, primary builder
- `flowOf(1, 2, 3)` -- fixed values
- `list.asFlow()` -- from collection
- `callbackFlow { }` -- wrapping callback-based APIs

**Operator categories:**

| Category | Operators |
|----------|-----------|
| Transform | `map`, `filter`, `transform`, `scan`, `runningFold` |
| Flatten | `flatMapLatest`, `flatMapMerge`, `flatMapConcat` |
| Combine | `combine`, `merge`, `zip` |
| Rate limit | `debounce`, `sample`, `conflate`, `buffer` |
| Lifecycle | `onStart`, `onEach`, `onCompletion`, `catch` |
| Context | `flowOn`, `launchIn` |
| Terminal | `collect`, `first`, `single`, `toList`, `fold`, `reduce`, `any`, `all`, `none` |
| Distinct | `distinctUntilChanged`, `distinctUntilChangedBy` |

**Critical rules:**
- `flowOn` changes the upstream dispatcher, NOT downstream
- `catch` only catches upstream exceptions, NOT downstream (collector)
- Operators are sequential by default; use `buffer()` for concurrent emission/collection
- `flatMapLatest` cancels the previous inner flow on each new emission

### 4. StateFlow and SharedFlow (Hot Streams)

**StateFlow** -- holds a single current value, replays the latest to new collectors:

```kotlin
private val _state = MutableStateFlow(UiState.Loading)
val state: StateFlow<UiState> = _state.asStateFlow()
```

**SharedFlow** -- event bus, configurable replay:

```kotlin
private val _events = MutableSharedFlow<Event>(
    replay = 0,            // no replay for one-shot events
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
)
val events: SharedFlow<Event> = _events.asSharedFlow()
```

**Converting cold Flow to hot:**

| Function | Result | Use case |
|----------|--------|----------|
| `stateIn(scope, started, initial)` | `StateFlow` | UI state with initial value |
| `shareIn(scope, started, replay)` | `SharedFlow` | Shared upstream, configurable replay |

**SharingStarted strategies:**

| Strategy | Behavior | Use case |
|----------|----------|----------|
| `WhileSubscribed(5000)` | Stops 5s after last subscriber leaves | Screen-level state (saves resources) |
| `Lazily` | Starts on first subscriber, never stops | Session-level state |
| `Eagerly` | Starts immediately, never stops | App-level state |

**StateFlow equality pitfall:** `StateFlow` uses `equals()` to deduplicate emissions. If you emit a data class with the same content, subscribers will NOT see the update. Use `copy()` or wrap in a unique wrapper if needed.

### 5. Channels

Channels provide a way to transfer values between coroutines (hot, one-to-one consumption).

| Type | Buffer | Behavior |
|------|--------|----------|
| `Channel.RENDEZVOUS` (0) | None | Suspends sender until receiver is ready |
| `Channel.BUFFERED` (64) | Default | Suspends when buffer full |
| `Channel.CONFLATED` | 1 | Drops oldest, never suspends sender |
| `Channel.UNLIMITED` | Unlimited | Never suspends, risk of OOM |

**Use cases:**
- One-shot UI events (navigation, snackbar) via `Channel<Event>(Channel.BUFFERED)`
- Producer-consumer with `produce { }` and `consumeEach { }`
- Fan-out (multiple consumers from one channel)

**Note:** `actor` is marked `@ObsoleteCoroutinesApi`. Do not use it for new code.

### 6. Exception Handling

**In `launch`:** Uncaught exceptions propagate to the parent scope. Use `try/catch` inside the lambda or install a `CoroutineExceptionHandler`.

**In `async`:** Exceptions are deferred until `.await()` is called. Wrap `.await()` in `try/catch`.

**`CoroutineExceptionHandler`:** Only works on root coroutines (direct children of a scope). It is a last-resort handler, not a replacement for structured error handling.

```kotlin
val handler = CoroutineExceptionHandler { _, exception ->
    logger.error("Unhandled", exception)
}
val scope = CoroutineScope(SupervisorJob() + handler)
```

**Flow exception handling:**

```kotlin
flow
    .catch { e -> emit(Result.Error(e)) }  // catches upstream errors
    .onCompletion { cause -> /* cleanup, cause is null on normal completion */ }
    .collect { result -> /* process */ }
```

**Retry with backoff:**

```kotlin
fun <T> Flow<T>.retryWithBackoff(
    maxRetries: Int = 3,
    initialDelay: Long = 1000L,
    factor: Double = 2.0,
): Flow<T> = retry(maxRetries.toLong()) { cause ->
    delay(initialDelay * factor.pow((maxRetries - retries).toDouble()).toLong())
    cause is IOException
}
```

### 7. Concurrency Primitives

**Mutex** -- mutual exclusion for shared mutable state:

```kotlin
private val mutex = Mutex()
private var count = 0

suspend fun increment() = mutex.withLock {
    count++
}
```

**Semaphore** -- limit concurrency:

```kotlin
private val semaphore = Semaphore(permits = 5)

suspend fun fetchWithLimit(url: String) = semaphore.withPermit {
    httpClient.get(url)
}
```

### 8. Dispatcher Injection via Metro DI

Always inject dispatchers for testability. In tests, replace with `StandardTestDispatcher` or `UnconfinedTestDispatcher`.

```kotlin
// commonMain
@Inject
@SingleIn(AppScope::class)
class DefaultDispatcherProvider : DispatcherProvider {
    override val main: CoroutineDispatcher = Dispatchers.Main
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val default: CoroutineDispatcher = Dispatchers.Default
}

@ContributesBinding(AppScope::class)
interface DispatcherProviderBinding : DispatcherProvider
```

### 9. Common Pitfalls

| Pitfall | Fix |
|---------|-----|
| Using `GlobalScope` | Inject a scoped `CoroutineScope` via Metro DI |
| Hardcoding `Dispatchers.IO` | Inject `DispatcherProvider` |
| Collecting Flow in wrong scope | Use `viewModelScope` or presenter scope, not `lifecycleScope.launch {}` directly |
| StateFlow equality skipping updates | Ensure data classes produce different `equals()` results, or use a wrapper |
| Missing cancellation in `callbackFlow` | Always provide `awaitClose { callback.unregister() }` |
| `catch` not catching collector errors | `catch` is upstream-only; wrap collector in `try/catch` |
| Blocking the main thread | Use `withContext(dispatchers.io)` for blocking calls |
| Forgetting `flowOn` is upstream-only | Place `flowOn` after the operators that need the dispatcher change |
| Not handling `CancellationException` | Never catch `CancellationException` -- rethrow it to preserve cancellation |
| Using `stateIn` with `Eagerly` for screen state | Use `WhileSubscribed(5000)` to release resources when screen is backgrounded |

### 10. New in 1.10.x

- `Flow.any`, `Flow.all`, `Flow.none` -- terminal operators for boolean checks
- Fixed `Flow.stateIn` hanging when scope is cancelled or flow is empty
- Improved `limitedParallelism` dispatcher failure handling
- `Dispatchers.IO` fully available on Kotlin/Native (64-thread default parallelism)

## Integration Points

- **Circuit Presenters**: Use `rememberCoroutineScope()` inside `@Composable fun present()` for launching coroutines
- **Metro DI**: Inject `CoroutineScope` and `DispatcherProvider` into repositories and use cases
- **Store5**: Repository flows from `Store.stream()` use coroutines internally
- **SQLDelight**: `.asFlow().mapToList()` returns a `Flow<List<T>>` for reactive queries
- **Ktor**: `HttpClient` suspend functions integrate with structured concurrency
- **Testing**: See `coroutines-test-expert` skill for `runTest`, `TestScope`, and `Turbine`
