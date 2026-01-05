package com.keisardev.metroditest.core.model

/**
 * Represents a spending category.
 *
 * @param id The unique identifier for this category
 * @param name The display name of the category
 * @param icon The icon identifier (e.g., "restaurant", "directions_car")
 * @param colorHex The color as a hex value (e.g., 0xFFE57373)
 */
data class Category(
    val id: Long,
    val name: String,
    val icon: String,
    val colorHex: Long,
)

/**
 * Default categories available in the app.
 */
object DefaultCategories {
    val categories = listOf(
        Category(0, "Food", "restaurant", 0xFFE57373),
        Category(0, "Transport", "directions_car", 0xFF64B5F6),
        Category(0, "Entertainment", "movie", 0xFFBA68C8),
        Category(0, "Shopping", "shopping_bag", 0xFFFFD54F),
        Category(0, "Bills", "receipt", 0xFF4DB6AC),
        Category(0, "Health", "medical_services", 0xFFFF8A65),
        Category(0, "Other", "more_horiz", 0xFF90A4AE),
    )
}
