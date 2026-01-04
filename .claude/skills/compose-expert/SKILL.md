---
name: compose-expert
description: Elite Compose Multiplatform expertise for KMP apps. Use when creating composables, optimizing recomposition, managing composition state, implementing custom layouts, handling gestures, building accessible UI, or solving multiplatform UI challenges. Triggers on any Compose UI code generation, performance optimization, state management, or KMP UI patterns.
---

# Compose Multiplatform Expert Skill

## Core Principles

1. **Composition over inheritance** - Build from small, reusable composables
2. **Unidirectional data flow** - State flows down, events flow up
3. **Stability for performance** - Use stable/immutable types to skip recomposition
4. **Multiplatform first** - Avoid platform-specific APIs unless necessary
5. **Modifier chains** - Always accept and use `modifier` parameter

## Composable Patterns

### Stateless UI Component (Preferred)

```kotlin
@Composable
fun UserCard(
    user: User,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,  // ALWAYS accept modifier
) {
    Card(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = user.avatarUrl,
                contentDescription = "Avatar for ${user.name}",
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = user.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEditClick) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit user",
                )
            }
        }
    }
}
```

### Stateful Wrapper Pattern

```kotlin
// Stateless (reusable, testable)
@Composable
fun Counter(
    count: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onDecrement) {
            Icon(Icons.Default.Remove, "Decrement")
        }
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        IconButton(onClick = onIncrement) {
            Icon(Icons.Default.Add, "Increment")
        }
    }
}

// Stateful wrapper (for standalone use)
@Composable
fun Counter(
    initialCount: Int = 0,
    modifier: Modifier = Modifier,
) {
    var count by remember { mutableIntStateOf(initialCount) }
    Counter(
        count = count,
        onIncrement = { count++ },
        onDecrement = { count-- },
        modifier = modifier,
    )
}
```

### State Holder Pattern

```kotlin
@Stable
class SearchFieldState(
    initialQuery: String = "",
) {
    var query by mutableStateOf(initialQuery)
        private set
    
    var isActive by mutableStateOf(false)
        private set
    
    var suggestions by mutableStateOf<List<String>>(emptyList())
        private set
    
    fun updateQuery(newQuery: String) {
        query = newQuery
    }
    
    fun activate() { isActive = true }
    fun deactivate() { 
        isActive = false
        suggestions = emptyList()
    }
    
    fun updateSuggestions(newSuggestions: List<String>) {
        suggestions = newSuggestions
    }
}

@Composable
fun rememberSearchFieldState(
    initialQuery: String = "",
): SearchFieldState = remember { SearchFieldState(initialQuery) }

// Usage
@Composable
fun SearchScreen() {
    val searchState = rememberSearchFieldState()
    
    SearchField(
        state = searchState,
        onSearch = { query -> /* perform search */ },
    )
}
```

## Recomposition Optimization

### Stability Fundamentals

```kotlin
// ✅ STABLE - primitives, String, immutable data classes
@Immutable
data class User(
    val id: String,
    val name: String,
    val email: String,
)

// ✅ STABLE - using kotlinx.collections.immutable
@Immutable
data class UserList(
    val users: ImmutableList<User>,
)

// ❌ UNSTABLE - var properties
data class MutableUser(
    var name: String,  // var makes entire class unstable
)

// ❌ UNSTABLE - standard collections
data class UserListBad(
    val users: List<User>,  // List is unstable (could be mutable)
)

// ✅ FIX - wrap in immutable
@Immutable
data class UserListGood(
    val users: List<User>,  // @Immutable promises it won't change
)
```

### Lambda Stability

