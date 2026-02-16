# DataStore Implementation Examples (v1.2.0)

Production-ready examples using AndroidX DataStore with Metro DI, Circuit MVI, and KMP targets.

---

## 1. Preferences DataStore KMP Setup with expect/actual + Metro

Complete end-to-end setup: platform-specific path resolution, Metro DI module, and singleton DataStore provision.

```kotlin
// --- commonMain: expect declaration ---
package com.example.platform

import okio.Path

/**
 * Returns the platform-specific directory for DataStore files.
 * Each platform must provide its own actual implementation.
 */
expect fun dataStoreDir(): Path
```

```kotlin
// --- androidMain: actual implementation ---
package com.example.platform

import android.content.Context
import okio.Path
import okio.Path.Companion.toOkioPath

/**
 * Android stores DataStore files in the app's internal files directory.
 * This survives app updates but is deleted on uninstall.
 */
actual fun dataStoreDir(): Path =
    applicationContext.filesDir.resolve("datastore").toOkioPath()

// Application context holder -- set in Application.onCreate()
lateinit var applicationContext: Context
    private set

fun initApplicationContext(context: Context) {
    applicationContext = context.applicationContext
}
```

```kotlin
// --- iosMain: actual implementation ---
package com.example.platform

import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/**
 * iOS stores DataStore files in the app's Documents directory.
 */
actual fun dataStoreDir(): Path {
    val documentDir = NSSearchPathForDirectoriesInDomains(
        NSDocumentDirectory, NSUserDomainMask, true,
    ).first() as String
    return "$documentDir/datastore".toPath()
}
```

```kotlin
// --- desktopMain: actual implementation ---
package com.example.platform

import okio.Path
import okio.Path.Companion.toPath

/**
 * Desktop stores DataStore files in a dot-directory under user home.
 */
actual fun dataStoreDir(): Path {
    val home = System.getProperty("user.home")
    return "$home/.myapp/datastore".toPath()
}
```

```kotlin
// --- commonMain: DataStore path provider with Metro DI ---
package com.example.data.datastore

import com.example.platform.dataStoreDir
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import okio.Path

/**
 * Provides the base directory for all DataStore files.
 * Injected as a singleton to ensure consistent path resolution.
 */
@SingleIn(AppScope::class)
@Inject
class DataStorePathProvider {
    val path: Path get() = dataStoreDir()
}
```

```kotlin
// --- commonMain: Metro DI module for Preferences DataStore ---
package com.example.di

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.example.data.datastore.DataStorePathProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@ContributesTo(AppScope::class)
interface PreferencesDataStoreModule {
    companion object {
        @Provides
        @SingleIn(AppScope::class)
        fun providePreferencesDataStore(
            pathProvider: DataStorePathProvider,
        ): DataStore<Preferences> =
            PreferenceDataStoreFactory.createWithPath(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                produceFile = { pathProvider.path / "app_settings.preferences_pb" },
            )
    }
}
```

---

## 2. User Settings Repository with Flow

A repository wrapping Preferences DataStore that exposes type-safe accessors and reactive Flows.

