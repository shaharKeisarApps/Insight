package com.keisardev.insight.core.ai.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import com.keisardev.insight.core.data.repository.CategoryRepository
import com.keisardev.insight.core.data.repository.ExpenseRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate

/**
 * Koog tools for querying expense data.
 * These tools allow the AI agent to access and analyze expense information.
 */
@LLMDescription("Tools for querying and analyzing expense data from the user's expense tracker")
class ExpenseTools(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
) : ToolSet {

    @Tool
    @LLMDescription("Get the total amount spent within a date range")
    suspend fun getTotalExpenses(
        @LLMDescription("Start date in YYYY-MM-DD format") startDate: String,
        @LLMDescription("End date in YYYY-MM-DD format") endDate: String,
    ): String {
        return try {
            val start = LocalDate.parse(startDate)
            val end = LocalDate.parse(endDate)
            val total = expenseRepository.observeMonthlyTotal(start, end).first()
            "Total expenses from $startDate to $endDate: $${String.format("%.2f", total)}"
        } catch (e: Exception) {
            "Error getting total expenses: ${e.message}"
        }
    }

    @Tool
    @LLMDescription("Get expenses breakdown by category for a date range, showing how much was spent in each category")
    suspend fun getExpensesByCategory(
        @LLMDescription("Start date in YYYY-MM-DD format") startDate: String,
        @LLMDescription("End date in YYYY-MM-DD format") endDate: String,
    ): String {
        return try {
            val start = LocalDate.parse(startDate)
            val end = LocalDate.parse(endDate)
            val breakdown = expenseRepository.observeTotalByCategory(start, end).first()

            if (breakdown.isEmpty()) {
                "No expenses found in this date range."
            } else {
                val total = breakdown.values.sum()
                val lines = breakdown.entries
                    .sortedByDescending { it.value }
                    .map { (category, amount) ->
                        val percentage = (amount / total * 100)
                        "${category.name}: $${String.format("%.2f", amount)} (${String.format("%.1f", percentage)}%)"
                    }
                "Expenses by category from $startDate to $endDate:\n${lines.joinToString("\n")}\n\nTotal: $${String.format("%.2f", total)}"
            }
        } catch (e: Exception) {
            "Error getting expenses by category: ${e.message}"
        }
    }

    @Tool
    @LLMDescription("Get all available expense categories that users can assign to their expenses")
    suspend fun getCategories(): String {
        return try {
            val categories = categoryRepository.observeAllCategories().first()
            "Available categories: ${categories.joinToString(", ") { it.name }}"
        } catch (e: Exception) {
            "Error getting categories: ${e.message}"
        }
    }

    @Tool
    @LLMDescription("Get recent expenses with details including amount, category, description, and date")
    suspend fun getRecentExpenses(
        @LLMDescription("Number of recent expenses to retrieve (max 20)") count: Int = 10,
    ): String {
        return try {
            val limitedCount = count.coerceIn(1, 20)
            val expenses = expenseRepository.observeAllExpenses().first().take(limitedCount)

            if (expenses.isEmpty()) {
                "No expenses recorded yet."
            } else {
                val lines = expenses.map { expense ->
                    "- ${expense.date}: ${expense.category.name} - $${String.format("%.2f", expense.amount)} ${if (expense.description.isNotBlank()) "(${expense.description})" else ""}"
                }
                "Recent expenses:\n${lines.joinToString("\n")}"
            }
        } catch (e: Exception) {
            "Error getting recent expenses: ${e.message}"
        }
    }

    @Tool
    @LLMDescription("Get a list of expenses for a specific date range")
    suspend fun getExpensesByDateRange(
        @LLMDescription("Start date in YYYY-MM-DD format") startDate: String,
        @LLMDescription("End date in YYYY-MM-DD format") endDate: String,
    ): String {
        return try {
            val start = LocalDate.parse(startDate)
            val end = LocalDate.parse(endDate)
            val expenses = expenseRepository.observeExpensesByDateRange(start, end).first()

            if (expenses.isEmpty()) {
                "No expenses found from $startDate to $endDate."
            } else {
                val lines = expenses.map { expense ->
                    "- ${expense.date}: ${expense.category.name} - $${String.format("%.2f", expense.amount)} ${if (expense.description.isNotBlank()) "(${expense.description})" else ""}"
                }
                "Expenses from $startDate to $endDate:\n${lines.joinToString("\n")}"
            }
        } catch (e: Exception) {
            "Error getting expenses: ${e.message}"
        }
    }
}
