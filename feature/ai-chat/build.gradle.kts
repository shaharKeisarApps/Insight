plugins {
    id("insight.android.feature")
}

android {
    namespace = "com.keisardev.insight.feature.aichat"
}

dependencies {
    implementation(project(":core:ai"))
}
