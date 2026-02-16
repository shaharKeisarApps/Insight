# Android Platform Expert - API Reference

> **Scope**: Android-specific APIs for KMP projects
> **Source Set**: All Android code in `androidMain` unless noted
> **Min API**: 24 | **Target API**: 35
> **DI**: Metro (NOT Hilt)

---

## 1. Activity & Fragment

### ComponentActivity

The base activity for Compose-based Android apps.

```kotlin
// androidMain
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                // Root composable
            }
        }
    }
}
```

### Key Activity Methods

| Method | Purpose | Notes |
|--------|---------|-------|
| `setContent {}` | Set Compose UI root | From `activity-compose` artifact |
| `enableEdgeToEdge()` | Edge-to-edge display | From `androidx.activity` 1.8+ |
| `onNewIntent(intent)` | Handle new intents | For `singleTop`/`singleTask` launch modes |
| `onConfigurationChanged(config)` | Handle config changes | Only if declared in manifest |
| `addOnNewIntentListener {}` | Lifecycle-aware intent listener | Preferred over override in new code |

### Fragment (Legacy Interop)

Fragments are rarely needed in pure Compose apps. Use only when embedding Compose in existing Fragment-based navigation.

```kotlin
class LegacyFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent { /* Compose UI */ }
    }
}
```

---

## 2. Services

### Service Lifecycle

| Callback | Called When |
|----------|------------|
| `onCreate()` | Service first created |
| `onStartCommand(intent, flags, startId)` | Each `startService()` / `startForegroundService()` call |
| `onBind(intent)` | Client calls `bindService()` |
| `onUnbind(intent)` | All clients unbound |
| `onDestroy()` | Service being destroyed |

### onStartCommand Return Values

| Value | Behavior |
|-------|----------|
| `START_NOT_STICKY` | Do not recreate after kill |
| `START_STICKY` | Recreate with null intent |
| `START_REDELIVER_INTENT` | Recreate with last intent |

### Foreground Service (API 34+ Type Requirements)

Starting with API 34, `foregroundServiceType` must be declared in the manifest and passed to `startForeground()`.

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />

<service
    android:name=".SyncService"
    android:foregroundServiceType="dataSync"
    android:exported="false" />
```

#### Foreground Service Types (API 34+)

| Type | Permission Required | Use Case |
|------|-------------------|----------|
| `camera` | `FOREGROUND_SERVICE_CAMERA` | Camera access |
| `connectedDevice` | `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Bluetooth, USB |
| `dataSync` | `FOREGROUND_SERVICE_DATA_SYNC` | Data upload/download |
| `health` | `FOREGROUND_SERVICE_HEALTH` | Fitness tracking |
| `location` | `FOREGROUND_SERVICE_LOCATION` | Location tracking |
| `mediaPlayback` | `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Audio/video playback |
| `mediaProjection` | `FOREGROUND_SERVICE_MEDIA_PROJECTION` | Screen capture |
| `microphone` | `FOREGROUND_SERVICE_MICROPHONE` | Audio recording |
| `phoneCall` | `FOREGROUND_SERVICE_PHONE_CALL` | Ongoing calls |
| `remoteMessaging` | `FOREGROUND_SERVICE_REMOTE_MESSAGING` | Messaging |
| `shortService` | (none) | Brief tasks (<3 min) |
| `specialUse` | `FOREGROUND_SERVICE_SPECIAL_USE` | Other (requires justification) |

### Bound Service with ServiceConnection

```kotlin
private val connection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
        val localBinder = binder as MyService.LocalBinder
        service = localBinder.getService()
    }

    override fun onServiceDisconnected(name: ComponentName) {
        service = null
    }
}

// Bind
bindService(Intent(this, MyService::class.java), connection, Context.BIND_AUTO_CREATE)

