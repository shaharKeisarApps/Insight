package com.keisardev.insight.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Benchmarks for measuring scroll (frame timing) performance on the expense list.
 *
 * Run with: ./gradlew :benchmark:connectedBenchmarkAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun expenseListScroll() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Full(),
            iterations = 5,
            startupMode = StartupMode.COLD,
        ) {
            pressHome()
            startActivityAndWait()

            // Wait for the expense list to be visible, then scroll
            val list = device.wait(
                Until.findObject(By.scrollable(true)),
                5_000L,
            )
            list?.run {
                setGestureMargin(device.displayWidth / 5)
                repeat(3) {
                    fling(Direction.DOWN)
                }
                repeat(3) {
                    fling(Direction.UP)
                }
            }
        }
    }
}
