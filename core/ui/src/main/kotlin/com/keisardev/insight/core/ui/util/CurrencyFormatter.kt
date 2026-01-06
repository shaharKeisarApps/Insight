package com.keisardev.insight.core.ui.util

import java.text.NumberFormat
import java.util.Locale

/**
 * Formats a double value as currency (USD).
 */
fun formatCurrency(amount: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale.US)
    return format.format(amount)
}
