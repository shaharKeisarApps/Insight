package com.keisardev.insight.feature.expenses

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.keisardev.insight.core.ui.component.SkeletonTransactionItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.keisardev.insight.core.common.di.AppScope
import com.keisardev.insight.core.data.datastore.UserSettings
import com.keisardev.insight.core.data.datastore.UserSettingsRepository
import com.keisardev.insight.core.data.repository.ExpenseRepository
import com.keisardev.insight.core.designsystem.theme.InsightTheme
import com.keisardev.insight.core.model.Category
import com.keisardev.insight.core.model.Expense
import com.keisardev.insight.core.ui.component.CategoryIconCircle
import com.keisardev.insight.core.ui.component.EmptyState
import com.keisardev.insight.core.ui.util.formatCurrency
import com.keisardev.insight.core.ui.util.formatDateShort
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.collectAsRetainedState
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.screen.Screen
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.parcelize.Parcelize

@Parcelize
data object ExpensesScreen : Screen {
    data class State(
        val isLoading: Boolean,
        val isRefreshing: Boolean,
        val expenses: List<Expense>,
        val currencyCode: String,
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data object OnAddClick : Event
        data class OnExpenseClick(val expenseId: Long) : Event
        data object OnRefresh : Event
    }
}

@AssistedInject
class ExpensesPresenter(
    @Assisted private val navigator: Navigator,
    private val expenseRepository: ExpenseRepository,
    private val userSettingsRepository: UserSettingsRepository,
) : Presenter<ExpensesScreen.State> {

    @Composable
    override fun present(): ExpensesScreen.State {
        val expenses by expenseRepository.observeAllExpenses().collectAsRetainedState(initial = emptyList())
        val settings by userSettingsRepository.observeSettings()
            .collectAsRetainedState(initial = UserSettings())
        var isRefreshing by rememberRetained { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        return ExpensesScreen.State(
            isLoading = false,
            isRefreshing = isRefreshing,
            expenses = expenses,
            currencyCode = settings.currencyCode,
        ) { event ->
            when (event) {
                ExpensesScreen.Event.OnAddClick -> {
                    navigator.goTo(AddEditExpenseScreen(expenseId = null))
                }
                is ExpensesScreen.Event.OnExpenseClick -> {
                    navigator.goTo(AddEditExpenseScreen(expenseId = event.expenseId))
                }
                ExpensesScreen.Event.OnRefresh -> {
                    isRefreshing = true
                    scope.launch {
                        // Simulate refresh - in real app, this would refetch from network
                        kotlinx.coroutines.delay(800)
                        isRefreshing = false
                    }
                }
            }
        }
    }

    @CircuitInject(ExpensesScreen::class, AppScope::class)
    @AssistedFactory
    fun interface Factory {
        fun create(navigator: Navigator): ExpensesPresenter
    }
}

@CircuitInject(ExpensesScreen::class, AppScope::class)
@Composable
fun ExpensesUi(state: ExpensesScreen.State, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()

    // FAB visibility: show when at top or scrolling up, hide when scrolling down
    val isFabVisible by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 ||
            listState.layoutInfo.visibleItemsInfo.firstOrNull()?.let { first ->
                val previousIndex = listState.layoutInfo.visibleItemsInfo.getOrNull(1)?.index ?: 0
                first.index <= previousIndex
            } ?: true
        }
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            AnimatedVisibility(
                visible = isFabVisible || state.expenses.isEmpty(),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            ) {
                FloatingActionButton(
                    onClick = { state.eventSink(ExpensesScreen.Event.OnAddClick) },
                    modifier = Modifier.testTag("fab_add_expense"),
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add expense")
                }
            }
        },
    ) { paddingValues ->
        when {
            state.isLoading -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(5) {
                        SkeletonTransactionItem()
                    }
                }
            }
            state.expenses.isEmpty() -> {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(400)) + scaleIn(
                        initialScale = 0.9f,
                        animationSpec = tween(400)
                    ),
                ) {
                    EmptyState(
                        icon = Icons.Outlined.Receipt,
                        title = "No expenses yet",
                        subtitle = "Tap + to add your first expense",
                        modifier = Modifier.padding(paddingValues),
                    )
                }
            }
            else -> {
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = { state.eventSink(ExpensesScreen.Event.OnRefresh) },
                    modifier = Modifier.padding(paddingValues)
                ) {
                    ExpensesList(
                        expenses = state.expenses,
                        currencyCode = state.currencyCode,
                        onExpenseClick = { state.eventSink(ExpensesScreen.Event.OnExpenseClick(it)) },
                        listState = listState,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpensesList(
    expenses: List<Expense>,
    currencyCode: String,
    onExpenseClick: (Long) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(expenses, key = { it.id }) { expense ->
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = tween(300)) + expandVertically(
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                ),
                exit = fadeOut(animationSpec = tween(150)) + shrinkVertically()
            ) {
                ExpenseItem(
                    expense = expense,
                    currencyCode = currencyCode,
                    onClick = { onExpenseClick(expense.id) },
                )
            }
        }
    }
}

@Composable
private fun ExpenseItem(
    expense: Expense,
    currencyCode: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryIconCircle(category = expense.category)

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = expense.description.ifEmpty { expense.category.name },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = expense.category.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    text = formatCurrency(expense.amount, currencyCode),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = formatDateShort(expense.date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// Preview functions
@Preview(showBackground = true)
@Composable
private fun PreviewExpensesUiEmpty() {
    InsightTheme {
        ExpensesUi(
            state = ExpensesScreen.State(
                isLoading = false,
                isRefreshing = false,
                expenses = emptyList(),
                currencyCode = "USD",
                eventSink = {},
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewExpensesUiWithData() {
    InsightTheme {
        val sampleCategory = Category(
            id = 1,
            name = "Food",
            icon = "restaurant",
            colorHex = 0xFFE57373,
        )
        ExpensesUi(
            state = ExpensesScreen.State(
                isLoading = false,
                isRefreshing = false,
                expenses = listOf(
                    Expense(
                        id = 1,
                        amount = 25.50,
                        category = sampleCategory,
                        description = "Lunch",
                        date = LocalDate(2024, 1, 15),
                        createdAt = Clock.System.now(),
                    ),
                    Expense(
                        id = 2,
                        amount = 12.00,
                        category = sampleCategory,
                        description = "Coffee",
                        date = LocalDate(2024, 1, 14),
                        createdAt = Clock.System.now(),
                    ),
                ),
                currencyCode = "USD",
                eventSink = {},
            )
        )
    }
}
