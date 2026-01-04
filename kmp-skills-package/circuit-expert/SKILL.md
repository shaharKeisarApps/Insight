---
name: circuit-expert
description: Elite expertise for Slack's Circuit framework - a Compose-driven architecture for Kotlin/Android apps. Use when creating Circuit screens, presenters, UI components, or navigation; managing state with rememberRetained, collectAsRetainedState, produceRetainedState; integrating with Metro DI or other DI frameworks; writing Circuit tests with FakeNavigator and Turbine; implementing overlays, nested navigation, or shared elements. Triggers on any Circuit-related code generation, architecture decisions, or state management patterns.
---

# Circuit Expert Skill

Elite-level expertise for Slack's Circuit framework with Metro DI integration.

## Core Architecture

Circuit separates presentation from UI using Compose runtime for state management:

```
Screen (Key) → Presenter (Logic) → State → UI (Render)
                    ↑                ↓
                Navigator ← Events (eventSink)
```

**Fundamental Rules:**
1. Presenter and UI never directly access each other - only through State/Events
2. Both Presenter and UI are Composable functions
3. Screens are Parcelable keys that pair Presenter + UI
4. State must implement `CircuitUiState`, events implement `CircuitUiEvent`

## Screen Definition Pattern

```kotlin
@Parcelize
data class ProfileScreen(val userId: String) : Screen {
    
    data class State(
        val user: User?,
        val isLoading: Boolean,
        val error: String?,
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState
    
    sealed interface Event : CircuitUiEvent {
        data object Refresh : Event
        data object BackClick : Event
        data class EditClick(val field: String) : Event
    }
}
```

**Conventions:**
- Screen: `@Parcelize data class/object` implementing `Screen`
- State: Nested `data class State(..., val eventSink: (Event) -> Unit) : CircuitUiState`
- Events: Nested `sealed interface Event : CircuitUiEvent`

## Presenter Patterns

### Function-Based Presenter (Preferred with Metro)

```kotlin
@CircuitInject(ProfileScreen::class, AppScope::class)
@Composable
fun ProfilePresenter(
    screen: ProfileScreen,
    navigator: Navigator,
    userRepository: UserRepository,  // Injected by Metro
): ProfileScreen.State {
    
    var isLoading by rememberRetained { mutableStateOf(true) }
    
    val user by produceRetainedState<User?>(null, screen.userId) {
        value = userRepository.getUser(screen.userId)
        isLoading = false
    }
    
    return ProfileScreen.State(
        user = user,
        isLoading = isLoading,
        error = null,
    ) { event ->
        when (event) {
            ProfileScreen.Event.Refresh -> { /* refresh logic */ }
            ProfileScreen.Event.BackClick -> navigator.pop()
            is ProfileScreen.Event.EditClick -> navigator.goTo(EditScreen(event.field))
        }
    }
}
```

### Class-Based Presenter (For Complex Dependencies)

```kotlin
class ProfilePresenter @Inject constructor(
    @Assisted private val screen: ProfileScreen,
    @Assisted private val navigator: Navigator,
    private val userRepository: UserRepository,
    private val analyticsTracker: AnalyticsTracker,
) : Presenter<ProfileScreen.State> {
    
    @Composable
    override fun present(): ProfileScreen.State {
        // Same implementation as function-based
    }
    
    @CircuitInject(ProfileScreen::class, AppScope::class)
    @AssistedFactory
    fun interface Factory {
        fun create(screen: ProfileScreen, navigator: Navigator): ProfilePresenter
    }
}
```

## State Retention APIs (Critical)

### rememberRetained
Retains value across config changes AND back stack navigation. Use for UI state that shouldn't reset.

```kotlin
// Survives rotation and back navigation, NOT process death
var expandedItems by rememberRetained { mutableStateOf(setOf<String>()) }
var scrollPosition by rememberRetained { mutableStateOf(0) }
```

### rememberRetainedSaveable
Retains AND survives process death. Use for critical user input.

```kotlin
// Survives everything including process death
var searchQuery by rememberRetainedSaveable { mutableStateOf("") }
```

### collectAsRetainedState
Collects Flow into retained State. The key API for repository data.

```kotlin
// For StateFlow (no initial value needed)
val user by userRepository.userFlow.collectAsRetainedState()

// For regular Flow (initial value required)
val items by itemsRepository.itemsFlow.collectAsRetainedState(initial = emptyList())
```

### produceRetainedState
Retained version of produceState. Use for one-shot async loads.

```kotlin
val userData by produceRetainedState<Result<User>?>(null, userId) {
    value = runCatching { userRepository.fetchUser(userId) }
}

// With multiple keys - re-runs when any key changes
val searchResults by produceRetainedState<List<Item>>(emptyList(), query, filters) {
    value = searchRepository.search(query, filters)
}
```

### When to Use Each

