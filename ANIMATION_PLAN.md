# Animation Implementation Plan

**Project**: Insight - Expense Tracker
**Date**: 2026-01-11
**Current Animation Usage**: ZERO - No animations currently implemented
**Goal**: Implement comprehensive animation system following Material 3 motion guidelines

---

## Overview

This document catalogs ALL animation opportunities identified across the app, categorized by animation type and prioritized by user impact.

### Animation Types Summary

| Type | Use Cases | Current Count | Proposed Count |
|------|-----------|---------------|----------------|
| AnimatedVisibility | Conditional UI, list items, dialogs | 0 | 15+ |
| AnimatedContent | State-dependent content, tabs | 0 | 5+ |
| Shared Elements (Circuit) | List→Detail navigation | 0 | 4+ |
| animate*AsState | Colors, sizes, positions, alphas | 0 | 20+ |
| Custom animations | Typing indicator, progress bars | 0 | 5+ |

**Total Animation Sites**: 0 → 49+

---

## High Impact (Priority 1)

These animations have the highest user-facing impact and should be implemented first.

### P1-1: AI Chat Message Entrance

**Type**: AnimatedVisibility
**Location**: `feature/ai-chat/src/main/kotlin/com/keisardev/insight/feature/aichat/AiChatScreen.kt:235`
**Current**: Messages appear instantly
**Proposed**: Slide + fade animation

**Implementation**:
```kotlin
items(messages, key = { it.id }) { message ->
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(300)) + slideInVertically(
            initialOffsetY = { if (message.isUser) it / 3 else -it / 3 },
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        ),
        exit = fadeOut(tween(150)) + shrinkVertically()
    ) {
        ChatMessageItem(message)
    }
}
```

**Effort**: S (2 hours)
**Impact**: CRITICAL - Most noticeable missing animation
**Dependencies**: None

---

### P1-2: AI Chat Typing Indicator

**Type**: Custom Animation (Infinite)
**Location**: `feature/ai-chat/src/main/kotlin/com/keisardev/insight/feature/aichat/AiChatScreen.kt:302`
**Current**: Static CircularProgressIndicator
**Proposed**: Bouncing dots

**Implementation**:
```kotlin
@Composable
fun BouncingDotsIndicator() {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { index ->
            val infiniteTransition = rememberInfiniteTransition()
            val offsetY by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -8f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = EaseInOutCubic),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 150)
                )
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
```

**Effort**: M (3 hours)
**Impact**: HIGH
**Dependencies**: None

---

### P1-3: Reports View Type Switching

**Type**: AnimatedContent
**Location**: `feature/reports/src/main/kotlin/com/keisardev/insight/feature/reports/ReportsScreen.kt:212`
**Current**: Instant content swap (very jarring)
**Proposed**: Crossfade with slide

**Implementation**:
```kotlin
AnimatedContent(
    targetState = state.selectedViewType,
    transitionSpec = {
        (fadeIn(tween(300)) + slideInVertically { it / 2 }) togetherWith
        (fadeOut(tween(150)) + slideOutVertically { -it / 2 })
    },
    label = "report_view_switch"
) { viewType ->
    when (viewType) {
        ReportViewType.SPENDING -> SpendingView(...)
        ReportViewType.EARNINGS -> EarningsView(...)
        ReportViewType.BALANCE -> BalanceView(...)
    }
}
```

**Effort**: S (1 hour)
**Impact**: CRITICAL - Second most jarring missing animation
**Dependencies**: None

---

### P1-4: Expense List Items

**Type**: AnimatedVisibility
**Location**: `feature/expenses/src/main/kotlin/com/keisardev/insight/feature/expenses/ExpensesScreen.kt:156`
**Current**: Items appear instantly
**Proposed**: Fade + expand animation

**Implementation**:
```kotlin
items(expenses, key = { it.id }) { expense ->
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(300)) + expandVertically(
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        ),
        exit = fadeOut(tween(150)) + shrinkVertically()
    ) {
        ExpenseItem(expense, onClick = { onExpenseClick(expense.id) })
    }
}
```

**Effort**: S (1 hour)
**Impact**: HIGH
**Dependencies**: None

