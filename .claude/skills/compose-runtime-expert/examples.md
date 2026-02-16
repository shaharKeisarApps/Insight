# Compose Runtime Examples

> Compose Multiplatform 1.10.0 | Kotlin 2.3.10 | Production patterns

---

## 1. snapshotFlow for Reactive Bridging

Convert Compose state into a Kotlin Flow for bridging with non-Compose code.

```kotlin
@Composable
fun SearchPresenter(
    searchRepository: SearchRepository,
): SearchScreen.State {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    // Bridge Compose state -> Flow with debounce
    LaunchedEffect(Unit) {
        snapshotFlow { query }
            .debounce(300)
            .distinctUntilChanged()
            .filter { it.length >= 2 }
            .onEach { isLoading = true }
            .mapLatest { searchText ->
                searchRepository.search(searchText)
            }
            .catch { e ->
                // Handle error, emit empty
                emit(emptyList())
            }
            .collect { searchResults ->
                results = searchResults
                isLoading = false
            }
    }

    return SearchScreen.State(
        query = query,
        results = results,
        isLoading = isLoading,
        eventSink = { event ->
            when (event) {
                is SearchScreen.Event.QueryChanged -> query = event.query
                is SearchScreen.Event.ClearQuery -> {
                    query = ""
                    results = emptyList()
                }
            }
        },
    )
}
```

**Key points:**
- `snapshotFlow` re-evaluates its block whenever any Compose state read inside it changes.
- Integrates naturally with Flow operators (`debounce`, `mapLatest`, `catch`).
- Runs inside `LaunchedEffect(Unit)` so it is scoped to the Composable lifecycle.

---

## 2. derivedStateOf Optimization

Avoid recomputing expensive derived values on every recomposition.

```kotlin
@Composable
fun FilteredListPresenter(
    repository: ItemRepository,
): FilteredListScreen.State {
    var allItems by remember { mutableStateOf<List<Item>>(emptyList()) }
    var filterText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }

    // derivedStateOf caches the result and only re-evaluates when
    // allItems, filterText, or selectedCategory actually change.
    val filteredItems by remember {
        derivedStateOf {
            allItems
                .filter { item ->
                    (selectedCategory == null || item.category == selectedCategory) &&
                        (filterText.isEmpty() || item.name.contains(filterText, ignoreCase = true))
                }
                .sortedBy { it.name }
        }
    }

    // This count also benefits from derivedStateOf —
    // recomputed only when filteredItems changes.
    val resultCount by remember {
        derivedStateOf { filteredItems.size }
    }

    LaunchedEffect(Unit) {
        allItems = repository.getAllItems()
    }

    return FilteredListScreen.State(
        items = filteredItems,
        resultCount = resultCount,
        filterText = filterText,
        selectedCategory = selectedCategory,
        eventSink = { event ->
            when (event) {
                is FilteredListScreen.Event.FilterChanged -> filterText = event.text
                is FilteredListScreen.Event.CategorySelected -> selectedCategory = event.category
            }
        },
    )
}
```

**Key points:**
- `derivedStateOf` tracks which State objects are read inside its lambda.
- The lambda re-executes only when those tracked states change, not on every recomposition.
- Always wrap in `remember` to persist the derived state object across recompositions.
- Chain multiple `derivedStateOf` for cascading derivations.

---

## 3. Custom Stable State Object

Implementing a `@Stable` class with Compose-observable backing fields.

