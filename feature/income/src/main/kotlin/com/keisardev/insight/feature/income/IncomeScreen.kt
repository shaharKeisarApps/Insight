package com.keisardev.insight.feature.income

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
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.keisardev.insight.core.common.di.AppScope
import com.keisardev.insight.core.data.repository.IncomeRepository
import com.keisardev.insight.core.model.Income
import com.keisardev.insight.core.model.IncomeCategory
import com.keisardev.insight.core.model.IncomeType
import com.keisardev.insight.core.ui.component.EmptyState
import com.keisardev.insight.core.ui.component.IncomeCategoryIconCircle
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
import kotlinx.parcelize.Parcelize

@Parcelize
data object IncomeScreen : Screen {
    data class State(
        val isLoading: Boolean,
        val incomes: List<Income>,
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data object OnAddClick : Event
        data class OnIncomeClick(val incomeId: Long) : Event
    }
}

class IncomePresenter @AssistedInject constructor(
    @Assisted private val navigator: Navigator,
    private val incomeRepository: IncomeRepository,
) : Presenter<IncomeScreen.State> {

    @Composable
    override fun present(): IncomeScreen.State {
        val incomes by incomeRepository.observeAllIncome().collectAsRetainedState(initial = emptyList())

        return IncomeScreen.State(
            isLoading = false,
            incomes = incomes,
        ) { event ->
            when (event) {
                IncomeScreen.Event.OnAddClick -> {
                    navigator.goTo(AddEditIncomeScreen(incomeId = null))
                }
                is IncomeScreen.Event.OnIncomeClick -> {
                    navigator.goTo(AddEditIncomeScreen(incomeId = event.incomeId))
                }
            }
        }
    }

    @CircuitInject(IncomeScreen::class, AppScope::class)
    @AssistedFactory
    fun interface Factory {
        fun create(navigator: Navigator): IncomePresenter
    }
}

@CircuitInject(IncomeScreen::class, AppScope::class)
@Composable
fun IncomeUi(state: IncomeScreen.State, modifier: Modifier = Modifier) {
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
                visible = isFabVisible || state.incomes.isEmpty(),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            ) {
                FloatingActionButton(
                    onClick = { state.eventSink(IncomeScreen.Event.OnAddClick) },
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add income")
                }
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
            state.incomes.isEmpty() -> {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(400)) + scaleIn(
                        initialScale = 0.9f,
                        animationSpec = tween(400)
                    ),
                ) {
                    EmptyState(
                        icon = Icons.Outlined.AccountBalanceWallet,
                        title = "No earnings recorded yet",
                        subtitle = "Tap + to add your first income",
                        modifier = Modifier.padding(paddingValues),
                    )
                }
            }
            else -> {
                IncomeList(
                    incomes = state.incomes,
                    onIncomeClick = { state.eventSink(IncomeScreen.Event.OnIncomeClick(it)) },
                    listState = listState,
                    modifier = Modifier.padding(paddingValues),
                )
            }
        }
    }
}

@Composable
private fun IncomeList(
    incomes: List<Income>,
    onIncomeClick: (Long) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(incomes, key = { it.id }) { income ->
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = tween(300)) + expandVertically(
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                ),
                exit = fadeOut(animationSpec = tween(150)) + shrinkVertically()
            ) {
                IncomeItem(
                    income = income,
                    onClick = { onIncomeClick(income.id) },
                )
            }
        }
    }
}

@Composable
private fun IncomeItem(
    income: Income,
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
            IncomeCategoryIconCircle(category = income.category)

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = income.description.ifEmpty { income.category.name },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = income.category.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IncomeTypeBadge(incomeType = income.incomeType)
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    text = formatCurrency(income.amount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                Text(
                    text = formatDateShort(income.date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun IncomeTypeBadge(
    incomeType: IncomeType,
    modifier: Modifier = Modifier,
) {
    val (text, containerColor) = when (incomeType) {
        IncomeType.RECURRING -> "Recurring" to MaterialTheme.colorScheme.primaryContainer
        IncomeType.ONE_TIME -> "One-time" to MaterialTheme.colorScheme.secondaryContainer
    }
    AssistChip(
        onClick = {},
        label = {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = containerColor,
        ),
        modifier = modifier,
    )
}
