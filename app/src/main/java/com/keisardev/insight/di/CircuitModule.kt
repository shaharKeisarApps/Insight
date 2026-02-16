package com.keisardev.insight.di

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.keisardev.insight.core.common.di.AppScope
import com.slack.circuit.foundation.Circuit
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.ui.Ui
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

@ContributesTo(AppScope::class)
interface CircuitModule {
    companion object {
        @Provides
        fun provideCircuit(
            presenterFactories: Set<Presenter.Factory>,
            uiFactories: Set<Ui.Factory>,
        ): Circuit = Circuit.Builder()
            .addPresenterFactories(presenterFactories)
            .addUiFactories(uiFactories)
            .setOnUnavailableContent { screen, modifier ->
                Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Route not available: ${screen::class.simpleName}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            .build()
    }
}
