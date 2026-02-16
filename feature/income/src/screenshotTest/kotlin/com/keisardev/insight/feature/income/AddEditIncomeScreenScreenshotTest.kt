package com.keisardev.insight.feature.income

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.keisardev.insight.core.designsystem.theme.InsightTheme
import com.keisardev.insight.core.model.IncomeCategory
import com.keisardev.insight.core.model.IncomeType
import kotlinx.datetime.LocalDate

// ==================== ADD MODE ====================

@PreviewTest
@Preview(showBackground = true, name = "Add Mode - Empty Form")
@Composable
fun AddIncomeEmptyFormPreview() {
    InsightTheme {
        AddEditIncomeUi(
            state = AddEditIncomeScreen.State(
                isEditMode = false,
                amount = "",
                description = "",
                selectedIncomeType = IncomeType.ONE_TIME,
                selectedCategory = null,
                selectedDate = LocalDate(2024, 1, 15),
                categories = sampleIncomeCategories,
                isSaving = false,
                eventSink = {},
            ),
        )
    }
}

@PreviewTest
@Preview(showBackground = true, name = "Add Mode - Complete Form")
@Composable
fun AddIncomeCompleteFormPreview() {
    InsightTheme {
        AddEditIncomeUi(
            state = AddEditIncomeScreen.State(
                isEditMode = false,
                amount = "5000.00",
                description = "Monthly salary",
                selectedIncomeType = IncomeType.RECURRING,
                selectedCategory = sampleIncomeCategories[0],
                selectedDate = LocalDate(2024, 1, 15),
                categories = sampleIncomeCategories,
                isSaving = false,
                eventSink = {},
            ),
        )
    }
}

// ==================== EDIT MODE ====================

@PreviewTest
@Preview(showBackground = true, name = "Edit Mode - Light")
@Composable
fun EditIncomeLightPreview() {
    InsightTheme(darkTheme = false) {
        AddEditIncomeUi(
            state = AddEditIncomeScreen.State(
                isEditMode = true,
                amount = "1500.00",
                description = "Web project",
                selectedIncomeType = IncomeType.ONE_TIME,
                selectedCategory = sampleIncomeCategories[1],
                selectedDate = LocalDate(2024, 1, 15),
                categories = sampleIncomeCategories,
                isSaving = false,
                eventSink = {},
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
fun EditIncomeDarkPreview() {
    InsightTheme(darkTheme = true) {
        AddEditIncomeUi(
            state = AddEditIncomeScreen.State(
                isEditMode = true,
                amount = "1500.00",
                description = "Web project",
                selectedIncomeType = IncomeType.ONE_TIME,
                selectedCategory = sampleIncomeCategories[1],
                selectedDate = LocalDate(2024, 1, 15),
                categories = sampleIncomeCategories,
                isSaving = false,
                eventSink = {},
            ),
        )
    }
}

// ==================== SAVING STATE ====================

@PreviewTest
@Preview(showBackground = true, name = "Saving State")
@Composable
fun AddIncomeSavingPreview() {
    InsightTheme {
        AddEditIncomeUi(
            state = AddEditIncomeScreen.State(
                isEditMode = false,
                amount = "5000.00",
                description = "Monthly salary",
                selectedIncomeType = IncomeType.RECURRING,
                selectedCategory = sampleIncomeCategories[0],
                selectedDate = LocalDate(2024, 1, 15),
                categories = sampleIncomeCategories,
                isSaving = true,
                eventSink = {},
            ),
        )
    }
}

// ==================== SAMPLE DATA ====================

private val sampleIncomeCategories = listOf(
    IncomeCategory(1, "Salary", "work", 0xFF4CAF50),
    IncomeCategory(2, "Freelance", "computer", 0xFF2196F3),
    IncomeCategory(3, "Investment", "trending_up", 0xFFFF9800),
)
