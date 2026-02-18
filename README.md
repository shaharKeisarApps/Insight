# Insight

**A modern Android personal finance app showcasing Metro DI, Circuit MVI, and dual AI backends (cloud + on-device).**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.0_K2-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-API_33+-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

---

## Why This Project?

Most Android samples use Dagger/Hilt and MVVM. Insight takes a different path:

- **Metro DI** — Compile-time dependency injection with zero reflection, zero runtime overhead
- **Circuit** — Slack's MVI architecture with Compose-native state management
- **Dual AI engine** — Cloud inference (Koog/OpenAI) and on-device inference (Llamatik/llama.cpp)
- **NowInAndroid-style modules** — Clean multi-module architecture with convention plugins

---

## Screenshots

| Expenses | Income | Reports | AI Chat | Settings |
|----------|--------|---------|---------|----------|
| ![Expenses](docs/screenshots/light/expenses_main.png) | ![Income](docs/screenshots/light/income_main.png) | ![Reports](docs/screenshots/light/reports_main.png) | ![AI Chat](docs/screenshots/light/ai_chat_main.png) | ![Settings](docs/screenshots/light/settings_main.png) |

See [docs/screenshots/README.md](docs/screenshots/README.md) for the full screenshot capture guide covering light, dark, and accessibility themes.

---

## Architecture

```
                    ┌────────────┐
                    │    App     │  AppGraph (Metro DI root)
                    │ MainActivity│  NavigationSuiteScaffold
                    └─────┬──────┘
                          │
          ┌───────┬───────┼───────┬───────┐
          ▼       ▼       ▼       ▼       ▼
     ┌─────────┐ ┌─────┐ ┌───────┐ ┌──────┐ ┌────────┐
     │Expenses │ │Income│ │Reports│ │AI Chat│ │Settings│
     │ Screen  │ │Screen│ │Screen │ │Screen│  │ Screen │
     └────┬────┘ └──┬──┘ └───┬───┘ └──┬───┘ └───┬────┘
          │         │        │        │          │
     Circuit Presenters + @CircuitInject + Metro @AssistedInject
          │         │        │        │          │
          ▼         ▼        ▼        ▼          ▼
     ┌──────────────────────────────────────────────┐
     │              Core Modules                     │
     │  ┌──────┐ ┌────┐ ┌──────────┐ ┌────┐ ┌────┐ │
     │  │ data │ │ db │ │   model  │ │ ui │ │ ai │ │
     │  └──────┘ └────┘ └──────────┘ └────┘ └──┬─┘ │
     └──────────────────────────────────────────┼───┘
                                                │
                                   ┌────────────┤
                                   ▼            ▼
                              ┌────────┐  ┌──────────┐
                              │  Koog  │  │ Llamatik │
                              │ (Cloud)│  │ (Local)  │
                              └────────┘  └──────────┘
```

**Key rules:**
- Feature modules never depend on other features
- Features only depend on core modules
- `AiServiceStrategy` selects cloud or local AI automatically

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

## Key Architectural Highlights

### Metro DI — Zero-Reflection Injection

```kotlin
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class ExpenseRepositoryImpl(
    private val database: InsightDatabase,
) : ExpenseRepository
```

Metro generates all wiring at compile time. No `kapt`, no reflection, no runtime graph resolution.

### Circuit MVI — Compose-Native State

```kotlin
@Parcelize
data object ExpensesScreen : Screen {
    data class State(
        val expenses: List<Expense>,
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState
}

@CircuitInject(ExpensesScreen::class, AppScope::class)
@Composable
fun ExpensesUi(state: ExpensesScreen.State, modifier: Modifier)
```

Presenters produce state, UI consumes it. No ViewModel, no LiveData — just Compose.

### Dual AI Engine — Cloud + On-Device

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

`AUTO` mode tries on-device first, falls back to cloud. Zero API costs when running locally.

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
    └── settings/            # App settings
```

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
