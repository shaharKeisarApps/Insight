package com.keisardev.insight.feature.aichat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import org.jetbrains.compose.resources.stringResource
import com.keisardev.insight.feature.aichat.generated.resources.Res
import com.keisardev.insight.feature.aichat.generated.resources.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.keisardev.insight.core.common.di.AppScope
import com.keisardev.insight.core.designsystem.theme.InsightTheme
import com.keisardev.insight.core.model.ChatMessage
import com.keisardev.insight.core.model.ChatRole
import com.keisardev.insight.core.model.ModelInfo
import com.keisardev.insight.core.model.ModelState
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.keisardev.insight.core.common.parcelize.Parcelize
import com.keisardev.insight.core.common.parcelize.Parcelable

@Parcelize
data object AiChatScreen : Screen, Parcelable {
    data class State(
        val messages: List<ChatMessage>,
        val inputText: String,
        val isLoading: Boolean,
        val isAiEnabled: Boolean,
        val showModelSetup: Boolean,
        val modelState: ModelState,
        val availableModels: List<ModelInfo>,
        val searchResults: List<ModelInfo>,
        val isSearching: Boolean,
        val searchQuery: String,
        val showModelSelection: Boolean,
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data class OnInputChange(val text: String) : Event
        data object OnSend : Event
        data class OnDownloadModel(val model: ModelInfo) : Event
        data object OnCancelDownload : Event
        data object OnDismissModelSetup : Event
        data class OnSearchQueryChange(val query: String) : Event
        data object OnSearch : Event
        data class OnDeleteModel(val fileName: String) : Event
        data object OnChangeModel : Event
        data class OnSelectActiveModel(val fileName: String) : Event
    }
}

/**
 * Platform-specific composable for showing the model setup bottom sheet.
 * On Android, this renders [ModelSetupBottomSheet] from core/ui.
 * On iOS, this is a no-op since on-device AI is not available.
 */
@Composable
expect fun AiChatModelSetupSection(state: AiChatScreen.State)

