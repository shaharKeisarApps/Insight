---
name: metro-expert
description: Expert guidance on Metro Dependency Injection for KMP. Use for configuring dependency graphs, providing bindings, scopes, qualifiers, multibindings, graph extensions, assisted injection, and integrating with Circuit.
---

# Metro Expert Skill (v0.10.3)

## Overview

Metro is a compile-time dependency injection framework for Kotlin Multiplatform. It uses a Gradle compiler plugin (FIR + IR phases) -- NOT KSP or KAPT. This means zero annotation processing overhead and full KMP support.

## When to Use

- Creating dependency graphs (`@DependencyGraph`)
- Providing dependencies (`@Provides`, `@Binds`, `@ContributesBinding`)
- Constructor injection (`@Inject`)
- Assisted injection for Circuit Presenters (`@AssistedInject`, `@AssistedFactory`)
- Scoping singletons (`@SingleIn`)
- Multibindings (`@IntoSet`, `@IntoMap`, `@Multibinds`)
- Qualifiers (`@Qualifier`, `@Named`)
- Modular graph composition (`@ContributesTo`, `@GraphExtension`)
- Testing with dynamic graphs (`createDynamicGraph`)
- Android Activity/Service constructor injection via `AppComponentFactory` (`metrox-android`)
- AndroidX ViewModel injection (`metrox-viewmodel`, `metrox-viewmodel-compose`)
- Migrating from Hilt `hiltViewModel()` to Metro (`metroViewModel()`)

## Quick Reference

For detailed annotations and APIs, see [reference.md](reference.md).
For production examples (from CatchUp), see [examples.md](examples.md).

## Core Concepts

### Graph

The dependency container. Defined as an interface annotated with `@DependencyGraph`:

```kotlin
@DependencyGraph(AppScope::class)
interface AppGraph {
    val circuit: Circuit
    fun inject(app: MyApplication)

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides application: Application): AppGraph
    }
}
```

### Scope

Defines binding lifetime. Declare scope markers as abstract classes:

```kotlin
abstract class AppScope private constructor()
abstract class UserScope private constructor()
abstract class ActivityScope private constructor()
```

### Graph Factory

Create graphs with runtime parameters:

```kotlin
val graph = createGraphFactory<AppGraph.Factory>().create(application)
```

### Graph Extension

Extend an existing graph (like Dagger subcomponents):

```kotlin
@DependencyGraph(UserScope::class)
@GraphExtension
interface UserGraph {
    @GraphExtension.Factory
    interface Factory {
        fun create(@Provides user: User): UserGraph
    }
}
```

## Metro vs Dagger/Hilt Terminology

| Dagger/Hilt | Metro | Notes |
|-------------|-------|-------|
| `@Component` | `@DependencyGraph` | Interface defining the container |
| `@Subcomponent` | `@GraphExtension` | Child graph with parent access |
| `@Module` | `@ContributesTo` | Module contribution to a scope |
| `@Binds` | `@Binds` | Same -- binds impl to interface |
| `@Provides` | `@Provides` | Same -- factory function |
| `@Inject` | `@Inject` | Same -- constructor injection |
| `@Singleton` | `@SingleIn(AppScope::class)` | Scoped singleton |
| `@Qualifier` | `@Qualifier` | Same -- disambiguate bindings |
| `@Named` | `@Named` | Same -- string-based qualifier |
| `@IntoSet` / `@IntoMap` | `@IntoSet` / `@IntoMap` | Same -- multibinding |
| `@Multibinds` | `@Multibinds` | Same -- declare empty multibinding |
| `@BindsInstance` | `@Provides` on factory param | Bind instance at creation |
| `@AssistedInject` | `@AssistedInject` | Same -- assisted injection |
| `@AssistedFactory` | `@AssistedFactory` | Same -- factory interface |
| `@ContributesMultibinding` (Anvil) | `@ContributesIntoSet` / `@ContributesIntoMap` | Multi-module aggregation |
| `@ContributesBinding` (Anvil) | `@ContributesBinding` | Bind impl across modules |

## Circuit Integration

> **CRITICAL**: Metro itself is a compiler plugin (NOT KSP), but Circuit's `@CircuitInject` codegen
> DOES use KSP. You need BOTH the Metro Gradle plugin AND the KSP plugin + `circuit-codegen` dependency.
> Without `circuit-codegen`, `@CircuitInject` annotations are silently ignored and screens won't render.

### Required Gradle Setup (build.gradle.kts)

```kotlin
plugins {
    id("dev.zacsweers.metro")       // Metro compiler plugin
    id("com.google.devtools.ksp")   // KSP for Circuit codegen (NOT for Metro)
}

// Tell Circuit's KSP processor to generate Metro-compatible code
ksp { arg("circuit.codegen.mode", "METRO") }

dependencies {
    // Circuit codegen KSP processor - REQUIRED for @CircuitInject to work
    add("kspAndroid", libs.circuit.codegen)
    add("kspIosArm64", libs.circuit.codegen)
    add("kspIosSimulatorArm64", libs.circuit.codegen)
    // For multi-module KMP, use kspCommonMainMetadata instead (see gradle-plugin-expert)
}
```

