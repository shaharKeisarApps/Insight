plugins {
    alias(libs.plugins.insight.kmp.compose)
}

kotlin {
    android {
        namespace = "com.keisardev.insight.core.designsystem"
        compileSdk = ProjectConfig.COMPILE_SDK
        minSdk = ProjectConfig.MIN_SDK
    }

    sourceSets {
        commonMain.dependencies {
            api(compose.material3)
            api(compose.ui)
            api(compose.foundation)
        }
        androidMain.dependencies {
            api(libs.androidx.compose.ui.tooling.preview)
            api(libs.androidx.compose.ui.tooling)
            api(libs.androidx.compose.material3.expressive)
        }
    }
}
