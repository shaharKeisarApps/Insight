# Circuit Navigation Examples (v0.32.0)

Production-ready examples for Circuit Navigator patterns. Uses Metro DI, Circuit 0.32.0, and CatchUp conventions.

---

## Example 1: Complete App Setup (Gold Standard)

Full hierarchy: CircuitCompositionLocals > ContentWithOverlays > SharedElementTransitionLayout > NavigableCircuitContent with GestureNavigationDecorationFactory.

```kotlin
package com.example.app

// imports: Circuit foundation, overlay, shared-elements, gesture-navigation, Metro DI

@DependencyGraph(AppScope::class)
interface AppGraph {
    val circuit: Circuit
    @DependencyGraph.Factory
    fun interface Factory { fun create(): AppGraph }
}

@ContributesTo(AppScope::class)
interface CircuitModule {
    companion object {
        @Provides @SingleIn(AppScope::class)
        fun provideCircuit(
            presenterFactories: Set<Presenter.Factory>,
            uiFactories: Set<Ui.Factory>,
        ): Circuit = Circuit.Builder()
            .addPresenterFactories(presenterFactories)
            .addUiFactories(uiFactories)
            .build()
    }
}

@Composable
fun App(circuit: Circuit, modifier: Modifier = Modifier) {
    CircuitCompositionLocals(circuit) {                      // 1. Provide LocalCircuit
        ContentWithOverlays {                                 // 2. Provide LocalOverlayHost
            SharedElementTransitionLayout {                   // 3. Enable shared elements
                val backStack = rememberSaveableBackStack(HomeScreen)
                val navigator = rememberCircuitNavigator(backStack)
                NavigableCircuitContent(                      // 4. Render navigation
                    navigator = navigator,
                    backStack = backStack,
                    modifier = modifier,
                    decoratorFactory = GestureNavigationDecorationFactory(
                        onBackInvoked = navigator::pop,
                    ),
                )
            }
        }
    }
}
```

**Nesting order matters**: Reversing any layer causes runtime errors.

---

## Example 2: Tab Navigation with Multiple Backstacks

Three tabs with independent back stacks. `resetRoot` with `StateOptions.SaveAndRestore`. Double-tap to pop-to-root.

```kotlin
package com.example.feature.main

// imports: Material3 (Scaffold, NavigationBar, NavigationBarItem, Icon, Text),
//          Circuit (CircuitInject, Navigator, rememberRetained, Screen, Presenter),
//          Metro (AppScope, AssistedInject, AssistedFactory, Assisted)

@Parcelize data object FeedScreen : Screen
@Parcelize data object SearchScreen : Screen
@Parcelize data object ProfileScreen : Screen

enum class Tab(val screen: Screen, val label: String, val icon: ImageVector) {
    Feed(FeedScreen, "Feed", Icons.Default.Home),
    Search(SearchScreen, "Search", Icons.Default.Search),
    Profile(ProfileScreen, "Profile", Icons.Default.Person),
}

@Parcelize
data object MainScreen : Screen {
    data class State(val selectedTab: Tab, val eventSink: (Event) -> Unit = {}) : CircuitUiState
    sealed interface Event : CircuitUiEvent {
        data class SelectTab(val tab: Tab) : Event
    }
}

class MainPresenter @AssistedInject constructor(
    @Assisted private val navigator: Navigator,
) : Presenter<MainScreen.State> {

    @CircuitInject(MainScreen::class, AppScope::class)
    @AssistedFactory
    fun interface Factory { fun create(navigator: Navigator): MainPresenter }

    @Composable
    override fun present(): MainScreen.State {
        var selectedTab by rememberRetained { mutableStateOf(Tab.Feed) }

        return MainScreen.State(selectedTab = selectedTab) { event ->
            when (event) {
                is MainScreen.Event.SelectTab -> {
                    if (event.tab == selectedTab) {
                        navigator.popUntil { false } // Double-tap: pop to root
                    } else {
                        selectedTab = event.tab
                        navigator.resetRoot(
                            newRoot = event.tab.screen,
                            options = Navigator.StateOptions.SaveAndRestore,
                        )
                    }
                }
            }
        }
    }
}

@CircuitInject(MainScreen::class, AppScope::class)
@Composable
fun MainUi(state: MainScreen.State, modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier, bottomBar = {
        NavigationBar {
            Tab.entries.forEach { tab ->
                NavigationBarItem(
                    selected = state.selectedTab == tab,
                    onClick = { state.eventSink(MainScreen.Event.SelectTab(tab)) },
                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                    label = { Text(tab.label) },
                )
            }
        }
    }) { /* Content rendered by outer NavigableCircuitContent via resetRoot */ }
}
```

