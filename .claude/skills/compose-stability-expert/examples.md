# Compose Stability Examples

> Compose Multiplatform 1.10.0 | Kotlin 2.3.10 | K2 Compose Compiler Plugin | Circuit MVI

---

## 1. Reading Compiler Reports

How to generate, locate, and interpret Compose compiler stability reports.

### Gradle Setup

```kotlin
// build.gradle.kts (convention plugin or module level)
composeCompiler {
    stabilityConfigurationFile =
        project.rootDir.resolve("compose-stability-config.txt")

    // Enable reports (guard behind a property for local builds)
    if (providers.gradleProperty("composeReports").isPresent) {
        reportsDestination = layout.buildDirectory.dir("compose-reports")
        metricsDestination = layout.buildDirectory.dir("compose-metrics")
    }
}
```

### Generate Reports

```bash
# Generate for a specific module
./gradlew :feature:home:compileKotlin -PcomposeReports

# Find the report files
find feature/home/build/compose-reports -name "*.txt"
# Output:
# feature/home/build/compose-reports/commonMain-classes.txt
# feature/home/build/compose-reports/commonMain-composables.txt
```

### Reading classes.txt

```
// GOOD: All fields stable, class is stable
stable class HomeItem {
  stable val id: String
  stable val title: String
  stable val imageUrl: String
  <runtime stability> = Stable
}

// BAD: tags field is unstable, entire class is unstable
unstable class ProfileData {
  stable val name: String
  stable val email: String
  unstable val tags: List<String>
  <runtime stability> = Unstable
}

// PARAMETERIZED: Stability depends on type argument
runtime class Wrapper {
  runtime val data: T
  <runtime stability> = Parameter(T)
}
```

**Action**: Search for `unstable class` in the report. Each unstable class needs one of:
- Fix the unstable field (e.g., `List<T>` to `ImmutableList<T>`)
- Add `@Stable` or `@Immutable` annotation
- Add to stability configuration file

### Reading composables.txt

```
// GOOD: restartable AND skippable
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun HomeContent(
  stable items: ImmutableList<HomeItem>
  stable isLoading: Boolean
  stable onRefresh: Function0<Unit>
  stable modifier: Modifier? = @static Companion
)

// BAD: restartable but NOT skippable
restartable scheme("[androidx.compose.ui.UiComposable]") fun ProfileCard(
  unstable profile: ProfileData
  stable modifier: Modifier? = @static Companion
)
```

**Action**: Search for `restartable` lines that do NOT have `skippable`. Each one has at least one `unstable` parameter to fix.

### Module Metrics Check

```bash
# Parse module.json for CI
cat feature/home/build/compose-metrics/commonMain-module.json | jq '{
  skippable: .skippableComposables,
  restartable: .restartableComposables,
  ratio: (.skippableComposables / .restartableComposables * 100 | round)
}'
# Output: { "skippable": 42, "restartable": 48, "ratio": 88 }
# Target: ratio >= 85
```

---

## 2. Fixing Unstable Data Class (List to ImmutableList)

### Problem

```kotlin
// This data class is UNSTABLE because List<Tag> is unstable
data class ArticleState(
    val title: String,
    val body: String,
    val tags: List<Tag>,           // UNSTABLE
    val relatedIds: List<String>,  // UNSTABLE
    val eventSink: (Event) -> Unit,
) : CircuitUiState

// Compiler report:
// unstable class ArticleState {
//   stable val title: String
//   stable val body: String
//   unstable val tags: List<Tag>       <-- problem
//   unstable val relatedIds: List<String>  <-- problem
//   stable val eventSink: Function1<Event, Unit>
// }
//
// restartable scheme(...) fun ArticleUi(     <-- NOT skippable
//   unstable state: ArticleState
// )
```

Every time the Presenter emits state, `ArticleUi` recomposes even if nothing changed.

### Fix

