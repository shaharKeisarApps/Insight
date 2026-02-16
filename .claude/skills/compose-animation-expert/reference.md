# Compose Animation Reference

## AnimatedVisibility

Show/hide a composable with enter and exit transitions.

```kotlin
AnimatedVisibility(
    visible = isVisible,
    enter = fadeIn(animationSpec = tween(300)) + expandVertically(),
    exit = fadeOut(animationSpec = tween(300)) + shrinkVertically(),
    label = "ContentVisibility"
) {
    Content()
}
```

### EnterTransition combinators

- `fadeIn()` -- Fade from transparent.
- `expandVertically()` / `expandHorizontally()` / `expandIn()` -- Clip-reveal.
- `slideInVertically()` / `slideInHorizontally()` -- Slide from offset.
- `scaleIn()` -- Scale from smaller size.
- Combine with `+`: `fadeIn() + slideInVertically()`.

### ExitTransition combinators

- `fadeOut()` -- Fade to transparent.
- `shrinkVertically()` / `shrinkHorizontally()` / `shrinkOut()` -- Clip-hide.
- `slideOutVertically()` / `slideOutHorizontally()` -- Slide to offset.
- `scaleOut()` -- Scale to smaller size.
- Combine with `+`: `fadeOut() + slideOutVertically()`.

## AnimatedContent

Replace one composable with another using a `ContentTransform`.

```kotlin
AnimatedContent(
    targetState = screenState,
    transitionSpec = {
        fadeIn(tween(300)) + slideInVertically { it } togetherWith
            fadeOut(tween(300)) + slideOutVertically { -it } using
            SizeTransform(clip = false)
    },
    label = "ScreenContent"
) { state ->
    when (state) {
        is Loading -> LoadingView()
        is Success -> SuccessView(state.data)
        is Error -> ErrorView(state.message)
    }
}
```

### ContentTransform

- Built with `EnterTransition togetherWith ExitTransition`.
- Optional `using SizeTransform(clip = true/false)` to animate container size.

## Crossfade

Simple cross-fade between content keyed on a state value.

```kotlin
Crossfade(
    targetState = currentTab,
    animationSpec = tween(400),
    label = "TabCrossfade"
) { tab ->
    when (tab) {
        Tab.Home -> HomeScreen()
        Tab.Profile -> ProfileScreen()
    }
}
```

## animate*AsState

Animate a single value when a target changes. Returns a `State<T>`.

```kotlin
val alpha by animateFloatAsState(
    targetValue = if (isSelected) 1f else 0.5f,
    animationSpec = tween(300),
    label = "Alpha"
)

val size by animateDpAsState(
    targetValue = if (isExpanded) 200.dp else 100.dp,
    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
    label = "Size"
)

val color by animateColorAsState(
    targetValue = if (isError) Color.Red else Color.Green,
    animationSpec = tween(500),
    label = "StatusColor"
)
```

Available variants: `animateFloatAsState`, `animateDpAsState`, `animateColorAsState`, `animateIntAsState`, `animateSizeAsState`, `animateOffsetAsState`, `animateIntOffsetAsState`, `animateIntSizeAsState`, `animateRectAsState`.

## updateTransition

Coordinate multiple animated properties on a single state change.

```kotlin
val transition = updateTransition(targetState = cardState, label = "CardTransition")

val elevation by transition.animateDp(label = "Elevation") { state ->
    when (state) {
        CardState.Collapsed -> 2.dp
        CardState.Expanded -> 8.dp
    }
}

val cornerRadius by transition.animateDp(label = "Corner") { state ->
    when (state) {
        CardState.Collapsed -> 16.dp
        CardState.Expanded -> 4.dp
    }
}

val backgroundColor by transition.animateColor(label = "BgColor") { state ->
    when (state) {
        CardState.Collapsed -> MaterialTheme.colorScheme.surface
        CardState.Expanded -> MaterialTheme.colorScheme.primaryContainer
    }
}
```

Available: `animateFloat`, `animateDp`, `animateColor`, `animateInt`, `animateSize`, `animateOffset`, `animateRect`, `animateValue`.

