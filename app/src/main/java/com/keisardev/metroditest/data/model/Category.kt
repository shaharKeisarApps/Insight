package com.keisardev.metroditest.data.model

import androidx.compose.ui.graphics.Color

data class Category(
    val id: Long,
    val name: String,
    val icon: String,
    val color: Color,
)

object DefaultCategories {
    val categories = listOf(
        Category(0, "Food", "restaurant", Color(0xFFE57373)),
        Category(0, "Transport", "directions_car", Color(0xFF64B5F6)),
        Category(0, "Entertainment", "movie", Color(0xFFBA68C8)),
        Category(0, "Shopping", "shopping_bag", Color(0xFFFFD54F)),
        Category(0, "Bills", "receipt", Color(0xFF4DB6AC)),
        Category(0, "Health", "medical_services", Color(0xFFFF8A65)),
        Category(0, "Other", "more_horiz", Color(0xFF90A4AE)),
    )
}
