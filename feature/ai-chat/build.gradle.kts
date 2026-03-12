plugins {
    alias(libs.plugins.insight.kmp.feature)
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.keisardev.insight.feature.aichat.generated.resources"
    generateResClass = always
}

kotlin {
    android {
        namespace = "com.keisardev.insight.feature.aichat"
        compileSdk = ProjectConfig.COMPILE_SDK
        minSdk = ProjectConfig.MIN_SDK
        withHostTestBuilder {}.configure {}
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:ai"))
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
            implementation(libs.truth)
            implementation(libs.turbine)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.circuit.test)
            implementation(libs.robolectric)
        }
    }
}
