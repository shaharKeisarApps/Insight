# Migration Guide: ViewModel/MVI to Circuit

## Conceptual Mapping

| ViewModel Concept | Circuit Equivalent |
|-------------------|-------------------|
| `ViewModel` | `Presenter` (Composable function) |
| `StateFlow<UiState>` | Return value of `present()` |
| `viewModelScope` | Compose scope (automatic) or injected `CoroutineScope` |
| `SavedStateHandle` | `rememberRetainedSaveable` |
| `viewModel()` | `CircuitContent(screen)` |
| `NavBackStackEntry` | `BackStack.Record` |
| `MutableLiveData`/`MutableStateFlow` | `rememberRetained { mutableStateOf() }` |

## Common Migrations

### ViewModel with StateFlow → Circuit Presenter

**Before (ViewModel):**
```kotlin
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    
    private val userId = savedStateHandle.get<String>("userId")!!
    
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()
    
    init {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val user = userRepository.getUser(userId)
                _uiState.update { it.copy(user = user, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }
    
    fun onRefresh() {
        // ...
    }
}
```

**After (Circuit):**
```kotlin
@CircuitInject(ProfileScreen::class, AppScope::class)
@Composable
fun ProfilePresenter(
    screen: ProfileScreen,  // Contains userId
    userRepository: UserRepository,
): ProfileScreen.State {
    
    var isLoading by rememberRetained { mutableStateOf(true) }
    var error by rememberRetained { mutableStateOf<String?>(null) }
    
    val user by produceRetainedState<User?>(null, screen.userId) {
        try {
            value = userRepository.getUser(screen.userId)
        } catch (e: Exception) {
            error = e.message
        }
        isLoading = false
    }
    
    return ProfileScreen.State(
        user = user,
        isLoading = isLoading,
        error = error,
    ) { event ->
        when (event) {
            ProfileScreen.Event.Refresh -> {
                // Trigger reload
            }
        }
    }
}
```

### Repository Flow Collection

**Before:**
```kotlin
class ItemListViewModel(private val repo: ItemRepository) : ViewModel() {
    val items = repo.itemsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
```

**After:**
```kotlin
@Composable
fun ItemListPresenter(repo: ItemRepository): State {
    val items by repo.itemsFlow.collectAsRetainedState(initial = emptyList())
    return State(items) { /* events */ }
}
```

### Event Handling

**Before (Intent/Effect pattern):**
```kotlin
class MyViewModel : ViewModel() {
    private val _events = Channel<ViewEvent>()
    val events = _events.receiveAsFlow()
    
    fun onAction(action: Action) {
        when (action) {
            is NavigateToDetail -> {
                viewModelScope.launch {
                    _events.send(ViewEvent.Navigate(action.id))
                }
            }
        }
    }
}

// In Fragment/Activity
lifecycleScope.launch {
    viewModel.events.collect { event ->
        when (event) {
            is ViewEvent.Navigate -> navController.navigate(...)
        }
    }
}
```

**After (Circuit):**
```kotlin
@Composable
fun MyPresenter(navigator: Navigator): State {
    return State(...) { event ->
        when (event) {
            is NavigateToDetail -> navigator.goTo(DetailScreen(event.id))
        }
    }
}
// Navigation is handled directly - no side-effect channels needed!
```

### One-Shot Operations

**Before:**
```kotlin
class LoginViewModel : ViewModel() {
    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    
    fun login(email: String, password: String) {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            try {
                authService.login(email, password)
                _loginState.value = LoginState.Success
            } catch (e: Exception) {
                _loginState.value = LoginState.Error(e.message)
            }
        }
    }
}
```

**After:**
```kotlin
@Composable
fun LoginPresenter(
    authService: AuthService,
    navigator: Navigator,
    appScope: CoroutineScope,  // Injected scope for operations that outlive presenter
): LoginScreen.State {
    
    var loginState by rememberRetained { mutableStateOf<LoginState>(LoginState.Idle) }
    
    return LoginScreen.State(loginState) { event ->
        when (event) {
            is Login -> {
                loginState = LoginState.Loading
                appScope.launch {
                    try {
                        authService.login(event.email, event.password)
                        navigator.resetRoot(HomeScreen)
                    } catch (e: Exception) {
                        loginState = LoginState.Error(e.message)
                    }
                }
            }
        }
    }
}
```

## Lifecycle Differences

### Config Change Survival

| Scenario | ViewModel | Circuit |
|----------|-----------|---------|
| Rotation | ✅ Automatic | ✅ `rememberRetained` |
| Back stack | ❌ Cleared | ✅ `rememberRetained` |
| Process death | ✅ SavedStateHandle | ✅ `rememberRetainedSaveable` |

### Scope Management

**ViewModel**: Operations in `viewModelScope` survive config changes but are cancelled when ViewModel is cleared.

**Circuit**: 
- Operations in Compose scope are tied to composition
- Use `rememberCoroutineScope()` for event-driven operations
- Inject an external `CoroutineScope` for operations that must outlive the presenter

