package com.keisardev.insight.core.model

/**
 * Represents the type of income entry.
 *
 * @param displayName The user-friendly name for display in UI
 */
enum class IncomeType(val displayName: String) {
    RECURRING("Recurring"),
    ONE_TIME("One-time"),
}
