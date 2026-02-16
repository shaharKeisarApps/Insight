# Coil 3.3.0 API Reference (KMP)

## Gradle Dependencies

```toml
# gradle/libs.versions.toml
[versions]
coil = "3.3.0"
ktor = "3.1.0"

[libraries]
coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil" }
coil-compose-core = { module = "io.coil-kt.coil3:coil-compose-core", version.ref = "coil" }
coil-core = { module = "io.coil-kt.coil3:coil-core", version.ref = "coil" }
coil-network-ktor3 = { module = "io.coil-kt.coil3:coil-network-ktor3", version.ref = "coil" }
coil-network-okhttp = { module = "io.coil-kt.coil3:coil-network-okhttp", version.ref = "coil" }
coil-svg = { module = "io.coil-kt.coil3:coil-svg", version.ref = "coil" }
coil-gif = { module = "io.coil-kt.coil3:coil-gif", version.ref = "coil" }
coil-test = { module = "io.coil-kt.coil3:coil-test", version.ref = "coil" }
```

### Build file setup (commonMain)

```kotlin
// build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
        }
        commonTest.dependencies {
            implementation(libs.coil.test)
        }
        androidMain.dependencies {
            // Optional: use OkHttp instead of Ktor on Android
            // implementation(libs.coil.network.okhttp)
            implementation(libs.coil.gif)
        }
    }
}
```

---

## Key Imports

```kotlin
// Core
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.request.ErrorResult
import coil3.request.CachePolicy

// Compose
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter

// Cache
import coil3.disk.DiskCache
import coil3.memory.MemoryCache

// Transformations
import coil3.transform.Transformation
import coil3.transform.CircleCropTransformation
import coil3.transform.RoundedCornersTransformation

// Fetcher / Decoder
import coil3.fetch.Fetcher
import coil3.fetch.FetchResult
import coil3.fetch.SourceFetchResult
import coil3.decode.Decoder
import coil3.decode.DecodeResult

// SVG
import coil3.svg.SvgDecoder

// GIF (Android)
import coil3.gif.GifDecoder
import coil3.gif.AnimatedImageDecoder

// Testing
import coil3.test.FakeImageLoaderEngine

// Singleton setup
import coil3.compose.setSingletonImageLoaderFactory
```

---

## PlatformContext

`PlatformContext` is Coil 3's multiplatform abstraction replacing Android's `Context`.

| Platform | Type | Access |
|----------|------|--------|
| Android | `android.content.Context` | Pass any `Context` (Application, Activity) |
| iOS | Singleton object | `PlatformContext.INSTANCE` |
| Desktop (JVM) | Singleton object | `PlatformContext.INSTANCE` |
| JS / wasmJs | Singleton object | `PlatformContext.INSTANCE` |
| Compose Multiplatform | CompositionLocal | `LocalPlatformContext.current` |

```kotlin
// Android
val platformContext: PlatformContext = context

// Non-Android (iOS, Desktop, JS)
val platformContext: PlatformContext = PlatformContext.INSTANCE

// Compose Multiplatform (all platforms)
@Composable
fun MyComposable() {
    val platformContext = LocalPlatformContext.current
}
```

---

## ImageLoader

The central class that executes `ImageRequest`s. Create a single shared instance per app.

### ImageLoader.Builder

```kotlin
ImageLoader.Builder(context: PlatformContext)
```

