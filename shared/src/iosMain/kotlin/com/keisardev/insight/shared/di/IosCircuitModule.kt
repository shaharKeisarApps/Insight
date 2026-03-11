package com.keisardev.insight.shared.di

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.keisardev.insight.core.common.di.AppScope
import com.keisardev.insight.feature.aichat.AiChatPresenterFactory
import com.keisardev.insight.feature.aichat.AiChatUiFactory
import com.keisardev.insight.feature.expenses.AddEditExpensePresenterFactory
import com.keisardev.insight.feature.expenses.AddEditExpenseUiFactory
import com.keisardev.insight.feature.expenses.ExpensesPresenterFactory
import com.keisardev.insight.feature.expenses.ExpensesUiFactory
import com.keisardev.insight.feature.income.AddEditIncomePresenterFactory
import com.keisardev.insight.feature.income.AddEditIncomeUiFactory
import com.keisardev.insight.feature.income.IncomePresenterFactory
import com.keisardev.insight.feature.income.IncomeUiFactory
import com.keisardev.insight.feature.reports.ReportsPresenterFactory
import com.keisardev.insight.feature.reports.ReportsUiFactory
import com.keisardev.insight.feature.settings.SettingsPresenterFactory
import com.keisardev.insight.feature.settings.SettingsUiFactory
import com.slack.circuit.foundation.Circuit
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

/**
 * Provides Circuit wiring for iOS.
 *
 * Cross-module @ContributesIntoSet aggregation requires Kotlin 2.3.20+ for native targets.
 * With Kotlin 2.3.0, the generated *PresenterFactory and *UiFactory classes carry
 * @Inject constructors so Metro can construct them, but cannot auto-aggregate them
 * into Set<Presenter.Factory> across module boundaries. Each factory is explicitly
 * injected here and passed to Circuit.Builder, which is the correct workaround.
 */
@ContributesTo(AppScope::class)
interface IosCircuitModule {
    companion object {
        @Provides
        @Suppress("LongParameterList")
        fun provideCircuit(
            // Expenses
            expensesPresenterFactory: ExpensesPresenterFactory,
            expensesUiFactory: ExpensesUiFactory,
            addEditExpensePresenterFactory: AddEditExpensePresenterFactory,
            addEditExpenseUiFactory: AddEditExpenseUiFactory,
            // Income
            incomePresenterFactory: IncomePresenterFactory,
            incomeUiFactory: IncomeUiFactory,
            addEditIncomePresenterFactory: AddEditIncomePresenterFactory,
            addEditIncomeUiFactory: AddEditIncomeUiFactory,
            // Reports
            reportsPresenterFactory: ReportsPresenterFactory,
            reportsUiFactory: ReportsUiFactory,
            // Settings
            settingsPresenterFactory: SettingsPresenterFactory,
            settingsUiFactory: SettingsUiFactory,
            // AI Chat
            aiChatPresenterFactory: AiChatPresenterFactory,
            aiChatUiFactory: AiChatUiFactory,
        ): Circuit = Circuit.Builder()
            .addPresenterFactories(
                listOf(
                    expensesPresenterFactory,
                    addEditExpensePresenterFactory,
                    incomePresenterFactory,
                    addEditIncomePresenterFactory,
                    reportsPresenterFactory,
                    settingsPresenterFactory,
                    aiChatPresenterFactory,
                )
            )
            .addUiFactories(
                listOf(
                    expensesUiFactory,
                    addEditExpenseUiFactory,
                    incomeUiFactory,
                    addEditIncomeUiFactory,
                    reportsUiFactory,
                    settingsUiFactory,
                    aiChatUiFactory,
                )
            )
            .setOnUnavailableContent { screen, modifier ->
                Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Route not available: ${screen::class.simpleName}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            .build()
    }
}
