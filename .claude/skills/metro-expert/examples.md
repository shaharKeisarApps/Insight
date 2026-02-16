# Metro Production Examples

> Based on CatchUp production patterns (ZacSweers/CatchUp).
> Metro v0.10.3 | Kotlin 2.2.20+ (compiles against 2.3.0) | Plugin: dev.zacsweers.metro

---

## 1. Root AppGraph with Factory

The application-level dependency graph. Uses `@DependencyGraph.Factory` to bind runtime parameters (like the `Application` instance) into the graph.

```kotlin
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import android.app.Application

abstract class AppScope private constructor()

@DependencyGraph(AppScope::class)
interface AppGraph {
    // Accessor bindings -- expose dependencies to the outside
    val circuit: Circuit
    val appConfig: AppConfig
    val imageLoader: ImageLoader

    // Member injection
    fun inject(application: CatchUpApplication)

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Application,
            @Provides @Named("debug") isDebug: Boolean,
        ): AppGraph
    }
}

// In Application.onCreate()
class CatchUpApplication : Application() {
    lateinit var appGraph: AppGraph

    override fun onCreate() {
        super.onCreate()
        appGraph = createGraphFactory<AppGraph.Factory>()
            .create(
                application = this,
                isDebug = BuildConfig.DEBUG,
            )
        appGraph.inject(this)
    }
}
```

---

## 2. Circuit Module with Multibinds

Collects all `Presenter.Factory` and `Ui.Factory` contributions from across the app and assembles the `Circuit` instance. This is the standard CatchUp wiring pattern.

```kotlin
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import com.slack.circuit.foundation.Circuit
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.ui.Ui

@ContributesTo(AppScope::class)
interface CircuitModule {
    @Multibinds fun presenterFactories(): Set<Presenter.Factory>
    @Multibinds fun viewFactories(): Set<Ui.Factory>

    companion object {
        @Provides
        @SingleIn(AppScope::class)
        fun provideCircuit(
            presenterFactories: Set<Presenter.Factory>,
            uiFactories: Set<Ui.Factory>,
        ): Circuit = Circuit.Builder()
            .addPresenterFactories(presenterFactories)
            .addUiFactories(uiFactories)
            .build()
    }
}
```

**Key points:**
- `@Multibinds` declarations are required before any `@IntoSet` or `@ContributesIntoSet` contributions.
- Circuit codegen with `circuit.codegen.mode = "METRO"` generates `@ContributesIntoSet` for each `@CircuitInject`-annotated factory.
- The `Circuit` instance is scoped with `@SingleIn(AppScope::class)` so it is created once.

---

## 3. Circuit Presenter with @AssistedInject

The standard pattern for a Circuit Presenter that needs a `Navigator` at runtime. The `Navigator` is `@Assisted` because it is provided by Circuit at navigation time, not from the DI graph.

```kotlin
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import androidx.compose.runtime.Composable

@AssistedInject
class HomePresenter(
    @Assisted private val navigator: Navigator,
    private val userRepository: UserRepository,
    private val settingsRepository: SettingsRepository,
    private val analyticsTracker: AnalyticsTracker,
) : Presenter<HomeScreen.State> {

    @Composable
    override fun present(): HomeScreen.State {
        val users = userRepository.observeUsers().collectAsRetainedState(emptyList())
        return HomeScreen.State(
            users = users.value,
            eventSink = { event ->
                when (event) {
                    is HomeScreen.Event.OnUserClick -> {
                        analyticsTracker.track("user_click", mapOf("id" to event.userId))
                        navigator.goTo(UserDetailScreen(event.userId))
                    }
                    is HomeScreen.Event.OnSettingsClick -> {
                        navigator.goTo(SettingsScreen)
                    }
                }
            },
        )
    }

    @CircuitInject(HomeScreen::class, AppScope::class)
    @AssistedFactory
    fun interface Factory {
        fun create(navigator: Navigator): HomePresenter
    }
}
```

