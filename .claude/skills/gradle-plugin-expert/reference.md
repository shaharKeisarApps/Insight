# Gradle Convention Plugin Reference (Gradle 8.x + Kotlin DSL)

> Targets: KMP with Android, iOS, JVM, wasmJs
> Kotlin 2.3.10 | Compose 1.10.0 | Metro 0.10.1 | Circuit 0.32.0
> Convention plugins live in `build-logic/convention/src/main/kotlin/`

---

## build-logic/settings.gradle.kts

The build-logic module is a standalone Gradle build. Its `settings.gradle.kts` configures access to the root project's version catalog and plugin repositories.

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

**Notes:**
- `from(files("../gradle/libs.versions.toml"))` bridges the root project's catalog into build-logic.
- `rootProject.name` must be set (Gradle requirement for included builds).
- Multiple subprojects (e.g., `:convention`, `:testing`) are possible but one is typical.

---

## build-logic/convention/build.gradle.kts

This is where you declare dependencies on Gradle plugin artifacts and register your convention plugins.

```kotlin
plugins {
    `kotlin-dsl`  // Enables Kotlin DSL for plugin authoring + java-gradle-plugin
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
    // Gradle plugin artifacts -- needed to programmatically configure these plugins
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
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

**Notes:**
- `kotlin-dsl` plugin implies `java-gradle-plugin` + Kotlin compiler for precompiled script plugins.
- Use `compileOnly` for plugin artifacts -- they are provided at runtime by Gradle's classpath.
- The `register("name")` name is arbitrary; `id` is the plugin ID used in `build.gradle.kts` files.
- `implementationClass` is the fully-qualified class name (package optional if in default package).

---

## Plugin<Project> Interface

The core Gradle API for convention plugins.

```kotlin
import org.gradle.api.Plugin
import org.gradle.api.Project

class MyConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            // 1. Apply other plugins
            with(pluginManager) {
                apply("org.jetbrains.kotlin.multiplatform")
                apply("com.android.library")
            }

            // 2. Configure extensions
            extensions.configure<LibraryExtension> {
                compileSdk = 35
            }

            // 3. Add dependencies
            dependencies {
                add("implementation", libs.findLibrary("ktor-core").get())
            }

            // 4. Configure tasks
            tasks.withType<KotlinCompile>().configureEach {
                compilerOptions {
                    freeCompilerArgs.addAll("-Xexpect-actual-classes")
                }
            }
        }
    }
}
```

### Key `Project` Methods Used in Plugins

| Method | Purpose |
|--------|---------|
| `pluginManager.apply(id)` | Apply a plugin by ID (no version) |
| `pluginManager.withPlugin(id) { }` | Run block only if plugin is already applied |
| `pluginManager.hasPlugin(id)` | Check if plugin is applied |
| `extensions.configure<T> { }` | Configure an extension by type |
| `extensions.getByType<T>()` | Get an extension (throws if not found) |
| `extensions.findByType<T>()` | Get an extension or null |
| `the<T>()` | Shortcut for `extensions.getByType<T>()` |
| `dependencies { add(config, dep) }` | Add a dependency programmatically |
| `tasks.withType<T>().configureEach { }` | Configure all tasks of a type lazily |
| `tasks.register<T>(name) { }` | Register a new task lazily |

---

## Version Catalog Access (VersionCatalogsExtension)

Convention plugin classes cannot use the generated `libs` accessor. Use `VersionCatalogsExtension` instead.

```kotlin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.the

internal val Project.libs: VersionCatalog
    get() = the<VersionCatalogsExtension>().named("libs")
```

### VersionCatalog API

| Method | Return Type | Example |
|--------|-------------|---------|
| `findVersion("key")` | `Optional<VersionConstraint>` | `libs.findVersion("kotlin").get().toString()` |
| `findLibrary("key")` | `Optional<MinimalDependency>` | `libs.findLibrary("ktor-core").get()` |
| `findPlugin("key")` | `Optional<PluginDependency>` | `libs.findPlugin("kotlin-multiplatform").get().get().pluginId` |
| `findBundle("key")` | `Optional<ExternalModuleDependencyBundle>` | `libs.findBundle("compose").get()` |

**Notes:**
- Catalog keys use dashes or dots: `libs.findLibrary("ktor-client-core")`.
- `.get()` unwraps the `Optional`. Use `.orElse(null)` for optional lookups.
- For plugin IDs: `libs.findPlugin("kotlin-multiplatform").get().get().pluginId` returns the plugin ID string.

---

## KotlinMultiplatformExtension Configuration

Used inside `Plugin<Project>.apply()` after applying the Kotlin Multiplatform plugin.

```kotlin
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

