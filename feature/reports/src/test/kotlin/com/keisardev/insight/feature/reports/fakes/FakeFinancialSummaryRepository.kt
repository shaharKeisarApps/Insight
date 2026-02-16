package com.keisardev.insight.feature.reports.fakes

import com.keisardev.insight.core.data.repository.FinancialSummaryRepository
import com.keisardev.insight.core.model.FinancialSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate

class FakeFinancialSummaryRepository : FinancialSummaryRepository {

    private val summary = MutableStateFlow(
        FinancialSummary(totalIncome = 0.0, totalExpenses = 0.0)
    )

    fun setSummary(financialSummary: FinancialSummary) {
        summary.value = financialSummary
    }

    override fun observeFinancialSummary(
        startDate: LocalDate,
        endDate: LocalDate,
    ): Flow<FinancialSummary> = summary
}
