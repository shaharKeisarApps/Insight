---
name: arrow-expert
description: Elite Arrow Kt expertise for functional error handling in Kotlin. Use when implementing Either patterns, typed domain errors, Raise DSL, parallel operations, or functional composition. Triggers on error handling design, domain error modeling, result types, or functional programming patterns.
---

# Arrow Expert Skill

## Core Concepts

Arrow provides type-safe error handling with `Either<Error, Success>`:
- `Left` = Error case
- `Right` = Success case (mnemonic: "right" is correct)

```kotlin
// Dependencies (Arrow 2.x)
implementation("io.arrow-kt:arrow-core:2.0.1")
implementation("io.arrow-kt:arrow-fx-coroutines:2.0.1")
```

## Domain Error Modeling

### Sealed Error Hierarchy

```kotlin
// Base sealed interface for all domain errors
sealed interface DomainError {
    val message: String
    
    // Network-related errors
    sealed interface Network : DomainError {
        data class HttpError(
            val code: Int,
            override val message: String,
        ) : Network
        
        data object Timeout : Network {
            override val message = "Request timed out"
        }
        
        data object NoConnection : Network {
            override val message = "No internet connection"
        }
    }
    
    // Validation errors
    sealed interface Validation : DomainError {
        data class InvalidEmail(val email: String) : Validation {
            override val message = "Invalid email format: $email"
        }
        
        data class InvalidPassword(val reason: String) : Validation {
            override val message = "Invalid password: $reason"
        }
        
        data object EmptyField : Validation {
            override val message = "Field cannot be empty"
        }
    }
    
    // Business logic errors
    sealed interface Business : DomainError {
        data object UserNotFound : Business {
            override val message = "User not found"
        }
        
        data object Unauthorized : Business {
            override val message = "Not authorized"
        }
        
        data class InsufficientFunds(
            val required: Double,
            val available: Double,
        ) : Business {
            override val message = "Insufficient funds: need $required, have $available"
        }
        
        data object ItemOutOfStock : Business {
            override val message = "Item is out of stock"
        }
    }
    
    // Storage errors
    sealed interface Storage : DomainError {
        data class ReadError(val cause: Throwable) : Storage {
            override val message = "Failed to read: ${cause.message}"
        }
        
        data class WriteError(val cause: Throwable) : Storage {
            override val message = "Failed to write: ${cause.message}"
        }
    }
}
```

### Error Conversion Helpers

```kotlin
// Convert network errors to domain errors
fun NetworkError.toDomain(): DomainError.Network = when (this) {
    is NetworkError.Http -> DomainError.Network.HttpError(code, message)
    is NetworkError.Timeout -> DomainError.Network.Timeout
    is NetworkError.Connection -> DomainError.Network.NoConnection
    is NetworkError.Unknown -> DomainError.Network.HttpError(500, message)
}

// Convert throwables to domain errors
fun Throwable.toDomainError(): DomainError = when (this) {
    is IOException -> DomainError.Network.NoConnection
    is SocketTimeoutException -> DomainError.Network.Timeout
    is HttpException -> DomainError.Network.HttpError(code(), message())
    else -> DomainError.Storage.ReadError(this)
}
```

## Either Basics

### Creating Either Values

```kotlin
// Create success
val success: Either<DomainError, User> = User("123", "John").right()

// Create error
val error: Either<DomainError, User> = DomainError.Business.UserNotFound.left()

// From nullable (null becomes Left)
val maybeUser: User? = repository.findUser(id)
val result: Either<DomainError, User> = maybeUser?.right() 
    ?: DomainError.Business.UserNotFound.left()

// From exception
val safeResult: Either<Throwable, User> = Either.catch {
    api.getUser(id)  // May throw
}

// Catch with custom error mapping
val domainResult: Either<DomainError, User> = Either.catch {
    api.getUser(id)
}.mapLeft { it.toDomainError() }
```

### Transforming Either

