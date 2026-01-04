package com.keisardev.metroditest.screens

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
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.keisardev.metroditest.data.model.Category
import com.keisardev.metroditest.data.model.Expense
import com.keisardev.metroditest.data.repository.CategoryRepository
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
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlinx.parcelize.Parcelize

@Parcelize
data class AddEditExpenseScreen(val expenseId: Long?) : Screen {
    data class State(
        val isEditMode: Boolean,
        val amount: String,
        val description: String,
        val selectedCategory: Category?,
        val selectedDate: LocalDate,
        val categories: List<Category>,
        val isSaving: Boolean,
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data class OnAmountChange(val amount: String) : Event
        data class OnDescriptionChange(val description: String) : Event
        data class OnCategorySelect(val category: Category) : Event
        data class OnDateSelect(val date: LocalDate) : Event
        data object OnSave : Event
        data object OnDelete : Event
        data object OnBack : Event
    }
}

class AddEditExpensePresenter @AssistedInject constructor(
    @Assisted private val screen: AddEditExpenseScreen,
    @Assisted private val navigator: Navigator,
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
) : Presenter<AddEditExpenseScreen.State> {

    @Composable
    override fun present(): AddEditExpenseScreen.State {
        val categories by categoryRepository.observeAllCategories().collectAsState(initial = emptyList())
        val existingExpense by expenseRepository.observeExpenseById(screen.expenseId ?: -1)
            .collectAsState(initial = null)

        var amount by rememberSaveable { mutableStateOf("") }
        var description by rememberSaveable { mutableStateOf("") }
        var selectedCategory by remember { mutableStateOf<Category?>(null) }
        var selectedDate by remember {
            mutableStateOf(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date)
        }
        var isSaving by remember { mutableStateOf(false) }
        var isInitialized by remember { mutableStateOf(false) }

        // Load existing expense data
        LaunchedEffect(existingExpense) {
            if (!isInitialized && existingExpense != null) {
                amount = existingExpense!!.amount.toString()
                description = existingExpense!!.description
                selectedCategory = existingExpense!!.category
                selectedDate = existingExpense!!.date
                isInitialized = true
            }
        }

        val scope = rememberCoroutineScope()

        return AddEditExpenseScreen.State(
            isEditMode = screen.expenseId != null,
            amount = amount,
            description = description,
            selectedCategory = selectedCategory,
            selectedDate = selectedDate,
            categories = categories,
            isSaving = isSaving,
        ) { event ->
            when (event) {
                is AddEditExpenseScreen.Event.OnAmountChange -> {
                    // Only allow valid decimal input
                    val filtered = event.amount.filter { it.isDigit() || it == '.' }
                    if (filtered.count { it == '.' } <= 1) {
                        amount = filtered
                    }
                }
                is AddEditExpenseScreen.Event.OnDescriptionChange -> {
                    description = event.description.take(100)
                }
                is AddEditExpenseScreen.Event.OnCategorySelect -> {
                    selectedCategory = event.category
                }
                is AddEditExpenseScreen.Event.OnDateSelect -> {
                    selectedDate = event.date
                }
                AddEditExpenseScreen.Event.OnSave -> {
                    val parsedAmount = amount.toDoubleOrNull()
                    val category = selectedCategory
                    if (parsedAmount != null && parsedAmount > 0 && category != null) {
                        isSaving = true
                        scope.launch {
                            val expense = Expense(
                                id = screen.expenseId ?: 0,
                                amount = parsedAmount,
                                category = category,
                                description = description,
                                date = selectedDate,
                                createdAt = existingExpense?.createdAt ?: Clock.System.now(),
                            )
                            if (screen.expenseId != null) {
                                expenseRepository.updateExpense(expense)
                            } else {
                                expenseRepository.insertExpense(expense)
                            }
                            navigator.pop()
                        }
                    }
                }
                AddEditExpenseScreen.Event.OnDelete -> {
                    screen.expenseId?.let { id ->
                        scope.launch {
                            expenseRepository.deleteExpense(id)
                            navigator.pop()
                        }
                    }
                }
                AddEditExpenseScreen.Event.OnBack -> {
                    navigator.pop()
                }
            }
        }
    }

    @CircuitInject(AddEditExpenseScreen::class, AppScope::class)
    @AssistedFactory
    fun interface Factory {
        fun create(screen: AddEditExpenseScreen, navigator: Navigator): AddEditExpensePresenter
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@CircuitInject(AddEditExpenseScreen::class, AppScope::class)
@Composable
fun AddEditExpenseUi(state: AddEditExpenseScreen.State, modifier: Modifier = Modifier) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditMode) "Edit Expense" else "Add Expense") },
                navigationIcon = {
                    IconButton(onClick = { state.eventSink(AddEditExpenseScreen.Event.OnBack) }) {
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
                        onClick = { state.eventSink(AddEditExpenseScreen.Event.OnSave) },
                        enabled = !state.isSaving && state.amount.toDoubleOrNull() != null && state.selectedCategory != null,
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
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
            // Amount field
            OutlinedTextField(
                value = state.amount,
                onValueChange = { state.eventSink(AddEditExpenseScreen.Event.OnAmountChange(it)) },
                label = { Text("Amount") },
                prefix = { Text("$") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Description field
            OutlinedTextField(
                value = state.description,
                onValueChange = { state.eventSink(AddEditExpenseScreen.Event.OnDescriptionChange(it)) },
                label = { Text("Description (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Date selector
            OutlinedTextField(
                value = formatDate(state.selectedDate),
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

            // Category selector
            Text(
                text = "Category",
                style = MaterialTheme.typography.labelLarge,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.categories.forEach { category ->
                    CategoryChip(
                        category = category,
                        isSelected = state.selectedCategory?.id == category.id,
                        onClick = { state.eventSink(AddEditExpenseScreen.Event.OnCategorySelect(category)) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Save button
            Button(
                onClick = { state.eventSink(AddEditExpenseScreen.Event.OnSave) },
                enabled = !state.isSaving && state.amount.toDoubleOrNull() != null && state.selectedCategory != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isEditMode) "Update Expense" else "Add Expense")
            }
        }
    }

    // Date picker dialog
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
                            state.eventSink(AddEditExpenseScreen.Event.OnDateSelect(date))
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

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Expense?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.eventSink(AddEditExpenseScreen.Event.OnDelete)
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
private fun CategoryChip(
    category: Category,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(category.name) },
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(category.color.copy(alpha = if (isSelected) 0.3f else 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = getCategoryIcon(category.icon),
                    contentDescription = null,
                    tint = category.color,
                    modifier = Modifier.size(16.dp),
                )
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = category.color.copy(alpha = 0.15f),
        ),
        modifier = modifier,
    )
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

private fun formatDate(date: LocalDate): String {
    val format = LocalDate.Format {
        monthName(MonthNames.ENGLISH_FULL)
        char(' ')
        dayOfMonth()
        chars(", ")
        year()
    }
    return date.format(format)
}
