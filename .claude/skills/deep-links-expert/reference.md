# Deep Links API Reference

Comprehensive reference for deep linking across Android, iOS, Circuit, and Nav3.

---

## Android Intent Filter Configuration

### Custom URI Scheme

```xml
<!-- AndroidManifest.xml -->
<activity
    android:name=".MainActivity"
    android:launchMode="singleTask">

    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />

        <!-- myapp://open/profile/123?tab=posts -->
        <data
            android:scheme="myapp"
            android:host="open" />
    </intent-filter>
</activity>
```

### Verified App Links

```xml
<!-- AndroidManifest.xml -->
<activity
    android:name=".MainActivity"
    android:launchMode="singleTask">

    <!-- android:autoVerify="true" triggers domain verification at install time -->
    <intent-filter android:autoVerify="true">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />

        <data
            android:scheme="https"
            android:host="myapp.com"
            android:pathPrefix="/profile" />
        <data android:pathPrefix="/settings" />
        <data android:pathPrefix="/about" />
    </intent-filter>
</activity>
```

### Multiple Path Patterns

```xml
<!-- Use pathPattern for wildcard matching -->
<data android:pathPattern="/user/.*/profile" />

<!-- Use pathPrefix for prefix matching -->
<data android:pathPrefix="/content/" />

<!-- Exact path -->
<data android:path="/settings" />
```

### launchMode Behavior

| launchMode | Deep Link Behavior | Callback |
|------------|-------------------|----------|
| `standard` | New Activity instance created | `onCreate` |
| `singleTop` | Reuses if already on top | `onNewIntent` |
| `singleTask` | Reuses existing, clears above | `onNewIntent` |
| `singleInstance` | Always reuses single instance | `onNewIntent` |

**Recommendation**: Use `singleTask` for deep link target Activities.

---

## assetlinks.json (Android App Links Verification)

### Format

Host at `https://yourdomain.com/.well-known/assetlinks.json`.

```json
[
  {
    "relation": ["delegate_permission/common.handle_all_urls"],
    "target": {
      "namespace": "android_app",
      "package_name": "com.example.myapp",
      "sha256_cert_fingerprints": [
        "14:6D:E9:83:C5:73:06:50:D8:EE:B9:95:2F:34:FC:64:16:A0:83:42:E6:1D:BE:A8:8A:04:96:B2:3F:CF:44:E5"
      ]
    }
  }
]
```

### Requirements

- Must be served over HTTPS (no redirects)
- Content-Type: `application/json`
- Must be publicly accessible (no auth wall)
- Max size: 100 KB
- One entry per app signing key (debug + release may differ)

### Getting SHA-256 Fingerprint

```bash
# From keystore
keytool -list -v -keystore my-release-key.keystore | grep SHA256

# From installed APK (debug)
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android

# From Google Play (App Signing)
# Download from Google Play Console -> Setup -> App signing
```

### Verification Commands

```bash
# Check if verification is set up correctly
adb shell pm get-app-links com.example.myapp

# Force re-verify
adb shell pm verify-app-links --re-verify com.example.myapp

# Check verification status
adb shell pm get-app-links --user cur com.example.myapp
```

---

## apple-app-site-association (iOS Universal Links)

### Format

Host at `https://yourdomain.com/.well-known/apple-app-site-association` (no `.json` extension).

```json
{
  "applinks": {
    "apps": [],
    "details": [
      {
        "appID": "ABCDE12345.com.example.myapp",
        "paths": [
          "/profile/*",
          "/settings",
          "/about",
          "/content/*/detail",
          "NOT /admin/*"
        ]
      }
    ]
  }
}
```

### Path Matching Rules

| Pattern | Matches |
|---------|---------|
| `/profile/*` | `/profile/123`, `/profile/abc` |
| `/content/*/detail` | `/content/42/detail` |
| `*` | Everything |
| `NOT /admin/*` | Excludes `/admin/` paths |
| `?` | Single character wildcard |

### applinks v2 (iOS 13+)

```json
{
  "applinks": {
    "details": [
      {
        "appIDs": ["ABCDE12345.com.example.myapp"],
        "components": [
          { "/": "/profile/*", "comment": "User profiles" },
          { "/": "/settings", "comment": "App settings" },
          { "/": "/admin/*", "exclude": true, "comment": "Exclude admin" },
          { "/": "/search", "?": { "q": "?*" }, "comment": "Search with query" }
        ]
      }
    ]
  }
}
```