```kotlin
@Stable
class FormState(
    initialName: String = "",
    initialEmail: String = "",
) {
    var name: String by mutableStateOf(initialName)
        private set

    var email: String by mutableStateOf(initialEmail)
        private set

    var nameError: String? by mutableStateOf(null)
        private set

    var emailError: String? by mutableStateOf(null)
        private set

    val isValid: Boolean by derivedStateOf {
        nameError == null && emailError == null &&
            name.isNotBlank() && email.isNotBlank()
    }

    fun updateName(value: String) {
        name = value
        nameError = when {
            value.isBlank() -> "Name is required"
            value.length < 2 -> "Name must be at least 2 characters"
            else -> null
        }
    }

    fun updateEmail(value: String) {
        email = value
        emailError = when {
            value.isBlank() -> "Email is required"
            !value.contains("@") -> "Invalid email format"
            else -> null
        }
    }

    fun reset() {
        name = ""
        email = ""
        nameError = null
        emailError = null
    }
}

// Usage in a Presenter (Circuit pattern)
@Composable
fun ProfileEditPresenter(): ProfileEditScreen.State {
    val formState = remember { FormState() }

    return ProfileEditScreen.State(
        name = formState.name,
        email = formState.email,
        nameError = formState.nameError,
        emailError = formState.emailError,
        isValid = formState.isValid,
        eventSink = { event ->
            when (event) {
                is ProfileEditScreen.Event.NameChanged -> formState.updateName(event.value)
                is ProfileEditScreen.Event.EmailChanged -> formState.updateEmail(event.value)
                is ProfileEditScreen.Event.Reset -> formState.reset()
            }
        },
    )
}
```

**Key points:**
- `@Stable` tells the compiler this class satisfies the stability contract.
- Each field uses `mutableStateOf` delegation, so writes trigger recomposition of readers.
- `derivedStateOf` for `isValid` avoids redundant validation checks.
- Private setters with public mutation methods ensure controlled state changes.
- This pattern works well for form state, timer state, or any complex mutable model.

---

## 4. movableContentOf for Shared Element Transitions

Moving composable content between layout positions without losing state.

```kotlin
@Composable
fun AdaptiveDetailScreen(
    isExpanded: Boolean,
    detailContent: @Composable () -> Unit,
) {
    // movableContentOf preserves state (remember, effects) when
    // the content moves between the two layout branches.
    val movableDetail = remember(detailContent) {
        movableContentOf { detailContent() }
    }

    if (isExpanded) {
        // Two-pane layout: detail on the right
        Row(modifier = Modifier.fillMaxSize()) {
            ListPane(modifier = Modifier.weight(1f))
            Box(modifier = Modifier.weight(2f)) {
                movableDetail()
            }
        }
    } else {
        // Single-pane layout: detail fills screen
        Column(modifier = Modifier.fillMaxSize()) {
            movableDetail()
        }
    }
}

// With parameters
@Composable
fun AnimatedCardContent(
    card: CardData,
    isFullScreen: Boolean,
) {
    val movableCard = remember {
        movableContentOf { data: CardData ->
            // Internal state survives movement between layouts
            var expanded by remember { mutableStateOf(false) }
            Card(onClick = { expanded = !expanded }) {
                Text(data.title)
                if (expanded) {
                    Text(data.description)
                }
            }
        }
    }

    if (isFullScreen) {
        Box(modifier = Modifier.fillMaxSize()) {
            movableCard(card)
        }
    } else {
        Box(modifier = Modifier.size(200.dp)) {
            movableCard(card)
        }
    }
}
```

**Key points:**
- `movableContentOf` wraps a Composable so its internal state (`remember`, effects) persists across tree moves.
- The content identity is tracked by the lambda instance; wrap in `remember` to keep it stable.
- When the content appears in a new call site while still present at the old one (same frame), Compose copies rather than moves.
- Useful for adaptive layouts (compact vs expanded), shared element transitions, and drag-and-drop.

---

## 5. CompositionLocal Provider Pattern

Creating and providing project-wide locals for dependency injection and theming.

```kotlin
// --- Definition ---

// Dynamic: reads are tracked, only readers recompose on change
val LocalAnalytics = compositionLocalOf<AnalyticsTracker> {
    error("No AnalyticsTracker provided")
}

// Static: entire subtree recomposes on change (rare changes only)
val LocalDesignSystem = staticCompositionLocalOf<DesignSystem> {
    error("No DesignSystem provided")
}

// With default value (no error if unprovided)
val LocalFeatureFlags = compositionLocalOf { FeatureFlags() }

// --- Provider at app root ---

@Composable
fun AppRoot(
    analytics: AnalyticsTracker,
    designSystem: DesignSystem,
    featureFlags: FeatureFlags,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAnalytics provides analytics,
        LocalDesignSystem provides designSystem,
        LocalFeatureFlags provides featureFlags,
    ) {
        content()
    }
}

// --- Consumer ---

@Composable
fun TrackableButton(
    eventName: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val analytics = LocalAnalytics.current
    val designSystem = LocalDesignSystem.current

    Button(
        onClick = {
            analytics.track(eventName)
            onClick()
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = designSystem.primaryColor,
        ),
    ) {
        content()
    }
}

// --- Overriding in subtree ---

@Composable
fun PreviewWrapper(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalAnalytics provides NoOpAnalyticsTracker(),
        LocalDesignSystem provides PreviewDesignSystem,
    ) {
        content()
    }
}

// --- Using providesDefault (does not override existing) ---

@Composable
fun LibraryComponent(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalFeatureFlags providesDefault FeatureFlags(experimentalEnabled = false),
    ) {
        content()
    }
}
```

