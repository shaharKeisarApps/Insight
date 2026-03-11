package com.keisardev.insight.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.keisardev.insight.core.ai.model.ModelDownloadTrigger
import com.keisardev.insight.core.ai.model.ModelRepository
import com.keisardev.insight.core.ai.service.AiServiceStrategy
import com.keisardev.insight.core.common.di.AppScope
import com.keisardev.insight.core.data.datastore.UserSettings
import com.keisardev.insight.core.data.datastore.UserSettingsRepository
import com.keisardev.insight.core.data.repository.CategoryRepository
import com.keisardev.insight.core.data.repository.ExpenseRepository
import com.keisardev.insight.core.data.repository.IncomeCategoryRepository
import com.keisardev.insight.core.data.repository.IncomeRepository
import com.keisardev.insight.core.model.ModelState
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.collectAsRetainedState
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.presenter.Presenter
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.launch
import com.keisardev.insight.core.ai.service.AiMode as CoreAiMode

@CircuitInject(SettingsScreen::class, AppScope::class)
@Inject
class SettingsPresenter(
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val categoryRepository: CategoryRepository,
    private val incomeCategoryRepository: IncomeCategoryRepository,
    private val aiServiceStrategy: AiServiceStrategy,
    private val modelRepository: ModelRepository,
    private val modelDownloadTrigger: ModelDownloadTrigger,
    private val userSettingsRepository: UserSettingsRepository,
) : Presenter<SettingsScreen.State> {

    @Composable
    override fun present(): SettingsScreen.State {
        var showConfirmation by rememberRetained { mutableStateOf(false) }
        var showModelSetup by rememberRetained { mutableStateOf(false) }
        var showModelSelection by rememberRetained { mutableStateOf(false) }
        var showCurrencyPicker by rememberRetained { mutableStateOf(false) }
        var showCloudSetup by rememberRetained { mutableStateOf(false) }
        var searchQuery by rememberRetained { mutableStateOf("") }
        val coreAiMode by aiServiceStrategy.observeAiMode()
            .collectAsRetainedState(initial = aiServiceStrategy.mode)
        val modelState by modelRepository.modelState
            .collectAsRetainedState(initial = ModelState.NotInstalled)
        val searchResults by modelRepository.searchResults
            .collectAsRetainedState(initial = emptyList())
        val isSearching by modelRepository.isSearching
            .collectAsRetainedState(initial = false)
        val settings by userSettingsRepository.observeSettings()
            .collectAsRetainedState(initial = UserSettings())
        val expenseCategories by categoryRepository.observeAllCategories()
            .collectAsRetainedState(initial = emptyList())
        val incomeCategories by incomeCategoryRepository.observeAllCategories()
            .collectAsRetainedState(initial = emptyList())
        val scope = rememberCoroutineScope()

        val cloudProvider = settings.cloudSettings.provider.name
        val cloudModels = aiServiceStrategy.getAvailableCloudModels(cloudProvider)

        return SettingsScreen.State(
            showClearDataConfirmation = showConfirmation,
            aiMode = coreAiMode.toFeatureAiMode(),
            isLocalModelAvailable = aiServiceStrategy.isLocalAvailable,
            isCloudAvailable = aiServiceStrategy.isCloudAvailable,
            modelState = modelState,
            showModelSetup = showModelSetup,
            availableModels = modelRepository.availableModels,
            searchResults = searchResults,
            isSearching = isSearching,
            searchQuery = searchQuery,
            showModelSelection = showModelSelection,
            currencyCode = settings.currencyCode,
            showCurrencyPicker = showCurrencyPicker,
            categoryCount = expenseCategories.size + incomeCategories.size,
            showCloudSetup = showCloudSetup,
            cloudProvider = cloudProvider,
            cloudApiKey = settings.cloudSettings.apiKey,
            cloudModelId = settings.cloudSettings.selectedModelId,
            cloudModels = cloudModels,
            isDevKeyAvailable = aiServiceStrategy.hasDevKey,
            useDevKey = settings.cloudSettings.useDevKey,
            getModelsForProvider = { aiServiceStrategy.getAvailableCloudModels(it) },
        ) { event ->
            when (event) {
                SettingsScreen.Event.OnClearDataClick -> {
                    showConfirmation = true
                }
                SettingsScreen.Event.OnClearDataConfirm -> {
                    scope.launch {
                        expenseRepository.deleteAllExpenses()
                        incomeRepository.deleteAllIncome()
                        showConfirmation = false
                    }
                }
                SettingsScreen.Event.OnClearDataDismiss -> {
                    showConfirmation = false
                }
                is SettingsScreen.Event.OnAiModeChange -> {
                    scope.launch {
                        aiServiceStrategy.setMode(event.mode.toCoreAiMode())
                    }
                }
                SettingsScreen.Event.OnShowModelSetup -> {
                    showModelSetup = true
                }
                SettingsScreen.Event.OnDismissModelSetup -> {
                    showModelSetup = false
                    showModelSelection = false
                    searchQuery = ""
                }
                is SettingsScreen.Event.OnDownloadModel -> {
                    modelDownloadTrigger.startDownloadService()
                    showModelSelection = false
                    scope.launch {
                        modelRepository.startDownload(event.model)
                    }
                }
                SettingsScreen.Event.OnCancelDownload -> {
                    modelRepository.cancelDownload()
                    modelDownloadTrigger.stopDownloadService()
                }
                is SettingsScreen.Event.OnSearchQueryChange -> {
                    searchQuery = event.query
                }
                SettingsScreen.Event.OnSearch -> {
                    scope.launch {
                        modelRepository.searchModels(searchQuery)
                    }
                }
                is SettingsScreen.Event.OnDeleteModel -> {
                    scope.launch {
                        modelRepository.deleteModel(event.fileName)
                    }
                }
                is SettingsScreen.Event.OnSelectActiveModel -> {
                    scope.launch {
                        modelRepository.setActiveModel(event.fileName)
                    }
                }
                SettingsScreen.Event.OnChangeModel -> {
                    showModelSelection = true
                }
                SettingsScreen.Event.OnCurrencyClick -> {
                    showCurrencyPicker = true
                }
                is SettingsScreen.Event.OnCurrencySelect -> {
                    scope.launch {
                        userSettingsRepository.updateCurrency(event.currencyCode)
                    }
                    showCurrencyPicker = false
                }
                SettingsScreen.Event.OnDismissCurrencyPicker -> {
                    showCurrencyPicker = false
                }
                SettingsScreen.Event.OnShowCloudSetup -> {
                    showCloudSetup = true
                }
                SettingsScreen.Event.OnDismissCloudSetup -> {
                    showCloudSetup = false
                }
                is SettingsScreen.Event.OnSaveCloudSettings -> {
                    scope.launch {
                        val providerProto = when (event.provider) {
                            "GEMINI" -> UserSettings.CloudProviderProto.GEMINI
                            else -> UserSettings.CloudProviderProto.OPENAI
                        }
                        userSettingsRepository.updateCloudSettings(
                            UserSettings.CloudSettings(
                                provider = providerProto,
                                apiKey = event.apiKey,
                                selectedModelId = event.modelId,
                                useDevKey = settings.cloudSettings.useDevKey,
                            ),
                        )
                        aiServiceStrategy.refreshCloudConfig()
                    }
                    showCloudSetup = false
                }
                SettingsScreen.Event.OnActivateDevKey -> {
                    scope.launch {
                        val newValue = !settings.cloudSettings.useDevKey
                        userSettingsRepository.updateUseDevKey(newValue)
                        aiServiceStrategy.refreshCloudConfig()
                    }
                }
            }
        }
    }
}

private fun CoreAiMode.toFeatureAiMode(): AiMode = when (this) {
    CoreAiMode.LOCAL -> AiMode.LOCAL
    CoreAiMode.CLOUD -> AiMode.CLOUD
    CoreAiMode.AUTO -> AiMode.AUTO
}

private fun AiMode.toCoreAiMode(): CoreAiMode = when (this) {
    AiMode.LOCAL -> CoreAiMode.LOCAL
    AiMode.CLOUD -> CoreAiMode.CLOUD
    AiMode.AUTO -> CoreAiMode.AUTO
}
