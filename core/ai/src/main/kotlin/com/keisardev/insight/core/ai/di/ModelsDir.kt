package com.keisardev.insight.core.ai.di

import dev.zacsweers.metro.Qualifier

/**
 * Qualifier for the on-device models directory path (`{filesDir}/models`).
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ModelsDir
