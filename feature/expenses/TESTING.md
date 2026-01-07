# Expenses Feature Testing Guide

This document describes the test coverage for the expenses feature and provides guidance on maintaining tests when modifying the feature.

## Test Coverage Overview

| Component | Test Type | Coverage |
|-----------|-----------|----------|
| `ExpensesPresenter` | Unit Test | State management, navigation, event handling |
| `AddEditExpensePresenter` | Unit Test | Form validation, CRUD operations, navigation |
| `ExpensesUi` | Screenshot Test | Visual appearance in all states |
| `AddEditExpenseUi` | Screenshot Test | Form layouts, theme variants |

## Running Tests

### Unit Tests

```bash
# Run all expenses feature tests
./gradlew :feature:expenses:test

# Run with coverage report
./gradlew :feature:expenses:testDebugUnitTest

# Run a specific test class
./gradlew :feature:expenses:test --tests "*.ExpensesPresenterTest"
./gradlew :feature:expenses:test --tests "*.AddEditExpensePresenterTest"
```

### Screenshot Tests

```bash
# Generate/update reference screenshots
./gradlew :feature:expenses:updateDebugScreenshotTest

# Validate screenshots against references (fails on differences)
./gradlew :feature:expenses:validateDebugScreenshotTest
```

**Screenshot test outputs:**
- References: `feature/expenses/src/screenshotTest/reference/`
- Reports: `feature/expenses/build/reports/screenshotTest/preview/debug/index.html`

## Test Files

```
feature/expenses/src/
├── test/kotlin/com/keisardev/insight/feature/expenses/
│   ├── TestData.kt                      # Shared test fixtures
│   ├── ExpensesPresenterTest.kt         # List screen presenter tests
│   ├── AddEditExpensePresenterTest.kt   # Add/edit form presenter tests
│   └── fakes/
│       ├── FakeExpenseRepository.kt     # Test double for ExpenseRepository
│       ├── FakeCategoryRepository.kt    # Test double for CategoryRepository
│       └── FakeNavigator.kt             # Test double for Circuit Navigator
└── screenshotTest/kotlin/com/keisardev/insight/feature/expenses/
    ├── ExpensesScreenScreenshotTest.kt   # Expenses list visual tests
    └── AddEditExpenseScreenScreenshotTest.kt  # Form visual tests
```

## What's Tested

### ExpensesPresenter

- ✅ Initial state with empty expenses list
- ✅ State contains expenses from repository
- ✅ State updates when repository emits new data
- ✅ `OnAddClick` navigates to AddEditExpenseScreen with null id
- ✅ `OnExpenseClick` navigates to AddEditExpenseScreen with expense id
- ✅ Multiple navigation events work correctly

### AddEditExpensePresenter

**Add Mode:**
- ✅ Initial state has empty form fields
- ✅ Categories loaded from repository
- ✅ Save creates new expense and navigates back

**Edit Mode:**
- ✅ Form populated from existing expense
- ✅ Save updates existing expense
- ✅ Delete removes expense and navigates back

**Validation:**
- ✅ Amount only accepts digits and single decimal point
- ✅ Description limited to 100 characters
- ✅ Save prevented without valid amount
- ✅ Save prevented without selected category
- ✅ Save prevented with zero amount

**Other:**
- ✅ Category selection updates state
- ✅ Date selection updates state
- ✅ Back navigation works correctly
- ✅ Delete in add mode does nothing

### Screenshot Tests

**ExpensesScreen:**
- Empty state (light/dark)
- Loading state
- With data (light/dark)
- Single item
- Font scale variants (large/small)

**AddEditExpenseScreen:**
- Add mode: empty form, partial form, complete form, saving state
- Edit mode (light/dark)
- Category selection variants
- Long description handling
- Many categories layout
- Font scale variants

---

## ⚠️ IMPORTANT: Updating Tests When Modifying the Feature

When you modify the expenses feature, **you MUST update the corresponding tests**:

### 1. Adding New State Properties

