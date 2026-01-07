package com.keisardev.insight.feature.expenses

import com.keisardev.insight.core.model.Category
import com.keisardev.insight.core.model.Expense
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate

/**
 * Test fixtures for expense feature tests.
 *
 * Provides consistent test data across all test classes.
 */
object TestData {

    // Categories
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

    val categories = listOf(foodCategory, transportCategory, entertainmentCategory)

    // Expenses
    val lunchExpense = Expense(
        id = 1,
        amount = 25.50,
        category = foodCategory,
        description = "Lunch at restaurant",
        date = LocalDate(2024, 1, 15),
        createdAt = Clock.System.now(),
    )

    val coffeeExpense = Expense(
        id = 2,
        amount = 5.00,
        category = foodCategory,
        description = "Morning coffee",
        date = LocalDate(2024, 1, 15),
        createdAt = Clock.System.now(),
    )

    val taxiExpense = Expense(
        id = 3,
        amount = 15.00,
        category = transportCategory,
        description = "Taxi to work",
        date = LocalDate(2024, 1, 14),
        createdAt = Clock.System.now(),
    )

    val movieExpense = Expense(
        id = 4,
        amount = 12.00,
        category = entertainmentCategory,
        description = "Movie tickets",
        date = LocalDate(2024, 1, 13),
        createdAt = Clock.System.now(),
    )

    val expenses = listOf(lunchExpense, coffeeExpense, taxiExpense, movieExpense)

    // Builder functions for creating custom test data
    fun expense(
        id: Long = 0,
        amount: Double = 10.0,
        category: Category = foodCategory,
        description: String = "Test expense",
        date: LocalDate = LocalDate(2024, 1, 15),
    ) = Expense(
        id = id,
        amount = amount,
        category = category,
        description = description,
        date = date,
        createdAt = Clock.System.now(),
    )

    fun category(
        id: Long = 0,
        name: String = "Test Category",
        icon: String = "category",
        colorHex: Long = 0xFFCCCCCCL,
    ) = Category(
        id = id,
        name = name,
        icon = icon,
        colorHex = colorHex,
    )
}
