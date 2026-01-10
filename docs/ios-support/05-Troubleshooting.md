# Troubleshooting Guide

Common issues and solutions when setting up iOS support.

## Build Errors

### "Unresolved reference: Parcelize"

**Problem:** Using `@Parcelize` in commonMain fails.

**Solution:** Use the expect/actual pattern from core:common:
```kotlin
// WRONG
import kotlinx.parcelize.Parcelize

// CORRECT
import com.keisardev.insight.core.common.parcelize.Parcelize
```

See [02-Parcelize-Pattern.md](./02-Parcelize-Pattern.md) for full setup.

---

### "Task 'embedAndSignAppleFrameworkForXcode' already exists"

**Problem:** You defined a custom task that conflicts with the KMP plugin's built-in task.

**Solution:** Remove your custom task. The Kotlin Multiplatform plugin provides this automatically when you define `iosTarget.binaries.framework`.

---

### "Could not infer iOS target architectures"

**Problem:** Running `embedAndSignAppleFrameworkForXcode` outside of Xcode.

**Solution:** This task requires Xcode environment variables. Either:
1. Run from Xcode (it sets the variables automatically)
2. Or build the framework directly:
   ```bash
   ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
   ```

---

### "Undefined symbols for sqlite3_*"

**Problem:** SQLDelight native driver can't find SQLite symbols.

**Solution:** Add `-lsqlite3` to `OTHER_LDFLAGS` in Xcode:
```
OTHER_LDFLAGS = (
    "$(inherited)",
    "-framework",
    shared,
    "-lsqlite3",
);
```

---

### "Framework not found: shared"

**Problem:** Xcode can't find the shared.framework.

**Solution:**
1. Build the framework first:
   ```bash
   ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
   ```
2. Verify `FRAMEWORK_SEARCH_PATHS` points to the correct location:
   ```
   $(SRCROOT)/../shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)
   ```
3. Clean and rebuild in Xcode.

---

### KSP errors: "Cannot find symbol"

**Problem:** Circuit codegen not running for all targets.

**Solution:** Ensure KSP is configured for ALL targets in the feature convention plugin:
```kotlin
dependencies.add("kspCommonMainMetadata", libs.findLibrary("circuit.codegen").get())
dependencies.add("kspAndroid", libs.findLibrary("circuit.codegen").get())
dependencies.add("kspIosX64", libs.findLibrary("circuit.codegen").get())
dependencies.add("kspIosArm64", libs.findLibrary("circuit.codegen").get())
dependencies.add("kspIosSimulatorArm64", libs.findLibrary("circuit.codegen").get())
```

---

## Runtime Crashes

### App crashes immediately on launch (SIGABRT)

**Problem:** `PlistSanityCheck` fails in Compose Multiplatform.

**Solution:** Add `CADisableMinimumFrameDurationOnPhone` to Info.plist:
```xml
<key>CADisableMinimumFrameDurationOnPhone</key>
<true/>
```

This is REQUIRED for Compose Multiplatform on iOS. The check validates that this key exists for 120Hz ProMotion support.

---

### "NSInvalidArgumentException: MainViewController not found"

**Problem:** Swift can't find the Kotlin MainViewController.

**Solution:**
1. Verify the function is exported correctly:
   ```kotlin
   // shared/src/iosMain/.../MainViewController.kt
   fun MainViewController(): UIViewController = ComposeUIViewController { ... }
   ```
2. Check the Swift import:
   ```swift
   import shared
   MainViewControllerKt.MainViewController()
   ```
   Note: Kotlin top-level functions are accessed via `FileNameKt.functionName()`.

---

### Kotlin linker errors with third-party libraries

**Problem:** Some libraries don't support iOS or have Kotlin version mismatches.

**Example:** Koog AI framework may have linker errors on iOS.

**Solution:**
1. Check if the library supports iOS targets
2. If not, move to Android-only source set:
   ```kotlin
   // Move from core:ai/src/commonMain to core:ai/src/androidMain
   ```
3. Create stub implementations for iOS if needed
4. Don't export the module to iOS in shared/build.gradle.kts

---

## Simulator Issues

### "CoreSimulator version mismatch"

**Problem:** Simulator runtime doesn't match macOS version.

**Solution:**
```bash
# Download latest iOS runtime
xcodebuild -downloadPlatform iOS

# List available runtimes
xcrun simctl list runtimes

# Create simulator with correct runtime
xcrun simctl create "iPhone 16 Pro" \
    "com.apple.CoreSimulator.SimDeviceType.iPhone-16-Pro" \
    "com.apple.CoreSimulator.SimRuntime.iOS-17-0"
```

---

### "No simulator found"

**Problem:** xcodebuild can't find a matching simulator.

**Solution:**
```bash
# List all devices
xcrun simctl list devices

# Find booted device UUID
xcrun simctl list devices | grep Booted

# Specify device by UUID
xcodebuild -destination 'id=DEVICE-UUID-HERE' build
```

---

## Gradle Issues

### "Task already exists" or duplicate task errors

**Problem:** Plugin conflicts or duplicate plugin applications.

**Solution:**
1. Check if plugins are applied multiple times
2. Remove redundant plugin applications
3. Use convention plugins to standardize configuration

---

### iOS targets not building

**Problem:** `./gradlew build` skips iOS targets.

**Solution:** iOS targets need explicit tasks:
```bash
# List all tasks
./gradlew tasks --all | grep ios

# Build specific iOS target
./gradlew :shared:compileKotlinIosSimulatorArm64
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

---

### "Could not resolve dependency"

**Problem:** KMP artifacts not found.

**Solution:** Ensure you're using KMP-compatible artifact names:
```toml
# WRONG (JVM-only)
koog-agents = { module = "ai.koog:koog-agents-jvm" }

# CORRECT (KMP)
koog-agents = { module = "ai.koog:koog-agents" }
```

Check the library's documentation for multiplatform artifact names.

---

## Compose Issues

### Compose APIs not available

**Problem:** Android-only Compose APIs used in commonMain.

**Solution:** Use Compose Multiplatform equivalents:
```kotlin
// WRONG - Android only
import androidx.compose.ui.platform.LocalContext

// CORRECT - Use expect/actual or find multiplatform alternative
```

Common Android-only APIs to avoid in commonMain:
- `LocalContext`
- `BackHandler` (use Circuit's Navigator instead)
- Android-specific modifiers

---

### Theme not applying

**Problem:** Material3 theme looks wrong on iOS.

**Solution:** Ensure theme uses Compose Multiplatform Material3:
```kotlin
// Use compose.material3, not androidx.compose.material3
implementation(compose.material3)
```

---

## Debug Techniques

### View iOS crash logs

```bash
# List recent crash logs
ls -la ~/Library/Logs/DiagnosticReports/*.ips | tail -5

# Read crash log
cat ~/Library/Logs/DiagnosticReports/iosApp-*.ips | head -100
```

### Check if app is running

```bash
# List running processes in simulator
xcrun simctl spawn booted launchctl list | grep bundle.id
```

### Rebuild everything

```bash
# Clean Gradle
./gradlew clean

# Clean Xcode derived data
rm -rf ~/Library/Developer/Xcode/DerivedData/*

# Rebuild
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

### Check framework contents

```bash
# List framework symbols
nm shared/build/bin/iosSimulatorArm64/debugFramework/shared.framework/shared | head -50

# Check framework info
file shared/build/bin/iosSimulatorArm64/debugFramework/shared.framework/shared
```
