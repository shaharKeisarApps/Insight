# Compose UI API Reference (Compose Multiplatform 1.10.0)

Comprehensive API reference for layout composables, modifier catalog, custom layout primitives, Canvas/DrawScope, and GraphicsLayer.

---

## Layout Composables

### Row

```kotlin
@Composable
inline fun Row(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    content: @Composable RowScope.() -> Unit,
)
```

**RowScope modifiers:** `weight(weight: Float, fill: Boolean = true)`, `align(alignment: Alignment.Vertical)`, `alignByBaseline()`, `alignBy(alignmentLine: HorizontalAlignmentLine)`

### Column

```kotlin
@Composable
inline fun Column(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
)
```

**ColumnScope modifiers:** `weight(weight: Float, fill: Boolean = true)`, `align(alignment: Alignment.Horizontal)`, `alignBy(alignmentLine: VerticalAlignmentLine)`

### Box

```kotlin
@Composable
inline fun Box(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    propagateMinConstraints: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
)

// Single-child shorthand (no BoxScope)
@Composable
fun Box(modifier: Modifier)
```

**BoxScope modifiers:** `align(alignment: Alignment)`, `matchParentSize()`

### BoxWithConstraints

```kotlin
@Composable
fun BoxWithConstraints(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    propagateMinConstraints: Boolean = false,
    content: @Composable BoxWithConstraintsScope.() -> Unit,
)
```

**BoxWithConstraintsScope properties:** `constraints: Constraints`, `minWidth: Dp`, `maxWidth: Dp`, `minHeight: Dp`, `maxHeight: Dp`

### Spacer

```kotlin
@Composable
fun Spacer(modifier: Modifier)
```

Common patterns: `Spacer(Modifier.height(16.dp))`, `Spacer(Modifier.weight(1f))` (in Row/Column), `Spacer(Modifier.width(8.dp))`

---

## Lazy List Composables

### LazyColumn

```kotlin
@Composable
fun LazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical = if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    content: LazyListScope.() -> Unit,
)
```

### LazyRow

```kotlin
@Composable
fun LazyRow(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    horizontalArrangement: Arrangement.Horizontal = if (!reverseLayout) Arrangement.Start else Arrangement.End,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    content: LazyListScope.() -> Unit,
)
```

### LazyListScope

```kotlin
interface LazyListScope {
    fun item(
        key: Any? = null,
        contentType: Any? = null,
        content: @Composable LazyItemScope.() -> Unit,
    )

    fun items(
        count: Int,
        key: ((index: Int) -> Any)? = null,
        contentType: (index: Int) -> Any? = { null },
        itemContent: @Composable LazyItemScope.(index: Int) -> Unit,
    )

    fun stickyHeader(
        key: Any? = null,
        contentType: Any? = null,
        content: @Composable LazyItemScope.() -> Unit,
    )
}

// Extension functions
inline fun <T> LazyListScope.items(
    items: List<T>,
    noinline key: ((item: T) -> Any)? = null,
    noinline contentType: (item: T) -> Any? = { null },
    crossinline itemContent: @Composable LazyItemScope.(item: T) -> Unit,
)

inline fun <T> LazyListScope.itemsIndexed(
    items: List<T>,
    noinline key: ((index: Int, item: T) -> Any)? = null,
    crossinline contentType: (index: Int, item: T) -> Any? = { _, _ -> null },
    crossinline itemContent: @Composable LazyItemScope.(index: Int, item: T) -> Unit,
)
```

### LazyVerticalGrid

```kotlin
@Composable
fun LazyVerticalGrid(
    columns: GridCells,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical = if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    content: LazyGridScope.() -> Unit,
)
```

### GridCells

```kotlin
sealed interface GridCells {
    class Fixed(val count: Int) : GridCells
    class Adaptive(val minSize: Dp) : GridCells
    class FixedSize(val size: Dp) : GridCells
}
```

### LazyVerticalStaggeredGrid

