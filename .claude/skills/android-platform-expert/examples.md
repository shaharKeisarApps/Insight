# Android Platform Expert - Production Examples

> **DI**: Metro (NOT Hilt)
> **Architecture**: Circuit MVI or ViewModel + Nav3 (ask developer)
> **Source Sets**: Common interfaces in `commonMain`, Android impl in `androidMain`

---

## 1. expect/actual Pattern -- Platform Abstraction

Define a common interface in `commonMain`, provide the Android implementation in `androidMain`, and bind it via Metro.

### commonMain -- Interface

```kotlin
// commonMain/src/com/example/platform/DeviceInfo.kt
interface DeviceInfo {
    val osName: String
    val osVersion: String
    val deviceModel: String
    val appVersionName: String
    val appVersionCode: Long
    val isDebugBuild: Boolean
}
```

### androidMain -- Implementation

```kotlin
// androidMain/src/com/example/platform/AndroidDeviceInfo.kt
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.example.di.AppScope
import com.slack.metro.ContributesBinding
import com.slack.metro.Inject
import com.slack.metro.SingleIn

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class AndroidDeviceInfo @Inject constructor(
    private val context: Context,
) : DeviceInfo {

    override val osName: String = "Android"

    override val osVersion: String = Build.VERSION.RELEASE

    override val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}"

    override val appVersionName: String by lazy {
        try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            info.versionName.orEmpty()
        } catch (_: PackageManager.NameNotFoundException) {
            "unknown"
        }
    }

    override val appVersionCode: Long by lazy {
        try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            info.longVersionCode
        } catch (_: PackageManager.NameNotFoundException) {
            -1L
        }
    }

    override val isDebugBuild: Boolean =
        (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
}
```

---

## 2. Foreground Service -- DownloadService

A complete foreground service with Metro-injected dependencies, notification channel, and progress updates.

### Manifest Declaration

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<service
    android:name=".download.DownloadService"
    android:foregroundServiceType="dataSync"
    android:exported="false" />
```

### Service Implementation

```kotlin
// androidMain/src/com/example/download/DownloadService.kt
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class DownloadService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL) ?: run {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildProgressNotification(0)
        startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )

        scope.launch {
            try {
                performDownload(url) { progress ->
                    notificationManager.notify(
                        NOTIFICATION_ID,
                        buildProgressNotification(progress),
                    )
                }
                notificationManager.notify(
                    NOTIFICATION_ID,
                    buildCompleteNotification(),
                )
            } catch (e: Exception) {
                notificationManager.notify(
                    NOTIFICATION_ID,
                    buildErrorNotification(e.message ?: "Download failed"),
                )
            } finally {
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Downloads",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "File download progress"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildProgressNotification(progress: Int) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading...")
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setSilent(true)
            .build()

    private fun buildCompleteNotification() =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download complete")
            .setAutoCancel(true)
            .build()

    private fun buildErrorNotification(message: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Download failed")
            .setContentText(message)
            .setAutoCancel(true)
            .build()

    private suspend fun performDownload(url: String, onProgress: (Int) -> Unit) {
        // Use Ktor HttpClient or OkHttp for actual download
        // Call onProgress(percent) as data arrives
    }

    companion object {
        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 1001
        private const val EXTRA_URL = "extra_url"

        fun start(context: Context, url: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                putExtra(EXTRA_URL, url)
            }
            context.startForegroundService(intent)
        }
    }
}
```

---

## 3. Runtime Permissions -- Camera Permission

Full permission request flow with rationale dialog and settings redirect.

```kotlin
// androidMain/src/com/example/permissions/CameraPermissionScreen.kt
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