```kotlin
// ❌ Creates new lambda every recomposition
@Composable
fun BadExample(viewModel: ViewModel) {
    Button(onClick = { viewModel.doSomething() }) {
        Text("Click")
    }
}

// ✅ Stable lambda - remember the lambda
@Composable
fun GoodExample1(viewModel: ViewModel) {
    val onClick = remember { { viewModel.doSomething() } }
    Button(onClick = onClick) {
        Text("Click")
    }
}

// ✅ Stable lambda - method reference (if viewModel is stable)
@Composable
fun GoodExample2(viewModel: ViewModel) {
    Button(onClick = viewModel::doSomething) {
        Text("Click")
    }
}

// ✅ For event sinks (Circuit pattern) - already stable
@Composable
fun CircuitExample(state: MyScreen.State) {
    Button(onClick = { state.eventSink(MyScreen.Event.Click) }) {
        Text("Click")
    }
}
```

### Key for Lists

```kotlin
LazyColumn {
    items(
        items = users,
        key = { it.id },  // ALWAYS provide key
    ) { user ->
        UserCard(
            user = user,
            onEditClick = { onEdit(user.id) },
        )
    }
}

// For heterogeneous lists
LazyColumn {
    items(
        items = items,
        key = { item ->
            when (item) {
                is HeaderItem -> "header-${item.id}"
                is ContentItem -> "content-${item.id}"
                is FooterItem -> "footer-${item.id}"
            }
        },
        contentType = { item ->
            when (item) {
                is HeaderItem -> "header"
                is ContentItem -> "content"
                is FooterItem -> "footer"
            }
        },
    ) { item ->
        when (item) {
            is HeaderItem -> HeaderRow(item)
            is ContentItem -> ContentRow(item)
            is FooterItem -> FooterRow(item)
        }
    }
}
```

### derivedStateOf (Expensive Computations)

```kotlin
@Composable
fun FilteredList(items: List<Item>, query: String) {
    // ✅ Only recomputes when items or query changes
    val filteredItems by remember(items, query) {
        derivedStateOf {
            items.filter { it.name.contains(query, ignoreCase = true) }
        }
    }
    
    LazyColumn {
        items(filteredItems, key = { it.id }) { item ->
            ItemRow(item)
        }
    }
}

// ❌ DON'T use derivedStateOf for simple checks
val isEnabled by remember { derivedStateOf { text.isNotEmpty() } }  // Overkill

// ✅ Simple derivation - just compute directly
val isEnabled = text.isNotEmpty()
```

### remember with Keys

```kotlin
@Composable
fun ExpensiveComponent(userId: String, config: Config) {
    // Recomputes only when userId OR config changes
    val processor = remember(userId, config) {
        ExpensiveProcessor(userId, config)
    }
    
    // Single key
    val formatter = remember(config.locale) {
        DateFormatter(config.locale)
    }
}
```

## Side Effects

### LaunchedEffect (One-Shot Operations)

```kotlin
@Composable
fun ProfileScreen(userId: String) {
    var user by remember { mutableStateOf<User?>(null) }
    
    // Runs when userId changes
    LaunchedEffect(userId) {
        user = repository.getUser(userId)
    }
    
    user?.let { ProfileContent(it) }
}
```

### DisposableEffect (Cleanup Required)

```kotlin
@Composable
fun LifecycleAwareComponent(lifecycle: Lifecycle) {
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> { /* ... */ }
                Lifecycle.Event.ON_PAUSE -> { /* ... */ }
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }
}
```

### SideEffect (Every Successful Composition)

```kotlin
@Composable
fun AnalyticsScreen(screenName: String) {
    // Runs after every successful composition
    SideEffect {
        analytics.trackScreenView(screenName)
    }
}
```

### rememberCoroutineScope (User-Triggered)

```kotlin
@Composable
fun RefreshableContent(onRefresh: suspend () -> Unit) {
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    
    Button(
        onClick = {
            scope.launch {
                isRefreshing = true
                try {
                    onRefresh()
                } finally {
                    isRefreshing = false
                }
            }
        },
        enabled = !isRefreshing,
    ) {
        if (isRefreshing) {
            CircularProgressIndicator(Modifier.size(16.dp))
        } else {
            Text("Refresh")
        }
    }
}
```

## Multiplatform Patterns

### Expect/Actual for Platform UI

