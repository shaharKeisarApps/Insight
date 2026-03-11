package com.keisardev.insight.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.OkioStorage
import com.keisardev.insight.core.common.di.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
@ContributesTo(AppScope::class)
interface IosDataStoreModule {
    companion object {
        @Provides
        @SingleIn(AppScope::class)
        fun provideUserSettingsDataStore(): DataStore<UserSettings> {
            val documentDir = requireNotNull(
                NSFileManager.defaultManager.URLForDirectory(
                    directory = NSDocumentDirectory,
                    inDomain = NSUserDomainMask,
                    appropriateForURL = null,
                    create = true,
                    error = null,
                )?.path
            ) { "Could not locate iOS Documents directory" }
            return DataStoreFactory.create(
                storage = OkioStorage(
                    fileSystem = FileSystem.SYSTEM,
                    serializer = UserSettingsSerializer,
                    producePath = {
                        "$documentDir/datastore/user_settings.json".toPath()
                    },
                ),
            )
        }
    }
}
