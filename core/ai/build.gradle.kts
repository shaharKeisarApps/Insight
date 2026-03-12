plugins {
    alias(libs.plugins.insight.kmp.library)
    alias(libs.plugins.metro)
}

kotlin {
    android {
        namespace = "com.keisardev.insight.core.ai"
        compileSdk = ProjectConfig.COMPILE_SDK
        minSdk = ProjectConfig.MIN_SDK
        withHostTestBuilder {}.configure {}
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:common"))
            implementation(project(":core:model"))
            implementation(project(":core:data"))
            implementation(project(":core:database"))

            // Llamatik (on-device LLM inference)
            implementation(libs.llamatik)

            // Ktor HTTP client core
            implementation(libs.ktor.client.core)

            // Okio (filesystem abstraction)
            implementation(libs.okio)

            // Coroutines
            implementation(libs.kotlinx.coroutines.core)

            // DateTime
            implementation(libs.kotlinx.datetime)

            // Serialization (for JSON parsing)
            implementation(libs.kotlinx.serialization.json)

            // AtomicFU
            implementation(libs.kotlinx.atomicfu)
        }

        androidMain.dependencies {
            // Koog AI Agent Framework (cloud) — JVM-only due to reflection-based tools
            implementation(libs.koog.agents)
            implementation(libs.koog.openai)
            implementation(libs.koog.google)

            implementation(libs.ktor.client.okhttp)
            implementation(libs.kotlinx.coroutines.android)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }

        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
            implementation(libs.truth)
        }
    }
}
