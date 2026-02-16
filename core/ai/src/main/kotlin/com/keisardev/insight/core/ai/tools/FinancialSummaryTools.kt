package com.keisardev.insight.core.ai.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import com.keisardev.insight.core.data.repository.FinancialSummaryRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate

/**
 * Koog tools for querying combined financial data (income vs expenses).
 * These tools allow the AI agent to provide holistic financial insights.
 */
@LLMDescription("Tools for querying combined financial summaries including income, expenses, and net balance")
class FinancialSummaryTools(
    private val financialSummaryRepository: FinancialSummaryRepository,
) : ToolSet {

    @Tool
    @LLMDescription(
        "Get a complete financial summary for a date range including total income, total expenses, " +
            "net balance, savings rate, and breakdowns by category. Use this when users ask about their " +
            "overall financial health, savings, or income vs expenses comparison."
    )
    suspend fun getFinancialSummary(
        @LLMDescription("Start date in YYYY-MM-DD format") startDate: String,
        @LLMDescription("End date in YYYY-MM-DD format") endDate: String,
    ): String {
        return try {
            val start = LocalDate.parse(startDate)
            val end = LocalDate.parse(endDate)
            val summary = financialSummaryRepository.observeFinancialSummary(start, end).first()

            buildString {
                appendLine("Financial Summary from $startDate to $endDate:")
                appendLine("Total Income: $${String.format("%.2f", summary.totalIncome)}")
                appendLine("Total Expenses: $${String.format("%.2f", summary.totalExpenses)}")
                appendLine("Net Balance: $${String.format("%.2f", summary.netBalance)}")
                if (summary.totalIncome > 0) {
                    appendLine("Savings Rate: ${String.format("%.1f", summary.savingsRate)}%")
                }
                appendLine("Status: ${if (summary.isSaving) "Saving money" else "Spending more than earning"}")

                if (summary.expensesByCategory.isNotEmpty()) {
                    appendLine()
                    appendLine("Expense Breakdown:")
                    summary.expensesByCategory.entries
                        .sortedByDescending { it.value }
                        .forEach { (category, amount) ->
                            appendLine("  - ${category.name}: $${String.format("%.2f", amount)}")
                        }
                }

                if (summary.incomeByCategory.isNotEmpty()) {
                    appendLine()
                    appendLine("Income Breakdown:")
                    summary.incomeByCategory.entries
                        .sortedByDescending { it.value }
                        .forEach { (category, amount) ->
                            appendLine("  - ${category.name}: $${String.format("%.2f", amount)}")
                        }
                }
            }.trim()
        } catch (e: Exception) {
            "Error getting financial summary: ${e.message}"
        }
    }
}
