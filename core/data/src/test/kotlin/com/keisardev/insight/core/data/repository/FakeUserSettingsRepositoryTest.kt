package com.keisardev.insight.core.data.repository

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.keisardev.insight.core.data.datastore.UserSettings
import com.keisardev.insight.core.testing.fakes.FakeUserSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Integration tests for [FakeUserSettingsRepository].
 *
 * These tests verify that the fake repository implements the contract correctly
 * and properly stores and emits settings updates.
 */
class FakeUserSettingsRepositoryTest {

    private lateinit var repository: FakeUserSettingsRepository

    @Before
    fun setup() {
        repository = FakeUserSettingsRepository()
    }

    @Test
    fun observeSettings_emitsSettingsAsFlow() = runTest {
        repository.observeSettings().test {
            val settings = awaitItem()
            assertThat(settings).isNotNull()
            cancel()
        }
    }

    @Test
    fun observeSettings_initiallyDefaultSettings() = runTest {
        repository.observeSettings().test {
            val settings = awaitItem()
            assertThat(settings).isEqualTo(UserSettings())
            cancel()
        }
    }

    @Test
    fun updateAiMode_updatesSettings() = runTest {
        val newMode = UserSettings.AiModeProto.CLOUD_API

        repository.observeSettings().test {
            val initial = awaitItem()
            assertThat(initial.aiMode).isNotEqualTo(newMode)

            repository.updateAiMode(newMode)

            val updated = awaitItem()
            assertThat(updated.aiMode).isEqualTo(newMode)
            cancel()
        }
    }

    @Test
    fun updateCurrency_updatesSettings() = runTest {
        repository.observeSettings().test {
            val initial = awaitItem()

            repository.updateCurrency("EUR")

            val updated = awaitItem()
            assertThat(updated.currencyCode).isEqualTo("EUR")
            cancel()
        }
    }

    @Test
    fun updateActiveModel_updatesSettings() = runTest {
        repository.observeSettings().test {
            val initial = awaitItem()

            repository.updateActiveModel("model.gguf")

            val updated = awaitItem()
            assertThat(updated.activeModelFileName).isEqualTo("model.gguf")
            cancel()
        }
    }

    @Test
    fun updateCloudSettings_updatesSettings() = runTest {
        val newCloudSettings = UserSettings.CloudSettings(
            cloudProvider = UserSettings.CloudProvider.OPENAI,
            useDevKey = false,
        )

        repository.observeSettings().test {
            val initial = awaitItem()

            repository.updateCloudSettings(newCloudSettings)

            val updated = awaitItem()
            assertThat(updated.cloudSettings.cloudProvider).isEqualTo(newCloudSettings.cloudProvider)
            cancel()
        }
    }

    @Test
    fun updateUseDevKey_updatesCloudSettings() = runTest {
        repository.observeSettings().test {
            val initial = awaitItem()

            repository.updateUseDevKey(true)

            val updated = awaitItem()
            assertThat(updated.cloudSettings.useDevKey).isTrue()
            cancel()
        }
    }

    @Test
    fun multipleUpdates_accumulateCorrectly() = runTest {
        repository.observeSettings().test {
            awaitItem() // initial

            repository.updateCurrency("GBP")
            var current = awaitItem()
            assertThat(current.currencyCode).isEqualTo("GBP")

            repository.updateAiMode(UserSettings.AiModeProto.ON_DEVICE)
            current = awaitItem()
            assertThat(current.currencyCode).isEqualTo("GBP")
            assertThat(current.aiMode).isEqualTo(UserSettings.AiModeProto.ON_DEVICE)

            repository.updateActiveModel("new_model.gguf")
            current = awaitItem()
            assertThat(current.currencyCode).isEqualTo("GBP")
            assertThat(current.aiMode).isEqualTo(UserSettings.AiModeProto.ON_DEVICE)
            assertThat(current.activeModelFileName).isEqualTo("new_model.gguf")

            cancel()
        }
    }

    @Test
    fun setSettings_replacesAllSettings() = runTest {
        val customSettings = UserSettings(
            aiMode = UserSettings.AiModeProto.CLOUD_API,
            currencyCode = "JPY",
            activeModelFileName = "custom.gguf",
        )

        repository.observeSettings().test {
            awaitItem() // initial

            repository.setSettings(customSettings)

            val updated = awaitItem()
            assertThat(updated.aiMode).isEqualTo(customSettings.aiMode)
            assertThat(updated.currencyCode).isEqualTo(customSettings.currencyCode)
            assertThat(updated.activeModelFileName).isEqualTo(customSettings.activeModelFileName)
            cancel()
        }
    }

    @Test
    fun reset_restoresDefaultSettings() = runTest {
        repository.updateCurrency("CAD")
        repository.updateAiMode(UserSettings.AiModeProto.ON_DEVICE)

        repository.reset()

        repository.observeSettings().test {
            val settings = awaitItem()
            assertThat(settings).isEqualTo(UserSettings())
            cancel()
        }
    }
}