---

### P1-5: Income List Items

**Type**: AnimatedVisibility
**Location**: `feature/income/src/main/kotlin/com/keisardev/insight/feature/income/IncomeScreen.kt:154`
**Current**: Items appear instantly
**Proposed**: Same as expenses

**Implementation**: Same pattern as P1-4

**Effort**: S (30 minutes, copy pattern)
**Impact**: HIGH
**Dependencies**: P1-4

---

### P1-6: Category Breakdown Items (Reports)

**Type**: AnimatedVisibility
**Location**: `feature/reports/src/main/kotlin/com/keisardev/insight/feature/reports/ReportsScreen.kt:297`
**Current**: Items appear instantly
**Proposed**: Staggered entrance animation

**Implementation**:
```kotlin
items(categoryBreakdown.withIndex()) { (index, spending) ->
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = 300,
                delayMillis = index * 50 // Stagger
            )
        ) + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        CategoryBreakdownItem(spending)
    }
}
```

**Effort**: M (2 hours)
**Impact**: HIGH
**Dependencies**: None

---

### P1-7: Linear Progress Indicator Animation (Reports)

**Type**: animateFloatAsState
**Location**: `feature/reports/src/main/kotlin/com/keisardev/insight/feature/reports/ReportsScreen.kt:580`
**Current**: Progress bar fills instantly
**Proposed**: Smooth animation to percentage

**Implementation**:
```kotlin
@Composable
private fun CategoryBreakdownItem(
    spending: CategorySpending,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = spending.percentage,
        animationSpec = tween(
            durationMillis = 800,
            easing = EaseOutCubic
        ),
        label = "progress_${spending.category.id}"
    )

    // ...existing code...

    LinearProgressIndicator(
        progress = { animatedProgress }, // Use animated value
        // ...
    )
}
```

**Effort**: S (1 hour)
**Impact**: HIGH - Very noticeable improvement
**Dependencies**: None

---

### P1-8: Shared Element - Expense Icon (List → Detail)

**Type**: Circuit SharedElement
**Location**:
- List: `feature/expenses/src/main/kotlin/com/keisardev/insight/feature/expenses/ExpensesScreen.kt:184`
- Detail: `feature/expenses/src/main/kotlin/com/keisardev/insight/feature/expenses/AddEditExpenseScreen.kt:295`
**Current**: No transition
**Proposed**: Icon smoothly transitions from list to detail form

**Implementation**:

```kotlin
// 1. Wrap navigation content in SharedElementTransitionLayout
// In MainActivity or root navigation composable
SharedElementTransitionLayout {
    CircuitContent(
        screen = navigator.currentScreen,
        // ...
    )
}

// 2. In ExpensesScreen - mark icon as shared element
CategoryIconCircle(
    category = expense.category,
    modifier = Modifier.sharedElement(
        sharedContentState = rememberSharedContentState(
            key = "expense_icon_${expense.id}"
        ),
        animatedVisibilityScope = requireAnimatedScope(
            SharedElementTransitionScope.AnimatedScope.Navigation
        )
    )
)

// 3. In AddEditExpenseScreen - mark selected category chip icon
Icon(
    imageVector = getCategoryIcon(category.icon),
    contentDescription = null,
    modifier = Modifier.sharedElement(
        sharedContentState = rememberSharedContentState(
            key = "expense_icon_${screen.expenseId}"
        ),
        animatedVisibilityScope = requireAnimatedScope(
            SharedElementTransitionScope.AnimatedScope.Navigation
        )
    )
    // ...
)
```

**Effort**: L (6-8 hours, requires architectural change)
**Impact**: HIGH - Impressive visual continuity
**Dependencies**: Circuit SharedElementTransitionLayout setup

---

### P1-9: Send Button State Animation (AI Chat)

**Type**: animateColorAsState + animateFloatAsState
**Location**: `feature/ai-chat/src/main/kotlin/com/keisardev/insight/feature/aichat/AiChatScreen.kt:374`
**Current**: Instant color/state change
**Proposed**: Smooth transition

