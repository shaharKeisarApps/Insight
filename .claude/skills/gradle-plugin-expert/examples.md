# Gradle Convention Plugin Production Examples

> Enterprise KMP build-logic patterns.
> Gradle 8.x | Kotlin 2.3.10 | Compose 1.10.0 | Metro 0.10.1 | Circuit 0.32.0
> Convention plugins live in `build-logic/convention/src/main/kotlin/`

---

## 1. Full build-logic Setup

The complete scaffolding for a `build-logic` included build.

### build-logic/settings.gradle.kts

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")
```

### build-logic/convention/build.gradle.kts

```kotlin
plugins {
    `kotlin-dsl`
}

group = "my.project.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.compose.compiler.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.metro.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "my.kmp.library"
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("androidLibrary") {
            id = "my.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidApplication") {
            id = "my.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("composeMultiplatform") {
            id = "my.compose.multiplatform"
            implementationClass = "ComposeConventionPlugin"
        }
        register("kmpFeatureApi") {
            id = "my.kmp.feature.api"
            implementationClass = "KmpFeatureApiConventionPlugin"
        }
        register("kmpFeatureImpl") {
            id = "my.kmp.feature.impl"
            implementationClass = "KmpFeatureImplConventionPlugin"
        }
    }
}
```

### gradle/libs.versions.toml (relevant entries)

```toml
[versions]
kotlin = "2.3.10"
agp = "8.7.3"
compose-multiplatform = "1.10.0"
ksp = "2.3.10-1.0.30"
metro = "0.10.1"
circuit = "0.32.0"
compileSdk = "35"
minSdk = "26"
targetSdk = "35"

[libraries]
android-gradlePlugin = { group = "com.android.tools.build", name = "gradle", version.ref = "agp" }
kotlin-gradlePlugin = { group = "org.jetbrains.kotlin", name = "kotlin-gradle-plugin", version.ref = "kotlin" }
compose-gradlePlugin = { group = "org.jetbrains.compose", name = "compose-gradle-plugin", version.ref = "compose-multiplatform" }
compose-compiler-gradlePlugin = { group = "org.jetbrains.kotlin", name = "compose-compiler-gradle-plugin", version.ref = "kotlin" }
ksp-gradlePlugin = { group = "com.google.devtools.ksp", name = "com.google.devtools.ksp.gradle.plugin", version.ref = "ksp" }
metro-gradlePlugin = { group = "dev.zacsweers.metro", name = "metro-gradle-plugin", version.ref = "metro" }

circuit-foundation = { group = "com.slack.circuit", name = "circuit-foundation", version.ref = "circuit" }
circuit-codegen = { group = "com.slack.circuit", name = "circuit-codegen", version.ref = "circuit" }
circuit-codegen-annotations = { group = "com.slack.circuit", name = "circuit-codegen-annotations", version.ref = "circuit" }
circuit-runtime = { group = "com.slack.circuit", name = "circuit-runtime", version.ref = "circuit" }
circuit-retained = { group = "com.slack.circuit", name = "circuit-retained", version.ref = "circuit" }
circuit-overlay = { group = "com.slack.circuit", name = "circuit-overlay", version.ref = "circuit" }
circuit-test = { group = "com.slack.circuit", name = "circuit-test", version.ref = "circuit" }

metro-runtime = { group = "dev.zacsweers.metro", name = "runtime", version.ref = "metro" }

kotlinx-coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serialization" }