```kotlin
// commonMain
@Composable
expect fun BackHandler(enabled: Boolean = true, onBack: () -> Unit)

@Composable
expect fun PlatformStatusBarColor(color: Color, darkIcons: Boolean)

// androidMain
@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled = enabled, onBack = onBack)
}

@Composable
actual fun PlatformStatusBarColor(color: Color, darkIcons: Boolean) {
    val systemUiController = rememberSystemUiController()
    SideEffect {
        systemUiController.setStatusBarColor(color, darkIcons)
    }
}

// iosMain
@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    // iOS handles back via navigation controller
}

@Composable
actual fun PlatformStatusBarColor(color: Color, darkIcons: Boolean) {
    // iOS status bar handled differently
}

// desktopMain
@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    // Handle ESC key or window close
    val window = LocalWindow.current
    DisposableEffect(enabled) {
        val listener = object : KeyEventDispatcher {
            override fun dispatchKeyEvent(e: KeyEvent): Boolean {
                if (enabled && e.keyCode == KeyEvent.VK_ESCAPE) {
                    onBack()
                    return true
                }
                return false
            }
        }
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
            .addKeyEventDispatcher(listener)
        onDispose {
            KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .removeKeyEventDispatcher(listener)
        }
    }
}
```

### Compose Resources

```kotlin
// commonMain/composeResources/values/strings.xml
// Use Compose Multiplatform Resources API

@Composable
fun Greeting() {
    Text(text = stringResource(Res.string.hello_world))
}

@Composable
fun Logo() {
    Image(
        painter = painterResource(Res.drawable.logo),
        contentDescription = stringResource(Res.string.logo_description),
    )
}
```

### Material 3 Adaptive Layouts

```kotlin
// Dependencies for M3 Adaptive
implementation("androidx.compose.material3.adaptive:adaptive:1.1.0")
implementation("androidx.compose.material3.adaptive:adaptive-layout:1.1.0")
implementation("androidx.compose.material3.adaptive:adaptive-navigation:1.1.0")
```

#### NavigationSuiteScaffold (Adaptive Navigation)

Automatically switches between bottom navigation, navigation rail, and drawer:

```kotlin
@Composable
fun AdaptiveNavigation(
    selectedDestination: AppDestination,
    onDestinationSelected: (AppDestination) -> Unit,
    content: @Composable () -> Unit,
) {
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestination.entries.forEach { destination ->
                item(
                    icon = { Icon(destination.icon, contentDescription = destination.label) },
                    label = { Text(destination.label) },
                    selected = destination == selectedDestination,
                    onClick = { onDestinationSelected(destination) },
                )
            }
        },
    ) {
        content()
    }
}

// Control navigation type manually if needed
@Composable
fun CustomAdaptiveNavigation() {
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val customNavSuiteType = with(adaptiveInfo) {
        if (windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED) {
            NavigationSuiteType.NavigationDrawer
        } else {
            NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(adaptiveInfo)
        }
    }

    NavigationSuiteScaffold(
        layoutType = customNavSuiteType,
        navigationSuiteItems = { /* ... */ },
    ) {
        // Content
    }
}
```

#### ListDetailPaneScaffold (List-Detail Layout)

```kotlin
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun <T : Any> AdaptiveListDetail(
    items: List<T>,
    selectedItem: T?,
    onItemSelected: (T) -> Unit,
    itemContent: @Composable (T) -> Unit,
    detailContent: @Composable (T) -> Unit,
    emptyDetailContent: @Composable () -> Unit = { Text("Select an item") },
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<T>()

    BackHandler(navigator.canNavigateBack()) {
        navigator.navigateBack()
    }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            AnimatedPane {
                LazyColumn {
                    items(items, key = { it.hashCode() }) { item ->
                        itemContent(item)
                        Modifier.clickable {
                            onItemSelected(item)
                            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, item)
                        }
                    }
                }
            }
        },
        detailPane = {
            AnimatedPane {
                navigator.currentDestination?.content?.let { item ->
                    detailContent(item)
                } ?: emptyDetailContent()
            }
        },
    )
}
```