```kotlin
@Composable
fun LazyVerticalStaggeredGrid(
    columns: StaggeredGridCells,
    modifier: Modifier = Modifier,
    state: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalItemSpacing: Dp = 0.dp,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    content: LazyStaggeredGridScope.() -> Unit,
)
```

---

## Custom Layout API

### Layout

```kotlin
@Composable
inline fun Layout(
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    measurePolicy: MeasurePolicy,
)

// MeasurePolicy
fun interface MeasurePolicy {
    fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult
}
```

### Measurable

```kotlin
interface Measurable : IntrinsicMeasurable {
    fun measure(constraints: Constraints): Placeable
}

interface IntrinsicMeasurable {
    val parentData: Any?
    fun minIntrinsicWidth(height: Int): Int
    fun maxIntrinsicWidth(height: Int): Int
    fun minIntrinsicHeight(width: Int): Int
    fun maxIntrinsicHeight(width: Int): Int
}
```

### Constraints

```kotlin
@Immutable
inline class Constraints(val value: Long) {
    val minWidth: Int
    val maxWidth: Int
    val minHeight: Int
    val maxHeight: Int
    val hasBoundedWidth: Boolean
    val hasBoundedHeight: Boolean
    val hasFixedWidth: Boolean   // minWidth == maxWidth
    val hasFixedHeight: Boolean  // minHeight == maxHeight

    companion object {
        fun fixed(width: Int, height: Int): Constraints
        fun fixedWidth(width: Int): Constraints
        fun fixedHeight(height: Int): Constraints
    }
}

// Extension function
fun Constraints.constrain(size: IntSize): IntSize
fun Constraints.constrainWidth(width: Int): Int
fun Constraints.constrainHeight(height: Int): Int
fun Constraints.offset(horizontal: Int = 0, vertical: Int = 0): Constraints
```

### Placeable

```kotlin
abstract class Placeable {
    val width: Int
    val height: Int
    val measuredWidth: Int
    val measuredHeight: Int

    // Inside PlacementScope
    abstract fun placeAt(position: IntOffset, zIndex: Float, layerBlock: (GraphicsLayerScope.() -> Unit)?)
}

// PlacementScope methods
interface PlacementScope {
    fun Placeable.place(x: Int, y: Int, zIndex: Float = 0f)
    fun Placeable.placeRelative(x: Int, y: Int, zIndex: Float = 0f)   // RTL-aware
    fun Placeable.placeWithLayer(x: Int, y: Int, zIndex: Float = 0f, layerBlock: GraphicsLayerScope.() -> Unit = {})
    fun Placeable.placeRelativeWithLayer(x: Int, y: Int, zIndex: Float = 0f, layerBlock: GraphicsLayerScope.() -> Unit = {})
}
```

### MeasureResult

```kotlin
// Created by MeasureScope.layout()
fun MeasureScope.layout(
    width: Int,
    height: Int,
    alignmentLines: Map<AlignmentLine, Int> = emptyMap(),
    placementBlock: Placeable.PlacementScope.() -> Unit,
): MeasureResult
```

### IntrinsicSize

```kotlin
enum class IntrinsicSize { Min, Max }

// Usage
Modifier.height(IntrinsicSize.Min)
Modifier.width(IntrinsicSize.Min)
Modifier.height(IntrinsicSize.Max)
Modifier.width(IntrinsicSize.Max)
```

---

## SubcomposeLayout

```kotlin
@Composable
fun SubcomposeLayout(
    modifier: Modifier = Modifier,
    measurePolicy: SubcomposeMeasureScope.(Constraints) -> MeasureResult,
)

interface SubcomposeMeasureScope : MeasureScope {
    fun subcompose(slotId: Any?, content: @Composable () -> Unit): List<Measurable>
}
```

---

## Canvas / DrawScope API

### Canvas Composable

```kotlin
@Composable
fun Canvas(
    modifier: Modifier,
    onDraw: DrawScope.() -> Unit,
)
```

