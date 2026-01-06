plugins {
    id("insight.android.library")
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.metro)
}

android {
    namespace = "com.keisardev.insight.core.database"
}

sqldelight {
    databases {
        create("ExpenseDatabase") {
            packageName.set("com.keisardev.insight.core.database")
            srcDirs.setFrom("src/main/sqldelight")
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(libs.sqldelight.android.driver)
    implementation(libs.sqldelight.coroutines)
    implementation(libs.kotlinx.datetime)
}
