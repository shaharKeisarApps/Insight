package com.keisardev.insight.core.ui.util

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

actual fun formatCurrency(amount: Double, currencyCode: String): String {
    val format = if (currencyCode == "DEVICE") {
        NumberFormat.getCurrencyInstance(Locale.getDefault())
    } else {
        NumberFormat.getCurrencyInstance(Locale.US).apply {
            currency = Currency.getInstance(currencyCode)
        }
    }
    return format.format(amount)
}

actual fun currencyDisplayName(currencyCode: String): String {
    if (currencyCode == "DEVICE") {
        val deviceCurrency = Currency.getInstance(Locale.getDefault())
        return "Device Default (${deviceCurrency.symbol})"
    }
    val currency = Currency.getInstance(currencyCode)
    return "${currency.currencyCode} (${currency.symbol})"
}
