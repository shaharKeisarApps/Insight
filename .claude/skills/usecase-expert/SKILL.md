---
name: usecase-expert
description: Elite use case/interactor pattern expertise for KMP domain layers. Use when designing business logic encapsulation, orchestrating repositories, implementing domain operations, handling validation, or separating concerns. Triggers on business logic organization, use case design, domain layer architecture, or interactor patterns.
---

# UseCase Expert Skill

## Core Philosophy

Use cases encapsulate **single business operations**:
- One use case = one business action
- Pure Kotlin (no framework dependencies)
- Testable in isolation
- Orchestrates repositories/services

## Base Patterns

### Operator Invoke Pattern

```kotlin
// Single operation use case
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

// Usage - reads like a function call
val profile = getUserProfileUseCase(userId)
```

### Flow-Based Use Case

```kotlin
// For observing data streams
class ObserveUserUseCase @Inject constructor(
    private val userRepository: UserRepository,
) {
    operator fun invoke(userId: String): Flow<Either<DomainError, User>> =
        userRepository.observeUser(userId)
}

// Combined streams
class ObserveDashboardUseCase @Inject constructor(
    private val userRepository: UserRepository,
    private val statsRepository: StatsRepository,
    private val notificationsRepository: NotificationsRepository,
) {
    operator fun invoke(userId: String): Flow<Either<DomainError, Dashboard>> =
        combine(
            userRepository.observeUser(userId),
            statsRepository.observeStats(userId),
            notificationsRepository.observeUnreadCount(userId),
        ) { userResult, statsResult, countResult ->
            either {
                Dashboard(
                    user = userResult.bind(),
                    stats = statsResult.bind(),
                    unreadNotifications = countResult.bind(),
                )
            }
        }
}
```

### Parameterized Use Case

```kotlin
// When you need multiple parameters
data class SearchParams(
    val query: String,
    val filters: SearchFilters,
    val page: Int = 0,
    val pageSize: Int = 20,
)

class SearchItemsUseCase @Inject constructor(
    private val searchRepository: SearchRepository,
    private val analyticsService: AnalyticsService,
) {
    suspend operator fun invoke(params: SearchParams): Either<DomainError, SearchResults> = either {
        // Track search analytics
        analyticsService.trackSearch(params.query, params.filters)
        
        // Perform search
        searchRepository.search(
            query = params.query,
            filters = params.filters,
            page = params.page,
            pageSize = params.pageSize,
        ).bind()
    }
}
```

## Use Case Categories

### Query Use Cases (Read Operations)

```kotlin
// Single entity
class GetUserByIdUseCase @Inject constructor(
    private val userRepository: UserRepository,
) {
    suspend operator fun invoke(userId: String): Either<DomainError, User> =
        userRepository.getUser(userId)
}

// List of entities
class GetActiveOrdersUseCase @Inject constructor(
    private val orderRepository: OrderRepository,
) {
    suspend operator fun invoke(userId: String): Either<DomainError, List<Order>> =
        orderRepository.getOrdersByStatus(userId, OrderStatus.ACTIVE)
}

// Aggregated data
class GetOrderSummaryUseCase @Inject constructor(
    private val orderRepository: OrderRepository,
    private val paymentRepository: PaymentRepository,
) {
    suspend operator fun invoke(orderId: String): Either<DomainError, OrderSummary> = either {
        val order = orderRepository.getOrder(orderId).bind()
        val payments = paymentRepository.getPaymentsForOrder(orderId).bind()
        
        OrderSummary(
            order = order,
            payments = payments,
            totalPaid = payments.sumOf { it.amount },
            remainingBalance = order.total - payments.sumOf { it.amount },
        )
    }
}
```

### Command Use Cases (Write Operations)

