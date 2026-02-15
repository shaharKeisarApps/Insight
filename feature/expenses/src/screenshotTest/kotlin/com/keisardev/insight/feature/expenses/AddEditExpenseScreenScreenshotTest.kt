package com.keisardev.insight.feature.expenses

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.keisardev.insight.core.designsystem.theme.InsightTheme
import com.keisardev.insight.core.model.Category
import kotlinx.datetime.LocalDate

/**
 * Screenshot tests for [AddEditExpenseUi].
 *
 * These tests verify the visual appearance of the add/edit expense screen
 * in various states: add mode, edit mode, with form data, and validation states.
 *
 * Run with: ./gradlew :feature:expenses:updateDebugScreenshotTest
 * Validate with: ./gradlew :feature:expenses:validateDebugScreenshotTest
 */

// ==================== ADD MODE ====================

@PreviewTest
@Preview(showBackground = true, name = "Add Mode - Empty Form")
@Composable
fun AddExpenseEmptyFormPreview() {
    InsightTheme {
        AddEditExpenseUi(
            state = AddEditExpenseScreen.State(
                isEditMode = false,
                amount = "",
                description = "",
                selectedCategory = null,
                selectedDate = LocalDate(2024, 1, 15),
                categories = sampleCategories,
                isSaving = false,
                eventSink = noOpEventSink,
            ),
        )
    }
}

@PreviewTest
@Preview(showBackground = true, name = "Add Mode - Partial Form")
@Composable
fun AddExpensePartialFormPreview() {
    InsightTheme {
        AddEditExpenseUi(
            state = AddEditExpenseScreen.State(
                isEditMode = false,
                amount = "25.50",
                description = "",
                selectedCategory = null,
                selectedDate = LocalDate(2024, 1, 15),
                categories = sampleCategories,
                isSaving = false,
                eventSink = noOpEventSink,
            ),
        )
    }
}

@PreviewTest
@Preview(showBackground = true, name = "Add Mode - Complete Form")
@Composable
fun AddExpenseCompleteFormPreview() {
    InsightTheme {
        AddEditExpenseUi(
            state = AddEditExpenseScreen.State(
                isEditMode = false,
                amount = "25.50",
                description = "Lunch at restaurant",
                selectedCategory = sampleCategories[0],
                selectedDate = LocalDate(2024, 1, 15),
                categories = sampleCategories,
                isSaving = false,
                eventSink = noOpEventSink,
            ),
        )
    }
}

@PreviewTest
@Preview(showBackground = true, name = "Add Mode - Saving")
@Composable
fun AddExpenseSavingPreview() {
    InsightTheme {
        AddEditExpenseUi(
            state = AddEditExpenseScreen.State(
                isEditMode = false,
                amount = "25.50",
                description = "Lunch at restaurant",
                selectedCategory = sampleCategories[0],
                selectedDate = LocalDate(2024, 1, 15),
                categories = sampleCategories,
                isSaving = true,
                eventSink = noOpEventSink,
            ),
        )
    }
}

// ==================== EDIT MODE ====================

@PreviewTest
@Preview(showBackground = true, name = "Edit Mode - Light")
@Composable
fun EditExpenseLightPreview() {
    InsightTheme(darkTheme = false) {
        AddEditExpenseUi(
            state = AddEditExpenseScreen.State(
                isEditMode = true,
                amount = "25.50",
                description = "Lunch at restaurant",
                selectedCategory = sampleCategories[0],
                selectedDate = LocalDate(2024, 1, 15),
                categories = sampleCategories,
                isSaving = false,
                eventSink = noOpEventSink,
            ),
        )
    }
}

@PreviewTest
@Preview(
    showBackground = true,
    name = "Edit Mode - Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun EditExpenseDarkPreview() {
    InsightTheme(darkTheme = true) {
        AddEditExpenseUi(
            state = AddEditExpenseScreen.State(
                isEditMode = true,
                amount = "25.50",
                description = "Lunch at restaurant",
                selectedCategory = sampleCategories[0],
                selectedDate = LocalDate(2024, 1, 15),
                categories = sampleCategories,
                isSaving = false,
                eventSink = noOpEventSink,
            ),
        )
    }
}

