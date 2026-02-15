# AI Chat Screen - Improvement Specification

**Screen**: AiChatScreen
**Location**: `feature/ai-chat/src/main/kotlin/com/keisardev/insight/feature/aichat/AiChatScreen.kt`
**Priority**: HIGH - This screen needs major UX improvements
**Current State**: Functional but lacks modern chat UX polish

---

## Current Implementation Analysis

### What Works Well ✅

1. **Architecture**: Properly using Circuit presenter pattern with ChatRepository
2. **State Management**: Reactive state with collectAsRetainedState
3. **Auto-scroll**: Messages automatically scroll to bottom (using animateScrollToItem)
4. **Message Bubbles**: Well-designed with RoundedCornerShape, distinct colors for user/AI
5. **Loading State**: Shows "Thinking..." indicator
6. **Disabled State**: Clear error card when AI is not configured
7. **Input Handling**: OutlinedTextField with proper keyboard handling (imePadding)

### Critical Gaps ❌

1. **No message entrance animations** - Messages pop into view instantly
2. **Static typing indicator** - Just shows CircularProgressIndicator, no animation
3. **No streaming text effect** - AI responses appear all at once
4. **Instant state changes** - Send button, input field have no animation
5. **Basic input styling** - Could be more polished

---

## Priority 1: Message Animations

### 1.1 New Message Appearance

**Current**: Messages appear instantly when added to the list (jarring)

**Proposed**: AnimatedVisibility with slide + fade animation

**Implementation**:

```kotlin
@Composable
private fun ChatMessagesList(
    messages: List<ChatMessage>,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

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
            // NEW: Wrap each message in AnimatedVisibility
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = tween(300)) +
                    slideInVertically(
                        initialOffsetY = { fullHeight ->
                            // User messages slide from bottom, AI from top
                            if (message.isUser) fullHeight / 3 else -fullHeight / 3
                        },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    ),
                exit = fadeOut(animationSpec = tween(150)) +
                    shrinkVertically()
            ) {
                ChatMessageItem(message = message)
            }
        }

        if (isLoading) {
            item {
                // Updated typing indicator (see section 1.2)
                TypingIndicator()
            }
        }
    }
}
```

**Effort**: S (2 hours)
**Impact**: CRITICAL - Makes chat feel alive
**Files**: AiChatScreen.kt (lines 214-245)

---

### 1.2 Typing Indicator Animation

**Current**: Static "Thinking..." text with CircularProgressIndicator

**Proposed**: Animated bouncing dots (industry standard)

**Implementation**:

```kotlin
@Composable
private fun TypingIndicator(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // AI avatar (keep existing)
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

        // NEW: Animated bubble with bouncing dots
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Three animated dots
                repeat(3) { index ->
                    val infiniteTransition = rememberInfiniteTransition(
                        label = "typing_dot_$index"
                    )
                    val offsetY by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = -8f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(
                                durationMillis = 600,
                                easing = EaseInOutCubic
                            ),
                            repeatMode = RepeatMode.Reverse,
                            initialStartOffset = StartOffset(index * 150) // Stagger
                        ),
                        label = "dot_offset_$index"
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .offset(y = offsetY.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }
        }
    }
}
```

**Alternative Implementation** (Simpler):
```kotlin
// Just use pulsing alpha instead of bouncing
val alpha by infiniteTransition.animateFloat(
    initialValue = 0.3f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
        animation = tween(600),
        repeatMode = RepeatMode.Reverse,
        initialStartOffset = StartOffset(index * 200)
    )
)
Box(
    modifier = Modifier
        .size(8.dp)
        .alpha(alpha)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.onSurfaceVariant)
)
```

**Effort**: M (3-4 hours including testing)
**Impact**: HIGH - Much more engaging than static indicator
**Files**: AiChatScreen.kt (lines 302-345)

---

## Priority 2: Input Enhancements

### 2.1 Send Button State Animation

**Current**: Send button instantly changes color and enabled state

**Proposed**: Smooth color transition and scale animation

**Implementation**:

```kotlin
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
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
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

            // NEW: Animated IconButton
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

            IconButton(
                onClick = onSend,
                enabled = isEnabled,
                modifier = Modifier.scale(scale)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = iconColor,
                )
            }
        }
    }
}
```

**Effort**: S (1-2 hours)
**Impact**: MEDIUM - Subtle but noticeable polish
**Files**: AiChatScreen.kt (lines 347-390)

---

### 2.2 TextField Multiline Expansion Animation

**Current**: TextField expands instantly when text wraps

**Proposed**: Smooth height animation

