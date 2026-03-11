# Architecture Decision Records (ADRs)

This document captures key architectural decisions made for the Insight project.

---

## ADR-1: Multi-Module Architecture (NowInAndroid Pattern)

**Status**: Accepted
**Date**: 2025-12
**Context**: The app has growing complexity with 5+ major feature areas (expenses, income, reports, AI chat, settings) and shared infrastructure (database, data layer, UI components).

**Decision**: Adopt a multi-module architecture following the NowInAndroid pattern with clear separation:
- **Feature modules** (`feature:*`) — One module per screen feature; no inter-feature dependencies
- **Core modules** (`core:*`) — Shared infrastructure; may depend on other core modules
- **App module** (`:app`) — Entry point; aggregates all features for navigation

**Consequences**:
- **Scalability**: Easy to add new features without touching existing code
- **Team parallelism**: Teams can work on features independently
- **Compile isolation**: Changing one feature doesn't recompile the entire codebase
- **Testing burden**: Must establish clear contracts between layers
- **Gradle overhead**: More modules = longer build times (mitigated by strong caching and KSP)

**Related**:
- Architecture enforced via `build-logic/convention/` plugins
- Metro DI and Circuit are essential for the multi-module approach (see ADR-2 and ADR-3)

---

## ADR-2: Metro DI for Compile-Time Dependency Injection

**Status**: Accepted
**Date**: 2025-12
**Context**: Feature modules need to inject dependencies from core modules without creating a hard dependency tree. Dagger/Hilt require annotation processors and have runtime graph initialization.

**Decision**: Use Metro DI with `@ContributesBinding` annotations in core modules and `@AssistedInject` in feature presenters:
```kotlin
// In core:data, expose a repository
@ContributesBinding(AppScope::class)
@Singleton
class ExpenseRepositoryImpl @Inject constructor(...) : ExpenseRepository

// In feature:expenses, inject it
@AssistedInject
class ExpensesPresenter(
    @Assisted private val navigator: Navigator,
    private val expenseRepository: ExpenseRepository,
) : Presenter<ExpensesScreen.State>
```

**Consequences**:
- **Compile-time safety**: All bindings verified at `./gradlew build`; no NotFoundException at runtime
- **KMP ready**: No reliance on Android annotation processors; works for multiplatform later
- **Kotlin-first API**: No XML or annotation-heavy configuration
- **Zero runtime overhead**: No reflection; Metro generates static code
- **Learning curve**: Team must adopt `@ContributesBinding` and `@Inject` patterns
- **Scalability**: Metro's incremental compilation is efficient even with hundreds of bindings

**Alternative considered**: Dagger 2 with explicit multibindings. Rejected because Hilt is Android-specific and requires processor-heavy setup.

---

## ADR-3: Circuit MVI for UI Architecture

**Status**: Accepted
**Date**: 2025-11
**Context**: Modern Android development demands a Compose-first architecture with clear separation of concerns. Traditional MVVM + LiveData patterns feel heavyweight in Compose.

**Decision**: Use Circuit's MVI (Model-View-Intent) pattern for every screen:
```kotlin
@Parcelize
data object ExpensesScreen : Screen {
    data class State(...) : CircuitUiState
    sealed interface Event : CircuitUiEvent
}

@AssistedInject
class ExpensesPresenter(...) : Presenter<ExpensesScreen.State>

@CircuitInject(ExpensesScreen::class, AppScope::class)
@Composable
fun ExpensesUi(state: ExpensesScreen.State, modifier: Modifier)
```

**Consequences**:
- **Unidirectional flow**: Events trigger state changes; state drives UI. No two-way binding bugs
- **Composable presenters**: No ViewModel/LiveData friction; Presenter is a @Composable function
- **Type-safe navigation**: Screen objects carry navigation arguments; compiler verifies routes
- **Excellent testability**: State machines easy to test; FakeNavigator for nav testing
- **Back stack management**: Circuit handles back button semantics; no need for custom logic
- **Developer friction**: Initial learning curve on state → event → reducer pattern
- **Testing infrastructure**: Requires Circuit test harness for presenter testing

**Benefits over MVVM**:
- No LiveData re-emission bugs or stale state from configuration changes
- Compose-native; no bridging between reactive and functional paradigms

---

## ADR-4: SQLDelight for Type-Safe Persistence

**Status**: Accepted
**Date**: 2025-11
**Context**: The app needs a reliable, type-safe database layer for expenses, income, and categories. Room is excellent but is Android-specific; planning KMP support (iOS) suggests a KMP-ready solution.

**Decision**: Use SQLDelight with auto-generated queries and type adapters:
```sql
-- core/database/src/main/sqldelight/Expense.sq
CREATE TABLE expense (
  id INTEGER PRIMARY KEY,
  description TEXT NOT NULL,
  amount REAL NOT NULL,
  category_id INTEGER NOT NULL,
  date TEXT NOT NULL
);

SELECT * FROM expense WHERE id = ?;
```

