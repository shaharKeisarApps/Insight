# Build Optimization Examples

## Example 1: Optimized gradle.properties

A complete production-ready gradle.properties for a KMP project targeting Android, iOS, Desktop, and WASM.

```properties
# =============================================================================
# Gradle JVM & Daemon
# =============================================================================
org.gradle.jvmargs=-Xmx4g \
  -XX:+UseG1GC \
  -XX:MaxMetaspaceSize=1g \
  -XX:+HeapDumpOnOutOfMemoryError \
  -Dfile.encoding=UTF-8 \
  -Dkotlin.daemon.jvm.options=-Xmx2g

org.gradle.daemon=true

# =============================================================================
# Parallelism & Configuration
# =============================================================================
org.gradle.parallel=true
org.gradle.workers.max=8
org.gradle.configureondemand=true
org.gradle.configuration-cache=true

# =============================================================================
# Build Cache
# =============================================================================
org.gradle.caching=true

# =============================================================================
# Kotlin Compiler
# =============================================================================
kotlin.incremental=true
kotlin.incremental.multiplatform=true
kotlin.code.style=official
kotlin.daemon.jvmargs=-Xmx2g
kotlin.compiler.preciseCompilationResultsBackup=true
kotlin.build.report.output=file

# =============================================================================
# KSP
# =============================================================================
ksp.incremental=true

# =============================================================================
# Android
# =============================================================================
android.useAndroidX=true
android.nonTransitiveRClass=true
android.enableR8.fullMode=true
android.defaults.buildfeatures.buildconfig=false
android.defaults.buildfeatures.resValues=false

# =============================================================================
# Compose
# =============================================================================
# Strong skipping is default in Compose Compiler 2.0+
# Stability config is set per-module in build.gradle.kts

# =============================================================================
# Kotlin/Native (iOS)
# =============================================================================
kotlin.native.cacheKind.iosX64=none
kotlin.native.cacheKind.iosArm64=none
kotlin.native.cacheKind.iosSimulatorArm64=none
# Set to 'static' once stable for your dependency set
```

### When to Adjust

- **Low-memory machines**: Reduce `-Xmx4g` to `-Xmx2g` and `workers.max` to 4
- **CI environments**: Override with `org.gradle.daemon=false` and `org.gradle.vfs.watch=false`
- **Kotlin/Native caching**: Switch `kotlin.native.cacheKind.*` to `static` once all native dependencies support it

---

## Example 2: build-logic Convention Plugin

Setting up an included build for shared build configuration, replacing `buildSrc`.

### Directory Structure

```
project-root/
  build-logic/
    convention/
      build.gradle.kts
      src/main/kotlin/
        kmp-library-convention.gradle.kts
        android-app-convention.gradle.kts
        compose-convention.gradle.kts
  settings.gradle.kts
  build.gradle.kts
  feature/
    home/
      build.gradle.kts
    profile/
      build.gradle.kts
  core/
    network/
      build.gradle.kts
```

### build-logic/convention/build.gradle.kts

```kotlin
plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
}
```

### build-logic/convention/src/main/kotlin/kmp-library-convention.gradle.kts

```kotlin
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    androidTarget {
        compilations.all {
            compilerOptions.configure {
                jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            }
        }
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    jvm("desktop")

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            // Shared dependencies
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
```

