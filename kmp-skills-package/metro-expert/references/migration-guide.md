# Migration Guide: Dagger/Hilt → Metro

## Conceptual Mapping

| Dagger Concept | Metro Equivalent |
|----------------|------------------|
| `@Component` | `@DependencyGraph` |
| `@Subcomponent` | `@GraphExtension` |
| `@Module` | `@ContributesTo` interface or `@BindingContainer` |
| `@Provides` | `@Provides` (same!) |
| `@Binds` | `@Binds` (same!) |
| `@Inject` | `@Inject` (same!) |
| `@Singleton` | `@SingleIn(AppScope::class)` |
| `@BindsInstance` | `@Provides` on Factory param |
| `@Component.Factory` | `@DependencyGraph.Factory` |
| `@IntoSet` | `@IntoSet` or `@ContributesIntoSet` |
| `@IntoMap` | `@IntoMap` or `@ContributesIntoMap` |
| `@AssistedInject` | `@Inject` + `@Assisted` |
| `@AssistedFactory` | `@AssistedFactory` (same!) |

## Step-by-Step Migration

### 1. Update Build Configuration

**Before (Dagger + KAPT):**
```kotlin
plugins {
    id("kotlin-kapt")
}

dependencies {
    implementation("com.google.dagger:dagger:2.x")
    kapt("com.google.dagger:dagger-compiler:2.x")
}
```

**After (Metro):**
```kotlin
plugins {
    id("dev.zacsweers.metro") version "x.y.z"
}
// No additional dependencies needed!
```

### 2. Migrate Components

**Before (Dagger):**
```kotlin
@Singleton
@Component(modules = [AppModule::class, NetworkModule::class])
interface AppComponent {
    fun inject(activity: MainActivity)
    fun userRepository(): UserRepository
    
    @Component.Factory
    interface Factory {
        fun create(@BindsInstance application: Application): AppComponent
    }
}
```

**After (Metro):**
```kotlin
@DependencyGraph(AppScope::class)
interface AppGraph {
    val userRepository: UserRepository
    // Note: inject() methods not needed - use constructor injection
    
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides application: Application): AppGraph
    }
}
```

### 3. Migrate Modules

**Before (Dagger Module):**
```kotlin
@Module
class NetworkModule {
    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = HttpClient()
    
    @Provides
    fun provideApi(client: HttpClient): Api = ApiImpl(client)
}

@Module
abstract class BindingsModule {
    @Binds
    abstract fun bindRepository(impl: RealRepository): Repository
}
```

**After (Metro - Option 1: ContributesTo):**
```kotlin
@ContributesTo(AppScope::class)
interface NetworkModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideHttpClient(): HttpClient = HttpClient()
    
    @Provides
    fun provideApi(client: HttpClient): Api = ApiImpl(client)
    
    @Binds
    val RealRepository.bind: Repository
}
```

**After (Metro - Option 2: ContributesBinding):**
```kotlin
// Even simpler - no module needed!
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class RealRepository(private val api: Api) : Repository
```

### 4. Migrate Subcomponents

**Before (Dagger):**
```kotlin
@UserScope
@Subcomponent(modules = [UserModule::class])
interface UserComponent {
    fun userRepository(): UserRepository
    
    @Subcomponent.Factory
    interface Factory {
        fun create(@BindsInstance userId: String): UserComponent
    }
}

// In parent component
@Component
interface AppComponent {
    fun userComponentFactory(): UserComponent.Factory
}
```

**After (Metro):**
```kotlin
@GraphExtension(UserScope::class)
interface UserGraph {
    val userRepository: UserRepository
    
    @GraphExtension.Factory
    fun interface Factory {
        fun create(@Provides userId: String): UserGraph
    }
}

@DependencyGraph(AppScope::class)
interface AppGraph {
    val userGraphFactory: UserGraph.Factory
}
```

### 5. Migrate Scopes

**Before (Dagger):**
```kotlin
@Scope
@Retention(AnnotationRetention.RUNTIME)
annotation class UserScope

@UserScope
class UserManager @Inject constructor()
```

**After (Metro):**
```kotlin
// Scope is just a marker object
object UserScope

@SingleIn(UserScope::class)
@Inject
class UserManager
```

### 6. Migrate Member Injection

**Before (Dagger - field injection):**
```kotlin
class MainActivity : Activity() {
    @Inject lateinit var viewModel: MainViewModel
    
    override fun onCreate(savedInstanceState: Bundle?) {
        (application as App).component.inject(this)
        super.onCreate(savedInstanceState)
    }
}
```

**After (Metro - constructor injection):**
```kotlin
// Metro doesn't support field injection - use constructor injection
@Inject
class MainActivity(
    private val viewModel: MainViewModel,
) : Activity()

// With AppComponentFactory in AndroidManifest
class MetroAppComponentFactory : AppComponentFactory() {
    override fun instantiateActivityCompat(
        cl: ClassLoader,
        className: String,
        intent: Intent?,
    ): Activity {
        // Create activity with constructor injection
    }
}
```

**Alternative - Manual Graph Access:**
```kotlin
class MainActivity : Activity() {
    private val viewModel: MainViewModel by lazy {
        (application as App).appGraph.mainViewModel
    }
}
```

### 7. Migrate Assisted Injection

**Before (Dagger):**
```kotlin
class UserPresenter @AssistedInject constructor(
    @Assisted private val userId: String,
    private val repository: UserRepository,
) {
    @AssistedFactory
    interface Factory {
        fun create(userId: String): UserPresenter
    }
}
```

**After (Metro):**
```kotlin
// Almost identical!
@Inject
class UserPresenter(
    @Assisted private val userId: String,
    private val repository: UserRepository,
) {
    @AssistedFactory
    fun interface Factory {  // Can use fun interface!
        fun create(userId: String): UserPresenter
    }
}
```

