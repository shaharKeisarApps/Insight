plugins {
    id("insight.android.library")
    id("insight.android.compose")
}

android {
    namespace = "com.keisardev.insight.core.designsystem"
}

dependencies {
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.tooling.preview)
    debugApi(libs.androidx.compose.ui.tooling)
}