| Method | Type | Description |
|--------|------|-------------|
| `.crossfade(enable)` | `Boolean` | Enable crossfade animation (default: `false`) |
| `.crossfade(durationMillis)` | `Int` | Crossfade duration in ms (enables crossfade) |
| `.placeholder(image)` | `Image?` | Default placeholder for all requests |
| `.placeholder(factory)` | `(ImageRequest) -> Image?` | Factory for default placeholder |
| `.error(image)` | `Image?` | Default error image for all requests |
| `.error(factory)` | `(ImageRequest) -> Image?` | Factory for default error image |
| `.fallback(image)` | `Image?` | Default fallback for null data |
| `.fallback(factory)` | `(ImageRequest) -> Image?` | Factory for default fallback |
| `.memoryCachePolicy(policy)` | `CachePolicy` | Default memory cache policy |
| `.diskCachePolicy(policy)` | `CachePolicy` | Default disk cache policy |
| `.networkCachePolicy(policy)` | `CachePolicy` | Default network cache policy |
| `.memoryCache(cache)` | `MemoryCache?` | Set memory cache instance |
| `.memoryCache(factory)` | `() -> MemoryCache?` | Lazy memory cache factory |
| `.diskCache(cache)` | `DiskCache?` | Set disk cache instance |
| `.diskCache(factory)` | `() -> DiskCache?` | Lazy disk cache factory |
| `.components(builder)` | `ComponentRegistry.Builder.() -> Unit` | Register Fetchers, Decoders, Interceptors, Mappers, Keyers |
| `.coroutineContext(ctx)` | `CoroutineContext` | Default coroutine context for pipeline |
| `.decoderCoroutineContext(ctx)` | `CoroutineContext` | Coroutine context for decoders |
| `.fetcherCoroutineContext(ctx)` | `CoroutineContext` | Coroutine context for fetchers |
| `.interceptorCoroutineContext(ctx)` | `CoroutineContext` | Coroutine context for interceptors |
| `.precision(precision)` | `Precision` | Size matching precision (`EXACT`, `INEXACT`) |
| `.logger(logger)` | `Logger?` | Logger for debug output |
| `.eventListener(listener)` | `EventListener` | Global event listener |
| `.eventListenerFactory(factory)` | `EventListener.Factory` | Per-request event listener factory |
| `.fileSystem(fs)` | `FileSystem` | Okio FileSystem for disk operations |
| `.serviceLoaderEnabled(enabled)` | `Boolean` | Auto-register components via service loader |
| `.maxBitmapSize(size)` | `Size` | Maximum decoded bitmap size |
| `.addLastModifiedToFileCacheKey(add)` | `Boolean` | Include last-modified in file cache key |
| `.build()` | `ImageLoader` | Build the ImageLoader |

### Executing Requests

```kotlin
// Async (suspend) - returns ImageResult
val result: ImageResult = imageLoader.execute(request)

// Fire-and-forget (enqueue) - returns Disposable
val disposable: Disposable = imageLoader.enqueue(request)

// Cancel
disposable.dispose()

// Shutdown
imageLoader.shutdown()
```

### Singleton Setup (Compose Multiplatform)

```kotlin
// Call this before any AsyncImage usage, typically in your App composable
setSingletonImageLoaderFactory { context ->
    ImageLoader.Builder(context)
        .crossfade(true)
        .build()
}
```

---

## ImageRequest

Describes an image load operation: what to load, how to configure it, and where to deliver the result.

### ImageRequest.Builder

```kotlin
ImageRequest.Builder(context: PlatformContext)
```

| Method | Type | Description |
|--------|------|-------------|
| `.data(data)` | `Any?` | Image source: URL string, Uri, File, ByteArray, resource ID, Bitmap, etc. |
| `.crossfade(enable)` | `Boolean` | Enable crossfade for this request |
| `.crossfade(durationMillis)` | `Int` | Crossfade duration in ms |
| `.placeholder(painter)` | `Image?` | Placeholder while loading |
| `.error(painter)` | `Image?` | Error image on failure |
| `.fallback(painter)` | `Image?` | Fallback for null data |
| `.transformations(vararg)` | `Transformation` | Image transformations to apply |
| `.transformations(list)` | `List<Transformation>` | Image transformations list |
| `.size(size)` | `Size` | Override the target size |
| `.size(width, height)` | `Int, Int` | Override target size with pixel dimensions |
| `.size(width, height)` | `Dimension, Dimension` | Override target size with Dimensions |
| `.scale(scale)` | `Scale` | How to scale image (`FILL`, `FIT`) |
| `.precision(precision)` | `Precision` | Size matching precision |
| `.memoryCacheKey(key)` | `String?` | Custom memory cache key |
| `.memoryCacheKey(key)` | `MemoryCache.Key?` | Custom memory cache key object |
| `.memoryCacheKeyExtra(key, value)` | `String, String?` | Add extra to memory cache key |
| `.diskCacheKey(key)` | `String?` | Custom disk cache key |
| `.memoryCachePolicy(policy)` | `CachePolicy` | Memory cache policy for this request |
| `.diskCachePolicy(policy)` | `CachePolicy` | Disk cache policy for this request |
| `.networkCachePolicy(policy)` | `CachePolicy` | Network cache policy for this request |
| `.fetcherFactory(factory, type)` | `Fetcher.Factory<T>, KClass<T>` | Custom fetcher for this request |
| `.decoderFactory(factory)` | `Decoder.Factory` | Custom decoder for this request |
| `.headers(headers)` | `NetworkHeaders` | HTTP headers for network requests |
| `.setHeader(name, value)` | `String, String` | Set a single HTTP header |
| `.tags(tags)` | `Tags` | Attach metadata tags |
| `.setTag(type, tag)` | `KClass<T>, T?` | Attach a typed tag |
| `.listener(listener)` | `ImageRequest.Listener?` | Request lifecycle listener |
| `.listener(onStart, onCancel, onError, onSuccess)` | lambdas | Inline listener callbacks |
| `.target(target)` | `Target?` | Delivery target |
| `.coroutineContext(ctx)` | `CoroutineContext` | Override coroutine context |
| `.decoderCoroutineContext(ctx)` | `CoroutineContext` | Override decoder coroutine context |
| `.fetcherCoroutineContext(ctx)` | `CoroutineContext` | Override fetcher coroutine context |
| `.interceptorCoroutineContext(ctx)` | `CoroutineContext` | Override interceptor coroutine context |
| `.fileSystem(fs)` | `FileSystem` | Okio FileSystem override |
| `.build()` | `ImageRequest` | Build the request |

