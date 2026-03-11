package com.keisardev.insight.core.testing.fakes

import com.keisardev.insight.core.data.repository.IncomeCategoryRepository
import com.keisardev.insight.core.model.IncomeCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Fake implementation of [IncomeCategoryRepository] for testing.
 */
class FakeIncomeCategoryRepository : IncomeCategoryRepository {

    private val categories = MutableStateFlow<List<IncomeCategory>>(emptyList())

    fun setCategories(list: List<IncomeCategory>) {
        categories.value = list
    }

    fun reset() {
        categories.value = emptyList()
    }

    override fun observeAllCategories(): Flow<List<IncomeCategory>> = categories

    override suspend fun getCategoryById(id: Long): IncomeCategory? =
        categories.value.find { it.id == id }

    override suspend fun seedDefaultCategories() {
        categories.value = defaultCategories
    }

    override suspend fun getCategoryCount(): Long = categories.value.size.toLong()

    companion object {
        val defaultCategories = listOf(
            IncomeCategory(1, "Salary", "payments", 0xFF4CAF50L),
            IncomeCategory(2, "Freelance", "work", 0xFF2196F3L),
            IncomeCategory(3, "Investments", "trending_up", 0xFF9C27B0L),
            IncomeCategory(4, "Rental", "home", 0xFFFF9800L),
            IncomeCategory(5, "Gifts", "card_giftcard", 0xFFE91E63L),
            IncomeCategory(6, "Bonus", "stars", 0xFFFFEB3BL),
            IncomeCategory(7, "Other", "more_horiz", 0xFF607D8BL),
        )
    }
}
