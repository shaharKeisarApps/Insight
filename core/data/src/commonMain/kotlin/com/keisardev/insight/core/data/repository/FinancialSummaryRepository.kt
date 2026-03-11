package com.keisardev.insight.core.data.repository

import com.keisardev.insight.core.model.FinancialSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface FinancialSummaryRepository {
    fun observeFinancialSummary(startDate: LocalDate, endDate: LocalDate): Flow<FinancialSummary>
}
