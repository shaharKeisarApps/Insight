package com.keisardev.insight.feature.income

import com.google.common.truth.Truth.assertThat
import com.keisardev.insight.core.model.IncomeType
import com.keisardev.insight.feature.income.fakes.FakeIncomeCategoryRepository
import com.keisardev.insight.feature.income.fakes.FakeIncomeRepository
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AddEditIncomePresenterTest {

    private lateinit var incomeRepository: FakeIncomeRepository
    private lateinit var incomeCategoryRepository: FakeIncomeCategoryRepository
    private lateinit var navigator: FakeNavigator

    @Before
    fun setup() {
        incomeRepository = FakeIncomeRepository()
        incomeCategoryRepository = FakeIncomeCategoryRepository()
        incomeCategoryRepository.setCategories(TestData.categories)
    }

    @Test
    fun `add mode - initial state is not edit mode`() = runTest {
        val screen = AddEditIncomeScreen(incomeId = null)
        navigator = FakeNavigator(screen)

        presenterTestOf(
            presentFunction = {
                AddEditIncomePresenter(
                    screen = screen,
                    navigator = navigator,
                    incomeRepository = incomeRepository,
                    incomeCategoryRepository = incomeCategoryRepository,
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
    fun `edit mode - loads existing income data`() = runTest {
        incomeRepository.setIncomes(listOf(TestData.salaryIncome))
        val screen = AddEditIncomeScreen(incomeId = 1L)
        navigator = FakeNavigator(screen)

        presenterTestOf(
            presentFunction = {
                AddEditIncomePresenter(
                    screen = screen,
                    navigator = navigator,
                    incomeRepository = incomeRepository,
                    incomeCategoryRepository = incomeCategoryRepository,
                ).present()
            },
        ) {
            var state = awaitItem()
            assertThat(state.isEditMode).isTrue()

            // Wait for the LaunchedEffect to populate fields
            do {
                state = awaitItem()
            } while (state.amount.isEmpty())

            assertThat(state.amount).isEqualTo("5000.0")
            assertThat(state.description).isEqualTo("Monthly salary")
            assertThat(state.selectedIncomeType).isEqualTo(IncomeType.RECURRING)
            assertThat(state.selectedCategory?.id).isEqualTo(TestData.salaryCategory.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `amount change event updates state`() = runTest {
        val screen = AddEditIncomeScreen(incomeId = null)
        navigator = FakeNavigator(screen)

        presenterTestOf(
            presentFunction = {
                AddEditIncomePresenter(
                    screen = screen,
                    navigator = navigator,
                    incomeRepository = incomeRepository,
                    incomeCategoryRepository = incomeCategoryRepository,
                ).present()
            },
        ) {
            var state = awaitItem()
            assertThat(state.amount).isEmpty()

            state.eventSink(AddEditIncomeScreen.Event.OnAmountChange("42.50"))

            do {
                state = awaitItem()
            } while (state.amount.isEmpty())

            assertThat(state.amount).isEqualTo("42.50")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `amount change filters non-numeric characters`() = runTest {
        val screen = AddEditIncomeScreen(incomeId = null)
        navigator = FakeNavigator(screen)

        presenterTestOf(
            presentFunction = {
                AddEditIncomePresenter(
                    screen = screen,
                    navigator = navigator,
                    incomeRepository = incomeRepository,
                    incomeCategoryRepository = incomeCategoryRepository,
                ).present()
            },
        ) {
            var state = awaitItem()

            state.eventSink(AddEditIncomeScreen.Event.OnAmountChange("12abc.50"))

            do {
                state = awaitItem()
            } while (state.amount.isEmpty())

            assertThat(state.amount).isEqualTo("12.50")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `description change event updates state`() = runTest {
        val screen = AddEditIncomeScreen(incomeId = null)
        navigator = FakeNavigator(screen)

        presenterTestOf(
            presentFunction = {
                AddEditIncomePresenter(
                    screen = screen,
                    navigator = navigator,
                    incomeRepository = incomeRepository,
                    incomeCategoryRepository = incomeCategoryRepository,
                ).present()
            },
        ) {
            var state = awaitItem()
            assertThat(state.description).isEmpty()

            state.eventSink(AddEditIncomeScreen.Event.OnDescriptionChange("My income"))

            do {
                state = awaitItem()
            } while (state.description.isEmpty())

            assertThat(state.description).isEqualTo("My income")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `description truncates at 100 characters`() = runTest {
        val screen = AddEditIncomeScreen(incomeId = null)
        navigator = FakeNavigator(screen)

        presenterTestOf(
            presentFunction = {
                AddEditIncomePresenter(
                    screen = screen,
                    navigator = navigator,
                    incomeRepository = incomeRepository,
                    incomeCategoryRepository = incomeCategoryRepository,
                ).present()
            },
        ) {
            var state = awaitItem()
            val longDescription = "A".repeat(150)
            state.eventSink(AddEditIncomeScreen.Event.OnDescriptionChange(longDescription))

            do {
                state = awaitItem()
            } while (state.description.isEmpty())

            assertThat(state.description).hasLength(100)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `income type change event updates state`() = runTest {
        val screen = AddEditIncomeScreen(incomeId = null)
        navigator = FakeNavigator(screen)

        presenterTestOf(
            presentFunction = {
                AddEditIncomePresenter(
                    screen = screen,
                    navigator = navigator,
                    incomeRepository = incomeRepository,
                    incomeCategoryRepository = incomeCategoryRepository,
                ).present()
            },
        ) {
            var state = awaitItem()
            assertThat(state.selectedIncomeType).isEqualTo(IncomeType.ONE_TIME)

            state.eventSink(AddEditIncomeScreen.Event.OnIncomeTypeChange(IncomeType.RECURRING))

            do {
                state = awaitItem()
            } while (state.selectedIncomeType == IncomeType.ONE_TIME)

            assertThat(state.selectedIncomeType).isEqualTo(IncomeType.RECURRING)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `category select event updates state`() = runTest {
        val screen = AddEditIncomeScreen(incomeId = null)
        navigator = FakeNavigator(screen)

        presenterTestOf(
            presentFunction = {
                AddEditIncomePresenter(
                    screen = screen,
                    navigator = navigator,
                    incomeRepository = incomeRepository,
                    incomeCategoryRepository = incomeCategoryRepository,
                ).present()
            },
        ) {
            var state = awaitItem()
            assertThat(state.selectedCategory).isNull()

            state.eventSink(AddEditIncomeScreen.Event.OnCategorySelect(TestData.salaryCategory))

            do {
                state = awaitItem()
            } while (state.selectedCategory == null)

            assertThat(state.selectedCategory).isEqualTo(TestData.salaryCategory)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `date select event updates state`() = runTest {
        val screen = AddEditIncomeScreen(incomeId = null)
        navigator = FakeNavigator(screen)

        presenterTestOf(
            presentFunction = {
                AddEditIncomePresenter(
                    screen = screen,
                    navigator = navigator,
                    incomeRepository = incomeRepository,
                    incomeCategoryRepository = incomeCategoryRepository,
                ).present()
            },
        ) {
            var state = awaitItem()
            val initialDate = state.selectedDate
            val newDate = LocalDate(2024, 6, 15)

            state.eventSink(AddEditIncomeScreen.Event.OnDateSelect(newDate))

            do {
                state = awaitItem()
            } while (state.selectedDate == initialDate)

            assertThat(state.selectedDate).isEqualTo(newDate)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `back event navigates back`() = runTest {
        val screen = AddEditIncomeScreen(incomeId = null)
        navigator = FakeNavigator(screen)

        presenterTestOf(
            presentFunction = {
                AddEditIncomePresenter(
                    screen = screen,
                    navigator = navigator,
                    incomeRepository = incomeRepository,
                    incomeCategoryRepository = incomeCategoryRepository,
                ).present()
            },
        ) {
            val state = awaitItem()
            state.eventSink(AddEditIncomeScreen.Event.OnBack)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `save with valid data inserts income`() = runTest {
        val screen = AddEditIncomeScreen(incomeId = null)
        navigator = FakeNavigator(screen)

        presenterTestOf(
            presentFunction = {
                AddEditIncomePresenter(
                    screen = screen,
                    navigator = navigator,
                    incomeRepository = incomeRepository,
                    incomeCategoryRepository = incomeCategoryRepository,
                ).present()
            },
        ) {
            var state = awaitItem()

            // Fill form
            state.eventSink(AddEditIncomeScreen.Event.OnAmountChange("100.00"))
            do { state = awaitItem() } while (state.amount.isEmpty())

            state.eventSink(AddEditIncomeScreen.Event.OnCategorySelect(TestData.salaryCategory))
            do { state = awaitItem() } while (state.selectedCategory == null)

            // Save
            state.eventSink(AddEditIncomeScreen.Event.OnSave)

            navigator.awaitPop()
            assertThat(incomeRepository.insertCallCount).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `delete in edit mode deletes income`() = runTest {
        incomeRepository.setIncomes(listOf(TestData.salaryIncome))
        val screen = AddEditIncomeScreen(incomeId = 1L)
        navigator = FakeNavigator(screen)

        presenterTestOf(
            presentFunction = {
                AddEditIncomePresenter(
                    screen = screen,
                    navigator = navigator,
                    incomeRepository = incomeRepository,
                    incomeCategoryRepository = incomeCategoryRepository,
                ).present()
            },
        ) {
            val state = awaitItem()
            state.eventSink(AddEditIncomeScreen.Event.OnDelete)

            navigator.awaitPop()
            assertThat(incomeRepository.deleteCallCount).isEqualTo(1)
            assertThat(incomeRepository.lastDeletedId).isEqualTo(1L)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
