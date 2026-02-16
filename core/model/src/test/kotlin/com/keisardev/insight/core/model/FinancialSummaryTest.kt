package com.keisardev.insight.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FinancialSummaryTest {

    @Test
    fun `netBalance is income minus expenses`() {
        val summary = FinancialSummary(totalIncome = 5000.0, totalExpenses = 3000.0)
        assertThat(summary.netBalance).isEqualTo(2000.0)
    }

    @Test
    fun `netBalance is negative when expenses exceed income`() {
        val summary = FinancialSummary(totalIncome = 1000.0, totalExpenses = 3000.0)
        assertThat(summary.netBalance).isEqualTo(-2000.0)
    }

    @Test
    fun `netBalance is zero when income equals expenses`() {
        val summary = FinancialSummary(totalIncome = 3000.0, totalExpenses = 3000.0)
        assertThat(summary.netBalance).isEqualTo(0.0)
    }

    @Test
    fun `savingsRate is percentage of income saved`() {
        val summary = FinancialSummary(totalIncome = 5000.0, totalExpenses = 3000.0)
        assertThat(summary.savingsRate).isEqualTo(40.0)
    }

    @Test
    fun `savingsRate is zero when no income`() {
        val summary = FinancialSummary(totalIncome = 0.0, totalExpenses = 100.0)
        assertThat(summary.savingsRate).isEqualTo(0.0)
    }

    @Test
    fun `savingsRate is 100 when no expenses`() {
        val summary = FinancialSummary(totalIncome = 5000.0, totalExpenses = 0.0)
        assertThat(summary.savingsRate).isEqualTo(100.0)
    }

    @Test
    fun `savingsRate is negative when spending exceeds income`() {
        val summary = FinancialSummary(totalIncome = 1000.0, totalExpenses = 1500.0)
        assertThat(summary.savingsRate).isEqualTo(-50.0)
    }

    @Test
    fun `isSaving is true when income exceeds expenses`() {
        val summary = FinancialSummary(totalIncome = 5000.0, totalExpenses = 3000.0)
        assertThat(summary.isSaving).isTrue()
    }

    @Test
    fun `isSaving is true when income equals expenses`() {
        val summary = FinancialSummary(totalIncome = 3000.0, totalExpenses = 3000.0)
        assertThat(summary.isSaving).isTrue()
    }

    @Test
    fun `isSaving is false when expenses exceed income`() {
        val summary = FinancialSummary(totalIncome = 1000.0, totalExpenses = 3000.0)
        assertThat(summary.isSaving).isFalse()
    }

    @Test
    fun `default category breakdowns are empty`() {
        val summary = FinancialSummary(totalIncome = 5000.0, totalExpenses = 3000.0)
        assertThat(summary.incomeByCategory).isEmpty()
        assertThat(summary.expensesByCategory).isEmpty()
    }
}