[plugins]
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
compose-multiplatform = { id = "org.jetbrains.compose", version.ref = "compose-multiplatform" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
metro = { id = "dev.zacsweers.metro", version.ref = "metro" }
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
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

rootProject.name = "MyKmpApp"

include(":app")
include(":feature:home:api")
include(":feature:home:impl")
include(":feature:settings:api")
include(":feature:settings:impl")
include(":core:network")
include(":core:database")
include(":core:designsystem")
include(":core:common")
```

### Root build.gradle.kts

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.metro) apply false
}
```

**Key points:**
- Root `build.gradle.kts` declares all plugins with `apply false` for version resolution only.
- `includeBuild("build-logic")` lives inside `pluginManagement { }` (not at root level).
- `compileOnly` in build-logic avoids leaking plugin dependencies to consuming modules.

---

## 2. Version Catalog Helper (ProjectExtensions.kt)

A utility file shared across all convention plugins.

```kotlin
// build-logic/convention/src/main/kotlin/ProjectExtensions.kt

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.the

/**
 * Provides access to the version catalog from convention plugin code.
 * Cannot use the generated `libs` accessor -- that only works in build.gradle.kts scripts.
 */
internal val Project.libs: VersionCatalog
    get() = the<VersionCatalogsExtension>().named("libs")

/**
 * Reads a version from the catalog as an Int (useful for compileSdk, minSdk).
 */
internal fun VersionCatalog.versionInt(alias: String): Int =
    findVersion(alias).get().toString().toInt()

/**
 * Reads a version from the catalog as a String.
 */
internal fun VersionCatalog.versionString(alias: String): String =
    findVersion(alias).get().toString()
```

**Usage in plugins:**
```kotlin
// In any convention plugin or extension function
val compileSdk = libs.versionInt("compileSdk")    // 35
val kotlinVersion = libs.versionString("kotlin")   // "2.3.10"
val ktorLib = libs.findLibrary("ktor-core").get()  // dependency provider
```

---

## 3. KmpLibraryConventionPlugin

Configures a Kotlin Multiplatform library module with Android, iOS, and JVM targets.

```kotlin
// build-logic/convention/src/main/kotlin/KmpLibraryConventionPlugin.kt

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.multiplatform")
                apply("com.android.library")
            }
            configureKotlinMultiplatform()
            configureKotlinAndroid()
        }
    }
}
```

### configureKotlinMultiplatform() Extension

```kotlin
// build-logic/convention/src/main/kotlin/KotlinMultiplatform.kt

import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

@OptIn(ExperimentalKotlinGradlePluginApi::class)
internal fun Project.configureKotlinMultiplatform() {
    extensions.configure<KotlinMultiplatformExtension> {
        // Android target
        androidTarget {
            compilations.all {
                compilerOptions.configure {
                    jvmTarget.set(JvmTarget.JVM_17)
                }
            }
        }

        // iOS targets
        iosX64()
        iosArm64()
        iosSimulatorArm64()

        // JVM target (for desktop or server)
        jvm()

        // Automatic hierarchy: creates iosMain, nativeMain, etc.
        applyDefaultHierarchyTemplate()

        // Shared compiler options for all targets
        compilerOptions {
            freeCompilerArgs.addAll(
                "-Xexpect-actual-classes",
            )
        }

        // Common dependencies
        sourceSets {
            commonMain.dependencies {
                implementation(libs.findLibrary("kotlinx-coroutines-core").get())
            }
            commonTest.dependencies {
                implementation(kotlin("test"))
                implementation(libs.findLibrary("kotlinx-coroutines-test").get())
            }
        }
    }
}
```

**Key points:**
- `applyDefaultHierarchyTemplate()` creates intermediate source sets automatically.
- `androidTarget()` is the current API (replaces deprecated `android()`).
- `-Xexpect-actual-classes` enables `expect class` declarations in common code.
- Shared test dependency on `kotlin("test")` provides platform-specific test runners.

---

## 4. AndroidLibraryConventionPlugin

Configures Android-specific defaults for library modules.

```kotlin
// build-logic/convention/src/main/kotlin/AndroidLibraryConventionPlugin.kt

import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.library")
                apply("org.jetbrains.kotlin.android")
            }

            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
                defaultConfig.targetSdk = libs.versionInt("targetSdk")
            }
        }
    }
}
```

### configureKotlinAndroid() Extension

```kotlin
// build-logic/convention/src/main/kotlin/KotlinAndroid.kt

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

internal fun Project.configureKotlinAndroid(
    commonExtension: CommonExtension<*, *, *, *, *, *>,
) {
    commonExtension.apply {
        compileSdk = libs.versionInt("compileSdk")

        defaultConfig {
            minSdk = libs.versionInt("minSdk")
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }

        packaging {
            resources {
                excludes += "/META-INF/{AL2.0,LGPL2.1}"
                excludes += "/META-INF/versions/9/previous-compilation-data.bin"
            }
        }
    }

    // Set JVM target for Kotlin tasks on Android
    tasks.withType(KotlinCompile::class.java).configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}
```

**Key points:**
- `CommonExtension<*, *, *, *, *, *>` works for both `LibraryExtension` and `BaseAppModuleExtension`.
- SDK values read from version catalog (`libs.versionInt("compileSdk")`) keep versions centralized.
- `packaging.resources.excludes` prevents duplicate META-INF files causing merge conflicts.

---

## 5. AndroidApplicationConventionPlugin

Configures the Android app module (`:app`).

```kotlin
// build-logic/convention/src/main/kotlin/AndroidApplicationConventionPlugin.kt

import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.application")
                apply("org.jetbrains.kotlin.android")
                apply("org.jetbrains.kotlin.plugin.compose")
                apply("org.jetbrains.compose")
            }

            extensions.configure<BaseAppModuleExtension> {
                configureKotlinAndroid(this)

                defaultConfig {
                    targetSdk = libs.versionInt("targetSdk")
                    versionCode = 1
                    versionName = "1.0.0"
                }

                buildFeatures {
                    compose = true
                    buildConfig = true
                }

                buildTypes {
                    debug {
                        applicationIdSuffix = ".debug"
                        versionNameSuffix = "-dev"
                        isMinifyEnabled = false
                    }
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
        }
    }
}
```

---

## 6. ComposeConventionPlugin

Enables JetBrains Compose Multiplatform and configures the Compose compiler.

```kotlin
// build-logic/convention/src/main/kotlin/ComposeConventionPlugin.kt

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

class ComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.compose")
                apply("org.jetbrains.kotlin.plugin.compose")
            }
            configureComposeMultiplatform()
        }
    }
}

internal fun Project.configureComposeMultiplatform() {
    // Configure Compose Compiler options
    extensions.configure<ComposeCompilerGradlePluginExtension> {
        // Point to stability configuration file
        val stabilityFile = rootProject.layout.projectDirectory.file("compose-stability.conf")
        if (stabilityFile.asFile.exists()) {
            stabilityConfigurationFile.set(stabilityFile)
        }

        // Compose metrics and reports (opt-in via gradle property)
        if (project.findProperty("enableComposeReports") == "true") {
            reportsDestination.set(layout.buildDirectory.dir("compose-reports"))
            metricsDestination.set(layout.buildDirectory.dir("compose-metrics"))
        }
    }
}
```

**Key points:**
- Two separate plugins: `org.jetbrains.compose` (JB Compose framework) and `org.jetbrains.kotlin.plugin.compose` (Kotlin Compose compiler).
- `stabilityConfigurationFile` marks classes as stable so the compiler can skip recomposition.
- `reportsDestination` and `metricsDestination` are useful for debugging recomposition issues.

---

## 7. KmpFeatureApiConventionPlugin

For feature API modules containing Screen definitions and data models.

```kotlin
// build-logic/convention/src/main/kotlin/KmpFeatureApiConventionPlugin.kt

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class KmpFeatureApiConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("my.kmp.library")
                apply("org.jetbrains.kotlin.plugin.serialization")
            }

            // Add Circuit runtime for Screen type
            extensions.configure<KotlinMultiplatformExtension> {
                sourceSets {
                    commonMain.dependencies {
                        api(libs.findLibrary("circuit-runtime").get())
                        implementation(libs.findLibrary("kotlinx-serialization-json").get())
                    }
                }
            }
        }
    }
}
```

**Usage in module:**
```kotlin
// feature/home/api/build.gradle.kts
plugins {
    id("my.kmp.feature.api")
}

android {
    namespace = "my.project.feature.home.api"
}
```

The module only needs two lines -- all KMP targets, Android config, and shared dependencies come from convention plugins.

---

## 8. KmpFeatureImplConventionPlugin

The most complex plugin. Configures feature implementation modules with KMP + Compose + Metro DI + Circuit MVI.

```kotlin
// build-logic/convention/src/main/kotlin/KmpFeatureImplConventionPlugin.kt

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class KmpFeatureImplConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                // Convention plugins (composed)
                apply("my.kmp.library")
                apply("my.compose.multiplatform")

                // Third-party plugins
                apply("dev.zacsweers.metro")
                apply("com.google.devtools.ksp")
            }

            // Configure Metro
            configureMetro()

            // Configure Circuit KSP codegen
            configureCircuitCodegen()

            // Add feature-impl dependencies
            extensions.configure<KotlinMultiplatformExtension> {
                sourceSets {
                    commonMain.dependencies {
                        // Circuit
                        implementation(libs.findLibrary("circuit-foundation").get())
                        implementation(libs.findLibrary("circuit-retained").get())
                        implementation(libs.findLibrary("circuit-overlay").get())
                        implementation(libs.findLibrary("circuit-codegen-annotations").get())

                        // Metro runtime
                        implementation(libs.findLibrary("metro-runtime").get())

                        // Compose (from JB Compose plugin)
                        val compose = project.the<org.jetbrains.compose.ComposeExtension>()
                        implementation(compose.dependencies.runtime)
                        implementation(compose.dependencies.foundation)
                        implementation(compose.dependencies.material3)
                        implementation(compose.dependencies.ui)
                    }
                    commonTest.dependencies {
                        implementation(libs.findLibrary("circuit-test").get())
                    }
                }
            }
        }
    }
}
```

### configureMetro() Extension

```kotlin
// build-logic/convention/src/main/kotlin/Metro.kt

import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

internal fun Project.configureMetro() {
    // Metro Gradle plugin extension
    extensions.configure<dev.zacsweers.metro.gradle.MetroPluginExtension> {
        enabled.set(true)
        debug.set(false)
        transformProvidersToPrivate.set(true)
        shrinkUnusedBindings.set(true)
        reportsDestination.set(layout.buildDirectory.dir("metro"))
        interop {
            includeDagger()
        }
    }
}
```

### configureCircuitCodegen() Extension

```kotlin
// build-logic/convention/src/main/kotlin/CircuitCodegen.kt

import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

internal fun Project.configureCircuitCodegen() {
    extensions.configure<com.google.devtools.ksp.gradle.KspExtension> {
        arg("circuit.codegen.mode", "METRO")
    }

    dependencies {
        add("kspCommonMainMetadata", libs.findLibrary("circuit-codegen").get())
    }
}
```

**Usage in module:**
```kotlin
// feature/home/impl/build.gradle.kts
plugins {
    id("my.kmp.feature.impl")
}

android {
    namespace = "my.project.feature.home.impl"
}

dependencies {
    implementation(project(":feature:home:api"))
    implementation(project(":core:network"))
}
```

**Key points:**
- `my.kmp.feature.impl` composes `my.kmp.library` and `my.compose.multiplatform`.
- Metro and Circuit plugins are applied and configured automatically.
- The consuming `build.gradle.kts` only needs the namespace and project dependencies.
- KSP arg `circuit.codegen.mode = "METRO"` tells Circuit codegen to produce Metro-compatible factories.

---

## 9. Accessing Compose Dependencies from JB Plugin

When using the JetBrains Compose plugin, dependencies are accessed through the `ComposeExtension`:

```kotlin
// Inside a convention plugin or extension function
val compose = project.the<org.jetbrains.compose.ComposeExtension>()

extensions.configure<KotlinMultiplatformExtension> {
    sourceSets {
        commonMain.dependencies {
            // JB Compose dependency accessors
            implementation(compose.dependencies.runtime)
            implementation(compose.dependencies.foundation)
            implementation(compose.dependencies.material3)
            implementation(compose.dependencies.ui)
            implementation(compose.dependencies.components.resources)

            // Alternatively, from the version catalog
            implementation(libs.findLibrary("compose-runtime").get())
        }
        androidMain.dependencies {
            implementation(compose.dependencies.preview)
        }
    }
}
```

**Notes:**
- `compose.dependencies.xxx` comes from the JB Compose plugin and resolves to the correct version.
- You can also reference Compose artifacts through the version catalog for more explicit version control.
- `compose.dependencies.components.resources` provides `Res` class generation for multiplatform resources.

---

## 10. Plugin Composition Diagram

Shows how convention plugins layer on top of each other:

```
my.kmp.feature.impl
  |-- my.kmp.library
  |     |-- org.jetbrains.kotlin.multiplatform
  |     |-- com.android.library
  |     |-- configureKotlinMultiplatform()
  |     |-- configureKotlinAndroid()
  |
  |-- my.compose.multiplatform
  |     |-- org.jetbrains.compose
  |     |-- org.jetbrains.kotlin.plugin.compose
  |     |-- configureComposeMultiplatform()
  |
  |-- dev.zacsweers.metro
  |     |-- configureMetro()
  |
  |-- com.google.devtools.ksp
  |     |-- configureCircuitCodegen()
  |
  |-- Circuit + Metro dependencies

my.kmp.feature.api
  |-- my.kmp.library
  |     |-- (same as above)
  |-- org.jetbrains.kotlin.plugin.serialization
  |-- circuit-runtime (api dependency)
  |-- kotlinx-serialization-json
```

**Result:** A module `build.gradle.kts` using `id("my.kmp.feature.impl")` gets KMP, Android, Compose, Metro, Circuit, and all their configuration in a single line.

---

## 11. Conditional Configuration with withPlugin

Guard against double-applying or configure only when a specific plugin is present.

```kotlin
// build-logic/convention/src/main/kotlin/OptionalAndroidConfig.kt

import com.android.build.gradle.LibraryExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

internal fun Project.configureAndroidNamespace() {
    // Only configure if android plugin is applied
    pluginManager.withPlugin("com.android.library") {
        extensions.configure<LibraryExtension> {
            // Auto-derive namespace from project path
            // :feature:home:impl -> my.project.feature.home.impl
            if (namespace == null) {
                namespace = "my.project" + path
                    .replace(":", ".")
                    .replace("-", ".")
            }
        }
    }
}
```

**Key points:**
- `withPlugin` registers a callback that runs when the specified plugin is applied.
- This pattern is safe to call even if the plugin might not be applied.
- Useful for optional integrations (e.g., if a module optionally uses Compose).

---

## 12. Custom Extension for Convention Plugins

Define your own DSL extension for project-specific configuration.

```kotlin
// build-logic/convention/src/main/kotlin/MyProjectExtension.kt

import org.gradle.api.Project
import org.gradle.api.provider.Property

abstract class MyProjectExtension {
    abstract val enableMetrics: Property<Boolean>
    abstract val moduleName: Property<String>

    init {
        enableMetrics.convention(false)
    }
}

// Register the extension in a plugin
class MyProjectPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val ext = target.extensions.create("myProject", MyProjectExtension::class.java)

        // After evaluation, use the values
        target.afterEvaluate {
            if (ext.enableMetrics.getOrElse(false)) {
                configureMetrics()
            }
        }
    }
}
```

**Usage:**
```kotlin
// feature/home/impl/build.gradle.kts
plugins {
    id("my.project.plugin")
}

myProject {
    enableMetrics = true
    moduleName = "home"
}
```

---

## 13. Complete Module build.gradle.kts Files

### Feature API module

```kotlin
// feature/home/api/build.gradle.kts
plugins {
    id("my.kmp.feature.api")
}

android {
    namespace = "my.project.feature.home.api"
}
```

That is the entire file. All KMP targets, Android config, Circuit runtime, and serialization come from the convention plugin.

### Feature Impl module

```kotlin
// feature/home/impl/build.gradle.kts
plugins {
    id("my.kmp.feature.impl")
}

android {
    namespace = "my.project.feature.home.impl"
}

dependencies {
    implementation(project(":feature:home:api"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
}
```

### Core library module

```kotlin
// core/network/build.gradle.kts
plugins {
    id("my.kmp.library")
}

android {
    namespace = "my.project.core.network"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.ktor.core)
            api(libs.ktor.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.ktor.engine.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.engine.darwin)
        }
    }
}
```

### App module

```kotlin
// app/build.gradle.kts
plugins {
    id("my.android.application")
}

android {
    namespace = "my.project.app"
    defaultConfig {
        applicationId = "my.project.app"
    }
}

dependencies {
    implementation(project(":feature:home:impl"))
    implementation(project(":feature:settings:impl"))
    implementation(project(":core:designsystem"))
}
```

**Key insight:** Convention plugins reduce module `build.gradle.kts` files to only the unique configuration -- namespace, dependencies, and any module-specific overrides.

---

## 14. Handling KSP in KMP Convention Plugins

KSP with KMP requires special handling due to platform-specific configurations.

```kotlin
// build-logic/convention/src/main/kotlin/KspMultiplatform.kt

import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Adds a KSP processor for all KMP compilation targets.
 * In KMP, KSP configurations are target-specific (kspAndroid, kspIosArm64, etc.)
 * but kspCommonMainMetadata processes common source sets.
 */
internal fun Project.addKspProcessor(libraryAlias: String) {
    dependencies {
        // Process common code
        add("kspCommonMainMetadata", libs.findLibrary(libraryAlias).get())
    }

    // For full KSP on all targets (not just metadata):
    // afterEvaluate {
    //     val kmpExtension = the<KotlinMultiplatformExtension>()
    //     kmpExtension.targets.forEach { target ->
    //         val configName = "ksp${target.name.replaceFirstChar { it.uppercase() }}"
    //         if (configurations.findByName(configName) != null) {
    //             dependencies.add(configName, libs.findLibrary(libraryAlias).get())
    //         }
    //     }
    // }
}
```

---

## 15. Testing Convention Plugins

Convention plugins can be tested using Gradle TestKit.

```kotlin
// build-logic/convention/src/test/kotlin/KmpLibraryPluginTest.kt

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals

class KmpLibraryPluginTest {
    @TempDir
    lateinit var testProjectDir: File

    @BeforeEach
    fun setup() {
        // settings.gradle.kts
        File(testProjectDir, "settings.gradle.kts").writeText("""
            rootProject.name = "test-project"
        """.trimIndent())

        // build.gradle.kts
        File(testProjectDir, "build.gradle.kts").writeText("""
            plugins {
                id("my.kmp.library")
            }
            android {
                namespace = "test.project"
            }
        """.trimIndent())

        // local.properties (required for Android)
        File(testProjectDir, "local.properties").writeText(
            "sdk.dir=${System.getenv("ANDROID_HOME")}"
        )
    }

    @Test
    fun `plugin applies without errors`() {
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("tasks", "--stacktrace")
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":tasks")?.outcome)
    }
}
```

**Notes:**
- `withPluginClasspath()` makes convention plugins available to the test project.
- Add `testImplementation("org.gradle:gradle-test-kit")` to `build-logic/convention/build.gradle.kts`.
- TestKit tests verify plugin configuration without running the full build.
