# Compose Stability API Reference

> Compose Multiplatform 1.10.0 | Kotlin 2.3.10 | K2 Compose Compiler Plugin

---

## 1. Stability Annotations

### @Stable

Marks a type as stable for the Compose compiler. The compiler trusts this annotation unconditionally.

```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
@StableMarker
annotation class Stable
```

**Contract:**
1. The result of `equals` for two instances will always return the same result for the same two instances.
2. Whenever a public property of the type changes, composition will be notified.
3. All public property types are also stable.

**Valid uses:**
```kotlin
// Class with mutableStateOf-backed properties
@Stable
class TimerState {
    var elapsed by mutableStateOf(0L)
        private set
    fun tick() { elapsed++ }
}

// Interface with stable implementations
@Stable
interface Theme {
    val primaryColor: Color
    val backgroundColor: Color
}
```

### @Immutable

Marks a type as immutable. Stricter than `@Stable` -- guarantees no property will ever change after construction.

```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
@StableMarker
annotation class Immutable
```

**Contract:**
- All properties are `val`.
- All property types are also immutable.
- No property value will change after the instance is constructed.

**Valid uses:**
```kotlin
@Immutable
data class Coordinate(val lat: Double, val lng: Double)

@Immutable
data class UserBadge(val label: String, val color: Long)
```

### @StableMarker

Meta-annotation for creating custom stability markers. Any annotation annotated with `@StableMarker` acts as a stability annotation.

```kotlin
@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class StableMarker
```

**Custom marker example:**
```kotlin
@StableMarker
@Target(AnnotationTarget.CLASS)
annotation class DomainStable
```

### @NonRestartableComposable

Prevents the compiler from generating a restart group for a Composable function. The function cannot be individually recomposed; its parent recomposes instead.

```kotlin
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
annotation class NonRestartableComposable
```

**Use for:** Small utility Composables that do not read state directly and serve only as wrappers. Reduces slot table overhead.

```kotlin
@NonRestartableComposable
@Composable
fun Spacer16() {
    Spacer(modifier = Modifier.height(16.dp))
}
```

---

## 2. Compiler Inference Rules

The K2 Compose compiler plugin infers stability automatically based on these rules (in order of precedence):

### Rule 1: Primitive and Built-in Stability

| Type | Stable | Reason |
|------|--------|--------|
| `Int`, `Long`, `Float`, `Double`, `Boolean`, `Char`, `Byte`, `Short` | Yes | Immutable value types |
| `String` | Yes | Immutable |
| `Unit` | Yes | Singleton |
| `Nothing` | Yes | Bottom type |
| Enum classes | Yes | Fixed set of instances |
| Function types `(A) -> B`, `() -> Unit` | Yes | Compiler treats as stable |
| `Modifier` (companion) | Yes | Static singleton |
| `Color`, `TextUnit`, `Dp`, `Sp` (inline value classes over primitives) | Yes | Inline over stable |

### Rule 2: Snapshot-Observable Types

| Type | Stable | Reason |
|------|--------|--------|
| `MutableState<T>` | Yes | Changes notify via Snapshot system |
| `State<T>` | Yes | Read-only observable |
| `SnapshotStateList<T>` | Yes | Observable list |
| `SnapshotStateMap<K,V>` | Yes | Observable map |
| `derivedStateOf {}` result | Yes | Cached observable |

### Rule 3: Data Class Inference

A `data class` is inferred stable if and only if **all** of its constructor properties are:
- Declared as `val` (not `var`)
- Of a stable type

```kotlin
// Inferred STABLE: all val, all stable types
data class UserName(val first: String, val last: String)

// Inferred UNSTABLE: List<String> is unstable
data class TagList(val tags: List<String>)

// Inferred UNSTABLE: var property
data class MutableCounter(var count: Int)
```

### Rule 4: External Module Types

Types from external modules (dependencies) are **unstable by default** unless:
- They are annotated with `@Stable` or `@Immutable` in their source.
- They are listed in the stability configuration file.
- They match a built-in rule (primitives, String, etc.).

### Rule 5: Annotation Override

