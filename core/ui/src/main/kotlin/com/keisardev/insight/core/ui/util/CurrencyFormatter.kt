package com.keisardev.insight.core.ui.util

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Formats a double value as currency using the given currency code.
 * "DEVICE" uses the device's default locale currency.
 */
fun formatCurrency(amount: Double, currencyCode: String = "USD"): String {
    val format = if (currencyCode == "DEVICE") {
        NumberFormat.getCurrencyInstance(Locale.getDefault())
    } else {
        NumberFormat.getCurrencyInstance(Locale.US).apply {
            currency = Currency.getInstance(currencyCode)
        }
    }
    return format.format(amount)
}

/**
 * Returns a display label for a currency code, e.g. "USD ($)" or "Device Default".
 */
fun currencyDisplayName(currencyCode: String): String {
    if (currencyCode == "DEVICE") {
        val deviceCurrency = Currency.getInstance(Locale.getDefault())
        return "Device Default (${deviceCurrency.symbol})"
    }
    val currency = Currency.getInstance(currencyCode)
    return "${currency.currencyCode} (${currency.symbol})"
}

/** Common currencies for the picker. */
val AVAILABLE_CURRENCIES = listOf(
    "DEVICE",
    "USD",
    "EUR",
    "GBP",
    "ILS",
    "JPY",
    "CAD",
    "AUD",
    "CHF",
    "CNY",
    "INR",
    "BRL",
)
