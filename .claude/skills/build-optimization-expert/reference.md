# Build Optimization Reference

## gradle.properties Optimization Flags

### JVM and Daemon Settings

| Property | Default | Recommended | Description |
|----------|---------|-------------|-------------|
| `org.gradle.jvmargs` | `-Xmx512m` | `-Xmx4g -XX:+UseG1GC -XX:MaxMetaspaceSize=1g` | JVM args for the Gradle daemon |
| `org.gradle.daemon` | `true` | `true` | Keep daemon alive between builds |
| `org.gradle.daemon.idletimeout` | `10800000` (3h) | `10800000` | Milliseconds before idle daemon exits |

### Parallelism Settings

| Property | Default | Recommended | Description |
|----------|---------|-------------|-------------|
| `org.gradle.parallel` | `false` | `true` | Execute independent modules in parallel |
| `org.gradle.workers.max` | CPU cores | CPU cores | Max parallel worker threads |
| `org.gradle.configureondemand` | `false` | `true` | Only configure required projects |

### Caching Settings

| Property | Default | Recommended | Description |
|----------|---------|-------------|-------------|
| `org.gradle.caching` | `false` | `true` | Enable build cache (local by default) |
| `org.gradle.configuration-cache` | `false` | `true` | Cache the task graph across builds |
| `org.gradle.configuration-cache.max-problems` | `0` | `512` (during migration) | Allow N problems before failing |
| `org.gradle.configuration-cache.problems` | `fail` | `warn` (during migration) | How to handle CC problems |

### Kotlin Settings

| Property | Default | Recommended | Description |
|----------|---------|-------------|-------------|
| `kotlin.incremental` | `true` | `true` | Incremental JVM compilation |
| `kotlin.incremental.multiplatform` | `false` | `true` | Incremental compilation for KMP |
| `kotlin.daemon.jvmargs` | `-Xmx512m` | `-Xmx2g` | JVM args for Kotlin daemon |
| `kotlin.code.style` | `official` | `official` | Code style for IDE |
| `kotlin.build.report.output` | none | `file` | Output build reports |
| `kotlin.compiler.preciseCompilationResultsBackup` | `false` | `true` | More accurate incremental backups |

### KSP Settings

| Property | Default | Recommended | Description |
|----------|---------|-------------|-------------|
| `ksp.incremental` | `true` | `true` | Incremental symbol processing |
| `ksp.incremental.log` | `false` | `true` (debug) | Log incremental decisions |
| `ksp.useKSP2` | `false` | `true` (if stable) | Use KSP2 implementation |

### Android Settings

| Property | Default | Recommended | Description |
|----------|---------|-------------|-------------|
| `android.useAndroidX` | `false` | `true` | Use AndroidX libraries |
| `android.nonTransitiveRClass` | `false` | `true` | Non-transitive R classes (smaller) |
| `android.enableR8.fullMode` | `false` | `true` | R8 full optimization mode |
| `android.defaults.buildfeatures.buildconfig` | `true` | `false` | Disable BuildConfig generation if unused |
| `android.defaults.buildfeatures.resValues` | `true` | `false` | Disable res values if unused |

---

## Configuration Cache Compatibility Checklist

### Common Incompatible Patterns

| Pattern | Problem | Fix |
|---------|---------|-----|
| `Task.project` at execution time | Captures `Project` reference | Use `ProviderFactory`, pass values via properties |
| `BuildListener` | Not serializable | Use `BuildServiceRegistry` or flow actions |
| `TaskExecutionListener` | Not serializable | Use `BuildEventsListenerRegistry` |
| `allprojects {}` / `subprojects {}` | Evaluates all projects | Use convention plugins |
| Accessing `configurations` at execution | Not cacheable | Resolve at configuration time, pass as input |
| `gradle.buildFinished {}` | Deprecated listener | Use `BuildEventsListenerRegistry` |

### Plugin Compatibility

