---
name: compose-ui-expert
description: Expert guidance on Compose UI visuals for KMP. Use for Layouts (Row, Column, Box, LazyColumn), Modifier chains, Custom Layouts, Canvas drawing, GraphicsLayer, and SubcomposeLayout. Covers Compose Multiplatform 1.10.0.
---

# Compose UI Expert Skill (Compose Multiplatform 1.10.0)

## Overview

This skill covers the **visual representation layer** of Compose Multiplatform: layout positioning, modifier chains, lazy lists, custom layouts, Canvas drawing, and hardware-accelerated graphics layers. It applies to all KMP targets: Android, iOS, Desktop, and Web (wasmJs).

**In scope:** Row, Column, Box, LazyColumn/Row/Grid, Modifier ordering, custom `Layout`, `SubcomposeLayout`, `Canvas`, `DrawScope`, `Modifier.graphicsLayer`, constraints propagation, WindowInsets.

**Out of scope:** State management and recomposition (see `compose-runtime-expert`). Material Design tokens, theming, and M3 components (see `compose-material3-expert`). Animation APIs (see `compose-animation-expert`).

## When to Use

- **Layout**: Positioning elements with `Row`, `Column`, `Box`, `BoxWithConstraints`, `Spacer`.
- **Modifiers**: Sizing, padding, backgrounds, borders, clips, clicks, drawing.
- **Lazy lists**: `LazyColumn`, `LazyRow`, `LazyVerticalGrid`, `LazyVerticalStaggeredGrid`.
- **Custom layouts**: Building non-standard positioning with `Layout` composable.
- **Drawing**: `Canvas`, `DrawScope`, custom shapes, gradients, paths.
- **Graphics**: Hardware-accelerated transforms via `Modifier.graphicsLayer`.
- **Responsive design**: `BoxWithConstraints` for adaptive layouts.
- **Nested scrolling**: `NestedScrollConnection` for coordinated scroll behavior.

## Quick Reference

For complete API signatures and modifier catalog, see [reference.md](reference.md).
For production code examples, see [examples.md](examples.md).

---

## Layout Fundamentals

### Container Composables

| Composable | Axis | Children | Use Case |
|------------|------|----------|----------|
| `Row` | Horizontal | Sequential left-to-right | Horizontal arrangements, toolbars |
| `Column` | Vertical | Sequential top-to-bottom | Vertical lists, forms, stacking |
| `Box` | Z-axis (stacked) | Overlapping layers | Overlays, badges, centering single child |
| `BoxWithConstraints` | Z-axis + constraints | Overlapping + access to parent constraints | Responsive/adaptive layouts |
| `Spacer` | None | Empty space | Fixed gaps, weighted flex space |

### Row and Column

Both accept `horizontalArrangement`/`verticalArrangement` and alignment parameters.

```kotlin
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
) {
    Text("Left")
    Text("Right")
}

Column(
    modifier = Modifier.fillMaxHeight(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    Text("First")
    Text("Second")
}
```

### Box

Stacks children on top of each other. The last child is drawn on top.

```kotlin
Box(
    modifier = Modifier.size(200.dp),
    contentAlignment = Alignment.Center,
) {
    Image(painter, contentDescription = null, Modifier.fillMaxSize())
    Text("Overlay", Modifier.align(Alignment.BottomCenter))
}
```

### BoxWithConstraints

Provides access to the incoming `Constraints` (maxWidth, maxHeight, minWidth, minHeight) as Dp values. Use for responsive layouts.

```kotlin
BoxWithConstraints {
    if (maxWidth < 600.dp) {
        PhoneLayout()
    } else {
        TabletLayout()
    }
}
```

---

## Modifier Chain

### Order Matters -- The Linked-List Mental Model

A modifier chain is processed **outside-in** (first modifier = outermost wrapper). Each modifier wraps the next, creating nested layers. The order produces fundamentally different visual results.

```
Modifier.padding(16.dp).background(Color.Red).padding(8.dp)

Visual result (outside-in):
+------ 16dp transparent padding ------+
|  +---- red background ----+          |
|  |  +-- 8dp red padding --+          |
|  |  |     CONTENT         |          |
|  |  +---------------------+          |
|  +------------------------+          |
+--------------------------------------+

vs.

Modifier.background(Color.Red).padding(16.dp)

Visual result:
+---- red background ----+
|  +-- 16dp red pad  --+ |
|  |     CONTENT       | |
|  +-------------------+ |
+------------------------+
```

Key principle: **padding before background** creates transparent spacing. **Background before padding** creates colored spacing.

### Modifier Categories

