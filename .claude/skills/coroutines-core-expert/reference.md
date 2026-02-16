# Coroutines Core Reference (kotlinx-coroutines 1.10.2)

## Coroutine Builders

### launch

```kotlin
fun CoroutineScope.launch(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit
): Job
```

Launches a new coroutine without blocking the current thread. Returns a `Job`. Exceptions propagate to the parent scope (fire-and-forget).

### async

```kotlin
fun <T> CoroutineScope.async(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> T
): Deferred<T>
```

Launches a coroutine that returns a result via `Deferred<T>`. Call `.await()` to get the result. Exceptions are deferred until `.await()`.

### withContext

```kotlin
suspend fun <T> withContext(
    context: CoroutineContext,
    block: suspend CoroutineScope.() -> T
): T
```

Switches coroutine context (typically dispatcher) for the block. Suspends until block completes. Primary tool for dispatcher switching.

### withTimeout / withTimeoutOrNull

```kotlin
suspend fun <T> withTimeout(
    timeMillis: Long,
    block: suspend CoroutineScope.() -> T
): T  // throws TimeoutCancellationException

suspend fun <T> withTimeoutOrNull(
    timeMillis: Long,
    block: suspend CoroutineScope.() -> T
): T?  // returns null on timeout
```

### runBlocking

```kotlin
fun <T> runBlocking(
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend CoroutineScope.() -> T
): T
```

Bridges regular blocking code to coroutines. Blocks the current thread. Use only in `main()` or tests, never inside coroutines.

---

## Scope Builders

### coroutineScope

```kotlin
suspend fun <T> coroutineScope(
    block: suspend CoroutineScope.() -> T
): T
```

Creates a new scope. Waits for all children. If any child fails, all others are cancelled and the exception propagates.

### supervisorScope

```kotlin
suspend fun <T> supervisorScope(
    block: suspend CoroutineScope.() -> T
): T
```

Creates a scope with `SupervisorJob`. Child failures do NOT cancel siblings or the parent. Each child must handle its own exceptions.

---

## Job Hierarchy

### Job

```kotlin
fun Job(parent: Job? = null): CompletableJob
```

| Method | Description |
|--------|-------------|
| `cancel(cause)` | Cancels the job with optional cause |
| `join()` | Suspends until the job completes |
| `isActive` | `true` if running |
| `isCompleted` | `true` if finished (success or failure) |
| `isCancelled` | `true` if cancelled |
| `children` | Sequence of child jobs |
| `invokeOnCompletion { }` | Callback on completion |

### SupervisorJob

```kotlin
fun SupervisorJob(parent: Job? = null): CompletableJob
```

Same as `Job` but child failures do not propagate to the parent.

### Deferred

```kotlin
interface Deferred<out T> : Job {
    suspend fun await(): T
    fun getCompleted(): T  // throws if not completed
}
```

---

## Dispatchers

```kotlin
object Dispatchers {
    val Default: CoroutineDispatcher   // CPU-bound work, shared thread pool
    val Main: MainCoroutineDispatcher  // UI thread (platform-specific)
    val IO: CoroutineDispatcher        // Blocking I/O (64 threads on JVM/Native)
    val Unconfined: CoroutineDispatcher // No confinement, resumes in caller thread
}
```

### Main.immediate

```kotlin
Dispatchers.Main.immediate  // Dispatches immediately if already on main thread
```

### limitedParallelism

```kotlin
fun CoroutineDispatcher.limitedParallelism(
    parallelism: Int
): CoroutineDispatcher
```

Creates a view of this dispatcher with limited parallelism. Useful for rate-limiting concurrent access.

```kotlin
// Limit database access to 4 concurrent operations
val dbDispatcher = Dispatchers.IO.limitedParallelism(4)
```

---

## Flow Builders

### flow

```kotlin
fun <T> flow(
    block: suspend FlowCollector<T>.() -> Unit
): Flow<T>
```

Primary cold flow builder. Calls `emit(value)` to produce values.

### flowOf

```kotlin
fun <T> flowOf(vararg elements: T): Flow<T>
```

Creates a flow from fixed values.

### asFlow

```kotlin
fun <T> Iterable<T>.asFlow(): Flow<T>
fun <T> Sequence<T>.asFlow(): Flow<T>
fun <T> Array<T>.asFlow(): Flow<T>
fun IntRange.asFlow(): Flow<Int>
```

### callbackFlow

```kotlin
fun <T> callbackFlow(
    block: suspend ProducerScope<T>.() -> Unit
): Flow<T>
```

Wraps callback-based APIs. Must include `awaitClose { }` for cleanup.

### channelFlow

```kotlin
fun <T> channelFlow(
    block: suspend ProducerScope<T>.() -> Unit
): Flow<T>
```

