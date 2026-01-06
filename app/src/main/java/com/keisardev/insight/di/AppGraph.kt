package com.keisardev.insight.di

import android.app.Activity
import android.app.Application
import com.keisardev.insight.InsightApp
import com.keisardev.insight.core.common.di.AppScope
import com.keisardev.insight.core.data.repository.CategoryRepository
import com.keisardev.insight.core.data.repository.IncomeCategoryRepository
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.SingleIn
import kotlin.reflect.KClass

@DependencyGraph(AppScope::class)
@SingleIn(AppScope::class)
interface AppGraph {
    val activityProviders: Map<KClass<out Activity>, Provider<Activity>>
    val categoryRepository: CategoryRepository
    val incomeCategoryRepository: IncomeCategoryRepository

    fun inject(application: InsightApp)

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides application: Application): AppGraph
    }
}
