# Metro API Reference (v0.10.3)

> All annotations are in the `dev.zacsweers.metro` package unless otherwise noted.
> Kotlin requirement: 2.2.20+ (compiles against 2.3.0, experimental 2.4.0 support)
> Plugin ID: `dev.zacsweers.metro`

---

## Core Graph Annotations

### `@DependencyGraph`

Marks an interface as a dependency injection container (equivalent to Dagger `@Component`).

```kotlin
@DependencyGraph(
    scope: KClass<*>,                       // Required. The scope marker class.
    additionalScopes: Array<KClass<*>> = [], // Extra scopes this graph satisfies.
    excludes: Array<KClass<*>> = [],         // Contributed bindings to exclude.
    bindingContainers: Array<KClass<*>> = [] // Explicit BindingContainers to include.
)
```

**Usage:**
```kotlin
@DependencyGraph(AppScope::class)
interface AppGraph {
    val httpClient: HttpClient          // Accessor binding
    fun inject(activity: MainActivity)  // Member injection

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Application,
            @Provides @Named("baseUrl") baseUrl: String,
        ): AppGraph
    }
}
```

**Notes:**
- The graph interface must be non-private.
- Accessor properties/functions expose bindings from the graph.
- `@DependencyGraph.Factory` is a nested annotation for the factory interface.
- Factory parameters annotated with `@Provides` become graph bindings.

---

### `@GraphExtension`

Extends a parent graph with additional bindings (equivalent to Dagger `@Subcomponent`).

```kotlin
@GraphExtension(
    scope: KClass<*>,                       // Required. The child scope.
    additionalScopes: Array<KClass<*>> = [],
    excludes: Array<KClass<*>> = [],
    bindingContainers: Array<KClass<*>> = []
)
```

**Usage:**
```kotlin
@DependencyGraph(UserScope::class)
@GraphExtension
interface UserGraph {
    val userProfile: UserProfile

    @GraphExtension.Factory
    interface Factory {
        fun create(@Provides user: User): UserGraph
    }
}
```

**Notes:**
- The parent graph must expose the `@GraphExtension.Factory` as an accessor.
- Child graphs inherit all parent bindings.
- Scopes must differ between parent and child.

---

### `@BindingContainer`

Groups reusable bindings that can be included in multiple graphs.

```kotlin
@BindingContainer(
    includes: Array<KClass<*>> = []  // Other BindingContainers to compose.
)
```

**Usage:**
```kotlin
@BindingContainer
interface NetworkBindings {
    companion object {
        @Provides
        @SingleIn(AppScope::class)
        fun provideHttpClient(): HttpClient = HttpClient { install(ContentNegotiation) }
    }
}

// Include in a graph
@DependencyGraph(AppScope::class, bindingContainers = [NetworkBindings::class])
interface AppGraph { ... }
```

---

## Injection Annotations

### `@Inject`

Marks a constructor, property, or function for dependency injection.

```kotlin
@Inject
class UserRepository(
    private val api: UserApi,
    private val cache: UserCache,
)
```

**Notes:**
- Applied to the class (Kotlin convention) or constructor.
- All constructor parameters are resolved from the graph.
- Also used for member injection on properties/functions.

---

### `@Provides`

Marks a function or property as a binding provider within a module or graph.

```kotlin
@ContributesTo(AppScope::class)
interface NetworkModule {
    companion object {
        @Provides
        @SingleIn(AppScope::class)
        fun provideOkHttpClient(
            interceptors: Set<Interceptor>,
        ): OkHttpClient = OkHttpClient.Builder()
            .apply { interceptors.forEach(::addInterceptor) }
            .build()
    }
}
```

**Notes:**
- Can be used in `@ContributesTo` module companions, `@DependencyGraph` interfaces, or `@BindingContainer` companions.
- Parameters are resolved from the graph.
- With `transformProvidersToPrivate = true` (default), provider functions are compiled as private.

---

### `@Binds`

Binds an implementation to its supertype. Used as an extension property or function in a module interface.

