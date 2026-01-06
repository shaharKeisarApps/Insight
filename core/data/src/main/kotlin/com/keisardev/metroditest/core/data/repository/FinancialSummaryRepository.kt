package com.keisardev.metroditest.core.data.repository

import com.keisardev.metroditest.core.model.FinancialSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface FinancialSummaryRepository {
    fun observeFinancialSummary(startDate: LocalDate, endDate: LocalDate): Flow<FinancialSummary>
}
