---
name: gradle-plugin-expert
description: Expert guidance on writing Gradle Convention Plugins for KMP. Use for `build-logic` composite builds, convention plugin authoring, extension functions, plugin registration, and sharing build configuration across modules.
---

# Gradle Plugin Expert Skill (Gradle 8.x + Kotlin DSL)

## Overview

This skill focuses on **authoring** reusable Gradle build logic. Enterprise KMP apps scale by extracting shared configuration from individual `build.gradle.kts` files into convention plugins living in a `build-logic` included build. Convention plugins are **additive** and **composable** -- each handles a single responsibility, and modules pick and choose what they need.

## When to Use

- **build-logic Setup**: Creating the `build-logic/` composite build from scratch
- **Convention Plugins**: Writing `Plugin<Project>` implementations
- **Extension Functions**: Extracting `fun Project.configureX()` helpers
- **Plugin Registration**: `gradlePlugin { plugins { register(...) } }` DSL
- **Version Catalog Access**: Using `libs.versions.toml` from within plugins
- **Plugin Composition**: Applying plugins from within other plugins
- **Shared Defaults**: compileSdk, minSdk, JVM target, Compose, Metro, Circuit

## Quick Reference

For detailed API reference, see [reference.md](reference.md).
For production examples with full code, see [examples.md](examples.md).

## build-logic Directory Layout

```
build-logic/
  settings.gradle.kts          # Declares build-logic as a standalone build
  convention/
    build.gradle.kts            # Dependencies on Gradle plugin artifacts
    src/main/kotlin/
      AndroidLibraryConventionPlugin.kt
      KmpLibraryConventionPlugin.kt
      KmpFeatureImplConventionPlugin.kt
      ComposeConventionPlugin.kt
      AndroidApplicationConventionPlugin.kt
      KotlinMultiplatform.kt    # fun Project.configureKotlinMultiplatform()
      KotlinAndroid.kt          # fun Project.configureKotlinAndroid()
      ComposeMultiplatform.kt   # fun Project.configureComposeMultiplatform()
```

**Key insight:** The `build-logic/convention` module is a regular Gradle project that produces plugin classes. It depends on Gradle plugin artifacts (kotlin-gradle-plugin, android-gradle-plugin, compose-gradle-plugin) as `implementation` dependencies so it can configure them programmatically.

## Convention Plugin Anatomy

```kotlin
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.multiplatform")
            }
            configureKotlinMultiplatform()
        }
    }
}
```

**Rules:**
1. A plugin class implements `Plugin<Project>`.
2. `apply(target)` is the entry point -- apply other plugins and configure extensions.
3. Extract configuration into `Project` extension functions for reuse across plugins.
4. Keep each plugin focused on a single concern (additive + composable).

## Version Catalog Access from Plugins

Convention plugins cannot use `libs.xxx` accessor syntax. Use `VersionCatalogsExtension`:

```kotlin
internal val Project.libs: VersionCatalog
    get() = the<VersionCatalogsExtension>().named("libs")

// Usage in extension functions
fun Project.configureKotlinMultiplatform() {
    val kotlinVersion = libs.findVersion("kotlin").get().toString()
    // ...
}
```

## Plugin Composition

Plugins apply other plugins to compose behavior:

```kotlin
// KmpFeatureImplConventionPlugin applies 3 convention plugins + Metro + Circuit
class KmpFeatureImplConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("my.kmp.library")         // Our convention plugin
                apply("my.compose.multiplatform") // Our convention plugin
                apply("dev.zacsweers.metro")      // Third-party plugin
            }
            // Configure Circuit KSP
            dependencies {
                add("kspCommonMainMetadata", libs.findLibrary("circuit-codegen").get())
            }
        }
    }
}
```

## Plugin Registration

```kotlin
// build-logic/convention/build.gradle.kts
gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "my.kmp.library"
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("androidLibrary") {
            id = "my.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
    }
}
```

## Common Plugin Types for KMP

| Plugin ID | Responsibility |
|-----------|---------------|
| `my.kmp.library` | KMP targets (Android, iOS, JVM), shared source sets, Kotlin options |
| `my.android.library` | compileSdk, minSdk, Java compatibility, ProGuard defaults |
| `my.android.application` | Application ID, versionCode, signing, build types |
| `my.compose.multiplatform` | JB Compose plugin, Compose compiler options |
| `my.kmp.feature.api` | KMP library + serialization (Screen definitions) |
| `my.kmp.feature.impl` | KMP library + Compose + Metro + Circuit (Presenters/UI) |

## Core Rules

1. **Kotlin DSL only** -- All files use `.kts` extension.
2. **`build-logic` as included build** -- Declared via `includeBuild("build-logic")` in root `settings.gradle.kts`.
3. **Plugin IDs follow project convention** -- e.g., `my.project.kmp.library`.
4. **Version catalog access via `VersionCatalogsExtension`** -- Not `libs.xxx` accessor.
5. **One responsibility per plugin** -- Compose plugin does not configure Android SDK.
6. **Extension functions for shared logic** -- `fun Project.configureX()` in separate files.
7. **Plugin artifacts as `implementation` deps** -- `build-logic/convention/build.gradle.kts` depends on `kotlin-gradle-plugin`, `android-gradle-plugin`, etc.
8. **Never hardcode versions** -- Always read from `libs.versions.toml`.

## Common Pitfalls

1. **Circular plugin dependencies** -- Plugin A applies Plugin B which applies Plugin A. Use `pluginManager.withPlugin("id") { }` to guard against re-application.
2. **Missing plugin artifacts in build-logic** -- If your convention plugin calls `apply("com.android.library")`, you must have `implementation(libs.findLibrary("android-gradle-plugin").get())` in `build-logic/convention/build.gradle.kts`.
3. **Using `libs.xxx` in plugin code** -- This accessor syntax only works in `build.gradle.kts` files, not in `Plugin<Project>` classes. Use `the<VersionCatalogsExtension>()`.
4. **Wrong `java-gradle-plugin` setup** -- `build-logic/convention/build.gradle.kts` must apply `java-gradle-plugin` (or `kotlin-dsl`) for `gradlePlugin { }` DSL to be available.
5. **Forgetting `includeBuild`** -- Root `settings.gradle.kts` must have `includeBuild("build-logic")` for convention plugins to resolve.
6. **Applying plugins by version in convention plugins** -- Convention plugins apply plugins **without** versions since versions are resolved from the root project's version catalog. Use `apply("plugin.id")`, not `id("plugin.id") version "x.y.z"`.
7. **Configuring extensions before plugin is applied** -- Always `apply(pluginId)` first, then `extensions.configure<ExtType> { }`.
8. **Missing `dependencyResolutionManagement` for build-logic** -- `build-logic/settings.gradle.kts` must configure `versionCatalogs` to reference the root project's `libs.versions.toml`.
