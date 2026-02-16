# Kermit 2.0.8 -- Production Examples

> All examples target `commonMain` unless noted. Stack: Metro DI, Circuit MVI, Ktor, kotlinx.serialization.

---

## 1. Basic Setup with Metro DI

Provide a configured `Logger` instance via Metro. Platform-specific writers are resolved through `platformLogWriter()`.

```kotlin
// commonMain/di/LoggingModule.kt
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.loggerConfigInit
import co.touchlab.kermit.platformLogWriter
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@ContributesTo(AppScope::class)
interface LoggingModule {

    companion object {

        @Provides
        @SingleIn(AppScope::class)
        fun provideBaseLogger(): Logger = Logger(
            config = loggerConfigInit(
                platformLogWriter(),
                minSeverity = if (BuildConfig.DEBUG) Severity.Verbose else Severity.Info
            ),
            tag = "App"
        )
    }
}
```

```kotlin
// commonMain/config/BuildConfig.kt
// expect/actual to expose build type
expect object BuildConfig {
    val DEBUG: Boolean
}

// androidMain/config/BuildConfig.android.kt
actual object BuildConfig {
    actual val DEBUG: Boolean = android.os.Build.TYPE == "userdebug"
        || com.myapp.BuildConfig.DEBUG
}

// iosMain/config/BuildConfig.ios.kt
actual object BuildConfig {
    actual val DEBUG: Boolean = Platform.isDebugBinary
}
```

Usage in any injected class:

```kotlin
@Inject
class AppInitializer(private val logger: Logger) {
    fun initialize() {
        logger.i { "Application initialized" }
    }
}
```

---

## 2. Tagged Logger per Class

Use `Logger.withTag()` to create class-scoped loggers. The tag makes filtering easy in Logcat or Console.app.

```kotlin
// commonMain/feature/home/HomePresenter.kt
import co.touchlab.kermit.Logger
import com.slack.circuit.runtime.presenter.Presenter
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.Assisted

class HomePresenter @AssistedInject constructor(
    @Assisted private val navigator: Navigator,
    private val userRepository: UserRepository,
    baseLogger: Logger,
) : Presenter<HomeScreen.State> {

    private val log = baseLogger.withTag("HomePresenter")

    @CircuitInject(HomeScreen::class, AppScope::class)
    @AssistedFactory
    fun interface Factory {
        fun create(navigator: Navigator): HomePresenter
    }

    @Composable
    override fun present(): HomeScreen.State {
        log.d { "present() called" }

        var users by remember { mutableStateOf<List<User>>(emptyList()) }
        var isLoading by remember { mutableStateOf(true) }

        LaunchedEffect(Unit) {
            log.i { "Loading users" }
            try {
                users = userRepository.getUsers()
                log.d { "Loaded ${users.size} users" }
            } catch (e: Exception) {
                log.e(e) { "Failed to load users" }
            } finally {
                isLoading = false
            }
        }

        return HomeScreen.State(
            users = users,
            isLoading = isLoading,
            eventSink = { event ->
                when (event) {
                    is HomeScreen.Event.UserClicked -> {
                        log.d { "User clicked: ${event.userId}" }
                        navigator.goTo(UserDetailScreen(event.userId))
                    }
                }
            }
        )
    }
}
```

Pattern: Inject the base `Logger` via constructor, then immediately create a tagged copy as a private property. Every class gets its own tag without extra DI bindings.

---

## 3. Custom CrashReporting LogWriter

Write a custom `LogWriter` that forwards errors to your crash reporting service. This example shows a generic approach that works with Crashlytics, Sentry, or any backend.

```kotlin
// commonMain/logging/CrashReportingLogWriter.kt
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity

class CrashReportingLogWriter(
    private val crashReporter: CrashReporter,
    private val minBreadcrumbSeverity: Severity = Severity.Warn,
    private val minExceptionSeverity: Severity = Severity.Error,
) : LogWriter() {

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        // Record breadcrumbs for Warn and above
        if (severity >= minBreadcrumbSeverity) {
            crashReporter.logBreadcrumb(
                message = "[$tag] $message",
                level = severity.toCrashLevel()
            )
        }

        // Record non-fatal exceptions for Error and above
        if (severity >= minExceptionSeverity && throwable != null) {
            crashReporter.recordException(throwable, "[$tag] $message")
        }
    }

    override fun isLoggable(tag: String, severity: Severity): Boolean {
        return severity >= minBreadcrumbSeverity
    }

    private fun Severity.toCrashLevel(): CrashLevel = when (this) {
        Severity.Verbose, Severity.Debug -> CrashLevel.DEBUG
        Severity.Info -> CrashLevel.INFO
        Severity.Warn -> CrashLevel.WARNING
        Severity.Error -> CrashLevel.ERROR
        Severity.Assert -> CrashLevel.CRITICAL
    }
}
```

