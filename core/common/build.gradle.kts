plugins {
    id("insight.android.library")
}

android {
    namespace = "com.keisardev.insight.core.common"
}

dependencies {
    api(libs.kotlinx.coroutines.android)
}
