package com.keisardev.insight.core.data.repository

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.keisardev.insight.core.testing.TestData
import com.keisardev.insight.core.testing.fakes.FakeExpenseRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Before
import org.junit.Test

/**
 * Integration tests for [FakeExpenseRepository].
 *
 * These tests verify that the fake repository implements the contract correctly
 * and properly tracks insert, update, and delete operations.
 */
class FakeExpenseRepositoryTest {

    private lateinit var repository: FakeExpenseRepository

    @Before
    fun setup() {
        repository = FakeExpenseRepository()
    }

    @Test
    fun observeAllExpenses_emitsExpensesAsFlow() = runTest {
        repository.setExpenses(TestData.expenses)

        repository.observeAllExpenses().test {
            val expenses = awaitItem()
            assertThat(expenses).isEqualTo(TestData.expenses)
            assertThat(expenses).hasSize(8)
            cancel()
        }
    }

    @Test
    fun observeAllExpenses_initiallyEmpty() = runTest {
        repository.observeAllExpenses().test {
            val expenses = awaitItem()
            assertThat(expenses).isEmpty()
            cancel()
        }
    }

    @Test
    fun observeExpenseById_returnsExpenseWhenExists() = runTest {
        repository.setExpenses(TestData.expenses)

        repository.observeExpenseById(1).test {
            val expense = awaitItem()
            assertThat(expense).isEqualTo(TestData.groceryExpense)
            cancel()
        }
    }

    @Test
    fun observeExpenseById_returnsNullWhenNotFound() = runTest {
        repository.setExpenses(TestData.expenses)

        repository.observeExpenseById(999).test {
            val expense = awaitItem()
            assertThat(expense).isNull()
            cancel()
        }
    }

    @Test
    fun observeExpensesByDateRange_filtersCorrectly() = runTest {
        repository.setExpenses(TestData.expenses)

        val startDate = LocalDate(2026, 3, 1)
        val endDate = LocalDate(2026, 3, 5)

        repository.observeExpensesByDateRange(startDate, endDate).test {
            val filtered = awaitItem()
            assertThat(filtered).isNotEmpty()
            assertThat(filtered.all { it.date >= startDate && it.date < endDate }).isTrue()
            cancel()
        }
    }

    @Test
    fun observeMonthlyTotal_calculatesCorrectly() = runTest {
        repository.setExpenses(TestData.expenses)

        val startDate = LocalDate(2026, 3, 1)
        val endDate = LocalDate(2026, 3, 5)

        repository.observeMonthlyTotal(startDate, endDate).test {
            val total = awaitItem()
            assertThat(total).isGreaterThan(0.0)
            cancel()
        }
    }

    @Test
    fun observeMonthlyTotal_returnsZeroWhenNoExpenses() = runTest {
        val startDate = LocalDate(2026, 3, 1)
        val endDate = LocalDate(2026, 3, 5)

        repository.observeMonthlyTotal(startDate, endDate).test {
            val total = awaitItem()
            assertThat(total).isEqualTo(0.0)
            cancel()
        }
    }

    @Test
    fun observeTotalByCategory_groupsCorrectly() = runTest {
        repository.setExpenses(TestData.expenses)

        val startDate = LocalDate(2026, 2, 1)
        val endDate = LocalDate(2026, 3, 31)

        repository.observeTotalByCategory(startDate, endDate).test {
            val breakdown = awaitItem()
            assertThat(breakdown).isNotEmpty()
            // Verify that we have at least food category with amount
            val foodTotal = breakdown.values.firstOrNull { it > 0 }
            assertThat(foodTotal).isNotNull()
            cancel()
        }
    }

    @Test
    fun insertExpense_incrementsCallCount() = runTest {
        assertThat(repository.insertCallCount).isEqualTo(0)

        repository.insertExpense(TestData.groceryExpense)

        assertThat(repository.insertCallCount).isEqualTo(1)
    }

    @Test
    fun insertExpense_storesLastInsertedExpense() = runTest {
        repository.insertExpense(TestData.groceryExpense)

        assertThat(repository.lastInsertedExpense).isNotNull()
        assertThat(repository.lastInsertedExpense?.amount).isEqualTo(TestData.groceryExpense.amount)
    }

