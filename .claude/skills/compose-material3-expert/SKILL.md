---
name: compose-material3-expert
description: Expert guidance on Material 3 Design System for KMP. Use for Theming (ColorScheme, Typography, Shapes), Dynamic Color, Component selection (Buttons, Cards, Navigation, TopAppBar, BottomSheet, Dialogs, Snackbar, TextField, ListItem), Adaptive Layouts (WindowSizeClass, ListDetailPaneScaffold), and resolving common M3 pitfalls.
---

# Compose Material 3 Expert Skill (Compose Multiplatform 1.10.0)

## Overview

Material 3 (Material You) is the design system for Compose Multiplatform. It provides a token-based theming system (ColorScheme, Typography, Shapes), dynamic color on Android, and a comprehensive component library. In KMP projects, Material 3 is accessed via `compose.material3` from the JetBrains Compose Gradle plugin. This skill covers the entire M3 surface area relevant to multiplatform apps targeting Android, iOS, Desktop, and Web.

## When to Use

- **Theming**: Defining `MaterialTheme` with `ColorScheme`, `Typography`, `Shapes`
- **Dynamic Color**: Platform-aware dynamic theming (Android 12+ / fallback)
- **Component Selection**: Choosing the right button, card, navigation, or input variant
- **Adaptive Layouts**: `WindowSizeClass`, `ListDetailPaneScaffold`, `NavigationSuiteScaffold`
- **Dark/Light Mode**: Switching color schemes, respecting system settings
- **Pitfall Resolution**: Fixing hardcoded colors, wrong tokens, missing `contentColor`

## Quick Reference

For detailed API signatures, see [reference.md](reference.md).
For production examples with Metro DI, see [examples.md](examples.md).

---

## M3 Theming System

Material 3 theming is built on three pillars: **ColorScheme**, **Typography**, and **Shapes**. All three are provided via `MaterialTheme` and accessed throughout the composition tree.

```kotlin
MaterialTheme(
    colorScheme = appColorScheme,
    typography = appTypography,
    shapes = appShapes,
    content = { /* app content */ }
)
```

### ColorScheme (29 Color Roles)

The `ColorScheme` defines 29 named color roles organized into groups:

| Group | Roles | Usage |
|-------|-------|-------|
| **Primary** | `primary`, `onPrimary`, `primaryContainer`, `onPrimaryContainer` | Key actions, FABs, active states |
| **Secondary** | `secondary`, `onSecondary`, `secondaryContainer`, `onSecondaryContainer` | Less prominent actions, filters, chips |
| **Tertiary** | `tertiary`, `onTertiary`, `tertiaryContainer`, `onTertiaryContainer` | Complementary accents, contrast elements |
| **Error** | `error`, `onError`, `errorContainer`, `onErrorContainer` | Validation errors, destructive actions |
| **Surface** | `surface`, `onSurface`, `surfaceVariant`, `onSurfaceVariant` | Backgrounds, cards, text on backgrounds |
| **Surface Tint** | `surfaceTint` | Elevation overlay tint |
| **Surface Containers** | `surfaceContainerLowest`, `surfaceContainerLow`, `surfaceContainer`, `surfaceContainerHigh`, `surfaceContainerHighest` | Layered surfaces at different emphasis levels |
| **Surface Bright/Dim** | `surfaceBright`, `surfaceDim` | Bright/dim surface variants |
| **Inverse** | `inverseSurface`, `inverseOnSurface`, `inversePrimary` | Snackbars, tooltips (inverted context) |
| **Outline** | `outline`, `outlineVariant` | Borders, dividers |
| **Scrim** | `scrim` | Modal overlays (bottom sheets, dialogs) |

Access: `MaterialTheme.colorScheme.primary`, `MaterialTheme.colorScheme.surfaceContainer`, etc.

### Typography (15 Text Styles)

Material 3 defines 15 text styles across 5 categories with 3 sizes each:

| Category | Large | Medium | Small | Typical Use |
|----------|-------|--------|-------|-------------|
| **Display** | `displayLarge` | `displayMedium` | `displaySmall` | Hero text, large numerals |
| **Headline** | `headlineLarge` | `headlineMedium` | `headlineSmall` | Section headers |
| **Title** | `titleLarge` | `titleMedium` | `titleSmall` | Card titles, app bar titles |
| **Body** | `bodyLarge` | `bodyMedium` | `bodySmall` | Paragraph text, descriptions |
| **Label** | `labelLarge` | `labelMedium` | `labelSmall` | Buttons, tabs, captions |

Access: `MaterialTheme.typography.bodyMedium`, `MaterialTheme.typography.titleLarge`, etc.

### Shapes (5 Corner Sizes)

