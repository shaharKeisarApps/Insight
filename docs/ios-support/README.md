# iOS Support Migration Guide

This documentation covers the complete process of adding iOS support to an Android-only KMP project using Compose Multiplatform with Circuit MVI architecture.

## Overview

The migration is split into two main phases:

### Phase 1: KMP Foundation
Making the existing Android codebase KMP-compatible without breaking Android functionality.

- [01-KMP-Migration.md](./01-KMP-Migration.md) - Converting Android modules to KMP
- [02-Parcelize-Pattern.md](./02-Parcelize-Pattern.md) - expect/actual pattern for Parcelable
- [03-Convention-Plugins.md](./03-Convention-Plugins.md) - Gradle convention plugins for KMP

### Phase 2: iOS Integration
Setting up the iOS target and Xcode project.

- [04-iOS-Project-Setup.md](./04-iOS-Project-Setup.md) - Xcode project and framework integration
- [05-Troubleshooting.md](./05-Troubleshooting.md) - Common issues and solutions

## Quick Reference

### Key Changes Summary

| Component | Before (Android) | After (KMP) |
|-----------|------------------|-------------|
| Plugin | `insight.android.library` | `insight.kmp.library` |
| Feature Plugin | `insight.android.feature` | `insight.kmp.feature` |
| Source Set | `src/main/kotlin/` | `src/commonMain/kotlin/` |
| Parcelize | `@Parcelize` from kotlinx.parcelize | Custom expect/actual pattern |
| Coroutines | `kotlinx-coroutines-android` | `kotlinx-coroutines-core` (common) |

### Module Structure

```
project/
├── app/                    # Android app entry point
├── shared/                 # NEW: iOS framework umbrella module
├── iosApp/                 # NEW: Xcode project
├── build-logic/
│   └── convention/
│       ├── KmpLibraryConventionPlugin.kt   # NEW
│       └── KmpFeatureConventionPlugin.kt   # NEW
├── core/
│   ├── common/             # KMP: expect/actual Parcelize, AppScope
│   ├── model/              # KMP: Domain models
│   ├── database/           # KMP: SQLDelight with platform drivers
│   ├── data/               # KMP: Repositories
│   ├── designsystem/       # KMP: Compose theme
│   └── ui/                 # KMP: Shared UI components
└── feature/
    └── */                  # KMP: Circuit screens with @Parcelize
```

### Required Dependencies

```toml
# gradle/libs.versions.toml additions
composeMultiplatform = "1.9.3"

[libraries]
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core" }
sqldelight-native-driver = { module = "app.cash.sqldelight:native-driver" }
composeMultiplatform-gradlePlugin = { module = "org.jetbrains.compose:compose-gradle-plugin" }

[plugins]
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform" }
compose-multiplatform = { id = "org.jetbrains.compose" }
```

## Prerequisites

- Xcode 15+ (for iOS 17+ support)
- Kotlin 2.0+ with K2 compiler
- Compose Multiplatform 1.6+
- Circuit 0.20+ (KMP-compatible)
- Metro DI 0.9+ (KMP-compatible)
