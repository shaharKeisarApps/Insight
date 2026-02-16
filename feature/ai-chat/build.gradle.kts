plugins {
    alias(libs.plugins.insight.android.feature)
}

android {
    namespace = "com.keisardev.insight.feature.aichat"

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(project(":core:ai"))

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.circuit.test)
    testImplementation(libs.robolectric)
}
