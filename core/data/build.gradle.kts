plugins {
    id("metroditest.android.library")
    alias(libs.plugins.metro)
}

android {
    namespace = "com.keisardev.metroditest.core.data"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.sqldelight.coroutines)
}
