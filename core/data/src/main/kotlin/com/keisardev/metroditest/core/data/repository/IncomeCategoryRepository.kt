package com.keisardev.metroditest.core.data.repository

import com.keisardev.metroditest.core.model.IncomeCategory
import kotlinx.coroutines.flow.Flow

interface IncomeCategoryRepository {
    fun observeAllCategories(): Flow<List<IncomeCategory>>

    suspend fun getCategoryById(id: Long): IncomeCategory?

    suspend fun seedDefaultCategories()

    suspend fun getCategoryCount(): Long
}
