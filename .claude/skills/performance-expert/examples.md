# Performance Examples

## 1. Baseline Profile Generator (Comprehensive CUJ)

`benchmark/src/main/java/com/example/app/benchmark/BaselineProfileGenerator.kt`:

```kotlin
package com.example.app.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generateStartupProfile() = rule.collect(
        packageName = "com.example.app",
        includeInStartupProfile = true,
        maxIterations = 5,
    ) {
        // CUJ 1: Cold startup
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun generateFullProfile() = rule.collect(
        packageName = "com.example.app",
        includeInStartupProfile = true,
        maxIterations = 5,
    ) {
        // CUJ 1: Startup + initial content load
        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.res("home_list")), 10_000)

        // CUJ 2: Scroll the home feed
        val list = device.findObject(By.res("home_list"))
        list.setGestureMargin(device.displayWidth / 5)
        repeat(3) {
            list.fling(Direction.DOWN)
            device.waitForIdle()
        }
        repeat(3) {
            list.fling(Direction.UP)
            device.waitForIdle()
        }

        // CUJ 3: Navigate to detail screen
        device.findObject(By.res("item_card")).click()
        device.wait(Until.hasObject(By.res("detail_content")), 5_000)

        // CUJ 4: Navigate back
        device.pressBack()
        device.wait(Until.hasObject(By.res("home_list")), 5_000)

        // CUJ 5: Tab navigation
        device.findObject(By.res("tab_search")).click()
        device.wait(Until.hasObject(By.res("search_input")), 5_000)

        // CUJ 6: Search flow
        device.findObject(By.res("search_input")).text = "kotlin"
        device.wait(Until.hasObject(By.res("search_results")), 5_000)

        // CUJ 7: Settings screen
        device.findObject(By.res("tab_profile")).click()
        device.wait(Until.hasObject(By.res("settings_button")), 5_000)
        device.findObject(By.res("settings_button")).click()
        device.wait(Until.hasObject(By.res("settings_content")), 5_000)
    }
}
```

---

## 2. Macrobenchmark (Startup + Frame Timing)

`benchmark/src/main/java/com/example/app/benchmark/AppBenchmark.kt`:

```kotlin
package com.example.app.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = rule.measureRepeated(
        packageName = "com.example.app",
        metrics = listOf(StartupTimingMetric()),
        iterations = 10,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.DEFAULT,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
    }

    @Test
    fun coldStartupWithoutBaselineProfiles() = rule.measureRepeated(
        packageName = "com.example.app",
        metrics = listOf(StartupTimingMetric()),
        iterations = 10,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.None(), // Pure JIT -- worst case
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
    }

    @Test
    fun warmStartup() = rule.measureRepeated(
        packageName = "com.example.app",
        metrics = listOf(StartupTimingMetric()),
        iterations = 10,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.DEFAULT,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
    }

    @Test
    fun homeFeedScrolling() = rule.measureRepeated(
        packageName = "com.example.app",
        metrics = listOf(FrameTimingMetric()),
        iterations = 10,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.DEFAULT,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.res("home_list")), 10_000)
        },
    ) {
        val list = device.findObject(By.res("home_list"))
        list.setGestureMargin(device.displayWidth / 5)
        repeat(5) {
            list.fling(Direction.DOWN)
            device.waitForIdle()
        }
    }

    @Test
    fun diGraphConstruction() = rule.measureRepeated(
        packageName = "com.example.app",
        metrics = listOf(TraceSectionMetric("MetroDiInit")),
        iterations = 10,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.DEFAULT,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
    }
}
```

---

## 3. Stability Configuration

`shared/ui/stability-config.txt`:

```
// Third-party types that are effectively immutable but not annotated.
// The Compose compiler treats these as stable, enabling skipping.

// kotlinx.datetime -- all value types, safe to mark stable
kotlinx.datetime.Instant
kotlinx.datetime.LocalDate
kotlinx.datetime.LocalDateTime
kotlinx.datetime.LocalTime
kotlinx.datetime.TimeZone
kotlinx.datetime.UtcOffset
kotlinx.datetime.DateTimePeriod
kotlinx.datetime.DatePeriod

// kotlinx.collections.immutable -- already stable, explicit for clarity
kotlinx.collections.immutable.*

// Java stdlib types used in domain models
java.util.UUID
java.math.BigDecimal
java.math.BigInteger
java.net.URI
java.net.URL

// Arrow types (if using Arrow for error handling)
arrow.core.Either
arrow.core.Option
arrow.core.None
```

Register in `shared/ui/build.gradle.kts`:

```kotlin
composeCompiler {
    stabilityConfigurationFile = project.layout.projectDirectory.file("stability-config.txt")
}
```

