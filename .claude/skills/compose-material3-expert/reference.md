# Material 3 API Reference (Compose Multiplatform 1.10.0)

Complete API signatures for Material 3 theming, components, and adaptive layouts.

---

## MaterialTheme

The root composable that provides M3 theming to the composition tree.

```kotlin
// Package: androidx.compose.material3

@Composable
fun MaterialTheme(
    colorScheme: ColorScheme = MaterialTheme.colorScheme,
    shapes: Shapes = MaterialTheme.shapes,
    typography: Typography = MaterialTheme.typography,
    content: @Composable () -> Unit,
)

// Access current theme values anywhere in composition:
object MaterialTheme {
    val colorScheme: ColorScheme   // Current color scheme
    val typography: Typography     // Current typography
    val shapes: Shapes             // Current shapes
}
```

---

## ColorScheme

All 29 color roles. Create with `lightColorScheme()` or `darkColorScheme()`.

```kotlin
// Package: androidx.compose.material3

@Stable
class ColorScheme(
    // Primary group
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,

    // Secondary group
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,

    // Tertiary group
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,

    // Error group
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,

    // Surface group
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val surfaceTint: Color,

    // Surface containers (layered elevation)
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,

    // Surface bright/dim
    val surfaceBright: Color,
    val surfaceDim: Color,

    // Inverse (for snackbars, tooltips)
    val inverseSurface: Color,
    val inverseOnSurface: Color,
    val inversePrimary: Color,

    // Outline
    val outline: Color,
    val outlineVariant: Color,

    // Scrim
    val scrim: Color,
)
```

### Factory Functions

```kotlin
fun lightColorScheme(
    primary: Color = /* M3 default */,
    onPrimary: Color = /* M3 default */,
    primaryContainer: Color = /* M3 default */,
    onPrimaryContainer: Color = /* M3 default */,
    secondary: Color = /* M3 default */,
    onSecondary: Color = /* M3 default */,
    secondaryContainer: Color = /* M3 default */,
    onSecondaryContainer: Color = /* M3 default */,
    tertiary: Color = /* M3 default */,
    onTertiary: Color = /* M3 default */,
    tertiaryContainer: Color = /* M3 default */,
    onTertiaryContainer: Color = /* M3 default */,
    error: Color = /* M3 default */,
    onError: Color = /* M3 default */,
    errorContainer: Color = /* M3 default */,
    onErrorContainer: Color = /* M3 default */,
    surface: Color = /* M3 default */,
    onSurface: Color = /* M3 default */,
    surfaceVariant: Color = /* M3 default */,
    onSurfaceVariant: Color = /* M3 default */,
    surfaceTint: Color = /* M3 default */,
    inverseSurface: Color = /* M3 default */,
    inverseOnSurface: Color = /* M3 default */,
    inversePrimary: Color = /* M3 default */,
    outline: Color = /* M3 default */,
    outlineVariant: Color = /* M3 default */,
    scrim: Color = /* M3 default */,
    surfaceContainerLowest: Color = /* M3 default */,
    surfaceContainerLow: Color = /* M3 default */,
    surfaceContainer: Color = /* M3 default */,
    surfaceContainerHigh: Color = /* M3 default */,
    surfaceContainerHighest: Color = /* M3 default */,
    surfaceBright: Color = /* M3 default */,
    surfaceDim: Color = /* M3 default */,
): ColorScheme

fun darkColorScheme(/* same parameters with dark defaults */): ColorScheme

// Android-only (android.os.Build.VERSION.SDK_INT >= 31)
fun dynamicLightColorScheme(context: Context): ColorScheme
fun dynamicDarkColorScheme(context: Context): ColorScheme
```

### ColorScheme.copy()

```kotlin
fun ColorScheme.copy(
    primary: Color = this.primary,
    onPrimary: Color = this.onPrimary,
    // ... all 29 parameters ...
): ColorScheme
```

---

## Typography

All 15 text styles in the Material type scale.

