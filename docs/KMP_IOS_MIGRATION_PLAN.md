# Kotlin Multiplatform iOS Migration Plan

## Executive Summary

This document outlines the migration strategy for converting the **Insight** Android application to a Kotlin Multiplatform (KMP) project supporting iOS using **Compose Multiplatform + Circuit**. This approach maximizes code sharing including the entire UI layer.

**Key Statistics:**
- Estimated shared code: **90-95%** (including UI)
- Primary effort: Platform configuration and iOS app shell
- Critical blocker: Koog AI framework (JVM-only)

---

## Table of Contents

1. [Current Architecture Assessment](#1-current-architecture-assessment)
2. [Migration Strategy](#2-migration-strategy)
3. [Module Migration Details](#3-module-migration-details)
4. [Platform Abstractions](#4-platform-abstractions)
5. [iOS App Shell](#5-ios-app-shell)
6. [Build Configuration](#6-build-configuration)
7. [Compose Multiplatform Considerations](#7-compose-multiplatform-considerations)
8. [Risk Assessment](#8-risk-assessment)
9. [Implementation Phases](#9-implementation-phases)

---

## 1. Current Architecture Assessment

### Module Structure

```
Insight/
├── app/                    # Android entry point
├── build-logic/            # Gradle convention plugins
├── core/
│   ├── common/             # AppScope, utilities
│   ├── model/              # Domain models
│   ├── database/           # SQLDelight schemas
│   ├── data/               # Repository implementations
│   ├── designsystem/       # Material3 theme
│   ├── ui/                 # Shared Compose components
│   └── ai/                 # Koog AI integration
└── feature/
    ├── expenses/           # Circuit + Compose
    ├── income/             # Circuit + Compose
    ├── reports/            # Circuit + Compose
    ├── ai-chat/            # Circuit + Compose
    └── settings/           # Circuit + Compose
```

### Technology Stack Compatibility

| Technology | Version | KMP + CMP Support | Notes |
|------------|---------|-------------------|-------|
| Kotlin | 2.3.0 (K2) | ✅ Full | No changes needed |
| Coroutines | 1.10.2 | ✅ Full | No changes needed |
| SQLDelight | 2.2.1 | ✅ Full | Need native driver |
| Metro DI | 0.9.2 | ✅ Full | Compile-time Kotlin |
| **Circuit** | 0.31.0 | ✅ **Full KMP** | Compose Multiplatform support |
| Ktor | 3.1.3 | ✅ Full | HTTP client ready |
| kotlinx-datetime | 0.6.1 | ✅ Full | No changes needed |
| **Compose Multiplatform** | 1.7+ | ✅ Stable iOS | Production ready |
| Koog | 0.6.0 | ❌ JVM-only | **Needs replacement** |

### Migration Readiness by Module

| Module | Readiness | Effort | Changes Required |
|--------|-----------|--------|------------------|
| core:model | 100% | None | Move to commonMain |
| core:common | 95% | Low | Minor expect/actual |
| core:database | 80% | Low | Platform drivers |
| core:data | 90% | Low | Minor abstractions |
| core:ai | 30% | High | Replace Koog |
| **core:designsystem** | **95%** | **Low** | Migrate to CMP theme |
| **core:ui** | **95%** | **Low** | Already Compose-based |
| **feature:*** | **95%** | **Low** | Circuit + Compose works on iOS |

---

## 2. Migration Strategy

### Approach: Full Compose Multiplatform + Circuit

With Compose Multiplatform, we share **almost everything** including UI:

```
┌─────────────────────────────────────────────────────────┐
│                    Platform Entry Points                 │
│  ┌─────────────────────┐   ┌─────────────────────────┐  │
│  │   Android App       │   │     iOS App Shell       │  │
│  │   (MainActivity)    │   │   (minimal Swift code)  │  │
│  └─────────────────────┘   └─────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
                            │
┌─────────────────────────────────────────────────────────┐
│               Shared (commonMain) - 95%                  │
│  ┌─────────────────────────────────────────────────────┐│
│  │          UI Layer (Compose Multiplatform)           ││
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐ ││
│  │  │   Circuit   │  │  Material3  │  │  Features   │ ││
│  │  │ Presenters  │  │   Theme     │  │     UI      │ ││
│  │  └─────────────┘  └─────────────┘  └─────────────┘ ││
│  └─────────────────────────────────────────────────────┘│
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐  │
│  │   Models    │  │ Repositories│  │   Database      │  │
│  │  (100%)     │  │   (95%)     │  │  (SQLDelight)   │  │
│  └─────────────┘  └─────────────┘  └─────────────────┘  │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐  │
│  │  AI Service │  │  DI (Metro) │  │  Shared UI      │  │
│  │  Interface  │  │   Bindings  │  │  Components     │  │
│  └─────────────┘  └─────────────┘  └─────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

### Why Compose Multiplatform + Circuit?

1. **Maximum code sharing** (90-95% including UI)
2. **Circuit is KMP-native** - full multiplatform support since 0.20+
3. **Material3 components** work on iOS via CMP
4. **Existing code reuse** - minimal changes to current implementation
5. **Single source of truth** for UI logic and presentation
6. **Faster development** - one codebase for both platforms

---

## 3. Module Migration Details

### 3.1 core:model (100% Shareable)

**Current state:** Pure Kotlin data classes.

**Migration:**
- Move all files to `src/commonMain/kotlin/`
- No code changes required

### 3.2 core:common (95% Shareable)

**Current state:** Contains `AppScope` for Metro DI.

**Migration:**
- Move `AppScope` to commonMain (already platform-agnostic)
- No changes expected

### 3.3 core:database (Needs Expect/Actual for Driver)

**Current state:** Uses `AndroidSqliteDriver`.

**Migration Steps:**

1. **Move SQLDelight schemas to commonMain:**
   ```
   src/commonMain/sqldelight/com/keisardev/insight/core/database/
   ├── Category.sq
   ├── Expense.sq
   ├── Income.sq
   └── IncomeCategory.sq
   ```

2. **Create expect/actual for driver creation:**

   ```kotlin
   // commonMain
   expect class DatabaseDriverFactory {
       fun createDriver(): SqlDriver
   }

   // androidMain
   actual class DatabaseDriverFactory(
       private val context: Context
   ) {
       actual fun createDriver(): SqlDriver =
           AndroidSqliteDriver(
               schema = ExpenseDatabase.Schema,
               context = context,
               name = "expense_tracker.db"
           )
   }

   // iosMain
   actual class DatabaseDriverFactory {
       actual fun createDriver(): SqlDriver =
           NativeSqliteDriver(
               schema = ExpenseDatabase.Schema,
               name = "expense_tracker.db"
           )
   }
   ```

### 3.4 core:data (90% Shareable)

**Current state:** Repository implementations using SQLDelight + Coroutines.

**Migration:**
- Move all files to commonMain
- `Dispatchers.IO` → Use `Dispatchers.Default` for iOS compatibility
- Create `ioDispatcher` expect/actual if needed

### 3.5 core:ai (Needs Replacement)

**Current state:** Uses Koog (JVM-only).

**Solution:** Create custom OpenAI HTTP client using Ktor.

```kotlin
// commonMain
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class HttpAiService @Inject constructor(
    private val aiConfig: AiConfig,
    private val httpClient: HttpClient,
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
) : AiService {

    override suspend fun chat(message: String): String {
        val response = httpClient.post("https://api.openai.com/v1/chat/completions") {
            headers {
                append("Authorization", "Bearer ${aiConfig.openAiApiKey}")
            }
            contentType(ContentType.Application.Json)
            setBody(buildChatRequest(message))
        }
        return response.body<ChatResponse>().choices.first().message.content
    }

    override suspend fun suggestCategory(
        description: String,
        amount: Double,
        availableCategories: List<Category>
    ): Category? {
        // Use OpenAI function calling API
    }
}
```

### 3.6 core:designsystem (95% Shareable)

**Current state:** Material3 theme with Compose.

**Migration:**
- Move to commonMain
- Compose Multiplatform supports Material3 fully
- Minor adjustments for platform-specific typography if needed

```kotlin
// commonMain - Works as-is!
@Composable
fun InsightTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
```

### 3.7 core:ui (95% Shareable)

**Current state:** Shared Compose components.

**Migration:**
- Move to commonMain
- All Compose components work on iOS via CMP
- Components like `CategoryIcon`, `EmptyState`, etc. work without changes

### 3.8 Feature Modules (95% Shareable)

**Current state:** Circuit presenters + Compose UI.

**Migration:**
- Move to commonMain
- **Circuit is fully KMP-compatible!**
- Presenters, Screens, and UI work on both platforms

**Existing code works as-is:**
```kotlin
// This entire file moves to commonMain unchanged!
@Parcelize
data object ExpensesScreen : Screen {
    data class State(
        val isLoading: Boolean,
        val expenses: List<Expense>,
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data object OnAddClick : Event
        data class OnExpenseClick(val id: Long) : Event
    }
}

class ExpensesPresenter @AssistedInject constructor(
    @Assisted private val navigator: Navigator,
    private val expenseRepository: ExpenseRepository,
) : Presenter<ExpensesScreen.State> {

    @Composable
    override fun present(): ExpensesScreen.State {
        val expenses by expenseRepository.observeAllExpenses()
            .collectAsRetainedState(initial = emptyList())
        // ... existing logic works on iOS!
    }
}

@CircuitInject(ExpensesScreen::class, AppScope::class)
@Composable
fun ExpensesUi(state: ExpensesScreen.State, modifier: Modifier = Modifier) {
    Scaffold(/* ... */) { padding ->
        // All Compose UI works on iOS!
    }
}
```

---

## 4. Platform Abstractions

### 4.1 Database Driver (Required)

```kotlin
// commonMain
expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

// androidMain
actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver =
        AndroidSqliteDriver(ExpenseDatabase.Schema, context, "expense_tracker.db")
}

// iosMain
actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver =
        NativeSqliteDriver(ExpenseDatabase.Schema, "expense_tracker.db")
}
```

### 4.2 Platform Configuration

```kotlin
// commonMain
expect class PlatformConfig {
    val openAiApiKey: String?
    val isDebug: Boolean
}

// androidMain
actual class PlatformConfig(private val context: Context) {
    actual val openAiApiKey: String? = BuildConfig.OPENAI_API_KEY
    actual val isDebug: Boolean = BuildConfig.DEBUG
}

// iosMain
actual class PlatformConfig {
    actual val openAiApiKey: String? =
        NSBundle.mainBundle.objectForInfoDictionaryKey("OPENAI_API_KEY") as? String
    actual val isDebug: Boolean = Platform.isDebugBinary
}
```

### 4.3 Dispatcher Configuration

```kotlin
// commonMain
expect val ioDispatcher: CoroutineDispatcher

// androidMain
actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

// iosMain
actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
```

### 4.4 Parcelize for iOS

Circuit screens use `@Parcelize`. For iOS compatibility:

```kotlin
// Use Circuit's built-in KMP Parcelable support
// Circuit provides expect/actual for Parcelable on iOS
@Parcelize
data object ExpensesScreen : Screen { /* ... */ }
```

---

## 5. iOS App Shell

The iOS app is minimal - just a shell that launches Compose Multiplatform.

### 5.1 iOS Project Structure

```
iosApp/
├── iosApp.xcodeproj/
├── iosApp/
│   ├── App/
│   │   └── iOSApp.swift           # ~20 lines
│   ├── ContentView.swift          # ~10 lines
│   └── Info.plist
└── Podfile (or Package.swift)
```

### 5.2 iOS App Entry Point

```swift
// iOSApp.swift
import SwiftUI
import Shared  // KMP framework

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

// ContentView.swift
import SwiftUI
import Shared

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all)
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
```

### 5.3 Kotlin iOS Entry Point

```kotlin
// shared/src/iosMain/kotlin/MainViewController.kt
import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController() = ComposeUIViewController {
    val circuit = remember {
        // Create Circuit instance with Metro DI
        createCircuit()
    }

    InsightTheme {
        InsightApp(circuit = circuit)
    }
}

@Composable
fun InsightApp(circuit: Circuit) {
    // Your existing navigation scaffold from MainActivity
    val backStack = rememberSaveableBackStack(root = ExpensesScreen)
    val navigator = rememberCircuitNavigator(backStack)

    NavigationSuiteScaffold(
        // ... existing navigation code
    ) {
        NavigableCircuitContent(
            navigator = navigator,
            backStack = backStack,
        )
    }
}
```

---

## 6. Build Configuration

### 6.1 Updated settings.gradle.kts

```kotlin
rootProject.name = "Insight"

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

include(":app")  // Android app

// Core modules (KMP)
include(":core:common")
include(":core:model")
include(":core:database")
include(":core:data")
include(":core:ai")
include(":core:designsystem")
include(":core:ui")

// Feature modules (KMP with Compose)
include(":feature:expenses")
include(":feature:income")
include(":feature:reports")
include(":feature:ai-chat")
include(":feature:settings")

// Shared umbrella module for iOS
include(":shared")
```

### 6.2 Shared Module build.gradle.kts

```kotlin
plugins {
    kotlin("multiplatform")
    kotlin("native.cocoapods")
    id("com.android.library")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.metro)
    alias(libs.plugins.ksp)
    kotlin("plugin.serialization")
    id("kotlin-parcelize")
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "21"
            }
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    cocoapods {
        summary = "Insight shared KMP module"
        homepage = "https://github.com/keisardev/insight"
        version = "1.0"
        ios.deploymentTarget = "16.0"
        podfile = project.file("../iosApp/Podfile")
        framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Compose Multiplatform
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)

                // Circuit
                implementation(libs.circuit.foundation)
                implementation(libs.circuit.overlay)
                implementation(libs.circuitx.gestureNavigation)

                // Core
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.serialization.json)

                // Database
                implementation(libs.sqldelight.runtime)
                implementation(libs.sqldelight.coroutines)

                // Network
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.json)

                // DI
                implementation(libs.metro.runtime)
            }
        }

        val androidMain by getting {
            dependencies {
                implementation(libs.sqldelight.android.driver)
                implementation(libs.ktor.client.okhttp)
                implementation(libs.androidx.activity.compose)
            }
        }

        val iosX64Main by getting
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting
        val iosMain by creating {
            dependsOn(commonMain)
            iosX64Main.dependsOn(this)
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)
            dependencies {
                implementation(libs.sqldelight.native.driver)
                implementation(libs.ktor.client.darwin)
            }
        }
    }
}

