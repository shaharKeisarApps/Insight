package com.keisardev.insight.fakes

import com.keisardev.insight.core.model.Category
import com.keisardev.insight.core.data.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeCategoryRepository : CategoryRepository {

    private val categories = MutableStateFlow<List<Category>>(emptyList())

    fun setCategories(list: List<Category>) {
        categories.value = list
    }

    override fun observeAllCategories(): Flow<List<Category>> = categories

    override suspend fun getCategoryById(id: Long): Category? =
        categories.value.find { it.id == id }

    override suspend fun seedDefaultCategories() {
        categories.value = defaultCategories
    }

    override suspend fun getCategoryCount(): Long = categories.value.size.toLong()

    companion object {
        val defaultCategories = listOf(
            Category(1, "Food", "restaurant", 0xFFE57373L),
            Category(2, "Transport", "directions_car", 0xFF64B5F6L),
            Category(3, "Entertainment", "movie", 0xFFBA68C8L),
            Category(4, "Shopping", "shopping_bag", 0xFF81C784L),
            Category(5, "Bills", "receipt", 0xFFFFB74DL),
            Category(6, "Health", "medical_services", 0xFF4DB6ACL),
            Category(7, "Other", "more_horiz", 0xFF90A4AEL),
        )
    }
}
