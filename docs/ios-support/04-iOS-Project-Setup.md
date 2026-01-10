# iOS Project Setup

This document covers setting up the iOS Xcode project and shared framework.

## Step 1: Create the Shared Module

The shared module is an umbrella module that creates the iOS framework.

### settings.gradle.kts

```kotlin
// Add shared module
include(":shared")
```

### shared/build.gradle.kts

```kotlin
plugins {
    id("insight.kmp.library")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.metro)
}

android {
    namespace = "com.keisardev.insight.shared"
}

kotlin {
    // Configure iOS framework
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "shared"
            isStatic = true

            // Export modules to iOS
            export(project(":core:model"))
            export(project(":core:common"))
            export(project(":core:data"))
            export(project(":core:database"))
            export(project(":core:designsystem"))
            export(project(":core:ui"))
            export(project(":feature:expenses"))
            export(project(":feature:income"))
            export(project(":feature:reports"))
            export(project(":feature:ai-chat"))
            export(project(":feature:settings"))
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Re-export all modules with api()
            api(project(":core:model"))
            api(project(":core:common"))
            api(project(":core:data"))
            api(project(":core:database"))
            api(project(":core:designsystem"))
            api(project(":core:ui"))
            api(project(":core:ai"))
            api(project(":feature:expenses"))
            api(project(":feature:income"))
            api(project(":feature:reports"))
            api(project(":feature:ai-chat"))
            api(project(":feature:settings"))

            // Compose
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)

            // Circuit
            implementation(libs.circuit.foundation)
            implementation(libs.circuitx.gesture.navigation)
        }
    }
}
```

### shared/src/iosMain/kotlin/.../MainViewController.kt

```kotlin
package com.keisardev.insight.shared

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import com.keisardev.insight.core.designsystem.theme.InsightTheme
import com.keisardev.insight.feature.expenses.ExpensesScreen
import com.slack.circuit.backstack.rememberSaveableBackStack
import com.slack.circuit.foundation.Circuit
import com.slack.circuit.foundation.CircuitCompositionLocals
import com.slack.circuit.foundation.NavigableCircuitContent
import com.slack.circuit.foundation.rememberCircuitNavigator
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController {
    val circuit = remember {
        Circuit.Builder()
            // Circuit uses generated presenter/UI factories
            .build()
    }

    val backStack = rememberSaveableBackStack(root = ExpensesScreen)
    val navigator = rememberCircuitNavigator(backStack) {
        // iOS doesn't need to handle root pop
    }

    InsightTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            CircuitCompositionLocals(circuit) {
                NavigableCircuitContent(
                    navigator = navigator,
                    backStack = backStack,
                )
            }
        }
    }
}
```

## Step 2: Create iOS App Directory

```
iosApp/
├── iosApp/
│   ├── iOSApp.swift          # App entry point
│   ├── ContentView.swift     # SwiftUI wrapper
│   └── Info.plist            # App configuration
└── iosApp.xcodeproj/
    ├── project.pbxproj       # Xcode project
    └── xcshareddata/
        └── xcschemes/
            └── iosApp.xcscheme
```

### iosApp/iosApp/iOSApp.swift

```swift
import SwiftUI
import shared

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
```

### iosApp/iosApp/ContentView.swift

```swift
import SwiftUI
import shared

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all)
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
```

### iosApp/iosApp/Info.plist

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleDevelopmentRegion</key>
    <string>$(DEVELOPMENT_LANGUAGE)</string>
    <key>CFBundleExecutable</key>
    <string>$(EXECUTABLE_NAME)</string>
    <key>CFBundleIdentifier</key>
    <string>$(PRODUCT_BUNDLE_IDENTIFIER)</string>
    <key>CFBundleInfoDictionaryVersion</key>
    <string>6.0</string>
    <key>CFBundleName</key>
    <string>$(PRODUCT_NAME)</string>
    <key>CFBundlePackageType</key>
    <string>$(PRODUCT_BUNDLE_PACKAGE_TYPE)</string>
    <key>CFBundleShortVersionString</key>
    <string>1.0</string>
    <key>CFBundleVersion</key>
    <string>1</string>
    <key>LSRequiresIPhoneOS</key>
    <true/>
    <key>UIApplicationSceneManifest</key>
    <dict>
        <key>UIApplicationSupportsMultipleScenes</key>
        <false/>
    </dict>
    <!-- REQUIRED for Compose Multiplatform 120Hz support -->
    <key>CADisableMinimumFrameDurationOnPhone</key>
    <true/>
    <key>UILaunchScreen</key>
    <dict/>
    <key>UISupportedInterfaceOrientations</key>
    <array>
        <string>UIInterfaceOrientationPortrait</string>
        <string>UIInterfaceOrientationLandscapeLeft</string>
        <string>UIInterfaceOrientationLandscapeRight</string>
    </array>
    <key>UISupportedInterfaceOrientations~ipad</key>
    <array>
        <string>UIInterfaceOrientationPortrait</string>
        <string>UIInterfaceOrientationPortraitUpsideDown</string>
        <string>UIInterfaceOrientationLandscapeLeft</string>
        <string>UIInterfaceOrientationLandscapeRight</string>
    </array>