**When no assisted params are needed** (no Navigator), use `@Inject` instead:

```kotlin
import dev.zacsweers.metro.Inject
import com.slack.circuit.codegen.annotations.CircuitInject

@Inject
@CircuitInject(AboutScreen::class, AppScope::class)
class AboutPresenter(
    private val appConfig: AppConfig,
) : Presenter<AboutScreen.State> {
    @Composable
    override fun present(): AboutScreen.State {
        return AboutScreen.State(version = appConfig.version)
    }
}
```

---

## 4. Service Map Contribution

CatchUp uses map multibindings keyed by string to register services. Each service module contributes itself into a shared `Map<String, Service>`.

```kotlin
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.StringKey
import dev.zacsweers.metro.binding

// The service interface
interface Service {
    suspend fun fetchItems(page: Int): List<Item>
}

// A concrete service contributed to the map
@ContributesIntoMap(AppScope::class, binding = binding<Service>())
@StringKey("slashdot")
@SingleIn(AppScope::class)
@Inject
class SlashdotService(
    private val api: SlashdotApi,
    private val parser: SlashdotParser,
) : Service {
    override suspend fun fetchItems(page: Int): List<Item> {
        return api.fetchStories(page).map(parser::parse)
    }
}

// Another service
@ContributesIntoMap(AppScope::class, binding = binding<Service>())
@StringKey("hackernews")
@SingleIn(AppScope::class)
@Inject
class HackerNewsService(
    private val api: HackerNewsApi,
) : Service {
    override suspend fun fetchItems(page: Int): List<Item> {
        return api.topStories(page).map { it.toItem() }
    }
}

// Consuming the map
@Inject
class ServiceManager(
    private val services: Map<String, Service>,
) {
    suspend fun fetchFrom(key: String, page: Int): List<Item> {
        return services[key]?.fetchItems(page)
            ?: error("Unknown service: $key. Available: ${services.keys}")
    }
}
```

**Key points:**
- `binding = binding<Service>()` explicitly specifies the bound type in the map.
- `@StringKey("slashdot")` provides the map key.
- The `@Multibinds` declaration for `Map<String, Service>` must exist in a contributed module.

---

## 5. Module with @ContributesTo

A module that provides network-layer dependencies with qualifiers, bindings, and scoping.

```kotlin
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.Qualifier
import dev.zacsweers.metro.IntoSet

@Qualifier
private annotation class InternalApi

@Qualifier
private annotation class ExternalApi

@ContributesTo(AppScope::class)
interface NetworkModule {
    @Binds val RealAuthInterceptor.bind: AuthInterceptor

    companion object {
        @Provides
        @SingleIn(AppScope::class)
        @InternalApi
        fun provideInternalOkHttp(
            authInterceptor: AuthInterceptor,
            loggingInterceptor: LoggingInterceptor,
        ): OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .build()

        @Provides
        @SingleIn(AppScope::class)
        @ExternalApi
        fun provideExternalOkHttp(
            loggingInterceptor: LoggingInterceptor,
        ): OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .build()

        @Provides
        @SingleIn(AppScope::class)
        fun provideRetrofit(
            @InternalApi okHttpClient: OkHttpClient,
            @Named("baseUrl") baseUrl: String,
        ): Retrofit = Retrofit.Builder()
            .client(okHttpClient)
            .baseUrl(baseUrl)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()

        @Provides
        @IntoSet
        fun provideLoggingInterceptor(): Interceptor = LoggingInterceptor()
    }
}
```

**Key points:**
- `@Qualifier` annotations (`@InternalApi`, `@ExternalApi`) disambiguate same-type bindings.
- `@Binds` on an extension property binds `RealAuthInterceptor` to `AuthInterceptor`.
- `@IntoSet` contributes the logging interceptor to a `Set<Interceptor>`.

---

## 6. @ContributesBinding with replaces

Override a real implementation with a fake for debug builds or testing. The `replaces` parameter removes the original binding.