Like `flow` but allows concurrent emission from multiple coroutines via `send()`.

---

## Flow Transform Operators

### map / mapNotNull

```kotlin
fun <T, R> Flow<T>.map(transform: suspend (T) -> R): Flow<R>
fun <T, R : Any> Flow<T>.mapNotNull(transform: suspend (T) -> R?): Flow<R>
```

### filter / filterNot / filterIsInstance

```kotlin
fun <T> Flow<T>.filter(predicate: suspend (T) -> Boolean): Flow<T>
fun <T> Flow<T>.filterNot(predicate: suspend (T) -> Boolean): Flow<T>
inline fun <reified R> Flow<*>.filterIsInstance(): Flow<R>
```

### transform

```kotlin
fun <T, R> Flow<T>.transform(
    transform: suspend FlowCollector<R>.(value: T) -> Unit
): Flow<R>
```

Most general operator. Can emit zero, one, or multiple values per input.

### scan / runningFold

```kotlin
fun <T, R> Flow<T>.scan(initial: R, operation: suspend (R, T) -> R): Flow<R>
fun <T, R> Flow<T>.runningFold(initial: R, operation: suspend (R, T) -> R): Flow<R>
```

Accumulates values, emitting each intermediate result. `scan` and `runningFold` are aliases.

### take / drop

```kotlin
fun <T> Flow<T>.take(count: Int): Flow<T>
fun <T> Flow<T>.drop(count: Int): Flow<T>
```

---

## Flow Flattening Operators

### flatMapLatest

```kotlin
fun <T, R> Flow<T>.flatMapLatest(
    transform: suspend (T) -> Flow<R>
): Flow<R>
```

Cancels the previous inner flow when a new value is emitted. Best for search-as-you-type patterns.

### flatMapMerge

```kotlin
fun <T, R> Flow<T>.flatMapMerge(
    concurrency: Int = DEFAULT_CONCURRENCY,
    transform: suspend (T) -> Flow<R>
): Flow<R>
```

Collects all inner flows concurrently (up to `concurrency` limit).

### flatMapConcat

```kotlin
fun <T, R> Flow<T>.flatMapConcat(
    transform: suspend (T) -> Flow<R>
): Flow<R>
```

Collects inner flows sequentially, one after another.

---

## Flow Combining Operators

### combine

```kotlin
fun <T1, T2, R> combine(
    flow: Flow<T1>,
    flow2: Flow<T2>,
    transform: suspend (T1, T2) -> R
): Flow<R>

// Also: combine(flow1, flow2, flow3, ...) up to 5 parameters
// And: combine(flows: Iterable<Flow<T>>, transform: ...)
```

Emits whenever ANY source emits, using the latest value from each source.

### merge

```kotlin
fun <T> merge(vararg flows: Flow<T>): Flow<T>
fun <T> Iterable<Flow<T>>.merge(): Flow<T>
```

Merges multiple flows into one, emitting values from all sources as they arrive.

### zip

```kotlin
fun <T1, T2, R> Flow<T1>.zip(
    other: Flow<T2>,
    transform: suspend (T1, T2) -> R
): Flow<R>
```

Pairs emissions 1:1 from two flows. Completes when either flow completes.

---

## Flow Rate-Limiting Operators

### debounce

```kotlin
fun <T> Flow<T>.debounce(timeoutMillis: Long): Flow<T>
fun <T> Flow<T>.debounce(timeout: Duration): Flow<T>
```

Emits only after a period of inactivity. Best for text input fields.

### sample

```kotlin
fun <T> Flow<T>.sample(periodMillis: Long): Flow<T>
fun <T> Flow<T>.sample(period: Duration): Flow<T>
```

Emits the latest value at fixed intervals. Useful for high-frequency data (sensor readings).

### buffer

```kotlin
fun <T> Flow<T>.buffer(
    capacity: Int = BUFFERED,
    onBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND
): Flow<T>
```

Decouples emission and collection into concurrent coroutines. Emission does not wait for collection.

### conflate

```kotlin
fun <T> Flow<T>.conflate(): Flow<T>
```

Equivalent to `buffer(CONFLATED)`. Keeps only the latest value, drops intermediate values when collector is slow.

---

## Flow Lifecycle Operators

### onStart

```kotlin
fun <T> Flow<T>.onStart(action: suspend FlowCollector<T>.() -> Unit): Flow<T>
```

Executes before the flow starts collecting. Can emit initial values.

### onEach

```kotlin
fun <T> Flow<T>.onEach(action: suspend (T) -> Unit): Flow<T>
```

Performs a side effect for each emitted value.

### onCompletion

