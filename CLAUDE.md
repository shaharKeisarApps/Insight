# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run all unit tests
./gradlew test

# Run a single unit test class
./gradlew test --tests "com.keisardev.metroditest.ExampleUnitTest"

# Run instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest

# Clean build
./gradlew clean
```

## Architecture

**Single-Activity Compose Architecture**: The app uses `MainActivity` as the sole activity, with all UI built declaratively using Jetpack Compose.

**Navigation Pattern**: Uses Material3's `NavigationSuiteScaffold` for adaptive navigation that automatically adjusts between bottom navigation, rail, and drawer based on screen size. Destinations are defined in the `AppDestinations` enum.

**State Management**: Local state via Compose's `mutableStateOf` with `rememberSaveable` for configuration change survival. No DI framework is currently implemented.

**Theme System**: Material3 theming in `ui/theme/` with dynamic color support (API 31+). `Theme.kt` provides `MetroDITestTheme` composable wrapper.

## Project Configuration

- **Min SDK**: 33 (Android 13+)
- **Target/Compile SDK**: 36
- **Kotlin**: 2.0.21 (K2 compiler)
- **Compose BOM**: 2024.09.00
- **AGP**: 9.1.0-alpha01

Dependencies are managed via version catalog at `gradle/libs.versions.toml`.