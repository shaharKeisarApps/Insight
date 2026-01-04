# Advanced Circuit Patterns

## Overlays (Bottom Sheets, Dialogs)

Overlays are transient UI that appears above the current screen without navigation.

### Basic Overlay Usage

```kotlin
@Parcelize
data class ConfirmationOverlay(val message: String) : Overlay<ConfirmationOverlay.Result> {
    @Parcelize
    data class Result(val confirmed: Boolean) : OverlayResult
}

@CircuitInject(ConfirmationOverlay::class, AppScope::class)
@Composable
fun ConfirmationOverlayUi(
    overlay: ConfirmationOverlay,
    navigator: OverlayNavigator<ConfirmationOverlay.Result>,
) {
    AlertDialog(
        onDismissRequest = { navigator.finish(ConfirmationOverlay.Result(false)) },
        text = { Text(overlay.message) },
        confirmButton = {
            TextButton(onClick = { navigator.finish(ConfirmationOverlay.Result(true)) }) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = { navigator.finish(ConfirmationOverlay.Result(false)) }) {
                Text("Cancel")
            }
        }
    )
}
```

### Using Overlays in Presenter

```kotlin
@Composable
fun ItemPresenter(
    navigator: Navigator,
    overlayHost: OverlayHost,
): ItemScreen.State {
    return ItemScreen.State(...) { event ->
        when (event) {
            is DeleteClick -> {
                val result = overlayHost.show(
                    ConfirmationOverlay("Delete this item?")
                )
                if (result.confirmed) {
                    // Perform delete
                }
            }
        }
    }
}
```

### Bottom Sheet Overlay

```kotlin
@Parcelize
data class OptionsBottomSheet(val itemId: String) : BottomSheetOverlay<OptionsBottomSheet, OptionsBottomSheet.Result> {
    @Parcelize
    sealed interface Result : OverlayResult {
        data object Edit : Result
        data object Delete : Result
        data object Share : Result
        data object Dismissed : Result
    }
    
    @Composable
    override fun Content(navigator: OverlayNavigator<Result>) {
        Column(modifier = Modifier.padding(16.dp)) {
            ListItem(
                headlineContent = { Text("Edit") },
                leadingContent = { Icon(Icons.Default.Edit, null) },
                modifier = Modifier.clickable { navigator.finish(Result.Edit) }
            )
            ListItem(
                headlineContent = { Text("Delete") },
                leadingContent = { Icon(Icons.Default.Delete, null) },
                modifier = Modifier.clickable { navigator.finish(Result.Delete) }
            )
            ListItem(
                headlineContent = { Text("Share") },
                leadingContent = { Icon(Icons.Default.Share, null) },
                modifier = Modifier.clickable { navigator.finish(Result.Share) }
            )
        }
    }
}
```

## Nested Navigation (Bottom Nav / Tabs)

### Bottom Navigation with Separate Stacks

```kotlin
@Parcelize
data object MainScreen : Screen {
    data class State(
        val selectedTab: Tab,
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState
    
    enum class Tab { HOME, SEARCH, PROFILE }
    
    sealed interface Event : CircuitUiEvent {
        data class TabSelected(val tab: Tab) : Event
    }
}

@CircuitInject(MainScreen::class, AppScope::class)
@Composable
fun MainPresenter(): MainScreen.State {
    var selectedTab by rememberRetained { mutableStateOf(MainScreen.Tab.HOME) }
    
    return MainScreen.State(selectedTab) { event ->
        when (event) {
            is MainScreen.Event.TabSelected -> selectedTab = event.tab
        }
    }
}

@CircuitInject(MainScreen::class, AppScope::class)
@Composable
fun MainUi(state: MainScreen.State, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                MainScreen.Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = state.selectedTab == tab,
                        onClick = { state.eventSink(MainScreen.Event.TabSelected(tab)) },
                        icon = { Icon(tab.icon, tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        // Each tab has its own navigation stack
        val rootScreen = when (state.selectedTab) {
            MainScreen.Tab.HOME -> HomeScreen
            MainScreen.Tab.SEARCH -> SearchScreen
            MainScreen.Tab.PROFILE -> ProfileScreen
        }
        
        val backStack = rememberSaveableBackStack(root = rootScreen)
        val navigator = rememberCircuitNavigator(backStack)
        
        NavigableCircuitContent(
            navigator = navigator,
            backStack = backStack,
            modifier = Modifier.padding(padding),
        )
    }
}
```

### Preserving Tab State with resetRoot

