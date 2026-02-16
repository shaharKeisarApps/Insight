# Kermit 2.0.8 -- API Reference

> Target: `co.touchlab:kermit:2.0.8` for Kotlin Multiplatform

---

## Logger Class

The primary logging interface. Can be used via the global `Logger` companion or as a local instance.

### Constructor

```kotlin
class Logger(
    config: LoggerConfig = MutableLoggerConfig(),
    tag: String = ""
)
```

- `config` -- Determines which `LogWriter` instances receive messages and the minimum `Severity` filter.
- `tag` -- Default tag attached to all messages from this logger instance.

### Logging Methods

Each severity level has two overloads: a lambda (preferred) and a direct string.

```kotlin
// Lambda overloads (lazy evaluation -- preferred)
fun v(throwable: Throwable? = null, tag: String = this.tag, message: () -> String)
fun d(throwable: Throwable? = null, tag: String = this.tag, message: () -> String)
fun i(throwable: Throwable? = null, tag: String = this.tag, message: () -> String)
fun w(throwable: Throwable? = null, tag: String = this.tag, message: () -> String)
fun e(throwable: Throwable? = null, tag: String = this.tag, message: () -> String)
fun a(throwable: Throwable? = null, tag: String = this.tag, message: () -> String)

// String overloads (eager evaluation)
fun v(messageString: String, throwable: Throwable? = null, tag: String = this.tag)
fun d(messageString: String, throwable: Throwable? = null, tag: String = this.tag)
fun i(messageString: String, throwable: Throwable? = null, tag: String = this.tag)
fun w(messageString: String, throwable: Throwable? = null, tag: String = this.tag)
fun e(messageString: String, throwable: Throwable? = null, tag: String = this.tag)
fun a(messageString: String, throwable: Throwable? = null, tag: String = this.tag)
```

Parameters:
- `throwable` -- Optional exception. Passed to `LogWriter.log()` for stack trace capture.
- `tag` -- Overrides the default tag for this single call.
- `message` -- Lambda returning the message string. Only invoked if severity >= minSeverity.
- `messageString` -- Eagerly evaluated message string.

### withTag

```kotlin
fun withTag(tag: String): Logger
```

Returns a new `Logger` instance sharing the same `LoggerConfig` but with a different default tag. The config (writers, severity) is shared, not copied.

```kotlin
val log = Logger.withTag("UserRepository")
log.d { "Fetching user: $userId" }
```

### General log Method

```kotlin
fun log(
    severity: Severity,
    tag: String = this.tag,
    throwable: Throwable? = null,
    message: () -> String
)
```

Logs at an arbitrary severity. Useful when severity is determined at runtime.

---

## Logger.Companion (Global Logger)

The `Logger` companion object is itself a `Logger` instance backed by a global `MutableLoggerConfig`. It provides convenience methods for quick setup.

### setLogWriters

```kotlin
fun setLogWriters(vararg logWriter: LogWriter)
fun setLogWriters(logWriters: List<LogWriter>)
```

Replaces the global logger's writer list. Clears any previously configured writers.

```kotlin
Logger.setLogWriters(platformLogWriter(), analyticsWriter)
```

### addLogWriter

```kotlin
fun addLogWriter(vararg logWriter: LogWriter)
```

Appends writers to the existing list without clearing.

### setMinSeverity

```kotlin
fun setMinSeverity(severity: Severity)
```

Sets the minimum severity for the global logger. Messages below this level are discarded.

```kotlin
Logger.setMinSeverity(Severity.Info) // filters out Verbose and Debug
```

### setTag

```kotlin
fun setTag(tag: String)
```

Sets the default tag for the global logger instance.

---

## Severity Enum

```kotlin
enum class Severity {
    Verbose,
    Debug,
    Info,
    Warn,
    Error,
    Assert
}
```

Ordered by increasing importance. The `minSeverity` filter discards all messages with severity strictly below the configured level.

| Level | Android Logcat | Apple OSLog | Typical Use |
|-------|---------------|-------------|-------------|
| `Verbose` | `Log.v` | `.debug` | Trace-level detail |
| `Debug` | `Log.d` | `.debug` | Development diagnostics |
| `Info` | `Log.i` | `.info` | Runtime milestones |
| `Warn` | `Log.w` | `.default` | Recoverable issues |
| `Error` | `Log.e` | `.error` | Failures, exceptions |
| `Assert` | `Log.wtf` | `.fault` | Critical invariant breaks |

---

## LogWriter

Abstract base class for log output destinations.

```kotlin
abstract class LogWriter {
    abstract fun log(severity: Severity, message: String, tag: String, throwable: Throwable?)
    open fun isLoggable(tag: String, severity: Severity): Boolean = true
}
```

- `log` -- Called for every message that passes the severity filter.
- `isLoggable` -- Optional per-writer filter. Return `false` to skip this writer for specific tags or severities.

### Built-in Writers

#### CommonWriter

```kotlin
class CommonWriter(
    private val logFormatter: LogFormatter = DefaultLogFormatter
) : LogWriter()
```

Outputs via `println()`. Works on all platforms. Suitable for JVM CLI tools or as a fallback.

#### platformLogWriter()

```kotlin
expect fun platformLogWriter(logFormatter: LogFormatter = DefaultLogFormatter): LogWriter
```

Factory function returning the platform-native writer:
- **Android**: `LogcatWriter` -- writes to Android Logcat via `android.util.Log`.
- **iOS/macOS/tvOS/watchOS**: `OSLogWriter` -- writes to Apple unified logging via `os_log`.
- **JVM**: `SystemWriter` -- writes to `System.out`/`System.err`.
- **JS/Wasm**: `ConsoleActualWriter` -- writes to `console.log`/`console.warn`/`console.error`.