#### SupportingPaneScaffold (Main + Supporting Content)

```kotlin
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun AdaptiveSupportingPane(
    mainContent: @Composable () -> Unit,
    supportingContent: @Composable () -> Unit,
) {
    val navigator = rememberSupportingPaneScaffoldNavigator()

    SupportingPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        mainPane = {
            AnimatedPane {
                mainContent()
            }
        },
        supportingPane = {
            AnimatedPane {
                supportingContent()
            }
        },
    )
}
```

#### Window Size Classes

```kotlin
@Composable
fun ResponsiveContent() {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass

    when (windowSizeClass.windowWidthSizeClass) {
        WindowWidthSizeClass.COMPACT -> {
            // Phone - single column, bottom nav
            CompactLayout()
        }
        WindowWidthSizeClass.MEDIUM -> {
            // Tablet portrait / foldable - nav rail, 2 columns
            MediumLayout()
        }
        WindowWidthSizeClass.EXPANDED -> {
            // Tablet landscape / desktop - nav drawer, 3 columns
            ExpandedLayout()
        }
    }
}
```

#### Legacy: Manual BoxWithConstraints

```kotlin
@Composable
fun ManualAdaptiveLayout(
    content: @Composable () -> Unit,
) {
    BoxWithConstraints {
        when {
            maxWidth < 600.dp -> {
                // Phone layout
                Column { content() }
            }
            maxWidth < 900.dp -> {
                // Tablet layout
                Row { content() }
            }
            else -> {
                // Desktop layout
                Row(Modifier.padding(horizontal = 48.dp)) { content() }
            }
        }
    }
}
```

## Accessibility

```kotlin
@Composable
fun AccessibleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.semantics {
            contentDescription = text
            role = Role.Button
            if (!enabled) {
                disabled()
            }
        },
    ) {
        Text(text)
    }
}

// For images
@Composable
fun ProfileAvatar(
    imageUrl: String?,
    userName: String,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        model = imageUrl,
        contentDescription = "Profile picture of $userName",  // Meaningful description
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .semantics {
                testTag = "profile_avatar"
            },
        placeholder = painterResource(Res.drawable.avatar_placeholder),
        error = painterResource(Res.drawable.avatar_error),
    )
}

// For decorative images
@Composable  
fun DecorativeImage(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(Res.drawable.decoration),
        contentDescription = null,  // null for decorative
        modifier = modifier,
    )
}

// Custom semantics for complex components
@Composable
fun RatingBar(
    rating: Float,
    onRatingChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "Rating: ${"%.1f".format(rating)} out of 5 stars"
            role = Role.Slider
            stateDescription = "${"%.1f".format(rating)} stars"
        },
    ) {
        // Stars implementation
    }
}
```

## Common UI Patterns

### Pull to Refresh

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshableList(
    items: List<Item>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pullRefreshState = rememberPullToRefreshState()
    
    Box(
        modifier = modifier.nestedScroll(pullRefreshState.nestedScrollConnection),
    ) {
        LazyColumn(Modifier.fillMaxSize()) {
            items(items, key = { it.id }) { item ->
                ItemRow(item)
            }
        }
        
        PullToRefreshContainer(
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
    
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            pullRefreshState.startRefresh()
        } else {
            pullRefreshState.endRefresh()
        }
    }
    
    LaunchedEffect(pullRefreshState.isRefreshing) {
        if (pullRefreshState.isRefreshing) {
            onRefresh()
        }
    }
}
```

### Infinite Scroll / Pagination

```kotlin
@Composable
fun PaginatedList(
    items: List<Item>,
    isLoadingMore: Boolean,
    hasMoreItems: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    
    // Trigger load more when near end
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            hasMoreItems && !isLoadingMore && lastVisibleItem >= totalItems - 5
        }
    }
    
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            onLoadMore()
        }
    }
    
    LazyColumn(
        state = listState,
        modifier = modifier,
    ) {
        items(items, key = { it.id }) { item ->
            ItemRow(item)
        }
        
        if (isLoadingMore) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
```

### Shimmer Loading Placeholder

```kotlin
@Composable
fun ShimmerEffect(
    modifier: Modifier = Modifier,
) {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant,
        MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.surfaceVariant,
    )
    
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_translate",
    )
    
    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim - 500f, translateAnim - 500f),
        end = Offset(translateAnim, translateAnim),
    )
    
    Box(
        modifier = modifier.background(brush),
    )
}

