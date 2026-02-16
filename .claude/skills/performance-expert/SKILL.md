---
name: performance-expert
description: Expert guidance on KMP runtime performance optimization. Use for Baseline Profiles, Macrobenchmarks, Compose recomposition analysis, system tracing, memory leak detection, startup optimization, R8 shrinking, and Kotlin/Native GC tuning.
---

# Performance Expert Skill

## Overview

Performance is a feature, not an afterthought. In a KMP project targeting Android (API 24-35), iOS 17+, and Desktop JVM with Compose Multiplatform 1.10.0, performance problems manifest differently on each platform: Android suffers from JIT compilation stalls, iOS from Kotlin/Native GC pauses, and Desktop from cold JVM startup. This skill covers measurement-first optimization -- profile before you guess, benchmark before you ship, and automate regression detection in CI.

## When to Use

- **Slow startup**: Generating Baseline Profiles, configuring App Startup, eliminating main-thread I/O.
- **Dropped frames (jank)**: Compose recomposition analysis, stability fixes, system tracing with Perfetto.
- **Memory pressure**: LeakCanary setup, heap analysis, bitmap management, Presenter/ViewModel leak detection.
- **APK/binary size**: R8 full mode configuration, code shrinking measurement, resource optimization.
- **Benchmarking**: Macrobenchmark setup for startup timing, frame timing, and custom trace sections.
- **Network latency**: Response compression, request batching, image optimization.
- **Kotlin/Native (iOS)**: GC tuning, avoiding performance pitfalls on Apple platforms.

## Quick Reference

For API details and build configuration, see [reference.md](reference.md).
For complete production-ready code, see [examples.md](examples.md).

## Baseline Profiles (Android)

Baseline Profiles are lists of classes and methods that the Android Runtime (ART) pre-compiles to native code during app installation, bypassing JIT compilation on first launch. They are the single most impactful Android performance optimization for Compose apps.

### Why They Matter

- **~30% faster cold startup** on average for Compose apps (Google-reported).
- **Eliminates JIT jank** in the first seconds of app use when Compose runtime is still being JIT-compiled.
- **Dex Layout Optimization (DLO)**: When combined with AGP 8.0+, baseline profiles also trigger DLO, which reorders DEX bytecode to improve class loading locality.

### Profile Types

| Type | Purpose | Generation |
|------|---------|------------|
| **Startup Profile** | Pre-compiles code needed for cold start | `includeInStartupProfile = true` in BaselineProfileRule |
| **Baseline Profile** | Pre-compiles all Critical User Journeys (CUJ) | Full BaselineProfileRule with navigation, scrolling, etc. |

### Generation

Baseline Profiles are generated using the Macrobenchmark library. A `:benchmark` module runs instrumented tests on a physical device or emulator, exercises CUJs, and outputs a `baseline-prof.txt` that AGP merges into the APK.

```
:benchmark (com.android.test) --> generates --> baseline-prof.txt --> merged by AGP into :app
```

## Macrobenchmark

Macrobenchmark measures real-world app performance on an actual device. It launches your app in a separate process and collects system-level metrics.

### Key Metrics

| Metric | Measures | Use For |
|--------|----------|---------|
| `StartupTimingMetric()` | Time to initial display (TTID) and full display (TTFD) | Cold/warm/hot start timing |
| `FrameTimingMetric()` | Frame duration, jank count, missed vsync | Scroll and animation smoothness |
| `TraceSectionMetric("name")` | Duration of custom `trace("name")` blocks | Specific operation timing |
| `PowerMetric(type)` | Battery drain per CUJ | Energy efficiency |

### Startup Modes

| Mode | Description | Benchmark Constant |
|------|-------------|--------------------|
| Cold | Process killed, fresh launch | `StartupMode.COLD` |
| Warm | Activity destroyed, process alive | `StartupMode.WARM` |
| Hot | Activity stopped, not destroyed | `StartupMode.HOT` |

## Compose Performance

### Stability System

The Compose compiler tracks the stability of every parameter passed to a composable. If all parameters are stable and unchanged, the composable is **skipped** during recomposition. Instability causes unnecessary recomposition, which is the primary cause of Compose jank.

**Stability rules:**
- `val` properties of primitive types, `String`, or other `@Stable`/`@Immutable` types are stable.
- `List<T>`, `Map<K,V>`, `Set<T>` from `kotlin.collections` are **unstable** (they are interfaces, could be mutable).
- `ImmutableList<T>`, `ImmutableMap<K,V>`, `ImmutableSet<T>` from `kotlinx.collections.immutable` are stable.
- Data classes with all stable properties are inferred stable. One unstable property makes the entire class unstable.
- Classes from external modules are unstable by default unless annotated or listed in the stability configuration file.