```kotlin
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

// Production implementation
interface AnalyticsTracker {
    fun track(event: String, properties: Map<String, Any> = emptyMap())
}

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class FirebaseAnalyticsTracker(
    private val firebase: FirebaseAnalytics,
) : AnalyticsTracker {
    override fun track(event: String, properties: Map<String, Any>) {
        firebase.logEvent(event, properties.toBundle())
    }
}

// Debug override -- replaces production implementation
@ContributesBinding(AppScope::class, replaces = [FirebaseAnalyticsTracker::class])
@SingleIn(AppScope::class)
@Inject
class DebugAnalyticsTracker(
    private val logger: Logger,
) : AnalyticsTracker {
    override fun track(event: String, properties: Map<String, Any>) {
        logger.debug("Analytics: $event -> $properties")
    }
}
```

**Key points:**
- `replaces = [FirebaseAnalyticsTracker::class]` removes the original from the graph.
- Place the debug override in a `debug` source set so it only compiles in debug builds.
- Both classes implement `AnalyticsTracker` -- Metro resolves the replacement automatically.

---

## 7. Scope Hierarchy with @GraphExtension

Model nested scopes: `AppScope` (application-wide) -> `UserScope` (authenticated session) -> `ActivityScope` (per-screen).

```kotlin
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraphFactory

abstract class AppScope private constructor()
abstract class UserScope private constructor()
abstract class ActivityScope private constructor()

// Root graph
@DependencyGraph(AppScope::class)
interface AppGraph {
    val circuit: Circuit
    val userGraphFactory: UserGraph.Factory  // Expose child factory

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides application: Application): AppGraph
    }
}

// User session graph -- extends AppGraph
@DependencyGraph(UserScope::class)
@GraphExtension
interface UserGraph {
    val userProfile: UserProfile
    val activityGraphFactory: ActivityGraph.Factory  // Expose grandchild factory

    @GraphExtension.Factory
    interface Factory {
        fun create(
            @Provides user: AuthenticatedUser,
            @Provides authToken: AuthToken,
        ): UserGraph
    }
}

// Per-activity graph -- extends UserGraph
@DependencyGraph(ActivityScope::class)
@GraphExtension
interface ActivityGraph {
    val presenter: MainPresenter

    @GraphExtension.Factory
    interface Factory {
        fun create(@Provides activityContext: ActivityContext): ActivityGraph
    }
}

// Usage: creating the scope hierarchy
val appGraph = createGraphFactory<AppGraph.Factory>().create(application)

// On login
val userGraph = appGraph.userGraphFactory.create(
    user = authenticatedUser,
    authToken = token,
)

// On activity creation
val activityGraph = userGraph.activityGraphFactory.create(
    activityContext = ActivityContext(this),
)

// Scoped bindings within UserScope
@SingleIn(UserScope::class)
@Inject
class UserSessionManager(
    private val user: AuthenticatedUser,
    private val authToken: AuthToken,
    private val api: UserApi,
) {
    suspend fun refreshSession(): AuthToken = api.refresh(authToken)
}
```

**Key points:**
- Each `@GraphExtension` inherits all bindings from its parent.
- Parent graphs expose child factories as accessor properties.
- `@SingleIn(UserScope::class)` ensures one instance per user session.
- Destroying the `UserGraph` reference allows GC of all user-scoped dependencies.

---

## 8. @IntoSet Multibinding for Initializers

Contribute initialization tasks from separate modules. The app runs all contributed initializers at startup.

