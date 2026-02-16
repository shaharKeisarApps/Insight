package com.keisardev.insight.core.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.keisardev.insight.core.common.di.AppScope
import com.keisardev.insight.core.database.ExpenseDatabase
import com.keisardev.insight.core.database.SelectAll
import com.keisardev.insight.core.database.SelectById
import com.keisardev.insight.core.database.SelectByDateRange
import com.keisardev.insight.core.database.SelectTotalByCategory
import com.keisardev.insight.core.model.Category
import com.keisardev.insight.core.model.Expense
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

@ContributesBinding(AppScope::class)
@Inject
class ExpenseRepositoryImpl(
    private val database: ExpenseDatabase,
) : ExpenseRepository {

    override fun observeAllExpenses(): Flow<List<Expense>> {
        return database.expenseQueries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { expenses -> expenses.map { it.toDomain() } }
    }

    override fun observeExpenseById(id: Long): Flow<Expense?> {
        return database.expenseQueries.selectById(id)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { it?.toDomain() }
    }

    override fun observeExpensesByDateRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): Flow<List<Expense>> {
        val startMillis = startDate.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        // Add one day to end date to include the entire end day (SQL uses date < endMillis)
        val endMillis = endDate.plus(1, DateTimeUnit.DAY)
            .atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        return database.expenseQueries.selectByDateRange(startMillis, endMillis)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { expenses -> expenses.map { it.toDomain() } }
    }

    override fun observeMonthlyTotal(startDate: LocalDate, endDate: LocalDate): Flow<Double> {
        val startMillis = startDate.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        // Add one day to end date to include the entire end day
        val endMillis = endDate.plus(1, DateTimeUnit.DAY)
            .atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        return database.expenseQueries.selectMonthlyTotal(startMillis, endMillis)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { it ?: 0.0 }
    }

    override fun observeTotalByCategory(
        startDate: LocalDate,
        endDate: LocalDate,
    ): Flow<Map<Category, Double>> {
        val startMillis = startDate.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        // Add one day to end date to include the entire end day
        val endMillis = endDate.plus(1, DateTimeUnit.DAY)
            .atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        return database.expenseQueries.selectTotalByCategory(startMillis, endMillis)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { results ->
                results.associate { row ->
                    row.toCategory() to (row.total ?: 0.0)
                }
            }
    }

    override suspend fun insertExpense(expense: Expense): Long = withContext(Dispatchers.IO) {
        val dateMillis = expense.date.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        val createdAtMillis = expense.createdAt.toEpochMilliseconds()
        database.expenseQueries.insert(
            amount = expense.amount,
            categoryId = expense.category.id,
            description = expense.description,
            date = dateMillis,
            createdAt = createdAtMillis,
        )
        database.expenseQueries.lastInsertRowId().executeAsOne()
    }

    override suspend fun updateExpense(expense: Expense): Unit = withContext(Dispatchers.IO) {
        val dateMillis = expense.date.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        database.expenseQueries.update(
            amount = expense.amount,
            categoryId = expense.category.id,
            description = expense.description,
            date = dateMillis,
            id = expense.id,
        )
    }

    override suspend fun deleteExpense(id: Long): Unit = withContext(Dispatchers.IO) {
        database.expenseQueries.deleteById(id)
    }

    override suspend fun deleteAllExpenses(): Unit = withContext(Dispatchers.IO) {
        database.expenseQueries.deleteAll()
    }

    private fun SelectAll.toDomain(): Expense = Expense(
        id = id,
        amount = amount,
        category = Category(
            id = categoryId,
            name = categoryName,
            icon = categoryIcon,
            colorHex = categoryColorHex,
        ),
        description = description,
        date = Instant.fromEpochMilliseconds(date)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date,
        createdAt = Instant.fromEpochMilliseconds(createdAt),
    )

    private fun SelectById.toDomain(): Expense = Expense(
        id = id,
        amount = amount,
        category = Category(
            id = categoryId,
            name = categoryName,
            icon = categoryIcon,
            colorHex = categoryColorHex,
        ),
        description = description,
        date = Instant.fromEpochMilliseconds(date)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date,
        createdAt = Instant.fromEpochMilliseconds(createdAt),
    )

    private fun SelectByDateRange.toDomain(): Expense = Expense(
        id = id,
        amount = amount,
        category = Category(
            id = categoryId,
            name = categoryName,
            icon = categoryIcon,
            colorHex = categoryColorHex,
        ),
        description = description,
        date = Instant.fromEpochMilliseconds(date)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date,
        createdAt = Instant.fromEpochMilliseconds(createdAt),
    )

    private fun SelectTotalByCategory.toCategory(): Category = Category(
        id = id,
        name = name,
        icon = icon,
        colorHex = colorHex,
    )
}
