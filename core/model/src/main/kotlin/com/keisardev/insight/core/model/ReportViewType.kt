package com.keisardev.insight.core.model

/**
 * Represents the view type for the reports screen.
 *
 * @param displayName The user-friendly name for display in UI
 * @param icon The icon identifier for the filter chip
 */
enum class ReportViewType(val displayName: String, val icon: String) {
    SPENDING("Spending", "payments"),
    EARNINGS("Earnings", "account_balance_wallet"),
    BALANCE("Balance", "balance"),
}
