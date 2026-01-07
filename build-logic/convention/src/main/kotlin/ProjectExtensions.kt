import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/**
 * Provides programmatic access to the version catalog in convention plugins.
 * Use this when you need to call findLibrary(), findPlugin(), etc.
 *
 * Note: Named `versionCatalog` instead of `libs` to avoid shadowing
 * the type-safe accessors used in module build.gradle.kts files.
 *
 * Usage:
 * ```kotlin
 * class MyConventionPlugin : Plugin<Project> {
 *     override fun apply(target: Project) {
 *         with(target) {
 *             val myLib = versionCatalog.findLibrary("my-library").get()
 *             val myPlugin = versionCatalog.findPlugin("my-plugin").get().get().pluginId
 *         }
 *     }
 * }
 * ```
 */
internal val Project.versionCatalog: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")
