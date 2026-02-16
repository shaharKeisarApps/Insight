# Modularization API Reference

## Module Dependency Rules

### Complete Dependency Matrix

| Source Module | Allowed Dependencies | Gradle Configuration |
|--------------|---------------------|---------------------|
| `app` | All `:feature-xxx-impl`, all `:core-*` | `implementation(project(...))` |
| `:feature-xxx-impl` | Own `:feature-xxx-api` | `implementation(project(":features:xxx:api"))` |
| `:feature-xxx-impl` | Other features' `:api` | `implementation(project(":features:yyy:api"))` |
| `:feature-xxx-impl` | `:core-ui`, `:core-network`, `:core-data`, `:core-model`, `:core-common` | `implementation(project(":core:..."))` |
| `:feature-xxx-api` | `:core-model`, `:core-common` | `api(project(":core:model"))` |
| `:core-ui` | `:core-model`, `:core-common` | `api(project(":core:model"))` |
| `:core-network` | `:core-model`, `:core-common` | `implementation(project(":core:model"))` |
| `:core-data` | `:core-model`, `:core-common` | `implementation(project(":core:model"))` |
| `:core-model` | `:core-common` (optional) | `api(project(":core:common"))` |
| `:core-common` | Nothing | -- |
| `:core-testing` | `:core-model`, `:core-common`, test libraries | `api(project(":core:model"))` |

### Forbidden Dependencies (Enforced)

| Source | Forbidden Target | Reason |
|--------|-----------------|--------|
| `:feature-xxx-impl` | `:feature-yyy-impl` | Creates hidden coupling; defeats api/impl split |
| `:feature-xxx-api` | `:feature-yyy-api` | Creates circular risk; extract shared types to `:core-model` |
| `:feature-xxx-api` | `:core-network`, `:core-data` | `:api` should be framework-free; only domain types |
| `:core-*` | `:feature-*` | Core modules must be feature-agnostic |
| Any module | Direct version strings | Use `libs.versions.toml` exclusively |

## Convention Plugin Configuration Reference

### Project Structure

```
project-root/
  build-logic/
    convention/
      build.gradle.kts              # Plugin project build file
      src/main/kotlin/
        KmpLibraryConventionPlugin.kt
        AndroidLibraryConventionPlugin.kt
        KmpFeatureApiConventionPlugin.kt
        KmpFeatureImplConventionPlugin.kt
        KmpComposeConventionPlugin.kt
        AndroidAppConventionPlugin.kt
        extensions/
          KotlinMultiplatformExtensions.kt
          AndroidExtensions.kt
    settings.gradle.kts              # build-logic settings
  gradle/
    libs.versions.toml               # Version catalog
  settings.gradle.kts                # Root settings (includeBuild + include)
  app/
    build.gradle.kts
  core/
    model/build.gradle.kts
    common/build.gradle.kts
    ui/build.gradle.kts
    network/build.gradle.kts
    data/build.gradle.kts
    testing/build.gradle.kts
  features/
    auth/
      api/build.gradle.kts
      impl/build.gradle.kts
    profile/
      api/build.gradle.kts
      impl/build.gradle.kts
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
    compileOnly(libs.metro.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
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
        register("kmpFeatureApi") {
            id = "my.kmp.feature.api"
            implementationClass = "KmpFeatureApiConventionPlugin"
        }
        register("kmpFeatureImpl") {
            id = "my.kmp.feature.impl"
            implementationClass = "KmpFeatureImplConventionPlugin"
        }
        register("kmpCompose") {
            id = "my.kmp.compose"
            implementationClass = "KmpComposeConventionPlugin"
        }
        register("androidApp") {
            id = "my.android.app"
            implementationClass = "AndroidAppConventionPlugin"
        }
    }
}
```

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

### Plugin: my.kmp.library

Configures a standard KMP library module with all targets, testing, and code quality.

```kotlin
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("org.jetbrains.kotlin.multiplatform")
            apply("com.android.library")
            apply("io.gitlab.arturbosch.detekt")
        }

        extensions.configure<KotlinMultiplatformExtension> {
            androidTarget {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                }
            }
            iosX64()
            iosArm64()
            iosSimulatorArm64()

            applyDefaultHierarchyTemplate()

            sourceSets {
                val commonMain by getting
                val commonTest by getting {
                    dependencies {
                        implementation(kotlin("test"))
                    }
                }
            }
        }

        extensions.configure<LibraryExtension> {
            compileSdk = libs.findVersion("compileSdk").get().requiredVersion.toInt()
            defaultConfig {
                minSdk = libs.findVersion("minSdk").get().requiredVersion.toInt()
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
        }
    }
}
```

### Plugin: my.kmp.feature.api