---

## Example 3: Result Passing with rememberAnsweringNavigator + PopResult

Parent navigates to picker. Picker pops with typed `PopResult`. Parent receives via callback.

```kotlin
package com.example.feature.picker

// imports: Circuit (CircuitInject, rememberAnsweringNavigator, rememberRetained, Navigator,
//          PopResult, Presenter, Screen), Metro (AppScope, AssistedInject, AssistedFactory)

@Parcelize
data object ColorPickerScreen : Screen {
    data class State(val colors: List<ColorOption>, val eventSink: (Event) -> Unit = {}) : CircuitUiState
    sealed interface Event : CircuitUiEvent {
        data class SelectColor(val colorHex: String) : Event
        data object Cancel : Event
    }
    @Parcelize data class Result(val selectedColorHex: String) : PopResult
}

class ColorPickerPresenter @AssistedInject constructor(
    @Assisted private val navigator: Navigator,
) : Presenter<ColorPickerScreen.State> {
    @CircuitInject(ColorPickerScreen::class, AppScope::class)
    @AssistedFactory
    fun interface Factory { fun create(navigator: Navigator): ColorPickerPresenter }

    @Composable
    override fun present(): ColorPickerScreen.State {
        val colors = rememberRetained { listOf(ColorOption("#FF0000", "Red"), ColorOption("#00FF00", "Green")) }
        return ColorPickerScreen.State(colors = colors) { event ->
            when (event) {
                is ColorPickerScreen.Event.SelectColor -> navigator.pop(result = ColorPickerScreen.Result(event.colorHex))
                ColorPickerScreen.Event.Cancel -> navigator.pop()
            }
        }
    }
}

@Parcelize
data object ThemeScreen : Screen {
    data class State(val selectedColor: String?, val eventSink: (Event) -> Unit = {}) : CircuitUiState
    sealed interface Event : CircuitUiEvent { data object PickColor : Event }
}

class ThemePresenter @AssistedInject constructor(
    @Assisted private val navigator: Navigator,
) : Presenter<ThemeScreen.State> {
    @CircuitInject(ThemeScreen::class, AppScope::class)
    @AssistedFactory
    fun interface Factory { fun create(navigator: Navigator): ThemePresenter }

    @Composable
    override fun present(): ThemeScreen.State {
        var selectedColor by rememberRetained { mutableStateOf<String?>(null) }
        // CRITICAL: use rememberAnsweringNavigator, not raw navigator
        val answeringNavigator = rememberAnsweringNavigator<ColorPickerScreen.Result>(navigator) { result ->
            selectedColor = result.selectedColorHex
        }
        return ThemeScreen.State(selectedColor = selectedColor) { event ->
            when (event) {
                ThemeScreen.Event.PickColor -> answeringNavigator.goTo(ColorPickerScreen) // NOT navigator
            }
        }
    }
}
```

**Critical**: Navigate with `answeringNavigator.goTo(...)`, never the raw `navigator`.

---

## Example 4: Nested Navigation Sub-Flow

Checkout wizard with own back stack. `onRootPop` exits the flow.

