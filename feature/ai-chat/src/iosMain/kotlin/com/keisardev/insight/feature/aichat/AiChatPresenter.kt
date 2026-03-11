package com.keisardev.insight.feature.aichat

import androidx.compose.runtime.Composable
import com.keisardev.insight.core.common.di.AppScope
import com.keisardev.insight.core.model.ModelState
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject

@AssistedInject
class AiChatPresenter(
    @Assisted private val navigator: Navigator,
) : Presenter<AiChatScreen.State> {

    @Composable
    override fun present(): AiChatScreen.State {
        return AiChatScreen.State(
            messages = emptyList(),
            inputText = "",
            isLoading = false,
            isAiEnabled = false,
            showModelSetup = false,
            modelState = ModelState.NotInstalled,
            availableModels = emptyList(),
            searchResults = emptyList(),
            isSearching = false,
            searchQuery = "",
            showModelSelection = false,
            eventSink = {},
        )
    }

    @CircuitInject(AiChatScreen::class, AppScope::class)
    @AssistedFactory
    fun interface Factory {
        fun create(navigator: Navigator): AiChatPresenter
    }
}
