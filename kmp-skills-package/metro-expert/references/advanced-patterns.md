# Advanced Metro Patterns

## Graph Extension Hierarchies

### Multi-Level Graph Extensions

```kotlin
// Root graph
@DependencyGraph(AppScope::class)
interface AppGraph {
    val userGraphFactory: UserGraph.Factory
    val httpClient: HttpClient
}

// First level extension
@GraphExtension(UserScope::class)
interface UserGraph {
    val userRepository: UserRepository
    val settingsGraphFactory: SettingsGraph.Factory
    
    @GraphExtension.Factory
    fun interface Factory {
        fun create(@Provides userId: String): UserGraph
    }
}

// Second level extension
@GraphExtension(SettingsScope::class)
interface SettingsGraph {
    val settingsManager: SettingsManager
    
    @GraphExtension.Factory
    fun interface Factory {
        fun create(): SettingsGraph
    }
}

// Usage - cascading graph creation
val appGraph = createGraph<AppGraph>()
val userGraph = appGraph.userGraphFactory.create("user123")
val settingsGraph = userGraph.settingsGraphFactory.create()
```

### Contributed Graph Extensions

```kotlin
// Contributed graph extension - merged at compile time
@ContributesGraphExtension(AppScope::class)
@GraphExtension(FeatureScope::class)
interface FeatureGraph {
    val featurePresenter: FeaturePresenter
    
    @GraphExtension.Factory
    fun interface Factory {
        fun create(@Provides featureId: String): FeatureGraph
    }
}

// Parent graph automatically gets the factory
@DependencyGraph(AppScope::class)
interface AppGraph {
    // featureGraphFactory is auto-contributed!
    val featureGraphFactory: FeatureGraph.Factory
}
```

## Advanced Multibindings

### Typed Map Keys

```kotlin
// Custom KClass-based key
@MapKey
annotation class ViewModelKey(val value: KClass<out ViewModel>)

@ContributesIntoMap(AppScope::class)
@ViewModelKey(HomeViewModel::class)
@Inject
class HomeViewModel : ViewModel()

@ContributesIntoMap(AppScope::class)
@ViewModelKey(ProfileViewModel::class)
@Inject
class ProfileViewModel(
    private val userRepository: UserRepository,
) : ViewModel()

// ViewModel factory using the map
@ContributesBinding(AppScope::class)
@Inject
class MetroViewModelFactory(
    private val viewModelProviders: Map<KClass<out ViewModel>, @JvmSuppressWildcards Provider<ViewModel>>,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return viewModelProviders[modelClass.kotlin]?.get() as? T
            ?: throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
```

### Collecting Multiple Interfaces

```kotlin
// Interface for app lifecycle
interface AppLifecycleObserver {
    fun onAppCreate()
    fun onAppTerminate()
}

// Each module contributes its observer
@ContributesIntoSet(AppScope::class)
@Inject
class AnalyticsLifecycleObserver : AppLifecycleObserver {
    override fun onAppCreate() { Analytics.init() }
    override fun onAppTerminate() { Analytics.flush() }
}

@ContributesIntoSet(AppScope::class)
@Inject
class CrashReportingLifecycleObserver : AppLifecycleObserver {
    override fun onAppCreate() { CrashReporter.init() }
    override fun onAppTerminate() { CrashReporter.sendReports() }
}

// App orchestrates all observers
@Inject
class AppLifecycleManager(
    private val observers: Set<@JvmSuppressWildcards AppLifecycleObserver>,
) {
    fun onCreate() = observers.forEach { it.onAppCreate() }
    fun onTerminate() = observers.forEach { it.onAppTerminate() }
}
```

### Empty Multibindings Declaration

```kotlin
// Declare empty set/map that can be contributed to
@DependencyGraph(AppScope::class)
interface AppGraph {
    // Declare the multibinding even if nothing contributes yet
    @Multibinds
    fun interceptors(): Set<Interceptor>
    
    @Multibinds
    fun featureFlags(): Map<String, Boolean>
}
```

