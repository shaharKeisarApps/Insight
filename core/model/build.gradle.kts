plugins {
    alias(libs.plugins.insight.kmp.library)
}

android {
    namespace = "com.keisardev.insight.core.model"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:common"))
            api(libs.kotlinx.datetime)
        }
    }
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
