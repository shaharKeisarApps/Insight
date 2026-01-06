package com.keisardev.insight.di

import android.app.Activity
import android.app.Application
import android.content.Intent
import androidx.core.app.AppComponentFactory
import com.keisardev.insight.InsightApp
import dev.zacsweers.metro.Provider
import kotlin.reflect.KClass

class InsightAppComponentFactory : AppComponentFactory() {

    private lateinit var application: InsightApp

    override fun instantiateApplicationCompat(cl: ClassLoader, className: String): Application {
        val app = super.instantiateApplicationCompat(cl, className)
        application = app as InsightApp
        return app
    }

    override fun instantiateActivityCompat(
        cl: ClassLoader,
        className: String,
        intent: Intent?,
    ): Activity {
        return application.appGraph.activityProviders
            .getInstance(className)
            ?: super.instantiateActivityCompat(cl, className, intent)
    }

    private inline fun <reified T : Any> Map<KClass<out T>, Provider<T>>.getInstance(
        className: String,
    ): T? {
        val clazz = Class.forName(className).kotlin
        return this[clazz]?.invoke()
    }
}
