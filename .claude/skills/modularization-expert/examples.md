# Modularization Examples

## 1. Feature Module Pair -- Complete Auth Feature

### :features:auth:api

#### build.gradle.kts

```kotlin
// features/auth/api/build.gradle.kts
plugins {
    id("my.kmp.feature.api")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
        }
    }
}
```

#### Screen Definition

```kotlin
// features/auth/api/src/commonMain/kotlin/com/myapp/feature/auth/AuthScreen.kt
package com.myapp.feature.auth

import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import kotlinx.parcelize.Parcelize

@Parcelize
data object LoginScreen : Screen {
    data class State(
        val email: String,
        val password: String,
        val isLoading: Boolean,
        val error: String?,
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data class EmailChanged(val email: String) : Event
        data class PasswordChanged(val password: String) : Event
        data object LoginClicked : Event
        data object ForgotPasswordClicked : Event
        data object SignUpClicked : Event
    }
}

@Parcelize
data object SignUpScreen : Screen

@Parcelize
data object ForgotPasswordScreen : Screen
```

#### Domain Model

```kotlin
// features/auth/api/src/commonMain/kotlin/com/myapp/feature/auth/model/User.kt
package com.myapp.feature.auth.model

import com.myapp.core.model.UserId

data class User(
    val id: UserId,
    val email: String,
    val displayName: String,
    val avatarUrl: String?,
)
```

#### Repository Interface

```kotlin
// features/auth/api/src/commonMain/kotlin/com/myapp/feature/auth/AuthRepository.kt
package com.myapp.feature.auth

import com.myapp.feature.auth.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    fun observeCurrentUser(): Flow<User?>
    suspend fun login(email: String, password: String): Result<User>
    suspend fun signUp(email: String, password: String, displayName: String): Result<User>
    suspend fun logout()
    suspend fun isLoggedIn(): Boolean
}
```

### :features:auth:impl

#### build.gradle.kts

```kotlin
// features/auth/impl/build.gradle.kts
plugins {
    id("my.kmp.feature.impl")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":features:auth:api"))
            implementation(project(":core:network"))
            implementation(project(":core:data"))

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.sqldelight.coroutines)
        }
    }
}
```

#### Presenter

```kotlin
// features/auth/impl/src/commonMain/kotlin/com/myapp/feature/auth/LoginPresenter.kt
package com.myapp.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.Assisted
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope

class LoginPresenter @AssistedInject constructor(
    @Assisted private val navigator: Navigator,
    private val authRepository: AuthRepository,
) : Presenter<LoginScreen.State> {

    @CircuitInject(LoginScreen::class, AppScope::class)
    @AssistedFactory
    fun interface Factory {
        fun create(navigator: Navigator): LoginPresenter
    }

    @Composable
    override fun present(): LoginScreen.State {
        var email by rememberRetained { mutableStateOf("") }
        var password by rememberRetained { mutableStateOf("") }
        var isLoading by rememberRetained { mutableStateOf(false) }
        var error by rememberRetained { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

        return LoginScreen.State(
            email = email,
            password = password,
            isLoading = isLoading,
            error = error,
        ) { event ->
            when (event) {
                is LoginScreen.Event.EmailChanged -> {
                    email = event.email
                    error = null
                }
                is LoginScreen.Event.PasswordChanged -> {
                    password = event.password
                    error = null
                }
                LoginScreen.Event.LoginClicked -> {
                    scope.launch {
                        isLoading = true
                        error = null
                        authRepository.login(email, password)
                            .onSuccess { navigator.resetRoot(HomeScreen) }
                            .onFailure { error = it.message }
                        isLoading = false
                    }
                }
                LoginScreen.Event.ForgotPasswordClicked -> navigator.goTo(ForgotPasswordScreen)
                LoginScreen.Event.SignUpClicked -> navigator.goTo(SignUpScreen)
            }
        }
    }
}
```

#### UI

