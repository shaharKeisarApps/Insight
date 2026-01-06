package com.keisardev.insight.core.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/**
 * Represents an income entry.
 *
 * @param id The unique identifier for this income
 * @param amount The income amount in dollars
 * @param incomeType Whether this is recurring or one-time income
 * @param category The category this income belongs to
 * @param description Optional description for the income
 * @param date The date when the income was received
 * @param createdAt The timestamp when this income was created
 */
data class Income(
    val id: Long = 0,
    val amount: Double,
    val incomeType: IncomeType,
    val category: IncomeCategory,
    val description: String,
    val date: LocalDate,
    val createdAt: Instant,
)
