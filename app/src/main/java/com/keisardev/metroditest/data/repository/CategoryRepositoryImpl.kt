package com.keisardev.metroditest.data.repository

import androidx.compose.ui.graphics.Color
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.keisardev.metroditest.data.db.ExpenseDatabase
import com.keisardev.metroditest.data.model.Category
import com.keisardev.metroditest.data.model.DefaultCategories
import com.keisardev.metroditest.di.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.keisardev.metroditest.data.db.Category as DbCategory

@ContributesBinding(AppScope::class)
@Inject
class CategoryRepositoryImpl(
    private val database: ExpenseDatabase,
) : CategoryRepository {

    override fun observeAllCategories(): Flow<List<Category>> {
        return database.categoryQueries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { categories -> categories.map { it.toDomain() } }
    }

    override suspend fun getCategoryById(id: Long): Category? = withContext(Dispatchers.IO) {
        database.categoryQueries.selectById(id).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun seedDefaultCategories() = withContext(Dispatchers.IO) {
        val count = database.categoryQueries.count().executeAsOne()
        if (count == 0L) {
            DefaultCategories.categories.forEach { category ->
                database.categoryQueries.insert(
                    name = category.name,
                    icon = category.icon,
                    colorHex = category.color.value.toLong(),
                )
            }
        }
    }

    override suspend fun getCategoryCount(): Long = withContext(Dispatchers.IO) {
        database.categoryQueries.count().executeAsOne()
    }

    private fun DbCategory.toDomain(): Category = Category(
        id = id,
        name = name,
        icon = icon,
        color = Color(colorHex.toULong()),
    )
}