```kotlin
// features/auth/impl/src/commonMain/kotlin/com/myapp/feature/auth/LoginUi.kt
package com.myapp.feature.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject

@CircuitInject(LoginScreen::class, AppScope::class)
@Composable
fun LoginUi(state: LoginScreen.State, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Welcome Back",
            style = MaterialTheme.typography.headlineMedium,
        )

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = state.email,
            onValueChange = { state.eventSink(LoginScreen.Event.EmailChanged(it)) },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = state.password,
            onValueChange = { state.eventSink(LoginScreen.Event.PasswordChanged(it)) },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        state.error?.let { errorMessage ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { state.eventSink(LoginScreen.Event.LoginClicked) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading,
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp))
            } else {
                Text("Log In")
            }
        }

        Spacer(Modifier.height(8.dp))

        TextButton(onClick = { state.eventSink(LoginScreen.Event.ForgotPasswordClicked) }) {
            Text("Forgot Password?")
        }

        TextButton(onClick = { state.eventSink(LoginScreen.Event.SignUpClicked) }) {
            Text("Create Account")
        }
    }
}
```

#### Repository Implementation

```kotlin
// features/auth/impl/src/commonMain/kotlin/com/myapp/feature/auth/data/AuthRepositoryImpl.kt
package com.myapp.feature.auth.data

import com.myapp.core.model.UserId
import com.myapp.feature.auth.AuthRepository
import com.myapp.feature.auth.model.User
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AuthRepositoryImpl(
    private val authApi: AuthApi,
    private val authLocalDataSource: AuthLocalDataSource,
) : AuthRepository {

    private val currentUser = MutableStateFlow<User?>(null)

    override fun observeCurrentUser(): Flow<User?> = currentUser

    override suspend fun login(email: String, password: String): Result<User> =
        suspendRunCatching {
            val dto = authApi.login(LoginRequest(email, password))
            val user = dto.toDomain()
            authLocalDataSource.saveToken(dto.token)
            authLocalDataSource.saveUser(user.toEntity())
            currentUser.value = user
            user
        }

    override suspend fun signUp(
        email: String,
        password: String,
        displayName: String,
    ): Result<User> = suspendRunCatching {
        val dto = authApi.signUp(SignUpRequest(email, password, displayName))
        val user = dto.toDomain()
        authLocalDataSource.saveToken(dto.token)
        authLocalDataSource.saveUser(user.toEntity())
        currentUser.value = user
        user
    }

    override suspend fun logout() {
        authLocalDataSource.clearToken()
        authLocalDataSource.clearUser()
        currentUser.value = null
    }

    override suspend fun isLoggedIn(): Boolean =
        authLocalDataSource.getToken() != null
}
```

#### DTO and Mapper

```kotlin
// features/auth/impl/src/commonMain/kotlin/com/myapp/feature/auth/data/AuthDto.kt
package com.myapp.feature.auth.data

import com.myapp.core.model.UserId
import com.myapp.feature.auth.model.User
import kotlinx.serialization.Serializable

@Serializable
internal data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
internal data class SignUpRequest(
    val email: String,
    val password: String,
    val displayName: String,
)

@Serializable
internal data class AuthResponse(
    val id: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String?,
    val token: String,
)

internal fun AuthResponse.toDomain(): User = User(
    id = UserId(id),
    email = email,
    displayName = displayName,
    avatarUrl = avatarUrl,
)
```

---

## 2. Convention Plugin -- KmpFeatureImplConventionPlugin

Complete convention plugin with all configuration for `:impl` feature modules.