fun Project.configureKotlinMultiplatform() {
    extensions.configure<KotlinMultiplatformExtension> {
        // Targets
        androidTarget {
            compilations.all {
                compilerOptions.configure {
                    jvmTarget.set(JvmTarget.JVM_17)
                }
            }
        }
        iosX64()
        iosArm64()
        iosSimulatorArm64()
        jvm()

        // Convenience target group
        applyDefaultHierarchyTemplate()

        // Source sets
        sourceSets {
            commonMain.dependencies {
                implementation(libs.findLibrary("kotlinx-coroutines-core").get())
            }
            commonTest.dependencies {
                implementation(kotlin("test"))
            }
            androidMain.dependencies {
                implementation(libs.findLibrary("kotlinx-coroutines-android").get())
            }
        }

        // Compiler options for all targets
        compilerOptions {
            freeCompilerArgs.addAll(
                "-Xexpect-actual-classes",
            )
        }
    }
}
```

**Notes:**
- `applyDefaultHierarchyTemplate()` creates intermediate source sets (`iosMain`, `nativeMain`, etc.).
- `androidTarget()` replaced `android()` in Kotlin 2.0+.
- Use `sourceSets { commonMain.dependencies { } }` -- not the older `val commonMain by getting { }` syntax.

---

## Android Extension Configuration

Used after applying `com.android.library` or `com.android.application`.

```kotlin
import com.android.build.api.dsl.CommonExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension

// Shared Android configuration (works for both library and application)
internal fun Project.configureKotlinAndroid(
    commonExtension: CommonExtension<*, *, *, *, *, *>,
) {
    commonExtension.apply {
        compileSdk = 35

        defaultConfig {
            minSdk = 26
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }

        packaging {
            resources {
                excludes += "/META-INF/{AL2.0,LGPL2.1}"
            }
        }
    }
}

// Library-specific usage
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply("org.jetbrains.kotlin.android")

            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
                defaultConfig.targetSdk = 35
            }
        }
    }
}

// Application-specific usage
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("org.jetbrains.kotlin.android")

            extensions.configure<BaseAppModuleExtension> {
                configureKotlinAndroid(this)
                defaultConfig.targetSdk = 35
            }
        }
    }
}
```

**Notes:**
- `CommonExtension` is the shared supertype of `LibraryExtension` and `BaseAppModuleExtension`.
- `compileSdk = 35` corresponds to Android 15.
- The generic parameters on `CommonExtension<*, *, *, *, *, *>` changed in AGP 8.x (6 type params).

---

## Compose Multiplatform Configuration

Used after applying the JetBrains Compose plugin.

```kotlin
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

