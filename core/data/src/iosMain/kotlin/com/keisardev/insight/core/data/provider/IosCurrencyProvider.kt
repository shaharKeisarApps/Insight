package com.keisardev.insight.core.data.provider

import com.keisardev.insight.core.common.CurrencyProvider
import com.keisardev.insight.core.common.di.AppScope
import com.keisardev.insight.core.data.datastore.UserSettingsRepository
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.first
import platform.Foundation.NSLocale
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterCurrencyStyle
import platform.Foundation.currentLocale

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class IosCurrencyProvider(
    private val userSettingsRepository: UserSettingsRepository,
) : CurrencyProvider {

    override suspend fun getCurrencySymbol(): String {
        return try {
            val settings = userSettingsRepository.observeSettings().first()
            val code = settings.currencyCode
            if (code == "DEVICE") {
                val formatter = NSNumberFormatter()
                formatter.numberStyle = NSNumberFormatterCurrencyStyle
                formatter.locale = NSLocale.currentLocale
                formatter.currencySymbol
            } else {
                val formatter = NSNumberFormatter()
                formatter.numberStyle = NSNumberFormatterCurrencyStyle
                formatter.currencyCode = code
                formatter.currencySymbol
            }
        } catch (_: Exception) {
            "$"
        }
    }
}
