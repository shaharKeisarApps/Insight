package com.keisardev.metroditest

import android.app.Application
import com.keisardev.metroditest.di.AppGraph
import dev.zacsweers.metro.createGraphFactory

class MetroDITestApp : Application() {

    lateinit var appGraph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        appGraph = createGraphFactory<AppGraph.Factory>()
            .create(this)
            .apply { inject(this@MetroDITestApp) }
    }
}
