plugins {
    alias(libs.plugins.insight.kmp.library)
}

kotlin {
    android {
        namespace = "com.keisardev.insight.core.model"
        compileSdk = ProjectConfig.COMPILE_SDK
        minSdk = ProjectConfig.MIN_SDK
        withHostTestBuilder {}.configure {}
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:common"))
            api(libs.kotlinx.datetime)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
            implementation(libs.truth)
        }
    }
}
