plugins {
    alias(libs.plugins.insight.kmp.feature)
    alias(libs.plugins.screenshot)
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.keisardev.insight.feature.aichat.generated.resources"
    generateResClass = always
}

android {
    namespace = "com.keisardev.insight.feature.aichat"

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

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
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.circuit.test)
    testImplementation(libs.robolectric)

    screenshotTestImplementation(libs.screenshot.validation.api)
    screenshotTestImplementation(libs.androidx.compose.ui.tooling)
}
