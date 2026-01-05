# Skill Improvement Analysis - Expense Tracker MVP

This document analyzes issues encountered during MVP implementation and provides specific recommendations for improving Claude Code skills.

---

## 1. SQLDelight Expert Skill

### Issues Encountered

1. **Missing Android-only setup**: The skill focuses on KMP but doesn't cover Android-only projects
2. **Missing kotlin.android plugin requirement**: Critical dependency not documented
3. **Aggregation query result types unclear**: `SELECT SUM(...) AS total` returns Double directly, not wrapper
4. **Update/Delete void operations unclear**: These return QueryResult, need explicit handling

### Recommended Additions

```markdown
## Android-Only Setup (Non-KMP)

For Android-only projects, SQLDelight requires the `kotlin-android` plugin:

\`\`\`kotlin
// build.gradle.kts
plugins {
    id("com.android.application") // or library
    id("org.jetbrains.kotlin.android")  // REQUIRED for SQLDelight
    id("app.cash.sqldelight")
}

sqldelight {
    databases {
        create("AppDatabase") {
            packageName.set("com.app.db")
            srcDirs.setFrom("src/main/sqldelight")  // Explicit for Android-only
        }
    }
}
\`\`\`

> **Important**: The `kotlin-android` plugin is mandatory. Without it, SQLDelight cannot find the source sets and will fail with "KotlinSourceSet with name 'main' not found".

## Aggregation Query Results

Aggregation queries with aliases return the primitive type directly:

\`\`\`sql
-- Schema
selectMonthlyTotal:
SELECT COALESCE(SUM(amount), 0.0) AS total
FROM expense
WHERE date >= ? AND date < ?;
\`\`\`

\`\`\`kotlin
// Usage - returns Double directly, NOT a wrapper object
override fun observeMonthlyTotal(start: LocalDate, end: LocalDate): Flow<Double> {
    return database.expenseQueries.selectMonthlyTotal(startMillis, endMillis)
        .asFlow()
        .mapToOneOrNull(Dispatchers.IO)
        .map { it ?: 0.0 }  // 'it' IS the Double, not it.total
}
\`\`\`

## Void Operations (Update/Delete)

Update and delete operations return QueryResult. For interface compatibility:

\`\`\`kotlin
// Interface expects Unit
interface ExpenseRepository {
    suspend fun deleteExpense(id: Long)
}

// Implementation - explicit Unit return type needed
override suspend fun deleteExpense(id: Long): Unit = withContext(Dispatchers.IO) {
    database.expenseQueries.deleteById(id)
}
\`\`\`
```

---

## 2. Circuit Expert Skill

### Issues Encountered

1. **Confusion between assisted vs non-assisted presenters**: The class-based presenter example shows Factory, but non-assisted presenters should just use @CircuitInject on class
2. **Missing @AssistedInject annotation**: When using @Assisted, must use @AssistedInject (not @Inject)
3. **Wrong Flow collection API used**: Used `collectAsState` instead of `collectAsRetainedState` for presenter state
4. **Wrong state retention API used**: Used `remember`/`rememberSaveable` instead of `rememberRetained`/`rememberRetainedSaveable`
5. **Missing @Preview patterns**: No guidance on how to create preview functions for Circuit UI components

### Recommended Additions

