plugins {
    id("metroditest.android.library")
    id("metroditest.android.compose")
}

android {
    namespace = "com.keisardev.metroditest.core.designsystem"
}

dependencies {
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.tooling.preview)
    debugApi(libs.androidx.compose.ui.tooling)
}
