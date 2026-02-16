---
name: image-loading-expert
description: Expert guidance on image loading in KMP using Coil 3. Use for async image loading, caching, transformations, SVG/GIF support, preloading, and platform-specific optimizations.
---

# Image Loading Expert Skill (Coil 3.3.0)

## Overview

Coil 3 is a fully KMP-compatible image loading library built on coroutines and Compose Multiplatform. It supports Android, iOS, Desktop (JVM), JS, and wasmJs targets. Coil 3 uses Ktor for networking on non-Android platforms and OkHttp on Android.

## When to Use

- Loading images from network URLs, local files, or resources
- Image caching (memory + disk) with configurable policies
- Image transformations (circle crop, rounded corners, blur, grayscale)
- SVG and GIF/animated image support
- Preloading images for smooth scrolling
- Thumbnail/placeholder patterns
- Custom fetchers for proprietary image sources

## Quick Reference

For detailed APIs, see [reference.md](reference.md).
For production code examples, see [examples.md](examples.md).

## Platform Engine Selection

| Platform | Network Engine | Disk Cache | Notes |
|----------|---------------|------------|-------|
| Android | OkHttp (default) | Coil disk cache | Integrates with existing OkHttp client |
| iOS | Ktor (Darwin) | Coil disk cache | Uses NSURLSession under the hood |
| Desktop (JVM) | Ktor (CIO) | Coil disk cache | File-based cache |
| JS/wasmJs | Ktor (Js) | Memory only | No disk cache in browser |

## Core Rules

1. **Always provide a shared ImageLoader via Metro DI**: Create a single `ImageLoader` instance per app, provided through your Metro DependencyGraph.
2. **Use `AsyncImage` for Compose Multiplatform**: Prefer `AsyncImage` over `SubcomposeAsyncImage` unless you need custom loading/error composables that depend on the image state.
3. **Always set `crossfade(true)`**: Provides smooth image transitions and avoids jarring pops.
4. **Configure disk cache for production**: Set explicit disk cache size (typically 250MB) and directory.
5. **Use `ImageRequest.Builder.memoryCachePolicy` and `diskCachePolicy`**: Control caching per request when needed (e.g., skip cache for user avatars that change frequently).
6. **Preload images in lists**: Use `ImageLoader.enqueue(ImageRequest)` to preload off-screen images for smooth scrolling.
7. **Provide placeholders**: Always set `placeholder()` and `error()` drawables/painters for good UX.
8. **Use transformations sparingly**: `CircleCropTransformation` and `RoundedCornersTransformation` are efficient, but chaining many transformations degrades performance.

## Metro DI Integration

```kotlin
@DependencyGraph(AppScope::class)
interface AppGraph {
    @Provides
    fun provideImageLoader(
        context: PlatformContext,
    ): ImageLoader = ImageLoader.Builder(context)
        .crossfade(true)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir / "image_cache")
                .maxSizeBytes(250L * 1024 * 1024) // 250 MB
                .build()
        }
        .build()
}
```

## Circuit Integration

```kotlin
@CircuitInject(ProfileScreen::class, AppScope::class)
@Composable
fun ProfileUi(state: ProfileScreen.State, modifier: Modifier = Modifier) {
    AsyncImage(
        model = ImageRequest.Builder(LocalPlatformContext.current)
            .data(state.avatarUrl)
            .crossfade(true)
            .build(),
        contentDescription = "Profile avatar",
        modifier = modifier.size(64.dp).clip(CircleShape),
        contentScale = ContentScale.Crop,
    )
}
```

## Common Pitfalls

1. **Creating multiple ImageLoader instances**: Always share a single instance via DI. Multiple instances waste memory with duplicate caches.
2. **Not setting contentScale**: Without `ContentScale.Crop` or `Fit`, images may stretch or leave blank space.
3. **Missing contentDescription**: Accessibility requires content descriptions for all images. Use `null` only for purely decorative images.
4. **Large images without size constraints**: Always constrain image size with `Modifier.size()` or `size()` in `ImageRequest.Builder` to avoid OOM.
5. **Using SubcomposeAsyncImage in LazyColumn**: `SubcomposeAsyncImage` triggers subcomposition which hurts scroll performance. Prefer `AsyncImage` with placeholder.

## Gradle Dependencies

```toml
[versions]
coil = "3.3.0"

[libraries]
coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil" }
coil-network-ktor3 = { module = "io.coil-kt.coil3:coil-network-ktor3", version.ref = "coil" }
coil-svg = { module = "io.coil-kt.coil3:coil-svg", version.ref = "coil" }
coil-gif = { module = "io.coil-kt.coil3:coil-gif", version.ref = "coil" }
coil-test = { module = "io.coil-kt.coil3:coil-test", version.ref = "coil" }
```
