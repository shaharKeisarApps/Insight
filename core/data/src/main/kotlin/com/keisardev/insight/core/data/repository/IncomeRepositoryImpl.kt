package com.keisardev.insight.core.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.keisardev.insight.core.common.di.AppScope
import com.keisardev.insight.core.database.ExpenseDatabase
import com.keisardev.insight.core.database.IncomeSelectAll
import com.keisardev.insight.core.database.IncomeSelectById
import com.keisardev.insight.core.database.IncomeSelectByDateRange
import com.keisardev.insight.core.database.IncomeSelectTotalByCategory
import com.keisardev.insight.core.model.Income
import com.keisardev.insight.core.model.IncomeCategory
import com.keisardev.insight.core.model.IncomeType
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
class IncomeRepositoryImpl(
    private val database: ExpenseDatabase,
) : IncomeRepository {

    override fun observeAllIncome(): Flow<List<Income>> {
        return database.incomeQueries.incomeSelectAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { incomes -> incomes.map { it.toDomain() } }
    }

    override fun observeIncomeById(id: Long): Flow<Income?> {
        return database.incomeQueries.incomeSelectById(id)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { it?.toDomain() }
    }

    override fun observeIncomeByDateRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): Flow<List<Income>> {
        val startMillis = startDate.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        // Add one day to end date to include the entire end day (SQL uses date < endMillis)
        val endMillis = endDate.plus(1, DateTimeUnit.DAY)
            .atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        return database.incomeQueries.incomeSelectByDateRange(startMillis, endMillis)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { incomes -> incomes.map { it.toDomain() } }
    }

    override fun observeMonthlyTotal(startDate: LocalDate, endDate: LocalDate): Flow<Double> {
        val startMillis = startDate.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        // Add one day to end date to include the entire end day
        val endMillis = endDate.plus(1, DateTimeUnit.DAY)
            .atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        return database.incomeQueries.incomeSelectMonthlyTotal(startMillis, endMillis)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { it ?: 0.0 }
    }

    override fun observeTotalByCategory(
        startDate: LocalDate,
        endDate: LocalDate,
    ): Flow<Map<IncomeCategory, Double>> {
        val startMillis = startDate.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        // Add one day to end date to include the entire end day
        val endMillis = endDate.plus(1, DateTimeUnit.DAY)
            .atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        return database.incomeQueries.incomeSelectTotalByCategory(startMillis, endMillis)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { results ->
                results.associate { row ->
                    row.toCategory() to (row.total ?: 0.0)
                }
            }
    }

    override suspend fun insertIncome(income: Income): Long = withContext(Dispatchers.IO) {
        val dateMillis = income.date.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        val createdAtMillis = income.createdAt.toEpochMilliseconds()
        database.incomeQueries.incomeInsert(
            amount = income.amount,
            incomeType = income.incomeType.name,
            categoryId = income.category.id,
            description = income.description,
            date = dateMillis,
            createdAt = createdAtMillis,
        )
        database.incomeQueries.incomeLastInsertRowId().executeAsOne()
    }

    override suspend fun updateIncome(income: Income): Unit = withContext(Dispatchers.IO) {
        val dateMillis = income.date.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        database.incomeQueries.incomeUpdate(
            amount = income.amount,
            incomeType = income.incomeType.name,
            categoryId = income.category.id,
            description = income.description,
            date = dateMillis,
            id = income.id,
        )
    }

    override suspend fun deleteIncome(id: Long): Unit = withContext(Dispatchers.IO) {
        database.incomeQueries.incomeDeleteById(id)
    }

    override suspend fun deleteAllIncome(): Unit = withContext(Dispatchers.IO) {
        database.incomeQueries.incomeDeleteAll()
    }

    private fun IncomeSelectAll.toDomain(): Income = Income(
        id = id,
        amount = amount,
        incomeType = IncomeType.valueOf(incomeType),
        category = IncomeCategory(
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

    private fun IncomeSelectById.toDomain(): Income = Income(
        id = id,
        amount = amount,
        incomeType = IncomeType.valueOf(incomeType),
        category = IncomeCategory(
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

    private fun IncomeSelectByDateRange.toDomain(): Income = Income(
        id = id,
        amount = amount,
        incomeType = IncomeType.valueOf(incomeType),
        category = IncomeCategory(
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

    private fun IncomeSelectTotalByCategory.toCategory(): IncomeCategory = IncomeCategory(
        id = id,
        name = name,
        icon = icon,
        colorHex = colorHex,
    )
}