**Implementation**:
```kotlin
val isEnabled = inputText.isNotBlank() && !isLoading
val scale by animateFloatAsState(
    targetValue = if (isEnabled) 1f else 0.9f,
    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
    label = "send_scale"
)
val iconColor by animateColorAsState(
    targetValue = if (isEnabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    },
    animationSpec = tween(300),
    label = "send_color"
)

IconButton(
    onClick = onSend,
    enabled = isEnabled,
    modifier = Modifier.scale(scale)
) {
    Icon(tint = iconColor, /*...*/)
}
```

**Effort**: S (1 hour)
**Impact**: MEDIUM
**Dependencies**: None

---

## Medium Impact (Priority 2)

### P2-1: FAB Scroll Hide/Show (Expenses, Income)

**Type**: AnimatedVisibility
**Locations**:
- `feature/expenses/src/main/kotlin/com/keisardev/insight/feature/expenses/ExpensesScreen.kt:107`
- `feature/income/src/main/kotlin/com/keisardev/insight/feature/income/IncomeScreen.kt:105`
**Current**: FAB always visible
**Proposed**: Hide on scroll down, show on scroll up

**Implementation**:
```kotlin
val listState = rememberLazyListState()
val isFabVisible by remember {
    derivedStateOf {
        listState.firstVisibleItemIndex == 0 ||
        !listState.isScrollInProgress ||
        listState.isScrollingUp()
    }
}

Scaffold(
    floatingActionButton = {
        AnimatedVisibility(
            visible = isFabVisible,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            FloatingActionButton(onClick = /*...*/) {
                Icon(Icons.Default.Add, /*...*/)
            }
        }
    }
) {
    LazyColumn(state = listState) {
        // ...
    }
}

// Extension function
fun LazyListState.isScrollingUp(): Boolean {
    return firstVisibleItemIndex < previousFirstVisibleItemIndex ||
           (firstVisibleItemIndex == previousFirstVisibleItemIndex &&
            firstVisibleItemScrollOffset < previousFirstVisibleItemScrollOffset)
}
```

**Effort**: M (3 hours, requires state management)
**Impact**: MEDIUM
**Dependencies**: None

---

### P2-2: Dialog Entrance Animations

**Type**: Custom Dialog wrapper with AnimatedVisibility
**Locations**:
- All AlertDialogs across the app (6+ instances)
- DatePickerDialog (2 instances)
**Current**: Dialogs appear instantly
**Proposed**: Fade + scale animation

**Implementation**:
```kotlin
@Composable
fun AnimatedAlertDialog(
    onDismissRequest: () -> Unit,
    visible: Boolean,
    content: @Composable () -> Unit
) {
    if (visible) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(onClick = onDismissRequest),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(200)) + scaleIn(
                    initialScale = 0.8f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy
                    )
                ),
                exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.8f)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.clickable(enabled = false) {} // Prevent click through
                ) {
                    content()
                }
            }
        }
    }
}
```

**Effort**: M (4 hours to create wrapper + migrate all dialogs)
**Impact**: MEDIUM
**Dependencies**: None

---

### P2-3: Category Chip Selection Animation

**Type**: animateFloatAsState
**Locations**:
- `feature/expenses/src/main/kotlin/com/keisardev/insight/feature/expenses/AddEditExpenseScreen.kt:379`
- `feature/income/src/main/kotlin/com/keisardev/insight/feature/income/AddEditIncomeScreen.kt:410`
**Current**: Instant selection state change
**Proposed**: Subtle scale animation

**Implementation**:
```kotlin
@Composable
private fun CategoryChip(
    category: Category,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "chip_scale_${category.id}"
    )

    val containerColor by animateColorAsState(
        targetValue = if (isSelected) {
            category.color.copy(alpha = 0.15f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        label = "chip_color_${category.id}"
    )

    FilterChip(
        selected = isSelected,
        onClick = onClick,
        modifier = modifier.scale(scale),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = containerColor
        ),
        // ...
    )
}
```

**Effort**: S (2 hours for both locations)
**Impact**: LOW-MEDIUM
**Dependencies**: None

---

### P2-4: Month Navigation Animation (Reports)