```kotlin
// Simple creation
class CreateUserUseCase @Inject constructor(
    private val userRepository: UserRepository,
    private val emailService: EmailService,
) {
    suspend operator fun invoke(request: CreateUserRequest): Either<DomainError, User> = either {
        // Validate
        val validEmail = validateEmail(request.email).bind()
        val validPassword = validatePassword(request.password).bind()
        
        // Create user
        val user = userRepository.createUser(
            email = validEmail,
            passwordHash = hashPassword(validPassword),
            name = request.name,
        ).bind()
        
        // Side effect: send welcome email (don't fail if this fails)
        emailService.sendWelcomeEmail(user.email).onLeft {
            logger.warn("Failed to send welcome email: ${it.message}")
        }
        
        user
    }
}

// Complex transaction
class ProcessOrderUseCase @Inject constructor(
    private val orderRepository: OrderRepository,
    private val inventoryRepository: InventoryRepository,
    private val paymentService: PaymentService,
    private val notificationService: NotificationService,
) {
    suspend operator fun invoke(order: Order): Either<DomainError, OrderConfirmation> = either {
        // 1. Check inventory
        val availability = inventoryRepository.checkAvailability(order.items).bind()
        ensure(availability.allAvailable) {
            DomainError.Business.InsufficientInventory(availability.unavailableItems)
        }
        
        // 2. Reserve inventory (with cleanup on failure)
        val reservation = inventoryRepository.reserve(order.items).bind()
        
        // 3. Process payment
        val payment = paymentService.charge(order.paymentMethod, order.total)
            .onLeft { 
                // Rollback inventory on payment failure
                inventoryRepository.release(reservation.id)
            }
            .bind()
        
        // 4. Create confirmed order
        val confirmedOrder = orderRepository.confirmOrder(
            order = order,
            paymentId = payment.id,
            reservationId = reservation.id,
        ).bind()
        
        // 5. Send notification (fire-and-forget)
        notificationService.sendOrderConfirmation(confirmedOrder)
        
        OrderConfirmation(
            orderId = confirmedOrder.id,
            paymentId = payment.id,
            estimatedDelivery = confirmedOrder.estimatedDelivery,
        )
    }
}
```

### Validation Use Cases

```kotlin
class ValidateRegistrationUseCase @Inject constructor(
    private val userRepository: UserRepository,
) {
    suspend operator fun invoke(
        email: String,
        password: String,
        confirmPassword: String,
    ): Either<NonEmptyList<ValidationError>, ValidatedRegistration> = either {
        zipOrAccumulate(
            { validateEmail(email).bind() },
            { validatePassword(password).bind() },
            { validatePasswordMatch(password, confirmPassword).bind() },
            { validateEmailNotTaken(email).bind() },
        ) { validEmail, validPassword, _, _ ->
            ValidatedRegistration(validEmail, validPassword)
        }
    }
    
    private fun validateEmail(email: String): Either<ValidationError, Email> =
        if (email.matches(EMAIL_REGEX)) Email(email).right()
        else ValidationError.InvalidEmail(email).left()
    
    private fun validatePassword(password: String): Either<ValidationError, Password> =
        when {
            password.length < 8 -> ValidationError.PasswordTooShort.left()
            !password.any { it.isDigit() } -> ValidationError.PasswordNoDigit.left()
            !password.any { it.isUpperCase() } -> ValidationError.PasswordNoUppercase.left()
            else -> Password(password).right()
        }
    
    private fun validatePasswordMatch(
        password: String,
        confirm: String,
    ): Either<ValidationError, Unit> =
        if (password == confirm) Unit.right()
        else ValidationError.PasswordMismatch.left()
    
    private suspend fun validateEmailNotTaken(
        email: String,
    ): Either<ValidationError, Unit> =
        userRepository.existsByEmail(email).fold(
            ifLeft = { Unit.right() }, // Error checking = assume not taken
            ifRight = { exists ->
                if (exists) ValidationError.EmailTaken(email).left()
                else Unit.right()
            }
        )
    
    companion object {
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@(.+)$")
    }
}
```

### Sync/Background Use Cases