```markdown
## Presenter Injection Patterns

### Non-Assisted Presenter (No Navigator/Screen Params)

For presenters that only need injected dependencies (no @Assisted params):

\`\`\`kotlin
// CORRECT - @CircuitInject directly on class
@CircuitInject(ReportsScreen::class, AppScope::class)
class ReportsPresenter @Inject constructor(
    private val expenseRepository: ExpenseRepository,
) : Presenter<ReportsScreen.State> {

    @Composable
    override fun present(): ReportsScreen.State {
        // ...
    }
}

// NO Factory needed for non-assisted presenters!
\`\`\`

### Assisted Presenter (With Navigator/Screen Params)

For presenters that need runtime parameters (navigator, screen):

\`\`\`kotlin
// CORRECT - Use @AssistedInject (NOT @Inject) with @Assisted params
class ExpensesPresenter @AssistedInject constructor(
    @Assisted private val navigator: Navigator,
    private val expenseRepository: ExpenseRepository,
) : Presenter<ExpensesScreen.State> {

    @Composable
    override fun present(): ExpensesScreen.State {
        // ...
    }

    // Factory REQUIRED for assisted injection
    @CircuitInject(ExpensesScreen::class, AppScope::class)
    @AssistedFactory
    fun interface Factory {
        fun create(navigator: Navigator): ExpensesPresenter
    }
}
\`\`\`

> **Critical**: When using `@Assisted`, you MUST use `@AssistedInject` on the constructor, NOT `@Inject`. Using `@Inject` with `@Assisted` parameters will cause: "AssistedFactory target classes must have a single @AssistedInject-annotated constructor"

## State Retention APIs (MANDATORY for Presenters)

**IMPORTANT**: Always use Circuit's retained state APIs in presenters, NOT standard Compose APIs.

| Standard Compose (WRONG) | Circuit Retained (CORRECT) | Purpose |
|--------------------------|----------------------------|---------|
| `collectAsState` | `collectAsRetainedState` | Flow collection that survives config changes |
| `remember` | `rememberRetained` | State that survives config changes |
| `rememberSaveable` | `rememberRetainedSaveable` | State that survives process death |

\`\`\`kotlin
// WRONG - State lost on config change
val expenses by expenseRepository.observeAll().collectAsState(initial = emptyList())
var selectedMonth by remember { mutableStateOf(now.month) }

// CORRECT - State retained across config changes
val expenses by expenseRepository.observeAll().collectAsRetainedState(initial = emptyList())
var selectedMonth by rememberRetained { mutableStateOf(now.month) }
\`\`\`

> **Why**: Circuit presenters may be paused/resumed during navigation. Standard Compose APIs reset state, while retained APIs preserve it across configuration changes and back stack navigation.

## @Preview Functions for Circuit UI

Since Circuit UI functions receive State as a parameter, previews are straightforward:

\`\`\`kotlin
@CircuitInject(ProfileScreen::class, AppScope::class)
@Composable
fun ProfileUi(state: ProfileScreen.State, modifier: Modifier = Modifier) {
    // UI implementation
}

// Preview functions - pass different state objects for each scenario
@Preview(showBackground = true)
@Composable
private fun PreviewProfileUiLoading() {
    YourAppTheme {
        ProfileUi(
            state = ProfileScreen.State(
                isLoading = true,
                user = null,
                eventSink = {},  // Empty lambda for previews
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewProfileUiWithData() {
    YourAppTheme {
        ProfileUi(
            state = ProfileScreen.State(
                isLoading = false,
                user = User("John", "Doe"),
                eventSink = {},
            )
        )
    }
}
\`\`\`

> **Benefits**: Circuit's state-based UI makes previews trivial - just pass different state objects to visualize each UI variation.
```

---

## 3. Metro Expert Skill

### Issues Encountered

1. **@AssistedInject not shown for Circuit integration**: The Circuit section shows function-based presenters but not the @AssistedInject requirement for class-based

### Recommended Additions

```markdown
## Circuit Class-Based Presenter with Metro

When using class-based presenters with Circuit:

\`\`\`kotlin
// Use @AssistedInject for constructors with @Assisted parameters
class ProfilePresenter @AssistedInject constructor(  // NOT @Inject!
    @Assisted private val screen: ProfileScreen,
    @Assisted private val navigator: Navigator,
    private val userRepository: UserRepository,  // Injected by Metro
) : Presenter<ProfileScreen.State> {

    @Composable
    override fun present(): ProfileScreen.State {
        // ...
    }

    @CircuitInject(ProfileScreen::class, AppScope::class)
    @AssistedFactory
    fun interface Factory {
        fun create(screen: ProfileScreen, navigator: Navigator): ProfilePresenter
    }
}
\`\`\`

> **Note**: `@AssistedInject` is required when mixing `@Assisted` and regular injected parameters. `@Inject` alone will not work.
```

---

## 4. Gradle Expert Skill

### Issues Encountered

1. **AGP version compatibility not documented**: AGP 9.0-alpha incompatible with many plugins
2. **JVM target alignment missing**: Java and Kotlin targets must match
3. **Deprecated kotlinOptions syntax shown**: Should use new compilerOptions DSL

