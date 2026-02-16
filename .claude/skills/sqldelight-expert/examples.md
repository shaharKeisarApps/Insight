# SQLDelight Production Examples

> SQLDelight **2.2.1** | Metro DI | Store5 | kotlinx.serialization **1.10.0**

## 1. Complete .sq File with Schema, Queries, and Named Parameters

```sql
-- src/commonMain/sqldelight/com/example/db/Product.sq

import kotlin.Boolean;
import kotlin.Int;
import kotlinx.datetime.Instant;
import com.example.model.ProductCategory;
import com.example.model.PriceBreakdown;

CREATE TABLE product (
    id TEXT NOT NULL PRIMARY KEY,
    sku TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    category TEXT AS ProductCategory NOT NULL,
    price_cents INTEGER NOT NULL,
    price_breakdown TEXT AS PriceBreakdown NOT NULL,
    stock_count INTEGER AS Int NOT NULL DEFAULT 0,
    is_active INTEGER AS Boolean NOT NULL DEFAULT 1,
    image_url TEXT,
    created_at TEXT AS Instant NOT NULL,
    updated_at TEXT AS Instant NOT NULL
);

CREATE INDEX product_category_idx ON product(category);
CREATE INDEX product_sku_idx ON product(sku);
CREATE INDEX product_active_idx ON product(is_active, category);

-- SELECT queries

selectAll:
SELECT *
FROM product
WHERE is_active = 1
ORDER BY name ASC;

selectById:
SELECT *
FROM product
WHERE id = :id;

selectBySku:
SELECT *
FROM product
WHERE sku = :sku;

selectByCategory:
SELECT *
FROM product
WHERE category = :category AND is_active = 1
ORDER BY name ASC
LIMIT :limit OFFSET :offset;

searchByName:
SELECT *
FROM product
WHERE name LIKE '%' || :query || '%' AND is_active = 1
ORDER BY name ASC;

selectLowStock:
SELECT *
FROM product
WHERE stock_count < :threshold AND is_active = 1
ORDER BY stock_count ASC;

countByCategory:
SELECT category, COUNT(*) AS count
FROM product
WHERE is_active = 1
GROUP BY category;

-- INSERT queries

insert:
INSERT INTO product(id, sku, name, description, category, price_cents, price_breakdown, stock_count, is_active, image_url, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

upsert:
INSERT OR REPLACE INTO product(id, sku, name, description, category, price_cents, price_breakdown, stock_count, is_active, image_url, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

-- UPDATE queries

updateDetails:
UPDATE product
SET name = :name, description = :description, category = :category, updated_at = :updatedAt
WHERE id = :id;

updatePrice:
UPDATE product
SET price_cents = :priceCents, price_breakdown = :priceBreakdown, updated_at = :updatedAt
WHERE id = :id;

adjustStock:
UPDATE product
SET stock_count = stock_count + :delta, updated_at = :updatedAt
WHERE id = :id;

deactivate:
UPDATE product
SET is_active = 0, updated_at = :updatedAt
WHERE id = :id;

-- DELETE queries

deleteById:
DELETE FROM product
WHERE id = :id;

deleteAll:
DELETE FROM product;
```

## 2. expect/actual SqlDriver Factory with Metro DI @Provides Per Platform

```kotlin
// src/commonMain/kotlin/com/example/db/DriverFactory.kt
package com.example.db

import app.cash.sqldelight.db.SqlDriver

expect class DriverFactory {
    fun createDriver(): SqlDriver
}
```

```kotlin
// src/androidMain/kotlin/com/example/db/DriverFactory.android.kt
package com.example.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

actual class DriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver =
        AndroidSqliteDriver(
            schema = AppDatabase.Schema,
            context = context,
            name = "app.db",
        )
}
```

```kotlin
// src/iosMain/kotlin/com/example/db/DriverFactory.ios.kt
package com.example.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

actual class DriverFactory {
    actual fun createDriver(): SqlDriver =
        NativeSqliteDriver(
            schema = AppDatabase.Schema,
            name = "app.db",
        )
}
```

```kotlin
// src/jvmMain/kotlin/com/example/db/DriverFactory.jvm.kt
package com.example.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.util.Properties

actual class DriverFactory {
    actual fun createDriver(): SqlDriver =
        JdbcSqliteDriver(
            url = "jdbc:sqlite:app.db",
            properties = Properties(),
            schema = AppDatabase.Schema,
        )
}
```