## Animatable

Low-level coroutine-driven animation for full control.

```kotlin
val offset = remember { Animatable(0f) }

LaunchedEffect(targetOffset) {
    offset.animateTo(
        targetValue = targetOffset,
        animationSpec = spring(stiffness = Spring.StiffnessLow)
    )
}

// Snap without animation
LaunchedEffect(Unit) {
    offset.snapTo(0f)
}

// Use the value
Box(Modifier.offset { IntOffset(offset.value.roundToInt(), 0) })
```

Key methods: `animateTo`, `snapTo`, `animateDecay`, `stop`.

## rememberInfiniteTransition

Create animations that repeat indefinitely.

```kotlin
val infiniteTransition = rememberInfiniteTransition(label = "Infinite")

val alpha by infiniteTransition.animateFloat(
    initialValue = 0.3f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
        animation = tween(1000),
        repeatMode = RepeatMode.Reverse
    ),
    label = "PulseAlpha"
)
```

RepeatMode options: `Restart` (reset to initial), `Reverse` (ping-pong).

## AnimationSpec Types

### spring

```kotlin
spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy, // 0f..1f, lower = bouncier
    stiffness = Spring.StiffnessMedium              // higher = faster
)
```

Constants: `DampingRatioNoBouncy` (1f), `DampingRatioLowBouncy` (0.75f), `DampingRatioMediumBouncy` (0.5f), `DampingRatioHighBouncy` (0.2f). Stiffness: `StiffnessHigh` (10000f), `StiffnessMedium` (1500f), `StiffnessMediumLow` (400f), `StiffnessLow` (200f), `StiffnessVeryLow` (50f).

### tween

```kotlin
tween<Float>(
    durationMillis = 300,
    delayMillis = 0,
    easing = FastOutSlowInEasing
)
```

Easing options: `FastOutSlowInEasing` (standard), `LinearOutSlowInEasing` (decelerate), `FastOutLinearInEasing` (accelerate), `LinearEasing`, `EaseInOutCubic`, `CubicBezierEasing(a, b, c, d)`.

### keyframes

```kotlin
keyframes<Float> {
    durationMillis = 500
    0f at 0 using LinearEasing
    0.5f at 150 using FastOutSlowInEasing
    1f at 500
}
```

### snap

```kotlin
snap<Float>(delayMillis = 0) // Instant change, optional delay
```

## Modifier.animateContentSize

Smoothly animate when a composable's content changes size.

```kotlin
Column(
    modifier = Modifier
        .animateContentSize(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
) {
    Text("Always visible")
    if (isExpanded) {
        Text("Extra content that appears/disappears")
    }
}
```

## SharedTransitionLayout

Shared element transitions between composables (Compose Multiplatform).

```kotlin
SharedTransitionLayout {
    AnimatedContent(targetState = showDetail, label = "SharedTransition") { isDetail ->
        if (!isDetail) {
            ListItem(
                imageModifier = Modifier.sharedElement(
                    state = rememberSharedContentState(key = "image-$id"),
                    animatedVisibilityScope = this@AnimatedContent
                ),
                textModifier = Modifier.sharedElement(
                    state = rememberSharedContentState(key = "title-$id"),
                    animatedVisibilityScope = this@AnimatedContent
                )
            )
        } else {
            DetailScreen(
                imageModifier = Modifier.sharedElement(
                    state = rememberSharedContentState(key = "image-$id"),
                    animatedVisibilityScope = this@AnimatedContent
                ),
                textModifier = Modifier.sharedElement(
                    state = rememberSharedContentState(key = "title-$id"),
                    animatedVisibilityScope = this@AnimatedContent
                )
            )
        }
    }
}
```

Key APIs:
- `SharedTransitionLayout` -- Root scope providing shared transition context.
- `Modifier.sharedElement` -- Mark an element as shared (matched by key).
- `Modifier.sharedBounds` -- Animate the bounds (container) between two layouts.
- `rememberSharedContentState(key)` -- Create a key-based state for matching elements.
