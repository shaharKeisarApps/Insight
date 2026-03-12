package com.keisardev.insight.core.ai.di

import android.app.Application
import com.keisardev.insight.core.common.di.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath

@ContributesTo(AppScope::class)
interface AiFileSystemModule {
    companion object {
        @Provides
        @SingleIn(AppScope::class)
        @ModelsDir
        fun provideModelsDir(application: Application): Path =
            application.filesDir.toOkioPath() / "models"

        @Provides
        @SingleIn(AppScope::class)
        fun provideFileSystem(): FileSystem = FileSystem.SYSTEM
    }
}