```kotlin
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

// STABLE: ImmutableList<T> is stable (with stability config entry)
data class ArticleState(
    val title: String,
    val body: String,
    val tags: ImmutableList<Tag>,           // STABLE
    val relatedIds: ImmutableList<String>,  // STABLE
    val eventSink: (Event) -> Unit,
) : CircuitUiState

// In the Presenter, convert at the boundary:
@Composable
override fun present(): ArticleScreen.State {
    var tags by rememberRetained { mutableStateOf(emptyList<Tag>()) }
    var relatedIds by rememberRetained { mutableStateOf(emptyList<String>()) }

    // ... loading logic populates tags and relatedIds as List<T> ...

    return ArticleScreen.State(
        title = article.title,
        body = article.body,
        tags = tags.toImmutableList(),              // Convert here
        relatedIds = relatedIds.toImmutableList(),  // Convert here
        eventSink = { event -> /* ... */ },
    )
}
```

### Stability Config Entry

```
// compose-stability-config.txt
kotlinx.collections.immutable.ImmutableList
kotlinx.collections.immutable.ImmutableSet
kotlinx.collections.immutable.ImmutableMap
kotlinx.collections.immutable.PersistentList
kotlinx.collections.immutable.PersistentSet
kotlinx.collections.immutable.PersistentMap
```

### Compiler Report After Fix

```
stable class ArticleState {
  stable val title: String
  stable val body: String
  stable val tags: ImmutableList<Tag>
  stable val relatedIds: ImmutableList<String>
  stable val eventSink: Function1<Event, Unit>
  <runtime stability> = Stable
}

restartable skippable scheme(...) fun ArticleUi(
  stable state: ArticleState
)
```

---

## 3. Stability Config File for Third-Party Types

### Problem

Your state uses types from external libraries that the compiler cannot analyze:

```kotlin
data class EventState(
    val scheduledAt: Instant,         // from kotlinx.datetime -- UNSTABLE
    val location: LatLng,             // from maps SDK -- UNSTABLE
    val status: HttpStatusCode,       // from Ktor -- UNSTABLE
    val eventSink: (Event) -> Unit,
) : CircuitUiState
```

### Fix: Stability Configuration File

```
// compose-stability-config.txt

// kotlinx.datetime -- immutable value types
kotlinx.datetime.Instant
kotlinx.datetime.LocalDate
kotlinx.datetime.LocalDateTime
kotlinx.datetime.LocalTime
kotlinx.datetime.TimeZone
kotlinx.datetime.DateTimePeriod
kotlinx.datetime.DatePeriod
kotlinx.datetime.UtcOffset

// kotlinx.collections.immutable
kotlinx.collections.immutable.*

// Ktor HTTP types (immutable)
io.ktor.http.Url
io.ktor.http.HttpMethod
io.ktor.http.HttpStatusCode
io.ktor.http.ContentType

// UUID
kotlin.uuid.Uuid

// Google Maps types (immutable data holders)
com.google.android.gms.maps.model.LatLng
com.google.android.gms.maps.model.LatLngBounds

// Project model package (all immutable data classes)
com.myapp.core.model.**

// Circuit Screen types (used as navigation keys)
com.myapp.**.Screen
```

### Gradle Registration

```kotlin
// build-logic/convention/ComposeConventionPlugin.kt
extensions.configure<ComposeCompilerGradlePluginExtension> {
    stabilityConfigurationFile.set(
        rootProject.file("compose-stability-config.txt")
    )
}
```

### Verification

```bash
./gradlew :feature:events:compileKotlin -PcomposeReports

# Check that EventState is now stable
grep -A 10 "class EventState" feature/events/build/compose-reports/commonMain-classes.txt
# Expected: stable class EventState { ... }
```

---

## 4. Lambda Stability Fix in LazyColumn

### Problem

```kotlin
@Composable
fun ItemListUi(state: ItemListScreen.State, modifier: Modifier = Modifier) {
    LazyColumn(modifier) {
        items(
            items = state.items,
            key = { it.id },
        ) { item ->
            ItemCard(
                item = item,
                // PROBLEM: New lambda instance on every recomposition.
                // Without strong skipping, ItemCard cannot skip.
                // With strong skipping, skips only if lambda is === equal
                // (auto-remembered), but if state.eventSink changes,
                // all lambdas are recreated.
                onClick = { state.eventSink(ItemListScreen.Event.ItemClicked(item.id)) },
                onBookmark = { state.eventSink(ItemListScreen.Event.BookmarkClicked(item.id)) },
            )
        }
    }
}
```

### Fix A: Use Circuit Event Pattern (Preferred)

Pass the eventSink lambda directly and let the child composable construct events:

