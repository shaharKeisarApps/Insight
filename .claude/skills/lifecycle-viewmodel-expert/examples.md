# Lifecycle & ViewModel Examples

## 1. ViewModel with Metro @AssistedInject and SavedStateHandle

```kotlin
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metro.viewmodel.ManualViewModelAssistedFactoryKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class TaskListViewModel @AssistedInject constructor(
    @Assisted private val projectId: String,
    @Assisted private val savedStateHandle: SavedStateHandle,
    private val taskRepository: TaskRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    // Reactive saved state -- survives process death
    val filterQuery: StateFlow<String> = savedStateHandle.getStateFlow(
        key = FILTER_KEY,
        initialValue = ""
    )

    private val _uiState = MutableStateFlow<TaskListUiState>(TaskListUiState.Loading)
    val uiState: StateFlow<TaskListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                taskRepository.observeTasks(projectId),
                filterQuery,
                userPreferences.sortOrder()
            ) { tasks, filter, sort ->
                val filtered = if (filter.isBlank()) tasks
                    else tasks.filter { it.title.contains(filter, ignoreCase = true) }
                val sorted = when (sort) {
                    SortOrder.NEWEST -> filtered.sortedByDescending { it.createdAt }
                    SortOrder.PRIORITY -> filtered.sortedByDescending { it.priority }
                }
                TaskListUiState.Content(sorted)
            }.collect { _uiState.value = it }
        }
    }

    fun setFilter(query: String) {
        savedStateHandle[FILTER_KEY] = query
    }

    fun toggleComplete(taskId: String) {
        viewModelScope.launch {
            taskRepository.toggleComplete(taskId)
        }
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey(Factory::class)
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(projectId: String, savedStateHandle: SavedStateHandle): TaskListViewModel
    }

    companion object {
        private const val FILTER_KEY = "filter_query"
    }
}

sealed interface TaskListUiState {
    data object Loading : TaskListUiState
    data class Content(val tasks: List<Task>) : TaskListUiState
}
```

## 2. Navigation 3 Entry with rememberViewModelStoreNavEntryDecorator

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.NavDisplay
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.savedstate.navigation3.rememberSaveableStateHolderNavEntryDecorator
import dev.zacsweers.metro.viewmodel.compose.LocalMetroViewModelFactory
import dev.zacsweers.metro.viewmodel.compose.metroViewModel
import kotlinx.serialization.Serializable

// Route definitions
@Serializable
data class TaskListRoute(val projectId: String)

@Serializable
data class TaskDetailRoute(val taskId: String)