### build-logic/convention/src/main/kotlin/android-app-convention.gradle.kts

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    compileSdk = 35
    defaultConfig {
        minSdk = 26
        targetSdk = 35
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
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

### build-logic/convention/src/main/kotlin/compose-convention.gradle.kts

```kotlin
plugins {
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

composeCompiler {
    stabilityConfigurationFile = rootProject.layout.projectDirectory.file("stability-config.txt")
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    metricsDestination = layout.buildDirectory.dir("compose_compiler")
}
```

### Root settings.gradle.kts

```kotlin
pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "my-kmp-project"

include(":app")
include(":feature:home")
include(":feature:profile")
include(":core:network")
include(":core:data")
include(":core:ui")
```

### Usage in Feature Module

```kotlin
// feature/home/build.gradle.kts
plugins {
    id("kmp-library-convention")
    id("compose-convention")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:ui"))
            implementation(project(":core:data"))
        }
    }
}
```

### Why build-logic Instead of buildSrc

| Aspect | buildSrc | build-logic |
|--------|----------|-------------|
| Recompilation | Any change recompiles entire build | Only changed convention plugins recompile |
| Cache invalidation | Invalidates all task caches | Isolated; no cache invalidation |
| Configuration cache | Can cause issues | Fully compatible |
| Build time impact | 5-15s per change | < 1s per change |

---

## Example 3: Configuration Cache Fix

Common incompatibilities and their fixes when enabling the configuration cache.

### Problem: Task Accesses `project` at Execution Time

```kotlin
// BROKEN: Captures Project reference at execution time
abstract class GenerateConfigTask : DefaultTask() {
    @TaskAction
    fun generate() {
        val version = project.version  // Configuration cache violation
        val outputDir = project.layout.buildDirectory.get()
        File(outputDir.asFile, "config.txt").writeText("version=$version")
    }
}
```

```kotlin
// FIXED: Use Property inputs instead
abstract class GenerateConfigTask : DefaultTask() {
    @get:Input
    abstract val projectVersion: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val version = projectVersion.get()
        File(outputDir.get().asFile, "config.txt").writeText("version=$version")
    }
}

// Registration
tasks.register<GenerateConfigTask>("generateConfig") {
    projectVersion.set(project.version.toString())
    outputDir.set(layout.buildDirectory.dir("generated"))
}
```

### Problem: Using `allprojects {}` or `subprojects {}`

```kotlin
// BROKEN: Evaluates all projects eagerly
// root build.gradle.kts
allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}
```

```kotlin
// FIXED: Move to convention plugins and settings.gradle.kts
// settings.gradle.kts
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// build-logic/convention/src/main/kotlin/kotlin-convention.gradle.kts
plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Each module applies:
// plugins { id("kotlin-convention") }
```

### Problem: BuildListener or TaskExecutionListener

```kotlin
// BROKEN: Listeners are not serializable
gradle.addBuildListener(object : BuildAdapter() {
    override fun buildFinished(result: BuildResult) {
        println("Build finished in ${result.gradle?.startParameter}")
    }
})
```

```kotlin
// FIXED: Use BuildEventsListenerRegistry
abstract class BuildResultService :
    BuildService<BuildResultService.Params>,
    OperationCompletionListener {

    interface Params : BuildServiceParameters

    override fun onFinish(event: FinishEvent) {
        if (event is TaskFinishEvent) {
            println("Task ${event.descriptor.taskPath} finished")
        }
    }
}

// In plugin or build script
val buildResultService = gradle.sharedServices.registerIfAbsent(
    "buildResult", BuildResultService::class
) {}
gradle.services.get(BuildEventsListenerRegistry::class.java)
    .onTaskCompletion(buildResultService)
```

### Migration Strategy

1. Enable with `warn` mode: `org.gradle.configuration-cache.problems=warn`
2. Set a high max: `org.gradle.configuration-cache.max-problems=512`
3. Run a full build and collect all warnings
4. Fix violations starting with your own build scripts, then file issues for third-party plugins
5. Reduce `max-problems` as you fix issues
6. Switch to `fail` mode when all problems are resolved

---

## Example 4: Compose Compiler Stability

Configuring stability for optimal Compose performance.

### stability-config.txt (Project Root)

```text
// Java standard library types
java.time.LocalDate
java.time.LocalDateTime
java.time.Instant
java.time.ZonedDateTime
java.time.Duration
java.time.LocalTime
java.util.UUID
java.util.Locale
java.math.BigDecimal

// Kotlinx types
kotlinx.datetime.LocalDate
kotlinx.datetime.LocalDateTime
kotlinx.datetime.Instant
kotlinx.datetime.Clock

// Kotlinx immutable collections (always stable)
kotlinx.collections.immutable.ImmutableList
kotlinx.collections.immutable.ImmutableMap
kotlinx.collections.immutable.ImmutableSet
kotlinx.collections.immutable.PersistentList
kotlinx.collections.immutable.PersistentMap
kotlinx.collections.immutable.PersistentSet

// Your own domain types from external modules
com.example.core.model.UserId
com.example.core.model.Currency
com.example.core.model.Coordinate
```

### Making Data Classes Stable

```kotlin
// UNSTABLE: Contains mutable or non-stable types
data class UserProfile(
    val name: String,              // Stable (primitive-like)
    val tags: List<String>,        // UNSTABLE: List is mutable interface
    val metadata: Map<String, Any> // UNSTABLE: Map is mutable, Any is not stable
)

// STABLE: Use immutable collections and concrete types
data class UserProfile(
    val name: String,
    val tags: ImmutableList<String>,
    val metadata: ImmutableMap<String, String>
)
```

### Using @Stable and @Immutable Annotations

```kotlin
// For classes that are logically immutable but the compiler cannot infer it
@Stable
class ThemeConfiguration(
    val primaryColor: Long,
    val secondaryColor: Long,
    val typography: Typography  // Custom type; annotating parent as @Stable
)

// For truly immutable data
@Immutable
data class Coordinate(
    val latitude: Double,
    val longitude: Double
)
```

### Analyzing Stability Reports

```bash
# After building with metrics enabled, check:
cat app/build/compose_compiler/app_release-classes.txt

# Look for lines like:
# unstable class UserProfile {
#   stable val name: String
#   unstable val tags: List<String>    <-- Problem here
#   <runtime stability> = Unstable
# }
```

### Strong Skipping Mode

Strong skipping is enabled by default in Compose Compiler 2.0+. It allows composables to skip recomposition even when they have unstable parameters, as long as the parameter instances are the same (referential equality). This reduces the urgency of making everything stable but stability still matters for correctness and for cases where new instances are created.

---

## Example 5: Module Dependency Graph

Comparing optimal versus problematic module structures.

### Problematic: Star Graph

```
         +--------+
         |  :app  |
         +--------+
        /  |  |  |  \
       /   |  |  |   \
      v    v  v  v    v
  :feat1 :feat2 :feat3 :feat4 :feat5
      \   |  |  |   /
       \  |  |  |  /
        v v  v  v v
       +-----------+
       |   :core   |  (monolithic core)
       +-----------+
```

Problems:
- `:core` changes invalidate ALL feature modules
- Feature modules cannot build in parallel (all depend on `:core`)
- `:core` grows into a "god module" with unrelated concerns

### Optimal: Layered Graph

```
                  +--------+
                  |  :app  |
                  +--------+
                 /    |     \
                v     v      v
          :feature:  :feature:  :feature:
           home      profile    settings
            |    \     |         |
            v     v    v         v
        :domain:  :domain:   :domain:
         user      media      prefs
            \       |        /
             v      v       v
           :core:  :core:  :core:
            model   network  storage
```

Benefits:
- Each core module is focused (single responsibility)
- Feature modules depend only on the domain modules they need
- Maximum parallelism: `:core:model`, `:core:network`, `:core:storage` build in parallel
- Changes to `:core:network` only rebuild `:domain:media` and its dependents

### Module Graph Best Practices

```kotlin
// settings.gradle.kts -- Organized module inclusion
include(":app")

// Core layer (no dependencies on other project modules)
include(":core:model")
include(":core:network")
include(":core:storage")
include(":core:ui")
include(":core:testing")

// Domain layer (depends only on core)
include(":domain:user")
include(":domain:media")
include(":domain:preferences")

// Feature layer (depends on domain + core:ui)
include(":feature:home")
include(":feature:profile")
include(":feature:settings")
include(":feature:onboarding")
```

### Enforcing Module Boundaries

```kotlin
// In a convention plugin: enforce that feature modules never depend on other features
// build-logic/convention/src/main/kotlin/feature-convention.gradle.kts
afterEvaluate {
    configurations.all {
        dependencies.forEach { dep ->
            if (dep is ProjectDependency) {
                val path = dep.dependencyProject.path
                check(!path.startsWith(":feature:")) {
                    "Feature module ${project.path} must not depend on another feature module ($path). " +
                        "Extract shared logic to a domain or core module."
                }
            }
        }
    }
}
```

---

## Example 6: CI Build Optimization

GitHub Actions workflow optimized for KMP builds.

### .github/workflows/build.yml

```yaml
name: Build & Test

on:
  pull_request:
    branches: [main]
  push:
    branches: [main]

concurrency:
  group: build-${{ github.ref }}
  cancel-in-progress: true

env:
  GRADLE_OPTS: "-Xmx4g -XX:+UseG1GC -Dorg.gradle.daemon=false"

jobs:
  build:
    runs-on: ubuntu-latest
    timeout-minutes: 30

    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: 'zulu'
          java-version: '17'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4
        with:
          cache-read-only: ${{ github.ref != 'refs/heads/main' }}
          gradle-home-cache-cleanup: true

      - name: Build all targets
        run: ./gradlew assemble --no-daemon --build-cache --configuration-cache --parallel

      - name: Run tests
        run: ./gradlew check --no-daemon --build-cache --configuration-cache --parallel

      - name: Upload test results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-results
          path: '**/build/reports/tests/'

  ios-build:
    runs-on: macos-14
    timeout-minutes: 45

    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: 'zulu'
          java-version: '17'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Cache Konan
        uses: actions/cache@v4
        with:
          path: ~/.konan
          key: konan-${{ runner.os }}-${{ hashFiles('**/*.gradle*', '**/libs.versions.toml') }}
          restore-keys: konan-${{ runner.os }}-

      - name: Build iOS framework
        run: ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 --no-daemon --build-cache --parallel
```

### Key CI Optimizations

| Technique | Impact | Implementation |
|-----------|--------|----------------|
| `concurrency.cancel-in-progress` | Avoid redundant builds | Cancel previous runs on same PR |
| `cache-read-only` on PRs | Prevent cache pollution | Only `main` pushes write cache |
| `gradle-home-cache-cleanup` | Save disk space | Remove old cache entries |
| `--no-daemon` | Avoid daemon startup overhead | One-shot CI builds |
| Separate iOS job | Parallel execution | macOS runner only for iOS |
| Konan cache | Avoid re-downloading K/N compiler | Separate cache key |

### Advanced: Matrix Strategy for Multiple Targets

```yaml
jobs:
  test:
    strategy:
      fail-fast: false
      matrix:
        include:
          - target: jvm
            task: jvmTest
            os: ubuntu-latest
          - target: android
            task: testDebugUnitTest
            os: ubuntu-latest
          - target: ios
            task: iosSimulatorArm64Test
            os: macos-14
          - target: desktop
            task: desktopTest
            os: ubuntu-latest

    runs-on: ${{ matrix.os }}
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'zulu'
          java-version: '17'
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew ${{ matrix.task }} --no-daemon --build-cache --parallel
```

---

## Example 7: Build Scan Analysis

Reading and acting on build scan results to identify optimization opportunities.

### Step 1: Generate a Build Scan

```bash
# Run a full build with scan
./gradlew assembleDebug check --scan

# Output will include a URL like:
# Publishing build scan...
# https://scans.gradle.com/s/abcdef123456
```

### Step 2: Analyze the Performance Tab

Key areas to examine:

**Build Duration Breakdown**
```
Total build time:    4m 32s
  Configuration:       12s    (2.6%)   <-- Should be < 5s
  Dependency resolution: 8s   (2.9%)   <-- Should be < 3s
  Task execution:    4m 12s   (92.7%)  <-- Focus area
  Overhead:            0.8s   (0.3%)
```

**If configuration time is high (> 5s):**
- Enable configuration cache
- Move from `buildSrc` to `build-logic`
- Remove unnecessary `allprojects`/`subprojects` blocks

**If dependency resolution is high (> 3s):**
- Enable dependency locking
- Use a repository mirror / proxy
- Reduce the number of repositories

### Step 3: Analyze Task Execution

**Task Avoidance Rate**
```
Total tasks:        247
  Executed:          42   (17.0%)
  Up-to-date:      185   (74.9%)
  From cache:        15   (6.1%)
  Skipped:            5   (2.0%)
```

Target: At least 70% avoided (UP-TO-DATE + FROM-CACHE) on incremental builds.

**If avoidance rate is low:**
- Check for tasks with volatile inputs (timestamps, git hashes in non-reproducible way)
- Verify build cache is enabled and working
- Look for tasks that disable caching (`outputs.cacheIf { false }`)

### Step 4: Identify Longest Tasks

```
Top 10 slowest tasks:
1. :app:compileDebugKotlin                    45s
2. :shared:compileKotlinIosArm64              38s
3. :shared:compileKotlinIosSimulatorArm64     35s
4. :app:mergeDebugResources                   22s
5. :feature:home:compileDebugKotlin           18s
6. :shared:compileKotlinJvm                   15s
7. :app:packageDebug                          12s
8. :core:network:compileDebugKotlin            8s
9. :app:compileDebugJavaWithJavac              6s
10. :feature:profile:compileDebugKotlin        5s
```

**Actions based on findings:**
- Tasks 1-3 are Kotlin compilation: ensure incremental compilation is enabled, check for non-incremental annotation processors
- Task 4 is resource merging: enable `android.nonTransitiveRClass=true`
- If multiple feature modules have similar compile times, check they build in parallel (timeline view)

### Step 5: Check the Timeline View

The timeline shows task execution as a Gantt chart across worker threads.

```
Worker 1: [compileDebugKotlin (:app)         ] [packageDebug]
Worker 2: [compileKotlinIosArm64 (:shared)   ] [linkDebug   ]
Worker 3: [compileKotlinIosSim (:shared)     ]
Worker 4: [compileDebugKotlin (:feature:home)] [test        ]
Worker 5: [idle.........................][compileDebugKotlin (:feature:profile)]
Worker 6: [idle.........................][idle........................]
```

**Red flags in timeline:**
- Long idle periods for workers: module graph is too serial
- One very long task blocking everything: consider splitting the module
- Tasks not starting until late: dependency chain is too deep

### Step 6: Create an Optimization Action Plan

Based on the scan analysis, create targeted optimizations:

```
Optimization Plan (from build scan analysis)
=============================================
1. [HIGH] Enable configuration cache           -> Save 12s per build
2. [HIGH] Split :shared into :shared:data      -> Enable parallel iOS/JVM compilation
   and :shared:ui
3. [MED]  Enable nonTransitiveRClass           -> Save 22s on resource merging
4. [MED]  Add remote build cache for CI        -> Save 2-3 min on CI builds
5. [LOW]  Replace kapt with KSP in :app       -> Enable incremental compilation
6. [LOW]  Lock dependencies                    -> Save 8s on resolution
```

### Ongoing Monitoring

```bash
# Compare two build scans
# Develocity provides comparison view: /s/<id1>/compare/<id2>

# Track build time trends
# Set up build scan publishing on every CI build
# Monitor the Develocity dashboard for regressions

# Automated alerts
# Develocity can alert when build time exceeds threshold
```