```kotlin
class SyncUserDataUseCase @Inject constructor(
    private val userRepository: UserRepository,
    private val localCache: LocalCache,
    private val syncStatusTracker: SyncStatusTracker,
) {
    suspend operator fun invoke(userId: String): Either<DomainError, SyncResult> = either {
        syncStatusTracker.markSyncStarted(userId)
        
        try {
            // Fetch fresh data from server
            val freshUser = userRepository.refreshUser(userId).bind()
            
            // Update local cache
            localCache.updateUser(freshUser)
            
            // Get sync timestamp
            val syncedAt = Clock.System.now()
            syncStatusTracker.markSyncCompleted(userId, syncedAt)
            
            SyncResult.Success(syncedAt)
        } catch (e: Exception) {
            syncStatusTracker.markSyncFailed(userId, e.message ?: "Unknown error")
            raise(DomainError.Storage.WriteError(e))
        }
    }
}
```

## Orchestration Patterns

### Parallel Operations

```kotlin
class LoadHomeScreenUseCase @Inject constructor(
    private val userRepository: UserRepository,
    private val feedRepository: FeedRepository,
    private val recommendationsRepository: RecommendationsRepository,
    private val notificationsRepository: NotificationsRepository,
) {
    suspend operator fun invoke(userId: String): Either<DomainError, HomeScreenData> = either {
        // All operations run in parallel
        parZip(
            { userRepository.getUser(userId).bind() },
            { feedRepository.getFeed(userId, limit = 20).bind() },
            { recommendationsRepository.getRecommendations(userId).bind() },
            { notificationsRepository.getUnreadCount(userId).bind() },
        ) { user, feed, recommendations, unreadCount ->
            HomeScreenData(
                user = user,
                feed = feed,
                recommendations = recommendations,
                unreadNotifications = unreadCount,
            )
        }
    }
}
```

### Sequential with Dependencies

```kotlin
class CompleteOnboardingUseCase @Inject constructor(
    private val userRepository: UserRepository,
    private val profileRepository: ProfileRepository,
    private val preferencesRepository: PreferencesRepository,
    private val analyticsService: AnalyticsService,
) {
    suspend operator fun invoke(
        userId: String,
        profile: ProfileData,
        preferences: UserPreferences,
    ): Either<DomainError, OnboardingResult> = either {
        // Step 1: Update profile (must complete first)
        val updatedProfile = profileRepository.updateProfile(userId, profile).bind()
        
        // Step 2: Save preferences (depends on profile existing)
        preferencesRepository.savePreferences(userId, preferences).bind()
        
        // Step 3: Mark onboarding complete
        userRepository.completeOnboarding(userId).bind()
        
        // Step 4: Track completion
        analyticsService.trackOnboardingComplete(userId, profile.interests)
        
        OnboardingResult(
            userId = userId,
            profile = updatedProfile,
            preferences = preferences,
        )
    }
}
```

### Retry Logic

```kotlin
class SyncWithRetryUseCase @Inject constructor(
    private val syncUseCase: SyncUserDataUseCase,
) {
    suspend operator fun invoke(
        userId: String,
        maxRetries: Int = 3,
        initialDelayMs: Long = 1000,
    ): Either<DomainError, SyncResult> {
        var lastError: DomainError? = null
        var delay = initialDelayMs
        
        repeat(maxRetries) { attempt ->
            syncUseCase(userId).fold(
                ifLeft = { error ->
                    lastError = error
                    if (error.isRetryable() && attempt < maxRetries - 1) {
                        delay(delay)
                        delay *= 2 // Exponential backoff
                    }
                },
                ifRight = { result ->
                    return result.right()
                }
            )
        }
        
        return (lastError ?: DomainError.Unknown("Max retries exceeded")).left()
    }
}
```

## Use Case Naming Conventions

