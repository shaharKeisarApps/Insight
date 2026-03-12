package com.keisardev.insight.core.ai.di

import com.keisardev.insight.core.common.di.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.cinterop.ExperimentalForeignApi
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
@ContributesTo(AppScope::class)
interface IosAiFileSystemModule {
    companion object {
        @Provides
        @SingleIn(AppScope::class)
        @ModelsDir
        fun provideModelsDir(): Path {
            val documentDir = requireNotNull(
                NSFileManager.defaultManager.URLForDirectory(
                    directory = NSDocumentDirectory,
                    inDomain = NSUserDomainMask,
                    appropriateForURL = null,
                    create = true,
                    error = null,
                )?.path
            ) { "Could not locate iOS Documents directory" }
            return "$documentDir/models".toPath()
        }

        @Provides
        @SingleIn(AppScope::class)
        fun provideFileSystem(): FileSystem = FileSystem.SYSTEM
    }
}
