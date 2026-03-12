package com.keisardev.insight.shared.di

import com.keisardev.insight.core.ai.di.IosAiFileSystemModule
import com.keisardev.insight.core.ai.model.IosHttpClientModule
import com.keisardev.insight.core.common.di.AppScope
import com.keisardev.insight.core.data.datastore.IosDataStoreModule
import com.keisardev.insight.core.data.repository.CategoryRepository
import com.keisardev.insight.core.data.repository.IncomeCategoryRepository
import com.keisardev.insight.core.database.di.IosDatabaseModule
import com.slack.circuit.foundation.Circuit
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.SingleIn

/**
 * iOS dependency graph for the Insight app.
 *
 * Bindings are provided by IosBindingsModule (companion object @Provides).
 * Platform modules provide database, datastore, filesystem, and HTTP client.
 * Circuit wiring is handled by IosCircuitModule via @ContributesTo aggregation.
 */
@DependencyGraph(AppScope::class)
@SingleIn(AppScope::class)
interface IosAppGraph : IosDatabaseModule, IosDataStoreModule,
    IosAiFileSystemModule, IosHttpClientModule, IosBindingsModule {

    val circuit: Circuit
    val categoryRepository: CategoryRepository
    val incomeCategoryRepository: IncomeCategoryRepository

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(): IosAppGraph
    }
}