**Implementation**:

```kotlin
// Use animateDpAsState for smooth size transitions
val textFieldHeight by animateDpAsState(
    targetValue = when {
        inputText.isEmpty() -> 56.dp
        inputText.lines().size > 2 -> 120.dp
        inputText.lines().size > 1 -> 88.dp
        else -> 56.dp
    },
    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
    label = "input_height"
)

OutlinedTextField(
    value = inputText,
    onValueChange = onInputChange,
    modifier = Modifier
        .weight(1f)
        .heightIn(min = textFieldHeight), // Animated height
    placeholder = { Text("Ask about your expenses...") },
    maxLines = 3,
    enabled = !isLoading,
    shape = RoundedCornerShape(24.dp),
)
```

**Note**: This might conflict with TextField's internal height calculation. Alternative approach:

```kotlin
// Simpler: Just animate the corner radius for visual feedback
val cornerRadius by animateDpAsState(
    targetValue = if (inputText.lines().size > 1) 16.dp else 24.dp,
    label = "corner_radius"
)
OutlinedTextField(
    shape = RoundedCornerShape(cornerRadius),
    // ...
)
```

**Effort**: S (1-2 hours, may require experimentation)
**Impact**: LOW - Nice to have, not critical
**Files**: AiChatScreen.kt (lines 364-372)

---

### 2.3 Enhanced Input Styling

**Current**: Basic OutlinedTextField

**Proposed**: More polished styling with subtle enhancements

**Implementation**:

```kotlin
OutlinedTextField(
    value = inputText,
    onValueChange = onInputChange,
    modifier = Modifier.weight(1f),
    placeholder = {
        Text(
            "Ask about your expenses...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    },
    maxLines = 3,
    enabled = !isLoading,
    shape = RoundedCornerShape(24.dp),
    colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
    ),
    // NEW: Add leading icon for context
    leadingIcon = {
        Icon(
            imageVector = Icons.Default.Chat, // or QuestionAnswer
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
)
```

**Effort**: S (1 hour)
**Impact**: LOW - Visual polish
**Files**: AiChatScreen.kt (lines 364-372)

---

## Priority 3: AI Response UX

### 3.1 Streaming Text Effect

**Current**: AI responses appear all at once

**Proposed**: Character-by-character or word-by-word reveal

**Implementation Approach 1: Repository-level streaming**

Modify `ChatRepository` to emit partial messages:

```kotlin
// In ChatRepository.kt
suspend fun sendMessage(content: String) {
    _isLoading.value = true

    // Add user message
    val userMessage = ChatMessage(/*...*/)
    addMessage(userMessage)

    // Create placeholder AI message
    val aiMessageId = UUID.randomUUID().toString()
    var accumulatedText = ""

    try {
        // Stream response
        aiService.streamResponse(messages.value).collect { chunk ->
            accumulatedText += chunk
            updateMessage(aiMessageId, accumulatedText)
        }
    } catch (e: Exception) {
        // Handle error
    } finally {
        _isLoading.value = false
    }
}
```

Then in UI, messages update reactively as text accumulates.

**Implementation Approach 2: UI-level animation (simpler)**

```kotlin
@Composable
fun StreamingText(
    fullText: String,
    isComplete: Boolean,
    modifier: Modifier = Modifier,
) {
    var visibleCharCount by remember { mutableStateOf(0) }

    LaunchedEffect(fullText, isComplete) {
        if (!isComplete && visibleCharCount < fullText.length) {
            while (visibleCharCount < fullText.length) {
                delay(20) // 20ms per character = 50 chars/sec
                visibleCharCount++
            }
        } else if (isComplete) {
            visibleCharCount = fullText.length
        }
    }

    Text(
        text = fullText.take(visibleCharCount),
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// Usage in ChatMessageItem
Surface(/*...*/) {
    if (message.isUser) {
        Text(text = message.content, /*...*/)
    } else {
        StreamingText(
            fullText = message.content,
            isComplete = message.isComplete, // Add this field to ChatMessage
            /*...*/
        )
    }
}
```

**Effort**: L (1-2 days, includes backend changes for Approach 1)
**Impact**: HIGH - Modern chat UX standard
**Files**:
- Approach 1: `core/ai/repository/ChatRepository.kt` + AiChatScreen.kt
- Approach 2: AiChatScreen.kt only

**Recommendation**: Start with Approach 2 (UI-level) for quicker implementation, migrate to Approach 1 later for true streaming.

---

### 3.2 Thinking Indicator

**Current**: "Thinking..." label with CircularProgressIndicator

