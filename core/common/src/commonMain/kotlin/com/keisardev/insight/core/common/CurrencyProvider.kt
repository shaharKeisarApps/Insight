package com.keisardev.insight.core.common

/**
 * Abstraction for retrieving the currency symbol based on user settings.
 */
interface CurrencyProvider {
    /**
     * Get the currency symbol for the current settings.
     *
     * @return Currency symbol (e.g., "$", "€", "£") or "$" as fallback
     */
    suspend fun getCurrencySymbol(): String
}
