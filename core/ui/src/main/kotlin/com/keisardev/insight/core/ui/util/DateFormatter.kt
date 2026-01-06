package com.keisardev.insight.core.ui.util

import kotlinx.datetime.LocalDate
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char

/**
 * Formats a LocalDate as "Jan 15" (abbreviated month + day).
 */
fun formatDateShort(date: LocalDate): String {
    val format = LocalDate.Format {
        monthName(MonthNames.ENGLISH_ABBREVIATED)
        char(' ')
        dayOfMonth()
    }
    return date.format(format)
}

/**
 * Formats a LocalDate as "January 15, 2024" (full month + day + year).
 */
fun formatDateFull(date: LocalDate): String {
    val format = LocalDate.Format {
        monthName(MonthNames.ENGLISH_FULL)
        char(' ')
        dayOfMonth()
        chars(", ")
        year()
    }
    return date.format(format)
}