    @Test
    fun insertExpense_assignsId() = runTest {
        val id = repository.insertExpense(TestData.groceryExpense)

        assertThat(id).isGreaterThan(0)
        assertThat(repository.lastInsertedExpense?.id).isEqualTo(id)
    }

    @Test
    fun insertExpense_addsToObservable() = runTest {
        repository.observeAllExpenses().test {
            val initial = awaitItem()
            assertThat(initial).isEmpty()

            repository.insertExpense(TestData.groceryExpense)

            val updated = awaitItem()
            assertThat(updated).hasSize(1)
            cancel()
        }
    }

    @Test
    fun updateExpense_incrementsCallCount() = runTest {
        repository.setExpenses(listOf(TestData.groceryExpense))

        repository.updateExpense(TestData.groceryExpense.copy(amount = 100.0))

        assertThat(repository.updateCallCount).isEqualTo(1)
    }

    @Test
    fun updateExpense_storesLastUpdatedExpense() = runTest {
        repository.setExpenses(listOf(TestData.groceryExpense))

        repository.updateExpense(TestData.groceryExpense.copy(amount = 100.0))

        assertThat(repository.lastUpdatedExpense?.amount).isEqualTo(100.0)
    }

    @Test
    fun updateExpense_updatesInObservable() = runTest {
        repository.setExpenses(listOf(TestData.groceryExpense))

        repository.observeAllExpenses().test {
            val initial = awaitItem()
            assertThat(initial[0].amount).isEqualTo(TestData.groceryExpense.amount)

            repository.updateExpense(TestData.groceryExpense.copy(amount = 100.0))

            val updated = awaitItem()
            assertThat(updated[0].amount).isEqualTo(100.0)
            cancel()
        }
    }

    @Test
    fun deleteExpense_incrementsCallCount() = runTest {
        repository.setExpenses(listOf(TestData.groceryExpense))

        repository.deleteExpense(1)

        assertThat(repository.deleteCallCount).isEqualTo(1)
    }

    @Test
    fun deleteExpense_storesLastDeletedId() = runTest {
        repository.setExpenses(listOf(TestData.groceryExpense))

        repository.deleteExpense(1)

        assertThat(repository.lastDeletedId).isEqualTo(1)
    }

    @Test
    fun deleteExpense_removesFromObservable() = runTest {
        repository.setExpenses(TestData.expenses)

        repository.observeAllExpenses().test {
            val initial = awaitItem()
            assertThat(initial).hasSize(8)

            repository.deleteExpense(1)

            val updated = awaitItem()
            assertThat(updated).hasSize(7)
            cancel()
        }
    }

    @Test
    fun deleteAllExpenses_clearsRepository() = runTest {
        repository.setExpenses(TestData.expenses)

        repository.deleteAllExpenses()

        repository.observeAllExpenses().test {
            val expenses = awaitItem()
            assertThat(expenses).isEmpty()
            cancel()
        }
    }

    @Test
    fun reset_clearsAllState() = runTest {
        repository.setExpenses(TestData.expenses)
        repository.insertExpense(TestData.groceryExpense)
        repository.updateExpense(TestData.groceryExpense.copy(amount = 100.0))
        repository.deleteExpense(1)

        repository.reset()

        assertThat(repository.insertCallCount).isEqualTo(0)
        assertThat(repository.updateCallCount).isEqualTo(0)
        assertThat(repository.deleteCallCount).isEqualTo(0)
        assertThat(repository.lastInsertedExpense).isNull()
        assertThat(repository.lastUpdatedExpense).isNull()
        assertThat(repository.lastDeletedId).isNull()

        repository.observeAllExpenses().test {
            val expenses = awaitItem()
            assertThat(expenses).isEmpty()
            cancel()
        }
    }

    @Test
    fun multipleInserts_incrementIdCorrectly() = runTest {
        val id1 = repository.insertExpense(TestData.groceryExpense)
        val id2 = repository.insertExpense(TestData.restaurantExpense)
        val id3 = repository.insertExpense(TestData.taxiExpense)

        assertThat(id1).isLessThan(id2)
        assertThat(id2).isLessThan(id3)
    }
}
