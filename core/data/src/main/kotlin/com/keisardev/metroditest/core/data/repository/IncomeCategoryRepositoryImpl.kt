package com.keisardev.metroditest.core.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.keisardev.metroditest.core.common.di.AppScope
import com.keisardev.metroditest.core.database.ExpenseDatabase
import com.keisardev.metroditest.core.model.DefaultIncomeCategories
import com.keisardev.metroditest.core.model.IncomeCategory
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.keisardev.metroditest.core.database.IncomeCategory as DbIncomeCategory

@ContributesBinding(AppScope::class)
@Inject
class IncomeCategoryRepositoryImpl(
    private val database: ExpenseDatabase,
) : IncomeCategoryRepository {

    override fun observeAllCategories(): Flow<List<IncomeCategory>> {
        return database.incomeCategoryQueries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { categories -> categories.map { it.toDomain() } }
    }

    override suspend fun getCategoryById(id: Long): IncomeCategory? = withContext(Dispatchers.IO) {
        database.incomeCategoryQueries.selectById(id).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun seedDefaultCategories() = withContext(Dispatchers.IO) {
        val count = database.incomeCategoryQueries.count().executeAsOne()
        if (count == 0L) {
            DefaultIncomeCategories.categories.forEach { category ->
                database.incomeCategoryQueries.insert(
                    name = category.name,
                    icon = category.icon,
                    colorHex = category.colorHex,
                )
            }
        }
    }

    override suspend fun getCategoryCount(): Long = withContext(Dispatchers.IO) {
        database.incomeCategoryQueries.count().executeAsOne()
    }

    private fun DbIncomeCategory.toDomain(): IncomeCategory = IncomeCategory(
        id = id,
        name = name,
        icon = icon,
        colorHex = colorHex,
    )
}