```kotlin
// Map success value
val userName: Either<DomainError, String> = getUser(id).map { it.name }

// Map error type
val genericError: Either<String, User> = getUser(id).mapLeft { it.message }

// Bimap (both sides)
val result: Either<String, String> = getUser(id).bimap(
    leftOperation = { error -> error.message },
    rightOperation = { user -> user.name },
)
```

### FlatMap (Chaining Operations)

```kotlin
// Sequential operations
suspend fun getUserWithPosts(userId: String): Either<DomainError, UserWithPosts> {
    return getUser(userId).flatMap { user ->
        getPosts(user.id).map { posts ->
            UserWithPosts(user, posts)
        }
    }
}

// Better: use either block (see below)
```

## Either Block (Raise DSL)

The `either { }` block provides imperative-style error handling with `bind()`:

### Basic Usage

```kotlin
suspend fun processOrder(orderId: String): Either<DomainError, OrderResult> = either {
    // Each bind() short-circuits on Left (error)
    val order = getOrder(orderId).bind()
    val user = getUser(order.userId).bind()
    val payment = processPayment(order, user).bind()
    
    // All succeeded - return success
    OrderResult(order, user, payment)
}
```

### With Validation

```kotlin
suspend fun createUser(
    email: String,
    password: String,
    name: String,
): Either<DomainError, User> = either {
    // Validate inputs
    val validEmail = validateEmail(email).bind()
    val validPassword = validatePassword(password).bind()
    val validName = validateName(name).bind()
    
    // Create user
    repository.createUser(validEmail, validPassword, validName).bind()
}

private fun validateEmail(email: String): Either<DomainError.Validation, Email> =
    if (email.contains("@") && email.contains(".")) {
        Email(email).right()
    } else {
        DomainError.Validation.InvalidEmail(email).left()
    }
```

### ensure / ensureNotNull

```kotlin
suspend fun withdrawFunds(
    accountId: String,
    amount: Double,
): Either<DomainError, Transaction> = either {
    val account = getAccount(accountId).bind()

    // ensure = condition must be true, else return error
    ensure(amount > 0) {
        DomainError.Validation.InvalidAmount("Amount must be positive")
    }

    ensure(account.balance >= amount) {
        DomainError.Business.InsufficientFunds(
            required = amount,
            available = account.balance,
        )
    }

    // ensureNotNull = value must not be null
    val paymentMethod = ensureNotNull(account.defaultPaymentMethod) {
        DomainError.Business.NoPaymentMethod
    }

    processWithdrawal(account, amount, paymentMethod).bind()
}
```

### withError (Error Type Transformation - Arrow 2.x)

```kotlin
// Transform errors from one type to another within Raise context
suspend fun processUserOrder(userId: String): Either<DomainError, OrderResult> = either {
    // Get user - UserError transformed to DomainError
    val user = withError({ userError: UserError -> userError.toDomainError() }) {
        userRepository.getUser(userId).bind()
    }

    // Get order - OrderError transformed to DomainError
    val order = withError({ orderError: OrderError -> orderError.toDomainError() }) {
        orderRepository.getLatestOrder(user.id).bind()
    }

    OrderResult(user, order)
}

// Simpler: when errors share a common interface
suspend fun fetchData(): Either<DomainError, Data> = either {
    withError(NetworkError::toDomain) {
        api.fetchData().bind()
    }
}
```

### recover (Error Recovery - Arrow 2.x)

```kotlin
// Recover from specific error types
suspend fun getUserOrDefault(userId: String): Either<OtherError, User> =
    fetchUser(userId)
        .recover { _: UserNotFound ->
            // Return default user or raise different error
            User.anonymous()
        }

// Recover with new error type
suspend fun Raise<OtherError>.getUserWithRecovery(userId: String): User =
    recover({
        fetchUser(userId)
    }) { _: UserNotFound ->
        raise(OtherError.UserMissing)
    }
```

## Use Case Patterns

### Simple Query Use Case

