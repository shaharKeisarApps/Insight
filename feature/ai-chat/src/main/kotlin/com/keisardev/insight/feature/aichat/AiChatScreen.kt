package com.keisardev.insight.feature.aichat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.keisardev.insight.core.ai.service.AiService
import com.keisardev.insight.core.common.di.AppScope
import com.keisardev.insight.core.designsystem.theme.MetroDITestTheme
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.screen.Screen
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.parcelize.Parcelize

@Parcelize
data object AiChatScreen : Screen {
    data class State(
        val messages: List<ChatMessage>,
        val inputText: String,
        val isLoading: Boolean,
        val isAiEnabled: Boolean,
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data class OnInputChange(val text: String) : Event
        data object OnSend : Event
    }
}

data class ChatMessage(
    val id: String,
    val content: String,
    val isUser: Boolean,
    val timestamp: Instant = Clock.System.now(),
)

class AiChatPresenter @AssistedInject constructor(
    @Assisted private val navigator: Navigator,
    private val aiService: AiService,
) : Presenter<AiChatScreen.State> {

    @Composable
    override fun present(): AiChatScreen.State {
        var messages by rememberRetained { mutableStateOf(emptyList<ChatMessage>()) }
        var inputText by rememberRetained { mutableStateOf("") }
        var isLoading by rememberRetained { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        // Add welcome message on first load
        LaunchedEffect(Unit) {
            if (messages.isEmpty() && aiService.isEnabled) {
                messages = listOf(
                    ChatMessage(
                        id = "welcome",
                        content = "Hi! I'm your financial assistant. Ask me anything about your expenses, like:\n\n" +
                            "- \"How much did I spend this month?\"\n" +
                            "- \"What's my biggest expense category?\"\n" +
                            "- \"Show me my recent expenses\"",
                        isUser = false,
                    )
                )
            }
        }

        return AiChatScreen.State(
            messages = messages,
            inputText = inputText,
            isLoading = isLoading,
            isAiEnabled = aiService.isEnabled,
        ) { event ->
            when (event) {
                is AiChatScreen.Event.OnInputChange -> inputText = event.text
                AiChatScreen.Event.OnSend -> {
                    if (inputText.isNotBlank() && !isLoading) {
                        val userMessage = ChatMessage(
                            id = "user_${Clock.System.now().toEpochMilliseconds()}",
                            content = inputText.trim(),
                            isUser = true,
                        )
                        messages = messages + userMessage
                        val query = inputText.trim()
                        inputText = ""
                        isLoading = true

                        scope.launch {
                            val response = aiService.chat(query)
                            val aiMessage = ChatMessage(
                                id = "ai_${Clock.System.now().toEpochMilliseconds()}",
                                content = response,
                                isUser = false,
                            )
                            messages = messages + aiMessage
                            isLoading = false
                        }
                    }
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

@CircuitInject(AiChatScreen::class, AppScope::class)
@Composable
fun AiChatUi(state: AiChatScreen.State, modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier) { paddingValues ->
        if (!state.isAiEnabled) {
            AiDisabledContent(modifier = Modifier.padding(paddingValues))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .imePadding(),
            ) {
                ChatMessagesList(
                    messages = state.messages,
                    isLoading = state.isLoading,
                    modifier = Modifier.weight(1f),
                )

                ChatInput(
                    inputText = state.inputText,
                    isLoading = state.isLoading,
                    onInputChange = { state.eventSink(AiChatScreen.Event.OnInputChange(it)) },
                    onSend = { state.eventSink(AiChatScreen.Event.OnSend) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
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
                    text = "AI Features Disabled",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = "To enable AI features, add your OpenAI API key to local.properties:\n\nOPENAI_API_KEY=sk-your-key-here",
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

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(messages, key = { it.id }) { message ->
            ChatMessageItem(message = message)
        }

        if (isLoading) {
            item {
                LoadingIndicator()
            }
        }
    }
}

@Composable
private fun ChatMessageItem(
    message: ChatMessage,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
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
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isUser) 16.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 16.dp,
            ),
            color = if (message.isUser) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (message.isUser) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun LoadingIndicator(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
                Text(
                    text = "Thinking...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
    Surface(
        modifier = modifier,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask about your expenses...") },
                maxLines = 3,
                enabled = !isLoading,
                shape = RoundedCornerShape(24.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onSend,
                enabled = inputText.isNotBlank() && !isLoading,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (inputText.isNotBlank() && !isLoading) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

// Preview functions
@Preview(showBackground = true)
@Composable
private fun PreviewAiChatUi() {
    MetroDITestTheme {
        AiChatUi(
            state = AiChatScreen.State(
                messages = listOf(
                    ChatMessage(
                        id = "1",
                        content = "How much did I spend this month?",
                        isUser = true,
                    ),
                    ChatMessage(
                        id = "2",
                        content = "Based on your expenses, you've spent $1,234.56 this month. Your biggest category is Food at $450.00 (36%).",
                        isUser = false,
                    ),
                ),
                inputText = "",
                isLoading = false,
                isAiEnabled = true,
                eventSink = {},
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewAiChatUiDisabled() {
    MetroDITestTheme {
        AiChatUi(
            state = AiChatScreen.State(
                messages = emptyList(),
                inputText = "",
                isLoading = false,
                isAiEnabled = false,
                eventSink = {},
            )
        )
    }
}
