---
name: datastore-expert
description: Expert guidance on AndroidX DataStore for KMP. Use for Preferences DataStore (key-value), Proto DataStore (typed schemas with kotlinx-serialization), migration from SharedPreferences, and cross-platform file path setup.
---

# DataStore Expert Skill (v1.2.0)

## Overview

AndroidX DataStore is a data storage solution for key-value pairs and typed objects, designed as a modern replacement for SharedPreferences. DataStore uses Kotlin coroutines and Flow for asynchronous, transactional reads and writes. Starting with 1.1.0, DataStore supports Kotlin Multiplatform via Okio-based file I/O and the `createWithPath` factory APIs. The latest stable version is **1.2.0**.

### DataStore vs SharedPreferences vs Room

| Feature | SharedPreferences | Preferences DataStore | Proto DataStore | Room |
|---------|-------------------|-----------------------|-----------------|------|
| Async API | No (blocking) | Yes (Flow + suspend) | Yes (Flow + suspend) | Yes (Flow + suspend) |
| Type safety | No | Partial (typed keys) | Full (schema) | Full (SQL schema) |
| Transactional | No | Yes | Yes | Yes |
| Process-safe | No | Yes | Yes | Yes |
| KMP support | No (Android only) | Yes (1.1.0+, latest 1.2.0) | Yes (1.1.0+, latest 1.2.0) | Yes (2.7.0+) |
| Schema | None | Key-value pairs | Typed data class | Relational tables |
| Best for | Legacy code | Simple settings | Structured config | Relational data |

### When to Use DataStore

- **Preferences DataStore**: Simple key-value settings (theme, locale, feature flags, onboarding state)
- **Proto DataStore**: Structured configuration with nested objects, lists, or enums (user profile, app config, cached tokens)
- **NOT for**: Large datasets, relational data, or complex queries (use Room/SQLDelight)

## When to Use

- Setting up Preferences DataStore with type-safe keys
- Creating Proto DataStore with `@Serializable` schemas (kotlinx-serialization, NOT protobuf)
- Configuring KMP file paths via `expect`/`actual` `producePath`
- Performing transactional updates with `edit {}`
- Migrating from SharedPreferences to DataStore
- Providing DataStore via Metro DI (`@SingleIn`, `@Provides`)
- Observing DataStore in Circuit Presenters via `collectAsRetainedState`

## Quick Reference

For detailed API reference, see [reference.md](reference.md).
For production examples with Metro + Circuit, see [examples.md](examples.md).

## Core Concepts

### Preferences DataStore (Key-Value)

Stores primitive values indexed by typed keys. No schema definition required.

```kotlin
// 1. Define typed keys
object PrefsKeys {
    val DARK_MODE = booleanPreferencesKey("dark_mode")
    val LANGUAGE = stringPreferencesKey("language")
    val FONT_SIZE = intPreferencesKey("font_size")
}

// 2. Create DataStore with KMP-compatible factory
val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath(
    produceFile = { dataStorePath / "settings.preferences_pb" },
)

// 3. Read via Flow
val darkMode: Flow<Boolean> = dataStore.data.map { prefs ->
    prefs[PrefsKeys.DARK_MODE] ?: false
}

// 4. Write via edit {}
suspend fun setDarkMode(enabled: Boolean) {
    dataStore.edit { prefs ->
        prefs[PrefsKeys.DARK_MODE] = enabled
    }
}
```

### Proto DataStore (Typed Objects)

Stores a single typed object with full schema. Uses kotlinx-serialization for encoding.

```kotlin
// 1. Define @Serializable schema
@Serializable
data class UserSettings(
    val displayName: String = "",
    val notificationsEnabled: Boolean = true,
    val theme: Theme = Theme.SYSTEM,
) {
    @Serializable
    enum class Theme { LIGHT, DARK, SYSTEM }
}

// 2. Implement OkioSerializer
object UserSettingsSerializer : OkioSerializer<UserSettings> {
    override val defaultValue: UserSettings = UserSettings()

    override suspend fun readFrom(source: BufferedSource): UserSettings {
        return Json.decodeFromString(source.readUtf8())
    }

    override suspend fun writeTo(t: UserSettings, sink: BufferedSink) {
        sink.writeUtf8(Json.encodeToString(t))
    }
}

// 3. Create DataStore
val dataStore: DataStore<UserSettings> = DataStoreFactory.createWithPath(
    serializer = UserSettingsSerializer,
    produceFile = { dataStorePath / "user_settings.json" },
)
```

### KMP File Path (expect/actual)

DataStore 1.2.0 uses `okio.Path` for multiplatform file I/O. Define `expect`/`actual` for the data directory.

