plugins {
    alias(libs.plugins.insight.android.library)
}

android {
    namespace = "com.keisardev.insight.core.testing"
}

dependencies {
    api(project(":core:data"))
    api(project(":core:model"))
    api(project(":core:ai"))
    api(libs.kotlinx.coroutines.test)
    api(libs.circuit.test)
    api(libs.truth)
    api(libs.turbine)
}
