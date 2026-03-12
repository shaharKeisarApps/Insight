plugins {
    alias(libs.plugins.insight.kmp.feature)
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.keisardev.insight.feature.settings.generated.resources"
    generateResClass = always
}

kotlin {
    android {
        namespace = "com.keisardev.insight.feature.settings"
        compileSdk = ProjectConfig.COMPILE_SDK
        minSdk = ProjectConfig.MIN_SDK
        androidResources.enable = true
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:ai"))
        }
    }
}
