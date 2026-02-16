package com.keisardev.insight.feature.reports

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.keisardev.insight.core.designsystem.theme.InsightTheme
import com.keisardev.insight.core.model.Category
import com.keisardev.insight.core.model.FinancialSummary
import com.keisardev.insight.core.model.IncomeCategory
import com.keisardev.insight.core.model.ReportViewType
import kotlinx.datetime.Month

// ==================== SPENDING VIEW ====================

@PreviewTest
@Preview(showBackground = true, name = "Spending View - Light")
@Composable
fun ReportsSpendingLightPreview() {
    InsightTheme(darkTheme = false) {
        ReportsUi(
            state = ReportsScreen.State(
                selectedMonth = Month.JANUARY,
                selectedYear = 2024,
                selectedViewType = ReportViewType.SPENDING,
                totalSpending = 1250.50,
                totalIncome = 5000.0,
                categoryBreakdown = sampleCategoryBreakdown,
                incomeCategoryBreakdown = emptyList(),
                financialSummary = sampleSummary,
                currencyCode = "USD",
                eventSink = {},
            ),
        )
    }
}

@PreviewTest
@Preview(
    showBackground = true,
    name = "Spending View - Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun ReportsSpendingDarkPreview() {
    InsightTheme(darkTheme = true) {
        ReportsUi(
            state = ReportsScreen.State(
                selectedMonth = Month.JANUARY,
                selectedYear = 2024,
                selectedViewType = ReportViewType.SPENDING,
                totalSpending = 1250.50,
                totalIncome = 5000.0,
                categoryBreakdown = sampleCategoryBreakdown,
                incomeCategoryBreakdown = emptyList(),
                financialSummary = sampleSummary,
                currencyCode = "USD",
                eventSink = {},
            ),
        )
    }
}

// ==================== SPENDING EMPTY ====================

@PreviewTest
@Preview(showBackground = true, name = "Spending View - Empty")
@Composable
fun ReportsSpendingEmptyPreview() {
    InsightTheme {
        ReportsUi(
            state = ReportsScreen.State(
                selectedMonth = Month.FEBRUARY,
                selectedYear = 2024,
                selectedViewType = ReportViewType.SPENDING,
                totalSpending = 0.0,
                totalIncome = 0.0,
                categoryBreakdown = emptyList(),
                incomeCategoryBreakdown = emptyList(),
                financialSummary = FinancialSummary(totalIncome = 0.0, totalExpenses = 0.0),
                currencyCode = "USD",
                eventSink = {},
            ),
        )
    }
}

// ==================== EARNINGS VIEW ====================

@PreviewTest
@Preview(showBackground = true, name = "Earnings View")
@Composable
fun ReportsEarningsPreview() {
    InsightTheme {
        ReportsUi(
            state = ReportsScreen.State(
                selectedMonth = Month.JANUARY,
                selectedYear = 2024,
                selectedViewType = ReportViewType.EARNINGS,
                totalSpending = 1250.50,
                totalIncome = 6750.0,
                categoryBreakdown = emptyList(),
                incomeCategoryBreakdown = sampleIncomeCategoryBreakdown,
                financialSummary = sampleSummary,
                currencyCode = "USD",
                eventSink = {},
            ),
        )
    }
}

// ==================== BALANCE VIEW ====================

@PreviewTest
@Preview(showBackground = true, name = "Balance View - Saving")
@Composable
fun ReportsBalanceSavingPreview() {
    InsightTheme {
        ReportsUi(
            state = ReportsScreen.State(
                selectedMonth = Month.JANUARY,
                selectedYear = 2024,
                selectedViewType = ReportViewType.BALANCE,
                totalSpending = 3000.0,
                totalIncome = 5000.0,
                categoryBreakdown = emptyList(),
                incomeCategoryBreakdown = emptyList(),
                financialSummary = FinancialSummary(totalIncome = 5000.0, totalExpenses = 3000.0),
                currencyCode = "USD",
                eventSink = {},
            ),
        )
    }
}

@PreviewTest
@Preview(showBackground = true, name = "Balance View - Deficit")
@Composable
fun ReportsBalanceDeficitPreview() {
    InsightTheme {
        ReportsUi(
            state = ReportsScreen.State(
                selectedMonth = Month.JANUARY,
                selectedYear = 2024,
                selectedViewType = ReportViewType.BALANCE,
                totalSpending = 6000.0,
                totalIncome = 4000.0,
                categoryBreakdown = emptyList(),
                incomeCategoryBreakdown = emptyList(),
                financialSummary = FinancialSummary(totalIncome = 4000.0, totalExpenses = 6000.0),
                currencyCode = "USD",
                eventSink = {},
            ),
        )
    }
}

// ==================== SAMPLE DATA ====================

private val foodCategory = Category(1, "Food", "restaurant", 0xFFE57373)
private val transportCategory = Category(2, "Transport", "directions_car", 0xFF64B5F6)
private val entertainmentCategory = Category(3, "Entertainment", "movie", 0xFFBA68C8)

private val sampleCategoryBreakdown = listOf(
    CategorySpending(foodCategory, 600.0, 0.48f),
    CategorySpending(transportCategory, 400.0, 0.32f),
    CategorySpending(entertainmentCategory, 250.50, 0.20f),
)

private val salaryIncomeCategory = IncomeCategory(1, "Salary", "work", 0xFF4CAF50)
private val freelanceIncomeCategory = IncomeCategory(2, "Freelance", "computer", 0xFF2196F3)

private val sampleIncomeCategoryBreakdown = listOf(
    IncomeCategorySpending(salaryIncomeCategory, 5000.0, 0.74f),
    IncomeCategorySpending(freelanceIncomeCategory, 1750.0, 0.26f),
)

private val sampleSummary = FinancialSummary(
    totalIncome = 6750.0,
    totalExpenses = 1250.50,
)