```kotlin
// Package: androidx.compose.material3

@Immutable
class Typography(
    val displayLarge: TextStyle = /* defaults: Roboto 57/64 -0.25 */,
    val displayMedium: TextStyle = /* defaults: Roboto 45/52 0 */,
    val displaySmall: TextStyle = /* defaults: Roboto 36/44 0 */,

    val headlineLarge: TextStyle = /* defaults: Roboto 32/40 0 */,
    val headlineMedium: TextStyle = /* defaults: Roboto 28/36 0 */,
    val headlineSmall: TextStyle = /* defaults: Roboto 24/32 0 */,

    val titleLarge: TextStyle = /* defaults: Roboto 22/28 0 */,
    val titleMedium: TextStyle = /* defaults: Roboto Medium 16/24 0.15 */,
    val titleSmall: TextStyle = /* defaults: Roboto Medium 14/20 0.1 */,

    val bodyLarge: TextStyle = /* defaults: Roboto 16/24 0.5 */,
    val bodyMedium: TextStyle = /* defaults: Roboto 14/20 0.25 */,
    val bodySmall: TextStyle = /* defaults: Roboto 12/16 0.4 */,

    val labelLarge: TextStyle = /* defaults: Roboto Medium 14/20 0.1 */,
    val labelMedium: TextStyle = /* defaults: Roboto Medium 12/16 0.5 */,
    val labelSmall: TextStyle = /* defaults: Roboto Medium 11/16 0.5 */,
)
```

### Default Values Summary

| Style | Size/Line Height | Weight | Letter Spacing |
|-------|-----------------|--------|----------------|
| `displayLarge` | 57sp / 64sp | 400 | -0.25sp |
| `displayMedium` | 45sp / 52sp | 400 | 0sp |
| `displaySmall` | 36sp / 44sp | 400 | 0sp |
| `headlineLarge` | 32sp / 40sp | 400 | 0sp |
| `headlineMedium` | 28sp / 36sp | 400 | 0sp |
| `headlineSmall` | 24sp / 32sp | 400 | 0sp |
| `titleLarge` | 22sp / 28sp | 400 | 0sp |
| `titleMedium` | 16sp / 24sp | 500 | 0.15sp |
| `titleSmall` | 14sp / 20sp | 500 | 0.1sp |
| `bodyLarge` | 16sp / 24sp | 400 | 0.5sp |
| `bodyMedium` | 14sp / 20sp | 400 | 0.25sp |
| `bodySmall` | 12sp / 16sp | 400 | 0.4sp |
| `labelLarge` | 14sp / 20sp | 500 | 0.1sp |
| `labelMedium` | 12sp / 16sp | 500 | 0.5sp |
| `labelSmall` | 11sp / 16sp | 500 | 0.5sp |

---

## Shapes

```kotlin
// Package: androidx.compose.material3

@Immutable
class Shapes(
    val extraSmall: CornerBasedShape = RoundedCornerShape(4.dp),
    val small: CornerBasedShape = RoundedCornerShape(8.dp),
    val medium: CornerBasedShape = RoundedCornerShape(12.dp),
    val large: CornerBasedShape = RoundedCornerShape(16.dp),
    val extraLarge: CornerBasedShape = RoundedCornerShape(28.dp),
)
```

---

## Scaffold

The primary layout container that coordinates top bar, bottom bar, FAB, snackbar, and content.

```kotlin
@Composable
fun Scaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(containerColor),
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    content: @Composable (PaddingValues) -> Unit,
)
```

**Important**: Always use the `PaddingValues` parameter in the `content` lambda:
```kotlin
Scaffold { paddingValues ->
    LazyColumn(modifier = Modifier.padding(paddingValues)) { /* ... */ }
}
```

---

## Top App Bars

### TopAppBar (Small)

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    expandedHeight: Dp = TopAppBarDefaults.TopAppBarExpandedHeight,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
)
```

### CenterAlignedTopAppBar

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CenterAlignedTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    expandedHeight: Dp = TopAppBarDefaults.TopAppBarExpandedHeight,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors = TopAppBarDefaults.centerAlignedTopAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
)
```

### MediumTopAppBar

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediumTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    collapsedHeight: Dp = TopAppBarDefaults.MediumAppBarCollapsedHeight,
    expandedHeight: Dp = TopAppBarDefaults.MediumAppBarExpandedHeight,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors = TopAppBarDefaults.mediumTopAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
)
```

### LargeTopAppBar

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LargeTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    collapsedHeight: Dp = TopAppBarDefaults.LargeAppBarCollapsedHeight,
    expandedHeight: Dp = TopAppBarDefaults.LargeAppBarExpandedHeight,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors = TopAppBarDefaults.largeTopAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
)
```

