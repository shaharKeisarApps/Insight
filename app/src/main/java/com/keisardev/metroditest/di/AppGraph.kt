package com.keisardev.metroditest.di

import android.app.Activity
import android.app.Application
import com.keisardev.metroditest.MetroDITestApp
import com.keisardev.metroditest.data.repository.CategoryRepository
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

    fun inject(application: MetroDITestApp)

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides application: Application): AppGraph
    }
}
