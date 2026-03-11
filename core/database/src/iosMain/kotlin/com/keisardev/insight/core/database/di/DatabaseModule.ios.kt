package com.keisardev.insight.core.database.di

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.keisardev.insight.core.common.di.AppScope
import com.keisardev.insight.core.database.ExpenseDatabase
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@ContributesTo(AppScope::class)
interface IosDatabaseModule {
    companion object {
        @Provides
        @SingleIn(AppScope::class)
        fun provideExpenseDatabase(): ExpenseDatabase {
            val driver = NativeSqliteDriver(
                schema = ExpenseDatabase.Schema,
                name = "expense_tracker.db",
            )
            return ExpenseDatabase(driver)
        }
    }
}