**Proposed**: Animated shimmer effect or pulsing brain icon

**Implementation** (covered in section 1.2 above):

Already addressed with animated dots in typing indicator.

**Alternative**: Pulsing brain icon

```kotlin
@Composable
fun ThinkingIndicator() {
    val infiniteTransition = rememberInfiniteTransition()
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        )
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        )
    )

    Icon(
        imageVector = Icons.Default.Psychology,
        contentDescription = null,
        modifier = Modifier
            .size(20.dp)
            .scale(scale)
            .alpha(alpha),
        tint = MaterialTheme.colorScheme.primary,
    )
}
```

**Effort**: S (1 hour)
**Impact**: MEDIUM - Engaging visual feedback
**Files**: AiChatScreen.kt (new composable)

---

## Priority 4: Additional Polish

### 4.1 Message Timestamps

**Proposed**: Show timestamp on long-press or always visible (faded)

**Implementation**:

```kotlin
@Composable
private fun ChatMessageItem(
    message: ChatMessage,
    modifier: Modifier = Modifier,
) {
    var showTimestamp by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    ) {
        Row(/*existing message bubble*/) {
            // ...existing code...
        }

        // NEW: Timestamp
        AnimatedVisibility(
            visible = showTimestamp,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Text(
                text = formatTimestamp(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

// Add timestamp field to ChatMessage model
data class ChatMessage(
    val id: String,
    val content: String,
    val role: ChatRole,
    val timestamp: Instant = Clock.System.now(), // NEW
)

fun formatTimestamp(instant: Instant): String {
    val now = Clock.System.now()
    val diff = now - instant
    return when {
        diff < 60.seconds -> "Just now"
        diff < 3600.seconds -> "${diff.inWholeMinutes}m ago"
        diff < 86400.seconds -> "${diff.inWholeHours}h ago"
        else -> instant.toLocalDateTime(TimeZone.currentSystemDefault())
            .let { "${it.hour}:${it.minute.toString().padStart(2, '0')}" }
    }
}
```

**Effort**: S (2 hours)
**Impact**: LOW - Nice polish
**Files**: AiChatScreen.kt + ChatMessage.kt

---

### 4.2 Message Read Receipts / Status

**Proposed**: Show checkmarks for message status (sending → sent → read)

**Implementation**:

```kotlin
enum class MessageStatus {
    SENDING,    // Clock icon
    SENT,       // Single checkmark
    DELIVERED,  // Double checkmark
    FAILED      // Error icon
}

// Add to ChatMessage
data class ChatMessage(
    // ...existing fields...
    val status: MessageStatus = MessageStatus.SENT,
)

@Composable
fun MessageStatusIcon(status: MessageStatus) {
    val (icon, tint) = when (status) {
        MessageStatus.SENDING -> Icons.Default.Schedule to MaterialTheme.colorScheme.onSurfaceVariant
        MessageStatus.SENT -> Icons.Default.Done to MaterialTheme.colorScheme.primary
        MessageStatus.DELIVERED -> Icons.Default.DoneAll to MaterialTheme.colorScheme.primary
        MessageStatus.FAILED -> Icons.Default.Error to MaterialTheme.colorScheme.error
    }

    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(14.dp),
        tint = tint
    )
}
```

**Effort**: M (4 hours including state management)
**Impact**: LOW - Mostly relevant for multi-user chat
**Files**: AiChatScreen.kt + ChatMessage.kt + ChatRepository.kt

---

### 4.3 Empty State Enhancement

**Current**: Shows error card when AI is disabled

**Proposed**: Add helpful empty state when chat is empty

**Implementation**:

```kotlin
@Composable
private fun ChatMessagesList(
    messages: List<ChatMessage>,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    if (messages.isEmpty() && !isLoading) {
        // NEW: Empty state
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )
                Text(
                    text = "Ask me about your finances!",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "I can help you understand your spending patterns, find expenses, and more.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )

                // Suggested prompts
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    SuggestionChip(
                        onClick = { /* Pre-fill input */ },
                        label = { Text("How much did I spend this month?") }
                    )
                    SuggestionChip(
                        onClick = { /* Pre-fill input */ },
                        label = { Text("What's my biggest expense category?") }
                    )
                    SuggestionChip(
                        onClick = { /* Pre-fill input */ },
                        label = { Text("Show me my spending trends") }
                    )
                }
            }
        }
    } else {
        LazyColumn(/*existing code*/) {
            // ...
        }
    }
}
```

**Effort**: S (2 hours)
**Impact**: MEDIUM - Helps users discover AI features
**Files**: AiChatScreen.kt

