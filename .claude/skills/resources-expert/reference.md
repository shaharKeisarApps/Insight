# Compose Multiplatform Resources Reference

## Gradle Dependencies

The resources system is included with the Compose Multiplatform Gradle plugin. No additional dependency is needed beyond the standard Compose setup:

```kotlin
// build.gradle.kts (module level)
plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.components.resources) // Resource library
        }
    }
}
```

## Gradle Configuration Block

```kotlin
compose.resources {
    publicResClass = true                          // Default: internal
    packageOfResClass = "com.myapp.resources"      // Default: derived from module
    nameOfResClass = "Res"                         // Default: "Res"
    generateResClass = auto                        // auto | always | never
}
```

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `publicResClass` | `Boolean` | `false` | When `true`, the generated `Res` class has `public` visibility. Required when sharing resources across modules. |
| `packageOfResClass` | `String` | Module-derived | The package name for the generated `Res` class. |
| `nameOfResClass` | `String` | `"Res"` | The name of the generated resource class. |
| `generateResClass` | `auto/always/never` | `auto` | `auto` generates only when resources exist. `always` always generates. `never` disables generation. |

## Custom Resource Directory

You can add additional resource directories programmatically:

```kotlin
kotlin {
    sourceSets {
        commonMain {
            compose.resources.customDirectory(
                sourceSetName = "commonMain",
                directoryPath = provider { layout.projectDirectory.dir("extraResources") }
            )
        }
    }
}
```

---

## Generated Res Object

The build generates a top-level object with nested types:

```kotlin
// Auto-generated -- do not edit
object Res {
    object string {
        val app_name: StringResource
        val greeting: StringResource
        val welcome_message: StringResource
    }
    object plurals {
        val items_count: PluralStringResource
    }
    object drawable {
        val icon: DrawableResource
        val background: DrawableResource
        val ic_arrow_back: DrawableResource
    }
    object font {
        val roboto_regular: FontResource
        val roboto_bold: FontResource
    }
}
```

### Resource Types

| Type | Accessor | Source Directory |
|------|----------|-----------------|
| `StringResource` | `Res.string.*` | `values/strings.xml` |
| `PluralStringResource` | `Res.plurals.*` | `values/plurals.xml` |
| `DrawableResource` | `Res.drawable.*` | `drawable/` |
| `FontResource` | `Res.font.*` | `font/` |

---

## Composable APIs

### stringResource

Retrieves a localized string from `values/strings.xml`.

```kotlin
@Composable
fun stringResource(resource: StringResource): String

@Composable
fun stringResource(resource: StringResource, vararg formatArgs: Any): String
```

**Usage:**

```kotlin
// Simple string
Text(text = stringResource(Res.string.app_name))

// Formatted string (strings.xml: <string name="greeting">Hello, %1$s! You have %2$d messages.</string>)
Text(text = stringResource(Res.string.greeting, userName, messageCount))
```

### pluralStringResource

Retrieves a quantity-aware plural string from `values/plurals.xml`.

```kotlin
@Composable
fun pluralStringResource(
    resource: PluralStringResource,
    quantity: Int
): String

@Composable
fun pluralStringResource(
    resource: PluralStringResource,
    quantity: Int,
    vararg formatArgs: Any
): String
```

**Usage:**

```kotlin
// plurals.xml: <plurals name="items_count">
//   <item quantity="one">%d item</item>
//   <item quantity="other">%d items</item>
// </plurals>
Text(text = pluralStringResource(Res.plurals.items_count, count, count))
```

**Quantity values:** `zero`, `one`, `two`, `few`, `many`, `other`. Which quantities are used depends on the locale's plural rules (CLDR).

### painterResource

Loads a `Painter` from a drawable resource. Automatically detects the image format (XML vector, SVG, or raster bitmap).

```kotlin
@Composable
fun painterResource(resource: DrawableResource): Painter
```

**Usage:**

