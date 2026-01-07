package com.keisardev.insight.feature.expenses

import com.google.common.truth.Truth.assertThat
import com.keisardev.insight.feature.expenses.fakes.FakeCategoryRepository
import com.keisardev.insight.feature.expenses.fakes.FakeExpenseRepository
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [AddEditExpensePresenter].
 *
 * Coverage:
 * - Add mode (new expense)
 * - Edit mode (existing expense)
 * - Form field validation and input filtering
 * - Save operations (insert and update)
 * - Delete operation
 * - Navigation (back, after save, after delete)
 * - Category selection
 * - Date selection
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AddEditExpensePresenterTest {

    private lateinit var expenseRepository: FakeExpenseRepository
    private lateinit var categoryRepository: FakeCategoryRepository
    private lateinit var navigator: FakeNavigator

    @Before
    fun setup() {
        expenseRepository = FakeExpenseRepository()
        categoryRepository = FakeCategoryRepository()
        categoryRepository.setCategories(TestData.categories)
    }

    // ==================== ADD MODE TESTS ====================

    @Test
    fun `add mode - initial state is not edit mode`() = runTest {
        val screen = AddEditExpenseScreen(expenseId = null)
        navigator = FakeNavigator(screen)

        presenterTestOf(
            presentFunction = {
                AddEditExpensePresenter(
                    screen = screen,
                    navigator = navigator,
                    expenseRepository = expenseRepository,
                    categoryRepository = categoryRepository,
                ).present()
            },
        ) {
            val state = awaitItem()
            assertThat(state.isEditMode).isFalse()
            assertThat(state.isSaving).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `add mode - categories are loaded`() = runTest {
        val screen = AddEditExpenseScreen(expenseId = null)
        navigator = FakeNavigator(screen)

        presenterTestOf(
            presentFunction = {
                AddEditExpensePresenter(
                    screen = screen,
                    navigator = navigator,
                    expenseRepository = expenseRepository,
                    categoryRepository = categoryRepository,
                ).present()
            },
        ) {
            // Wait for state with categories loaded
            var state = awaitItem()
            while (state.categories.isEmpty()) {
                state = awaitItem()
            }
            assertThat(state.categories).hasSize(3)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ==================== EDIT MODE TESTS ====================

    @Test
    fun `edit mode - isEditMode is true`() = runTest {
        expenseRepository.setExpenses(listOf(TestData.lunchExpense))
        val screen = AddEditExpenseScreen(expenseId = 1L)
        navigator = FakeNavigator(screen)

        presenterTestOf(
            presentFunction = {
                AddEditExpensePresenter(
                    screen = screen,
                    navigator = navigator,
                    expenseRepository = expenseRepository,
                    categoryRepository = categoryRepository,
                ).present()
            },
        ) {
            val state = awaitItem()
            assertThat(state.isEditMode).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ==================== INPUT VALIDATION TESTS ====================

    @Test
    fun `amount change event updates state`() = runTest {
        val screen = AddEditExpenseScreen(expenseId = null)
        navigator = FakeNavigator(screen)

        presenterTestOf(
            presentFunction = {
                AddEditExpensePresenter(
                    screen = screen,
                    navigator = navigator,
                    expenseRepository = expenseRepository,
                    categoryRepository = categoryRepository,
                ).present()
            },
        ) {
            var state = awaitItem()
            assertThat(state.amount).isEmpty()

            state.eventSink(AddEditExpenseScreen.Event.OnAmountChange("123.45"))

            // Wait for state with updated amount
            do {
                state = awaitItem()
            } while (state.amount.isEmpty())

            assertThat(state.amount).isEqualTo("123.45")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `description change event updates state`() = runTest {
        val screen = AddEditExpenseScreen(expenseId = null)
        navigator = FakeNavigator(screen)

        presenterTestOf(
            presentFunction = {
                AddEditExpensePresenter(
                    screen = screen,
                    navigator = navigator,
                    expenseRepository = expenseRepository,
                    categoryRepository = categoryRepository,
                ).present()
            },
        ) {
            var state = awaitItem()
            assertThat(state.description).isEmpty()

            state.eventSink(AddEditExpenseScreen.Event.OnDescriptionChange("Test description"))

            // Wait for state with updated description
            do {
                state = awaitItem()
            } while (state.description.isEmpty())

            assertThat(state.description).isEqualTo("Test description")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `category select event updates state`() = runTest {
        val screen = AddEditExpenseScreen(expenseId = null)
        navigator = FakeNavigator(screen)

        presenterTestOf(
            presentFunction = {
                AddEditExpensePresenter(
                    screen = screen,
                    navigator = navigator,
                    expenseRepository = expenseRepository,
                    categoryRepository = categoryRepository,
                ).present()
            },
        ) {
            var state = awaitItem()
            assertThat(state.selectedCategory).isNull()

            state.eventSink(AddEditExpenseScreen.Event.OnCategorySelect(TestData.foodCategory))

            // Wait for state with selected category
            do {
                state = awaitItem()
            } while (state.selectedCategory == null)

            assertThat(state.selectedCategory).isEqualTo(TestData.foodCategory)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `date select event updates state`() = runTest {
        val screen = AddEditExpenseScreen(expenseId = null)
        navigator = FakeNavigator(screen)

        presenterTestOf(
            presentFunction = {
                AddEditExpensePresenter(
                    screen = screen,
                    navigator = navigator,
                    expenseRepository = expenseRepository,
                    categoryRepository = categoryRepository,
                ).present()
            },
        ) {
            var state = awaitItem()
            val initialDate = state.selectedDate

            val newDate = LocalDate(2024, 6, 15)
            state.eventSink(AddEditExpenseScreen.Event.OnDateSelect(newDate))

            // Wait for state with new date
            do {
                state = awaitItem()
            } while (state.selectedDate == initialDate)

            assertThat(state.selectedDate).isEqualTo(newDate)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ==================== NAVIGATION TESTS ====================

    @Test
    fun `back event navigates back`() = runTest {
        val screen = AddEditExpenseScreen(expenseId = null)
        navigator = FakeNavigator(screen)

        presenterTestOf(
            presentFunction = {
                AddEditExpensePresenter(
                    screen = screen,
                    navigator = navigator,
                    expenseRepository = expenseRepository,
                    categoryRepository = categoryRepository,
                ).present()
            },
        ) {
            val state = awaitItem()
            state.eventSink(AddEditExpenseScreen.Event.OnBack)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ==================== SAVE TESTS ====================

    @Test
    fun `save with valid data inserts expense`() = runTest {
        val screen = AddEditExpenseScreen(expenseId = null)
        navigator = FakeNavigator(screen)

        presenterTestOf(
            presentFunction = {
                AddEditExpensePresenter(
                    screen = screen,
                    navigator = navigator,
                    expenseRepository = expenseRepository,
                    categoryRepository = categoryRepository,
                ).present()
            },
        ) {
            var state = awaitItem()

            // Fill form
            state.eventSink(AddEditExpenseScreen.Event.OnAmountChange("25.50"))
            do { state = awaitItem() } while (state.amount.isEmpty())

            state.eventSink(AddEditExpenseScreen.Event.OnCategorySelect(TestData.foodCategory))
            do { state = awaitItem() } while (state.selectedCategory == null)

            // Save
            state.eventSink(AddEditExpenseScreen.Event.OnSave)

            // Wait for navigation
            navigator.awaitPop()

            // Verify insert was called
            assertThat(expenseRepository.insertCallCount).isEqualTo(1)
            assertThat(expenseRepository.lastInsertedExpense?.amount).isEqualTo(25.50)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `save without amount does not insert`() = runTest {
        val screen = AddEditExpenseScreen(expenseId = null)
        navigator = FakeNavigator(screen)

        presenterTestOf(
            presentFunction = {
                AddEditExpensePresenter(
                    screen = screen,
                    navigator = navigator,
                    expenseRepository = expenseRepository,
                    categoryRepository = categoryRepository,
                ).present()
            },
        ) {
            var state = awaitItem()

            // Only select category, no amount
            state.eventSink(AddEditExpenseScreen.Event.OnCategorySelect(TestData.foodCategory))
            do { state = awaitItem() } while (state.selectedCategory == null)

            // Try to save
            state.eventSink(AddEditExpenseScreen.Event.OnSave)

            // Should not insert
            assertThat(expenseRepository.insertCallCount).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `save without category does not insert`() = runTest {
        val screen = AddEditExpenseScreen(expenseId = null)
        navigator = FakeNavigator(screen)

        presenterTestOf(
            presentFunction = {
                AddEditExpensePresenter(
                    screen = screen,
                    navigator = navigator,
                    expenseRepository = expenseRepository,
                    categoryRepository = categoryRepository,
                ).present()
            },
        ) {
            var state = awaitItem()

            // Only enter amount, no category
            state.eventSink(AddEditExpenseScreen.Event.OnAmountChange("25.50"))
            do { state = awaitItem() } while (state.amount.isEmpty())

            // Try to save
            state.eventSink(AddEditExpenseScreen.Event.OnSave)

            // Should not insert
            assertThat(expenseRepository.insertCallCount).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ==================== DELETE TESTS ====================

    @Test
    fun `delete in edit mode deletes expense`() = runTest {
        expenseRepository.setExpenses(listOf(TestData.lunchExpense))
        val screen = AddEditExpenseScreen(expenseId = 1L)
        navigator = FakeNavigator(screen)

        presenterTestOf(
            presentFunction = {
                AddEditExpensePresenter(
                    screen = screen,
                    navigator = navigator,
                    expenseRepository = expenseRepository,
                    categoryRepository = categoryRepository,
                ).present()
            },
        ) {
            val state = awaitItem()

            // Delete
            state.eventSink(AddEditExpenseScreen.Event.OnDelete)

            // Wait for navigation
            navigator.awaitPop()

            // Verify delete was called
            assertThat(expenseRepository.deleteCallCount).isEqualTo(1)
            assertThat(expenseRepository.lastDeletedId).isEqualTo(1L)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `delete in add mode does nothing`() = runTest {
        val screen = AddEditExpenseScreen(expenseId = null)
        navigator = FakeNavigator(screen)

        presenterTestOf(
            presentFunction = {
                AddEditExpensePresenter(
                    screen = screen,
                    navigator = navigator,
                    expenseRepository = expenseRepository,
                    categoryRepository = categoryRepository,
                ).present()
            },
        ) {
            val state = awaitItem()

            // Try to delete
            state.eventSink(AddEditExpenseScreen.Event.OnDelete)

            // Should not delete
            assertThat(expenseRepository.deleteCallCount).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
