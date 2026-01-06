plugins {
    id("insight.android.library")
    alias(libs.plugins.metro)
}

android {
    namespace = "com.keisardev.insight.core.ai"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:data"))

    // Koog AI Agent Framework
    implementation(libs.koog.agents)
    implementation(libs.koog.openai)

    // Ktor HTTP client (required by Koog)
    implementation(libs.ktor.client.okhttp)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // DateTime
    implementation(libs.kotlinx.datetime)
}