@Composable
fun CameraPermissionScreen(
    onPermissionGranted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    var showRationale by remember { mutableStateOf(false) }
    var permanentlyDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        when {
            isGranted -> onPermissionGranted()
            activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(
                activity, Manifest.permission.CAMERA
            ) -> {
                permanentlyDenied = true
            }
            else -> {
                showRationale = true
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Button(
            onClick = {
                when {
                    ContextCompat.checkSelfPermission(
                        context, Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED -> {
                        onPermissionGranted()
                    }
                    activity != null && ActivityCompat.shouldShowRequestPermissionRationale(
                        activity, Manifest.permission.CAMERA
                    ) -> {
                        showRationale = true
                    }
                    else -> {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }
            },
        ) {
            Text("Open Camera")
        }
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text("Camera Permission Required") },
            text = { Text("The camera is needed to scan documents. Please grant access.") },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }) { Text("Grant") }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false }) { Text("Cancel") }
            },
        )
    }

    if (permanentlyDenied) {
        AlertDialog(
            onDismissRequest = { permanentlyDenied = false },
            title = { Text("Permission Denied") },
            text = { Text("Camera permission was permanently denied. Please enable it in Settings.") },
            confirmButton = {
                TextButton(onClick = {
                    permanentlyDenied = false
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }) { Text("Open Settings") }
            },
            dismissButton = {
                TextButton(onClick = { permanentlyDenied = false }) { Text("Cancel") }
            },
        )
    }
}
```

---

## 4. AndroidView Interop -- MapView with Lifecycle

Embedding a MapView in Compose with proper lifecycle management.

```kotlin
// androidMain/src/com/example/map/MapComposable.kt
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

@Composable
fun GoogleMapView(
    latitude: Double,
    longitude: Double,
    title: String,
    modifier: Modifier = Modifier,
    onMapReady: ((GoogleMap) -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnMapReady = rememberUpdatedState(onMapReady)

    val mapView = remember {
        MapView(context).apply {
            onCreate(Bundle.EMPTY)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {} // Already called
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            view.getMapAsync { googleMap ->
                val position = LatLng(latitude, longitude)
                googleMap.clear()
                googleMap.addMarker(
                    MarkerOptions()
                        .position(position)
                        .title(title),
                )
                currentOnMapReady.value?.invoke(googleMap)
            }
        },
    )
}
```

---

## 5. WorkManager Background Sync

A CoroutineWorker with Metro-injected repository, periodic scheduling, and retry logic.

### Worker Implementation

```kotlin
// androidMain/src/com/example/sync/SyncWorker.kt
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.SyncRepository

class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
    private val syncRepository: SyncRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            syncRepository.syncAll()
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        const val WORK_NAME = "periodic_sync"
        private const val MAX_RETRIES = 3
    }
}
```

### WorkerFactory with Metro DI

```kotlin
// androidMain/src/com/example/sync/SyncWorkerFactory.kt
import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.example.data.SyncRepository
import com.example.di.AppScope
import com.slack.metro.ContributesBinding
import com.slack.metro.Inject
import com.slack.metro.SingleIn

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class SyncWorkerFactory @Inject constructor(
    private val syncRepository: SyncRepository,
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? {
        return when (workerClassName) {
            SyncWorker::class.java.name -> SyncWorker(
                appContext, workerParameters, syncRepository,
            )
            else -> null
        }
    }
}
```

### WorkManager Initialization (Disable Default, Use Custom)

```xml
<!-- AndroidManifest.xml -->
<provider
    android:name="androidx.startup.InitializationProvider"
    android:authorities="${applicationId}.androidx-startup"
    android:exported="false"
    tools:node="merge">
    <meta-data
        android:name="androidx.work.WorkManagerInitializer"
        android:value="androidx.startup"
        tools:node="remove" />
</provider>
```

```kotlin
// androidMain/src/com/example/App.kt
import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager

class App : Application(), Configuration.Provider {

