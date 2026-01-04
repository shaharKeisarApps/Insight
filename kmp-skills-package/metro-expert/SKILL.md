---
name: metro-expert
description: Elite expertise for Metro DI - a multiplatform, compile-time dependency injection framework for Kotlin. Use when creating dependency graphs, providing dependencies with @Provides/@Binds, constructor injection with @Inject, scoping with @SingleIn, Anvil-style aggregation with @ContributesTo/@ContributesBinding, assisted injection, graph extensions, multibindings, or integrating with Circuit. Triggers on Metro DI setup, graph creation, binding contributions, scoping decisions, or any DI architecture questions.
---

# Metro DI Expert Skill

Elite-level expertise for Metro dependency injection framework.

## Core Concepts

Metro is a compile-time DI framework combining Dagger's performance, Anvil's aggregation, and kotlin-inject's Kotlin-first API.

```
@DependencyGraph (Component) → Contains bindings
     ↓
@Provides / @Inject → Define how to create instances
     ↓
@SingleIn(Scope) → Control instance lifecycle
     ↓
@ContributesTo/Binding → Modular aggregation
```

## Installation

```kotlin
// build.gradle.kts
plugins {
    id("dev.zacsweers.metro") version "<version>"
}

// That's it! No KSP needed - it's a compiler plugin
```

## Dependency Graphs

### Basic Graph

```kotlin
@DependencyGraph
interface AppGraph {
    // Exposed dependencies (accessors)
    val userRepository: UserRepository
    val httpClient: HttpClient
    
    // Internal provider
    @Provides
    private fun provideHttpClient(): HttpClient = HttpClient()
}

// Create and use
val graph = createGraph<AppGraph>()
val repo = graph.userRepository
```

### Graph with Factory (Runtime Inputs)

```kotlin
@DependencyGraph
interface AppGraph {
    val repository: UserRepository
    
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Application,  // Instance binding
            @Provides @Named("apiKey") apiKey: String,
        ): AppGraph
    }
}

// Create with runtime values
val graph = createGraphFactory<AppGraph.Factory>()
    .create(
        application = this,
        apiKey = BuildConfig.API_KEY,
    )
```

### Graph Dependencies (@Includes)

```kotlin
interface NetworkModule {
    val httpClient: HttpClient
    val cache: Cache
}

@DependencyGraph
interface AppGraph {
    val repository: UserRepository
    
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Application,
            @Includes networkModule: NetworkModule,  // Graph dependency
        ): AppGraph
    }
}
// All NetworkModule accessors become available bindings in AppGraph
```

## Providing Dependencies

### @Provides (Provider Functions)

```kotlin
@DependencyGraph
interface AppGraph {
    val httpClient: HttpClient
    
    // Simple provider
    @Provides
    fun provideHttpClient(): HttpClient = HttpClient()
    
    // Provider with dependencies
    @Provides
    fun provideRetrofit(httpClient: HttpClient): Retrofit =
        Retrofit.Builder()
            .client(httpClient)
            .build()
    
    // Qualified provider
    @Provides
    @Named("auth")
    fun provideAuthClient(httpClient: HttpClient): HttpClient =
        httpClient.newBuilder()
            .addInterceptor(AuthInterceptor())
            .build()
    
    // Private providers - only Metro can use!
    @Provides
    private fun provideFileSystem(): FileSystem = FileSystem.SYSTEM
}
```

### @Binds (Interface → Implementation)

```kotlin
interface UserRepository
class RealUserRepository @Inject constructor(api: Api) : UserRepository

@DependencyGraph
interface AppGraph {
    val userRepo: UserRepository
    
    // Bind implementation to interface
    @Binds
    val RealUserRepository.bind: UserRepository
    
    // With qualifier
    @Binds
    @Named("cached")
    val CachedUserRepository.bindCached: UserRepository
}
```

### @Inject Constructor Injection

```kotlin
// Simple injection - Metro auto-discovers this
@Inject
class UserRepository(
    private val api: Api,
    private val cache: Cache,
)

// With default parameters (optional dependencies!)
@Inject
class AnalyticsTracker(
    private val logger: Logger = ConsoleLogger(),  // Uses default if not provided
)

// Private constructor injection works!
class InternalService @Inject private constructor(
    private val dependency: Dependency,
)
```

