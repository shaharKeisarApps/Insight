package com.keisardev.insight.feature.expenses

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.keisardev.insight.core.designsystem.theme.MetroDITestTheme
import com.keisardev.insight.core.model.Category
import com.keisardev.insight.core.model.Expense
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate

/**
 * Screenshot tests for [ExpensesUi].
 *
 * These tests verify the visual appearance of the expenses list screen
 * in various states: loading, empty, and with data.
 *
 * Run with: ./gradlew :feature:expenses:updateDebugScreenshotTest
 * Validate with: ./gradlew :feature:expenses:validateDebugScreenshotTest
 */

// ==================== EMPTY STATE ====================

@PreviewTest
@Preview(showBackground = true, name = "Empty State - Light")
@Composable
fun ExpensesScreenEmptyLightPreview() {
    MetroDITestTheme(darkTheme = false) {
        ExpensesUi(
            state = ExpensesScreen.State(
                isLoading = false,
                expenses = emptyList(),
                eventSink = noOpEventSink,
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
fun ExpensesScreenEmptyDarkPreview() {
    MetroDITestTheme(darkTheme = true) {
        ExpensesUi(
            state = ExpensesScreen.State(
                isLoading = false,
                expenses = emptyList(),
                eventSink = noOpEventSink,
            ),
        )
    }
}

// ==================== LOADING STATE ====================

@PreviewTest
@Preview(showBackground = true, name = "Loading State")
@Composable
fun ExpensesScreenLoadingPreview() {
    MetroDITestTheme {
        ExpensesUi(
            state = ExpensesScreen.State(
                isLoading = true,
                expenses = emptyList(),
                eventSink = noOpEventSink,
            ),
        )
    }
}

// ==================== WITH DATA ====================

@PreviewTest
@Preview(showBackground = true, name = "With Data - Light")
@Composable
fun ExpensesScreenWithDataLightPreview() {
    MetroDITestTheme(darkTheme = false) {
        ExpensesUi(
            state = ExpensesScreen.State(
                isLoading = false,
                expenses = sampleExpenses,
                eventSink = noOpEventSink,
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
fun ExpensesScreenWithDataDarkPreview() {
    MetroDITestTheme(darkTheme = true) {
        ExpensesUi(
            state = ExpensesScreen.State(
                isLoading = false,
                expenses = sampleExpenses,
                eventSink = noOpEventSink,
            ),
        )
    }
}

// ==================== SINGLE ITEM ====================

@PreviewTest
@Preview(showBackground = true, name = "Single Expense")
@Composable
fun ExpensesScreenSingleItemPreview() {
    MetroDITestTheme {
        ExpensesUi(
            state = ExpensesScreen.State(
                isLoading = false,
                expenses = listOf(sampleExpenses.first()),
                eventSink = noOpEventSink,
            ),
        )
    }
}

// ==================== FONT SCALE VARIANTS ====================

@PreviewTest
@Preview(showBackground = true, name = "Large Font", fontScale = 1.5f)
@Composable
fun ExpensesScreenLargeFontPreview() {
    MetroDITestTheme {
        ExpensesUi(
            state = ExpensesScreen.State(
                isLoading = false,
                expenses = sampleExpenses.take(2),
                eventSink = noOpEventSink,
            ),
        )
    }
}

@PreviewTest
@Preview(showBackground = true, name = "Small Font", fontScale = 0.85f)
@Composable
fun ExpensesScreenSmallFontPreview() {
    MetroDITestTheme {
        ExpensesUi(
            state = ExpensesScreen.State(
                isLoading = false,
                expenses = sampleExpenses.take(2),
                eventSink = noOpEventSink,
            ),
        )
    }
}

// ==================== HELPERS ====================

private val noOpEventSink: (ExpensesScreen.Event) -> Unit = {}

// ==================== SAMPLE DATA ====================

private val foodCategory = Category(
    id = 1,
    name = "Food",
    icon = "restaurant",
    colorHex = 0xFFE57373,
)

private val transportCategory = Category(
    id = 2,
    name = "Transport",
    icon = "directions_car",
    colorHex = 0xFF64B5F6,
)

private val entertainmentCategory = Category(
    id = 3,
    name = "Entertainment",
    icon = "movie",
    colorHex = 0xFFBA68C8,
)

private val sampleExpenses = listOf(
    Expense(
        id = 1,
        amount = 25.50,
        category = foodCategory,
        description = "Lunch at restaurant",
        date = LocalDate(2024, 1, 15),
        createdAt = Clock.System.now(),
    ),
    Expense(
        id = 2,
        amount = 5.00,
        category = foodCategory,
        description = "Morning coffee",
        date = LocalDate(2024, 1, 15),
        createdAt = Clock.System.now(),
    ),
    Expense(
        id = 3,
        amount = 15.00,
        category = transportCategory,
        description = "Taxi to work",
        date = LocalDate(2024, 1, 14),
        createdAt = Clock.System.now(),
    ),
    Expense(
        id = 4,
        amount = 12.00,
        category = entertainmentCategory,
        description = "Movie tickets",
        date = LocalDate(2024, 1, 13),
        createdAt = Clock.System.now(),
    ),
    Expense(
        id = 5,
        amount = 150.00,
        category = transportCategory,
        description = "Monthly bus pass",
        date = LocalDate(2024, 1, 1),
        createdAt = Clock.System.now(),
    ),
)
