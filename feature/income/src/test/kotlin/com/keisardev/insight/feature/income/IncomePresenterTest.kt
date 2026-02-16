package com.keisardev.insight.feature.income

import com.google.common.truth.Truth.assertThat
import com.keisardev.insight.feature.income.fakes.FakeIncomeRepository
import com.keisardev.insight.feature.income.fakes.FakeUserSettingsRepository
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class IncomePresenterTest {

    private lateinit var incomeRepository: FakeIncomeRepository
    private lateinit var userSettingsRepository: FakeUserSettingsRepository
    private lateinit var navigator: FakeNavigator

    @Before
    fun setup() {
        incomeRepository = FakeIncomeRepository()
        userSettingsRepository = FakeUserSettingsRepository()
        navigator = FakeNavigator(IncomeScreen)
    }

    @Test
    fun `initial state has empty incomes`() = runTest {
        presenterTestOf(
            presentFunction = {
                IncomePresenter(
                    navigator = navigator,
                    incomeRepository = incomeRepository,
                    userSettingsRepository = userSettingsRepository,
                ).present()
            },
        ) {
            val state = awaitItem()
            assertThat(state.isLoading).isFalse()
            assertThat(state.incomes).isEmpty()
        }
    }

    @Test
    fun `state contains incomes from repository`() = runTest {
        incomeRepository.setIncomes(TestData.incomes)

        presenterTestOf(
            presentFunction = {
                IncomePresenter(
                    navigator = navigator,
                    incomeRepository = incomeRepository,
                    userSettingsRepository = userSettingsRepository,
                ).present()
            },
        ) {
            skipItems(1)
            val state = awaitItem()
            assertThat(state.incomes).hasSize(3)
            assertThat(state.incomes).containsExactlyElementsIn(TestData.incomes)
        }
    }

    @Test
    fun `state updates when repository emits new incomes`() = runTest {
        presenterTestOf(
            presentFunction = {
                IncomePresenter(
                    navigator = navigator,
                    incomeRepository = incomeRepository,
                    userSettingsRepository = userSettingsRepository,
                ).present()
            },
        ) {
            val initialState = awaitItem()
            assertThat(initialState.incomes).isEmpty()

            incomeRepository.setIncomes(listOf(TestData.salaryIncome))

            val updatedState = awaitItem()
            assertThat(updatedState.incomes).hasSize(1)
            assertThat(updatedState.incomes.first().description).isEqualTo("Monthly salary")
        }
    }

    @Test
    fun `OnAddClick navigates to AddEditIncomeScreen with null id`() = runTest {
        presenterTestOf(
            presentFunction = {
                IncomePresenter(
                    navigator = navigator,
                    incomeRepository = incomeRepository,
                    userSettingsRepository = userSettingsRepository,
                ).present()
            },
        ) {
            val state = awaitItem()
            state.eventSink(IncomeScreen.Event.OnAddClick)

            val navigatedScreen = navigator.awaitNextScreen()
            assertThat(navigatedScreen).isInstanceOf(AddEditIncomeScreen::class.java)
            assertThat((navigatedScreen as AddEditIncomeScreen).incomeId).isNull()
        }
    }

    @Test
    fun `OnIncomeClick navigates to AddEditIncomeScreen with income id`() = runTest {
        incomeRepository.setIncomes(listOf(TestData.salaryIncome))

        presenterTestOf(
            presentFunction = {
                IncomePresenter(
                    navigator = navigator,
                    incomeRepository = incomeRepository,
                    userSettingsRepository = userSettingsRepository,
                ).present()
            },
        ) {
            skipItems(1)
            val state = awaitItem()
            assertThat(state.incomes).isNotEmpty()

            state.eventSink(IncomeScreen.Event.OnIncomeClick(incomeId = 1L))

            val navigatedScreen = navigator.awaitNextScreen()
            assertThat(navigatedScreen).isInstanceOf(AddEditIncomeScreen::class.java)
            assertThat((navigatedScreen as AddEditIncomeScreen).incomeId).isEqualTo(1L)
        }
    }

    @Test
    fun `currency code comes from user settings`() = runTest {
        presenterTestOf(
            presentFunction = {
                IncomePresenter(
                    navigator = navigator,
                    incomeRepository = incomeRepository,
                    userSettingsRepository = userSettingsRepository,
                ).present()
            },
        ) {
            val state = awaitItem()
            assertThat(state.currencyCode).isEqualTo("DEVICE")
        }
    }
}
