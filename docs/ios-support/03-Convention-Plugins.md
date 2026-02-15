# Convention Plugins for KMP

This document describes the Gradle convention plugins needed for KMP modules.

## Overview

Two new convention plugins are needed:

| Plugin | Purpose | Used By |
|--------|---------|---------|
| `insight.kmp.library` | Basic KMP library setup | Core modules |
| `insight.kmp.feature` | Feature module with Circuit, Compose, Metro | Feature modules |

## Plugin: insight.kmp.library

Basic KMP library configuration for core modules.

### KmpLibraryConventionPlugin.kt

```kotlin
import com.android.build.gradle.LibraryExtension
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

            extensions.configure<KotlinMultiplatformExtension> {
                // Android target
                androidTarget {
                    compilations.all {
                        compileTaskProvider.configure {
                            compilerOptions {
                                jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
                            }
                        }
                    }
                }

                // iOS targets (no binaries - shared module creates the framework)
                iosX64()
                iosArm64()
                iosSimulatorArm64()

                // Enable hierarchical source sets for iosMain
                applyDefaultHierarchyTemplate()
            }

            extensions.configure<LibraryExtension> {
                compileSdk = 36
                defaultConfig {
                    minSdk = 33
                }
                compileOptions {
                    sourceCompatibility = org.gradle.api.JavaVersion.VERSION_21
                    targetCompatibility = org.gradle.api.JavaVersion.VERSION_21
                }
            }
        }
    }
}
```

### Key Features

1. **iOS Targets Without Binaries:**
   ```kotlin
   iosX64()
   iosArm64()
   iosSimulatorArm64()
   ```
   These define the targets but don't create framework binaries. The `shared` umbrella module handles that.

2. **Hierarchical Source Sets:**
   ```kotlin
   applyDefaultHierarchyTemplate()
   ```
   This creates the `iosMain` source set that's shared across all iOS targets.

3. **JVM 21 Target:**
   Matches Android app configuration.

## Plugin: insight.kmp.feature

Feature module configuration with Circuit, Compose Multiplatform, and Metro DI.

### KmpFeatureConventionPlugin.kt

```kotlin
import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class KmpFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.multiplatform")
                apply("com.android.library")
                apply("org.jetbrains.compose")              // Compose Multiplatform
                apply("org.jetbrains.kotlin.plugin.compose") // Compose compiler
                apply("org.jetbrains.kotlin.plugin.parcelize")
                apply("dev.zacsweers.metro")                 // Metro DI
                apply("com.google.devtools.ksp")             // For Circuit codegen
            }

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

            extensions.configure<KotlinMultiplatformExtension> {
                androidTarget {
                    compilations.all {
                        compileTaskProvider.configure {
                            compilerOptions {
                                jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
                            }
                        }
                    }
                }

                iosX64()
                iosArm64()
                iosSimulatorArm64()

                applyDefaultHierarchyTemplate()

                sourceSets.apply {
                    commonMain.dependencies {
                        // Core modules
                        implementation(project(":core:model"))
                        implementation(project(":core:data"))
                        implementation(project(":core:designsystem"))
                        implementation(project(":core:ui"))
                        implementation(project(":core:common"))

                        // Circuit (KMP-compatible)
                        implementation(libs.findLibrary("circuit.foundation").get())
                        implementation(libs.findLibrary("circuit.retained").get())
                        implementation(libs.findLibrary("circuit.codegen.annotations").get())
                        implementation(libs.findLibrary("circuitx.gesture.navigation").get())

                        // Coroutines (common)
                        implementation(libs.findLibrary("kotlinx.coroutines.core").get())
                        implementation(libs.findLibrary("kotlinx.datetime").get())
                    }

                    androidMain.dependencies {
                        implementation(libs.findLibrary("kotlinx.coroutines.android").get())
                    }
                }
            }

            extensions.configure<LibraryExtension> {
                compileSdk = 36
                defaultConfig {
                    minSdk = 33
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
                compileOptions {
                    sourceCompatibility = org.gradle.api.JavaVersion.VERSION_21
                    targetCompatibility = org.gradle.api.JavaVersion.VERSION_21
                }
            }

            // Configure Circuit codegen mode for Metro
            afterEvaluate {
                extensions.findByName("ksp")?.let { ksp ->
                    (ksp as? com.google.devtools.ksp.gradle.KspExtension)?.apply {
                        arg("circuit.codegen.mode", "metro")
                    }
                }
            }

            // KSP for all targets
            dependencies.add("kspCommonMainMetadata", libs.findLibrary("circuit.codegen").get())
            dependencies.add("kspAndroid", libs.findLibrary("circuit.codegen").get())
            dependencies.add("kspIosX64", libs.findLibrary("circuit.codegen").get())
            dependencies.add("kspIosArm64", libs.findLibrary("circuit.codegen").get())
            dependencies.add("kspIosSimulatorArm64", libs.findLibrary("circuit.codegen").get())
        }
    }
}
```

