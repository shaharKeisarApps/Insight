---
name: m3-theme-expert
description: Elite Material 3 theming expertise for Compose Multiplatform. Use when setting up design systems, creating color schemes, defining typography, configuring shapes, implementing dynamic color, or creating custom theme extensions. Triggers on theme setup, color token usage, design system questions, or dark mode implementation.
---

# Material 3 Theme Expert Skill

## Core Concepts

Material 3 (M3) provides:
- **Color Scheme**: 29 semantic color roles
- **Typography**: 15 text styles
- **Shapes**: 6 shape categories
- **Dynamic Color**: Platform-adaptive colors (Android 12+)

## Complete Theme Setup

### Theme File Structure

```
core/ui/
├── src/commonMain/kotlin/com/app/ui/theme/
│   ├── Theme.kt           # Main theme composable
│   ├── Color.kt           # Color definitions
│   ├── Typography.kt      # Typography definitions
│   ├── Shapes.kt          # Shape definitions
│   ├── Dimensions.kt      # Spacing, sizing
│   └── ExtendedTheme.kt   # Custom extensions
```

### Main Theme

```kotlin
// Theme.kt
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && supportsDynamicColor() -> {
            if (darkTheme) dynamicDarkColorScheme() 
            else dynamicLightColorScheme()
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    val extendedColors = if (darkTheme) {
        DarkExtendedColors
    } else {
        LightExtendedColors
    }
    
    CompositionLocalProvider(
        LocalExtendedColors provides extendedColors,
        LocalSpacing provides Spacing(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

// Platform-specific dynamic color support
@Composable
expect fun supportsDynamicColor(): Boolean

@Composable
expect fun dynamicLightColorScheme(): ColorScheme

@Composable
expect fun dynamicDarkColorScheme(): ColorScheme
```

### Android Dynamic Color Implementation

```kotlin
// androidMain
@Composable
actual fun supportsDynamicColor(): Boolean = 
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
actual fun dynamicLightColorScheme(): ColorScheme {
    val context = LocalContext.current
    return dynamicLightColorScheme(context)
}

@Composable
actual fun dynamicDarkColorScheme(): ColorScheme {
    val context = LocalContext.current
    return dynamicDarkColorScheme(context)
}
```

### iOS/Desktop Fallback

```kotlin
// iosMain / desktopMain
@Composable
actual fun supportsDynamicColor(): Boolean = false

@Composable
actual fun dynamicLightColorScheme(): ColorScheme = LightColorScheme

@Composable
actual fun dynamicDarkColorScheme(): ColorScheme = DarkColorScheme
```

## Color Scheme

### Complete Color Definition