```kotlin
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.SingleIn

// Type alias for clarity
typealias AppInitializer = () -> Unit

// Declare the multibinding
@ContributesTo(AppScope::class)
interface InitializerModule {
    @Multibinds(allowEmpty = true)
    fun initializers(): Set<AppInitializer>
}

// Analytics module contributes an initializer
@ContributesTo(AppScope::class)
interface AnalyticsInitModule {
    companion object {
        @Provides
        @IntoSet
        fun provideAnalyticsInit(
            analytics: AnalyticsTracker,
        ): AppInitializer = {
            analytics.track("app_start")
        }
    }
}

// Crash reporting module contributes an initializer
@ContributesTo(AppScope::class)
interface CrashReportingInitModule {
    companion object {
        @Provides
        @IntoSet
        fun provideCrashReportingInit(
            @Named("debug") isDebug: Boolean,
        ): AppInitializer = {
            if (!isDebug) {
                CrashReporter.initialize()
            }
        }
    }
}

// Timber logging module contributes an initializer
@ContributesTo(AppScope::class)
interface TimberInitModule {
    companion object {
        @Provides
        @IntoSet
        fun provideTimberInit(
            @Named("debug") isDebug: Boolean,
        ): AppInitializer = {
            if (isDebug) {
                Timber.plant(Timber.DebugTree())
            }
        }
    }
}

// Run all initializers at startup
@Inject
class AppStartup(
    private val initializers: Set<AppInitializer>,
) {
    fun initialize() {
        initializers.forEach { init -> init() }
    }
}
```

---

## 9. Custom Qualifier

Define project-specific qualifiers for disambiguating same-type bindings.

```kotlin
import dev.zacsweers.metro.Qualifier
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

// Custom qualifiers
@Qualifier
annotation class IoDispatcher

@Qualifier
annotation class MainDispatcher

@Qualifier
annotation class DefaultDispatcher

@Qualifier
annotation class UnconfinedDispatcher

@ContributesTo(AppScope::class)
interface DispatcherModule {
    companion object {
        @Provides
        @IoDispatcher
        fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

        @Provides
        @MainDispatcher
        fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main

        @Provides
        @DefaultDispatcher
        fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

        @Provides
        @UnconfinedDispatcher
        fun provideUnconfinedDispatcher(): CoroutineDispatcher = Dispatchers.Unconfined
    }
}

// Consuming qualified dependencies
@SingleIn(AppScope::class)
@Inject
class UserRepository(
    private val api: UserApi,
    private val cache: UserCache,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
) {
    suspend fun getUser(id: String): User = withContext(ioDispatcher) {
        cache.get(id) ?: api.fetchUser(id).also { cache.put(id, it) }
    }

    suspend fun observeUser(id: String): Flow<User> = cache.observe(id)
        .flowOn(ioDispatcher)
}
```

**Key points:**
- `@Qualifier` is a meta-annotation -- apply it to your custom annotation class.
- Use `@Qualifier` when you have multiple bindings of the same type (e.g., multiple `CoroutineDispatcher` instances).
- The qualifier must be present at both the `@Provides` site and the injection site.

---

## 10. Dynamic Graph for Testing

Use `createDynamicGraph` or `createDynamicGraphFactory` to override bindings in tests without modifying production code.

