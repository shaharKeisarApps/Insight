plugins {
    alias(libs.plugins.insight.android.library)
    alias(libs.plugins.insight.android.compose)
}

android {
    namespace = "com.keisardev.insight.core.ui"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    api(project(":core:designsystem"))

    api(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.datetime)
}