```kotlin
package com.example.feature.checkout

// imports: Circuit (NavigableCircuitContent, rememberSaveableBackStack, rememberCircuitNavigator,
//          CircuitInject, Navigator, Presenter, Screen), Metro (AppScope, AssistedInject)

@Parcelize data object ShippingScreen : Screen {
    data class State(val eventSink: (Event) -> Unit = {}) : CircuitUiState
    sealed interface Event : CircuitUiEvent { data object Continue : Event }
}
@Parcelize data object PaymentScreen : Screen
@Parcelize data object ConfirmationScreen : Screen

@Parcelize
data object CheckoutFlowScreen : Screen {
    data class State(val eventSink: (Event) -> Unit = {}) : CircuitUiState
    sealed interface Event : CircuitUiEvent { data object FlowComplete : Event }
}

class CheckoutFlowPresenter @AssistedInject constructor(
    @Assisted private val navigator: Navigator,
) : Presenter<CheckoutFlowScreen.State> {
    @CircuitInject(CheckoutFlowScreen::class, AppScope::class)
    @AssistedFactory
    fun interface Factory { fun create(navigator: Navigator): CheckoutFlowPresenter }

    @Composable
    override fun present(): CheckoutFlowScreen.State {
        return CheckoutFlowScreen.State { event ->
            when (event) { CheckoutFlowScreen.Event.FlowComplete -> navigator.pop() }
        }
    }
}

@CircuitInject(CheckoutFlowScreen::class, AppScope::class)
@Composable
fun CheckoutFlowUi(state: CheckoutFlowScreen.State, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        LinearProgressIndicator(modifier = Modifier.padding(16.dp))
        val nestedBackStack = rememberSaveableBackStack(ShippingScreen)
        val nestedNavigator = rememberCircuitNavigator(
            backStack = nestedBackStack,
            onRootPop = { state.eventSink(CheckoutFlowScreen.Event.FlowComplete) },
        )
        NavigableCircuitContent(navigator = nestedNavigator, backStack = nestedBackStack)
    }
}
```

**Key**: Each nested `NavigableCircuitContent` MUST have its own `rememberSaveableBackStack`. `onRootPop` fires when back is pressed on the sub-flow root.

---

## Example 5: Embedded Screen with CircuitContent + onNavEvent

HorizontalPager with CircuitContent per page. Nav events forwarded to parent. CatchUp ServicePager pattern.

```kotlin
package com.example.feature.pager

// imports: Circuit (CircuitContent, NavEvent, onNavEvent, CircuitInject, Navigator, Presenter, Screen),
//          Compose (HorizontalPager, rememberPagerState, ScrollableTabRow, Tab), Metro

@Parcelize data class ServiceScreen(val serviceId: String) : Screen

@Parcelize
data object ServicePagerScreen : Screen {
    data class State(val tabs: ImmutableList<ServiceTab>, val eventSink: (Event) -> Unit = {}) : CircuitUiState
    sealed interface Event : CircuitUiEvent {
        data class ChildNavEvent(val navEvent: NavEvent) : Event
    }
}

class ServicePagerPresenter @AssistedInject constructor(
    @Assisted private val navigator: Navigator,
) : Presenter<ServicePagerScreen.State> {
    @CircuitInject(ServicePagerScreen::class, AppScope::class)
    @AssistedFactory
    fun interface Factory { fun create(navigator: Navigator): ServicePagerPresenter }

    @Composable
    override fun present(): ServicePagerScreen.State {
        val tabs = persistentListOf(ServiceTab("reddit", "Reddit"), ServiceTab("hn", "HN"), ServiceTab("github", "GitHub"))
        return ServicePagerScreen.State(tabs = tabs) { event ->
            when (event) {
                is ServicePagerScreen.Event.ChildNavEvent -> navigator.onNavEvent(event.navEvent)
            }
        }
    }
}

@CircuitInject(ServicePagerScreen::class, AppScope::class)
@Composable
fun ServicePagerUi(state: ServicePagerScreen.State, modifier: Modifier = Modifier) {
    val pagerState = rememberPagerState(pageCount = { state.tabs.size })
    Column(modifier = modifier) {
        ScrollableTabRow(selectedTabIndex = pagerState.currentPage) {
            state.tabs.forEachIndexed { index, tab ->
                Tab(selected = pagerState.currentPage == index,
                    onClick = { /* animateScrollToPage */ }, text = { Text(tab.label) })
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            CircuitContent(
                screen = ServiceScreen(state.tabs[page].id),
                onNavEvent = { state.eventSink(ServicePagerScreen.Event.ChildNavEvent(it)) },
            )
        }
    }
}
```

---

## Example 6: Auth Flow with resetRoot

Login gate. `StateOptions.Default` for clean slate on auth transitions.

