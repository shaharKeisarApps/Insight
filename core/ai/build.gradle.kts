plugins {
    alias(libs.plugins.insight.android.library)
    alias(libs.plugins.metro)
}

android {
    namespace = "com.keisardev.insight.core.ai"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:database"))

    // Koog AI Agent Framework (cloud)
    implementation(libs.koog.agents)
    implementation(libs.koog.openai)
    implementation(libs.koog.google)

    // Llamatik (on-device LLM inference)
    implementation(libs.llamatik)

    // Ktor HTTP client (required by Koog)
    implementation(libs.ktor.client.okhttp)

    // Okio (filesystem abstraction)
    implementation(libs.okio)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // DateTime
    implementation(libs.kotlinx.datetime)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