| Token | Default | Typical Component |
|-------|---------|-------------------|
| `extraSmall` | 4dp | Chips, small badges |
| `small` | 8dp | Buttons, text fields |
| `medium` | 12dp | Cards, dialogs |
| `large` | 16dp | Bottom sheets, FAB |
| `extraLarge` | 28dp | Large sheets, full-screen elements |

Access: `MaterialTheme.shapes.medium`, etc.

---

## Dynamic Color

Dynamic color extracts a palette from the user's wallpaper (Android 12+). On non-Android platforms, fall back to a custom color scheme.

### KMP Pattern (expect/actual)

```kotlin
// commonMain
@Composable
expect fun platformColorScheme(useDarkTheme: Boolean): ColorScheme

// androidMain
@Composable
actual fun platformColorScheme(useDarkTheme: Boolean): ColorScheme {
    val context = LocalContext.current
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (useDarkTheme) dynamicDarkColorScheme(context)
        else dynamicLightColorScheme(context)
    } else {
        if (useDarkTheme) AppDarkColorScheme else AppLightColorScheme
    }
}

// iosMain / desktopMain / wasmJsMain
@Composable
actual fun platformColorScheme(useDarkTheme: Boolean): ColorScheme {
    return if (useDarkTheme) AppDarkColorScheme else AppLightColorScheme
}
```

---

## Component Catalog Decision Guide

### Buttons

| Variant | When to Use | Emphasis |
|---------|-------------|----------|
| `Button` (Filled) | Primary action on screen | Highest |
| `FilledTonalButton` | Important but not primary action | High |
| `ElevatedButton` | Alternative to FilledTonal, adds shadow | High |
| `OutlinedButton` | Secondary action, pairs with Filled | Medium |
| `TextButton` | Tertiary action, cancel/dismiss | Low |
| `FloatingActionButton` | Primary screen action, anchored | Highest (floating) |
| `ExtendedFloatingActionButton` | FAB with label text | Highest (floating) |
| `IconButton` | Icon-only action (toolbar, list trailing) | Varies |
| `FilledIconButton` | Emphasized icon action (toggle, selection) | High |

**Rule**: One Filled button per screen. Use TextButton for cancel/dismiss. Never put two Filled buttons side by side.

### Cards

| Variant | Surface | Border | Use Case |
|---------|---------|--------|----------|
| `Card` (Filled) | `surfaceContainerHighest` | None | Default content container |
| `ElevatedCard` | `surface` + elevation | None | Prominent items in grids/lists |
| `OutlinedCard` | `surface` | `outline` | Equal emphasis items, settings |

### Navigation

| Component | Position | Best For |
|-----------|----------|----------|
| `NavigationBar` | Bottom | 3-5 top-level destinations, phone |
| `NavigationRail` | Side (vertical) | Tablets, foldables, medium screens |
| `NavigationDrawer` (Modal) | Overlay from edge | 5+ destinations, deep hierarchies |
| `PermanentNavigationDrawer` | Always visible side | Desktop, expanded screens |
| `TabRow` / `ScrollableTabRow` | Top, below app bar | Content categories within a screen |

### Top App Bars

| Variant | Scroll Behavior | Use Case |
|---------|-----------------|----------|
| `TopAppBar` | `pinnedScrollBehavior()` | Always visible, small title |
| `CenterAlignedTopAppBar` | `pinnedScrollBehavior()` | Branding, centered title |
| `MediumTopAppBar` | `exitUntilCollapsedScrollBehavior()` | Collapsible, medium title |
| `LargeTopAppBar` | `exitUntilCollapsedScrollBehavior()` | Collapsible, large title (hero) |

**Important**: Connect scroll behavior to `Scaffold` via `Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)`.

### Dialogs

| Variant | Use Case |
|---------|----------|
| `AlertDialog` | Simple confirmation with title, text, buttons |
| `BasicAlertDialog` | Custom layout dialog (no built-in button slots) |

### Bottom Sheets

| Variant | Use Case |
|---------|----------|
| `ModalBottomSheet` | Standalone sheet that overlays content |
| `BottomSheetScaffold` | Persistent sheet integrated into layout |

### Text Fields

| Variant | Use Case |
|---------|----------|
| `TextField` (Filled) | Default input in forms |
| `OutlinedTextField` | Alternative style, better in lists of inputs |

Both support: `label`, `placeholder`, `leadingIcon`, `trailingIcon`, `supportingText`, `isError`, `singleLine`, `keyboardOptions`, `keyboardActions`.

### Lists and Snackbars

| Component | Use Case |
|-----------|----------|
| `ListItem` | Standard list row with headline, supporting, leading, trailing |
| `SnackbarHost` | Container for showing `Snackbar` messages |

---

## Adaptive Layouts