```kotlin
@Composable
fun MainUi(state: MainScreen.State, modifier: Modifier = Modifier) {
    val homeBackStack = rememberSaveableBackStack(root = HomeScreen)
    val searchBackStack = rememberSaveableBackStack(root = SearchScreen)
    val profileBackStack = rememberSaveableBackStack(root = ProfileScreen)
    
    val currentBackStack = when (state.selectedTab) {
        Tab.HOME -> homeBackStack
        Tab.SEARCH -> searchBackStack
        Tab.PROFILE -> profileBackStack
    }
    
    val navigator = rememberCircuitNavigator(currentBackStack)
    
    Scaffold(
        bottomBar = { /* ... */ }
    ) { padding ->
        NavigableCircuitContent(
            navigator = navigator,
            backStack = currentBackStack,
            modifier = Modifier.padding(padding),
        )
    }
}
```

## Shared Element Transitions

### Setup Shared Elements

```kotlin
@CircuitInject(ListScreen::class, AppScope::class)
@Composable
fun ListUi(state: ListScreen.State, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier) {
        items(state.items) { item ->
            SharedTransitionLayout {
                ListItem(
                    modifier = Modifier
                        .sharedElement(
                            state = rememberSharedContentState(key = "item-${item.id}"),
                            animatedContentScope = this,
                        )
                        .clickable { state.eventSink(ItemClick(item.id)) },
                    headlineContent = { 
                        Text(
                            item.title,
                            modifier = Modifier.sharedElement(
                                state = rememberSharedContentState(key = "title-${item.id}"),
                                animatedContentScope = this,
                            )
                        )
                    }
                )
            }
        }
    }
}

@CircuitInject(DetailScreen::class, AppScope::class)
@Composable
fun DetailUi(state: DetailScreen.State, modifier: Modifier = Modifier) {
    SharedTransitionLayout {
        Column(
            modifier = modifier.sharedElement(
                state = rememberSharedContentState(key = "item-${state.item.id}"),
                animatedContentScope = this,
            )
        ) {
            Text(
                state.item.title,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.sharedElement(
                    state = rememberSharedContentState(key = "title-${state.item.id}"),
                    animatedContentScope = this,
                )
            )
        }
    }
}
```

## CircuitX Effects

### LaunchedEffect with Lifecycle Awareness

```kotlin
// In your presenter - runs only when screen is active
@Composable
fun PresenterWithEffects(): State {
    // ImpressionEffect - fires once when screen becomes visible
    ImpressionEffect {
        analytics.trackScreenView("ProfileScreen")
    }
    
    // RememberImpressionNavigator - track impression with navigation
    val impressionNavigator = rememberImpressionNavigator { screen ->
        analytics.trackImpression(screen::class.simpleName)
    }
    
    return State(...)
}
```

### Lifecycle-Aware Retained State

```kotlin
@Composable
fun LocationPresenter(locationService: LocationService): State {
    val lifecycle = LocalRecordLifecycle.current
    
    var location by rememberRetained { mutableStateOf<Location?>(null) }
    
    DisposableEffect(lifecycle) {
        val listener = LocationListener { location = it }
        
        lifecycle.addObserver(object : RecordLifecycleObserver {
            override fun onResume() {
                locationService.startUpdates(listener)
            }
            override fun onPause() {
                locationService.stopUpdates(listener)
            }
        })
        
        onDispose {
            locationService.stopUpdates(listener)
        }
    }
    
    return State(location)
}
```

## Complex State Management

### Multiple Data Sources

```kotlin
@Composable
fun DashboardPresenter(
    userRepo: UserRepository,
    statsRepo: StatsRepository,
    notificationsRepo: NotificationsRepository,
): DashboardScreen.State {
    
    val user by userRepo.userFlow.collectAsRetainedState()
    val stats by statsRepo.statsFlow.collectAsRetainedState(initial = null)
    val notifications by notificationsRepo.unreadCount.collectAsRetainedState(initial = 0)
    
    // Combine into unified loading state
    val isLoading = user == null || stats == null
    
    return DashboardScreen.State(
        user = user,
        stats = stats,
        unreadNotifications = notifications,
        isLoading = isLoading,
    ) { event ->
        // Handle events
    }
}
```

### Form State with Validation

```kotlin
@Composable
fun ProfileEditPresenter(
    screen: ProfileEditScreen,
    navigator: Navigator,
    userRepo: UserRepository,
): ProfileEditScreen.State {
    
    var name by rememberRetainedSaveable { mutableStateOf(screen.initialName) }
    var email by rememberRetainedSaveable { mutableStateOf(screen.initialEmail) }
    var isSaving by rememberRetained { mutableStateOf(false) }
    var errors by rememberRetained { mutableStateOf(mapOf<String, String>()) }
    
    val isValid = remember(name, email) {
        buildMap {
            if (name.isBlank()) put("name", "Name is required")
            if (!email.contains("@")) put("email", "Invalid email")
        }.also { errors = it }.isEmpty()
    }
    
    return ProfileEditScreen.State(
        name = name,
        email = email,
        errors = errors,
        isValid = isValid,
        isSaving = isSaving,
    ) { event ->
        when (event) {
            is NameChanged -> name = event.value
            is EmailChanged -> email = event.value
            is SaveClick -> if (isValid) {
                isSaving = true
                // Save with external scope
            }
            BackClick -> navigator.pop()
        }
    }
}
```

