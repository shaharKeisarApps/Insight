# DataStore API Reference (v1.2.0)

Comprehensive API reference for AndroidX DataStore with KMP support via Okio.

---

## Preferences DataStore

### Preference Keys

Type-safe keys for storing primitive values in `Preferences`.

```kotlin
// Package: androidx.datastore.preferences.core

fun booleanPreferencesKey(name: String): Preferences.Key<Boolean>
fun intPreferencesKey(name: String): Preferences.Key<Int>
fun longPreferencesKey(name: String): Preferences.Key<Long>
fun floatPreferencesKey(name: String): Preferences.Key<Float>
fun doublePreferencesKey(name: String): Preferences.Key<Double>
fun stringPreferencesKey(name: String): Preferences.Key<String>
fun stringSetPreferencesKey(name: String): Preferences.Key<Set<String>>
fun byteArrayPreferencesKey(name: String): Preferences.Key<ByteArray>
```

### Preferences

Immutable map-like container for preference key-value pairs.

```kotlin
// Package: androidx.datastore.preferences.core

abstract class Preferences {

    /** Returns the value for the given key, or null if not set. */
    abstract operator fun <T> get(key: Key<T>): T?

    /** Returns true if the preferences contain the given key. */
    abstract fun <T> contains(key: Key<T>): Boolean

    /** Returns all key-value pairs as a Map. */
    abstract fun asMap(): Map<Key<*>, Any>

    /** A strongly-typed key for a preference value. */
    class Key<T>(val name: String)
}
```

### MutablePreferences

Mutable version of `Preferences` used inside `edit {}` blocks.

```kotlin
// Package: androidx.datastore.preferences.core

class MutablePreferences : Preferences() {

    /** Sets the value for the given key. */
    operator fun <T> set(key: Key<T>, value: T)

    /** Removes the value for the given key. Returns the previous value or null. */
    fun <T> remove(key: Key<T>): T?

    /** Removes all preferences. */
    fun clear()

    /** Adds all key-value pairs from another Preferences. */
    fun putAll(vararg pairs: Preferences.Pair<*>)
}
```

### PreferenceDataStoreFactory

Factory for creating `DataStore<Preferences>` instances. Use `createWithPath` for KMP.

```kotlin
// Package: androidx.datastore.preferences.core

object PreferenceDataStoreFactory {

    /**
     * KMP-compatible factory. Uses okio.Path for file location.
     * Available since DataStore 1.1.0.
     */
    fun createWithPath(
        corruptionHandler: ReplaceFileCorruptionHandler<Preferences>? = null,
        migrations: List<DataMigration<Preferences>> = listOf(),
        scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        produceFile: () -> Path,
    ): DataStore<Preferences>

    /**
     * JVM-only factory. Uses java.io.File.
     * Do NOT use for KMP -- use createWithPath instead.
     */
    fun create(
        corruptionHandler: ReplaceFileCorruptionHandler<Preferences>? = null,
        migrations: List<DataMigration<Preferences>> = listOf(),
        scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        produceFile: () -> File,
    ): DataStore<Preferences>
}
```

### DataStore<Preferences> Extensions

```kotlin
// Package: androidx.datastore.preferences.core

/**
 * Atomically updates the preferences.
 * The transform block receives MutablePreferences and any mutations are written atomically.
 * If the transform throws, no data is written.
 */
suspend fun DataStore<Preferences>.edit(
    transform: suspend (MutablePreferences) -> Unit,
): Preferences
```

---

## Proto DataStore (Typed DataStore)

### DataStoreFactory

Factory for creating typed `DataStore<T>` instances with custom serializers.

