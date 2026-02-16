---
name: deep-links-expert
description: KMP deep linking expertise for Circuit and Nav3. Use for App Links, Universal Links, custom URI schemes, push notification navigation, deferred deep links, and deep link testing across Android and iOS.
---

# Deep Links Expert Skill

## Overview

Deep linking allows external URLs to navigate directly to specific screens in your app. This skill covers both Circuit and Nav3 paradigms, Android App Links, iOS Universal Links, and custom URI schemes. The patterns follow CatchUp's production-proven approach using Metro multibinding for scalable deep link registration.

## When to Use

- App links from web (marketing pages, email campaigns)
- Push notification navigation (tap -> specific screen)
- Cross-app navigation (another app opens yours)
- QR code scanning to in-app content
- Deferred deep links (install first, then navigate)
- Deep link testing and validation

## Quick Reference

For detailed API reference and platform config, see [reference.md](reference.md).
For production examples, see [examples.md](examples.md).
For navigation patterns, see [navigation-expert](../navigation-expert/SKILL.md).
For Circuit specifics, see [circuit-expert](../circuit-expert/SKILL.md).

## Deep Link Types

| Type | Example | Platform | Verification |
|------|---------|----------|--------------|
| Custom URI | `myapp://profile/123` | Android + iOS | None (any app can claim) |
| App Links | `https://myapp.com/profile/123` | Android | `assetlinks.json` on domain |
| Universal Links | `https://myapp.com/profile/123` | iOS | `apple-app-site-association` on domain |

**Recommendation**: Always support both custom URI and verified links. Custom URI for development/testing, verified links for production.

## Architecture

```
URI/URL Intent
    |
    v
DeepLinkHandler.parse(intent)
    |
    v
Match path segments -> DeepLinkable map (Metro multibinding)
    |
    v
DeepLinkable.createScreen(queryParams) -> Screen / NavKey
    |
    v
Initialize backStack with [HomeScreen, ...targetScreens]
    |
    v
Circuit NavigableCircuitContent / Nav3 NavDisplay
```

## Circuit Deep Links (CatchUp Pattern)

### DeepLinkable Interface

```kotlin
// commonMain - Interface for screens that support deep linking
interface DeepLinkable {
    fun createScreen(queryParams: ImmutableMap<String, List<String?>>): Screen
}
```

### DeepLinkHandler

```kotlin
// commonMain
interface DeepLinkHandler {
    fun parse(url: HttpUrl): ImmutableList<Screen>
}
```

### Screen Registration via Metro Multibinding

```kotlin
@Parcelize
data object HomeScreen : Screen, DeepLinkable {
    @ContributesIntoMap(AppScope::class, binding = binding<DeepLinkable>())
    @StringKey("home")
    override fun createScreen(params: ImmutableMap<String, List<String?>>): Screen = HomeScreen
}

@Parcelize
data class ProfileScreen(val userId: String) : Screen {
    // Use a nested object when the Screen has constructor params
    @ContributesIntoMap(AppScope::class, binding = binding<DeepLinkable>())
    @StringKey("profile")
    object Deeplinker : DeepLinkable {
        override fun createScreen(params: ImmutableMap<String, List<String?>>): Screen =
            ProfileScreen(userId = params["id"]?.firstOrNull() ?: "")
    }
}
```

### Activity Integration

```kotlin
// androidMain - MainActivity
class MainActivity : ComponentActivity() {
    @Inject lateinit var deepLinkHandler: DeepLinkHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialScreens = deepLinkHandler.parse(intent)
        val backStack = rememberSaveableBackStack(initialScreens.ifEmpty { listOf(HomeScreen) })
        // ... NavigableCircuitContent setup
    }
}
```

## Nav3 Deep Links

### Parsing Intents to NavKeys

```kotlin
fun parseDeepLink(intent: Intent): List<NavKey>? {
    val data = intent.data ?: return null
    return when (data.pathSegments.firstOrNull()) {
        "profile" -> listOf(Home, Profile(id = data.lastPathSegment ?: return null))
        "settings" -> listOf(Home, Settings)
        else -> null
    }
}

// In Activity
val initialKeys = parseDeepLink(intent) ?: listOf(Home)
val backStack = rememberNavBackStack(initialKeys.first())
// Add remaining keys to backStack
```

## Android Configuration

### AndroidManifest.xml

```xml
<activity android:name=".MainActivity">
    <!-- Custom URI scheme -->
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="myapp" android:host="open" />
    </intent-filter>

    <!-- Verified App Links -->
    <intent-filter android:autoVerify="true">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="https" android:host="myapp.com" />
    </intent-filter>
</activity>
```

### assetlinks.json

Host at `https://myapp.com/.well-known/assetlinks.json`:

```json
[{
  "relation": ["delegate_permission/common.handle_all_urls"],
  "target": {
    "namespace": "android_app",
    "package_name": "com.example.myapp",
    "sha256_cert_fingerprints": ["AA:BB:CC:..."]
  }
}]
```

## iOS Configuration

### apple-app-site-association

Host at `https://myapp.com/.well-known/apple-app-site-association`:

```json
{
  "applinks": {
    "apps": [],
    "details": [{
      "appID": "TEAM_ID.com.example.myapp",
      "paths": ["/profile/*", "/settings", "/about"]
    }]
  }
}
```

### Associated Domains Entitlement

In Xcode, add to your target's entitlements:

```
applinks:myapp.com
```

## Core Rules

1. **Always validate deep link parameters** -- Never trust external input. Check for null, empty, and malformed values.
2. **Handle missing/invalid parameters gracefully** -- Fall back to HomeScreen rather than crashing.
3. **Use Metro multibinding for scalable registration** -- Each screen registers itself via `@ContributesIntoMap`.
4. **Initialize backStack with full path** -- Include HomeScreen as root so the back button works correctly.
5. **Coerce custom schemes to HTTPS for parsing** -- `HttpUrl` does not support custom schemes; convert `myapp://` to `https://` before parsing.
6. **Handle `onNewIntent`** -- For single-task activities, deep links arrive via `onNewIntent`, not `onCreate`.
7. **Test with `adb shell am start`** -- Verify every deep link path on Android before release.
8. **Keep deep link paths stable** -- Changing paths breaks existing links in emails, campaigns, and bookmarks.

## Common Pitfalls

1. **Forgetting `onNewIntent`** -- If the Activity is already running, `onCreate` is NOT called. Override `onNewIntent` and re-parse.
2. **Missing `android:autoVerify="true"`** -- Without this, App Links show a disambiguation dialog instead of opening directly.
3. **Custom scheme collisions** -- Any app can register `myapp://`. Use verified App Links / Universal Links for production.
4. **Empty backStack after deep link** -- Always prepend HomeScreen so users can navigate back.
5. **Not handling `ACTION_VIEW` vs `ACTION_MAIN`** -- Check `intent.action` before parsing deep links to avoid false positives on normal app launch.
6. **Broken assetlinks.json** -- Must be served with `Content-Type: application/json` over HTTPS with no redirects.
7. **iOS Associated Domains caching** -- Apple caches the association file. Changes can take 24-48 hours to propagate. Use `?mode=developer` during development.
8. **Ignoring process death** -- If the app was killed and relaunched via deep link, ensure all required state is restored.