Circuit codegen with Metro generates `@ContributesIntoSet` factories:

```kotlin

// Presenter with assisted Navigator
@AssistedInject
class HomePresenter(
    @Assisted private val navigator: Navigator,
    private val repo: UserRepository,
) : Presenter<HomeScreen.State> {
    @CircuitInject(HomeScreen::class, AppScope::class)
    @AssistedFactory
    fun interface Factory {
        fun create(navigator: Navigator): HomePresenter
    }
}

// Simple Presenter (no assisted params)
@Inject
@CircuitInject(MyScreen::class, AppScope::class)
class MyPresenter(private val repo: Repo) : Presenter<MyScreen.State> { ... }
```

## Circuit Module Wiring

```kotlin
@ContributesTo(AppScope::class)
interface CircuitModule {
    @Multibinds fun presenterFactories(): Set<Presenter.Factory>
    @Multibinds fun viewFactories(): Set<Ui.Factory>

    companion object {
        @Provides
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

## MetroX -- Android Extensions

MetroX is a collection of **optional** Android-specific extension artifacts. Metro itself is fully KMP -- MetroX adds Android-only integrations for `AppComponentFactory`, Activities, Services, and AndroidX ViewModels.

### Three Artifacts

| Artifact | Purpose | When to Use |
|----------|---------|-------------|
| `metrox-android` | `AppComponentFactory` for constructor-injected Activities, Services, BroadcastReceivers, ContentProviders | Android apps needing constructor injection for Android components (API 28+) |
| `metrox-viewmodel` | `MetroViewModelFactory` + `ViewModelGraph` for AndroidX ViewModel injection | Projects using ViewModels (migration from Hilt/manual DI) |
| `metrox-viewmodel-compose` | `metroViewModel()` + `LocalMetroViewModelFactory` composable helpers | Compose UI retrieving Metro-injected ViewModels (replaces `hiltViewModel()`) |

### When to Use MetroX vs Circuit

- **Circuit apps (recommended)**: Skip `metrox-viewmodel` entirely. Circuit Presenters replace ViewModels. Use `@AssistedInject` + `@CircuitInject` instead. CatchUp follows this approach.
- **ViewModel apps**: Use `metrox-viewmodel` + `metrox-viewmodel-compose` as a replacement for `hiltViewModel()`.
- **All Android apps**: Consider `metrox-android` for constructor-injected Activities/Services instead of manual `@ContributesIntoMap` patterns.

### metrox-android Quick Setup

Enables constructor injection for Android components via `AppComponentFactory` (API 28+):

```kotlin
// 1. AppGraph implements MetroAppComponentProviders
@DependencyGraph(AppScope::class)
interface AppGraph : MetroAppComponentProviders

// 2. Application implements MetroApplication
class MyApp : Application(), MetroApplication {
    private val appGraph by lazy { createGraph<AppGraph>() }
    override val appComponentProviders get() = appGraph
}

// 3. Activities use @ActivityKey + @ContributesIntoMap
@ContributesIntoMap(AppScope::class, binding<Activity>())
@ActivityKey(MainActivity::class)
@Inject
class MainActivity(private val circuit: Circuit) : AppCompatActivity()
```

### metrox-viewmodel Quick Setup

```kotlin
// 1. AppGraph extends ViewModelGraph
@DependencyGraph(AppScope::class)
interface AppGraph : ViewModelGraph

// 2. Provide MetroViewModelFactory binding
@Inject
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class MyViewModelFactory(
    override val viewModelProviders: Map<KClass<out ViewModel>, Provider<ViewModel>>,
    override val assistedFactoryProviders: Map<KClass<out ViewModel>, Provider<ViewModelAssistedFactory>>,
    override val manualAssistedFactoryProviders: Map<KClass<out ManualViewModelAssistedFactory>, Provider<ManualViewModelAssistedFactory>>,
) : MetroViewModelFactory()

// 3. ViewModels use @ViewModelKey
@Inject
@ViewModelKey(HomeViewModel::class)
@ContributesIntoMap(AppScope::class)
class HomeViewModel(private val repo: UserRepository) : ViewModel()
```

### metrox-viewmodel-compose Quick Setup

```kotlin
// In your root composable
CompositionLocalProvider(LocalMetroViewModelFactory provides metroVmf) {
    App()
}

// In screen composables (replaces hiltViewModel())
@Composable
fun HomeScreen(viewModel: HomeViewModel = metroViewModel()) { ... }
```

## Gradle Setup

```kotlin
// build.gradle.kts
plugins {
    id("dev.zacsweers.metro") version "0.10.3"
}

metro {
    enabled = true
    debug = false
    generateAssistedFactories = false
    transformProvidersToPrivate = true
    shrinkUnusedBindings = true
    reportsDestination = layout.buildDirectory.dir("metro")
}

