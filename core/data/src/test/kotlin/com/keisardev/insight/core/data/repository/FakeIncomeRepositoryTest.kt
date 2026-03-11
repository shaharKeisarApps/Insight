package com.keisardev.insight.core.data.repository

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.keisardev.insight.core.testing.TestData
import com.keisardev.insight.core.testing.fakes.FakeIncomeRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Before
import org.junit.Test

/**
 * Integration tests for [FakeIncomeRepository].
 *
 * These tests verify that the fake repository implements the contract correctly
 * and properly tracks insert, update, and delete operations.
 */
class FakeIncomeRepositoryTest {

    private lateinit var repository: FakeIncomeRepository

    @Before
    fun setup() {
        repository = FakeIncomeRepository()
    }

    @Test
    fun observeAllIncome_emitsIncomeAsFlow() = runTest {
        repository.setIncomes(TestData.incomes)

        repository.observeAllIncome().test {
            val incomes = awaitItem()
            assertThat(incomes).isEqualTo(TestData.incomes)
            assertThat(incomes).hasSize(5)
            cancel()
        }
    }

    @Test
    fun observeAllIncome_initiallyEmpty() = runTest {
        repository.observeAllIncome().test {
            val incomes = awaitItem()
            assertThat(incomes).isEmpty()
            cancel()
        }
    }

    @Test
    fun observeIncomeById_returnsIncomeWhenExists() = runTest {
        repository.setIncomes(TestData.incomes)

        repository.observeIncomeById(1).test {
            val income = awaitItem()
            assertThat(income).isEqualTo(TestData.monthlySalary)
            cancel()
        }
    }

    @Test
    fun observeIncomeById_returnsNullWhenNotFound() = runTest {
        repository.setIncomes(TestData.incomes)

        repository.observeIncomeById(999).test {
            val income = awaitItem()
            assertThat(income).isNull()
            cancel()
        }
    }

    @Test
    fun observeIncomeByDateRange_filtersCorrectly() = runTest {
        repository.setIncomes(TestData.incomes)

        val startDate = LocalDate(2026, 3, 1)
        val endDate = LocalDate(2026, 3, 5)

        repository.observeIncomeByDateRange(startDate, endDate).test {
            val filtered = awaitItem()
            assertThat(filtered.all { it.date >= startDate && it.date < endDate }).isTrue()
            cancel()
        }
    }

    @Test
    fun observeMonthlyTotal_calculatesCorrectly() = runTest {
        repository.setIncomes(TestData.incomes)

        val startDate = LocalDate(2026, 3, 1)
        val endDate = LocalDate(2026, 3, 5)

        repository.observeMonthlyTotal(startDate, endDate).test {
            val total = awaitItem()
            assertThat(total).isGreaterThan(0.0)
            cancel()
        }
    }

    @Test
    fun observeMonthlyTotal_returnsZeroWhenNoIncome() = runTest {
        val startDate = LocalDate(2026, 1, 1)
        val endDate = LocalDate(2026, 1, 31)

        repository.observeMonthlyTotal(startDate, endDate).test {
            val total = awaitItem()
            assertThat(total).isEqualTo(0.0)
            cancel()
        }
    }

    @Test
    fun observeTotalByCategory_groupsCorrectly() = runTest {
        repository.setIncomes(TestData.incomes)

        val startDate = LocalDate(2026, 2, 1)
        val endDate = LocalDate(2026, 3, 31)

        repository.observeTotalByCategory(startDate, endDate).test {
            val breakdown = awaitItem()
            assertThat(breakdown).isNotEmpty()
            cancel()
        }
    }

    @Test
    fun insertIncome_incrementsCallCount() = runTest {
        assertThat(repository.insertCallCount).isEqualTo(0)

        repository.insertIncome(TestData.monthlySalary)

        assertThat(repository.insertCallCount).isEqualTo(1)
    }

    @Test
    fun insertIncome_storesLastInsertedIncome() = runTest {
        repository.insertIncome(TestData.monthlySalary)

        assertThat(repository.lastInsertedIncome).isNotNull()
        assertThat(repository.lastInsertedIncome?.amount).isEqualTo(TestData.monthlySalary.amount)
    }