```kotlin
// Package: androidx.datastore.core

object DataStoreFactory {

    /**
     * KMP-compatible factory. Uses okio.Path and OkioSerializer.
     * Available since DataStore 1.1.0.
     */
    fun <T> createWithPath(
        serializer: OkioSerializer<T>,
        corruptionHandler: ReplaceFileCorruptionHandler<T>? = null,
        migrations: List<DataMigration<T>> = listOf(),
        scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        produceFile: () -> Path,
    ): DataStore<T>

    /**
     * JVM-only factory. Uses java.io.File and Serializer<T>.
     * Do NOT use for KMP -- use createWithPath instead.
     */
    fun <T> create(
        serializer: Serializer<T>,
        corruptionHandler: ReplaceFileCorruptionHandler<T>? = null,
        migrations: List<DataMigration<T>> = listOf(),
        scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        produceFile: () -> File,
    ): DataStore<T>
}
```

### DataStore<T> Interface

The core DataStore interface.

```kotlin
// Package: androidx.datastore.core

interface DataStore<T> {

    /**
     * A Flow of the current data. Emits the current value on collection,
     * then emits again whenever the data changes.
     */
    val data: Flow<T>

    /**
     * Atomically updates the data. The transform receives the current value
     * and returns the new value. If the transform throws, no data is written.
     */
    suspend fun updateData(transform: suspend (t: T) -> T): T
}
```

### OkioSerializer<T>

KMP-compatible serializer interface using Okio's `BufferedSource`/`BufferedSink`.

```kotlin
// Package: androidx.datastore.core.okio

interface OkioSerializer<T> {

    /** The default value returned when no data exists on disk. */
    val defaultValue: T

    /**
     * Deserializes the data from the given Okio BufferedSource.
     * @throws CorruptionException if the data is corrupted
     */
    suspend fun readFrom(source: BufferedSource): T

    /**
     * Serializes the data to the given Okio BufferedSink.
     */
    suspend fun writeTo(t: T, sink: BufferedSink)
}
```

### Serializer<T> (JVM-only)

JVM-only serializer using `java.io.InputStream`/`OutputStream`. Do NOT use for KMP.

```kotlin
// Package: androidx.datastore.core

interface Serializer<T> {
    val defaultValue: T
    suspend fun readFrom(input: InputStream): T
    suspend fun writeTo(t: T, output: OutputStream)
}
```

---

## Corruption Handling

### ReplaceFileCorruptionHandler<T>

Handles data corruption by replacing the corrupted data with a fallback value.

```kotlin
// Package: androidx.datastore.core.handlers

class ReplaceFileCorruptionHandler<T>(
    val produceNewData: (CorruptionException) -> T,
)
```

Usage:

```kotlin
val dataStore = PreferenceDataStoreFactory.createWithPath(
    corruptionHandler = ReplaceFileCorruptionHandler { exception ->
        // Log the corruption
        logger.error("DataStore corrupted", exception)
        // Return empty preferences
        emptyPreferences()
    },
    produceFile = { path / "settings.preferences_pb" },
)

// For Proto DataStore
val protoStore = DataStoreFactory.createWithPath(
    serializer = UserSettingsSerializer,
    corruptionHandler = ReplaceFileCorruptionHandler { exception ->
        logger.error("UserSettings corrupted", exception)
        UserSettings() // Return default
    },
    produceFile = { path / "user_settings.json" },
)
```

### CorruptionException

Thrown by serializers when data cannot be deserialized.

```kotlin
// Package: androidx.datastore.core

class CorruptionException(
    message: String,
    cause: Throwable? = null,
)
```

---

## Migration APIs

### DataMigration<T>

Interface for migrating data into DataStore. Migrations run before any data access.

```kotlin
// Package: androidx.datastore.core

interface DataMigration<T> {

    /**
     * Returns true if this migration should run. Called before migrate().
     * Once cleanUp() completes successfully, shouldMigrate() should return false.
     */
    suspend fun shouldMigrate(currentData: T): Boolean

    /**
     * Performs the migration. Receives the current data and returns the migrated data.
     */
    suspend fun migrate(currentData: T): T

    /**
     * Called after a successful migration. Use to clean up old data sources.
     * If cleanUp() throws, the migration will be retried on next DataStore access.
     */
    suspend fun cleanUp()
}
```

### SharedPreferencesMigration (Android-only)

Migrates data from SharedPreferences to Preferences DataStore.