```kotlin
@ContributesTo(AppScope::class)
interface RepositoryModule {
    @Binds val RealUserRepository.bind: UserRepository
    @Binds val RealSettingsRepository.bind: SettingsRepository
}
```

**Notes:**
- The receiver type is the implementation; the return type is the bound interface.
- More efficient than `@Provides` because it avoids creating a wrapper.
- The implementation class must have `@Inject` on its constructor.

---

## Assisted Injection

### `@AssistedInject`

Marks a constructor that receives both injected and runtime-provided parameters.

```kotlin
@AssistedInject
class HomePresenter(
    @Assisted private val navigator: Navigator,
    private val userRepo: UserRepository,
) : Presenter<HomeScreen.State> { ... }
```

### `@Assisted`

Marks a constructor parameter as provided at runtime (not from the graph).

```kotlin
@Assisted  // positional
@Assisted("userId")  // named, for disambiguation when multiple assisted params share a type
```

**Parameters:**
- `value: String = ""` -- Optional name for disambiguation.

### `@AssistedFactory`

Marks an interface as the factory for creating assisted-injected instances.

```kotlin
@AssistedFactory
fun interface HomePresenterFactory {
    fun create(navigator: Navigator): HomePresenter
}
```

**Notes:**
- Must have exactly one abstract function.
- Function parameters must match the `@Assisted` parameters of the target class.
- Prefer `fun interface` for SAM conversion support.
- Metro can auto-generate these if `generateAssistedFactories = true` in the Gradle config.

---

## Scoping Annotations

### `@Scope`

Meta-annotation for defining custom scope annotations.

```kotlin
@Scope
annotation class ActivityScoped
```

### `@SingleIn`

Scopes a binding as a singleton within the specified scope.

```kotlin
@SingleIn(scope: KClass<*>)
```

**Usage:**
```kotlin
@Inject
@SingleIn(AppScope::class)
class DatabaseImpl(private val driver: SqlDriver) : Database { ... }
```

**Notes:**
- Without `@SingleIn`, a new instance is created on each injection.
- The scope class must match the `@DependencyGraph` scope.

---

## Qualifier Annotations

### `@Qualifier`

Meta-annotation for defining custom qualifier annotations.

```kotlin
@Qualifier
annotation class InternalApi

@Qualifier
annotation class ExternalApi
```

### `@Named`

Built-in string-based qualifier.

```kotlin
@Named(name: String)
```

**Usage:**
```kotlin
@Provides
@Named("baseUrl")
fun provideBaseUrl(): String = "https://api.example.com"

@Inject
class ApiClient(@Named("baseUrl") private val baseUrl: String)
```

---

## Multibinding Annotations

### `@Multibinds`

Declares a multibinding collection (Set or Map). Required before any contributions.

```kotlin
@Multibinds(allowEmpty: Boolean = false)
```

**Usage:**
```kotlin
@ContributesTo(AppScope::class)
interface PluginModule {
    @Multibinds fun plugins(): Set<Plugin>
    @Multibinds fun namedPlugins(): Map<String, Plugin>
}
```

**Notes:**
- If `allowEmpty = false` (default), the graph fails to compile if no contributions exist.
- Set to `true` for optional collections that may be empty.

### `@IntoSet`

Contributes a binding into a `Set<T>` multibinding.

```kotlin
@Provides
@IntoSet
fun provideLoggingInterceptor(): Interceptor = LoggingInterceptor()
```

### `@ElementsIntoSet`

Contributes a `Collection<T>` of elements into a `Set<T>` multibinding.

```kotlin
@Provides
@ElementsIntoSet
fun provideDefaultInterceptors(): Set<Interceptor> = setOf(
    AuthInterceptor(),
    CacheInterceptor(),
)
```

### `@IntoMap`

Contributes a binding into a `Map<K, V>` multibinding.

```kotlin
@Provides
@IntoMap
@StringKey("auth")
fun provideAuthService(impl: AuthServiceImpl): Service = impl
```

### `@MapKey`

Meta-annotation for defining custom map key annotations.

```kotlin
@MapKey(unwrapValue: Boolean = true)
```