    @Test
    fun insertIncome_assignsId() = runTest {
        val id = repository.insertIncome(TestData.monthlySalary)

        assertThat(id).isGreaterThan(0)
        assertThat(repository.lastInsertedIncome?.id).isEqualTo(id)
    }

    @Test
    fun insertIncome_addsToObservable() = runTest {
        repository.observeAllIncome().test {
            val initial = awaitItem()
            assertThat(initial).isEmpty()

            repository.insertIncome(TestData.monthlySalary)

            val updated = awaitItem()
            assertThat(updated).hasSize(1)
            cancel()
        }
    }

    @Test
    fun updateIncome_incrementsCallCount() = runTest {
        repository.setIncomes(listOf(TestData.monthlySalary))

        repository.updateIncome(TestData.monthlySalary.copy(amount = 6000.0))

        assertThat(repository.updateCallCount).isEqualTo(1)
    }

    @Test
    fun updateIncome_storesLastUpdatedIncome() = runTest {
        repository.setIncomes(listOf(TestData.monthlySalary))

        repository.updateIncome(TestData.monthlySalary.copy(amount = 6000.0))

        assertThat(repository.lastUpdatedIncome?.amount).isEqualTo(6000.0)
    }

    @Test
    fun updateIncome_updatesInObservable() = runTest {
        repository.setIncomes(listOf(TestData.monthlySalary))

        repository.observeAllIncome().test {
            val initial = awaitItem()
            assertThat(initial[0].amount).isEqualTo(TestData.monthlySalary.amount)

            repository.updateIncome(TestData.monthlySalary.copy(amount = 6000.0))

            val updated = awaitItem()
            assertThat(updated[0].amount).isEqualTo(6000.0)
            cancel()
        }
    }

    @Test
    fun deleteIncome_incrementsCallCount() = runTest {
        repository.setIncomes(listOf(TestData.monthlySalary))

        repository.deleteIncome(1)

        assertThat(repository.deleteCallCount).isEqualTo(1)
    }

    @Test
    fun deleteIncome_storesLastDeletedId() = runTest {
        repository.setIncomes(listOf(TestData.monthlySalary))

        repository.deleteIncome(1)

        assertThat(repository.lastDeletedId).isEqualTo(1)
    }

    @Test
    fun deleteIncome_removesFromObservable() = runTest {
        repository.setIncomes(TestData.incomes)

        repository.observeAllIncome().test {
            val initial = awaitItem()
            assertThat(initial).hasSize(5)

            repository.deleteIncome(1)

            val updated = awaitItem()
            assertThat(updated).hasSize(4)
            cancel()
        }
    }

    @Test
    fun deleteAllIncome_clearsRepository() = runTest {
        repository.setIncomes(TestData.incomes)

        repository.deleteAllIncome()

        repository.observeAllIncome().test {
            val incomes = awaitItem()
            assertThat(incomes).isEmpty()
            cancel()
        }
    }

    @Test
    fun reset_clearsAllState() = runTest {
        repository.setIncomes(TestData.incomes)
        repository.insertIncome(TestData.monthlySalary)
        repository.updateIncome(TestData.monthlySalary.copy(amount = 6000.0))
        repository.deleteIncome(1)

        repository.reset()

        assertThat(repository.insertCallCount).isEqualTo(0)
        assertThat(repository.updateCallCount).isEqualTo(0)
        assertThat(repository.deleteCallCount).isEqualTo(0)
        assertThat(repository.lastInsertedIncome).isNull()
        assertThat(repository.lastUpdatedIncome).isNull()
        assertThat(repository.lastDeletedId).isNull()

        repository.observeAllIncome().test {
            val incomes = awaitItem()
            assertThat(incomes).isEmpty()
            cancel()
        }
    }

    @Test
    fun multipleInserts_incrementIdCorrectly() = runTest {
        val id1 = repository.insertIncome(TestData.monthlySalary)
        val id2 = repository.insertIncome(TestData.freelanceJob)
        val id3 = repository.insertIncome(TestData.bonus)

        assertThat(id1).isLessThan(id2)
        assertThat(id2).isLessThan(id3)
    }
}
