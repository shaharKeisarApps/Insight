---
name: build-optimization-expert
description: Expert guidance on Gradle build optimization for KMP. Use for configuration cache, build cache, parallel execution, module structure, compiler tuning, and CI pipeline optimization.
---

# Build Optimization Expert

## Overview

Optimizing Gradle build times for Kotlin Multiplatform (KMP) projects. Covers configuration cache, build cache, parallel execution, module structure, convention plugins, Kotlin compiler tuning, KSP optimization, and advanced CI techniques. The goal is to reduce both local developer iteration time and CI pipeline duration.

## When to Use

- Build times are growing beyond acceptable thresholds (clean build > 3 min, incremental > 30s)
- CI pipelines are slow and costly
- Developer experience is degraded by waiting on builds
- Adding new modules or dependencies and want to avoid regressions
- Setting up a new KMP project and want optimal defaults from the start
- Migrating from buildSrc to build-logic included builds

## Key Techniques

### 1. Gradle Configuration Cache

Caches the task graph so the configuration phase is skipped on repeat builds. Yields dramatic speedups for incremental builds.

```properties
org.gradle.configuration-cache=true
```

Common incompatibilities include `Task.project` access at execution time, `BuildListener` / `TaskExecutionListener` usage, and certain older plugins. Fix by migrating to provider APIs and `ProviderFactory`.

### 2. Build Cache

Caches task outputs keyed by inputs. Enables reuse across branches and machines.

```properties
org.gradle.caching=true
```

For CI, configure a remote cache via Develocity (formerly Gradle Enterprise) or a custom HTTP cache node. Remote caches let CI runs benefit from previously computed outputs.

### 3. Parallel Execution

Build independent modules concurrently across available CPU cores.

```properties
org.gradle.parallel=true
org.gradle.workers.max=<number-of-cores>
```

Effectiveness depends on module graph structure; "wide" graphs with many independent modules benefit most.

### 4. Convention Plugins (build-logic)

Share build configuration across modules without recompilation overhead. Use an included build (`build-logic/`) instead of `buildSrc` to avoid invalidating the entire build when configuration changes.

```
build-logic/
  convention/
    build.gradle.kts
    src/main/kotlin/
      kmp-library-convention.gradle.kts
      android-app-convention.gradle.kts
```

### 5. Kotlin Compilation

Enable incremental compilation for faster recompilation. The K2 compiler (default since Kotlin 2.0) provides significant compilation speed improvements.

```properties
kotlin.incremental=true
kotlin.incremental.multiplatform=true
```

For Kotlin daemon memory tuning:

```properties
kotlin.daemon.jvmargs=-Xmx2g
```

### 6. KSP Optimization

Use KSP instead of kapt wherever possible. Enable incremental processing to avoid full reprocessing on each build.

```properties
ksp.incremental=true
ksp.incremental.log=true
```

Multi-round KSP processing should be avoided unless required; it adds overhead per additional round.

### 7. Module Structure

Avoid "star" dependency graphs where one module depends on many others. Prefer layered architectures:

- **Problematic**: `:app` depends on 20+ feature modules directly
- **Optimal**: `:app` -> `:feature:*` -> `:domain:*` -> `:core:*`

Each layer should have minimal cross-dependencies. This maximizes parallel compilation and cache hit rates.

### 8. Gradle Daemon

Keep the daemon running for local development. Tune JVM arguments for sufficient heap and modern GC:

```properties
org.gradle.jvmargs=-Xmx4g -XX:+UseG1GC -XX:MaxMetaspaceSize=1g -XX:+HeapDumpOnOutOfMemoryError
```

On CI, consider `--no-daemon` to avoid daemon startup overhead for one-shot builds.

### 9. R8 / ProGuard

Enable R8 full mode for release builds to maximize code shrinking and optimization:

```kotlin
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
}
```

Use `android.enableR8.fullMode=true` in gradle.properties for maximum optimization.

### 10. Compose Compiler

Configure stability for Compose to reduce unnecessary recompositions, which indirectly affects build correctness and runtime performance.

- Use `stability-config.txt` to declare stable external types
- Enable strong skipping mode for fewer recomposition checks
- Use Compose compiler metrics to identify unstable classes

## gradle.properties Template

```properties
# JVM and Daemon
org.gradle.jvmargs=-Xmx4g -XX:+UseG1GC -XX:MaxMetaspaceSize=1g -XX:+HeapDumpOnOutOfMemoryError
org.gradle.daemon=true

# Parallelism
org.gradle.parallel=true
org.gradle.configureondemand=true
org.gradle.workers.max=8

# Caching
org.gradle.caching=true
org.gradle.configuration-cache=true

# Kotlin
kotlin.incremental=true
kotlin.incremental.multiplatform=true
kotlin.daemon.jvmargs=-Xmx2g
kotlin.code.style=official

# KSP
ksp.incremental=true

# Android
android.useAndroidX=true
android.nonTransitiveRClass=true
android.enableR8.fullMode=true
```

## Core Rules

1. Always measure before optimizing -- use `--scan` or `--profile` to identify bottlenecks
2. Never disable the configuration cache to work around a plugin issue; fix the incompatibility
3. Prefer `build-logic` included builds over `buildSrc` for convention plugins
4. Keep module dependency graphs shallow and wide for maximum parallelism
5. Enable incremental compilation for all Kotlin source sets
6. Use KSP instead of kapt; kapt runs the full javac annotation processing pipeline
7. Tune JVM memory based on actual usage, not arbitrary large values
8. On CI, use remote build cache to share outputs across builds
9. Review build scans regularly to catch regressions in build time
10. Test configuration cache compatibility when adding new plugins

## Common Pitfalls

- **buildSrc changes invalidate everything**: Migrate to `build-logic` included build
- **Configuration cache breaks with old plugins**: Check plugin compatibility before adopting
- **Too much heap causes GC pauses**: Profile actual memory usage; 4g is usually sufficient
- **Star module graphs prevent parallelism**: Restructure into layered architecture
- **kapt blocks incremental compilation**: Replace with KSP equivalents
- **Missing `nonTransitiveRClass`**: Without it, R class grows linearly with dependencies
- **Gradle daemon memory leaks**: Restart daemon periodically on CI or use `--no-daemon`