@Composable
fun UserCardPlaceholder(modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShimmerEffect(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
            )
            Spacer(Modifier.width(16.dp))
            Column {
                ShimmerEffect(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp)),
                )
                Spacer(Modifier.height(8.dp))
                ShimmerEffect(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp)),
                )
            }
        }
    }
}
```

## Testing Composables

```kotlin
class UserCardTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun displayUserName() {
        val user = User(id = "1", name = "John Doe", email = "john@example.com")
        
        composeTestRule.setContent {
            UserCard(user = user, onEditClick = {})
        }
        
        composeTestRule.onNodeWithText("John Doe").assertIsDisplayed()
        composeTestRule.onNodeWithText("john@example.com").assertIsDisplayed()
    }
    
    @Test
    fun editButtonCallsCallback() {
        var clicked = false
        val user = User(id = "1", name = "John", email = "john@test.com")
        
        composeTestRule.setContent {
            UserCard(user = user, onEditClick = { clicked = true })
        }
        
        composeTestRule.onNodeWithContentDescription("Edit user").performClick()
        
        assertThat(clicked).isTrue()
    }
    
    @Test
    fun accessibilityCheck() {
        val user = User(id = "1", name = "John", email = "john@test.com")
        
        composeTestRule.setContent {
            UserCard(user = user, onEditClick = {})
        }
        
        // Verify content descriptions exist
        composeTestRule
            .onNodeWithContentDescription("Avatar for John")
            .assertExists()
        
        composeTestRule
            .onNodeWithContentDescription("Edit user")
            .assertExists()
            .assertHasClickAction()
    }
}
```

## Anti-Patterns

❌ **Don't forget modifier parameter**
```kotlin
// WRONG
@Composable
fun MyComponent() { ... }

// RIGHT
@Composable
fun MyComponent(modifier: Modifier = Modifier) { ... }
```

❌ **Don't read state without remember**
```kotlin
// WRONG - recalculates every recomposition
@Composable
fun BadList(items: List<Item>) {
    val filtered = items.filter { it.isActive }
    LazyColumn {
        items(filtered) { ... }
    }
}

// RIGHT
@Composable
fun GoodList(items: List<Item>) {
    val filtered = remember(items) { items.filter { it.isActive } }
    LazyColumn {
        items(filtered, key = { it.id }) { ... }
    }
}
```

❌ **Don't use mutableStateListOf for large lists**
```kotlin
// WRONG - inefficient for large lists
val items = remember { mutableStateListOf<Item>() }

// RIGHT - use immutable list with State
var items by remember { mutableStateOf<List<Item>>(emptyList()) }
```

❌ **Don't nest scrollables without coordination**
```kotlin
// WRONG - scroll conflict
LazyColumn {
    item {
        LazyRow { ... }  // This is OK - different axis
    }
    item {
        LazyColumn { ... }  // BAD - same axis, no coordination
    }
}

// RIGHT - use fixed height or nestedScroll
LazyColumn {
    item {
        LazyColumn(
            modifier = Modifier.height(200.dp)  // Fixed height
        ) { ... }
    }
}
```

## References

- Compose Multiplatform: https://www.jetbrains.com/lp/compose-multiplatform/
- Compose Documentation: https://developer.android.com/jetpack/compose
- Compose Performance: https://developer.android.com/jetpack/compose/performance
- Compose Accessibility: https://developer.android.com/jetpack/compose/accessibility