```kotlin
// Color.kt

// Primary brand colors (generate from M3 theme builder)
private val md_theme_light_primary = Color(0xFF6750A4)
private val md_theme_light_onPrimary = Color(0xFFFFFFFF)
private val md_theme_light_primaryContainer = Color(0xFFEADDFF)
private val md_theme_light_onPrimaryContainer = Color(0xFF21005D)

private val md_theme_light_secondary = Color(0xFF625B71)
private val md_theme_light_onSecondary = Color(0xFFFFFFFF)
private val md_theme_light_secondaryContainer = Color(0xFFE8DEF8)
private val md_theme_light_onSecondaryContainer = Color(0xFF1D192B)

private val md_theme_light_tertiary = Color(0xFF7D5260)
private val md_theme_light_onTertiary = Color(0xFFFFFFFF)
private val md_theme_light_tertiaryContainer = Color(0xFFFFD8E4)
private val md_theme_light_onTertiaryContainer = Color(0xFF31111D)

private val md_theme_light_error = Color(0xFFB3261E)
private val md_theme_light_onError = Color(0xFFFFFFFF)
private val md_theme_light_errorContainer = Color(0xFFF9DEDC)
private val md_theme_light_onErrorContainer = Color(0xFF410E0B)

private val md_theme_light_background = Color(0xFFFFFBFE)
private val md_theme_light_onBackground = Color(0xFF1C1B1F)

private val md_theme_light_surface = Color(0xFFFFFBFE)
private val md_theme_light_onSurface = Color(0xFF1C1B1F)
private val md_theme_light_surfaceVariant = Color(0xFFE7E0EC)
private val md_theme_light_onSurfaceVariant = Color(0xFF49454F)

private val md_theme_light_outline = Color(0xFF79747E)
private val md_theme_light_outlineVariant = Color(0xFFCAC4D0)

private val md_theme_light_inverseSurface = Color(0xFF313033)
private val md_theme_light_inverseOnSurface = Color(0xFFF4EFF4)
private val md_theme_light_inversePrimary = Color(0xFFD0BCFF)

private val md_theme_light_surfaceTint = Color(0xFF6750A4)
private val md_theme_light_scrim = Color(0xFF000000)

// Dark theme colors
private val md_theme_dark_primary = Color(0xFFD0BCFF)
private val md_theme_dark_onPrimary = Color(0xFF381E72)
private val md_theme_dark_primaryContainer = Color(0xFF4F378B)
private val md_theme_dark_onPrimaryContainer = Color(0xFFEADDFF)

private val md_theme_dark_secondary = Color(0xFFCCC2DC)
private val md_theme_dark_onSecondary = Color(0xFF332D41)
private val md_theme_dark_secondaryContainer = Color(0xFF4A4458)
private val md_theme_dark_onSecondaryContainer = Color(0xFFE8DEF8)

private val md_theme_dark_tertiary = Color(0xFFEFB8C8)
private val md_theme_dark_onTertiary = Color(0xFF492532)
private val md_theme_dark_tertiaryContainer = Color(0xFF633B48)
private val md_theme_dark_onTertiaryContainer = Color(0xFFFFD8E4)

private val md_theme_dark_error = Color(0xFFF2B8B5)
private val md_theme_dark_onError = Color(0xFF601410)
private val md_theme_dark_errorContainer = Color(0xFF8C1D18)
private val md_theme_dark_onErrorContainer = Color(0xFFF9DEDC)

private val md_theme_dark_background = Color(0xFF1C1B1F)
private val md_theme_dark_onBackground = Color(0xFFE6E1E5)

private val md_theme_dark_surface = Color(0xFF1C1B1F)
private val md_theme_dark_onSurface = Color(0xFFE6E1E5)
private val md_theme_dark_surfaceVariant = Color(0xFF49454F)
private val md_theme_dark_onSurfaceVariant = Color(0xFFCAC4D0)

private val md_theme_dark_outline = Color(0xFF938F99)
private val md_theme_dark_outlineVariant = Color(0xFF49454F)

private val md_theme_dark_inverseSurface = Color(0xFFE6E1E5)
private val md_theme_dark_inverseOnSurface = Color(0xFF313033)
private val md_theme_dark_inversePrimary = Color(0xFF6750A4)

private val md_theme_dark_surfaceTint = Color(0xFFD0BCFF)
private val md_theme_dark_scrim = Color(0xFF000000)

val LightColorScheme = lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,
    secondary = md_theme_light_secondary,
    onSecondary = md_theme_light_onSecondary,
    secondaryContainer = md_theme_light_secondaryContainer,
    onSecondaryContainer = md_theme_light_onSecondaryContainer,
    tertiary = md_theme_light_tertiary,
    onTertiary = md_theme_light_onTertiary,
    tertiaryContainer = md_theme_light_tertiaryContainer,
    onTertiaryContainer = md_theme_light_onTertiaryContainer,
    error = md_theme_light_error,
    onError = md_theme_light_onError,
    errorContainer = md_theme_light_errorContainer,
    onErrorContainer = md_theme_light_onErrorContainer,
    background = md_theme_light_background,
    onBackground = md_theme_light_onBackground,
    surface = md_theme_light_surface,
    onSurface = md_theme_light_onSurface,
    surfaceVariant = md_theme_light_surfaceVariant,
    onSurfaceVariant = md_theme_light_onSurfaceVariant,
    outline = md_theme_light_outline,
    outlineVariant = md_theme_light_outlineVariant,
    inverseSurface = md_theme_light_inverseSurface,
    inverseOnSurface = md_theme_light_inverseOnSurface,
    inversePrimary = md_theme_light_inversePrimary,
    surfaceTint = md_theme_light_surfaceTint,
    scrim = md_theme_light_scrim,
)

val DarkColorScheme = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    error = md_theme_dark_error,
    onError = md_theme_dark_onError,
    errorContainer = md_theme_dark_errorContainer,
    onErrorContainer = md_theme_dark_onErrorContainer,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    outline = md_theme_dark_outline,
    outlineVariant = md_theme_dark_outlineVariant,
    inverseSurface = md_theme_dark_inverseSurface,
    inverseOnSurface = md_theme_dark_inverseOnSurface,
    inversePrimary = md_theme_dark_inversePrimary,
    surfaceTint = md_theme_dark_surfaceTint,
    scrim = md_theme_dark_scrim,
)
```

## Extended Colors

```kotlin
// ExtendedTheme.kt

@Immutable
data class ExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val info: Color,
    val onInfo: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
)

val LightExtendedColors = ExtendedColors(
    success = Color(0xFF386A20),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFB8F397),
    onSuccessContainer = Color(0xFF042100),
    warning = Color(0xFF7D5700),
    onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFFFDEA6),
    onWarningContainer = Color(0xFF271900),
    info = Color(0xFF00658E),
    onInfo = Color(0xFFFFFFFF),
    infoContainer = Color(0xFFC5E7FF),
    onInfoContainer = Color(0xFF001E2E),
)

val DarkExtendedColors = ExtendedColors(
    success = Color(0xFF9DD67D),
    onSuccess = Color(0xFF0C3900),
    successContainer = Color(0xFF205107),
    onSuccessContainer = Color(0xFFB8F397),
    warning = Color(0xFFF9BD4A),
    onWarning = Color(0xFF422C00),
    warningContainer = Color(0xFF5F4100),
    onWarningContainer = Color(0xFFFFDEA6),
    info = Color(0xFF82CFFF),
    onInfo = Color(0xFF00344C),
    infoContainer = Color(0xFF004C6C),
    onInfoContainer = Color(0xFFC5E7FF),
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

// Extension for easy access
val MaterialTheme.extendedColors: ExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalExtendedColors.current
```

