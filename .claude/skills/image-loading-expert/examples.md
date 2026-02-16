# Coil 3.3.0 Production Examples (KMP)

## Example 1: Metro DI ImageLoader Provider

Full `ImageLoader` setup with Metro DI, including platform-specific cache directory via `expect`/`actual`.

### commonMain

```kotlin
// com/example/app/di/ImageLoaderProvider.kt
package com.example.app.di

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.svg.SvgDecoder
import com.example.app.di.scopes.AppScope
import com.slack.metro.DependencyGraph
import com.slack.metro.Provides
import okio.Path

/** Platform-specific cache directory. */
expect fun PlatformContext.imageCacheDirectory(): Path

@DependencyGraph(AppScope::class)
interface ImageLoaderComponent {

    @Provides
    fun provideImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .crossfade(300)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.imageCacheDirectory())
                    .maxSizeBytes(250L * 1024 * 1024)
                    .build()
            }
            .components {
                add(SvgDecoder.Factory())
            }
            .build()
}
```

### androidMain

```kotlin
// com/example/app/di/ImageCacheDirectory.android.kt
package com.example.app.di

import coil3.PlatformContext
import okio.Path
import okio.Path.Companion.toOkioPath

actual fun PlatformContext.imageCacheDirectory(): Path =
    cacheDir.resolve("image_cache").toOkioPath()
```

### iosMain

```kotlin
// com/example/app/di/ImageCacheDirectory.ios.kt
package com.example.app.di

import coil3.PlatformContext
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

actual fun PlatformContext.imageCacheDirectory(): Path {
    val caches = NSSearchPathForDirectoriesInDomains(
        NSCachesDirectory, NSUserDomainMask, true,
    ).first() as String
    return "$caches/image_cache".toPath()
}
```

### desktopMain (JVM)

```kotlin
// com/example/app/di/ImageCacheDirectory.desktop.kt
package com.example.app.di

import coil3.PlatformContext
import okio.Path
import okio.Path.Companion.toPath

actual fun PlatformContext.imageCacheDirectory(): Path {
    val home = System.getProperty("user.home")
    return "$home/.cache/myapp/image_cache".toPath()
}
```

---

## Example 2: Basic AsyncImage with Placeholder

Loading a network image with placeholder, error fallback, and crossfade.

```kotlin
// com/example/app/ui/components/NetworkImage.kt
package com.example.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest

@Composable
fun NetworkImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalPlatformContext.current

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(url)
            .crossfade(true)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build(),
        contentDescription = contentDescription,
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
        contentScale = contentScale,
        alignment = Alignment.Center,
    )
}
```

Usage:

```kotlin
NetworkImage(
    url = "https://example.com/hero-banner.jpg",
    contentDescription = "Hero banner",
    modifier = Modifier
        .fillMaxWidth()
        .height(240.dp),
)
```

---

## Example 3: User Avatar with Circle Crop

Avatar component using `CircleCropTransformation` with proper sizing and accessibility.

```kotlin
// com/example/app/ui/components/UserAvatar.kt
package com.example.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.transform.CircleCropTransformation

@Composable
fun UserAvatar(
    imageUrl: String?,
    displayName: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val context = LocalPlatformContext.current

    if (imageUrl != null) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageUrl)
                .crossfade(true)
                .transformations(CircleCropTransformation())
                .size(with(LocalDensity.current) { size.roundToPx() })
                .build(),
            contentDescription = "Avatar for $displayName",
            modifier = modifier
                .size(size)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    } else {
        // Fallback: initials avatar
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = displayName.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
```

Usage with Circuit:

```kotlin
@CircuitInject(ProfileScreen::class, AppScope::class)
@Composable
fun ProfileUi(state: ProfileScreen.State, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        UserAvatar(
            imageUrl = state.avatarUrl,
            displayName = state.displayName,
            size = 64.dp,
        )
        Text(
            text = state.displayName,
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}
```

---

## Example 4: Image Gallery in LazyVerticalGrid

Efficient image loading in a scrollable grid with preloading for smooth scrolling.

```kotlin
// com/example/app/ui/gallery/ImageGallery.kt
package com.example.app.ui.gallery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest

data class GalleryImage(
    val id: String,
    val url: String,
    val description: String?,
)

@Composable
fun ImageGallery(
    images: List<GalleryImage>,
    imageLoader: ImageLoader,
    onImageClick: (GalleryImage) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 3,
) {
    val context = LocalPlatformContext.current
    val gridState = rememberLazyGridState()

    // Preload images that are about to become visible
    LaunchedEffect(gridState, images) {
        snapshotFlow { gridState.layoutInfo }
            .collect { layoutInfo ->
                val visibleEnd = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                val preloadEnd = (visibleEnd + columns * 2).coerceAtMost(images.size - 1)

                for (i in (visibleEnd + 1)..preloadEnd) {
                    val request = ImageRequest.Builder(context)
                        .data(images[i].url)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .size(256, 256)
                        .build()
                    imageLoader.enqueue(request)
                }
            }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxSize(),
        state = gridState,
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(
            items = images,
            key = { it.id },
        ) { image ->
            // Use AsyncImage (NOT SubcomposeAsyncImage) for scroll performance
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(image.url)
                    .crossfade(true)
                    .size(256, 256)
                    .build(),
                contentDescription = image.description,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onImageClick(image) },
                contentScale = ContentScale.Crop,
            )
        }
    }
}
```