```kotlin
// For operations that should survive navigation away and back
@Composable
fun MyPresenter(
    appScope: CoroutineScope,  // SingleIn(AppScope::class) from Metro
): State {
    return State(...) { event ->
        when (event) {
            is LongRunningTask -> appScope.launch {
                // This continues even if user navigates away
            }
        }
    }
}
```

## Navigation Migration

### NavController → Circuit Navigator

**Before:**
```kotlin
// In Fragment
findNavController().navigate(R.id.detailFragment, bundleOf("id" to itemId))

// With SafeArgs
findNavController().navigate(ListFragmentDirections.toDetail(itemId))
```

**After:**
```kotlin
// In Presenter
navigator.goTo(DetailScreen(itemId))
```

### Deep Linking

**Before:**
```xml
<fragment android:id="@+id/detail">
    <deepLink app:uri="myapp://item/{id}" />
</fragment>
```

**After:**
```kotlin
// In your deep link handler
fun handleDeepLink(uri: Uri): Screen? {
    val pathSegments = uri.pathSegments
    return when {
        pathSegments.firstOrNull() == "item" -> {
            DetailScreen(pathSegments[1])
        }
        else -> null
    }
}

// In Activity
val deepLinkScreen = handleDeepLink(intent.data)
if (deepLinkScreen != null) {
    navigator.resetRoot(deepLinkScreen)
}
```

## Testing Migration

### ViewModel Test → Presenter Test

**Before:**
```kotlin
@Test
fun `load user shows loading then data`() = runTest {
    val viewModel = ProfileViewModel(fakeRepo, SavedStateHandle(mapOf("userId" to "123")))
    
    viewModel.uiState.test {
        assertThat(awaitItem().isLoading).isTrue()
        assertThat(awaitItem().user).isNotNull()
    }
}
```

**After:**
```kotlin
@Test
fun `load user shows loading then data`() = runTest {
    val presenter = ProfilePresenter(
        screen = ProfileScreen("123"),
        userRepository = fakeRepo,
    )
    
    presenter.test {
        assertThat(awaitItem().isLoading).isTrue()
        assertThat(awaitItem().user).isNotNull()
    }
}
```

## Hilt/Dagger → Metro Migration

### AssistedInject Pattern

**Before (Hilt):**
```kotlin
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel()
```

**After (Metro + Circuit):**
```kotlin
// Function-based (simpler)
@CircuitInject(ProfileScreen::class, AppScope::class)
@Composable
fun ProfilePresenter(
    screen: ProfileScreen,  // Assisted
    navigator: Navigator,   // Assisted
    userRepository: UserRepository,  // Injected by Metro
): ProfileScreen.State

// Or class-based with AssistedFactory
class ProfilePresenter @Inject constructor(
    @Assisted private val screen: ProfileScreen,
    @Assisted private val navigator: Navigator,
    private val userRepository: UserRepository,
) : Presenter<ProfileScreen.State> {
    
    @CircuitInject(ProfileScreen::class, AppScope::class)
    @AssistedFactory
    fun interface Factory {
        fun create(screen: ProfileScreen, navigator: Navigator): ProfilePresenter
    }
}
```

## Common Pitfalls

### ❌ Using LaunchedEffect for data loading

```kotlin
// WRONG - won't survive back navigation
@Composable
fun MyPresenter(): State {
    var data by remember { mutableStateOf<Data?>(null) }
    
    LaunchedEffect(Unit) {
        data = repository.getData()  // Lost on back navigation!
    }
}

// RIGHT - use produceRetainedState
@Composable
fun MyPresenter(): State {
    val data by produceRetainedState<Data?>(null) {
        value = repository.getData()
    }
}
```

### ❌ Forgetting to retain mutable state

```kotlin
// WRONG - resets on config change
var count by remember { mutableStateOf(0) }

// RIGHT - survives config change and back navigation
var count by rememberRetained { mutableStateOf(0) }
```

### ❌ Blocking in eventSink

```kotlin
// WRONG - blocks UI thread
return State(...) { event ->
    when (event) {
        is Save -> {
            runBlocking { repository.save() }  // DON'T DO THIS
        }
    }
}

// RIGHT - launch in appropriate scope
return State(...) { event ->
    when (event) {
        is Save -> appScope.launch {
            repository.save()
        }
    }
}
```

## Incremental Migration Strategy

1. **Start with new features** - Build new screens with Circuit
2. **Keep existing navigation** - Use `CircuitContent` embedded in Fragments initially
3. **Migrate screen by screen** - Convert ViewModels to Presenters one at a time
4. **Finally replace navigation** - Switch from Navigation Component to Circuit Navigator

### Embedding Circuit in Existing Fragment

```kotlin
class ProfileFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val circuit = (requireActivity().application as MyApp).circuit
                CircuitCompositionLocals(circuit) {
                    CircuitContent(ProfileScreen(arguments?.getString("userId")!!))
                }
            }
        }
    }
}
```

This allows gradual migration while keeping existing navigation infrastructure.
