package com.keisardev.insight.core.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/**
 * Represents an expense entry.
 *
 * @param id The unique identifier for this expense
 * @param amount The expense amount in dollars
 * @param category The category this expense belongs to
 * @param description Optional description for the expense
 * @param date The date when the expense occurred
 * @param createdAt The timestamp when this expense was created
 */
data class Expense(
    val id: Long = 0,
    val amount: Double,
    val category: Category,
    val description: String,
    val date: LocalDate,
    val createdAt: Instant,
)
