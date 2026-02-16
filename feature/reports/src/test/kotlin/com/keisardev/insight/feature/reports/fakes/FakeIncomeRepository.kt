package com.keisardev.insight.feature.reports.fakes

import com.keisardev.insight.core.data.repository.IncomeRepository
import com.keisardev.insight.core.model.Income
import com.keisardev.insight.core.model.IncomeCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

class FakeIncomeRepository : IncomeRepository {

    private val incomes = MutableStateFlow<List<Income>>(emptyList())

    fun setIncomes(list: List<Income>) {
        incomes.value = list
    }

    override fun observeAllIncome(): Flow<List<Income>> = incomes

    override fun observeIncomeById(id: Long): Flow<Income?> =
        incomes.map { list -> list.find { it.id == id } }

    override fun observeIncomeByDateRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): Flow<List<Income>> = incomes.map { list ->
        list.filter { it.date >= startDate && it.date < endDate }
    }

    override fun observeMonthlyTotal(
        startDate: LocalDate,
        endDate: LocalDate,
    ): Flow<Double> = incomes.map { list ->
        list.filter { it.date >= startDate && it.date < endDate }
            .sumOf { it.amount }
    }

    override fun observeTotalByCategory(
        startDate: LocalDate,
        endDate: LocalDate,
    ): Flow<Map<IncomeCategory, Double>> = incomes.map { list ->
        list.filter { it.date >= startDate && it.date < endDate }
            .groupBy { it.category }
            .mapValues { (_, items) -> items.sumOf { it.amount } }
    }

    override suspend fun insertIncome(income: Income): Long = 0L
    override suspend fun updateIncome(income: Income) {}
    override suspend fun deleteIncome(id: Long) {}
    override suspend fun deleteAllIncome() {}
}