**Type**: Crossfade
**Location**: `feature/reports/src/main/kotlin/com/keisardev/insight/feature/reports/ReportsScreen.kt:159-167`
**Current**: Instant data swap
**Proposed**: Crossfade transition

**Implementation**:
```kotlin
// Wrap the entire ReportsUi content in Crossfade
Crossfade(
    targetState = Pair(state.selectedMonth, state.selectedYear),
    animationSpec = tween(300),
    label = "month_data"
) { (month, year) ->
    Column {
        // Existing MonthSelector, content, etc.
        // ...
    }
}
```

**Effort**: M (2 hours, may require restructuring)
**Impact**: MEDIUM
**Dependencies**: None

---

### P2-5: Save Button Loading State (Forms)

**Type**: AnimatedContent
**Locations**:
- `feature/expenses/src/main/kotlin/com/keisardev/insight/feature/expenses/AddEditExpenseScreen.kt:235`
- `feature/income/src/main/kotlin/com/keisardev/insight/feature/income/AddEditIncomeScreen.kt:247`
**Current**: Button just disables
**Proposed**: Switch to CircularProgressIndicator

**Implementation**:
```kotlin
AnimatedContent(
    targetState = state.isSaving,
    transitionSpec = {
        fadeIn(tween(200)) togetherWith fadeOut(tween(200))
    },
    label = "save_button"
) { isSaving ->
    if (isSaving) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp
        )
    } else {
        Icon(Icons.Default.Check, contentDescription = "Save")
    }
}
```

**Effort**: S (1 hour for both)
**Impact**: MEDIUM
**Dependencies**: None

---

### P2-6: FilterChip Selection (Reports)

**Type**: animateColorAsState
**Location**: `feature/reports/src/main/kotlin/com/keisardev/insight/feature/reports/ReportsScreen.kt:239`
**Current**: Instant selection
**Proposed**: Smooth color transition

**Implementation**:
```kotlin
ReportViewType.entries.forEach { viewType ->
    val containerColor by animateColorAsState(
        targetValue = if (selectedViewType == viewType) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        label = "filter_color_${viewType.name}"
    )

    FilterChip(
        selected = selectedViewType == viewType,
        onClick = { onViewTypeChange(viewType) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = containerColor
        ),
        // ...
    )
}
```

**Effort**: S (30 minutes)
**Impact**: LOW
**Dependencies**: None

---

## Low Impact (Priority 3)

### P3-1: Empty State Entrance Animation

**Type**: AnimatedVisibility
**Locations**: All screens with EmptyState (5+ instances)
**Current**: Appears instantly
**Proposed**: Fade + scale in

**Implementation**:
```kotlin
AnimatedVisibility(
    visible = state.expenses.isEmpty(),
    enter = fadeIn(tween(400)) + scaleIn(initialScale = 0.9f),
    exit = fadeOut(tween(200))
) {
    EmptyState(
        icon = Icons.Outlined.Receipt,
        title = "No expenses yet",
        subtitle = "Tap + to add your first expense"
    )
}
```

**Effort**: S (1 hour for all locations)
**Impact**: LOW
**Dependencies**: None

---

### P3-2: Loading Indicator Entrance

**Type**: AnimatedVisibility
**Locations**: All screens with CircularProgressIndicator (6+ instances)
**Current**: Appears instantly
**Proposed**: Fade in with delay (prevents flash for fast loads)

**Implementation**:
```kotlin
AnimatedVisibility(
    visible = state.isLoading,
    enter = fadeIn(tween(300, delayMillis = 200)), // Delay prevents flash
    exit = fadeOut(tween(150))
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}
```

**Effort**: S (1 hour for all locations)
**Impact**: LOW
**Dependencies**: None

---

### P3-3: Settings Item Press Feedback

**Type**: animateFloatAsState
**Location**: `feature/settings/src/main/kotlin/com/keisardev/insight/feature/settings/SettingsScreen.kt:216`
**Current**: Basic ripple only
**Proposed**: Subtle scale on press

**Implementation**:
```kotlin
@Composable
private fun SettingsItem(/*...*/) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "settings_press"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple()
            ) {
                onClick()
            }
            .padding(16.dp),
        // ...
    )
}
```