```kotlin
class GetUserProfileUseCase @Inject constructor(
    private val userRepository: UserRepository,
    private val preferencesRepository: PreferencesRepository,
) {
    suspend operator fun invoke(userId: String): Either<DomainError, UserProfile> = either {
        val user = userRepository.getUser(userId).bind()
        val preferences = preferencesRepository.getPreferences(userId).bind()
        
        UserProfile(
            user = user,
            preferences = preferences,
            displayName = user.name.ifEmpty { user.email.substringBefore("@") },
        )
    }
}
```

### Orchestrating Use Case

```kotlin
class ProcessOrderUseCase @Inject constructor(
    private val orderRepository: OrderRepository,
    private val inventoryRepository: InventoryRepository,
    private val paymentService: PaymentService,
    private val notificationService: NotificationService,
) {
    suspend operator fun invoke(order: Order): Either<DomainError, OrderConfirmation> = either {
        // 1. Validate inventory
        val availability = inventoryRepository.checkAvailability(order.items).bind()
        ensure(availability.allAvailable) {
            DomainError.Business.ItemOutOfStock
        }
        
        // 2. Reserve inventory
        inventoryRepository.reserve(order.items).bind()
        
        // 3. Process payment (with rollback on failure)
        val payment = paymentService.charge(order.total)
            .onLeft { 
                // Rollback on payment failure
                inventoryRepository.release(order.items)
            }
            .bind()
        
        // 4. Create order
        val confirmedOrder = orderRepository.create(
            order.copy(paymentId = payment.id)
        ).bind()
        
        // 5. Send notification (fire-and-forget, don't fail order)
        notificationService.sendOrderConfirmation(confirmedOrder)
            .onLeft { error ->
                logger.warn("Failed to send notification: ${error.message}")
            }
        
        OrderConfirmation(
            orderId = confirmedOrder.id,
            paymentId = payment.id,
            estimatedDelivery = confirmedOrder.estimatedDelivery,
        )
    }
}
```

## Parallel Operations

### parZip (Multiple Independent Operations)

```kotlin
suspend fun loadDashboard(userId: String): Either<DomainError, Dashboard> = either {
    // All three run in parallel, fail-fast on first error
    parZip(
        { userRepository.getUser(userId).bind() },
        { statsRepository.getStats(userId).bind() },
        { notificationsRepository.getUnreadCount(userId).bind() },
    ) { user, stats, unreadCount ->
        Dashboard(user, stats, unreadCount)
    }
}
```

### parMap (Transform Collection in Parallel)

```kotlin
suspend fun enrichUsers(userIds: List<String>): Either<DomainError, List<EnrichedUser>> = either {
    userIds.parMap { userId ->
        val user = userRepository.getUser(userId).bind()
        val activity = activityRepository.getRecent(userId).bind()
        EnrichedUser(user, activity)
    }
}
```

### parMapOrAccumulate (Collect All Errors)

```kotlin
suspend fun validateUsers(
    requests: List<CreateUserRequest>,
): Either<NonEmptyList<DomainError.Validation>, List<ValidatedUser>> {
    return requests.parMapOrAccumulate { request ->
        validateEmail(request.email).bind()
        validatePassword(request.password).bind()
        ValidatedUser(request.email, request.password)
    }
}
```

## Error Accumulation

### zipOrAccumulate (Collect All Validation Errors)

```kotlin
data class RegistrationForm(
    val email: String,
    val password: String,
    val confirmPassword: String,
    val name: String,
)

fun validateRegistration(
    form: RegistrationForm,
): Either<NonEmptyList<DomainError.Validation>, ValidatedRegistration> = either {
    zipOrAccumulate(
        { validateEmail(form.email).bind() },
        { validatePassword(form.password).bind() },
        { validatePasswordMatch(form.password, form.confirmPassword).bind() },
        { validateName(form.name).bind() },
    ) { email, password, _, name ->
        ValidatedRegistration(email, password, name)
    }
}

// Returns Either<NonEmptyList<ValidationError>, ValidatedRegistration>
// Left contains ALL validation errors, not just first one
```

