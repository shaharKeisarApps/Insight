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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.keisardev.metroditest.core.common.di.AppScope
import com.keisardev.metroditest.core.data.repository.ExpenseRepository
import com.keisardev.metroditest.core.data.repository.FinancialSummaryRepository
import com.keisardev.metroditest.core.data.repository.IncomeRepository
import com.keisardev.metroditest.core.model.Category
import com.keisardev.metroditest.core.model.FinancialSummary
import com.keisardev.metroditest.core.model.IncomeCategory
import com.keisardev.metroditest.core.model.ReportViewType
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
        val selectedViewType: ReportViewType,
        val totalSpending: Double,
        val totalIncome: Double,
        val categoryBreakdown: List<CategorySpending>,
        val incomeCategoryBreakdown: List<IncomeCategorySpending>,
        val financialSummary: FinancialSummary,
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data object OnPreviousMonth : Event
        data object OnNextMonth : Event
        data class OnViewTypeChange(val viewType: ReportViewType) : Event
    }
}

data class CategorySpending(
    val category: Category,
    val amount: Double,
    val percentage: Float,
)

data class IncomeCategorySpending(
    val category: IncomeCategory,
    val amount: Double,
    val percentage: Float,
)

@CircuitInject(ReportsScreen::class, AppScope::class)
class ReportsPresenter @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val financialSummaryRepository: FinancialSummaryRepository,
) : Presenter<ReportsScreen.State> {

    @Composable
    override fun present(): ReportsScreen.State {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        var selectedMonth by rememberRetained { mutableStateOf(now.month) }
        var selectedYear by rememberRetained { mutableStateOf(now.year) }
        var selectedViewType by rememberRetained { mutableStateOf(ReportViewType.SPENDING) }

        val startDate = LocalDate(selectedYear, selectedMonth, 1)
        val endDate = startDate.plus(1, DateTimeUnit.MONTH)

        val totalSpending by expenseRepository.observeMonthlyTotal(startDate, endDate)
            .collectAsRetainedState(initial = 0.0)

        val totalIncome by incomeRepository.observeMonthlyTotal(startDate, endDate)
            .collectAsRetainedState(initial = 0.0)

        val categoryTotals by expenseRepository.observeTotalByCategory(startDate, endDate)
            .collectAsRetainedState(initial = emptyMap())

        val incomeCategoryTotals by incomeRepository.observeTotalByCategory(startDate, endDate)
            .collectAsRetainedState(initial = emptyMap())

        val financialSummary by financialSummaryRepository.observeFinancialSummary(startDate, endDate)
            .collectAsRetainedState(
                initial = FinancialSummary(
                    totalIncome = 0.0,
                    totalExpenses = 0.0,
                )
            )

        val categoryBreakdown = categoryTotals.map { (category, amount) ->
            CategorySpending(
                category = category,
                amount = amount,
                percentage = if (totalSpending > 0) (amount / totalSpending).toFloat() else 0f,
            )
        }.sortedByDescending { it.amount }

        val incomeCategoryBreakdown = incomeCategoryTotals.map { (category, amount) ->
            IncomeCategorySpending(
                category = category,
                amount = amount,
                percentage = if (totalIncome > 0) (amount / totalIncome).toFloat() else 0f,
            )
        }.sortedByDescending { it.amount }

        return ReportsScreen.State(
            selectedMonth = selectedMonth,
            selectedYear = selectedYear,
            selectedViewType = selectedViewType,
            totalSpending = totalSpending,
            totalIncome = totalIncome,
            categoryBreakdown = categoryBreakdown,
            incomeCategoryBreakdown = incomeCategoryBreakdown,
            financialSummary = financialSummary,
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
                is ReportsScreen.Event.OnViewTypeChange -> {
                    selectedViewType = event.viewType
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

        Spacer(modifier = Modifier.height(16.dp))

        ReportViewTypeChips(
            selectedViewType = state.selectedViewType,
            onViewTypeChange = { state.eventSink(ReportsScreen.Event.OnViewTypeChange(it)) },
        )

        Spacer(modifier = Modifier.height(24.dp))

        when (state.selectedViewType) {
            ReportViewType.SPENDING -> SpendingView(
                totalSpending = state.totalSpending,
                categoryBreakdown = state.categoryBreakdown,
            )
            ReportViewType.EARNINGS -> EarningsView(
                totalIncome = state.totalIncome,
                incomeCategoryBreakdown = state.incomeCategoryBreakdown,
            )
            ReportViewType.BALANCE -> BalanceView(
                financialSummary = state.financialSummary,
            )
        }
    }
}

@Composable
private fun ReportViewTypeChips(
    selectedViewType: ReportViewType,
    onViewTypeChange: (ReportViewType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ReportViewType.entries.forEach { viewType ->
            FilterChip(
                selected = selectedViewType == viewType,
                onClick = { onViewTypeChange(viewType) },
                label = { Text(viewType.displayName) },
                leadingIcon = {
                    Icon(
                        imageVector = getViewTypeIcon(viewType),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}

private fun getViewTypeIcon(viewType: ReportViewType): ImageVector {
    return when (viewType) {
        ReportViewType.SPENDING -> Icons.Default.Payments
        ReportViewType.EARNINGS -> Icons.Default.AccountBalanceWallet
        ReportViewType.BALANCE -> Icons.Default.Balance
    }
}

@Composable
private fun SpendingView(
    totalSpending: Double,
    categoryBreakdown: List<CategorySpending>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        TotalCard(
            title = "Total Spending",
            amount = totalSpending,
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (categoryBreakdown.isEmpty()) {
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
                items(categoryBreakdown) { spending ->
                    CategoryBreakdownItem(spending = spending)
                }
            }
        }
    }
}

@Composable
private fun EarningsView(
    totalIncome: Double,
    incomeCategoryBreakdown: List<IncomeCategorySpending>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        TotalCard(
            title = "Total Earnings",
            amount = totalIncome,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (incomeCategoryBreakdown.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.BarChart,
                title = "No earnings this month",
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
                items(incomeCategoryBreakdown) { spending ->
                    IncomeCategoryBreakdownItem(spending = spending)
                }
            }
        }
    }
}

@Composable
private fun BalanceView(
    financialSummary: FinancialSummary,
    modifier: Modifier = Modifier,
) {
    val isSaving = financialSummary.isSaving
    val balanceColor = if (isSaving) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.error
    }

    Column(modifier = modifier) {
        // Balance Summary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isSaving) {
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                },
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (isSaving) "You're saving money!" else "Spending exceeds income",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSaving) {
                        MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                    },
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = formatCurrency(kotlin.math.abs(financialSummary.netBalance)),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = balanceColor,
                )
                if (financialSummary.totalIncome > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Savings rate: ${financialSummary.savingsRate.toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSaving) {
                            MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                        },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Income and Expense Summary
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Income",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatCurrency(financialSummary.totalIncome),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Expenses",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatCurrency(financialSummary.totalExpenses),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
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
private fun TotalCard(
    title: String,
    amount: Double,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor.copy(alpha = 0.7f),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formatCurrency(amount),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = contentColor,
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

@Composable
private fun IncomeCategoryBreakdownItem(
    spending: IncomeCategorySpending,
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
