plugins {
    alias(libs.plugins.insight.kmp.library)
    alias(libs.plugins.kotlin.parcelize)
}

kotlin {
    android {
        namespace = "com.keisardev.insight.core.common"
        compileSdk = ProjectConfig.COMPILE_SDK
        minSdk = ProjectConfig.MIN_SDK
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            api(libs.kotlinx.coroutines.android)
        }
    }
}