---

## 4. Compose Compiler Metrics (Gradle Setup + Reading Reports)

### Gradle Task Setup

Add to your root `build.gradle.kts` or a convention plugin:

```kotlin
// build-logic/convention/src/main/kotlin/ComposeReportsConventionPlugin.kt
import org.gradle.api.Plugin
import org.gradle.api.Project

class ComposeReportsConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.withPlugin("org.jetbrains.kotlin.plugin.compose") {
            extensions.configure<org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension> {
                val reportsEnabled = project.findProperty("compose.compiler.reports") == "true"
                val metricsEnabled = project.findProperty("compose.compiler.metrics") == "true"

                if (reportsEnabled) {
                    reportsDestination.set(layout.buildDirectory.dir("compose_compiler"))
                }
                if (metricsEnabled) {
                    metricsDestination.set(layout.buildDirectory.dir("compose_compiler"))
                }
            }
        }
    }
}
```

Run:

```bash
./gradlew :shared:ui:assembleRelease \
    -Pcompose.compiler.reports=true \
    -Pcompose.compiler.metrics=true
```

### Reading the Reports

Check `shared/ui/build/compose_compiler/` for output files.

**Identify unstable classes** in `*-classes.txt`:

```
# Look for "unstable" or "runtime" stability
unstable class HomeState {
  unstable val items: List<Product>     # <-- List<T> is always unstable
  stable val isLoading: Boolean
  stable val query: String
  runtime val eventSink: Function1<Event, Unit>  # <-- runtime = ok for lambdas
  <runtime stability> = Unstable
}
```

**Fix**: Change `List<Product>` to `ImmutableList<Product>`.

**Identify non-skippable composables** in `*-composables.txt`:

```
# "restartable" without "skippable" = always recomposes
restartable scheme("[...]") fun ProductCard(
  unstable items: List<Product>         # <-- This parameter is unstable
  stable title: String
)
```

---

## 5. Recomposition Fix (Unstable Lambda Capture)

### Before (Unstable)

```kotlin
@Composable
fun ProductListPresenter(
    navigator: Navigator,
    repository: ProductRepository,
) : Presenter<ProductListScreen.State> {

    @Composable
    override fun present(): ProductListScreen.State {
        var products by rememberRetained { mutableStateOf(persistentListOf<Product>()) }
        var sortOrder by rememberRetained { mutableStateOf(SortOrder.NAME) }

        return ProductListScreen.State(
            products = products,
            sortOrder = sortOrder,
        ) { event ->
            when (event) {
                // BAD: This lambda captures `sortOrder` (a local mutable var).
                // Every time sortOrder changes, a new lambda instance is created,
                // which causes every composable reading eventSink to recompose.
                is Event.Sort -> {
                    sortOrder = event.order
                    products = products.sortedWith(event.order.comparator).toImmutableList()
                }
                is Event.ProductClicked -> navigator.goTo(DetailScreen(event.id))
            }
        }
    }
}
```

### After (Stable)

```kotlin
@Composable
fun ProductListPresenter(
    navigator: Navigator,
    repository: ProductRepository,
) : Presenter<ProductListScreen.State> {

    @Composable
    override fun present(): ProductListScreen.State {
        var products by rememberRetained { mutableStateOf(persistentListOf<Product>()) }
        var sortOrder by rememberRetained { mutableStateOf(SortOrder.NAME) }

        // GOOD: With strong skipping mode (K2 default), lambdas are automatically
        // remembered. The eventSink lambda only recreates when its captured
        // MutableState values actually change identity (which they don't -- the
        // MutableState container is the same object, only its .value changes).
        // This is already handled correctly by Circuit's snapshot-based approach.
        //
        // If you are NOT using strong skipping mode (pre-K2), extract the
        // event handler logic:
        val eventSink: (Event) -> Unit = remember(navigator) {
            { event: Event ->
                when (event) {
                    is Event.Sort -> {
                        sortOrder = event.order
                        products = products.sortedWith(event.order.comparator).toImmutableList()
                    }
                    is Event.ProductClicked -> navigator.goTo(DetailScreen(event.id))
                }
            }
        }

        return ProductListScreen.State(
            products = products,
            sortOrder = sortOrder,
            eventSink = eventSink,
        )
    }
}
```

**Key insight**: With Kotlin 2.0+ and strong skipping mode, lambdas passed to composables are automatically remembered. The pattern above is primarily needed when targeting older compiler versions or when lambdas capture objects that change identity frequently.

---

## 6. derivedStateOf (Filtering Without Parent Recomposition)

