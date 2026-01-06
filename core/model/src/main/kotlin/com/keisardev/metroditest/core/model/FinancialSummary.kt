package com.keisardev.metroditest.core.model

/**
 * Represents a financial summary combining income and expenses.
 *
 * @param totalIncome The total income for the period
 * @param totalExpenses The total expenses for the period
 * @param incomeByCategory Income breakdown by category
 * @param expensesByCategory Expenses breakdown by category
 */
data class FinancialSummary(
    val totalIncome: Double,
    val totalExpenses: Double,
    val incomeByCategory: Map<IncomeCategory, Double> = emptyMap(),
    val expensesByCategory: Map<Category, Double> = emptyMap(),
) {
    /**
     * Net balance (income - expenses).
     * Positive means saving, negative means deficit.
     */
    val netBalance: Double
        get() = totalIncome - totalExpenses

    /**
     * Savings rate as a percentage of income.
     * Returns 0 if there's no income.
     */
    val savingsRate: Double
        get() = if (totalIncome > 0) (netBalance / totalIncome) * 100 else 0.0

    /**
     * Whether the user is saving money (income > expenses).
     */
    val isSaving: Boolean
        get() = netBalance >= 0
}
