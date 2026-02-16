package com.keisardev.insight.feature.income.fakes

import com.keisardev.insight.core.data.repository.IncomeRepository
import com.keisardev.insight.core.model.Income
import com.keisardev.insight.core.model.IncomeCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

class FakeIncomeRepository : IncomeRepository {

    private val incomes = MutableStateFlow<List<Income>>(emptyList())
    private var nextId = 1L

    var insertCallCount = 0
        private set
    var updateCallCount = 0
        private set
    var deleteCallCount = 0
        private set
    var lastInsertedIncome: Income? = null
        private set
    var lastUpdatedIncome: Income? = null
        private set
    var lastDeletedId: Long? = null
        private set

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

    override suspend fun insertIncome(income: Income): Long {
        insertCallCount++
        val id = nextId++
        val newIncome = income.copy(id = id)
        lastInsertedIncome = newIncome
        incomes.value = incomes.value + newIncome
        return id
    }

    override suspend fun updateIncome(income: Income) {
        updateCallCount++
        lastUpdatedIncome = income
        incomes.value = incomes.value.map {
            if (it.id == income.id) income else it
        }
    }

    override suspend fun deleteIncome(id: Long) {
        deleteCallCount++
        lastDeletedId = id
        incomes.value = incomes.value.filter { it.id != id }
    }

    override suspend fun deleteAllIncome() {
        incomes.value = emptyList()
    }
}