### CachePolicy Enum

```kotlin
enum class CachePolicy {
    ENABLED,       // Read and write
    READ_ONLY,     // Read from cache, don't write
    WRITE_ONLY,    // Write to cache, don't read
    DISABLED,      // Skip cache entirely
}
```

---

## AsyncImage Composable

The primary composable for loading images. Does not use subcomposition, so it performs well in `LazyColumn`/`LazyRow`.

```kotlin
@Composable
fun AsyncImage(
    model: Any?,                              // ImageRequest or raw data (URL string, Uri, etc.)
    contentDescription: String?,              // Accessibility description (null for decorative)
    imageLoader: ImageLoader,                 // ImageLoader instance (defaults to singleton)
    modifier: Modifier = Modifier,            // Layout modifier
    placeholder: Painter? = null,             // Painter shown while loading
    error: Painter? = null,                   // Painter shown on failure
    fallback: Painter? = null,                // Painter shown when model is null
    onLoading: ((AsyncImagePainter.State.Loading) -> Unit)? = null,
    onSuccess: ((AsyncImagePainter.State.Success) -> Unit)? = null,
    onError: ((AsyncImagePainter.State.Error) -> Unit)? = null,
    alignment: Alignment = Alignment.Center,  // Image alignment within bounds
    contentScale: ContentScale = ContentScale.Fit,  // How to scale image
    alpha: Float = DefaultAlpha,              // Image opacity
    colorFilter: ColorFilter? = null,         // Color filter to apply
    filterQuality: FilterQuality = DrawScope.DefaultFilterQuality,
    clipToBounds: Boolean = true,             // Clip image to composable bounds
)
```

---

## SubcomposeAsyncImage

Uses subcomposition to provide slot-based content for each loading state. Slower than `AsyncImage` -- avoid in `LazyColumn`.

```kotlin
@Composable
fun SubcomposeAsyncImage(
    model: Any?,
    contentDescription: String?,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
    loading: @Composable (SubcomposeAsyncImageScope.(AsyncImagePainter.State.Loading) -> Unit)? = null,
    success: @Composable (SubcomposeAsyncImageScope.(AsyncImagePainter.State.Success) -> Unit)? = null,
    error: @Composable (SubcomposeAsyncImageScope.(AsyncImagePainter.State.Error) -> Unit)? = null,
    onLoading: ((AsyncImagePainter.State.Loading) -> Unit)? = null,
    onSuccess: ((AsyncImagePainter.State.Success) -> Unit)? = null,
    onError: ((AsyncImagePainter.State.Error) -> Unit)? = null,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    filterQuality: FilterQuality = DrawScope.DefaultFilterQuality,
    clipToBounds: Boolean = true,
)
```

### SubcomposeAsyncImageContent

Used inside `SubcomposeAsyncImage` content slots to render the current painter with the parent's configuration:

```kotlin
@Composable
fun SubcomposeAsyncImageScope.SubcomposeAsyncImageContent(
    modifier: Modifier = Modifier,
    painter: Painter = this.painter,
    contentDescription: String? = this.contentDescription,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    clipToBounds: Boolean = true,
)
```

### Alternative: content lambda

```kotlin
SubcomposeAsyncImage(
    model = url,
    contentDescription = null,
) { state ->
    when (state) {
        is AsyncImagePainter.State.Loading -> CircularProgressIndicator()
        is AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
        is AsyncImagePainter.State.Error -> Icon(Icons.Default.Warning, contentDescription = "Error")
        is AsyncImagePainter.State.Empty -> Unit
    }
}
```

