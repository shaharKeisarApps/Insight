package com.keisardev.insight.feature.aichat

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.keisardev.insight.core.designsystem.theme.InsightTheme
import com.keisardev.insight.core.model.ChatMessage
import com.keisardev.insight.core.model.ChatRole
import com.keisardev.insight.core.model.ModelState

// ==================== ENABLED - WITH MESSAGES ====================

@PreviewTest
@Preview(showBackground = true, name = "Chat With Messages - Light")
@Composable
fun AiChatWithMessagesLightPreview() {
    InsightTheme(darkTheme = false) {
        AiChatUi(
            state = AiChatScreen.State(
                messages = sampleMessages,
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
            ),
        )
    }
}

@PreviewTest
@Preview(
    showBackground = true,
    name = "Chat With Messages - Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun AiChatWithMessagesDarkPreview() {
    InsightTheme(darkTheme = true) {
        AiChatUi(
            state = AiChatScreen.State(
                messages = sampleMessages,
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
            ),
        )
    }
}

// ==================== ENABLED - LOADING ====================

@PreviewTest
@Preview(showBackground = true, name = "Chat Loading Response")
@Composable
fun AiChatLoadingPreview() {
    InsightTheme {
        AiChatUi(
            state = AiChatScreen.State(
                messages = listOf(
                    ChatMessage(
                        id = "1",
                        content = "What are my top spending categories?",
                        role = ChatRole.USER,
                    ),
                ),
                inputText = "",
                isLoading = true,
                isAiEnabled = true,
                showModelSetup = false,
                modelState = ModelState.NotInstalled,
                availableModels = emptyList(),
                searchResults = emptyList(),
                isSearching = false,
                searchQuery = "",
                showModelSelection = false,
                eventSink = {},
            ),
        )
    }
}

// ==================== ENABLED - EMPTY ====================

@PreviewTest
@Preview(showBackground = true, name = "Chat Empty State")
@Composable
fun AiChatEmptyPreview() {
    InsightTheme {
        AiChatUi(
            state = AiChatScreen.State(
                messages = emptyList(),
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
            ),
        )
    }
}

// ==================== DISABLED ====================

@PreviewTest
@Preview(showBackground = true, name = "AI Disabled - Light")
@Composable
fun AiChatDisabledLightPreview() {
    InsightTheme(darkTheme = false) {
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
            ),
        )
    }
}

@PreviewTest
@Preview(
    showBackground = true,
    name = "AI Disabled - Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun AiChatDisabledDarkPreview() {
    InsightTheme(darkTheme = true) {
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
            ),
        )
    }
}

// ==================== WITH INPUT TEXT ====================

@PreviewTest
@Preview(showBackground = true, name = "Chat With Input Text")
@Composable
fun AiChatWithInputPreview() {
    InsightTheme {
        AiChatUi(
            state = AiChatScreen.State(
                messages = sampleMessages,
                inputText = "How can I save more money?",
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
            ),
        )
    }
}

// ==================== SAMPLE DATA ====================

private val sampleMessages = listOf(
    ChatMessage(
        id = "1",
        content = "How much did I spend this month?",
        role = ChatRole.USER,
    ),
    ChatMessage(
        id = "2",
        content = "Based on your expenses, you've spent \$1,234.56 this month. Your biggest category is Food at \$450.00 (36%), followed by Transport at \$320.00 (26%).",
        role = ChatRole.ASSISTANT,
    ),
    ChatMessage(
        id = "3",
        content = "What about compared to last month?",
        role = ChatRole.USER,
    ),
    ChatMessage(
        id = "4",
        content = "Last month you spent \$1,100.00, so your spending increased by 12.2%. The biggest increase was in Entertainment, which went up 45%.",
        role = ChatRole.ASSISTANT,
    ),
)
