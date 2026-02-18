package com.keisardev.insight.core.ai.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import com.keisardev.insight.core.data.repository.CategoryRepository
import com.keisardev.insight.core.data.repository.ExpenseRepository
import com.keisardev.insight.core.model.Expense
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Koog tools for querying expense data.
 * These tools allow the AI agent to access and analyze expense information.
 */
@LLMDescription("Tools for querying and analyzing expense data from the user's expense tracker")
class ExpenseTools(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val currencySymbol: String = "$",
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
            "Total expenses from $startDate to $endDate: ${currencySymbol}${String.format("%.2f", total)}"
        } catch (e: IllegalArgumentException) {
            "ERROR: Invalid date format. Use YYYY-MM-DD. You provided: startDate='$startDate', endDate='$endDate'"
        } catch (e: Exception) {
            "ERROR: Failed to get total expenses: ${e.message}"
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
                        "${category.name}: ${currencySymbol}${String.format("%.2f", amount)} (${String.format("%.1f", percentage)}%)"
                    }
                "Expenses by category from $startDate to $endDate:\n${lines.joinToString("\n")}\n\nTotal: ${currencySymbol}${String.format("%.2f", total)}"
            }
        } catch (e: IllegalArgumentException) {
            "ERROR: Invalid date format. Use YYYY-MM-DD. You provided: startDate='$startDate', endDate='$endDate'"
        } catch (e: Exception) {
            "ERROR: Failed to get expenses by category: ${e.message}"
        }
    }

    @Tool
    @LLMDescription("Get all available expense categories that users can assign to their expenses")
    suspend fun getCategories(): String {
        return try {
            val categories = categoryRepository.observeAllCategories().first()
            "Available categories: ${categories.joinToString(", ") { it.name }}"
        } catch (e: Exception) {
            "ERROR: Failed to get categories: ${e.message}"
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
                    "- ${expense.date}: ${expense.category.name} - ${currencySymbol}${String.format("%.2f", expense.amount)} ${if (expense.description.isNotBlank()) "(${expense.description})" else ""}"
                }
                "Recent expenses:\n${lines.joinToString("\n")}"
            }
        } catch (e: Exception) {
            "ERROR: Failed to get recent expenses: ${e.message}"
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
                    "- ${expense.date}: ${expense.category.name} - ${currencySymbol}${String.format("%.2f", expense.amount)} ${if (expense.description.isNotBlank()) "(${expense.description})" else ""}"
                }
                "Expenses from $startDate to $endDate:\n${lines.joinToString("\n")}"
            }
        } catch (e: IllegalArgumentException) {
            "ERROR: Invalid date format. Use YYYY-MM-DD. You provided: startDate='$startDate', endDate='$endDate'"
        } catch (e: Exception) {
            "ERROR: Failed to get expenses: ${e.message}"
        }
    }

    @Tool
    @LLMDescription(
        "Search for expenses by keyword. ALWAYS use this tool when the user asks about specific items " +
            "like 'pizza', 'coffee', 'uber', 'groceries', etc. The search is case-INSENSITIVE " +
            "(searching 'Pizza' will find 'pizza', 'PIZZA', etc.). Searches both description and category name."
    )
    suspend fun searchExpenses(
        @LLMDescription("Keyword to search for (case-insensitive). Examples: 'pizza', 'coffee', 'uber'")
        keyword: String,
    ): String {
        return try {
            val allExpenses = expenseRepository.observeAllExpenses().first()
            val matchingExpenses = allExpenses.filter { expense ->
                expense.description.contains(keyword, ignoreCase = true) ||
                    expense.category.name.contains(keyword, ignoreCase = true)
            }

            if (matchingExpenses.isEmpty()) {
                "No expenses found matching '$keyword'. The search is case-insensitive and looks in both descriptions and category names."
            } else {
                val total = matchingExpenses.sumOf { it.amount }
                val lines = matchingExpenses.map { expense ->
                    "- ${expense.date}: ${expense.category.name} - ${currencySymbol}${String.format("%.2f", expense.amount)} ${if (expense.description.isNotBlank()) "(${expense.description})" else ""}"
                }
                "Found ${matchingExpenses.size} expense(s) matching '$keyword':\n${lines.joinToString("\n")}\n\nTotal spent on '$keyword': ${currencySymbol}${String.format("%.2f", total)}"
            }
        } catch (e: Exception) {
            "ERROR: Failed to search expenses: ${e.message}"
        }
    }

    @Tool
    @LLMDescription(
        "Add a new expense entry to the user's tracker. Use this when the user asks to add, create, or record an expense. " +
            "Requires amount, category name, and optionally a description and date. " +
            "The category must match one of the available expense categories (case-insensitive). " +
            "If no date is provided, today's date is used.",
    )
    suspend fun addExpense(
        @LLMDescription("The expense amount as a number (e.g. 42.50)") amount: Double,
        @LLMDescription("The expense category name (must match an existing category, case-insensitive). Examples: 'Food', 'Transport', 'Entertainment'")
        categoryName: String,
        @LLMDescription("Optional description for the expense entry") description: String = "",
        @LLMDescription("Optional date in YYYY-MM-DD format. Defaults to today if not provided.") date: String = "",
    ): String {
        return try {
            val categories = categoryRepository.observeAllCategories().first()
            val category = categories.find { it.name.equals(categoryName, ignoreCase = true) }
                ?: return "ERROR: Category '$categoryName' not found. Available categories: ${categories.joinToString(", ") { it.name }}"

            val expenseDate = if (date.isNotBlank()) {
                LocalDate.parse(date)
            } else {
                Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            }

            val expense = Expense(
                amount = amount,
                category = category,
                description = description,
                date = expenseDate,
                createdAt = Clock.System.now(),
            )
            expenseRepository.insertExpense(expense)
            "Successfully added expense: ${currencySymbol}${String.format("%.2f", amount)} in ${category.name} on $expenseDate${if (description.isNotBlank()) " ($description)" else ""}"
        } catch (e: IllegalArgumentException) {
            "ERROR: Invalid date format. Use YYYY-MM-DD. You provided: '$date'"
        } catch (e: Exception) {
            "ERROR: Failed to add expense: ${e.message}"
        }
    }
}