Extends `my.kmp.library` with Circuit runtime for Screen definitions.

```kotlin
class KmpFeatureApiConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("my.kmp.library")
        }

        extensions.configure<KotlinMultiplatformExtension> {
            sourceSets {
                val commonMain by getting {
                    dependencies {
                        api(libs.findLibrary("circuit.runtime.screen").get())
                        api(libs.findLibrary("kotlinx.immutable").get())
                    }
                }
            }
        }
    }
}
```

### Plugin: my.kmp.feature.impl

Extends `my.kmp.library` with Compose, Metro, Circuit codegen, and KSP.

```kotlin
class KmpFeatureImplConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("my.kmp.library")
            apply("my.kmp.compose")
            apply("dev.zacsweers.metro")
            apply("com.google.devtools.ksp")
        }

        extensions.configure<MetroExtension> {
            enabled.set(true)
            debug.set(false)
            transformProvidersToPrivate.set(true)
        }

        // Configure KSP for Circuit codegen
        extensions.configure<KspExtension> {
            arg("circuit.codegen.mode", "METRO")
        }

        extensions.configure<KotlinMultiplatformExtension> {
            sourceSets {
                val commonMain by getting {
                    dependencies {
                        implementation(libs.findLibrary("circuit.foundation").get())
                        implementation(libs.findLibrary("circuit.retained").get())
                        implementation(libs.findLibrary("circuit.codegenAnnotations").get())
                        implementation(libs.findLibrary("metro.runtime").get())
                        implementation(libs.findLibrary("kotlinx.immutable").get())
                    }
                }
                val commonTest by getting {
                    dependencies {
                        implementation(libs.findLibrary("circuit.test").get())
                    }
                }
            }
        }

        // Add KSP dependency for Circuit codegen
        dependencies {
            add("kspCommonMainMetadata", libs.findLibrary("circuit.codegen").get())
        }
    }
}
```

## `api()` vs `implementation()` Decision Guide

### When to Use `api()`

The `api()` configuration makes a dependency **transitive** -- any module that depends on your module also gets access to the `api()` dependency.

```kotlin
// :features:auth:api/build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            // api() because consumers of :auth:api need to see User, UserId types
            api(project(":core:model"))

            // api() because consumers need Circuit Screen type for navigation
            api(libs.circuit.runtime.screen)

            // api() because ImmutableList appears in public interfaces
            api(libs.kotlinx.immutable)
        }
    }
}
```

### When to Use `implementation()`

The `implementation()` configuration keeps a dependency **private** -- consumers of your module cannot see it.

```kotlin
// :features:auth:impl/build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            // implementation() -- consumers do not need :auth:api directly
            // (they already have it through their own dependency if needed)
            implementation(project(":features:auth:api"))

            // implementation() -- Ktor is an internal detail of the repository
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)

            // implementation() -- SQLDelight is an internal detail of local storage
            implementation(libs.sqldelight.runtime)

            // implementation() -- Metro runtime is used internally for DI
            implementation(libs.metro.runtime)

            // implementation() -- Compose is used internally for UI
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
        }
    }
}
```

### Decision Flowchart

```
Does the type appear in a public function signature,
return type, or class hierarchy of THIS module?
  |
  +-- YES --> Use api()
  |
  +-- NO ---> Does the type appear in a public property type?
                |
                +-- YES --> Use api()
                |
                +-- NO ---> Use implementation()
```

## Version Catalog Structure (`libs.versions.toml`)

### Modular Project Version Catalog

