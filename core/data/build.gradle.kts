plugins {
    alias(libs.plugins.insight.android.library)
    alias(libs.plugins.metro)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.keisardev.insight.core.data"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.sqldelight.coroutines)
    api(libs.androidx.datastore)
    implementation(libs.kotlinx.serialization.json)
}