## Interop with Other DI Frameworks

### Dagger Interop

```kotlin
// Enable in Gradle
metro {
    interop {
        enableDagger()  // Understands @javax.inject.* annotations
    }
}

// Existing Dagger code works
class LegacyService @javax.inject.Inject constructor(
    private val cache: javax.inject.Provider<Cache>,
)

// Can be injected into Metro graph
@DependencyGraph
interface AppGraph {
    val legacyService: LegacyService
}
```

### Anvil Interop

```kotlin
metro {
    interop {
        enableAnvil()  // Understands @ContributesTo etc from Anvil
    }
}

// Existing Anvil modules work
@com.squareup.anvil.annotations.ContributesTo(AppScope::class)
interface LegacyBindings {
    @Provides
    fun provideLegacyService(): LegacyService
}
```

### Including Dagger Components

```kotlin
// Include existing Dagger component
@DependencyGraph
interface AppGraph {
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Includes legacyComponent: LegacyDaggerComponent,
        ): AppGraph
    }
}

// All exposed bindings from LegacyDaggerComponent are available
```

## Optional and Nullable Bindings

### Default Parameters (Optional Dependencies)

```kotlin
// Metro supports Kotlin default parameters
@Inject
class FlexibleService(
    private val required: RequiredDep,
    private val optional: OptionalDep = DefaultOptionalDep(),  // Used if not provided
    private val logger: Logger = NoOpLogger(),
)

// If OptionalDep isn't in the graph, default is used
@DependencyGraph
interface AppGraph {
    val flexibleService: FlexibleService
    
    @Provides
    fun provideRequired(): RequiredDep = RequiredDep()
    // Note: No provider for OptionalDep - default will be used
}
```

### Nullable Bindings

```kotlin
@DependencyGraph
interface AppGraph {
    // Can provide null
    @Provides
    fun provideOptionalFeature(): Feature? = 
        if (BuildConfig.FEATURE_ENABLED) RealFeature() else null
}

@Inject
class FeatureConsumer(
    private val feature: Feature?,  // May be null
) {
    fun doWork() {
        feature?.execute() ?: logFeatureDisabled()
    }
}
```

## Scoping Strategies

### Feature Scopes

```kotlin
// Feature-level scope for lazy loading
object FeatureScope

@GraphExtension(FeatureScope::class)
interface FeatureGraph {
    val presenter: FeaturePresenter
    val repository: FeatureRepository
    
    @GraphExtension.Factory
    fun interface Factory {
        fun create(): FeatureGraph
    }
}

// Feature module - all components scoped together
@SingleIn(FeatureScope::class)
@Inject
class FeatureRepository(private val api: Api)

@SingleIn(FeatureScope::class)
@Inject
class FeaturePresenter(private val repository: FeatureRepository)

// When FeatureGraph is garbage collected, all scoped instances are too
```

### Screen Scopes with Circuit

```kotlin
object ScreenScope

// Per-screen cache that survives config changes
@SingleIn(ScreenScope::class)
@ContributesBinding(ScreenScope::class)
@Inject
class ScreenCache : Cache

// Circuit presenter with screen-scoped dependencies
@CircuitInject(DetailScreen::class, ScreenScope::class)
@Composable
fun DetailPresenter(
    screen: DetailScreen,
    navigator: Navigator,
    cache: ScreenCache,  // Shared within this screen's scope
): DetailScreen.State
```

## Performance Optimization

### Lazy Graph Creation

```kotlin
class App : Application() {
    // Lazy - only created when first accessed
    val appGraph: AppGraph by lazy {
        createGraphFactory<AppGraph.Factory>().create(this)
    }
    
    // For features that may never be used
    val heavyFeatureGraph: HeavyFeatureGraph by lazy {
        appGraph.heavyFeatureGraphFactory.create()
    }
}
```

### Avoiding Over-Scoping

