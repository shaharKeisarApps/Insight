---
name: gradle-expert
description: Elite Gradle expertise for KMP projects. Use when configuring builds, creating convention plugins, managing dependencies with version catalogs, setting up multiplatform targets, optimizing build performance, or troubleshooting build issues. Triggers on build configuration, module setup, dependency management, or Gradle optimization questions.
---

# Gradle Expert Skill

## Project Structure

### Root Settings

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MyApp"

// Feature modules
include(":app:android")
include(":app:ios")
include(":app:desktop")

// Core modules
include(":core:common")
include(":core:ui")
include(":core:network")
include(":core:database")
include(":core:testing")

// Domain layer
include(":domain:models")
include(":domain:usecases")

// Data layer
include(":data:repositories")

// Features
include(":features:feature-home")
include(":features:feature-profile")
include(":features:feature-settings")
```

### Version Catalog

```toml
# gradle/libs.versions.toml

[versions]
kotlin = "2.0.21"
agp = "8.5.2"
compose-multiplatform = "1.7.0"
coroutines = "1.8.1"
ktor = "2.3.12"
sqldelight = "2.0.2"
circuit = "0.24.0"
metro = "0.6.8"
arrow = "1.2.4"
store5 = "5.1.0"
coil = "3.0.0"
ksp = "2.0.21-1.0.28"

# Testing
junit = "5.10.3"
turbine = "1.1.0"
kotest = "5.9.1"
paparazzi = "1.3.4"

[libraries]
# Kotlin
kotlin-stdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib", version.ref = "kotlin" }
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version = "1.7.1" }
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version = "0.6.0" }
kotlinx-collections-immutable = { module = "org.jetbrains.kotlinx:kotlinx-collections-immutable", version = "0.3.7" }

# Ktor
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-okhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }
ktor-client-darwin = { module = "io.ktor:ktor-client-darwin", version.ref = "ktor" }
ktor-client-cio = { module = "io.ktor:ktor-client-cio", version.ref = "ktor" }
ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-client-logging = { module = "io.ktor:ktor-client-logging", version.ref = "ktor" }
ktor-client-auth = { module = "io.ktor:ktor-client-auth", version.ref = "ktor" }
ktor-serialization-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
ktor-client-mock = { module = "io.ktor:ktor-client-mock", version.ref = "ktor" }

# SQLDelight
sqldelight-runtime = { module = "app.cash.sqldelight:runtime", version.ref = "sqldelight" }
sqldelight-coroutines = { module = "app.cash.sqldelight:coroutines-extensions", version.ref = "sqldelight" }
sqldelight-android-driver = { module = "app.cash.sqldelight:android-driver", version.ref = "sqldelight" }
sqldelight-native-driver = { module = "app.cash.sqldelight:native-driver", version.ref = "sqldelight" }
sqldelight-sqlite-driver = { module = "app.cash.sqldelight:sqlite-driver", version.ref = "sqldelight" }

# Circuit
circuit-foundation = { module = "com.slack.circuit:circuit-foundation", version.ref = "circuit" }
circuit-runtime = { module = "com.slack.circuit:circuit-runtime", version.ref = "circuit" }
circuit-codegen = { module = "com.slack.circuit:circuit-codegen", version.ref = "circuit" }
circuit-codegen-annotations = { module = "com.slack.circuit:circuit-codegen-annotations", version.ref = "circuit" }
circuit-overlay = { module = "com.slack.circuit:circuit-overlay", version.ref = "circuit" }
circuit-retained = { module = "com.slack.circuit:circuit-retained", version.ref = "circuit" }
circuit-test = { module = "com.slack.circuit:circuit-test", version.ref = "circuit" }

# Arrow
arrow-core = { module = "io.arrow-kt:arrow-core", version.ref = "arrow" }
arrow-fx-coroutines = { module = "io.arrow-kt:arrow-fx-coroutines", version.ref = "arrow" }

# Store5
store5 = { module = "org.mobilenativefoundation.store:store5", version.ref = "store5" }

# Coil (Multiplatform)
coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil" }
coil-network-ktor = { module = "io.coil-kt.coil3:coil-network-ktor", version.ref = "coil" }

# Testing
kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
kotest-assertions = { module = "io.kotest:kotest-assertions-core", version.ref = "kotest" }
paparazzi = { module = "app.cash.paparazzi:paparazzi", version.ref = "paparazzi" }

[bundles]
ktor-common = [
    "ktor-client-core",
    "ktor-client-content-negotiation",
    "ktor-client-logging",
    "ktor-client-auth",
    "ktor-serialization-json",
]

circuit = [
    "circuit-foundation",
    "circuit-runtime",
    "circuit-overlay",
    "circuit-retained",
]

testing-common = [
    "kotlin-test",
    "kotlinx-coroutines-test",
    "turbine",
    "kotest-assertions",
]