```kotlin
// commonMain/logging/CrashReporter.kt
interface CrashReporter {
    fun logBreadcrumb(message: String, level: CrashLevel)
    fun recordException(throwable: Throwable, context: String)
    fun setUserId(userId: String)
}

enum class CrashLevel { DEBUG, INFO, WARNING, ERROR, CRITICAL }
```

```kotlin
// androidMain/logging/CrashlyticsReporter.android.kt
import com.google.firebase.crashlytics.FirebaseCrashlytics

class CrashlyticsReporter : CrashReporter {
    private val crashlytics = FirebaseCrashlytics.getInstance()

    override fun logBreadcrumb(message: String, level: CrashLevel) {
        crashlytics.log(message)
    }

    override fun recordException(throwable: Throwable, context: String) {
        crashlytics.log(context)
        crashlytics.recordException(throwable)
    }

    override fun setUserId(userId: String) {
        crashlytics.setUserId(userId)
    }
}
```

```kotlin
// commonMain/di/LoggingModule.kt -- updated with crash reporting
@ContributesTo(AppScope::class)
interface LoggingModule {

    companion object {

        @Provides
        @SingleIn(AppScope::class)
        fun provideLogger(crashReporter: CrashReporter): Logger {
            val writers = buildList {
                add(platformLogWriter())
                add(CrashReportingLogWriter(crashReporter))
            }
            return Logger(
                config = loggerConfigInit(
                    *writers.toTypedArray(),
                    minSeverity = if (BuildConfig.DEBUG) Severity.Verbose else Severity.Info
                ),
                tag = "App"
            )
        }
    }
}
```

---

## 4. Ktor Client Logging Integration

Bridge Kermit into Ktor's `Logging` plugin so all HTTP traffic is logged through the same system.

```kotlin
// commonMain/di/NetworkBindings.kt
import co.touchlab.kermit.Logger
import io.ktor.client.*
import io.ktor.client.plugins.logging.*
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@ContributesTo(AppScope::class)
interface NetworkBindings {

    companion object {

        @Provides
        @SingleIn(AppScope::class)
        fun provideHttpClient(
            json: Json,
            baseLogger: Logger,
            appConfig: AppConfig,
        ): HttpClient {
            val httpLog = baseLogger.withTag("HttpClient")

            return HttpClient(httpEngine()) {
                expectSuccess = true

                install(ContentNegotiation) { json(json) }

                install(HttpTimeout) {
                    requestTimeoutMillis = 30_000
                    connectTimeoutMillis = 10_000
                    socketTimeoutMillis = 30_000
                }

                install(Logging) {
                    logger = object : io.ktor.client.plugins.logging.Logger {
                        override fun log(message: String) {
                            httpLog.d { message }
                        }
                    }
                    level = if (appConfig.isDebug) LogLevel.HEADERS else LogLevel.NONE
                    sanitizeHeader { header ->
                        header == HttpHeaders.Authorization
                    }
                }
            }
        }
    }
}
```

Key points:
- The Ktor `Logger` interface (`io.ktor.client.plugins.logging.Logger`) is distinct from Kermit's `Logger` class. The object adapter bridges them.
- Use `LogLevel.HEADERS` in debug to inspect request/response headers without leaking bodies.
- `sanitizeHeader` replaces the value of sensitive headers with `***` in log output.
- Never use `LogLevel.BODY` or `LogLevel.ALL` in production -- body logging captures full payloads.

---

## 5. Production Configuration with expect/actual

Use `expect`/`actual` to configure different severity levels and writers per build type.

```kotlin
// commonMain/logging/LoggingConfig.kt
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity

data class LoggingConfig(
    val minSeverity: Severity,
    val writers: List<LogWriter>,
    val defaultTag: String = "App",
)

expect fun createLoggingConfig(): LoggingConfig
```

```kotlin
// androidMain/logging/LoggingConfig.android.kt
import co.touchlab.kermit.Severity
import co.touchlab.kermit.platformLogWriter
import com.myapp.BuildConfig

actual fun createLoggingConfig(): LoggingConfig {
    val writers = buildList {
        add(platformLogWriter())
        if (!BuildConfig.DEBUG) {
            // Add crash reporting writer only in release
            add(CrashlyticsLogWriter(
                minSeverity = Severity.Warn,
                minCrashSeverity = Severity.Error
            ))
        }
    }
    return LoggingConfig(
        minSeverity = if (BuildConfig.DEBUG) Severity.Verbose else Severity.Info,
        writers = writers,
    )
}
```