```kotlin
// Auto-generated Expense.kt with compile-time verified queries
val expense = database.expenseQueries.selectById(id).executeAsOne()
```

**Consequences**:
- **Compile-time verification**: Invalid SQL caught at build time; no runtime surprises
- **KMP-ready**: Same code compiles for Android and iOS (via sqlcipher multiplatform)
- **Type-safe generated code**: IDE autocomplete for all query parameters and result columns
- **Fine-grained observability**: Observe specific queries with `Flow<List<T>>` instead of entire table
- **SQL-first mindset**: Developers write SQL; code generation handles boilerplate
- **Migration complexity**: Manual SQL migrations required (no automatic schema diffs like Room)
- **Debugging**: SQL logic is explicit; easier to reason about query performance

**Benefits over Room**:
- Multiplatform support eliminates need for separate iOS persistence layer later
- Queries are pure SQL; easier to optimize and debug using standard SQL tools

---

## ADR-5: Dual AI Backend Strategy (Koog Cloud + Llamatik On-Device)

**Status**: Accepted
**Date**: 2025-12
**Context**: Smart expense categorization and financial insights chat are core features. Users have varying needs:
- Privacy-conscious users want data to stay on-device
- Users with limited device storage want cloud inference
- Users want fallback when one backend is unavailable

**Decision**: Implement `AiServiceStrategy` that abstracts both backends and allows users to choose:
```kotlin
// Settings screen: user selects LOCAL, CLOUD, or AUTO
enum class AiMode { LOCAL, CLOUD, AUTO }

@ContributesBinding(AppScope::class)
class AiServiceStrategy(
    private val llamatikAiService: LlamatikAiService,  // On-device inference
    private val koogAiService: KoogAiService,           // Cloud (OpenAI/Gemini)
) : AiService {
    var mode: AiMode = AiMode.AUTO

    override suspend fun categorizeExpense(description: String): Category {
        return when (mode) {
            LOCAL -> llamatikAiService.categorize(description)
            CLOUD -> koogAiService.categorize(description)
            AUTO -> {
                return try {
                    llamatikAiService.categorize(description)
                } catch (e: Exception) {
                    koogAiService.categorize(description)  // Fallback to cloud
                }
            }
        }
    }
}
```

**Consequences**:
- **User choice**: Privacy-conscious users can run offline; others use cloud for quality
- **Cost control**: On-device inference eliminates per-request API costs
- **Resilience**: Auto mode provides seamless fallback if one backend fails
- **Complexity**: Two AI frameworks to maintain; ensure parity between local/cloud responses
- **Model management**: Users must download local models (~200MB–2GB depending on quality)
- **Storage overhead**: Local models use significant device storage; requires cleanup UI
- **Development burden**: Both services must support the same tool set for financial queries

**Alternatives considered**:
- Cloud only: Rejected due to privacy concerns and per-request costs
- On-device only: Rejected due to device storage and inference latency on slower devices

---

## ADR-6: Okio for KMP-Ready File System Operations

**Status**: Accepted
**Date**: 2025-12
**Context**: The app manages model file downloads, storage, and deletion. Direct use of Java `File` API is Android-specific and complicates future KMP support.

**Decision**: Use Okio (Square) for all file operations:
```kotlin
// Download and write a model file
val source = response.body.source()
val sink = fileSystem.sink(modelPath).buffer()
source.use { src ->
    sink.use { snk ->
        snk.writeAll(src)
    }
}

// List installed models
val files = fileSystem.list(modelsDir)
```

**Consequences**:
- **KMP-ready**: Same Okio code works on Android, iOS, and JVM
- **Exception handling**: Okio exceptions are consistent across platforms
- **Resource safety**: `use` blocks ensure streams are closed properly
- **Testing**: In-memory file system support via FakeFileSystem for unit tests
- **Learning curve**: Okio API differs slightly from `java.io.File`; team must adopt it
- **Performance**: Buffering and source/sink patterns are slightly different from File I/O

**Benefits**:
- Enables future iOS app without rewriting file management logic
- Better resource management than `java.io.File`

---

## Summary Table

| ADR | Decision | Key Benefit | Trade-off |
|-----|----------|------------|-----------|
| 1 | Multi-module NowInAndroid | Independent feature development | Build time, module management |
| 2 | Metro DI | Compile-time safety, KMP-ready | Learning curve for @ContributesBinding |
| 3 | Circuit MVI | Unidirectional flow, Compose-native | Testing setup, state management complexity |
| 4 | SQLDelight | Compile-time SQL verification, KMP | Manual migrations, SQL mindset required |
| 5 | Dual AI backends | User choice, cost control, resilience | Framework complexity, model storage |
| 6 | Okio file operations | KMP-ready I/O | Small API differences from java.io |

---

## Related Documents

- See `CLAUDE.md` for detailed build commands and project configuration
- See `README.md` for high-level architecture diagram and tech stack overview
- See `docs/ACCESSIBILITY_AUDIT.md` for UI component accessibility findings
