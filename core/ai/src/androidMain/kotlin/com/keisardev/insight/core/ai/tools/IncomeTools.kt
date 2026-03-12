package com.keisardev.insight.core.ai.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import com.keisardev.insight.core.data.repository.IncomeCategoryRepository
import com.keisardev.insight.core.data.repository.IncomeRepository
import com.keisardev.insight.core.model.Income
import com.keisardev.insight.core.model.IncomeType
import kotlinx.coroutines.flow.first
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Koog tools for querying income data.
 * These tools allow the AI agent to access and analyze income information.
 */
@LLMDescription("Tools for querying and analyzing income data from the user's income tracker")
class IncomeTools(
    private val incomeRepository: IncomeRepository,
    private val incomeCategoryRepository: IncomeCategoryRepository,
    private val currencySymbol: String = "$",
) : ToolSet {

    @Tool
    @LLMDescription("Get the total income received within a date range")
    suspend fun getTotalIncome(
        @LLMDescription("Start date in YYYY-MM-DD format") startDate: String,
        @LLMDescription("End date in YYYY-MM-DD format") endDate: String,
    ): String {
        return try {
            val start = LocalDate.parse(startDate)
            val end = LocalDate.parse(endDate)
            val total = incomeRepository.observeMonthlyTotal(start, end).first()
            "Total income from $startDate to $endDate: ${currencySymbol}${String.format("%.2f", total)}"
        } catch (e: IllegalArgumentException) {
            "ERROR: Invalid date format. Use YYYY-MM-DD. You provided: startDate='$startDate', endDate='$endDate'"
        } catch (e: Exception) {
            "ERROR: Failed to get total income: ${e.message}"
        }
    }

    @Tool
    @LLMDescription("Get income breakdown by category for a date range, showing how much was earned in each category")
    suspend fun getIncomeByCategory(
        @LLMDescription("Start date in YYYY-MM-DD format") startDate: String,
        @LLMDescription("End date in YYYY-MM-DD format") endDate: String,
    ): String {
        return try {
            val start = LocalDate.parse(startDate)
            val end = LocalDate.parse(endDate)
            val breakdown = incomeRepository.observeTotalByCategory(start, end).first()

            if (breakdown.isEmpty()) {
                "No income found in this date range."
            } else {
                val total = breakdown.values.sum()
                val lines = breakdown.entries
                    .sortedByDescending { it.value }
                    .map { (category, amount) ->
                        val percentage = (amount / total * 100)
                        "${category.name}: ${currencySymbol}${String.format("%.2f", amount)} (${String.format("%.1f", percentage)}%)"
                    }
                "Income by category from $startDate to $endDate:\n${lines.joinToString("\n")}\n\nTotal: ${currencySymbol}${String.format("%.2f", total)}"
            }
        } catch (e: IllegalArgumentException) {
            "ERROR: Invalid date format. Use YYYY-MM-DD. You provided: startDate='$startDate', endDate='$endDate'"
        } catch (e: Exception) {
            "ERROR: Failed to get income by category: ${e.message}"
        }
    }

    @Tool
    @LLMDescription("Get all available income categories")
    suspend fun getIncomeCategories(): String {
        return try {
            val categories = incomeCategoryRepository.observeAllCategories().first()
            "Available income categories: ${categories.joinToString(", ") { it.name }}"
        } catch (e: Exception) {
            "ERROR: Failed to get income categories: ${e.message}"
        }
    }

    @Tool
    @LLMDescription("Get recent income entries with details including amount, category, type, description, and date")
    suspend fun getRecentIncome(
        @LLMDescription("Number of recent income entries to retrieve (max 20)") count: Int = 10,
    ): String {
        return try {
            val limitedCount = count.coerceIn(1, 20)
            val incomes = incomeRepository.observeAllIncome().first().take(limitedCount)

            if (incomes.isEmpty()) {
                "No income recorded yet."
            } else {
                val lines = incomes.map { income ->
                    "- ${income.date}: ${income.category.name} (${income.incomeType.displayName}) - ${currencySymbol}${String.format("%.2f", income.amount)} ${if (income.description.isNotBlank()) "(${income.description})" else ""}"
                }
                "Recent income:\n${lines.joinToString("\n")}"
            }
        } catch (e: Exception) {
            "ERROR: Failed to get recent income: ${e.message}"
        }
    }

    @Tool
    @LLMDescription("Get a list of income entries for a specific date range")
    suspend fun getIncomeByDateRange(
        @LLMDescription("Start date in YYYY-MM-DD format") startDate: String,
        @LLMDescription("End date in YYYY-MM-DD format") endDate: String,
    ): String {
        return try {
            val start = LocalDate.parse(startDate)
            val end = LocalDate.parse(endDate)
            val incomes = incomeRepository.observeIncomeByDateRange(start, end).first()

            if (incomes.isEmpty()) {
                "No income found from $startDate to $endDate."
            } else {
                val lines = incomes.map { income ->
                    "- ${income.date}: ${income.category.name} (${income.incomeType.displayName}) - ${currencySymbol}${String.format("%.2f", income.amount)} ${if (income.description.isNotBlank()) "(${income.description})" else ""}"
                }
                "Income from $startDate to $endDate:\n${lines.joinToString("\n")}"
            }
        } catch (e: IllegalArgumentException) {
            "ERROR: Invalid date format. Use YYYY-MM-DD. You provided: startDate='$startDate', endDate='$endDate'"
        } catch (e: Exception) {
            "ERROR: Failed to get income: ${e.message}"
        }
    }

    @Tool
    @LLMDescription(
        "Search for income entries by keyword. Use this when the user asks about specific income " +
            "like 'salary', 'freelance', 'bonus', etc. The search is case-INSENSITIVE. " +
            "Searches both description and category name."
    )
    suspend fun searchIncome(
        @LLMDescription("Keyword to search for (case-insensitive). Examples: 'salary', 'freelance', 'bonus'")
        keyword: String,
    ): String {
        return try {
            val allIncome = incomeRepository.observeAllIncome().first()
            val matchingIncome = allIncome.filter { income ->
                income.description.contains(keyword, ignoreCase = true) ||
                    income.category.name.contains(keyword, ignoreCase = true)
            }

            if (matchingIncome.isEmpty()) {
                "No income found matching '$keyword'. The search is case-insensitive and looks in both descriptions and category names."
            } else {
                val total = matchingIncome.sumOf { it.amount }
                val lines = matchingIncome.map { income ->
                    "- ${income.date}: ${income.category.name} (${income.incomeType.displayName}) - ${currencySymbol}${String.format("%.2f", income.amount)} ${if (income.description.isNotBlank()) "(${income.description})" else ""}"
                }
                "Found ${matchingIncome.size} income entry/entries matching '$keyword':\n${lines.joinToString("\n")}\n\nTotal income from '$keyword': ${currencySymbol}${String.format("%.2f", total)}"
            }
        } catch (e: Exception) {
            "ERROR: Failed to search income: ${e.message}"
        }
    }

    @Tool
    @LLMDescription(
        "Add a new income entry to the user's tracker. Use this when the user asks to add, create, or record income. " +
            "Requires amount, category name, and optionally a description and date. " +
            "The category must match one of the available income categories (case-insensitive). " +
            "If no date is provided, today's date is used. If no incomeType is provided, defaults to ONE_TIME.",
    )
    suspend fun addIncome(
        @LLMDescription("The income amount as a number (e.g. 100.0)") amount: Double,
        @LLMDescription("The income category name (must match an existing category, case-insensitive). Examples: 'Salary', 'Investment', 'Freelance'")
        categoryName: String,
        @LLMDescription("Optional description for the income entry") description: String = "",
        @LLMDescription("Optional date in YYYY-MM-DD format. Defaults to today if not provided.") date: String = "",
        @LLMDescription("Income type: 'RECURRING' or 'ONE_TIME'. Defaults to ONE_TIME.") incomeType: String = "ONE_TIME",
    ): String {
        return try {
            val categories = incomeCategoryRepository.observeAllCategories().first()
            val category = categories.find { it.name.equals(categoryName, ignoreCase = true) }
                ?: return "ERROR: Category '$categoryName' not found. Available categories: ${categories.joinToString(", ") { it.name }}"

            val incomeDate = if (date.isNotBlank()) {
                LocalDate.parse(date)
            } else {
                Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            }

            val type = when (incomeType.uppercase()) {
                "RECURRING" -> IncomeType.RECURRING
                else -> IncomeType.ONE_TIME
            }

            val income = Income(
                amount = amount,
                incomeType = type,
                category = category,
                description = description,
                date = incomeDate,
                createdAt = Clock.System.now(),
            )
            incomeRepository.insertIncome(income)
            "Successfully added income: ${currencySymbol}${String.format("%.2f", amount)} in ${category.name} on $incomeDate${if (description.isNotBlank()) " ($description)" else ""}"
        } catch (e: IllegalArgumentException) {
            "ERROR: Invalid date format. Use YYYY-MM-DD. You provided: '$date'"
        } catch (e: Exception) {
            "ERROR: Failed to add income: ${e.message}"
        }
    }
}