#### LogcatWriter (Android)

```kotlin
class LogcatWriter(
    private val logFormatter: LogFormatter = DefaultLogFormatter
) : LogWriter()
```

Delegates to `android.util.Log`. Tag is truncated to 23 characters per Android's limit.

#### OSLogWriter (Apple)

```kotlin
class OSLogWriter(
    private val logFormatter: LogFormatter = DefaultLogFormatter,
    private val subsystem: String = "",
    private val category: String = ""
) : LogWriter()
```

Delegates to `os_log` with configurable subsystem and category. Visible in Console.app.

---

## LogFormatter

Interface for formatting log messages before they reach a `LogWriter`.

```kotlin
interface LogFormatter {
    fun formatMessage(severity: Severity, tag: Tag, message: Message): String
    fun formatTag(tag: Tag): String
}
```

### Built-in Formatters

| Formatter | Output | Use Case |
|-----------|--------|----------|
| `DefaultLogFormatter` | `(TAG) message` | General purpose |
| `NoTagLogFormatter` | `message` | When tags are handled by the platform (e.g., Logcat) |

---

## LoggerConfig

Interface defining logger configuration.

```kotlin
interface LoggerConfig {
    val minSeverity: Severity
    val logWriterList: List<LogWriter>
}
```

### StaticConfig

Immutable implementation. Thread-safe by construction.

```kotlin
class StaticConfig(
    override val minSeverity: Severity = Severity.Verbose,
    override val logWriterList: List<LogWriter> = listOf()
) : LoggerConfig
```

### MutableLoggerConfig

Mutable implementation. Allows runtime changes to severity and writers.

```kotlin
class MutableLoggerConfig(
    override var minSeverity: Severity = Severity.Verbose,
    override var logWriterList: MutableList<LogWriter> = mutableListOf()
) : LoggerConfig
```

The global `Logger` companion uses a `MutableLoggerConfig` internally.

### loggerConfigInit Helper

```kotlin
fun loggerConfigInit(
    vararg logWriters: LogWriter,
    minSeverity: Severity = Severity.Verbose
): LoggerConfig = StaticConfig(
    minSeverity = minSeverity,
    logWriterList = logWriters.toList()
)
```

Convenience factory for creating a `StaticConfig`.

---

## Crash Reporting Modules

### kermit-crashlytics

**Artifact**: `co.touchlab:kermit-crashlytics:2.0.8`

```kotlin
class CrashlyticsLogWriter(
    private val minSeverity: Severity = Severity.Warn,
    private val minCrashSeverity: Severity = Severity.Error
) : LogWriter()
```

- Messages at `minSeverity` and above are logged to Crashlytics as breadcrumbs.
- Messages at `minCrashSeverity` and above with a `Throwable` record a non-fatal exception.
- Requires Firebase Crashlytics SDK configured in the Android/iOS app.

### kermit-bugsnag

**Artifact**: `co.touchlab:kermit-bugsnag:2.0.8`

```kotlin
class BugsnagLogWriter(
    private val minSeverity: Severity = Severity.Warn,
    private val minCrashSeverity: Severity = Severity.Error
) : LogWriter()
```

Same pattern as `CrashlyticsLogWriter` but routes to the Bugsnag SDK.

---

## Key Imports

```kotlin
// Core
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.LoggerConfig
import co.touchlab.kermit.StaticConfig
import co.touchlab.kermit.MutableLoggerConfig
import co.touchlab.kermit.loggerConfigInit

// Formatters
import co.touchlab.kermit.LogFormatter
import co.touchlab.kermit.DefaultLogFormatter
import co.touchlab.kermit.NoTagLogFormatter

// Platform writer factory
import co.touchlab.kermit.platformLogWriter

// Writers
import co.touchlab.kermit.CommonWriter

// Crash reporting (separate artifacts)
import co.touchlab.kermit.crashlytics.CrashlyticsLogWriter
import co.touchlab.kermit.bugsnag.BugsnagLogWriter
```

---

## Gradle Dependencies (libs.versions.toml)

```toml
[versions]
kermit = "2.0.8"

[libraries]
kermit = { module = "co.touchlab:kermit", version.ref = "kermit" }
kermit-core = { module = "co.touchlab:kermit-core", version.ref = "kermit" }
kermit-crashlytics = { module = "co.touchlab:kermit-crashlytics", version.ref = "kermit" }
kermit-bugsnag = { module = "co.touchlab:kermit-bugsnag", version.ref = "kermit" }
```

```kotlin
// build.gradle.kts (shared module)
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kermit)
            // kermit-core is a transitive dependency of kermit; no need to add explicitly
        }
        androidMain.dependencies {
            implementation(libs.kermit.crashlytics) // Firebase Crashlytics
            // OR
            implementation(libs.kermit.bugsnag)     // Bugsnag
        }
        iosMain.dependencies {
            implementation(libs.kermit.crashlytics) // CrashKiOS bridges to Crashlytics
            // OR
            implementation(libs.kermit.bugsnag)     // Bugsnag iOS
        }
    }
}
```

**Note**: The `kermit` artifact includes `kermit-core` transitively. You only need to declare `kermit` in `commonMain`. The crash reporting modules (`kermit-crashlytics`, `kermit-bugsnag`) should be added per platform since they depend on platform-specific SDKs.
