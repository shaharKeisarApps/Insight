package com.keisardev.metroditest.core.data.repository

import com.keisardev.metroditest.core.model.Income
import com.keisardev.metroditest.core.model.IncomeCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface IncomeRepository {
    fun observeAllIncome(): Flow<List<Income>>

    fun observeIncomeById(id: Long): Flow<Income?>

    fun observeIncomeByDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<Income>>

    fun observeMonthlyTotal(startDate: LocalDate, endDate: LocalDate): Flow<Double>

    fun observeTotalByCategory(startDate: LocalDate, endDate: LocalDate): Flow<Map<IncomeCategory, Double>>

    suspend fun insertIncome(income: Income): Long

    suspend fun updateIncome(income: Income)

    suspend fun deleteIncome(id: Long)

    suspend fun deleteAllIncome()
}