```kotlin
// DON'T - scoping everything wastes memory
@SingleIn(AppScope::class)
@Inject
class DateFormatter  // Stateless! Should not be scoped

// DO - only scope stateful or expensive objects
@Inject
class DateFormatter  // Unscoped - new instance is cheap

@SingleIn(AppScope::class)
@Inject
class DatabaseConnection(  // Scoped - expensive to create
    private val config: DbConfig,
)
```

### Provider vs Direct Injection

```kotlin
@Inject
class SmartService(
    // Direct - created immediately with parent
    private val alwaysNeeded: AlwaysNeededDep,
    
    // Provider - created only when get() is called
    private val sometimesNeeded: Provider<SometimesNeededDep>,
    
    // Lazy - created once on first access, then cached
    private val expensiveOnce: Lazy<ExpensiveDep>,
)
```

## Module Organization

### Feature Module Pattern

```kotlin
// :feature:profile module
// ========================

// Public API
interface ProfileRepository {
    suspend fun getProfile(): Profile
}

// Internal implementation
@ContributesBinding(AppScope::class)
@Inject
internal class RealProfileRepository(
    private val api: Api,
    private val cache: Cache,
) : ProfileRepository

// Contributed bindings
@ContributesTo(AppScope::class)
interface ProfileBindings {
    val profileRepository: ProfileRepository
}
```

### Platform-Specific Bindings

```kotlin
// In commonMain
interface PlatformService {
    fun getPlatformName(): String
}

// In androidMain
@ContributesBinding(AppScope::class)
@Inject
class AndroidPlatformService(
    private val application: Application,
) : PlatformService {
    override fun getPlatformName() = "Android ${Build.VERSION.SDK_INT}"
}

// In iosMain
@ContributesBinding(AppScope::class)
@Inject
class IosPlatformService : PlatformService {
    override fun getPlatformName() = "iOS ${UIDevice.currentDevice.systemVersion}"
}

// Metro automatically uses correct impl per platform
```

## Testing Patterns

### Test Module Replacement

```kotlin
// Production binding
@ContributesBinding(AppScope::class)
@Inject
class RealNetworkService : NetworkService

// Test replacement
@ContributesBinding(
    AppScope::class,
    replaces = [RealNetworkService::class],
)
class FakeNetworkService : NetworkService {
    var nextResponse: Response = Response.Success(emptyList())
    
    override suspend fun fetch(): Response = nextResponse
}

// Test graph excludes production, includes fake
@DependencyGraph(
    scope = AppScope::class,
    excludes = [RealNetworkService::class],
)
interface TestAppGraph
```

### Test Graph Factory

```kotlin
// Reusable test graph builder
object TestGraphs {
    fun createAppGraph(
        fakeApi: FakeApi = FakeApi(),
        fakeAnalytics: FakeAnalytics = FakeAnalytics(),
    ): TestAppGraph {
        return createGraphFactory<TestAppGraph.Factory>()
            .create(
                api = fakeApi,
                analytics = fakeAnalytics,
            )
    }
}

// In tests
@Test
fun `test with custom fake`() {
    val fakeApi = FakeApi().apply {
        nextResponse = Response.Error("Network error")
    }
    val graph = TestGraphs.createAppGraph(fakeApi = fakeApi)
    val presenter = graph.homePresenter
    // Test error handling...
}
```

## Error Handling

### Common Error Messages

**"Missing binding for X"**
- Check that X has @Inject constructor or @Provides method
- Verify X is in a module that's contributed to the right scope
- Check for typos in qualifiers

**"Duplicate binding for X"**
- Two @Provides methods return same type
- @ContributesBinding conflicts with explicit @Provides
- Use qualifiers to disambiguate or `replaces` to override

**"Scope mismatch"**
- Trying to inject UserScope dependency into AppScope graph
- Child scopes can depend on parent, not vice versa

### Debugging Tips

```kotlin
// Enable verbose logging
metro {
    debug = true  // Prints detailed binding resolution
}

// Check generated code
// Build, then look in build/generated/... for Metro implementations
```