---

## Example 5: SVG Icon Loading

Loading SVG images with `SvgDecoder` registered in the `ImageLoader`.

```kotlin
// com/example/app/ui/components/SvgIcon.kt
package com.example.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest

/**
 * Loads an SVG icon from a URL. Requires [coil3.svg.SvgDecoder.Factory]
 * to be registered in the [coil3.ImageLoader] (see Example 1).
 */
@Composable
fun SvgIcon(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: androidx.compose.ui.graphics.Color? = null,
) {
    val context = LocalPlatformContext.current

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(url)
            .crossfade(true)
            .build(),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit,
        colorFilter = tint?.let { ColorFilter.tint(it) },
    )
}
```

Usage:

```kotlin
// In a composable
SvgIcon(
    url = "https://example.com/icons/settings.svg",
    contentDescription = "Settings",
    size = 32.dp,
    tint = MaterialTheme.colorScheme.onSurface,
)
```

### ImageLoader setup with SVG support (Metro DI)

```kotlin
@DependencyGraph(AppScope::class)
interface AppGraph {
    @Provides
    fun provideImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .crossfade(true)
            .components {
                add(SvgDecoder.Factory())
            }
            .build()
}
```

---

## Example 6: Custom Fetcher for Encrypted Images

Creating a custom `Fetcher` that decrypts AES-encrypted images before display.

```kotlin
// com/example/app/image/EncryptedImageFetcher.kt
package com.example.app.image

import coil3.ImageLoader
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.source.ImageSource
import okio.Buffer
import okio.BufferedSource
import okio.FileSystem

/**
 * Data class representing an encrypted image reference.
 * The [path] points to an AES-encrypted image file on disk.
 */
data class EncryptedImage(
    val path: String,
    val decryptionKey: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedImage) return false
        return path == other.path && decryptionKey.contentEquals(other.decryptionKey)
    }

    override fun hashCode(): Int {
        var result = path.hashCode()
        result = 31 * result + decryptionKey.contentHashCode()
        return result
    }
}

class EncryptedImageFetcher(
    private val data: EncryptedImage,
    private val options: Options,
    private val decryptor: ImageDecryptor,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val encryptedBytes = FileSystem.SYSTEM.read(data.path.toPath()) {
            readByteArray()
        }

        val decryptedBytes = decryptor.decrypt(
            encrypted = encryptedBytes,
            key = data.decryptionKey,
        )

        val buffer = Buffer().write(decryptedBytes)

        return SourceFetchResult(
            source = ImageSource(
                source = buffer,
                fileSystem = FileSystem.SYSTEM,
            ),
            mimeType = "image/png",
            dataSource = coil3.decode.DataSource.DISK,
        )
    }

    class Factory(
        private val decryptor: ImageDecryptor,
    ) : Fetcher.Factory<EncryptedImage> {
        override fun create(
            data: EncryptedImage,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = EncryptedImageFetcher(data, options, decryptor)
    }
}

/** Platform-agnostic decryption interface. Use expect/actual for implementation. */
interface ImageDecryptor {
    suspend fun decrypt(encrypted: ByteArray, key: ByteArray): ByteArray
}
```

### Registration with Metro DI

```kotlin
@DependencyGraph(AppScope::class)
interface AppGraph {
    @Provides
    fun provideImageLoader(
        context: PlatformContext,
        decryptor: ImageDecryptor,
    ): ImageLoader =
        ImageLoader.Builder(context)
            .crossfade(true)
            .components {
                add(EncryptedImageFetcher.Factory(decryptor), EncryptedImage::class)
                add(SvgDecoder.Factory())
            }
            .build()
}
```

### Usage

```kotlin
@Composable
fun SecureImage(
    encryptedImage: EncryptedImage,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalPlatformContext.current

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(encryptedImage)
            .crossfade(true)
            .memoryCachePolicy(CachePolicy.DISABLED) // Don't cache decrypted in memory
            .build(),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}
```

---

## Example 7: Testing with FakeImageLoaderEngine

Unit testing composables that use `AsyncImage` with deterministic image results.

