---
name: logging-expert
description: Expert guidance on Kermit logging for KMP. Use for configuring Logger, LogWriters, severity levels, crash reporting integration, Ktor logging, Metro DI setup, production vs debug configuration, and structured tagging.
---

# Kermit Logging Expert Skill (v2.0.8)

## Overview

Kermit is the standard logging library for Kotlin Multiplatform by Touchlab. It provides a unified `Logger` API across all KMP targets (Android, iOS, macOS, JVM, JS, Linux, Windows) with platform-specific output via the `LogWriter` system. Kermit is lightweight, supports lazy message evaluation, and integrates with crash reporting services.

## When to Use

- **Debug logging**: Trace execution flow, variable state, and lifecycle events during development.
- **Production crash reporting**: Forward `Error` and `Assert` logs to Crashlytics, Sentry, or Bugsnag.
- **Network request logging**: Bridge Kermit into Ktor's `Logging` plugin for HTTP traffic inspection.
- **Analytics events**: Log structured events at `Info` severity for analytics pipelines.
- **Error diagnostics**: Attach `Throwable` instances to log calls for stack trace capture.

## Quick Reference

See [reference.md](reference.md) for the full API surface (Logger, LogWriter, Severity, Config).
See [examples.md](examples.md) for production-quality code with Metro DI integration.

## Core API

```kotlin
// Lambda syntax (preferred -- lazy evaluation, zero cost when filtered)
Logger.d { "User loaded: $userId" }
Logger.i { "Screen opened: HomeScreen" }
Logger.w { "Cache miss for key: $key" }
Logger.e(throwable) { "Failed to fetch user" }
Logger.v { "Layout pass completed in ${elapsed}ms" }
Logger.a { "Critical invariant violated" }

// Tagged logger
val log = Logger.withTag("MyPresenter")
log.d { "State updated" }
```

## Severity Levels

| Level | Constant | Use Case | Production |
|-------|----------|----------|------------|
| **Verbose** | `Severity.Verbose` | Fine-grained trace output | Filtered out |
| **Debug** | `Severity.Debug` | Development diagnostics | Filtered out |
| **Info** | `Severity.Info` | Noteworthy runtime events | Visible |
| **Warn** | `Severity.Warn` | Recoverable issues, deprecations | Visible |
| **Error** | `Severity.Error` | Failures requiring attention | Visible + crash report |
| **Assert** | `Severity.Assert` | Critical invariant violations | Visible + crash report |

## LogWriter System

LogWriters receive log messages and route them to platform-specific outputs.

| Writer | Output | Platform |
|--------|--------|----------|
| `CommonWriter` | `println()` | All (fallback) |
| `platformLogWriter()` | Logcat / OSLog / console.log | Android / Apple / JS |
| `CrashlyticsLogWriter` | Firebase Crashlytics | Android + iOS |
| `BugsnagLogWriter` | Bugsnag | Android + iOS |
| Custom `LogWriter` | Any destination | All |

`platformLogWriter()` is an `expect`/`actual` factory that returns `LogcatWriter` on Android, `OSLogWriter` on Apple targets, and `ConsoleActualWriter` on JS/Wasm.

## Configuration

```kotlin
// Global configuration (simple setup)
Logger.setLogWriters(platformLogWriter())
Logger.setMinSeverity(Severity.Debug)
Logger.setTag("MyApp")

// Static config (immutable, thread-safe)
val logger = Logger(
    config = loggerConfigInit(platformLogWriter(), minSeverity = Severity.Info),
    tag = "MyFeature"
)

// Mutable config (runtime changes)
val config = MutableLoggerConfig().apply {
    minSeverity = Severity.Debug
    logWriterList = listOf(platformLogWriter(), analyticsWriter)
}
val logger = Logger(config = config, tag = "MyFeature")
```

## Metro DI Integration

Provide `Logger` through Metro dependency injection rather than using the global `Logger` companion in production code:

```kotlin
@ContributesTo(AppScope::class)
interface LoggingModule {
    companion object {
        @Provides
        @SingleIn(AppScope::class)
        fun provideLogger(): Logger = Logger(
            config = loggerConfigInit(platformLogWriter(), minSeverity = Severity.Info),
            tag = "App"
        )
    }
}
```

## Core Rules

1. **Use lambda syntax** `Logger.d { "msg" }` not `Logger.d("msg")`. The lambda is only evaluated if the severity passes the filter, avoiding string concatenation overhead in production.

2. **Never log sensitive data**. Tokens, passwords, PII, API keys, and session IDs must never appear in log messages. Sanitize before logging.

3. **Set minimum severity to `Info` for production**. Debug and Verbose logs add noise and may leak implementation details. Use `expect`/`actual` or build config to toggle.

4. **Use tags for filtering**. Every logical component should use `Logger.withTag("ClassName")` so logs can be filtered by source in Logcat or console.

5. **Provide Logger via DI**. Inject `Logger` instances via Metro rather than calling `Logger` companion directly. This enables testing and per-scope configuration.

6. **Attach throwables to error logs**. Always pass the exception: `Logger.e(exception) { "message" }`. This enables crash reporters to capture stack traces.

7. **Use `platformLogWriter()`** not `CommonWriter` as the default. Platform writers use native logging (Logcat, OSLog) which integrates with system tools.

## Common Pitfalls

| Pitfall | Consequence | Fix |
|---------|-------------|-----|
| Using string overload `Logger.d("msg $expensive")` | String built even when filtered | Use lambda: `Logger.d { "msg $expensive" }` |
| Logging PII or tokens | Security/compliance violation | Sanitize all user data before logging |
| No `setMinSeverity` in release builds | Verbose logs in production | Set `Severity.Info` minimum for release |
| Using global `Logger` everywhere | Untestable, no per-scope config | Inject via Metro DI |
| Forgetting throwable in `Logger.e` | Crash reporter misses stack trace | Always pass: `Logger.e(throwable) { "msg" }` |
| Creating Logger per function call | Unnecessary allocations | Create once with `withTag`, store in property |

## Gradle Dependencies

```toml
# gradle/libs.versions.toml
[versions]
kermit = "2.0.8"

[libraries]
kermit = { module = "co.touchlab:kermit", version.ref = "kermit" }
kermit-crashlytics = { module = "co.touchlab:kermit-crashlytics", version.ref = "kermit" }
kermit-bugsnag = { module = "co.touchlab:kermit-bugsnag", version.ref = "kermit" }
```

```kotlin
// build.gradle.kts (shared module)
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kermit)
        }
        // Add crash reporting modules only on targets that support them
        androidMain.dependencies {
            implementation(libs.kermit.crashlytics) // if using Firebase
        }
    }
}
```