### Recommended Additions

```markdown
## AGP Version Compatibility

### Known Compatibility Issues

| AGP Version | Issue | Solution |
|-------------|-------|----------|
| 9.0-alpha | SQLDelight fails with "KotlinSourceSet not found" | Use AGP 8.x |
| 9.0-alpha | Many KSP plugins incompatible | Use AGP 8.x until stable |

> **Recommendation**: Use stable AGP 8.x versions for production projects until AGP 9.0 reaches stable.

## JVM Target Configuration

Ensure Java and Kotlin JVM targets match to avoid bytecode incompatibility:

\`\`\`kotlin
// build.gradle.kts - NEW syntax (Kotlin 2.0+)
android {
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

// OLD syntax (deprecated in Kotlin 2.0+) - AVOID
android {
    kotlinOptions {
        jvmTarget = "21"  // Deprecated!
    }
}
\`\`\`

> **Important**: The `kotlinOptions` DSL inside `android {}` is deprecated. Use the top-level `kotlin { compilerOptions {} }` block instead.

### Java Version Support

| Java Version | Status | Notes |
|-------------|--------|-------|
| JVM 11 | ✅ Stable | Minimum for Android |
| JVM 17 | ✅ Stable | Recommended for most projects |
| JVM 21 | ✅ Recommended | LTS, full tooling support (Robolectric, etc.) |
| JVM 23 | ⚠️ Limited | Cutting-edge, but Robolectric not supported yet |

> **Recommendation**: Use Java 21 for projects requiring Robolectric unit tests. Robolectric 4.16 does not yet support Java 23.
```

---

## Summary of Required Skill Updates

| Skill | Priority | Changes Needed |
|-------|----------|----------------|
| `sqldelight-expert` | **High** | Add Android-only setup section with kotlin.android requirement |
| `sqldelight-expert` | Medium | Clarify aggregation query result types |
| `sqldelight-expert` | Medium | Add void operation patterns (update/delete) |
| `circuit-expert` | **High** | Clarify non-assisted vs assisted presenter patterns |
| `circuit-expert` | **High** | Show @AssistedInject requirement for class-based presenters |
| `circuit-expert` | **High** | Add State Retention APIs section (collectAsRetainedState, rememberRetained) |
| `circuit-expert` | **High** | Add @Preview patterns for Circuit UI components |
| `metro-expert` | Medium | Add @AssistedInject to Circuit integration section |
| `gradle-expert` | **High** | Add AGP compatibility table |
| `gradle-expert` | **High** | Update JVM target config to new compilerOptions DSL |
| `gradle-expert` | Medium | Add kotlin.android plugin requirement notes |

---

## Process Improvements

### Pre-Implementation Checklist

Before starting implementation, verify:

1. [ ] AGP version compatibility with planned libraries
2. [ ] Required Gradle plugins (kotlin.android for SQLDelight on Android-only)
3. [ ] JVM target consistency between Java and Kotlin
4. [ ] Dependency versions match documented requirements

### During Implementation

1. **Build early and often**: Run `./gradlew assembleDebug` after each major file creation
2. **Check generated code**: For KSP-based libraries, verify generated code exists
3. **Use correct annotations**: @Inject vs @AssistedInject based on @Assisted usage
4. **Use Circuit retained APIs**: In presenters, always use `collectAsRetainedState`, `rememberRetained`, `rememberRetainedSaveable` instead of standard Compose equivalents
5. **Add @Preview functions**: Create preview functions for each UI state variation

### Skill Usage Pattern

```
1. Check skill for setup requirements
2. Verify Gradle plugin requirements
3. Check version compatibility notes
4. Follow patterns exactly (especially annotations)
5. Build to verify before proceeding
```

---

## 5. Multi-Module Architecture (New Section)

### Issues Encountered During Modularization

1. **Convention Plugin Extension Access**: Cannot use `CommonExtension` directly with `getByType` - must use specific types like `LibraryExtension`
2. **Compose BOM in Convention Plugins**: Library modules using convention plugins don't get BOM version resolution without explicit setup
3. **KSP Scope Resolution Across Modules**: Metro KSP can't resolve scope types if old files reference wrong package locations
4. **Parcelize Plugin Missing**: Feature modules need `kotlin-parcelize` plugin for `@Parcelize` on Screen classes
5. **Old Files Cause Confusing Errors**: Duplicate files with old imports cause cryptic KSP errors like "Error type '<ERROR TYPE: AppScope>'"
6. **Pure Model Design**: Compose types (`Color`) in data models break modularization - use primitives (`Long`) instead