## Typography

```kotlin
// Typography.kt

// Custom font (optional)
expect val AppFontFamily: FontFamily

// Fallback for platforms without custom font
val DefaultFontFamily = FontFamily.Default

val AppTypography = Typography(
    // Display styles
    displayLarge = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp,
    ),
    
    // Headline styles
    headlineLarge = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    
    // Title styles
    titleLarge = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    
    // Body styles
    bodyLarge = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    
    // Label styles
    labelLarge = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)
```

## Shapes

```kotlin
// Shapes.kt

val AppShapes = Shapes(
    // Extra small: chips, small buttons
    extraSmall = RoundedCornerShape(4.dp),
    
    // Small: cards, dialogs
    small = RoundedCornerShape(8.dp),
    
    // Medium: floating action buttons, menus
    medium = RoundedCornerShape(12.dp),
    
    // Large: bottom sheets, large cards
    large = RoundedCornerShape(16.dp),
    
    // Extra large: full-screen dialogs
    extraLarge = RoundedCornerShape(28.dp),
)

// Custom shapes for specific components
object CustomShapes {
    val Pill = RoundedCornerShape(50)
    val TopRounded = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    val BottomRounded = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
}
```

## Spacing System

```kotlin
// Dimensions.kt

@Immutable
data class Spacing(
    val none: Dp = 0.dp,
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 24.dp,
    val extraLarge: Dp = 32.dp,
    val huge: Dp = 48.dp,
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }

val MaterialTheme.spacing: Spacing
    @Composable
    @ReadOnlyComposable
    get() = LocalSpacing.current

// Usage
@Composable
fun Example() {
    Column(
        modifier = Modifier.padding(MaterialTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        // Content
    }
}
```

## Usage Patterns

### Correct Color Usage

```kotlin
@Composable
fun UserCard(user: User, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(MaterialTheme.spacing.medium),
        ) {
            // Avatar with primary color
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    text = user.initials,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(12.dp),
                )
            }
            
            Column {
                // Primary content
                Text(
                    text = user.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                
                // Secondary content
                Text(
                    text = user.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
```

### Status Colors

```kotlin
@Composable
fun StatusBadge(
    status: Status,
    modifier: Modifier = Modifier,
) {
    val (containerColor, contentColor) = when (status) {
        Status.SUCCESS -> MaterialTheme.extendedColors.successContainer to 
            MaterialTheme.extendedColors.onSuccessContainer
        Status.WARNING -> MaterialTheme.extendedColors.warningContainer to 
            MaterialTheme.extendedColors.onWarningContainer
        Status.ERROR -> MaterialTheme.colorScheme.errorContainer to 
            MaterialTheme.colorScheme.onErrorContainer
        Status.INFO -> MaterialTheme.extendedColors.infoContainer to 
            MaterialTheme.extendedColors.onInfoContainer
    }
    
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = containerColor,
    ) {
        Text(
            text = status.label,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(
                horizontal = MaterialTheme.spacing.small,
                vertical = MaterialTheme.spacing.extraSmall,
            ),
        )
    }
}
```

### Component Theming

```kotlin
// Themed button variants
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Text(text)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Text(text)
    }
}
```

## Anti-Patterns

❌ **Don't hardcode colors**
```kotlin
// WRONG
Text(text = "Hello", color = Color.Black)

// RIGHT
Text(
    text = "Hello",
    color = MaterialTheme.colorScheme.onSurface,
)
```

❌ **Don't use wrong semantic colors**
```kotlin
// WRONG - using error for non-error state
Surface(color = MaterialTheme.colorScheme.error) {
    Text("Active", color = MaterialTheme.colorScheme.onError)
}

// RIGHT - use appropriate semantic color
Surface(color = MaterialTheme.extendedColors.successContainer) {
    Text("Active", color = MaterialTheme.extendedColors.onSuccessContainer)
}
```

❌ **Don't mix color systems**
```kotlin
// WRONG - mixing M2 and M3
MaterialTheme.colors.primary  // M2
MaterialTheme.colorScheme.primary  // M3 ✓
```

## References

- M3 Design: https://m3.material.io/
- M3 Theme Builder: https://m3.material.io/theme-builder
- Compose M3: https://developer.android.com/jetpack/compose/designsystems/material3
- Color Roles: https://m3.material.io/styles/color/roles
