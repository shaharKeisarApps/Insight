package com.keisardev.metroditest.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.keisardev.metroditest.data.model.Expense
import com.keisardev.metroditest.data.repository.ExpenseRepository
import com.keisardev.metroditest.di.AppScope
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.screen.Screen
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import kotlinx.parcelize.Parcelize
import java.text.NumberFormat
import java.util.Locale

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
        val expenses by expenseRepository.observeAllExpenses().collectAsState(initial = emptyList())

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
                EmptyExpensesState(modifier = Modifier.padding(paddingValues))
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
private fun EmptyExpensesState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Receipt,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "No expenses yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Tap + to add your first expense",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
            ExpenseItem(
                expense = expense,
                onClick = { onExpenseClick(expense.id) },
            )
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
            // Category icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(expense.category.color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = getCategoryIcon(expense.category.icon),
                    contentDescription = expense.category.name,
                    tint = expense.category.color,
                    modifier = Modifier.size(24.dp),
                )
            }

            // Description and category
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

            // Amount and date
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
                    text = formatDate(expense.date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun getCategoryIcon(iconName: String): ImageVector {
    return when (iconName) {
        "restaurant" -> Icons.Default.Restaurant
        "directions_car" -> Icons.Default.DirectionsCar
        "movie" -> Icons.Default.Movie
        "shopping_bag" -> Icons.Default.ShoppingBag
        "receipt" -> Icons.Default.Receipt
        "medical_services" -> Icons.Default.HealthAndSafety
        else -> Icons.Default.MoreHoriz
    }
}

private fun formatCurrency(amount: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale.US)
    return format.format(amount)
}

private fun formatDate(date: LocalDate): String {
    val format = LocalDate.Format {
        monthName(MonthNames.ENGLISH_ABBREVIATED)
        char(' ')
        dayOfMonth()
    }
    return date.format(format)
}