### Metro DI Bindings (commonMain + platform actuals)

```kotlin
// src/commonMain/kotlin/com/example/di/DatabaseGraph.kt
package com.example.di

import app.cash.sqldelight.db.SqlDriver
import com.example.db.AppDatabase
import com.example.db.DriverFactory
import com.example.db.Product
import com.example.db.ProductQueries
import com.example.db.adapters.InstantColumnAdapter
import com.example.db.adapters.ProductCategoryAdapter
import com.example.db.adapters.JsonColumnAdapter
import com.example.model.PriceBreakdown
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.DependencyGraph
import kotlinx.serialization.json.Json

@DependencyGraph(AppScope::class)
interface DatabaseGraph {

    val database: AppDatabase
    val productQueries: ProductQueries

    companion object {

        @Provides
        @SingleIn(AppScope::class)
        fun provideDatabase(driverFactory: DriverFactory, json: Json): AppDatabase {
            val driver = driverFactory.createDriver()
            return AppDatabase(
                driver = driver,
                productAdapter = Product.Adapter(
                    categoryAdapter = ProductCategoryAdapter,
                    price_breakdownAdapter = JsonColumnAdapter(json, PriceBreakdown.serializer()),
                    created_atAdapter = InstantColumnAdapter,
                    updated_atAdapter = InstantColumnAdapter,
                ),
            )
        }

        @Provides
        fun provideProductQueries(database: AppDatabase): ProductQueries =
            database.productQueries
    }
}
```

```kotlin
// src/androidMain/kotlin/com/example/di/PlatformDatabaseGraph.kt
package com.example.di

import android.content.Context
import com.example.db.DriverFactory
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.DependencyGraph

@DependencyGraph(AppScope::class)
interface PlatformDatabaseGraph {
    companion object {
        @Provides
        @SingleIn(AppScope::class)
        fun provideDriverFactory(context: Context): DriverFactory = DriverFactory(context)
    }
}
```

```kotlin
// src/iosMain/kotlin/com/example/di/PlatformDatabaseGraph.kt
package com.example.di

import com.example.db.DriverFactory
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.DependencyGraph

@DependencyGraph(AppScope::class)
interface PlatformDatabaseGraph {
    companion object {
        @Provides
        @SingleIn(AppScope::class)
        fun provideDriverFactory(): DriverFactory = DriverFactory()
    }
}
```

## 3. Flow-Based Reactive Query with mapToList

```kotlin
// src/commonMain/kotlin/com/example/data/ProductFeedRepository.kt
package com.example.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.example.db.AppDatabase
import com.example.db.Product
import com.example.model.ProductCategory
import com.example.model.ProductDomain
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Inject
@SingleIn(AppScope::class)
class ProductFeedRepository(
    private val database: AppDatabase,
) {
    /** Re-emits whenever product table changes. */
    fun observeActiveProducts(): Flow<List<ProductDomain>> =
        database.productQueries
            .selectAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toDomain() } }

    fun observeByCategory(
        category: ProductCategory,
        limit: Long = 50,
        offset: Long = 0,
    ): Flow<List<ProductDomain>> =
        database.productQueries
            .selectByCategory(category = category, limit = limit, offset = offset)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toDomain() } }

    fun observeProduct(id: String): Flow<ProductDomain?> =
        database.productQueries
            .selectById(id)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { it?.toDomain() }
}

private fun Product.toDomain(): ProductDomain = ProductDomain(
    id = id,
    sku = sku,
    name = name,
    description = description,
    category = category,
    priceCents = price_cents,
    priceBreakdown = price_breakdown,
    stockCount = stock_count,
    isActive = is_active,
    imageUrl = image_url,
    createdAt = created_at,
    updatedAt = updated_at,
)
```

## 4. Transaction Batch Insert