| Category | Modifiers | Notes |
|----------|-----------|-------|
| **Sizing** | `size`, `width`, `height`, `fillMaxWidth`, `fillMaxHeight`, `fillMaxSize`, `wrapContentWidth`, `wrapContentHeight`, `wrapContentSize`, `requiredSize`, `requiredWidth`, `requiredHeight`, `defaultMinSize`, `weight` (Row/Column only), `aspectRatio` | `weight` distributes remaining space. `required*` ignores incoming constraints. |
| **Padding** | `padding` (all, horizontal/vertical, per-side), `absolutePadding`, `offset`, `absoluteOffset` | `offset` does not affect measurement. `padding` affects measurement. |
| **Drawing** | `background`, `border`, `clip`, `shadow`, `alpha`, `drawBehind`, `drawWithContent`, `drawWithCache`, `paint` | `clip` before `background` clips the background shape. |
| **Interaction** | `clickable`, `combinedClickable`, `toggleable`, `selectable`, `draggable`, `swipeable`, `scrollable`, `focusable`, `hoverable` | Always add after sizing/padding. Include semantics. |
| **Layout** | `wrapContentSize`, `align` (in Box), `matchParentSize` (in Box), `alignByBaseline` (in Row), `layoutId`, `onSizeChanged`, `onGloballyPositioned` | `matchParentSize` sizes to parent without affecting parent size. |
| **Graphics** | `graphicsLayer`, `rotate`, `scale` | `graphicsLayer` for hardware-accelerated transforms. |
| **Semantics** | `semantics`, `clearAndSetSemantics`, `testTag`, `contentDescription` | Always set for accessibility. |

---

## Lazy Lists

### Core Rules

1. **Always provide `key`** -- Enables correct recomposition and animation when items change.
2. **Use `contentType`** -- Enables Compose to reuse composables across items of the same type.
3. **Never nest scrollable in same direction** -- `LazyColumn` inside `Column(Modifier.verticalScroll())` crashes.
4. **Use `LazyListState` for scroll control** -- `rememberLazyListState()` for programmatic scrolling.

```kotlin
LazyColumn(
    state = rememberLazyListState(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
) {
    items(
        items = data,
        key = { it.id },
        contentType = { it.type },
    ) { item ->
        ItemCard(item)
    }
}
```

### Lazy Grid and Staggered Grid

```kotlin
LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = 160.dp),
    contentPadding = PaddingValues(8.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
) {
    items(data, key = { it.id }) { item ->
        GridItem(item)
    }
}
```

### Sticky Headers

```kotlin
LazyColumn {
    groupedItems.forEach { (header, items) ->
        stickyHeader(key = "header-$header") {
            HeaderRow(header)
        }
        items(items, key = { it.id }) { item ->
            ItemRow(item)
        }
    }
}
```

---

## Custom Layout

The `Layout` composable gives full control over measurement and placement. It replaces `Row`/`Column`/`Box` when their built-in logic is insufficient.

### The Layout Pipeline

1. **Measure**: Each `Measurable` is measured with `Constraints` to produce a `Placeable`.
2. **Place**: Each `Placeable` is positioned with `place(x, y)` inside a `layout(width, height)` block.

```kotlin
@Composable
fun CustomLayout(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(
        content = content,
        modifier = modifier,
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        val width = placeables.maxOfOrNull { it.width } ?: 0
        val height = placeables.sumOf { it.height }

        layout(width, height) {
            var y = 0
            placeables.forEach { placeable ->
                placeable.placeRelative(0, y)
                y += placeable.height
            }
        }
    }
}
```

### IntrinsicMeasurements

Intrinsics allow a composable to report its preferred size **before** actual measurement. Required when one child's size depends on another child's intrinsic dimensions.

| Method | Returns |
|--------|---------|
| `minIntrinsicWidth(height)` | Minimum width to display content at given height |
| `maxIntrinsicWidth(height)` | Width at which further increase provides no benefit |
| `minIntrinsicHeight(width)` | Minimum height to display content at given width |
| `maxIntrinsicHeight(width)` | Height at which further increase provides no benefit |

Use `Modifier.height(IntrinsicSize.Min)` or `Modifier.width(IntrinsicSize.Min)` to query intrinsics.

---

## SubcomposeLayout

Use `SubcomposeLayout` when one part of the layout needs to be measured **based on the measured size of another part**. Normal `Layout` measures all children in one pass; `SubcomposeLayout` allows multi-pass measurement.

```kotlin
@Composable
fun MeasureThenLayout(
    header: @Composable () -> Unit,
    body: @Composable (headerHeight: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    SubcomposeLayout(modifier) { constraints ->
        val headerPlaceables = subcompose("header") { header() }
            .map { it.measure(constraints) }
        val headerHeight = headerPlaceables.maxOfOrNull { it.height } ?: 0

        val bodyPlaceables = subcompose("body") { body(headerHeight) }
            .map { it.measure(constraints) }

        val width = maxOf(
            headerPlaceables.maxOfOrNull { it.width } ?: 0,
            bodyPlaceables.maxOfOrNull { it.width } ?: 0,
        )
        val height = headerHeight + (bodyPlaceables.maxOfOrNull { it.height } ?: 0)

        layout(width, height) {
            headerPlaceables.forEach { it.placeRelative(0, 0) }
            bodyPlaceables.forEach { it.placeRelative(0, headerHeight) }
        }
    }
}
```

