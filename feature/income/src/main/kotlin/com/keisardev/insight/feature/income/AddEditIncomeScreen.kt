package com.keisardev.insight.feature.income

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.keisardev.insight.core.common.di.AppScope
import com.keisardev.insight.core.data.repository.IncomeCategoryRepository
import com.keisardev.insight.core.data.repository.IncomeRepository
import com.keisardev.insight.core.model.Income
import com.keisardev.insight.core.model.IncomeCategory
import com.keisardev.insight.core.model.IncomeType
import com.keisardev.insight.core.ui.component.color
import com.keisardev.insight.core.ui.component.getCategoryIcon
import com.keisardev.insight.core.ui.util.formatDateFull
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.collectAsRetainedState
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.retained.rememberRetainedSaveable
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.parcelize.Parcelize

@Parcelize
data class AddEditIncomeScreen(val incomeId: Long?) : Screen {
    data class State(
        val isEditMode: Boolean,
        val amount: String,
        val description: String,
        val selectedIncomeType: IncomeType,
        val selectedCategory: IncomeCategory?,
        val selectedDate: LocalDate,
        val categories: List<IncomeCategory>,
        val isSaving: Boolean,
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data class OnAmountChange(val amount: String) : Event
        data class OnDescriptionChange(val description: String) : Event
        data class OnIncomeTypeChange(val incomeType: IncomeType) : Event
        data class OnCategorySelect(val category: IncomeCategory) : Event
        data class OnDateSelect(val date: LocalDate) : Event
        data object OnSave : Event
        data object OnDelete : Event
        data object OnBack : Event
    }
}

