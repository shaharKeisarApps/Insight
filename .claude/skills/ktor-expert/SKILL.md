---
name: ktor-expert
description: Elite Ktor Client expertise for KMP networking. Use when setting up HTTP clients, handling authentication, configuring serialization, implementing interceptors, managing retries, or handling network errors. Triggers on API client setup, request configuration, authentication flows, or network layer questions.
---

# Ktor Client Expert Skill

## Core Concepts

Ktor Client is a multiplatform HTTP client with:
- Plugin-based architecture (interceptors, serialization, auth)
- Coroutine-native async operations
- Platform-specific engines

## Installation

```kotlin
// build.gradle.kts (Ktor 3.x)
commonMain.dependencies {
    implementation("io.ktor:ktor-client-core:3.3.0")
    implementation("io.ktor:ktor-client-content-negotiation:3.3.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.3.0")
    implementation("io.ktor:ktor-client-logging:3.3.0")
    implementation("io.ktor:ktor-client-auth:3.3.0")
}

androidMain.dependencies {
    implementation("io.ktor:ktor-client-okhttp:3.3.0")
}

iosMain.dependencies {
    implementation("io.ktor:ktor-client-darwin:3.3.0")
}

jvmMain.dependencies {
    implementation("io.ktor:ktor-client-cio:3.3.0")
}
```

## Client Setup

### Basic Configuration

```kotlin
// commonMain - expect/actual for engine
expect fun httpEngine(): HttpClientEngine

// androidMain
actual fun httpEngine(): HttpClientEngine = OkHttp.create {
    config {
        connectTimeout(30, TimeUnit.SECONDS)
        readTimeout(30, TimeUnit.SECONDS)
    }
}

// iosMain
actual fun httpEngine(): HttpClientEngine = Darwin.create {
    configureSession {
        timeoutIntervalForRequest = 30.0
        timeoutIntervalForResource = 60.0
    }
}

// jvmMain
actual fun httpEngine(): HttpClientEngine = CIO.create {
    requestTimeout = 30_000
}
```

### Full Client Configuration

```kotlin
@ContributesTo(AppScope::class)
interface NetworkModule {
    
    @Provides
    @SingleIn(AppScope::class)
    fun provideHttpClient(
        json: Json,
        tokenProvider: TokenProvider,
        logger: Logger,
    ): HttpClient = HttpClient(httpEngine()) {
        
        // JSON Serialization
        install(ContentNegotiation) {
            json(json)
        }
        
        // Logging
        install(Logging) {
            this.logger = object : io.ktor.client.plugins.logging.Logger {
                override fun log(message: String) {
                    logger.d("HTTP") { message }
                }
            }
            level = if (BuildConfig.DEBUG) LogLevel.HEADERS else LogLevel.NONE
            sanitizeHeader { header -> header == "Authorization" }
        }
        
        // Timeouts
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 30_000
        }
        
        // Authentication
        install(Auth) {
            bearer {
                loadTokens {
                    tokenProvider.getTokens()?.let {
                        BearerTokens(it.accessToken, it.refreshToken)
                    }
                }
                refreshTokens {
                    val newTokens = tokenProvider.refreshTokens()
                    newTokens?.let {
                        BearerTokens(it.accessToken, it.refreshToken)
                    }
                }
                sendWithoutRequest { request ->
                    request.url.host == "api.myapp.com"
                }
            }
        }
        
        // Default request configuration
        defaultRequest {
            url {
                protocol = URLProtocol.HTTPS
                host = "api.myapp.com"
                path("v1/")
            }
            contentType(ContentType.Application.Json)
            header("X-App-Version", BuildConfig.VERSION_NAME)
            header("X-Platform", getPlatform())
        }
        
        // Response validation
        expectSuccess = true
        
        // Retry on failure
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 3)
            retryOnException(maxRetries = 3, retryOnTimeout = true)
            exponentialDelay()
        }
    }
    
    @Provides
    @SingleIn(AppScope::class)
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }
}
```

## API Service Pattern

### Interface Definition

```kotlin
interface UserApi {
    suspend fun getUser(id: String): Either<NetworkError, UserResponse>
    suspend fun getUsers(page: Int, size: Int): Either<NetworkError, PaginatedResponse<UserResponse>>
    suspend fun createUser(request: CreateUserRequest): Either<NetworkError, UserResponse>
    suspend fun updateUser(id: String, request: UpdateUserRequest): Either<NetworkError, UserResponse>
    suspend fun deleteUser(id: String): Either<NetworkError, Unit>
    suspend fun uploadAvatar(id: String, image: ByteArray): Either<NetworkError, AvatarResponse>
}
```

