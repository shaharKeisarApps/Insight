plugins {
    alias(libs.plugins.insight.kmp.compose)
}

android {
    namespace = "com.keisardev.insight.core.designsystem"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(compose.material3)
            api(compose.ui)
            api(compose.foundation)
        }
        androidMain.dependencies {
            api(libs.androidx.compose.ui.tooling.preview)
        }
    }
}

dependencies {
    debugApi(libs.androidx.compose.ui.tooling)
}