[plugins]
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
compose-multiplatform = { id = "org.jetbrains.compose", version.ref = "compose-multiplatform" }
sqldelight = { id = "app.cash.sqldelight", version.ref = "sqldelight" }
metro = { id = "dev.zacsweers.metro", version.ref = "metro" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
paparazzi = { id = "app.cash.paparazzi", version.ref = "paparazzi" }
```

## Convention Plugins

### Build Logic Setup

```kotlin
// build-logic/build.gradle.kts
plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.gradlePlugin.kotlin)
    compileOnly(libs.gradlePlugin.android)
    compileOnly(libs.gradlePlugin.compose)
    compileOnly(libs.gradlePlugin.ksp)
}

gradlePlugin {
    plugins {
        register("kotlinMultiplatform") {
            id = "app.kmp.library"
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("composeMultiplatform") {
            id = "app.compose.library"
            implementationClass = "ComposeLibraryConventionPlugin"
        }
        register("featureModule") {
            id = "app.feature"
            implementationClass = "FeatureModuleConventionPlugin"
        }
    }
}
```

### KMP Library Plugin

```kotlin
// build-logic/src/main/kotlin/KmpLibraryConventionPlugin.kt
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.*

class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("org.jetbrains.kotlin.multiplatform")
            apply("com.android.library")
        }
        
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
            androidTarget {
                compilations.all {
                    kotlinOptions {
                        jvmTarget = "17"
                    }
                }
            }
            
            listOf(
                iosX64(),
                iosArm64(),
                iosSimulatorArm64(),
            ).forEach { target ->
                target.binaries.framework {
                    baseName = project.name
                    isStatic = true
                }
            }
            
            jvm("desktop")
            
            sourceSets {
                commonMain.dependencies {
                    implementation(libs.findLibrary("kotlinx-coroutines-core").get())
                }
                
                commonTest.dependencies {
                    implementation(libs.findBundle("testing-common").get())
                }
                
                androidMain.dependencies {
                    implementation(libs.findLibrary("kotlinx-coroutines-android").get())
                }
            }
        }
        
        extensions.configure<com.android.build.gradle.LibraryExtension> {
            compileSdk = 34
            
            defaultConfig {
                minSdk = 26
            }
            
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
        }
    }
}
```

### Compose Library Plugin

```kotlin
// build-logic/src/main/kotlin/ComposeLibraryConventionPlugin.kt
class ComposeLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("app.kmp.library")
            apply("org.jetbrains.compose")
            apply("org.jetbrains.kotlin.plugin.compose")
        }
        
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
            sourceSets {
                commonMain.dependencies {
                    implementation(compose.runtime)
                    implementation(compose.foundation)
                    implementation(compose.material3)
                    implementation(compose.ui)
                    implementation(compose.components.resources)
                }
            }
        }
    }
}
```

### Feature Module Plugin

```kotlin
// build-logic/src/main/kotlin/FeatureModuleConventionPlugin.kt
class FeatureModuleConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("app.compose.library")
            apply("dev.zacsweers.metro")
            apply("com.google.devtools.ksp")
        }
        
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
            sourceSets {
                commonMain.dependencies {
                    // Circuit
                    implementation(libs.findBundle("circuit").get())
                    
                    // Arrow
                    implementation(libs.findLibrary("arrow-core").get())
                    
                    // Project modules
                    implementation(project(":core:common"))
                    implementation(project(":core:ui"))
                    implementation(project(":domain:models"))
                }
                
                commonTest.dependencies {
                    implementation(libs.findLibrary("circuit-test").get())
                    implementation(project(":core:testing"))
                }
            }
        }
        
        // KSP for Circuit
        dependencies {
            add("kspCommonMainMetadata", libs.findLibrary("circuit-codegen").get())
            add("kspAndroid", libs.findLibrary("circuit-codegen").get())
            add("kspIosX64", libs.findLibrary("circuit-codegen").get())
            add("kspIosArm64", libs.findLibrary("circuit-codegen").get())
            add("kspIosSimulatorArm64", libs.findLibrary("circuit-codegen").get())
            add("kspDesktop", libs.findLibrary("circuit-codegen").get())
        }
    }
}
```

## Module Build Files

### Feature Module Example

```kotlin
// features/feature-profile/build.gradle.kts
plugins {
    id("app.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":domain:usecases"))
            implementation(project(":data:repositories"))
        }
    }
}

android {
    namespace = "com.app.feature.profile"
}
```

### Core UI Module

```kotlin
// core/ui/build.gradle.kts
plugins {
    id("app.compose.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.coil.compose)
            api(libs.coil.network.ktor)
            api(libs.kotlinx.collections.immutable)
        }
        
        androidMain.dependencies {
            api(libs.androidx.activity.compose)
        }
    }
}

android {
    namespace = "com.app.core.ui"
}
```

### Network Module

```kotlin
// core/network/build.gradle.kts
plugins {
    id("app.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.bundles.ktor.common)
            api(libs.kotlinx.serialization.json)
            api(libs.arrow.core)
        }
        
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        
        named("desktopMain").dependencies {
            implementation(libs.ktor.client.cio)
        }
        
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
        }
    }
}

android {
    namespace = "com.app.core.network"
}
```

### Database Module

```kotlin
// core/database/build.gradle.kts
plugins {
    id("app.kmp.library")
    alias(libs.plugins.sqldelight)
}