**When to use SubcomposeLayout vs Layout:**
- Use `Layout` when all children can be measured independently.
- Use `SubcomposeLayout` when child B's content or constraints depend on child A's measured size.

---

## Canvas and Drawing

### Canvas Composable

The `Canvas` composable provides a `DrawScope` for imperative drawing.

```kotlin
Canvas(modifier = Modifier.size(200.dp)) {
    drawCircle(
        color = Color.Blue,
        radius = size.minDimension / 2f,
        center = center,
    )
    drawLine(
        color = Color.Red,
        start = Offset.Zero,
        end = Offset(size.width, size.height),
        strokeWidth = 4f,
    )
}
```

### DrawScope API

| Method | Parameters | Description |
|--------|-----------|-------------|
| `drawLine` | color/brush, start, end, strokeWidth, cap, pathEffect | Straight line |
| `drawRect` | color/brush, topLeft, size, style | Rectangle (fill or stroke) |
| `drawRoundRect` | color/brush, topLeft, size, cornerRadius, style | Rounded rectangle |
| `drawCircle` | color/brush, radius, center, style | Circle |
| `drawOval` | color/brush, topLeft, size, style | Oval/ellipse |
| `drawArc` | color/brush, startAngle, sweepAngle, useCenter, topLeft, size, style | Arc or pie slice |
| `drawPath` | path, color/brush, style | Arbitrary path |
| `drawPoints` | points, pointMode, color/brush, strokeWidth | Point set |
| `drawImage` | image, topLeft | Bitmap image |
| `drawIntoCanvas` | block: (Canvas) -> Unit | Raw Canvas access |

### Drawing Modifiers

| Modifier | When Content Draws | Use |
|----------|--------------------|-----|
| `drawBehind { }` | Before content | Backgrounds, underlines |
| `drawWithContent { drawContent(); ... }` | You control order | Overlays, gradients on top |
| `drawWithCache { onDrawBehind { } }` | Cached computations | Expensive Path/Brush creation |

---

## Graphics Layer

`Modifier.graphicsLayer` applies hardware-accelerated transformations. These do NOT trigger re-layout or re-measurement.

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `alpha` | Float | 1f | Opacity (0f = invisible, 1f = opaque) |
| `translationX` | Float | 0f | Horizontal offset in pixels |
| `translationY` | Float | 0f | Vertical offset in pixels |
| `scaleX` | Float | 1f | Horizontal scale |
| `scaleY` | Float | 1f | Vertical scale |
| `rotationX` | Float | 0f | Rotation around X axis (3D tilt) |
| `rotationY` | Float | 0f | Rotation around Y axis (3D flip) |
| `rotationZ` | Float | 0f | Rotation around Z axis (2D rotation) |
| `shadowElevation` | Float | 0f | Shadow depth in pixels |
| `shape` | Shape | RectangleShape | Shape for clipping and shadow |
| `clip` | Boolean | false | Clip content to shape |
| `transformOrigin` | TransformOrigin | Center | Pivot point for transforms |
| `cameraDistance` | Float | 8f | Perspective distance for 3D |
| `compositingStrategy` | CompositingStrategy | Auto | Controls offscreen buffer usage |

```kotlin
Modifier.graphicsLayer {
    rotationZ = 45f
    scaleX = 0.8f
    scaleY = 0.8f
    alpha = 0.9f
    shadowElevation = 8f
    shape = RoundedCornerShape(16.dp)
    clip = true
    transformOrigin = TransformOrigin.Center
}
```

---

## Constraints and Sizing

### How Constraints Propagate

Parent passes `Constraints(minWidth, maxWidth, minHeight, maxHeight)` to children. Children must produce a size within these bounds.

- `fillMaxWidth()` sets `minWidth = maxWidth` (forces child to take all width).
- `wrapContentWidth()` sets `minWidth = 0` (allows child to shrink).
- `requiredWidth(200.dp)` overrides constraints to exactly 200dp.
- `weight(1f)` in Row/Column distributes remaining space proportionally.

### Weight in Row/Column

```kotlin
Row(Modifier.fillMaxWidth()) {
    Box(Modifier.weight(1f).background(Color.Red))    // 1/3
    Box(Modifier.weight(2f).background(Color.Blue))   // 2/3
}
```

Weight is measured AFTER non-weighted children. Non-weighted children are measured first, then remaining space is distributed by weight.

---

## Core Rules

