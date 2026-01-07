plugins {
    id("insight.android.feature")
    alias(libs.plugins.screenshot)
}

android {
    namespace = "com.keisardev.insight.feature.expenses"

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    experimentalProperties["android.experimental.enableScreenshotTest"] = true
}

dependencies {
    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.circuit.test)
    testImplementation(libs.robolectric)

    // Screenshot tests
    screenshotTestImplementation(libs.screenshot.validation.api)
    screenshotTestImplementation(libs.androidx.compose.ui.tooling)
}