```kotlin
@Composable
fun ItemListUi(state: ItemListScreen.State, modifier: Modifier = Modifier) {
    LazyColumn(modifier) {
        items(
            items = state.items,
            key = { it.id },
        ) { item ->
            // Pass eventSink directly -- it is a stable function type.
            // ItemCard constructs the specific event internally.
            ItemCard(
                item = item,
                eventSink = state.eventSink,
            )
        }
    }
}

@Composable
fun ItemCard(
    item: Item,
    eventSink: (ItemListScreen.Event) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        onClick = { eventSink(ItemListScreen.Event.ItemClicked(item.id)) },
    ) {
        // ...
        IconButton(onClick = { eventSink(ItemListScreen.Event.BookmarkClicked(item.id)) }) {
            Icon(Icons.Default.Bookmark, contentDescription = "Bookmark")
        }
    }
}
```

### Fix B: Remember Lambda with Key

When you cannot pass eventSink directly (e.g., the child composable has a generic onClick):

```kotlin
items(
    items = state.items,
    key = { it.id },
) { item ->
    val onClick = remember(item.id) {
        { state.eventSink(ItemListScreen.Event.ItemClicked(item.id)) }
    }
    GenericCard(
        title = item.title,
        onClick = onClick, // Stable: same instance across recompositions for same item.id
    )
}
```

### Fix C: Strong Skipping (Automatic in K2)

With Kotlin 2.0+ and strong skipping enabled (default), the compiler auto-remembers lambdas:

```kotlin
// The compiler transforms this:
onClick = { state.eventSink(ItemListScreen.Event.ItemClicked(item.id)) }

// Into approximately:
onClick = remember(state.eventSink, item.id) {
    { state.eventSink(ItemListScreen.Event.ItemClicked(item.id)) }
}
```

This works for most cases, but Fix A (Circuit event pattern) is still preferred because:
- It makes the composable signature cleaner
- It avoids per-item lambda allocations entirely
- It is explicit about the event contract

---

## 5. Circuit State Stability

### Problem: Unstable Nested Type in State

```kotlin
// UserInfo uses List<String> -- UNSTABLE
data class UserInfo(
    val name: String,
    val roles: List<String>,  // UNSTABLE
)

@Parcelize
data object ProfileScreen : Screen {
    data class State(
        val user: UserInfo,                     // UNSTABLE (because UserInfo is unstable)
        val posts: List<Post>,                  // UNSTABLE
        val isFollowing: Boolean,
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data object ToggleFollow : Event
        data class PostClicked(val id: String) : Event
    }
}

// Compiler report:
// restartable scheme(...) fun ProfileUi(   <-- NOT skippable
//   unstable state: ProfileScreen.State
// )
```

### Fix: Make All Nested Types Stable

```kotlin
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

// Fix UserInfo: use ImmutableList
@Immutable
data class UserInfo(
    val name: String,
    val roles: ImmutableList<String>,  // STABLE
)

@Parcelize
data object ProfileScreen : Screen {
    data class State(
        val user: UserInfo,                         // STABLE (UserInfo is now stable)
        val posts: ImmutableList<Post>,             // STABLE
        val isFollowing: Boolean,
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data object ToggleFollow : Event
        data class PostClicked(val id: String) : Event
    }
}

// Presenter conversion
@Composable
override fun present(): ProfileScreen.State {
    var user by rememberRetained { mutableStateOf<UserInfo?>(null) }
    var posts by rememberRetained { mutableStateOf(emptyList<Post>()) }
    var isFollowing by rememberRetained { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val profile = repo.getProfile(userId)
        user = UserInfo(
            name = profile.name,
            roles = profile.roles.toImmutableList(), // Convert at boundary
        )
        posts = repo.getPosts(userId)
    }

    return ProfileScreen.State(
        user = user ?: UserInfo("", persistentListOf()),
        posts = posts.toImmutableList(),             // Convert at boundary
        isFollowing = isFollowing,
        eventSink = { event ->
            when (event) {
                ProfileScreen.Event.ToggleFollow -> isFollowing = !isFollowing
                is ProfileScreen.Event.PostClicked -> navigator.goTo(PostDetailScreen(event.id))
            }
        },
    )
}
```

### Compiler Report After Fix

