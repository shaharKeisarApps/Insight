package com.keisardev.insight.core.ui.util

expect fun formatCurrency(amount: Double, currencyCode: String = "USD"): String

expect fun currencyDisplayName(currencyCode: String): String

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
