plugins {
    alias(libs.plugins.insight.kmp.library)
    alias(libs.plugins.kotlin.parcelize)
}

android {
    namespace = "com.keisardev.insight.core.common"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            api(libs.kotlinx.coroutines.android)
        }
    }
}
