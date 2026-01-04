# KMP Project - AI Development Ecosystem

## Project Overview

This is a Kotlin Multiplatform project targeting **Android**, **iOS**, and **Desktop** using a modern, production-grade architecture.

### Tech Stack

| Layer | Technology |
|-------|------------|
| **UI** | Circuit MVI + Compose Multiplatform + Material 3 |
| **DI** | Metro |
| **Data** | Store5 + SQLDelight + kotlinx.serialization |
| **Network** | Ktor Client |
| **Error Handling** | Arrow.Either |
| **Testing** | Kotest, Turbine, Paparazzi |
| **CI/CD** | GitHub Actions |

## Module Structure

```
project/
├── app/
│   ├── android/           # Android app entry point
│   ├── ios/               # iOS app entry point (Xcode project)
│   └── desktop/           # Desktop app entry point
├── core/
│   ├── common/            # Shared utilities, extensions
│   ├── ui/                # Design system, theme, components
│   ├── network/           # Ktor client setup
│   ├── database/          # SQLDelight setup
│   └── testing/           # Test fixtures, fakes
├── domain/
│   ├── models/            # Domain entities
│   └── usecases/          # Business logic
├── data/
│   └── repositories/      # Repository implementations
├── features/
│   ├── feature-home/      # Home screen feature
│   ├── feature-profile/   # Profile feature
│   └── feature-settings/  # Settings feature
└── build-logic/           # Convention plugins
```

---

## Quality Gates (MANDATORY)

### Before ANY Code Generation

Always verify changes compile and pass checks:

```bash
# Quick validation
./gradlew spotlessApply detekt :affected:check

# Full validation before commit
./gradlew spotlessCheck detekt check
```

### Quality Pipeline

```
[Generate Code] → [Format] → [Lint] → [Build] → [Test] → [Commit]
                     ↓
              spotlessApply
                     ↓
                  detekt
                     ↓
             ./gradlew build
                     ↓
             ./gradlew check
```

### Commands Reference

| Task | Command |
|------|---------|
| Format code | `./gradlew spotlessApply` |
| Check formatting | `./gradlew spotlessCheck` |
| Lint (Detekt) | `./gradlew detekt` |
| Build all | `./gradlew assemble` |
| Run tests | `./gradlew check` |
| Full validation | `./gradlew spotlessCheck detekt check` |
| Screenshot tests | `./gradlew verifyPaparazziDebug` |
| Update screenshots | `./gradlew recordPaparazziDebug` |

---

## Skill Invocation Guide

### When to Use Each Skill

| Task | Primary Skill | Support Skills |
|------|---------------|----------------|
| New Circuit screen | `circuit-expert` | `metro-expert`, `compose-expert` |
| Repository + caching | `store5-expert` | `sqldelight-expert`, `ktor-expert` |
| Business logic | `usecase-expert` | `arrow-expert` |
| UI implementation | `compose-expert` | `m3-theme-expert` |
| Dependency injection | `metro-expert` | `circuit-expert` |
| Error handling | `arrow-expert` | `usecase-expert` |
| Testing | `testing-expert` | `coroutines-expert` |
| Build configuration | `gradle-expert` | — |
| Code quality | `quality-expert` | — |
| CI/CD pipelines | `cicd-expert` | — |
| Git workflows | `git-expert` | — |
| Async operations | `coroutines-expert` | — |

### Skill Locations

```
~/.claude/skills/user/
├── circuit-expert/SKILL.md      # Screen/Presenter/State patterns
├── metro-expert/SKILL.md        # DI graphs, scoping, aggregation
├── store5-expert/SKILL.md       # Offline-first caching
├── compose-expert/SKILL.md      # UI patterns, recomposition
├── arrow-expert/SKILL.md        # Either, error handling
├── sqldelight-expert/SKILL.md   # Database schemas, migrations
├── ktor-expert/SKILL.md         # HTTP client, auth
├── testing-expert/SKILL.md      # Turbine, Paparazzi, fakes
├── usecase-expert/SKILL.md      # Domain layer patterns
├── coroutines-expert/SKILL.md   # Flow, structured concurrency
├── gradle-expert/SKILL.md       # Convention plugins, KMP
├── m3-theme-expert/SKILL.md     # Material 3 theming
├── quality-expert/SKILL.md      # Detekt, ktlint
├── cicd-expert/SKILL.md         # GitHub Actions
└── git-expert/SKILL.md          # Branching, commits
```

---

## Code Generation Patterns

### New Feature Module Checklist

When creating a new feature, generate in this order:

1. **Screen definitions** (`{Feature}Screen.kt`)
   - Screen data class with Parcelize
   - State sealed interface
   - Event sealed interface

2. **Domain models** (if new entities)
   - Data classes in `:domain:models`
   - Immutable, with @Immutable annotation

3. **Use case** (if business logic needed)
   - In `:domain:usecases`
   - Returns `Either<DomainError, T>`

4. **Repository interface** (in `:domain`)
   - Define contract
   - Use Either for results

5. **Repository implementation** (in `:data`)
   - Implement with Store5
   - Add `@ContributesBinding`

6. **Presenter** (`{Feature}Presenter.kt`)
   - Use `@CircuitInject`
   - Handle state and events

7. **UI** (`{Feature}Ui.kt`)
   - Use `@CircuitInject` for Composable
   - Accept state, handle events

8. **Tests**
   - Presenter tests with Turbine
   - Screenshot tests with Paparazzi

9. **DI wiring**
   - Verify all bindings compile

### File Naming Conventions

