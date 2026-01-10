package com.keisardev.insight.feature.expenses

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.keisardev.insight.core.common.di.AppScope
import com.keisardev.insight.core.data.repository.ExpenseRepository
import com.keisardev.insight.core.designsystem.theme.MetroDITestTheme
import com.keisardev.insight.core.model.Category
import com.keisardev.insight.core.model.Expense
import com.keisardev.insight.core.ui.component.CategoryIconCircle
import com.keisardev.insight.core.ui.component.EmptyState
import com.keisardev.insight.core.ui.component.color
import com.keisardev.insight.core.ui.util.formatCurrency
import com.keisardev.insight.core.ui.util.formatDateShort
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.collectAsRetainedState
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.screen.Screen
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.parcelize.Parcelize

@Parcelize
data object ExpensesScreen : Screen {
    data class State(
        val isLoading: Boolean,
        val expenses: List<Expense>,
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data object OnAddClick : Event
        data class OnExpenseClick(val expenseId: Long) : Event
    }
}

class ExpensesPresenter @AssistedInject constructor(
    @Assisted private val navigator: Navigator,
    private val expenseRepository: ExpenseRepository,
) : Presenter<ExpensesScreen.State> {

    @Composable
    override fun present(): ExpensesScreen.State {
        val expenses by expenseRepository.observeAllExpenses().collectAsRetainedState(initial = emptyList())

        return ExpensesScreen.State(
            isLoading = false,
            expenses = expenses,
        ) { event ->
            when (event) {
                ExpensesScreen.Event.OnAddClick -> {
                    navigator.goTo(AddEditExpenseScreen(expenseId = null))
                }
                is ExpensesScreen.Event.OnExpenseClick -> {
                    navigator.goTo(AddEditExpenseScreen(expenseId = event.expenseId))
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
    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { state.eventSink(ExpensesScreen.Event.OnAddClick) },
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add expense")
            }
        },
    ) { paddingValues ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            state.expenses.isEmpty() -> {
                EmptyState(
                    icon = Icons.Outlined.Receipt,
                    title = "No expenses yet",
                    subtitle = "Tap + to add your first expense",
                    modifier = Modifier.padding(paddingValues),
                )
            }
            else -> {
                ExpensesList(
                    expenses = state.expenses,
                    onExpenseClick = { state.eventSink(ExpensesScreen.Event.OnExpenseClick(it)) },
                    modifier = Modifier.padding(paddingValues),
                )
            }
        }
    }
}

@Composable
private fun ExpensesList(
    expenses: List<Expense>,
    onExpenseClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
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
                    onClick = { onExpenseClick(expense.id) },
                )
            }
        }
    }
}

@Composable
private fun ExpenseItem(
    expense: Expense,
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
                    text = formatCurrency(expense.amount),
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
    MetroDITestTheme {
        ExpensesUi(
            state = ExpensesScreen.State(
                isLoading = false,
                expenses = emptyList(),
                eventSink = {},
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewExpensesUiWithData() {
    MetroDITestTheme {
        val sampleCategory = Category(
            id = 1,
            name = "Food",
            icon = "restaurant",
            colorHex = 0xFFE57373,
        )
        ExpensesUi(
            state = ExpensesScreen.State(
                isLoading = false,
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
                eventSink = {},
            )
        )
    }
}
