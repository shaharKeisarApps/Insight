# Modularization Plan - Expense Tracker

This document outlines the plan to modularize the Expense Tracker MVP into a scalable, maintainable multi-module architecture following NowInAndroid and CatchUp patterns.

---

## Current State

Single-module Android app with all code in `:app`:

```
app/
├── di/           → Metro DI configuration
├── data/
│   ├── db/       → SQLDelight database
│   ├── model/    → Domain models
│   └── repository/ → Repositories
├── screens/      → Circuit screens/presenters/UI
└── ui/theme/     → Material3 theme
```

---

## Target Architecture

Following NowInAndroid's modularization pattern with Circuit-specific adaptations:

```
Insight/
├── app/                          # Android application entry point
├── build-logic/                  # Convention plugins (already created)
│   └── convention/
├── core/
│   ├── common/                   # Shared utilities, extensions
│   ├── model/                    # Domain models (Expense, Category)
│   ├── database/                 # SQLDelight database layer
│   ├── data/                     # Repositories implementation
│   ├── ui/                       # Shared UI components, theme
│   └── testing/                  # Test utilities, fakes
└── feature/
    ├── expenses/                 # Expenses list screen
    ├── reports/                  # Reports/analytics screen
    ├── settings/                 # Settings screen
    └── add-edit-expense/         # Add/Edit expense screen
```

---

## Module Dependency Graph

```
                    ┌─────────────┐
                    │     app     │
                    └──────┬──────┘
                           │
         ┌─────────────────┼─────────────────┐
         │                 │                 │
         ▼                 ▼                 ▼
   ┌──────────┐     ┌──────────┐     ┌──────────┐
   │ feature: │     │ feature: │     │ feature: │
   │ expenses │     │ reports  │     │ settings │
   └────┬─────┘     └────┬─────┘     └────┬─────┘
        │                │                │
        └────────────────┼────────────────┘
                         │
         ┌───────────────┼───────────────┐
         │               │               │
         ▼               ▼               ▼
   ┌──────────┐    ┌──────────┐    ┌──────────┐
   │ core:data│◄───│ core:ui  │    │core:model│
   └────┬─────┘    └──────────┘    └─────┬────┘
        │                                │
        ▼                                │
   ┌──────────┐                          │
   │  core:   │◄─────────────────────────┘
   │ database │
   └────┬─────┘
        │
        ▼
   ┌──────────┐
   │  core:   │
   │  common  │
   └──────────┘
```

**Key Rules (from NowInAndroid):**
- Feature modules never depend on other feature modules
- Feature modules only depend on core modules
- Core modules may depend on other core modules
- `:app` depends on all feature modules for navigation

---

## Module Specifications

### 1. `:core:common`

Shared utilities and extensions.

```kotlin
// build.gradle.kts
plugins {
    id("metroditest.android.library")
}

// Contents:
// - Extension functions
// - Result/Either wrappers
// - Date utilities
// - Constants
```

**Files to extract:**
- Utility functions
- Common extensions

---

### 2. `:core:model`

Domain models with no dependencies.

```kotlin
// build.gradle.kts
plugins {
    id("metroditest.android.library")
    alias(libs.plugins.kotlin.parcelize)
}

dependencies {
    implementation(libs.kotlinx.datetime)
    implementation(libs.androidx.compose.ui)  // For Color in Category
}
```

**Files to extract:**
- `data/model/Expense.kt`
- `data/model/Category.kt`

---

### 3. `:core:database`

SQLDelight database layer.

```kotlin
// build.gradle.kts
plugins {
    id("metroditest.android.library")
    alias(libs.plugins.sqldelight)
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.sqldelight.android.driver)
    implementation(libs.sqldelight.coroutines)
    implementation(libs.kotlinx.datetime)
}

sqldelight {
    databases {
        create("ExpenseDatabase") {
            packageName.set("com.keisardev.metroditest.core.database")
        }
    }
}
```

**Files to extract:**
- `data/db/*.sq` files
- `data/db/DatabaseProvider.kt`
- `data/db/adapters/` (LocalDate, Instant, Color adapters)

---