1. **Modifier order is the layout** -- Always think outside-in. `padding` before `background` creates transparent spacing; `background` before `padding` creates colored spacing.
2. **Always provide `key` in lazy lists** -- Without keys, Compose cannot efficiently diff items, causing incorrect animations and state loss.
3. **Use `contentType` in lazy lists** -- Enables item-level view recycling between items of the same type.
4. **Never nest same-direction scrolling** -- A `LazyColumn` inside a `verticalScroll` Column will crash. Use `NestedScrollConnection` for coordinated scrolling.
5. **Prefer `Modifier.graphicsLayer` over layout changes for animations** -- Graphics layer transformations are GPU-accelerated and skip re-layout.
6. **Use `SubcomposeLayout` sparingly** -- It introduces a second composition pass. Prefer `Layout` with intrinsics when possible.
7. **Use `placeRelative` in RTL-aware layouts** -- `place(x, y)` ignores RTL; `placeRelative(x, y)` mirrors for RTL locales.
8. **Accept and forward `Modifier` parameter** -- Every public composable should accept `modifier: Modifier = Modifier` as a parameter and apply it to the root element.
9. **Use `Modifier.drawWithCache` for expensive drawing** -- Path and Brush objects created in `drawBehind` are recreated on every recomposition. `drawWithCache` caches them.
10. **Test with different screen densities** -- Compose Multiplatform renders at device density. Always use `Dp` for sizes, never raw pixels.

## Common Pitfalls

| Pitfall | Wrong | Correct |
|---------|-------|---------|
| Modifier order | `Modifier.clickable { }.padding(16.dp)` (click area includes padding) | `Modifier.padding(16.dp).clickable { }` (click area is content only) or vice versa depending on intent |
| Nested scrolling | `Column(Modifier.verticalScroll(rememberScrollState())) { LazyColumn { } }` | Use `LazyColumn` only, or `NestedScrollConnection` |
| Missing key in lazy | `items(list) { }` | `items(list, key = { it.id }) { }` |
| fillMaxSize in LazyColumn item | Item takes full screen height | Use `wrapContentHeight()` or explicit `height()` |
| Drawing without cache | `Modifier.drawBehind { val path = Path(); ... }` creates Path every frame | `Modifier.drawWithCache { val path = Path(); onDrawBehind { drawPath(path, ...) } }` |
| requiredSize in constrained parent | `requiredSize` overflows parent bounds, content clipped | Use `size` which respects constraints, or clip parent |
| graphicsLayer for simple alpha | `Modifier.graphicsLayer { alpha = 0.5f }` | `Modifier.alpha(0.5f)` -- simpler for just alpha |
| Forgetting Modifier parameter | `@Composable fun MyWidget() { Box { ... } }` | `@Composable fun MyWidget(modifier: Modifier = Modifier) { Box(modifier) { ... } }` |

## Platform Differences (Compose Multiplatform)

| Feature | Android | iOS | Desktop | Web (wasmJs) |
|---------|---------|-----|---------|-------------|
| `WindowInsets` | Full SafeArea, IME, status/nav bars | SafeArea (notch, home indicator) | Window chrome | Viewport only |
| Touch/Pointer | Touch events | Touch events | Mouse + keyboard | Mouse + touch |
| `Modifier.systemBarsPadding()` | Status + navigation bar | Safe area insets | No-op | No-op |
| Hardware acceleration | GPU via RenderThread | Metal | OpenGL/Vulkan | WebGL/Canvas2D |
| `drawIntoCanvas` | Android Canvas | Skia Canvas | Skia Canvas | Skia Canvas (WASM) |
| Text rendering | Native text shaping | Skia text shaping | Skia text shaping | Skia text shaping |
| `Modifier.hoverable` | No hover (touch only) | No hover (touch only) | Full hover support | Hover support |
| Predictive back gesture | API 34+ | Interactive pop (Circuit) | N/A | N/A |

### WindowInsets in KMP

```kotlin
// Common approach for all platforms
Modifier
    .fillMaxSize()
    .windowInsetsPadding(WindowInsets.safeArea)

// Or using Scaffold which handles insets automatically
Scaffold(
    contentWindowInsets = WindowInsets.safeArea,
) { padding ->
    Content(Modifier.padding(padding))
}
```

## See Also

- [compose-runtime-expert](../compose-runtime-expert/SKILL.md) -- Snapshot system, recomposition internals
- [compose-material3-expert](../compose-material3-expert/SKILL.md) -- M3 components, theming, color system
- [compose-animation-expert](../compose-animation-expert/SKILL.md) -- Animations and transitions
- [accessibility-expert](../accessibility-expert/SKILL.md) -- Semantics, content descriptions, a11y testing
- [compose-stability-expert](../compose-stability-expert/SKILL.md) -- Stability, skipping, performance
