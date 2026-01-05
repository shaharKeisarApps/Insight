---
name: sqldelight-expert
description: Elite SQLDelight expertise for KMP database operations. Use when defining schemas, writing type-safe queries, managing migrations, handling relationships, or optimizing database performance. Triggers on database setup, schema design, query optimization, or multiplatform persistence questions.
---

# SQLDelight Expert Skill

## Core Concepts

SQLDelight generates type-safe Kotlin APIs from SQL statements:
- Write SQL → Get Kotlin interfaces
- Compile-time query verification
- Multiplatform support (Android, iOS, JVM, Native)

## Installation

```kotlin
// build.gradle.kts
plugins {
    id("app.cash.sqldelight") version "2.2.1"
}

sqldelight {
    databases {
        create("AppDatabase") {
            packageName.set("com.app.db")
            // For migrations
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            verifyMigrations.set(true)
        }
    }
}

// Dependencies
commonMain.dependencies {
    implementation("app.cash.sqldelight:runtime:2.2.1")
    implementation("app.cash.sqldelight:coroutines-extensions:2.2.1")
}

androidMain.dependencies {
    implementation("app.cash.sqldelight:android-driver:2.2.1")
}

iosMain.dependencies {
    implementation("app.cash.sqldelight:native-driver:2.2.1")
}

jvmMain.dependencies {
    implementation("app.cash.sqldelight:sqlite-driver:2.2.1")
}
```

## Android-Only Setup (Non-KMP)

For Android-only projects, SQLDelight requires the `kotlin-android` plugin:

```kotlin
// build.gradle.kts
plugins {
    id("com.android.application") // or library
    id("org.jetbrains.kotlin.android")  // REQUIRED for SQLDelight
    id("app.cash.sqldelight")
}

sqldelight {
    databases {
        create("AppDatabase") {
            packageName.set("com.app.db")
            srcDirs.setFrom("src/main/sqldelight")  // Explicit for Android-only
        }
    }
}

dependencies {
    implementation("app.cash.sqldelight:android-driver:2.2.1")
    implementation("app.cash.sqldelight:coroutines-extensions:2.2.1")
}
```

> **Important**: The `kotlin-android` plugin is mandatory. Without it, SQLDelight cannot find the source sets and will fail with "KotlinSourceSet with name 'main' not found".

## Aggregation Query Results

Aggregation queries with aliases return the primitive type directly, NOT a wrapper object:

```sql
-- Schema
selectMonthlyTotal:
SELECT COALESCE(SUM(amount), 0.0) AS total
FROM expense
WHERE date >= ? AND date < ?;

selectTotalByCategory:
SELECT Category.id, Category.name, SUM(Expense.amount) AS total
FROM Expense
INNER JOIN Category ON Expense.categoryId = Category.id
WHERE Expense.date >= ? AND Expense.date < ?
GROUP BY Category.id;
```

```kotlin
// Usage - selectMonthlyTotal returns Double directly, NOT a wrapper object
override fun observeMonthlyTotal(start: LocalDate, end: LocalDate): Flow<Double> {
    return database.expenseQueries.selectMonthlyTotal(startMillis, endMillis)
        .asFlow()
        .mapToOneOrNull(Dispatchers.IO)
        .map { it ?: 0.0 }  // 'it' IS the Double, not it.total
}

// For complex aggregations with multiple columns, generated class has named properties
override fun observeTotalByCategory(start: LocalDate, end: LocalDate): Flow<Map<Category, Double>> {
    return database.expenseQueries.selectTotalByCategory(startMillis, endMillis)
        .asFlow()
        .mapToList(Dispatchers.IO)
        .map { results ->
            results.associate { row ->
                row.toCategory() to (row.total ?: 0.0)  // row.total exists
            }
        }
}
```

## Void Operations (Update/Delete)

Update and delete operations execute immediately. For interface compatibility with Unit return types:

```kotlin
// Interface expects Unit
interface ExpenseRepository {
    suspend fun deleteExpense(id: Long)
    suspend fun updateExpense(expense: Expense)
}

// Implementation - explicit Unit return type for interface compliance
override suspend fun deleteExpense(id: Long): Unit = withContext(Dispatchers.IO) {
    database.expenseQueries.deleteById(id)
}

override suspend fun updateExpense(expense: Expense): Unit = withContext(Dispatchers.IO) {
    database.expenseQueries.update(
        amount = expense.amount,
        categoryId = expense.category.id,
        description = expense.description,
        date = expense.date.toEpochMillis(),
        id = expense.id,
    )
}
```

## Schema Definition

### Basic Table

```sql
-- src/commonMain/sqldelight/com/app/db/User.sq

CREATE TABLE user (
    id TEXT NOT NULL PRIMARY KEY,
    email TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    avatar_url TEXT,
    created_at INTEGER NOT NULL,  -- Epoch millis
    updated_at INTEGER NOT NULL
);

-- Indexes for common queries
CREATE INDEX user_email_idx ON user(email);
CREATE INDEX user_created_at_idx ON user(created_at DESC);
```

### Queries in .sq Files

```sql
-- User.sq continued

-- Select all
getAll:
SELECT *
FROM user
ORDER BY name ASC;

-- Select by ID
getById:
SELECT *
FROM user
WHERE id = ?;

-- Select by email
getByEmail:
SELECT *
FROM user
WHERE email = ?;

-- Search by name (case-insensitive)
search:
SELECT *
FROM user
WHERE name LIKE '%' || ? || '%' COLLATE NOCASE
ORDER BY name ASC;

-- Insert or replace
upsert:
INSERT OR REPLACE INTO user(id, email, name, avatar_url, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, ?);

-- Update specific fields
updateName:
UPDATE user
SET name = ?, updated_at = ?
WHERE id = ?;

-- Delete by ID
deleteById:
DELETE FROM user
WHERE id = ?;

-- Delete all
deleteAll:
DELETE FROM user;

-- Count users
count:
SELECT COUNT(*) FROM user;

-- Check if exists
exists:
SELECT EXISTS(SELECT 1 FROM user WHERE id = ?);

-- Pagination
getPage:
SELECT *
FROM user
ORDER BY created_at DESC
LIMIT :limit OFFSET :offset;
```

### Relationships (Foreign Keys)

```sql
-- Post.sq

CREATE TABLE post (
    id TEXT NOT NULL PRIMARY KEY,
    user_id TEXT NOT NULL,
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    published INTEGER NOT NULL DEFAULT 0,  -- Boolean as INTEGER
    created_at INTEGER NOT NULL,
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
);

CREATE INDEX post_user_id_idx ON post(user_id);
CREATE INDEX post_created_at_idx ON post(created_at DESC);

-- Get posts by user
getByUserId:
SELECT *
FROM post
WHERE user_id = ?
ORDER BY created_at DESC;

-- Get post with user (JOIN)
getPostWithUser:
SELECT 
    post.*,
    user.name AS user_name,
    user.avatar_url AS user_avatar
FROM post
JOIN user ON post.user_id = user.id
WHERE post.id = ?;

-- Get all posts with users
getAllWithUsers:
SELECT 
    post.*,
    user.name AS user_name,
    user.avatar_url AS user_avatar
FROM post
JOIN user ON post.user_id = user.id
ORDER BY post.created_at DESC;
```

### Custom Types with Adapters

```sql
-- Tag.sq

-- Import custom type
import kotlin.collections.List;

CREATE TABLE tag (
    id TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    color TEXT NOT NULL  -- Will map to Color
);

-- Post tags (many-to-many)
CREATE TABLE post_tag (
    post_id TEXT NOT NULL,
    tag_id TEXT NOT NULL,
    PRIMARY KEY (post_id, tag_id),
    FOREIGN KEY (post_id) REFERENCES post(id) ON DELETE CASCADE,
    FOREIGN KEY (tag_id) REFERENCES tag(id) ON DELETE CASCADE
);
```

```kotlin
// Type adapter for Color
val colorAdapter = object : ColumnAdapter<Color, String> {
    override fun decode(databaseValue: String): Color = 
        Color(databaseValue.toLong(16))
    
    override fun encode(value: Color): String = 
        value.value.toString(16)
}

// Use in database creation
val database = AppDatabase(
    driver = driver,
    tagAdapter = Tag.Adapter(colorAdapter = colorAdapter),
)
```