---

## AsyncImagePainter.State

Sealed interface representing the current state of an async image load.

```kotlin
sealed interface State {
    data object Empty : State                      // No request set
    data class Loading(val painter: Painter?) : State  // Request in progress
    data class Success(val painter: Painter, val result: SuccessResult) : State
    data class Error(val painter: Painter?, val result: ErrorResult) : State
}
```

---

## ImageResult

Sealed interface returned by `ImageLoader.execute()`.

```kotlin
sealed interface ImageResult {
    val image: Image?
    val request: ImageRequest
}

data class SuccessResult(
    override val image: Image,
    override val request: ImageRequest,
    val dataSource: DataSource,          // Where the image was loaded from
    val memoryCacheKey: MemoryCache.Key?, // Key used in memory cache
    val diskCacheKey: String?,           // Key used in disk cache
    val isSampled: Boolean,             // Whether image was downsampled
    val isPlaceholderCached: Boolean,    // Whether placeholder was from cache
) : ImageResult

data class ErrorResult(
    override val image: Image?,
    override val request: ImageRequest,
    val throwable: Throwable,           // The error that occurred
) : ImageResult
```

### DataSource Enum

```kotlin
enum class DataSource {
    MEMORY_CACHE,   // Loaded from memory cache
    MEMORY,         // Loaded from a memory source (not cache)
    DISK,           // Loaded from disk cache
    NETWORK,        // Loaded from network
}
```

---

## Transformations

### Built-in Transformations

**CircleCropTransformation** -- Crops the image to a circle.

```kotlin
class CircleCropTransformation : Transformation {
    override val cacheKey: String = "CircleCropTransformation"
    override suspend fun transform(input: Bitmap, size: Size): Bitmap
}
```

**RoundedCornersTransformation** -- Rounds the corners of the image.

```kotlin
class RoundedCornersTransformation(
    topLeft: Float = 0f,
    topRight: Float = 0f,
    bottomLeft: Float = 0f,
    bottomRight: Float = 0f,
) : Transformation {
    // Convenience: all corners same radius
    constructor(radius: Float = 0f)

    override val cacheKey: String
    override suspend fun transform(input: Bitmap, size: Size): Bitmap
}
```

### Custom Transformation Interface

```kotlin
abstract class Transformation {
    abstract val cacheKey: String
    // Android
    abstract suspend fun transform(input: android.graphics.Bitmap, size: Size): android.graphics.Bitmap
    // Non-Android (Skia)
    abstract suspend fun transform(input: org.jetbrains.skia.Bitmap, size: Size): org.jetbrains.skia.Bitmap
}
```

### Applying Transformations

```kotlin
val request = ImageRequest.Builder(context)
    .data("https://example.com/image.jpg")
    .transformations(
        CircleCropTransformation(),
    )
    .build()

// Multiple transformations
val request2 = ImageRequest.Builder(context)
    .data("https://example.com/image.jpg")
    .transformations(
        RoundedCornersTransformation(16f),
    )
    .build()
```

---

## DiskCache

File-based disk cache backed by Okio.

### DiskCache.Builder

```kotlin
DiskCache.Builder()
```

| Method | Type | Description |
|--------|------|-------------|
| `.directory(path)` | `okio.Path` | Cache directory (required) |
| `.fileSystem(fs)` | `FileSystem` | Okio FileSystem (default: `FileSystem.SYSTEM`) |
| `.maxSizeBytes(bytes)` | `Long` | Maximum cache size in bytes |
| `.maxSizePercent(percent)` | `Double` | Maximum cache size as fraction of total disk (0.0-1.0) |
| `.minimumMaxSizeBytes(bytes)` | `Long` | Floor for percent-based max size |
| `.maximumMaxSizeBytes(bytes)` | `Long` | Ceiling for percent-based max size |
| `.cleanupCoroutineContext(ctx)` | `CoroutineContext` | Context for cleanup operations |
| `.build()` | `DiskCache` | Build the DiskCache |

### Example

```kotlin
val diskCache = DiskCache.Builder()
    .directory("image_cache".toPath())  // okio.Path
    .maxSizeBytes(250L * 1024 * 1024)   // 250 MB
    .build()
```

---

## MemoryCache

In-memory LRU cache for loaded images.

### MemoryCache.Builder

```kotlin
MemoryCache.Builder()
```

