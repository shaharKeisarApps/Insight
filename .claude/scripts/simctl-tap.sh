#!/bin/bash
# simctl-tap.sh — iOS Simulator interaction helpers for device validation
# Mirrors adb-tap.sh but uses xcrun simctl instead of ADB.
#
# Usage:
#   source .claude/scripts/simctl-tap.sh
#   boot_sim
#   launch_app
#   screenshot "home"
#   tour_all

# ---------------------------------------------------------------------------
# Configuration — override via environment before sourcing
# ---------------------------------------------------------------------------
SIMCTL_DEVICE="${SIMCTL_DEVICE:-19294E3B-EB01-4CAC-8657-351A44894157}"
BUNDLE_ID="${BUNDLE_ID:-com.keisardev.insight.ios}"
SCREENSHOT_DIR="${SCREENSHOT_DIR:-/tmp/ios-screenshots}"

mkdir -p "$SCREENSHOT_DIR"

echo "simctl-tap.sh loaded."
echo "  Device : $SIMCTL_DEVICE"
echo "  Bundle : $BUNDLE_ID"
echo "  Screenshots -> $SCREENSHOT_DIR"
echo "  Commands: boot_sim, launch_app, terminate_app, screenshot, tap, type_text, go_home, tour_all"

# ---------------------------------------------------------------------------
# Simulator lifecycle
# ---------------------------------------------------------------------------

# Boot the simulator if it is currently shut down.
boot_sim() {
    local state
    state=$(xcrun simctl list devices | grep "$SIMCTL_DEVICE" | grep -oE "\(Booted\)|\(Shutdown\)")
    if [ "$state" = "(Shutdown)" ]; then
        echo "Booting simulator $SIMCTL_DEVICE ..."
        xcrun simctl boot "$SIMCTL_DEVICE"
        sleep 5
        open -a Simulator 2>/dev/null || true
        echo "Simulator booted."
    else
        echo "Simulator already running (state: ${state:-unknown})."
    fi
}

# Launch the app and wait for it to appear.
launch_app() {
    echo "Launching $BUNDLE_ID ..."
    xcrun simctl launch "$SIMCTL_DEVICE" "$BUNDLE_ID" 2>/dev/null
    sleep 2
}

# Terminate (force-stop) the app.
terminate_app() {
    echo "Terminating $BUNDLE_ID ..."
    xcrun simctl terminate "$SIMCTL_DEVICE" "$BUNDLE_ID" 2>/dev/null
    sleep 0.5
}

# Cold restart: terminate → launch.
restart_app() {
    terminate_app
    sleep 1
    launch_app
}

# ---------------------------------------------------------------------------
# Screenshot
# ---------------------------------------------------------------------------

# Take a PNG screenshot.
# Usage: screenshot [label]
# Output: $SCREENSHOT_DIR/<label>.png   (label defaults to "screenshot_<epoch>")
screenshot() {
    local name="${1:-screenshot_$(date +%s)}"
    local dest="$SCREENSHOT_DIR/${name}.png"
    xcrun simctl io "$SIMCTL_DEVICE" screenshot "$dest" 2>/dev/null
    echo "Screenshot saved: $dest"
}

# ---------------------------------------------------------------------------
# Input — coordinates (points, not pixels)
# ---------------------------------------------------------------------------

# Tap at logical-point coordinates.
# Usage: tap <x> <y>
tap() {
    local x=$1
    local y=$2
    # simctl io sendkey does not support tap; use xcrun simctl io tap (available Xcode 14+).
    xcrun simctl io "$SIMCTL_DEVICE" tap "$x" "$y" 2>/dev/null && return 0
    # Fallback: AppleScript send-event (works for Simulator window in foreground).
    echo "WARN: xcrun simctl io tap unavailable; trying AppleScript fallback."
    osascript -e "
        tell application \"Simulator\"
            activate
        end tell
        delay 0.3
        tell application \"System Events\"
            tell process \"Simulator\"
                click at {$x, $y}
            end tell
        end tell
    " 2>/dev/null || echo "FAIL: both tap methods unavailable at ($x, $y)"
    sleep 0.5
}

