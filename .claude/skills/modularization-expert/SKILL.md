---
name: modularization-expert
description: Expert guidance on Enterprise KMP Modularization. Use for splitting features into API/Impl, managing dependency graphs, writing Gradle Convention Plugins, enforcing module boundaries, and scaling multi-module KMP projects.
---

# Modularization Expert Skill

## Overview

Enterprise KMP projects require strict module boundaries to scale. Without them, compilation times explode, teams step on each other, and a single change ripples across the entire codebase. This skill enforces the "Api/Impl" pattern, centralized build logic via Convention Plugins, and strict dependency rules that prevent architectural drift.

## When to Use

- **Creating a new feature**: Splitting into `:feature-xxx-api` and `:feature-xxx-impl` modules.
- **Dependency management**: Deciding `api()` vs `implementation()` in multi-module Gradle builds.
- **Build logic**: Creating or modifying Gradle Convention Plugins in `build-logic/`.
- **Refactoring**: Extracting a monolithic module into proper api/impl pairs.
- **Code review**: Verifying that module dependency rules are not violated.
- **Adding core modules**: Creating shared `:core-*` modules.
- **Scaling the project**: Adding new targets, platforms, or teams.

## Quick Reference

See [reference.md](reference.md) for dependency rules, convention plugin configuration, and version catalog structure.
See [examples.md](examples.md) for production-ready code including full module pairs, convention plugins, and app wiring.

## Api/Impl Pattern

### Why Split

| Problem without splitting | How api/impl fixes it |
|---------------------------|----------------------|
| Feature A imports Feature B's internal classes | Feature A can only see B's `:api` -- interfaces and models |
| Changing B's Presenter forces A to recompile | Only B's `:impl` recompiles; `:api` is stable |
| Circular dependencies between features | `:api` modules have no feature-to-feature deps |
| Cannot test A in isolation from B's implementation | A depends on B's interface; test with a fake |
| DI graph leaks implementation details | Only `app` module sees `:impl` modules |

### What Goes Where

#### `:feature-xxx-api` (Stable, High Visibility)

- Screen / NavKey definitions (Circuit `Screen` or Nav3 `NavKey`)
- Domain models (`data class User`, `value class UserId`)
- Repository interfaces (`interface UserRepository`)
- Use case interfaces (when shared across features)
- Event/Result types for cross-feature communication
- Metro `@ContributesTo` stubs (scope marker interfaces)

**Rules**: No framework dependencies beyond Circuit runtime and kotlinx types. No Compose UI. No Ktor. No SQLDelight. This module changes rarely.

#### `:feature-xxx-impl` (Volatile, Low Visibility)

- Presenter / ViewModel implementations
- UI composables (`@CircuitInject` annotated)
- Repository implementations (`@ContributesBinding`)
- Data sources (remote + local)
- Metro DI bindings (`@Provides`, `@ContributesIntoSet`, `@ContributesIntoMap`)
- DTOs and entity mappers
- Internal navigation logic

**Rules**: Never depended on by other feature modules. Only the `app` module includes this. Changes frequently without affecting other features.

## Module Dependency Graph

### Dependency Direction Rules

```
app
 |
 +---> :feature-auth-impl ---> :feature-auth-api ---> :core-model
 |                         |-> :feature-profile-api    :core-common
 |
 +---> :feature-profile-impl -> :feature-profile-api -> :core-model
 |                           |-> :feature-auth-api
 |
 +---> :feature-feed-impl ---> :feature-feed-api ----> :core-model
                           |-> :feature-auth-api
```

### Allowed Dependencies

| Source Module | Can Depend On | Cannot Depend On |
|--------------|---------------|------------------|
| `app` | All `:impl` modules, `:core-*` | Nothing restricts app |
| `:feature-xxx-impl` | Own `:feature-xxx-api`, other features' `:api` modules, `:core-*` | Other features' `:impl` modules |
| `:feature-xxx-api` | `:core-model`, `:core-common` | Other features' `:api` or `:impl`, `:core-network`, `:core-data` |
| `:core-*` | Other `:core-*` (sparingly) | Any `:feature-*` module |

### Diamond Dependency Prevention

If Feature A and Feature B both need User data, they both depend on `:feature-auth-api` (which exposes the `User` model and `UserRepository` interface). They never depend on each other's `:impl`. The `app` module includes all `:impl` modules, and Metro wires everything together at compile time.

## Core Modules

| Module | Purpose | Typical Contents |
|--------|---------|-----------------|
| `:core-model` | Shared domain types | `UserId`, `Timestamp`, `AppError`, value classes |
| `:core-common` | Pure Kotlin utilities | Extension functions, `suspendRunCatching`, Result wrappers |
| `:core-ui` | Shared Compose components | Design system, theme, reusable composables |
| `:core-network` | HTTP client setup | Ktor `HttpClient` provider, auth interceptor, Metro bindings |
| `:core-data` | Database setup | SQLDelight driver provider, migration helpers |
| `:core-testing` | Test utilities | Fakes, test fixtures, coroutine test helpers |

**Rule**: Core modules never depend on feature modules. Core modules may depend on other core modules (e.g., `:core-network` depends on `:core-model` for error types).

## Convention Plugins