| Method | Type | Description |
|--------|------|-------------|
| `.maxSizePercent(context, percent)` | `PlatformContext, Double` | Max size as fraction of available memory |
| `.maxSizeBytes(bytes)` | `Long` | Max size in bytes |
| `.strongReferencesEnabled(enabled)` | `Boolean` | Keep strong references (default: `true`) |
| `.weakReferencesEnabled(enabled)` | `Boolean` | Keep weak references (default: `true`) |
| `.build()` | `MemoryCache` | Build the MemoryCache |

### Example

```kotlin
val memoryCache = MemoryCache.Builder()
    .maxSizePercent(context, 0.25) // 25% of available memory
    .build()
```

### MemoryCache.Key

```kotlin
data class Key(
    val key: String,
    val extras: Map<String, String> = emptyMap(),
)
```

---

## Custom Fetcher

Fetchers translate data (URL, Uri, File, etc.) into raw image bytes (`FetchResult`).

### Fetcher Interface

```kotlin
fun interface Fetcher {
    suspend fun fetch(): FetchResult?
}
```

### Fetcher.Factory

```kotlin
fun interface Factory<T : Any> {
    fun create(data: T, options: Options, imageLoader: ImageLoader): Fetcher?
}
```

### FetchResult

```kotlin
sealed interface FetchResult

data class SourceFetchResult(
    val source: ImageSource,           // Okio BufferedSource wrapping image bytes
    val mimeType: String?,             // MIME type (e.g., "image/png")
    val dataSource: DataSource,        // Where the data came from
) : FetchResult

data class ImageFetchResult(
    val image: Image,                  // Already-decoded image
    val isSampled: Boolean,            // Whether image was downsampled
    val dataSource: DataSource,
) : FetchResult
```

### Registering a Fetcher

```kotlin
val imageLoader = ImageLoader.Builder(context)
    .components {
        add(MyFetcher.Factory(), MyDataType::class)
    }
    .build()
```

---

## Custom Decoder

Decoders read raw image bytes from a `SourceFetchResult` and produce a decoded `Image`.

### Decoder Interface

```kotlin
fun interface Decoder {
    suspend fun decode(): DecodeResult?
}
```

### Decoder.Factory

```kotlin
fun interface Factory {
    fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder?
}
```

### DecodeResult

```kotlin
data class DecodeResult(
    val image: Image,
    val isSampled: Boolean,
)
```

### Registering a Decoder

```kotlin
val imageLoader = ImageLoader.Builder(context)
    .components {
        add(MyDecoder.Factory())
    }
    .build()
```

---

## SVG Decoder

Add SVG support via `coil-svg` artifact.

### Setup

```kotlin
// build.gradle.kts
commonMain.dependencies {
    implementation(libs.coil.svg)
}
```

```kotlin
val imageLoader = ImageLoader.Builder(context)
    .components {
        add(SvgDecoder.Factory())
    }
    .build()
```

### SvgDecoder.Factory

```kotlin
class SvgDecoder.Factory(
    val useViewBoundsAsIntrinsicSize: Boolean = true,
) : Decoder.Factory
```

---

## GIF / Animated Image Decoder

### Android (coil-gif artifact)

```kotlin
// build.gradle.kts
androidMain.dependencies {
    implementation(libs.coil.gif)
}
```

```kotlin
val imageLoader = ImageLoader.Builder(context)
    .components {
        // API 28+: better performance, supports animated WebP/HEIF
        if (Build.VERSION.SDK_INT >= 28) {
            add(AnimatedImageDecoder.Factory())
        } else {
            add(GifDecoder.Factory())
        }
    }
    .build()
```

### GifDecoder.Factory (all Android API levels)

```kotlin
class GifDecoder.Factory(
    val enforceMinimumFrameDelay: Boolean = true,
) : Decoder.Factory
```

### AnimatedImageDecoder.Factory (Android API 28+)

```kotlin
class AnimatedImageDecoder.Factory(
    val enforceMinimumFrameDelay: Boolean = true,
) : Decoder.Factory
```

### Non-Android Platforms

On non-Android platforms (iOS, Desktop, JS), Coil uses Skia-based decoding. Animated GIF support is handled automatically through the Skia backend without additional decoder registration.

---

## FakeImageLoaderEngine (Testing)

Part of `coil-test` artifact. Intercepts image requests and returns predefined results synchronously.

### FakeImageLoaderEngine.Builder

