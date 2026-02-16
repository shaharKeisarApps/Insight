# Navigation Expert Examples

Cross-cutting navigation examples covering shared element transitions, adaptive layouts, auth flows, and paradigm comparison. All examples use current API versions: Circuit 0.32.0, Navigation 3 1.0.0-alpha01, Compose 1.10.0, Metro 0.10.1.

For paradigm-specific examples:
- **Circuit Navigator**: [circuit-navigation-expert/examples.md](../circuit-navigation-expert/examples.md)
- **Navigation 3 + ViewModel**: [viewmodel-nav3-expert/examples.md](../viewmodel-nav3-expert/examples.md)

---

## Example 1: Shared Element Transitions -- Circuit

List-to-detail shared element transition using Circuit's `SharedElementTransitionLayout`.

```kotlin
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slack.circuit.backstack.rememberSaveableBackStack
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.foundation.CircuitCompositionLocals
import com.slack.circuit.foundation.NavigableCircuitContent
import com.slack.circuit.foundation.rememberCircuitNavigator
import com.slack.circuit.overlay.ContentWithOverlays
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.sharedelements.LocalAnimatedVisibilityScope
import com.slack.circuit.sharedelements.LocalSharedTransitionScope
import com.slack.circuit.sharedelements.SharedElementTransitionLayout
import coil3.compose.AsyncImage
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import kotlinx.parcelize.Parcelize

// -- App host with shared element support --

@Inject
class SharedElementNavHost(private val circuit: Circuit) {
    @Composable
    fun Content() {
        CircuitCompositionLocals(circuit) {
            SharedElementTransitionLayout {
                ContentWithOverlays {
                    val backStack = rememberSaveableBackStack(ItemListScreen)
                    val navigator = rememberCircuitNavigator(backStack)
                    NavigableCircuitContent(navigator = navigator, backStack = backStack)
                }
            }
        }
    }
}

// -- List screen UI --

@Parcelize data object ItemListScreen : Screen
@Parcelize data class ItemDetailScreen(val itemId: String, val imageUrl: String) : Screen

@CircuitInject(ItemListScreen::class, AppScope::class)
@Composable
fun ItemListUi(state: ItemListScreen.State, modifier: Modifier = Modifier) {
    val sharedTransitionScope = LocalSharedTransitionScope.current ?: return
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current ?: return

    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(state.items, key = { it.id }) { item ->
            with(sharedTransitionScope) {
                ListItem(
                    headlineContent = { Text(item.title) },
                    leadingContent = {
                        AsyncImage(
                            model = item.imageUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(56.dp)
                                .sharedElement(
                                    state = rememberSharedContentState(key = "image-${item.id}"),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                ),
                        )
                    },
                    modifier = Modifier.clickable {
                        state.eventSink(ItemListScreen.Event.OpenItem(item.id, item.imageUrl))
                    },
                )
            }
        }
    }
}

// -- Detail screen UI --

@CircuitInject(ItemDetailScreen::class, AppScope::class)
@Composable
fun ItemDetailUi(state: ItemDetailScreen.State, modifier: Modifier = Modifier) {
    val sharedTransitionScope = LocalSharedTransitionScope.current ?: return
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current ?: return

    Column(modifier = modifier.fillMaxSize()) {
        with(sharedTransitionScope) {
            AsyncImage(
                model = state.imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .sharedElement(
                        state = rememberSharedContentState(key = "image-${state.itemId}"),
                        animatedVisibilityScope = animatedVisibilityScope,
                    ),
            )
        }
        Text(text = state.title, modifier = Modifier.weight(1f))
    }
}
```

---

## Example 2: Shared Element Transitions -- Nav3

List-to-detail shared element transition using Compose foundation's `SharedTransitionLayout` with Nav3.

