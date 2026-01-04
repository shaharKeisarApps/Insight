package com.keisardev.metroditest.di

import android.app.Application
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.keisardev.metroditest.data.db.ExpenseDatabase
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@ContributesTo(AppScope::class)
interface DatabaseModule {
    companion object {
        @Provides
        @SingleIn(AppScope::class)
        fun provideExpenseDatabase(application: Application): ExpenseDatabase {
            val driver = AndroidSqliteDriver(
                schema = ExpenseDatabase.Schema,
                context = application,
                name = "expense_tracker.db",
            )
            return ExpenseDatabase(driver)
        }
    }
}
