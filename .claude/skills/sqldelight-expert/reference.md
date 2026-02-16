# SQLDelight API Reference

**Version: 2.2.1**

## Gradle Plugin Configuration

```kotlin
// build.gradle.kts (module level)
plugins {
    id("app.cash.sqldelight") version "2.2.1"
}

sqldelight {
    databases {
        create("AppDatabase") {
            packageName.set("com.example.db")
            // Optional: specify dialect for advanced SQL features
            dialect("app.cash.sqldelight:sqlite-3-38-dialect:2.2.1")
            // Optional: generate suspend query functions
            generateAsync.set(false)
            // Optional: derive schema from migration files instead of .sq CREATE statements
            deriveSchemaFromMigrations.set(false)
        }
    }
}
```

### Dependencies

```kotlin
// build.gradle.kts (module level)
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("app.cash.sqldelight:runtime:2.2.1")
            implementation("app.cash.sqldelight:coroutines-extensions:2.2.1")
            // Primitive type adapters (Boolean, Int, Short, Float)
            implementation("app.cash.sqldelight:primitive-adapters:2.2.1")
        }
        androidMain.dependencies {
            implementation("app.cash.sqldelight:android-driver:2.2.1")
        }
        nativeMain.dependencies {
            implementation("app.cash.sqldelight:native-driver:2.2.1")
        }
        jvmMain.dependencies {
            implementation("app.cash.sqldelight:sqlite-driver:2.2.1")
        }
    }
}
```

## .sq File Syntax

Place `.sq` files in `src/commonMain/sqldelight/com/example/db/` (matching your `packageName`).

### CREATE TABLE

```sql
-- src/commonMain/sqldelight/com/example/db/User.sq

import kotlin.Boolean;
import kotlinx.datetime.Instant;

CREATE TABLE user (
    id TEXT NOT NULL PRIMARY KEY,
    email TEXT NOT NULL UNIQUE,
    display_name TEXT NOT NULL,
    avatar_url TEXT,
    is_verified INTEGER AS Boolean NOT NULL DEFAULT 0,
    created_at TEXT AS Instant NOT NULL,
    updated_at TEXT AS Instant NOT NULL
);

CREATE INDEX user_email_idx ON user(email);
```

### SELECT

```sql
-- Named query: generates a function selectAll() returning Flow<List<User>>
selectAll:
SELECT *
FROM user
ORDER BY display_name ASC;

-- Named query with parameter: generates selectById(id: String)
selectById:
SELECT *
FROM user
WHERE id = :id;

-- Named query with multiple params
searchByName:
SELECT *
FROM user
WHERE display_name LIKE '%' || :query || '%'
ORDER BY display_name ASC
LIMIT :limit;

-- Projection: generates a custom data class
selectEmailAndName:
SELECT email, display_name
FROM user
WHERE is_verified = 1;
```

### INSERT

```sql
-- Insert with explicit columns
insert:
INSERT INTO user(id, email, display_name, avatar_url, is_verified, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, ?, ?);

-- Insert or replace (upsert)
upsert:
INSERT OR REPLACE INTO user(id, email, display_name, avatar_url, is_verified, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, ?, ?);

-- Insert full object (generated data class parameter)
insertObject:
INSERT INTO user
VALUES ?;
```

### UPDATE

```sql
updateDisplayName:
UPDATE user
SET display_name = :name, updated_at = :updatedAt
WHERE id = :id;

updateVerified:
UPDATE user
SET is_verified = :verified, updated_at = :updatedAt
WHERE id = :id;
```

### DELETE

```sql
deleteById:
DELETE FROM user
WHERE id = :id;

deleteAll:
DELETE FROM user;
```

## Generated Query API

SQLDelight generates a `UserQueries` class (based on the `.sq` filename) with methods for each named query.

### Synchronous Execution

```kotlin
// Returns List<User>
val users: List<User> = database.userQueries.selectAll().executeAsList()

// Returns User? (null if not found)
val user: User? = database.userQueries.selectById("user-1").executeAsOneOrNull()

// Returns User (throws if not found)
val user: User = database.userQueries.selectById("user-1").executeAsOne()
```

### Flow-Based Reactive Queries

Requires `coroutines-extensions` dependency.

```kotlin
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import app.cash.sqldelight.coroutines.mapToOneNotNull

// Flow<List<User>> -- re-emits whenever user table changes
val usersFlow: Flow<List<User>> =
    database.userQueries.selectAll()
        .asFlow()
        .mapToList(Dispatchers.IO)

// Flow<User?> -- re-emits whenever this specific row changes
val userFlow: Flow<User?> =
    database.userQueries.selectById("user-1")
        .asFlow()
        .mapToOneOrNull(Dispatchers.IO)

// Flow<User> -- throws if row disappears
val userFlow: Flow<User> =
    database.userQueries.selectById("user-1")
        .asFlow()
        .mapToOne(Dispatchers.IO)
```

### Execute Mutations

