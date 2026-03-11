---
name: ios-device-validate
description: >
  iOS Simulator validation for the Insight KMP app. Mirrors the Android device-validate
  skill but uses xcrun simctl instead of ADB. Covers build, install, launch, and
  screenshot-based acceptance testing on iPhone 16 Pro Simulator.
triggers:
  - /ios-validate
  - ios device validation
  - validate ios
  - ios smoke
argument-hint: "[full|smoke|build|install|screenshot]"
version: "1.0.0"
updated: "2026-03-11"
extends: ios-device-validate (claude-flow-kmp base)
---

# iOS Device Validation — Insight App

> Validates the Insight iOS app running on the iPhone 16 Pro Simulator.
> Mirrors the Android `device-validate` skill — same 4-phase structure,
> different tooling (simctl instead of ADB).

## Arguments

Parse `$ARGUMENTS` to determine scope:
- **`full`** (default): All 4 phases
- **`smoke`**: Boot + launch + screenshot only (Phase 2 + V1)
- **`build`**: Framework build + Xcode build only (Phase 1)
- **`install`**: Install + launch only (Phase 2)
- **`screenshot`**: Screenshot tour only (Phase 3 V1)

---

## Device Configuration

| Property | Value |
|----------|-------|
| Device | iPhone 16 Pro Simulator |
| UUID | `19294E3B-EB01-4CAC-8657-351A44894157` |
| OS | iOS 18.4 |
| Screen | 393 x 852 pts (scale 3x → 1179x2556 px) |
| Bundle ID | `com.keisardev.insight.ios` |
| Scheme | `iosApp` |

Full config: `.claude/device-config-ios.json`

---

## Phase 1: Build

### 1a. Build Shared Kotlin Framework

```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

Expected output: `BUILD SUCCESSFUL` and the framework at:
`shared/build/bin/iosSimulatorArm64/debugFramework/shared.framework`

### 1b. Regenerate Xcode Project (if needed)

Run when `project.yml` changes or after adding new source files:

```bash
cd iosApp && xcodegen generate && cd ..
```

### 1c. Build Xcode App

```bash
xcodebuild \
    -project iosApp/iosApp.xcodeproj \
    -scheme iosApp \
    -sdk iphonesimulator \
    -destination 'platform=iOS Simulator,name=iPhone 16 Pro' \
    -configuration Debug \
    build
```

Expected: `** BUILD SUCCEEDED **`

---

## Phase 2: Install and Launch

```bash
source .claude/scripts/simctl-tap.sh

# 1. Boot the simulator (no-op if already booted)
boot_sim

# 2. Install the freshly-built app
install_app   # finds the .app in DerivedData automatically

# 3. Launch
launch_app
sleep 3
```

Verify the app is running:

```bash
xcrun simctl listapps "$SIMCTL_DEVICE" | grep "com.keisardev.insight.ios"
```

---

## Phase 3: Screenshot Validation

```bash
source .claude/scripts/simctl-tap.sh
tour_all
```

Screenshots land in `/tmp/ios-screenshots/`.

For a targeted screenshot after a specific action:

```bash
source .claude/scripts/simctl-tap.sh
launch_app
sleep 3
screenshot "placeholder_screen"
```

---

## Phase 4: Acceptance Criteria

### Current (Placeholder Stage)

- [ ] Simulator boots without error
- [ ] App installs successfully (`xcrun simctl install`)
- [ ] App launches without crash (`xcrun simctl launch` returns 0)
- [ ] Screenshot shows `Insight for iOS` text (or equivalent placeholder)
- [ ] No black screen or immediate crash on cold start
- [ ] InsightTheme renders — check screenshot for Material3 colour scheme

### Future (once Circuit navigation is wired)

- [ ] All tabs visible: Expenses, Income, Reports, Settings
- [ ] Navigation between tabs works
- [ ] Expenses screen shows empty state
- [ ] Income screen shows empty state
- [ ] Reports screen shows zero state
- [ ] Settings screen renders

---

## Interaction Helpers

The `simctl-tap.sh` script provides:

```bash
source .claude/scripts/simctl-tap.sh

boot_sim            # Boot simulator if shutdown
launch_app          # Launch the Insight iOS app
terminate_app       # Force-stop the app
restart_app         # terminate + launch
screenshot "label"  # Save PNG to /tmp/ios-screenshots/label.png
tap 196 426         # Tap at logical-point coordinates
type_text "hello"   # Type into focused field
go_home             # Press home button
go_back             # Press escape / back
tour_all            # Boot + launch + screenshot
build_framework     # Run :shared:linkDebug... via Gradle
build_xcode         # Run xcodebuild
install_app         # Install from DerivedData
```

---

## Known Limitations

| Limitation | Workaround |
|------------|------------|
| No semantic tap-by-text (no uiautomator equivalent) | Use coordinate taps from `.claude/device-config-ios.json` |
| `xcrun simctl io tap` requires Xcode 14+ | AppleScript fallback in `simctl-tap.sh` |
| Accessibility tree dump (`xcrun simctl ui`) requires Xcode 15+ | Best-effort; fallback to screenshots |
| AI features not available on iOS (`core/ai` is Android-only) | Not tested on iOS |
| On-device model setup not available on iOS | Not tested on iOS |
| Circuit DI graph not yet wired for iOS | Placeholder screen only |

---

## Troubleshooting

### Simulator not found

```bash
xcrun simctl list devices | grep "19294E3B"
# If missing, create it:
xcrun simctl create "iPhone 16 Pro" "com.apple.CoreSimulator.SimDeviceType.iPhone-16-Pro" "com.apple.CoreSimulator.SimRuntime.iOS-18-4"
```

### App not installing

```bash
# Verify framework was built
ls shared/build/bin/iosSimulatorArm64/debugFramework/

# Verify Xcode build succeeded
find ~/Library/Developer/Xcode/DerivedData -name "iosApp.app" -path "*/Debug-iphonesimulator/*"
```

### xcrun simctl io tap not available

Use `tap()` in `simctl-tap.sh` — it auto-falls back to AppleScript mouse click.
Alternatively pass the Simulator app to foreground and tap manually during validation.

### App launches but shows wrong screen

Cold restart:
```bash
source .claude/scripts/simctl-tap.sh
terminate_app && sleep 1 && launch_app
```

---

## Relationship to Android Validation

| Aspect | Android (`device-validate`) | iOS (`ios-device-validate`) |
|--------|----------------------------|----------------------------|
| Tool | `adb` | `xcrun simctl` |
| Semantic taps | `tap_by_text`, `tap_by_desc` via uiautomator XML | Coordinate taps (no XML dump) |
| Screenshots | `adb shell screencap` | `xcrun simctl io screenshot` |
| App install | `adb install` | `xcrun simctl install` |
| Launch | `adb shell am start` | `xcrun simctl launch` |
| Coordinates | Physical pixels (1080x2404) | Logical points (393x852) |
| CI integration | GitHub Actions `android` runner | GitHub Actions `macos` runner |

Run both validations for dual-platform confidence:

```bash
# Android
source .claude/scripts/adb-tap.sh && tour_all

# iOS
source .claude/scripts/simctl-tap.sh && tour_all
```
