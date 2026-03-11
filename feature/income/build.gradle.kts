plugins {
    alias(libs.plugins.insight.kmp.feature)
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.keisardev.insight.feature.income.generated.resources"
    generateResClass = always
}

kotlin {
    android {
        namespace = "com.keisardev.insight.feature.income"
        compileSdk = ProjectConfig.COMPILE_SDK
        minSdk = ProjectConfig.MIN_SDK
        withHostTestBuilder {}.configure {}
    }

    sourceSets {
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