### Requirements

- Must be served over HTTPS
- Content-Type: `application/json`
- No redirects on the file itself
- Must be at the root or `.well-known` directory
- Apple CDN caches the file; changes can take 24-48 hours

### Associated Domains Entitlement

In Xcode, under Signing & Capabilities:

```
applinks:myapp.com
applinks:myapp.com?mode=developer   // Development mode - bypasses CDN cache
```

In the entitlements file:

```xml
<key>com.apple.developer.associated-domains</key>
<array>
    <string>applinks:myapp.com</string>
</array>
```

---

## Circuit DeepLinkHandler (CatchUp Pattern)

### DeepLinkable Interface

```kotlin
// Package: typically in your app's deep link module
interface DeepLinkable {
    /**
     * Create a Screen instance from deep link query parameters.
     * @param queryParams Map of query parameter name to list of values
     * @return Screen instance configured from the deep link
     */
    fun createScreen(queryParams: ImmutableMap<String, List<String?>>): Screen
}
```

### DeepLinkHandler Interface

```kotlin
interface DeepLinkHandler {
    /**
     * Parse an HttpUrl into a list of Screens forming a navigation stack.
     * The first screen is the bottom of the stack (typically HomeScreen).
     */
    fun parse(url: HttpUrl): ImmutableList<Screen>
}

/**
 * Extension to parse directly from an Android Intent.
 * Only handles ACTION_VIEW intents with non-null data.
 */
fun DeepLinkHandler.parse(intent: Intent): ImmutableList<Screen> {
    if (intent.action != Intent.ACTION_VIEW) return persistentListOf()
    val uri = intent.data ?: return persistentListOf()
    return parse(uri.toHttpUrl())
}
```

### DeepLinkHandlerImpl

```kotlin
@Inject
class DeepLinkHandlerImpl(
    private val deepLinkables: Map<String, DeepLinkable>,
) : DeepLinkHandler {

    override fun parse(url: HttpUrl): ImmutableList<Screen> {
        val screens = mutableListOf<Screen>()
        val queryParams = url.queryParameterNames.associateWith { name ->
            url.queryParameterValues(name)
        }.toImmutableMap()

        for (segment in url.pathSegments) {
            val deepLinkable = deepLinkables[segment] ?: continue
            screens += deepLinkable.createScreen(queryParams)
        }

        return screens.toImmutableList()
    }
}
```

### Metro Binding for DeepLinkHandlerImpl

```kotlin
@ContributesTo(AppScope::class)
interface DeepLinkModule {
    @Provides
    fun provideDeepLinkHandler(impl: DeepLinkHandlerImpl): DeepLinkHandler = impl
}
```

### Uri to HttpUrl Conversion

```kotlin
/**
 * Coerce a Uri to an HttpUrl. Custom schemes (e.g., myapp://)
 * are converted to https:// because OkHttp's HttpUrl only supports HTTP(S).
 */
fun Uri.toHttpUrl(): HttpUrl {
    val uriString = toString()
    val coerced = if (scheme != "http" && scheme != "https") {
        uriString.replaceFirst("$scheme://", "https://")
    } else {
        uriString
    }
    return coerced.toHttpUrl()
}
```

---

## Nav3 Deep Link Parsing

### Route-Based Parsing

```kotlin
@Serializable
data object Home : NavKey

@Serializable
data class Profile(val id: String) : NavKey

@Serializable
data object Settings : NavKey

fun parseDeepLink(intent: Intent): List<NavKey>? {
    if (intent.action != Intent.ACTION_VIEW) return null
    val data = intent.data ?: return null

    val targetKey: NavKey = when (data.pathSegments.firstOrNull()) {
        "profile" -> Profile(id = data.getQueryParameter("id") ?: data.lastPathSegment ?: return null)
        "settings" -> Settings
        else -> return null
    }

    // Always include Home as root for proper back navigation
    return listOf(Home, targetKey)
}
```

### Initializing NavDisplay from Deep Link

```kotlin
@Composable
fun AppNavigation(initialKeys: List<NavKey>) {
    val backStack = rememberNavBackStack(initialKeys.first())

    LaunchedEffect(Unit) {
        // Add remaining keys from deep link
        initialKeys.drop(1).forEach { key ->
            backStack.add(key)
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.pop() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider {
            entry<Home> { HomeScreen(onNavigate = { backStack.add(it) }) }
            entry<Profile> { key -> ProfileScreen(userId = key.id) }
            entry<Settings> { SettingsScreen() }
        }
    )
}
```