## Driver Setup (Multiplatform)

### Expect/Actual Pattern

```kotlin
// commonMain
expect class DriverFactory {
    fun createDriver(): SqlDriver
}

fun createDatabase(driverFactory: DriverFactory): AppDatabase {
    val driver = driverFactory.createDriver()
    return AppDatabase(driver)
}

// androidMain
actual class DriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(
            schema = AppDatabase.Schema,
            context = context,
            name = "app.db",
            callback = object : AndroidSqliteDriver.Callback(AppDatabase.Schema) {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    db.setForeignKeyConstraintsEnabled(true)
                }
            },
        )
    }
}

// iosMain
actual class DriverFactory {
    actual fun createDriver(): SqlDriver {
        return NativeSqliteDriver(
            schema = AppDatabase.Schema,
            name = "app.db",
        )
    }
}

// desktopMain (JVM)
actual class DriverFactory(private val dbPath: String = "app.db") {
    actual fun createDriver(): SqlDriver {
        return JdbcSqliteDriver("jdbc:sqlite:$dbPath").also { driver ->
            AppDatabase.Schema.create(driver)
        }
    }
}
```

### DI Integration (Metro)

```kotlin
@ContributesTo(AppScope::class)
interface DatabaseModule {
    
    @Provides
    @SingleIn(AppScope::class)
    fun provideDriverFactory(application: Application): DriverFactory =
        DriverFactory(application)
    
    @Provides
    @SingleIn(AppScope::class)
    fun provideDatabase(driverFactory: DriverFactory): AppDatabase =
        createDatabase(driverFactory)
    
    @Provides
    fun provideUserQueries(database: AppDatabase): UserQueries =
        database.userQueries
    
    @Provides
    fun providePostQueries(database: AppDatabase): PostQueries =
        database.postQueries
}
```

## Flow Integration

### Extension Functions

```kotlin
// QueryExtensions.kt

/**
 * Observe query as Flow, emitting on every change
 */
fun <T : Any> Query<T>.asFlow(): Flow<Query<T>> = callbackFlow {
    val listener = object : Query.Listener {
        override fun queryResultsChanged() {
            trySend(this@asFlow)
        }
    }
    
    addListener(listener)
    trySend(this@asFlow)  // Emit initial value
    
    awaitClose { removeListener(listener) }
}

/**
 * Map query to list
 */
fun <T : Any> Flow<Query<T>>.mapToList(): Flow<List<T>> =
    map { it.executeAsList() }

/**
 * Map query to single item or null
 */
fun <T : Any> Flow<Query<T>>.mapToOneOrNull(): Flow<T?> =
    map { it.executeAsOneOrNull() }

/**
 * Map query to single item (throws if not found)
 */
fun <T : Any> Flow<Query<T>>.mapToOne(): Flow<T> =
    map { it.executeAsOne() }
```

### Usage in Repository

```kotlin
@ContributesBinding(AppScope::class)
@Inject
class UserRepositoryImpl(
    private val userQueries: UserQueries,
) : UserRepository {
    
    override fun observeAll(): Flow<List<User>> =
        userQueries.getAll()
            .asFlow()
            .mapToList()
            .map { entities -> entities.map { it.toDomain() } }
    
    override fun observeUser(id: String): Flow<User?> =
        userQueries.getById(id)
            .asFlow()
            .mapToOneOrNull()
            .map { it?.toDomain() }
    
    override suspend fun getUser(id: String): User? =
        withContext(Dispatchers.IO) {
            userQueries.getById(id).executeAsOneOrNull()?.toDomain()
        }
    
    override suspend fun upsert(user: User) {
        withContext(Dispatchers.IO) {
            userQueries.upsert(
                id = user.id,
                email = user.email,
                name = user.name,
                avatar_url = user.avatarUrl,
                created_at = user.createdAt.toEpochMilliseconds(),
                updated_at = Clock.System.now().toEpochMilliseconds(),
            )
        }
    }
}
```

## Transactions

### Basic Transaction