**Key points:**
- Use `compositionLocalOf` for values that change frequently (user session, locale).
- Use `staticCompositionLocalOf` for values that rarely change (design system, DI container).
- `provides` overrides any parent value; `providesDefault` only sets if no parent provides.
- Throwing in the default factory catches missing providers at runtime instead of returning null.
- Circuit's `CircuitCompositionLocals` uses this pattern for `LocalOverlayHost`, `LocalRetainedStateRegistry`.

---

## 6. Side Effect Lifecycle Ordering

Demonstrating the exact execution order of side effects.

```kotlin
@Composable
fun EffectLifecycleDemo(screenId: String) {
    // 1. SideEffect runs AFTER every successful composition commit.
    //    It runs synchronously, cannot suspend, and has no cleanup.
    SideEffect {
        println("SideEffect: composition committed for $screenId")
    }

    // 2. LaunchedEffect starts a coroutine when entering composition.
    //    Cancels and restarts when screenId changes.
    //    Cancels when leaving composition.
    LaunchedEffect(screenId) {
        println("LaunchedEffect: started for $screenId")
        try {
            // Long-running work
            delay(Long.MAX_VALUE)
        } finally {
            println("LaunchedEffect: cancelled for $screenId")
        }
    }

    // 3. DisposableEffect runs its block on enter, onDispose on exit.
    //    When screenId changes: onDispose runs FIRST, then the new block.
    DisposableEffect(screenId) {
        println("DisposableEffect: setup for $screenId")
        val listener = SystemEventListener { event ->
            println("Event received: $event for $screenId")
        }
        SystemEventBus.register(listener)

        onDispose {
            println("DisposableEffect: cleanup for $screenId")
            SystemEventBus.unregister(listener)
        }
    }

    // 4. rememberUpdatedState captures latest value without restarting effect.
    var counter by remember { mutableStateOf(0) }
    val currentCounter by rememberUpdatedState(counter)

    LaunchedEffect(Unit) {
        // This effect never restarts, but always reads the latest counter.
        while (true) {
            delay(1000)
            println("Timer tick, counter = ${currentCounter}")
        }
    }
}

// Execution order on initial composition:
// 1. DisposableEffect: setup for "screen1"
// 2. LaunchedEffect: started for "screen1"
// 3. SideEffect: composition committed for "screen1"
//
// When screenId changes from "screen1" to "screen2":
// 1. DisposableEffect: cleanup for "screen1"
// 2. LaunchedEffect: cancelled for "screen1"
// 3. DisposableEffect: setup for "screen2"
// 4. LaunchedEffect: started for "screen2"
// 5. SideEffect: composition committed for "screen2"
//
// When leaving composition:
// 1. DisposableEffect: cleanup for "screen2"
// 2. LaunchedEffect: cancelled for "screen2"
```

**Key points:**
- `SideEffect` runs after every commit. No keys, no cleanup.
- `LaunchedEffect` is key-based. Cancels on key change or leave.
- `DisposableEffect` guarantees cleanup via `onDispose`.
- `rememberUpdatedState` lets long-running effects read current values without restarting.
- Cleanup order: `onDispose` runs before the new setup block.

---

## 7. Custom Applier (Non-UI Compose Tree)

Using Compose runtime to build a configuration tree instead of UI.