| Action | Pattern | Example |
|--------|---------|---------|
| Get single | `Get{Entity}UseCase` | `GetUserUseCase` |
| Get single by ID | `Get{Entity}ByIdUseCase` | `GetOrderByIdUseCase` |
| Get list | `Get{Entities}UseCase` | `GetOrdersUseCase` |
| Get filtered | `Get{Filter}{Entities}UseCase` | `GetActiveOrdersUseCase` |
| Observe single | `Observe{Entity}UseCase` | `ObserveUserUseCase` |
| Observe list | `Observe{Entities}UseCase` | `ObserveOrdersUseCase` |
| Create | `Create{Entity}UseCase` | `CreateOrderUseCase` |
| Update | `Update{Entity}UseCase` | `UpdateProfileUseCase` |
| Delete | `Delete{Entity}UseCase` | `DeleteAccountUseCase` |
| Action verb | `{Verb}{Noun}UseCase` | `ProcessPaymentUseCase` |
| Validate | `Validate{Entity}UseCase` | `ValidateEmailUseCase` |
| Sync | `Sync{Entity}UseCase` | `SyncUserDataUseCase` |
| Search | `Search{Entities}UseCase` | `SearchProductsUseCase` |

## Testing Use Cases

```kotlin
class GetUserProfileUseCaseTest {
    
    private val userRepository = FakeUserRepository()
    private val preferencesRepository = FakePreferencesRepository()
    private lateinit var useCase: GetUserProfileUseCase
    
    @BeforeTest
    fun setup() {
        useCase = GetUserProfileUseCase(userRepository, preferencesRepository)
    }
    
    @Test
    fun `returns profile when both repositories succeed`() = runTest {
        userRepository.setUser(testUser)
        preferencesRepository.setPreferences(testPreferences)
        
        val result = useCase("user-123")
        
        assertThat(result.isRight()).isTrue()
        val profile = result.getOrNull()!!
        assertThat(profile.user).isEqualTo(testUser)
        assertThat(profile.preferences).isEqualTo(testPreferences)
    }
    
    @Test
    fun `returns error when user not found`() = runTest {
        userRepository.setError(DomainError.Business.UserNotFound)
        preferencesRepository.setPreferences(testPreferences)
        
        val result = useCase("unknown")
        
        assertThat(result.isLeft()).isTrue()
        assertThat(result.leftOrNull()).isEqualTo(DomainError.Business.UserNotFound)
    }
    
    @Test
    fun `short-circuits on first error`() = runTest {
        userRepository.setError(DomainError.Network.NoConnection)
        
        val result = useCase("user-123")
        
        assertThat(result.isLeft()).isTrue()
        // Preferences should NOT be fetched
        assertThat(preferencesRepository.getPreferencesCallCount).isEqualTo(0)
    }
    
    @Test
    fun `creates display name from email when name is empty`() = runTest {
        val userWithEmptyName = testUser.copy(name = "")
        userRepository.setUser(userWithEmptyName)
        preferencesRepository.setPreferences(testPreferences)
        
        val result = useCase("user-123")
        
        val profile = result.getOrNull()!!
        assertThat(profile.displayName).isEqualTo("test") // from test@example.com
    }
}
```

## Anti-Patterns

❌ **Don't inject framework dependencies**
```kotlin
// WRONG - Android dependency in domain
class GetUserUseCase(private val context: Context)

// RIGHT - Pure Kotlin
class GetUserUseCase(private val userRepository: UserRepository)
```

❌ **Don't do multiple unrelated things**
```kotlin
// WRONG - Too many responsibilities
class UserOperationsUseCase {
    fun getUser()
    fun updateUser()
    fun deleteUser()
    fun sendEmail()
}

// RIGHT - Single responsibility
class GetUserUseCase
class UpdateUserUseCase
class DeleteUserUseCase
class SendEmailUseCase
```

❌ **Don't skip error handling**
```kotlin
// WRONG - Throws exceptions
suspend fun getUser(id: String): User = repository.getUser(id)!!

// RIGHT - Explicit error handling
suspend fun getUser(id: String): Either<DomainError, User> = 
    repository.getUser(id)
```

❌ **Don't couple use cases unnecessarily**
```kotlin
// WRONG - Use case depends on another use case
class CreateOrderUseCase(
    private val validateOrderUseCase: ValidateOrderUseCase,  // Coupling
)

// RIGHT - Share repositories instead
class CreateOrderUseCase(
    private val orderRepository: OrderRepository,
    private val orderValidator: OrderValidator,  // Shared utility
)
```

## References

- Clean Architecture: https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html
- Arrow Either: https://arrow-kt.io/docs/core/either/