```
stable class UserInfo {
  stable val name: String
  stable val roles: ImmutableList<String>
  <runtime stability> = Stable
}

restartable skippable scheme(...) fun ProfileUi(
  stable state: ProfileScreen.State
)
```

---

## 6. Collection Stability at Boundaries

### Problem: New List Instance on Every Emission

```kotlin
@Composable
override fun present(): SearchScreen.State {
    var query by remember { mutableStateOf("") }
    var allResults by rememberRetained { mutableStateOf(emptyList<SearchResult>()) }

    LaunchedEffect(Unit) {
        snapshotFlow { query }
            .debounce(300)
            .mapLatest { repo.search(it) }
            .collect { allResults = it }
    }

    // PROBLEM: .filter {} creates a NEW list on every recomposition,
    // even if the filtered result is identical.
    // With strong skipping, referential equality (===) fails because
    // it is a new instance.
    val activeResults = allResults.filter { it.isActive }

    return SearchScreen.State(
        query = query,
        results = activeResults.toImmutableList(), // New ImmutableList every time
        eventSink = { /* ... */ },
    )
}
```

### Fix: Use derivedStateOf to Cache Collection Operations

```kotlin
@Composable
override fun present(): SearchScreen.State {
    var query by remember { mutableStateOf("") }
    var allResults by rememberRetained { mutableStateOf(emptyList<SearchResult>()) }

    LaunchedEffect(Unit) {
        snapshotFlow { query }
            .debounce(300)
            .mapLatest { repo.search(it) }
            .collect { allResults = it }
    }

    // FIX: derivedStateOf caches the result. The lambda re-runs only when
    // allResults actually changes (not on every recomposition).
    // The returned ImmutableList is the same instance if allResults hasn't changed.
    val activeResults by remember {
        derivedStateOf {
            allResults.filter { it.isActive }.toImmutableList()
        }
    }

    return SearchScreen.State(
        query = query,
        results = activeResults, // Same instance when allResults unchanged
        eventSink = { event ->
            when (event) {
                is SearchScreen.Event.QueryChanged -> query = event.text
            }
        },
    )
}
```

**Key point**: `derivedStateOf` tracks reads of `allResults`. The filter/conversion only re-runs when `allResults` changes, producing the same `ImmutableList` instance across recompositions where the source did not change.

---

## 7. derivedStateOf for Expensive Computations

### Problem: Expensive Sort on Every Recomposition

```kotlin
@Composable
override fun present(): LeaderboardScreen.State {
    var players by rememberRetained { mutableStateOf(emptyList<Player>()) }
    var sortBy by remember { mutableStateOf(SortField.Score) }
    var ascending by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        players = repo.getPlayers()
    }

    // PROBLEM: Sorting runs on EVERY recomposition, even when
    // only unrelated state (like a timer) changes.
    val sorted = when (sortBy) {
        SortField.Score -> if (ascending) players.sortedBy { it.score }
                          else players.sortedByDescending { it.score }
        SortField.Name -> if (ascending) players.sortedBy { it.name }
                         else players.sortedByDescending { it.name }
        SortField.Rank -> if (ascending) players.sortedBy { it.rank }
                         else players.sortedByDescending { it.rank }
    }

    return LeaderboardScreen.State(
        players = sorted.toImmutableList(), // Expensive + new instance every time
        sortBy = sortBy,
        ascending = ascending,
        eventSink = { /* ... */ },
    )
}
```

### Fix

```kotlin
@Composable
override fun present(): LeaderboardScreen.State {
    var players by rememberRetained { mutableStateOf(emptyList<Player>()) }
    var sortBy by remember { mutableStateOf(SortField.Score) }
    var ascending by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        players = repo.getPlayers()
    }

    // FIX: derivedStateOf tracks reads of players, sortBy, ascending.
    // Sorting re-runs ONLY when one of these three changes.
    val sortedPlayers by remember {
        derivedStateOf {
            val comparator = when (sortBy) {
                SortField.Score -> compareBy<Player> { it.score }
                SortField.Name -> compareBy<Player> { it.name }
                SortField.Rank -> compareBy<Player> { it.rank }
            }.let { if (ascending) it else it.reversed() }

            players.sortedWith(comparator).toImmutableList()
        }
    }

    // Also derived: changes only when sortedPlayers changes
    val topPlayer by remember {
        derivedStateOf { sortedPlayers.firstOrNull() }
    }

    return LeaderboardScreen.State(
        players = sortedPlayers,
        topPlayer = topPlayer,
        sortBy = sortBy,
        ascending = ascending,
        eventSink = { event ->
            when (event) {
                is LeaderboardScreen.Event.SortChanged -> sortBy = event.field
                LeaderboardScreen.Event.ToggleOrder -> ascending = !ascending
            }
        },
    )
}
```