```kotlin
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import coil3.compose.AsyncImage
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.Assisted
import kotlinx.serialization.Serializable

// -- Routes --

@Serializable data object ItemListRoute : NavKey
@Serializable data class ItemDetailRoute(val itemId: String, val imageUrl: String) : NavKey

// -- App entry --

@Composable
fun SharedElementNav3App(
    listViewModelFactory: ItemListViewModel.Factory,
    detailViewModelFactory: ItemDetailViewModel.Factory,
) {
    val backStack = rememberNavBackStack(ItemListRoute)

    SharedTransitionLayout {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<ItemListRoute> {
                    val vm = viewModel { listViewModelFactory.create() }
                    val items by vm.items.collectAsStateWithLifecycle()
                    ItemListContent(
                        items = items,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this@entry,
                        onItemClick = { id, url ->
                            backStack.add(ItemDetailRoute(id, url))
                        },
                    )
                }
                entry<ItemDetailRoute> { key ->
                    val vm = viewModel { detailViewModelFactory.create(key.itemId) }
                    val state by vm.state.collectAsStateWithLifecycle()
                    ItemDetailContent(
                        itemId = key.itemId,
                        imageUrl = key.imageUrl,
                        title = state.title,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this@entry,
                    )
                }
            },
        )
    }
}

// -- List content --

@Composable
fun ItemListContent(
    items: List<Item>,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onItemClick: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(items, key = { it.id }) { item ->
            with(sharedTransitionScope) {
                ListItem(
                    headlineContent = { Text(item.title) },
                    leadingContent = {
                        AsyncImage(
                            model = item.imageUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(56.dp)
                                .sharedElement(
                                    state = rememberSharedContentState(key = "image-${item.id}"),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                ),
                        )
                    },
                    modifier = Modifier.clickable { onItemClick(item.id, item.imageUrl) },
                )
            }
        }
    }
}

// -- Detail content --

@Composable
fun ItemDetailContent(
    itemId: String,
    imageUrl: String,
    title: String,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        with(sharedTransitionScope) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .sharedElement(
                        state = rememberSharedContentState(key = "image-$itemId"),
                        animatedVisibilityScope = animatedVisibilityScope,
                    ),
            )
        }
        Text(text = title, modifier = Modifier.weight(1f))
    }
}
```

---

## Example 3: Adaptive Layout -- Circuit

WindowSizeClass-driven layout switching between single-pane and two-pane navigation with Circuit.

```kotlin
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slack.circuit.backstack.rememberSaveableBackStack
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.foundation.CircuitContent
import com.slack.circuit.foundation.NavigableCircuitContent
import com.slack.circuit.foundation.onNavEvent
import com.slack.circuit.foundation.rememberCircuitNavigator
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.screen.Screen
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.parcelize.Parcelize

// -- Screens --

@Parcelize data object AdaptiveHomeScreen : Screen {
    data class State(
        val isWideScreen: Boolean,
        val selectedItemId: String?,
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data class SelectItem(val id: String) : Event
        data class UpdateWindowSize(val isWide: Boolean) : Event
    }
}

@Parcelize data class ItemListScreen(val onItemSelected: Boolean = false) : Screen
@Parcelize data class ItemDetailScreen(val itemId: String) : Screen

// -- Presenter --

@AssistedInject
class AdaptiveHomePresenter(
    @Assisted private val navigator: Navigator,
) : Presenter<AdaptiveHomeScreen.State> {

    @CircuitInject(AdaptiveHomeScreen::class, AppScope::class)
    @AssistedFactory
    fun interface Factory {
        fun create(navigator: Navigator): AdaptiveHomePresenter
    }

    @Composable
    override fun present(): AdaptiveHomeScreen.State {
        var isWideScreen by rememberRetained { mutableStateOf(false) }
        var selectedItemId by rememberRetained { mutableStateOf<String?>(null) }

        return AdaptiveHomeScreen.State(
            isWideScreen = isWideScreen,
            selectedItemId = selectedItemId,
        ) { event ->
            when (event) {
                is AdaptiveHomeScreen.Event.SelectItem -> {
                    selectedItemId = event.id
                    if (!isWideScreen) {
                        // Compact: push detail onto main stack
                        navigator.goTo(ItemDetailScreen(event.id))
                    }
                    // Wide: detail pane updates automatically via selectedItemId
                }
                is AdaptiveHomeScreen.Event.UpdateWindowSize -> {
                    isWideScreen = event.isWide
                }
            }
        }
    }
}

// -- UI --

@CircuitInject(AdaptiveHomeScreen::class, AppScope::class)
@Composable
fun AdaptiveHomeUi(state: AdaptiveHomeScreen.State, modifier: Modifier = Modifier) {
    // Detect window size and report to presenter
    val windowSizeClass = calculateWindowSizeClass()
    val isWide = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact
    LaunchedEffect(isWide) {
        state.eventSink(AdaptiveHomeScreen.Event.UpdateWindowSize(isWide))
    }

    if (state.isWideScreen) {
        // Two-pane layout
        Row(modifier = modifier.fillMaxSize()) {
            // Left pane: list (uses CircuitContent with onNavEvent to intercept navigation)
            CircuitContent(
                screen = ItemListScreen(onItemSelected = true),
                modifier = Modifier.width(360.dp),
                onNavEvent = { navEvent ->
                    // Intercept goTo(ItemDetailScreen) to update selection instead
                    val goTo = navEvent as? NavEvent.GoTo
                    val detail = goTo?.screen as? ItemDetailScreen
                    if (detail != null) {
                        state.eventSink(AdaptiveHomeScreen.Event.SelectItem(detail.itemId))
                    }
                },
            )
            // Right pane: detail (has its own nested navigation for drill-down)
            if (state.selectedItemId != null) {
                val detailBackStack = rememberSaveableBackStack(
                    ItemDetailScreen(state.selectedItemId!!)
                )
                val detailNavigator = rememberCircuitNavigator(detailBackStack)
                NavigableCircuitContent(
                    navigator = detailNavigator,
                    backStack = detailBackStack,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    } else {
        // Single-pane: standard navigation handled by parent NavigableCircuitContent
        CircuitContent(
            screen = ItemListScreen(),
            modifier = modifier.fillMaxSize(),
            onNavEvent = { state.eventSink.onNavEvent(it) },
        )
    }
}
```

