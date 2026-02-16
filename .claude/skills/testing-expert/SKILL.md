---
name: testing-expert
description: Enterprise KMP testing expertise covering unit tests, integration tests, Compose UI tests, screenshot tests, and test architecture. Use for writing tests, configuring test infrastructure, or designing test strategies for Circuit/Metro/Store5/Ktor/SQLDelight projects.
---

# Testing Expert Skill

## Overview

Testing in Kotlin Multiplatform follows a layered strategy: business logic lives in `commonTest` where it runs on all targets, platform-specific behavior is verified in `androidTest`/`iosTest`/`jvmTest`, and UI is validated through Compose test rules and screenshot comparison. The test pyramid applies: many fast unit tests, fewer integration tests, and targeted UI/screenshot tests.

## When to use

- **Presenter Tests**: Verifying Circuit Presenter logic with Turbine and Molecule.
- **Repository Tests**: Testing Store5-backed repositories with fake network and database layers.
- **Flow Tests**: Asserting emission sequences, error propagation, and completion with Turbine.
- **Compose UI Tests**: Validating rendering, interaction, and accessibility via `ComposeTestRule`.
- **Screenshot Tests**: Pixel-level regression with Roborazzi or Paparazzi.
- **Integration Tests**: End-to-end data flow from Fetcher through SourceOfTruth to Presenter state.
- **Test Infrastructure**: Setting up dispatchers, test rules, and shared test utilities.

## Quick Reference

For API details and configuration, see [reference.md](reference.md).
For complete code examples, see [examples.md](examples.md).

## Philosophy

### 1. Behavior over Implementation

Test what the code **does**, not how it does it. A Presenter test should assert that a state changes in response to an event, not that a specific internal method was called. This makes tests resilient to refactoring.

```kotlin
// GOOD: Tests the behavior
@Test
fun `adding item updates the list`() = runTest {
    val presenter = createPresenter()
    presenter.test {
        val initial = awaitItem()
        initial.eventSink(Event.AddItem("Milk"))
        val updated = awaitItem()
        assertEquals(listOf("Milk"), updated.items)
    }
}

// BAD: Tests the implementation
@Test
fun `adding item calls repository insert`() = runTest {
    // This couples the test to the implementation detail
    verify(mockRepository).insert("Milk") // No mocking libraries!
}
```

### 2. Fakes over Mocks

KMP has no reliable cross-platform mocking library. This is a feature, not a limitation. Hand-written fakes produce better tests because they force you to define explicit contracts and make test behavior deterministic.

```kotlin
// Fake with controllable behavior
class FakeProductRepository : ProductRepository {
    var products = mutableListOf<Product>()
    var shouldFail = false

    override fun getProducts(): Flow<List<Product>> = flow {
        if (shouldFail) throw IOException("Network error")
        emit(products)
    }
}
```

### 3. commonTest First

All business logic tests belong in `commonTest`. They run on every target (JVM, iOS, JS/WASM) and execute fastest. Only move tests to platform source sets when they genuinely require platform APIs (Android Context, iOS NSUserDefaults, etc.).

| Source Set | What to Test | Examples |
|------------|--------------|---------|
| `commonTest` | All business logic, Presenters, Repositories, Flows, mappers, validators | 90%+ of tests |
| `androidTest` | Android-specific integrations | Room DAOs, WorkManager, Baseline Profiles |
| `iosTest` | iOS-specific integrations | CoreData, Keychain wrappers |
| `jvmTest` | JVM-specific utilities | File I/O, JVM threading |

## Dependencies

```kotlin
// In your shared module's build.gradle.kts
kotlin {
    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test) // 1.10.2
            implementation(libs.turbine)                   // 1.2.1
        }
        androidUnitTest.dependencies {
            implementation(libs.roborazzi)
            implementation(libs.roborazzi.compose)
            implementation(libs.roborazzi.rule)
            implementation(libs.compose.ui.test.junit4)
            implementation(libs.compose.ui.test.manifest)
        }
    }
}
```

