import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(versionCatalog.findPlugin("kotlin-compose").get().get().pluginId)

            extensions.configure<LibraryExtension> {
                buildFeatures {
                    compose = true
                }
            }

            dependencies {
                val bom = versionCatalog.findLibrary("androidx-compose-bom").get()
                add("implementation", platform(bom))
                add("androidTestImplementation", platform(bom))

                // Material3 Expressive alpha — overrides BOM version for LoadingIndicator etc.
                add("implementation", versionCatalog.findLibrary("androidx-compose-material3-expressive").get())
            }
        }
    }
}