```kotlin
class FakeImageLoaderEngine.Builder {
    constructor()
    constructor(engine: FakeImageLoaderEngine)

    // Intercept exact data match
    fun intercept(data: Any, image: Image): Builder

    // Intercept with predicate
    fun intercept(predicate: (Any) -> Boolean, image: Image): Builder

    // Intercept with predicate and custom interceptor
    fun intercept(predicate: (Any) -> Boolean, interceptor: OptionalInterceptor): Builder

    // Add raw interceptor
    fun addInterceptor(interceptor: OptionalInterceptor): Builder

    // Remove an interceptor
    fun removeInterceptor(interceptor: OptionalInterceptor): Builder

    // Clear all interceptors
    fun clearInterceptors(): Builder

    // Default image for unmatched requests
    fun default(image: Image): Builder
    fun default(interceptor: Interceptor): Builder

    // Transform requests before matching
    fun requestTransformer(transformer: RequestTransformer): Builder

    fun build(): FakeImageLoaderEngine
}
```

### Usage

```kotlin
val engine = FakeImageLoaderEngine.Builder()
    .intercept("https://example.com/image.jpg", ColorImage(Color.Red.toArgb()))
    .intercept(
        predicate = { it is String && it.endsWith("test.png") },
        image = ColorImage(Color.Green.toArgb()),
    )
    .default(ColorImage(Color.Blue.toArgb()))
    .build()

val imageLoader = ImageLoader.Builder(context)
    .components { add(engine) }
    .build()
```

### Observing Requests and Results

```kotlin
val engine: FakeImageLoaderEngine = ...

// Flow of all requests
engine.requests.collect { requestValue ->
    val request: ImageRequest = requestValue.request
    val size: Size = requestValue.size
}

// Flow of all results
engine.results.collect { resultValue ->
    val request: FakeImageLoaderEngine.RequestValue = resultValue.request
    val result: ImageResult = resultValue.result
}
```

### Helper: ColorImage

```kotlin
// Create a solid color image for testing
ColorImage(color: Int, width: Int = 100, height: Int = 100)
```

### Convenience Constructor

```kotlin
// Single default image for all requests
val engine = FakeImageLoaderEngine(image = ColorImage(Color.Blue.toArgb()))
```

---

## Preloading

Preload images into cache before they are needed (e.g., prefetch images in a list before the user scrolls to them).

```kotlin
// Preload into memory and disk cache
val request = ImageRequest.Builder(context)
    .data("https://example.com/image.jpg")
    .size(256, 256) // Preload at specific size
    .build()

// enqueue returns a Disposable
val disposable = imageLoader.enqueue(request)

// Cancel preload if no longer needed
disposable.dispose()

// Preload into disk cache only (skip memory)
val diskOnlyRequest = ImageRequest.Builder(context)
    .data("https://example.com/image.jpg")
    .memoryCachePolicy(CachePolicy.DISABLED)
    .build()
imageLoader.enqueue(diskOnlyRequest)
```

---

## ComponentRegistry

Registers custom pipeline components with the `ImageLoader`.

```kotlin
ImageLoader.Builder(context)
    .components {
        // Interceptors (execute in order)
        add(MyInterceptor())

        // Mappers (map data types before fetching)
        add(MyMapper())

        // Keyers (generate cache keys)
        add(MyKeyer())

        // Fetchers (fetch raw data)
        add(MyFetcher.Factory(), MyDataType::class)

        // Decoders (decode raw data into images)
        add(SvgDecoder.Factory())
        add(GifDecoder.Factory())
    }
    .build()
```

---

## rememberAsyncImagePainter

Lower-level API to get a `Painter` for manual image rendering.

```kotlin
@Composable
fun rememberAsyncImagePainter(
    model: Any?,
    imageLoader: ImageLoader = LocalImageLoader.current,
    placeholder: Painter? = null,
    error: Painter? = null,
    fallback: Painter? = null,
    onLoading: ((AsyncImagePainter.State.Loading) -> Unit)? = null,
    onSuccess: ((AsyncImagePainter.State.Success) -> Unit)? = null,
    onError: ((AsyncImagePainter.State.Error) -> Unit)? = null,
    contentScale: ContentScale = ContentScale.Fit,
    filterQuality: FilterQuality = DrawScope.DefaultFilterQuality,
): AsyncImagePainter
```

Use with standard `Image` composable:

```kotlin
val painter = rememberAsyncImagePainter(
    model = "https://example.com/image.jpg",
    contentScale = ContentScale.Crop,
)

Image(
    painter = painter,
    contentDescription = "My image",
    contentScale = ContentScale.Crop,
)
```