@CircuitInject(AiChatScreen::class, AppScope::class)
@Composable
fun AiChatUi(state: AiChatScreen.State, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val navBarBottom = WindowInsets.navigationBars.getBottom(density)
    val navSuiteBarPx = with(density) { 80.dp.roundToPx() }
    val effectiveImePadding = with(density) {
        (imeBottom - navBarBottom - navSuiteBarPx).coerceAtLeast(0).toDp()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (state.isAiEnabled) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = effectiveImePadding),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 0.dp,
                    tonalElevation = 0.dp,
                ) {
                    ChatInput(
                        inputText = state.inputText,
                        isLoading = state.isLoading,
                        onInputChange = { state.eventSink(AiChatScreen.Event.OnInputChange(it)) },
                        onSend = { state.eventSink(AiChatScreen.Event.OnSend) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    ) { paddingValues ->
        if (!state.isAiEnabled) {
            AiDisabledContent(modifier = Modifier.padding(paddingValues))
        } else {
            ChatMessagesList(
                messages = state.messages,
                isLoading = state.isLoading,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            )
        }
    }

    AiChatModelSetupSection(state = state)
}

@Composable
private fun AiDisabledContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.padding(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = stringResource(Res.string.ai_chat_disabled_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = stringResource(Res.string.ai_chat_disabled_message),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun ChatMessagesList(
    messages: List<ChatMessage>,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Scroll when new messages arrive or when streaming content grows
    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Typing dots show while waiting for first token (placeholder exists but is empty)
    val isWaitingForFirstToken = isLoading &&
        (messages.lastOrNull()?.role != ChatRole.ASSISTANT ||
            messages.lastOrNull()?.content.isNullOrEmpty())

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(messages.size, key = { messages[it].id }) { index ->
            val message = messages[index]
            // Hide empty placeholder — typing dots replace it visually
            val isEmptyPlaceholder = isLoading &&
                index == messages.lastIndex &&
                !message.isUser &&
                message.content.isEmpty()
            if (isEmptyPlaceholder) return@items

            val isStreaming = isLoading && index == messages.lastIndex && !message.isUser
            AnimatedVisibility(
                visible = true,
                enter = if (message.isUser) {
                    fadeIn(animationSpec = tween(200)) +
                    slideInVertically(
                        initialOffsetY = { fullHeight -> fullHeight },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ) +
                    scaleIn(
                        initialScale = 0.8f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                } else {
                    fadeIn(animationSpec = tween(300)) +
                    slideInVertically(
                        initialOffsetY = { fullHeight -> -fullHeight / 3 },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                },
                exit = fadeOut(animationSpec = tween(150)) + shrinkVertically()
            ) {
                ChatMessageItem(message = message, isStreaming = isStreaming)
            }
        }

        // Show typing dots while waiting for first token
        if (isWaitingForFirstToken) {
            item {
                TypingIndicator()
            }
        }
    }
}

@Composable
private fun ChatMessageItem(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start,
    ) {
        Row(
            horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
        ) {
            if (!message.isUser) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Surface(
                shape = RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomStart = if (message.isUser) 20.dp else 4.dp,
                    bottomEnd = if (message.isUser) 4.dp else 20.dp,
                ),
                color = if (message.isUser) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
                tonalElevation = if (message.isUser) 0.dp else 1.dp,
                shadowElevation = if (message.isUser) 2.dp else 0.dp,
                modifier = Modifier.widthIn(max = 280.dp),
            ) {
                if (isStreaming) {
                    // Blinking cursor while streaming
                    val cursorAlpha by rememberInfiniteTransition(
                        label = "cursor_blink"
                    ).animateFloat(
                        initialValue = 1f,
                        targetValue = 0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(530, easing = EaseInOutCubic),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "cursor_alpha",
                    )
                    Text(
                        text = buildAnnotatedString {
                            append(message.content)
                            withStyle(
                                style = SpanStyle(
                                    color = MaterialTheme.colorScheme.primary.copy(
                                        alpha = cursorAlpha,
                                    ),
                                ),
                            ) {
                                append("▌")
                            }
                        },
                        modifier = Modifier.padding(
                            horizontal = 16.dp,
                            vertical = 12.dp,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                } else {
                    Text(
                        text = message.content,
                        modifier = Modifier.padding(
                            horizontal = 16.dp,
                            vertical = 12.dp,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (message.isUser) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }

        Text(
            text = formatMessageTime(message.timestamp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(
                start = if (message.isUser) 0.dp else 40.dp,
                top = 4.dp
            )
        )
    }
}

@Composable
private fun formatMessageTime(timestamp: kotlinx.datetime.Instant): String {
    val now = Clock.System.now()
    val duration: kotlin.time.Duration = now - timestamp

    return when {
        duration.inWholeSeconds < 60 -> stringResource(Res.string.ai_chat_just_now)
        duration.inWholeMinutes < 60 -> stringResource(Res.string.ai_chat_minutes_ago, duration.inWholeMinutes)
        duration.inWholeHours < 24 -> stringResource(Res.string.ai_chat_hours_ago, duration.inWholeHours)
        else -> {
            val date = timestamp.toLocalDateTime(TimeZone.currentSystemDefault())
            "${date.hour.toString().padStart(2, '0')}:${date.minute.toString().padStart(2, '0')}"
        }
    }
}

@Composable
private fun TypingIndicator(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // AI avatar with subtle pulse animation (M3 Expressive)
        val avatarTransition = rememberInfiniteTransition(label = "avatar_pulse")
        val avatarScale by avatarTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = EaseInOutCubic),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "avatar_scale",
        )
        Box(
            modifier = Modifier
                .size(32.dp)
                .scale(avatarScale)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Psychology,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = 4.dp,
                bottomEnd = 20.dp,
            ),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 1.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val dotColor = MaterialTheme.colorScheme.primary
                repeat(3) { index ->
                    val infiniteTransition = rememberInfiniteTransition(
                        label = "typing_dot_$index",
                    )
                    val offsetY by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = -6f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(
                                durationMillis = 500,
                                easing = EaseInOutCubic,
                            ),
                            repeatMode = RepeatMode.Reverse,
                            initialStartOffset = StartOffset(index * 160),
                        ),
                        label = "dot_offset_$index",
                    )
                    val dotScale by infiniteTransition.animateFloat(
                        initialValue = 0.7f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(
                                durationMillis = 500,
                                easing = EaseInOutCubic,
                            ),
                            repeatMode = RepeatMode.Reverse,
                            initialStartOffset = StartOffset(index * 160),
                        ),
                        label = "dot_scale_$index",
                    )
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .offset(y = offsetY.dp)
                            .scale(dotScale)
                            .clip(CircleShape)
                            .background(dotColor),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatInput(
    inputText: String,
    isLoading: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEnabled = inputText.isNotBlank() && !isLoading
    val scale by animateFloatAsState(
        targetValue = if (isEnabled) 1f else 0.9f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "send_button_scale"
    )
    val iconColor by animateColorAsState(
        targetValue = if (isEnabled) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        },
        animationSpec = tween(300),
        label = "send_button_color"
    )

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f).testTag("input_chat"),
                placeholder = {
                    Text(
                        stringResource(Res.string.ai_chat_input_placeholder),
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge,
                maxLines = 4,
                enabled = !isLoading,
                shape = RoundedCornerShape(24.dp),
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onSend,
                enabled = isEnabled,
                modifier = Modifier
                    .testTag("btn_send")
                    .scale(scale)
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(Res.string.ai_chat_send),
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// Preview functions
@Preview
@Composable
private fun PreviewAiChatUi() {
    InsightTheme {
        AiChatUi(
            state = AiChatScreen.State(
                messages = listOf(
                    ChatMessage(
                        id = "1",
                        content = "How much did I spend this month?",
                        role = ChatRole.USER,
                    ),
                    ChatMessage(
                        id = "2",
                        content = "Based on your expenses, you've spent \$1,234.56 this month. Your biggest category is Food at \$450.00 (36%).",
                        role = ChatRole.ASSISTANT,
                    ),
                ),
                inputText = "",
                isLoading = false,
                isAiEnabled = true,
                showModelSetup = false,
                modelState = ModelState.NotInstalled,
                availableModels = emptyList(),
                searchResults = emptyList(),
                isSearching = false,
                searchQuery = "",
                showModelSelection = false,
                eventSink = {},
            )
        )
    }
}

@Preview
@Composable
private fun PreviewAiChatUiDisabled() {
    InsightTheme {
        AiChatUi(
            state = AiChatScreen.State(
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
        )
    }
}
