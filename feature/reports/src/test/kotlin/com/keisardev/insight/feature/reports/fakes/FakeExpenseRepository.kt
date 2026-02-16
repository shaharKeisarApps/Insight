package com.keisardev.insight.feature.reports.fakes

import com.keisardev.insight.core.data.repository.ExpenseRepository
import com.keisardev.insight.core.model.Category
import com.keisardev.insight.core.model.Expense
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

class FakeExpenseRepository : ExpenseRepository {

    private val expenses = MutableStateFlow<List<Expense>>(emptyList())

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
            .mapValues { (_, items) -> items.sumOf { it.amount } }
    }

    override suspend fun insertExpense(expense: Expense): Long = 0L
    override suspend fun updateExpense(expense: Expense) {}
    override suspend fun deleteExpense(id: Long) {}
    override suspend fun deleteAllExpenses() {}
}
