package com.keisardev.insight

import android.app.Application
import com.keisardev.insight.di.AppGraph
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class InsightApp : Application() {

    lateinit var appGraph: AppGraph
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        appGraph = createGraphFactory<AppGraph.Factory>()
            .create(this)
            .apply { inject(this@InsightApp) }

        // Seed default categories on first launch
        applicationScope.launch(Dispatchers.IO) {
            appGraph.categoryRepository.seedDefaultCategories()
            appGraph.incomeCategoryRepository.seedDefaultCategories()
        }
    }
}