```kotlin
// src/commonMain/kotlin/com/example/data/ProductSyncService.kt
package com.example.data

import com.example.db.AppDatabase
import com.example.network.ProductDto
import dev.zacsweers.metro.Inject
import kotlinx.datetime.Clock

@Inject
class ProductSyncService(
    private val database: AppDatabase,
) {
    /** Replace-all sync: clear stale rows and batch insert inside a single transaction. */
    fun replaceAll(products: List<ProductDto>) {
        val now = Clock.System.now()
        database.productQueries.transaction {
            database.productQueries.deleteAll()
            products.forEach { dto ->
                database.productQueries.insert(
                    id = dto.id,
                    sku = dto.sku,
                    name = dto.name,
                    description = dto.description,
                    category = dto.category.toDomain(),
                    price_cents = dto.priceCents,
                    price_breakdown = dto.priceBreakdown.toDomain(),
                    stock_count = dto.stockCount,
                    is_active = dto.isActive,
                    image_url = dto.imageUrl,
                    created_at = dto.createdAt,
                    updated_at = now,
                )
            }
        }
    }

    /** Incremental sync: upsert only newer items, return count of changes. */
    fun incrementalSync(products: List<ProductDto>): SyncResult {
        val now = Clock.System.now()
        return database.productQueries.transactionWithResult {
            var upserted = 0
            var skipped = 0

            products.forEach { dto ->
                val existing = database.productQueries
                    .selectById(dto.id)
                    .executeAsOneOrNull()

                if (existing == null || dto.updatedAt > existing.updated_at) {
                    database.productQueries.upsert(
                        id = dto.id,
                        sku = dto.sku,
                        name = dto.name,
                        description = dto.description,
                        category = dto.category.toDomain(),
                        price_cents = dto.priceCents,
                        price_breakdown = dto.priceBreakdown.toDomain(),
                        stock_count = dto.stockCount,
                        is_active = dto.isActive,
                        image_url = dto.imageUrl,
                        created_at = dto.createdAt,
                        updated_at = now,
                    )
                    upserted++
                } else {
                    skipped++
                }
            }

            SyncResult(upserted = upserted, skipped = skipped)
        }
    }
}

data class SyncResult(val upserted: Int, val skipped: Int)
```

## 5. Column Adapters for Enum and kotlinx.serialization JSON Types

```kotlin
// src/commonMain/kotlin/com/example/db/adapters/ProductCategoryAdapter.kt
package com.example.db.adapters

import app.cash.sqldelight.ColumnAdapter
import com.example.model.ProductCategory

/** Maps enum to its name for TEXT storage. Use EnumColumnAdapter for trivial cases. */
object ProductCategoryAdapter : ColumnAdapter<ProductCategory, String> {
    override fun decode(databaseValue: String): ProductCategory =
        ProductCategory.entries.firstOrNull { it.name == databaseValue }
            ?: ProductCategory.OTHER

    override fun encode(value: ProductCategory): String = value.name
}

enum class ProductCategory {
    ELECTRONICS, CLOTHING, GROCERY, HOME, OTHER
}
```

```kotlin
// src/commonMain/kotlin/com/example/db/adapters/JsonColumnAdapter.kt
package com.example.db.adapters

import app.cash.sqldelight.ColumnAdapter
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Stores any @Serializable type as a JSON TEXT column.
 * Shares the app-wide [Json] instance so ignoreUnknownKeys
 * and serializersModule stay consistent.
 */
class JsonColumnAdapter<T>(
    private val json: Json,
    private val serializer: KSerializer<T>,
) : ColumnAdapter<T, String> {
    override fun decode(databaseValue: String): T =
        json.decodeFromString(serializer, databaseValue)

    override fun encode(value: T): String =
        json.encodeToString(serializer, value)
}

// Domain model serialized into a TEXT column:
// @Serializable
// data class PriceBreakdown(
//     @SerialName("base_cents") val baseCents: Long,
//     @SerialName("tax_cents") val taxCents: Long,
//     @SerialName("discount_cents") val discountCents: Long = 0,
// )
//
// Adapter instance:
//   JsonColumnAdapter(json, PriceBreakdown.serializer())
```

```kotlin
// src/commonMain/kotlin/com/example/db/adapters/InstantColumnAdapter.kt
package com.example.db.adapters

import app.cash.sqldelight.ColumnAdapter
import kotlinx.datetime.Instant

object InstantColumnAdapter : ColumnAdapter<Instant, String> {
    override fun decode(databaseValue: String): Instant = Instant.parse(databaseValue)
    override fun encode(value: Instant): String = value.toString()
}
```

## 6. Store5 SourceOfTruth.of() Backed by SQLDelight