### Implementation with Either

```kotlin
@ContributesBinding(AppScope::class)
@Inject
class UserApiImpl(
    private val client: HttpClient,
) : UserApi {
    
    override suspend fun getUser(id: String): Either<NetworkError, UserResponse> =
        client.safeRequest {
            get("users/$id")
        }
    
    override suspend fun getUsers(
        page: Int,
        size: Int,
    ): Either<NetworkError, PaginatedResponse<UserResponse>> =
        client.safeRequest {
            get("users") {
                parameter("page", page)
                parameter("size", size)
            }
        }
    
    override suspend fun createUser(
        request: CreateUserRequest,
    ): Either<NetworkError, UserResponse> =
        client.safeRequest {
            post("users") {
                setBody(request)
            }
        }
    
    override suspend fun updateUser(
        id: String,
        request: UpdateUserRequest,
    ): Either<NetworkError, UserResponse> =
        client.safeRequest {
            put("users/$id") {
                setBody(request)
            }
        }
    
    override suspend fun deleteUser(id: String): Either<NetworkError, Unit> =
        client.safeRequest {
            delete("users/$id")
        }
    
    override suspend fun uploadAvatar(
        id: String,
        image: ByteArray,
    ): Either<NetworkError, AvatarResponse> =
        client.safeRequest {
            post("users/$id/avatar") {
                setBody(MultiPartFormDataContent(
                    formData {
                        append("file", image, Headers.build {
                            append(HttpHeaders.ContentType, "image/jpeg")
                            append(HttpHeaders.ContentDisposition, "filename=avatar.jpg")
                        })
                    }
                ))
            }
        }
}
```

## Error Handling

### Network Error Types

```kotlin
sealed class NetworkError {
    abstract val message: String
    
    data class Http(
        val code: Int,
        override val message: String,
        val body: String? = null,
    ) : NetworkError()
    
    data class Connection(
        val cause: Throwable,
    ) : NetworkError() {
        override val message = "Connection failed: ${cause.message}"
    }
    
    data class Serialization(
        val cause: Throwable,
    ) : NetworkError() {
        override val message = "Failed to parse response: ${cause.message}"
    }
    
    data object Timeout : NetworkError() {
        override val message = "Request timed out"
    }
    
    data class Unknown(
        val cause: Throwable,
    ) : NetworkError() {
        override val message = "Unknown error: ${cause.message}"
    }
}
```

### Safe Request Extension

```kotlin
suspend inline fun <reified T> HttpClient.safeRequest(
    crossinline block: HttpRequestBuilder.() -> Unit,
): Either<NetworkError, T> = Either.catch {
    request { block() }.body<T>()
}.mapLeft { throwable ->
    throwable.toNetworkError()
}

fun Throwable.toNetworkError(): NetworkError = when (this) {
    is ClientRequestException -> NetworkError.Http(
        code = response.status.value,
        message = message ?: "Client error",
    )
    is ServerResponseException -> NetworkError.Http(
        code = response.status.value,
        message = message ?: "Server error",
    )
    is RedirectResponseException -> NetworkError.Http(
        code = response.status.value,
        message = "Unexpected redirect",
    )
    is HttpRequestTimeoutException -> NetworkError.Timeout
    is ConnectTimeoutException -> NetworkError.Timeout
    is SocketTimeoutException -> NetworkError.Timeout
    is SerializationException -> NetworkError.Serialization(this)
    is JsonDecodingException -> NetworkError.Serialization(this)
    is UnresolvedAddressException -> NetworkError.Connection(this)
    is IOException -> NetworkError.Connection(this)
    else -> NetworkError.Unknown(this)
}
```

### Safe Request with Error Body Parsing

```kotlin
suspend inline fun <reified T, reified E> HttpClient.safeRequestWithError(
    crossinline block: HttpRequestBuilder.() -> Unit,
): Either<NetworkError, T> = Either.catch {
    val response = request { block() }
    response.body<T>()
}.mapLeft { throwable ->
    when (throwable) {
        is ClientRequestException -> {
            val errorBody = try {
                throwable.response.body<E>()
            } catch (e: Exception) {
                null
            }
            NetworkError.Http(
                code = throwable.response.status.value,
                message = (errorBody as? ApiError)?.message ?: throwable.message ?: "Error",
                body = errorBody?.toString(),
            )
        }
        else -> throwable.toNetworkError()
    }
}

// API error response model
@Serializable
data class ApiError(
    val code: String,
    val message: String,
    val details: Map<String, String>? = null,
)
```

