package com.keisardev.insight.feature.reports

import com.google.common.truth.Truth.assertThat
import com.keisardev.insight.core.model.FinancialSummary
import com.keisardev.insight.core.model.ReportViewType
import com.keisardev.insight.feature.reports.fakes.FakeExpenseRepository
import com.keisardev.insight.feature.reports.fakes.FakeFinancialSummaryRepository
import com.keisardev.insight.feature.reports.fakes.FakeIncomeRepository
import com.keisardev.insight.feature.reports.fakes.FakeUserSettingsRepository
import com.slack.circuit.test.presenterTestOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ReportsPresenterTest {

    private lateinit var expenseRepository: FakeExpenseRepository
    private lateinit var incomeRepository: FakeIncomeRepository
    private lateinit var financialSummaryRepository: FakeFinancialSummaryRepository
    private lateinit var userSettingsRepository: FakeUserSettingsRepository

    @Before
    fun setup() {
        expenseRepository = FakeExpenseRepository()
        incomeRepository = FakeIncomeRepository()
        financialSummaryRepository = FakeFinancialSummaryRepository()
        userSettingsRepository = FakeUserSettingsRepository()
    }

    @Test
    fun `initial state has current month and year`() = runTest {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

        presenterTestOf(
            presentFunction = {
                ReportsPresenter(
                    expenseRepository = expenseRepository,
                    incomeRepository = incomeRepository,
                    financialSummaryRepository = financialSummaryRepository,
                    userSettingsRepository = userSettingsRepository,
                ).present()
            },
        ) {
            val state = awaitItem()
            assertThat(state.selectedMonth).isEqualTo(now.month)
            assertThat(state.selectedYear).isEqualTo(now.year)
            assertThat(state.selectedViewType).isEqualTo(ReportViewType.SPENDING)
        }
    }

    @Test
    fun `OnPreviousMonth decrements month`() = runTest {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

        presenterTestOf(
            presentFunction = {
                ReportsPresenter(
                    expenseRepository = expenseRepository,
                    incomeRepository = incomeRepository,
                    financialSummaryRepository = financialSummaryRepository,
                    userSettingsRepository = userSettingsRepository,
                ).present()
            },
        ) {
            val state = awaitItem()
            state.eventSink(ReportsScreen.Event.OnPreviousMonth)

            var updated = awaitItem()
            // May get multiple emissions as flows re-collect
            while (updated.selectedMonth == now.month) {
                updated = awaitItem()
            }
            // Month should have changed
            assertThat(updated.selectedMonth).isNotEqualTo(now.month)
        }
    }

    @Test
    fun `OnNextMonth increments month`() = runTest {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

        presenterTestOf(
            presentFunction = {
                ReportsPresenter(
                    expenseRepository = expenseRepository,
                    incomeRepository = incomeRepository,
                    financialSummaryRepository = financialSummaryRepository,
                    userSettingsRepository = userSettingsRepository,
                ).present()
            },
        ) {
            val state = awaitItem()
            state.eventSink(ReportsScreen.Event.OnNextMonth)

            var updated = awaitItem()
            while (updated.selectedMonth == now.month) {
                updated = awaitItem()
            }
            assertThat(updated.selectedMonth).isNotEqualTo(now.month)
        }
    }

    @Test
    fun `OnViewTypeChange updates view type`() = runTest {
        presenterTestOf(
            presentFunction = {
                ReportsPresenter(
                    expenseRepository = expenseRepository,
                    incomeRepository = incomeRepository,
                    financialSummaryRepository = financialSummaryRepository,
                    userSettingsRepository = userSettingsRepository,
                ).present()
            },
        ) {
            val state = awaitItem()
            assertThat(state.selectedViewType).isEqualTo(ReportViewType.SPENDING)

            state.eventSink(ReportsScreen.Event.OnViewTypeChange(ReportViewType.BALANCE))

            val updated = awaitItem()
            assertThat(updated.selectedViewType).isEqualTo(ReportViewType.BALANCE)
        }
    }

    @Test
    fun `financial summary is observed from repository`() = runTest {
        financialSummaryRepository.setSummary(
            FinancialSummary(
                totalIncome = 5000.0,
                totalExpenses = 3000.0,
            )
        )

        presenterTestOf(
            presentFunction = {
                ReportsPresenter(
                    expenseRepository = expenseRepository,
                    incomeRepository = incomeRepository,
                    financialSummaryRepository = financialSummaryRepository,
                    userSettingsRepository = userSettingsRepository,
                ).present()
            },
        ) {
            var state = awaitItem()
            // Wait for financial summary to be populated
            while (state.financialSummary.totalIncome == 0.0) {
                state = awaitItem()
            }
            assertThat(state.financialSummary.totalIncome).isEqualTo(5000.0)
            assertThat(state.financialSummary.totalExpenses).isEqualTo(3000.0)
            assertThat(state.financialSummary.netBalance).isEqualTo(2000.0)
        }
    }
}
