# KMP Migration Guide

This document covers converting Android-only modules to Kotlin Multiplatform (KMP).

## Step 1: Update Version Catalog

Add KMP-related dependencies to `gradle/libs.versions.toml`:

```toml
[versions]
composeMultiplatform = "1.9.3"
# Update kotlinxDatetime if needed (KMP requires newer versions)
kotlinxDatetime = "0.7.1"

[libraries]
# KMP Coroutines (common module)
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }

# SQLDelight iOS driver
sqldelight-runtime = { module = "app.cash.sqldelight:runtime", version.ref = "sqldelight" }
sqldelight-native-driver = { module = "app.cash.sqldelight:native-driver", version.ref = "sqldelight" }

# Compose Multiplatform Gradle plugin (for build-logic)
composeMultiplatform-gradlePlugin = { module = "org.jetbrains.compose:compose-gradle-plugin", version.ref = "composeMultiplatform" }

# Circuit gesture navigation (KMP)
circuitx-gesture-navigation = { module = "com.slack.circuit:circuitx-gesture-navigation", version.ref = "circuit" }

[plugins]
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
compose-multiplatform = { id = "org.jetbrains.compose", version.ref = "composeMultiplatform" }
```

## Step 2: Update Build Logic

Add KMP Gradle plugin to `build-logic/convention/build.gradle.kts`:

```kotlin
dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.composeMultiplatform.gradlePlugin)  // ADD
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.sqldelight.gradlePlugin)            // ADD
}
```

Register new convention plugins:

```kotlin
gradlePlugin {
    plugins {
        // ... existing plugins ...

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

## Step 3: Move Source Files

For each module being converted:

### Directory Structure Change

```
module/src/
├── main/kotlin/...           # BEFORE: Android-only
│
├── commonMain/kotlin/...     # AFTER: Shared code (most files go here)
├── androidMain/kotlin/...    # AFTER: Android-specific (platform APIs)
└── iosMain/kotlin/...        # AFTER: iOS-specific (platform APIs)
```

### Migration Steps

1. **Create new directories:**
   ```bash
   mkdir -p module/src/commonMain/kotlin
   mkdir -p module/src/androidMain/kotlin
   mkdir -p module/src/iosMain/kotlin
   ```

2. **Move shared code to `commonMain`:**
   - Domain models
   - Repository interfaces
   - Business logic
   - Compose UI (using Compose Multiplatform)

3. **Keep platform-specific code in platform source sets:**
   - Android Context usage → `androidMain`
   - iOS UIKit/Foundation → `iosMain`
   - Platform-specific implementations

## Step 4: Update build.gradle.kts

Change from Android plugin to KMP plugin:

### Before (Android Library)
```kotlin
plugins {
    id("insight.android.library")
}

android {
    namespace = "com.example.module"
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
}
```

### After (KMP Library)
```kotlin
plugins {
    id("insight.kmp.library")
}

android {
    namespace = "com.example.module"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
        }
    }
}
```

## Step 5: Handle Platform-Specific Code

Use `expect`/`actual` for platform-specific implementations:

### Example: Dispatchers

**commonMain/AppDispatchers.kt:**
```kotlin
expect object AppDispatchers {
    val IO: CoroutineDispatcher
    val Default: CoroutineDispatcher
    val Main: CoroutineDispatcher
}
```

**androidMain/AppDispatchers.android.kt:**
```kotlin
actual object AppDispatchers {
    actual val IO: CoroutineDispatcher = Dispatchers.IO
    actual val Default: CoroutineDispatcher = Dispatchers.Default
    actual val Main: CoroutineDispatcher = Dispatchers.Main
}
```

**iosMain/AppDispatchers.ios.kt:**
```kotlin
actual object AppDispatchers {
    actual val IO: CoroutineDispatcher = Dispatchers.Default
    actual val Default: CoroutineDispatcher = Dispatchers.Default
    actual val Main: CoroutineDispatcher = Dispatchers.Main
}
```

## Step 6: Update Module Dependencies

Ensure all core modules are converted before feature modules:

**Conversion Order:**
1. `core:common` (foundation, expect/actual patterns)
2. `core:model` (domain models)
3. `core:database` (SQLDelight with platform drivers)
4. `core:data` (repositories)
5. `core:designsystem` (Compose theme)
6. `core:ui` (shared UI components)
7. `feature:*` (Circuit screens)

## Common Import Changes

| Android Import | KMP Import |
|----------------|------------|
| `android.os.Parcelable` | Custom expect/actual (see Parcelize doc) |
| `kotlinx.parcelize.Parcelize` | Custom expect/actual |
| `kotlinx.coroutines.android` | `kotlinx.coroutines.core` in commonMain |
| `androidx.compose.*` | Same (via Compose Multiplatform) |

## SQLDelight Migration

### build.gradle.kts
```kotlin
plugins {
    id("insight.kmp.library")
    alias(libs.plugins.sqldelight)
}

sqldelight {
    databases {
        create("InsightDatabase") {
            packageName.set("com.keisardev.insight.core.database")
        }
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
    }
}
```

### Platform Driver Implementation

**commonMain/DatabaseDriverFactory.kt:**
```kotlin
expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}
```

**androidMain/DatabaseDriverFactory.android.kt:**
```kotlin
actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(
            InsightDatabase.Schema,
            context,
            "insight.db"
        )
    }
}
```

**iosMain/DatabaseDriverFactory.ios.kt:**
```kotlin
actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        return NativeSqliteDriver(
            InsightDatabase.Schema,
            "insight.db"
        )
    }
}
```

## Verification

After migration, verify the build compiles for all targets:

```bash
# Build all targets
./gradlew build

# Build iOS framework specifically
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```
