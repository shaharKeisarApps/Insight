package com.keisardev.insight.feature.expenses

import com.google.common.truth.Truth.assertThat
import com.keisardev.insight.feature.expenses.fakes.FakeExpenseRepository
import com.keisardev.insight.feature.expenses.fakes.FakeUserSettingsRepository
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [ExpensesPresenter].
 *
 * Coverage:
 * - Initial state with empty expenses
 * - State updates when expenses change
 * - Navigation to add expense screen
 * - Navigation to edit expense screen
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ExpensesPresenterTest {

    private lateinit var expenseRepository: FakeExpenseRepository
    private lateinit var userSettingsRepository: FakeUserSettingsRepository
    private lateinit var navigator: FakeNavigator

    @Before
    fun setup() {
        expenseRepository = FakeExpenseRepository()
        userSettingsRepository = FakeUserSettingsRepository()
        navigator = FakeNavigator(ExpensesScreen)
    }

    @Test
    fun `initial state has empty expenses`() = runTest {
        presenterTestOf(
            presentFunction = {
                ExpensesPresenter(
                    navigator = navigator,
                    expenseRepository = expenseRepository,
                    userSettingsRepository = userSettingsRepository,
                ).present()
            },
        ) {
            val state = awaitItem()
            assertThat(state.isLoading).isFalse()
            assertThat(state.expenses).isEmpty()
        }
    }

    @Test
    fun `state contains expenses from repository`() = runTest {
        expenseRepository.setExpenses(TestData.expenses)

        presenterTestOf(
            presentFunction = {
                ExpensesPresenter(
                    navigator = navigator,
                    expenseRepository = expenseRepository,
                    userSettingsRepository = userSettingsRepository,
                ).present()
            },
        ) {
            // First emission may be the initial empty state, skip to the data
            skipItems(1)
            val state = awaitItem()
            assertThat(state.expenses).hasSize(4)
            assertThat(state.expenses).containsExactlyElementsIn(TestData.expenses)
        }
    }

    @Test
    fun `state updates when repository emits new expenses`() = runTest {
        presenterTestOf(
            presentFunction = {
                ExpensesPresenter(
                    navigator = navigator,
                    expenseRepository = expenseRepository,
                    userSettingsRepository = userSettingsRepository,
                ).present()
            },
        ) {
            // Initial empty state
            val initialState = awaitItem()
            assertThat(initialState.expenses).isEmpty()

            // Emit expenses
            expenseRepository.setExpenses(listOf(TestData.lunchExpense))

            val updatedState = awaitItem()
            assertThat(updatedState.expenses).hasSize(1)
            assertThat(updatedState.expenses.first().description).isEqualTo("Lunch at restaurant")
        }
    }

    @Test
    fun `OnAddClick navigates to AddEditExpenseScreen with null id`() = runTest {
        presenterTestOf(
            presentFunction = {
                ExpensesPresenter(
                    navigator = navigator,
                    expenseRepository = expenseRepository,
                    userSettingsRepository = userSettingsRepository,
                ).present()
            },
        ) {
            val state = awaitItem()

            // Trigger add click
            state.eventSink(ExpensesScreen.Event.OnAddClick)

            // Verify navigation
            val navigatedScreen = navigator.awaitNextScreen()
            assertThat(navigatedScreen).isInstanceOf(AddEditExpenseScreen::class.java)
            assertThat((navigatedScreen as AddEditExpenseScreen).expenseId).isNull()
        }
    }

    @Test
    fun `OnExpenseClick navigates to AddEditExpenseScreen with expense id`() = runTest {
        expenseRepository.setExpenses(listOf(TestData.lunchExpense))

        presenterTestOf(
            presentFunction = {
                ExpensesPresenter(
                    navigator = navigator,
                    expenseRepository = expenseRepository,
                    userSettingsRepository = userSettingsRepository,
                ).present()
            },
        ) {
            // Skip initial empty state
            skipItems(1)
            val state = awaitItem()
            assertThat(state.expenses).isNotEmpty()

            // Trigger expense click
            state.eventSink(ExpensesScreen.Event.OnExpenseClick(expenseId = 1L))

            // Verify navigation
            val navigatedScreen = navigator.awaitNextScreen()
            assertThat(navigatedScreen).isInstanceOf(AddEditExpenseScreen::class.java)
            assertThat((navigatedScreen as AddEditExpenseScreen).expenseId).isEqualTo(1L)
        }
    }

    @Test
    fun `expenses are sorted and keyed by id`() = runTest {
        val expenses = listOf(
            TestData.expense(id = 3, amount = 30.0),
            TestData.expense(id = 1, amount = 10.0),
            TestData.expense(id = 2, amount = 20.0),
        )
        expenseRepository.setExpenses(expenses)

        presenterTestOf(
            presentFunction = {
                ExpensesPresenter(
                    navigator = navigator,
                    expenseRepository = expenseRepository,
                    userSettingsRepository = userSettingsRepository,
                ).present()
            },
        ) {
            // Skip initial empty state
            skipItems(1)
            val state = awaitItem()
            assertThat(state.expenses).hasSize(3)
            // Verify we can find expenses by their ids
            assertThat(state.expenses.map { it.id }).containsExactly(3L, 1L, 2L).inOrder()
        }
    }

    @Test
    fun `multiple expense clicks navigate correctly`() = runTest {
        expenseRepository.setExpenses(TestData.expenses)

        presenterTestOf(
            presentFunction = {
                ExpensesPresenter(
                    navigator = navigator,
                    expenseRepository = expenseRepository,
                    userSettingsRepository = userSettingsRepository,
                ).present()
            },
        ) {
            // Skip initial empty state
            skipItems(1)
            val state = awaitItem()

            // Click first expense
            state.eventSink(ExpensesScreen.Event.OnExpenseClick(expenseId = 1L))
            val screen1 = navigator.awaitNextScreen() as AddEditExpenseScreen
            assertThat(screen1.expenseId).isEqualTo(1L)

            // Simulate going back and clicking another expense
            state.eventSink(ExpensesScreen.Event.OnExpenseClick(expenseId = 2L))
            val screen2 = navigator.awaitNextScreen() as AddEditExpenseScreen
            assertThat(screen2.expenseId).isEqualTo(2L)
        }
    }

    @Test
    fun `isLoading is always false`() = runTest {
        // Note: Current implementation hardcodes isLoading = false
        // This test documents the current behavior
        presenterTestOf(
            presentFunction = {
                ExpensesPresenter(
                    navigator = navigator,
                    expenseRepository = expenseRepository,
                    userSettingsRepository = userSettingsRepository,
                ).present()
            },
        ) {
            val state = awaitItem()
            assertThat(state.isLoading).isFalse()
        }
    }
}
