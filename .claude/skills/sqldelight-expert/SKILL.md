---
name: sqldelight-expert
description: Expert guidance on SQLDelight for KMP. Use for database schemas, typed queries, migrations, transactions, and integration with Store5 SourceOfTruth.
---

# SQLDelight Expert Skill

## Overview

SQLDelight generates type-safe Kotlin APIs from SQL statements. It is THE KMP database standard. You write `.sq` files with real SQL, and the Gradle plugin generates typesafe Kotlin data classes and query functions at compile time. Schema, statements, and migrations are all verified at compile time -- runtime SQL errors become build errors.

## When to use

- **Local Persistence**: Structured data that survives app restarts.
- **Offline-First**: Caching network data locally for offline access.
- **Reactive Caching**: Flow-based queries that re-emit when underlying data changes.
- **Search / Filtering**: Complex queries with WHERE, JOIN, GROUP BY that benefit from SQLite indexes.

## Quick Reference

See [reference.md](reference.md) for .sq syntax, driver setup, adapters, and migrations.
See [examples.md](examples.md) for full production implementations with Metro DI and Store5.

## Core Rules

1. **Write SQL first.** Define your schema and queries in `.sq` files. The generated Kotlin follows from that.
2. **Use Flow-based queries for reactivity.** `query.asFlow().mapToList(Dispatchers.IO)` re-emits automatically when rows change.
3. **Always use column adapters for custom types.** Enums, Instant, JSON blobs -- map them through `ColumnAdapter<KotlinType, SqlType>`.
4. **Integrate with Store5 SourceOfTruth.** SQLDelight Flow queries are the ideal `reader` for `SourceOfTruth.of()`.
5. **One driver per platform.** Use `expect`/`actual` to provide the correct `SqlDriver` on each target.

## Driver Setup Per Platform

| Platform | Driver Class | Dependency |
|----------|-------------|------------|
| Android | `AndroidSqliteDriver` | `app.cash.sqldelight:android-driver` |
| iOS / Native | `NativeSqliteDriver` | `app.cash.sqldelight:native-driver` |
| JVM / Desktop | `JdbcSqliteDriver` | `app.cash.sqldelight:sqlite-driver` |
| JS / Wasm | `WebWorkerDriver` / `SqlJsDriver` | `app.cash.sqldelight:sqljs-driver` |

## Best Practices

- **Use transactions for batch operations.** Wrapping N inserts in `transaction {}` is orders of magnitude faster than N individual inserts.
- **Enable WAL mode for concurrency.** WAL (Write-Ahead Logging) allows concurrent reads during writes on Android and iOS.
- **Use `.sqm` migration files for schema changes.** Never modify the original CREATE TABLE after first release -- add versioned migrations.
- **Keep queries in `.sq` files, not in Kotlin.** This ensures compile-time verification and keeps SQL centralized.
- **Use named parameters (`:name`) for clarity** in complex queries instead of positional `?` placeholders.

## Common Pitfalls

- **Forgetting driver `expect`/`actual`.** Each platform needs its own driver implementation. Missing one causes a compile error only on that target.
- **Not closing the driver.** `SqlDriver` must be closed when the database is no longer needed to avoid resource leaks, especially on iOS.
- **SQLite threading on iOS.** `NativeSqliteDriver` is NOT thread-safe by default. Access must be serialized (single-threaded dispatcher or `co.touchlab:stately-common` freezing).
- **Forgetting imports in `.sq` files.** Custom Kotlin types used in `AS` clauses require an explicit `import` statement at the top of the `.sq` file.
- **Not passing adapters to Database constructor.** If a table uses custom column types, you must provide the corresponding `Adapter` when constructing the `Database` instance.

## See Also

- [store5-expert](../store5-expert/SKILL.md) -- Store5 caching with SQLDelight as source of truth
- [coroutines-core-expert](../coroutines-core-expert/SKILL.md) -- Flow patterns for reactive queries
- [kotlinx-serialization-expert](../kotlinx-serialization-expert/SKILL.md) -- JSON column adapters