`@Stable` and `@Immutable` annotations always override compiler inference. The compiler trusts the annotation unconditionally -- it does not verify the contract.

---

## 3. Stability Configuration File

### File Format

Plain text file. One entry per line. Comments start with `//`. Supports glob patterns.

```
// Exact class match
kotlinx.datetime.Instant

// All classes in a package (single level)
kotlinx.collections.immutable.*

// All classes in a package and all subpackages
com.myapp.core.model.**

// Parameterized type (matches any type arguments)
kotlinx.collections.immutable.ImmutableList
```

### Pattern Matching

| Pattern | Matches |
|---------|---------|
| `com.example.Foo` | Exactly `com.example.Foo` |
| `com.example.*` | All classes directly in `com.example` (not subpackages) |
| `com.example.**` | All classes in `com.example` and all subpackages |

### Recommended Entries

```
// kotlinx.collections.immutable -- REQUIRED for ImmutableList/Set/Map stability
kotlinx.collections.immutable.ImmutableList
kotlinx.collections.immutable.ImmutableSet
kotlinx.collections.immutable.ImmutableMap
kotlinx.collections.immutable.PersistentList
kotlinx.collections.immutable.PersistentSet
kotlinx.collections.immutable.PersistentMap

// kotlinx.datetime -- immutable value types
kotlinx.datetime.Instant
kotlinx.datetime.LocalDate
kotlinx.datetime.LocalDateTime
kotlinx.datetime.LocalTime
kotlinx.datetime.TimeZone

// Ktor HTTP types used in state
io.ktor.http.Url
io.ktor.http.HttpMethod
io.ktor.http.HttpStatusCode

// UUID
kotlin.uuid.Uuid
```

---

## 4. Gradle Configuration (K2 Compose Compiler Plugin)

### composeCompiler Block

```kotlin
// build.gradle.kts (module level or convention plugin)
composeCompiler {
    // Path to stability configuration file
    stabilityConfigurationFile =
        project.rootDir.resolve("compose-stability-config.txt")

    // Directory for composable stability reports
    reportsDestination = layout.buildDirectory.dir("compose-reports")

    // Directory for module-level metrics
    metricsDestination = layout.buildDirectory.dir("compose-metrics")

    // Strong skipping mode (enabled by default in K2)
    // Explicitly setting it for documentation:
    featureFlags = setOf(
        ComposeFeatureFlag.StrongSkipping,       // default ON in K2
        // ComposeFeatureFlag.OptimizeNonSkippingGroups, // experimental
    )
}
```

### Convention Plugin Pattern

```kotlin
// build-logic/convention/src/main/kotlin/ComposeConventionPlugin.kt
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

class ComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        extensions.configure<ComposeCompilerGradlePluginExtension> {
            stabilityConfigurationFile.set(
                rootProject.file("compose-stability-config.txt")
            )
            // Enable reports only for CI or explicit opt-in
            if (providers.gradleProperty("composeReports").isPresent) {
                reportsDestination.set(layout.buildDirectory.dir("compose-reports"))
                metricsDestination.set(layout.buildDirectory.dir("compose-metrics"))
            }
        }
    }
}
```

### Generating Reports

```bash
# Generate reports for a specific module
./gradlew :feature:home:compileKotlin -PcomposeReports

# Or without the property guard (if always-on):
./gradlew :feature:home:compileKotlin

# Reports appear at:
# feature/home/build/compose-reports/<SourceSet>-classes.txt
# feature/home/build/compose-reports/<SourceSet>-composables.txt
# feature/home/build/compose-reports/<SourceSet>-composables.csv
# feature/home/build/compose-metrics/<SourceSet>-module.json
```

---

## 5. Compiler Report Format

### classes.txt

Each class analyzed by the compiler appears with its stability classification.

```
stable class UserProfile {
  stable val id: String
  stable val name: String
  stable val avatarUrl: String
  <runtime stability> = Stable
}
unstable class FeedState {
  unstable val items: List<FeedItem>
  stable val isLoading: Boolean
  <runtime stability> = Unstable
}
runtime class GenericWrapper {
  runtime val data: T
  <runtime stability> = Parameter(T)
}
```

