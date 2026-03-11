import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply(versionCatalog.findPlugin("android-library").get().get().pluginId)
            }

            extensions.configure<LibraryExtension> {
                compileSdk = ProjectConfig.COMPILE_SDK
                defaultConfig {
                    minSdk = ProjectConfig.MIN_SDK
                }
            }
        }
    }
}
