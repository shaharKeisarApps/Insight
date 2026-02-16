# Testing Examples

## 1. Circuit Presenter Test with Molecule/Turbine

```kotlin
package com.example.feature.productlist

import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.test
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProductListPresenterTest {
    private lateinit var fakeRepo: FakeProductRepository
    private lateinit var navigator: FakeNavigator

    @BeforeTest
    fun setup() {
        fakeRepo = FakeProductRepository()
        navigator = FakeNavigator(ProductListScreen)
    }

    @Test
    fun `loads products then shows content`() = runTest {
        fakeRepo.products = listOf(buildProduct(id = "1", name = "Widget"))
        ProductListPresenter(navigator, fakeRepo).test {
            assertTrue(awaitItem().isLoading)
            val content = awaitItem()
            assertFalse(content.isLoading)
            assertEquals("Widget", content.products[0].name)
        }
    }

    @Test
    fun `product click navigates to detail`() = runTest {
        fakeRepo.products = listOf(buildProduct(id = "42"))
        ProductListPresenter(navigator, fakeRepo).test {
            awaitItem() // loading
            awaitItem().eventSink(ProductListScreen.Event.ProductClicked("42"))
            assertEquals(ProductDetailScreen(productId = "42"), navigator.awaitNextScreen())
        }
    }
}
```

## 2. FakeNavigator Assertions

```kotlin
package com.example.feature.checkout

import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.test
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs

class CheckoutPresenterTest {
    private lateinit var navigator: FakeNavigator

    @BeforeTest
    fun setup() { navigator = FakeNavigator(CheckoutScreen) }

    @Test
    fun `successful order resets root to confirmation`() = runTest {
        CheckoutPresenter(navigator, FakeCartRepository(listOf(buildCartItem())), FakePaymentGateway(true)).test {
            awaitItem().eventSink(CheckoutScreen.Event.PlaceOrder)
            awaitItem() // processing
            awaitItem() // done
            assertIs<OrderConfirmationScreen>(navigator.awaitResetRoot())
        }
    }

    @Test
    fun `back press pops`() = runTest {
        CheckoutPresenter(navigator, FakeCartRepository(), FakePaymentGateway()).test {
            awaitItem().eventSink(CheckoutScreen.Event.BackPressed)
            navigator.awaitPop()
        }
    }
}
```

## 3. Repository Test with MockK + runTest + advanceUntilIdle

```kotlin
package com.example.data.product

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProductRepositoryImplTest {
    private val mockApi: ProductApi = mockk()
    private val mockDao: ProductDao = mockk(relaxed = true)
    private val repository = ProductRepositoryImpl(api = mockApi, dao = mockDao)

    @Test
    fun `fetches from API and caches in DB`() = runTest {
        val products = listOf(buildProduct(id = "1", name = "Widget"))
        coEvery { mockApi.getProducts() } returns products
        coEvery { mockDao.observeAll() } returns flowOf(products)
        val result = repository.getProducts(forceRefresh = true)
        advanceUntilIdle()
        assertEquals("Widget", result[0].name)
        coVerify(exactly = 1) { mockDao.insertAll(products) }
    }

    @Test
    fun `propagates API errors`() = runTest {
        coEvery { mockApi.getProducts() } throws RuntimeException("503")
        assertFailsWith<RuntimeException> { repository.getProducts(forceRefresh = true) }
    }
}
```

## 4. Store5 Flow Test with Turbine testIn

```kotlin
package com.example.data.store

import app.cash.turbine.testIn
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.mobilenativefoundation.store.store5.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProductStoreTest {
    @Test
    fun `emits cached then fresh data`() = runTest {
        val dbFlow = MutableSharedFlow<Product?>(replay = 1)
        var writtenToDb: Product? = null
        val store = StoreBuilder.from(
            fetcher = Fetcher.of { _: ProductId -> buildProduct(id = "1", name = "Fresh") },
            sourceOfTruth = SourceOfTruth.of(
                reader = { _: ProductId -> dbFlow },
                writer = { _, p -> writtenToDb = p; dbFlow.emit(p) },
            ),
        ).build()
        dbFlow.emit(buildProduct(id = "1", name = "Cached"))

        val turbine = store.stream(StoreReadRequest.cached(ProductId("1"), refresh = true)).testIn(this)
        val cached = turbine.awaitItem()
        assertIs<StoreReadResponse.Data<Product>>(cached)
        assertEquals("Cached", cached.value.name)
        advanceUntilIdle()
        val fresh = turbine.awaitItem()
        assertIs<StoreReadResponse.Data<Product>>(fresh)
        assertEquals("Fresh", fresh.value.name)
        assertEquals("Fresh", writtenToDb?.name)
        turbine.cancelAndIgnoreRemainingEvents()
    }
}
```

