package com.keisardev.insight.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.keisardev.insight.core.ai.model.ModelDownloadTrigger
import com.keisardev.insight.core.ai.model.ModelRepository
import com.keisardev.insight.core.ai.service.AiMode
import com.keisardev.insight.core.ai.service.AiServiceStrategy
import com.keisardev.insight.core.common.di.AppScope
import com.keisardev.insight.core.data.datastore.UserSettings
import com.keisardev.insight.core.data.datastore.UserSettingsRepository
import com.keisardev.insight.core.data.repository.CategoryRepository
import com.keisardev.insight.core.data.repository.ExpenseRepository
import com.keisardev.insight.core.data.repository.IncomeCategoryRepository
import com.keisardev.insight.core.data.repository.IncomeRepository
import com.keisardev.insight.core.designsystem.theme.InsightTheme
import com.keisardev.insight.core.model.CloudModelOption
import com.keisardev.insight.core.model.ModelInfo
import com.keisardev.insight.core.model.ModelState
import com.keisardev.insight.core.ui.component.CloudSetupBottomSheet
import com.keisardev.insight.core.ui.component.ModelSetupBanner
import com.keisardev.insight.core.ui.component.ModelSetupBottomSheet
import com.keisardev.insight.core.ui.util.AVAILABLE_CURRENCIES
import com.keisardev.insight.core.ui.util.currencyDisplayName
import com.keisardev.insight.core.ui.util.formatBytes
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.collectAsRetainedState
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.screen.Screen
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

@Parcelize
data object SettingsScreen : Screen {
    data class State(
        val showClearDataConfirmation: Boolean,
        val aiMode: AiMode,
        val isLocalModelAvailable: Boolean,
        val isCloudAvailable: Boolean,
        val modelState: ModelState,
        val showModelSetup: Boolean,
        val availableModels: List<ModelInfo>,
        val searchResults: List<ModelInfo>,
        val isSearching: Boolean,
        val searchQuery: String,
        val showModelSelection: Boolean,
        val currencyCode: String,
        val showCurrencyPicker: Boolean,
        val categoryCount: Int,
        val showCloudSetup: Boolean,
        val cloudProvider: String,
        val cloudApiKey: String,
        val cloudModelId: String,
        val cloudModels: List<CloudModelOption>,
        val isDevKeyAvailable: Boolean,
        val useDevKey: Boolean,
        val getModelsForProvider: (String) -> List<CloudModelOption>,
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data object OnClearDataClick : Event
        data object OnClearDataConfirm : Event
        data object OnClearDataDismiss : Event
        data class OnAiModeChange(val mode: AiMode) : Event
        data object OnShowModelSetup : Event
        data object OnDismissModelSetup : Event
        data class OnDownloadModel(val model: ModelInfo) : Event
        data object OnCancelDownload : Event
        data class OnSearchQueryChange(val query: String) : Event
        data object OnSearch : Event
        data class OnDeleteModel(val fileName: String) : Event
        data object OnChangeModel : Event
        data class OnSelectActiveModel(val fileName: String) : Event
        data object OnCurrencyClick : Event
        data class OnCurrencySelect(val currencyCode: String) : Event
        data object OnDismissCurrencyPicker : Event
        data object OnShowCloudSetup : Event
        data object OnDismissCloudSetup : Event
        data class OnSaveCloudSettings(
            val provider: String,
            val apiKey: String,
            val modelId: String,
        ) : Event
        data object OnActivateDevKey : Event
    }
}

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
        val aiMode by aiServiceStrategy.observeAiMode()
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
            aiMode = aiMode,
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
                        aiServiceStrategy.setMode(event.mode)
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