```kotlin
// build-logic/convention/src/main/kotlin/KmpFeatureImplConventionPlugin.kt
import com.android.build.gradle.LibraryExtension
import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class KmpFeatureImplConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        // Apply base KMP library plugin (targets, Android config, Detekt)
        with(pluginManager) {
            apply("my.kmp.library")
            apply("my.kmp.compose")
            apply("dev.zacsweers.metro")
            apply("com.google.devtools.ksp")
        }

        // Metro configuration
        extensions.configure<dev.zacsweers.metro.gradle.MetroExtension> {
            enabled.set(true)
            debug.set(false)
            transformProvidersToPrivate.set(true)
            shrinkUnusedBindings.set(true)
        }

        // KSP for Circuit codegen with Metro mode
        extensions.configure<KspExtension> {
            arg("circuit.codegen.mode", "METRO")
        }

        // KMP source set dependencies
        extensions.configure<KotlinMultiplatformExtension> {
            sourceSets.apply {
                getByName("commonMain") {
                    dependencies {
                        // Circuit
                        implementation(libs.findLibrary("circuit.foundation").get())
                        implementation(libs.findLibrary("circuit.retained").get())
                        implementation(libs.findLibrary("circuit.codegenAnnotations").get())

                        // Metro DI runtime
                        implementation(libs.findLibrary("metro.runtime").get())

                        // Collections
                        implementation(libs.findLibrary("kotlinx.immutable").get())

                        // Coroutines
                        implementation(libs.findLibrary("kotlinx.coroutines.core").get())
                    }
                }
                getByName("commonTest") {
                    dependencies {
                        implementation(libs.findLibrary("circuit.test").get())
                        implementation(libs.findLibrary("kotlinx.coroutines.test").get())
                        implementation(libs.findLibrary("turbine").get())
                    }
                }
            }
        }

        // Circuit codegen KSP processor
        dependencies {
            add("kspCommonMainMetadata", libs.findLibrary("circuit.codegen").get())
        }
    }
}

// Extension property for version catalog access in convention plugins
internal val Project.libs
    get() = extensions.getByType(
        org.gradle.api.artifacts.VersionCatalogsExtension::class.java
    ).named("libs")
```

---

## 3. Core Network Module

### build.gradle.kts

```kotlin
// core/network/build.gradle.kts
plugins {
    id("my.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            implementation(project(":core:common"))

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.metro.runtime)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}
```

### HttpClient Provider

```kotlin
// core/network/src/commonMain/kotlin/com/myapp/core/network/NetworkModule.kt
package com.myapp.core.network

import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

@ContributesTo(AppScope::class)
interface NetworkModule {

    companion object {
        @Provides
        @SingleIn(AppScope::class)
        fun provideJson(): Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }

        @Provides
        @SingleIn(AppScope::class)
        fun provideHttpClient(
            json: Json,
            authInterceptor: AuthInterceptor,
        ): HttpClient = HttpClient {
            install(ContentNegotiation) { json(json) }
            install(Logging) { level = LogLevel.HEADERS }

            defaultRequest {
                url("https://api.myapp.com/v1/")
                contentType(ContentType.Application.Json)
            }
        }
    }
}
```

### Auth Interceptor

```kotlin
// core/network/src/commonMain/kotlin/com/myapp/core/network/AuthInterceptor.kt
package com.myapp.core.network

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

interface AuthInterceptor {
    suspend fun getToken(): String?
    suspend fun refreshToken(): String?
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AuthInterceptorImpl(
    private val tokenStorage: TokenStorage,
) : AuthInterceptor {
    override suspend fun getToken(): String? = tokenStorage.getAccessToken()
    override suspend fun refreshToken(): String? = tokenStorage.refreshAccessToken()
}
```

---

## 4. App Module Wiring

### build.gradle.kts

```kotlin
// app/build.gradle.kts
plugins {
    id("my.android.app")
    alias(libs.plugins.metro)
    alias(libs.plugins.ksp)
}

metro {
    enabled = true
    debug = false
    transformProvidersToPrivate = true
}

ksp {
    arg("circuit.codegen.mode", "METRO")
}

android {
    namespace = "com.myapp"

    defaultConfig {
        applicationId = "com.myapp"
        versionCode = 1
        versionName = "1.0.0"
    }
}

dependencies {
    // Core modules
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:ui"))
    implementation(project(":core:network"))
    implementation(project(":core:data"))

    // Feature implementations (all :impl modules wired here for DI)
    implementation(project(":features:auth:impl"))
    implementation(project(":features:profile:impl"))
    implementation(project(":features:feed:impl"))
    implementation(project(":features:settings:impl"))

    // Circuit
    implementation(libs.circuit.foundation)
    implementation(libs.circuit.overlay)
    implementation(libs.circuit.retained)
    implementation(libs.circuitx.gestureNav)
    implementation(libs.circuitx.overlays)

    // Metro
    implementation(libs.metro.runtime)

    // Compose
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)

    // Circuit codegen
    ksp(libs.circuit.codegen)
}
```