### 4. `:core:data`

Repository implementations.

```kotlin
// build.gradle.kts
plugins {
    id("metroditest.android.library")
    alias(libs.plugins.metro)
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:common"))
    implementation(libs.kotlinx.coroutines.android)
}
```

**Files to extract:**
- `data/repository/ExpenseRepository.kt` (interface)
- `data/repository/ExpenseRepositoryImpl.kt`
- `data/repository/CategoryRepository.kt` (interface)
- `data/repository/CategoryRepositoryImpl.kt`

**DI Contributions:**
```kotlin
// core/data/di/DataModule.kt
@ContributesTo(AppScope::class)
interface DataModule {
    @Binds
    fun bindExpenseRepository(impl: ExpenseRepositoryImpl): ExpenseRepository

    @Binds
    fun bindCategoryRepository(impl: CategoryRepositoryImpl): CategoryRepository
}
```

---

### 5. `:core:ui`

Shared UI components and theme.

```kotlin
// build.gradle.kts
plugins {
    id("metroditest.android.library")
    id("metroditest.android.compose")
}

dependencies {
    implementation(project(":core:model"))
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons.extended)
    api(libs.circuit.foundation)
}
```

**Files to extract:**
- `ui/theme/Color.kt`
- `ui/theme/Type.kt`
- `ui/theme/Theme.kt`
- Shared composables (loading states, error states)
- Category icon mapping utilities

---

### 6. `:core:testing`

Test utilities and fakes.

```kotlin
// build.gradle.kts
plugins {
    id("metroditest.android.library")
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(libs.kotlinx.coroutines.test)
    implementation(libs.circuit.test)
    implementation(libs.turbine)
    implementation(libs.truth)
    implementation(libs.robolectric)
}
```

**Files to extract:**
- `fakes/FakeExpenseRepository.kt`
- `fakes/FakeCategoryRepository.kt`
- Test utilities
- Test fixtures (sample data)

---

### 7. `:feature:expenses`

Expenses list feature.

```kotlin
// build.gradle.kts
plugins {
    id("metroditest.android.library")
    id("metroditest.android.compose")
    alias(libs.plugins.metro)
    alias(libs.plugins.ksp)
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))

    implementation(libs.circuit.foundation)
    implementation(libs.circuit.retained)
    implementation(libs.circuit.codegen.annotations)
    ksp(libs.circuit.codegen)

    testImplementation(project(":core:testing"))
}

ksp {
    arg("circuit.codegen.mode", "metro")
}
```

**Files to extract:**
- `screens/ExpensesScreen.kt` → `feature/expenses/ExpensesScreen.kt`

---

### 8. `:feature:reports`

Reports/analytics feature.

```kotlin
// Same structure as :feature:expenses
```

**Files to extract:**
- `screens/ReportsScreen.kt` → `feature/reports/ReportsScreen.kt`

---

### 9. `:feature:settings`

Settings feature.

```kotlin
// Same structure as :feature:expenses
```

**Files to extract:**
- `screens/SettingsScreen.kt` → `feature/settings/SettingsScreen.kt`

---

### 10. `:feature:add-edit-expense`

Add/Edit expense feature.

```kotlin
// Same structure as :feature:expenses
```

**Files to extract:**
- `screens/AddEditExpenseScreen.kt` → `feature/add-edit-expense/AddEditExpenseScreen.kt`

---

### 11. `:app` (Updated)

Application entry point with navigation.

```kotlin
// build.gradle.kts
plugins {
    id("metroditest.android.application")
    id("metroditest.android.compose")
    alias(libs.plugins.metro)
    alias(libs.plugins.ksp)
}

dependencies {
    // Core modules
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:database"))
    implementation(project(":core:ui"))

    // Feature modules
    implementation(project(":feature:expenses"))
    implementation(project(":feature:reports"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:add-edit-expense"))

    // Circuit
    implementation(libs.circuit.foundation)
    ksp(libs.circuit.codegen)
}
```

**Remaining files:**
- `MainActivity.kt`
- `InsightApp.kt`
- `di/AppGraph.kt` (aggregates all module contributions)
- `navigation/MainContent.kt`
- `navigation/AppDestinations.kt`

