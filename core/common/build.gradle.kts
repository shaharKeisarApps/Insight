plugins {
    id("metroditest.android.library")
}

android {
    namespace = "com.keisardev.metroditest.core.common"
}

dependencies {
    api(libs.kotlinx.coroutines.android)
}
