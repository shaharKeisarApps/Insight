# Compose UI Examples (Compose Multiplatform 1.10.0)

Production-ready examples covering modifier chains, responsive layouts, custom layouts, lazy lists, Canvas drawing, GraphicsLayer, nested scrolling, SubcomposeLayout, and custom modifiers.

---

## 1. Modifier Chain Order -- Three Visual Results

Three modifier chains on the same content produce fundamentally different visuals due to ordering.

```kotlin
package com.example.ui.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ModifierOrderDemo(modifier: Modifier = Modifier) {
    Column(modifier) {
        // Example A: padding -> background -> padding
        // Result: 16dp transparent margin, then red background, then 8dp inner padding
        Box(
            modifier = Modifier
                .padding(16.dp)             // Outer transparent spacing
                .background(Color.Red, RoundedCornerShape(8.dp))
                .padding(8.dp),             // Inner colored spacing
            contentAlignment = Alignment.Center,
        ) {
            Text("A: pad -> bg -> pad", color = Color.White)
        }

        Spacer(Modifier.height(16.dp))

        // Example B: background -> padding
        // Result: Red fills the entire area, 16dp of red padding around content
        Box(
            modifier = Modifier
                .background(Color.Blue, RoundedCornerShape(8.dp))
                .padding(16.dp),            // Padding is INSIDE the blue area
            contentAlignment = Alignment.Center,
        ) {
            Text("B: bg -> pad", color = Color.White)
        }

        Spacer(Modifier.height(16.dp))

        // Example C: clickable BEFORE vs AFTER padding changes touch target
        // Click area includes the 16dp padding (larger touch target)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { /* tap handler */ }   // Click covers padding + content
                .background(Color.Green)
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("C: clickable wraps padding")
        }

        Spacer(Modifier.height(16.dp))

        // Contrast: clickable AFTER padding -- click area is content only
        Box(
            modifier = Modifier
                .padding(16.dp)                    // Transparent margin, not clickable
                .clip(RoundedCornerShape(8.dp))
                .clickable { /* tap handler */ }   // Click is content area only
                .background(Color.Magenta)
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("C': clickable inside padding", color = Color.White)
        }
    }
}
```

---

## 2. Responsive Layout with BoxWithConstraints

Adapts between single-column phone layout and two-pane tablet layout based on available width.

```kotlin
package com.example.ui.responsive

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ResponsiveLayout(
    listContent: @Composable (Modifier) -> Unit,
    detailContent: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        when {
            // Phone portrait: single pane
            maxWidth < 600.dp -> {
                Column(Modifier.fillMaxSize()) {
                    listContent(Modifier.fillMaxWidth())
                }
            }
            // Tablet portrait or phone landscape: 40/60 split
            maxWidth < 900.dp -> {
                Row(Modifier.fillMaxSize()) {
                    listContent(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.4f)
                            .padding(end = 8.dp),
                    )
                    detailContent(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(),   // Takes remaining 60%
                    )
                }
            }
            // Tablet landscape or desktop: 30/70 split with cards
            else -> {
                Row(Modifier.fillMaxSize().padding(16.dp)) {
                    Card(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.3f)
                            .padding(end = 12.dp),
                    ) {
                        listContent(Modifier.fillMaxSize().padding(8.dp))
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(),
                    ) {
                        detailContent(Modifier.fillMaxSize().padding(8.dp))
                    }
                }
            }
        }
    }
}
```

---

## 3. Custom Layout -- FlowRow (Wrapping Children)

A custom `Layout` that places children horizontally and wraps to the next line when the row is full.

```kotlin
package com.example.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalSpacing: Dp = 8.dp,
    verticalSpacing: Dp = 8.dp,
    content: @Composable () -> Unit,
) {
    Layout(
        content = content,
        modifier = modifier,
    ) { measurables, constraints ->
        val hSpacingPx = horizontalSpacing.roundToPx()
        val vSpacingPx = verticalSpacing.roundToPx()

        // Measure all children with at most the parent width
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }

        // Calculate rows
        val rows = mutableListOf<List<Placeable>>()
        var currentRow = mutableListOf<Placeable>()
        var currentRowWidth = 0

        placeables.forEach { placeable ->
            val itemWidth = placeable.width
            val neededWidth = if (currentRow.isEmpty()) itemWidth else itemWidth + hSpacingPx

            if (currentRowWidth + neededWidth > constraints.maxWidth && currentRow.isNotEmpty()) {
                // Wrap to next line
                rows.add(currentRow.toList())
                currentRow = mutableListOf(placeable)
                currentRowWidth = itemWidth
            } else {
                currentRow.add(placeable)
                currentRowWidth += neededWidth
            }
        }
        if (currentRow.isNotEmpty()) {
            rows.add(currentRow.toList())
        }

        // Calculate total height
        val rowHeights = rows.map { row -> row.maxOf { it.height } }
        val totalHeight = rowHeights.sum() + max(0, (rows.size - 1)) * vSpacingPx
        val totalWidth = constraints.maxWidth

        layout(totalWidth, totalHeight) {
            var y = 0
            rows.forEachIndexed { rowIndex, row ->
                var x = 0
                row.forEach { placeable ->
                    placeable.placeRelative(x, y)
                    x += placeable.width + hSpacingPx
                }
                y += rowHeights[rowIndex] + vSpacingPx
            }
        }
    }
}

// Usage
@Composable
fun TagCloud(tags: List<String>, modifier: Modifier = Modifier) {
    FlowRow(modifier = modifier, horizontalSpacing = 6.dp, verticalSpacing = 6.dp) {
        tags.forEach { tag ->
            Text(
                text = tag,
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.secondaryContainer,
                        RoundedCornerShape(16.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
```

