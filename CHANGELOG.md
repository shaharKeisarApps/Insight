# Changelog

All notable changes to Insight will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

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
