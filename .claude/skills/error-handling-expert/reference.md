# Error Handling API Reference

## kotlin.Result vs Custom Sealed Class

| Aspect | `kotlin.Result<T>` | Custom `AppError` sealed class |
|--------|--------------------|---------------------------------|
| Error type | Untyped `Throwable` | Typed sealed subtypes |
| Pattern matching | Limited (`isSuccess`, `isFailure`) | Exhaustive `when` on sealed subtypes |
| Multiple error kinds | Single `Throwable` | `NetworkError`, `ValidationError`, etc. |
| Serialization | Not straightforward | Fully controllable |
| Recommendation | Utility code, inline wrappers | Domain and feature-level error handling |

Use `kotlin.Result` only inside low-level utility functions (e.g., `suspendRunCatching`). For everything above the repository layer, use the sealed `AppError` hierarchy.

## AppError Sealed Hierarchy

```kotlin
sealed class AppError(
    open val message: String,
    open val cause: Throwable? = null,
) {
    // -- Network --
    sealed class Network(
        override val message: String,
        override val cause: Throwable? = null,
    ) : AppError(message, cause) {

        data class Timeout(
            override val cause: Throwable? = null,
        ) : Network("Request timed out", cause)

        data class NoConnection(
            override val cause: Throwable? = null,
        ) : Network("No internet connection", cause)

        data class ServerError(
            val code: Int,
            val body: String? = null,
            override val cause: Throwable? = null,
        ) : Network("Server error ($code)", cause)

        data class Unauthorized(
            override val cause: Throwable? = null,
        ) : Network("Session expired", cause)

        data class Forbidden(
            override val cause: Throwable? = null,
        ) : Network("Access denied", cause)

        data class NotFound(
            val resource: String = "Resource",
            override val cause: Throwable? = null,
        ) : Network("$resource not found", cause)

        data class RateLimited(
            val retryAfterSeconds: Int? = null,
            override val cause: Throwable? = null,
        ) : Network("Too many requests", cause)

        data class Unknown(
            override val message: String = "An unexpected network error occurred",
            override val cause: Throwable? = null,
        ) : Network(message, cause)
    }

    // -- Database --
    sealed class Database(
        override val message: String,
        override val cause: Throwable? = null,
    ) : AppError(message, cause) {

        data class ReadFailed(
            override val cause: Throwable? = null,
        ) : Database("Failed to read data", cause)

        data class WriteFailed(
            override val cause: Throwable? = null,
        ) : Database("Failed to save data", cause)

        data class NotFound(
            val entity: String = "Record",
            override val cause: Throwable? = null,
        ) : Database("$entity not found", cause)
    }

    // -- Validation --
    sealed class Validation(
        override val message: String,
    ) : AppError(message) {

        data class InvalidField(
            val field: String,
            override val message: String,
        ) : Validation(message)

        data class MissingField(
            val field: String,
        ) : Validation("$field is required")

        data class MultipleErrors(
            val errors: List<Validation>,
        ) : Validation("Multiple validation errors")
    }

    // -- Business Logic --
    sealed class Business(
        override val message: String,
        override val cause: Throwable? = null,
    ) : AppError(message, cause) {

        data class NotAuthenticated(
            override val cause: Throwable? = null,
        ) : Business("Please sign in to continue", cause)

        data class FeatureDisabled(
            val feature: String,
        ) : Business("$feature is currently unavailable")

        data class OperationFailed(
            override val message: String,
            override val cause: Throwable? = null,
        ) : Business(message, cause)
    }
}
```

## HTTP Status Code Mapping

```kotlin
fun mapHttpError(statusCode: Int, body: String?, cause: Throwable? = null): AppError.Network {
    return when (statusCode) {
        401 -> AppError.Network.Unauthorized(cause)
        403 -> AppError.Network.Forbidden(cause)
        404 -> AppError.Network.NotFound(cause = cause)
        408 -> AppError.Network.Timeout(cause)
        429 -> AppError.Network.RateLimited(cause = cause)
        in 500..599 -> AppError.Network.ServerError(statusCode, body, cause)
        else -> AppError.Network.Unknown("HTTP $statusCode", cause)
    }
}
```

## suspendRunCatching

`kotlin.runCatching` catches **all** `Throwable` instances, including `CancellationException`. This breaks structured concurrency. Use the following replacement in all suspend functions.

```kotlin
/**
 * Like [runCatching], but rethrows [CancellationException] to preserve
 * structured concurrency.
 */
inline fun <T> suspendRunCatching(block: () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

> Place this in a shared `core-common` module so every feature can import it.

## CoroutineExceptionHandler Setup

Install a `CoroutineExceptionHandler` at the application scope to catch truly unhandled exceptions that escape structured concurrency (e.g., from `launch` blocks without their own `try-catch`).

```kotlin
fun createAppCoroutineExceptionHandler(
    errorReporter: ErrorReporter,
): CoroutineExceptionHandler {
    return CoroutineExceptionHandler { _, throwable ->
        errorReporter.report(throwable)
    }
}

// In your application graph (Metro)
@Provides
@SingleIn(AppScope::class)
fun provideAppScope(
    errorReporter: ErrorReporter,
): CoroutineScope {
    return CoroutineScope(
        SupervisorJob() + Dispatchers.Default + createAppCoroutineExceptionHandler(errorReporter)
    )
}
```

## Retry Strategies

### Exponential Backoff with Jitter

```kotlin
suspend fun <T> retryWithBackoff(
    maxRetries: Int = 3,
    initialDelayMs: Long = 500L,
    maxDelayMs: Long = 10_000L,
    factor: Double = 2.0,
    shouldRetry: (Exception) -> Boolean = { it.isRetryable() },
    block: suspend () -> T,
): T {
    var currentDelay = initialDelayMs
    repeat(maxRetries) { attempt ->
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (attempt == maxRetries - 1 || !shouldRetry(e)) throw e
            val jitter = (0..(currentDelay / 4).toInt()).random().toLong()
            delay(currentDelay + jitter)
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelayMs)
        }
    }
    error("Unreachable")
}

fun Exception.isRetryable(): Boolean = when (this) {
    is java.net.SocketTimeoutException -> true
    is java.io.IOException -> true
    is io.ktor.client.network.sockets.ConnectTimeoutException -> true
    else -> false
}
```

## Error Reporting Interface (expect/actual)

```kotlin
// commonMain
expect class ErrorReporter {
    fun report(throwable: Throwable)
    fun report(error: AppError)
    fun setUserId(userId: String?)
    fun log(message: String)
}

// androidMain
actual class ErrorReporter {
    actual fun report(throwable: Throwable) {
        FirebaseCrashlytics.getInstance().recordException(throwable)
    }
    actual fun report(error: AppError) {
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.log("AppError: ${error::class.simpleName} - ${error.message}")
        error.cause?.let { crashlytics.recordException(it) }
    }
    actual fun setUserId(userId: String?) {
        FirebaseCrashlytics.getInstance().setUserId(userId.orEmpty())
    }
    actual fun log(message: String) {
        FirebaseCrashlytics.getInstance().log(message)
    }
}

// iosMain
actual class ErrorReporter {
    actual fun report(throwable: Throwable) {
        // Crashlytics iOS SDK or Sentry
        NSLog("ERROR: ${throwable.message}")
    }
    actual fun report(error: AppError) {
        NSLog("AppError: ${error::class.simpleName} - ${error.message}")
    }
    actual fun setUserId(userId: String?) { /* platform impl */ }
    actual fun log(message: String) { NSLog(message) }
}
```