### 8. Migrate Qualifiers

**Before (Dagger):**
```kotlin
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class IoDispatcher

@Module
object DispatchersModule {
    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}

class Repository @Inject constructor(
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
)
```

**After (Metro):**
```kotlin
// Exactly the same!
@Qualifier
annotation class IoDispatcher

@ContributesTo(AppScope::class)
interface DispatchersModule {
    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}

@Inject
class Repository(@IoDispatcher private val dispatcher: CoroutineDispatcher)
```

### 9. Migrate Multibindings

**Before (Dagger):**
```kotlin
@Module
abstract class InterceptorsModule {
    @Binds
    @IntoSet
    abstract fun bindLogging(impl: LoggingInterceptor): Interceptor
    
    @Binds
    @IntoSet
    abstract fun bindAuth(impl: AuthInterceptor): Interceptor
}
```

**After (Metro - Option 1):**
```kotlin
@ContributesTo(AppScope::class)
interface InterceptorsModule {
    @Binds
    @IntoSet
    val LoggingInterceptor.bindLogging: Interceptor
    
    @Binds
    @IntoSet
    val AuthInterceptor.bindAuth: Interceptor
}
```

**After (Metro - Option 2, simpler):**
```kotlin
@ContributesIntoSet(AppScope::class)
@Inject
class LoggingInterceptor : Interceptor

@ContributesIntoSet(AppScope::class)
@Inject
class AuthInterceptor(private val tokenProvider: TokenProvider) : Interceptor
```

## Hilt-Specific Migration

### Hilt Components → Metro Graphs

| Hilt Component | Metro Equivalent |
|----------------|------------------|
| `SingletonComponent` | `@DependencyGraph(AppScope::class)` |
| `ActivityComponent` | `@GraphExtension(ActivityScope::class)` |
| `ViewModelComponent` | `@GraphExtension(ViewModelScope::class)` |
| `FragmentComponent` | Not needed with Circuit/Compose |

### @HiltViewModel → Metro

**Before (Hilt):**
```kotlin
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: HomeRepository,
) : ViewModel()

// In Fragment/Activity
@AndroidEntryPoint
class HomeFragment : Fragment() {
    private val viewModel: HomeViewModel by viewModels()
}
```

**After (Metro + Circuit):**
```kotlin
// With Circuit, you don't need ViewModels!
@CircuitInject(HomeScreen::class, AppScope::class)
@Composable
fun HomePresenter(
    screen: HomeScreen,
    navigator: Navigator,
    repository: HomeRepository,  // Injected by Metro
): HomeScreen.State {
    // Presenter logic replaces ViewModel
}
```

**If you must use ViewModels:**
```kotlin
@ContributesIntoMap(AppScope::class)
@ViewModelKey(HomeViewModel::class)
@Inject
class HomeViewModel(
    private val repository: HomeRepository,
) : ViewModel()

// Custom ViewModel factory
@ContributesBinding(AppScope::class)
@Inject
class MetroViewModelFactory(
    private val providers: Map<KClass<out ViewModel>, Provider<ViewModel>>,
) : ViewModelProvider.Factory
```

### @InstallIn → @ContributesTo

**Before (Hilt):**
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideApi(): Api = ApiImpl()
}
```

**After (Metro):**
```kotlin
@ContributesTo(AppScope::class)
interface NetworkModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideApi(): Api = ApiImpl()
}
```

## Incremental Migration Strategy

### Phase 1: Enable Interop

```kotlin
// build.gradle.kts
metro {
    interop {
        enableDagger()  // Understand existing Dagger annotations
    }
}
```

This allows Metro to understand `@javax.inject.Inject`, `@Singleton`, etc.

### Phase 2: Create Metro Graph Alongside Dagger

```kotlin
// Include Dagger component as dependency
@DependencyGraph(AppScope::class)
interface AppGraph {
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Includes legacyComponent: LegacyDaggerComponent,
        ): AppGraph
    }
}
```

### Phase 3: Migrate Module by Module

1. Pick a leaf module (no internal dependencies)
2. Convert `@Module` → `@ContributesTo`
3. Convert `@Inject` classes → `@ContributesBinding`
4. Remove from Dagger component modules list
5. Test thoroughly
6. Repeat

### Phase 4: Replace Root Component

Once all modules are migrated:

```kotlin
// Remove Dagger component, use pure Metro
@DependencyGraph(AppScope::class)
interface AppGraph {
    // All bindings now come from Metro
}
```

### Phase 5: Clean Up

- Remove KAPT plugin
- Remove Dagger dependencies
- Remove Metro interop configuration
- Convert remaining `@Singleton` → `@SingleIn`

## Common Migration Issues

### "Missing binding" after migration

**Cause:** Module not contributed to correct scope

**Fix:** Add `@ContributesTo(AppScope::class)` or verify scope marker

### Field injection not working

**Cause:** Metro doesn't support field injection

**Fix:** Convert to constructor injection or use manual graph access

### Scope annotation mismatch

**Cause:** Dagger's `@Singleton` vs Metro's `@SingleIn`

**Fix:** Either enable Dagger interop or replace annotations

### Multibinding collection empty

**Cause:** Individual items not contributed to correct scope

**Fix:** Verify each `@ContributesIntoSet/Map` has correct scope

## Performance Comparison

| Metric | Dagger + KAPT | Dagger + KSP | Metro |
|--------|--------------|--------------|-------|
| Build time | Slowest | Medium | Fastest |
| Incremental | Poor | Good | Best |
| Generated code | More | More | Less |
| IDE support | Good | Good | Improving |

Metro's compiler plugin approach eliminates the KAPT/KSP overhead entirely, resulting in significantly faster builds especially for large projects.