```kotlin
package com.example.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

// Keys scoped to this repository
private object SettingsKeys {
    val DARK_MODE = booleanPreferencesKey("settings_dark_mode")
    val LANGUAGE = stringPreferencesKey("settings_language")
    val FONT_SIZE = intPreferencesKey("settings_font_size")
    val ONBOARDING_COMPLETE = booleanPreferencesKey("settings_onboarding_complete")
    val NOTIFICATION_ENABLED = booleanPreferencesKey("settings_notification_enabled")
}

data class AppSettings(
    val darkMode: Boolean = false,
    val language: String = "en",
    val fontSize: Int = 14,
    val onboardingComplete: Boolean = false,
    val notificationEnabled: Boolean = true,
)

interface SettingsRepository {
    /** Observes the full settings as a reactive Flow. */
    fun observeSettings(): Flow<AppSettings>

    /** Observes a single setting. */
    fun observeDarkMode(): Flow<Boolean>

    /** Updates dark mode. */
    suspend fun setDarkMode(enabled: Boolean)

    /** Updates language. */
    suspend fun setLanguage(language: String)

    /** Updates font size. */
    suspend fun setFontSize(size: Int)

    /** Marks onboarding as complete. */
    suspend fun setOnboardingComplete()

    /** Updates notification preference. */
    suspend fun setNotificationEnabled(enabled: Boolean)

    /** Clears all settings (reset to defaults). */
    suspend fun clearAll()
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class DefaultSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override fun observeSettings(): Flow<AppSettings> =
        dataStore.data
            .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
            .map { prefs ->
                AppSettings(
                    darkMode = prefs[SettingsKeys.DARK_MODE] ?: false,
                    language = prefs[SettingsKeys.LANGUAGE] ?: "en",
                    fontSize = prefs[SettingsKeys.FONT_SIZE] ?: 14,
                    onboardingComplete = prefs[SettingsKeys.ONBOARDING_COMPLETE] ?: false,
                    notificationEnabled = prefs[SettingsKeys.NOTIFICATION_ENABLED] ?: true,
                )
            }

    override fun observeDarkMode(): Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[SettingsKeys.DARK_MODE] ?: false }

    override suspend fun setDarkMode(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[SettingsKeys.DARK_MODE] = enabled }
    }

    override suspend fun setLanguage(language: String) {
        dataStore.edit { prefs -> prefs[SettingsKeys.LANGUAGE] = language }
    }

    override suspend fun setFontSize(size: Int) {
        dataStore.edit { prefs -> prefs[SettingsKeys.FONT_SIZE] = size }
    }

    override suspend fun setOnboardingComplete() {
        dataStore.edit { prefs -> prefs[SettingsKeys.ONBOARDING_COMPLETE] = true }
    }

    override suspend fun setNotificationEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[SettingsKeys.NOTIFICATION_ENABLED] = enabled }
    }

    override suspend fun clearAll() {
        dataStore.edit { prefs -> prefs.clear() }
    }
}
```

---

## 3. Proto DataStore with @Serializable

Full typed DataStore using kotlinx-serialization JSON. Includes OkioSerializer, Metro DI, and repository.

```kotlin
// --- Schema definition ---
package com.example.data.userprofile

import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val avatarUrl: String = "",
    val preferences: ProfilePreferences = ProfilePreferences(),
    val lastSyncTimestamp: Long = 0L,
) {
    @Serializable
    data class ProfilePreferences(
        val theme: Theme = Theme.SYSTEM,
        val compactMode: Boolean = false,
        val notificationChannels: List<NotificationChannel> = emptyList(),
    )

    @Serializable
    enum class Theme { LIGHT, DARK, SYSTEM }

    @Serializable
    data class NotificationChannel(
        val id: String,
        val enabled: Boolean = true,
    )
}
```

```kotlin
// --- OkioSerializer ---
package com.example.data.userprofile

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.okio.OkioSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okio.BufferedSink
import okio.BufferedSource

object UserProfileSerializer : OkioSerializer<UserProfile> {

    private val json = Json {
        ignoreUnknownKeys = true  // Forward compatibility with schema changes
        encodeDefaults = true      // Ensure all fields are persisted
    }

    override val defaultValue: UserProfile = UserProfile()

    override suspend fun readFrom(source: BufferedSource): UserProfile {
        try {
            return json.decodeFromString(
                UserProfile.serializer(),
                source.readUtf8(),
            )
        } catch (e: SerializationException) {
            throw CorruptionException("Cannot read UserProfile", e)
        }
    }

    override suspend fun writeTo(t: UserProfile, sink: BufferedSink) {
        sink.writeUtf8(
            json.encodeToString(UserProfile.serializer(), t),
        )
    }
}
```

```kotlin
// --- Metro DI Module ---
package com.example.di

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.example.data.datastore.DataStorePathProvider
import com.example.data.userprofile.UserProfile
import com.example.data.userprofile.UserProfileSerializer
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@ContributesTo(AppScope::class)
interface UserProfileDataStoreModule {
    companion object {
        @Provides
        @SingleIn(AppScope::class)
        fun provideUserProfileDataStore(
            pathProvider: DataStorePathProvider,
        ): DataStore<UserProfile> =
            DataStoreFactory.createWithPath(
                serializer = UserProfileSerializer,
                corruptionHandler = ReplaceFileCorruptionHandler { UserProfile() },
                produceFile = { pathProvider.path / "user_profile.json" },
            )
    }
}
```