**Effort**: S (1 hour)
**Impact**: LOW
**Dependencies**: None

---

### P3-4: Card Elevation on Press

**Type**: animateDpAsState
**Locations**: All clickable Cards (10+ instances)
**Current**: Static elevation
**Proposed**: Elevation increases on press

**Implementation**:
```kotlin
val elevation by animateDpAsState(
    targetValue = if (isPressed) 8.dp else 2.dp,
    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
    label = "card_elevation"
)

Card(
    modifier = modifier
        .fillMaxWidth()
        .clickable(onClick = onClick),
    elevation = CardDefaults.cardElevation(defaultElevation = elevation),
    // ...
)
```

**Effort**: M (3 hours for all locations)
**Impact**: LOW
**Dependencies**: None

---

### P3-5: Snackbar Animation (Settings)

**Type**: Built-in M3 animation (verify)
**Location**: `feature/settings/src/main/kotlin/com/keisardev/insight/feature/settings/SettingsScreen.kt:180`
**Current**: Should already animate (M3 default)
**Proposed**: Verify animation works, no custom implementation needed

**Effort**: N/A (just verify)
**Impact**: LOW
**Dependencies**: None

---

### P3-6: Income Type Badge Fade-In

**Type**: AnimatedVisibility
**Location**: `feature/income/src/main/kotlin/com/keisardev/insight/feature/income/IncomeScreen.kt:203`
**Current**: Appears instantly
**Proposed**: Fade in with slight delay

**Implementation**:
```kotlin
AnimatedVisibility(
    visible = true,
    enter = fadeIn(tween(300, delayMillis = 100)),
    label = "income_badge"
) {
    IncomeTypeBadge(incomeType = income.incomeType)
}
```

**Effort**: S (15 minutes)
**Impact**: LOW
**Dependencies**: None

---

### P3-7: SegmentedButton Selection Indicator

**Type**: Built-in M3 animation (verify)
**Location**: `feature/income/src/main/kotlin/com/keisardev/insight/feature/income/AddEditIncomeScreen.kt:270`
**Current**: Should already animate (M3 default)
**Proposed**: Verify smooth indicator slide

**Effort**: N/A (just verify)
**Impact**: LOW (M3 handles this)
**Dependencies**: None

---

## Advanced/Future Enhancements

### A1: Streaming Text Effect (AI Chat)

**Type**: Custom Animation
**Location**: AI Chat message bubbles
**Current**: Full text appears at once
**Proposed**: Character-by-character reveal

**Implementation**: See AI_CHAT_IMPROVEMENTS.md section 3.1

**Effort**: L (1-2 days)
**Impact**: HIGH for chat UX
**Dependencies**: Repository changes for true streaming

---

### A2: Pull-to-Refresh

**Type**: SwipeRefresh / PullRefreshIndicator
**Locations**: All list screens
**Current**: Not implemented
**Proposed**: Standard pull-to-refresh gesture

**Implementation**:
```kotlin
val refreshing by viewModel.isRefreshing.collectAsState()
val pullRefreshState = rememberPullRefreshState(
    refreshing = refreshing,
    onRefresh = { viewModel.refresh() }
)

Box(modifier = Modifier.pullRefresh(pullRefreshState)) {
    LazyColumn {
        // ...
    }
    PullRefreshIndicator(
        refreshing = refreshing,
        state = pullRefreshState,
        modifier = Modifier.align(Alignment.TopCenter)
    )
}
```

**Effort**: M (4 hours for all screens)
**Impact**: MEDIUM
**Dependencies**: Repository refresh methods

---

### A3: Skeleton Loading States

**Type**: Custom Shimmer Animation
**Locations**: All list screens
**Current**: CircularProgressIndicator only
**Proposed**: Shimmer skeleton cards

**Implementation**:
```kotlin
@Composable
fun ShimmerCard(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition()
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Card(modifier = modifier) {
        Row(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = shimmerAlpha)
                    )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(16.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = shimmerAlpha)
                        )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(14.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = shimmerAlpha)
                        )
                )
            }
        }
    }
}

// Usage
if (state.isLoading) {
    LazyColumn {
        items(5) { ShimmerCard() }
    }
} else {
    // Real content
}
```