```kotlin
Image(
    painter = painterResource(Res.drawable.icon),
    contentDescription = "App icon"
)
```

**Format detection:**
- `.xml` -> Returns `ImageVector` via `rememberVectorPainter`
- `.svg` -> Returns SVG painter
- Everything else (PNG, JPG, WebP) -> Returns `BitmapPainter`

### imageResource

Loads an `ImageBitmap` from a raster drawable resource. Use this when you specifically need a bitmap (not a vector).

```kotlin
@Composable
fun imageResource(resource: DrawableResource): ImageBitmap
```

**Usage:**

```kotlin
val bitmap: ImageBitmap = imageResource(Res.drawable.photo)
Image(bitmap = bitmap, contentDescription = "Photo")
```

### vectorResource

Loads an `ImageVector` from an XML vector drawable resource.

```kotlin
@Composable
fun vectorResource(resource: DrawableResource): ImageVector
```

**Usage:**

```kotlin
val vector: ImageVector = vectorResource(Res.drawable.ic_arrow_back)
Icon(imageVector = vector, contentDescription = "Back")
```

### Font

Creates a Compose `Font` from a font resource file.

```kotlin
fun Font(
    resource: FontResource,
    weight: FontWeight = FontWeight.Normal,
    style: FontStyle = FontStyle.Normal
): Font
```

**Usage:**

```kotlin
val customFontFamily = FontFamily(
    Font(Res.font.roboto_regular, FontWeight.Normal),
    Font(Res.font.roboto_bold, FontWeight.Bold),
    Font(Res.font.roboto_italic, FontWeight.Normal, FontStyle.Italic)
)

Text(
    text = "Styled text",
    fontFamily = customFontFamily
)
```

---

## Non-Composable (Suspend) APIs

These can be used outside `@Composable` functions (e.g., in ViewModels, repositories).

### getString

```kotlin
suspend fun getString(resource: StringResource): String
suspend fun getString(resource: StringResource, vararg formatArgs: Any): String
```

**Usage:**

```kotlin
class GreetingRepository {
    suspend fun getWelcome(name: String): String {
        return getString(Res.string.greeting, name)
    }
}
```

### getStringArray

```kotlin
suspend fun getStringArray(resource: StringArrayResource): List<String>
```

### readResourceBytes

Reads raw bytes from a file in the `files/` directory. The path is relative to `composeResources/`.

```kotlin
@InternalResourceApi
suspend fun readResourceBytes(path: String): ByteArray
```

The generated `Res` object provides a public wrapper:

```kotlin
// Usage via generated accessor
val bytes: ByteArray = Res.readBytes("files/config.json")
```

**Usage:**

```kotlin
suspend fun loadConfig(): AppConfig {
    val bytes = Res.readBytes("files/config.json")
    val json = bytes.decodeToString()
    return Json.decodeFromString<AppConfig>(json)
}
```

---

## Resource Qualifiers

Qualifiers are appended to directory names with a hyphen separator.

### Qualifier Table

| Qualifier | Format | Examples | Applies To |
|-----------|--------|----------|------------|
| Language | Two-letter ISO 639-1 | `values-fr/`, `values-de/`, `values-ja/` | `values`, `drawable` |
| Region | `r` + two-letter ISO 3166-1 | `values-pt-rBR/`, `values-en-rGB/` | `values`, `drawable` |
| Theme | `dark` or `light` | `drawable-dark/`, `values-dark/` | `values`, `drawable` |
| Density | DPI bucket | `drawable-mdpi/`, `drawable-xxhdpi/` | `drawable` only |

### Density Buckets

| Qualifier | Approximate DPI | Scale Factor |
|-----------|----------------|--------------|
| `mdpi` | ~160 | 1x |
| `hdpi` | ~240 | 1.5x |
| `xhdpi` | ~320 | 2x |
| `xxhdpi` | ~480 | 3x |
| `xxxhdpi` | ~640 | 4x |