### TopAppBarDefaults Scroll Behaviors

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
object TopAppBarDefaults {
    /** Bar stays visible, elevation changes on scroll. */
    @Composable
    fun pinnedScrollBehavior(
        state: TopAppBarState = rememberTopAppBarState(),
        canScroll: () -> Boolean = { true },
    ): TopAppBarScrollBehavior

    /** Bar scrolls off on down, reappears on up. */
    @Composable
    fun enterAlwaysScrollBehavior(
        state: TopAppBarState = rememberTopAppBarState(),
        canScroll: () -> Boolean = { true },
        snapAnimationSpec: AnimationSpec<Float>? = spring(stiffness = Spring.StiffnessMediumLow),
        flingAnimationSpec: DecayAnimationSpec<Float>? = rememberSplineBasedDecay(),
    ): TopAppBarScrollBehavior

    /** Bar collapses on scroll, expands back when scrolled to top. */
    @Composable
    fun exitUntilCollapsedScrollBehavior(
        state: TopAppBarState = rememberTopAppBarState(),
        canScroll: () -> Boolean = { true },
        snapAnimationSpec: AnimationSpec<Float>? = spring(stiffness = Spring.StiffnessMediumLow),
        flingAnimationSpec: DecayAnimationSpec<Float>? = rememberSplineBasedDecay(),
    ): TopAppBarScrollBehavior
}
```

**Usage pattern**:
```kotlin
val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
Scaffold(
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = { LargeTopAppBar(title = { Text("Title") }, scrollBehavior = scrollBehavior) },
) { padding -> /* scrollable content */ }
```

---

## NavigationBar

Bottom navigation for 3-5 destinations.

```kotlin
@Composable
fun NavigationBar(
    modifier: Modifier = Modifier,
    containerColor: Color = NavigationBarDefaults.containerColor,
    contentColor: Color = MaterialTheme.colorScheme.contentColorFor(containerColor),
    tonalElevation: Dp = NavigationBarDefaults.Elevation,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
    content: @Composable RowScope.() -> Unit,
)

@Composable
fun RowScope.NavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable (() -> Unit)? = null,
    alwaysShowLabel: Boolean = true,
    colors: NavigationBarItemColors = NavigationBarItemDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
)
```

---

## NavigationRail

Side rail for medium/expanded screens.

```kotlin
@Composable
fun NavigationRail(
    modifier: Modifier = Modifier,
    containerColor: Color = NavigationRailDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    header: @Composable (() -> Unit)? = null,
    windowInsets: WindowInsets = NavigationRailDefaults.windowInsets,
    content: @Composable ColumnScope.() -> Unit,
)

@Composable
fun NavigationRailItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable (() -> Unit)? = null,
    alwaysShowLabel: Boolean = true,
    colors: NavigationRailItemColors = NavigationRailItemDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
)
```

---

## NavigationDrawer

### ModalNavigationDrawer

```kotlin
@Composable
fun ModalNavigationDrawer(
    drawerContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    drawerState: DrawerState = rememberDrawerState(DrawerValue.Closed),
    gesturesEnabled: Boolean = true,
    scrimColor: Color = DrawerDefaults.scrimColor,
    content: @Composable () -> Unit,
)
```

### PermanentNavigationDrawer

```kotlin
@Composable
fun PermanentNavigationDrawer(
    drawerContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
)
```

### NavigationDrawerItem

```kotlin
@Composable
fun NavigationDrawerItem(
    label: @Composable () -> Unit,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit)? = null,
    badge: @Composable (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(28.dp),
    colors: NavigationDrawerItemColors = NavigationDrawerItemDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
)
```

---

## ModalBottomSheet

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = 0.dp,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.windowInsets },
    properties: ModalBottomSheetProperties = ModalBottomSheetDefaults.properties,
    content: @Composable ColumnScope.() -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberModalBottomSheetState(
    skipPartiallyExpanded: Boolean = false,
    confirmValueChange: (SheetValue) -> Boolean = { true },
): SheetState
```

---

