import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply(versionCatalog.findPlugin("insight-android-library").get().get().pluginId)
                apply(versionCatalog.findPlugin("insight-android-compose").get().get().pluginId)
                apply(versionCatalog.findPlugin("kotlin-parcelize").get().get().pluginId)
                apply(versionCatalog.findPlugin("metro").get().get().pluginId)
                apply(versionCatalog.findPlugin("ksp").get().get().pluginId)
            }

            extensions.configure<LibraryExtension> {
                defaultConfig {
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
            }

            // Configure KSP for Circuit
            afterEvaluate {
                extensions.findByName("ksp")?.let { ksp ->
                    (ksp as? com.google.devtools.ksp.gradle.KspExtension)?.apply {
                        arg("circuit.codegen.mode", "metro")
                    }
                }
            }

            dependencies {
                add("implementation", project(":core:model"))
                add("implementation", project(":core:data"))
                add("implementation", project(":core:designsystem"))
                add("implementation", project(":core:ui"))
                add("implementation", project(":core:common"))

                add("implementation", versionCatalog.findLibrary("circuit-foundation").get())
                add("implementation", versionCatalog.findLibrary("circuit-retained").get())
                add("implementation", versionCatalog.findLibrary("circuit-codegen-annotations").get())
                add("ksp", versionCatalog.findLibrary("circuit-codegen").get())
            }
        }
    }
}