### Recommended Additions to gradle-expert Skill

```markdown
## Convention Plugin Patterns

### Extension Type Access

**WRONG - CommonExtension cannot be retrieved directly:**
\`\`\`kotlin
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        // FAILS: Extension of type 'CommonExtension' does not exist
        val extension = extensions.getByType<CommonExtension<*, *, *, *, *, *>>()
    }
}
\`\`\`

**CORRECT - Use specific extension types:**
\`\`\`kotlin
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            // Use LibraryExtension for library modules
            extensions.configure<LibraryExtension> {
                buildFeatures {
                    compose = true
                }
            }
        }
    }
}
\`\`\`

### Compose BOM in Convention Plugins

Convention plugins must add BOM for version resolution:

\`\`\`kotlin
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            extensions.configure<LibraryExtension> {
                buildFeatures { compose = true }
            }

            // CRITICAL: Add BOM for version resolution
            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
            dependencies {
                val bom = libs.findLibrary("androidx-compose-bom").get()
                add("implementation", platform(bom))
                add("androidTestImplementation", platform(bom))
            }
        }
    }
}
\`\`\`

### Feature Module Convention Plugin

Complete pattern for feature modules with Circuit + Metro:

\`\`\`kotlin
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("metroditest.android.library")
                apply("metroditest.android.compose")
                apply("org.jetbrains.kotlin.plugin.parcelize")  // For @Parcelize on Screens
                apply("dev.zacsweers.metro")
                apply("com.google.devtools.ksp")
            }

            extensions.configure<LibraryExtension> {
                defaultConfig {
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
            }

            // KSP configuration for Circuit Metro mode
            afterEvaluate {
                extensions.findByName("ksp")?.let { ksp ->
                    (ksp as? com.google.devtools.ksp.gradle.KspExtension)?.apply {
                        arg("circuit.codegen.mode", "metro")
                    }
                }
            }

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

            dependencies {
                // Core module dependencies
                add("implementation", project(":core:model"))
                add("implementation", project(":core:data"))
                add("implementation", project(":core:designsystem"))
                add("implementation", project(":core:ui"))
                add("implementation", project(":core:common"))

                // Circuit dependencies
                add("implementation", libs.findLibrary("circuit.foundation").get())
                add("implementation", libs.findLibrary("circuit.retained").get())
                add("implementation", libs.findLibrary("circuit.codegen.annotations").get())
                add("ksp", libs.findLibrary("circuit.codegen").get())
            }
        }
    }
}
\`\`\`

## NowInAndroid Modularization Pattern

### Module Dependency Rules

\`\`\`
Feature modules:
  ✅ Can depend on: core modules (model, data, ui, designsystem, common)
  ❌ Cannot depend on: other feature modules

Core modules:
  ✅ Can depend on: other core modules
  ❌ Cannot depend on: feature modules

App module:
  ✅ Depends on: all feature modules + all core modules
\`\`\`

### Recommended Module Structure

\`\`\`
project/
├── app/                      # Application shell, aggregates features
├── build-logic/convention/   # Convention plugins
├── core/
│   ├── common/               # Shared utilities, DI scopes (AppScope)
│   ├── model/                # Pure data models (NO Compose dependencies!)
│   ├── database/             # Database setup + queries
│   ├── data/                 # Repositories
│   ├── designsystem/         # Theme, colors, typography
│   └── ui/                   # Shared composables
└── feature/
    ├── feature-a/            # Screen + Presenter + UI
    ├── feature-b/
    └── feature-c/
\`\`\`

### Pure Model Design

**WRONG - Compose types in models break modularization:**
\`\`\`kotlin
// core/model - BAD: Requires Compose dependency
data class Category(
    val id: Long,
    val name: String,
    val color: Color,  // Compose dependency!
)
\`\`\`

**CORRECT - Use primitives, convert in UI layer:**
\`\`\`kotlin
// core/model - GOOD: Pure data
data class Category(
    val id: Long,
    val name: String,
    val colorHex: Long,  // Primitive
)

// core/ui - Extension to convert
val Category.color: Color
    get() = Color(colorHex)
\`\`\`
```

