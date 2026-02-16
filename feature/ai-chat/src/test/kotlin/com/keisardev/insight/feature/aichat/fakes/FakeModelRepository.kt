package com.keisardev.insight.feature.aichat.fakes

import com.keisardev.insight.core.ai.model.ModelRepository
import com.keisardev.insight.core.model.ModelInfo
import com.keisardev.insight.core.model.ModelState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeModelRepository : ModelRepository {

    private val _modelState = MutableStateFlow<ModelState>(ModelState.NotInstalled)
    override val modelState: StateFlow<ModelState> = _modelState

    override val availableModels: List<ModelInfo> = emptyList()

    private val _searchResults = MutableStateFlow<List<ModelInfo>>(emptyList())
    override val searchResults: StateFlow<List<ModelInfo>> = _searchResults

    private val _isSearching = MutableStateFlow(false)
    override val isSearching: StateFlow<Boolean> = _isSearching

    var startDownloadCallCount = 0
        private set
    var cancelDownloadCallCount = 0
        private set
    var deleteCallCount = 0
        private set
    var searchCallCount = 0
        private set

    fun setModelState(state: ModelState) {
        _modelState.value = state
    }

    override suspend fun startDownload(model: ModelInfo) {
        startDownloadCallCount++
    }

    override fun cancelDownload() {
        cancelDownloadCallCount++
    }

    override fun refreshModelStatus() {}

    override suspend fun searchModels(query: String) {
        searchCallCount++
    }

    override suspend fun deleteCurrentModel() {
        deleteCallCount++
    }
}