## Scoping with @SingleIn

```kotlin
// Define scope markers (just marker classes)
object AppScope
object UserScope
object ActivityScope

// Scope a class
@SingleIn(AppScope::class)
@Inject
class UserManager(private val api: Api)

// Scope a provider
@DependencyGraph(AppScope::class)
interface AppGraph {
    @Provides
    @SingleIn(AppScope::class)
    fun provideDatabase(): Database = Database()
}

// Graph scope is implicit SingleIn
@DependencyGraph(AppScope::class)  // Implicitly @SingleIn(AppScope::class)
interface AppGraph
```

**Scoping Rules:**
- `@SingleIn` = one instance per graph lifetime
- Unscoped = new instance every injection
- Scope declared at binding, not at injection site
- Child graphs can access parent scoped instances

## Graph Extensions (Subcomponents)

```kotlin
// Parent graph
@DependencyGraph(AppScope::class)
interface AppGraph {
    val loggedInGraphFactory: LoggedInGraph.Factory
}

// Child graph extension
@GraphExtension(UserScope::class)
interface LoggedInGraph {
    val userProfile: UserProfile
    val userRepository: UserRepository
    
    @GraphExtension.Factory
    fun interface Factory {
        fun create(@Provides userId: String): LoggedInGraph
    }
}

// Usage
val appGraph = createGraph<AppGraph>()
val userGraph = appGraph.loggedInGraphFactory.create(userId = "123")
val profile = userGraph.userProfile
```

## Aggregation (Anvil-Style)

### @ContributesTo (Interface Contribution)

```kotlin
// In :network module
@ContributesTo(AppScope::class)
interface NetworkBindings {
    val httpClient: HttpClient
    
    @Provides
    fun provideHttpClient(): HttpClient = HttpClient()
}

// In :app module - NetworkBindings auto-merged!
@DependencyGraph(AppScope::class)
interface AppGraph  // Automatically extends NetworkBindings
```

### @ContributesBinding (Implementation Binding)

```kotlin
interface Analytics

// Auto-generates binding: Analytics → FirebaseAnalytics
@ContributesBinding(AppScope::class)
@Inject
class FirebaseAnalytics(private val context: Context) : Analytics

// With explicit bound type
@ContributesBinding(AppScope::class, boundType = Analytics::class)
@Inject
class DebugAnalytics(...) : Analytics, Debuggable

// Replace another binding (for testing)
@ContributesBinding(AppScope::class, replaces = [FirebaseAnalytics::class])
@Inject
class FakeAnalytics : Analytics
```

### @ContributesIntoSet / @ContributesIntoMap (Multibindings)

```kotlin
// Set multibinding
interface Initializer {
    fun initialize()
}

@ContributesIntoSet(AppScope::class)
@Inject
class AnalyticsInitializer : Initializer {
    override fun initialize() { /* ... */ }
}

@ContributesIntoSet(AppScope::class)
@Inject
class CrashReportingInitializer : Initializer {
    override fun initialize() { /* ... */ }
}

// Inject the set
@Inject
class AppStartup(
    private val initializers: Set<Initializer>,
) {
    fun start() = initializers.forEach { it.initialize() }
}
```

```kotlin
// Map multibinding with keys
@MapKey
annotation class FeatureKey(val value: String)

@ContributesIntoMap(AppScope::class)
@FeatureKey("profile")
@Inject
class ProfileFeature : Feature

@ContributesIntoMap(AppScope::class)
@FeatureKey("settings")
@Inject
class SettingsFeature : Feature

// Inject the map
@Inject
class FeatureManager(
    private val features: Map<String, Feature>,
)
```

## Assisted Injection

For runtime parameters combined with injected dependencies:

```kotlin
@Inject
class UserDetailPresenter(
    @Assisted private val userId: String,       // Runtime param
    @Assisted private val navigator: Navigator, // Runtime param
    private val userRepository: UserRepository, // Injected
    private val analytics: Analytics,           // Injected
) {
    @AssistedFactory
    fun interface Factory {
        fun create(userId: String, navigator: Navigator): UserDetailPresenter
    }
}

// Usage - inject the factory, call with runtime values
@Inject
class SomeScreen(
    private val presenterFactory: UserDetailPresenter.Factory,
) {
    fun showUser(userId: String, navigator: Navigator) {
        val presenter = presenterFactory.create(userId, navigator)
    }
}
```