| Classification | Meaning |
|---------------|---------|
| `stable` | All fields stable. Type is stable. |
| `unstable` | At least one field unstable. Type is unstable. |
| `runtime` | Stability depends on type parameter `T` at runtime. |

### composables.txt

Each Composable function with its restartability and skippability.

```
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun HomeContent(
  stable items: ImmutableList<Item>
  stable isLoading: Boolean
  stable onRefresh: Function0<Unit>
  stable modifier: Modifier? = @static Companion
)
restartable scheme("[androidx.compose.ui.UiComposable]") fun BadComponent(
  unstable data: List<String>
  stable label: String
)
```

| Marker | Meaning |
|--------|---------|
| `restartable skippable` | Can skip when all inputs unchanged. Optimal. |
| `restartable` (no `skippable`) | Cannot skip. Recomposes every time parent does. Fix unstable params. |
| `stable` (param) | Parameter is stable. |
| `unstable` (param) | Parameter is unstable. This is the problem. |
| `@static` | Parameter has a static default value (e.g., `Modifier.Companion`). |

### composables.csv

Machine-parseable format for CI integration.

```csv
name,composable,skippable,restartable,readonly,inline,isLambda,hasDefaults,defaultsGroup,groups,calls,
HomeContent,1,1,1,0,0,0,1,0,1,3,
BadComponent,1,0,1,0,0,0,0,0,1,2,
```

### module.json

```json
{
  "skippableComposables": 42,
  "restartableComposables": 48,
  "readonlyComposables": 3,
  "totalComposables": 51,
  "restartGroups": 48,
  "totalGroups": 85,
  "staticArguments": 22,
  "certainArguments": 30,
  "knownStableArguments": 156,
  "knownUnstableArguments": 6,
  "unknownStableArguments": 0,
  "totalArguments": 162
}
```

Key metric: `skippableComposables / restartableComposables` ratio. Aim for close to 1.0. Any `restartable` without `skippable` needs investigation.

---

## 6. kotlinx.collections.immutable API

### Types

| Type | Mutable Counterpart | Description |
|------|-------------------|-------------|
| `ImmutableList<T>` | `List<T>` | Read-only list guaranteed immutable |
| `ImmutableSet<T>` | `Set<T>` | Read-only set guaranteed immutable |
| `ImmutableMap<K,V>` | `Map<K,V>` | Read-only map guaranteed immutable |
| `PersistentList<T>` | `MutableList<T>` | Immutable list with efficient structural sharing for modifications |
| `PersistentSet<T>` | `MutableSet<T>` | Immutable set with structural sharing |
| `PersistentMap<K,V>` | `MutableMap<K,V>` | Immutable map with structural sharing |

### Conversion Functions

```kotlin
// From standard collections
val immutableList: ImmutableList<T> = list.toImmutableList()
val immutableSet: ImmutableSet<T> = set.toImmutableSet()
val immutableMap: ImmutableMap<K,V> = map.toImmutableMap()

// From standard collections (persistent)
val persistentList: PersistentList<T> = list.toPersistentList()

// Factory functions
val empty: PersistentList<String> = persistentListOf()
val withItems: PersistentList<String> = persistentListOf("a", "b", "c")

// Modification (returns new instance)
val updated: PersistentList<T> = persistentList.add(item)
val removed: PersistentList<T> = persistentList.removeAt(index)
val mutated: PersistentList<T> = persistentList.mutate { it.add(item); it.removeAt(0) }

// Builder for batch modifications
val built: PersistentList<T> = persistentList.builder().apply {
    add(item1)
    add(item2)
    removeAt(0)
}.build()
```

### Version

```toml
# libs.versions.toml
[versions]
kotlinx-collections-immutable = "0.3.8"

[libraries]
kotlinx-collections-immutable = { module = "org.jetbrains.kotlinx:kotlinx-collections-immutable", version.ref = "kotlinx-collections-immutable" }
```

---

## 7. derivedStateOf

Caches a computation that depends on other `State` objects. Re-evaluates only when a tracked dependency changes, not on every recomposition.