```kotlin
// src/commonMain/kotlin/com/example/data/ProductRepository.kt
package com.example.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.example.db.AppDatabase
import com.example.model.ProductDomain
import com.example.network.ProductApi
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import org.mobilenativefoundation.store.store5.Fetcher
import org.mobilenativefoundation.store.store5.SourceOfTruth
import org.mobilenativefoundation.store.store5.Store
import org.mobilenativefoundation.store.store5.StoreBuilder
import org.mobilenativefoundation.store.store5.StoreReadRequest
import org.mobilenativefoundation.store.store5.StoreReadResponse

@JvmInline
value class ProductId(val value: String)

@Inject
@SingleIn(AppScope::class)
class ProductRepository(
    private val database: AppDatabase,
    private val api: ProductApi,
) {
    private val store: Store<ProductId, ProductDomain> = StoreBuilder.from(
        fetcher = Fetcher.of("fetchProduct") { key: ProductId ->
            api.getProduct(key.value).toDomain()
        },
        sourceOfTruth = SourceOfTruth.of(
            reader = { key: ProductId ->
                database.productQueries
                    .selectById(key.value)
                    .asFlow()
                    .mapToOneOrNull(Dispatchers.IO)
                    .map { it?.toDomain() }
            },
            writer = { _: ProductId, domain: ProductDomain ->
                val now = Clock.System.now()
                database.productQueries.upsert(
                    id = domain.id,
                    sku = domain.sku,
                    name = domain.name,
                    description = domain.description,
                    category = domain.category,
                    price_cents = domain.priceCents,
                    price_breakdown = domain.priceBreakdown,
                    stock_count = domain.stockCount,
                    is_active = domain.isActive,
                    image_url = domain.imageUrl,
                    created_at = domain.createdAt,
                    updated_at = now,
                )
            },
            delete = { key: ProductId ->
                database.productQueries.deleteById(key.value)
            },
            deleteAll = {
                database.productQueries.deleteAll()
            },
        ),
    ).build()

    fun observeProduct(
        id: String,
        refresh: Boolean = false,
    ): Flow<StoreReadResponse<ProductDomain>> =
        store.stream(StoreReadRequest.cached(ProductId(id), refresh = refresh))

    fun freshProduct(id: String): Flow<StoreReadResponse<ProductDomain>> =
        store.stream(StoreReadRequest.fresh(ProductId(id)))
}
```

## 7. Migration .sqm File Example

```sql
-- src/commonMain/sqldelight/com/example/db/1.sqm
-- Migration v0 -> v1: handled by CREATE TABLE statements in .sq files.
-- This file is intentionally empty for the initial version.
```

```sql
-- src/commonMain/sqldelight/com/example/db/2.sqm
-- Migration v1 -> v2: Add weight and dimensions to product.

ALTER TABLE product
ADD COLUMN weight_grams INTEGER NOT NULL DEFAULT 0;

ALTER TABLE product
ADD COLUMN dimensions_json TEXT NOT NULL DEFAULT '{}';
```

```sql
-- src/commonMain/sqldelight/com/example/db/3.sqm
-- Migration v2 -> v3: Add product_review table for ratings.

CREATE TABLE product_review (
    id TEXT NOT NULL PRIMARY KEY,
    product_id TEXT NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    user_id TEXT NOT NULL,
    rating INTEGER NOT NULL CHECK(rating BETWEEN 1 AND 5),
    comment TEXT NOT NULL DEFAULT '',
    created_at TEXT NOT NULL
);

CREATE INDEX review_product_idx ON product_review(product_id);
CREATE INDEX review_user_idx ON product_review(user_id);
```

### Programmatic Data Migration with AfterVersion

```kotlin
// src/commonMain/kotlin/com/example/db/Migrations.kt
package com.example.db

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.SqlDriver

fun migrateDatabase(driver: SqlDriver) {
    AppDatabase.Schema.migrate(
        driver = driver,
        oldVersion = 0,
        newVersion = AppDatabase.Schema.version,
        AfterVersion(2) { drv ->
            // Backfill weight estimates for existing products
            drv.execute(
                identifier = null,
                sql = "UPDATE product SET weight_grams = 500 WHERE weight_grams = 0",
                parameters = 0,
            )
        },
    )
}
```
