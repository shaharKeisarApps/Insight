package com.keisardev.insight.shared.di

import androidx.datastore.core.DataStore
import com.keisardev.insight.core.ai.config.AiConfig
import com.keisardev.insight.core.ai.di.ModelsDir
import com.keisardev.insight.core.ai.model.ModelDownloadTrigger
import com.keisardev.insight.core.ai.model.ModelRepository
import com.keisardev.insight.core.ai.model.ModelRepositoryImpl
import com.keisardev.insight.core.ai.repository.ChatRepository
import com.keisardev.insight.core.ai.repository.ChatRepositoryImpl
import com.keisardev.insight.core.ai.service.AiService
import com.keisardev.insight.core.ai.service.AiServiceStrategy
import com.keisardev.insight.core.ai.service.CloudAiService
import com.keisardev.insight.core.ai.service.IosCloudAiService
import com.keisardev.insight.core.ai.service.LlamatikAiService
import com.keisardev.insight.core.common.CurrencyProvider
import com.keisardev.insight.core.common.di.AppScope
import com.keisardev.insight.core.data.datastore.UserSettings
import com.keisardev.insight.core.data.datastore.UserSettingsRepository
import com.keisardev.insight.core.data.datastore.UserSettingsRepositoryImpl
import com.keisardev.insight.core.data.provider.IosCurrencyProvider
import com.keisardev.insight.core.data.repository.CategoryRepository
import com.keisardev.insight.core.data.repository.CategoryRepositoryImpl
import com.keisardev.insight.core.data.repository.ExpenseRepository
import com.keisardev.insight.core.data.repository.ExpenseRepositoryImpl
import com.keisardev.insight.core.data.repository.FinancialSummaryRepository
import com.keisardev.insight.core.data.repository.FinancialSummaryRepositoryImpl
import com.keisardev.insight.core.data.repository.IncomeCategoryRepository
import com.keisardev.insight.core.data.repository.IncomeCategoryRepositoryImpl
import com.keisardev.insight.core.data.repository.IncomeRepository
import com.keisardev.insight.core.data.repository.IncomeRepositoryImpl
import com.keisardev.insight.core.database.ChatMessageLocalDataSourceImpl
import com.keisardev.insight.core.database.ChatMessageLocalDataSource
import com.keisardev.insight.core.database.ExpenseDatabase
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import okio.FileSystem
import okio.Path

/**
 * Provides interface→impl bindings for iOS.
 *
 * Metro 0.9.2 on Kotlin/Native 2.3.0 cannot resolve @ContributesBinding across
 * module boundaries. We manually construct implementations here using @Provides
 * in a companion object (the only pattern that works for Metro's FIR plugin on native).
 */
@ContributesTo(AppScope::class)
interface IosBindingsModule {
    companion object {
        // ── Data bindings ──

        @Provides
        fun provideCategoryRepository(database: ExpenseDatabase): CategoryRepository =
            CategoryRepositoryImpl(database)

        @Provides
        fun provideIncomeCategoryRepository(database: ExpenseDatabase): IncomeCategoryRepository =
            IncomeCategoryRepositoryImpl(database)

        @Provides
        fun provideExpenseRepository(database: ExpenseDatabase): ExpenseRepository =
            ExpenseRepositoryImpl(database)

        @Provides
        fun provideIncomeRepository(database: ExpenseDatabase): IncomeRepository =
            IncomeRepositoryImpl(database)

        @Provides
        fun provideFinancialSummaryRepository(
            expenseRepository: ExpenseRepository,
            incomeRepository: IncomeRepository,
        ): FinancialSummaryRepository = FinancialSummaryRepositoryImpl(expenseRepository, incomeRepository)

        @Provides
        @SingleIn(AppScope::class)
        fun provideUserSettingsRepository(
            dataStore: DataStore<UserSettings>,
        ): UserSettingsRepository = UserSettingsRepositoryImpl(dataStore)

        @Provides
        @SingleIn(AppScope::class)
        fun provideCurrencyProvider(
            userSettingsRepository: UserSettingsRepository,
        ): CurrencyProvider = IosCurrencyProvider(userSettingsRepository)

        // ── AI bindings ──

        @Provides
        @SingleIn(AppScope::class)
        fun provideAiConfig(
            userSettingsRepository: UserSettingsRepository,
        ): AiConfig = IosAiConfig(userSettingsRepository)

        @Provides
        @SingleIn(AppScope::class)
        fun provideCloudAiService(): CloudAiService = IosCloudAiService()

        @Provides
        fun provideModelDownloadTrigger(): ModelDownloadTrigger = IosModelDownloadTrigger()

        @Provides
        @SingleIn(AppScope::class)
        fun provideChatMessageLocalDataSource(
            database: ExpenseDatabase,
        ): ChatMessageLocalDataSource = ChatMessageLocalDataSourceImpl(database)

        @Provides
        @SingleIn(AppScope::class)
        fun provideModelRepository(
            @ModelsDir modelsDir: Path,
            fileSystem: FileSystem,
            httpClient: HttpClient,
            userSettingsRepository: UserSettingsRepository,
        ): ModelRepository = ModelRepositoryImpl(modelsDir, fileSystem, httpClient, userSettingsRepository)

        @Provides
        @SingleIn(AppScope::class)
        fun provideChatRepository(
            aiService: AiService,
            localDataSource: ChatMessageLocalDataSource,
        ): ChatRepository = ChatRepositoryImpl(aiService, localDataSource)

        @Provides
        @SingleIn(AppScope::class)
        fun provideAiService(
            llamatikAiService: LlamatikAiService,
            cloudAiService: CloudAiService,
            userSettingsRepository: UserSettingsRepository,
        ): AiService = AiServiceStrategy(llamatikAiService, cloudAiService, userSettingsRepository)
    }
}
