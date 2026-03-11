import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class KmpFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply(versionCatalog.findPlugin("insight-kmp-compose").get().get().pluginId)
                apply("org.jetbrains.kotlin.plugin.parcelize")
                apply("dev.zacsweers.metro")
                apply("com.google.devtools.ksp")
            }

            val compose = extensions.getByType(ComposeExtension::class.java).dependencies

            extensions.configure<KotlinMultiplatformExtension> {
                sourceSets.apply {
                    commonMain.dependencies {
                        implementation(project(":core:model"))
                        implementation(project(":core:data"))
                        implementation(project(":core:designsystem"))
                        implementation(project(":core:ui"))
                        implementation(project(":core:common"))

                        implementation(versionCatalog.findLibrary("circuit-foundation").get())
                        implementation(versionCatalog.findLibrary("circuit-retained").get())
                        implementation(versionCatalog.findLibrary("circuit-codegen-annotations").get())

                        implementation(versionCatalog.findLibrary("kotlinx-coroutines-core").get())
                        implementation(versionCatalog.findLibrary("kotlinx-datetime").get())

                        implementation(compose.components.resources)
                        implementation(compose.components.uiToolingPreview)
                    }

                    androidMain.dependencies {
                        implementation(versionCatalog.findLibrary("kotlinx-coroutines-android").get())
                    }
                }
            }

            extensions.configure<LibraryExtension> {
                defaultConfig {
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
            }

            // Configure KSP for Circuit with Metro mode
            afterEvaluate {
                extensions.findByName("ksp")?.let { ksp ->
                    (ksp as? com.google.devtools.ksp.gradle.KspExtension)?.apply {
                        arg("circuit.codegen.mode", "metro")
                    }
                }
            }

            // KSP for all targets
            dependencies {
                add("kspCommonMainMetadata", versionCatalog.findLibrary("circuit-codegen").get())
                add("kspAndroid", versionCatalog.findLibrary("circuit-codegen").get())
                add("kspIosX64", versionCatalog.findLibrary("circuit-codegen").get())
                add("kspIosArm64", versionCatalog.findLibrary("circuit-codegen").get())
                add("kspIosSimulatorArm64", versionCatalog.findLibrary("circuit-codegen").get())
            }
        }
    }
}
