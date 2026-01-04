package com.keisardev.metroditest.data.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

data class Expense(
    val id: Long = 0,
    val amount: Double,
    val category: Category,
    val description: String,
    val date: LocalDate,
    val createdAt: Instant,
)