| Plugin | Compatible | Notes |
|--------|-----------|-------|
| Kotlin (2.0+) | Yes | Full support |
| Android Gradle Plugin (8.0+) | Yes | Full support |
| KSP | Yes | Since 1.9+ |
| Compose Multiplatform | Yes | Since 1.5+ |
| Metro | Yes | Designed for CC |
| SQLDelight (2.0+) | Yes | Full support |
| Ktor plugin | Yes | Since 2.3+ |
| Detekt | Partial | Check latest version |
| Spotless | Partial | Some formatters incompatible |

### Diagnosing Configuration Cache Issues

```bash
# Run with CC enabled and see problems
./gradlew assembleDebug --configuration-cache

# Allow problems temporarily during migration
# In gradle.properties:
org.gradle.configuration-cache.problems=warn
org.gradle.configuration-cache.max-problems=512

# Generate a report of all problems
./gradlew assembleDebug --configuration-cache --configuration-cache-problems=warn 2>&1 | grep "configuration cache"
```

---

## Build Scan Analysis (--scan)

### Generating a Build Scan

```bash
# Full build scan (uploads to scans.gradle.com)
./gradlew assembleDebug --scan

# With Develocity plugin (private server)
# In settings.gradle.kts:
plugins {
    id("com.gradle.develocity") version "3.17"
}
develocity {
    buildScan {
        termsOfUseUrl.set("https://gradle.com/help/legal-terms-of-use")
        termsOfUseAgree.set("yes")
        publishing.onlyIf { true }
    }
}
```

### What to Look For in Build Scans

| Section | Key Metrics | Action |
|---------|------------|--------|
| **Performance** | Total build time, configuration time | Compare across builds |
| **Timeline** | Task execution waterfall | Identify serial bottlenecks |
| **Task execution** | Avoided tasks (UP-TO-DATE, FROM-CACHE) | Low cache hit ratio = investigation needed |
| **Build cache** | Hit/miss ratio | Below 60% means inputs are changing unexpectedly |
| **Configuration** | Configuration time | Above 5s means scripts need optimization |
| **Dependencies** | Dependency resolution time | Slow resolution = consider dependency locking |
| **Deprecations** | Deprecated API usage | Fix before next Gradle upgrade |

---

## Gradle Profiling (--profile)

```bash
# Generate HTML profile report
./gradlew assembleDebug --profile

# Output saved to build/reports/profile/
# Shows: configuration time, dependency resolution, task execution times

# Combined with scan for full picture
./gradlew assembleDebug --profile --scan
```

### Reading Profile Reports

1. **Configuration phase**: Time spent evaluating build scripts. Should be < 3 seconds.
2. **Dependency resolution**: Time resolving external dependencies. Use dependency locking to speed up.
3. **Task execution**: Sorted list of task durations. Focus on the top 10 longest tasks.

---

## Module Dependency Analysis

### Visualizing the Module Graph

```bash
# Using Gradle's built-in dependency report
./gradlew :app:dependencies --configuration implementationDependencyMetadata

# Using a module graph plugin (e.g., savvasdalkitsis/module-dependency-graph)
./gradlew createModuleGraph
```

### Analysis Metrics

| Metric | Target | Meaning |
|--------|--------|---------|
| Max depth | <= 4 | Deepest dependency chain |
| Max breadth | <= 15 | Most dependencies from one module |
| Leaf modules % | >= 40% | Modules with no dependents (highly parallelizable) |
| Circular deps | 0 | Must be zero; breaks incremental compilation |

---

## Compose Compiler Stability Configuration

### stability-config.txt

Create at the project root or module level:

```text
// Treat these external types as stable
java.time.LocalDate
java.time.Instant
java.time.LocalDateTime
java.time.ZonedDateTime
java.util.UUID
kotlinx.datetime.LocalDate
kotlinx.datetime.Instant
kotlinx.collections.immutable.ImmutableList
kotlinx.collections.immutable.ImmutableMap
kotlinx.collections.immutable.ImmutableSet
kotlinx.collections.immutable.PersistentList
kotlinx.collections.immutable.PersistentMap
kotlinx.collections.immutable.PersistentSet
```