### DependencyGraph

```kotlin
// app/src/main/kotlin/com/myapp/AppGraph.kt
package com.myapp

import android.app.Application
import com.slack.circuit.foundation.Circuit
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory

@DependencyGraph(AppScope::class)
interface AppGraph {
    val circuit: Circuit

    fun inject(activity: MainActivity)

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides application: Application): AppGraph
    }
}

// In Application.onCreate()
// val appGraph = createGraphFactory<AppGraph.Factory>().create(this)
```

### Circuit Module (collected by Metro from all :impl modules)

```kotlin
// app/src/main/kotlin/com/myapp/di/CircuitModule.kt
package com.myapp.di

import com.slack.circuit.foundation.Circuit
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.ui.Ui
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@ContributesTo(AppScope::class)
interface CircuitModule {
    // Declare empty multibindings -- Metro fills them from @ContributesIntoSet
    @Multibinds fun presenterFactories(): Set<Presenter.Factory>
    @Multibinds fun uiFactories(): Set<Ui.Factory>

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

### MainActivity

```kotlin
// app/src/main/kotlin/com/myapp/MainActivity.kt
package com.myapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.slack.circuit.backstack.rememberSaveableBackStack
import com.slack.circuit.foundation.CircuitCompositionLocals
import com.slack.circuit.foundation.NavigableCircuitContent
import com.slack.circuit.foundation.rememberCircuitNavigator
import com.slack.circuit.overlay.ContentWithOverlays
import com.slack.circuitx.gesturenavigation.GestureNavigationDecorationFactory
import com.myapp.feature.auth.LoginScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appGraph = (application as MyApplication).appGraph

        setContent {
            MyAppTheme {
                CircuitCompositionLocals(appGraph.circuit) {
                    ContentWithOverlays {
                        val backStack = rememberSaveableBackStack(LoginScreen)
                        val navigator = rememberCircuitNavigator(backStack)
                        NavigableCircuitContent(
                            navigator = navigator,
                            backStack = backStack,
                            decoratorFactory = GestureNavigationDecorationFactory(
                                onBackInvoked = navigator::pop,
                            ),
                        )
                    }
                }
            }
        }
    }
}
```

---

## 5. settings.gradle.kts -- Full Multi-Module Project

```kotlin
// settings.gradle.kts
pluginManagement {
    // CRITICAL: includeBuild must be in pluginManagement for convention plugins
    includeBuild("build-logic")

    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Prevent modules from declaring their own repositories
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "MyKmpApp"

// ──────────────────────────────
// App
// ──────────────────────────────
include(":app")

// ──────────────────────────────
// Core Modules
// ──────────────────────────────
include(":core:model")
include(":core:common")
include(":core:ui")
include(":core:network")
include(":core:data")
include(":core:testing")

// ──────────────────────────────
// Feature: Auth
// ──────────────────────────────
include(":features:auth:api")
include(":features:auth:impl")

// ──────────────────────────────
// Feature: Profile
// ──────────────────────────────
include(":features:profile:api")
include(":features:profile:impl")

// ──────────────────────────────
// Feature: Feed
// ──────────────────────────────
include(":features:feed:api")
include(":features:feed:impl")

// ──────────────────────────────
// Feature: Settings
// ──────────────────────────────
include(":features:settings:api")
include(":features:settings:impl")

// ──────────────────────────────
// Feature: Notifications
// ──────────────────────────────
include(":features:notifications:api")
include(":features:notifications:impl")

// ──────────────────────────────
// Feature: Search
// ──────────────────────────────
include(":features:search:api")
include(":features:search:impl")
```

---

## 6. Dependency Visualization -- ASCII Module Graph

```
                              +-----------+
                              |    app    |
                              +-----+-----+
                                    |
                     +--------------+--------------+
                     |              |              |
              +------v------+ +----v----+ +-------v-------+
              | auth:impl   | | feed:   | | profile:impl  |
              |             | | impl    | |               |
              +------+------+ +----+----+ +-------+-------+
                     |              |              |
          +----------+--+     +----+----+    +-----+-----+
          |             |     |         |    |           |
    +-----v----+  +-----v-+  |   +-----v-+  | +--------v---+
    | auth:api |  | core: |  |   | feed: |  | | profile:   |
    |          |  | net   |  |   | api   |  | | api        |
    +-----+----+  +-------+  |   +---+---+  | +------+-----+
          |                   |       |      |        |
          |     +--------+    |       |      |        |
          +---->| core:  |<---+-------+------+--------+
                | model  |
                +---+----+
                    |
                +---v-----+
                | core:   |
                | common  |
                +---------+

LEGEND:
  Solid arrows (|, +, v, >) = implementation() dependency
  All :impl modules depend on their own :api
  All :api modules depend on :core:model
  Only app depends on :impl modules
  :impl modules may depend on other features' :api (not shown for clarity)
```

---

## 7. Migration Example -- Extracting Monolithic Feature

### Before: Monolithic `:feature-auth` Module

```
features/auth/
  build.gradle.kts           # Has Compose, Ktor, SQLDelight, Metro, Circuit
  src/commonMain/kotlin/
    com/myapp/auth/
      AuthScreen.kt           # Screen definition
      LoginPresenter.kt       # Presenter
      LoginUi.kt              # Composable UI
      AuthRepository.kt       # Interface
      AuthRepositoryImpl.kt   # Implementation
      AuthApi.kt              # Ktor API calls
      AuthDao.kt              # SQLDelight queries
      User.kt                 # Domain model
      UserDto.kt              # Network DTO
```

### Step 1: Identify the API Surface

Look at what other modules import from `:feature-auth`:

```kotlin
// In :feature-profile (another module)
import com.myapp.auth.AuthScreen     // Screen for navigation
import com.myapp.auth.User           // Domain model
import com.myapp.auth.AuthRepository // Interface for user data
```

These are the types that belong in `:api`.

### Step 2: Create the `:api` Module

```
features/auth/
  api/
    build.gradle.kts
    src/commonMain/kotlin/
      com/myapp/feature/auth/
        AuthScreen.kt          # MOVED from monolith
        model/
          User.kt              # MOVED from monolith
        AuthRepository.kt     # Interface MOVED from monolith
```

```kotlin
// features/auth/api/build.gradle.kts
plugins {
    id("my.kmp.feature.api")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
        }
    }
}
```

### Step 3: Create the `:impl` Module

```
features/auth/
  impl/
    build.gradle.kts
    src/commonMain/kotlin/
      com/myapp/feature/auth/
        LoginPresenter.kt       # MOVED, updated imports
        LoginUi.kt              # MOVED, updated imports
        data/
          AuthRepositoryImpl.kt  # MOVED, added @ContributesBinding
          AuthApi.kt             # MOVED, made internal
          AuthDao.kt             # MOVED, made internal
          UserDto.kt             # MOVED, made internal
