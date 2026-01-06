package com.keisardev.insight.core.data.repository

import com.keisardev.insight.core.model.Category
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun observeAllCategories(): Flow<List<Category>>

    suspend fun getCategoryById(id: Long): Category?

    suspend fun seedDefaultCategories()

    suspend fun getCategoryCount(): Long
}