```kotlin
// --- Node types ---

sealed interface ConfigNode {
    val name: String
    val children: MutableList<ConfigNode>
}

data class GroupNode(
    override val name: String,
    override val children: MutableList<ConfigNode> = mutableListOf(),
) : ConfigNode

data class PropertyNode(
    override val name: String,
    val value: Any,
    override val children: MutableList<ConfigNode> = mutableListOf(),
) : ConfigNode

// --- Applier ---

class ConfigApplier(root: ConfigNode) : AbstractApplier<ConfigNode>(root) {
    override fun insertTopDown(index: Int, instance: ConfigNode) {
        // No-op: we use bottom-up insertion
    }

    override fun insertBottomUp(index: Int, instance: ConfigNode) {
        current.children.add(index, instance)
    }

    override fun remove(index: Int, count: Int) {
        current.children.removeRange(index, index + count)
    }

    override fun move(from: Int, to: Int, count: Int) {
        val dest = if (from > to) to else to - count
        val removed = current.children.subList(from, from + count).toList()
        current.children.removeRange(from, from + count)
        current.children.addAll(dest, removed)
    }

    override fun onClear() {
        current.children.clear()
    }
}

// --- DSL Composables ---

@Composable
fun Group(name: String, content: @Composable () -> Unit) {
    ComposeNode<GroupNode, ConfigApplier>(
        factory = { GroupNode(name) },
        update = { set(name) { this.copy(name = it) } },
        content = content,
    )
}

@Composable
fun Property(name: String, value: Any) {
    ComposeNode<PropertyNode, ConfigApplier>(
        factory = { PropertyNode(name, value) },
        update = {
            set(name) { this.copy(name = it) }
            set(value) { this.copy(value = it) }
        },
    )
}

// --- Running the composition ---

suspend fun buildConfig(content: @Composable () -> Unit): ConfigNode {
    val root = GroupNode("root")
    val recomposer = Recomposer(currentCoroutineContext())
    val composition = Composition(ConfigApplier(root), recomposer)

    composition.setContent(content)

    // Run one recomposition frame
    launch { recomposer.runRecomposeAndApplyChanges() }

    // Give composition time to settle
    yield()

    recomposer.close()
    recomposer.join()
    composition.dispose()

    return root
}

// --- Usage ---

suspend fun main() {
    val config = buildConfig {
        Group("server") {
            Property("host", "localhost")
            Property("port", 8080)
            Group("ssl") {
                Property("enabled", true)
                Property("certPath", "/etc/certs/server.pem")
            }
        }
        Group("database") {
            Property("url", "jdbc:postgresql://localhost/mydb")
            Property("poolSize", 10)
        }
    }

    // config is now a tree:
    // root
    //   server
    //     host = localhost
    //     port = 8080
    //     ssl
    //       enabled = true
    //       certPath = /etc/certs/server.pem
    //   database
    //     url = jdbc:postgresql://localhost/mydb
    //     poolSize = 10
}
```

**Key points:**
- `AbstractApplier<T>` manages the tree structure. Override insertion/removal methods.
- Use `ComposeNode` to emit nodes into the custom tree.
- A `Recomposer` drives the composition and must run in a coroutine.
- This pattern powers non-UI use cases: configuration DSLs, test harnesses, document generation.
- Circuit's `Molecule` uses a similar approach with a no-op applier to drive Presenter logic.

---

## 8. Stability Configuration File

Configuring the Compose compiler to treat external types as stable.

### File: `compose-stability-config.txt`

```
// Types from external modules that the compiler cannot analyze.
// Each line is a fully qualified class name or pattern.
// Wildcards: * matches any single segment, ** matches any depth.

// kotlinx-datetime
kotlinx.datetime.Instant
kotlinx.datetime.LocalDate
kotlinx.datetime.LocalDateTime
kotlinx.datetime.LocalTime
kotlinx.datetime.TimeZone

// kotlinx-collections-immutable (all types)
kotlinx.collections.immutable.*

// kotlinx-serialization (common sealed classes)
kotlinx.serialization.json.JsonElement
kotlinx.serialization.json.JsonObject
kotlinx.serialization.json.JsonArray
kotlinx.serialization.json.JsonPrimitive

// Ktor types used in state
io.ktor.http.Url
io.ktor.http.HttpMethod
io.ktor.http.HttpStatusCode

// Project-wide value types
com.example.app.core.model.**

// Circuit screen models
com.example.app.**.Screen
com.example.app.**.Screen.State
```

### Gradle configuration (K2 Compose compiler plugin)

