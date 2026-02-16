package com.keisardev.insight.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates baseline profiles for the app's critical user journeys.
 *
 * Run with: ./gradlew :benchmark:connectedBenchmarkAndroidTest -P android.testInstrumentationRunnerArguments.class=com.keisardev.insight.benchmark.BaselineProfileGenerator
 *
 * The generated profile should be copied to app/src/main/baselineProfiles/
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generateStartupProfile() {
        rule.collect(
            packageName = TARGET_PACKAGE,
            maxIterations = 10,
            stableIterations = 3,
            includeInStartupProfile = true,
        ) {
            // App startup journey
            pressHome()
            startActivityAndWait()
        }
    }

    @Test
    fun generateCriticalJourneysProfile() {
        rule.collect(
            packageName = TARGET_PACKAGE,
            maxIterations = 10,
            stableIterations = 3,
        ) {
            pressHome()
            startActivityAndWait()

            // Navigate through main tabs
            device.wait(Until.findObject(By.text("Expenses")), 5_000L)?.click()
            device.waitForIdle()

            device.wait(Until.findObject(By.text("Income")), 5_000L)?.click()
            device.waitForIdle()

            device.wait(Until.findObject(By.text("Reports")), 5_000L)?.click()
            device.waitForIdle()

            device.wait(Until.findObject(By.text("AI Chat")), 5_000L)?.click()
            device.waitForIdle()

            // Scroll expense list
            device.wait(Until.findObject(By.text("Expenses")), 5_000L)?.click()
            device.waitForIdle()

            val list = device.wait(Until.findObject(By.scrollable(true)), 3_000L)
            list?.fling(androidx.test.uiautomator.Direction.DOWN)
        }
    }
}
