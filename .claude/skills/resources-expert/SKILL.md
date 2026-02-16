---
name: resources-expert
description: Expert guidance on Compose Multiplatform Resources for KMP. Use for strings, images, fonts, raw files, plurals, localization, and theme-qualified resources across all targets.
---

# Compose Multiplatform Resources Expert Skill

## Overview

Compose Multiplatform Resources is the **official JetBrains resource system** for Kotlin Multiplatform projects. It replaces third-party solutions like moko-resources (now deprecated in favor of this). Introduced in Compose Multiplatform 1.6.0, refined in 1.7.0, and **stable since 1.10.0**, it provides compile-time-safe access to strings, images, fonts, raw files, and XML values across Android, iOS, Desktop, and Web targets.

The system generates a type-safe `Res` class during the build, so all resource references are checked at compile time. Resources are placed in `composeResources/` directories within source sets, and the Gradle plugin handles packaging for each platform automatically (Android assets, JVM JARs, iOS framework bundles, Web static files).

## When to Use

- **Localized strings**: Multi-language string resources with format arguments.
- **Plural strings**: Quantity-aware string formatting (`zero`, `one`, `two`, `few`, `many`, `other`).
- **Drawable images**: PNG, JPG, WebP, SVG, and XML vector drawables.
- **Custom fonts**: TTF and OTF font files loaded as Compose `Font` objects.
- **Raw files**: Arbitrary files (JSON configs, data files) read as `ByteArray`.
- **Themed resources**: Dark/light mode specific drawables or color values.
- **Density-qualified drawables**: MDPI, HDPI, XHDPI, XXHDPI, XXXHDPI variants.

## Resource Directory Structure

Resources live under `src/<sourceSet>/composeResources/`:

```
src/commonMain/composeResources/
    drawable/                  # Images: PNG, JPG, SVG, WebP, XML vector
    drawable-dark/             # Dark-theme images
    drawable-xxhdpi/           # Density-qualified images
    font/                      # TTF, OTF font files
    values/                    # strings.xml, plurals.xml
    values-fr/                 # French strings
    values-es/                 # Spanish strings
    values-de-rAT/             # German (Austria) strings
    files/                     # Raw files (no qualifier support)
```

## Generated Res Class

The build generates a `Res` object with nested accessors:

- `Res.string.app_name` -- `StringResource`
- `Res.plurals.items_count` -- `PluralStringResource`
- `Res.drawable.icon` -- `DrawableResource`
- `Res.font.roboto_regular` -- `FontResource`

## Key Composable APIs

| Function | Returns | Use Case |
|----------|---------|----------|
| `stringResource(Res.string.key)` | `String` | Display a string |
| `stringResource(Res.string.key, args...)` | `String` | Formatted string |
| `pluralStringResource(Res.plurals.key, qty, args...)` | `String` | Quantity-aware string |
| `painterResource(Res.drawable.icon)` | `Painter` | Any image (auto-detects format) |
| `imageResource(Res.drawable.photo)` | `ImageBitmap` | Raster images only |
| `vectorResource(Res.drawable.ic_arrow)` | `ImageVector` | XML vector drawables only |
| `Font(Res.font.roboto)` | `Font` | Load a font |

## Non-Composable (Suspend) APIs

| Function | Returns | Use Case |
|----------|---------|----------|
| `getString(Res.string.key)` | `String` | String outside Composable |
| `getStringArray(Res.string_array.key)` | `List<String>` | String array outside Composable |
| `readResourceBytes("files/config.json")` | `ByteArray` | Raw file reading |

## Qualifiers

| Qualifier | Example Directory | Description |
|-----------|-------------------|-------------|
| Language | `values-fr/` | French strings |
| Region | `values-pt-rBR/` | Portuguese (Brazil) |
| Theme | `drawable-dark/` | Dark mode drawables |
| Density | `drawable-xxhdpi/` | Screen density variants |

Qualifier order in directory names: `<type>-<language>-<region>-<theme>-<density>`.

## Gradle Configuration

```kotlin
compose.resources {
    publicResClass = true                          // Make Res class public (default: internal)
    packageOfResClass = "com.myapp.resources"      // Custom package for generated Res
    nameOfResClass = "Res"                         // Custom class name (default: "Res")
    generateResClass = auto                        // auto | always | never
}
```

## Metro DI Integration

Resources do **not** typically require DI. The generated `Res` object is a static accessor. If you need to inject resource-reading capability for testability, wrap it in an interface and provide via Metro, but this is uncommon.

## Core Rules

1. **File names must be valid Kotlin identifiers** -- use lowercase_snake_case (e.g., `my_icon.png`, not `my-icon.png`).
2. **Never mix resource types in one directory** -- `drawable/` is only for images, `values/` only for XML.
3. **SVG support is cross-platform** -- prefer SVG for icons; use `painterResource` which auto-detects the format.
4. **`files/` directory does not support qualifiers** -- no `files-fr/`, only `files/`.
5. **XML values files must use specific root elements** -- `<resources>` with `<string>`, `<plurals>`, `<string-array>`.
6. **Qualifier directories must follow the exact naming convention** -- `values-fr`, not `values_fr` or `values/fr`.
7. **Resource names derive from file names** -- `my_icon.png` becomes `Res.drawable.my_icon`.
8. **Always import from `org.jetbrains.compose.resources`** -- not from `androidx.compose.ui.res` (that is Desktop/Android only).

## Common Pitfalls

- Using `androidx.compose.ui.res.painterResource` instead of `org.jetbrains.compose.resources.painterResource` -- the former is platform-specific and will not work cross-platform.
- Forgetting to add the Compose Resources Gradle plugin -- it is included with `org.jetbrains.compose` but must be applied.
- Placing resources in `resources/` instead of `composeResources/` -- the old path was deprecated in 1.6.0.
- Using hyphens in file names (`my-icon.png`) -- this will fail code generation since hyphens are not valid in Kotlin identifiers.
- Calling `stringResource` outside a `@Composable` function -- use the suspend `getString` instead.