```kotlin
// build.gradle.kts (module level)
composeCompiler {
    stabilityConfigurationFile =
        project.rootDir.resolve("compose-stability-config.txt")

    // Generate stability reports for debugging
    reportsDestination = layout.buildDirectory.dir("compose-reports")

    // Enable metrics for analysis
    metricsDestination = layout.buildDirectory.dir("compose-metrics")
}
```

### Convention plugin pattern

```kotlin
// build-logic/convention/src/main/kotlin/ComposeConventionPlugin.kt
class ComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            extensions.configure<ComposeCompilerGradlePluginExtension> {
                stabilityConfigurationFile.set(
                    rootProject.file("compose-stability-config.txt")
                )
            }
        }
    }
}
```

**Key points:**
- Place `compose-stability-config.txt` at the project root for shared configuration.
- Use `**` to match all subpackages (e.g., `com.example.app.core.model.**`).
- `kotlinx.collections.immutable.*` is a common entry since `ImmutableList<T>` is stable but the compiler cannot verify it from binary.
- Generate reports (`reportsDestination`) to audit stability decisions.
- The K2 compiler plugin replaces the old `kotlinCompilerExtensionVersion` approach.

---

## 9. rememberSaveable with Custom Saver

Saving complex state across configuration changes and process death.

```kotlin
// --- Data class that is NOT Parcelable ---

data class MapViewport(
    val latitude: Double,
    val longitude: Double,
    val zoom: Float,
    val bearing: Float,
    val tilt: Float,
)

// --- Custom Saver using mapSaver ---

val MapViewportSaver = run {
    mapSaver(
        save = { viewport ->
            mapOf(
                "lat" to viewport.latitude,
                "lng" to viewport.longitude,
                "zoom" to viewport.zoom,
                "bearing" to viewport.bearing,
                "tilt" to viewport.tilt,
            )
        },
        restore = { map ->
            MapViewport(
                latitude = map["lat"] as Double,
                longitude = map["lng"] as Double,
                zoom = map["zoom"] as Float,
                bearing = map["bearing"] as Float,
                tilt = map["tilt"] as Float,
            )
        },
    )
}

// --- Custom Saver using listSaver (more compact) ---

val MapViewportListSaver = listSaver(
    save = { viewport ->
        listOf(
            viewport.latitude,
            viewport.longitude,
            viewport.zoom,
            viewport.bearing,
            viewport.tilt,
        )
    },
    restore = { list ->
        MapViewport(
            latitude = list[0] as Double,
            longitude = list[1] as Double,
            zoom = list[2] as Float,
            bearing = list[3] as Float,
            tilt = list[4] as Float,
        )
    },
)

// --- Usage ---

@Composable
fun MapScreen() {
    var viewport by rememberSaveable(stateSaver = MapViewportSaver) {
        mutableStateOf(
            MapViewport(
                latitude = 37.7749,
                longitude = -122.4194,
                zoom = 12f,
                bearing = 0f,
                tilt = 0f,
            )
        )
    }

    // viewport survives rotation and process death
    MapView(
        viewport = viewport,
        onViewportChanged = { newViewport ->
            viewport = newViewport
        },
    )
}

// --- Saver for a sealed class hierarchy ---

sealed interface DialogState {
    data object Hidden : DialogState
    data class Confirmation(val title: String, val message: String) : DialogState
    data class Input(val label: String, val currentValue: String) : DialogState
}

val DialogStateSaver = Saver<DialogState, Any>(
    save = { state ->
        when (state) {
            is DialogState.Hidden -> listOf("hidden")
            is DialogState.Confirmation -> listOf("confirm", state.title, state.message)
            is DialogState.Input -> listOf("input", state.label, state.currentValue)
        }
    },
    restore = { saved ->
        @Suppress("UNCHECKED_CAST")
        val list = saved as List<String>
        when (list[0]) {
            "hidden" -> DialogState.Hidden
            "confirm" -> DialogState.Confirmation(list[1], list[2])
            "input" -> DialogState.Input(list[1], list[2])
            else -> DialogState.Hidden
        }
    },
)
```