---

## Example 4: Adaptive Layout -- Nav3

Adaptive navigation using Nav3's built-in `ListDetailSceneStrategy` for automatic two-pane on wide screens.

```kotlin
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.ListDetailSceneStrategy
import androidx.navigation3.ui.NavDisplay
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.Assisted
import kotlinx.serialization.Serializable

// -- Routes --

@Serializable data object CatalogListRoute : NavKey
@Serializable data class CatalogDetailRoute(val productId: String) : NavKey

// -- App entry --

@Composable
fun AdaptiveNav3App(
    listViewModelFactory: CatalogListViewModel.Factory,
    detailViewModelFactory: CatalogDetailViewModel.Factory,
) {
    val backStack = rememberNavBackStack(CatalogListRoute)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        // ListDetailSceneStrategy automatically shows two-pane on wide screens
        // and single-pane on compact screens
        sceneStrategy = ListDetailSceneStrategy(),
        entryProvider = entryProvider {
            entry<CatalogListRoute>(
                // Metadata marks this as the "list" pane for ListDetailSceneStrategy
                metadata = mapOf("role" to "list"),
            ) {
                val vm = viewModel { listViewModelFactory.create() }
                val products by vm.products.collectAsStateWithLifecycle()
                CatalogList(
                    products = products,
                    onProductClick = { id -> backStack.add(CatalogDetailRoute(id)) },
                )
            }
            entry<CatalogDetailRoute>(
                metadata = mapOf("role" to "detail"),
            ) { key ->
                val vm = viewModel { detailViewModelFactory.create(key.productId) }
                val state by vm.state.collectAsStateWithLifecycle()
                CatalogDetail(state = state)
            }
        },
    )
}

@Composable
fun CatalogList(
    products: List<Product>,
    onProductClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(products, key = { it.id }) { product ->
            ListItem(
                headlineContent = { Text(product.name) },
                supportingContent = { Text(product.price) },
                modifier = Modifier.clickable { onProductClick(product.id) },
            )
        }
    }
}
```

---

## Example 5: Auth Flow -- Circuit

Splash screen auth check with graph swapping using `resetRoot`.

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.screen.Screen
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.Inject
import kotlinx.parcelize.Parcelize

// -- Screens --

@Parcelize data object SplashScreen : Screen {
    data class State(val isLoading: Boolean) : CircuitUiState
}

@Parcelize data object LoginScreen : Screen {
    data class State(
        val isSubmitting: Boolean,
        val error: String?,
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data class Submit(val email: String, val password: String) : Event
    }
}

@Parcelize data object HomeScreen : Screen

// -- Auth state manager (shared) --

@Inject
class AuthStateManager(private val tokenStore: TokenStore) {
    suspend fun isAuthenticated(): Boolean {
        val token = tokenStore.getToken() ?: return false
        return !token.isExpired
    }
}

// -- Splash Presenter --

@AssistedInject
class SplashPresenter(
    @Assisted private val navigator: Navigator,
    private val authStateManager: AuthStateManager,
) : Presenter<SplashScreen.State> {

    @CircuitInject(SplashScreen::class, AppScope::class)
    @AssistedFactory
    fun interface Factory {
        fun create(navigator: Navigator): SplashPresenter
    }

    @Composable
    override fun present(): SplashScreen.State {
        var isLoading by rememberRetained { mutableStateOf(true) }

        LaunchedEffect(Unit) {
            val authenticated = authStateManager.isAuthenticated()
            // resetRoot replaces entire stack -- no back navigation to splash
            if (authenticated) {
                navigator.resetRoot(newRoot = HomeScreen)
            } else {
                navigator.resetRoot(newRoot = LoginScreen)
            }
            isLoading = false
        }

        return SplashScreen.State(isLoading = isLoading)
    }
}