## BottomSheetScaffold

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetScaffold(
    sheetContent: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    scaffoldState: BottomSheetScaffoldState = rememberBottomSheetScaffoldState(),
    sheetPeekHeight: Dp = BottomSheetDefaults.SheetPeekHeight,
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    sheetShape: Shape = BottomSheetDefaults.ExpandedShape,
    sheetContainerColor: Color = BottomSheetDefaults.ContainerColor,
    sheetContentColor: Color = contentColorFor(sheetContainerColor),
    sheetTonalElevation: Dp = 0.dp,
    sheetShadowElevation: Dp = BottomSheetDefaults.Elevation,
    sheetDragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    sheetSwipeEnabled: Boolean = true,
    topBar: @Composable (() -> Unit)? = null,
    snackbarHost: @Composable (SnackbarHostState) -> Unit = { SnackbarHost(it) },
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(containerColor),
    content: @Composable (PaddingValues) -> Unit,
)
```

---

## SnackbarHost / SnackbarHostState

```kotlin
@Composable
fun SnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    snackbar: @Composable (SnackbarData) -> Unit = { Snackbar(it) },
)

@Stable
class SnackbarHostState {
    /** Shows a snackbar. Suspends until dismissed. */
    suspend fun showSnackbar(
        message: String,
        actionLabel: String? = null,
        withDismissAction: Boolean = false,
        duration: SnackbarDuration = if (actionLabel == null) SnackbarDuration.Short
                                     else SnackbarDuration.Indefinite,
    ): SnackbarResult
}

enum class SnackbarResult {
    Dismissed,
    ActionPerformed,
}

enum class SnackbarDuration {
    Short,
    Long,
    Indefinite,
}
```

**Usage pattern**:
```kotlin
val snackbarHostState = remember { SnackbarHostState() }
Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { /* ... */ }

// Show from a coroutine:
scope.launch {
    val result = snackbarHostState.showSnackbar("Item deleted", actionLabel = "Undo")
    if (result == SnackbarResult.ActionPerformed) { /* undo */ }
}
```

---

## AlertDialog

```kotlin
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
)
```

### BasicAlertDialog

```kotlin
@Composable
fun BasicAlertDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
)
```

---

## TextField / OutlinedTextField

### TextField (Filled)

```kotlin
@Composable
fun TextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = TextFieldDefaults.shape,
    colors: TextFieldColors = TextFieldDefaults.colors(),
)
```

### OutlinedTextField

Same parameters as `TextField`, but with `OutlinedTextFieldDefaults.shape` and `OutlinedTextFieldDefaults.colors()` defaults.

---

## ListItem

```kotlin
@Composable
fun ListItem(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    overlineContent: @Composable (() -> Unit)? = null,
    supportingContent: @Composable (() -> Unit)? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    colors: ListItemColors = ListItemDefaults.colors(),
    tonalElevation: Dp = ListItemDefaults.Elevation,
    shadowElevation: Dp = ListItemDefaults.Elevation,
)
```

---

## Buttons

### Button (Filled)

```kotlin
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
)
```

### Other Button Variants

All share the same parameter signature pattern as `Button`:

```kotlin
fun FilledTonalButton(onClick, modifier, enabled, shape, colors, elevation, border, contentPadding, interactionSource, content)
fun ElevatedButton(onClick, modifier, enabled, shape, colors, elevation, border, contentPadding, interactionSource, content)
fun OutlinedButton(onClick, modifier, enabled, shape, colors, elevation, border, contentPadding, interactionSource, content)
fun TextButton(onClick, modifier, enabled, shape, colors, elevation, border, contentPadding, interactionSource, content)
```

Default colors per variant:

| Variant | Container | Content |
|---------|-----------|---------|
| `Button` | `primary` | `onPrimary` |
| `FilledTonalButton` | `secondaryContainer` | `onSecondaryContainer` |
| `ElevatedButton` | `surface` + elevation | `primary` |
| `OutlinedButton` | `transparent` | `primary` |
| `TextButton` | `transparent` | `primary` |

---

## Cards

### Card (Filled)

```kotlin
@Composable
fun Card(
    modifier: Modifier = Modifier,
    shape: Shape = CardDefaults.shape,
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
)