```kotlin
// commonMain
expect fun dataStoreDir(): Path

// androidMain
actual fun dataStoreDir(): Path =
    applicationContext.filesDir.resolve("datastore").toOkioPath()

// iosMain
actual fun dataStoreDir(): Path {
    val dir = NSSearchPathForDirectoriesInDomains(
        NSDocumentDirectory, NSUserDomainMask, true,
    ).first() as String
    return "$dir/datastore".toPath()
}

// desktopMain
actual fun dataStoreDir(): Path {
    val home = System.getProperty("user.home")
    return "$home/.appname/datastore".toPath()
}
```

## Transactional Updates

All writes go through `edit {}` (Preferences) or `updateData {}` (Proto). These are atomic -- if the lambda throws, no data is written.

```kotlin
// Preferences: edit {}
dataStore.edit { prefs ->
    prefs[PrefsKeys.DARK_MODE] = true
    prefs[PrefsKeys.FONT_SIZE] = 16
    // Both values written atomically
}

// Proto: updateData {}
dataStore.updateData { current ->
    current.copy(
        displayName = "New Name",
        theme = UserSettings.Theme.DARK,
    )
}
```

## Migration from SharedPreferences

Android-only migration path. Runs once on first DataStore access, then deletes the old SharedPreferences file.

```kotlin
// Android-only: migrate SharedPreferences to Preferences DataStore
val dataStore = PreferenceDataStoreFactory.createWithPath(
    migrations = listOf(
        SharedPreferencesMigration(
            context = applicationContext,
            sharedPreferencesName = "legacy_prefs",
        ),
    ),
    produceFile = { dataStoreDir() / "settings.preferences_pb" },
)
```

## Metro DI Integration

Provide DataStore instances as singletons via Metro. DataStore must be a singleton per file to avoid corruption.

```kotlin
@ContributesTo(AppScope::class)
interface DataStoreModule {
    companion object {
        @Provides
        @SingleIn(AppScope::class)
        fun providePreferencesDataStore(
            pathProvider: DataStorePathProvider,
        ): DataStore<Preferences> =
            PreferenceDataStoreFactory.createWithPath(
                produceFile = { pathProvider.path / "settings.preferences_pb" },
            )

        @Provides
        @SingleIn(AppScope::class)
        fun provideUserSettingsDataStore(
            pathProvider: DataStorePathProvider,
        ): DataStore<UserSettings> =
            DataStoreFactory.createWithPath(
                serializer = UserSettingsSerializer,
                produceFile = { pathProvider.path / "user_settings.json" },
            )
    }
}
```

## Common Pitfalls

1. **Multiple DataStore instances for the same file** -- Always use `@SingleIn(AppScope::class)` in Metro. Two DataStore instances writing to the same file causes `CorruptionException`.
2. **Using `create()` instead of `createWithPath()`** -- `create()` takes `java.io.File` (JVM only). Always use `createWithPath()` with `okio.Path` for KMP.
3. **Using protobuf for Proto DataStore** -- In KMP, use `kotlinx-serialization` JSON with `OkioSerializer`. Protobuf requires platform-specific codegen that does not work cross-platform cleanly.
4. **Blocking reads** -- `dataStore.data` is a `Flow`. Never call `.first()` on the main thread. Use `collectAsRetainedState` in Circuit presenters.
5. **Large objects in DataStore** -- DataStore reads/writes the entire file on every operation. Keep objects small (< 1MB). For large data, use SQLDelight or Room.
6. **Missing `expect`/`actual` for file path** -- Each platform needs its own path resolution. Forgetting `iosMain` or `desktopMain` causes `LinkError` at compile time.
7. **Not handling `CorruptionException`** -- Provide a `ReplaceFileCorruptionHandler` to recover from corrupted files, especially after schema changes.
8. **SharedPreferences migration on KMP** -- `SharedPreferencesMigration` is Android-only. On other platforms, implement `DataMigration<T>` manually if needed.

## Artifacts

| Artifact | Purpose |
|----------|---------|
| `androidx.datastore:datastore-preferences-core` | Preferences DataStore (KMP, no Android dependency) |
| `androidx.datastore:datastore-core-okio` | Core DataStore with Okio serializers (KMP) |
| `androidx.datastore:datastore-preferences` | Preferences DataStore with Android Context helpers |
| `androidx.datastore:datastore` | Proto DataStore with Android Context helpers |

## Core Rules

1. **Always use `createWithPath` for KMP** -- Never use the Android `Context`-based delegates (`preferencesDataStore`, `dataStore`).
2. **One DataStore instance per file** -- Enforce via `@SingleIn(AppScope::class)` in Metro.
3. **Use `OkioSerializer<T>` for Proto DataStore** -- NOT `Serializer<T>` (which uses `java.io.InputStream`).
4. **kotlinx-serialization JSON for Proto schemas** -- NOT protobuf, NOT Java serialization.
5. **Provide `ReplaceFileCorruptionHandler`** in production -- Prevents crashes from corrupted files.
6. **Never store secrets in DataStore** -- DataStore files are not encrypted. Use platform keychain/keystore for secrets.