```kotlin
package com.example.feature.auth

// imports: Circuit (CircuitInject, rememberRetained, Navigator, Presenter, Screen), Metro

@Parcelize data object SplashScreen : Screen {
    data class State(val isLoading: Boolean) : CircuitUiState
}

class SplashPresenter @AssistedInject constructor(
    @Assisted private val navigator: Navigator,
    private val authRepo: AuthRepository,
) : Presenter<SplashScreen.State> {
    @CircuitInject(SplashScreen::class, AppScope::class)
    @AssistedFactory
    fun interface Factory { fun create(navigator: Navigator): SplashPresenter }

    @Composable
    override fun present(): SplashScreen.State {
        var isLoading by rememberRetained { mutableStateOf(true) }
        LaunchedEffect(Unit) {
            val target = if (authRepo.isLoggedIn()) HomeScreen else LoginScreen
            navigator.resetRoot(newRoot = target, options = Navigator.StateOptions.Default)
            isLoading = false
        }
        return SplashScreen.State(isLoading = isLoading)
    }
}

@Parcelize data object LoginScreen : Screen {
    data class State(val isAuthenticating: Boolean, val error: String?, val eventSink: (Event) -> Unit = {}) : CircuitUiState
    sealed interface Event : CircuitUiEvent { data class Login(val email: String, val password: String) : Event }
}

class LoginPresenter @AssistedInject constructor(
    @Assisted private val navigator: Navigator,
    private val authRepo: AuthRepository,
) : Presenter<LoginScreen.State> {
    @CircuitInject(LoginScreen::class, AppScope::class)
    @AssistedFactory
    fun interface Factory { fun create(navigator: Navigator): LoginPresenter }

    @Composable
    override fun present(): LoginScreen.State {
        var isAuthenticating by rememberRetained { mutableStateOf(false) }
        var error by rememberRetained { mutableStateOf<String?>(null) }
        return LoginScreen.State(isAuthenticating, error) { event ->
            when (event) {
                is LoginScreen.Event.Login -> {
                    isAuthenticating = true
                    // After successful login:
                    navigator.resetRoot(newRoot = HomeScreen, options = Navigator.StateOptions.Default)
                }
            }
        }
    }
}
// Logout: navigator.resetRoot(newRoot = LoginScreen, options = Navigator.StateOptions.Default)
```

**Key**: Auth flows use `StateOptions.Default` -- clean slate, no save/restore.

---

## Example 7: Navigation Interception

Analytics listener + auth guard interceptor + rate limiter. Wired with `rememberInterceptingNavigator`.

```kotlin
package com.example.navigation

// imports: circuitx-navigation (NavigationInterceptor, InterceptedGoToResult, NavigationEventListener,
//          rememberInterceptingNavigator, NavigationContext), Metro (Inject, SingleIn)

@Inject @SingleIn(AppScope::class)
class AnalyticsListener(private val analytics: AnalyticsTracker) : NavigationEventListener {
    override fun onGoTo(screen: Screen) { analytics.trackScreenView(screen::class.simpleName ?: "Unknown") }
    override fun onPop(result: PopResult?, poppedScreen: Screen?) { analytics.trackBack(poppedScreen?.let { it::class.simpleName } ?: "Unknown") }
}

@Inject @SingleIn(AppScope::class)
class AuthGuardInterceptor(private val authRepo: AuthRepository) : NavigationInterceptor {
    private val protectedScreens = setOf("ProfileScreen", "SettingsScreen")
    override fun interceptGoTo(screen: Screen, context: NavigationContext): InterceptedGoToResult {
        val name = screen::class.simpleName ?: return InterceptedGoToResult.Skipped
        if (name in protectedScreens && !authRepo.isLoggedInSync()) return InterceptedGoToResult.Rewrite(LoginScreen)
        return InterceptedGoToResult.Skipped
    }
}

@Inject @SingleIn(AppScope::class)
class RateLimitInterceptor : NavigationInterceptor {
    private var lastGoToTime = 0L
    override fun interceptGoTo(screen: Screen, context: NavigationContext): InterceptedGoToResult {
        val now = System.currentTimeMillis()
        if (now - lastGoToTime < 300L) return InterceptedGoToResult.Consumed
        lastGoToTime = now
        return InterceptedGoToResult.Skipped
    }
}

@Composable
fun AppWithInterception(circuit: Circuit, authGuard: AuthGuardInterceptor, rateLimiter: RateLimitInterceptor, analyticsListener: AnalyticsListener) {
    CircuitCompositionLocals(circuit) {
        ContentWithOverlays {
            val backStack = rememberSaveableBackStack(HomeScreen)
            val rawNavigator = rememberCircuitNavigator(backStack)
            val navigator = rememberInterceptingNavigator(
                navigator = rawNavigator,
                interceptors = listOf(rateLimiter, authGuard),
                listeners = listOf(analyticsListener),
            )
            NavigableCircuitContent(navigator = navigator, backStack = backStack)
        }
    }
}
```