**Usage:**
```kotlin
@MapKey
annotation class ServiceKey(val value: KClass<out Service>)
```

**Notes:**
- When `unwrapValue = true` (default), the annotation's single `value` property is used as the map key.
- When `unwrapValue = false`, the entire annotation instance is the key.

### `@StringKey`

Built-in map key annotation using a string value.

```kotlin
@StringKey(value: String)
```

---

## Contribution Annotations (Multi-Module Aggregation)

### `@ContributesTo`

Contributes a module interface (with its bindings) to a scoped graph.

```kotlin
@ContributesTo(
    scope: KClass<*>,                  // Target scope.
    replaces: Array<KClass<*>> = []    // Other modules to replace.
)
```

**Usage:**
```kotlin
@ContributesTo(AppScope::class)
interface AnalyticsModule {
    @Binds val RealAnalytics.bind: Analytics

    companion object {
        @Provides
        @SingleIn(AppScope::class)
        fun provideTracker(analytics: Analytics): Tracker = Tracker(analytics)
    }
}
```

### `@ContributesBinding`

Automatically binds an implementation to its supertype within a scope.

```kotlin
@ContributesBinding(
    scope: KClass<*>,                  // Target scope.
    binding: Any = Nothing::class,     // Explicit bound type (inferred if omitted).
    replaces: Array<KClass<*>> = []    // Bindings to replace.
)
```

**Usage:**
```kotlin
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class RealUserRepository(
    private val api: UserApi,
) : UserRepository
```

**Notes:**
- The bound type is inferred from the single supertype. Specify `binding` if there are multiple supertypes.
- Combine with `@SingleIn` for scoped singletons.
- With `contributesAsInject = true` (default), `@Inject` is implicit.

### `@ContributesIntoSet`

Contributes a binding into a `Set<T>` multibinding across modules.

```kotlin
@ContributesIntoSet(
    scope: KClass<*>,
    binding: Any = Nothing::class,     // Explicit bound type.
    replaces: Array<KClass<*>> = []
)
```

**Usage:**
```kotlin
@ContributesIntoSet(AppScope::class, binding = binding<Presenter.Factory>())
@Inject
class HomePresenterFactory(...) : Presenter.Factory
```

### `@ContributesIntoMap`

Contributes a binding into a `Map<K, V>` multibinding across modules.

```kotlin
@ContributesIntoMap(
    scope: KClass<*>,
    binding: Any = Nothing::class,     // Explicit bound type.
    replaces: Array<KClass<*>> = []
)
```

**Usage:**
```kotlin
@ContributesIntoMap(AppScope::class, binding = binding<Service>())
@StringKey("slashdot")
@Inject
class SlashdotService(private val api: SlashdotApi) : Service
```

---

## Advanced Annotations

### `@OptionalBinding`

Marks a binding as optional. If no provider exists, the injection point receives `null` (for nullable types) or a default.

```kotlin
@OptionalBinding
@Provides
fun provideAnalytics(): Analytics? = null
```

### `@HasMemberInjections`

Indicates a class has member (property/function) injections. Added in 0.8.0.

```kotlin
@HasMemberInjections
class MyActivity : Activity() {
    @Inject lateinit var analytics: Analytics
}
```

---

## Graph Creation APIs

All functions are top-level in the `dev.zacsweers.metro` package.

### `createGraph<T>()`

Creates a graph instance directly (no factory parameters).

```kotlin
val graph = createGraph<AppGraph>()
```

### `createGraphFactory<T>()`

Creates a factory for a graph that requires runtime parameters.

```kotlin
val factory = createGraphFactory<AppGraph.Factory>()
val graph = factory.create(application)
```

### `createDynamicGraph<T>(vararg containers: Any)`

Creates a graph with overrideable bindings. Primarily for testing.

```kotlin
val graph = createDynamicGraph<AppGraph>(FakeNetworkModule())
```

### `createDynamicGraphFactory<T>(vararg containers: Any)`

Factory version of `createDynamicGraph` for graphs requiring runtime parameters.