## Request/Response Models

### Basic Models

```kotlin
@Serializable
data class UserResponse(
    val id: String,
    val email: String,
    val name: String,
    @SerialName("avatar_url")
    val avatarUrl: String?,
    @SerialName("created_at")
    val createdAt: Instant,
    @SerialName("updated_at")
    val updatedAt: Instant,
)

@Serializable
data class CreateUserRequest(
    val email: String,
    val name: String,
    val password: String,
)

@Serializable
data class UpdateUserRequest(
    val name: String? = null,
    val email: String? = null,
)

@Serializable
data class PaginatedResponse<T>(
    val data: List<T>,
    val page: Int,
    @SerialName("page_size")
    val pageSize: Int,
    @SerialName("total_count")
    val totalCount: Int,
    @SerialName("has_more")
    val hasMore: Boolean,
)
```

### Domain Model Mapping

```kotlin
fun UserResponse.toDomain(): User = User(
    id = id,
    email = email,
    name = name,
    avatarUrl = avatarUrl,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun User.toUpdateRequest(): UpdateUserRequest = UpdateUserRequest(
    name = name,
    email = email,
)
```

## Authentication

### Token Management

```kotlin
interface TokenProvider {
    suspend fun getTokens(): AuthTokens?
    suspend fun refreshTokens(): AuthTokens?
    suspend fun clearTokens()
}

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Instant,
)

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class TokenProviderImpl(
    private val secureStorage: SecureStorage,
    private val authApi: AuthApi,
) : TokenProvider {
    
    private val mutex = Mutex()
    private var cachedTokens: AuthTokens? = null
    
    override suspend fun getTokens(): AuthTokens? {
        return cachedTokens ?: secureStorage.getTokens().also {
            cachedTokens = it
        }
    }
    
    override suspend fun refreshTokens(): AuthTokens? = mutex.withLock {
        val currentTokens = getTokens() ?: return@withLock null
        
        // Check if already refreshed by another coroutine
        if (currentTokens.expiresAt > Clock.System.now() + 1.minutes) {
            return@withLock currentTokens
        }
        
        // Refresh tokens
        authApi.refreshToken(RefreshRequest(currentTokens.refreshToken))
            .map { response ->
                AuthTokens(
                    accessToken = response.accessToken,
                    refreshToken = response.refreshToken,
                    expiresAt = Clock.System.now() + response.expiresIn.seconds,
                )
            }
            .onRight { newTokens ->
                cachedTokens = newTokens
                secureStorage.saveTokens(newTokens)
            }
            .onLeft {
                clearTokens()
            }
            .getOrNull()
    }
    
    override suspend fun clearTokens() {
        cachedTokens = null
        secureStorage.clearTokens()
    }
}
```

### Custom Auth Header

```kotlin
// For non-standard auth schemes
HttpClient(httpEngine()) {
    install(Auth) {
        customAuth("ApiKey") {
            sendWithoutRequest { true }
            authentication { request ->
                request.header("X-API-Key", apiKeyProvider.getKey())
            }
        }
    }
}

// Or using default request
HttpClient(httpEngine()) {
    defaultRequest {
        header("X-API-Key", apiKeyProvider.getKey())
    }
}
```

## Custom Plugins

### Request/Response Logging

```kotlin
val RequestTimingPlugin = createClientPlugin("RequestTiming") {
    val logger = pluginConfig.logger
    
    onRequest { request, _ ->
        request.attributes.put(AttributeKey("startTime"), System.currentTimeMillis())
        logger.d("HTTP") { "→ ${request.method.value} ${request.url}" }
    }
    
    onResponse { response ->
        val startTime = response.call.request.attributes[AttributeKey("startTime")] as Long
        val duration = System.currentTimeMillis() - startTime
        logger.d("HTTP") { 
            "← ${response.status.value} ${response.call.request.url} (${duration}ms)" 
        }
    }
}

// Usage
HttpClient(httpEngine()) {
    install(RequestTimingPlugin) {
        logger = myLogger
    }
}
```

### Error Interceptor

```kotlin
val ErrorInterceptorPlugin = createClientPlugin("ErrorInterceptor") {
    onResponse { response ->
        if (response.status == HttpStatusCode.Unauthorized) {
            // Trigger logout or token refresh
            onUnauthorized()
        }
    }
}
```

