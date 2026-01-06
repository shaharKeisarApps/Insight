package com.keisardev.insight.fakes

import com.keisardev.insight.core.model.Category
import com.keisardev.insight.core.model.Expense
import com.keisardev.insight.core.data.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

class FakeExpenseRepository : ExpenseRepository {

    private val expenses = MutableStateFlow<List<Expense>>(emptyList())
    private var nextId = 1L

    fun setExpenses(list: List<Expense>) {
        expenses.value = list
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
        val id = nextId++
        val newExpense = expense.copy(id = id)
        expenses.value = expenses.value + newExpense
        return id
    }

    override suspend fun updateExpense(expense: Expense) {
        expenses.value = expenses.value.map {
            if (it.id == expense.id) expense else it
        }
    }

    override suspend fun deleteExpense(id: Long) {
        expenses.value = expenses.value.filter { it.id != id }
    }

    override suspend fun deleteAllExpenses() {
        expenses.value = emptyList()
    }
}