```kotlin
// --- Repository ---
package com.example.data.userprofile

import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface UserProfileRepository {
    fun observeProfile(): Flow<UserProfile>
    fun observeTheme(): Flow<UserProfile.Theme>
    suspend fun updateDisplayName(name: String)
    suspend fun updateTheme(theme: UserProfile.Theme)
    suspend fun toggleCompactMode()
    suspend fun updateNotificationChannel(channelId: String, enabled: Boolean)
    suspend fun recordSync()
    suspend fun clearProfile()
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class DefaultUserProfileRepository(
    private val dataStore: DataStore<UserProfile>,
) : UserProfileRepository {

    override fun observeProfile(): Flow<UserProfile> = dataStore.data

    override fun observeTheme(): Flow<UserProfile.Theme> =
        dataStore.data.map { it.preferences.theme }

    override suspend fun updateDisplayName(name: String) {
        dataStore.updateData { current ->
            current.copy(displayName = name)
        }
    }

    override suspend fun updateTheme(theme: UserProfile.Theme) {
        dataStore.updateData { current ->
            current.copy(
                preferences = current.preferences.copy(theme = theme),
            )
        }
    }

    override suspend fun toggleCompactMode() {
        dataStore.updateData { current ->
            current.copy(
                preferences = current.preferences.copy(
                    compactMode = !current.preferences.compactMode,
                ),
            )
        }
    }

    override suspend fun updateNotificationChannel(channelId: String, enabled: Boolean) {
        dataStore.updateData { current ->
            val channels = current.preferences.notificationChannels.toMutableList()
            val index = channels.indexOfFirst { it.id == channelId }
            if (index >= 0) {
                channels[index] = channels[index].copy(enabled = enabled)
            } else {
                channels.add(UserProfile.NotificationChannel(channelId, enabled))
            }
            current.copy(
                preferences = current.preferences.copy(notificationChannels = channels),
            )
        }
    }

    override suspend fun recordSync() {
        dataStore.updateData { current ->
            current.copy(lastSyncTimestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds())
        }
    }

    override suspend fun clearProfile() {
        dataStore.updateData { UserProfile() }
    }
}
```

---

## 4. SharedPreferences Migration (Android)

Migrating legacy SharedPreferences to both Preferences DataStore and Proto DataStore.

```kotlin
// --- Preferences DataStore migration ---
package com.example.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import okio.Path.Companion.toOkioPath

@ContributesTo(AppScope::class)
interface AndroidDataStoreMigrationModule {
    companion object {

        /**
         * Creates a Preferences DataStore that migrates from the old "app_prefs"
         * SharedPreferences on first access. After migration, the SharedPreferences
         * file is deleted automatically.
         */
        @Provides
        @SingleIn(AppScope::class)
        fun providePreferencesDataStoreWithMigration(
            context: Context,
        ): DataStore<Preferences> =
            PreferenceDataStoreFactory.createWithPath(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                migrations = listOf(
                    SharedPreferencesMigration(
                        context = context,
                        sharedPreferencesName = "app_prefs",
                        // Migrate all keys. To migrate specific keys:
                        // keysToMigrate = setOf("dark_mode", "language"),
                    ),
                ),
                produceFile = {
                    context.filesDir.resolve("datastore").toOkioPath() /
                        "app_settings.preferences_pb"
                },
            )
    }
}
```