```kotlin
@Composable
override fun present(): SearchScreen.State {
    var allProducts by rememberRetained { mutableStateOf(persistentListOf<Product>()) }
    var query by rememberRetained { mutableStateOf("") }
    var selectedCategory by rememberRetained { mutableStateOf<Category?>(null) }

    // BAD: filteredProducts recalculates AND triggers recomposition
    // on EVERY change to allProducts, even if the filter result is identical.
    // val filteredProducts = allProducts.filter { it.matches(query, selectedCategory) }

    // GOOD: derivedStateOf only triggers recomposition when the RESULT changes.
    // If a new product is added that does not match the filter, no recomposition occurs.
    val filteredProducts by remember {
        derivedStateOf {
            allProducts
                .filter { product ->
                    (query.isBlank() || product.name.contains(query, ignoreCase = true)) &&
                        (selectedCategory == null || product.category == selectedCategory)
                }
                .toImmutableList()
        }
    }

    // filteredProducts is now a State<ImmutableList<Product>> that only
    // triggers recomposition when the actual filtered list contents change.

    LaunchedEffect(Unit) {
        allProducts = repository.getProducts().toImmutableList()
    }

    return SearchScreen.State(
        query = query,
        products = filteredProducts,
        selectedCategory = selectedCategory,
    ) { event ->
        when (event) {
            is Event.QueryChanged -> query = event.query
            is Event.CategorySelected -> selectedCategory = event.category
        }
    }
}
```

---

## 7. LazyColumn Optimization

```kotlin
@CircuitInject(ProductListScreen::class, AppScope::class)
@Composable
fun ProductListUi(state: ProductListScreen.State, modifier: Modifier = Modifier) {
    Scaffold(modifier) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),

            // Pre-compose 5 items beyond the visible viewport.
            // Prevents blank frames when scrolling fast.
            beyondBoundsItemCount = 5,
        ) {
            items(
                items = state.products, // ImmutableList<Product> -- stable

                // Unique, stable key per item. Enables:
                // 1. Item identity tracking across reorders
                // 2. Animation of additions/removals
                // 3. Correct rememberRetained/rememberSaveable scoping
                key = { product -> product.id },

                // Content type enables Compose node recycling.
                // Items with the same contentType share layout nodes,
                // dramatically reducing composition cost when scrolling.
                contentType = { product ->
                    when {
                        product.isFeatured -> "featured"
                        product.hasImage -> "image"
                        else -> "standard"
                    }
                },
            ) { product ->
                // Each item composable receives the product directly.
                // Because Product is @Immutable and key is set, this
                // composable only recomposes when the product itself changes.
                when {
                    product.isFeatured -> FeaturedProductCard(
                        product = product,
                        onClick = { state.eventSink(Event.ProductClicked(product.id)) },
                    )
                    product.hasImage -> ImageProductCard(
                        product = product,
                        onClick = { state.eventSink(Event.ProductClicked(product.id)) },
                    )
                    else -> StandardProductCard(
                        product = product,
                        onClick = { state.eventSink(Event.ProductClicked(product.id)) },
                    )
                }
            }
        }
    }
}
```

---

## 8. LeakCanary (Presenter/ViewModel Leak Detection)

### Setup

`app/androidApp/build.gradle.kts`:

```kotlin
dependencies {
    debugImplementation(libs.leakcanary) // com.squareup.leakcanary:leakcanary-android:3.0
    androidTestImplementation(libs.leakcanary.instrumentation)
}
```

### Automated Leak Detection in CI

`app/androidApp/src/androidTest/java/com/example/app/LeakDetectionTest.kt`:

```kotlin
package com.example.app

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import leakcanary.LeakAssertions
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LeakDetectionTest {

    @get:Rule
    val leakRule = LeakAssertions.rule()

    @Test
    fun noLeaksAfterScreenRotation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Wait for content
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            device.wait(Until.hasObject(By.res("home_list")), 10_000)

            // Rotate screen (triggers Activity recreation)
            scenario.recreate()
            device.wait(Until.hasObject(By.res("home_list")), 10_000)
        }
        // leakRule asserts no leaks after scenario is closed
    }

    @Test
    fun noLeaksAfterNavigationCycle() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            device.wait(Until.hasObject(By.res("home_list")), 10_000)

            // Navigate forward
            device.findObject(By.res("item_card")).click()
            device.wait(Until.hasObject(By.res("detail_content")), 5_000)

            // Navigate back (Presenter for detail screen should be collected)
            device.pressBack()
            device.wait(Until.hasObject(By.res("home_list")), 5_000)

            // Navigate to a different tab
            device.findObject(By.res("tab_search")).click()
            device.wait(Until.hasObject(By.res("search_input")), 5_000)

            // Navigate back to home
            device.findObject(By.res("tab_home")).click()
            device.wait(Until.hasObject(By.res("home_list")), 5_000)
        }
        // leakRule checks that no Presenters, ViewModels, or Activities leaked
    }
}
```

