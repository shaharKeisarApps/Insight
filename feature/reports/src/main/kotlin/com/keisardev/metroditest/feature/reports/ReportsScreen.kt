package com.keisardev.metroditest.feature.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.keisardev.metroditest.core.common.di.AppScope
import com.keisardev.metroditest.core.data.repository.ExpenseRepository
import com.keisardev.metroditest.core.designsystem.theme.MetroDITestTheme
import com.keisardev.metroditest.core.model.Category
import com.keisardev.metroditest.core.ui.component.EmptyState
import com.keisardev.metroditest.core.ui.component.color
import com.keisardev.metroditest.core.ui.component.getCategoryIcon
import com.keisardev.metroditest.core.ui.util.formatCurrency
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.collectAsRetainedState
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.screen.Screen
import dev.zacsweers.metro.Inject
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.parcelize.Parcelize

@Parcelize
data object ReportsScreen : Screen {
    data class State(
        val selectedMonth: Month,
        val selectedYear: Int,
        val totalSpending: Double,
        val categoryBreakdown: List<CategorySpending>,
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data object OnPreviousMonth : Event
        data object OnNextMonth : Event
    }
}

data class CategorySpending(
    val category: Category,
    val amount: Double,
    val percentage: Float,
)

@CircuitInject(ReportsScreen::class, AppScope::class)
class ReportsPresenter @Inject constructor(
    private val expenseRepository: ExpenseRepository,
) : Presenter<ReportsScreen.State> {

    @Composable
    override fun present(): ReportsScreen.State {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        var selectedMonth by rememberRetained { mutableStateOf(now.month) }
        var selectedYear by rememberRetained { mutableStateOf(now.year) }

        val startDate = LocalDate(selectedYear, selectedMonth, 1)
        val endDate = startDate.plus(1, DateTimeUnit.MONTH)

        val totalSpending by expenseRepository.observeMonthlyTotal(startDate, endDate)
            .collectAsRetainedState(initial = 0.0)

        val categoryTotals by expenseRepository.observeTotalByCategory(startDate, endDate)
            .collectAsRetainedState(initial = emptyMap())

        val categoryBreakdown = categoryTotals.map { (category, amount) ->
            CategorySpending(
                category = category,
                amount = amount,
                percentage = if (totalSpending > 0) (amount / totalSpending).toFloat() else 0f,
            )
        }.sortedByDescending { it.amount }

        return ReportsScreen.State(
            selectedMonth = selectedMonth,
            selectedYear = selectedYear,
            totalSpending = totalSpending,
            categoryBreakdown = categoryBreakdown,
        ) { event ->
            when (event) {
                ReportsScreen.Event.OnPreviousMonth -> {
                    val newDate = startDate.minus(1, DateTimeUnit.MONTH)
                    selectedMonth = newDate.month
                    selectedYear = newDate.year
                }
                ReportsScreen.Event.OnNextMonth -> {
                    val newDate = startDate.plus(1, DateTimeUnit.MONTH)
                    selectedMonth = newDate.month
                    selectedYear = newDate.year
                }
            }
        }
    }
}

@CircuitInject(ReportsScreen::class, AppScope::class)
@Composable
fun ReportsUi(state: ReportsScreen.State, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        MonthSelector(
            month = state.selectedMonth,
            year = state.selectedYear,
            onPrevious = { state.eventSink(ReportsScreen.Event.OnPreviousMonth) },
            onNext = { state.eventSink(ReportsScreen.Event.OnNextMonth) },
        )

        Spacer(modifier = Modifier.height(24.dp))

        TotalSpendingCard(totalSpending = state.totalSpending)

        Spacer(modifier = Modifier.height(24.dp))

        if (state.categoryBreakdown.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.BarChart,
                title = "No expenses this month",
            )
        } else {
            Text(
                text = "By Category",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.categoryBreakdown) { spending ->
                    CategoryBreakdownItem(spending = spending)
                }
            }
        }
    }
}

@Composable
private fun MonthSelector(
    month: Month,
    year: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
        }
        Text(
            text = "${month.name.lowercase().replaceFirstChar { it.uppercase() }} $year",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
        }
    }
}

@Composable
private fun TotalSpendingCard(
    totalSpending: Double,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Total Spending",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formatCurrency(totalSpending),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun CategoryBreakdownItem(
    spending: CategorySpending,
    modifier: Modifier = Modifier,
) {
    val categoryColor = spending.category.color
    Card(
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(categoryColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = getCategoryIcon(spending.category.icon),
                    contentDescription = spending.category.name,
                    tint = categoryColor,
                    modifier = Modifier.size(20.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = spending.category.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = formatCurrency(spending.amount),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { spending.percentage },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = categoryColor,
                    trackColor = categoryColor.copy(alpha = 0.2f),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${(spending.percentage * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewReportsUiEmpty() {
    MetroDITestTheme {
        ReportsUi(
            state = ReportsScreen.State(
                selectedMonth = Month.JANUARY,
                selectedYear = 2024,
                totalSpending = 0.0,
                categoryBreakdown = emptyList(),
                eventSink = {},
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewReportsUiWithData() {
    MetroDITestTheme {
        val foodCategory = Category(1, "Food", "restaurant", 0xFFE57373)
        val transportCategory = Category(2, "Transport", "directions_car", 0xFF64B5F6)
        ReportsUi(
            state = ReportsScreen.State(
                selectedMonth = Month.JANUARY,
                selectedYear = 2024,
                totalSpending = 450.00,
                categoryBreakdown = listOf(
                    CategorySpending(foodCategory, 300.0, 0.67f),
                    CategorySpending(transportCategory, 150.0, 0.33f),
                ),
                eventSink = {},
            )
        )
    }
}