```kotlin
suspend fun syncUsers(users: List<User>) {
    withContext(Dispatchers.IO) {
        database.transaction {
            userQueries.deleteAll()
            users.forEach { user ->
                userQueries.upsert(
                    id = user.id,
                    email = user.email,
                    name = user.name,
                    avatar_url = user.avatarUrl,
                    created_at = user.createdAt.toEpochMilliseconds(),
                    updated_at = user.updatedAt.toEpochMilliseconds(),
                )
            }
        }
    }
}
```

### Transaction with Rollback

```kotlin
suspend fun createPostWithTags(
    post: Post,
    tagIds: List<String>,
): Either<DomainError, Post> = withContext(Dispatchers.IO) {
    Either.catch {
        database.transactionWithResult {
            // Insert post
            postQueries.insert(
                id = post.id,
                user_id = post.userId,
                title = post.title,
                content = post.content,
                created_at = post.createdAt.toEpochMilliseconds(),
            )
            
            // Insert tag relations
            tagIds.forEach { tagId ->
                postTagQueries.insert(post.id, tagId)
            }
            
            post
        }
    }.mapLeft { DomainError.Storage.WriteError(it) }
}
```

### Nested Transactions

```kotlin
database.transaction {
    userQueries.deleteAll()
    
    // Nested transaction (savepoint)
    transaction {
        postQueries.deleteAll()
        tagQueries.deleteAll()
    }
}
```

## Migrations

### Creating Migrations

```sql
-- src/commonMain/sqldelight/migrations/1.sqm
-- First migration: Add bio column

ALTER TABLE user ADD COLUMN bio TEXT;

-- src/commonMain/sqldelight/migrations/2.sqm
-- Second migration: Add settings table

CREATE TABLE user_settings (
    user_id TEXT NOT NULL PRIMARY KEY,
    theme TEXT NOT NULL DEFAULT 'system',
    notifications_enabled INTEGER NOT NULL DEFAULT 1,
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
);
```

### Verifying Migrations

```kotlin
// build.gradle.kts
sqldelight {
    databases {
        create("AppDatabase") {
            packageName.set("com.app.db")
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            verifyMigrations.set(true)  // Fails build on invalid migrations
        }
    }
}
```

### Migration Callbacks

```kotlin
// androidMain
actual class DriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(
            schema = AppDatabase.Schema,
            context = context,
            name = "app.db",
            callback = object : AndroidSqliteDriver.Callback(AppDatabase.Schema) {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    db.setForeignKeyConstraintsEnabled(true)
                }
                
                override fun onMigrate(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int,
                ) {
                    super.onMigrate(db, oldVersion, newVersion)
                    // Custom migration logic if needed
                }
            },
        )
    }
}
```

## Advanced Queries

### Window Functions

```sql
-- Get users ranked by post count
getUsersRankedByPosts:
SELECT 
    user.*,
    COUNT(post.id) AS post_count,
    RANK() OVER (ORDER BY COUNT(post.id) DESC) AS rank
FROM user
LEFT JOIN post ON user.id = post.user_id
GROUP BY user.id
ORDER BY rank;
```

### Common Table Expressions (CTE)

```sql
-- Get users with their most recent post
getUsersWithLatestPost:
WITH latest_posts AS (
    SELECT 
        user_id,
        MAX(created_at) AS latest_created_at
    FROM post
    GROUP BY user_id
)
SELECT 
    user.*,
    post.title AS latest_post_title,
    post.created_at AS latest_post_date
FROM user
LEFT JOIN latest_posts ON user.id = latest_posts.user_id
LEFT JOIN post ON latest_posts.user_id = post.user_id 
    AND latest_posts.latest_created_at = post.created_at
ORDER BY latest_post_date DESC NULLS LAST;
```

### Full-Text Search (FTS)

