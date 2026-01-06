package com.keisardev.metroditest.core.data.repository

import com.keisardev.metroditest.core.common.di.AppScope
import com.keisardev.metroditest.core.model.FinancialSummary
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.LocalDate

@ContributesBinding(AppScope::class)
@Inject
class FinancialSummaryRepositoryImpl(
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
) : FinancialSummaryRepository {

    override fun observeFinancialSummary(
        startDate: LocalDate,
        endDate: LocalDate,
    ): Flow<FinancialSummary> {
        return combine(
            incomeRepository.observeMonthlyTotal(startDate, endDate),
            expenseRepository.observeMonthlyTotal(startDate, endDate),
            incomeRepository.observeTotalByCategory(startDate, endDate),
            expenseRepository.observeTotalByCategory(startDate, endDate),
        ) { totalIncome, totalExpenses, incomeByCategory, expensesByCategory ->
            FinancialSummary(
                totalIncome = totalIncome,
                totalExpenses = totalExpenses,
                incomeByCategory = incomeByCategory,
                expensesByCategory = expensesByCategory,
            )
        }
    }
}