### Compose Compiler Configuration in build.gradle.kts

```kotlin
composeCompiler {
    stabilityConfigurationFile = rootProject.layout.projectDirectory.file("stability-config.txt")
    enableStrongSkippingMode = true  // Default true since Compose Compiler 2.0
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    metricsDestination = layout.buildDirectory.dir("compose_compiler")
}
```

### Analyzing Compose Compiler Reports

```bash
# Generate reports
./gradlew assembleRelease

# Check reports at build/compose_compiler/
# Key files:
#   *-composables.txt  -- Lists all composable functions with restartability/skippability
#   *-classes.txt      -- Lists stability of all classes used in composables
#   *-module.json      -- Module-level stability summary
```

---

## R8 Full Mode Configuration

```kotlin
// In app/build.gradle.kts
android {
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
```

```properties
# In gradle.properties
android.enableR8.fullMode=true
```

### Common R8 Keep Rules for KMP

```proguard
# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
```

---

## K2 Compiler Options

```kotlin
// In build.gradle.kts
kotlin {
    compilerOptions {
        // Performance flags
        freeCompilerArgs.addAll(
            "-Xbackend-threads=4",            // Parallel backend compilation
        )
    }
}
```

K2 is the default compiler since Kotlin 2.0 and provides 30-60% faster compilation compared to K1. No explicit opt-in is needed for Kotlin 2.0+ projects.

---

## Gradle Daemon Settings

### Local Development (gradle.properties)

```properties
org.gradle.daemon=true
org.gradle.jvmargs=-Xmx4g -XX:+UseG1GC -XX:MaxMetaspaceSize=1g -XX:+HeapDumpOnOutOfMemoryError -Dfile.encoding=UTF-8
```

### Monitoring Daemon Health

```bash
# List running daemons
./gradlew --status

# Stop all daemons
./gradlew --stop

# Check daemon logs
ls -la ~/.gradle/daemon/<gradle-version>/
```

---

## CI-Specific Optimizations

### General CI Settings

```properties
# CI-specific gradle.properties (override via -P or environment)
org.gradle.daemon=false           # No daemon on CI (one-shot builds)
org.gradle.parallel=true          # Still use parallel execution
org.gradle.caching=true           # Use build cache
org.gradle.console=plain          # No rich console output
org.gradle.vfs.watch=false        # No file system watching on CI
```

### Environment Variables for CI

```bash
export GRADLE_OPTS="-Xmx4g -XX:+UseG1GC -Dorg.gradle.daemon=false"
export GRADLE_USER_HOME="${CI_PROJECT_DIR}/.gradle"  # Cache in workspace
```

### Dependency Caching on CI

```yaml
# GitHub Actions example
- uses: actions/cache@v4
  with:
    path: |
      ~/.gradle/caches
      ~/.gradle/wrapper
      ~/.konan
    key: gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties', '**/libs.versions.toml') }}
    restore-keys: gradle-
```

---

## Develocity / Gradle Enterprise Remote Cache

### Configuration in settings.gradle.kts

```kotlin
plugins {
    id("com.gradle.develocity") version "3.17"
}

develocity {
    server = "https://ge.example.com"
    buildCache {
        remote(develocity.buildCache) {
            isEnabled = true
            isPush = System.getenv("CI") != null  // Only CI pushes
        }
    }
}
```

### Local Build Cache Settings

```kotlin
// In settings.gradle.kts
buildCache {
    local {
        directory = File(rootDir, ".gradle/build-cache")
        removeUnusedEntriesAfterDays = 14
    }
}
```

### Cache Debugging

```bash
# See which tasks hit/miss cache
./gradlew assembleDebug --build-cache -Dorg.gradle.caching.debug=true

# Output shows for each task:
#   - Cache key inputs
#   - Whether it was UP-TO-DATE, FROM-CACHE, or executed
#   - Reason for cache miss (if any)
```
