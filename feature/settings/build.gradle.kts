plugins {
    alias(libs.plugins.insight.kmp.feature)
    alias(libs.plugins.screenshot)
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.keisardev.insight.feature.settings.generated.resources"
    generateResClass = always
}

android {
    namespace = "com.keisardev.insight.feature.settings"

    experimentalProperties["android.experimental.enableScreenshotTest"] = true
}

kotlin {
    sourceSets {
        androidMain.dependencies {
            implementation(project(":core:ai"))
        }
    }
}

dependencies {
    screenshotTestImplementation(libs.screenshot.validation.api)
    screenshotTestImplementation(libs.androidx.compose.ui.tooling)
}
