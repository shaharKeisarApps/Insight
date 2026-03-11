package com.keisardev.insight.core.data.repository

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.keisardev.insight.core.testing.TestData
import com.keisardev.insight.core.testing.fakes.FakeIncomeCategoryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Integration tests for [FakeIncomeCategoryRepository].
 *
 * These tests verify that the fake repository implements the contract correctly.
 */
class FakeIncomeCategoryRepositoryTest {

    private lateinit var repository: FakeIncomeCategoryRepository

    @Before
    fun setup() {
        repository = FakeIncomeCategoryRepository()
    }

    @Test
    fun observeAllCategories_emitsCategoriesAsFlow() = runTest {
        repository.setCategories(TestData.incomeCategories)

        repository.observeAllCategories().test {
            val categories = awaitItem()
            assertThat(categories).isEqualTo(TestData.incomeCategories)
            assertThat(categories).hasSize(7)
            cancel()
        }
    }

    @Test
    fun observeAllCategories_initiallyEmpty() = runTest {
        repository.observeAllCategories().test {
            val categories = awaitItem()
            assertThat(categories).isEmpty()
            cancel()
        }
    }

    @Test
    fun setCategories_updatesObservers() = runTest {
        repository.observeAllCategories().test {
            val initial = awaitItem()
            assertThat(initial).isEmpty()

            repository.setCategories(TestData.incomeCategories)

            val updated = awaitItem()
            assertThat(updated).isEqualTo(TestData.incomeCategories)
            cancel()
        }
    }

    @Test
    fun getCategoryById_returnsCategoryWhenExists() = runTest {
        repository.setCategories(TestData.incomeCategories)

        val found = repository.getCategoryById(1)

        assertThat(found).isEqualTo(TestData.salaryIncomeCategory)
    }

    @Test
    fun getCategoryById_returnsNullWhenNotFound() = runTest {
        repository.setCategories(TestData.incomeCategories)

        val notFound = repository.getCategoryById(999)

        assertThat(notFound).isNull()
    }

    @Test
    fun getCategoryById_returnsNullWhenRepositoryEmpty() = runTest {
        val notFound = repository.getCategoryById(1)

        assertThat(notFound).isNull()
    }

    @Test
    fun seedDefaultCategories_populatesCategories() = runTest {
        repository.seedDefaultCategories()

        repository.observeAllCategories().test {
            val categories = awaitItem()
            assertThat(categories).isNotEmpty()
            assertThat(categories).hasSize(7)
            cancel()
        }
    }

    @Test
    fun getCategoryCount_returnsCorrectSize() = runTest {
        repository.setCategories(TestData.incomeCategories)

        val count = repository.getCategoryCount()

        assertThat(count).isEqualTo(7L)
    }

    @Test
    fun getCategoryCount_returnsZeroWhenEmpty() = runTest {
        val count = repository.getCategoryCount()

        assertThat(count).isEqualTo(0L)
    }

    @Test
    fun reset_clearsCategories() = runTest {
        repository.setCategories(TestData.incomeCategories)

        repository.reset()

        repository.observeAllCategories().test {
            val categories = awaitItem()
            assertThat(categories).isEmpty()
            cancel()
        }
    }
}