If you add a new property to `ExpensesScreen.State` or `AddEditExpenseScreen.State`:

```kotlin
// In the Screen file
data class State(
    val isLoading: Boolean,
    val expenses: List<Expense>,
    val newProperty: String,  // <-- New property
    val eventSink: (Event) -> Unit,
)
```

**Required test updates:**
1. Update `TestData.kt` if new test fixtures are needed
2. Add tests in `*PresenterTest.kt` for the new property behavior
3. Update screenshot tests to cover visual changes
4. Run `./gradlew :feature:expenses:updateDebugScreenshotTest` to regenerate screenshots

### 2. Adding New Events

If you add a new event:

```kotlin
sealed interface Event : CircuitUiEvent {
    data object OnAddClick : Event
    data class OnNewEvent(val param: String) : Event  // <-- New event
}
```

**Required test updates:**
1. Add test case in `*PresenterTest.kt`:
   ```kotlin
   @Test
   fun `OnNewEvent does expected behavior`() = runTest {
       // Test implementation
   }
   ```

### 3. Changing UI Components

If you modify the UI:

1. **Visual changes**: Run `./gradlew :feature:expenses:updateDebugScreenshotTest`
2. **New UI states**: Add new screenshot test functions with `@PreviewTest` annotation
3. Review the generated HTML report to verify changes are intentional

### 4. Changing Validation Logic

If you change form validation in `AddEditExpensePresenter`:

1. Update the corresponding test in `AddEditExpensePresenterTest.kt`
2. Add edge cases for new validation rules
3. Verify boundary conditions (empty, min, max values)

### 5. Adding New Screens

If you add a new screen to the feature:

1. Create a new `*PresenterTest.kt` file following the existing pattern
2. Create a new `*ScreenshotTest.kt` file with `@PreviewTest` annotations
3. Add fake implementations if new repositories are needed
4. Update this documentation

---

## Test Patterns

### Using Fake Repositories

```kotlin
@Before
fun setup() {
    expenseRepository = FakeExpenseRepository()
    categoryRepository = FakeCategoryRepository()
    categoryRepository.setCategories(TestData.categories)
}

@Test
fun `test with pre-populated data`() = runTest {
    expenseRepository.setExpenses(listOf(TestData.lunchExpense))
    // ... test implementation
}
```

### Testing Navigation

```kotlin
@Test
fun `event navigates to correct screen`() = runTest {
    presenterTestOf(...) {
        val state = awaitItem()
        state.eventSink(SomeEvent)

        val screen = navigator.awaitNextScreen()
        assertThat(screen).isInstanceOf(ExpectedScreen::class.java)
    }
}
```

### Testing Form Validation

```kotlin
@Test
fun `invalid input is rejected`() = runTest {
    presenterTestOf(...) {
        val state = awaitItem()
        state.eventSink(OnInputChange("invalid"))
        expectNoEvents() // State should not change
    }
}
```

---

## CI Integration

These tests are designed to run in CI. Add to your GitHub Actions workflow:

```yaml
- name: Run unit tests
  run: ./gradlew :feature:expenses:test

- name: Validate screenshots
  run: ./gradlew :feature:expenses:validateDebugScreenshotTest

- name: Upload screenshot report on failure
  if: failure()
  uses: actions/upload-artifact@v4
  with:
    name: screenshot-report
    path: feature/expenses/build/reports/screenshotTest/
```

---

## Troubleshooting

### Screenshot tests fail in CI but pass locally

- Ensure CI uses the same JDK version and OS
- Consider using a Docker container for consistent rendering
- Check the HTML report for visual diff details

### Tests are flaky

- Ensure proper use of `awaitItem()` and `expectNoEvents()`
- Use `skipItems(n)` to skip expected state emissions
- Avoid timing-based assertions

### Fake repository not emitting data

- Call `setExpenses()` or `setCategories()` before starting the test
- Use `emitExpenses()` for mid-test emissions
- Check that the flow is being collected

---

**Last updated:** When modifying this feature, update this documentation and all related tests.