### @Stable vs @Immutable

| Annotation | Contract | Use When |
|------------|----------|----------|
| `@Immutable` | All properties are `val` and will never change | True immutable data classes |
| `@Stable` | If a property changes, Compose will be notified via snapshot system | Classes with `MutableState` properties |

### Strong Skipping Mode

Enabled by default in Kotlin 2.0+ (K2 compiler). Strong skipping relaxes stability requirements:
- Composables with **unstable** parameters can still be skipped if the parameter values are referentially equal (`===`).
- Lambdas are automatically remembered with their captures.
- This reduces (but does not eliminate) the need for manual stability annotations.

Even with strong skipping, ensuring stability is best practice because referential equality checks on complex objects can be expensive.

### Compose Compiler Metrics

Generate detailed stability reports to identify unstable classes and restartable/skippable composables:

```bash
./gradlew assembleRelease -Pcompose.compiler.reports=true -Pcompose.compiler.metrics=true
```

Output files in `build/compose_compiler/`:
- `*-classes.txt` -- Stability of each class (stable, unstable, runtime).
- `*-composables.txt` -- Each composable: restartable, skippable, parameters and their stability.
- `*-composables.csv` -- Machine-parseable version.

### Stability Configuration File

For third-party types you cannot annotate, declare them stable in a configuration file:

```
// stability-config.txt
kotlinx.datetime.Instant
kotlinx.datetime.LocalDate
kotlinx.datetime.LocalDateTime
kotlinx.collections.immutable.ImmutableList
kotlinx.collections.immutable.ImmutableMap
kotlinx.collections.immutable.ImmutableSet
```

Register in `build.gradle.kts`:

```kotlin
composeCompiler {
    stabilityConfigurationFile = project.layout.projectDirectory.file("stability-config.txt")
}
```

### Recomposition Debugging

1. **Layout Inspector**: Android Studio > Layout Inspector > Show Recomposition Counts. Highlights composables by recomposition frequency.
2. **Recomposition Highlighter**: `Modifier.recomposeHighlighter()` (from accompanist or custom) -- adds a colored border that flashes on recomposition.
3. **Compose compiler reports**: See above.

### Common Recomposition Triggers and Fixes

| Trigger | Problem | Fix |
|---------|---------|-----|
| Unstable lambda capture | Lambda captures mutable variable, recreated every recomposition | Extract to class-level function or use `remember` |
| `List<T>` parameter | Interface is unstable | Use `ImmutableList<T>` |
| Missing `key` in `LazyColumn` | Items recompose on every scroll | Add `key = { it.id }` |
| Computed value in composition | Expensive derivation runs every frame | Use `derivedStateOf { }` |
| `SnapshotStateList` as parameter | Mutable snapshot state changes trigger recomposition of parent | Expose as `ImmutableList` via `.toImmutableList()` in state production |

### derivedStateOf

Use `derivedStateOf` when a value depends on frequently-changing state but the derived value changes less often:

```kotlin
val items = mutableStateListOf<Item>() // changes often
val selectedFilter = mutableStateOf("all")

// BAD: recalculates and recomposes on every item change
val filtered = items.filter { it.matches(selectedFilter.value) }

// GOOD: only recomposes when the filtered result actually changes
val filtered by remember {
    derivedStateOf { items.filter { it.matches(selectedFilter.value) } }
}
```

### remember / rememberSaveable Best Practices

- `remember { }` survives recomposition, lost on config change. Use for computed values, animation states.
- `rememberSaveable { }` survives config changes. Use for user input, scroll position.
- Never `remember` a lambda that captures mutable state from outside composition -- it will become stale.

## System Tracing

### Perfetto

Perfetto is the system-level tracing tool for Android (replaces systrace). It captures CPU scheduling, binder transactions, and custom app trace sections in a single timeline.

```kotlin
import androidx.tracing.trace

// Add trace sections to critical code paths
fun loadData() = trace("loadData") {
    val result = repository.fetch()
    processResult(result)
}
```

Trace sections appear in Perfetto's flame chart, allowing you to identify exactly where time is spent. Use `trace("section") { }` in:
- `Application.onCreate()` initialization
- DI graph construction
- First composable rendering
- Network response processing

### Reading Flame Charts

