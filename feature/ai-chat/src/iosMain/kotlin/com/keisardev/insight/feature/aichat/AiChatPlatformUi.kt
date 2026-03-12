package com.keisardev.insight.feature.aichat

import androidx.compose.runtime.Composable
import com.keisardev.insight.core.model.ModelState
import com.keisardev.insight.core.ui.component.ModelSetupBottomSheet

@Composable
actual fun AiChatModelSetupSection(state: AiChatScreen.State) {
    if (state.showModelSetup || state.modelState is ModelState.Downloading) {
        ModelSetupBottomSheet(
            modelState = state.modelState,
            availableModels = state.availableModels,
            searchResults = state.searchResults,
            isSearching = state.isSearching,
            searchQuery = state.searchQuery,
            showModelSelection = state.showModelSelection,
            onDismiss = { state.eventSink(AiChatScreen.Event.OnDismissModelSetup) },
            onDownload = { state.eventSink(AiChatScreen.Event.OnDownloadModel(it)) },
            onCancel = { state.eventSink(AiChatScreen.Event.OnCancelDownload) },
            onSearchQueryChange = { state.eventSink(AiChatScreen.Event.OnSearchQueryChange(it)) },
            onSearch = { state.eventSink(AiChatScreen.Event.OnSearch) },
            onDeleteModel = { fileName -> state.eventSink(AiChatScreen.Event.OnDeleteModel(fileName)) },
            onChangeModel = { state.eventSink(AiChatScreen.Event.OnChangeModel) },
            onSelectActiveModel = { fileName -> state.eventSink(AiChatScreen.Event.OnSelectActiveModel(fileName)) },
        )
    }
}
