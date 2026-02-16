# Deep Links Examples

Production-ready examples for deep linking in KMP apps with Circuit and Nav3.

---

## Example 1: Circuit Deep Link Handler (CatchUp Pattern)

Complete deep link system with `DeepLinkHandler`, `DeepLinkable` interface, and Metro multibinding registration.

### DeepLinkable Interface

```kotlin
// :core:deep-links:api / commonMain
package com.example.app.deeplinks

import com.slack.circuit.runtime.screen.Screen
import kotlinx.collections.immutable.ImmutableMap

/**
 * Marker interface for screens that can be navigated to via deep link.
 * Implement this directly on singleton screens, or use a nested object
 * for screens that require constructor parameters.
 */
interface DeepLinkable {
    fun createScreen(queryParams: ImmutableMap<String, List<String?>>): Screen
}
```

### DeepLinkHandler Interface

```kotlin
// :core:deep-links:api / commonMain
package com.example.app.deeplinks

import com.slack.circuit.runtime.screen.Screen
import kotlinx.collections.immutable.ImmutableList
import okhttp3.HttpUrl

interface DeepLinkHandler {
    fun parse(url: HttpUrl): ImmutableList<Screen>
}
```

### DeepLinkHandlerImpl

```kotlin
// :core:deep-links:impl / commonMain
package com.example.app.deeplinks.impl

import com.example.app.deeplinks.DeepLinkHandler
import com.example.app.deeplinks.DeepLinkable
import com.slack.circuit.runtime.screen.Screen
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import okhttp3.HttpUrl

@Inject
class DeepLinkHandlerImpl(
    private val deepLinkables: Map<String, DeepLinkable>,
) : DeepLinkHandler {

    override fun parse(url: HttpUrl): ImmutableList<Screen> {
        val screens = mutableListOf<Screen>()
        val queryParams: ImmutableMap<String, List<String?>> =
            url.queryParameterNames.associateWith { name ->
                url.queryParameterValues(name)
            }.toImmutableMap()

        for (segment in url.pathSegments) {
            val deepLinkable = deepLinkables[segment] ?: continue
            screens += deepLinkable.createScreen(queryParams)
        }

        return screens.toImmutableList()
    }
}

@ContributesTo(AppScope::class)
interface DeepLinkModule {
    @Provides
    fun provideDeepLinkHandler(impl: DeepLinkHandlerImpl): DeepLinkHandler = impl
}
```

### Android Intent Extension

```kotlin
// :core:deep-links:impl / androidMain
package com.example.app.deeplinks.impl

import android.content.Intent
import android.net.Uri
import com.example.app.deeplinks.DeepLinkHandler
import com.slack.circuit.runtime.screen.Screen
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import okhttp3.HttpUrl.Companion.toHttpUrl

fun DeepLinkHandler.parse(intent: Intent): ImmutableList<Screen> {
    if (intent.action != Intent.ACTION_VIEW) return persistentListOf()
    val uri = intent.data ?: return persistentListOf()
    return parse(uri.toHttpUrl())
}

/**
 * Convert Android Uri to OkHttp HttpUrl.
 * Custom schemes (myapp://) are coerced to https:// because
 * OkHttp's HttpUrl only supports HTTP(S) schemes.
 */
fun Uri.toHttpUrl(): okhttp3.HttpUrl {
    val uriString = toString()
    val coerced = if (scheme != "http" && scheme != "https") {
        uriString.replaceFirst("$scheme://", "https://")
    } else {
        uriString
    }
    return coerced.toHttpUrl()
}
```

### Registering Screens as DeepLinkable

```kotlin
// :feature:home:impl
package com.example.app.feature.home

import com.example.app.deeplinks.DeepLinkable
import com.slack.circuit.runtime.screen.Screen
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.StringKey
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.parcelize.Parcelize

@Parcelize
data object HomeScreen : Screen, DeepLinkable {

    // Registers this screen for the "home" path segment
    @ContributesIntoMap(AppScope::class, binding = binding<DeepLinkable>())
    @StringKey("home")
    override fun createScreen(queryParams: ImmutableMap<String, List<String?>>): Screen = HomeScreen
}
```