```

```kotlin
// features/auth/impl/build.gradle.kts
plugins {
    id("my.kmp.feature.impl")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":features:auth:api"))
            implementation(project(":core:network"))
            implementation(project(":core:data"))
            implementation(libs.ktor.client.core)
            implementation(libs.sqldelight.coroutines)
        }
    }
}
```

### Step 4: Add Metro Bindings

The repository implementation needs `@ContributesBinding` so Metro wires it:

```kotlin
// BEFORE (in monolith, DI was probably manual or in a single graph)
class AuthRepositoryImpl(
    private val api: AuthApi,
    private val dao: AuthDao,
) : AuthRepository

// AFTER (in :impl, with Metro annotations)
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AuthRepositoryImpl(
    private val api: AuthApi,
    private val dao: AuthDao,
) : AuthRepository
```

### Step 5: Make Internal Types `internal`

Everything that is not in `:api` should be `internal` in `:impl`:

```kotlin
// DTOs are internal -- no other module should see them
@Serializable
internal data class UserDto(
    val id: String,
    val email: String,
    val name: String,
)

// API client is internal
internal class AuthApi @Inject constructor(
    private val httpClient: HttpClient,
) {
    internal suspend fun login(request: LoginRequest): AuthResponse =
        httpClient.post("auth/login") {
            setBody(request)
        }.body()
}
```

### Step 6: Update settings.gradle.kts

```kotlin
// BEFORE
include(":features:auth")