```kotlin
// --- Proto DataStore migration from SharedPreferences ---
package com.example.di

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.example.data.userprofile.UserProfile
import com.example.data.userprofile.UserProfileSerializer
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import okio.Path.Companion.toOkioPath

/**
 * Custom DataMigration that reads legacy SharedPreferences values
 * and maps them into the typed UserProfile schema.
 */
class UserProfileSharedPrefsMigration(
    private val context: Context,
    private val sharedPrefsName: String = "user_prefs",
) : DataMigration<UserProfile> {

    private val sharedPrefs: SharedPreferences by lazy {
        context.getSharedPreferences(sharedPrefsName, Context.MODE_PRIVATE)
    }

    override suspend fun shouldMigrate(currentData: UserProfile): Boolean {
        // Migrate only if the old SharedPreferences file exists and has data
        return sharedPrefs.all.isNotEmpty()
    }

    override suspend fun migrate(currentData: UserProfile): UserProfile {
        return currentData.copy(
            displayName = sharedPrefs.getString("display_name", "") ?: "",
            email = sharedPrefs.getString("email", "") ?: "",
            preferences = currentData.preferences.copy(
                theme = when (sharedPrefs.getString("theme", "system")) {
                    "light" -> UserProfile.Theme.LIGHT
                    "dark" -> UserProfile.Theme.DARK
                    else -> UserProfile.Theme.SYSTEM
                },
                compactMode = sharedPrefs.getBoolean("compact_mode", false),
            ),
        )
    }

    override suspend fun cleanUp() {
        // Delete the old SharedPreferences file after successful migration
        sharedPrefs.edit().clear().apply()
    }
}

@ContributesTo(AppScope::class)
interface UserProfileMigrationModule {
    companion object {
        @Provides
        @SingleIn(AppScope::class)
        fun provideUserProfileDataStoreWithMigration(
            context: Context,
        ): DataStore<UserProfile> =
            DataStoreFactory.createWithPath(
                serializer = UserProfileSerializer,
                corruptionHandler = ReplaceFileCorruptionHandler { UserProfile() },
                migrations = listOf(
                    UserProfileSharedPrefsMigration(context),
                ),
                produceFile = {
                    context.filesDir.resolve("datastore").toOkioPath() /
                        "user_profile.json"
                },
            )
    }
}
```

---

## 5. Circuit Presenter Integration

Using DataStore repositories in a Circuit Presenter with `collectAsRetainedState`.

```kotlin
package com.example.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.settings.AppSettings
import com.example.data.settings.SettingsRepository
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.collectAsRetainedState
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.screen.Screen
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

@Parcelize
data object SettingsScreen : Screen {
    data class State(
        val settings: AppSettings,
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data class ToggleDarkMode(val enabled: Boolean) : Event
        data class SetFontSize(val size: Int) : Event
        data class ToggleNotifications(val enabled: Boolean) : Event
        data object ResetSettings : Event
    }
}

@AssistedInject
class SettingsPresenter(
    @Assisted private val navigator: Navigator,
    private val settingsRepo: SettingsRepository,
) : Presenter<SettingsScreen.State> {

    @CircuitInject(SettingsScreen::class, AppScope::class)
    @AssistedFactory
    fun interface Factory {
        fun create(navigator: Navigator): SettingsPresenter
    }

    @Composable
    override fun present(): SettingsScreen.State {
        // collectAsRetainedState survives config changes
        val settings by settingsRepo.observeSettings()
            .collectAsRetainedState(initial = AppSettings())

        val scope = rememberCoroutineScope()

        return SettingsScreen.State(
            settings = settings,
        ) { event ->
            when (event) {
                is SettingsScreen.Event.ToggleDarkMode -> {
                    scope.launch { settingsRepo.setDarkMode(event.enabled) }
                }
                is SettingsScreen.Event.SetFontSize -> {
                    scope.launch { settingsRepo.setFontSize(event.size) }
                }
                is SettingsScreen.Event.ToggleNotifications -> {
                    scope.launch { settingsRepo.setNotificationEnabled(event.enabled) }
                }
                is SettingsScreen.Event.ResetSettings -> {
                    scope.launch { settingsRepo.clearAll() }
                }
            }
        }
    }
}

@CircuitInject(SettingsScreen::class, AppScope::class)
@Composable
fun SettingsUi(state: SettingsScreen.State, modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier) { padding ->
        Column(Modifier.padding(padding)) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(16.dp),
            )

            // Dark mode toggle
            SettingsToggleRow(
                label = "Dark Mode",
                checked = state.settings.darkMode,
                onCheckedChange = { state.eventSink(SettingsScreen.Event.ToggleDarkMode(it)) },
            )

            // Notifications toggle
            SettingsToggleRow(
                label = "Notifications",
                checked = state.settings.notificationEnabled,
                onCheckedChange = { state.eventSink(SettingsScreen.Event.ToggleNotifications(it)) },
            )

            // Font size selector
            SettingsRow(
                label = "Font Size",
                value = "${state.settings.fontSize}sp",
                onClick = {
                    val nextSize = when (state.settings.fontSize) {
                        12 -> 14
                        14 -> 16
                        16 -> 18
                        else -> 12
                    }
                    state.eventSink(SettingsScreen.Event.SetFontSize(nextSize))
                },
            )

            // Language display
            SettingsRow(
                label = "Language",
                value = state.settings.language,
                onClick = { /* Navigate to language picker */ },
            )
        }
    }
}

@Composable
private fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
```