**Key**: Interceptors checked in order. `Rewrite` = redirect, `Consumed` = swallow, `Skipped` = pass through.

---

## Example 8: Shared Element Transitions

List with images to detail with hero image. Matching `rememberSharedContentState` keys.

```kotlin
package com.example.feature.gallery

// imports: Compose animation (SharedTransitionScope, AnimatedVisibilityScope, sharedElement,
//          rememberSharedContentState), Coil (AsyncImage), Circuit, Metro

@Parcelize data object GalleryScreen : Screen {
    data class State(val photos: ImmutableList<Photo>, val eventSink: (Event) -> Unit = {}) : CircuitUiState
    sealed interface Event : CircuitUiEvent { data class PhotoClicked(val photo: Photo) : Event }
}
@Parcelize data class PhotoDetailScreen(val photoId: String, val imageUrl: String) : Screen

@CircuitInject(GalleryScreen::class, AppScope::class)
@Composable
fun GalleryUi(state: GalleryScreen.State, modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null, animatedVisibilityScope: AnimatedVisibilityScope? = null) {
    LazyColumn(modifier = modifier) {
        items(state.photos, key = { it.id }) { photo ->
            Card(modifier = Modifier.fillMaxWidth().padding(8.dp)
                .clickable { state.eventSink(GalleryScreen.Event.PhotoClicked(photo)) }) {
                AsyncImage(
                    model = photo.imageUrl, contentDescription = photo.title,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).then(
                        if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                            with(sharedTransitionScope) {
                                Modifier.sharedElement(
                                    state = rememberSharedContentState(key = "photo-${photo.id}"),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                )
                            }
                        } else Modifier
                    ),
                )
            }
        }
    }
}

// Detail screen uses same key: rememberSharedContentState(key = "photo-${state.photoId}")
```

**Key**: `rememberSharedContentState` keys MUST match between source and target. `SharedElementTransitionLayout` must wrap navigation at app level.

---

## Example 9: Adaptive Two-Pane Layout

WindowSizeClass detection. Compact: single-pane. Expanded: list + detail with `CircuitContent` intercept.

```kotlin
package com.example.feature.adaptive

// imports: WindowWidthSizeClass, Circuit (CircuitContent, NavEvent, onNavEvent, rememberRetained), Metro

@Parcelize
data object AdaptiveInboxScreen : Screen {
    data class State(val selectedMessageId: String?, val isCompact: Boolean, val eventSink: (Event) -> Unit = {}) : CircuitUiState
    sealed interface Event : CircuitUiEvent {
        data class SelectMessage(val messageId: String) : Event
        data class ChildNavEvent(val navEvent: NavEvent) : Event
    }
}

class AdaptiveInboxPresenter @AssistedInject constructor(
    @Assisted private val navigator: Navigator,
) : Presenter<AdaptiveInboxScreen.State> {
    @CircuitInject(AdaptiveInboxScreen::class, AppScope::class)
    @AssistedFactory
    fun interface Factory { fun create(navigator: Navigator): AdaptiveInboxPresenter }

    @Composable
    override fun present(): AdaptiveInboxScreen.State {
        var selectedMessageId by rememberRetained { mutableStateOf<String?>(null) }
        val isCompact = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.COMPACT
        return AdaptiveInboxScreen.State(selectedMessageId, isCompact) { event ->
            when (event) {
                is AdaptiveInboxScreen.Event.SelectMessage ->
                    if (isCompact) navigator.goTo(MessageDetailScreen(event.messageId))
                    else selectedMessageId = event.messageId
                is AdaptiveInboxScreen.Event.ChildNavEvent -> navigator.onNavEvent(event.navEvent)
            }
        }
    }
}

@CircuitInject(AdaptiveInboxScreen::class, AppScope::class)
@Composable
fun AdaptiveInboxUi(state: AdaptiveInboxScreen.State, modifier: Modifier = Modifier) {
    if (state.isCompact) {
        CircuitContent(screen = InboxScreen, onNavEvent = { state.eventSink(AdaptiveInboxScreen.Event.ChildNavEvent(it)) }, modifier = modifier)
    } else {
        Row(modifier = modifier.fillMaxSize()) {
            CircuitContent(screen = InboxScreen, onNavEvent = { navEvent ->
                when (navEvent) {
                    is NavEvent.GoTo -> if (navEvent.screen is MessageDetailScreen) state.eventSink(AdaptiveInboxScreen.Event.SelectMessage((navEvent.screen as MessageDetailScreen).messageId))
                        else state.eventSink(AdaptiveInboxScreen.Event.ChildNavEvent(navEvent))
                    else -> state.eventSink(AdaptiveInboxScreen.Event.ChildNavEvent(navEvent))
                }
            }, modifier = Modifier.width(360.dp).fillMaxHeight())
            state.selectedMessageId?.let { CircuitContent(screen = MessageDetailScreen(it), onNavEvent = { state.eventSink(AdaptiveInboxScreen.Event.ChildNavEvent(it)) }, modifier = Modifier.weight(1f)) }
        }
    }
}
```

