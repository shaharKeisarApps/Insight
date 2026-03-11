package com.keisardev.insight.feature.settings

import androidx.compose.runtime.Composable
import com.keisardev.insight.core.common.di.AppScope
import com.keisardev.insight.core.model.ModelState
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.presenter.Presenter
import dev.zacsweers.metro.Inject

@CircuitInject(SettingsScreen::class, AppScope::class)
@Inject
class SettingsPresenter : Presenter<SettingsScreen.State> {

    @Composable
    override fun present(): SettingsScreen.State {
        return SettingsScreen.State(
            showClearDataConfirmation = false,
            aiMode = AiMode.AUTO,
            isLocalModelAvailable = false,
            isCloudAvailable = false,
            modelState = ModelState.NotInstalled,
            showModelSetup = false,
            availableModels = emptyList(),
            searchResults = emptyList(),
            isSearching = false,
            searchQuery = "",
            showModelSelection = false,
            currencyCode = "USD",
            showCurrencyPicker = false,
            categoryCount = 0,
            showCloudSetup = false,
            cloudProvider = "OPENAI",
            cloudApiKey = "",
            cloudModelId = "",
            cloudModels = emptyList(),
            isDevKeyAvailable = false,
            useDevKey = false,
            getModelsForProvider = { emptyList() },
            eventSink = {},
        )
    }
}
