package com.keisardev.insight.core.data.datastore

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.OkioStorage
import com.keisardev.insight.core.common.di.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import okio.FileSystem
import okio.Path.Companion.toOkioPath

@ContributesTo(AppScope::class)
interface DataStoreModule {
    companion object {
        @Provides
        @SingleIn(AppScope::class)
        fun provideUserSettingsDataStore(
            application: Application,
        ): DataStore<UserSettings> = DataStoreFactory.create(
            storage = OkioStorage(
                fileSystem = FileSystem.SYSTEM,
                serializer = UserSettingsSerializer,
                producePath = {
                    application.filesDir.resolve("datastore").also { it.mkdirs() }
                        .resolve("user_settings.json")
                        .toOkioPath()
                },
            ),
        )
    }
}