**Assisted with Circuit:**
```kotlin
@CircuitInject(ProfileScreen::class, AppScope::class)
@Composable
fun ProfilePresenter(
    screen: ProfileScreen,     // @Assisted by Circuit
    navigator: Navigator,      // @Assisted by Circuit
    userRepository: UserRepository,  // Injected by Metro
): ProfileScreen.State {
    // ...
}
```

## Metro Intrinsics

### Provider<T> (Deferred Creation)

```kotlin
@Inject
class LazyLoader(
    private val heavyService: Provider<HeavyService>,
) {
    fun doWork() {
        val service = heavyService.get()  // Created on demand
    }
}
```

### Lazy<T> (Deferred + Cached)

```kotlin
@Inject
class CachedLoader(
    private val heavyService: Lazy<HeavyService>,
) {
    fun doWork() {
        val service = heavyService.value  // Created once, cached
    }
}
```

### Provider<Lazy<T>>

```kotlin
@Inject
class FactoryPattern(
    private val serviceFactory: Provider<Lazy<Service>>,
) {
    fun createCached(): Lazy<Service> = serviceFactory.get()
}
```

## Qualifiers

```kotlin
// Built-in @Named
@Provides
@Named("api")
fun provideApiUrl(): String = "https://api.example.com"

@Inject
class ApiClient(@Named("api") private val baseUrl: String)

// Custom qualifier
@Qualifier
annotation class IoDispatcher

@Provides
@IoDispatcher
fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

@Inject
class Repository(@IoDispatcher private val dispatcher: CoroutineDispatcher)
```

## Circuit Integration

### Complete Setup

```kotlin
// Define scope
object AppScope

// App graph with Circuit
@DependencyGraph(AppScope::class)
interface AppGraph {
    val circuit: Circuit
    
    @Provides
    fun provideCircuit(
        presenterFactories: Set<Presenter.Factory>,
        uiFactories: Set<Ui.Factory>,
    ): Circuit = Circuit.Builder()
        .addPresenterFactories(presenterFactories)
        .addUiFactories(uiFactories)
        .build()
}

// Screen with presenter using Metro injection
@Parcelize
data object HomeScreen : Screen {
    data class State(val items: List<Item>, val eventSink: (Event) -> Unit) : CircuitUiState
    sealed interface Event : CircuitUiEvent
}

@CircuitInject(HomeScreen::class, AppScope::class)
@Composable
fun HomePresenter(
    screen: HomeScreen,
    navigator: Navigator,
    itemRepository: ItemRepository,  // Injected by Metro
): HomeScreen.State {
    // ...
}

@CircuitInject(HomeScreen::class, AppScope::class)
@Composable
fun HomeUi(state: HomeScreen.State, modifier: Modifier = Modifier) {
    // ...
}
```

### Activity Setup

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val appGraph = (application as App).appGraph
        
        setContent {
            CircuitCompositionLocals(appGraph.circuit) {
                val backStack = rememberSaveableBackStack(root = HomeScreen)
                val navigator = rememberCircuitNavigator(backStack)
                NavigableCircuitContent(navigator, backStack)
            }
        }
    }
}
```

## Android Patterns

### Application Graph

```kotlin
@DependencyGraph(AppScope::class)
interface AppGraph {
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides application: Application): AppGraph
    }
}

class App : Application() {
    val appGraph: AppGraph by lazy {
        createGraphFactory<AppGraph.Factory>().create(this)
    }
}
```

### Activity Injection with AppComponentFactory

```kotlin
// Define activity key
@MapKey
annotation class ActivityKey(val value: KClass<out Activity>)

// Contribute activity
@ContributesIntoMap(AppScope::class)
@ActivityKey(MainActivity::class)
@Inject
class MainActivity(
    private val circuit: Circuit,
) : ComponentActivity()

// Graph exposes activity providers
@DependencyGraph(AppScope::class)
interface AppGraph {
    val activityProviders: Map<Class<out Activity>, Provider<Activity>>
}