---

## 8. Compiler Metrics Parsing for CI

### Script: Check Skippable Ratio

```bash
#!/bin/bash
# ci/check-compose-stability.sh
# Fails CI if skippable ratio drops below threshold.

THRESHOLD=85  # Percent
MODULES=("feature:home" "feature:profile" "feature:search")

for MODULE in "${MODULES[@]}"; do
    MODULE_PATH="${MODULE//:///}"
    METRICS_FILE="$MODULE_PATH/build/compose-metrics/commonMain-module.json"

    if [ ! -f "$METRICS_FILE" ]; then
        echo "ERROR: Metrics file not found for $MODULE"
        echo "  Expected: $METRICS_FILE"
        echo "  Run: ./gradlew :$MODULE:compileKotlin -PcomposeReports"
        exit 1
    fi

    SKIPPABLE=$(jq '.skippableComposables' "$METRICS_FILE")
    RESTARTABLE=$(jq '.restartableComposables' "$METRICS_FILE")

    if [ "$RESTARTABLE" -eq 0 ]; then
        echo "SKIP: $MODULE has no restartable composables"
        continue
    fi

    RATIO=$(( SKIPPABLE * 100 / RESTARTABLE ))

    if [ "$RATIO" -lt "$THRESHOLD" ]; then
        echo "FAIL: $MODULE skippable ratio is ${RATIO}% (threshold: ${THRESHOLD}%)"
        echo "  Skippable: $SKIPPABLE / Restartable: $RESTARTABLE"
        echo "  Check: $MODULE_PATH/build/compose-reports/commonMain-composables.txt"
        echo "  Look for 'restartable' without 'skippable' and fix unstable params."
        exit 1
    else
        echo "PASS: $MODULE skippable ratio is ${RATIO}% ($SKIPPABLE/$RESTARTABLE)"
    fi
done

echo "All modules pass stability check."
```

### Script: List All Non-Skippable Composables

```bash
#!/bin/bash
# ci/list-unskippable.sh
# Finds all restartable composables that are NOT skippable.

REPORTS_DIR="$1"  # e.g., feature/home/build/compose-reports

if [ -z "$REPORTS_DIR" ]; then
    echo "Usage: $0 <compose-reports-dir>"
    exit 1
fi

echo "=== Non-Skippable Composables ==="
for FILE in "$REPORTS_DIR"/*-composables.txt; do
    # Match lines with "restartable" but NOT "skippable"
    grep -n "^restartable " "$FILE" | grep -v "skippable" | while read -r LINE; do
        echo "$FILE: $LINE"
    done
done
```

### Script: Diff Stability Between Branches

```bash
#!/bin/bash
# ci/stability-diff.sh
# Compares stability metrics between current branch and main.

BRANCH_METRICS="build/compose-metrics/commonMain-module.json"
MAIN_METRICS="/tmp/main-compose-metrics.json"

# Fetch main metrics (generated in a prior CI step)
if [ ! -f "$MAIN_METRICS" ]; then
    echo "No baseline metrics found. Skipping diff."
    exit 0
fi

BRANCH_SKIP=$(jq '.skippableComposables' "$BRANCH_METRICS")
MAIN_SKIP=$(jq '.skippableComposables' "$MAIN_METRICS")
BRANCH_UNSTABLE=$(jq '.knownUnstableArguments' "$BRANCH_METRICS")
MAIN_UNSTABLE=$(jq '.knownUnstableArguments' "$MAIN_METRICS")

SKIP_DIFF=$(( BRANCH_SKIP - MAIN_SKIP ))
UNSTABLE_DIFF=$(( BRANCH_UNSTABLE - MAIN_UNSTABLE ))

echo "Skippable composables: $MAIN_SKIP -> $BRANCH_SKIP (${SKIP_DIFF:+$SKIP_DIFF})"
echo "Unstable arguments: $MAIN_UNSTABLE -> $BRANCH_UNSTABLE (${UNSTABLE_DIFF:+$UNSTABLE_DIFF})"

if [ "$UNSTABLE_DIFF" -gt 0 ]; then
    echo "WARNING: $UNSTABLE_DIFF new unstable arguments introduced."
    exit 1
fi
```