```kotlin
// :feature:profile:impl
package com.example.app.feature.profile

import com.example.app.deeplinks.DeepLinkable
import com.slack.circuit.runtime.screen.Screen
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.StringKey
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.parcelize.Parcelize

@Parcelize
data class ProfileScreen(val userId: String) : Screen {

    data class State(
        val userName: String,
        val isLoading: Boolean,
        val eventSink: (Event) -> Unit,
    ) : com.slack.circuit.runtime.CircuitUiState

    sealed interface Event : com.slack.circuit.runtime.CircuitUiEvent {
        data object GoBack : Event
    }

    // Nested object because ProfileScreen has constructor params
    @ContributesIntoMap(AppScope::class, binding = binding<DeepLinkable>())
    @StringKey("profile")
    object Deeplinker : DeepLinkable {
        override fun createScreen(queryParams: ImmutableMap<String, List<String?>>): Screen {
            val userId = queryParams["id"]?.firstOrNull() ?: ""
            return ProfileScreen(userId = userId)
        }
    }
}
```

```kotlin
// :feature:settings:impl
package com.example.app.feature.settings

import com.example.app.deeplinks.DeepLinkable
import com.slack.circuit.runtime.screen.Screen
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.StringKey
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.parcelize.Parcelize

@Parcelize
data class SettingsScreen(val section: String? = null) : Screen {

    @ContributesIntoMap(AppScope::class, binding = binding<DeepLinkable>())
    @StringKey("settings")
    object Deeplinker : DeepLinkable {
        override fun createScreen(queryParams: ImmutableMap<String, List<String?>>): Screen {
            val section = queryParams["section"]?.firstOrNull()
            return SettingsScreen(section = section)
        }
    }
}
```

---

## Example 2: Nav3 Deep Link Router

Parsing intents to NavKeys with backStack initialization for Navigation 3.

```kotlin
// :core:deep-links:impl / androidMain
package com.example.app.deeplinks.nav3

import android.content.Intent
import android.net.Uri
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// --- NavKey definitions ---

@Serializable
data object HomeRoute : NavKey

@Serializable
data class ProfileRoute(val id: String) : NavKey

@Serializable
data object SettingsRoute : NavKey

@Serializable
data class ContentRoute(val contentId: String, val showComments: Boolean = false) : NavKey

// --- Deep Link Router ---

object DeepLinkRouter {

    /**
     * Parse an Intent into a list of NavKeys.
     * Always returns HomeRoute as the first element for back navigation.
     * Returns null if the intent is not a deep link.
     */
    fun parseIntent(intent: Intent): List<NavKey>? {
        if (intent.action != Intent.ACTION_VIEW) return null
        val data = intent.data ?: return null
        return parseUri(data)
    }

    fun parseUri(uri: Uri): List<NavKey>? {
        val pathSegments = uri.pathSegments ?: return null
        if (pathSegments.isEmpty()) return null

        val targetKey: NavKey = when (pathSegments.first()) {
            "profile" -> {
                val id = uri.getQueryParameter("id")
                    ?: pathSegments.getOrNull(1)
                    ?: return null
                ProfileRoute(id = id)
            }
            "settings" -> SettingsRoute
            "content" -> {
                val contentId = pathSegments.getOrNull(1) ?: return null
                val showComments = uri.getBooleanQueryParameter("comments", false)
                ContentRoute(contentId = contentId, showComments = showComments)
            }
            else -> return null
        }

        // HomeRoute is always the root
        return listOf(HomeRoute, targetKey)
    }
}

// --- Activity integration ---

// In your Activity.onCreate:
// val initialKeys = DeepLinkRouter.parseIntent(intent) ?: listOf(HomeRoute)
// setContent { AppNavigation(initialKeys) }
```