---

### 4.4 Haptic Feedback

**Proposed**: Add haptic feedback for key interactions

**Implementation**:

```kotlin
@Composable
fun AiChatUi(state: AiChatScreen.State, modifier: Modifier = Modifier) {
    val hapticFeedback = LocalHapticFeedback.current

    // ...existing code...

    ChatInput(
        onSend = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            state.eventSink(AiChatScreen.Event.OnSend)
        },
        // ...
    )

    // In ChatRepository, when AI response arrives:
    LaunchedEffect(messages.size) {
        if (messages.lastOrNull()?.role == ChatRole.ASSISTANT) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
}
```

**Effort**: S (30 minutes)
**Impact**: LOW - Subtle enhancement
**Files**: AiChatScreen.kt

---

## Summary of Improvements

### Must Have (P0)
| Improvement | Effort | Impact | Files |
|-------------|--------|--------|-------|
| Message entrance animations | S (2h) | CRITICAL | AiChatScreen.kt |
| Typing indicator animation | M (4h) | HIGH | AiChatScreen.kt |
| Send button animation | S (2h) | MEDIUM | AiChatScreen.kt |

**Total P0**: ~8 hours

### Should Have (P1)
| Improvement | Effort | Impact | Files |
|-------------|--------|--------|-------|
| Streaming text effect (UI-level) | M (6h) | HIGH | AiChatScreen.kt |
| Empty state with suggestions | S (2h) | MEDIUM | AiChatScreen.kt |

**Total P1**: ~8 hours

### Nice to Have (P2)
| Improvement | Effort | Impact | Files |
|-------------|--------|--------|-------|
| Message timestamps | S (2h) | LOW | AiChatScreen.kt + model |
| Input multiline animation | S (2h) | LOW | AiChatScreen.kt |
| Enhanced input styling | S (1h) | LOW | AiChatScreen.kt |
| Haptic feedback | S (30m) | LOW | AiChatScreen.kt |

**Total P2**: ~5.5 hours

---

## Implementation Plan

### Sprint 1: Critical Animations (Week 1)
1. Message entrance animations (AnimatedVisibility)
2. Typing indicator with bouncing dots
3. Send button state animation
4. **Testing**: Manual testing on physical device for smoothness

### Sprint 2: Enhanced UX (Week 2)
5. Streaming text effect (UI-level implementation)
6. Empty state with suggested prompts
7. **Testing**: User testing with stakeholders

### Sprint 3: Polish (Week 3)
8. Message timestamps
9. Input enhancements
10. Haptic feedback
11. **Testing**: Final QA pass

---

## Testing Checklist

After implementing improvements:

- [ ] Messages animate in smoothly on send
- [ ] AI responses animate in smoothly when received
- [ ] Typing indicator shows bouncing dots
- [ ] Send button smoothly changes color/scale
- [ ] Streaming text effect works without flicker
- [ ] Empty state appears on first launch
- [ ] Auto-scroll works correctly with animations
- [ ] Performance is smooth on mid-range devices
- [ ] No animation jank or dropped frames
- [ ] Haptic feedback feels appropriate (not too strong)
- [ ] Works correctly in dark mode
- [ ] Accessibility: TalkBack announces messages correctly

---

## Additional Considerations

### Performance
- Use `remember` and `derivedStateOf` to minimize recompositions
- Profile with Layout Inspector to ensure animations run on GPU
- Test on mid-range device (not just flagship)

### Accessibility
- Ensure animations don't interfere with TalkBack
- Provide content descriptions for all icons
- Consider reduced motion preferences:
  ```kotlin
  val isReducedMotionEnabled = LocalAccessibilityManager.current
      ?.isReducedMotionEnabled() ?: false

  if (!isReducedMotionEnabled) {
      // Show animations
  } else {
      // Skip animations, instant state changes
  }
  ```

### Error Handling
- What happens if message sending fails during streaming animation?
- Should failed messages be retryable?
- Consider adding a subtle shake animation for errors

---

## Related Files

- **Main Screen**: `feature/ai-chat/src/main/kotlin/com/keisardev/insight/feature/aichat/AiChatScreen.kt`
- **Repository**: `core/ai/src/main/kotlin/com/keisardev/insight/core/ai/repository/ChatRepository.kt`
- **Model**: `core/model/src/main/kotlin/com/keisardev/insight/core/model/ChatMessage.kt`
- **AI Service**: `core/ai/src/main/kotlin/com/keisardev/insight/core/ai/AiService.kt`