```kotlin
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createDynamicGraph
import dev.zacsweers.metro.createDynamicGraphFactory

// Fake implementations for testing
class FakeUserRepository : UserRepository {
    private val users = mutableMapOf<String, User>()

    fun addUser(user: User) { users[user.id] = user }

    override suspend fun getUser(id: String): User =
        users[id] ?: error("User not found: $id")

    override suspend fun observeUser(id: String): Flow<User> =
        flowOf(getUser(id))
}

class FakeAnalyticsTracker : AnalyticsTracker {
    val events = mutableListOf<Pair<String, Map<String, Any>>>()

    override fun track(event: String, properties: Map<String, Any>) {
        events.add(event to properties)
    }
}

// Test binding container -- overrides production bindings
@BindingContainer
interface TestBindings {
    companion object {
        @Provides
        @SingleIn(AppScope::class)
        fun provideFakeUserRepo(): UserRepository = FakeUserRepository()

        @Provides
        @SingleIn(AppScope::class)
        fun provideFakeAnalytics(): AnalyticsTracker = FakeAnalyticsTracker()
    }
}

// Test using createDynamicGraph (no factory params)
class UserRepositoryTest {
    private lateinit var graph: AppGraph

    @BeforeTest
    fun setup() {
        graph = createDynamicGraph<AppGraph>(TestBindings())
    }

    @Test
    fun `getUser returns cached user`() = runTest {
        val repo = graph.userRepository as FakeUserRepository
        repo.addUser(User(id = "1", name = "Alice"))

        val result = graph.userRepository.getUser("1")
        assertEquals("Alice", result.name)
    }
}

// Test using createDynamicGraphFactory (with factory params)
class AppGraphIntegrationTest {
    private lateinit var graph: AppGraph

    @BeforeTest
    fun setup() {
        val factory = createDynamicGraphFactory<AppGraph.Factory>(TestBindings())
        graph = factory.create(
            application = TestApplication(),
            isDebug = true,
        )
    }

    @Test
    fun `circuit is configured with all factories`() {
        val circuit = graph.circuit
        assertNotNull(circuit)
    }

    @Test
    fun `analytics events are captured`() = runTest {
        val analytics = graph.analyticsTracker as FakeAnalyticsTracker

        // Trigger some action that tracks analytics
        graph.appStartup.initialize()

        assertTrue(analytics.events.any { it.first == "app_start" })
    }
}

// Per-test overrides with inline BindingContainer
class SpecificOverrideTest {
    @BindingContainer
    interface OfflineBindings {
        companion object {
            @Provides
            @SingleIn(AppScope::class)
            fun provideOfflineApi(): UserApi = object : UserApi {
                override suspend fun fetchUser(id: String): User =
                    error("Network unavailable")
            }
        }
    }

    @Test
    fun `handles offline gracefully`() = runTest {
        val graph = createDynamicGraph<AppGraph>(OfflineBindings())
        val result = runCatching { graph.userRepository.getUser("1") }
        assertTrue(result.isFailure)
    }
}
```

**Key points:**
- `createDynamicGraph` accepts `@BindingContainer` instances that override production bindings.
- Test containers provide fake implementations for isolation.
- `createDynamicGraphFactory` is used when the graph requires factory parameters.
- Each test can define its own `@BindingContainer` for specific override scenarios.
- Dynamic graphs are compile-time verified -- invalid overrides cause build errors.

---

## 11. MetroX Android -- AppComponentFactory Setup

Full setup for constructor-injected Activities and Services via `metrox-android`. This standardizes the manual pattern used in CatchUp.

```kotlin
import android.app.Application
import android.app.Activity
import android.app.Service
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.metrox.android.MetroApplication
import dev.zacsweers.metro.metrox.android.MetroAppComponentProviders
import dev.zacsweers.metro.metrox.android.ActivityKey
import dev.zacsweers.metro.metrox.android.ServiceKey
import com.slack.circuit.foundation.Circuit

abstract class AppScope private constructor()

// 1. Graph implements MetroAppComponentProviders
@DependencyGraph(AppScope::class)
interface AppGraph : MetroAppComponentProviders {
    val circuit: Circuit

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides application: Application): AppGraph
    }
}

// 2. Application implements MetroApplication
class MyApp : Application(), MetroApplication {
    private val appGraph by lazy {
        createGraphFactory<AppGraph.Factory>().create(this)
    }
    override val appComponentProviders: MetroAppComponentProviders get() = appGraph
}

// 3. Activities with constructor injection
@ContributesIntoMap(AppScope::class, binding<Activity>())
@ActivityKey(MainActivity::class)
@Inject
class MainActivity(
    private val circuit: Circuit,
    private val preferences: AppPreferences,
    private val deepLinkHandler: DeepLinkHandler,
) : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CircuitCompositionLocals(circuit) {
                ContentWithOverlays {
                    NavigableCircuitContent(navigator = rememberCircuitNavigator(...))
                }
            }
        }
    }
}

// 4. Services with constructor injection
@ContributesIntoMap(AppScope::class, binding<Service>())
@ServiceKey(SyncService::class)
@Inject
class SyncService(
    private val syncManager: SyncManager,
    private val notificationHelper: NotificationHelper,
) : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        syncManager.startSync()
        return START_STICKY
    }
    override fun onBind(intent: Intent?) = null
}
```

