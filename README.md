# MetroDITest

A modern Android personal finance management application built with cutting-edge Kotlin technologies. Track expenses, monitor income, view financial reports, and get AI-powered insights into your spending habits.

## Features

- **Expense Tracking** - Record and categorize daily expenses with descriptions and dates
- **Income Management** - Track recurring and one-time income sources
- **Financial Reports** - View monthly breakdowns of spending, earnings, and net balance with savings rate calculations
- **AI-Powered Insights** - Chat with an AI assistant to query your financial data using natural language
- **Smart Categorization** - AI suggests appropriate categories based on expense descriptions

## Tech Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| **Kotlin** | 2.3.0 (K2) | Primary language with K2 compiler |
| **Jetpack Compose** | 2025.12.01 | Declarative UI framework |
| **Metro DI** | 0.9.2 | Compile-time dependency injection |
| **Circuit** | 0.31.0 | MVI architecture and navigation |
| **SQLDelight** | 2.2.1 | Type-safe SQL database |
| **Koog** | 0.6.0 | AI agent framework (OpenAI integration) |
| **Ktor** | 3.1.3 | HTTP client |
| **Material 3** | Latest | Design system |

**Platform Requirements:**
- Min SDK: 33 (Android 13)
- Target SDK: 36
- Java: JVM 21

## Architecture

The project follows the **NowInAndroid modular architecture** pattern with clear separation of concerns:

```
MetroDITest/
├── app/                     # Entry point, AppGraph, navigation
├── build-logic/             # Gradle convention plugins
├── core/
│   ├── common/              # AppScope, shared utilities
│   ├── model/               # Domain models (Expense, Income, Category)
│   ├── database/            # SQLDelight schema and drivers
│   ├── data/                # Repository interfaces and implementations
│   ├── designsystem/        # Material3 theme
│   ├── ui/                  # Shared UI components
│   └── ai/                  # Koog AI integration
└── feature/
    ├── expenses/            # Expense tracking screen
    ├── income/              # Income management screen
    ├── reports/             # Financial reports and analytics
    ├── ai-chat/             # AI chat interface
    └── settings/            # App settings
```

**Key Principles:**
- Feature modules never depend on other feature modules
- Features only depend on core modules
- Core modules may depend on other core modules
- Single-activity architecture with Circuit navigation

## Getting Started

### Prerequisites

- Android Studio Ladybug or later
- JDK 21
- Android SDK 36

### Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/your-username/MetroDITest.git
   cd MetroDITest
   ```

2. **Configure AI Features (Optional)**

   To enable AI-powered features, add your OpenAI API key to `local.properties`:
   ```properties
   OPENAI_API_KEY=sk-your-key-here
   ```

   Get an API key from https://platform.openai.com/api-keys

   > Note: AI features gracefully degrade when no API key is provided.

3. **Build the project**
   ```bash
   ./gradlew assembleDebug
   ```

### Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Run all unit tests
./gradlew test

# Run tests for a specific module
./gradlew :feature:expenses:test

# Run a single test class
./gradlew test --tests "com.keisardev.metroditest.ExampleUnitTest"

# Run instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest

# Clean build
./gradlew clean
```

## Key Frameworks

### Metro DI

Compile-time dependency injection eliminating reflection overhead and runtime errors.

```kotlin
@CircuitInject(ExpensesScreen::class, AppScope::class)
class ExpensesPresenter @AssistedInject constructor(
    @Assisted private val navigator: Navigator,
    private val expenseRepository: ExpenseRepository,
) : Presenter<ExpensesScreen.State>
```

- `AppScope` - Root DI scope for application-wide singletons
- `@ContributesBinding` - Contribute implementations to the graph
- `@AssistedInject` - Runtime parameters like Navigator

### Circuit

MVI architecture with unidirectional data flow and Compose integration.

```kotlin
@Parcelize
data object ExpensesScreen : Screen {
    data class State(
        val expenses: List<Expense>,
        val eventSink: (Event) -> Unit
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data object AddExpense : Event
        data class EditExpense(val id: Long) : Event
    }
}
```

### SQLDelight

Type-safe SQL with generated Kotlin APIs.

```sql
-- Expense.sq
selectByDateRange:
SELECT * FROM Expense
WHERE date BETWEEN :startDate AND :endDate
ORDER BY date DESC;

selectTotalByCategory:
SELECT categoryId, SUM(amount) as total
FROM Expense
WHERE date BETWEEN :startDate AND :endDate
GROUP BY categoryId;
```

### Koog

AI agent framework for natural language financial queries.

```kotlin
interface AiService {
    suspend fun suggestCategory(description: String): String?
    suspend fun chat(message: String): String
}
```

## Default Categories

The app comes pre-seeded with common expense categories:

| Category | Icon | Color |
|----------|------|-------|
| Food | Restaurant | Red |
| Transport | Car | Blue |
| Entertainment | Movie | Purple |
| Shopping | Shopping Bag | Yellow |
| Bills | Receipt | Teal |
| Health | Medical | Orange |
| Other | More | Gray |

## Convention Plugins

Custom Gradle plugins in `build-logic/` for consistent module configuration:

- `metroditest.android.application` - App module setup
- `metroditest.android.library` - Library modules
- `metroditest.android.compose` - Compose configuration with BOM
- `metroditest.android.feature` - Feature modules with Circuit + Metro + KSP

## License

```
Copyright 2024 Keisardev

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
