# Lifecycle & ViewModel API Reference

## Dependencies (libs.versions.toml)

```toml
[versions]
lifecycle = "2.9.6"

[libraries]
# Core ViewModel (KMP)
androidx-lifecycle-viewmodel = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel", version.ref = "lifecycle" }

# ViewModel + Compose integration (KMP)
androidx-lifecycle-viewmodel-compose = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }

# SavedStateHandle (KMP)
androidx-lifecycle-viewmodel-savedstate = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-savedstate", version.ref = "lifecycle" }

# Runtime (repeatOnLifecycle) (KMP)
androidx-lifecycle-runtime = { module = "org.jetbrains.androidx.lifecycle:lifecycle-runtime", version.ref = "lifecycle" }

# Compose lifecycle extensions (collectAsStateWithLifecycle) (KMP)
androidx-lifecycle-runtime-compose = { module = "org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }

# Testing utilities (KMP)
androidx-lifecycle-viewmodel-testing = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-testing", version.ref = "lifecycle" }
```

---

## ViewModel

Base class for managing UI-related data in a lifecycle-conscious way. Survives configuration changes and is scoped to a `ViewModelStoreOwner`.

```kotlin
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
```

### Constructor

```kotlin
// No-arg constructor
class SimpleViewModel : ViewModel()

// Constructor with AutoCloseable resources
class ResourceViewModel(resource: AutoCloseable) : ViewModel(resource)

// Constructor with SavedStateHandle
class StatefulViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel()
```

### Properties and Methods

| Member | Signature | Description |
|--------|-----------|-------------|
| `viewModelScope` | `val viewModelScope: CoroutineScope` | Auto-cancelled scope. Uses `Dispatchers.Main.immediate`. Cancelled when `onCleared()` is called. |
| `onCleared()` | `protected open fun onCleared()` | Called when the ViewModel is permanently destroyed. Override for cleanup of resources not registered via `addCloseable`. |
| `addCloseable(key, closeable)` | `fun addCloseable(key: String, closeable: AutoCloseable)` | Register a keyed closeable. Closed on `onCleared()`. Retrieve later with `getCloseable(key)`. |
| `addCloseable(closeable)` | `fun addCloseable(closeable: AutoCloseable)` | Register an anonymous closeable. |
| `getCloseable(key)` | `fun <T : AutoCloseable> getCloseable(key: String): T?` | Retrieve a previously added keyed closeable. |

---

## SavedStateHandle

Key-value map that survives process death. Works on all KMP targets (state serialized to Bundle on Android, in-memory on other platforms).

### Core API

| Method | Signature | Description |
|--------|-----------|-------------|
| `get` | `operator fun <T> get(key: String): T?` | Retrieve a value by key. Returns `null` if absent. |
| `set` | `operator fun <T> set(key: String, value: T?)` | Store a value. Pass `null` to remove. |
| `getStateFlow` | `fun <T> getStateFlow(key: String, initialValue: T): StateFlow<T>` | Returns a `StateFlow` that emits whenever the value for `key` changes. |
| `getLiveData` | `fun <T> getLiveData(key: String): MutableLiveData<T?>` | Android-only. Returns LiveData backed by saved state. |
| `getLiveData` | `fun <T> getLiveData(key: String, initialValue: T): MutableLiveData<T>` | Android-only. With default value. |
| `contains` | `fun contains(key: String): Boolean` | Check if key exists. |
| `remove` | `fun <T> remove(key: String): T?` | Remove and return the value for key. |
| `keys` | `fun keys(): Set<String>` | All stored keys. |
| `setSavedStateProvider` | `fun setSavedStateProvider(key: String, provider: SavedStateRegistry.SavedStateProvider)` | Register a provider for custom bundle serialization. |
| `clearSavedStateProvider` | `fun clearSavedStateProvider(key: String)` | Remove a custom provider. |

### Supported Types (Android)

Primitives (`Int`, `Long`, `Float`, `Double`, `Boolean`, `Byte`, `Char`, `Short`), `String`, `CharSequence`, `Parcelable`, `Serializable`, `Bundle`, arrays and array lists of the above.

---

## collectAsStateWithLifecycle

Collects a `Flow` as Compose `State`, pausing collection when the lifecycle drops below `minActiveState`.

```kotlin
import androidx.lifecycle.compose.collectAsStateWithLifecycle
```

### Overloads

```kotlin
// StateFlow -- no initial value needed
@Composable
fun <T> StateFlow<T>.collectAsStateWithLifecycle(
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    context: CoroutineContext = EmptyCoroutineContext,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
): State<T>

// Flow -- requires initial value
@Composable
fun <T> Flow<T>.collectAsStateWithLifecycle(
    initialValue: T,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    context: CoroutineContext = EmptyCoroutineContext,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
): State<T>
```

### Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `minActiveState` | `STARTED` | Collection pauses when lifecycle drops below this state. Use `RESUMED` for camera/sensor streams. |
| `context` | `EmptyCoroutineContext` | Additional coroutine context for the collection coroutine. |
| `lifecycleOwner` | `LocalLifecycleOwner.current` | The lifecycle owner controlling collection. Override for custom scoping. |

---

## repeatOnLifecycle

Suspending function that launches a block each time the lifecycle reaches a target state and cancels it when the lifecycle drops below.

```kotlin
import androidx.lifecycle.repeatOnLifecycle

suspend fun LifecycleOwner.repeatOnLifecycle(
    state: Lifecycle.State,
    block: suspend CoroutineScope.() -> Unit
)
```

### Behavior

- Block is launched when lifecycle reaches the target state.
- Block is cancelled when lifecycle drops below the target state.
- Block is re-launched if lifecycle reaches the target state again.
- The calling coroutine suspends until the lifecycle reaches `DESTROYED`.
- Commonly used inside `lifecycleScope.launch { }` (non-Compose contexts).

---

## ViewModelProvider.Factory with Metro DI

### Simple ViewModel (no runtime params)

```kotlin
@Inject
@ViewModelKey(HomeViewModel::class)
@ContributesIntoMap(AppScope::class)
class HomeViewModel(
    private val repository: UserRepository
) : ViewModel()
```

### Assisted ViewModel (runtime params via CreationExtras)

```kotlin
@AssistedInject
class DetailViewModel(
    @Assisted val itemId: String,
    private val repository: ItemRepository
) : ViewModel() {

    @AssistedFactory
    @ViewModelAssistedFactoryKey(Factory::class)
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ViewModelAssistedFactory {
        override fun create(extras: CreationExtras): DetailViewModel {
            return create(extras[ITEM_ID_KEY] as String)
        }
        fun create(@Assisted itemId: String): DetailViewModel
    }

    companion object {
        val ITEM_ID_KEY = object : CreationExtras.Key<String> {}
    }
}
```

### MetroViewModelFactory (central factory in the graph)

```kotlin
@Inject
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class AppViewModelFactory(
    override val viewModelProviders: Map<KClass<out ViewModel>, Provider<ViewModel>>,
    override val assistedFactoryProviders: Map<KClass<out ViewModel>, Provider<ViewModelAssistedFactory>>,
    override val manualAssistedFactoryProviders: Map<KClass<out ManualViewModelAssistedFactory>, Provider<ManualViewModelAssistedFactory>>,
) : MetroViewModelFactory()
```

### ViewModelGraph

```kotlin
@DependencyGraph(AppScope::class)
interface AppGraph : ViewModelGraph {
    val metroViewModelFactory: MetroViewModelFactory
}
```

---

## KMP Target Support Matrix (v2.9.6)

| Artifact | Android | JVM (Desktop) | iOS | macOS | Linux | JS | wasmJs |
|----------|---------|---------------|-----|-------|-------|----|--------|
| lifecycle-viewmodel | Yes | Yes | Yes | Yes | Yes | Yes | Yes |
| lifecycle-viewmodel-compose | Yes | Yes | Yes | Yes | Yes | Yes | Yes |
| lifecycle-viewmodel-savedstate | Yes | Yes | Yes | Yes | Yes | Yes | Yes |
| lifecycle-runtime | Yes | Yes | Yes | Yes | Yes | Yes | Yes |
| lifecycle-runtime-compose | Yes | Yes | Yes | Yes | Yes | Yes | Yes |
| lifecycle-viewmodel-testing | Yes | Yes | Yes | Yes | Yes | Yes | Yes |

---

## ProcessLifecycleOwner

Provides a `LifecycleOwner` for the entire application process. Android-only.

```kotlin
import androidx.lifecycle.ProcessLifecycleOwner

// Access the app-level lifecycle
val appLifecycle = ProcessLifecycleOwner.get().lifecycle
```

| Event | Trigger |
|-------|---------|
| `ON_CREATE` | First Activity `onCreate` (once) |
| `ON_START` | First Activity `onStart` (app enters foreground) |
| `ON_RESUME` | First Activity `onResume` |
| `ON_PAUSE` | Last Activity `onPause` |
| `ON_STOP` | Last Activity `onStop` (app enters background, delayed to ignore config changes) |
| `ON_DESTROY` | Never dispatched |

---

## Lifecycle.State

| State | Description | `isAtLeast` checks |
|-------|-------------|-------------------|
| `INITIALIZED` | Created but not yet started | `isAtLeast(INITIALIZED)` = true |
| `CREATED` | Created, not visible | `isAtLeast(CREATED)` = true |
| `STARTED` | Visible but not interactive | `isAtLeast(STARTED)` = true |
| `RESUMED` | Visible and interactive | `isAtLeast(RESUMED)` = true |
| `DESTROYED` | About to be destroyed | Always false for other states |
