# Performance API Reference

## Macrobenchmark Module Setup

Complete `:benchmark` module configuration (`benchmark/build.gradle.kts`):

```kotlin
plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
    id("androidx.baselineprofile")
}

android {
    namespace = "com.example.app.benchmark"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Required: managed device for CI (optional for local runs)
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }

    // Must match the app's build types
    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    targetProjectPath = ":app:androidApp"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.espresso.core)
    implementation(libs.androidx.test.uiautomator)
}

androidComponents {
    beforeVariants(selector().all()) {
        it.enable = it.buildType == "benchmark"
    }
}

baselineProfile {
    managedDevices += "pixel6Api34"
    useConnectedDevices = false // true for local device
}
```

Required in the app module (`app/androidApp/build.gradle.kts`):

```kotlin
plugins {
    id("androidx.baselineprofile")
}

dependencies {
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":benchmark"))
}

baselineProfile {
    automaticGenerationDuringBuild = true // Generate on release builds
    saveInSrc = true // Save to src/main/baseline-prof.txt for VCS
    dexLayoutOptimization = true // Enable DLO (AGP 8.0+)
}
```

---

## BaselineProfileRule API

```kotlin
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        // Package name of the app under test
        packageName = "com.example.app",

        // Include these methods in the startup profile (pre-compiled before first frame)
        includeInStartupProfile = true,

        // Max iterations to stabilize the profile (default: 3)
        maxIterations = 5,

        // Filter rules to include only your app's classes (optional)
        filterPredicate = { rule ->
            rule.contains("com.example.app") || rule.contains("androidx.compose")
        },
    ) {
        // MacrobenchmarkScope receiver
        pressHome()
        startActivityAndWait()

        // Exercise Critical User Journeys here
    }
}
```

---

## MacrobenchmarkRule API

```kotlin
import androidx.benchmark.macro.*
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import org.junit.Rule
import org.junit.Test

class AppBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startup() = rule.measureRepeated(
        // Package name of the app under test
        packageName = "com.example.app",

        // Metrics to collect
        metrics = listOf(StartupTimingMetric()),

        // Number of iterations (more = more stable results)
        iterations = 10,

        // Startup mode
        startupMode = StartupMode.COLD,

        // Compilation mode (affects JIT state)
        compilationMode = CompilationMode.DEFAULT,

        // Setup block: runs before each iteration (not measured)
        setupBlock = {
            pressHome()
        },
    ) {
        // Measure block: this is timed
        startActivityAndWait()
    }
}
```

### All Metric Types

| Metric | Constructor | Measures |
|--------|-------------|----------|
| `StartupTimingMetric()` | No args | `timeToInitialDisplayMs`, `timeToFullDisplayMs` |
| `FrameTimingMetric()` | No args | `frameDurationCpuMs`, `frameOverrunMs` |
| `TraceSectionMetric("name")` | Section name | Duration of `trace("name") { }` blocks in ms |
| `TraceSectionMetric("name", mode)` | Name + mode | `Sum`, `Min`, `Max`, `Average`, `Count` |
| `PowerMetric(type)` | `Type.Battery` or `Type.Energy` | `powerTotalUw`, `powerCpuUw`, `powerGpuUw` |
| `MemoryUsageMetric(mode)` | `Mode.Last` or `Mode.Max` | `memoryRssKb`, `memoryPssKb` |

### CompilationMode

| Mode | Behavior | Use For |
|------|----------|---------|
| `CompilationMode.DEFAULT` | Uses system default (with baseline profiles if present) | Real-world performance |
| `CompilationMode.None()` | No AOT compilation, pure JIT | Worst-case baseline |
| `CompilationMode.Partial(baselineProfileMode)` | Baseline profiles only | Measuring profile impact |
| `CompilationMode.Full()` | Fully AOT compiled | Best-case ceiling |

### StartupMode

| Mode | Behavior |
|------|----------|
| `StartupMode.COLD` | Process killed before each iteration |
| `StartupMode.WARM` | Activity finished but process alive |
| `StartupMode.HOT` | Activity stopped, not destroyed |

### MacrobenchmarkScope Extensions

| Method | Description |
|--------|-------------|
| `startActivityAndWait()` | Launch default activity and wait for rendering |
| `startActivityAndWait(intent)` | Launch with custom intent |
| `pressHome()` | Press home button |
| `killProcess()` | Force kill the app process |
| `dropKernelPageCache()` | Drop filesystem cache (requires root) |
| `device` | UiDevice for UI automation |

---

## Compose Compiler Flags

Configure in `build.gradle.kts` using the `composeCompiler` block (Kotlin 2.0+):