**AndroidManifest.xml:**
```xml
<application
    android:name=".MyApp"
    android:appComponentFactory="dev.zacsweers.metro.metrox.android.MetroAppComponentFactory">

    <activity android:name=".MainActivity" />
    <service android:name=".SyncService" />
</application>
```

**Key points:**
- `MetroAppComponentProviders` exposes provider maps that `MetroAppComponentFactory` reads.
- `@ActivityKey` / `@ServiceKey` are built-in map key annotations from MetroX.
- The factory creates components via constructor injection before `onCreate()` is called.
- Requires API 28+ for `AppComponentFactory`.
- CatchUp does this manually with a custom `@ActivityKey` annotation -- MetroX provides it built-in.

---

## 12. MetroX ViewModel -- Standard + Assisted + Manual

Full ViewModel injection setup using `metrox-viewmodel`. Use this for ViewModel-based apps (not Circuit).

```kotlin
import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.metrox.viewmodel.ViewModelGraph
import dev.zacsweers.metro.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metro.metrox.viewmodel.ViewModelAssistedFactory
import dev.zacsweers.metro.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metro.metrox.viewmodel.ViewModelKey
import dev.zacsweers.metro.metrox.viewmodel.ViewModelAssistedFactoryKey
import dev.zacsweers.metro.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import kotlin.reflect.KClass

abstract class AppScope private constructor()

// 1. Graph extends ViewModelGraph
@DependencyGraph(AppScope::class)
interface AppGraph : ViewModelGraph {
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides application: Application): AppGraph
    }
}

// 2. Concrete MetroViewModelFactory binding (REQUIRED)
@Inject
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class AppViewModelFactory(
    override val viewModelProviders: Map<KClass<out ViewModel>, Provider<ViewModel>>,
    override val assistedFactoryProviders: Map<KClass<out ViewModel>, Provider<ViewModelAssistedFactory>>,
    override val manualAssistedFactoryProviders: Map<KClass<out ManualViewModelAssistedFactory>, Provider<ManualViewModelAssistedFactory>>,
) : MetroViewModelFactory()

// --- Tier 1: Standard ViewModel (no runtime params) ---

@Inject
@ViewModelKey(HomeViewModel::class)
@ContributesIntoMap(AppScope::class)
class HomeViewModel(
    private val userRepository: UserRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val users = userRepository.observeUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

// --- Tier 2: Assisted ViewModel (via CreationExtras) ---

@AssistedInject
class UserDetailViewModel(
    @Assisted extras: CreationExtras,
    private val userRepository: UserRepository,
) : ViewModel() {
    private val userId = extras[USER_ID_KEY]!!

    val user = userRepository.observeUser(userId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @AssistedFactory
    @ViewModelAssistedFactoryKey(UserDetailViewModel::class)
    @ContributesIntoMap(AppScope::class)
    interface Factory : ViewModelAssistedFactory

    companion object {
        val USER_ID_KEY = object : CreationExtras.Key<String> {}
    }
}

// --- Tier 3: Manual Assisted ViewModel (typed params) ---

class EditProfileViewModel(
    val userId: String,
    val editMode: EditMode,
    private val userRepository: UserRepository,
) : ViewModel() {
    // ...

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey(Factory::class)
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(userId: String, editMode: EditMode): EditProfileViewModel
    }
}

// 3. Activity overrides defaultViewModelProviderFactory
@Inject
class MainActivity(
    private val viewModelFactory: MetroViewModelFactory,
) : ComponentActivity() {
    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = viewModelFactory
}
```

**Key points:**
- `ViewModelGraph` provides the multibinding maps -- your graph must extend it.
- You **must** create a concrete `MetroViewModelFactory` subclass and bind it.
- Three tiers: Standard (`@ViewModelKey`), Assisted (`@ViewModelAssistedFactoryKey`), Manual (`@ManualViewModelAssistedFactoryKey`).
- For Circuit apps, skip all of this and use `@AssistedInject` + `@CircuitInject` instead.