**Effort**: L (1 day to create + apply to all screens)
**Impact**: MEDIUM-HIGH
**Dependencies**: None

---

### A4: Number Count-Up Animation (Reports)

**Type**: Custom Animation
**Location**: Reports total cards
**Current**: Numbers appear instantly
**Proposed**: Count up from 0 to target value

**Implementation**:
```kotlin
@Composable
fun AnimatedCurrency(
    targetAmount: Double,
    modifier: Modifier = Modifier
) {
    var currentAmount by remember { mutableStateOf(0.0) }

    LaunchedEffect(targetAmount) {
        val duration = 800 // ms
        val steps = 30
        val increment = targetAmount / steps
        repeat(steps) {
            delay(duration / steps.toLong())
            currentAmount += increment
        }
        currentAmount = targetAmount // Ensure exact value
    }

    Text(
        text = formatCurrency(currentAmount),
        modifier = modifier,
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold
    )
}
```

**Effort**: M (3 hours)
**Impact**: MEDIUM - Impressive visual effect
**Dependencies**: None

---

### A5: Balance Color Transition (Reports)

**Type**: animateColorAsState
**Location**: Reports Balance view
**Current**: Instant color change between saving/deficit
**Proposed**: Smooth color transition

**Implementation**:
```kotlin
val balanceColor by animateColorAsState(
    targetValue = if (financialSummary.isSaving) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.error
    },
    animationSpec = tween(600),
    label = "balance_color"
)

Text(
    text = formatCurrency(abs(financialSummary.netBalance)),
    color = balanceColor,
    // ...
)
```

**Effort**: S (30 minutes)
**Impact**: LOW-MEDIUM
**Dependencies**: None

---

## Implementation Roadmap

### Phase 1: Critical Animations (Week 1-2)
**Goal**: Eliminate the most jarring UI transitions

1. P1-1: AI Chat message entrance (2h)
2. P1-2: AI Chat typing indicator (3h)
3. P1-3: Reports view switching (1h)
4. P1-4: Expense list items (1h)
5. P1-5: Income list items (0.5h)
6. P1-9: Send button animation (1h)

**Total**: ~8.5 hours

---

### Phase 2: List & Dashboard Polish (Week 3)
**Goal**: Smooth out all list and data display animations

7. P1-6: Category breakdown staggered entrance (2h)
8. P1-7: Linear progress animation (1h)
9. P2-4: Month navigation crossfade (2h)
10. P2-5: Save button loading state (1h)
11. P2-6: FilterChip selection (0.5h)

**Total**: ~6.5 hours

---

### Phase 3: Navigation Transitions (Week 4-5)
**Goal**: Implement shared element transitions

12. Setup SharedElementTransitionLayout (4h)
13. P1-8: Expense icon shared element (4h)
14. P1-8b: Income icon shared element (2h)

**Total**: ~10 hours

---

### Phase 4: Form & Dialog Polish (Week 6)
**Goal**: Enhance form interactions

15. P2-2: Animated dialog wrapper (4h)
16. P2-3: Category chip selection (2h)
17. P2-1: FAB scroll hide/show (3h)

**Total**: ~9 hours

---

### Phase 5: Low Priority & Advanced (Week 7-8)
**Goal**: Final polish and advanced effects

18. P3-1: Empty state animations (1h)
19. P3-2: Loading indicator delays (1h)
20. A4: Number count-up animation (3h)
21. A5: Balance color transition (0.5h)
22. Final testing and refinement (4h)

**Total**: ~9.5 hours

---

## Total Estimated Effort

| Phase | Hours | Priority |
|-------|-------|----------|
| Phase 1 | 8.5 | P1 (Critical) |
| Phase 2 | 6.5 | P1 |
| Phase 3 | 10 | P1 (High effort) |
| Phase 4 | 9 | P2 |
| Phase 5 | 9.5 | P3 |
| **Total** | **43.5 hours** | ~ 1 week full-time or 2-3 weeks part-time |

---

## Performance Considerations

### Animation Best Practices