```toml
[versions]
# Kotlin & Build
kotlin = "2.3.10"
agp = "8.8.2"
ksp = "2.3.10-1.0.31"
compose-multiplatform = "1.10.0"

# SDK Versions (used by convention plugins)
compileSdk = "35"
minSdk = "28"
targetSdk = "35"

# DI & Architecture
metro = "0.10.1"
circuit = "0.32.0"

# Networking
ktor = "3.4.0"

# Data
sqldelight = "2.2.1"
store5 = "5.1.0-alpha08"
kotlinx-serialization = "1.10.0"

# Async
coroutines = "1.10.2"

# Collections
kotlinx-immutable = "0.3.8"

# Image Loading
coil = "3.3.0"

# Logging
kermit = "2.0.5"

# Code Quality
detekt = "1.23.8"

# Testing
turbine = "1.2.1"

[libraries]
# Kotlin
kotlin-gradlePlugin = { module = "org.jetbrains.kotlin:kotlin-gradle-plugin", version.ref = "kotlin" }

# Android Build
android-gradlePlugin = { module = "com.android.tools.build:gradle", version.ref = "agp" }

# Compose
compose-gradlePlugin = { module = "org.jetbrains.compose:compose-gradle-plugin", version.ref = "compose-multiplatform" }
compose-foundation = { module = "org.jetbrains.compose.foundation:foundation" }
compose-material3 = { module = "org.jetbrains.compose.material3:material3" }
compose-runtime = { module = "org.jetbrains.compose.runtime:runtime" }
compose-ui = { module = "org.jetbrains.compose.ui:ui" }

# Metro DI
metro-gradlePlugin = { module = "dev.zacsweers.metro:gradle-plugin", version.ref = "metro" }
metro-runtime = { module = "dev.zacsweers.metro:runtime", version.ref = "metro" }

# Circuit
circuit-foundation = { module = "com.slack.circuit:circuit-foundation", version.ref = "circuit" }
circuit-runtime = { module = "com.slack.circuit:circuit-runtime", version.ref = "circuit" }
circuit-runtime-screen = { module = "com.slack.circuit:circuit-runtime-screen", version.ref = "circuit" }
circuit-runtime-presenter = { module = "com.slack.circuit:circuit-runtime-presenter", version.ref = "circuit" }
circuit-runtime-ui = { module = "com.slack.circuit:circuit-runtime-ui", version.ref = "circuit" }
circuit-retained = { module = "com.slack.circuit:circuit-retained", version.ref = "circuit" }
circuit-overlay = { module = "com.slack.circuit:circuit-overlay", version.ref = "circuit" }
circuit-test = { module = "com.slack.circuit:circuit-test", version.ref = "circuit" }
circuit-codegen = { module = "com.slack.circuit:circuit-codegen", version.ref = "circuit" }
circuit-codegenAnnotations = { module = "com.slack.circuit:circuit-codegen-annotations", version.ref = "circuit" }
circuitx-gestureNav = { module = "com.slack.circuit:circuitx-gesture-navigation", version.ref = "circuit" }
circuitx-overlays = { module = "com.slack.circuit:circuitx-overlays", version.ref = "circuit" }

# Ktor
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-contentNegotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
ktor-client-darwin = { module = "io.ktor:ktor-client-darwin", version.ref = "ktor" }
ktor-client-okhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }

# SQLDelight
sqldelight-runtime = { module = "app.cash.sqldelight:runtime", version.ref = "sqldelight" }
sqldelight-coroutines = { module = "app.cash.sqldelight:coroutines-extensions", version.ref = "sqldelight" }
sqldelight-driver-android = { module = "app.cash.sqldelight:android-driver", version.ref = "sqldelight" }
sqldelight-driver-native = { module = "app.cash.sqldelight:native-driver", version.ref = "sqldelight" }
sqldelight-driver-jvm = { module = "app.cash.sqldelight:sqlite-driver", version.ref = "sqldelight" }

# KSP
ksp-gradlePlugin = { module = "com.google.devtools.ksp:symbol-processing-gradle-plugin", version.ref = "ksp" }

# Detekt
detekt-gradlePlugin = { module = "io.gitlab.arturbosch.detekt:detekt-gradle-plugin", version.ref = "detekt" }

# Collections
kotlinx-immutable = { module = "org.jetbrains.kotlinx:kotlinx-collections-immutable", version.ref = "kotlinx-immutable" }

# Serialization
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }

# Coroutines
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }

# Testing
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }

[plugins]
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
kotlin-parcelize = { id = "org.jetbrains.kotlin.plugin.parcelize", version.ref = "kotlin" }
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
compose-multiplatform = { id = "org.jetbrains.compose", version.ref = "compose-multiplatform" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
metro = { id = "dev.zacsweers.metro", version.ref = "metro" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
sqldelight = { id = "app.cash.sqldelight", version.ref = "sqldelight" }
detekt = { id = "io.gitlab.arturbosch.detekt", version.ref = "detekt" }
```

## Metro Wiring Across Modules

### How `@ContributesTo` Aggregates

Metro collects all `@ContributesTo(SomeScope::class)` interfaces at compile time from all modules that are on the classpath of the `@DependencyGraph(SomeScope::class)` module.

```
Module A (:feature-auth-impl)
  @ContributesTo(AppScope::class)
  interface AuthModule { ... }                  --+
                                                  |
Module B (:feature-profile-impl)                  |
  @ContributesTo(AppScope::class)                 +--> Metro collects all
  interface ProfileModule { ... }               --+    at compile time
                                                  |
Module C (:core-network)                          |
  @ContributesTo(AppScope::class)                 |
  interface NetworkModule { ... }               --+
                                                  |
App Module                                        |
  @DependencyGraph(AppScope::class)  <------------+
  interface AppGraph { ... }
```