```kotlin
val factory = createDynamicGraphFactory<AppGraph.Factory>(FakeModule())
val graph = factory.create(testApplication)
```

---

## Provider and Lazy

### `dev.zacsweers.metro.Provider<T>`

Defers dependency creation. Each call to `get()` returns a new instance (unless scoped).

```kotlin
@Inject
class MyClass(private val userProvider: Provider<User>) {
    fun doWork() {
        val user = userProvider.get()
    }
}
```

### Kotlin `Lazy<T>`

Standard Kotlin `Lazy<T>` is supported. The value is computed once on first access.

```kotlin
@Inject
class MyClass(private val database: Lazy<Database>) {
    fun query() {
        database.value.execute(...)
    }
}
```

**Notes:**
- `Provider<T>` creates a new instance each call (unless scoped with `@SingleIn`).
- `Lazy<T>` always caches after first access.
- Both help break circular dependency chains.

---

## Gradle Plugin Configuration

### Plugin Application

```kotlin
plugins {
    id("dev.zacsweers.metro") version "0.10.3"
}
```

### Full Extension Block

```kotlin
metro {
    // Core options
    enabled = true                               // Enable/disable the compiler plugin.
    debug = false                                // Verbose debug logging.

    // Code generation
    generateAssistedFactories = false            // Auto-generate @AssistedFactory interfaces.
    transformProvidersToPrivate = true            // Make @Provides functions private at compile time.
    shrinkUnusedBindings = true                  // Remove unused bindings from generated graphs.
    contributesAsInject = true                   // Treat @Contributes* as implicit @Inject.

    // Error handling
    maxIrErrors = 20                             // Max IR errors before stopping compilation.

    // Reports and analysis
    reportsDestination = layout.buildDirectory.dir("metro")  // Enables graph analysis tasks.

    // Interop with other DI frameworks
    interop {
        includeDagger()                          // Dagger/Javax/Jakarta annotations + Provider/Lazy.
        includeKotlinInject()                    // kotlin-inject annotations.
        includeGuice()                           // Guice annotations + Provider.

        // Custom Provider/Lazy types
        provider.add("com.example.MyProvider")
        lazy.add("com.example.MyLazy")
    }

    // Diagnostics
    interopAnnotationsNamedArgSeverity = DiagnosticSeverity.NONE  // WARN, ERROR, or NONE.

    // KMP contribution hints
    enableKotlinVersionCompatibilityChecks = true
    supportedHintContributionPlatforms = setOf("jvm", "android", "ios")  // Override auto-detection.
    generateContributionHintsInFir = false
}
```

---

## Graph Analysis Tasks

Enabled when `reportsDestination` is configured.

| Task | Description | Output |
|------|-------------|--------|
| `generateMetroGraphMetadata` | Dumps merged JSON of all binding graphs | `<reportsDir>/metadata.json` |
| `analyzeMetroGraph` | Fan-in/out, betweenness centrality, dominator analysis, longest path | `<reportsDir>/analysis.json` |
| `generateMetroGraphHtml` | Interactive HTML visualization (ECharts) with filtering and heatmaps | `<reportsDir>/graph.html` |

```bash
./gradlew :app:generateMetroGraphHtml
# Open build/metro/graph.html in a browser
```

---

## Artifacts

All published under group `dev.zacsweers.metro`.

| Artifact | Description | Auto-added |
|----------|-------------|------------|
| `runtime` | Core runtime (annotations, `Provider<T>`, graph creation functions) | Yes (by plugin) |
| `interop-dagger` | Dagger/Javax/Jakarta annotation + `Provider`/`Lazy` interop | Yes (when `includeDagger()`) |
| `interop-guice` | Guice annotation + `Provider` interop | Yes (when `includeGuice()`) |
| `metrox-android` | Android `AppComponentFactory` support (single-variant: release only since 0.10.3) | Manual |
| `metrox-viewmodel` | AndroidX ViewModel injection utilities | Manual |
| `metrox-viewmodel-compose` | Compose `metroViewModel()` + `LocalMetroViewModelFactory` (depends on metrox-viewmodel transitively) | Manual |

