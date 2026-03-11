package com.keisardev.insight.core.data.repository

import com.keisardev.insight.core.model.IncomeCategory
import kotlinx.coroutines.flow.Flow

interface IncomeCategoryRepository {
    fun observeAllCategories(): Flow<List<IncomeCategory>>

    suspend fun getCategoryById(id: Long): IncomeCategory?

    suspend fun seedDefaultCategories()

    suspend fun getCategoryCount(): Long
}
