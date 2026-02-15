plugins {
    alias(libs.plugins.insight.android.feature)
}

android {
    namespace = "com.keisardev.insight.feature.settings"
}

dependencies {
    implementation(project(":core:ai"))
}
