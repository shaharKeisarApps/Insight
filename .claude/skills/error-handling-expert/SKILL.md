---
name: error-handling-expert
description: Expert guidance on KMP error handling. Use for Result types, sealed error hierarchies, network error mapping, Circuit error states, and global error handling.
---

# Error Handling Expert Skill

## Overview

Consistent, typed error handling across all layers of a Kotlin Multiplatform application. This skill defines how errors originate, propagate, and are presented to users through a Clean Architecture stack using Circuit MVI, Ktor, Metro DI, and Store5.

## When to use

- **API Errors**: Mapping HTTP status codes and Ktor exceptions to domain errors.
- **Database Errors**: Handling SQLDelight / Room failures.
- **Validation**: Input validation before network or database calls.
- **User-Facing Error Messages**: Deciding what the user sees vs. what gets logged.
- **Global Error Handling**: CoroutineExceptionHandler, crash analytics, unhandled exceptions.

## Quick Reference

For detailed API patterns and type definitions, see [reference.md](reference.md).
For complete production code examples, see [examples.md](examples.md).

## Core Rules

1. **Sealed class hierarchies for domain errors.** Every feature defines its errors as subtypes of a common `AppError` sealed hierarchy. No raw exceptions cross layer boundaries.
2. **Map at the repository boundary.** HTTP status codes, Ktor exceptions, and database exceptions are caught and mapped to `AppError` subtypes inside the repository. Upper layers never see platform-specific throwables.
3. **Never catch `CancellationException`.** Kotlin coroutines rely on `CancellationException` for structured concurrency. Catching it breaks cancellation propagation. Use `suspendRunCatching` (see reference) instead of `runCatching`.
4. **Circuit State represents error/loading/success.** The Presenter produces a sealed `UiState` with `Loading`, `Success`, and `Error` variants. The UI renders each variant declaratively. No side-channel error communication.
5. **Errors are values, not control flow.** Prefer returning `Either<AppError, T>` or a sealed result over throwing exceptions in domain/use-case code.

## Error Propagation Through Clean Architecture Layers

```
Network (Ktor)
  HttpException / IOException / Timeout
       |
       v
Repository
  catch + map --> AppError.Network.*
       |
       v
Use Case / Domain
  validate + combine --> AppError.Validation.* / AppError.Business.*
       |
       v
Presenter (Circuit)
  produce UiState.Error(appError)
       |
       v
UI (Compose)
  render user-facing message
```

Each layer only knows about the error types defined at its own level or below. The UI never receives a raw `HttpException`.

## Best Practices

- **Separate developer errors from user-facing messages.** `AppError` carries both a technical `cause` (for logging) and a user-displayable `message` resource (for UI).
- **Use retry with exponential backoff.** Transient network errors (timeout, 503) should be retried automatically before surfacing to the user.
- **Report errors to crash analytics.** Non-recoverable errors go to Crashlytics/Sentry via an `ErrorReporter` expect/actual interface. Never silently discard them.
- **Provide actionable error states.** Error UI should include a retry button or navigation suggestion, not just a message.

## Common Pitfalls

| Pitfall | Why it is dangerous | Fix |
|---------|--------------------|----|
| `runCatching` in suspend functions | Catches `CancellationException`, breaking structured concurrency | Use `suspendRunCatching` that rethrows `CancellationException` |
| Swallowing errors silently | Bugs become invisible; users see blank screens | Always log + report; always emit an error state |
| Inconsistent error types across features | UI error handling becomes a mess of `when` branches | Use a single `AppError` sealed hierarchy project-wide |
| Showing raw exception messages to users | Exposes internals; messages are not localized | Map every `AppError` to a string resource ID |
| Catching `Throwable` instead of `Exception` | Catches `OutOfMemoryError`, `StackOverflowError` | Catch `Exception`; let `Error` subclasses crash |
