package com.keisardev.insight.feature.income

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.keisardev.insight.core.designsystem.theme.InsightTheme
import com.keisardev.insight.core.model.Income
import com.keisardev.insight.core.model.IncomeCategory
import com.keisardev.insight.core.model.IncomeType
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate

// ==================== EMPTY STATE ====================

@PreviewTest
@Preview(showBackground = true, name = "Empty State - Light")
@Composable
fun IncomeScreenEmptyLightPreview() {
    InsightTheme(darkTheme = false) {
        IncomeUi(
            state = IncomeScreen.State(
                isLoading = false,
                incomes = emptyList(),
                currencyCode = "USD",
                eventSink = {},
            ),
        )
    }
}

@PreviewTest
@Preview(
    showBackground = true,
    name = "Empty State - Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun IncomeScreenEmptyDarkPreview() {
    InsightTheme(darkTheme = true) {
        IncomeUi(
            state = IncomeScreen.State(
                isLoading = false,
                incomes = emptyList(),
                currencyCode = "USD",
                eventSink = {},
            ),
        )
    }
}

// ==================== LOADING STATE ====================

@PreviewTest
@Preview(showBackground = true, name = "Loading State")
@Composable
fun IncomeScreenLoadingPreview() {
    InsightTheme {
        IncomeUi(
            state = IncomeScreen.State(
                isLoading = true,
                incomes = emptyList(),
                currencyCode = "USD",
                eventSink = {},
            ),
        )
    }
}

// ==================== WITH DATA ====================

@PreviewTest
@Preview(showBackground = true, name = "With Data - Light")
@Composable
fun IncomeScreenWithDataLightPreview() {
    InsightTheme(darkTheme = false) {
        IncomeUi(
            state = IncomeScreen.State(
                isLoading = false,
                incomes = sampleIncomes,
                currencyCode = "USD",
                eventSink = {},
            ),
        )
    }
}

@PreviewTest
@Preview(
    showBackground = true,
    name = "With Data - Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun IncomeScreenWithDataDarkPreview() {
    InsightTheme(darkTheme = true) {
        IncomeUi(
            state = IncomeScreen.State(
                isLoading = false,
                incomes = sampleIncomes,
                currencyCode = "USD",
                eventSink = {},
            ),
        )
    }
}

// ==================== SAMPLE DATA ====================

private val salaryCategory = IncomeCategory(
    id = 1,
    name = "Salary",
    icon = "work",
    colorHex = 0xFF4CAF50,
)

private val freelanceCategory = IncomeCategory(
    id = 2,
    name = "Freelance",
    icon = "computer",
    colorHex = 0xFF2196F3,
)

private val investmentCategory = IncomeCategory(
    id = 3,
    name = "Investment",
    icon = "trending_up",
    colorHex = 0xFFFF9800,
)

private val sampleIncomes = listOf(
    Income(
        id = 1,
        amount = 5000.0,
        incomeType = IncomeType.RECURRING,
        category = salaryCategory,
        description = "Monthly salary",
        date = LocalDate(2024, 1, 1),
        createdAt = Clock.System.now(),
    ),
    Income(
        id = 2,
        amount = 1500.0,
        incomeType = IncomeType.ONE_TIME,
        category = freelanceCategory,
        description = "Web project",
        date = LocalDate(2024, 1, 15),
        createdAt = Clock.System.now(),
    ),
    Income(
        id = 3,
        amount = 250.0,
        incomeType = IncomeType.RECURRING,
        category = investmentCategory,
        description = "Stock dividends",
        date = LocalDate(2024, 1, 20),
        createdAt = Clock.System.now(),
    ),
)
