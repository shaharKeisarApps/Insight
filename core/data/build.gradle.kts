plugins {
    alias(libs.plugins.insight.kmp.library)
    alias(libs.plugins.metro)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.keisardev.insight.core.data"
        compileSdk = ProjectConfig.COMPILE_SDK
        minSdk = ProjectConfig.MIN_SDK
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:common"))
            implementation(project(":core:model"))
            implementation(project(":core:database"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.sqldelight.coroutines)
            api(libs.androidx.datastore)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.okio)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
        }
    }
}