@CircuitInject(SettingsScreen::class, AppScope::class)
@Composable
fun SettingsUi(state: SettingsScreen.State, modifier: Modifier = Modifier) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsItem(
                        icon = Icons.Default.Category,
                        title = "Manage Categories",
                        subtitle = "${state.categoryCount} categories",
                        onClick = {
                            scope.launch {
                                snackbarHostState.showSnackbar("Coming soon")
                            }
                        },
                    )
                    HorizontalDivider()
                    SettingsItem(
                        icon = Icons.Default.AttachMoney,
                        title = "Currency",
                        subtitle = currencyDisplayName(state.currencyCode),
                        onClick = { state.eventSink(SettingsScreen.Event.OnCurrencyClick) },
                    )
                    HorizontalDivider()
                    SettingsItem(
                        icon = Icons.Default.CurrencyExchange,
                        title = "Multi-Currency Tracking",
                        subtitle = "Track items in different currencies \u2014 Coming Soon",
                        onClick = {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    "Multi-currency tracking with conversion is coming in a future update"
                                )
                            }
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            AiEngineCard(
                aiMode = state.aiMode,
                isLocalAvailable = state.isLocalModelAvailable,
                isCloudAvailable = state.isCloudAvailable,
                modelState = state.modelState,
                cloudProvider = state.cloudProvider,
                cloudModelId = state.cloudModelId,
                cloudModels = state.cloudModels,
                useDevKey = state.useDevKey,
                onModeChange = { state.eventSink(SettingsScreen.Event.OnAiModeChange(it)) },
                onManageModel = { state.eventSink(SettingsScreen.Event.OnShowModelSetup) },
                onConfigureCloud = { state.eventSink(SettingsScreen.Event.OnShowCloudSetup) },
            )

            // Model setup banner — show when no local model and not in cloud-only mode
            if (state.modelState is ModelState.NotInstalled && state.aiMode != AiMode.CLOUD) {
                Spacer(modifier = Modifier.height(12.dp))
                ModelSetupBanner(
                    onClick = { state.eventSink(SettingsScreen.Event.OnShowModelSetup) },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                SettingsItem(
                    icon = Icons.Default.Delete,
                    title = "Clear All Data",
                    subtitle = "Delete all expenses and income",
                    onClick = { state.eventSink(SettingsScreen.Event.OnClearDataClick) },
                    isDestructive = true,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "About",
                    subtitle = "Version 0.1.0",
                    onClick = {
                        scope.launch {
                            snackbarHostState.showSnackbar("Insight v0.1.0")
                        }
                    },
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Built with Circuit + Metro DI",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (state.showClearDataConfirmation) {
        AlertDialog(
            onDismissRequest = { state.eventSink(SettingsScreen.Event.OnClearDataDismiss) },
            title = { Text("Clear All Data?") },
            text = { Text("This will delete all your expenses and income. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { state.eventSink(SettingsScreen.Event.OnClearDataConfirm) }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { state.eventSink(SettingsScreen.Event.OnClearDataDismiss) }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Currency picker dialog
    if (state.showCurrencyPicker) {
        AlertDialog(
            onDismissRequest = { state.eventSink(SettingsScreen.Event.OnDismissCurrencyPicker) },
            title = { Text("Select Currency") },
            text = {
                Column {
                    AVAILABLE_CURRENCIES.forEach { code ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    state.eventSink(SettingsScreen.Event.OnCurrencySelect(code))
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = currencyDisplayName(code),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (code == state.currencyCode) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.weight(1f),
                            )
                            if (code == state.currencyCode) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { state.eventSink(SettingsScreen.Event.OnDismissCurrencyPicker) },
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    // Model setup bottom sheet
    if (state.showModelSetup || state.modelState is ModelState.Downloading) {
        ModelSetupBottomSheet(
            modelState = state.modelState,
            availableModels = state.availableModels,
            searchResults = state.searchResults,
            isSearching = state.isSearching,
            searchQuery = state.searchQuery,
            showModelSelection = state.showModelSelection,
            onDismiss = { state.eventSink(SettingsScreen.Event.OnDismissModelSetup) },
            onDownload = { state.eventSink(SettingsScreen.Event.OnDownloadModel(it)) },
            onCancel = { state.eventSink(SettingsScreen.Event.OnCancelDownload) },
            onSearchQueryChange = { state.eventSink(SettingsScreen.Event.OnSearchQueryChange(it)) },
            onSearch = { state.eventSink(SettingsScreen.Event.OnSearch) },
            onDeleteModel = { fileName -> state.eventSink(SettingsScreen.Event.OnDeleteModel(fileName)) },
            onChangeModel = { state.eventSink(SettingsScreen.Event.OnChangeModel) },
            onSelectActiveModel = { fileName -> state.eventSink(SettingsScreen.Event.OnSelectActiveModel(fileName)) },
        )
    }

    // Cloud setup bottom sheet
    if (state.showCloudSetup) {
        CloudSetupBottomSheet(
            provider = state.cloudProvider,
            apiKey = state.cloudApiKey,
            selectedModelId = state.cloudModelId,
            getModelsForProvider = state.getModelsForProvider,
            isDevKeyAvailable = state.isDevKeyAvailable,
            useDevKey = state.useDevKey,
            onDismiss = { state.eventSink(SettingsScreen.Event.OnDismissCloudSetup) },
            onSave = { provider, apiKey, modelId ->
                state.eventSink(SettingsScreen.Event.OnSaveCloudSettings(provider, apiKey, modelId))
            },
            onActivateDevKey = { state.eventSink(SettingsScreen.Event.OnActivateDevKey) },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AiEngineCard(
    aiMode: AiMode,
    isLocalAvailable: Boolean,
    isCloudAvailable: Boolean,
    modelState: ModelState,
    cloudProvider: String,
    cloudModelId: String,
    cloudModels: List<CloudModelOption>,
    useDevKey: Boolean,
    onModeChange: (AiMode) -> Unit,
    onManageModel: () -> Unit,
    onConfigureCloud: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = "AI Engine",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth(),
            ) {
                val options = AiMode.entries
                options.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = aiMode == mode,
                        onClick = { onModeChange(mode) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = options.size,
                        ),
                        icon = {
                            SegmentedButtonDefaults.Icon(active = aiMode == mode) {
                                Icon(
                                    imageVector = when (mode) {
                                        AiMode.LOCAL -> Icons.Default.PhoneAndroid
                                        AiMode.CLOUD -> Icons.Default.Cloud
                                        AiMode.AUTO -> Icons.Default.SmartToy
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                                )
                            }
                        },
                    ) {
                        Text(
                            text = when (mode) {
                                AiMode.LOCAL -> "On-Device"
                                AiMode.CLOUD -> "Cloud"
                                AiMode.AUTO -> "Auto"
                            },
                        )
                    }
                }
            }

            // Inline wave loading indicator for download progress
            if (modelState is ModelState.Downloading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LoadingIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Downloading model... ${(modelState.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Status description
            val cloudStatusText = if (isCloudAvailable) {
                val providerDisplay = when (cloudProvider) {
                    "GEMINI" -> "Gemini"
                    else -> "OpenAI"
                }
                val modelDisplay = cloudModels.find { it.id == cloudModelId }?.displayName
                    ?: "Default"
                if (useDevKey) {
                    "Using $providerDisplay \u2014 $modelDisplay (dev key)"
                } else {
                    "Using $providerDisplay \u2014 $modelDisplay"
                }
            } else {
                "Tap Configure Cloud to set up"
            }
            val statusText = when (modelState) {
                is ModelState.Downloading -> null // Already shown above
                is ModelState.Ready -> when (aiMode) {
                    AiMode.LOCAL -> "On-device model ready \u2014 ${modelState.modelName}"
                    AiMode.CLOUD -> cloudStatusText
                    AiMode.AUTO -> "On-device model ready \u2014 cloud as fallback"
                }
                is ModelState.Error -> "Download failed: ${modelState.message}"
                else -> when (aiMode) {
                    AiMode.LOCAL -> if (isLocalAvailable) {
                        "Using on-device model \u2014 no internet needed"
                    } else {
                        "No local model \u2014 download one from AI Chat"
                    }
                    AiMode.CLOUD -> cloudStatusText
                    AiMode.AUTO -> when {
                        isLocalAvailable -> "On-device model active \u2014 cloud as fallback"
                        isCloudAvailable -> "Cloud API active \u2014 no local model found"
                        else -> "No AI backend available"
                    }
                }
            }

            val isWarning = when {
                modelState is ModelState.Error -> true
                modelState is ModelState.Downloading -> false
                aiMode == AiMode.LOCAL -> !isLocalAvailable
                aiMode == AiMode.CLOUD -> !isCloudAvailable
                aiMode == AiMode.AUTO -> !isLocalAvailable && !isCloudAvailable
                else -> false
            }

            if (statusText != null) {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isWarning) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            // Manage model button when model is ready
            if (modelState is ModelState.Ready) {
                TextButton(onClick = onManageModel) {
                    Text("Manage Model")
                }
            }

            // Configure Cloud button when mode is CLOUD or AUTO
            if (aiMode == AiMode.CLOUD || aiMode == AiMode.AUTO) {
                TextButton(onClick = onConfigureCloud) {
                    Text("Configure Cloud")
                }
            }
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isDestructive) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(24.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isDestructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewSettingsUi() {
    InsightTheme {
        SettingsUi(
            state = SettingsScreen.State(
                showClearDataConfirmation = false,
                aiMode = AiMode.AUTO,
                isLocalModelAvailable = true,
                isCloudAvailable = true,
                modelState = ModelState.Ready(
                    modelName = "SmolLM2 360M",
                    filePath = "",
                    sizeBytes = 387_000_000L,
                ),
                showModelSetup = false,
                availableModels = emptyList(),
                searchResults = emptyList(),
                isSearching = false,
                searchQuery = "",
                showModelSelection = false,
                currencyCode = "USD",
                showCurrencyPicker = false,
                categoryCount = 14,
                showCloudSetup = false,
                cloudProvider = "OPENAI",
                cloudApiKey = "",
                cloudModelId = "gpt-4o-mini",
                cloudModels = listOf(
                    CloudModelOption("gpt-4o-mini", "GPT-4o Mini", "Fast and affordable"),
                ),
                isDevKeyAvailable = false,
                useDevKey = false,
                getModelsForProvider = { emptyList() },
                eventSink = {},
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewSettingsUiWithDialog() {
    InsightTheme {
        SettingsUi(
            state = SettingsScreen.State(
                showClearDataConfirmation = true,
                aiMode = AiMode.CLOUD,
                isLocalModelAvailable = false,
                isCloudAvailable = true,
                modelState = ModelState.NotInstalled,
                showModelSetup = false,
                availableModels = emptyList(),
                searchResults = emptyList(),
                isSearching = false,
                searchQuery = "",
                showModelSelection = false,
                currencyCode = "USD",
                showCurrencyPicker = false,
                categoryCount = 14,
                showCloudSetup = false,
                cloudProvider = "OPENAI",
                cloudApiKey = "sk-test123",
                cloudModelId = "gpt-4o-mini",
                cloudModels = emptyList(),
                isDevKeyAvailable = false,
                useDevKey = false,
                getModelsForProvider = { emptyList() },
                eventSink = {},
            )
        )
    }
}