### NavDisplay Setup with Deep Link Keys

```kotlin
// :app / androidMain
package com.example.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation3.runtime.NavDisplay
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack

@Composable
fun AppNavigation(initialKeys: List<NavKey>) {
    val backStack = rememberNavBackStack(initialKeys.first())

    // Populate deep link target screens after the root
    LaunchedEffect(Unit) {
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
            entry<HomeRoute> {
                HomeScreen(
                    onNavigateToProfile = { id -> backStack.add(ProfileRoute(id)) },
                    onNavigateToSettings = { backStack.add(SettingsRoute) },
                )
            }
            entry<ProfileRoute> { key ->
                ProfileScreen(
                    userId = key.id,
                    onBack = { backStack.pop() },
                )
            }
            entry<SettingsRoute> {
                SettingsScreen(onBack = { backStack.pop() })
            }
            entry<ContentRoute> { key ->
                ContentScreen(
                    contentId = key.contentId,
                    showComments = key.showComments,
                    onBack = { backStack.pop() },
                )
            }
        }
    )
}
```

---

## Example 3: Android Manifest Configuration

Complete intent-filter configuration supporting custom scheme, App Links, and specific paths.

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.myapp">

    <application
        android:name=".MainApplication"
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/Theme.MyApp">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTask"
            android:theme="@style/Theme.MyApp.Splash">

            <!-- Standard launcher entry -->
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>

            <!-- Custom URI scheme: myapp://open/... -->
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data
                    android:scheme="myapp"
                    android:host="open" />
            </intent-filter>

            <!-- Verified App Links: https://myapp.com/... -->
            <intent-filter android:autoVerify="true">
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="https" android:host="myapp.com" />
                <data android:pathPrefix="/profile" />
                <data android:pathPrefix="/settings" />
                <data android:pathPrefix="/content" />
                <data android:pathPrefix="/about" />
            </intent-filter>

            <!-- Staging environment App Links -->
            <intent-filter android:autoVerify="true">
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="https" android:host="staging.myapp.com" />
                <data android:pathPrefix="/profile" />
                <data android:pathPrefix="/settings" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

### Handling onNewIntent (singleTask)

```kotlin
// androidMain
class MainActivity : ComponentActivity() {

    @Inject lateinit var deepLinkHandler: DeepLinkHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialScreens = deepLinkHandler.parse(intent)
        setContent {
            AppContent(initialScreens = initialScreens.ifEmpty { persistentListOf(HomeScreen) })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // Update the stored intent
        val screens = deepLinkHandler.parse(intent)
        if (screens.isNotEmpty()) {
            // Reset navigation to deep link target
            // Option A: Use a shared state holder
            deepLinkState.value = screens
            // Option B: Recreate the activity
            // recreate()
        }
    }
}
```

### Reactive Deep Link Handling

```kotlin
// Shared state for deep links arriving via onNewIntent
object DeepLinkState {
    val pendingDeepLink = MutableStateFlow<ImmutableList<Screen>?>(null)
}

// In your root Composable
@Composable
fun AppContent(
    initialScreens: ImmutableList<Screen>,
    navigator: Navigator = rememberCircuitNavigator(backStack),
) {
    val backStack = rememberSaveableBackStack(initialScreens)

    // React to new deep links arriving while app is running
    val pendingDeepLink by DeepLinkState.pendingDeepLink.collectAsRetainedState()
    LaunchedEffect(pendingDeepLink) {
        pendingDeepLink?.let { screens ->
            // Reset to home, then navigate to deep link target
            navigator.resetRoot(HomeScreen)
            screens.drop(1).forEach { screen ->
                navigator.goTo(screen)
            }
            DeepLinkState.pendingDeepLink.value = null
        }
    }

    CircuitCompositionLocals(circuit) {
        ContentWithOverlays {
            NavigableCircuitContent(navigator, backStack)
        }
    }
}
```

---

## Example 4: iOS Universal Links Setup