// -- Login Presenter --

@AssistedInject
class LoginPresenter(
    @Assisted private val navigator: Navigator,
    private val authService: AuthService,
) : Presenter<LoginScreen.State> {

    @CircuitInject(LoginScreen::class, AppScope::class)
    @AssistedFactory
    fun interface Factory {
        fun create(navigator: Navigator): LoginPresenter
    }

    @Composable
    override fun present(): LoginScreen.State {
        var isSubmitting by rememberRetained { mutableStateOf(false) }
        var error by rememberRetained { mutableStateOf<String?>(null) }

        return LoginScreen.State(
            isSubmitting = isSubmitting,
            error = error,
        ) { event ->
            when (event) {
                is LoginScreen.Event.Submit -> {
                    isSubmitting = true
                    error = null
                    launchInPresenter {
                        val result = authService.login(event.email, event.password)
                        if (result.isSuccess) {
                            // Swap to home graph -- clears login stack entirely
                            navigator.resetRoot(newRoot = HomeScreen)
                        } else {
                            error = result.exceptionOrNull()?.message ?: "Login failed"
                            isSubmitting = false
                        }
                    }
                }
            }
        }
    }
}

// -- Logout (from any screen's Presenter) --
// navigator.resetRoot(newRoot = LoginScreen)
// This clears the entire home stack and all retained state.
```

---

## Example 6: Auth Flow -- Nav3

Splash screen auth check with backStack manipulation and ViewModel-driven state.

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

// -- Routes --

@Serializable data object SplashRoute : NavKey
@Serializable data object LoginRoute : NavKey
@Serializable data object HomeRoute : NavKey

// -- Auth ViewModel --

@Inject
class AuthStateManager(private val tokenStore: TokenStore) {
    suspend fun isAuthenticated(): Boolean {
        val token = tokenStore.getToken() ?: return false
        return !token.isExpired
    }
}

// -- Splash ViewModel --

@AssistedInject
class SplashViewModel(
    private val authStateManager: AuthStateManager,
) : ViewModel() {

    sealed interface Destination {
        data object Loading : Destination
        data object Home : Destination
        data object Login : Destination
    }

    private val _destination = MutableStateFlow<Destination>(Destination.Loading)
    val destination: StateFlow<Destination> = _destination.asStateFlow()

    init {
        viewModelScope.launch {
            _destination.value = if (authStateManager.isAuthenticated()) {
                Destination.Home
            } else {
                Destination.Login
            }
        }
    }

    @AssistedFactory
    fun interface Factory {
        fun create(): SplashViewModel
    }
}

// -- Login ViewModel --

@AssistedInject
class LoginViewModel(
    private val authService: AuthService,
) : ViewModel() {

    data class UiState(
        val isSubmitting: Boolean = false,
        val error: String? = null,
        val loginSuccess: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSubmitting = true, error = null)
            val result = authService.login(email, password)
            if (result.isSuccess) {
                _state.value = _state.value.copy(loginSuccess = true)
            } else {
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    error = result.exceptionOrNull()?.message ?: "Login failed",
                )
            }
        }
    }

    @AssistedFactory
    fun interface Factory {
        fun create(): LoginViewModel
    }
}

// -- App entry --

@Composable
fun AuthNav3App(
    splashViewModelFactory: SplashViewModel.Factory,
    loginViewModelFactory: LoginViewModel.Factory,
) {
    val backStack = rememberNavBackStack(SplashRoute)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<SplashRoute> {
                val vm = viewModel { splashViewModelFactory.create() }
                val destination by vm.destination.collectAsStateWithLifecycle()

                LaunchedEffect(destination) {
                    when (destination) {
                        SplashViewModel.Destination.Home -> {
                            backStack.clear()
                            backStack.add(HomeRoute)
                        }
                        SplashViewModel.Destination.Login -> {
                            backStack.clear()
                            backStack.add(LoginRoute)
                        }
                        SplashViewModel.Destination.Loading -> { /* show loading */ }
                    }
                }
                SplashScreen()
            }
            entry<LoginRoute> {
                val vm = viewModel { loginViewModelFactory.create() }
                val state by vm.state.collectAsStateWithLifecycle()

                LaunchedEffect(state.loginSuccess) {
                    if (state.loginSuccess) {
                        backStack.clear()
                        backStack.add(HomeRoute)
                    }
                }
                LoginScreen(
                    isSubmitting = state.isSubmitting,
                    error = state.error,
                    onSubmit = { email, password -> vm.login(email, password) },
                )
            }
            entry<HomeRoute> {
                HomeScreen(
                    onLogout = {
                        // Swap to login graph -- clears all home state
                        backStack.clear()
                        backStack.add(LoginRoute)
                    },
                )
            }
        },
    )
}
```