---

## 9. Before/After Recomposition Audit

### Problem: Entire Settings Screen Recomposes on Every Toggle

```kotlin
// BEFORE: Unstable state causes full recomposition

// Settings domain model -- UNSTABLE (List<String>)
data class SettingsData(
    val notifications: Boolean,
    val darkMode: Boolean,
    val language: String,
    val blockedUsers: List<String>,    // UNSTABLE
    val enabledFeatures: List<String>, // UNSTABLE
)

@Parcelize
data object SettingsScreen : Screen {
    data class State(
        val settings: SettingsData,             // UNSTABLE
        val availableLanguages: List<String>,    // UNSTABLE
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data class ToggleNotifications(val enabled: Boolean) : Event
        data class ToggleDarkMode(val enabled: Boolean) : Event
        data class ChangeLanguage(val language: String) : Event
    }
}

// Compiler report:
// restartable scheme(...) fun SettingsUi(  <-- NOT skippable
//   unstable state: SettingsScreen.State
// )
//
// restartable scheme(...) fun NotificationToggle(  <-- NOT skippable
//   unstable settings: SettingsData
// )
//
// restartable scheme(...) fun LanguageSelector(  <-- NOT skippable
//   unstable languages: List<String>
// )
```

When the user toggles "Dark Mode", every composable in the settings tree recomposes: `NotificationToggle`, `LanguageSelector`, `BlockedUsersSection`, etc.

### Fix: Make Everything Stable

```kotlin
// AFTER: All types stable, composables skip correctly

@Immutable
data class SettingsData(
    val notifications: Boolean,
    val darkMode: Boolean,
    val language: String,
    val blockedUsers: ImmutableList<String>,    // STABLE
    val enabledFeatures: ImmutableList<String>, // STABLE
)

@Parcelize
data object SettingsScreen : Screen {
    data class State(
        val settings: SettingsData,                     // STABLE
        val availableLanguages: ImmutableList<String>,   // STABLE
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data class ToggleNotifications(val enabled: Boolean) : Event
        data class ToggleDarkMode(val enabled: Boolean) : Event
        data class ChangeLanguage(val language: String) : Event
    }
}

// UI: Split into focused composables that receive only what they need
@CircuitInject(SettingsScreen::class, AppScope::class)
@Composable
fun SettingsUi(state: SettingsScreen.State, modifier: Modifier = Modifier) {
    Column(modifier.verticalScroll(rememberScrollState())) {
        // Each section receives only the data it needs.
        // When darkMode changes, NotificationToggle SKIPS
        // because its input (notifications Boolean) didn't change.
        NotificationToggle(
            enabled = state.settings.notifications,
            onToggle = { state.eventSink(SettingsScreen.Event.ToggleNotifications(it)) },
        )
        DarkModeToggle(
            enabled = state.settings.darkMode,
            onToggle = { state.eventSink(SettingsScreen.Event.ToggleDarkMode(it)) },
        )
        LanguageSelector(
            selected = state.settings.language,
            available = state.availableLanguages,
            onSelect = { state.eventSink(SettingsScreen.Event.ChangeLanguage(it)) },
        )
    }
}

// Each child composable is now restartable skippable:
// restartable skippable fun NotificationToggle(
//   stable enabled: Boolean
//   stable onToggle: Function1<Boolean, Unit>
// )

@Composable
fun NotificationToggle(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Notifications", modifier = Modifier.weight(1f))
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
fun DarkModeToggle(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Dark Mode", modifier = Modifier.weight(1f))
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
fun LanguageSelector(
    selected: String,
    available: ImmutableList<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Only recomposes when selected or available actually change
    Column(modifier.fillMaxWidth().padding(16.dp)) {
        Text("Language: $selected")
        available.forEach { lang ->
            RadioButton(
                selected = lang == selected,
                onClick = { onSelect(lang) },
            )
        }
    }
}
```

### Result