---

## URI Parsing Utilities

### Query Parameter Extraction

```kotlin
/**
 * Safely extract a required query parameter, returning null if missing.
 */
fun Uri.requireParam(name: String): String? = getQueryParameter(name)

/**
 * Extract an optional query parameter with a default value.
 */
fun Uri.paramOrDefault(name: String, default: String): String =
    getQueryParameter(name) ?: default

/**
 * Extract an integer parameter, returning null if missing or not a valid int.
 */
fun Uri.intParam(name: String): Int? = getQueryParameter(name)?.toIntOrNull()

/**
 * Extract a boolean parameter (supports "true", "1", "yes").
 */
fun Uri.boolParam(name: String): Boolean =
    getQueryParameter(name)?.lowercase() in listOf("true", "1", "yes")
```

---

## Deferred Deep Links

Deferred deep links navigate users to content after they install the app for the first time.

### Flow

```
1. User clicks link -> App not installed
2. Redirect to App Store / Play Store
3. User installs and opens app
4. App retrieves the original deep link
5. Navigate to intended content
```

### Implementation Pattern

```kotlin
interface DeferredDeepLinkProvider {
    /**
     * Check for a pending deferred deep link on first launch.
     * Returns null if no deferred link exists.
     */
    suspend fun getPendingDeepLink(): HttpUrl?

    /** Mark the deferred deep link as consumed. */
    suspend fun markConsumed()
}

// In your app initialization
class AppInitializer @Inject constructor(
    private val deferredProvider: DeferredDeepLinkProvider,
    private val deepLinkHandler: DeepLinkHandler,
) {
    suspend fun getInitialScreens(): ImmutableList<Screen> {
        val deferredUrl = deferredProvider.getPendingDeepLink()
        if (deferredUrl != null) {
            deferredProvider.markConsumed()
            return deepLinkHandler.parse(deferredUrl)
        }
        return persistentListOf(HomeScreen)
    }
}
```

---

## Deep Link Testing Commands

### Android (adb)

```bash
# Test custom URI scheme
adb shell am start -a android.intent.action.VIEW \
    -d "myapp://open/profile/123?tab=posts" \
    com.example.myapp

# Test App Links (HTTPS)
adb shell am start -a android.intent.action.VIEW \
    -d "https://myapp.com/profile/123" \
    com.example.myapp

# Test with specific Activity
adb shell am start -a android.intent.action.VIEW \
    -d "myapp://open/settings" \
    -n com.example.myapp/.MainActivity

# Verify App Links status
adb shell pm get-app-links com.example.myapp

# Reset App Links verification
adb shell pm set-app-links --package com.example.myapp 0 all
adb shell pm verify-app-links --re-verify com.example.myapp
```

### iOS (xcrun / simctl)

```bash
# Open URL in Simulator
xcrun simctl openurl booted "https://myapp.com/profile/123"

# Open custom scheme in Simulator
xcrun simctl openurl booted "myapp://open/profile/123"

# List installed apps (to find bundle ID)
xcrun simctl listapps booted
```

### Instrumented Tests (Android)

```kotlin
@RunWith(AndroidJUnit4::class)
class DeepLinkTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun profileDeepLink_navigatesToProfile() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("myapp://open/profile/123")
        }
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            // Assert profile screen is displayed
            onView(withText("Profile")).check(matches(isDisplayed()))
        }
    }
}
```

---

## Key Imports

```kotlin
// Android deep link handling
import android.content.Intent
import android.net.Uri

// OkHttp URL parsing (used by CatchUp pattern)
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

// Kotlinx collections
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap

// Circuit
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.backstack.rememberSaveableBackStack
import com.slack.circuit.foundation.rememberCircuitNavigator
import com.slack.circuit.foundation.NavigableCircuitContent
import com.slack.circuit.foundation.CircuitCompositionLocals
import com.slack.circuit.overlay.ContentWithOverlays

// Circuit Navigation Interceptor
import com.slack.circuitx.navigation.NavigationInterceptor
import com.slack.circuitx.navigation.InterceptedGoToResult
import com.slack.circuitx.navigation.rememberInterceptingNavigator

// Nav3
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.NavDisplay
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.entry

// Metro DI
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.StringKey

// Parcelize
import kotlinx.parcelize.Parcelize

// Serialization (Nav3 NavKeys)
import kotlinx.serialization.Serializable
```