```kotlin
fun <T> Flow<T>.onCompletion(
    action: suspend FlowCollector<T>.(cause: Throwable?) -> Unit
): Flow<T>
```

Executes when flow completes (normally or exceptionally). `cause` is null on normal completion.

### catch

```kotlin
fun <T> Flow<T>.catch(action: suspend FlowCollector<T>.(cause: Throwable) -> Unit): Flow<T>
```

Catches upstream exceptions only. Can emit recovery values. Does NOT catch downstream (collector) exceptions.

---

## Flow Context Operators

### flowOn

```kotlin
fun <T> Flow<T>.flowOn(context: CoroutineContext): Flow<T>
```

Changes the upstream context (dispatcher). Does NOT affect downstream operators or collector. Place after the operators that need the context change.

### launchIn

```kotlin
fun <T> Flow<T>.launchIn(scope: CoroutineScope): Job
```

Terminal operator that launches collection in the given scope. Returns the `Job` for cancellation.

---

## Flow Terminal Operators

### collect

```kotlin
suspend fun <T> Flow<T>.collect(collector: FlowCollector<T>)
suspend fun <T> Flow<T>.collect()  // discards values
```

### first / firstOrNull

```kotlin
suspend fun <T> Flow<T>.first(): T
suspend fun <T> Flow<T>.first(predicate: suspend (T) -> Boolean): T
suspend fun <T> Flow<T>.firstOrNull(): T?
```

### single / singleOrNull

```kotlin
suspend fun <T> Flow<T>.single(): T  // throws if 0 or 2+ elements
suspend fun <T> Flow<T>.singleOrNull(): T?
```

### toList / toSet

```kotlin
suspend fun <T> Flow<T>.toList(): List<T>
suspend fun <T> Flow<T>.toSet(): Set<T>
```

### fold / reduce

```kotlin
suspend fun <T, R> Flow<T>.fold(initial: R, operation: suspend (R, T) -> R): R
suspend fun <S, T : S> Flow<T>.reduce(operation: suspend (S, T) -> S): S
```

### any / all / none (New in 1.10.x)

```kotlin
suspend fun <T> Flow<T>.any(predicate: suspend (T) -> Boolean): Boolean
suspend fun <T> Flow<T>.all(predicate: suspend (T) -> Boolean): Boolean
suspend fun <T> Flow<T>.none(predicate: suspend (T) -> Boolean): Boolean
```

Short-circuiting boolean terminal operators. Cancel collection as soon as result is determined.

### count

```kotlin
suspend fun <T> Flow<T>.count(): Int
suspend fun <T> Flow<T>.count(predicate: suspend (T) -> Boolean): Int
```

---

## Flow Distinctness

### distinctUntilChanged

```kotlin
fun <T> Flow<T>.distinctUntilChanged(): Flow<T>
```

Filters out consecutive duplicate emissions using `equals()`. `StateFlow` has this built in.

### distinctUntilChangedBy

```kotlin
fun <T, K> Flow<T>.distinctUntilChangedBy(keySelector: (T) -> K): Flow<T>
```

---

## StateFlow

```kotlin
interface StateFlow<out T> : SharedFlow<T> {
    val value: T  // current value, always available
}

interface MutableStateFlow<T> : StateFlow<T>, MutableSharedFlow<T> {
    override var value: T
    fun compareAndSet(expect: T, update: T): Boolean
}

fun <T> MutableStateFlow(value: T): MutableStateFlow<T>
```

Properties:
- Always has a current value (`value` property)
- Replay of 1 (new collectors get the latest value immediately)
- Uses `equals()` for deduplication (does NOT emit if new value equals old)
- Thread-safe reads and writes

### stateIn

```kotlin
fun <T> Flow<T>.stateIn(
    scope: CoroutineScope,
    started: SharingStarted,
    initialValue: T
): StateFlow<T>

suspend fun <T> Flow<T>.stateIn(
    scope: CoroutineScope
): StateFlow<T>  // suspends until first value
```

---

## SharedFlow

```kotlin
interface SharedFlow<out T> : Flow<T> {
    val replayCache: List<T>
}

interface MutableSharedFlow<T> : SharedFlow<T>, FlowCollector<T> {
    val subscriptionCount: StateFlow<Int>
    suspend fun emit(value: T)
    fun tryEmit(value: T): Boolean
    fun resetReplayCache()
}

fun <T> MutableSharedFlow(
    replay: Int = 0,
    extraBufferCapacity: Int = 0,
    onBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND
): MutableSharedFlow<T>
```

### shareIn

```kotlin
fun <T> Flow<T>.shareIn(
    scope: CoroutineScope,
    started: SharingStarted,
    replay: Int = 0
): SharedFlow<T>
```

---

## SharingStarted