---

## Convention Plugins to Add

Update `build-logic/convention/` with these additional plugins:

### `metroditest.android.feature`

```kotlin
// build-logic/convention/src/main/kotlin/AndroidFeatureConventionPlugin.kt
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("metroditest.android.library")
                apply("metroditest.android.compose")
                apply("dev.zacsweers.metro")
                apply("com.google.devtools.ksp")
            }

            extensions.configure<com.android.build.gradle.LibraryExtension> {
                defaultConfig {
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
            }

            extensions.configure<com.google.devtools.ksp.gradle.KspExtension> {
                arg("circuit.codegen.mode", "metro")
            }

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

            dependencies {
                // Circuit
                add("implementation", libs.findLibrary("circuit.foundation").get())
                add("implementation", libs.findLibrary("circuit.retained").get())
                add("implementation", libs.findLibrary("circuit.codegen.annotations").get())
                add("ksp", libs.findLibrary("circuit.codegen").get())
            }
        }
    }
}
```

---

## Implementation Order

### Phase 1: Core Infrastructure (Week 1)

1. **Update build-logic** with new convention plugins
   - `metroditest.android.feature`
   - Update existing plugins for Java 23

2. **Create `:core:common`**
   - Extract utilities
   - Add to settings.gradle.kts

3. **Create `:core:model`**
   - Extract `Expense`, `Category`
   - Update imports in `:app`

### Phase 2: Data Layer (Week 2)

4. **Create `:core:database`**
   - Move SQLDelight files
   - Update package names
   - Configure SQLDelight plugin

5. **Create `:core:data`**
   - Move repositories
   - Set up Metro DI contributions

### Phase 3: UI Foundation (Week 2)

6. **Create `:core:ui`**
   - Move theme files
   - Move shared UI components
   - Export Circuit dependencies

7. **Create `:core:testing`**
   - Move fakes
   - Add test utilities

### Phase 4: Feature Extraction (Week 3)

8. **Create `:feature:expenses`**
   - Move ExpensesScreen
   - Update presenter DI
   - Move tests

9. **Create `:feature:reports`**
   - Move ReportsScreen
   - Update presenter DI

10. **Create `:feature:settings`**
    - Move SettingsScreen
    - Update presenter DI
    - Move tests

11. **Create `:feature:add-edit-expense`**
    - Move AddEditExpenseScreen
    - Update presenter DI

### Phase 5: App Integration (Week 3)

12. **Update `:app`**
    - Update dependencies
    - Update DI graph to aggregate modules
    - Verify navigation works

13. **Final Verification**
    - Run all tests
    - Build release APK
    - Test all features manually

---

## Updated settings.gradle.kts

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

rootProject.name = "Insight"

// Application
include(":app")

// Core modules
include(":core:common")
include(":core:model")
include(":core:database")
include(":core:data")
include(":core:ui")
include(":core:testing")

// Feature modules
include(":feature:expenses")
include(":feature:reports")
include(":feature:settings")
include(":feature:add-edit-expense")
```

---

## Benefits of Modularization

| Benefit | Description |
|---------|-------------|
| **Build Speed** | Parallel compilation, incremental builds |
| **Encapsulation** | Clear boundaries, reduced coupling |
| **Testability** | Isolated unit testing per module |
| **Reusability** | Core modules reusable across apps |
| **Team Scaling** | Teams can own specific features |
| **Dynamic Delivery** | Optional Play Feature Delivery |

---

## Metrics to Track

Before/after modularization:
- Clean build time
- Incremental build time
- Test execution time
- APK size

---

## References

- [NowInAndroid Modularization](https://github.com/android/nowinandroid/blob/main/docs/ModularizationLearningJourney.md)
- [CatchUp Architecture](https://github.com/ZacSweers/CatchUp)
- [Gradle Convention Plugins](https://docs.gradle.org/current/samples/sample_convention_plugins.html)
- [Circuit Documentation](https://slackhq.github.io/circuit/)
- [Metro DI](https://github.com/ZacSweers/metro)