    // Injected by Metro via the AppGraph
    lateinit var workerFactory: SyncWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
```

### Scheduling Periodic Sync

```kotlin
// androidMain/src/com/example/sync/SyncScheduler.kt
import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.di.AppScope
import com.slack.metro.Inject
import com.slack.metro.SingleIn
import java.util.concurrent.TimeUnit

@SingleIn(AppScope::class)
class SyncScheduler @Inject constructor(
    private val context: Context,
) {
    fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            repeatInterval = 1,
            repeatIntervalTimeUnit = TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag("sync")
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancelSync() {
        WorkManager.getInstance(context).cancelUniqueWork(SyncWorker.WORK_NAME)
    }
}
```

---

## 6. Notification Helper

Metro-provided NotificationManager with channel creation and builder utilities.

```kotlin
// androidMain/src/com/example/notifications/NotificationHelper.kt
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.di.AppScope
import com.example.ui.MainActivity
import com.slack.metro.Inject
import com.slack.metro.SingleIn

@SingleIn(AppScope::class)
class NotificationHelper @Inject constructor(
    private val context: Context,
    private val notificationManager: NotificationManager,
) {
    init {
        createChannels()
    }

    private fun createChannels() {
        val channels = listOf(
            NotificationChannel(
                CHANNEL_GENERAL,
                "General",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "General notifications" },
            NotificationChannel(
                CHANNEL_IMPORTANT,
                "Important",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Important alerts"
                enableVibration(true)
            },
            NotificationChannel(
                CHANNEL_SILENT,
                "Background",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Background task updates"
                setShowBadge(false)
            },
        )
        notificationManager.createNotificationChannels(channels)
    }

    fun showNotification(
        id: Int,
        title: String,
        body: String,
        channelId: String = CHANNEL_GENERAL,
    ) {
        if (!hasNotificationPermission()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(id, notification)
    }

    fun cancel(id: Int) {
        notificationManager.cancel(id)
    }

    fun cancelAll() {
        notificationManager.cancelAll()
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    companion object {
        const val CHANNEL_GENERAL = "general"
        const val CHANNEL_IMPORTANT = "important"
        const val CHANNEL_SILENT = "silent"
    }
}
```

### Metro DI Binding for NotificationManager

```kotlin
// androidMain/src/com/example/di/AndroidModule.kt
import android.app.NotificationManager
import android.content.Context
import com.example.di.AppScope
import com.slack.metro.DependencyGraph
import com.slack.metro.Provides
import com.slack.metro.SingleIn

@DependencyGraph(AppScope::class)
interface AndroidModule {
    companion object {
        @Provides
        @SingleIn(AppScope::class)
        fun provideNotificationManager(context: Context): NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
}
```

---

## 7. Edge-to-Edge Setup

Complete edge-to-edge configuration with proper inset handling.

```kotlin
// androidMain/src/com/example/ui/MainActivity.kt
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    // Let content handle its own insets
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .consumeWindowInsets(innerPadding)
                            .windowInsetsPadding(WindowInsets.safeDrawing),
                    ) {
                        // App content here
                        AppContent()
                    }
                }
            }
        }
    }
}
```

### TopAppBar with Status Bar Insets

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(title: String) {
    TopAppBar(
        title = { Text(title) },
        windowInsets = WindowInsets.statusBars,  // Respect status bar
    )
}
```

### Bottom Navigation with Navigation Bar Insets

```kotlin
@Composable
fun AppBottomBar(selectedTab: Tab, onTabSelected: (Tab) -> Unit) {
    NavigationBar(
        windowInsets = WindowInsets.navigationBars,  // Respect nav bar
    ) {
        Tab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
            )
        }
    }
}
```

---

## 8. Deep Link Handling

Activity with intent-filter that parses URIs and navigates to Circuit screens.

### Manifest Configuration

```xml
<activity
    android:name=".ui.MainActivity"
    android:launchMode="singleTop"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>

    <!-- Deep Link: https://example.com/product/{id} -->
    <intent-filter android:autoVerify="true">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data
            android:scheme="https"
            android:host="example.com"
            android:pathPrefix="/product/" />
    </intent-filter>

    <!-- Custom Scheme: myapp://product/{id} -->
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data
            android:scheme="myapp"
            android:host="product" />
    </intent-filter>
</activity>
```

### Deep Link Parser

```kotlin
// androidMain/src/com/example/deeplink/DeepLinkParser.kt
import android.content.Intent
import android.net.Uri
import com.example.screens.HomeScreen
import com.example.screens.ProductDetailScreen
import com.example.screens.ProfileScreen
import com.slack.circuit.runtime.screen.Screen

object DeepLinkParser {

    fun parse(intent: Intent): Screen? {
        val uri = intent.data ?: return null
        return parse(uri)
    }

    fun parse(uri: Uri): Screen? {
        return when {
            // https://example.com/product/{id} or myapp://product/{id}
            uri.pathSegments.firstOrNull() == "product" || uri.host == "product" -> {
                val productId = uri.lastPathSegment ?: uri.getQueryParameter("id") ?: return null
                ProductDetailScreen(productId = productId)
            }

            // https://example.com/profile/{username}
            uri.pathSegments.firstOrNull() == "profile" -> {
                val username = uri.lastPathSegment ?: return null
                ProfileScreen(username = username)
            }

            else -> null
        }
    }
}
```

### Handling in Activity

```kotlin
// androidMain/src/com/example/ui/MainActivity.kt
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialScreen = DeepLinkParser.parse(intent) ?: HomeScreen

        setContent {
            AppTheme {
                CircuitContent(screen = initialScreen)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val screen = DeepLinkParser.parse(intent)
        if (screen != null) {
            // Navigate to the deep-linked screen via Circuit Navigator
            // This depends on your Navigator setup -- typically you would
            // expose a callback or use a shared state holder
        }
    }
}
```