@Composable
fun AppNavHost(appGraph: AppGraph) {
    val backStack = rememberNavBackStack(TaskListRoute("default-project"))

    CompositionLocalProvider(
        LocalMetroViewModelFactory provides appGraph.metroViewModelFactory
    ) {
        NavDisplay(
            backStack = backStack,
            entryDecorators = listOf(
                // Both decorators required: ViewModel scoping + saveable state
                rememberViewModelStoreNavEntryDecorator(),
                rememberSaveableStateHolderNavEntryDecorator()
            ),
            entryProvider = entryProvider {
                entry<TaskListRoute> { route ->
                    val factory = appGraph.metroViewModelFactory
                        .getManualAssistedFactory<TaskListViewModel.Factory>()
                    val viewModel = metroViewModel {
                        factory.create(route.projectId, it.createSavedStateHandle())
                    }
                    TaskListScreen(
                        viewModel = viewModel,
                        onTaskClick = { taskId ->
                            backStack.add(TaskDetailRoute(taskId))
                        }
                    )
                }

                entry<TaskDetailRoute> { route ->
                    val factory = appGraph.metroViewModelFactory
                        .getManualAssistedFactory<TaskDetailViewModel.Factory>()
                    val viewModel = metroViewModel {
                        factory.create(route.taskId)
                    }
                    TaskDetailScreen(viewModel = viewModel)
                }
            }
        )
    }
}
```

## 3. collectAsStateWithLifecycle in Compose

```kotlin
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun TaskListScreen(
    viewModel: TaskListViewModel,
    onTaskClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Collects when lifecycle >= STARTED (default), pauses in background
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val filterQuery by viewModel.filterQuery.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        TextField(
            value = filterQuery,
            onValueChange = { viewModel.setFilter(it) },
            placeholder = { Text("Filter tasks...") }
        )

        when (val state = uiState) {
            is TaskListUiState.Loading -> CircularProgressIndicator()
            is TaskListUiState.Content -> {
                LazyColumn {
                    items(state.tasks, key = { it.id }) { task ->
                        TaskRow(
                            task = task,
                            onClick = { onTaskClick(task.id) },
                            onToggle = { viewModel.toggleComplete(task.id) }
                        )
                    }
                }
            }
        }
    }
}
```

### Using minActiveState for sensor data

```kotlin
@Composable
fun CameraPreviewScreen(viewModel: CameraViewModel) {
    // Only collect when RESUMED (camera is active only when fully visible)
    val frame by viewModel.cameraFrames.collectAsStateWithLifecycle(
        minActiveState = Lifecycle.State.RESUMED
    )

    CameraPreview(frame = frame)
}
```

## 4. ViewModel Unit Test with coroutines-test

```kotlin
import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class TaskListViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeRepository: FakeTaskRepository
    private lateinit var fakePreferences: FakeUserPreferences

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeTaskRepository()
        fakePreferences = FakeUserPreferences()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        projectId: String = "project-1",
        savedStateHandle: SavedStateHandle = SavedStateHandle()
    ) = TaskListViewModel(
        projectId = projectId,
        savedStateHandle = savedStateHandle,
        taskRepository = fakeRepository,
        userPreferences = fakePreferences
    )

    @Test
    fun `initial state is Loading then Content`() = runTest {
        fakeRepository.setTasks(listOf(Task("1", "Buy milk", Priority.LOW)))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<TaskListUiState.Content>(state)
            assertEquals(1, state.tasks.size)
            assertEquals("Buy milk", state.tasks.first().title)
        }
    }

    @Test
    fun `setFilter updates saved state and filters tasks`() = runTest {
        fakeRepository.setTasks(
            listOf(
                Task("1", "Buy milk", Priority.LOW),
                Task("2", "Write tests", Priority.HIGH)
            )
        )

        val viewModel = createViewModel()

        viewModel.setFilter("milk")

        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<TaskListUiState.Content>(state)
            assertEquals(1, state.tasks.size)
            assertEquals("Buy milk", state.tasks.first().title)
        }
    }

    @Test
    fun `filter query survives process death via SavedStateHandle`() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf("filter_query" to "milk"))
        fakeRepository.setTasks(
            listOf(
                Task("1", "Buy milk", Priority.LOW),
                Task("2", "Write tests", Priority.HIGH)
            )
        )

        val viewModel = createViewModel(savedStateHandle = savedStateHandle)

        viewModel.filterQuery.test {
            assertEquals("milk", awaitItem())
        }
    }
}
```

## 5. Comparison: Same Logic as Circuit Presenter vs ViewModel

### Circuit Presenter version

```kotlin
class TaskListPresenter @AssistedInject constructor(
    @Assisted private val navigator: Navigator,
    private val taskRepository: TaskRepository
) : Presenter<TaskListScreen.State> {

    @Composable
    override fun present(): TaskListScreen.State {
        var filter by rememberRetained { mutableStateOf("") }
        val tasks by produceRetainedState<List<Task>>(emptyList()) {
            taskRepository.observeTasks("default").collect { value = it }
        }

        val filtered = remember(tasks, filter) {
            if (filter.isBlank()) tasks
            else tasks.filter { it.title.contains(filter, ignoreCase = true) }
        }

        return TaskListScreen.State(
            tasks = filtered,
            filterQuery = filter,
            eventSink = { event ->
                when (event) {
                    is TaskListScreen.Event.SetFilter -> filter = event.query
                    is TaskListScreen.Event.ToggleComplete ->
                        taskRepository.toggleComplete(event.taskId)
                    is TaskListScreen.Event.TaskClicked ->
                        navigator.goTo(TaskDetailScreen(event.taskId))
                }
            }
        )
    }
}
```

### ViewModel version (same behavior)

```kotlin
class TaskListViewModel @AssistedInject constructor(
    @Assisted private val savedStateHandle: SavedStateHandle,
    private val taskRepository: TaskRepository
) : ViewModel() {

    val filterQuery: StateFlow<String> = savedStateHandle.getStateFlow("filter", "")

    val uiState: StateFlow<TaskListUiState> = combine(
        taskRepository.observeTasks("default"),
        filterQuery
    ) { tasks, filter ->
        val filtered = if (filter.isBlank()) tasks
            else tasks.filter { it.title.contains(filter, ignoreCase = true) }
        TaskListUiState.Content(filtered)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskListUiState.Loading)

    fun setFilter(query: String) { savedStateHandle["filter"] = query }
    fun toggleComplete(taskId: String) {
        viewModelScope.launch { taskRepository.toggleComplete(taskId) }
    }
}
```

### Key differences

| Aspect | Circuit Presenter | ViewModel |
|--------|-------------------|-----------|
| State retention | `rememberRetained` (Compose) | `ViewModelStore` (platform) |
| Process death | `rememberRetainedSaveable` | `SavedStateHandle` |
| Flow collection | `produceRetainedState` in `@Composable` | `stateIn` + `viewModelScope` |
| Navigation | `navigator.goTo(...)` inside `eventSink` | Callback lambda from Composable |
| Testing | Molecule + Turbine | `runTest` + `Dispatchers.setMain` |
| Event model | `eventSink: (Event) -> Unit` in state | Direct method calls on ViewModel |