```kotlin
dependencies {
    // Core (usually auto-added by plugin)
    implementation("dev.zacsweers.metro:runtime:0.10.3")

    // MetroX -- Android extensions (pick what you need)
    implementation("dev.zacsweers.metro:metrox-android:0.10.3")              // AppComponentFactory (API 28+)
    implementation("dev.zacsweers.metro:metrox-viewmodel:0.10.3")           // ViewModel injection
    implementation("dev.zacsweers.metro:metrox-viewmodel-compose:0.10.3")   // metroViewModel() (includes metrox-viewmodel)
}
```

---

## MetroX Android (`metrox-android`)

> Package: `dev.zacsweers.metro.metrox.android`
> Requires: Android API 28+ (`AppComponentFactory` was introduced in Android P)
> Single-variant: publishes release only (since 0.10.3)

Provides constructor injection for Android components (Activities, Services, BroadcastReceivers, ContentProviders) via `AppComponentFactory`.

### `MetroApplication`

Interface for `Application` classes. Ensures a single `MetroAppComponentProviders` instance.

```kotlin
interface MetroApplication {
    val appComponentProviders: MetroAppComponentProviders
}
```

### `MetroAppComponentProviders`

Interface that your dependency graph should implement. Exposes maps of component providers.

```kotlin
interface MetroAppComponentProviders {
    val activityProviders: Map<KClass<out Activity>, Provider<Activity>>
    val serviceProviders: Map<KClass<out Service>, Provider<Service>>
    val broadcastReceiverProviders: Map<KClass<out BroadcastReceiver>, Provider<BroadcastReceiver>>
    val contentProviderProviders: Map<KClass<out ContentProvider>, Provider<ContentProvider>>
}
```

### `MetroAppComponentFactory`

An `AppComponentFactory` that uses Metro for constructor injection. Reads providers from `MetroAppComponentProviders`.

**Important**: Uses base Android types (`Activity`, not `ComponentActivity`). For custom base types, create your own factory and set `tools:replace="android:appComponentFactory"` in AndroidManifest.xml.

### Component Key Annotations

| Annotation | Target | Usage |
|-----------|--------|-------|
| `@ActivityKey(KClass<out Activity>)` | Activity class | Map key for activity injection |
| `@ServiceKey(KClass<out Service>)` | Service class | Map key for service injection |
| `@BroadcastReceiverKey(KClass<out BroadcastReceiver>)` | BroadcastReceiver class | Map key for receiver injection |
| `@ContentProviderKey(KClass<out ContentProvider>)` | ContentProvider class | Map key for provider injection |

### Setup Steps

1. **Graph implements `MetroAppComponentProviders`**:
```kotlin
@DependencyGraph(AppScope::class)
interface AppGraph : MetroAppComponentProviders {
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides application: Application): AppGraph
    }
}
```

2. **Application implements `MetroApplication`**:
```kotlin
class MyApp : Application(), MetroApplication {
    private val appGraph by lazy {
        createGraphFactory<AppGraph.Factory>().create(this)
    }
    override val appComponentProviders: MetroAppComponentProviders get() = appGraph
}
```

3. **Register `MetroAppComponentFactory` in AndroidManifest.xml**:
```xml
<application
    android:name=".MyApp"
    android:appComponentFactory="dev.zacsweers.metro.metrox.android.MetroAppComponentFactory"
    ...>
```

4. **Contribute components with key annotations**:
```kotlin
@ContributesIntoMap(AppScope::class, binding<Activity>())
@ActivityKey(MainActivity::class)
@Inject
class MainActivity(
    private val circuit: Circuit,
    private val preferences: AppPreferences,
) : AppCompatActivity()
```

### CatchUp Comparison

CatchUp predates MetroX and uses the **manual approach** -- custom `@ActivityKey` annotation + manual `@ContributesIntoMap` with `binding<Activity>()`. MetroX standardizes this exact pattern with built-in annotations and `MetroAppComponentFactory`. If starting a new project, prefer MetroX; existing CatchUp-style code works fine.

