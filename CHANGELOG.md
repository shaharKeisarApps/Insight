# Changelog

All notable changes to Insight will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [0.3.0] - 2026-03-12

### Added

- Full iOS app with all 5 screens: Expenses, Income, Reports, AI Chat, Settings
- On-device AI inference on iOS via Llamatik (same engine as Android)
- iOS AI Chat presenter with model download, search, and streaming support
- iOS Settings presenter with AI engine mode switcher, model management, and cloud configuration
- iOS-specific DI modules: IosBindingsModule, IosAiConfig, IosModelDownloadTrigger
- Model setup bottom sheet and download progress UI shared across platforms

### Changed

- Converted `core:ai` from Android-only to Kotlin Multiplatform (commonMain/androidMain/iosMain)
- Moved ModelSetupBottomSheet and ModelDownloadProgress from androidMain to commonMain
- Upgraded Metro DI from 0.9.2 to 0.11.2 — fixes Kotlin/Native graph transform failures
- Upgraded Koog AI agents to KMP artifacts (non-JVM-suffixed)
- Replaced `java.util.concurrent.atomic` with `kotlinx.atomicfu` in shared AI code
- Replaced `Dispatchers.IO` with `Dispatchers.Default` in shared code for native compatibility
- Replaced `org.json.JSONArray` with `kotlinx.serialization.json` in ModelRepositoryImpl
- Replaced `String.format(Locale)` with pure-Kotlin number formatting for multiplatform

### Architecture

- Koog tools (reflection-based) remain Android-only in `androidMain`
- New `CloudAiService` interface abstracts cloud AI — KoogAiService on Android, no-op on iOS
- IosBindingsModule provides manual @Provides bindings for cross-module resolution on native
- IosCircuitModule manually wires all Circuit presenter/UI factories for iOS

## [0.2.0] - 2026-03-06

### Added

- Token streaming for on-device AI chat — responses appear word-by-word as they are generated
- Chat persistence: conversation history is now saved to the local SQLDelight database and survives app restarts
- Prompt optimization for on-device inference to reduce latency and improve response quality
- Month transition animation in Reports screen — category lists animate smoothly when switching between months

### Changed

- Replaced `Application`/`java.io.File` with Okio `Path`/`FileSystem` in `core:ai` for idiomatic file I/O

## [0.1.1] - 2026-03-04

### Fixed

- Resolve AI chat crash ("rememberCoroutineScope left the composition") during on-device inference by using a retained coroutine scope that survives Circuit's composition lifecycle
- Properly rethrow CancellationException in ChatRepository instead of displaying internal error messages

## [0.1.0] - 2026-02-16

### Added

- Expense tracking with category-based organization (Food, Transport, Entertainment, Shopping, Health, Bills, Other)
- Income management with recurring/one-time type toggle and category support (Salary, Freelance, Investment, Gift, Other)
- Financial reports with Spending, Earnings, and Balance views with monthly navigation
- AI Chat with dual backend support: cloud (Koog/OpenAI) and on-device (Llamatik/llama.cpp)
- AI-powered expense category suggestion
- Settings screen with AI engine mode switcher (On-Device, Cloud, Auto), currency picker, and data management
- On-device model download and management with foreground service and progress notifications
- Multi-currency support with device-locale detection
- Material 3 dynamic color theming with dark mode support
- Pull-to-refresh on expense and income lists
- Date picker for expense and income entries
- Delete confirmation dialogs

### Architecture

- Multi-module structure following NowInAndroid pattern
- Metro DI for compile-time dependency injection
- Circuit MVI for Compose-native state management
- SQLDelight for type-safe database queries
- Convention plugins in `build-logic/` for shared build configuration