// ==================== CATEGORY SELECTION VARIANTS ====================

@PreviewTest
@Preview(showBackground = true, name = "Category - Food Selected")
@Composable
fun CategoryFoodSelectedPreview() {
    InsightTheme {
        AddEditExpenseUi(
            state = AddEditExpenseScreen.State(
                isEditMode = false,
                amount = "15.00",
                description = "",
                selectedCategory = sampleCategories[0], // Food
                selectedDate = LocalDate(2024, 1, 15),
                categories = sampleCategories,
                isSaving = false,
                eventSink = noOpEventSink,
            ),
        )
    }
}

@PreviewTest
@Preview(showBackground = true, name = "Category - Transport Selected")
@Composable
fun CategoryTransportSelectedPreview() {
    InsightTheme {
        AddEditExpenseUi(
            state = AddEditExpenseScreen.State(
                isEditMode = false,
                amount = "15.00",
                description = "",
                selectedCategory = sampleCategories[1], // Transport
                selectedDate = LocalDate(2024, 1, 15),
                categories = sampleCategories,
                isSaving = false,
                eventSink = noOpEventSink,
            ),
        )
    }
}

// ==================== FONT SCALE VARIANTS ====================

@PreviewTest
@Preview(showBackground = true, name = "Large Font", fontScale = 1.5f)
@Composable
fun AddExpenseLargeFontPreview() {
    InsightTheme {
        AddEditExpenseUi(
            state = AddEditExpenseScreen.State(
                isEditMode = false,
                amount = "25.50",
                description = "Lunch",
                selectedCategory = sampleCategories[0],
                selectedDate = LocalDate(2024, 1, 15),
                categories = sampleCategories,
                isSaving = false,
                eventSink = noOpEventSink,
            ),
        )
    }
}

// ==================== LONG DESCRIPTION ====================

@PreviewTest
@Preview(showBackground = true, name = "Long Description")
@Composable
fun AddExpenseLongDescriptionPreview() {
    InsightTheme {
        AddEditExpenseUi(
            state = AddEditExpenseScreen.State(
                isEditMode = false,
                amount = "99.99",
                description = "This is a very long description that tests how the UI handles longer text in the description field",
                selectedCategory = sampleCategories[2],
                selectedDate = LocalDate(2024, 1, 15),
                categories = sampleCategories,
                isSaving = false,
                eventSink = noOpEventSink,
            ),
        )
    }
}

// ==================== MANY CATEGORIES ====================

@PreviewTest
@Preview(showBackground = true, name = "Many Categories")
@Composable
fun AddExpenseManyCategoriesPreview() {
    InsightTheme {
        AddEditExpenseUi(
            state = AddEditExpenseScreen.State(
                isEditMode = false,
                amount = "",
                description = "",
                selectedCategory = null,
                selectedDate = LocalDate(2024, 1, 15),
                categories = allCategories,
                isSaving = false,
                eventSink = noOpEventSink,
            ),
        )
    }
}

// ==================== HELPERS ====================

private val noOpEventSink: (AddEditExpenseScreen.Event) -> Unit = {}

// ==================== SAMPLE DATA ====================

private val sampleCategories = listOf(
    Category(1, "Food", "restaurant", 0xFFE57373),
    Category(2, "Transport", "directions_car", 0xFF64B5F6),
    Category(3, "Entertainment", "movie", 0xFFBA68C8),
)

private val allCategories = listOf(
    Category(1, "Food", "restaurant", 0xFFE57373),
    Category(2, "Transport", "directions_car", 0xFF64B5F6),
    Category(3, "Entertainment", "movie", 0xFFBA68C8),
    Category(4, "Shopping", "shopping_bag", 0xFF81C784),
    Category(5, "Bills", "receipt", 0xFFFFB74D),
    Category(6, "Health", "medical_services", 0xFF4DB6AC),
    Category(7, "Other", "more_horiz", 0xFF90A4AE),
)