# Type text into the currently focused field.
# Usage: type_text "hello world"
type_text() {
    local text="$1"
    xcrun simctl io "$SIMCTL_DEVICE" sendText "$text" 2>/dev/null && return 0
    # Fallback: keyboard events via AppleScript.
    osascript -e "
        tell application \"System Events\"
            keystroke \"$text\"
        end tell
    " 2>/dev/null
    sleep 0.3
}

# Press the Home button (sends the home hardware key via simctl).
go_home() {
    xcrun simctl io "$SIMCTL_DEVICE" sendkey home 2>/dev/null
    sleep 0.5
}

# Press the back/escape key (useful for dismissing overlays).
go_back() {
    xcrun simctl io "$SIMCTL_DEVICE" sendkey escape 2>/dev/null || \
        osascript -e 'tell application "System Events" to key code 53' 2>/dev/null
    sleep 0.5
}

# ---------------------------------------------------------------------------
# Accessibility-tree helpers
# Note: iOS does not expose a direct XML dump like uiautomator.
# These helpers use xcrun simctl ui to query the accessibility hierarchy
# (requires Xcode 15+ and --accessibility flag on the simulator process).
# If unavailable they fall back to screenshot-only validation.
# ---------------------------------------------------------------------------

# Dump accessibility tree to /tmp/ios_ax_dump.txt (best-effort).
_refresh_ax() {
    xcrun simctl ui "$SIMCTL_DEVICE" accessibility 2>/dev/null > /tmp/ios_ax_dump.txt || \
        echo "(accessibility dump unavailable — visual inspection only)" > /tmp/ios_ax_dump.txt
}

# Assert that a given string appears in the accessibility tree.
# Usage: assert_text_exists "My Text"
assert_text_exists() {
    local text="$1"
    _refresh_ax
    if grep -q "$text" /tmp/ios_ax_dump.txt 2>/dev/null; then
        echo "  PASS: '$text' found in accessibility tree"
        return 0
    else
        echo "  WARN: '$text' not found in accessibility tree (may still be visible — check screenshot)"
        return 1
    fi
}

# ---------------------------------------------------------------------------
# Validation tour
# ---------------------------------------------------------------------------

# Tour the app: boot, launch, wait, screenshot.
# Extend this function as more screens are added to the iOS app.
tour_all() {
    echo "=== iOS Validation Tour ==="
    boot_sim
    launch_app
    sleep 3
    screenshot "01_launch"

    echo ""
    echo "Current iOS app is at placeholder stage."
    echo "Screenshots saved to: $SCREENSHOT_DIR/"
    echo ""
    echo "Expected on-screen: 'Insight for iOS' placeholder text"
    echo "=== Tour complete ==="
}

# ---------------------------------------------------------------------------
# Build helpers (convenience — run these before install)
# ---------------------------------------------------------------------------

# Build the shared Kotlin framework for the iOS Simulator.
build_framework() {
    local project_root
    project_root="$(git -C "$(dirname "$BASH_SOURCE[0]")" rev-parse --show-toplevel 2>/dev/null || pwd)"
    echo "Building shared framework from: $project_root"
    "$project_root/gradlew" -p "$project_root" :shared:linkDebugFrameworkIosSimulatorArm64
}

# Build the Xcode app (after framework is built).
build_xcode() {
    local project_root
    project_root="$(git -C "$(dirname "$BASH_SOURCE[0]")" rev-parse --show-toplevel 2>/dev/null || pwd)"
    xcodebuild \
        -project "$project_root/iosApp/iosApp.xcodeproj" \
        -scheme iosApp \
        -sdk iphonesimulator \
        -destination "platform=iOS Simulator,name=iPhone 16 Pro" \
        -configuration Debug \
        build
}

# Install the built app bundle onto the simulator.
install_app() {
    local app_path
    app_path=$(find ~/Library/Developer/Xcode/DerivedData -name "iosApp.app" \
        -path "*/Debug-iphonesimulator/*" 2>/dev/null | head -n 1)
    if [ -z "$app_path" ]; then
        echo "FAIL: iosApp.app not found in DerivedData. Run build_xcode first."
        return 1
    fi
    echo "Installing: $app_path"
    xcrun simctl install "$SIMCTL_DEVICE" "$app_path"
    echo "Install complete."
}