// Unbind (in onStop or onDestroy)
unbindService(connection)
```

---

## 3. BroadcastReceiver

### Dynamic Registration (Preferred)

```kotlin
ContextCompat.registerReceiver(
    context,
    receiver,
    IntentFilter(Intent.ACTION_BATTERY_CHANGED),
    ContextCompat.RECEIVER_NOT_EXPORTED  // Required flag on API 33+
)
```

### Exported Flag (API 33+)

| Flag | Meaning |
|------|---------|
| `RECEIVER_EXPORTED` | Other apps can send broadcasts to this receiver |
| `RECEIVER_NOT_EXPORTED` | Only same-app or system broadcasts |

### Common System Broadcasts

| Action | Description |
|--------|-------------|
| `Intent.ACTION_BATTERY_CHANGED` | Battery level/status changed |
| `Intent.ACTION_POWER_CONNECTED` | Device plugged in |
| `Intent.ACTION_SCREEN_ON/OFF` | Screen on/off |
| `Intent.ACTION_AIRPLANE_MODE_CHANGED` | Airplane mode toggled |
| `ConnectivityManager.CONNECTIVITY_ACTION` | Deprecated -- use `NetworkCallback` |

---

## 4. ContentProvider

### When to Use in KMP

ContentProviders are primarily needed when:
- Sharing data with other apps (contacts, media)
- Using `ContentResolver` to access system data
- `FileProvider` for sharing files via intents

### FileProvider (Most Common in KMP)

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

### ContentResolver Queries

```kotlin
context.contentResolver.query(
    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
    arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME),
    null, null,
    "${MediaStore.Images.Media.DATE_ADDED} DESC"
)?.use { cursor ->
    while (cursor.moveToNext()) {
        val id = cursor.getLong(0)
        val name = cursor.getString(1)
    }
}
```

---

## 5. AndroidView / AndroidViewBinding

### AndroidView

Wraps an Android `View` inside Compose.

```kotlin
@Composable
fun AndroidView(
    factory: (Context) -> T,            // Create the View (called once)
    modifier: Modifier = Modifier,
    update: (T) -> Unit = {},           // Called on every recomposition
    onRelease: (T) -> Unit = {},        // Cleanup when removed from composition
    onReset: ((T) -> Unit)? = null,     // Reset for reuse (node reuse)
)
```

### AndroidViewBinding

Wraps a View Binding layout inside Compose.

```kotlin
AndroidViewBinding(
    factory = MyLegacyLayoutBinding::inflate,
    modifier = Modifier.fillMaxSize(),
    update = {
        textView.text = "Updated from Compose"
    }
)
```

### Lifecycle-Aware AndroidView

For views requiring lifecycle callbacks (MapView, SurfaceView):

```kotlin
val lifecycleOwner = LocalLifecycleOwner.current

AndroidView(
    factory = { context -> MapView(context) },
    update = { mapView ->
        // Update map properties
    }
)

DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_RESUME -> mapView.onResume()
            Lifecycle.Event.ON_PAUSE -> mapView.onPause()
            else -> {}
        }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
}
```

---

## 6. Permissions

### ActivityResultContracts

| Contract | Purpose |
|----------|---------|
| `RequestPermission()` | Single permission |
| `RequestMultiplePermissions()` | Multiple permissions |
| `TakePicture()` | Capture photo |
| `TakePicturePreview()` | Capture thumbnail |
| `PickContact()` | Pick a contact |
| `PickVisualMedia()` | Photo picker (API 19+) |
| `GetContent()` | Pick file via SAF |
| `OpenDocument()` | Open document via SAF |
| `CreateDocument()` | Create document via SAF |

### Permission Launcher in Compose

```kotlin
val launcher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
) { isGranted: Boolean ->
    if (isGranted) { /* granted */ }
    else { /* denied */ }
}

// Check before requesting
val context = LocalContext.current
when {
    ContextCompat.checkSelfPermission(
        context, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED -> { /* already granted */ }

    shouldShowRequestPermissionRationale(
        context as Activity, Manifest.permission.CAMERA
    ) -> { /* show rationale UI */ }

    else -> launcher.launch(Manifest.permission.CAMERA)
}
```

---

## 7. Context Access

### In Compose

```kotlin
val context = LocalContext.current       // current Context
val activity = context as? Activity      // cast to Activity if needed
```

### Via Metro DI

```kotlin
@DependencyGraph(AppScope::class)
interface AppGraph {
    @Provides
    fun provideContext(application: Application): Context = application.applicationContext

    @Provides
    fun provideResources(context: Context): Resources = context.resources
}
```

> **Warning**: Never use `@ApplicationContext` or `@ActivityContext` -- those are Hilt annotations. With Metro, provide Context explicitly via `@Provides`.

---

## 8. Intents & Deep Links

### PendingIntent Flags (API 31+)

Starting with API 31, all PendingIntents must specify mutability.

| Flag | When to Use |
|------|-------------|
| `PendingIntent.FLAG_IMMUTABLE` | Default -- use unless intent needs modification |
| `PendingIntent.FLAG_MUTABLE` | Only when the PendingIntent needs to be modified (e.g., inline reply) |

```kotlin
PendingIntent.getActivity(
    context, requestCode, intent,
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
)
```

### TaskStackBuilder

Constructs a synthetic back stack for deep link destinations.

```kotlin
val resultIntent = Intent(context, DetailActivity::class.java)
val stackBuilder = TaskStackBuilder.create(context).apply {
    addNextIntentWithParentStack(resultIntent)
}
val pendingIntent = stackBuilder.getPendingIntent(
    0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
)
```

---

## 9. Notifications

### NotificationChannel (Required API 26+)

```kotlin
val channel = NotificationChannel(
    CHANNEL_ID,
    "Channel Name",
    NotificationManager.IMPORTANCE_DEFAULT
).apply {
    description = "Channel description"
    enableVibration(true)
    setShowBadge(true)
}
notificationManager.createNotificationChannel(channel)
```

### Importance Levels

| Level | Behavior |
|-------|----------|
| `IMPORTANCE_HIGH` | Sound + heads-up |
| `IMPORTANCE_DEFAULT` | Sound, no heads-up |
| `IMPORTANCE_LOW` | No sound |
| `IMPORTANCE_MIN` | No sound, no status bar icon |

### NotificationCompat.Builder

```kotlin
NotificationCompat.Builder(context, CHANNEL_ID)
    .setSmallIcon(R.drawable.ic_notification)
    .setContentTitle("Title")
    .setContentText("Content")
    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
    .setContentIntent(pendingIntent)
    .setAutoCancel(true)
    .build()
```

### POST_NOTIFICATIONS Permission (API 33+)

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

Must be requested at runtime on API 33+.

---

## 10. WorkManager

### Work Request Types

| Type | Use Case |
|------|----------|
| `OneTimeWorkRequest` | Single execution |
| `PeriodicWorkRequest` | Recurring (min 15 min interval) |

### CoroutineWorker

```kotlin
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            // Perform work
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry()
            else Result.failure()
        }
    }
}
```

### Constraints

```kotlin
val constraints = Constraints.Builder()
    .setRequiredNetworkType(NetworkType.CONNECTED)
    .setRequiresBatteryNotLow(true)
    .setRequiresStorageNotLow(true)
    .setRequiresCharging(false)
    .build()