### Key Features

1. **Compose Multiplatform:**
   ```kotlin
   apply("org.jetbrains.compose")
   apply("org.jetbrains.kotlin.plugin.compose")
   ```

2. **KSP for All Targets:**
   Circuit codegen must run for each target:
   ```kotlin
   dependencies.add("kspCommonMainMetadata", libs.findLibrary("circuit.codegen").get())
   dependencies.add("kspAndroid", libs.findLibrary("circuit.codegen").get())
   dependencies.add("kspIosX64", libs.findLibrary("circuit.codegen").get())
   dependencies.add("kspIosArm64", libs.findLibrary("circuit.codegen").get())
   dependencies.add("kspIosSimulatorArm64", libs.findLibrary("circuit.codegen").get())
   ```

3. **Metro Integration:**
   ```kotlin
   arg("circuit.codegen.mode", "metro")
   ```

## Build Logic Dependencies

Update `build-logic/convention/build.gradle.kts`:

```kotlin
dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.composeMultiplatform.gradlePlugin)  // NEW
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.sqldelight.gradlePlugin)            // NEW
}
```

Register plugins:

```kotlin
gradlePlugin {
    plugins {
        // Existing plugins...

        register("kmpLibrary") {
            id = "insight.kmp.library"
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("kmpFeature") {
            id = "insight.kmp.feature"
            implementationClass = "KmpFeatureConventionPlugin"
        }
    }
}
```

## Usage

### Core Module

```kotlin
// core/model/build.gradle.kts
plugins {
    id("insight.kmp.library")
}

android {
    namespace = "com.keisardev.insight.core.model"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.datetime)
        }
    }
}
```

### Feature Module

```kotlin
// feature/expenses/build.gradle.kts
plugins {
    id("insight.kmp.feature")
}

android {
    namespace = "com.keisardev.insight.feature.expenses"
}

// Dependencies handled by convention plugin
// Additional dependencies can be added:
kotlin {
    sourceSets {
        commonMain.dependencies {
            // Feature-specific dependencies
        }
    }
}
```

## Source Set Hierarchy

With `applyDefaultHierarchyTemplate()`:

```
commonMain
├── androidMain
└── iosMain (shared iOS code)
    ├── iosX64Main
    ├── iosArm64Main
    └── iosSimulatorArm64Main
```

## Comparison: Android vs KMP Plugins

| Aspect | insight.android.library | insight.kmp.library |
|--------|------------------------|---------------------|
| Kotlin plugin | kotlin.android | kotlin.multiplatform |
| Targets | Android only | Android + iOS |
| Source sets | main | commonMain, androidMain, iosMain |
| Dependencies | dependencies {} | kotlin.sourceSets.*.dependencies |

| Aspect | insight.android.feature | insight.kmp.feature |
|--------|------------------------|---------------------|
| Compose | Android Compose | Compose Multiplatform |
| KSP | ksp(...) | kspAndroid, kspIos*, kspCommonMainMetadata |
| Circuit | circuit.codegen | circuit.codegen for each target |