### apple-app-site-association

Host at `https://myapp.com/.well-known/apple-app-site-association`:

```json
{
  "applinks": {
    "details": [
      {
        "appIDs": [
          "ABCDE12345.com.example.myapp",
          "ABCDE12345.com.example.myapp.debug"
        ],
        "components": [
          {
            "/": "/profile/*",
            "comment": "User profile pages"
          },
          {
            "/": "/content/*",
            "comment": "Content detail pages"
          },
          {
            "/": "/settings",
            "comment": "App settings"
          },
          {
            "/": "/about",
            "comment": "About page"
          },
          {
            "/": "/admin/*",
            "exclude": true,
            "comment": "Never open admin in app"
          }
        ]
      }
    ]
  },
  "webcredentials": {
    "apps": [
      "ABCDE12345.com.example.myapp"
    ]
  }
}
```

### iOS AppDelegate / SceneDelegate Handling

```swift
// iosMain (Swift)
import UIKit

class SceneDelegate: UIResponder, UIWindowSceneDelegate {

    func scene(_ scene: UIScene,
               willConnectTo session: UISceneSession,
               options connectionOptions: UIScene.ConnectionOptions) {

        // Handle Universal Link on cold launch
        if let userActivity = connectionOptions.userActivities.first,
           userActivity.activityType == NSUserActivityTypeBrowsingWeb,
           let url = userActivity.webpageURL {
            handleDeepLink(url: url)
        }
    }

    func scene(_ scene: UIScene, continue userActivity: NSUserActivity) {
        // Handle Universal Link when app is already running
        guard userActivity.activityType == NSUserActivityTypeBrowsingWeb,
              let url = userActivity.webpageURL else { return }
        handleDeepLink(url: url)
    }

    private func handleDeepLink(url: URL) {
        // Pass to KMP deep link handler via shared module
        let urlString = url.absoluteString
        DeepLinkBridge.shared.handleUrl(urlString: urlString)
    }
}
```

### KMP Bridge for iOS Deep Links

```kotlin
// iosMain
package com.example.app.deeplinks

import platform.Foundation.NSURL

object DeepLinkBridge {
    private var handler: ((String) -> Unit)? = null

    fun setHandler(handler: (String) -> Unit) {
        this.handler = handler
    }

    fun handleUrl(urlString: String) {
        handler?.invoke(urlString)
    }
}
```

### Entitlements File (MyApp.entitlements)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN"
  "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>com.apple.developer.associated-domains</key>
    <array>
        <string>applinks:myapp.com</string>
        <string>applinks:myapp.com?mode=developer</string>
        <string>webcredentials:myapp.com</string>
    </array>
</dict>
</plist>
```

---

## Example 5: Deep Link with Authentication

Redirect to login if the user is not authenticated, then navigate to the deep link target after login.

```kotlin
// :core:deep-links:impl / commonMain
package com.example.app.deeplinks.auth

import com.example.app.auth.AuthRepository
import com.example.app.deeplinks.DeepLinkHandler
import com.slack.circuit.runtime.screen.Screen
import dev.zacsweers.metro.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import okhttp3.HttpUrl

/**
 * Wraps the real DeepLinkHandler to enforce authentication.
 * If the user is not logged in, the deep link target is stored
 * and the user is sent to LoginScreen. After successful login,
 * [consumePendingDeepLink] returns the stored screens.
 */
@Inject
class AuthAwareDeepLinkHandler(
    private val delegate: DeepLinkHandler,
    private val authRepository: AuthRepository,
) {
    private var pendingScreens: ImmutableList<Screen>? = null

    fun parse(url: HttpUrl): ImmutableList<Screen> {
        val screens = delegate.parse(url)
        if (screens.isEmpty()) return screens

        return if (authRepository.isLoggedIn()) {
            screens
        } else {
            // Store the intended destination and redirect to login
            pendingScreens = screens
            persistentListOf(LoginScreen(returnTo = url.toString()))
        }
    }

    /**
     * Call after successful login to retrieve the stored deep link screens.
     * Returns null if there was no pending deep link.
     */
    fun consumePendingDeepLink(): ImmutableList<Screen>? {
        val screens = pendingScreens
        pendingScreens = null
        return screens
    }
}
```

### LoginScreen Presenter with Deep Link Resume

```kotlin
// :feature:auth:impl
package com.example.app.feature.auth