---

## 4. LazyColumn with Sticky Headers, Keys, and Content Types

```kotlin
package com.example.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class Contact(val id: String, val name: String, val isOnline: Boolean)

enum class ContactContentType { HEADER, ONLINE_CONTACT, OFFLINE_CONTACT }

@Composable
fun ContactList(
    contacts: List<Contact>,
    modifier: Modifier = Modifier,
) {
    val grouped = contacts.groupBy { it.name.first().uppercaseChar() }
        .toSortedMap()

    LazyColumn(
        modifier = modifier,
        state = rememberLazyListState(),
    ) {
        grouped.forEach { (letter, contactsInGroup) ->
            // Sticky header with key
            stickyHeader(
                key = "header-$letter",
                contentType = ContactContentType.HEADER,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = letter.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Items with unique keys and content types
            items(
                items = contactsInGroup,
                key = { contact -> contact.id },
                contentType = { contact ->
                    if (contact.isOnline) ContactContentType.ONLINE_CONTACT
                    else ContactContentType.OFFLINE_CONTACT
                },
            ) { contact ->
                ListItem(
                    headlineContent = { Text(contact.name) },
                    supportingContent = {
                        Text(if (contact.isOnline) "Online" else "Offline")
                    },
                )
                HorizontalDivider()
            }
        }
    }
}
```

---

## 5. Canvas Drawing -- Custom Arc Progress Indicator

```kotlin
package com.example.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ArcProgressIndicator(
    progress: Float,  // 0f..1f
    modifier: Modifier = Modifier,
    trackColor: Color = Color.LightGray,
    progressColor: Color = Color.Blue,
    strokeWidth: Float = 12f,
) {
    val textMeasurer = rememberTextMeasurer()
    val percentText = "${(progress * 100).toInt()}%"

    Canvas(modifier = modifier.size(120.dp)) {
        val diameter = size.minDimension
        val radius = diameter / 2f
        val strokeHalf = strokeWidth / 2f
        val arcSize = Size(diameter - strokeWidth, diameter - strokeWidth)
        val arcTopLeft = Offset(strokeHalf, strokeHalf)

        // Background track (full circle arc, starting from top = -90 degrees)
        drawArc(
            color = trackColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )

        // Progress arc
        drawArc(
            color = progressColor,
            startAngle = -90f,
            sweepAngle = 360f * progress,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )

        // Center percentage text
        val textLayoutResult = textMeasurer.measure(
            text = percentText,
            style = androidx.compose.ui.text.TextStyle(fontSize = 24.sp),
        )
        drawText(
            textLayoutResult = textLayoutResult,
            topLeft = Offset(
                x = center.x - textLayoutResult.size.width / 2f,
                y = center.y - textLayoutResult.size.height / 2f,
            ),
        )
    }
}
```

---

## 6. GraphicsLayer -- 3D Card Flip Effect

Uses `rotationY` for a 3D flip with `cameraDistance` for perspective. Switches content at 90 degrees.

```kotlin
package com.example.ui.animation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
fun FlipCard(
    frontContent: @Composable () -> Unit,
    backContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFlipped by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "cardFlip",
    )

    Card(
        modifier = modifier
            .size(200.dp, 280.dp)
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density  // Scale camera distance with density
            }
            .clickable { isFlipped = !isFlipped },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        // Show front content for 0-89 degrees, back content for 90-180 degrees
        if (rotation <= 90f) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                frontContent()
            }
        } else {
            // Back content is mirrored, so counter-rotate it
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationY = 180f },
                contentAlignment = Alignment.Center,
            ) {
                backContent()
            }
        }
    }
}

// Usage
@Composable
fun FlipCardDemo(modifier: Modifier = Modifier) {
    FlipCard(
        modifier = modifier,
        frontContent = {
            Text("Front", style = MaterialTheme.typography.headlineMedium)
        },
        backContent = {
            Text("Back", style = MaterialTheme.typography.headlineMedium)
        },
    )
}
```

---

## 7. Nested Scrolling -- Collapsing Toolbar

Uses `NestedScrollConnection` to collapse a header when scrolling the list content.

