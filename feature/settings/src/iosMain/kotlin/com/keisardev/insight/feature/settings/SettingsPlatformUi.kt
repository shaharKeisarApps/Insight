package com.keisardev.insight.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.keisardev.insight.core.model.CloudModelOption
import com.keisardev.insight.core.model.ModelState
import com.keisardev.insight.core.ui.component.CloudSetupBottomSheet
import com.keisardev.insight.core.ui.component.ModelSetupBanner
import com.keisardev.insight.core.ui.component.ModelSetupBottomSheet
import com.keisardev.insight.feature.settings.generated.resources.Res
import com.keisardev.insight.feature.settings.generated.resources.settings_ai_auto
import com.keisardev.insight.feature.settings.generated.resources.settings_ai_auto_cloud_active
import com.keisardev.insight.feature.settings.generated.resources.settings_ai_auto_local_active
import com.keisardev.insight.feature.settings.generated.resources.settings_ai_auto_ready
import com.keisardev.insight.feature.settings.generated.resources.settings_ai_cloud
import com.keisardev.insight.feature.settings.generated.resources.settings_ai_cloud_setup
import com.keisardev.insight.feature.settings.generated.resources.settings_ai_cloud_using
import com.keisardev.insight.feature.settings.generated.resources.settings_ai_cloud_using_dev
import com.keisardev.insight.feature.settings.generated.resources.settings_ai_download_failed
import com.keisardev.insight.feature.settings.generated.resources.settings_ai_downloading
import com.keisardev.insight.feature.settings.generated.resources.settings_ai_engine
import com.keisardev.insight.feature.settings.generated.resources.settings_ai_local_available
import com.keisardev.insight.feature.settings.generated.resources.settings_ai_local_ready
import com.keisardev.insight.feature.settings.generated.resources.settings_ai_no_backend
import com.keisardev.insight.feature.settings.generated.resources.settings_ai_no_local
import com.keisardev.insight.feature.settings.generated.resources.settings_ai_on_device
import com.keisardev.insight.feature.settings.generated.resources.settings_configure_cloud
import com.keisardev.insight.feature.settings.generated.resources.settings_manage_model
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun AiSettingsSection(
    state: SettingsScreen.State,
    modifier: Modifier,
) {
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
        modifier = modifier,
    )

    // Model setup banner — show when no local model and not in cloud-only mode
    if (state.modelState is ModelState.NotInstalled && state.aiMode != AiMode.CLOUD) {
        Spacer(modifier = Modifier.height(12.dp))
        ModelSetupBanner(
            onClick = { state.eventSink(SettingsScreen.Event.OnShowModelSetup) },
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
                    text = stringResource(Res.string.settings_ai_engine),
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
                            text = stringResource(when (mode) {
                                AiMode.LOCAL -> Res.string.settings_ai_on_device
                                AiMode.CLOUD -> Res.string.settings_ai_cloud
                                AiMode.AUTO -> Res.string.settings_ai_auto
                            }),
                        )
                    }
                }
            }

            // Inline progress indicator for download progress
            if (modelState is ModelState.Downloading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = stringResource(Res.string.settings_ai_downloading, (modelState.progress * 100).toInt()),
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
                    stringResource(Res.string.settings_ai_cloud_using_dev, providerDisplay, modelDisplay)
                } else {
                    stringResource(Res.string.settings_ai_cloud_using, providerDisplay, modelDisplay)
                }
            } else {
                stringResource(Res.string.settings_ai_cloud_setup)
            }
            val statusText = when (modelState) {
                is ModelState.Downloading -> null // Already shown above
                is ModelState.Ready -> when (aiMode) {
                    AiMode.LOCAL -> stringResource(Res.string.settings_ai_local_ready, modelState.modelName)
                    AiMode.CLOUD -> cloudStatusText
                    AiMode.AUTO -> stringResource(Res.string.settings_ai_auto_ready)
                }
                is ModelState.Error -> stringResource(Res.string.settings_ai_download_failed, modelState.message)
                else -> when (aiMode) {
                    AiMode.LOCAL -> if (isLocalAvailable) {
                        stringResource(Res.string.settings_ai_local_available)
                    } else {
                        stringResource(Res.string.settings_ai_no_local)
                    }
                    AiMode.CLOUD -> cloudStatusText
                    AiMode.AUTO -> when {
                        isLocalAvailable -> stringResource(Res.string.settings_ai_auto_local_active)
                        isCloudAvailable -> stringResource(Res.string.settings_ai_auto_cloud_active)
                        else -> stringResource(Res.string.settings_ai_no_backend)
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
                    Text(stringResource(Res.string.settings_manage_model))
                }
            }

            // Configure Cloud button when mode is CLOUD or AUTO
            if (aiMode == AiMode.CLOUD || aiMode == AiMode.AUTO) {
                TextButton(onClick = onConfigureCloud) {
                    Text(stringResource(Res.string.settings_configure_cloud))
                }
            }
        }
    }
}