**Key points:**
- `mapSaver` is readable but slightly more overhead. `listSaver` is compact.
- Custom `Saver<T, S>` works for sealed class hierarchies using a type discriminator.
- The `S` type must be saveable by the platform (`Bundle`-compatible on Android, serializable in KMP).
- For Circuit: use `rememberRetained` instead of `rememberSaveable` for in-memory retention across config changes. Use `rememberSaveable` only when process death survival is needed.

---

## 10. produceState for Async Loading

Converting a suspend function into Compose state with loading/error handling.

```kotlin
sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Success<T>(val data: T) : LoadState<T>
    data class Error(val message: String, val cause: Throwable? = null) : LoadState<Nothing>
}

@Composable
fun <T> produceLoadState(
    vararg keys: Any?,
    loader: suspend () -> T,
): State<LoadState<T>> {
    return produceState<LoadState<T>>(
        initialValue = LoadState.Loading,
        keys = keys,
    ) {
        value = try {
            LoadState.Success(loader())
        } catch (e: CancellationException) {
            throw e // Never catch CancellationException
        } catch (e: Exception) {
            LoadState.Error(
                message = e.message ?: "Unknown error",
                cause = e,
            )
        }
    }
}

// --- Usage in a Circuit Presenter ---

@CircuitInject(UserProfileScreen::class, AppScope::class)
@AssistedFactory
interface Factory {
    fun create(@Assisted navigator: Navigator): UserProfilePresenter
}

class UserProfilePresenter
@AssistedInject
constructor(
    @Assisted private val navigator: Navigator,
    private val userRepository: UserRepository,
) : Presenter<UserProfileScreen.State> {

    @Composable
    override fun present(): UserProfileScreen.State {
        val userId = remember { navigator.peek()?.let { (it as UserProfileScreen).userId } }

        val userState by produceLoadState(userId) {
            userRepository.getUser(userId ?: error("No user ID"))
        }

        // Refresh capability
        var refreshKey by remember { mutableStateOf(0) }
        val refreshedUserState by produceLoadState(userId, refreshKey) {
            userRepository.getUser(userId ?: error("No user ID"))
        }

        return UserProfileScreen.State(
            userState = refreshedUserState,
            eventSink = { event ->
                when (event) {
                    UserProfileScreen.Event.Refresh -> refreshKey++
                    UserProfileScreen.Event.Back -> navigator.pop()
                }
            },
        )
    }
}

// --- produceState with cleanup (awaitDispose) ---

@Composable
fun locationState(
    locationProvider: LocationProvider,
): State<Location?> {
    return produceState<Location?>(initialValue = null) {
        val callback = object : LocationCallback {
            override fun onLocationUpdate(location: Location) {
                value = location
            }
        }
        locationProvider.requestUpdates(callback)

        awaitDispose {
            locationProvider.removeUpdates(callback)
        }
    }
}
```

**Key points:**
- `produceState` launches a coroutine scoped to the composition.
- Set `value` inside the producer to emit new state.
- Always re-throw `CancellationException` in catch blocks.
- Use `awaitDispose` for callback-based APIs that need cleanup.
- Increment a refresh key to force re-execution of the producer.
- The `LoadState` sealed interface pattern provides type-safe loading/success/error handling.

---

## 11. Snapshot Transaction for Batch Updates

Atomically updating multiple state objects to prevent intermediate recompositions.

```kotlin
class ShoppingCartState {
    var items = mutableStateListOf<CartItem>()
        private set
    var totalPrice by mutableStateOf(0.0)
        private set
    var itemCount by mutableStateOf(0)
        private set
    var lastModified by mutableStateOf<Instant?>(null)
        private set

    fun addItem(item: CartItem) {
        // Without snapshot: each assignment triggers a separate recomposition.
        // With snapshot: all changes are applied atomically.
        Snapshot.withMutableSnapshot {
            items.add(item)
            totalPrice += item.price * item.quantity
            itemCount += item.quantity
            lastModified = Clock.System.now()
        }
    }

    fun clearCart() {
        Snapshot.withMutableSnapshot {
            items.clear()
            totalPrice = 0.0
            itemCount = 0
            lastModified = Clock.System.now()
        }
    }

    fun updateQuantity(itemId: String, newQuantity: Int) {
        Snapshot.withMutableSnapshot {
            val index = items.indexOfFirst { it.id == itemId }
            if (index >= 0) {
                val oldItem = items[index]
                val quantityDiff = newQuantity - oldItem.quantity
                items[index] = oldItem.copy(quantity = newQuantity)
                totalPrice += oldItem.price * quantityDiff
                itemCount += quantityDiff
                lastModified = Clock.System.now()
            }
        }
    }
}
```

