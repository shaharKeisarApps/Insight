package com.keisardev.insight.core.ui.util

import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterCurrencyStyle
import platform.Foundation.currentLocale

actual fun formatCurrency(amount: Double, currencyCode: String): String {
    val formatter = NSNumberFormatter()
    formatter.numberStyle = NSNumberFormatterCurrencyStyle
    if (currencyCode != "DEVICE") {
        formatter.currencyCode = currencyCode
    } else {
        formatter.locale = NSLocale.currentLocale
    }
    return formatter.stringFromNumber(NSNumber(double = amount)) ?: "$amount"
}

actual fun currencyDisplayName(currencyCode: String): String {
    if (currencyCode == "DEVICE") {
        val formatter = NSNumberFormatter()
        formatter.numberStyle = NSNumberFormatterCurrencyStyle
        formatter.locale = NSLocale.currentLocale
        return "Device Default (${formatter.currencySymbol})"
    }
    val formatter = NSNumberFormatter()
    formatter.numberStyle = NSNumberFormatterCurrencyStyle
    formatter.currencyCode = currencyCode
    return "$currencyCode (${formatter.currencySymbol})"
}
