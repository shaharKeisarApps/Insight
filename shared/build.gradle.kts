plugins {
    alias(libs.plugins.insight.kmp.compose)
    alias(libs.plugins.metro)
    alias(libs.plugins.ksp)
}

kotlin {
    android {
        namespace = "com.keisardev.insight.shared"
        compileSdk = ProjectConfig.COMPILE_SDK
        minSdk = ProjectConfig.MIN_SDK
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "shared"
            isStatic = true
            // Export modules that iOS needs to access directly
            export(project(":core:common"))
            export(project(":core:model"))
            export(project(":core:designsystem"))
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:common"))
            api(project(":core:model"))
            api(project(":core:database"))
            api(project(":core:data"))
            api(project(":core:designsystem"))
            api(project(":core:ui"))
            implementation(project(":feature:expenses"))
            implementation(project(":feature:income"))
            implementation(project(":feature:reports"))
            implementation(project(":feature:settings"))
            implementation(project(":feature:ai-chat"))

            implementation(libs.circuit.foundation)
            implementation(libs.circuit.retained)
            implementation(libs.circuit.codegen.annotations)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

ksp {
    arg("circuit.codegen.mode", "metro")
}

dependencies {
    add("kspIosX64", libs.circuit.codegen)
    add("kspIosArm64", libs.circuit.codegen)
    add("kspIosSimulatorArm64", libs.circuit.codegen)
}