```kotlin
// iosMain/logging/LoggingConfig.ios.kt
import co.touchlab.kermit.Severity
import co.touchlab.kermit.platformLogWriter

actual fun createLoggingConfig(): LoggingConfig {
    val isDebug = Platform.isDebugBinary
    val writers = buildList {
        add(platformLogWriter())
        if (!isDebug) {
            add(CrashlyticsLogWriter(
                minSeverity = Severity.Warn,
                minCrashSeverity = Severity.Error
            ))
        }
    }
    return LoggingConfig(
        minSeverity = if (isDebug) Severity.Verbose else Severity.Info,
        writers = writers,
    )
}
```

```kotlin
// commonMain/di/LoggingModule.kt -- using expect/actual config
@ContributesTo(AppScope::class)
interface LoggingModule {

    companion object {

        @Provides
        @SingleIn(AppScope::class)
        fun provideLogger(): Logger {
            val config = createLoggingConfig()
            return Logger(
                config = loggerConfigInit(
                    *config.writers.toTypedArray(),
                    minSeverity = config.minSeverity
                ),
                tag = config.defaultTag
            )
        }
    }
}
```

---

## 6. Repository Logging with Structured Context

Log meaningful context in the data layer. Use tags to distinguish repositories and include operation identifiers for tracing.

```kotlin
// commonMain/data/user/UserRepository.kt
import co.touchlab.kermit.Logger
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

interface UserRepository {
    suspend fun getUser(id: String): User
    suspend fun getUsers(page: Int = 1, limit: Int = 20): List<User>
    suspend fun updateUser(id: String, name: String, email: String): User
    suspend fun deleteUser(id: String)
}

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class DefaultUserRepository(
    private val userApi: UserApi,
    baseLogger: Logger,
) : UserRepository {

    private val log = baseLogger.withTag("UserRepository")

    override suspend fun getUser(id: String): User {
        log.d { "getUser(id=$id)" }
        return try {
            val dto = userApi.getUser(id)
            log.d { "getUser(id=$id) -> found: ${dto.name}" }
            dto.toDomain()
        } catch (e: Exception) {
            log.e(e) { "getUser(id=$id) failed" }
            throw e
        }
    }

    override suspend fun getUsers(page: Int, limit: Int): List<User> {
        log.d { "getUsers(page=$page, limit=$limit)" }
        return try {
            val response = userApi.getUsers(page, limit)
            log.i { "getUsers -> ${response.data.size} users, page $page/${response.totalPages}" }
            response.data.map { it.toDomain() }
        } catch (e: Exception) {
            log.e(e) { "getUsers(page=$page, limit=$limit) failed" }
            throw e
        }
    }

    override suspend fun updateUser(id: String, name: String, email: String): User {
        log.i { "updateUser(id=$id)" }
        return try {
            val dto = userApi.updateUser(id, UpdateUserRequest(name, email))
            log.i { "updateUser(id=$id) -> success" }
            dto.toDomain()
        } catch (e: Exception) {
            log.e(e) { "updateUser(id=$id) failed" }
            throw e
        }
    }

    override suspend fun deleteUser(id: String) {
        log.w { "deleteUser(id=$id) -- destructive operation" }
        try {
            userApi.deleteUser(id)
            log.i { "deleteUser(id=$id) -> success" }
        } catch (e: Exception) {
            log.e(e) { "deleteUser(id=$id) failed" }
            throw e
        }
    }
}
```

Logging conventions for repositories:
- `d { }` for method entry and data-level details.
- `i { }` for successful completions and notable metrics (counts, pages).
- `w { }` for destructive or risky operations (delete, purge).
- `e(throwable) { }` for failures -- always include the exception.
- Never log sensitive fields (email content, tokens). Log IDs and counts only.

---

## 7. Testing with TestLogWriter

Capture log output in tests to assert that the right messages are logged at the right severity.