```kotlin
// com/example/app/ui/components/NetworkImageTest.kt
package com.example.app.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.test.FakeImageLoaderEngine
import coil3.test.default
import org.junit.Rule
import org.junit.Test

class NetworkImageTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun networkImage_displaysSuccessfully() {
        // Arrange: configure FakeImageLoaderEngine to return a red image for all URLs
        val engine = FakeImageLoaderEngine.Builder()
            .intercept(
                predicate = { it is String && it.startsWith("https://") },
                image = ColorImage(android.graphics.Color.RED),
            )
            .default(ColorImage(android.graphics.Color.GRAY))
            .build()

        composeRule.setContent {
            setSingletonImageLoaderFactory { context ->
                ImageLoader.Builder(context)
                    .components { add(engine) }
                    .build()
            }

            NetworkImage(
                url = "https://example.com/photo.jpg",
                contentDescription = "Test photo",
            )
        }

        // Assert: the image composable is displayed
        composeRule
            .onNodeWithContentDescription("Test photo")
            .assertIsDisplayed()
    }

    @Test
    fun networkImage_handlesSpecificUrls() {
        val avatarUrl = "https://example.com/avatar.jpg"
        val bannerUrl = "https://example.com/banner.jpg"

        val engine = FakeImageLoaderEngine.Builder()
            .intercept(avatarUrl, ColorImage(android.graphics.Color.BLUE))
            .intercept(bannerUrl, ColorImage(android.graphics.Color.GREEN))
            .default(ColorImage(android.graphics.Color.GRAY))
            .build()

        composeRule.setContent {
            setSingletonImageLoaderFactory { context ->
                ImageLoader.Builder(context)
                    .components { add(engine) }
                    .build()
            }

            UserAvatar(
                imageUrl = avatarUrl,
                displayName = "Alice",
                size = 48.dp,
            )
        }

        composeRule
            .onNodeWithContentDescription("Avatar for Alice")
            .assertIsDisplayed()
    }
}
```

### KMP-compatible test with FakeImageLoaderEngine

```kotlin
// commonTest - Using kotlin.test
package com.example.app.image

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.test.FakeImageLoaderEngine
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class ImageLoaderTest {

    @Test
    fun execute_returnsSuccess_forInterceptedUrl() = runTest {
        val engine = FakeImageLoaderEngine.Builder()
            .intercept("https://example.com/test.jpg", ColorImage(0xFFFF0000.toInt()))
            .default(ColorImage(0xFF0000FF.toInt()))
            .build()

        val imageLoader = ImageLoader.Builder(PlatformContext.INSTANCE)
            .components { add(engine) }
            .build()

        val request = ImageRequest.Builder(PlatformContext.INSTANCE)
            .data("https://example.com/test.jpg")
            .build()

        val result = imageLoader.execute(request)
        assertIs<SuccessResult>(result)
    }
}
```

---

## Example 8: Image with Shimmer Placeholder

Custom shimmer effect while image loads using `SubcomposeAsyncImage`. Use this pattern only outside of `LazyColumn`/`LazyRow` because subcomposition impacts scroll performance.

```kotlin
// com/example/app/ui/components/ShimmerImage.kt
package com.example.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest

@Composable
fun ShimmerImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalPlatformContext.current

    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(url)
            .crossfade(true)
            .build(),
        contentDescription = contentDescription,
        modifier = modifier.clip(RoundedCornerShape(12.dp)),
        contentScale = contentScale,
    ) { state ->
        when (state) {
            is AsyncImagePainter.State.Loading -> ShimmerPlaceholder()
            is AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
            is AsyncImagePainter.State.Error -> ErrorPlaceholder()
            is AsyncImagePainter.State.Empty -> Unit
        }
    }
}

@Composable
private fun ShimmerPlaceholder(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_alpha",
    )

    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(x = shimmerProgress * 1000f - 500f, y = 0f),
        end = Offset(x = shimmerProgress * 1000f, y = 0f),
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(brush),
    )
}

@Composable
private fun ErrorPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.errorContainer),
    )
}
```

Usage:

```kotlin
// Hero image on a detail screen (not in a LazyColumn)
ShimmerImage(
    url = "https://example.com/article-hero.jpg",
    contentDescription = "Article hero image",
    modifier = Modifier
        .fillMaxWidth()
        .height(280.dp),
)
```

### Alternative: Shimmer in LazyColumn with AsyncImage

For lists, use `AsyncImage` with a static placeholder instead of `SubcomposeAsyncImage`:

```kotlin
// Use this pattern inside LazyColumn/LazyRow for better scroll performance
@Composable
fun LazyListImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalPlatformContext.current

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(url)
            .crossfade(true)
            .build(),
        contentDescription = contentDescription,
        modifier = modifier.clip(RoundedCornerShape(8.dp)),
        contentScale = ContentScale.Crop,
        // Use a static color placeholder for LazyColumn performance
        // (no subcomposition, no shimmer animation overhead during scroll)
    )
}
```