```kotlin
composeCompiler {
    // Generate stability reports (use with assembleRelease)
    reportsDestination = layout.buildDirectory.dir("compose_compiler")

    // Enable metrics generation
    metricsDestination = layout.buildDirectory.dir("compose_compiler")

    // Stability configuration file for external types
    stabilityConfigurationFile = project.layout.projectDirectory.file("stability-config.txt")

    // Strong skipping mode (enabled by default in K2)
    featureFlags = setOf(
        // ComposeFeatureFlag.StrongSkipping // Already default in Kotlin 2.0+
    )
}
```

Alternatively, pass flags via Gradle properties (for CI):

```bash
./gradlew assembleRelease \
    -Pcompose.compiler.reports=true \
    -Pcompose.compiler.metrics=true
```

### Compose Compiler Report Output

`*-composables.txt` format:

```
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun ProductCard(
  stable product: Product
  stable onClick: Function0<Unit>
  stable modifier: Modifier? = @static Companion
)
```

`*-classes.txt` format:

```
stable class Product {
  stable val id: String
  stable val name: String
  stable val price: Double
  <runtime stability> = Stable
}

unstable class CartState {
  unstable val items: List<Product>  // <-- List is unstable
  <runtime stability> = Unstable
}
```

Key terms:
- **restartable**: Compose can restart this composable on recomposition.
- **skippable**: Compose can skip this composable if inputs are unchanged.
- **stable**: All parameters are stable; composable is skippable.
- **unstable**: At least one parameter is unstable; composable always recomposes.

---

## Stability Configuration File

File: `stability-config.txt` (place at module root)

```
// Fully qualified class names, one per line.
// Wildcard (*) is supported for packages.

// kotlinx.datetime types
kotlinx.datetime.Instant
kotlinx.datetime.LocalDate
kotlinx.datetime.LocalDateTime
kotlinx.datetime.LocalTime
kotlinx.datetime.TimeZone

// kotlinx.collections.immutable (already stable, but explicit for clarity)
kotlinx.collections.immutable.*

// Your domain model types from other modules
com.example.shared.model.*

// Third-party types used in state
java.util.UUID
java.math.BigDecimal
```

### @Stable and @Immutable Contract Requirements

```kotlin
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable

// @Immutable: ALL of the following must be true:
// 1. All properties are val
// 2. Property types are themselves immutable or primitive
// 3. Instance will NEVER change after construction
@Immutable
data class Product(
    val id: String,
    val name: String,
    val price: Double,
)

// @Stable: ALL of the following must be true:
// 1. equals() results will ALWAYS return the same value for the same two instances
// 2. When a public property changes, Compose is notified (via MutableState)
// 3. All public property types are also @Stable
@Stable
class CartViewModel {
    var itemCount by mutableIntStateOf(0)
        private set
}
```

**Warning**: Annotating a class as `@Stable` or `@Immutable` when it does not satisfy the contract will cause Compose to skip recomposition when it should not, producing stale UI.

---

## Strong Skipping Mode

Enabled by default in Kotlin 2.0+ (K2 compiler). No configuration needed.

Behavior:
- Unstable parameters are compared by referential equality (`===`).
- If the same object instance is passed, the composable is skipped even if the type is unstable.
- Lambdas are automatically remembered with their captured values.
- This means `onClick = { viewModel.doSomething() }` no longer forces recomposition if `viewModel` is the same instance.

To explicitly disable (not recommended):

```kotlin
composeCompiler {
    featureFlags = setOf(
        ComposeFeatureFlag.StrongSkipping.disabled()
    )
}
```

---

## Perfetto Trace API

```kotlin
import androidx.tracing.trace

// Inline function -- zero overhead when tracing is not active
fun <T> expensiveOperation(): T = trace("expensiveOperation") {
    // Appears as a span in Perfetto timeline
    val data = fetchData()
    processData(data)
}

// Async trace (for coroutines)
import androidx.tracing.Trace

suspend fun asyncOperation() {
    Trace.beginAsyncSection("asyncOperation", /* cookie */ 0)
    try {
        withContext(Dispatchers.IO) { /* work */ }
    } finally {
        Trace.endAsyncSection("asyncOperation", /* cookie */ 0)
    }
}
```

---

## LeakCanary Setup

```kotlin
// build.gradle.kts (app module)
dependencies {
    // Auto-installs on debug builds -- no code needed
    debugImplementation(libs.leakcanary) // com.squareup.leakcanary:leakcanary-android:3.0

    // For instrumentation tests (optional)
    androidTestImplementation(libs.leakcanary.instrumentation)
}
```

LeakCanary 3.0 auto-detects:
- Activity leaks
- Fragment leaks
- ViewModel leaks
- Service leaks
- Custom watched objects via `AppWatcher.objectWatcher.expectWeaklyReachable()`

### Custom Leak Watchers (for Circuit Presenters)