### DrawScope

```kotlin
interface DrawScope : Density {
    val drawContext: DrawContext
    val center: Offset        // Center of the drawing area
    val size: Size            // Size of the drawing area
    val layoutDirection: LayoutDirection

    // Drawing primitives
    fun drawLine(color: Color, start: Offset, end: Offset, strokeWidth: Float = Stroke.HairlineWidth, cap: StrokeCap = Stroke.DefaultCap, pathEffect: PathEffect? = null, alpha: Float = 1.0f, colorFilter: ColorFilter? = null, blendMode: BlendMode = DefaultBlendMode)
    fun drawLine(brush: Brush, start: Offset, end: Offset, strokeWidth: Float = Stroke.HairlineWidth, cap: StrokeCap = Stroke.DefaultCap, pathEffect: PathEffect? = null, alpha: Float = 1.0f, colorFilter: ColorFilter? = null, blendMode: BlendMode = DefaultBlendMode)

    fun drawRect(color: Color, topLeft: Offset = Offset.Zero, size: Size = this.size.offsetSize(topLeft), alpha: Float = 1.0f, style: DrawStyle = Fill, colorFilter: ColorFilter? = null, blendMode: BlendMode = DefaultBlendMode)
    fun drawRect(brush: Brush, topLeft: Offset = Offset.Zero, size: Size = this.size.offsetSize(topLeft), alpha: Float = 1.0f, style: DrawStyle = Fill, colorFilter: ColorFilter? = null, blendMode: BlendMode = DefaultBlendMode)

    fun drawRoundRect(color: Color, topLeft: Offset = Offset.Zero, size: Size = this.size.offsetSize(topLeft), cornerRadius: CornerRadius = CornerRadius.Zero, alpha: Float = 1.0f, style: DrawStyle = Fill, colorFilter: ColorFilter? = null, blendMode: BlendMode = DefaultBlendMode)

    fun drawCircle(color: Color, radius: Float = size.minDimension / 2.0f, center: Offset = this.center, alpha: Float = 1.0f, style: DrawStyle = Fill, colorFilter: ColorFilter? = null, blendMode: BlendMode = DefaultBlendMode)

    fun drawOval(color: Color, topLeft: Offset = Offset.Zero, size: Size = this.size.offsetSize(topLeft), alpha: Float = 1.0f, style: DrawStyle = Fill, colorFilter: ColorFilter? = null, blendMode: BlendMode = DefaultBlendMode)

    fun drawArc(color: Color, startAngle: Float, sweepAngle: Float, useCenter: Boolean, topLeft: Offset = Offset.Zero, size: Size = this.size.offsetSize(topLeft), alpha: Float = 1.0f, style: DrawStyle = Fill, colorFilter: ColorFilter? = null, blendMode: BlendMode = DefaultBlendMode)

    fun drawPath(path: Path, color: Color, alpha: Float = 1.0f, style: DrawStyle = Fill, colorFilter: ColorFilter? = null, blendMode: BlendMode = DefaultBlendMode)

    fun drawPoints(points: List<Offset>, pointMode: PointMode, color: Color, strokeWidth: Float = Stroke.HairlineWidth, cap: StrokeCap = StrokeCap.Butt, pathEffect: PathEffect? = null, alpha: Float = 1.0f, colorFilter: ColorFilter? = null, blendMode: BlendMode = DefaultBlendMode)

    fun drawImage(image: ImageBitmap, topLeft: Offset = Offset.Zero, alpha: Float = 1.0f, style: DrawStyle = Fill, colorFilter: ColorFilter? = null, blendMode: BlendMode = DefaultBlendMode)

    fun drawIntoCanvas(block: (Canvas) -> Unit)
}
```

### DrawStyle

