# Insight

**A personal finance Android app with AI-powered features — smart expense categorization and a financial insights chat powered by both cloud and on-device inference.**

[![CI](https://github.com/shaharKeisarApps/Insight/actions/workflows/ci.yml/badge.svg)](https://github.com/shaharKeisarApps/Insight/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.0-7F52FF.svg?logo=kotlin)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Min%20SDK-33-34A853.svg?logo=android)](https://developer.android.com)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202025.12.01-4285F4.svg)](https://developer.android.com/jetpack/compose)

---

## Overview

Insight is a full-featured personal finance tracker that uses AI to make managing money easier. Add an expense and the app suggests the right category. Have a question about your spending? Ask the AI chat — it queries your financial data and provides insights in natural language.

The AI system supports two inference backends that can run independently or together:

- **Cloud AI** (Koog + OpenAI) — High-quality responses using GPT models with tool-calling for querying expenses, income, and reports
- **On-Device AI** (Llamatik + llama.cpp) — Fully offline inference using local GGUF models, with no API costs or data leaving the device

Users can switch between Local, Cloud, or Auto mode directly from Settings. In Auto mode, the app prefers on-device inference and falls back to cloud when needed.

---

## Screenshots

| Expenses | Income | Reports | AI Chat | Settings |
|----------|--------|---------|---------|----------|
| ![Expenses](docs/screenshots/light/expenses.png) | ![Income](docs/screenshots/light/income.png) | ![Reports](docs/screenshots/light/reports.png) | ![AI Chat](docs/screenshots/light/ai_chat.png) | ![Settings](docs/screenshots/light/settings.png) |

---

## Architecture

### Dependency Graph

```mermaid
graph TD
    app[":app<br/>Entry Point"] --> feat_expenses[":feature:expenses"]
    app --> feat_income[":feature:income"]
    app --> feat_reports[":feature:reports"]
    app --> feat_aichat[":feature:ai-chat"]
    app --> feat_settings[":feature:settings"]

    feat_expenses --> core_data[":core:data"]
    feat_expenses --> core_ui[":core:ui"]
    feat_income --> core_data
    feat_income --> core_ui
    feat_reports --> core_data
    feat_reports --> core_ui
    feat_aichat --> core_data
    feat_aichat --> core_ui
    feat_aichat --> core_ai[":core:ai"]
    feat_settings --> core_ai
    feat_settings --> core_data

    core_data --> core_model[":core:model"]
    core_data --> core_database[":core:database"]
    core_data --> core_common[":core:common"]
    core_ai --> core_data
    core_ai --> core_common
    core_ui --> core_model
    core_ui --> core_designsystem[":core:designsystem"]
    core_database --> core_model
```

**Key architectural rules:**
- Feature modules never depend on other feature modules
- Feature modules only depend on core modules
- Core modules may depend on other core modules
- `:app` aggregates all feature modules for navigation
- `AiServiceStrategy` selects cloud or local AI automatically

---

## Why These Technical Choices

### Metro DI over Dagger/Hilt
- **Compile-time safety**: All bindings verified at build time; no runtime errors
- **KMP-ready**: Works seamlessly with Kotlin Multiplatform without separate frameworks
- **Kotlin-first**: Native Kotlin syntax; zero annotation processor complexity
- **Anvil-style aggregation**: Modular binding without a separate plugin; scales with multi-module architecture

### Circuit MVI over MVVM
- **Unidirectional data flow**: Clear event→state→UI pipeline prevents logic bugs
- **First-class Compose integration**: No ViewModel/LiveData friction; Presenters are composable functions
- **Type-safe navigation**: Screen-based routing with validated arguments
- **Excellent testability**: FakeNavigator for navigation testing; state machines easy to verify

### SQLDelight over Room
- **SQL-first with compile-time verification**: Invalid queries caught at build time
- **KMP-ready**: Same code runs on Android, iOS, JVM without changes
- **Type-safe generated code**: No string queries; IDE autocomplete for SQL
- **Fine-grained observability**: Observe specific queries with Flow instead of entire entities

### Dual AI Backends (Cloud + On-Device)
- **Privacy-first**: Financial data stays on device by default; cloud optional
- **Offline capability**: All AI features work without internet using local models
- **Graceful degradation**: Auto mode falls back to cloud if on-device inference fails
- **Cost control**: On-device inference eliminates API costs for common queries

---

## Tech Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| **Kotlin** | 2.3.0 (K2) | Language with K2 compiler |
| **Jetpack Compose** | BOM 2025.12.01 | Declarative UI |
| **Metro DI** | 0.9.2 | Compile-time dependency injection |
| **Circuit** | 0.31.0 | MVI architecture & navigation |
| **SQLDelight** | 2.2.1 | Type-safe SQL database |
| **Koog** | 0.6.0 | Cloud AI agent framework (OpenAI) |
| **Llamatik** | 0.16.0 | On-device LLM inference (llama.cpp) |
| **Ktor** | 3.1.3 | HTTP client |
| **Material 3** | Latest | Design system with dynamic color |

**Requirements:** Min SDK 33 (Android 13) · Target SDK 36 · JDK 21

---

## AI Integration

### Smart Category Suggestion

When adding an expense, the AI analyzes the description and suggests the most appropriate category — reducing manual input and improving data consistency.

### Financial Insights Chat

The AI chat screen lets users ask natural language questions about their finances. The AI has access to tool functions that query the local database:

```kotlin
@ContributesBinding(AppScope::class)
@Inject
class AiServiceStrategy(
    private val llamatikAiService: LlamatikAiService,  // local
    private val koogAiService: KoogAiService,           // cloud
) : AiService {
    var mode: AiMode = AiMode.AUTO  // LOCAL | CLOUD | AUTO
}
```

Example queries: *"How much did I spend on food this month?"*, *"What's my biggest expense category?"*, *"Compare my income vs spending."*

---

## Module Structure

```
Insight/
├── app/                     # Entry point, AppGraph, navigation
├── build-logic/             # Gradle convention plugins
├── core/
│   ├── common/              # AppScope, shared utilities
│   ├── model/               # Domain models (Expense, Income, Category)
│   ├── database/            # SQLDelight schema and drivers
│   ├── data/                # Repository interfaces and implementations
│   ├── designsystem/        # Material3 theme (InsightTheme)
│   ├── ui/                  # Shared UI components
│   └── ai/                  # Dual AI service (Koog + Llamatik)
└── feature/
    ├── expenses/            # Expense tracking with AI categorization
    ├── income/              # Income management
    ├── reports/             # Financial reports and analytics
    ├── ai-chat/             # AI chat with financial tools
    └── settings/            # App settings and AI mode selection
```

---

## Testing Strategy

### Unit Tests
- **Presenter logic**: Circuit's test harness validates state changes without UI
- **Repository contracts**: In-memory implementations verify query behavior
- **Tools**: Turbine for Flow assertions, Truth for readable expectations

### Screenshot Tests
- **UI regression detection**: Compose screenshot testing API captures visual changes
- **Feature-module focused**: Each screen has baseline screenshots for dark/light themes
- **CI integration**: Automated comparison on pull requests

### Benchmark Tests
- **Startup performance**: Measure app launch time and first screen render
- **Screen transitions**: Verify navigation between feature modules meets performance budgets
- **Run with**: `./gradlew benchmark` on physical device for reliable results

### Accessibility Testing
- See `docs/ACCESSIBILITY_AUDIT.md` for component audit and remediation roadmap

---

## Build & Run

```bash
# Clone
git clone https://github.com/shaharKeisarApps/Insight.git
cd Insight

# (Optional) Enable cloud AI
echo "OPENAI_API_KEY=sk-your-key" >> local.properties

# Build
./gradlew assembleDebug

# Test
./gradlew test
```

### On-Device AI Setup (Optional)

Place a GGUF model in the app's files directory to enable local inference:

```bash
adb push phi-2.Q4_0.gguf /data/data/com.keisardev.insight/files/models/
```

Recommended models: Phi-2 Q4_0 (~1.6 GB) for chat, any small model for categorization.

---

## License

```
Copyright 2025 Keisardev

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