---

## Example 10: Gesture Navigation Cross-Platform

```kotlin
package com.example.app

// imports: Circuit foundation, overlay, circuitx-gesture-navigation

@Composable
fun GestureEnabledApp(circuit: Circuit, modifier: Modifier = Modifier) {
    CircuitCompositionLocals(circuit) {
        ContentWithOverlays {
            val backStack = rememberSaveableBackStack(HomeScreen)
            val navigator = rememberCircuitNavigator(backStack)
            NavigableCircuitContent(
                navigator = navigator, backStack = backStack, modifier = modifier,
                decoratorFactory = GestureNavigationDecorationFactory(
                    onBackInvoked = navigator::pop,
                    fallback = AnimatedNavDecorator.Factory.DefaultDecoratorFactory,
                ),
            )
        }
    }
}
// Android 34+: Predictive back. iOS: Swipe-to-pop. Desktop/Web: Animated fallback.
```

---

## Example 11: BackStackRecordLocalProvider

Per-entry CompositionLocals scoped to individual back stack records.

```kotlin
package com.example.navigation

// imports: Compose (compositionLocalOf, ProvidedValue), Circuit (BackStackRecordLocalProvider, Circuit), Metro

val LocalAnalyticsContext = compositionLocalOf<AnalyticsScreenContext> { error("Not provided") }
data class AnalyticsScreenContext(val screenName: String, val backStackDepth: Int)

@Inject @SingleIn(AppScope::class)
class AnalyticsLocalProvider : BackStackRecordLocalProvider {
    @Composable
    override fun providedValues(record: BackStack.Record, backStackDepth: Int): ProvidedValues {
        return ProvidedValues(LocalAnalyticsContext provides AnalyticsScreenContext(
            screenName = record.screen::class.simpleName ?: "Unknown", backStackDepth = backStackDepth,
        ))
    }
}

// Register: Circuit.Builder().addBackStackRecordLocalProvider(analyticsProvider).build()
// Access: val ctx = LocalAnalyticsContext.current
```

---

## Example 12: Navigation Testing with FakeNavigator