---

## MetroX ViewModel (`metrox-viewmodel`)

> Package: `dev.zacsweers.metro.metrox.viewmodel`
> Note: For Circuit apps, skip this -- use Circuit Presenters instead.

Provides ViewModel injection support as a replacement for `hiltViewModel()` patterns.

### `ViewModelGraph`

Interface that your dependency graph should extend to get multibindings for ViewModel providers.

```kotlin
interface ViewModelGraph {
    val viewModelProviders: Map<KClass<out ViewModel>, Provider<ViewModel>>
    val assistedFactoryProviders: Map<KClass<out ViewModel>, Provider<ViewModelAssistedFactory>>
    val manualAssistedFactoryProviders: Map<KClass<out ManualViewModelAssistedFactory>, Provider<ManualViewModelAssistedFactory>>
    val metroViewModelFactory: MetroViewModelFactory
}
```

### `MetroViewModelFactory`

Abstract `ViewModelProvider.Factory` that creates ViewModels from the injected provider maps. **You must provide a concrete subclass binding**:

```kotlin
@Inject
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class MyViewModelFactory(
    override val viewModelProviders: Map<KClass<out ViewModel>, Provider<ViewModel>>,
    override val assistedFactoryProviders: Map<KClass<out ViewModel>, Provider<ViewModelAssistedFactory>>,
    override val manualAssistedFactoryProviders: Map<KClass<out ManualViewModelAssistedFactory>, Provider<ManualViewModelAssistedFactory>>,
) : MetroViewModelFactory()
```

### `ViewModelAssistedFactory`

Interface for assisted ViewModel creation using `CreationExtras`.

```kotlin
interface ViewModelAssistedFactory {
    fun create(extras: CreationExtras): ViewModel
}
```

### `ManualViewModelAssistedFactory`

Interface for manual assisted ViewModel creation with custom parameters.

```kotlin
interface ManualViewModelAssistedFactory
```

Implementations define custom `create` methods:

```kotlin
interface Factory : ManualViewModelAssistedFactory {
    fun create(userId: String, mode: EditMode): EditProfileViewModel
}
```

### ViewModel Key Annotations

| Annotation | Purpose |
|-----------|---------|
| `@ViewModelKey(KClass<out ViewModel>)` | Standard ViewModel contribution into `viewModelProviders` |
| `@ViewModelAssistedFactoryKey(KClass<out ViewModel>)` | Assisted factory contribution into `assistedFactoryProviders` |
| `@ManualViewModelAssistedFactoryKey(KClass<out ManualViewModelAssistedFactory>)` | Manual factory contribution into `manualAssistedFactoryProviders` |

### Three Tiers of ViewModel Injection

| Tier | When to Use | Annotation |
|------|-------------|------------|
| **Standard** | No runtime params needed | `@ViewModelKey` + `@Inject` |
| **Assisted** (CreationExtras) | Runtime params via `CreationExtras` | `@ViewModelAssistedFactoryKey` + `@AssistedInject` |
| **Manual Assisted** | Full control with typed params | `@ManualViewModelAssistedFactoryKey` + custom `create()` |

---

## MetroX ViewModel Compose (`metrox-viewmodel-compose`)

> Package: `dev.zacsweers.metro.metrox.viewmodel.compose`
> Transitively depends on `metrox-viewmodel` -- no need to add both.

### `LocalMetroViewModelFactory`

`ProvidableCompositionLocal` that provides the `MetroViewModelFactory` within the Compose tree.

```kotlin
val LocalMetroViewModelFactory: ProvidableCompositionLocal<MetroViewModelFactory>
```

**Must be provided at the root of your Compose hierarchy:**

```kotlin
@Composable
fun App(metroVmf: MetroViewModelFactory) {
    CompositionLocalProvider(LocalMetroViewModelFactory provides metroVmf) {
        // App content
    }
}
```

### `metroViewModel()`

Retrieves a Metro-injected ViewModel from `LocalMetroViewModelFactory`. Replaces `hiltViewModel()`.