## Folding Either (Consuming Results)

### In Presenter (Circuit)

```kotlin
@CircuitInject(ProfileScreen::class, AppScope::class)
@Composable
fun ProfilePresenter(
    screen: ProfileScreen,
    navigator: Navigator,
    getUserProfile: GetUserProfileUseCase,
): ProfileScreen.State {
    var state by rememberRetained { 
        mutableStateOf<ProfileScreen.State>(ProfileScreen.State.Loading) 
    }
    
    LaunchedEffect(screen.userId) {
        getUserProfile(screen.userId).fold(
            ifLeft = { error ->
                state = ProfileScreen.State.Error(
                    message = error.toUserMessage(),
                    canRetry = error.isRetryable(),
                    onRetry = { /* retry logic */ },
                )
            },
            ifRight = { profile ->
                state = ProfileScreen.State.Success(
                    profile = profile,
                    eventSink = { event -> handleEvent(event) },
                )
            },
        )
    }
    
    return state
}
```

### Error to User Message

```kotlin
fun DomainError.toUserMessage(): String = when (this) {
    // Network errors
    is DomainError.Network.NoConnection -> 
        "No internet connection. Please check your network."
    is DomainError.Network.Timeout -> 
        "Request timed out. Please try again."
    is DomainError.Network.HttpError -> when (code) {
        401 -> "Please sign in again."
        403 -> "You don't have permission to do this."
        404 -> "Content not found."
        in 500..599 -> "Server error. Please try again later."
        else -> "Network error. Please try again."
    }
    
    // Validation errors
    is DomainError.Validation.InvalidEmail -> 
        "Please enter a valid email address."
    is DomainError.Validation.InvalidPassword -> 
        "Password $reason"
    DomainError.Validation.EmptyField -> 
        "This field is required."
    
    // Business errors
    DomainError.Business.UserNotFound -> 
        "User not found."
    DomainError.Business.Unauthorized -> 
        "You're not authorized. Please sign in."
    is DomainError.Business.InsufficientFunds -> 
        "Insufficient funds. You need $$required but only have $$available."
    DomainError.Business.ItemOutOfStock -> 
        "This item is currently out of stock."
    
    // Storage errors
    is DomainError.Storage.ReadError -> 
        "Failed to load data. Please try again."
    is DomainError.Storage.WriteError -> 
        "Failed to save data. Please try again."
}

fun DomainError.isRetryable(): Boolean = when (this) {
    is DomainError.Network -> true
    is DomainError.Storage -> true
    is DomainError.Validation -> false
    is DomainError.Business -> when (this) {
        is DomainError.Business.InsufficientFunds -> false
        DomainError.Business.ItemOutOfStock -> true  // Might become available
        DomainError.Business.Unauthorized -> false
        DomainError.Business.UserNotFound -> false
    }
}
```

### getOrElse / getOrNull

```kotlin
// Get value or default
val userName = getUser(id).getOrElse { "Unknown User" }

// Get value or null
val user: User? = getUser(id).getOrNull()

// Get value or throw
val user = getUser(id).getOrElse { error ->
    throw IllegalStateException("User not found: ${error.message}")
}
```

## Flow Integration

### Mapping Flow Results

```kotlin
fun observeUser(userId: String): Flow<Either<DomainError, User>> =
    userRepository.observeUser(userId)
        .map { result ->
            result.mapLeft { it.toDomain() }
        }

// In presenter
LaunchedEffect(userId) {
    observeUser(userId).collect { result ->
        result.fold(
            ifLeft = { state = ErrorState(it.toUserMessage()) },
            ifRight = { state = SuccessState(it) },
        )
    }
}
```

### filterRight / filterLeft

```kotlin
// Only emit success values
val successFlow: Flow<User> = resultFlow.mapNotNull { it.getOrNull() }

// Custom filter
val usersOnly: Flow<User> = resultFlow
    .filter { it.isRight() }
    .map { it.getOrNull()!! }
```