import com.example.app.deeplinks.auth.AuthAwareDeepLinkHandler
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import com.slack.circuit.codegen.annotations.CircuitInject
import dev.zacsweers.metro.AppScope

@AssistedInject
class LoginPresenter(
    @Assisted private val navigator: Navigator,
    @Assisted private val screen: LoginScreen,
    private val authRepository: AuthRepository,
    private val authDeepLinkHandler: AuthAwareDeepLinkHandler,
) : Presenter<LoginScreen.State> {

    @CircuitInject(LoginScreen::class, AppScope::class)
    @AssistedFactory
    fun interface Factory {
        fun create(navigator: Navigator, screen: LoginScreen): LoginPresenter
    }

    @Composable
    override fun present(): LoginScreen.State {
        var isLoading by rememberRetained { mutableStateOf(false) }
        var error by rememberRetained { mutableStateOf<String?>(null) }

        return LoginScreen.State(
            isLoading = isLoading,
            error = error,
        ) { event ->
            when (event) {
                is LoginScreen.Event.Login -> {
                    isLoading = true
                    error = null
                    scope.launch {
                        val result = authRepository.login(event.email, event.password)
                        isLoading = false
                        if (result.isSuccess) {
                            // Check for pending deep link
                            val pendingScreens = authDeepLinkHandler.consumePendingDeepLink()
                            if (pendingScreens != null) {
                                navigator.resetRoot(pendingScreens.first())
                                pendingScreens.drop(1).forEach { navigator.goTo(it) }
                            } else {
                                navigator.resetRoot(HomeScreen)
                            }
                        } else {
                            error = result.exceptionOrNull()?.message ?: "Login failed"
                        }
                    }
                }
            }
        }
    }
}
```

### NavigationInterceptor Approach (Alternative)

```kotlin
// Use Circuit's NavigationInterceptor to enforce auth on all navigation
class AuthNavigationInterceptor(
    private val authRepository: AuthRepository,
) : NavigationInterceptor {

    // Screens that do not require authentication
    private val publicScreens = setOf(
        LoginScreen::class,
        SignUpScreen::class,
        ForgotPasswordScreen::class,
    )

    override fun goTo(
        screen: Screen,
        navigationContext: NavigationContext,
    ): InterceptedGoToResult {
        if (screen::class in publicScreens) return InterceptedGoToResult.Skipped
        if (authRepository.isLoggedIn()) return InterceptedGoToResult.Skipped

        // Redirect to login
        return InterceptedGoToResult.Rewrite(
            LoginScreen(returnTo = screen.toString())
        )
    }
}

