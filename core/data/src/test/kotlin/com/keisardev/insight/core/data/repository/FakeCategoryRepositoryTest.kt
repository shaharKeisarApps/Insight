package com.keisardev.insight.core.data.repository

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.keisardev.insight.core.testing.TestData
import com.keisardev.insight.core.testing.fakes.FakeCategoryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Integration tests for [FakeCategoryRepository].
 *
 * These tests verify that the fake repository implements the contract correctly
 * and can be used reliably as a test double.
 */
class FakeCategoryRepositoryTest {

    private lateinit var repository: FakeCategoryRepository

    @Before
    fun setup() {
        repository = FakeCategoryRepository()
    }

    @Test
    fun observeAllCategories_emitsCategoriesAsFlow() = runTest {
        repository.setCategories(TestData.categories)

        repository.observeAllCategories().test {
            val categories = awaitItem()
            assertThat(categories).isEqualTo(TestData.categories)
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

            repository.setCategories(TestData.categories)

            val updated = awaitItem()
            assertThat(updated).isEqualTo(TestData.categories)
            cancel()
        }
    }

    @Test
    fun getCategoryById_returnsCategoryWhenExists() = runTest {
        repository.setCategories(TestData.categories)

        val found = repository.getCategoryById(1)

        assertThat(found).isEqualTo(TestData.foodCategory)
    }

    @Test
    fun getCategoryById_returnsNullWhenNotFound() = runTest {
        repository.setCategories(TestData.categories)

        val notFound = repository.getCategoryById(999)

        assertThat(notFound).isNull()
    }

    @Test
    fun getCategoryById_returnsNullWhenRepositoryEmpty() = runTest {
        val notFound = repository.getCategoryById(1)

        assertThat(notFound).isNull()
    }

    @Test
    fun seedDefaultCategories_incrementsCallCount() = runTest {
        assertThat(repository.seedCallCount).isEqualTo(0)

        repository.seedDefaultCategories()

        assertThat(repository.seedCallCount).isEqualTo(1)
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
    fun seedDefaultCategories_multipleCallsIncrementsCount() = runTest {
        repository.seedDefaultCategories()
        repository.seedDefaultCategories()

        assertThat(repository.seedCallCount).isEqualTo(2)
    }

    @Test
    fun getCategoryCount_returnsCorrectSize() = runTest {
        repository.setCategories(TestData.categories)

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
        repository.setCategories(TestData.categories)

        repository.reset()

        repository.observeAllCategories().test {
            val categories = awaitItem()
            assertThat(categories).isEmpty()
            cancel()
        }
    }

    @Test
    fun reset_resetsSeedCallCount() = runTest {
        repository.seedDefaultCategories()
        repository.seedDefaultCategories()

        repository.reset()

        assertThat(repository.seedCallCount).isEqualTo(0)
    }
}