## 5. Compose UI Test

```kotlin
package com.example.feature.productlist.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.feature.productlist.ProductListScreen.Event
import com.example.feature.productlist.ProductListScreen.State
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class ProductListUiTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun `loading state shows indicator`() {
        composeTestRule.setContent {
            ProductListUi(State(isLoading = true, products = emptyList(), error = null, eventSink = {}))
        }
        composeTestRule.onNodeWithContentDescription("Loading").assertIsDisplayed()
    }

    @Test
    fun `renders products`() {
        composeTestRule.setContent {
            ProductListUi(State(false, listOf(ProductUiModel("1", "Widget", "$9.99")), null, {}))
        }
        composeTestRule.onNodeWithText("Widget").assertIsDisplayed()
        composeTestRule.onNodeWithText("$9.99").assertIsDisplayed()
    }

    @Test
    fun `click dispatches event`() {
        var captured: Event? = null
        composeTestRule.setContent {
            ProductListUi(State(false, listOf(ProductUiModel("1", "Widget", "$9.99")), null) { captured = it })
        }
        composeTestRule.onNodeWithText("Widget").performClick()
        assertEquals(Event.ProductClicked("1"), captured)
    }
}
```

## 6. ViewModel Test with Dispatchers.setMain(UnconfinedTestDispatcher())

```kotlin
package com.example.feature.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeRepo: FakeSearchRepository
    private lateinit var viewModel: SearchViewModel

    @BeforeTest fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeSearchRepository()
        viewModel = SearchViewModel(repository = fakeRepo)
    }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `initial state is empty`() {
        assertEquals("", viewModel.state.value.query)
        assertFalse(viewModel.state.value.isSearching)
    }

    @Test
    fun `search populates results`() = runTest {
        fakeRepo.searchResults = listOf(SearchResult("1", "Kotlin Coroutines"))
        viewModel.onQueryChanged("Kotlin")
        advanceUntilIdle()
        assertEquals("Kotlin Coroutines", viewModel.state.value.results[0].title)
    }

    @Test
    fun `search error sets error state`() = runTest {
        fakeRepo.shouldFail = true
        viewModel.onQueryChanged("fail")
        advanceUntilIdle()
        assertTrue(viewModel.state.value.error != null)
    }
}
```

## 7. Integration Test: Presenter -> Repository -> FakeStore

```kotlin
package com.example.integration

import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.test
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.mobilenativefoundation.store.store5.*
import kotlin.test.*

class ProductDetailIntegrationTest {
    private lateinit var navigator: FakeNavigator
    private lateinit var dbFlow: MutableSharedFlow<Product?>
    private lateinit var fakeApi: FakeProductApi

    @BeforeTest fun setup() {
        navigator = FakeNavigator(ProductDetailScreen(productId = "42"))
        dbFlow = MutableSharedFlow(replay = 1)
        fakeApi = FakeProductApi()
    }

    private fun buildStack(): ProductDetailPresenter {
        val store = StoreBuilder.from(
            fetcher = Fetcher.of { key: ProductId -> fakeApi.getProduct(key.id) },
            sourceOfTruth = SourceOfTruth.of(
                reader = { _: ProductId -> dbFlow },
                writer = { _, product -> dbFlow.emit(product) },
            ),
        ).build()
        return ProductDetailPresenter(ProductDetailScreen("42"), navigator, ProductRepositoryImpl(store))
    }

    @Test
    fun `shows cached then fresh from network`() = runTest {
        dbFlow.emit(buildProduct(id = "42", name = "Cached Widget", price = 9.99))
        fakeApi.productResponses["42"] = buildProduct(id = "42", name = "Fresh Widget", price = 12.99)
        buildStack().test {
            assertTrue(awaitItem().isLoading)
            assertEquals("Cached Widget", awaitItem().product?.name)
            advanceUntilIdle()
            val fresh = awaitItem()
            assertEquals("Fresh Widget", fresh.product?.name)
            assertEquals(12.99, fresh.product?.price)
            fresh.eventSink(ProductDetailScreen.Event.BackPressed)
            navigator.awaitPop()
        }
    }

    @Test
    fun `network failure preserves stale cache with error`() = runTest {
        fakeApi.shouldFail = true
        dbFlow.emit(buildProduct(id = "42", name = "Stale Widget"))
        buildStack().test {
            awaitItem() // loading
            assertEquals("Stale Widget", awaitItem().product?.name)
            advanceUntilIdle()
            val errorState = awaitItem()
            assertTrue(errorState.error != null)
            assertEquals("Stale Widget", errorState.product?.name)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```