```kotlin
@Composable
inline fun <reified VM : ViewModel> metroViewModel(
    viewModelStoreOwner: ViewModelStoreOwner = requireViewModelStoreOwner(),
    key: String? = null,
): VM
```

### `assistedMetroViewModel()`

Retrieves an assisted ViewModel (using `CreationExtras`):

```kotlin
@Composable
inline fun <reified VM : ViewModel> assistedMetroViewModel(
    viewModelStoreOwner: ViewModelStoreOwner = requireViewModelStoreOwner(),
    key: String? = null,
    extras: CreationExtras = ...,
): VM
```

For manual assisted factories:

```kotlin
@Composable
inline fun <reified VM : ViewModel, reified F : ManualViewModelAssistedFactory> assistedMetroViewModel(
    crossinline factory: F.() -> VM,
): VM
```

### MetroX ViewModel Key Imports

```kotlin
// metrox-android
import dev.zacsweers.metro.metrox.android.MetroApplication
import dev.zacsweers.metro.metrox.android.MetroAppComponentProviders
import dev.zacsweers.metro.metrox.android.MetroAppComponentFactory
import dev.zacsweers.metro.metrox.android.ActivityKey
import dev.zacsweers.metro.metrox.android.ServiceKey
import dev.zacsweers.metro.metrox.android.BroadcastReceiverKey
import dev.zacsweers.metro.metrox.android.ContentProviderKey

// metrox-viewmodel
import dev.zacsweers.metro.metrox.viewmodel.ViewModelGraph
import dev.zacsweers.metro.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metro.metrox.viewmodel.ViewModelAssistedFactory
import dev.zacsweers.metro.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metro.metrox.viewmodel.ViewModelKey
import dev.zacsweers.metro.metrox.viewmodel.ViewModelAssistedFactoryKey
import dev.zacsweers.metro.metrox.viewmodel.ManualViewModelAssistedFactoryKey

// metrox-viewmodel-compose
import dev.zacsweers.metro.metrox.viewmodel.compose.LocalMetroViewModelFactory
import dev.zacsweers.metro.metrox.viewmodel.compose.metroViewModel
import dev.zacsweers.metro.metrox.viewmodel.compose.assistedMetroViewModel
```

---

## KMP-Specific Notes

### Supported Targets

Metro supports JVM, Android, JS, WASM, and Kotlin/Native targets. The compiler plugin runs on all targets.

### Native/WASM Limitations

- `@ContributesTo`, `@ContributesBinding`, `@ContributesIntoSet`, and `@ContributesIntoMap` rely on contribution hint generation.
- On Native and WASM targets, hint generation may require explicit platform configuration via `supportedHintContributionPlatforms`.
- Interop dependencies (`interop-dagger`, `interop-guice`) are only added for JVM/Android targets automatically.
- Graph analysis tasks run on JVM compilation only.

### commonMain Usage

Metro works seamlessly in `commonMain`. Define graphs, modules, and bindings in shared code:

```kotlin
// commonMain
@DependencyGraph(AppScope::class)
interface SharedGraph {
    val repository: UserRepository
}

// androidMain / iosMain
// Platform-specific factory parameters via @DependencyGraph.Factory
```

---

## Key Imports

```kotlin
// Core
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Binds

// Assisted
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory

// Scoping
import dev.zacsweers.metro.Scope
import dev.zacsweers.metro.SingleIn

// Qualifiers
import dev.zacsweers.metro.Qualifier
import dev.zacsweers.metro.Named

// Multibindings
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.ElementsIntoSet
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.MapKey
import dev.zacsweers.metro.StringKey

// Contributions
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.ContributesIntoMap

// Advanced
import dev.zacsweers.metro.OptionalBinding
import dev.zacsweers.metro.HasMemberInjections
import dev.zacsweers.metro.Provider

// Graph creation
import dev.zacsweers.metro.createGraph
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metro.createDynamicGraph
import dev.zacsweers.metro.createDynamicGraphFactory

// Binding helper (for explicit type in @ContributesIntoSet / @ContributesIntoMap)
import dev.zacsweers.metro.binding
```