```kotlin
// commonTest/logging/TestLogWriter.kt
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity

class TestLogWriter : LogWriter() {

    data class LogEntry(
        val severity: Severity,
        val message: String,
        val tag: String,
        val throwable: Throwable?,
    )

    private val _logs = mutableListOf<LogEntry>()
    val logs: List<LogEntry> get() = _logs.toList()

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        _logs.add(LogEntry(severity, message, tag, throwable))
    }

    fun clear() {
        _logs.clear()
    }

    // Assertion helpers
    fun assertContains(severity: Severity, messageSubstring: String) {
        val found = _logs.any { it.severity == severity && messageSubstring in it.message }
        if (!found) {
            val logDump = _logs.joinToString("\n") { "  [${it.severity}] ${it.tag}: ${it.message}" }
            error(
                "Expected log with severity=$severity containing \"$messageSubstring\" " +
                    "but found:\n$logDump"
            )
        }
    }

    fun assertNoErrors() {
        val errors = _logs.filter { it.severity >= Severity.Error }
        if (errors.isNotEmpty()) {
            val errorDump = errors.joinToString("\n") { "  [${it.severity}] ${it.tag}: ${it.message}" }
            error("Expected no errors but found:\n$errorDump")
        }
    }

    fun assertHasThrowable(severity: Severity) {
        val found = _logs.any { it.severity == severity && it.throwable != null }
        if (!found) {
            error("Expected log with severity=$severity and a throwable, but none found")
        }
    }

    fun logsWithTag(tag: String): List<LogEntry> = _logs.filter { it.tag == tag }
}
```

```kotlin
// commonTest/data/user/UserRepositoryTest.kt
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.loggerConfigInit
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class UserRepositoryTest {

    private lateinit var testLogWriter: TestLogWriter
    private lateinit var logger: Logger
    private lateinit var fakeApi: FakeUserApi
    private lateinit var repository: DefaultUserRepository

    @BeforeTest
    fun setUp() {
        testLogWriter = TestLogWriter()
        logger = Logger(
            config = loggerConfigInit(testLogWriter, minSeverity = Severity.Verbose),
            tag = "Test"
        )
        fakeApi = FakeUserApi()
        repository = DefaultUserRepository(
            userApi = fakeApi,
            baseLogger = logger,
        )
    }

    @Test
    fun getUserLogsDebugOnSuccess() = runTest {
        fakeApi.userToReturn = UserDto("1", "Alice", "alice@test.com")

        val user = repository.getUser("1")

        assertEquals("Alice", user.name)
        testLogWriter.assertContains(Severity.Debug, "getUser(id=1)")
        testLogWriter.assertContains(Severity.Debug, "found: Alice")
        testLogWriter.assertNoErrors()
    }

    @Test
    fun getUserLogsErrorOnFailure() = runTest {
        fakeApi.shouldFail = true

        assertFailsWith<RuntimeException> { repository.getUser("1") }

        testLogWriter.assertContains(Severity.Error, "getUser(id=1) failed")
        testLogWriter.assertHasThrowable(Severity.Error)
    }

    @Test
    fun deleteUserLogsWarning() = runTest {
        repository.deleteUser("42")

        testLogWriter.assertContains(Severity.Warn, "deleteUser(id=42)")
        testLogWriter.assertContains(Severity.Info, "deleteUser(id=42) -> success")
    }

    @Test
    fun logTagIsUserRepository() = runTest {
        fakeApi.userToReturn = UserDto("1", "Bob", "bob@test.com")

        repository.getUser("1")

        val repoLogs = testLogWriter.logsWithTag("UserRepository")
        assert(repoLogs.isNotEmpty()) { "Expected logs tagged 'UserRepository'" }
    }
}
```

```kotlin
// commonTest/data/user/FakeUserApi.kt
class FakeUserApi : UserApi {
    var userToReturn: UserDto? = null
    var shouldFail: Boolean = false

    override suspend fun getUser(id: String): UserDto {
        if (shouldFail) throw RuntimeException("API failure")
        return userToReturn ?: throw RuntimeException("No user configured")
    }

    override suspend fun getUsers(page: Int, limit: Int): PaginatedResponse<UserDto> {
        if (shouldFail) throw RuntimeException("API failure")
        return PaginatedResponse(
            data = listOfNotNull(userToReturn),
            page = page,
            totalPages = 1
        )
    }

    override suspend fun updateUser(id: String, request: UpdateUserRequest): UserDto {
        if (shouldFail) throw RuntimeException("API failure")
        return UserDto(id, request.name, request.email)
    }

    override suspend fun deleteUser(id: String) {
        if (shouldFail) throw RuntimeException("API failure")
    }
}
```

Testing strategy:
- Create a `TestLogWriter` that collects all log entries in memory.
- Inject it via `loggerConfigInit(testLogWriter)` with `Severity.Verbose` to capture everything.
- Use assertion helpers to verify severity, message content, tag, and throwable presence.
- Clear the writer in `@BeforeTest` for test isolation.
- The `FakeUserApi` lets you control success/failure without network or mocks.
