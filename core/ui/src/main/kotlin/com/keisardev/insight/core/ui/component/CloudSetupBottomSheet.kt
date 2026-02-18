package com.keisardev.insight.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.keisardev.insight.core.model.CloudModelOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudSetupBottomSheet(
    provider: String,
    apiKey: String,
    selectedModelId: String,
    getModelsForProvider: (String) -> List<CloudModelOption>,
    isDevKeyAvailable: Boolean,
    useDevKey: Boolean,
    onDismiss: () -> Unit,
    onSave: (provider: String, apiKey: String, modelId: String) -> Unit,
    onActivateDevKey: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        // Force LTR inside the bottom sheet so SegmentedButton shapes match
        // button positions. ModalBottomSheet creates a separate popup window
        // that doesn't inherit the LTR override from MainActivity.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            CloudSetupContent(
                initialProvider = provider,
                initialApiKey = apiKey,
                initialModelId = selectedModelId,
                getModelsForProvider = getModelsForProvider,
                isDevKeyAvailable = isDevKeyAvailable,
                useDevKey = useDevKey,
                onSave = onSave,
                onActivateDevKey = onActivateDevKey,
            )
        }
    }
}

@Composable
private fun CloudSetupContent(
    initialProvider: String,
    initialApiKey: String,
    initialModelId: String,
    getModelsForProvider: (String) -> List<CloudModelOption>,
    isDevKeyAvailable: Boolean,
    useDevKey: Boolean,
    onSave: (provider: String, apiKey: String, modelId: String) -> Unit,
    onActivateDevKey: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedProvider by remember { mutableStateOf(initialProvider) }
    var apiKeyText by remember { mutableStateOf(initialApiKey) }
    var selectedModel by remember { mutableStateOf(initialModelId) }
    var passwordVisible by remember { mutableStateOf(false) }

    // Dev key backdoor: tap cloud icon 5x
    var tapCount by remember { mutableIntStateOf(0) }
    var lastTapTime by remember { mutableLongStateOf(0L) }

    val providers = listOf("OPENAI", "GEMINI")
    val currentModels = remember(selectedProvider) { getModelsForProvider(selectedProvider) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Cloud icon with hidden tap counter
        Icon(
            imageVector = Icons.Default.Cloud,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clickable {
                    if (!isDevKeyAvailable) return@clickable
                    val now = System.currentTimeMillis()
                    if (now - lastTapTime > 3000L) {
                        tapCount = 1
                    } else {
                        tapCount++
                    }
                    lastTapTime = now
                    if (tapCount >= 5) {
                        tapCount = 0
                        onActivateDevKey()
                    }
                },
            tint = MaterialTheme.colorScheme.primary,
        )

        Text(
            text = "Configure Cloud AI",
            style = MaterialTheme.typography.headlineSmall,
        )

        Text(
            text = "Enter your API key to use cloud-based AI models",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Dev key banner
        if (useDevKey) {
            AssistChip(
                onClick = {},
                label = { Text("Using developer key") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = null,
                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    leadingIconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ),
            )
        }

        // Provider picker
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth(),
        ) {
            providers.forEachIndexed { index, providerName ->
                SegmentedButton(
                    selected = selectedProvider == providerName,
                    onClick = {
                        selectedProvider = providerName
                        // Reset model selection when switching providers
                        selectedModel = ""
                    },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = providers.size,
                    ),
                ) {
                    Text(
                        text = when (providerName) {
                            "OPENAI" -> "OpenAI"
                            "GEMINI" -> "Gemini"
                            else -> providerName
                        },
                    )
                }
            }
        }

        // API key input
        OutlinedTextField(
            value = apiKeyText,
            onValueChange = { apiKeyText = it },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text(
                    when (selectedProvider) {
                        "OPENAI" -> "OpenAI API Key"
                        "GEMINI" -> "Gemini API Key"
                        else -> "API Key"
                    },
                )
            },
            placeholder = {
                Text(
                    when (selectedProvider) {
                        "OPENAI" -> "sk-..."
                        "GEMINI" -> "AI..."
                        else -> "Enter API key"
                    },
                )
            },
            singleLine = true,
            visualTransformation = if (passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) {
                            Icons.Default.VisibilityOff
                        } else {
                            Icons.Default.Visibility
                        },
                        contentDescription = if (passwordVisible) "Hide key" else "Show key",
                    )
                }
            },
            enabled = !useDevKey,
        )

        // Model selector
        Text(
            text = "Select Model",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.Start),
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f, fill = false),
        ) {
            items(currentModels, key = { it.id }) { model ->
                CloudModelCard(
                    model = model,
                    isSelected = model.id == selectedModel,
                    onSelect = { selectedModel = model.id },
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Save button
        Button(
            onClick = { onSave(selectedProvider, apiKeyText, selectedModel) },
            modifier = Modifier.fillMaxWidth(),
            enabled = useDevKey || apiKeyText.isNotBlank(),
        ) {
            Text("Save")
        }
    }
}

@Composable
private fun CloudModelCard(
    model: CloudModelOption,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onSelect,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onSelect,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    text = model.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