```kotlin
sealed class DrawStyle
object Fill : DrawStyle()
data class Stroke(
    val width: Float = 0.0f,
    val miter: Float = DefaultMiter,
    val cap: StrokeCap = DefaultCap,
    val join: StrokeJoin = DefaultJoin,
    val pathEffect: PathEffect? = null,
) : DrawStyle()
```

### Brush

```kotlin
// Linear gradient
Brush.linearGradient(colors: List<Color>, start: Offset = Offset.Zero, end: Offset = Offset.Infinite, tileMode: TileMode = TileMode.Clamp)

// Radial gradient
Brush.radialGradient(colors: List<Color>, center: Offset = Offset.Unspecified, radius: Float = Float.POSITIVE_INFINITY, tileMode: TileMode = TileMode.Clamp)

// Sweep gradient
Brush.sweepGradient(colors: List<Color>, center: Offset = Offset.Unspecified)

// Vertical/Horizontal gradient (convenience)
Brush.verticalGradient(colors: List<Color>, startY: Float = 0.0f, endY: Float = Float.POSITIVE_INFINITY)
Brush.horizontalGradient(colors: List<Color>, startX: Float = 0.0f, endX: Float = Float.POSITIVE_INFINITY)
```

### Path

```kotlin
interface Path {
    fun moveTo(x: Float, y: Float)
    fun relativeMoveTo(dx: Float, dy: Float)
    fun lineTo(x: Float, y: Float)
    fun relativeLineTo(dx: Float, dy: Float)
    fun quadraticBezierTo(x1: Float, y1: Float, x2: Float, y2: Float)
    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float)
    fun arcTo(rect: Rect, startAngleDegrees: Float, sweepAngleDegrees: Float, forceMoveTo: Boolean)
    fun addRect(rect: Rect)
    fun addOval(oval: Rect)
    fun addArc(oval: Rect, startAngleDegrees: Float, sweepAngleDegrees: Float)
    fun addRoundRect(roundRect: RoundRect)
    fun addPath(path: Path, offset: Offset = Offset.Zero)
    fun close()
    fun reset()
    fun op(path1: Path, path2: Path, operation: PathOperation): Boolean
}
```

---

## GraphicsLayer Properties

```kotlin
fun Modifier.graphicsLayer(block: GraphicsLayerScope.() -> Unit): Modifier

interface GraphicsLayerScope : Density {
    var alpha: Float                          // 0f..1f, default 1f
    var translationX: Float                   // pixels, default 0f
    var translationY: Float                   // pixels, default 0f
    var scaleX: Float                         // multiplier, default 1f
    var scaleY: Float                         // multiplier, default 1f
    var rotationX: Float                      // degrees, default 0f (3D tilt forward/back)
    var rotationY: Float                      // degrees, default 0f (3D flip left/right)
    var rotationZ: Float                      // degrees, default 0f (2D rotation)
    var shadowElevation: Float                // pixels, default 0f
    var ambientShadowColor: Color             // default Black
    var spotShadowColor: Color                // default Black
    var shape: Shape                          // default RectangleShape
    var clip: Boolean                         // default false
    var transformOrigin: TransformOrigin      // default Center
    var cameraDistance: Float                  // default 8f (perspective)
    var compositingStrategy: CompositingStrategy // default Auto
    var renderEffect: RenderEffect?           // default null (blur, etc.)
    var size: Size                            // read-only: current layer size
}

// CompositingStrategy
enum class CompositingStrategy {
    Auto,           // System decides (default)
    Offscreen,      // Forces offscreen buffer (needed for BlendMode)
    ModulateAlpha,  // Applies alpha to each draw call individually
}

// TransformOrigin
data class TransformOrigin(val pivotFractionX: Float, val pivotFractionY: Float) {
    companion object {
        val Center: TransformOrigin = TransformOrigin(0.5f, 0.5f)
    }
}
```

---

## Alignment and Arrangement

### Alignment

