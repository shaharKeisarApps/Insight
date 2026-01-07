plugins {
    alias(libs.plugins.insight.android.feature)
}

android {
    namespace = "com.keisardev.insight.feature.aichat"
}

dependencies {
    implementation(project(":core:ai"))
}
