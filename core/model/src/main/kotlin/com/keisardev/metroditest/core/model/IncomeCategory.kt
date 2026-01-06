package com.keisardev.metroditest.core.model

/**
 * Represents an income category.
 *
 * @param id The unique identifier for this category
 * @param name The display name of the category
 * @param icon The icon identifier (e.g., "payments", "work")
 * @param colorHex The color as a hex value (e.g., 0xFF4CAF50)
 */
data class IncomeCategory(
    val id: Long,
    val name: String,
    val icon: String,
    val colorHex: Long,
)

/**
 * Default income categories available in the app.
 */
object DefaultIncomeCategories {
    val categories = listOf(
        IncomeCategory(0, "Salary", "payments", 0xFF4CAF50),
        IncomeCategory(0, "Freelance", "work", 0xFF2196F3),
        IncomeCategory(0, "Investments", "trending_up", 0xFF9C27B0),
        IncomeCategory(0, "Rental", "home", 0xFFFF9800),
        IncomeCategory(0, "Gifts", "card_giftcard", 0xFFE91E63),
        IncomeCategory(0, "Bonus", "stars", 0xFFFFEB3B),
        IncomeCategory(0, "Other", "more_horiz", 0xFF607D8B),
    )
}