```kotlin
// Package: androidx.datastore.preferences

class SharedPreferencesMigration(
    context: Context,
    sharedPreferencesName: String,
    keysToMigrate: Set<String> = MIGRATE_ALL_KEYS,
) : DataMigration<Preferences>

// Overload accepting SharedPreferences instance directly
class SharedPreferencesMigration(
    sharedPreferences: SharedPreferences,
    keysToMigrate: Set<String> = MIGRATE_ALL_KEYS,
) : DataMigration<Preferences>
```

### SharedPreferencesMigration for Proto DataStore (Android-only)

Migrates from SharedPreferences to a typed DataStore.

```kotlin
// Package: androidx.datastore.preferences

class SharedPreferencesMigration<T>(
    context: Context,
    sharedPreferencesName: String,
    keysToMigrate: Set<String> = MIGRATE_ALL_KEYS,
    migrate: suspend (SharedPreferencesView, currentData: T) -> T,
) : DataMigration<T>
```

---

## Okio Path Utilities

### Path Construction

```kotlin
// okio.Path - Multiplatform file path

// From string
val path: Path = "/data/user/0/com.example/files/datastore".toPath()

// Path division (appending segments)
val filePath: Path = parentDir / "settings.preferences_pb"

// From java.io.File (JVM/Android)
val okioPath: Path = javaFile.toOkioPath()
```

### Platform Path Patterns

```kotlin
// Android: Context.filesDir
actual fun dataStoreDir(): Path =
    context.filesDir.resolve("datastore").toOkioPath()

// iOS: NSDocumentDirectory
actual fun dataStoreDir(): Path {
    val dir = NSSearchPathForDirectoriesInDomains(
        NSDocumentDirectory, NSUserDomainMask, true,
    ).first() as String
    return "$dir/datastore".toPath()
}

// Desktop JVM: user.home
actual fun dataStoreDir(): Path {
    val home = System.getProperty("user.home")
    return "$home/.myapp/datastore".toPath()
}
```

---

## Preferences Utility Functions

```kotlin
// Package: androidx.datastore.preferences.core

/** Creates an empty Preferences instance. */
fun emptyPreferences(): Preferences

/** Creates a Preferences instance from key-value pairs. */
fun preferencesOf(vararg pairs: Preferences.Pair<*>): Preferences

/** Creates a MutablePreferences instance from key-value pairs. */
fun mutablePreferencesOf(vararg pairs: Preferences.Pair<*>): MutablePreferences

/** Creates a Preferences.Pair for use with preferencesOf. */
infix fun <T> Preferences.Key<T>.to(value: T): Preferences.Pair<T>
```

---

## Gradle Dependencies

### KMP (commonMain)

```toml
[versions]
datastore = "1.2.0"

[libraries]
# Preferences DataStore (KMP-compatible, no Android dependency)
datastore-preferences-core = { module = "androidx.datastore:datastore-preferences-core", version.ref = "datastore" }

# Core DataStore with Okio serializers (KMP-compatible)
datastore-core-okio = { module = "androidx.datastore:datastore-core-okio", version.ref = "datastore" }
```

### Android-specific (androidMain)

```toml
[libraries]
# Preferences DataStore with Android Context helpers (preferencesDataStore delegate)
datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }

# Proto DataStore with Android Context helpers (dataStore delegate)
datastore = { module = "androidx.datastore:datastore", version.ref = "datastore" }
```

### build.gradle.kts

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.datastore.preferences.core)
            implementation(libs.datastore.core.okio)
        }
        androidMain.dependencies {
            // Only if you need SharedPreferencesMigration or Context delegates
            implementation(libs.datastore.preferences)
        }
    }
}
```

---

## Key Imports

```kotlin
// Core
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataMigration

// Okio Serializer (KMP)
import androidx.datastore.core.okio.OkioSerializer

// Preferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences

// Okio
import okio.Path
import okio.Path.Companion.toPath
import okio.Path.Companion.toOkioPath
import okio.BufferedSource
import okio.BufferedSink

// Android-only migration
import androidx.datastore.preferences.SharedPreferencesMigration

// Metro DI
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
```