</dict>
</plist>
```

**CRITICAL:** The `CADisableMinimumFrameDurationOnPhone` key is REQUIRED by Compose Multiplatform. Without it, the app will crash on launch with a `PlistSanityCheck` error.

## Step 3: Xcode Project Configuration

### Key Build Settings

In `project.pbxproj`, these settings are critical:

```
// Framework search path (finds shared.framework)
FRAMEWORK_SEARCH_PATHS = (
    "$(inherited)",
    "$(SRCROOT)/../shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)",
);

// Link the shared framework AND SQLite
OTHER_LDFLAGS = (
    "$(inherited)",
    "-framework",
    shared,
    "-lsqlite3",  // Required if using SQLDelight
);

// Deployment target
IPHONEOS_DEPLOYMENT_TARGET = 17.0;

// Disable script sandboxing (for Gradle integration)
ENABLE_USER_SCRIPT_SANDBOXING = NO;
```

### Run Script Build Phase

Add a shell script build phase that runs BEFORE "Compile Sources":

```bash
cd "$SRCROOT/.."
if [ -z "$CONFIGURATION" ]; then
    echo "Not running from Xcode, skipping Gradle build"
    exit 0
fi
./gradlew :shared:embedAndSignAppleFrameworkForXcode
```

This script:
1. Changes to the project root
2. Checks if running from Xcode (CONFIGURATION is set)
3. Runs the Gradle task to build and embed the framework

## Step 4: Build the Framework

Before opening Xcode, build the framework:

```bash
# For simulator (development)
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64

# For device (release)
./gradlew :shared:linkReleaseFrameworkIosArm64

# Or let Xcode handle it via embedAndSignAppleFrameworkForXcode
```

## Step 5: Run from Xcode

1. Open `iosApp/iosApp.xcodeproj` in Xcode
2. Select a simulator (iPhone 15 Pro recommended)
3. Build and Run (Cmd+R)

The Run Script will automatically rebuild the framework when Kotlin code changes.

## Alternative: Command Line Build

```bash
# Build iOS app from command line
xcodebuild -project iosApp/iosApp.xcodeproj \
    -scheme iosApp \
    -sdk iphonesimulator \
    -destination 'platform=iOS Simulator,name=iPhone 15 Pro' \
    build

# Install and launch on simulator
xcrun simctl install booted path/to/iosApp.app
xcrun simctl launch booted com.keisardev.insight
```

## Framework Output Location

After building, the framework is at:

```
shared/build/xcode-frameworks/
├── Debug/
│   ├── iphonesimulator/        # Simulator debug
│   │   └── shared.framework
│   └── iphoneos/               # Device debug
│       └── shared.framework
└── Release/
    ├── iphonesimulator/        # Simulator release
    │   └── shared.framework
    └── iphoneos/               # Device release
        └── shared.framework
```

The `FRAMEWORK_SEARCH_PATHS` setting uses `$(CONFIGURATION)/$(SDK_NAME)` to automatically select the right one.

## Checklist

- [ ] Create `shared` module with framework configuration
- [ ] Create `MainViewController.kt` in iosMain
- [ ] Create `iosApp` directory with Swift files
- [ ] Create `Info.plist` with `CADisableMinimumFrameDurationOnPhone`
- [ ] Configure `project.pbxproj` with framework paths
- [ ] Add SQLite linking (`-lsqlite3`) if using SQLDelight
- [ ] Add Run Script build phase for Gradle
- [ ] Test build from Xcode
