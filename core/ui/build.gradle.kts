plugins {
    alias(libs.plugins.insight.kmp.compose)
}

android {
    namespace = "com.keisardev.insight.core.ui"
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.keisardev.insight.core.ui.generated.resources"
    generateResClass = always
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:common"))
            implementation(project(":core:model"))
            api(project(":core:designsystem"))

            api(compose.material3)
            api(compose.materialIconsExtended)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.datetime)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.compose.material3.expressive)
        }
    }
}