## Repository Pattern

```kotlin
interface UserRepository {
    fun observeUser(id: String): Flow<Either<DomainError, User>>
    suspend fun getUser(id: String): Either<DomainError, User>
    suspend fun updateUser(user: User): Either<DomainError, Unit>
    suspend fun deleteUser(id: String): Either<DomainError, Unit>
}

@ContributesBinding(AppScope::class)
@Inject
class UserRepositoryImpl(
    private val api: UserApi,
    private val db: UserDatabase,
) : UserRepository {
    
    override suspend fun getUser(id: String): Either<DomainError, User> =
        Either.catch { api.getUser(id) }
            .mapLeft { it.toDomainError() }
            .map { it.toDomain() }
    
    override suspend fun updateUser(user: User): Either<DomainError, Unit> = either {
        // Update local first (optimistic)
        Either.catch { db.userQueries.upsert(user.toEntity()) }
            .mapLeft { DomainError.Storage.WriteError(it) }
            .bind()
        
        // Sync to server
        Either.catch { api.updateUser(user.id, user.toRequest()) }
            .mapLeft { it.toDomainError() }
            .onLeft { 
                // Rollback local on server failure
                db.userQueries.deleteById(user.id)
            }
            .bind()
    }
}
```

## Testing

```kotlin
class GetUserProfileUseCaseTest {
    
    private val userRepository = mockk<UserRepository>()
    private val prefsRepository = mockk<PreferencesRepository>()
    private val useCase = GetUserProfileUseCase(userRepository, prefsRepository)
    
    @Test
    fun `returns profile when both succeed`() = runTest {
        coEvery { userRepository.getUser("123") } returns testUser.right()
        coEvery { prefsRepository.getPreferences("123") } returns testPrefs.right()
        
        val result = useCase("123")
        
        assertThat(result.isRight()).isTrue()
        assertThat(result.getOrNull()?.user).isEqualTo(testUser)
    }
    
    @Test
    fun `returns error when user not found`() = runTest {
        coEvery { userRepository.getUser("123") } returns 
            DomainError.Business.UserNotFound.left()
        
        val result = useCase("123")
        
        assertThat(result.isLeft()).isTrue()
        assertThat(result.leftOrNull()).isEqualTo(DomainError.Business.UserNotFound)
    }
    
    @Test
    fun `short-circuits on first error`() = runTest {
        coEvery { userRepository.getUser("123") } returns 
            DomainError.Network.NoConnection.left()
        
        val result = useCase("123")
        
        assertThat(result.isLeft()).isTrue()
        // Preferences should NOT be called
        coVerify(exactly = 0) { prefsRepository.getPreferences(any()) }
    }
}
```

## Anti-Patterns

❌ **Don't use exceptions for expected errors**
```kotlin
// WRONG
fun getUser(id: String): User {
    return api.getUser(id) ?: throw UserNotFoundException()
}

// RIGHT
fun getUser(id: String): Either<DomainError, User> {
    return api.getUser(id)?.right() ?: DomainError.Business.UserNotFound.left()
}
```

❌ **Don't swallow errors**
```kotlin
// WRONG - silently returns null
fun getUser(id: String): User? = getUser(id).getOrNull()

// RIGHT - handle the error
fun getUser(id: String) = getUser(id).fold(
    ifLeft = { logError(it); null },
    ifRight = { it },
)
```

❌ **Don't nest Either unnecessarily**
```kotlin
// WRONG
val result: Either<Error, Either<Error, User>> = ...

// RIGHT - use flatMap or either block
val result: Either<Error, User> = either {
    val intermediate = firstOp().bind()
    secondOp(intermediate).bind()
}
```

## References

- Arrow Documentation: https://arrow-kt.io/docs/
- Arrow Core API: https://arrow-kt.io/docs/core/
- Either Guide: https://arrow-kt.io/docs/core/either/
- Error Handling: https://arrow-kt.io/docs/patterns/error_handling/