| Scenario | API |
|----------|-----|
| Local UI state (expanded, selected) | `rememberRetained` |
| User input that must survive death | `rememberRetainedSaveable` |
| Observing repository Flow | `collectAsRetainedState` |
| One-shot data fetch | `produceRetainedState` |
| Simple derived state | `remember` (no retention needed) |

## UI Pattern

```kotlin
@CircuitInject(ProfileScreen::class, AppScope::class)
@Composable
fun ProfileUi(state: ProfileScreen.State, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    IconButton(onClick = { state.eventSink(ProfileScreen.Event.BackClick) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> LoadingContent(Modifier.padding(padding))
            state.error != null -> ErrorContent(state.error, Modifier.padding(padding))
            state.user != null -> UserContent(state.user, state.eventSink, Modifier.padding(padding))
        }
    }
}
```

**UI Rules:**
- UI receives State and Modifier only
- All user actions go through `state.eventSink(Event)`
- No business logic in UI - only rendering
- Use `modifier` parameter on root composable

## Navigation

### Basic Navigation Setup

```kotlin
@Composable
fun App() {
    val backStack = rememberSaveableBackStack(root = HomeScreen)
    val navigator = rememberCircuitNavigator(backStack) {
        // Called when root is popped - typically exit app
    }
    
    NavigableCircuitContent(
        navigator = navigator,
        backStack = backStack,
        modifier = Modifier.fillMaxSize(),
    )
}
```

### Navigator Operations

```kotlin
// Forward navigation
navigator.goTo(DetailScreen(itemId))

// Back navigation
navigator.pop()

// Pop with result
navigator.pop(result = SelectionResult(selectedId))

// Pop to specific screen
navigator.popUntil { it is HomeScreen }

// Reset entire stack (e.g., after login)
navigator.resetRoot(
    newRoot = MainScreen,
    saveState = false,    // Don't save popped screens
    restoreState = false, // Don't restore if returning
)
```

### Navigation with Results

```kotlin
// In parent presenter
@Composable
fun ParentPresenter(navigator: Navigator): ParentState {
    var selectedPhoto by rememberRetained { mutableStateOf<String?>(null) }
    
    val photoNavigator = rememberAnsweringNavigator<PhotoPickerScreen.Result>(navigator) { result ->
        selectedPhoto = result.photoUri
    }
    
    return ParentState(selectedPhoto) { event ->
        when (event) {
            is SelectPhotoClick -> photoNavigator.goTo(PhotoPickerScreen)
        }
    }
}

// In child screen
@Parcelize
data object PhotoPickerScreen : Screen {
    @Parcelize
    data class Result(val photoUri: String) : PopResult
}

@Composable
fun PhotoPickerPresenter(navigator: Navigator): State {
    return State { event ->
        when (event) {
            is PhotoSelected -> navigator.pop(result = PhotoPickerScreen.Result(event.uri))
        }
    }
}
```

## Metro DI Integration

### Graph Setup

```kotlin
@DependencyGraph(AppScope::class)
@SingleIn(AppScope::class)
interface AppGraph {
    val circuit: Circuit
    
    @Provides
    fun provideCircuit(
        presenterFactories: Set<Presenter.Factory>,
        uiFactories: Set<Ui.Factory>,
    ): Circuit = Circuit.Builder()
        .addPresenterFactories(presenterFactories)
        .addUiFactories(uiFactories)
        .build()
}
```

### Activity Setup

```kotlin
class MainActivity : ComponentActivity() {
    
    @Inject lateinit var circuit: Circuit
    
    override fun onCreate(savedInstanceState: Bundle?) {
        (application as App).appGraph.inject(this)
        super.onCreate(savedInstanceState)
        
        setContent {
            CircuitCompositionLocals(circuit) {
                val backStack = rememberSaveableBackStack(root = HomeScreen)
                val navigator = rememberCircuitNavigator(backStack)
                NavigableCircuitContent(navigator, backStack)
            }
        }
    }
}
```

### Scoping with Metro

```kotlin
// App-level singleton
@SingleIn(AppScope::class)
@Inject
class UserRepository(private val api: Api, private val db: Database)

// Feature-level scope
@SingleIn(ProfileScope::class)
@Inject  
class ProfileCache(private val userRepository: UserRepository)
```

## Testing

### Presenter Testing with Turbine

```kotlin
@Test
fun `loading then shows user`() = runTest {
    val fakeRepo = FakeUserRepository()
    val navigator = FakeNavigator(ProfileScreen("123"))
    
    val presenter = ProfilePresenter(
        screen = ProfileScreen("123"),
        navigator = navigator,
        userRepository = fakeRepo,
    )
    
    presenter.test {
        // Initial loading state
        val loading = awaitItem()
        assertThat(loading.isLoading).isTrue()
        assertThat(loading.user).isNull()
        
        // After data loads
        fakeRepo.emitUser(testUser)
        val loaded = awaitItem()
        assertThat(loaded.isLoading).isFalse()
        assertThat(loaded.user).isEqualTo(testUser)
    }
}
```

### Testing Navigation

