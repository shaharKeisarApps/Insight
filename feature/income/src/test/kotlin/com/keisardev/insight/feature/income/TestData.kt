package com.keisardev.insight.feature.income

import com.keisardev.insight.core.model.Income
import com.keisardev.insight.core.model.IncomeCategory
import com.keisardev.insight.core.model.IncomeType
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate

object TestData {

    val salaryCategory = IncomeCategory(
        id = 1,
        name = "Salary",
        icon = "work",
        colorHex = 0xFF4CAF50,
    )

    val freelanceCategory = IncomeCategory(
        id = 2,
        name = "Freelance",
        icon = "computer",
        colorHex = 0xFF2196F3,
    )

    val investmentCategory = IncomeCategory(
        id = 3,
        name = "Investment",
        icon = "trending_up",
        colorHex = 0xFFFF9800,
    )

    val categories = listOf(salaryCategory, freelanceCategory, investmentCategory)

    val salaryIncome = Income(
        id = 1,
        amount = 5000.0,
        incomeType = IncomeType.RECURRING,
        category = salaryCategory,
        description = "Monthly salary",
        date = LocalDate(2024, 1, 1),
        createdAt = Clock.System.now(),
    )

    val freelanceIncome = Income(
        id = 2,
        amount = 1500.0,
        incomeType = IncomeType.ONE_TIME,
        category = freelanceCategory,
        description = "Web project",
        date = LocalDate(2024, 1, 15),
        createdAt = Clock.System.now(),
    )

    val dividendIncome = Income(
        id = 3,
        amount = 250.0,
        incomeType = IncomeType.RECURRING,
        category = investmentCategory,
        description = "Stock dividends",
        date = LocalDate(2024, 1, 20),
        createdAt = Clock.System.now(),
    )

    val incomes = listOf(salaryIncome, freelanceIncome, dividendIncome)
}