// AppComponentFactory (registered in AndroidManifest)
class MetroAppComponentFactory : AppComponentFactory() {
    private lateinit var activityProviders: Map<Class<out Activity>, Provider<Activity>>
    
    override fun instantiateApplicationCompat(cl: ClassLoader, className: String): Application {
        val app = super.instantiateApplicationCompat(cl, className) as App
        activityProviders = app.appGraph.activityProviders
        return app
    }
    
    override fun instantiateActivityCompat(cl: ClassLoader, className: String, intent: Intent?): Activity {
        return activityProviders[Class.forName(className)]?.get()
            ?: super.instantiateActivityCompat(cl, className, intent)
    }
}
```

## Testing

### Test Graphs

```kotlin
// Replace bindings for testing
@DependencyGraph(AppScope::class, excludes = [RealNetworkModule::class])
interface TestAppGraph {
    @Provides
    fun provideFakeApi(): Api = FakeApi()
}

// Or use replaces in ContributesBinding
@ContributesBinding(AppScope::class, replaces = [RealRepository::class])
class FakeRepository @Inject constructor() : Repository
```

### Manual Construction

```kotlin
@Test
fun `test user flow`() {
    val fakeRepo = FakeUserRepository()
    val presenter = UserPresenter(
        userRepository = fakeRepo,
        analytics = FakeAnalytics(),
    )
    // Test presenter directly
}
```

## Common Patterns

### Coroutine Dispatchers

```kotlin
@Qualifier annotation class IoDispatcher
@Qualifier annotation class MainDispatcher
@Qualifier annotation class DefaultDispatcher

@ContributesTo(AppScope::class)
interface DispatchersModule {
    @Provides
    @SingleIn(AppScope::class)
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
    
    @Provides
    @SingleIn(AppScope::class)
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main
    
    @Provides
    @SingleIn(AppScope::class)
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
```

### Application CoroutineScope

```kotlin
@ContributesTo(AppScope::class)
interface AppScopeModule {
    @Provides
    @SingleIn(AppScope::class)
    @ForScope(AppScope::class)
    fun provideAppCoroutineScope(
        @DefaultDispatcher dispatcher: CoroutineDispatcher,
    ): CoroutineScope = CoroutineScope(dispatcher + SupervisorJob())
}
```

### Binding Container Pattern

```kotlin
// Group related bindings
@BindingContainer
object NetworkBindings {
    @Provides
    @SingleIn(AppScope::class)
    fun provideHttpClient(): HttpClient = HttpClient()
    
    @Provides
    fun provideRetrofit(client: HttpClient): Retrofit =
        Retrofit.Builder().client(client).build()
}

// Include in graph
@DependencyGraph(
    scope = AppScope::class,
    bindingContainers = [NetworkBindings::class],
)
interface AppGraph
```

## Anti-Patterns to Avoid

❌ **Don't inject Context directly - use Application**
```kotlin
// WRONG
@Inject class Repo(private val context: Context)

// RIGHT
@Inject class Repo(private val application: Application)
```

❌ **Don't scope everything**
```kotlin
// WRONG - unnecessary singleton
@SingleIn(AppScope::class)
@Inject class SimpleMapper  // Stateless, cheap to create

// RIGHT - unscoped for stateless classes
@Inject class SimpleMapper
```

❌ **Don't forget explicit return types on @Provides**
```kotlin
// WRONG - no return type
@Provides fun provideApi() = ApiImpl()

// RIGHT
@Provides fun provideApi(): Api = ApiImpl()
```

❌ **Don't use field injection (not supported)**
```kotlin
// WRONG - Metro doesn't support field injection
class MyActivity : Activity() {
    @Inject lateinit var repo: Repository  // Won't work!
}

// RIGHT - use constructor injection via AppComponentFactory
@Inject class MyActivity(private val repo: Repository) : Activity()
```

## References

- Metro Docs: https://zacsweers.github.io/metro/latest/
- Metro GitHub: https://github.com/ZacSweers/metro
- CatchUp (reference app): https://github.com/ZacSweers/CatchUp
- Metro Samples: https://github.com/ZacSweers/metro/tree/main/samples
- Introducing Metro Blog: https://www.zacsweers.dev/introducing-metro/