```kotlin
companion object {
    val Eagerly: SharingStarted      // Starts immediately, never stops
    val Lazily: SharingStarted       // Starts on first subscriber, never stops

    fun WhileSubscribed(
        stopTimeoutMillis: Long = 0,
        replayExpirationMillis: Long = Long.MAX_VALUE
    ): SharingStarted
    // Stops after last subscriber leaves (with optional delay)
}
```

---

## Channels

```kotlin
fun <E> Channel(
    capacity: Int = RENDEZVOUS,
    onBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND,
    onUndeliveredElement: ((E) -> Unit)? = null
): Channel<E>
```

### Channel constants

| Constant | Value | Behavior |
|----------|-------|----------|
| `Channel.RENDEZVOUS` | 0 | No buffer, sender suspends until receiver ready |
| `Channel.BUFFERED` | 64 | Default buffer size |
| `Channel.CONFLATED` | -1 | Keeps only latest, drops older |
| `Channel.UNLIMITED` | Int.MAX_VALUE | Unlimited buffer |

### SendChannel

```kotlin
interface SendChannel<in E> {
    suspend fun send(element: E)
    fun trySend(element: E): ChannelResult<Unit>
    fun close(cause: Throwable? = null): Boolean
}
```

### ReceiveChannel

```kotlin
interface ReceiveChannel<out E> {
    suspend fun receive(): E
    fun tryReceive(): ChannelResult<E>
    suspend fun receiveCatching(): ChannelResult<E>
    operator fun iterator(): ChannelIterator<E>
}

suspend fun <E> ReceiveChannel<E>.consumeEach(action: (E) -> Unit)
```

### produce

```kotlin
fun <E> CoroutineScope.produce(
    context: CoroutineContext = EmptyCoroutineContext,
    capacity: Int = 0,
    block: suspend ProducerScope<E>.() -> Unit
): ReceiveChannel<E>
```

---

## Concurrency Primitives

### Mutex

```kotlin
fun Mutex(locked: Boolean = false): Mutex

interface Mutex {
    val isLocked: Boolean
    suspend fun lock(owner: Any? = null)
    fun unlock(owner: Any? = null)
    fun tryLock(owner: Any? = null): Boolean
}

suspend inline fun <T> Mutex.withLock(owner: Any? = null, action: () -> T): T
```

### Semaphore

```kotlin
fun Semaphore(permits: Int, acquiredPermits: Int = 0): Semaphore

interface Semaphore {
    val availablePermits: Int
    suspend fun acquire()
    fun release()
    fun tryAcquire(): Boolean
}

suspend inline fun <T> Semaphore.withPermit(action: () -> T): T
```

---

## Exception Handling

### CoroutineExceptionHandler

```kotlin
fun CoroutineExceptionHandler(
    handler: (CoroutineContext, Throwable) -> Unit
): CoroutineExceptionHandler
```

Only works on root coroutines. Installed via `CoroutineScope(SupervisorJob() + handler)`.

### retry

```kotlin
fun <T> Flow<T>.retry(
    retries: Long = Long.MAX_VALUE,
    predicate: suspend (Throwable) -> Boolean = { true }
): Flow<T>

fun <T> Flow<T>.retryWhen(
    predicate: suspend FlowCollector<T>.(cause: Throwable, attempt: Long) -> Boolean
): Flow<T>
```

---

## CoroutineStart

```kotlin
enum class CoroutineStart {
    DEFAULT,    // Immediately scheduled
    LAZY,       // Starts only when explicitly started or awaited
    ATOMIC,     // Cannot be cancelled before first suspension
    UNDISPATCHED // Executes in current thread until first suspension
}
```

---

## Utility Functions

### delay

```kotlin
suspend fun delay(timeMillis: Long)
suspend fun delay(duration: Duration)
```

### yield

```kotlin
suspend fun yield()
```

Yields the thread to other coroutines. Checks for cancellation.

### ensureActive

```kotlin
fun CoroutineScope.ensureActive()
fun CoroutineContext.ensureActive()
fun Job.ensureActive()
```

Throws `CancellationException` if the scope/job is no longer active.

### isActive

```kotlin
val CoroutineScope.isActive: Boolean
```

### currentCoroutineContext

```kotlin
suspend fun currentCoroutineContext(): CoroutineContext
```

### NonCancellable

```kotlin
object NonCancellable : Job
```

Used with `withContext(NonCancellable)` to run cleanup code that must not be cancelled (e.g., in `finally` blocks).

### joinAll / awaitAll

```kotlin
suspend fun joinAll(vararg jobs: Job)
suspend fun <T> awaitAll(vararg deferreds: Deferred<T>): List<T>
suspend fun <T> Collection<Deferred<T>>.awaitAll(): List<T>
```