```kotlin
package com.example.feature.home

// imports: circuit-test (FakeNavigator, test), Truth (assertThat), coroutines-test (runTest)

class HomePresenterTest {
    private val fakeNavigator = FakeNavigator(HomeScreen)

    @Test fun `clicking item navigates to detail`() = runTest {
        val presenter = HomePresenter(navigator = fakeNavigator, repo = FakeItemRepository(listOf(testItem)))
        presenter.test {
            awaitItem() // loading
            val state = awaitItem()
            state.eventSink(HomeScreen.Event.ItemClicked("item-1"))
            assertThat(fakeNavigator.awaitNextScreen()).isEqualTo(DetailScreen("item-1"))
        }
    }

    @Test fun `back event pops navigator`() = runTest {
        val presenter = HomePresenter(navigator = fakeNavigator, repo = FakeItemRepository())
        presenter.test {
            awaitItem().eventSink(HomeScreen.Event.GoBack)
            assertThat(fakeNavigator.awaitPop().result).isNull()
        }
    }

    @Test fun `logout resets root to login`() = runTest {
        val presenter = HomePresenter(navigator = fakeNavigator, repo = FakeItemRepository())
        presenter.test {
            awaitItem().eventSink(HomeScreen.Event.Logout)
            assertThat(fakeNavigator.awaitResetRoot().newRoot).isEqualTo(LoginScreen)
        }
    }

    @Test fun `picker result updates selected color`() = runTest {
        val presenter = ThemePresenter(navigator = fakeNavigator)
        presenter.test {
            awaitItem().eventSink(ThemeScreen.Event.PickColor)
            assertThat(fakeNavigator.awaitNextScreen()).isEqualTo(ColorPickerScreen)
            fakeNavigator.pop(result = ColorPickerScreen.Result("#FF0000"))
            assertThat(awaitItem().selectedColor).isEqualTo("#FF0000")
        }
    }

    @Test fun `refresh does not navigate`() = runTest {
        val presenter = HomePresenter(navigator = fakeNavigator, repo = FakeItemRepository())
        presenter.test {
            awaitItem().eventSink(HomeScreen.Event.Refresh)
            fakeNavigator.expectNoEvents()
        }
    }

    @Test fun `tab switch uses SaveAndRestore`() = runTest {
        val nav = FakeNavigator(FeedScreen)
        val presenter = MainPresenter(navigator = nav)
        presenter.test {
            awaitItem().eventSink(MainScreen.Event.SelectTab(Tab.Search))
            val reset = nav.awaitResetRoot()
            assertThat(reset.newRoot).isEqualTo(SearchScreen)
            assertThat(reset.options).isEqualTo(Navigator.StateOptions.SaveAndRestore)
        }
    }
}
```

### FakeNavigator Methods

| Method | Verifies |
|--------|----------|
| `awaitNextScreen()` | `goTo` called, returns screen |
| `awaitPop()` | `pop` called, returns PopData (screen + result) |
| `awaitResetRoot()` | `resetRoot` called, returns ResetRootData (newRoot + options) |
| `expectNoEvents()` | No navigation occurred |
| `pop(result)` | Simulate pop with result (for answering navigator tests) |

---

## Example 13: Custom Screen Transitions

Per-screen AnimatedScreenTransform. Slide for details, fade for tabs, vertical for modals.

```kotlin
package com.example.navigation

// imports: Compose animation (EnterTransition, ExitTransition, tween, fadeIn, fadeOut,
//          slideInHorizontally, slideOutHorizontally, slideInVertically, slideOutVertically),
//          Circuit (AnimatedNavDecorator, AnimatedScreenTransform, NavDecorator)

@Parcelize
data class DetailScreen(val id: String) : Screen, AnimatedScreenTransform {
    override val enterTransition get() = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300))
    override val exitTransition get() = slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(300))
    override val popEnterTransition get() = slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(300))
    override val popExitTransition get() = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300))
}

@Parcelize
data class ModalScreen(val title: String) : Screen, AnimatedScreenTransform {
    override val enterTransition get() = slideInVertically(initialOffsetY = { it }, animationSpec = tween(350))
    override val exitTransition get() = fadeOut(animationSpec = tween(200))
    override val popEnterTransition get() = fadeIn(animationSpec = tween(200))
    override val popExitTransition get() = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(350))
}

@Parcelize
data object FeedScreen : Screen, AnimatedScreenTransform {
    override val enterTransition get() = fadeIn(animationSpec = tween(200))
    override val exitTransition get() = fadeOut(animationSpec = tween(200))
    override val popEnterTransition get() = fadeIn(animationSpec = tween(200))
    override val popExitTransition get() = fadeOut(animationSpec = tween(200))
}

class AppNavDecoratorFactory : NavDecorator.Factory {
    override fun create(): NavDecorator = AnimatedNavDecorator(
        enterTransition = fadeIn(tween(250)) + slideInHorizontally(initialOffsetX = { it / 4 }, animationSpec = tween(250)),
        exitTransition = fadeOut(tween(200)),
        popEnterTransition = fadeIn(tween(200)),
        popExitTransition = fadeOut(tween(200)) + slideOutHorizontally(targetOffsetX = { it / 4 }, animationSpec = tween(250)),
    )
}

// Usage: NavigableCircuitContent(navigator, backStack, decoratorFactory = AppNavDecoratorFactory())
// Resolution: Screen's AnimatedScreenTransform > NavDecorator defaults > fallback
```
