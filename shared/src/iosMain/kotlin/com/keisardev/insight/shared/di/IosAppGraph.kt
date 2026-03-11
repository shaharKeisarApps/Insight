package com.keisardev.insight.shared.di

import com.keisardev.insight.core.common.di.AppScope
import com.keisardev.insight.core.data.datastore.IosDataStoreModule
import com.keisardev.insight.core.data.datastore.UserSettingsRepository
import com.keisardev.insight.core.data.datastore.UserSettingsRepositoryImpl
import com.keisardev.insight.core.data.repository.CategoryRepository
import com.keisardev.insight.core.data.repository.CategoryRepositoryImpl
import com.keisardev.insight.core.data.repository.ExpenseRepository
import com.keisardev.insight.core.data.repository.ExpenseRepositoryImpl
import com.keisardev.insight.core.data.repository.FinancialSummaryRepository
import com.keisardev.insight.core.data.repository.FinancialSummaryRepositoryImpl
import com.keisardev.insight.core.data.repository.IncomeCategoryRepository
import com.keisardev.insight.core.data.repository.IncomeCategoryRepositoryImpl
import com.keisardev.insight.core.data.repository.IncomeRepository
import com.keisardev.insight.core.data.repository.IncomeRepositoryImpl
import com.keisardev.insight.core.database.di.IosDatabaseModule
import com.slack.circuit.foundation.Circuit
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.SingleIn

/**
 * iOS dependency graph for the Insight app.
 *
 * Cross-module @ContributesTo and @ContributesBinding aggregation requires
 * Kotlin 2.3.20+ for native targets. With Kotlin 2.3.0 we work around this by:
 *
 * 1. Explicitly extending IosDatabaseModule and IosDataStoreModule so their
 *    @Provides methods are directly visible to this graph.
 * 2. Declaring @Binds methods for every repository interface -> implementation
 *    binding that @ContributesBinding would have auto-generated on JVM.
 * 3. Circuit wiring is handled in IosCircuitModule via explicit factory injection.
 */
@DependencyGraph(AppScope::class)
@SingleIn(AppScope::class)
interface IosAppGraph : IosDatabaseModule, IosDataStoreModule {

    val circuit: Circuit
    val categoryRepository: CategoryRepository
    val incomeCategoryRepository: IncomeCategoryRepository

    @Binds
    fun bindCategoryRepository(impl: CategoryRepositoryImpl): CategoryRepository

    @Binds
    fun bindIncomeCategoryRepository(impl: IncomeCategoryRepositoryImpl): IncomeCategoryRepository

    @Binds
    fun bindExpenseRepository(impl: ExpenseRepositoryImpl): ExpenseRepository

    @Binds
    fun bindIncomeRepository(impl: IncomeRepositoryImpl): IncomeRepository

    @Binds
    fun bindFinancialSummaryRepository(impl: FinancialSummaryRepositoryImpl): FinancialSummaryRepository

    @Binds
    fun bindUserSettingsRepository(impl: UserSettingsRepositoryImpl): UserSettingsRepository

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(): IosAppGraph
    }
}
