package com.keisardev.insight.core.ai.model

import com.keisardev.insight.core.model.ModelInfo
import com.keisardev.insight.core.model.ModelState
import kotlinx.coroutines.flow.StateFlow

interface ModelRepository {
    val modelState: StateFlow<ModelState>
    val availableModels: List<ModelInfo>
    val searchResults: StateFlow<List<ModelInfo>>
    val isSearching: StateFlow<Boolean>
    suspend fun startDownload(model: ModelInfo)
    fun cancelDownload()
    fun refreshModelStatus()
    suspend fun searchModels(query: String)
    suspend fun deleteModel(fileName: String)
    suspend fun setActiveModel(fileName: String)
}