```kotlin
@Test
fun `back click pops navigator`() = runTest {
    val navigator = FakeNavigator(ProfileScreen("123"))
    val presenter = ProfilePresenter(screen, navigator, fakeRepo)
    
    presenter.test {
        val state = awaitItem()
        state.eventSink(ProfileScreen.Event.BackClick)
        
        assertThat(navigator.awaitPop()).isNotNull()
    }
}

@Test
fun `edit navigates to edit screen`() = runTest {
    val navigator = FakeNavigator(ProfileScreen("123"))
    val presenter = ProfilePresenter(screen, navigator, fakeRepo)
    
    presenter.test {
        val state = awaitItem()
        state.eventSink(ProfileScreen.Event.EditClick("name"))
        
        assertThat(navigator.awaitNextScreen()).isEqualTo(EditScreen("name"))
    }
}
```

### UI Snapshot Testing

```kotlin
@Test
fun `profile loaded state`() {
    paparazzi.snapshot {
        ProfileUi(
            state = ProfileScreen.State(
                user = testUser,
                isLoading = false,
                error = null,
                eventSink = {},
            )
        )
    }
}
```

## Common Patterns

### Loading/Error/Content States

```kotlin
sealed interface ContentState<out T> {
    data object Loading : ContentState<Nothing>
    data class Error(val message: String) : ContentState<Nothing>
    data class Success<T>(val data: T) : ContentState<T>
}

@Composable
fun <T> ProfilePresenter(/* ... */): State {
    var contentState by rememberRetained<ContentState<User>> { ContentState.Loading }
    
    LaunchedEffect(userId) {
        contentState = ContentState.Loading
        contentState = try {
            ContentState.Success(userRepository.getUser(userId))
        } catch (e: Exception) {
            ContentState.Error(e.message ?: "Unknown error")
        }
    }
    
    return State(contentState) { event ->
        when (event) {
            Event.Retry -> { /* re-trigger LaunchedEffect by changing a key */ }
        }
    }
}
```

### Paging Integration

```kotlin
@Composable
fun ItemListPresenter(repository: ItemRepository): State {
    val lazyPagingItems = repository.pagedItems.collectAsLazyPagingItems()
    
    return State(lazyPagingItems) { event ->
        when (event) {
            Event.Refresh -> lazyPagingItems.refresh()
        }
    }
}
```

### Nested Circuit (Sub-screens)

```kotlin
@Composable
fun ParentUi(state: ParentState, modifier: Modifier) {
    Column(modifier) {
        HeaderSection(state.header)
        
        // Nested Circuit - events forwarded to parent
        CircuitContent(
            screen = ChildScreen(state.childId),
            onNavEvent = { navEvent ->
                state.eventSink(ParentEvent.ChildNav(navEvent))
            }
        )
    }
}
```

### Long-Running Operations

```kotlin
@Composable
fun LoginPresenter(
    authService: AuthService,
    navigator: Navigator,
    appScope: CoroutineScope,  // Injected scope that outlives presenter
): State {
    var isLoading by rememberRetained { mutableStateOf(false) }
    var error by rememberRetained { mutableStateOf<String?>(null) }
    
    return State(isLoading, error) { event ->
        when (event) {
            is Event.Login -> {
                isLoading = true
                appScope.launch {
                    authService.login(event.credentials)
                        .onSuccess { navigator.resetRoot(HomeScreen) }
                        .onFailure { error = it.message }
                    isLoading = false
                }
            }
        }
    }
}
```

## Anti-Patterns to Avoid

❌ **Don't access UI from Presenter**
```kotlin
// WRONG - Presenter should not know about UI
fun present(): State {
    val context = LocalContext.current  // DON'T DO THIS
}
```

❌ **Don't use LaunchedEffect for data loading in Presenter**
```kotlin
// WRONG - Use produceRetainedState instead
LaunchedEffect(Unit) {
    val data = repository.getData()  // Won't be retained!
}
```

❌ **Don't put business logic in eventSink**
```kotlin
// WRONG - Complex logic in lambda
return State(data) { event ->
    when (event) {
        is Save -> {
            // 50 lines of save logic... DON'T DO THIS
        }
    }
}

// RIGHT - Extract to functions
return State(data) { event ->
    when (event) {
        is Save -> handleSave(event.data)
    }
}
```

❌ **Don't forget modifier on UI root**
```kotlin
// WRONG - modifier parameter ignored
@Composable
fun MyUi(state: State, modifier: Modifier = Modifier) {
    Column {  // modifier not applied!
    }
}

// RIGHT
@Composable
fun MyUi(state: State, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
    }
}
```

## References

- Circuit Docs: https://slackhq.github.io/circuit/
- Circuit GitHub: https://github.com/slackhq/circuit
- Metro DI: https://github.com/ZacSweers/metro
- CatchUp (reference app): https://github.com/ZacSweers/CatchUp
- Turbine Testing: https://github.com/cashapp/turbine