fun Project.configureComposeMultiplatform() {
    // JB Compose plugin (resources, desktop app, etc.)
    extensions.configure<ComposeExtension> {
        // ComposeExtension from JB plugin -- provides `dependencies { }` block
        // with compose.material3, compose.runtime, etc.
    }

    // Kotlin Compose Compiler plugin (separate from JB Compose)
    extensions.configure<ComposeCompilerGradlePluginExtension> {
        // Stability configuration
        stabilityConfigurationFile.set(
            rootProject.layout.projectDirectory.file("compose-stability.conf")
        )
        // Metrics and reports (debug only)
        if (project.findProperty("enableComposeReports") == "true") {
            reportsDestination.set(layout.buildDirectory.dir("compose-reports"))
            metricsDestination.set(layout.buildDirectory.dir("compose-metrics"))
        }
    }
}
```

**Notes:**
- JetBrains Compose plugin (`org.jetbrains.compose`) and Kotlin Compose Compiler plugin (`org.jetbrains.kotlin.plugin.compose`) are separate.
- As of Kotlin 2.0+, the Compose compiler is distributed as a Kotlin compiler plugin, not a separate artifact.
- `ComposeExtension` provides the `compose.xxx` dependency accessors (runtime, material3, etc.).

---

## Applying Plugins from Convention Plugins

### Direct Apply

```kotlin
pluginManager.apply("org.jetbrains.kotlin.multiplatform")
```

### Conditional Apply

```kotlin
pluginManager.withPlugin("com.android.library") {
    // Only runs if android library plugin is already applied
    extensions.configure<LibraryExtension> {
        // ...
    }
}
```

### Check Before Apply

```kotlin
if (!pluginManager.hasPlugin("org.jetbrains.kotlin.multiplatform")) {
    pluginManager.apply("org.jetbrains.kotlin.multiplatform")
}
```

### Apply Using Catalog Plugin ID

```kotlin
val pluginId = libs.findPlugin("kotlin-multiplatform").get().get().pluginId
pluginManager.apply(pluginId)
```

---

## gradlePlugin { } Registration DSL

```kotlin
gradlePlugin {
    plugins {
        register("registrationName") {
            id = "my.project.plugin.id"       // Applied via id("my.project.plugin.id")
            implementationClass = "com.example.MyPlugin"  // FQCN of Plugin<Project>
            displayName = "My Convention Plugin"           // Optional metadata
            description = "Configures KMP library defaults"  // Optional metadata
        }
    }
}
```

**Notes:**
- `registrationName` is internal to the build-logic project.
- `id` is the public plugin ID consumers use.
- `implementationClass` must match the class name exactly.
- Plugins are resolved from included builds automatically -- no `pluginManagement` resolution needed.

---

## Root settings.gradle.kts Integration

```kotlin
pluginManagement {
    includeBuild("build-logic")  // Makes convention plugins available
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
    }
}

rootProject.name = "MyKmpApp"

include(":app")
include(":feature:home:api")
include(":feature:home:impl")
include(":core:network")
include(":core:database")
```

**Notes:**
- `includeBuild("build-logic")` must be inside `pluginManagement { }`.
- `FAIL_ON_PROJECT_REPOS` prevents per-module repository declarations for consistency.
- Convention plugin IDs become available to all modules after `includeBuild`.

---

## Adding Dependencies Programmatically

```kotlin
// Single dependency
dependencies {
    add("implementation", libs.findLibrary("ktor-core").get())
}

// KSP dependency
dependencies {
    add("kspCommonMainMetadata", libs.findLibrary("circuit-codegen").get())
}

// Platform-specific
dependencies {
    add("androidMainImplementation", libs.findLibrary("androidx-core").get())
}

// Project dependency
dependencies {
    add("implementation", project(":core:network"))
}

// Using a bundle
dependencies {
    val composeBundleProvider = libs.findBundle("compose").get()
    add("commonMainImplementation", composeBundleProvider)
}
```

---

## Task Configuration in Plugins

```kotlin
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// Configure all Kotlin compile tasks
tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
        )
    }
}

// Register a custom task
tasks.register("printModuleInfo") {
    doLast {
        println("Module: ${project.name}")
        println("Path: ${project.path}")
    }
}
```

---

## Metro Plugin Configuration from Convention Plugin

```kotlin
import dev.zacsweers.metro.gradle.MetroPluginExtension

fun Project.configureMetro() {
    pluginManager.apply("dev.zacsweers.metro")

    extensions.configure<MetroPluginExtension> {
        enabled.set(true)
        debug.set(false)
        transformProvidersToPrivate.set(true)
        shrinkUnusedBindings.set(true)
        reportsDestination.set(layout.buildDirectory.dir("metro"))
    }
}
```

---

## Circuit KSP Configuration from Convention Plugin

```kotlin
fun Project.configureCircuit() {
    pluginManager.apply("com.google.devtools.ksp")

    // Set Circuit codegen mode for Metro
    extensions.configure<com.google.devtools.ksp.gradle.KspExtension> {
        arg("circuit.codegen.mode", "METRO")
    }

    dependencies {
        add("kspCommonMainMetadata", libs.findLibrary("circuit-codegen").get())
        add("commonMainImplementation", libs.findLibrary("circuit-codegen-annotations").get())
    }
}
```

---

## Key Imports for Convention Plugins

```kotlin
// Gradle core
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

// Android
import com.android.build.api.dsl.CommonExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension

// Kotlin
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// Compose
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension
```
