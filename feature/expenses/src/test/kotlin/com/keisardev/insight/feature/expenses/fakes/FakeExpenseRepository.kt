package com.keisardev.insight.feature.expenses.fakes

import com.keisardev.insight.core.data.repository.ExpenseRepository
import com.keisardev.insight.core.model.Category
import com.keisardev.insight.core.model.Expense
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/**
 * Fake implementation of [ExpenseRepository] for testing.
 *
 * Usage:
 * ```
 * val fakeRepo = FakeExpenseRepository()
 * fakeRepo.setExpenses(listOf(expense1, expense2))
 * // or emit dynamically
 * fakeRepo.emitExpenses(listOf(expense1))
 * ```
 */
class FakeExpenseRepository : ExpenseRepository {

    private val expenses = MutableStateFlow<List<Expense>>(emptyList())
    private var nextId = 1L

    // Tracking calls for verification
    var insertCallCount = 0
        private set
    var updateCallCount = 0
        private set
    var deleteCallCount = 0
        private set
    var lastInsertedExpense: Expense? = null
        private set
    var lastUpdatedExpense: Expense? = null
        private set
    var lastDeletedId: Long? = null
        private set

    fun setExpenses(list: List<Expense>) {
        expenses.value = list
    }

    fun emitExpenses(list: List<Expense>) {
        expenses.value = list
    }

    fun reset() {
        expenses.value = emptyList()
        nextId = 1L
        insertCallCount = 0
        updateCallCount = 0
        deleteCallCount = 0
        lastInsertedExpense = null
        lastUpdatedExpense = null
        lastDeletedId = null
    }

    override fun observeAllExpenses(): Flow<List<Expense>> = expenses

    override fun observeExpenseById(id: Long): Flow<Expense?> =
        expenses.map { list -> list.find { it.id == id } }

    override fun observeExpensesByDateRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): Flow<List<Expense>> = expenses.map { list ->
        list.filter { it.date >= startDate && it.date < endDate }
    }

    override fun observeMonthlyTotal(
        startDate: LocalDate,
        endDate: LocalDate,
    ): Flow<Double> = expenses.map { list ->
        list.filter { it.date >= startDate && it.date < endDate }
            .sumOf { it.amount }
    }

    override fun observeTotalByCategory(
        startDate: LocalDate,
        endDate: LocalDate,
    ): Flow<Map<Category, Double>> = expenses.map { list ->
        list.filter { it.date >= startDate && it.date < endDate }
            .groupBy { it.category }
            .mapValues { (_, expenses) -> expenses.sumOf { it.amount } }
    }

    override suspend fun insertExpense(expense: Expense): Long {
        insertCallCount++
        val id = nextId++
        val newExpense = expense.copy(id = id)
        lastInsertedExpense = newExpense
        expenses.value = expenses.value + newExpense
        return id
    }

    override suspend fun updateExpense(expense: Expense) {
        updateCallCount++
        lastUpdatedExpense = expense
        expenses.value = expenses.value.map {
            if (it.id == expense.id) expense else it
        }
    }

    override suspend fun deleteExpense(id: Long) {
        deleteCallCount++
        lastDeletedId = id
        expenses.value = expenses.value.filter { it.id != id }
    }

    override suspend fun deleteAllExpenses() {
        expenses.value = emptyList()
    }
}
