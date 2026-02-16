plugins {
    alias(libs.plugins.insight.android.feature)
    alias(libs.plugins.screenshot)
}

android {
    namespace = "com.keisardev.insight.feature.settings"

    experimentalProperties["android.experimental.enableScreenshotTest"] = true
}

dependencies {
    implementation(project(":core:ai"))

    screenshotTestImplementation(libs.screenshot.validation.api)
    screenshotTestImplementation(libs.androidx.compose.ui.tooling)
}
