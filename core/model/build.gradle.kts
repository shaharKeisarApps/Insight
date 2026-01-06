plugins {
    id("insight.android.library")
    alias(libs.plugins.kotlin.parcelize)
}

android {
    namespace = "com.keisardev.insight.core.model"
}

dependencies {
    implementation(project(":core:common"))
    api(libs.kotlinx.datetime)
}