### Recommended Additions to metro-expert Skill

```markdown
## Multi-Module Metro DI Setup

### Scope Class Location

The scope class (e.g., `AppScope`) MUST be in a module that all contributing modules can depend on:

\`\`\`
core/common/
└── di/
    └── AppScope.kt   # Shared scope class

// core/common/di/AppScope.kt
abstract class AppScope private constructor()
\`\`\`

### KSP Error: "Error type '<ERROR TYPE: AppScope>'"

This error occurs when:
1. Old files reference wrong AppScope location
2. Module dependencies don't expose the scope class
3. Duplicate files exist with conflicting imports

**Solution:**
1. Delete all old files that were moved to new modules
2. Ensure all modules import from the correct package
3. Run `./gradlew clean` to clear cached symbols

### Contributing from Feature Modules

Generated code from feature modules uses `@ContributesIntoSet`:

\`\`\`kotlin
// Generated by Circuit codegen in feature module
@Inject
@ContributesIntoSet(AppScope::class)  // Must resolve AppScope
public class ExpensesPresenterFactory(
    private val factory: ExpensesPresenter.Factory,
) : Presenter.Factory { ... }
\`\`\`

The app module's Metro graph aggregates all contributions automatically.
```

---

## 6. Modularization Process Checklist (New Section)

### Pre-Modularization

- [ ] Plan module structure (core/*, feature/*)
- [ ] Identify shared code (models, repositories, theme, utilities)
- [ ] Design pure data models (no UI framework dependencies)
- [ ] Create convention plugin strategy

### During Modularization

- [ ] Create build-logic module first
- [ ] Create core modules in dependency order (common → model → database → data → designsystem → ui)
- [ ] Create feature modules last
- [ ] Update imports in all moved files
- [ ] **DELETE old files** - duplicates cause confusing errors

### Post-Modularization Verification

- [ ] `./gradlew clean` - Clear all caches
- [ ] `./gradlew assembleDebug` - Verify compilation
- [ ] `./gradlew test` - Verify tests pass
- [ ] Check no duplicate files exist (especially in app module)
- [ ] Verify no old imports remain (`grep -r "old.package.name" .`)

### Common Pitfalls

| Issue | Symptom | Solution |
|-------|---------|----------|
| Old files not deleted | KSP "Error type" errors | Delete all moved files from original location |
| Wrong imports | Unresolved reference | Update imports to new module packages |
| Missing BOM in convention plugin | "Could not find library:." | Add BOM platform() to convention plugin |
| Missing parcelize plugin | "Unresolved reference: Parcelize" | Add parcelize plugin to feature convention |
| CommonExtension access | "Extension does not exist" | Use specific type (LibraryExtension) |

---

## Summary of Required Skill Updates (Updated)

| Skill | Priority | Changes Needed |
|-------|----------|----------------|
| `sqldelight-expert` | **High** | Add Android-only setup section with kotlin.android requirement |
| `sqldelight-expert` | Medium | Clarify aggregation query result types |
| `sqldelight-expert` | Medium | Add void operation patterns (update/delete) |
| `circuit-expert` | **High** | Clarify non-assisted vs assisted presenter patterns |
| `circuit-expert` | **High** | Show @AssistedInject requirement for class-based presenters |
| `circuit-expert` | **High** | Add State Retention APIs section |
| `circuit-expert` | **High** | Add @Preview patterns for Circuit UI components |
| `metro-expert` | Medium | Add @AssistedInject to Circuit integration section |
| `metro-expert` | **High** | Add multi-module setup and KSP error resolution |
| `gradle-expert` | **High** | Add AGP compatibility table |
| `gradle-expert` | **High** | Update JVM target config to new compilerOptions DSL |
| `gradle-expert` | **High** | Add convention plugin patterns (extension access, BOM, feature modules) |
| `gradle-expert` | **High** | Add NowInAndroid modularization patterns |
| `gradle-expert` | Medium | Add kotlin.android plugin requirement notes |