sqldelight {
    databases {
        create("AppDatabase") {
            packageName.set("com.app.db")
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            verifyMigrations.set(true)
        }
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.sqldelight.runtime)
            api(libs.sqldelight.coroutines)
        }
        
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
        }
        
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
        
        named("desktopMain").dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
    }
}

android {
    namespace = "com.app.core.database"
}
```

## Build Optimization

### Gradle Properties

```properties
# gradle.properties

# Performance
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true
org.gradle.daemon=true
org.gradle.jvmargs=-Xmx4g -XX:+HeapDumpOnOutOfMemoryError -Dfile.encoding=UTF-8

# Kotlin
kotlin.code.style=official
kotlin.incremental.multiplatform=true
kotlin.mpp.stability.nowarn=true
kotlin.native.cacheKind=static

# Android
android.useAndroidX=true
android.nonTransitiveRClass=true

# Compose
org.jetbrains.compose.experimental.jscanvas.enabled=true
org.jetbrains.compose.experimental.macos.enabled=true
```

### Build Cache Configuration

```kotlin
// settings.gradle.kts
buildCache {
    local {
        isEnabled = true
        directory = file("${rootDir}/.gradle/build-cache")
        removeUnusedEntriesAfterDays = 30
    }
    
    // Optional: Remote cache for CI
    remote<HttpBuildCache> {
        url = uri("https://cache.mycompany.com/")
        isPush = System.getenv("CI") != null
        credentials {
            username = System.getenv("CACHE_USER") ?: ""
            password = System.getenv("CACHE_PASSWORD") ?: ""
        }
    }
}
```

### Dependency Locking

```kotlin
// build.gradle.kts (root)
allprojects {
    dependencyLocking {
        lockAllConfigurations()
    }
}

// Generate lock files: ./gradlew dependencies --write-locks
```

## KMP Target Configuration

### iOS Framework

```kotlin
// Shared iOS framework configuration
kotlin {
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
            
            // Export dependencies to iOS
            export(project(":core:common"))
            export(libs.kotlinx.coroutines.core)
        }
    }
}

// XCFramework for distribution
tasks.register<org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink>("buildXCFramework") {
    // Configure for release distribution
}
```

### Desktop Configuration

```kotlin
// app/desktop/build.gradle.kts
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvm("desktop")
    
    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(project(":shared"))
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.app.MainKt"
        
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
            )
            
            packageName = "MyApp"
            packageVersion = "1.0.0"
            
            macOS {
                bundleID = "com.app.desktop"
                iconFile.set(project.file("icons/icon.icns"))
            }
            
            windows {
                iconFile.set(project.file("icons/icon.ico"))
            }
            
            linux {
                iconFile.set(project.file("icons/icon.png"))
            }
        }
    }
}
```

## Useful Tasks

### Custom Tasks

```kotlin
// build.gradle.kts (root)

// Run all checks
tasks.register("checkAll") {
    group = "verification"
    description = "Runs all verification tasks"
    
    dependsOn(
        "spotlessCheck",
        "detekt",
        "check",
    )
}

// Clean and rebuild
tasks.register("rebuild") {
    group = "build"
    description = "Cleans and rebuilds everything"
    
    dependsOn("clean", "assemble")
    tasks.findByName("assemble")?.mustRunAfter("clean")
}

// Generate all KSP
tasks.register("generateKsp") {
    group = "build"
    description = "Generates all KSP code"
    
    dependsOn(subprojects.mapNotNull { 
        it.tasks.findByName("kspCommonMainKotlinMetadata") 
    })
}
```

### Task Dependencies

```kotlin
// Ensure spotless runs before compile
tasks.matching { it.name.contains("compile") && it.name.contains("Kotlin") }.configureEach {
    mustRunAfter(tasks.matching { it.name == "spotlessApply" })
}
```

## Troubleshooting

### Common Issues

```kotlin
// Fix: Duplicate class error
android {
    packagingOptions {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/versions/9/previous-compilation-data.bin"
        }
    }
}

// Fix: Missing expect/actual
kotlin {
    sourceSets {
        // Ensure all targets have actual implementations
        iosMain.dependencies { }
        androidMain.dependencies { }
    }
}

// Fix: KSP not generating
ksp {
    arg("circuit.codegen.mode", "kotlin_inject_anvil")
}
```

### Debug Commands

```bash
# Show dependency tree
./gradlew :module:dependencies --configuration commonMainImplementation

# Show build scan
./gradlew build --scan

# Debug configuration cache
./gradlew help --configuration-cache-problems=warn

# Clean everything
./gradlew clean cleanBuildCache

# Check for dependency updates
./gradlew dependencyUpdates
```

## References

- Gradle Kotlin DSL: https://docs.gradle.org/current/userguide/kotlin_dsl.html
- KMP Documentation: https://kotlinlang.org/docs/multiplatform.html
- Version Catalogs: https://docs.gradle.org/current/userguide/platforms.html
- Convention Plugins: https://docs.gradle.org/current/samples/sample_convention_plugins.html