When the user toggles "Dark Mode":
- `SettingsUi` recomposes (state changed).
- `DarkModeToggle` recomposes (its `enabled` parameter changed).
- `NotificationToggle` **SKIPS** (its `enabled` Boolean did not change).
- `LanguageSelector` **SKIPS** (its `selected` and `available` did not change).

---

## 10. Strong Skipping Mode Behavior

### Understanding What Gets Auto-Remembered

With strong skipping (default in K2), the compiler automatically remembers lambdas.

```kotlin
// ORIGINAL CODE:
@Composable
fun UserCard(user: User, onFollow: () -> Unit) {
    Card {
        Text(user.name)
        Button(onClick = onFollow) { Text("Follow") }
    }
}

// What the compiler generates (approximately):
@Composable
fun UserCard(user: User, onFollow: () -> Unit) {
    // Strong skipping adds referential equality checks:
    // if (user === previousUser && onFollow === previousOnFollow) skip()
    Card {
        Text(user.name)
        Button(onClick = onFollow) { Text("Follow") }
    }
}
```

### When Strong Skipping Helps

```kotlin
@Composable
fun ParentScreen(state: ParentState) {
    // With strong skipping, this lambda is auto-remembered.
    // If state.eventSink hasn't changed (===), the lambda is the same instance.
    val onClick = { state.eventSink(ParentEvent.ButtonClicked) }

    // UserCard can skip if user === previousUser && onClick === previousOnClick
    UserCard(
        user = state.user,
        onClick = onClick,
    )
}
```

### When Strong Skipping Does NOT Help

```kotlin
@Composable
fun ParentScreen(items: List<Item>) {
    // PROBLEM: Even with strong skipping, if the repository returns a new
    // List instance each time (common with Room, network responses),
    // referential equality (===) fails.

    // items: List<Item> is unstable.
    // Strong skipping checks: items === previousItems
    // This is FALSE if the repo returned a new list, even with same contents.
    // Result: ItemList recomposes unnecessarily.
    ItemList(items = items)
}

// FIX: Use ImmutableList and ensure the same instance is reused when contents
// are unchanged. derivedStateOf and toImmutableList() at the Presenter boundary
// solve this because:
// 1. ImmutableList is stable -> structural equality check (==) is used.
// 2. derivedStateOf caches the result -> same instance when unchanged.
```

### Strong Skipping with Lambda Captures

```kotlin
@Composable
fun SearchScreen(state: SearchState) {
    // The compiler auto-remembers this lambda with captures: [state.eventSink, item.id]
    // If either capture changes, a new lambda instance is created (correct behavior).
    LazyColumn {
        items(state.results, key = { it.id }) { item ->
            ResultCard(
                result = item,
                // Auto-remembered: remember(state.eventSink, item.id) { { ... } }
                onClick = { state.eventSink(SearchEvent.ResultClicked(item.id)) },
            )
        }
    }
}
```

### Compiler Report with Strong Skipping

With strong skipping enabled, the compiler report shows composables as `skippable` even when parameters are unstable. The difference is in the skip mechanism:

```
// With strong skipping:
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun UserCard(
  unstable user: User      // Skips via === (referential equality)
  stable onClick: Function0<Unit>  // Skips via == (structural equality)
)

// Without strong skipping, the same composable would be:
restartable scheme("[androidx.compose.ui.UiComposable]") fun UserCard(
  unstable user: User      // Cannot skip at all
  stable onClick: Function0<Unit>
)
```

### Best Practice Summary

| Scenario | Strong Skipping Behavior | Recommendation |
|----------|------------------------|----------------|
| Primitive/String params | Skips via `==` (same as before) | No action needed |
| `ImmutableList<T>` params | Skips via `==` (stable) | Use `ImmutableList` in state |
| `List<T>` params | Skips only if `===` (same instance) | Switch to `ImmutableList` for reliability |
| Lambda params | Auto-remembered | Circuit eventSink pattern is still cleaner |
| Data class with all stable fields | Skips via `==` (inferred stable) | No annotation needed |
| External module type | Skips only if `===` | Add to stability config for `==` behavior |
| `@Stable` class with `mutableStateOf` | Skips via `==` | Annotate correctly |

Strong skipping makes stability less of a cliff (unstable no longer means "never skips"), but proper stability design is still the foundation for predictable, efficient recomposition.
