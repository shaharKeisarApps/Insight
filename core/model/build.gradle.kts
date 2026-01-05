plugins {
    id("metroditest.android.library")
    alias(libs.plugins.kotlin.parcelize)
}

android {
    namespace = "com.keisardev.metroditest.core.model"
}

dependencies {
    implementation(project(":core:common"))
    api(libs.kotlinx.datetime)
}
