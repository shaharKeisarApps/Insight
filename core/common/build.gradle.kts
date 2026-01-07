plugins {
    alias(libs.plugins.insight.android.library)
}

android {
    namespace = "com.keisardev.insight.core.common"
}

dependencies {
    api(libs.kotlinx.coroutines.android)
}