Convention plugins eliminate duplication across `build.gradle.kts` files. They live in `build-logic/convention/` and are applied by plugin ID.

### Standard Plugin IDs

| Plugin ID | Applied To | What It Configures |
|-----------|-----------|-------------------|
| `my.kmp.library` | All KMP library modules | Kotlin targets (Android, iOS, JVM), `applyDefaultHierarchyTemplate()`, Detekt, testing |
| `my.android.library` | Android-only library modules | `compileSdk`, `minSdk`, namespace conventions, ProGuard defaults |
| `my.kmp.feature.api` | `:feature-xxx-api` modules | Extends `my.kmp.library`, adds Circuit runtime dependency |
| `my.kmp.feature.impl` | `:feature-xxx-impl` modules | Extends `my.kmp.library`, adds Compose, Metro, Circuit codegen, KSP |
| `my.android.app` | Root `app` module | Android Application plugin, signing, ProGuard, Metro graph |
| `my.kmp.compose` | Any module needing Compose | JetBrains Compose plugin, Compose compiler config |

### How They Work

Instead of repeating 30 lines of configuration in every module:

```kotlin
// BAD: Repetitive build.gradle.kts (repeated in every module)
plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("org.jetbrains.compose")
    id("dev.zacsweers.metro")
}
kotlin {
    androidTarget()
    iosArm64()
    iosSimulatorArm64()
    iosX64()
    applyDefaultHierarchyTemplate()
    // ... 20 more lines
}

// GOOD: Convention plugin applied by ID
plugins {
    id("my.kmp.feature.impl")
}
```

## Module Creation Checklist

When adding a new feature (e.g., "notifications"):

1. **Create the `:api` module directory**: `features/notifications/api/`
2. **Create the `:impl` module directory**: `features/notifications/impl/`
3. **Add `build.gradle.kts` for `:api`**: Apply `id("my.kmp.feature.api")`, add `:core-model` dependency
4. **Add `build.gradle.kts` for `:impl`**: Apply `id("my.kmp.feature.impl")`, add own `:api` + other features' `:api` as needed
5. **Register in `settings.gradle.kts`**: `include(":features:notifications:api")` and `include(":features:notifications:impl")`
6. **Define the Screen**: In `:api`, create `NotificationsScreen` implementing `Screen`
7. **Define the repository interface**: In `:api`, create `NotificationRepository`
8. **Implement the Presenter**: In `:impl`, create `NotificationsPresenter` with `@CircuitInject`
9. **Implement the UI**: In `:impl`, create `NotificationsUi` with `@CircuitInject`
10. **Implement the repository**: In `:impl`, create `NotificationRepositoryImpl` with `@ContributesBinding`
11. **Wire into app**: Add `implementation(project(":features:notifications:impl"))` to `app/build.gradle.kts`
12. **Verify build**: Run `./gradlew :features:notifications:impl:build`

## `api()` vs `implementation()` Decision

| Use `api()` when | Use `implementation()` when |
|------------------|----------------------------|
| The type appears in your module's public API | The type is only used internally |
| Consumers of your module need the transitive dependency | Consumers do not need to see this dependency |
| Interface return types, parameter types, supertype | Implementation details, private helpers |
| `:api` modules exposing `:core-model` types | `:impl` modules using Ktor, SQLDelight internally |

**Rule of thumb**: `:api` modules use `api()` for their exposed types. `:impl` modules use `implementation()` for almost everything.

## Common Pitfalls

| Pitfall | Why It Happens | Fix |
|---------|---------------|-----|
| Implementation leakage | `:impl` module exposes internal types in public API | Move shared types to `:api`; keep `:impl` classes `internal` |
| Circular dependency | Feature A `:api` depends on Feature B `:api` which depends on A `:api` | Extract shared types into `:core-model` |
| Too many modules | Every small utility becomes its own module | Only split when there is a clear boundary; keep related code together |
| Monolithic `:shared` module | Everything dumped into one KMP module | Split by feature with api/impl; extract core modules |
| Using `subprojects {}` | Legacy Gradle pattern that applies config globally | Use convention plugins applied per-module |
| Feature `:impl` depending on another `:impl` | Breaks isolation; creates hidden coupling | Depend only on the other feature's `:api` module |
| `:api` module with framework dependencies | Ktor, SQLDelight, Compose in an `:api` module | Move framework code to `:impl`; keep `:api` pure Kotlin + domain types |
| Missing `include()` in settings.gradle.kts | New module exists on disk but Gradle does not know about it | Add both `:api` and `:impl` to `settings.gradle.kts` |
| Hardcoded versions in build.gradle.kts | Version scattered across modules instead of centralized | Use `libs.versions.toml` exclusively |

## Related Skills

- [gradle-plugin-expert](../gradle-plugin-expert/SKILL.md) -- Convention plugin authoring details
- [gradle-kmp-expert](../gradle-kmp-expert/SKILL.md) -- Version catalogs, targets, settings.gradle.kts
- [metro-expert](../metro-expert/SKILL.md) -- DI graph wiring across modules
- [architecture-patterns-expert](../architecture-patterns-expert/SKILL.md) -- Layer responsibilities and dependency rules
- [circuit-expert](../circuit-expert/SKILL.md) -- Screen/Presenter/UI pattern within modules
