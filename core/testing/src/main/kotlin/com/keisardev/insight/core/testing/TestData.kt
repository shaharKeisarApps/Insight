package com.keisardev.insight.core.testing

import com.keisardev.insight.core.model.Category
import com.keisardev.insight.core.model.Expense
import com.keisardev.insight.core.model.Income
import com.keisardev.insight.core.model.IncomeCategory
import com.keisardev.insight.core.model.IncomeType
import kotlin.time.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/**
 * Shared test data for all testing modules.
 * Provides sample fixtures for Category, Expense, Income, and IncomeCategory objects.
 */
object TestData {

    // Sample Categories
    val foodCategory = Category(
        id = 1,
        name = "Food",
        icon = "restaurant",
        colorHex = 0xFFE57373L,
    )

    val transportCategory = Category(
        id = 2,
        name = "Transport",
        icon = "directions_car",
        colorHex = 0xFF64B5F6L,
    )

    val entertainmentCategory = Category(
        id = 3,
        name = "Entertainment",
        icon = "movie",
        colorHex = 0xFFBA68C8L,
    )

    val shoppingCategory = Category(
        id = 4,
        name = "Shopping",
        icon = "shopping_bag",
        colorHex = 0xFF81C784L,
    )

    val billsCategory = Category(
        id = 5,
        name = "Bills",
        icon = "receipt",
        colorHex = 0xFFFFB74DL,
    )

    val healthCategory = Category(
        id = 6,
        name = "Health",
        icon = "medical_services",
        colorHex = 0xFF4DB6ACL,
    )

    val otherCategory = Category(
        id = 7,
        name = "Other",
        icon = "more_horiz",
        colorHex = 0xFF90A4AEL,
    )

    val categories = listOf(
        foodCategory,
        transportCategory,
        entertainmentCategory,
        shoppingCategory,
        billsCategory,
        healthCategory,
        otherCategory,
    )

    // Sample Income Categories
    val salaryIncomeCategory = IncomeCategory(
        id = 1,
        name = "Salary",
        icon = "payments",
        colorHex = 0xFF4CAF50L,
    )

    val freelanceIncomeCategory = IncomeCategory(
        id = 2,
        name = "Freelance",
        icon = "work",
        colorHex = 0xFF2196F3L,
    )

    val investmentIncomeCategory = IncomeCategory(
        id = 3,
        name = "Investments",
        icon = "trending_up",
        colorHex = 0xFF9C27B0L,
    )

    val rentalIncomeCategory = IncomeCategory(
        id = 4,
        name = "Rental",
        icon = "home",
        colorHex = 0xFFFF9800L,
    )

    val giftIncomeCategory = IncomeCategory(
        id = 5,
        name = "Gifts",
        icon = "card_giftcard",
        colorHex = 0xFFE91E63L,
    )

    val bonusIncomeCategory = IncomeCategory(
        id = 6,
        name = "Bonus",
        icon = "stars",
        colorHex = 0xFFFFEB3BL,
    )

    val otherIncomeCategory = IncomeCategory(
        id = 7,
        name = "Other",
        icon = "more_horiz",
        colorHex = 0xFF607D8BL,
    )

    val incomeCategories = listOf(
        salaryIncomeCategory,
        freelanceIncomeCategory,
        investmentIncomeCategory,
        rentalIncomeCategory,
        giftIncomeCategory,
        bonusIncomeCategory,
        otherIncomeCategory,
    )

    // Sample Expenses with varied amounts and dates
    val groceryExpense = Expense(
        id = 1,
        amount = 45.50,
        category = foodCategory,
        description = "Grocery shopping",
        date = LocalDate(2026, 3, 1),
        createdAt = Clock.System.now(),
    )

    val restaurantExpense = Expense(
        id = 2,
        amount = 28.75,
        category = foodCategory,
        description = "Dinner with friends",
        date = LocalDate(2026, 3, 3),
        createdAt = Clock.System.now(),
    )

    val taxiExpense = Expense(
        id = 3,
        amount = 15.00,
        category = transportCategory,
        description = "Taxi to office",
        date = LocalDate(2026, 3, 2),
        createdAt = Clock.System.now(),
    )

    val busExpense = Expense(
        id = 4,
        amount = 50.00,
        category = transportCategory,
        description = "Monthly bus pass",
        date = LocalDate(2026, 3, 1),
        createdAt = Clock.System.now(),
    )

    val movieExpense = Expense(
        id = 5,
        amount = 15.00,
        category = entertainmentCategory,
        description = "Movie tickets",
        date = LocalDate(2026, 3, 4),
        createdAt = Clock.System.now(),
    )

    val electronicExpense = Expense(
        id = 6,
        amount = 120.00,
        category = shoppingCategory,
        description = "Headphones",
        date = LocalDate(2026, 2, 28),
        createdAt = Clock.System.now(),
    )

    val internetExpense = Expense(
        id = 7,
        amount = 60.00,
        category = billsCategory,
        description = "Internet bill",
        date = LocalDate(2026, 3, 1),
        createdAt = Clock.System.now(),
    )

    val doctorExpense = Expense(
        id = 8,
        amount = 100.00,
        category = healthCategory,
        description = "Doctor visit",
        date = LocalDate(2026, 2, 25),
        createdAt = Clock.System.now(),
    )

    val expenses = listOf(
        groceryExpense,
        restaurantExpense,
        taxiExpense,
        busExpense,
        movieExpense,
        electronicExpense,
        internetExpense,
        doctorExpense,
    )

    // Sample Income with varied amounts and types
    val monthlySalary = Income(
        id = 1,
        amount = 5000.00,
        incomeType = IncomeType.RECURRING,
        category = salaryIncomeCategory,
        description = "Monthly salary",
        date = LocalDate(2026, 3, 1),
        createdAt = Clock.System.now(),
    )

    val freelanceJob = Income(
        id = 2,
        amount = 1200.00,
        incomeType = IncomeType.ONE_TIME,
        category = freelanceIncomeCategory,
        description = "Website design project",
        date = LocalDate(2026, 2, 28),
        createdAt = Clock.System.now(),
    )

    val investmentReturn = Income(
        id = 3,
        amount = 250.00,
        incomeType = IncomeType.ONE_TIME,
        category = investmentIncomeCategory,
        description = "Stock dividend",
        date = LocalDate(2026, 2, 15),
        createdAt = Clock.System.now(),
    )

    val bonus = Income(
        id = 4,
        amount = 2000.00,
        incomeType = IncomeType.ONE_TIME,
        category = bonusIncomeCategory,
        description = "Annual bonus",
        date = LocalDate(2026, 3, 3),
        createdAt = Clock.System.now(),
    )

    val gift = Income(
        id = 5,
        amount = 100.00,
        incomeType = IncomeType.ONE_TIME,
        category = giftIncomeCategory,
        description = "Birthday gift",
        date = LocalDate(2026, 3, 2),
        createdAt = Clock.System.now(),
    )

    val incomes = listOf(
        monthlySalary,
        freelanceJob,
        investmentReturn,
        bonus,
        gift,
    )
}