---

## 9. Context Provider in Metro

Complete Metro DependencyGraph providing Android system services and Context.

```kotlin
// androidMain/src/com/example/di/AndroidAppGraph.kt
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.net.ConnectivityManager
import android.os.PowerManager
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Build
import com.example.di.AppScope
import com.slack.metro.DependencyGraph
import com.slack.metro.Provides
import com.slack.metro.SingleIn

@DependencyGraph(AppScope::class)
interface AndroidAppGraph {

    companion object {
        @Provides
        @SingleIn(AppScope::class)
        fun provideContext(application: Application): Context =
            application.applicationContext

        @Provides
        @SingleIn(AppScope::class)
        fun provideResources(context: Context): Resources =
            context.resources

        @Provides
        @SingleIn(AppScope::class)
        fun provideSharedPreferences(context: Context): SharedPreferences =
            context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

        @Provides
        @SingleIn(AppScope::class)
        fun provideNotificationManager(context: Context): NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        @Provides
        @SingleIn(AppScope::class)
        fun provideConnectivityManager(context: Context): ConnectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        @Provides
        @SingleIn(AppScope::class)
        fun provideAlarmManager(context: Context): AlarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        @Provides
        @SingleIn(AppScope::class)
        fun provideClipboardManager(context: Context): ClipboardManager =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        @Provides
        @SingleIn(AppScope::class)
        fun providePowerManager(context: Context): PowerManager =
            context.getSystemService(Context.POWER_SERVICE) as PowerManager

        @Provides
        @SingleIn(AppScope::class)
        fun provideVibrator(context: Context): Vibrator =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                    as VibratorManager
                manager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
    }
}
```

---

## 10. Platform File System -- expect/actual

Common file operations interface with Android implementation using `Context.filesDir`.

### commonMain -- Interface

```kotlin
// commonMain/src/com/example/platform/FileSystem.kt
interface PlatformFileSystem {
    suspend fun readText(fileName: String): String?
    suspend fun writeText(fileName: String, content: String)
    suspend fun delete(fileName: String): Boolean
    suspend fun exists(fileName: String): Boolean
    suspend fun listFiles(): List<String>
}
```

### androidMain -- Implementation

```kotlin
// androidMain/src/com/example/platform/AndroidFileSystem.kt
import android.content.Context
import com.example.di.AppScope
import com.slack.metro.ContributesBinding
import com.slack.metro.Inject
import com.slack.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class AndroidFileSystem @Inject constructor(
    private val context: Context,
) : PlatformFileSystem {

    private val filesDir: File
        get() = context.filesDir

    override suspend fun readText(fileName: String): String? = withContext(Dispatchers.IO) {
        try {
            File(filesDir, fileName).readText()
        } catch (_: FileNotFoundException) {
            null
        }
    }

    override suspend fun writeText(fileName: String, content: String) =
        withContext(Dispatchers.IO) {
            val file = File(filesDir, fileName)
            file.parentFile?.mkdirs()
            file.writeText(content)
        }

    override suspend fun delete(fileName: String): Boolean = withContext(Dispatchers.IO) {
        File(filesDir, fileName).delete()
    }

    override suspend fun exists(fileName: String): Boolean = withContext(Dispatchers.IO) {
        File(filesDir, fileName).exists()
    }

    override suspend fun listFiles(): List<String> = withContext(Dispatchers.IO) {
        filesDir.listFiles()?.map { it.name } ?: emptyList()
    }
}
```

### Cache Directory Variant

```kotlin
// For temporary/cache files, use context.cacheDir instead
@ContributesBinding(AppScope::class, boundType = CacheFileSystem::class)
@SingleIn(AppScope::class)
class AndroidCacheFileSystem @Inject constructor(
    private val context: Context,
) : CacheFileSystem {

    private val cacheDir: File
        get() = context.cacheDir

    suspend fun readText(fileName: String): String? = withContext(Dispatchers.IO) {
        try {
            File(cacheDir, fileName).readText()
        } catch (_: FileNotFoundException) {
            null
        }
    }

    suspend fun writeText(fileName: String, content: String) = withContext(Dispatchers.IO) {
        File(cacheDir, fileName).writeText(content)
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }
}
```
