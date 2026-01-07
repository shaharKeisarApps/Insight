import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * Centralized project configuration for build settings.
 * Change values here to update across all modules.
 */
object ProjectConfig {
    /**
     * Java version used for source and target compatibility.
     * This affects both Java compilation and Kotlin JVM target.
     */
    const val JAVA_VERSION_INT = 21
    val JAVA_VERSION: JavaVersion = JavaVersion.VERSION_21
    val JVM_TARGET: JvmTarget = JvmTarget.JVM_21

    /**
     * Android SDK versions.
     */
    const val COMPILE_SDK = 36
    const val TARGET_SDK = 36
    const val MIN_SDK = 33
}