```sql
-- Create FTS table
CREATE VIRTUAL TABLE post_fts USING fts5(
    title,
    content,
    content=post,
    content_rowid=rowid
);

-- Triggers to keep FTS in sync
CREATE TRIGGER post_ai AFTER INSERT ON post BEGIN
    INSERT INTO post_fts(rowid, title, content)
    VALUES (NEW.rowid, NEW.title, NEW.content);
END;

CREATE TRIGGER post_ad AFTER DELETE ON post BEGIN
    INSERT INTO post_fts(post_fts, rowid, title, content)
    VALUES ('delete', OLD.rowid, OLD.title, OLD.content);
END;

CREATE TRIGGER post_au AFTER UPDATE ON post BEGIN
    INSERT INTO post_fts(post_fts, rowid, title, content)
    VALUES ('delete', OLD.rowid, OLD.title, OLD.content);
    INSERT INTO post_fts(rowid, title, content)
    VALUES (NEW.rowid, NEW.title, NEW.content);
END;

-- Search query
searchPosts:
SELECT post.*
FROM post
JOIN post_fts ON post.rowid = post_fts.rowid
WHERE post_fts MATCH ?
ORDER BY rank;
```

## Entity Mapping

### Domain Model Conversion

```kotlin
// Generated SQLDelight entity
// data class User(
//     val id: String,
//     val email: String,
//     val name: String,
//     val avatar_url: String?,
//     val created_at: Long,
//     val updated_at: Long,
// )

// Domain model
data class User(
    val id: String,
    val email: String,
    val name: String,
    val avatarUrl: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

// Extensions
fun com.app.db.User.toDomain(): User = User(
    id = id,
    email = email,
    name = name,
    avatarUrl = avatar_url,
    createdAt = Instant.fromEpochMilliseconds(created_at),
    updatedAt = Instant.fromEpochMilliseconds(updated_at),
)

fun User.toEntity(): com.app.db.User = com.app.db.User(
    id = id,
    email = email,
    name = name,
    avatar_url = avatarUrl,
    created_at = createdAt.toEpochMilliseconds(),
    updated_at = updatedAt.toEpochMilliseconds(),
)
```

## Testing

### In-Memory Database

```kotlin
class UserRepositoryTest {
    
    private lateinit var database: AppDatabase
    private lateinit var repository: UserRepositoryImpl
    
    @BeforeTest
    fun setup() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        database = AppDatabase(driver)
        repository = UserRepositoryImpl(database.userQueries)
    }
    
    @AfterTest
    fun teardown() {
        database.close()
    }
    
    @Test
    fun `insert and retrieve user`() = runTest {
        val user = User(
            id = "123",
            email = "test@example.com",
            name = "Test User",
            avatarUrl = null,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
        )
        
        repository.upsert(user)
        val retrieved = repository.getUser("123")
        
        assertThat(retrieved).isEqualTo(user)
    }
    
    @Test
    fun `observe users emits updates`() = runTest {
        repository.observeAll().test {
            assertThat(awaitItem()).isEmpty()
            
            repository.upsert(testUser)
            assertThat(awaitItem()).hasSize(1)
            
            repository.upsert(testUser2)
            assertThat(awaitItem()).hasSize(2)
            
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

## Anti-Patterns

❌ **Don't use string concatenation for queries**
```sql
-- WRONG - SQL injection risk
getByName:
SELECT * FROM user WHERE name = '' || ? || '';

-- RIGHT - Use proper parameters
getByName:
SELECT * FROM user WHERE name = ?;

-- For LIKE queries
searchByName:
SELECT * FROM user WHERE name LIKE '%' || ? || '%';
```

❌ **Don't execute queries on main thread**
```kotlin
// WRONG
fun getUser(id: String) = userQueries.getById(id).executeAsOne()

// RIGHT
suspend fun getUser(id: String) = withContext(Dispatchers.IO) {
    userQueries.getById(id).executeAsOne()
}
```

❌ **Don't ignore foreign key constraints**
```kotlin
// WRONG - Might fail silently on some platforms
driver.execute(null, "DELETE FROM user WHERE id = ?", 1) { bindString(1, id) }

// RIGHT - Ensure constraints are enabled
db.setForeignKeyConstraintsEnabled(true)
```

## References

- SQLDelight: https://cashapp.github.io/sqldelight/
- SQLDelight KMP: https://cashapp.github.io/sqldelight/2.0.2/multiplatform_sqlite/
- SQLite Documentation: https://www.sqlite.org/lang.html