```kotlin
// Screens
{Feature}Screen.kt           // Screen + State + Events

// Presenters
{Feature}Presenter.kt        // Presenter function

// UI
{Feature}Ui.kt               // Main UI composable
{Feature}Content.kt          // Alternative naming

// Use Cases
Get{Entity}UseCase.kt        // Query single
Get{Entities}UseCase.kt      // Query list
Observe{Entity}UseCase.kt    // Flow-based
Create{Entity}UseCase.kt     // Create
Update{Entity}UseCase.kt     // Update
Delete{Entity}UseCase.kt     // Delete
{Verb}{Noun}UseCase.kt       // Action (ProcessPaymentUseCase)

// Repositories
{Entity}Repository.kt        // Interface (in domain)
{Entity}RepositoryImpl.kt    // Implementation (in data)

// Tests
{Feature}PresenterTest.kt    // Presenter tests
{Feature}UiScreenshotTest.kt // Screenshot tests
{Entity}RepositoryTest.kt    // Repository tests
```

---

## Architecture Patterns

### Circuit Screen Structure

```kotlin
@Parcelize
data class ProfileScreen(
    val userId: String,
) : Screen {
    
    sealed interface State : CircuitUiState {
        data object Loading : State
        data class Success(
            val user: User,
            val eventSink: (Event) -> Unit,
        ) : State
        data class Error(
            val message: String,
            val onRetry: () -> Unit,
        ) : State
    }
    
    sealed interface Event : CircuitUiEvent {
        data object Refresh : Event
        data object BackClick : Event
        data class EditClick(val field: String) : Event
    }
}
```

### Error Handling Flow

```
Network/DB Error → Repository (Either) → UseCase (bind) → Presenter (fold) → UI State
```

```kotlin
// Repository
override suspend fun getUser(id: String): Either<DomainError, User>

// Use Case
suspend operator fun invoke(id: String): Either<DomainError, UserProfile> = either {
    val user = userRepository.getUser(id).bind()
    // ...
}

// Presenter
result.fold(
    ifLeft = { state = State.Error(it.toUserMessage()) },
    ifRight = { state = State.Success(it) },
)
```

### Store5 + SQLDelight Pattern

```kotlin
private val store = StoreBuilder.from(
    fetcher = Fetcher.of { key -> api.getData(key) },
    sourceOfTruth = SourceOfTruth.of(
        reader = { key -> db.queries.get(key).asFlow().mapToOneOrNull() },
        writer = { key, value -> db.queries.upsert(value) },
    ),
).build()
```

---

## Commit Conventions

### Format

```
<type>(<scope>): <subject>

[optional body]

[optional footer]
```

### Types

- `feat` - New feature
- `fix` - Bug fix
- `docs` - Documentation
- `style` - Formatting
- `refactor` - Restructure
- `perf` - Performance
- `test` - Testing
- `build` - Build system
- `ci` - CI/CD
- `chore` - Maintenance

### Examples

```bash
feat(profile): add avatar upload functionality
fix(auth): resolve token refresh race condition
test(presenter): add ProfilePresenter unit tests
refactor(di): migrate from Dagger to Metro
```

---

## Workflow Examples

### Example A: Add New Feature

```bash
# 1. Create feature branch
git checkout -b feature/user-settings

# 2. Read relevant skills
# - circuit-expert, metro-expert, store5-expert, testing-expert

# 3. Generate code in order
# Screen → Models → UseCase → Repository → Presenter → UI → Tests

# 4. Run quality gates
./gradlew spotlessApply detekt :features:feature-settings:check

# 5. Commit with conventional format
git commit -m "feat(settings): add user settings screen"

# 6. Create PR
```

### Example B: Fix Bug

```bash
# 1. Create fix branch
git checkout -b fix/profile-crash

# 2. Write failing test first
# 3. Implement fix
# 4. Verify tests pass

./gradlew :features:feature-profile:check

# 5. Commit
git commit -m "fix(profile): resolve crash on rotation"
```

### Example C: Refactor to Store5

```bash
# 1. Read store5-expert and sqldelight-expert skills
# 2. Add SQLDelight schema
# 3. Create Store with SourceOfTruth
# 4. Update repository implementation
# 5. Update tests for cache scenarios
# 6. Run full validation

./gradlew spotlessApply detekt check
```

---

## Tips for AI Assistance

1. **Always read skills first** - Before generating code, read the relevant SKILL.md files

2. **Generate incrementally** - Create files one at a time, verify each compiles

3. **Run quality gates early** - Don't wait until the end to run checks

4. **Use existing patterns** - Look at similar features for reference

5. **Test as you go** - Write tests alongside implementation

6. **Commit atomically** - One logical change per commit

---

## Quick Reference

### Gradle Tasks

```bash
./gradlew tasks                    # List all tasks
./gradlew :module:dependencies     # Show dependencies
./gradlew build --scan             # Build with scan
./gradlew clean                    # Clean build
```

### Common Issues

| Issue | Solution |
|-------|----------|
| KSP not generating | Run `./gradlew kspCommonMainKotlinMetadata` |
| Compose preview crash | Check for missing `@Preview` parameters |
| iOS build fails | Run `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` |
| Test not found | Ensure test class is in correct source set |

---

## Resources

- [Circuit Documentation](https://slackhq.github.io/circuit/)
- [Metro Documentation](https://zacsweers.github.io/metro/)
- [Store5 Documentation](https://mobilenativefoundation.github.io/Store/)
- [Arrow Documentation](https://arrow-kt.io/docs/)
- [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