```kotlin
// Fire-and-forget insert
database.userQueries.insert(
    id = "user-1",
    email = "alice@example.com",
    display_name = "Alice",
    avatar_url = null,
    is_verified = true,
    created_at = Clock.System.now(),
    updated_at = Clock.System.now()
)

// Update
database.userQueries.updateDisplayName(
    name = "Alice Smith",
    updatedAt = Clock.System.now(),
    id = "user-1"
)

// Delete
database.userQueries.deleteById("user-1")
```

## Schema Migrations

Migration files use the `.sqm` extension and are placed alongside `.sq` files. Each file is named with its version number.

### Migration File Naming

```
src/commonMain/sqldelight/com/example/db/
    1.sqm    -- migration from version 0 -> 1
    2.sqm    -- migration from version 1 -> 2
    3.sqm    -- migration from version 2 -> 3
```

### Migration File Content

```sql
-- 2.sqm: Add profile_bio column to user table
import kotlin.String;

ALTER TABLE user
ADD COLUMN profile_bio TEXT;
```

```sql
-- 3.sqm: Add posts table
CREATE TABLE post (
    id TEXT NOT NULL PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES user(id),
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE INDEX post_user_id_idx ON post(user_id);
```

### Programmatic Migration (afterVersion callbacks)

```kotlin
AppDatabase.Schema.migrate(
    driver = driver,
    oldVersion = 1,
    newVersion = AppDatabase.Schema.version,
    AfterVersion(2) { driver ->
        // Run data migration after schema version 2 is applied
        driver.execute(null, "UPDATE user SET profile_bio = '' WHERE profile_bio IS NULL", 0)
    }
)
```

## Column Adapters

### EnumColumnAdapter (built-in)

```kotlin
import app.cash.sqldelight.EnumColumnAdapter

val database = AppDatabase(
    driver = driver,
    userAdapter = User.Adapter(
        roleAdapter = EnumColumnAdapter<UserRole>()
    )
)
```

### Custom ColumnAdapter

```kotlin
import app.cash.sqldelight.ColumnAdapter

// Adapter for kotlinx.datetime.Instant stored as TEXT (ISO-8601)
val instantAdapter = object : ColumnAdapter<Instant, String> {
    override fun decode(databaseValue: String): Instant =
        Instant.parse(databaseValue)

    override fun encode(value: Instant): String =
        value.toString()
}

// Adapter for a JSON blob stored as TEXT
val jsonListAdapter = object : ColumnAdapter<List<String>, String> {
    override fun decode(databaseValue: String): List<String> =
        if (databaseValue.isEmpty()) emptyList()
        else databaseValue.split(",")

    override fun encode(value: List<String>): String =
        value.joinToString(",")
}
```

## Transaction API

### Basic Transaction

```kotlin
// All operations succeed or all are rolled back
database.userQueries.transaction {
    database.userQueries.insert(/* user 1 */)
    database.userQueries.insert(/* user 2 */)
    database.userQueries.insert(/* user 3 */)
}
```

### Transaction with Result

```kotlin
val insertedCount: Int = database.userQueries.transactionWithResult {
    database.userQueries.insert(/* user 1 */)
    database.userQueries.insert(/* user 2 */)
    2
}
```

### Rollback

```kotlin
database.userQueries.transaction {
    database.userQueries.insert(/* user 1 */)
    if (someConditionFails) {
        rollback()  // Aborts entire transaction
    }
    database.userQueries.insert(/* user 2 */)
}
```

### Transaction with Result and Rollback

```kotlin
val result: String = database.userQueries.transactionWithResult {
    database.userQueries.insert(/* ... */)
    if (duplicate) {
        rollback("duplicate")  // Returns "duplicate" as the result
    }
    "success"
}
```

## Driver Factory Per Platform

### expect declaration (commonMain)

```kotlin
// src/commonMain/kotlin/com/example/db/DriverFactory.kt
expect class DriverFactory {
    fun createDriver(): SqlDriver
}
```

### Android (androidMain)

```kotlin
// src/androidMain/kotlin/com/example/db/DriverFactory.android.kt
import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

actual class DriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(
            schema = AppDatabase.Schema,
            context = context,
            name = "app.db"
        )
    }
}
```

### iOS / Native (nativeMain)

```kotlin
// src/nativeMain/kotlin/com/example/db/DriverFactory.native.kt
import app.cash.sqldelight.driver.native.NativeSqliteDriver

actual class DriverFactory {
    actual fun createDriver(): SqlDriver {
        return NativeSqliteDriver(
            schema = AppDatabase.Schema,
            name = "app.db"
        )
    }
}
```

### JVM / Desktop (jvmMain)

```kotlin
// src/jvmMain/kotlin/com/example/db/DriverFactory.jvm.kt
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.util.Properties

actual class DriverFactory {
    actual fun createDriver(): SqlDriver {
        return JdbcSqliteDriver(
            url = "jdbc:sqlite:app.db",
            properties = Properties(),
            schema = AppDatabase.Schema
        )
    }
}
```

## Testing with In-Memory Driver

```kotlin
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver

fun createInMemoryDatabase(): AppDatabase {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    AppDatabase.Schema.create(driver)
    return AppDatabase(
        driver = driver,
        userAdapter = User.Adapter(
            created_atAdapter = instantAdapter,
            updated_atAdapter = instantAdapter
        )
    )
}
```