---

## Example 7: Paradigm Comparison -- Same Feature, Both Paradigms

The same "navigate to user profile detail" feature implemented side-by-side in Circuit and Nav3, highlighting the mapping between paradigms.

```kotlin
// ============================================================
// CIRCUIT MVI (Circuit 0.32.0 + Metro 0.10.1)
// ============================================================

import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.screen.Screen
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.parcelize.Parcelize

// Route: Screen (Parcelable data class)
@Parcelize
data class UserProfileScreen(val userId: String) : Screen {
    data class State(
        val userName: String,
        val avatarUrl: String,
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data object GoBack : Event
    }
}

// Logic: Presenter with injected Navigator
@AssistedInject
class UserProfilePresenter(
    @Assisted private val screen: UserProfileScreen,
    @Assisted private val navigator: Navigator,
    private val userRepo: UserRepository,
) : Presenter<UserProfileScreen.State> {

    @CircuitInject(UserProfileScreen::class, AppScope::class)
    @AssistedFactory
    fun interface Factory {
        fun create(screen: UserProfileScreen, navigator: Navigator): UserProfilePresenter
    }

    @Composable
    override fun present(): UserProfileScreen.State {
        val user by userRepo.observeUser(screen.userId)
            .collectAsRetainedState(initial = null)

        return UserProfileScreen.State(
            userName = user?.name ?: "",
            avatarUrl = user?.avatarUrl ?: "",
        ) { event ->
            when (event) {
                UserProfileScreen.Event.GoBack -> navigator.pop()
            }
        }
    }
}

// Navigate: navigator.goTo(UserProfileScreen(userId = "123"))
// Pop:      navigator.pop()

// ============================================================
// NAVIGATION 3 + VIEWMODEL (Nav3 1.0.0-alpha01 + Metro 0.10.1)
// ============================================================

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.Assisted
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable

// Route: NavKey (@Serializable data class)
@Serializable
data class UserProfileRoute(val userId: String) : NavKey

// State: Plain data class (no CircuitUiState)
data class UserProfileUiState(
    val userName: String = "",
    val avatarUrl: String = "",
)

// Logic: ViewModel with StateFlow
@AssistedInject
class UserProfileViewModel(
    @Assisted private val userId: String,
    private val userRepo: UserRepository,
) : ViewModel() {

    val state: StateFlow<UserProfileUiState> = userRepo.observeUser(userId)
        .map { user ->
            UserProfileUiState(
                userName = user?.name ?: "",
                avatarUrl = user?.avatarUrl ?: "",
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserProfileUiState())

    @AssistedFactory
    fun interface Factory {
        fun create(userId: String): UserProfileViewModel
    }
}

// Navigate: backStack.add(UserProfileRoute(userId = "123"))
// Pop:      backStack.removeLastOrNull()

// ============================================================
// MAPPING TABLE
// ============================================================

// Circuit                              | Navigation 3
// -------------------------------------|-------------------------------------
// Screen (Parcelable)                  | NavKey (@Serializable)
// Presenter                            | ViewModel
// CircuitUiState                       | Plain data class / StateFlow<T>
// CircuitUiEvent                       | Direct function calls on ViewModel
// navigator.goTo(screen)               | backStack.add(key)
// navigator.pop()                      | backStack.removeLastOrNull()
// navigator.resetRoot(screen)          | backStack.clear(); backStack.add(key)
// navigator.peek()                     | backStack.lastOrNull()
// rememberRetained { }                 | ViewModel property (survives config change)
// collectAsRetainedState()             | collectAsStateWithLifecycle()
// @CircuitInject                       | entry<Route> { } in entryProvider
// NavigableCircuitContent              | NavDisplay
// rememberSaveableBackStack(root)      | rememberNavBackStack(startKey)
// rememberCircuitNavigator(backStack)  | (no navigator object -- use backStack directly)
// PopResult + rememberAnsweringNav     | Shared ViewModel or callback lambda
// CircuitContent + onNavEvent          | (no direct equivalent)
// resetRoot(save=true, restore=true)   | Multiple rememberNavBackStack instances
```