```kotlin
// 2D Alignment (for Box)
object Alignment {
    val TopStart: Alignment
    val TopCenter: Alignment
    val TopEnd: Alignment
    val CenterStart: Alignment
    val Center: Alignment
    val CenterEnd: Alignment
    val BottomStart: Alignment
    val BottomCenter: Alignment
    val BottomEnd: Alignment

    // 1D Vertical (for Row's verticalAlignment)
    interface Vertical {
        val Top: Vertical
        val CenterVertically: Vertical
        val Bottom: Vertical
    }

    // 1D Horizontal (for Column's horizontalAlignment)
    interface Horizontal {
        val Start: Horizontal
        val CenterHorizontally: Horizontal
        val End: Horizontal
    }
}
```

### Arrangement

```kotlin
object Arrangement {
    // Horizontal
    val Start: Horizontal
    val End: Horizontal
    val Center: Horizontal
    val SpaceBetween: Horizontal      // No space at edges, equal space between
    val SpaceAround: Horizontal       // Half space at edges, equal space between
    val SpaceEvenly: Horizontal       // Equal space everywhere

    // Vertical
    val Top: Vertical
    val Bottom: Vertical
    val Center: Vertical              // Also exists for Vertical
    val SpaceBetween: Vertical
    val SpaceAround: Vertical
    val SpaceEvenly: Vertical

    // Fixed spacing
    fun spacedBy(space: Dp): HorizontalOrVertical
    fun spacedBy(space: Dp, alignment: Alignment.Horizontal): Horizontal
    fun spacedBy(space: Dp, alignment: Alignment.Vertical): Vertical

    // Absolute (ignores RTL)
    object Absolute {
        val Left: Horizontal
        val Right: Horizontal
        val Center: Horizontal
        val SpaceBetween: Horizontal
        val SpaceAround: Horizontal
        val SpaceEvenly: Horizontal
        fun spacedBy(space: Dp): HorizontalOrVertical
    }
}
```

---

## WindowInsets (Compose Multiplatform)

```kotlin
object WindowInsets {
    // Common across platforms
    val safeArea: WindowInsets            // Safe drawing area (notch, home indicator, status bar)
    val safeDrawing: WindowInsets         // Same as safeArea on most platforms
    val safeContent: WindowInsets         // Content-safe area
    val safeGestures: WindowInsets        // Gesture-safe area

    // Android-specific (no-op on other platforms)
    val statusBars: WindowInsets          // Top system bar
    val navigationBars: WindowInsets      // Bottom/side nav bar
    val systemBars: WindowInsets          // statusBars + navigationBars
    val ime: WindowInsets                 // Software keyboard
    val displayCutout: WindowInsets       // Notch/cutout area
    val captionBar: WindowInsets          // Freeform window caption
    val systemGestures: WindowInsets      // System gesture zones
    val mandatorySystemGestures: WindowInsets
    val tappableElement: WindowInsets
    val waterfall: WindowInsets           // Waterfall display edges
}

// Modifier extensions
fun Modifier.windowInsetsPadding(insets: WindowInsets): Modifier
fun Modifier.safeAreaPadding(): Modifier
fun Modifier.statusBarsPadding(): Modifier         // Android
fun Modifier.navigationBarsPadding(): Modifier     // Android
fun Modifier.systemBarsPadding(): Modifier         // Android
fun Modifier.imePadding(): Modifier                // Android
fun Modifier.safeDrawingPadding(): Modifier
fun Modifier.safeContentPadding(): Modifier
fun Modifier.safeGesturesPadding(): Modifier

// Consuming insets (prevents double-padding)
fun Modifier.consumeWindowInsets(insets: WindowInsets): Modifier
fun Modifier.consumeWindowInsets(paddingValues: PaddingValues): Modifier
```

---

## Key Imports

```kotlin
// Layout
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeArea

// Lazy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells

// Drawing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawWithCache

// Graphics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.RenderEffect

// Layout internals
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

// Interaction
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource

// Geometry
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect

// Shape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.ui.graphics.RectangleShape

// Scroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.nestedScroll
```
