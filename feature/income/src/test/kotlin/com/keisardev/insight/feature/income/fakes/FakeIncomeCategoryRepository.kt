package com.keisardev.insight.feature.income.fakes

import com.keisardev.insight.core.data.repository.IncomeCategoryRepository
import com.keisardev.insight.core.model.IncomeCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeIncomeCategoryRepository : IncomeCategoryRepository {

    private val categories = MutableStateFlow<List<IncomeCategory>>(emptyList())

    fun setCategories(list: List<IncomeCategory>) {
        categories.value = list
    }

    override fun observeAllCategories(): Flow<List<IncomeCategory>> = categories

    override suspend fun getCategoryById(id: Long): IncomeCategory? =
        categories.value.find { it.id == id }

    override suspend fun seedDefaultCategories() {
        // no-op in tests
    }

    override suspend fun getCategoryCount(): Long = categories.value.size.toLong()
}
