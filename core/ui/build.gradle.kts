plugins {
    id("insight.android.library")
    id("insight.android.compose")
}

android {
    namespace = "com.keisardev.insight.core.ui"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    api(project(":core:designsystem"))

    api(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlinx.datetime)
}
