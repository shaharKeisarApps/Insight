package com.keisardev.insight.feature.aichat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.keisardev.insight.core.ai.model.ModelDownloadTrigger
import com.keisardev.insight.core.ai.model.ModelRepository
import com.keisardev.insight.core.ai.repository.ChatRepository
import com.keisardev.insight.core.common.di.AppScope
import com.keisardev.insight.core.model.ModelState
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.collectAsRetainedState
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlin.AutoCloseable
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@AssistedInject
class AiChatPresenter(
    @Assisted private val navigator: Navigator,
    private val chatRepository: ChatRepository,
    private val modelRepository: ModelRepository,
    private val modelDownloadTrigger: ModelDownloadTrigger,
) : Presenter<AiChatScreen.State> {

    @Composable
    override fun present(): AiChatScreen.State {
        val messages by chatRepository.observeMessages()
            .collectAsRetainedState(initial = emptyList())

        val isLoading by chatRepository.isLoading
            .collectAsRetainedState(initial = false)

        val modelState by modelRepository.modelState
            .collectAsRetainedState(initial = ModelState.NotInstalled)

        val searchResults by modelRepository.searchResults
            .collectAsRetainedState(initial = emptyList())

        val isSearching by modelRepository.isSearching
            .collectAsRetainedState(initial = false)

        var inputText by rememberRetained { mutableStateOf("") }
        var dismissedSetup by rememberRetained { mutableStateOf(false) }
        var showModelSelection by rememberRetained { mutableStateOf(false) }
        var searchQuery by rememberRetained { mutableStateOf("") }
        var welcomeShown by rememberRetained { mutableStateOf(false) }
        val scope = rememberRetained { RetainedCoroutineScope() }

        LaunchedEffect(Unit) {
            if (!welcomeShown && messages.isEmpty() && chatRepository.isEnabled) {
                welcomeShown = true
                chatRepository.clearHistory(addWelcomeMessage = true)
            }
        }

        val showModelSetup = !dismissedSetup &&
            !chatRepository.isEnabled &&
            modelState is ModelState.NotInstalled

        return AiChatScreen.State(
            messages = messages,
            inputText = inputText,
            isLoading = isLoading,
            isAiEnabled = chatRepository.isEnabled,
            showModelSetup = showModelSetup,
            modelState = modelState,
            availableModels = modelRepository.availableModels,
            searchResults = searchResults,
            isSearching = isSearching,
            searchQuery = searchQuery,
            showModelSelection = showModelSelection,
        ) { event ->
            when (event) {
                is AiChatScreen.Event.OnInputChange -> inputText = event.text
                AiChatScreen.Event.OnSend -> {
                    if (inputText.isNotBlank() && !isLoading) {
                        val messageToSend = inputText.trim()
                        inputText = ""
                        scope.launch {
                            chatRepository.sendMessageStream(messageToSend).collect {}
                        }
                    }
                }
                is AiChatScreen.Event.OnDownloadModel -> {
                    modelDownloadTrigger.startDownloadService()
                    showModelSelection = false
                    scope.launch {
                        modelRepository.startDownload(event.model)
                    }
                }
                AiChatScreen.Event.OnCancelDownload -> {
                    modelRepository.cancelDownload()
                    modelDownloadTrigger.stopDownloadService()
                }
                AiChatScreen.Event.OnDismissModelSetup -> {
                    dismissedSetup = true
                    showModelSelection = false
                    searchQuery = ""
                }
                is AiChatScreen.Event.OnSearchQueryChange -> {
                    searchQuery = event.query
                }
                AiChatScreen.Event.OnSearch -> {
                    scope.launch {
                        modelRepository.searchModels(searchQuery)
                    }
                }
                is AiChatScreen.Event.OnDeleteModel -> {
                    scope.launch {
                        modelRepository.deleteModel(event.fileName)
                    }
                }
                is AiChatScreen.Event.OnSelectActiveModel -> {
                    scope.launch {
                        modelRepository.setActiveModel(event.fileName)
                    }
                }
                AiChatScreen.Event.OnChangeModel -> {
                    showModelSelection = true
                }
            }
        }
    }

    @CircuitInject(AiChatScreen::class, AppScope::class)
    @AssistedFactory
    fun interface Factory {
        fun create(navigator: Navigator): AiChatPresenter
    }
}

/**
 * A [CoroutineScope] that survives Circuit's composition lifecycle via [rememberRetained].
 */
private class RetainedCoroutineScope(
    override val coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Main.immediate,
) : CoroutineScope, AutoCloseable {
    override fun close() {
        coroutineContext.cancel()
    }
}