Version catalog entries (`libs.versions.toml`):

```toml
[versions]
kotlinx-coroutines = "1.10.2"
turbine = "1.2.1"
roborazzi = "1.35.0"

[libraries]
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinx-coroutines" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
roborazzi = { module = "io.github.takahirom.roborazzi:roborazzi", version.ref = "roborazzi" }
roborazzi-compose = { module = "io.github.takahirom.roborazzi:roborazzi-compose", version.ref = "roborazzi" }
roborazzi-rule = { module = "io.github.takahirom.roborazzi:roborazzi-junit-rule", version.ref = "roborazzi" }
```

## Test Types

### Presenter Tests (Circuit)

Circuit Presenters are `@Composable` functions. Test them with Molecule's `moleculeFlow` or Circuit's built-in `Presenter.test {}` extension. The Presenter returns a `State` and accepts `Event`s via the `eventSink`.

```kotlin
@Test
fun `loading products shows loading then content`() = runTest {
    val fakeRepo = FakeProductRepository()
    fakeRepo.products = listOf(Product("1", "Widget"))

    val presenter = ProductListPresenter(fakeRepo)
    presenter.test {
        val loading = awaitItem()
        assertTrue(loading.isLoading)

        val content = awaitItem()
        assertFalse(content.isLoading)
        assertEquals(1, content.products.size)
    }
}
```

### Repository Tests

Test repositories by substituting real network and database layers with fakes. Verify the data transformation pipeline without touching real I/O.

```kotlin
@Test
fun `repository returns cached data then refreshes`() = runTest {
    val fakeApi = FakeProductApi()
    val fakeDb = FakeProductDao()
    fakeDb.insertProduct(Product("1", "Cached"))
    fakeApi.response = Product("1", "Fresh")

    val repository = ProductRepositoryImpl(fakeApi, fakeDb)
    repository.getProduct("1", refresh = true).test {
        val cached = awaitItem()
        assertEquals("Cached", cached.name)

        val fresh = awaitItem()
        assertEquals("Fresh", fresh.name)

        cancelAndIgnoreRemainingEvents()
    }
}
```

### Flow Tests (Turbine)

Turbine provides structured assertions for Flow emissions. Always use `.test {}` to ensure proper cancellation and avoid hanging tests.

```kotlin
@Test
fun `search debounces input`() = runTest {
    val searchFlow = MutableSharedFlow<String>()
    val results = searchFlow
        .debounce(300)
        .mapLatest { query -> searchApi.search(query) }

    results.test {
        searchFlow.emit("K")
        searchFlow.emit("Ko")
        searchFlow.emit("Kot")
        advanceTimeBy(350)
        assertEquals(searchApi.resultsFor("Kot"), awaitItem())
    }
}
```

### Compose UI Tests

Use `ComposeTestRule` (Android) to assert layout, interactions, and accessibility.

```kotlin
@get:Rule
val composeTestRule = createComposeRule()

@Test
fun `product card shows name and price`() {
    composeTestRule.setContent {
        ProductCard(
            state = ProductCardState(
                name = "Widget",
                price = "$9.99"
            )
        )
    }

    composeTestRule.onNodeWithText("Widget").assertIsDisplayed()
    composeTestRule.onNodeWithText("$9.99").assertIsDisplayed()
}
```

### Screenshot Tests

Capture reference images and compare against future renders to catch visual regressions.

```kotlin
@get:Rule
val roborazziRule = RoborazziRule(
    composeRule = composeTestRule,
    captureRoot = composeTestRule.onRoot(),
    options = RoborazziRule.Options(captureType = RoborazziRule.CaptureType.LastImage())
)

@Test
fun `product list matches reference`() {
    composeTestRule.setContent {
        AppTheme {
            ProductListScreen(state = sampleState())
        }
    }
}
```

## Fake Construction Patterns

### Interface-based Fakes

Every dependency injected into a Presenter or Repository should have a corresponding Fake in the test source set.

