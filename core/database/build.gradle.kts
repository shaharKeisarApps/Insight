plugins {
    alias(libs.plugins.insight.kmp.library)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.metro)
}

sqldelight {
    databases {
        create("ExpenseDatabase") {
            packageName.set("com.keisardev.insight.core.database")
        }
    }
}

kotlin {
    android {
        namespace = "com.keisardev.insight.core.database"
        compileSdk = ProjectConfig.COMPILE_SDK
        minSdk = ProjectConfig.MIN_SDK
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:common"))
            implementation(project(":core:model"))
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.kotlinx.datetime)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
    }
}