```kotlin
fun <T> derivedStateOf(
    policy: SnapshotMutationPolicy<T> = structuralEqualityPolicy(),
    calculation: () -> T
): State<T>
```

### Stability Implications

`derivedStateOf` returns a `State<T>`, which is stable. This means passing a `derivedStateOf` result as a parameter produces a stable parameter, enabling skipping.

```kotlin
// The filteredItems State is stable. Reading .value is snapshot-tracked.
val filteredItems: State<ImmutableList<Item>> = remember {
    derivedStateOf {
        allItems.filter { it.matches(query) }.toImmutableList()
    }
}
```

### When to Use

- Filtering or sorting a large list based on a search query
- Computing a count or summary from frequently-changing state
- Any expensive computation where the result changes less often than the inputs

### When NOT to Use

- Simple property reads (overhead of tracking outweighs benefit)
- Computations that change on every input change (no caching benefit)

---

## 8. Strong Skipping Mode

### Configuration

Strong skipping is enabled by default in Kotlin 2.0+ (K2 Compose compiler plugin). No explicit configuration needed.

To explicitly control it:

```kotlin
composeCompiler {
    featureFlags = setOf(
        ComposeFeatureFlag.StrongSkipping, // Enabled by default
    )
}
```

To disable (not recommended):

```kotlin
composeCompiler {
    featureFlags = setOf(
        ComposeFeatureFlag.StrongSkipping.disabled(),
    )
}
```

### Behavior Changes

| Aspect | Without Strong Skipping | With Strong Skipping (Default K2) |
|--------|------------------------|----------------------------------|
| Unstable params | Composable never skips | Skips if params are `===` equal |
| Lambda params | Must manually `remember` | Auto-remembered with captures as keys |
| Stability annotations | Required for skipping | Still recommended but less critical |
| `List<T>` param | Always recomposes | Skips if same list instance (`===`) |

### Compiler Report Impact

With strong skipping, composables that were previously `restartable` (not skippable) may now appear as `restartable skippable` in reports. However, the skip is based on referential equality for unstable params, which is weaker than structural equality.

---

## 9. Recomposition Debugging Tools

### Layout Inspector (Android Studio)

1. Run > Debug.
2. View > Tool Windows > Layout Inspector.
3. Check "Show Recomposition Counts" in the toolbar.
4. Composables are highlighted by recomposition frequency (blue = low, red = high).

### Recomposition Highlighter Modifier

```kotlin
// Debug-only modifier that flashes a border on recomposition
fun Modifier.recomposeHighlighter(): Modifier = this.then(
    Modifier.drawWithContent {
        drawContent()
        // Visual indicator logic (color changes on each recomposition)
    }
)
```

### Compose Compiler Reports (CI)

```bash
# In CI, generate reports and fail if skippable ratio drops
./gradlew :app:compileKotlin -PcomposeReports
# Parse module.json and assert:
# skippableComposables >= restartableComposables * 0.85
```

---

## 10. Circuit-Specific Stability

### CircuitUiState Stability

Every field in a `CircuitUiState` data class should be stable. The `eventSink: (Event) -> Unit` field is a function type and is always stable.

```kotlin
// All stable
data class State(
    val items: ImmutableList<Item>,
    val query: String,
    val isLoading: Boolean,
    val error: String?,
    val eventSink: (Event) -> Unit,
) : CircuitUiState
```

### Presenter State Production

Convert at the boundary between presenter logic and state emission:

```kotlin
@Composable
override fun present(): MyScreen.State {
    var items by rememberRetained { mutableStateOf(emptyList<Item>()) }
    // ... logic uses List<Item> internally

    return MyScreen.State(
        items = items.toImmutableList(), // Convert at boundary
        // ...
    )
}
```

### Nested State Objects

If `State` contains a nested data class, that class must also be fully stable:

```kotlin
// UNSTABLE: UserInfo has List<String> field
data class UserInfo(val name: String, val roles: List<String>)

data class State(
    val user: UserInfo, // Makes entire State unstable
) : CircuitUiState

// FIX: Make UserInfo stable
@Immutable
data class UserInfo(val name: String, val roles: ImmutableList<String>)
```