## WebSocket Support

```kotlin
suspend fun connectWebSocket(
    userId: String,
    onMessage: (Message) -> Unit,
): Job = coroutineScope {
    launch {
        client.webSocket("ws://api.myapp.com/ws?userId=$userId") {
            // Send initial message
            send(Frame.Text(Json.encodeToString(ConnectMessage(userId))))
            
            // Receive messages
            for (frame in incoming) {
                when (frame) {
                    is Frame.Text -> {
                        val message = Json.decodeFromString<Message>(frame.readText())
                        onMessage(message)
                    }
                    is Frame.Close -> {
                        // Handle close
                        break
                    }
                    else -> {}
                }
            }
        }
    }
}
```

## File Upload/Download

### Multipart Upload

```kotlin
suspend fun uploadFile(
    file: ByteArray,
    fileName: String,
    mimeType: String,
): Either<NetworkError, UploadResponse> = client.safeRequest {
    post("upload") {
        setBody(MultiPartFormDataContent(
            formData {
                append("file", file, Headers.build {
                    append(HttpHeaders.ContentType, mimeType)
                    append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                })
                append("description", "User uploaded file")
            }
        ))
    }
}
```

### File Download with Progress

```kotlin
suspend fun downloadFile(
    url: String,
    onProgress: (Float) -> Unit,
): Either<NetworkError, ByteArray> = Either.catch {
    client.prepareGet(url).execute { response ->
        val contentLength = response.contentLength() ?: 0L
        val channel = response.bodyAsChannel()
        val buffer = ByteArrayOutputStream()
        var downloaded = 0L
        
        while (!channel.isClosedForRead) {
            val packet = channel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
            while (!packet.isEmpty) {
                val bytes = packet.readBytes()
                buffer.write(bytes)
                downloaded += bytes.size
                if (contentLength > 0) {
                    onProgress(downloaded.toFloat() / contentLength)
                }
            }
        }
        
        buffer.toByteArray()
    }
}.mapLeft { it.toNetworkError() }
```

## Testing

### Mock Engine

```kotlin
class UserApiTest {
    
    private fun createMockClient(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): HttpClient = HttpClient(MockEngine { request -> handler(request) }) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
    
    @Test
    fun `getUser returns user on success`() = runTest {
        val mockResponse = UserResponse(
            id = "123",
            email = "test@example.com",
            name = "Test User",
            avatarUrl = null,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
        )
        
        val client = createMockClient { request ->
            respond(
                content = Json.encodeToString(mockResponse),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        
        val api = UserApiImpl(client)
        val result = api.getUser("123")
        
        assertThat(result.isRight()).isTrue()
        assertThat(result.getOrNull()?.id).isEqualTo("123")
    }
    
    @Test
    fun `getUser returns error on 404`() = runTest {
        val client = createMockClient { request ->
            respond(
                content = """{"code": "NOT_FOUND", "message": "User not found"}""",
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        
        val api = UserApiImpl(client)
        val result = api.getUser("unknown")
        
        assertThat(result.isLeft()).isTrue()
        val error = result.leftOrNull() as NetworkError.Http
        assertThat(error.code).isEqualTo(404)
    }
}
```

## Anti-Patterns

❌ **Don't create client per request**
```kotlin
// WRONG - creates new client each time
suspend fun getUser(id: String) = HttpClient().use { 
    it.get("users/$id").body<User>() 
}

// RIGHT - reuse single client instance
class UserApi(private val client: HttpClient) {
    suspend fun getUser(id: String) = client.get("users/$id").body<User>()
}
```

❌ **Don't ignore response status**
```kotlin
// WRONG - might silently succeed on error
val response = client.get("users/$id")
val user = response.body<User>()

// RIGHT - check status or use expectSuccess = true
val response = client.get("users/$id")
if (response.status.isSuccess()) {
    val user = response.body<User>()
}
```

❌ **Don't hardcode base URL**
```kotlin
// WRONG
client.get("https://api.myapp.com/v1/users/$id")

// RIGHT - use defaultRequest
HttpClient {
    defaultRequest { url("https://api.myapp.com/v1/") }
}
client.get("users/$id")
```

## References

- Ktor Client: https://ktor.io/docs/client-create-and-configure.html
- Ktor Plugins: https://ktor.io/docs/client-plugins.html
- Ktor Auth: https://ktor.io/docs/client-bearer-auth.html
- Ktor Testing: https://ktor.io/docs/client-testing.html
