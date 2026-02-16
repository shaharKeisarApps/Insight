# Error Handling Production Examples

## 1. Repository with Complete Ktor Error Mapping

```kotlin
package com.example.app.feature.user.data

import com.example.app.core.error.AppError
import com.example.app.core.error.Either
import com.example.app.core.error.suspendRunCatching
import com.example.app.core.network.AppErrorException
import com.example.app.core.network.toAppError
import com.example.app.feature.user.domain.User
import com.example.app.feature.user.domain.UserRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import kotlin.coroutines.cancellation.CancellationException

class UserRepositoryImpl(
    private val httpClient: HttpClient,
    private val userDao: UserDao,
    private val errorReporter: ErrorReporter,
) : UserRepository {

    override suspend fun getUser(id: String): Either<AppError, User> {
        return suspendRunCatching {
            httpClient.get("/api/users/$id").body<UserDto>()
        }.fold(
            onSuccess = { dto ->
                val user = dto.toDomain()
                suspendRunCatching { userDao.upsert(user.toEntity()) }
                    .onFailure { errorReporter.log("Cache write failed: ${it.message}") }
                Either.Right(user)
            },
            onFailure = { throwable ->
                val cached = suspendRunCatching { userDao.getById(id) }.getOrNull()
                if (cached != null) {
                    Either.Right(cached.toDomain())
                } else {
                    val appError = throwable.toAppError()
                    errorReporter.report(appError)
                    Either.Left(appError)
                }
            },
        )
    }

    override suspend fun updateProfile(
        id: String,
        name: String,
        email: String,
    ): Either<AppError, User> {
        return suspendRunCatching {
            httpClient.put("/api/users/$id") {
                setBody(UpdateProfileRequest(name, email))
            }.body<UserDto>()
        }.fold(
            onSuccess = { dto -> Either.Right(dto.toDomain()) },
            onFailure = { throwable -> Either.Left(throwable.toAppError()) },
        )
    }
}
```

## 2. Circuit Presenter with Loading/Success/Error States

```kotlin
package com.example.app.feature.user.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.example.app.core.error.AppError
import com.example.app.core.error.Either
import com.example.app.feature.user.domain.User
import com.example.app.feature.user.domain.UserRepository
import com.slack.circuit.retained.produceRetainedState
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter

class UserProfilePresenter(
    private val screen: UserProfileScreen,
    private val navigator: Navigator,
    private val repository: UserRepository,
) : Presenter<UserProfileScreen.State> {

    @Composable
    override fun present(): UserProfileScreen.State {
        var loadAttempt by rememberRetained { mutableIntStateOf(0) }

        val result by produceRetainedState<Either<AppError, User>?>(
            initialValue = null,
            key1 = loadAttempt,
        ) {
            value = repository.getUser(screen.userId)
        }

        return when (val current = result) {
            null -> UserProfileScreen.State.Loading
            is Either.Left -> UserProfileScreen.State.Error(current.value) { event ->
                when (event) {
                    UserProfileScreen.Event.Retry -> loadAttempt++
                    UserProfileScreen.Event.NavigateBack -> navigator.pop()
                }
            }
            is Either.Right -> UserProfileScreen.State.Success(current.value) { event ->
                when (event) {
                    UserProfileScreen.Event.Retry -> loadAttempt++
                    UserProfileScreen.Event.NavigateBack -> navigator.pop()
                }
            }
        }
    }
}

data class UserProfileScreen(val userId: String) : Screen {
    sealed interface State : CircuitUiState {
        data object Loading : State
        data class Success(val user: User, val eventSink: (Event) -> Unit) : State
        data class Error(val error: AppError, val eventSink: (Event) -> Unit) : State
    }

    sealed interface Event : CircuitUiEvent {
        data object Retry : Event
        data object NavigateBack : Event
    }
}
```

## 3. Compose ErrorView with Retry Button

```kotlin
package com.example.app.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.app.core.error.AppError

@Composable
fun ErrorView(
    error: AppError,
    onRetry: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = when (error) {
                is AppError.Network.NoConnection -> Icons.Outlined.WifiOff
                is AppError.Network.Unauthorized -> Icons.Outlined.Lock
                is AppError.Network -> Icons.Outlined.CloudOff
                else -> Icons.Outlined.ErrorOutline
            },
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = error.message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        if (onRetry != null) {
            Button(onClick = onRetry) { Text("Try Again") }
            Spacer(Modifier.height(8.dp))
        }
        if (onBack != null) {
            OutlinedButton(onClick = onBack) { Text("Go Back") }
        }
    }
}
```