// AFTER
// include(":features:auth")  // REMOVE old monolith
include(":features:auth:api")
include(":features:auth:impl")
```

### Step 7: Update Dependent Modules

```kotlin
// BEFORE (in :feature-profile:impl/build.gradle.kts)
implementation(project(":features:auth"))

// AFTER
implementation(project(":features:auth:api"))
```

### Step 8: Update app Module

```kotlin
// BEFORE (in app/build.gradle.kts)
implementation(project(":features:auth"))

// AFTER
implementation(project(":features:auth:impl"))
```

### Step 9: Delete the Old Module

Once everything compiles and tests pass, delete `features/auth/build.gradle.kts` (the old monolith) and its source directory.

### Step 10: Verify

```bash
# Clean build to catch any stale references
./gradlew clean build

# Specifically test the new modules
./gradlew :features:auth:api:build :features:auth:impl:build

# Run tests
./gradlew :features:auth:impl:allTests
```

---

## 8. Core Model Module

```kotlin
// core/model/build.gradle.kts
plugins {
    id("my.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Core model has minimal dependencies -- pure Kotlin types
            api(libs.kotlinx.immutable)
        }
    }
}
```

```kotlin
// core/model/src/commonMain/kotlin/com/myapp/core/model/UserId.kt
package com.myapp.core.model

import kotlin.jvm.JvmInline

@JvmInline
value class UserId(val value: String)

@JvmInline
value class ProductId(val value: String)

@JvmInline
value class Timestamp(val epochMillis: Long)
```

```kotlin
// core/model/src/commonMain/kotlin/com/myapp/core/model/AppError.kt
package com.myapp.core.model

sealed interface AppError {
    sealed interface Network : AppError {
        data object NoConnection : Network
        data object Timeout : Network
        data class Server(val code: Int, val message: String) : Network
    }
    sealed interface Data : AppError {
        data object NotFound : Data
        data class Corruption(val cause: Throwable) : Data
    }
    sealed interface Auth : AppError {
        data object Unauthorized : Auth
        data object SessionExpired : Auth
    }
}
```

---

## 9. Scope Markers

Scope markers are defined once and shared across all modules via `:core:common` or a dedicated `:core:di` module.

```kotlin
// core/common/src/commonMain/kotlin/com/myapp/core/di/Scopes.kt
package com.myapp.core.di

// Metro scope markers: abstract classes with private constructors
abstract class AppScope private constructor()
abstract class UserScope private constructor()
abstract class ActivityScope private constructor()
```

All modules that use `@SingleIn(AppScope::class)` or `@ContributesTo(AppScope::class)` depend on `:core:common` (or wherever the scope markers live).

---

## 10. build-logic/convention/build.gradle.kts -- Complete Reference

```kotlin
// build-logic/convention/build.gradle.kts
plugins {
    `kotlin-dsl`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Gradle plugin dependencies -- use compileOnly so they are not
    // transitively added to consuming modules
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.metro.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "my.kmp.library"
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("androidLibrary") {
            id = "my.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("kmpFeatureApi") {
            id = "my.kmp.feature.api"
            implementationClass = "KmpFeatureApiConventionPlugin"
        }
        register("kmpFeatureImpl") {
            id = "my.kmp.feature.impl"
            implementationClass = "KmpFeatureImplConventionPlugin"
        }
        register("kmpCompose") {
            id = "my.kmp.compose"
            implementationClass = "KmpComposeConventionPlugin"
        }
        register("androidApp") {
            id = "my.android.app"
            implementationClass = "AndroidAppConventionPlugin"
        }
    }
}
```