```kotlin
// In debug Application class
class DebugApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Watch custom objects that should be garbage collected
        val watcher = AppWatcher.objectWatcher
        // Use in Presenter.onDispose or similar lifecycle callback
    }
}
```

### CI Integration

```kotlin
// In androidTest
@RunWith(AndroidJUnit4::class)
class LeakTest {
    @get:Rule
    val rule = LeakAssertions.rule()

    @Test
    fun noLeaksAfterNavigation() {
        // Navigate through screens, then assert no leaks
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Navigate, rotate, etc.
        }
        // rule automatically checks for leaks
    }
}
```

---

## R8 Configuration

### Maximum Optimization (`proguard-rules.pro`)

```proguard
# R8 full mode (also set android.enableR8.fullMode=true in gradle.properties)

# Keep kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep @Serializable generated serializers
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Circuit screens (used via reflection in navigation)
-keep class * implements com.slack.circuit.runtime.screen.Screen { *; }

# Aggressive optimizations
-optimizationpasses 5
-allowaccessmodification
-repackageclasses ''
```

### gradle.properties

```properties
# Enable R8 full mode
android.enableR8.fullMode=true
```

### build.gradle.kts (app module)

```kotlin
android {
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}
```

---

## App Startup Library

### Initializer API

```kotlin
import android.content.Context
import androidx.startup.Initializer

class CrashReportingInitializer : Initializer<CrashReporting> {
    override fun create(context: Context): CrashReporting {
        return CrashReporting.init(context, apiKey = BuildConfig.CRASH_REPORTING_KEY)
    }

    // Declare dependencies (initialized first)
    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}

class AnalyticsInitializer : Initializer<Analytics> {
    override fun create(context: Context): Analytics {
        return Analytics.init(context)
    }

    // Analytics depends on crash reporting being initialized first
    override fun dependencies(): List<Class<out Initializer<*>>> = listOf(
        CrashReportingInitializer::class.java,
    )
}
```

### Manifest Registration

```xml
<provider
    android:name="androidx.startup.InitializationProvider"
    android:authorities="${applicationId}.androidx-startup"
    android:exported="false"
    tools:node="merge">

    <!-- Only declare leaf initializers; dependencies are resolved automatically -->
    <meta-data
        android:name="com.example.app.AnalyticsInitializer"
        android:value="androidx.startup" />
</provider>
```

### Lazy (On-Demand) Initialization

```kotlin
// Do NOT register in manifest. Initialize manually when needed:
val analytics = AppInitializer.getInstance(context)
    .initializeComponent(AnalyticsInitializer::class.java)
```

---

## LazyColumn Performance Configuration

```kotlin
@Composable
fun OptimizedList(
    items: ImmutableList<Item>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,

        // Pre-compose items beyond visible bounds for smoother scrolling
        beyondBoundsItemCount = 5,
    ) {
        items(
            items = items,

            // Stable key prevents recomposition on reorder
            key = { item -> item.id },

            // Content type enables compose node recycling across item types
            contentType = { item -> item.type },
        ) { item ->
            ItemRow(item = item)
        }
    }
}
```

### LazyColumn Performance Checklist

| Configuration | Why | Default |
|---------------|-----|---------|
| `key = { it.id }` | Enables item identity tracking, prevents unnecessary recomposition | Index-based (bad) |
| `contentType = { it.type }` | Enables Compose node recycling for different item layouts | All same type |
| `beyondBoundsItemCount = N` | Pre-composes N items beyond viewport for smoother scrolling | 0 |
| `ImmutableList` parameter | Makes the list parameter stable, enables skipping | `List` (unstable) |

### PrefetchStrategy (Compose 1.7+)

```kotlin
val prefetchStrategy = remember {
    LazyListPrefetchStrategy(
        // Number of items to prefetch ahead of scroll direction
        prefetchCount = 3,
    )
}

LazyColumn(
    prefetchStrategy = prefetchStrategy,
) {
    // ...
}
```

---

## Version Summary

| Library | Version | Module |
|---------|---------|--------|
| Benchmark Macro | 1.4.0 | `androidx.benchmark:benchmark-macro-junit4` |
| Profile Installer | 1.4.1 | `androidx.profileinstaller:profileinstaller` |
| App Startup | 1.2.0 | `androidx.startup:startup-runtime` |
| Tracing | 1.3.0 | `androidx.tracing:tracing-ktx` |
| LeakCanary | 3.0 | `com.squareup.leakcanary:leakcanary-android` |
| kotlinx-collections-immutable | 0.3.8 | `org.jetbrains.kotlinx:kotlinx-collections-immutable` |
| Compose Multiplatform | 1.10.0 | JetBrains Compose compiler |
| Kotlin | 2.3.10 | K2 compiler (strong skipping default) |