```kotlin
package com.example.ui.scroll

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun CollapsingToolbarLayout(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val toolbarHeightDp = 200.dp
    val minToolbarHeightDp = 56.dp
    val density = LocalDensity.current
    val toolbarHeightPx = with(density) { toolbarHeightDp.toPx() }
    val minToolbarHeightPx = with(density) { minToolbarHeightDp.toPx() }

    var toolbarOffsetPx by remember { mutableFloatStateOf(0f) }
    val maxOffset = -(toolbarHeightPx - minToolbarHeightPx)

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val newOffset = toolbarOffsetPx + delta
                toolbarOffsetPx = newOffset.coerceIn(maxOffset, 0f)
                // Consume scroll only when toolbar is collapsing/expanding
                return if (newOffset in maxOffset..0f) {
                    Offset(0f, delta)
                } else {
                    Offset.Zero
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
    ) {
        // Scrollable content offset by toolbar height
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset {
                    IntOffset(0, (toolbarHeightPx + toolbarOffsetPx).roundToInt())
                },
        ) {
            content()
        }

        // Collapsing toolbar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(toolbarHeightDp)
                .offset { IntOffset(0, toolbarOffsetPx.roundToInt()) }
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.BottomStart,
        ) {
            Text(
                text = title,
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.headlineMedium,
            )
        }
    }
}

// Usage
@Composable
fun CollapsingToolbarDemo(modifier: Modifier = Modifier) {
    CollapsingToolbarLayout(title = "My App", modifier = modifier) {
        LazyColumn {
            items(50) { index ->
                ListItem(headlineContent = { Text("Item $index") })
            }
        }
    }
}
```

---

## 8. SubcomposeLayout -- Measure Text to Size Container

Measures a label's width first, then sizes the container to match. The badge width dynamically fits the text content.

```kotlin
package com.example.ui.subcompose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

@Composable
fun DynamicBadge(
    badgeText: String,
    content: @Composable (badgeWidth: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    SubcomposeLayout(modifier) { constraints ->
        // Phase 1: Measure the badge text to know its width
        val badgePlaceables = subcompose("badge") {
            Text(
                text = badgeText,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.error)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                color = MaterialTheme.colorScheme.onError,
                style = MaterialTheme.typography.labelSmall,
            )
        }.map { it.measure(constraints) }

        val badgeWidth = badgePlaceables.maxOfOrNull { it.width } ?: 0
        val badgeHeight = badgePlaceables.maxOfOrNull { it.height } ?: 0

        // Phase 2: Compose content that knows the badge width
        val contentPlaceables = subcompose("content") {
            content(badgeWidth)
        }.map { it.measure(constraints) }

        val contentWidth = contentPlaceables.maxOfOrNull { it.width } ?: 0
        val contentHeight = contentPlaceables.maxOfOrNull { it.height } ?: 0

        // Layout: content at bottom-left, badge at top-right overlapping
        val totalWidth = maxOf(contentWidth, badgeWidth)
        val totalHeight = contentHeight + badgeHeight / 2  // Badge overlaps half

        layout(totalWidth, totalHeight) {
            contentPlaceables.forEach { it.placeRelative(0, badgeHeight / 2) }
            badgePlaceables.forEach {
                it.placeRelative(contentWidth - badgeWidth, 0)
            }
        }
    }
}

// Usage
@Composable
fun DynamicBadgeDemo(modifier: Modifier = Modifier) {
    DynamicBadge(
        badgeText = "NEW",
        modifier = modifier,
        content = { badgeWidth ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(16.dp)
                    // Ensure right padding accommodates badge
                    .padding(end = (badgeWidth / 2).dp),
            ) {
                Text("Product Card", style = MaterialTheme.typography.bodyLarge)
            }
        },
    )
}
```

---

## 9. Custom Modifier -- Shimmer Loading Effect

Uses `drawWithContent` with a linear gradient that translates across the composable.

```kotlin
package com.example.ui.modifier

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

fun Modifier.shimmer(
    isLoading: Boolean = true,
    shimmerColor: Color = Color.White.copy(alpha = 0.6f),
    durationMillis: Int = 1200,
): Modifier = composed {
    if (!isLoading) return@composed this

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateX by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerTranslateX",
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            Color.Transparent,
            shimmerColor,
            Color.Transparent,
        ),
        start = Offset(translateX * 1000f, 0f),
        end = Offset(translateX * 1000f + 500f, 0f),
    )

    this
        .graphicsLayer {
            // Required for BlendMode.SrcAtop to work correctly
            compositingStrategy = CompositingStrategy.Offscreen
        }
        .drawWithContent {
            drawContent()
            drawRect(
                brush = shimmerBrush,
                blendMode = BlendMode.SrcAtop,
            )
        }
}

// Usage: Skeleton loading placeholder
@Composable
fun LoadingSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(16.dp)) {
        // Title placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(24.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.LightGray)
                .shimmer(),
        )
        Spacer(Modifier.height(12.dp))

        // Subtitle placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.LightGray)
                .shimmer(),
        )
        Spacer(Modifier.height(16.dp))

        // Content lines
        repeat(3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.LightGray)
                    .shimmer(),
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
```