## 4. suspendRunCatching in a Use Case

```kotlin
package com.example.app.feature.checkout.domain

import com.example.app.core.error.AppError
import com.example.app.core.error.Either
import com.example.app.core.error.suspendRunCatching
import com.example.app.feature.cart.domain.CartRepository
import com.example.app.feature.payment.domain.PaymentGateway

class PlaceOrderUseCase(
    private val cartRepository: CartRepository,
    private val paymentGateway: PaymentGateway,
    private val orderRepository: OrderRepository,
) {
    suspend fun execute(cartId: String): Either<AppError, Order> {
        val cart = when (val result = cartRepository.getCart(cartId)) {
            is Either.Left -> return result
            is Either.Right -> result.value
        }

        if (cart.items.isEmpty()) {
            return Either.Left(
                AppError.Validation.InvalidField("cart", "Cart is empty"),
            )
        }

        val payment = suspendRunCatching {
            paymentGateway.charge(cart.totalCents)
        }.fold(
            onSuccess = { it },
            onFailure = { throwable ->
                return Either.Left(
                    AppError.Business.OperationFailed(
                        message = "Payment failed",
                        cause = throwable,
                    ),
                )
            },
        )

        return orderRepository.create(cart, payment)
    }
}
```

## 5. CoroutineExceptionHandler Setup with Metro DI

```kotlin
package com.example.app.core.di

import com.example.app.core.error.ErrorReporter
import com.example.app.core.error.GlobalErrorHandler
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

interface AppCoroutineModule {

    @Provides
    @SingleIn(AppScope::class)
    fun provideGlobalErrorHandler(
        errorReporter: ErrorReporter,
    ): GlobalErrorHandler = GlobalErrorHandler(errorReporter)

    @Provides
    @SingleIn(AppScope::class)
    fun provideAppScope(
        globalErrorHandler: GlobalErrorHandler,
    ): CoroutineScope {
        val handler = CoroutineExceptionHandler { _, throwable ->
            globalErrorHandler.handle(throwable)
        }
        return CoroutineScope(SupervisorJob() + Dispatchers.Default + handler)
    }
}
```

## 6. Unit Test Verifying Error Mapping

```kotlin
package com.example.app.feature.user.data

import com.example.app.core.error.AppError
import com.example.app.core.error.Either
import com.example.app.core.network.AppErrorException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class UserRepositoryImplTest {

    private val fakeApi = FakeUserApi()
    private val fakeDao = FakeUserDao()
    private val fakeReporter = FakeErrorReporter()
    private val repository = UserRepositoryImpl(fakeApi.client, fakeDao, fakeReporter)

    @Test
    fun `returns user on successful fetch`() = runTest {
        fakeApi.respondWith(UserDto("1", "Alice", "alice@test.com"))

        val result = repository.getUser("1")

        assertIs<Either.Right<*>>(result)
        assertEquals("Alice", (result.value as User).name)
    }

    @Test
    fun `maps 401 to Unauthorized error`() = runTest {
        fakeApi.respondWithStatus(401)
        fakeDao.clear()

        val result = repository.getUser("1")

        assertIs<Either.Left<*>>(result)
        assertIs<AppError.Network.Unauthorized>(result.value)
    }

    @Test
    fun `falls back to cache on network failure`() = runTest {
        fakeApi.respondWithStatus(500)
        fakeDao.insert(UserEntity("1", "Cached Alice", "alice@test.com"))

        val result = repository.getUser("1")

        assertIs<Either.Right<*>>(result)
        assertEquals("Cached Alice", (result.value as User).name)
    }

    @Test
    fun `maps timeout to Timeout error when no cache`() = runTest {
        fakeApi.respondWithTimeout()
        fakeDao.clear()

        val result = repository.getUser("1")

        assertIs<Either.Left<*>>(result)
        assertIs<AppError.Network.Timeout>(result.value)
    }

    @Test
    fun `reports error to crash analytics`() = runTest {
        fakeApi.respondWithStatus(500)
        fakeDao.clear()

        repository.getUser("1")

        assertEquals(1, fakeReporter.reportedErrors.size)
        assertIs<AppError.Network.ServerError>(fakeReporter.reportedErrors.first())
    }
}
```
