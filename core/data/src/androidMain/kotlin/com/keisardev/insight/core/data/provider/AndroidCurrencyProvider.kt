package com.keisardev.insight.core.data.provider

import com.keisardev.insight.core.common.CurrencyProvider
import com.keisardev.insight.core.common.di.AppScope
import com.keisardev.insight.core.data.datastore.UserSettingsRepository
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.util.Currency
import java.util.Locale
import kotlinx.coroutines.flow.first

/**
 * Android-specific implementation of [CurrencyProvider] using Java's Currency and Locale APIs.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class AndroidCurrencyProvider(
    private val userSettingsRepository: UserSettingsRepository,
) : CurrencyProvider {

    override suspend fun getCurrencySymbol(): String {
        return try {
            val settings = userSettingsRepository.observeSettings().first()
            val code = settings.currencyCode
            if (code == "DEVICE") {
                Currency.getInstance(Locale.getDefault()).symbol
            } else {
                Currency.getInstance(code).symbol
            }
        } catch (_: Exception) {
            "$"
        }
    }
}