---

## 6. Type-Safe Wrapper Extensions

Utility extensions that provide type-safe, null-safe access to Preferences DataStore values with defaults.

```kotlin
package com.example.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Observes a single preference key with a default value.
 * Returns a Flow that emits the current value and updates reactively.
 */
fun <T> DataStore<Preferences>.observe(
    key: Preferences.Key<T>,
    defaultValue: T,
): Flow<T> = data.map { prefs -> prefs[key] ?: defaultValue }

/**
 * Observes a single preference key, returning null if not set.
 */
fun <T> DataStore<Preferences>.observeNullable(
    key: Preferences.Key<T>,
): Flow<T?> = data.map { prefs -> prefs[key] }

/**
 * Sets a single preference value atomically.
 */
suspend fun <T> DataStore<Preferences>.set(
    key: Preferences.Key<T>,
    value: T,
) {
    edit { prefs -> prefs[key] = value }
}

/**
 * Removes a single preference key.
 */
suspend fun <T> DataStore<Preferences>.remove(
    key: Preferences.Key<T>,
) {
    edit { prefs -> prefs.remove(key) }
}

/**
 * Toggles a boolean preference. Returns the new value.
 */
suspend fun DataStore<Preferences>.toggle(
    key: Preferences.Key<Boolean>,
): Boolean {
    var newValue = false
    edit { prefs ->
        newValue = !(prefs[key] ?: false)
        prefs[key] = newValue
    }
    return newValue
}

/**
 * Increments an integer preference by the given amount. Returns the new value.
 */
suspend fun DataStore<Preferences>.increment(
    key: Preferences.Key<Int>,
    amount: Int = 1,
): Int {
    var newValue = 0
    edit { prefs ->
        newValue = (prefs[key] ?: 0) + amount
        prefs[key] = newValue
    }
    return newValue
}
```

```kotlin
// --- Usage in a repository ---
package com.example.data.analytics

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.data.datastore.increment
import com.example.data.datastore.observe
import com.example.data.datastore.set
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow

private object AnalyticsKeys {
    val APP_LAUNCH_COUNT = intPreferencesKey("analytics_launch_count")
    val LAST_LAUNCH_TIMESTAMP = longPreferencesKey("analytics_last_launch")
    val LAST_SCREEN = stringPreferencesKey("analytics_last_screen")
}

interface AnalyticsPrefsRepository {
    fun observeLaunchCount(): Flow<Int>
    suspend fun recordLaunch()
    suspend fun recordScreenView(screenName: String)
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class DefaultAnalyticsPrefsRepository(
    private val dataStore: DataStore<Preferences>,
) : AnalyticsPrefsRepository {

    override fun observeLaunchCount(): Flow<Int> =
        dataStore.observe(AnalyticsKeys.APP_LAUNCH_COUNT, defaultValue = 0)

    override suspend fun recordLaunch() {
        dataStore.increment(AnalyticsKeys.APP_LAUNCH_COUNT)
        dataStore.set(
            AnalyticsKeys.LAST_LAUNCH_TIMESTAMP,
            kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
        )
    }

    override suspend fun recordScreenView(screenName: String) {
        dataStore.set(AnalyticsKeys.LAST_SCREEN, screenName)
    }
}
```