**Key**: The `app` module must have `implementation()` dependencies on ALL `:impl` modules. If an `:impl` module is not on the classpath, its `@ContributesTo` interfaces are invisible to Metro.

### @ContributesBinding for Cross-Module Bindings

When a repository interface lives in `:api` and the implementation lives in `:impl`, use `@ContributesBinding`:

```kotlin
// In :feature-auth-api
interface AuthRepository {
    fun getUser(): Flow<User>
}

// In :feature-auth-impl
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AuthRepositoryImpl(
    private val api: AuthApi,
    private val dao: AuthDao,
) : AuthRepository {
    override fun getUser(): Flow<User> = dao.observeUser().map { it.toDomain() }
}
```

Metro generates the binding automatically: `AuthRepository` is bound to `AuthRepositoryImpl` in `AppScope`.

### @ContributesIntoSet / @ContributesIntoMap

Used by Circuit codegen to register Presenter.Factory and Ui.Factory implementations from feature modules into the central Circuit instance.

```kotlin
// Metro + Circuit codegen generates this from @CircuitInject
@ContributesIntoSet(AppScope::class)
class HomePresenter_Factory(...) : Presenter.Factory { ... }

@ContributesIntoSet(AppScope::class)
class HomeUi_Factory(...) : Ui.Factory { ... }
```

These factories are collected into `Set<Presenter.Factory>` and `Set<Ui.Factory>` by Metro, then provided to `Circuit.Builder` in the Circuit module.

## settings.gradle.kts Structure

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
    }
}

rootProject.name = "MyKmpApp"

// App
include(":app")

// Core modules
include(":core:model")
include(":core:common")
include(":core:ui")
include(":core:network")
include(":core:data")
include(":core:testing")

// Feature: Auth
include(":features:auth:api")
include(":features:auth:impl")

// Feature: Profile
include(":features:profile:api")
include(":features:profile:impl")

// Feature: Feed
include(":features:feed:api")
include(":features:feed:impl")

// Feature: Settings
include(":features:settings:api")
include(":features:settings:impl")
```

### Important: `includeBuild("build-logic")`

This line makes convention plugins available to all modules. Without it, `id("my.kmp.library")` will fail with "plugin not found". This must appear in the `pluginManagement` block, before any `include()` statements.

### FAIL_ON_PROJECT_REPOS

`repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)` prevents individual modules from declaring their own repositories. All repos are centralized in `settings.gradle.kts`. This avoids "works on my machine" issues where a module secretly pulls from a non-standard repo.

## Gradle Module Types

| Gradle Plugin | Use Case | Kotlin DSL |
|--------------|----------|------------|
| `kotlin("multiplatform")` | KMP library (Android + iOS + JVM) | `kotlin { androidTarget(); iosArm64(); ... }` |
| `com.android.library` | Android-only library or KMP Android target | `android { namespace = "..."; compileSdk = 35 }` |
| `com.android.application` | Root Android app | `android { applicationId = "..."; ... }` |
| `kotlin("jvm")` | Pure JVM module (no Android, no iOS) | Standard Kotlin/JVM project |
| `java-library` | Pure Java module | Rarely needed in KMP projects |

### KMP Target Configuration

```kotlin
kotlin {
    // Android
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // iOS
    iosX64()           // Intel simulator
    iosArm64()         // Device
    iosSimulatorArm64() // Apple Silicon simulator

    // Desktop / Server (optional)
    jvm()

    // Web (optional)
    wasmJs { browser() }

    // Hierarchy
    applyDefaultHierarchyTemplate()
}
```

## Android Namespace Convention

Each module needs a unique `namespace` in its `android {}` block. Use a consistent pattern:

```kotlin
// Convention: com.myapp.{module-type}.{feature-name}
android { namespace = "com.myapp.feature.auth.api" }
android { namespace = "com.myapp.feature.auth.impl" }
android { namespace = "com.myapp.core.model" }
android { namespace = "com.myapp.core.network" }
```

A convention plugin can automate this:

```kotlin
// In KmpLibraryConventionPlugin
extensions.configure<LibraryExtension> {
    namespace = "com.myapp.${project.path
        .removePrefix(":")
        .replace(":", ".")}"
}
```

## Incremental Build Benefits

| Module Count | Without api/impl | With api/impl |
|-------------|-------------------|---------------|
| 5 features | Change in auth recompiles all 5 | Change in auth-impl recompiles only auth-impl |
| 20 features | Full rebuild: ~8 min | Incremental: ~30 sec |
| 50 features | Full rebuild: ~25 min | Incremental: ~45 sec |

The key insight: `:api` modules change infrequently. When an `:impl` module changes, only that module recompiles. Other features that depend on the `:api` are unaffected because the interface did not change.
