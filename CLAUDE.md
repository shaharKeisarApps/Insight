# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Run all unit tests
./gradlew test

# Run tests for a specific module
./gradlew :feature:expenses:test

# Run a single test class
./gradlew test --tests "com.keisardev.insight.ExampleUnitTest"

# Run instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest

# Clean build
./gradlew clean
```

## Project Configuration

- **Min SDK**: 33 (Android 13+)
- **Target/Compile SDK**: 36
- **Kotlin**: 2.3.0 (K2 compiler)
- **Java Target**: JVM 21
- **Compose BOM**: 2025.12.01

Dependencies managed via version catalog at `gradle/libs.versions.toml`.

## Architecture Overview

**Multi-module structure following NowInAndroid pattern:**

```
Insight/
├── app/                 # Entry point, AppGraph, navigation
├── build-logic/         # Gradle convention plugins
├── core/
│   ├── common/          # AppScope, shared utilities
│   ├── model/           # Domain models (Expense, Income, Category)
│   ├── database/        # SQLDelight schema and drivers
│   ├── data/            # Repository interfaces and implementations
│   ├── designsystem/    # Material3 theme
│   ├── ui/              # Shared UI components
│   └── ai/              # Koog AI integration (AiService, tools)
└── feature/
    ├── expenses/        # Expenses screen (Circuit)
    ├── income/          # Income tracking
    ├── reports/         # Financial reports
    ├── ai-chat/         # AI-powered chat for financial insights
    └── settings/        # App settings
```

**Key architectural rules:**
- Feature modules never depend on other feature modules
- Feature modules only depend on core modules
- Core modules may depend on other core modules
- `:app` aggregates all feature modules for navigation

## Key Frameworks

### Metro DI (Compile-time Dependency Injection)

- **AppScope** (`core/common`): Root DI scope
- **AppGraph** (`app/src/main/java/.../di/AppGraph.kt`): Main dependency graph
- Repositories use `@ContributesBinding` to contribute to AppScope
- Presenters use `@AssistedInject` with Navigator as assisted parameter

### Circuit (MVI Architecture)

Each screen follows the pattern:
```kotlin
@Parcelize
data object FeatureScreen : Screen {
    data class State(...) : CircuitUiState
    sealed interface Event : CircuitUiEvent
}

@AssistedInject class FeaturePresenter @Inject constructor(
    @Assisted private val navigator: Navigator,
    // dependencies
) : Presenter<FeatureScreen.State>

@CircuitInject(FeatureScreen::class, AppScope::class)
@Composable
fun FeatureUi(state: FeatureScreen.State, modifier: Modifier)
```

Circuit codegen configured with Metro mode in feature modules.

### SQLDelight (Database)

- Schema files in `core/database/src/main/sqldelight/`
- Tables: `Expense.sq`, `Category.sq`, `Income.sq`, `IncomeCategory.sq`
- Type adapters for LocalDate and Instant in `DatabaseProvider`
- Repositories wrap queries with Flow-based observation

### Koog (AI Agent Framework)

- **AiConfig** (`core/ai`): Interface for API key configuration
- **AiService** (`core/ai`): Service interface for AI operations
- **ExpenseTools** (`core/ai`): Koog tools for querying expense data
- API key loaded from `local.properties` via BuildConfig (gitignored)
- Features: Smart category suggestion, financial insights chat

## Convention Plugins

Located in `build-logic/convention/`:
- `insight.android.application` - App module setup
- `insight.android.library` - Library modules
- `insight.android.compose` - Compose configuration + BOM
- `insight.android.feature` - Feature modules with Circuit + Metro + KSP

## Navigation

- Single-Activity architecture with `MainActivity`
- `NavigationSuiteScaffold` for adaptive navigation (bottom nav/rail/drawer)
- `AppDestinations` enum defines main tabs
- Circuit Navigator handles back stack management