android {
    namespace = "com.keisardev.insight.shared"
    compileSdk = 36
    defaultConfig {
        minSdk = 33
    }
    buildFeatures {
        compose = true
    }
}

sqldelight {
    databases {
        create("ExpenseDatabase") {
            packageName.set("com.keisardev.insight.core.database")
        }
    }
}
```

### 6.3 Updated libs.versions.toml

```toml
[versions]
kotlin = "2.3.0"
compose-multiplatform = "1.7.3"
circuit = "0.31.0"
sqldelight = "2.2.1"
ktor = "3.1.3"
coroutines = "1.10.2"
metro = "0.9.2"

[libraries]
# Compose Multiplatform (managed by plugin)
# No explicit compose dependencies needed - use compose.* in build script

# Circuit (KMP)
circuit-foundation = { group = "com.slack.circuit", name = "circuit-foundation", version.ref = "circuit" }
circuit-overlay = { group = "com.slack.circuit", name = "circuit-overlay", version.ref = "circuit" }
circuitx-gestureNavigation = { group = "com.slack.circuit", name = "circuitx-gesture-navigation", version.ref = "circuit" }

# SQLDelight
sqldelight-runtime = { group = "app.cash.sqldelight", name = "runtime", version.ref = "sqldelight" }
sqldelight-coroutines = { group = "app.cash.sqldelight", name = "coroutines-extensions", version.ref = "sqldelight" }
sqldelight-android-driver = { group = "app.cash.sqldelight", name = "android-driver", version.ref = "sqldelight" }
sqldelight-native-driver = { group = "app.cash.sqldelight", name = "native-driver", version.ref = "sqldelight" }

