plugins {
    id("metroditest.android.library")
    id("metroditest.android.compose")
}

android {
    namespace = "com.keisardev.metroditest.core.ui"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    api(project(":core:designsystem"))

    api(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlinx.datetime)
}