```kotlin
// In commonTest
class FakeProductApi : ProductApi {
    var response: ProductResponse? = null
    var error: Throwable? = null

    override suspend fun getProducts(): ProductResponse {
        error?.let { throw it }
        return response ?: error("FakeProductApi: no response configured")
    }
}
```

### Controllable Fakes with Recording

For verifying side effects without mocks, fakes can record calls.

```kotlin
class FakeAnalytics : Analytics {
    val events = mutableListOf<AnalyticsEvent>()

    override fun track(event: AnalyticsEvent) {
        events.add(event)
    }
}

// In test
val analytics = FakeAnalytics()
presenter.handleEvent(Event.Purchase)
assertEquals(1, analytics.events.size)
assertEquals("purchase_completed", analytics.events.first().name)
```

## Test Data Builders

Use builder functions to create test data with sensible defaults. Override only what matters for each test.

```kotlin
fun buildProduct(
    id: String = "test-id",
    name: String = "Test Product",
    price: Double = 9.99,
    inStock: Boolean = true,
    category: String = "General",
): Product = Product(
    id = id,
    name = name,
    price = price,
    inStock = inStock,
    category = category,
)

// Usage: only specify what matters
val expensiveProduct = buildProduct(price = 999.99)
val outOfStock = buildProduct(inStock = false)
```

## Best Practices

1. **Name tests descriptively**: Use backtick-quoted names that read as specifications.
   - Good: `` `empty cart shows zero total` ``
   - Bad: `testCart1`

2. **Test behavior, not implementation**: Assert on observable outputs (state, emissions, side effects), never on internal method calls.

3. **Put business logic in commonTest**: If a test does not need `android.content.Context` or a platform API, it belongs in `commonTest`.

4. **One assertion theme per test**: A test should verify one logical behavior. Multiple assertions are fine if they all verify aspects of the same behavior.

5. **Use `advanceUntilIdle()` liberally**: After emitting events or triggering coroutines in `runTest`, call `advanceUntilIdle()` to flush all pending work before asserting.

6. **Arrange-Act-Assert structure**: Keep test bodies clean with clear separation of setup, action, and verification.

7. **Isolate each test**: Fakes should be recreated per test (in `@BeforeTest` or locally). Never share mutable state between tests.

8. **Prefer `StandardTestDispatcher`**: It gives you explicit control over coroutine execution. Use `UnconfinedTestDispatcher` only when you need immediate dispatch (e.g., `stateIn` collectors).

## Common Pitfalls

| Pitfall | Symptom | Fix |
|---------|---------|-----|
| Testing implementation details | Test breaks on refactor even though behavior is unchanged | Assert on state/output, not internal method calls |
| Not calling `advanceUntilIdle()` | State assertions fail because coroutines have not completed | Always advance the test dispatcher after triggering async work |
| Flaky timing in Flow tests | Tests pass locally but fail in CI | Use `advanceTimeBy()` with exact values instead of real delays; use Turbine `.test {}` |
| Shared mutable fakes | Tests pass individually but fail when run together | Create fresh fakes per test in `@BeforeTest` |
| Using `runBlocking` instead of `runTest` | Delays actually wait; virtual time does not work | Always use `runTest` from `kotlinx-coroutines-test` |
| Forgetting `cancelAndIgnoreRemainingEvents()` | Turbine throws "Unconsumed events" error | Call it when you only care about a subset of emissions |
| Testing in `androidTest` unnecessarily | Slow test suite, cannot run on iOS | Move to `commonTest` unless platform APIs are required |
| Hardcoded test data | Tests are brittle and hard to read | Use test data builder functions with defaults |

## Related Skills

- [circuit-expert](../circuit-expert/SKILL.md) -- Presenter architecture and event handling
- [coroutines-test-expert](../coroutines-test-expert/SKILL.md) -- Deep dive on `runTest`, dispatchers, and virtual time
- [store5-expert](../store5-expert/SKILL.md) -- Store5 caching patterns and `StoreReadResponse` handling
- [quality-expert](../quality-expert/SKILL.md) -- Lint rules, static analysis, and code coverage thresholds