// Usage with rememberInterceptingNavigator
@Composable
fun AppContent(circuit: Circuit) {
    val backStack = rememberSaveableBackStack(HomeScreen)
    val baseNavigator = rememberCircuitNavigator(backStack)
    val navigator = rememberInterceptingNavigator(
        navigator = baseNavigator,
        interceptors = listOf(authInterceptor),
    )
    NavigableCircuitContent(navigator, backStack)
}
```

---

## Example 6: Push Notification Deep Links

Handling notification tap to navigate to a specific screen.

### Notification Payload

```json
{
  "notification": {
    "title": "New message from Alice",
    "body": "Hey, check out this post!"
  },
  "data": {
    "deep_link": "myapp://open/content/456?comments=true",
    "type": "new_message",
    "sender_id": "alice-123"
  }
}
```

### FirebaseMessagingService (Android)

```kotlin
// androidMain
package com.example.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class AppFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val deepLink = message.data["deep_link"] ?: return
        val title = message.notification?.title ?: "New notification"
        val body = message.notification?.body ?: ""

        showNotification(title, body, deepLink)
    }

    private fun showNotification(title: String, body: String, deepLink: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).apply {
            setPackage(packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            deepLink.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val channelId = "deep_link_notifications"
        createNotificationChannel(channelId)

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(deepLink.hashCode(), notification)
    }

    private fun createNotificationChannel(channelId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Deep Link Notifications",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
```

### iOS Push Notification Deep Link Handling

```swift
// iosMain (Swift)
import UserNotifications

class NotificationDelegate: NSObject, UNUserNotificationCenterDelegate {

    // Called when user taps the notification
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let userInfo = response.notification.request.content.userInfo
        if let deepLink = userInfo["deep_link"] as? String {
            DeepLinkBridge.shared.handleUrl(urlString: deepLink)
        }
        completionHandler()
    }

    // Called when notification arrives while app is in foreground
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound])
    }
}
```

### KMP Notification Deep Link Presenter

```kotlin
// commonMain - Presenter that checks for pending notification deep links on screen appear
@AssistedInject
class HomePresenter(
    @Assisted private val navigator: Navigator,
    private val notificationDeepLinkStore: NotificationDeepLinkStore,
) : Presenter<HomeScreen.State> {

    @CircuitInject(HomeScreen::class, AppScope::class)
    @AssistedFactory
    fun interface Factory {
        fun create(navigator: Navigator): HomePresenter
    }

    @Composable
    override fun present(): HomeScreen.State {
        // Check for pending notification deep links on first composition
        LaunchedEffect(Unit) {
            val pendingScreen = notificationDeepLinkStore.consumePending()
            if (pendingScreen != null) {
                navigator.goTo(pendingScreen)
            }
        }

        // ... rest of state
    }
}

// commonMain
@Inject
class NotificationDeepLinkStore {
    private val pending = MutableStateFlow<Screen?>(null)

    fun setPending(screen: Screen) {
        pending.value = screen
    }

    fun consumePending(): Screen? {
        val screen = pending.value
        pending.value = null
        return screen
    }
}
```

---

## Example 7: Deep Link Testing

### adb Commands

```bash
# --- Custom URI Scheme ---

# Profile deep link
adb shell am start -a android.intent.action.VIEW \
    -d "myapp://open/profile/user-123" \
    com.example.myapp

# Profile with query params
adb shell am start -a android.intent.action.VIEW \
    -d "myapp://open/profile?id=user-123&tab=posts" \
    com.example.myapp

# Settings deep link
adb shell am start -a android.intent.action.VIEW \
    -d "myapp://open/settings?section=notifications" \
    com.example.myapp

# Content with nested path
adb shell am start -a android.intent.action.VIEW \
    -d "myapp://open/content/456?comments=true" \
    com.example.myapp

# --- App Links (HTTPS) ---

# Profile via App Link
adb shell am start -a android.intent.action.VIEW \
    -d "https://myapp.com/profile/user-123" \
    com.example.myapp

# --- Verification ---

# Check App Links verification status
adb shell pm get-app-links --user cur com.example.myapp

# Re-verify App Links
adb shell pm set-app-links --package com.example.myapp 0 all
adb shell pm verify-app-links --re-verify com.example.myapp

# List all verified domains
adb shell pm get-app-links com.example.myapp
```

### iOS Simulator Commands

```bash
# Open URL in running simulator
xcrun simctl openurl booted "https://myapp.com/profile/user-123"

# Open custom scheme
xcrun simctl openurl booted "myapp://open/profile/user-123"

# Specific device
xcrun simctl openurl "iPhone 15 Pro" "https://myapp.com/settings"
```

### Unit Tests for DeepLinkHandler

```kotlin
// :core:deep-links:impl / commonTest
package com.example.app.deeplinks.impl