// Clickable variant
@Composable
fun Card(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CardDefaults.shape,
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable ColumnScope.() -> Unit,
)
```

### ElevatedCard / OutlinedCard

Same parameter pattern. Use `CardDefaults.elevatedCardColors()` / `CardDefaults.outlinedCardColors()`.

---

## FloatingActionButton

```kotlin
@Composable
fun FloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = FloatingActionButtonDefaults.shape,
    containerColor: Color = FloatingActionButtonDefaults.containerColor,
    contentColor: Color = contentColorFor(containerColor),
    elevation: FloatingActionButtonElevation = FloatingActionButtonDefaults.elevation(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
)

@Composable
fun ExtendedFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = FloatingActionButtonDefaults.extendedFabShape,
    containerColor: Color = FloatingActionButtonDefaults.containerColor,
    contentColor: Color = contentColorFor(containerColor),
    elevation: FloatingActionButtonElevation = FloatingActionButtonDefaults.elevation(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
)

// Convenience overload with separate icon and text
@Composable
fun ExtendedFloatingActionButton(
    text: @Composable () -> Unit,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
    shape: Shape = FloatingActionButtonDefaults.extendedFabShape,
    containerColor: Color = FloatingActionButtonDefaults.containerColor,
    contentColor: Color = contentColorFor(containerColor),
    elevation: FloatingActionButtonElevation = FloatingActionButtonDefaults.elevation(),
    interactionSource: MutableInteractionSource? = null,
)
```

---

## Surface

```kotlin
@Composable
fun Surface(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    color: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(color),
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    border: BorderStroke? = null,
    content: @Composable () -> Unit,
)
```

**Key behavior**: `Surface` sets `LocalContentColor` to `contentColor`, so children (Text, Icon) automatically use the correct contrast color.

---

## WindowSizeClass

```kotlin
// Package: androidx.compose.material3.windowsizeclass

@Immutable
class WindowSizeClass private constructor(
    val widthSizeClass: WindowWidthSizeClass,
    val heightSizeClass: WindowHeightSizeClass,
)

enum class WindowWidthSizeClass {
    Compact,   // < 600dp
    Medium,    // 600dp - 839dp
    Expanded,  // >= 840dp
}

enum class WindowHeightSizeClass {
    Compact,   // < 480dp
    Medium,    // 480dp - 899dp
    Expanded,  // >= 900dp
}

// Calculate from Activity (Android) or top-level composable
@Composable
fun calculateWindowSizeClass(activity: Activity): WindowSizeClass

// KMP: Use platform-appropriate calculation
// Android: calculateWindowSizeClass(activity)
// Desktop/iOS: Based on window dimensions
```

---

## TabRow / ScrollableTabRow

```kotlin
@Composable
fun TabRow(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    containerColor: Color = TabRowDefaults.primaryContainerColor,
    contentColor: Color = TabRowDefaults.primaryContentColor,
    indicator: @Composable TabIndicatorScope.() -> Unit = { TabRowDefaults.PrimaryIndicator(Modifier.tabIndicatorOffset(selectedTabIndex)) },
    divider: @Composable () -> Unit = { HorizontalDivider() },
    tabs: @Composable () -> Unit,
)

@Composable
fun ScrollableTabRow(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    containerColor: Color = TabRowDefaults.primaryContainerColor,
    contentColor: Color = TabRowDefaults.primaryContentColor,
    edgePadding: Dp = TabRowDefaults.ScrollableTabRowEdgeStartPadding,
    indicator: @Composable TabIndicatorScope.() -> Unit = { TabRowDefaults.PrimaryIndicator(Modifier.tabIndicatorOffset(selectedTabIndex)) },
    divider: @Composable () -> Unit = { HorizontalDivider() },
    tabs: @Composable () -> Unit,
)

@Composable
fun Tab(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    selectedContentColor: Color = LocalContentColor.current,
    unselectedContentColor: Color = selectedContentColor,
    interactionSource: MutableInteractionSource? = null,
)
```

---

## Key Imports

```kotlin
// Theming
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicLightColorScheme  // Android only
import androidx.compose.material3.dynamicDarkColorScheme  // Android only

// Layout
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface

// Top Bars
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.TopAppBarDefaults

// Navigation
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem

// Buttons
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton

// Cards
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.CardDefaults

// Inputs
import androidx.compose.material3.TextField
import androidx.compose.material3.OutlinedTextField

// Dialogs & Sheets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.rememberModalBottomSheetState

// Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration

// Lists
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults

// Tabs
import androidx.compose.material3.TabRow
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab

// Adaptive
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass

// Utilities
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
```