---

## 9. R8 Rules (Production Configuration)

`app/androidApp/proguard-rules.pro`:

```proguard
# ============================================================
# R8 Full Mode Configuration for KMP + Circuit + Metro + Ktor
# ============================================================

# --- kotlinx.serialization ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}

-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

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

# Keep all @Serializable classes themselves (needed for polymorphic serialization)
-keep @kotlinx.serialization.Serializable class * { *; }

# --- Circuit ---
# Screens are used by Circuit's runtime for type-based matching
-keep class * implements com.slack.circuit.runtime.screen.Screen { *; }

# --- Ktor ---
# Ktor uses reflection for engine selection
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# --- Coroutines ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# --- General optimization ---
-optimizationpasses 5
-allowaccessmodification
-repackageclasses ''

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Remove Kermit debug logging in release (keep warnings and errors)
-assumenosideeffects class co.touchlab.kermit.Logger {
    public void v(...);
    public void d(...);
    public void i(...);
}
```

`gradle.properties`:

```properties
android.enableR8.fullMode=true
```

---

## 10. App Startup (Lazy SDK Initialization)

`app/androidApp/src/main/java/com/example/app/startup/`:

### CrashReportingInitializer.kt

```kotlin
package com.example.app.startup

import android.content.Context
import androidx.startup.Initializer
import androidx.tracing.trace
import com.example.app.BuildConfig

class CrashReportingInitializer : Initializer<Unit> {
    override fun create(context: Context) = trace("CrashReportingInit") {
        // Initialize crash reporting SDK
        // This runs early because Analytics depends on it
        CrashReporting.init(
            context = context,
            apiKey = BuildConfig.CRASH_REPORTING_KEY,
            enabled = !BuildConfig.DEBUG,
        )
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
```

### AnalyticsInitializer.kt

```kotlin
package com.example.app.startup

import android.content.Context
import androidx.startup.Initializer
import androidx.tracing.trace
import com.example.app.BuildConfig

class AnalyticsInitializer : Initializer<Unit> {
    override fun create(context: Context) = trace("AnalyticsInit") {
        Analytics.init(
            context = context,
            apiKey = BuildConfig.ANALYTICS_KEY,
            trackCrashes = true, // requires CrashReporting to be initialized first
        )
    }

    // Dependency: crash reporting must be initialized before analytics
    override fun dependencies(): List<Class<out Initializer<*>>> = listOf(
        CrashReportingInitializer::class.java,
    )
}
```

### FeatureFlagsInitializer.kt (Lazy -- On-Demand)

```kotlin
package com.example.app.startup

import android.content.Context
import androidx.startup.Initializer
import androidx.tracing.trace

/**
 * Feature flags are NOT registered in the manifest.
 * They are initialized lazily on first access:
 *
 * ```kotlin
 * val flags = AppInitializer.getInstance(context)
 *     .initializeComponent(FeatureFlagsInitializer::class.java)
 * ```
 */
class FeatureFlagsInitializer : Initializer<FeatureFlags> {
    override fun create(context: Context): FeatureFlags = trace("FeatureFlagsInit") {
        FeatureFlags.init(context)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = listOf(
        AnalyticsInitializer::class.java, // Track flag evaluation events
    )
}
```

### AndroidManifest.xml (partial)

```xml
<application>
    <provider
        android:name="androidx.startup.InitializationProvider"
        android:authorities="${applicationId}.androidx-startup"
        android:exported="false"
        tools:node="merge">

        <!-- Only Analytics is declared -- CrashReporting is pulled in as a dependency -->
        <meta-data
            android:name="com.example.app.startup.AnalyticsInitializer"
            android:value="androidx.startup" />

        <!-- FeatureFlagsInitializer is NOT declared here.
             It is initialized lazily via AppInitializer.initializeComponent(). -->
    </provider>
</application>
```

### Using Lazy Initialization in Metro DI

```kotlin
import android.content.Context
import androidx.startup.AppInitializer
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

@ContributesTo(AppScope::class)
interface FeatureFlagsModule {

    companion object {
        @Provides
        fun provideFeatureFlags(
            context: Context,
        ): FeatureFlags {
            // Lazily initializes on first injection
            return AppInitializer.getInstance(context)
                .initializeComponent(FeatureFlagsInitializer::class.java)
        }
    }
}
```

This pattern defers expensive SDK initialization until the first screen that actually needs it, rather than blocking `Application.onCreate()`. The `trace()` calls make each initializer visible in Perfetto for timing analysis.