**Key points:**
- `Snapshot.withMutableSnapshot` groups multiple state writes into a single atomic commit.
- Readers see either all changes or none; no intermediate states.
- Reduces unnecessary recompositions from individual state changes.
- Throws on write conflict if another snapshot modified the same state concurrently.

---

## 12. Circuit Presenter with Compose Runtime (Full Pattern)

Demonstrates how Circuit leverages Compose runtime for reactive presenters.

```kotlin
// --- Screen definition ---

@Parcelize
data class FeedScreen(val feedType: FeedType) : Screen {
    sealed interface State : CircuitUiState {
        data object Loading : State
        data class Loaded(
            val items: List<FeedItem>,
            val isRefreshing: Boolean,
            val eventSink: (Event) -> Unit,
        ) : State
        data class Error(
            val message: String,
            val eventSink: (Event) -> Unit,
        ) : State
    }

    sealed interface Event : CircuitUiEvent {
        data object Refresh : Event
        data object Retry : Event
        data class ItemClicked(val id: String) : Event
    }
}

// --- Presenter using full Compose runtime ---

class FeedPresenter
@AssistedInject
constructor(
    @Assisted private val screen: FeedScreen,
    @Assisted private val navigator: Navigator,
    private val feedRepository: FeedRepository,
    private val analyticsTracker: AnalyticsTracker,
) : Presenter<FeedScreen.State> {

    @Composable
    override fun present(): FeedScreen.State {
        // mutableStateOf for simple state
        var isRefreshing by remember { mutableStateOf(false) }

        // rememberRetained survives config changes (Circuit API)
        var retryCount by rememberRetained { mutableStateOf(0) }

        // produceState for async loading
        val feedResult by produceState<Result<List<FeedItem>>?>(
            initialValue = null,
            screen.feedType,
            retryCount,
        ) {
            value = runCatching {
                feedRepository.getFeed(screen.feedType)
            }
        }

        // snapshotFlow for analytics
        LaunchedEffect(Unit) {
            snapshotFlow { feedResult }
                .filterNotNull()
                .collect { result ->
                    result.onSuccess { items ->
                        analyticsTracker.trackFeedLoaded(
                            type = screen.feedType,
                            count = items.size,
                        )
                    }
                }
        }

        // SideEffect for non-Compose sync
        SideEffect {
            analyticsTracker.setCurrentScreen("Feed-${screen.feedType}")
        }

        // Build state from Compose runtime primitives
        return when (val result = feedResult) {
            null -> FeedScreen.State.Loading
            else -> result.fold(
                onSuccess = { items ->
                    FeedScreen.State.Loaded(
                        items = items,
                        isRefreshing = isRefreshing,
                        eventSink = { event ->
                            when (event) {
                                FeedScreen.Event.Refresh -> {
                                    isRefreshing = true
                                    retryCount++
                                }
                                FeedScreen.Event.Retry -> retryCount++
                                is FeedScreen.Event.ItemClicked -> {
                                    navigator.goTo(DetailScreen(event.id))
                                }
                            }
                        },
                    )
                },
                onFailure = { error ->
                    FeedScreen.State.Error(
                        message = error.message ?: "Unknown error",
                        eventSink = { event ->
                            when (event) {
                                FeedScreen.Event.Retry -> retryCount++
                                else -> Unit
                            }
                        },
                    )
                },
            )
        }
    }
}
```

**Key points:**
- Circuit Presenters are `@Composable` functions, so all Compose runtime APIs work natively.
- `rememberRetained` (Circuit API) uses `RememberObserver` under the hood.
- `produceState` replaces manual `LaunchedEffect` + `mutableStateOf` for async loading.
- `snapshotFlow` bridges reactive state observation to Flow-based analytics.
- `SideEffect` synchronizes Compose state to external systems on every commit.
- The Presenter returns an immutable `State` sealed interface, making the UI a pure function of state.