```

### Enqueueing Work

```kotlin
val request = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
    .setConstraints(constraints)
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
    .addTag("sync")
    .build()

WorkManager.getInstance(context)
    .enqueueUniquePeriodicWork("sync", ExistingPeriodicWorkPolicy.KEEP, request)
```

---

## 11. Lifecycle

### Lifecycle.Event

| Event | Corresponding Callback |
|-------|----------------------|
| `ON_CREATE` | `onCreate()` |
| `ON_START` | `onStart()` |
| `ON_RESUME` | `onResume()` |
| `ON_PAUSE` | `onPause()` |
| `ON_STOP` | `onStop()` |
| `ON_DESTROY` | `onDestroy()` |

### ProcessLifecycleOwner

Provides lifecycle for the entire application process (foreground/background transitions).

```kotlin
ProcessLifecycleOwner.get().lifecycle.addObserver(
    LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> { /* App entered foreground */ }
            Lifecycle.Event.ON_STOP -> { /* App entered background */ }
            else -> {}
        }
    }
)
```

### LifecycleEffect in Compose

```kotlin
LifecycleResumeEffect(key) {
    // Called on ON_RESUME
    onPauseOrDispose {
        // Called on ON_PAUSE or disposal
    }
}

LifecycleStartEffect(key) {
    // Called on ON_START
    onStopOrDispose {
        // Called on ON_STOP or disposal
    }
}
```

---

## 12. Window & Display

### WindowSizeClass

Adaptive layout breakpoints from `material3-window-size-class`.

| Class | Width | Typical Device |
|-------|-------|---------------|
| `Compact` | < 600dp | Phone portrait |
| `Medium` | 600-840dp | Tablet portrait / foldable |
| `Expanded` | > 840dp | Tablet landscape / desktop |

```kotlin
val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
when (windowSizeClass.windowWidthSizeClass) {
    WindowWidthSizeClass.COMPACT -> { /* Phone layout */ }
    WindowWidthSizeClass.MEDIUM -> { /* Tablet layout */ }
    WindowWidthSizeClass.EXPANDED -> { /* Large layout */ }
}
```

### Edge-to-Edge

```kotlin
// In Activity.onCreate(), before setContent
enableEdgeToEdge()
```

### WindowInsetsCompat

```kotlin
Modifier.windowInsetsPadding(WindowInsets.systemBars)
Modifier.windowInsetsPadding(WindowInsets.navigationBars)
Modifier.windowInsetsPadding(WindowInsets.statusBars)
Modifier.windowInsetsPadding(WindowInsets.ime)
Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
```

### Consuming Insets

```kotlin
Scaffold(
    modifier = Modifier.fillMaxSize(),
    contentWindowInsets = WindowInsets(0),  // Scaffold does NOT consume insets
) { innerPadding ->
    Content(
        modifier = Modifier
            .padding(innerPadding)
            .consumeWindowInsets(innerPadding)
    )
}
```