# Ktor
ktor-client-core = { group = "io.ktor", name = "ktor-client-core", version.ref = "ktor" }
ktor-client-okhttp = { group = "io.ktor", name = "ktor-client-okhttp", version.ref = "ktor" }
ktor-client-darwin = { group = "io.ktor", name = "ktor-client-darwin", version.ref = "ktor" }
ktor-client-content-negotiation = { group = "io.ktor", name = "ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-json = { group = "io.ktor", name = "ktor-serialization-kotlinx-json", version.ref = "ktor" }

# Coroutines
kotlinx-coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-datetime = { group = "org.jetbrains.kotlinx", name = "kotlinx-datetime", version = "0.6.1" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version = "1.8.0" }

# Metro DI
metro-runtime = { group = "dev.zacsweeny.metro", name = "metro-runtime", version.ref = "metro" }

[plugins]
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
compose-multiplatform = { id = "org.jetbrains.compose", version.ref = "compose-multiplatform" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
sqldelight = { id = "app.cash.sqldelight", version.ref = "sqldelight" }
metro = { id = "dev.zacsweeny.metro", version.ref = "metro" }
```

### 6.4 KMP Convention Plugin

```kotlin
// build-logic/convention/src/main/kotlin/KmpFeatureConventionPlugin.kt
class KmpFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.multiplatform")
                apply("com.android.library")
                apply("org.jetbrains.compose")
                apply("org.jetbrains.kotlin.plugin.compose")
                apply("dev.zacsweeny.metro")
                apply("com.google.devtools.ksp")
                apply("kotlin-parcelize")
            }

            extensions.configure<KotlinMultiplatformExtension> {
                androidTarget()

                listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach {
                    it.binaries.framework {
                        baseName = project.name
                        isStatic = true
                    }
                }

                sourceSets {
                    commonMain.dependencies {
                        // Common dependencies added here
                    }
                }
            }
        }
    }
}
```

---

## 7. Compose Multiplatform Considerations

### 7.1 Material3 Components Support

✅ **Fully supported on iOS:**
- Scaffold, TopAppBar, BottomAppBar
- NavigationBar, NavigationRail, NavigationDrawer
- Cards, Buttons, FABs
- TextFields, Dialogs, Sheets
- Lists, LazyColumn, LazyRow
- Icons (Material Icons)

### 7.2 Platform-Specific Adjustments

**Typography (optional):**
```kotlin
// commonMain
expect val platformTypography: Typography

