plugins {
    alias(libs.plugins.insight.android.library)
    alias(libs.plugins.kotlin.parcelize)
}

android {
    namespace = "com.keisardev.insight.core.model"
}

dependencies {
    implementation(project(":core:common"))
    api(libs.kotlinx.datetime)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