class AddEditIncomePresenter @AssistedInject constructor(
    @Assisted private val screen: AddEditIncomeScreen,
    @Assisted private val navigator: Navigator,
    private val incomeRepository: IncomeRepository,
    private val incomeCategoryRepository: IncomeCategoryRepository,
) : Presenter<AddEditIncomeScreen.State> {

    @Composable
    override fun present(): AddEditIncomeScreen.State {
        val categories by incomeCategoryRepository.observeAllCategories().collectAsRetainedState(initial = emptyList())
        val existingIncome by incomeRepository.observeIncomeById(screen.incomeId ?: -1)
            .collectAsRetainedState(initial = null)

        var amount by rememberRetainedSaveable { mutableStateOf("") }
        var description by rememberRetainedSaveable { mutableStateOf("") }
        var selectedIncomeType by rememberRetained { mutableStateOf(IncomeType.ONE_TIME) }
        var selectedCategory by rememberRetained { mutableStateOf<IncomeCategory?>(null) }
        var selectedDate by rememberRetained {
            mutableStateOf(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date)
        }
        var isSaving by rememberRetained { mutableStateOf(false) }
        var isInitialized by rememberRetained { mutableStateOf(false) }

        LaunchedEffect(existingIncome) {
            if (!isInitialized && existingIncome != null) {
                amount = existingIncome!!.amount.toString()
                description = existingIncome!!.description
                selectedIncomeType = existingIncome!!.incomeType
                selectedCategory = existingIncome!!.category
                selectedDate = existingIncome!!.date
                isInitialized = true
            }
        }

        val scope = rememberCoroutineScope()

        return AddEditIncomeScreen.State(
            isEditMode = screen.incomeId != null,
            amount = amount,
            description = description,
            selectedIncomeType = selectedIncomeType,
            selectedCategory = selectedCategory,
            selectedDate = selectedDate,
            categories = categories,
            isSaving = isSaving,
        ) { event ->
            when (event) {
                is AddEditIncomeScreen.Event.OnAmountChange -> {
                    val filtered = event.amount.filter { it.isDigit() || it == '.' }
                    if (filtered.count { it == '.' } <= 1) {
                        amount = filtered
                    }
                }
                is AddEditIncomeScreen.Event.OnDescriptionChange -> {
                    description = event.description.take(100)
                }
                is AddEditIncomeScreen.Event.OnIncomeTypeChange -> {
                    selectedIncomeType = event.incomeType
                }
                is AddEditIncomeScreen.Event.OnCategorySelect -> {
                    selectedCategory = event.category
                }
                is AddEditIncomeScreen.Event.OnDateSelect -> {
                    selectedDate = event.date
                }
                AddEditIncomeScreen.Event.OnSave -> {
                    val parsedAmount = amount.toDoubleOrNull()
                    val category = selectedCategory
                    if (parsedAmount != null && parsedAmount > 0 && category != null) {
                        isSaving = true
                        scope.launch {
                            val income = Income(
                                id = screen.incomeId ?: 0,
                                amount = parsedAmount,
                                incomeType = selectedIncomeType,
                                category = category,
                                description = description,
                                date = selectedDate,
                                createdAt = existingIncome?.createdAt ?: Clock.System.now(),
                            )
                            if (screen.incomeId != null) {
                                incomeRepository.updateIncome(income)
                            } else {
                                incomeRepository.insertIncome(income)
                            }
                            navigator.pop()
                        }
                    }
                }
                AddEditIncomeScreen.Event.OnDelete -> {
                    screen.incomeId?.let { id ->
                        scope.launch {
                            incomeRepository.deleteIncome(id)
                            navigator.pop()
                        }
                    }
                }
                AddEditIncomeScreen.Event.OnBack -> {
                    navigator.pop()
                }
            }
        }
    }

    @CircuitInject(AddEditIncomeScreen::class, AppScope::class)
    @AssistedFactory
    fun interface Factory {
        fun create(screen: AddEditIncomeScreen, navigator: Navigator): AddEditIncomePresenter
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@CircuitInject(AddEditIncomeScreen::class, AppScope::class)
@Composable
fun AddEditIncomeUi(state: AddEditIncomeScreen.State, modifier: Modifier = Modifier) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditMode) "Edit Income" else "Add Income") },
                navigationIcon = {
                    IconButton(onClick = { state.eventSink(AddEditIncomeScreen.Event.OnBack) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.isEditMode) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                    IconButton(
                        onClick = { state.eventSink(AddEditIncomeScreen.Event.OnSave) },
                        enabled = !state.isSaving && state.amount.toDoubleOrNull() != null && state.selectedCategory != null,
                    ) {
                        AnimatedContent(
                            targetState = state.isSaving,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(200)) togetherWith
                                fadeOut(animationSpec = tween(200))
                            },
                            label = "save_button"
                        ) { isSaving ->
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(Icons.Default.Check, contentDescription = "Save")
                            }
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Income Type Toggle
            Text(
                text = "Income Type",
                style = MaterialTheme.typography.labelLarge,
            )
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth(),
            ) {
                IncomeType.entries.forEachIndexed { index, incomeType ->
                    SegmentedButton(
                        selected = state.selectedIncomeType == incomeType,
                        onClick = { state.eventSink(AddEditIncomeScreen.Event.OnIncomeTypeChange(incomeType)) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = IncomeType.entries.size),
                    ) {
                        Text(incomeType.displayName)
                    }
                }
            }

            OutlinedTextField(
                value = state.amount,
                onValueChange = { state.eventSink(AddEditIncomeScreen.Event.OnAmountChange(it)) },
                label = { Text("Amount") },
                prefix = { Text("$") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.description,
                onValueChange = { state.eventSink(AddEditIncomeScreen.Event.OnDescriptionChange(it)) },
                label = { Text("Description (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = formatDateFull(state.selectedDate),
                onValueChange = {},
                label = { Text("Date") },
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = "Select date")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true },
            )

            Text(
                text = "Category",
                style = MaterialTheme.typography.labelLarge,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.categories.forEach { category ->
                    IncomeCategoryChip(
                        category = category,
                        isSelected = state.selectedCategory?.id == category.id,
                        onClick = { state.eventSink(AddEditIncomeScreen.Event.OnCategorySelect(category)) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { state.eventSink(AddEditIncomeScreen.Event.OnSave) },
                enabled = !state.isSaving && state.amount.toDoubleOrNull() != null && state.selectedCategory != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isEditMode) "Update Income" else "Add Income")
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.selectedDate
                .atStartOfDayIn(TimeZone.currentSystemDefault())
                .toEpochMilliseconds()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val date = Instant.fromEpochMilliseconds(millis)
                                .toLocalDateTime(TimeZone.currentSystemDefault()).date
                            state.eventSink(AddEditIncomeScreen.Event.OnDateSelect(date))
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Income?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.eventSink(AddEditIncomeScreen.Event.OnDelete)
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun IncomeCategoryChip(
    category: IncomeCategory,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val categoryColor = category.color

    // Animate selection state with subtle scale and color transition
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "chip_scale"
    )

    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(category.name) },
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(categoryColor.copy(alpha = if (isSelected) 0.3f else 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = getCategoryIcon(category.icon),
                    contentDescription = null,
                    tint = categoryColor,
                    modifier = Modifier.size(16.dp),
                )
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = categoryColor.copy(alpha = 0.15f),
        ),
        modifier = modifier.scale(scale),
    )
}