dependencies {
    // Core (usually auto-added by plugin)
    implementation("dev.zacsweers.metro:runtime:<version>")

    // MetroX -- Android extensions (optional, add as needed)
    implementation("dev.zacsweers.metro:metrox-android:<version>")          // AppComponentFactory (API 28+)
    implementation("dev.zacsweers.metro:metrox-viewmodel:<version>")       // ViewModel injection
    implementation("dev.zacsweers.metro:metrox-viewmodel-compose:<version>") // metroViewModel() composable
}
```

## Core Rules

1. **Metro is a Gradle compiler plugin** -- no KSP/KAPT needed for Metro itself. However, Circuit codegen DOES need KSP (see Circuit Integration above).
2. **Use `@ContributesTo` for modules** -- contributes interfaces/classes into scoped graphs automatically.
3. **Use `@AssistedInject` (not `@Inject`) for Circuit Presenters** -- because Navigator is a runtime parameter.
4. **Use `@Multibinds` declarations** -- required before `@IntoSet`/`@IntoMap` contributions work.
5. **`@ContributesIntoSet`/`@ContributesIntoMap` for cross-module contributions** -- used by Circuit codegen.
6. **Factory fun interfaces** -- use `fun interface` for graph factories and assisted factories.
7. **`@Qualifier` for disambiguation** -- use custom `@Qualifier` annotations for same-type bindings.

## Common Pitfalls

1. **Applying KSP config for Metro** -- Metro itself does NOT use KSP (remove `ksp("dev.zacsweers.metro:...")` lines). BUT Circuit's codegen DOES use KSP -- you MUST add `circuit-codegen` as a KSP dependency with `circuit.codegen.mode=METRO`.
2. **Missing `@Multibinds` declaration** -- Metro requires explicit multibinding declarations before contributions.
3. **Using `@Inject` for assisted classes** -- Use `@AssistedInject` on the constructor, not `@Inject`.
4. **Non-public contributions** -- `@ContributesTo` interfaces should be `interface` (not `private`/`internal`) for cross-module visibility.
5. **Missing scope on `@DependencyGraph`** -- Always specify the scope class.
6. **Using `@BindsInstance` (Dagger)** -- Metro uses `@Provides` on factory parameters, not `@BindsInstance`.
7. **Forgetting `@SingleIn` on `@ContributesBinding`** -- Without it, a new instance is created each time.
8. **Positional arguments in interop annotations** -- Configure `interopAnnotationsNamedArgSeverity` to catch these.
9. **JS incremental compilation + top-level injection** -- `enableTopLevelFunctionInjection`, `generateContributionHintsInFir`, and `supportedHintContributionPlatforms` will error if enabled on Kotlin/JS with JS incremental compilation enabled. See [KT-82395](https://youtrack.jetbrains.com/issue/KT-82395).
10. **Assisted inject in SwitchingProvider/DelegateFactory** -- Since 0.10.3, assisted inject bindings are encapsulated by their consuming factory bindings and can't accidentally participate in `SwitchingProvider`s or cycle breaking with `DelegateFactory`.

## MetroX Pitfalls

11. **Using `metrox-viewmodel` with Circuit** -- Circuit Presenters replace ViewModels. Don't add MetroX ViewModel artifacts to Circuit-based apps. Use `@AssistedInject` + `@CircuitInject` instead.
12. **`metrox-android` on API < 28** -- `AppComponentFactory` requires API 28+. The artifact is useless on older targets.
13. **`MetroAppComponentFactory` uses base `Activity` type** -- It injects `Activity`, not `ComponentActivity`. For custom base types (e.g., `ComponentActivity`), create your own `AppComponentFactory` and override `tools:replace="android:appComponentFactory"` in the manifest.
14. **Missing `MetroViewModelFactory` binding** -- `ViewModelGraph` interface declares the maps but you must provide a concrete `MetroViewModelFactory` subclass via `@ContributesBinding`. Without it, `metroViewModel()` will crash at runtime.
15. **Forgetting `CompositionLocalProvider` for `LocalMetroViewModelFactory`** -- `metroViewModel()` reads from `LocalMetroViewModelFactory`. If not provided at the root, you get a missing CompositionLocal error.
16. **Using `metrox-android` single-variant in debug builds** -- Since 0.10.3, `metrox-android` publishes release only. This is normal for library consumption; your app's debug build still works fine because AGP handles variant matching.

## See Also

- [circuit-expert](../circuit-expert/SKILL.md) -- Circuit component wiring with Metro
- [modularization-expert](../modularization-expert/SKILL.md) -- Api/Impl module DI patterns
- [architecture-patterns-expert](../architecture-patterns-expert/SKILL.md) -- Layer architecture and DI strategy
- [lifecycle-viewmodel-expert](../lifecycle-viewmodel-expert/SKILL.md) -- AndroidX ViewModel patterns (when using MetroX ViewModel)
