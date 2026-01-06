plugins {
    id("metroditest.android.feature")
}

android {
    namespace = "com.keisardev.metroditest.feature.aichat"
}

dependencies {
    implementation(project(":core:ai"))
}