### Qualifier Ordering

When combining qualifiers, the order must be: **language** -> **region** -> **theme** -> **density**.

```
values-fr/                     # Language only
values-fr-rCA/                 # Language + Region
drawable-dark/                 # Theme only
drawable-xxhdpi/               # Density only
drawable-dark-xxhdpi/          # Theme + Density
```

### Resolution Priority

The system selects the most specific matching qualifier set. For example, if the device locale is `fr-CA` in dark mode:
1. `values-fr-rCA-dark/` (exact match)
2. `values-fr-rCA/` (language + region)
3. `values-fr-dark/` (language + theme)
4. `values-fr/` (language only)
5. `values-dark/` (theme only)
6. `values/` (default fallback)

---

## XML Resource Formats

### strings.xml

```xml
<resources>
    <string name="app_name">My Application</string>
    <string name="greeting">Hello, %1$s!</string>
    <string name="message_count">You have %1$d new messages</string>
    <string name="welcome_back">Welcome back, %1$s! Last seen %2$s.</string>
</resources>
```

**Format specifiers:** `%1$s` (string), `%1$d` (integer), `%1$f` (float). The number is the argument index (1-based).

### plurals.xml

```xml
<resources>
    <plurals name="items_count">
        <item quantity="zero">No items</item>
        <item quantity="one">%d item</item>
        <item quantity="two">%d items</item>
        <item quantity="few">%d items</item>
        <item quantity="many">%d items</item>
        <item quantity="other">%d items</item>
    </plurals>
</resources>
```

**Quantity values:**
- `zero` -- When the language requires special treatment of the number 0.
- `one` -- When the language requires special treatment of numbers like 1.
- `two` -- When the language requires special treatment of numbers like 2.
- `few` -- When the language requires special treatment of "small" numbers (e.g., 2-4 in Czech).
- `many` -- When the language requires special treatment of "large" numbers (e.g., endings in 11-99 in Maltese).
- `other` -- **Required.** Used when none of the other rules apply.

### string-array (in strings.xml)

```xml
<resources>
    <string-array name="planets">
        <item>Mercury</item>
        <item>Venus</item>
        <item>Earth</item>
        <item>Mars</item>
    </string-array>
</resources>
```

---

## Key Imports

```kotlin
// Resource composable functions
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.imageResource
import org.jetbrains.compose.resources.vectorResource
import org.jetbrains.compose.resources.Font

// Non-composable suspend functions
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.getStringArray

// Resource types
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.FontResource
import org.jetbrains.compose.resources.StringArrayResource

// Generated Res class (your package)
import com.myapp.resources.Res
```

---

## Platform-Specific Packaging

| Platform | Packaging Location | Access Method |
|----------|-------------------|---------------|
| Android | `assets/` directory in APK | `AssetManager` |
| JVM/Desktop | Inside JAR file | `ClassLoader.getResourceAsStream()` |
| iOS/macOS | Framework bundle | `NSBundle` |
| JS/Wasm (Web) | Static files in dist directory | HTTP fetch |

This is handled automatically by the Gradle plugin. You do not need to configure platform-specific resource handling.

---

## Build Tasks

The Compose Resources Gradle plugin registers these tasks per source set:

| Task | Purpose |
|------|---------|
| `prepareComposeResourcesTaskFor<SourceSet>` | Validates resource naming and qualifier syntax |
| `convertXmlValueResourcesFor<SourceSet>` | Converts XML values to binary `.cvr` format |
| `copyNonXmlValueResourcesFor<SourceSet>` | Copies drawables, fonts, files as-is |
| `generateResourceAccessorsFor<SourceSet>` | Generates the `Res` class with typed accessors |
| `generateExpectResourceCollectorsFor<SourceSet>` | Generates `expect` declarations for KMP |
| `generateActualResourceCollectorsFor<SourceSet>` | Generates `actual` implementations per target |