1. Open `https://ui.perfetto.dev/` and load the trace file.
2. Find your app's process row.
3. Look for `Choreographer#doFrame` slices -- each is one frame.
4. Frames exceeding 16.6ms (60fps) or 8.3ms (120fps) indicate jank.
5. Your custom `trace("name")` sections appear as colored spans within each frame.

## Memory Optimization

### LeakCanary

LeakCanary detects memory leaks at runtime by watching objects after they should be garbage collected.

Common leak sources in KMP apps:
- **Presenter holding Activity reference** after config change (Circuit's retained system prevents this when used correctly).
- **Flow collection without lifecycle awareness** -- collecting in `GlobalScope` or leaked coroutine scope.
- **Static/companion object holding Context** -- use `applicationContext` instead.
- **Bitmap not recycled** -- Coil handles this automatically; manual bitmap handling requires explicit recycling.

### Bitmap Management

- Use Coil 3.3.0 for automatic memory-aware image loading.
- Set `memoryCachePolicy` and `diskCachePolicy` on `ImageRequest`.
- Use `size(ViewSizeResolver)` to avoid loading full-resolution images.
- For RecyclerView/LazyColumn thumbnails, request exact pixel dimensions.

## Startup Optimization

### Cold vs Warm vs Hot Start

| Type | What Happens | Target |
|------|-------------|--------|
| Cold | Process created, Application.onCreate, Activity.onCreate | < 500ms TTID |
| Warm | Process alive, Activity recreated | < 200ms TTID |
| Hot | Activity brought to foreground | < 100ms TTID |

### App Startup Library

Replaces `ContentProvider`-based initialization (which blocks the main thread) with lazy, dependency-aware initialization:

```kotlin
class AnalyticsInitializer : Initializer<Analytics> {
    override fun create(context: Context): Analytics {
        return Analytics.init(context) // expensive SDK init
    }
    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
```

### Startup Optimization Checklist

1. Move all disk I/O off the main thread (database, SharedPreferences, file reads).
2. Defer non-critical initialization (analytics, crash reporting, ads) to after first frame.
3. Use Baseline Profiles to pre-compile Compose and navigation code.
4. Minimize `Application.onCreate()` work -- use App Startup for lazy init.
5. Avoid synchronous network calls during startup.

## R8 / Code Shrinking

### Full Mode

R8 full mode (`android.enableR8.fullMode=true` in `gradle.properties`) enables aggressive optimizations:
- Class merging, enum unboxing, constant propagation.
- Removes unused code paths more aggressively than default mode.
- **Warning**: Can break reflection-based code. Test thoroughly.

### Measuring Impact

```bash
# Before and after APK size
./gradlew :app:androidApp:assembleRelease
ls -la app/androidApp/build/outputs/apk/release/*.apk
```

Use Android Studio's APK Analyzer to inspect DEX file count, method count, and resource size.

## Network Performance

- **Response compression**: Enable gzip/brotli via `ContentEncoding` Ktor plugin.
- **Image optimization**: Serve WebP instead of PNG/JPEG. Use Coil's `Transformation` for client-side resizing.
- **Request batching**: Combine multiple API calls with `async { }` in a `coroutineScope { }` block.
- **Caching**: Store5 handles offline-first caching with TTL-based invalidation.

## KMP-Specific Performance

### Kotlin/Native (iOS)

- **GC tuning**: Kotlin/Native uses a tracing GC (since 1.7.20). The legacy memory model with frozen objects was removed in Kotlin 2.0.
- **GC pauses**: The new GC is concurrent but may still cause short pauses. Avoid allocating large object graphs in tight loops.
- **Interop overhead**: Each Kotlin-to-Swift bridge call has overhead. Batch operations where possible.
- **SKIE**: Use SKIE 0.10.9 to convert `Flow` to `AsyncSequence`, avoiding manual wrapper overhead.

### WASM Performance

- Compose for WASM (wasmJs) is experimental. Binary size and startup are the primary concerns.
- Use `@JsExport` sparingly -- each export adds to the WASM binary.
- Tree-shaking is limited compared to JVM R8.

## Core Rules

1. **Measure first, optimize second** -- Never guess where the bottleneck is. Use Macrobenchmark, Perfetto, and Compose compiler reports to identify real problems before writing optimization code.
2. **Ship Baseline Profiles** -- Every Compose Android app must generate and include Baseline Profiles. There is no valid reason to skip this.
3. **Ensure Compose stability** -- All state classes passed to composables must be stable. Use `ImmutableList`, `@Stable`, and the stability configuration file.
4. **Trace critical paths** -- Add `trace("section") { }` to initialization, navigation, and data loading code paths.
5. **Test on low-end devices** -- Performance is acceptable on a Pixel 8 but may be terrible on a Galaxy A13. Benchmark on representative devices.
6. **Automate regression detection** -- Run Macrobenchmarks in CI and fail the build if startup time or P95 frame time exceeds thresholds.
7. **Defer initialization** -- Use App Startup library for lazy SDK initialization. Nothing should block `Application.onCreate()` that can be deferred.
8. **Monitor memory** -- Run LeakCanary in debug builds. Investigate every leak before it reaches production.
9. **Minimize APK size** -- Enable R8 full mode, remove unused resources with `shrinkResources true`, and measure APK size in CI.
10. **Profile iOS separately** -- Kotlin/Native has different performance characteristics than JVM. Use Xcode Instruments for iOS-specific profiling.

## Common Pitfalls

| Pitfall | Symptom | Fix |
|---------|---------|-----|
| No Baseline Profile | 300ms+ cold start jank on first launch | Generate via Macrobenchmark, include in release build |
| `List<T>` in state | Composable never skips, recomposes every frame | Use `ImmutableList<T>` from kotlinx.collections.immutable |
| Lambda capturing mutable var | Lambda recreated on every recomposition | Extract to stable function reference or `remember` the lambda |
| `derivedStateOf` missing | Expensive filtering recalculates every recomposition | Wrap computed values in `derivedStateOf { }` |
| Missing `key` in LazyColumn | All visible items recompose on any list change | Add `key = { item.id }` to `items()` |
| Disk I/O on main thread | ANR on startup, StrictMode violations | Move to `withContext(Dispatchers.IO)` or use App Startup |
| LeakCanary not installed | Memory leaks go undetected until OOM crashes | Add LeakCanary debug dependency |
| R8 rules too broad | `-dontwarn **` masks real issues; bloated APK | Use specific keep rules; enable R8 full mode |
| Benchmarking on emulator | Unreliable numbers, no real hardware behavior | Always benchmark on physical devices |
| Not testing on low-end device | Good numbers on Pixel 8, jank on budget phones | Include a low-end device (2GB RAM, older SoC) in benchmark matrix |

## Gradle Dependencies

Version catalog entries (`libs.versions.toml`):

```toml
[versions]
androidx-benchmark = "1.4.0"
androidx-profileinstaller = "1.4.1"
androidx-startup = "1.2.0"
androidx-tracing = "1.3.0"
leakcanary = "3.0"
kotlinx-collections-immutable = "0.3.8"

[libraries]
androidx-benchmark-macro-junit4 = { module = "androidx.benchmark:benchmark-macro-junit4", version.ref = "androidx-benchmark" }
androidx-profileinstaller = { module = "androidx.profileinstaller:profileinstaller", version.ref = "androidx-profileinstaller" }
androidx-startup-runtime = { module = "androidx.startup:startup-runtime", version.ref = "androidx-startup" }
androidx-tracing = { module = "androidx.tracing:tracing", version.ref = "androidx-tracing" }
androidx-tracing-ktx = { module = "androidx.tracing:tracing-ktx", version.ref = "androidx-tracing" }
leakcanary = { module = "com.squareup.leakcanary:leakcanary-android", version.ref = "leakcanary" }
leakcanary-instrumentation = { module = "com.squareup.leakcanary:leakcanary-android-instrumentation", version.ref = "leakcanary" }
kotlinx-collections-immutable = { module = "org.jetbrains.kotlinx:kotlinx-collections-immutable", version.ref = "kotlinx-collections-immutable" }
```

App module (`build.gradle.kts`):

```kotlin
dependencies {
    implementation(libs.androidx.profileinstaller) // Required for Baseline Profiles
    implementation(libs.androidx.tracing.ktx)
    implementation(libs.androidx.startup.runtime)
    implementation(libs.kotlinx.collections.immutable)
    debugImplementation(libs.leakcanary)
}
```

## Related Skills

- [compose-runtime-expert](../compose-runtime-expert/SKILL.md) -- Compose snapshot system and recomposition internals
- [build-optimization-expert](../build-optimization-expert/SKILL.md) -- Gradle build speed and caching
- [android-platform-expert](../android-platform-expert/SKILL.md) -- Android lifecycle and process management
- [testing-expert](../testing-expert/SKILL.md) -- Testing patterns for benchmarks and performance regression tests