---

## 13. MetroX ViewModel Compose -- metroViewModel() in Compose

Using `metroViewModel()` as a drop-in replacement for `hiltViewModel()`.

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import dev.zacsweers.metro.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metro.metrox.viewmodel.compose.LocalMetroViewModelFactory
import dev.zacsweers.metro.metrox.viewmodel.compose.metroViewModel
import dev.zacsweers.metro.metrox.viewmodel.compose.assistedMetroViewModel

// 1. Provide factory at root (in Activity or top-level composable)
class MainActivity : ComponentActivity() {
    @Inject lateinit var metroVmf: MetroViewModelFactory

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalMetroViewModelFactory provides metroVmf) {
                AppNavigation()
            }
        }
    }
}

// 2. Standard ViewModel retrieval (replaces hiltViewModel())
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = metroViewModel(),
) {
    val users by viewModel.users.collectAsStateWithLifecycle()
    LazyColumn {
        items(users) { user -> UserRow(user) }
    }
}

// 3. Assisted ViewModel retrieval (via CreationExtras)
@Composable
fun UserDetailScreen(
    userId: String,
    viewModel: UserDetailViewModel = assistedMetroViewModel(
        extras = MutableCreationExtras().apply {
            set(UserDetailViewModel.USER_ID_KEY, userId)
        }
    ),
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    user?.let { UserProfile(it) }
}

// 4. Manual Assisted ViewModel retrieval (typed factory params)
@Composable
fun EditProfileScreen(
    userId: String,
    mode: EditMode,
    viewModel: EditProfileViewModel = assistedMetroViewModel<EditProfileViewModel, EditProfileViewModel.Factory> {
        create(userId, mode)
    },
) {
    // Use viewModel
}
```

**Key points:**
- `LocalMetroViewModelFactory` must be provided at the Compose root before `metroViewModel()` can be called.
- `metroViewModel<T>()` is a reified inline composable -- the type is inferred from the assignment.
- `assistedMetroViewModel()` has two overloads: one with `CreationExtras`, one with a lambda for manual factories.
- `metrox-viewmodel-compose` transitively includes `metrox-viewmodel` -- only add the compose artifact.

---

## 14. CatchUp Manual Activity Injection (Without MetroX)

How CatchUp handles Activity injection manually -- without `metrox-android`. This is the pattern MetroX standardizes.

```kotlin
import android.app.Activity
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.MapKey
import dev.zacsweers.metro.binding
import kotlin.reflect.KClass

// Custom ActivityKey (CatchUp defines its own; MetroX provides @ActivityKey built-in)
@MapKey
annotation class ActivityKey(val value: KClass<out Activity>)

// Activity contributed to map
@ActivityKey(MainActivity::class)
@ContributesIntoMap(AppScope::class, binding = binding<Activity>())
@Inject
class MainActivity(
    private val customTab: CustomTabActivityHelper,
    private val linkManager: LinkManager,
    private val circuit: Circuit,
    private val catchUpPreferences: CatchUpPreferences,
    private val rootContent: RootContent,
    private val deepLinkHandler: DeepLinkHandler,
    appConfig: AppConfig,
) : AppCompatActivity()

// The AppGraph does NOT implement MetroAppComponentProviders -- it uses its own map
@DependencyGraph(AppScope::class)
interface AppGraph {
    val activityProviders: Map<KClass<out Activity>, Provider<Activity>>
    // ...
}
```

**When to use this vs MetroX:**
- **New projects**: Use `metrox-android` -- built-in annotations, standard `MetroAppComponentFactory`.
- **Existing CatchUp-style projects**: Keep the manual approach; it works identically. Migrate to MetroX when convenient.
- **Circuit apps**: Both approaches work with Circuit. The choice is about Activity injection, not navigation.
