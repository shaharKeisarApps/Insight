package com.keisardev.metroditest.data.repository

import com.keisardev.metroditest.data.model.Category
import com.keisardev.metroditest.data.model.Expense
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface ExpenseRepository {
    fun observeAllExpenses(): Flow<List<Expense>>

    fun observeExpenseById(id: Long): Flow<Expense?>

    fun observeExpensesByDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<Expense>>

    fun observeMonthlyTotal(startDate: LocalDate, endDate: LocalDate): Flow<Double>

    fun observeTotalByCategory(startDate: LocalDate, endDate: LocalDate): Flow<Map<Category, Double>>

    suspend fun insertExpense(expense: Expense): Long

    suspend fun updateExpense(expense: Expense)

    suspend fun deleteExpense(id: Long)

    suspend fun deleteAllExpenses()
}