import com.example.app.feature.home.HomeScreen
import com.example.app.feature.profile.ProfileScreen
import com.example.app.feature.settings.SettingsScreen
import kotlinx.collections.immutable.persistentMapOf
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeepLinkHandlerImplTest {

    private val deepLinkables = mapOf(
        "home" to HomeScreen,
        "profile" to ProfileScreen.Deeplinker,
        "settings" to SettingsScreen.Deeplinker,
    )

    private val handler = DeepLinkHandlerImpl(deepLinkables)

    @Test
    fun `parse profile deep link with id query param`() {
        val url = "https://myapp.com/profile?id=user-123".toHttpUrl()
        val screens = handler.parse(url)

        assertEquals(1, screens.size)
        assertEquals(ProfileScreen(userId = "user-123"), screens.first())
    }

    @Test
    fun `parse settings deep link with section`() {
        val url = "https://myapp.com/settings?section=notifications".toHttpUrl()
        val screens = handler.parse(url)

        assertEquals(1, screens.size)
        assertTrue(screens.first() is SettingsScreen)
    }

    @Test
    fun `parse unknown path returns empty list`() {
        val url = "https://myapp.com/unknown/path".toHttpUrl()
        val screens = handler.parse(url)

        assertTrue(screens.isEmpty())
    }

    @Test
    fun `parse profile deep link without id returns empty userId`() {
        val url = "https://myapp.com/profile".toHttpUrl()
        val screens = handler.parse(url)

        assertEquals(1, screens.size)
        assertEquals(ProfileScreen(userId = ""), screens.first())
    }

    @Test
    fun `parse multi-segment path returns multiple screens`() {
        val url = "https://myapp.com/home/profile?id=user-456".toHttpUrl()
        val screens = handler.parse(url)

        assertEquals(2, screens.size)
        assertEquals(HomeScreen, screens[0])
        assertEquals(ProfileScreen(userId = "user-456"), screens[1])
    }

    @Test
    fun `coerced custom scheme parses correctly`() {
        // Simulate what Uri.toHttpUrl() does with custom schemes
        val url = "https://open/profile?id=user-789".toHttpUrl()
        val screens = handler.parse(url)

        assertEquals(1, screens.size)
        assertEquals(ProfileScreen(userId = "user-789"), screens.first())
    }
}
```

### Instrumented Tests (Android)

```kotlin
// androidTest
package com.example.app

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeepLinkInstrumentedTest {

    @Test
    fun customScheme_profileDeepLink_opensProfileScreen() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("myapp://open/profile?id=user-123")
        }
        ActivityScenario.launch<MainActivity>(intent).use {
            // Verify profile screen is displayed
            // Adapt assertions to your UI framework (Compose testing, etc.)
        }
    }

    @Test
    fun appLink_settingsDeepLink_opensSettingsScreen() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://myapp.com/settings?section=notifications")
        }
        ActivityScenario.launch<MainActivity>(intent).use {
            // Verify settings screen is displayed
        }
    }

    @Test
    fun invalidDeepLink_fallsBackToHomeScreen() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("myapp://open/nonexistent")
        }
        ActivityScenario.launch<MainActivity>(intent).use {
            // Verify home screen is displayed (fallback)
        }
    }

    @Test
    fun noDeepLink_normalLaunch_showsHomeScreen() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // Verify home screen is displayed
        }
    }
}
```

### Compose UI Test for Deep Link Navigation

```kotlin
// androidTest (Compose)
package com.example.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import com.slack.circuit.test.FakeNavigator
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test

class DeepLinkComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun deepLinkToProfile_displaysProfileScreen() {
        val initialScreens = persistentListOf(
            HomeScreen,
            ProfileScreen(userId = "user-123"),
        )

        composeRule.setContent {
            // Set up Circuit with initial deep link screens
            TestAppContent(initialScreens = initialScreens)
        }

        // Profile screen should be on top
        composeRule.onNodeWithText("user-123").assertIsDisplayed()
    }
}
```