1. **Use hardware acceleration**
   - Compose animations run on GPU by default
   - Avoid layout changes during animation when possible

2. **Optimize recomposition**
   ```kotlin
   // BAD: Causes unnecessary recompositions
   val animatedValue = remember { Animatable(0f) }

   // GOOD: Scoped to animation
   val animatedValue by animateFloatAsState(targetValue)
   ```

3. **Limit concurrent animations**
   - Max 3-5 simultaneous complex animations
   - Use staggered delays instead of all-at-once

4. **Test on mid-range devices**
   - Target: Smooth 60fps on 2-year-old mid-range Android
   - Use Layout Inspector to identify jank

5. **Respect reduced motion preferences**
   ```kotlin
   val isReducedMotionEnabled = LocalAccessibilityManager.current
       ?.isReducedMotionEnabled ?: false

   val animationSpec = if (isReducedMotionEnabled) {
       snap() // Instant
   } else {
       tween(300)
   }
   ```

### Profiling Checklist

- [ ] Run Layout Inspector during animations
- [ ] Check GPU rendering profile (no red bars)
- [ ] Monitor Compose recompositions (no unnecessary recomps)
- [ ] Test on physical mid-range device
- [ ] Verify battery impact is minimal

---

## Testing Strategy

### Manual Testing

For each animation:
1. Verify animation is smooth (no jank)
2. Check dark mode appearance
3. Test with accessibility services (TalkBack)
4. Verify reduced motion fallback works
5. Test on various screen sizes

### Automated Testing

```kotlin
@Test
fun testMessageEntranceAnimation() = runComposeUiTest {
    setContent {
        val messages = remember { mutableStateListOf<ChatMessage>() }
        ChatMessagesList(messages = messages, isLoading = false)
    }

    // Add message
    runOnIdle {
        messages.add(ChatMessage(/*...*/ ))
    }

    // Verify animation occurs (check for AnimatedVisibility)
    onNodeWithTag("message_item").assertExists()
}
```

### Performance Testing

```kotlin
@Test
fun testScrollPerformance() = runComposeUiTest {
    setContent {
        ExpensesUi(state = stateWithManyItems)
    }

    onNodeWithTag("expense_list").performScrollToIndex(50)

    // Verify no dropped frames
    // (Requires integration with performance monitoring)
}
```

---

## Accessibility

### Reduced Motion Support

All animations should respect `Settings.System.ANIMATOR_DURATION_SCALE`:

```kotlin
@Composable
fun <T> animatedTransition(
    enabled: Boolean = !isReducedMotionEnabled()
): AnimationSpec<T> {
    return if (enabled) {
        tween(300)
    } else {
        snap()
    }
}

@Composable
fun isReducedMotionEnabled(): Boolean {
    val context = LocalContext.current
    val scale = Settings.System.getFloat(
        context.contentResolver,
        Settings.System.ANIMATOR_DURATION_SCALE,
        1f
    )
    return scale == 0f
}
```

### Screen Reader Compatibility

- Ensure AnimatedVisibility items are announced
- Use `semantics { liveRegion = LiveRegionMode.Polite }` for dynamic content
- Test with TalkBack enabled

---

## Dependencies

### Required Compose Dependencies

All animation APIs are part of Compose BOM 2025.12.01 (already included):
- `androidx.compose.animation:animation`
- `androidx.compose.animation:animation-core`
- `androidx.compose.ui:ui` (for Modifier.animateContentSize, etc.)

### Circuit Shared Elements

Already included (Circuit 0.31.0):
- `com.slack.circuit:circuit-foundation`

No additional dependencies required.

---

## Related Documentation

- **Circuit Shared Elements**: See CIRCUIT_SHARED_ELEMENTS.md
- **UI Audit**: See UI_AUDIT.md
- **AI Chat Improvements**: See AI_CHAT_IMPROVEMENTS.md
- **Master Plan**: See UI_IMPROVEMENT_PLAN.md

---

## Notes

- Animations should enhance UX, not distract
- Follow Material 3 motion guidelines (200-500ms durations)
- Spring animations are preferred for interactive elements
- Tween animations for non-interactive transitions
- Always provide instant fallbacks for reduced motion