### Optimistic Updates

```kotlin
@Composable
fun TodoListPresenter(todoRepo: TodoRepository): TodoListScreen.State {
    
    var todos by rememberRetained { mutableStateOf<List<Todo>>(emptyList()) }
    var pendingChanges by rememberRetained { mutableStateOf<Set<String>>(emptySet()) }
    
    // Load initial data
    LaunchedEffect(Unit) {
        todos = todoRepo.getTodos()
    }
    
    return TodoListScreen.State(
        todos = todos,
        pendingChanges = pendingChanges,
    ) { event ->
        when (event) {
            is ToggleTodo -> {
                val todoId = event.id
                
                // Optimistic update
                todos = todos.map { 
                    if (it.id == todoId) it.copy(completed = !it.completed) 
                    else it 
                }
                pendingChanges = pendingChanges + todoId
                
                // Actual update (use external scope for survival)
                appScope.launch {
                    try {
                        todoRepo.toggleTodo(todoId)
                    } catch (e: Exception) {
                        // Revert on failure
                        todos = todos.map {
                            if (it.id == todoId) it.copy(completed = !it.completed)
                            else it
                        }
                    }
                    pendingChanges = pendingChanges - todoId
                }
            }
        }
    }
}
```

## Static Screens

For screens that don't need a presenter (pure UI with no business logic):

```kotlin
@Parcelize
data object AboutScreen : StaticScreen {
    data class State(
        val version: String = BuildConfig.VERSION_NAME,
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState
    
    sealed interface Event : CircuitUiEvent {
        data object BackClick : Event
    }
}

// No presenter needed - UI handles everything
@CircuitInject(AboutScreen::class, AppScope::class)
@Composable
fun AboutUi(state: AboutScreen.State, modifier: Modifier = Modifier) {
    // Pure UI rendering
}
```

## Testing Complex Scenarios

### Testing Async Operations

```kotlin
@Test
fun `save shows loading then navigates on success`() = runTest {
    val fakeRepo = FakeUserRepository()
    val navigator = FakeNavigator(EditScreen)
    
    val presenter = EditPresenter(navigator, fakeRepo, backgroundScope)
    
    presenter.test {
        var state = awaitItem()
        assertThat(state.isSaving).isFalse()
        
        // Trigger save
        state.eventSink(Event.Save)
        
        // Should show loading
        state = awaitItem()
        assertThat(state.isSaving).isTrue()
        
        // Complete the save
        fakeRepo.completePendingSave()
        advanceUntilIdle()
        
        // Should navigate
        assertThat(navigator.awaitPop()).isNotNull()
    }
}
```

### Testing Overlays

```kotlin
@Test
fun `delete shows confirmation then deletes`() = runTest {
    val fakeOverlayHost = FakeOverlayHost()
    val navigator = FakeNavigator(ItemScreen("123"))
    
    fakeOverlayHost.queueResult(ConfirmationOverlay.Result(confirmed = true))
    
    val presenter = ItemPresenter(navigator, fakeOverlayHost, itemRepo)
    
    presenter.test {
        val state = awaitItem()
        state.eventSink(Event.DeleteClick)
        
        // Verify overlay was shown
        assertThat(fakeOverlayHost.awaitOverlay())
            .isInstanceOf(ConfirmationOverlay::class.java)
        
        // Verify delete happened
        assertThat(itemRepo.wasDeleted("123")).isTrue()
    }
}
```

### Testing Navigation Results

```kotlin
@Test
fun `photo selection updates state`() = runTest {
    val navigator = FakeNavigator(ParentScreen)
    
    val presenter = ParentPresenter(navigator)
    
    presenter.test {
        var state = awaitItem()
        assertThat(state.selectedPhoto).isNull()
        
        // Trigger photo picker
        state.eventSink(Event.SelectPhotoClick)
        assertThat(navigator.awaitNextScreen()).isEqualTo(PhotoPickerScreen)
        
        // Simulate return with result
        navigator.pop(PhotoPickerScreen.Result("photo://123"))
        
        state = awaitItem()
        assertThat(state.selectedPhoto).isEqualTo("photo://123")
    }
}
```