// androidMain
actual val platformTypography = Typography(
    // Roboto is default on Android
)

// iosMain
actual val platformTypography = Typography(
    // San Francisco is default on iOS
    bodyLarge = TextStyle(fontFamily = FontFamily.Default)
)
```

**Edge-to-edge handling:**
```kotlin
// commonMain
@Composable
expect fun PlatformInsets(content: @Composable () -> Unit)

// androidMain
@Composable
actual fun PlatformInsets(content: @Composable () -> Unit) {
    // Already handled by enableEdgeToEdge() in MainActivity
    content()
}

// iosMain
@Composable
actual fun PlatformInsets(content: @Composable () -> Unit) {
    Box(modifier = Modifier.systemBarsPadding()) {
        content()
    }
}
```

### 7.3 Resources and Images

```kotlin
// Use Compose Multiplatform resources
// src/commonMain/composeResources/drawable/
// src/commonMain/composeResources/values/strings.xml

@Composable
fun CategoryIcon(icon: String) {
    Icon(
        painter = painterResource(Res.drawable.category_icon),
        contentDescription = null
    )
}
```

### 7.4 Navigation with Circuit on iOS

Circuit navigation works identically on iOS:

```kotlin
// Works on both platforms!
@Composable
fun InsightApp(circuit: Circuit) {
    val backStack = rememberSaveableBackStack(root = ExpensesScreen)
    val navigator = rememberCircuitNavigator(backStack)

    NavigableCircuitContent(
        navigator = navigator,
        backStack = backStack,
    )
}
```

**iOS back gesture:** Use CircuitX gesture navigation:
```kotlin
NavigableCircuitContent(
    navigator = navigator,
    backStack = backStack,
    decoration = GestureNavigationDecoration(
        onBackInvoked = navigator::pop
    )
)
```

---

## 8. Risk Assessment

### High Risk

| Risk | Impact | Mitigation |
|------|--------|------------|
| Koog JVM-only | Blocks AI features on iOS | Create custom Ktor-based OpenAI client |
| Metro DI iOS codegen | Build failures | Verify KSP native support |

### Medium Risk

| Risk | Impact | Mitigation |
|------|--------|------------|
| CMP iOS performance | Slower UI | Profile, optimize Compose |
| SQLDelight native | Slower queries | Use indices, profile |
| Kotlin/Native memory | Memory leaks | Test extensively |

### Low Risk

| Risk | Impact | Mitigation |
|------|--------|------------|
| Circuit iOS | Rare edge cases | Well-tested KMP library |
| Material3 iOS | Minor visual differences | Platform adjustments |
| kotlinx-datetime | Date handling | Already stable |

---

## 9. Implementation Phases

### Phase 1: Project Setup
**Goal:** Create KMP project structure with Compose Multiplatform.

**Tasks:**
1. Add Compose Multiplatform plugin to project
2. Create `:shared` umbrella KMP module
3. Add iOS targets (iosX64, iosArm64, iosSimulatorArm64)
4. Set up CocoaPods integration
5. Create basic Xcode project
6. Verify empty iOS app compiles and runs

**Deliverables:**
- KMP project compiling for both platforms
- Empty iOS app launching

---

### Phase 2: Core Module Migration
**Goal:** Migrate core modules to commonMain.

**Tasks:**
1. Convert `core:model` → commonMain (no changes)
2. Convert `core:common` → commonMain (minimal changes)
3. Convert `core:database`:
   - Move schemas to commonMain
   - Create expect/actual DatabaseDriverFactory
4. Convert `core:data`:
   - Move repositories to commonMain
   - Create dispatcher abstraction

**Deliverables:**
- Database working on iOS simulator
- Repositories callable from iOS

---

### Phase 3: AI Service Replacement
**Goal:** Replace Koog with KMP-compatible implementation.

**Tasks:**
1. Create `HttpAiService` using Ktor
2. Implement OpenAI chat completions API
3. Convert Koog tools to function calling
4. Test AI features on both platforms

**Deliverables:**
- AI chat working on iOS
- Category suggestion working on iOS

---

### Phase 4: UI Module Migration
**Goal:** Migrate Compose UI to Compose Multiplatform.

**Tasks:**
1. Convert `core:designsystem` → commonMain
2. Convert `core:ui` components → commonMain
3. Set up Compose Multiplatform resources
4. Test theme and components on iOS

**Deliverables:**
- Material3 theme working on iOS
- Shared components rendering correctly

---

### Phase 5: Feature Module Migration
**Goal:** Migrate all feature modules with Circuit.

**Tasks:**
1. Convert `feature:expenses` → commonMain
2. Convert `feature:income` → commonMain
3. Convert `feature:reports` → commonMain
4. Convert `feature:ai-chat` → commonMain
5. Convert `feature:settings` → commonMain
6. Set up Circuit navigation for iOS

**Deliverables:**
- All features working on iOS
- Navigation functional on both platforms

---

### Phase 6: iOS App Shell & Polish
**Goal:** Complete iOS app with platform polish.

**Tasks:**
1. Create iOS app entry point (Swift)
2. Configure iOS-specific settings (Info.plist)
3. Add iOS app icon and launch screen
4. Handle iOS-specific edge cases
5. Performance optimization

**Deliverables:**
- Production-ready iOS app
- App Store submission ready

---

### Phase 7: Testing & QA
**Goal:** Ensure quality across platforms.

**Tasks:**
1. Shared unit tests for business logic
2. UI tests on both platforms
3. Integration testing
4. Performance profiling on iOS
5. Edge case testing

**Deliverables:**
- Test coverage for shared code
- Verified feature parity

---

## Appendix A: File Migration Summary

### Move to commonMain (No Changes)

```
✅ core/model/src/main/kotlin/** → shared/src/commonMain/kotlin/model/
✅ core/common/src/main/kotlin/** → shared/src/commonMain/kotlin/common/
✅ core/data/src/main/kotlin/** → shared/src/commonMain/kotlin/data/
✅ core/database/src/main/sqldelight/** → shared/src/commonMain/sqldelight/
✅ core/designsystem/src/main/kotlin/** → shared/src/commonMain/kotlin/designsystem/
✅ core/ui/src/main/kotlin/** → shared/src/commonMain/kotlin/ui/
✅ feature/*/src/main/kotlin/** → shared/src/commonMain/kotlin/feature/*/
```

### Requires Expect/Actual

```
🔄 DatabaseDriverFactory - Driver creation
🔄 PlatformConfig - API key access
🔄 ioDispatcher - IO dispatcher
```

### Requires Replacement

```
❌ Koog AI → HttpAiService (Ktor-based)
```

### Platform-Specific (Remains Separate)

```
📱 app/ - Android entry point (MainActivity, InsightApp)
🍎 iosApp/ - iOS entry point (iOSApp.swift, MainViewController.kt)
```

---

## Appendix B: Estimated Code Sharing

| Layer | Shared Code | Notes |
|-------|-------------|-------|
| Domain Models | 100% | Pure Kotlin |
| Repository Interfaces | 100% | Pure Kotlin |
| Repository Implementations | 95% | Minor expect/actual |
| Database Schemas | 100% | SQLDelight |
| Database Driver | 0% | Platform-specific |
| AI Service | 90% | Ktor-based (shared) |
| DI Bindings | 95% | Metro compile-time |
| Theme | 100% | Compose Multiplatform |
| UI Components | 100% | Compose Multiplatform |
| Feature Presenters | 100% | Circuit |
| Feature UI | 100% | Compose Multiplatform |
| **Overall** | **~93%** | Excellent sharing! |

---

*Document Version: 2.0*
*Last Updated: January 2026*
*Approach: Compose Multiplatform + Circuit*