### WindowSizeClass

Categorizes display area into width/height buckets:

| Width Class | Breakpoint | Layout |
|-------------|------------|--------|
| `Compact` | < 600dp | Phone portrait, single column |
| `Medium` | 600-839dp | Tablet portrait, foldable |
| `Expanded` | >= 840dp | Tablet landscape, desktop |

**Dependency**: `compose.material3` + `material3-window-size-class` (commonized in CMP 1.10.0)

```kotlin
// Calculate in top-level composable
val windowSizeClass = calculateWindowSizeClass()
val isCompact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact
```

### ListDetailPaneScaffold

Two-pane layout that adapts between list and detail on compact vs. expanded:

**Dependency**: `material3-adaptive-layout` + `material3-adaptive-navigation`

### NavigationSuiteScaffold

Auto-selects NavigationBar, NavigationRail, or NavigationDrawer based on WindowSizeClass.

**Dependency**: `material3-adaptive-navigation-suite`

---

## Common Pitfalls

### 1. Hardcoded Colors
**Wrong**: `color = Color(0xFF6200EE)`
**Right**: `color = MaterialTheme.colorScheme.primary`

Always use theme tokens. Hardcoded colors break dark mode, dynamic color, and theming.

### 2. Wrong Typography Tokens
**Wrong**: `fontSize = 14.sp, fontWeight = FontWeight.Bold`
**Right**: `style = MaterialTheme.typography.labelLarge`

Material 3 typography tokens include font size, weight, line height, and letter spacing as a unit.

### 3. Missing contentColor
`Surface`, `Card`, and `Button` automatically set `LocalContentColor` for children. If you set a custom `containerColor`, ensure the corresponding `contentColor` has sufficient contrast.

### 4. Scroll Behavior Not Connected
TopAppBar scroll behaviors require `Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)` on the scrollable content or `Scaffold`.

### 5. SnackbarHost Not in Scaffold
`SnackbarHost` must be passed to `Scaffold(snackbarHost = { ... })` to position correctly above NavigationBar and respect insets.

### 6. ModalBottomSheet Missing Dismiss
Always provide `onDismissRequest` for `ModalBottomSheet`. Without it, the sheet cannot be dismissed by scrim tap or back gesture.

### 7. Using Android-Only APIs in Common Code
`dynamicDarkColorScheme`/`dynamicLightColorScheme` and `LocalContext` are Android-only. Use `expect`/`actual` to isolate them.

### 8. Ignoring contentPadding from Scaffold
Always use the `PaddingValues` provided by `Scaffold`'s content lambda. Ignoring them causes content to render behind the top bar or navigation bar.

---

## Gradle Dependencies (libs.versions.toml)

```toml
[versions]
compose-multiplatform = "1.10.0"

[libraries]
# Via JetBrains Compose Gradle plugin shortcuts:
# compose.material3                          -> material3 core
# compose.material3.windowSizeClass          -> WindowSizeClass (commonized)
# compose.material3.adaptive                 -> adaptive layout
# compose.material3.adaptive.layout          -> ListDetailPaneScaffold
# compose.material3.adaptive.navigation      -> adaptive navigation
# compose.material3.adaptive.navigationSuite -> NavigationSuiteScaffold
```

In `build.gradle.kts`:

```kotlin
commonMain.dependencies {
    implementation(compose.material3)
    // For adaptive layouts:
    implementation(compose.material3.adaptive)
    implementation(compose.material3.adaptive.layout)
    implementation(compose.material3.adaptive.navigation)
}
```

## Core Rules

1. **Always use theme tokens** -- `MaterialTheme.colorScheme.*`, `MaterialTheme.typography.*`, `MaterialTheme.shapes.*`.
2. **One Filled Button per screen** -- Use Tonal, Outlined, or Text for secondary actions.
3. **Connect scroll behaviors** -- `nestedScroll` modifier is required for collapsing top bars.
4. **Use expect/actual for dynamic color** -- Android-only APIs must not leak into common code.
5. **Pass Scaffold padding** -- Always apply `PaddingValues` from Scaffold's content lambda.
6. **Prefer Surface over Box** -- `Surface` sets `LocalContentColor` automatically for correct text contrast.
7. **Choose navigation by screen size** -- NavigationBar for compact, Rail for medium, Drawer for expanded.

## See Also

- [compose-ui-expert](../compose-ui-expert/SKILL.md) -- Layout, modifiers, window insets
- [compose-animation-expert](../compose-animation-expert/SKILL.md) -- Motion and transitions for M3 components
- [accessibility-expert](../accessibility-expert/SKILL.md) -- Accessibility best practices for M3 components
- [resources-expert](../resources-expert/SKILL.md) -- Multiplatform string/image resources
