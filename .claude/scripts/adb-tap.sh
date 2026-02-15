#!/bin/bash
# adb-tap.sh — Semantic UI interaction helpers for ADB device validation
# Source this file, then use tap_by_desc/tap_by_text/tap_by_id functions
# instead of hardcoded pixel coordinates.
#
# Usage:
#   source .claude/scripts/adb-tap.sh
#   refresh_dump
#   tap_by_text "Settings"
#   sleep 1
#   refresh_dump
#   tap_by_text "Cloud"

ADB_TAP_DUMP=""
ADB_TAP_DUMP_FILE="/tmp/insight_ui_dump.xml"

# Refresh the UI hierarchy dump. Call before a sequence of taps if the UI changed.
refresh_dump() {
  adb shell uiautomator dump /sdcard/ui_dump.xml 2>/dev/null
  adb pull /sdcard/ui_dump.xml "$ADB_TAP_DUMP_FILE" 2>/dev/null
  ADB_TAP_DUMP=$(cat "$ADB_TAP_DUMP_FILE" 2>/dev/null)
}

# Parse bounds "[x1,y1][x2,y2]" and tap the center
_tap_bounds() {
  local bounds="$1"
  local label="$2"
  if [ -z "$bounds" ]; then
    echo "FAIL: '${label}' not found"
    return 1
  fi
  local x1 y1 x2 y2
  read -r x1 y1 x2 y2 <<< "$(echo "$bounds" | sed 's/\[//g; s/\]/ /g; s/,/ /g')"
  local cx=$(( (x1 + x2) / 2 ))
  local cy=$(( (y1 + y2) / 2 ))
  echo "OK: Tapping '${label}' at (${cx}, ${cy})"
  adb shell input tap "$cx" "$cy"
}

# Tap element by content-desc attribute (best for icons with contentDescription)
tap_by_desc() {
  [ -z "$ADB_TAP_DUMP" ] && refresh_dump
  local desc="$1"
  local bounds=$(echo "$ADB_TAP_DUMP" | xmllint --xpath "string(//node[@content-desc='${desc}']/@bounds)" - 2>/dev/null)
  _tap_bounds "$bounds" "desc=${desc}"
}

# Tap element by visible text (best for labels, buttons, tabs)
tap_by_text() {
  [ -z "$ADB_TAP_DUMP" ] && refresh_dump
  local text="$1"
  local bounds=$(echo "$ADB_TAP_DUMP" | xmllint --xpath "string(//node[@text='${text}']/@bounds)" - 2>/dev/null)
  if [ -z "$bounds" ]; then
    # Try finding parent clickable element containing this text
    local parent_bounds=$(echo "$ADB_TAP_DUMP" | xmllint --xpath "string(//node[@text='${text}']/ancestor::node[@clickable='true'][1]/@bounds)" - 2>/dev/null)
    _tap_bounds "$parent_bounds" "text=${text} (parent)"
  else
    _tap_bounds "$bounds" "text=${text}"
  fi
}

# Tap element by resource-id (requires testTag + testTagsAsResourceId)
tap_by_id() {
  [ -z "$ADB_TAP_DUMP" ] && refresh_dump
  local id="$1"
  local bounds=$(echo "$ADB_TAP_DUMP" | xmllint --xpath "string(//node[@resource-id='${id}']/@bounds)" - 2>/dev/null)
  _tap_bounds "$bounds" "id=${id}"
}

# Assert an element with given text exists on screen
assert_text_exists() {
  [ -z "$ADB_TAP_DUMP" ] && refresh_dump
  local text="$1"
  local count=$(echo "$ADB_TAP_DUMP" | xmllint --xpath "count(//node[@text='${text}'])" - 2>/dev/null)
  if [ "$count" -gt 0 ] 2>/dev/null; then
    echo "  PASS: text='${text}' found"
    return 0
  else
    echo "  FAIL: text='${text}' NOT found"
    return 1
  fi
}

# Assert an element with given content-desc exists on screen
assert_desc_exists() {
  [ -z "$ADB_TAP_DUMP" ] && refresh_dump
  local desc="$1"
  local count=$(echo "$ADB_TAP_DUMP" | xmllint --xpath "count(//node[@content-desc='${desc}'])" - 2>/dev/null)
  if [ "$count" -gt 0 ] 2>/dev/null; then
    echo "  PASS: desc='${desc}' found"
    return 0
  else
    echo "  FAIL: desc='${desc}' NOT found"
    return 1
  fi
}

# Screenshot current screen to /tmp with a label
screenshot() {
  local label="${1:-screen}"
  local timestamp=$(date +%Y%m%d_%H%M%S)
  local file="/tmp/insight_${label}_${timestamp}.png"
  adb shell screencap -p /sdcard/screenshot.png
  adb pull /sdcard/screenshot.png "$file" 2>/dev/null
  echo "Screenshot: ${file}"
}

# Navigate to a specific tab by its label text
nav_to() {
  local tab="$1"
  refresh_dump
  tap_by_text "$tab"
  sleep 1.5
  refresh_dump
}

# Quick smoke: visit all tabs and screenshot
tour_all() {
  local tabs=("Settings" "AI Chat" "Reports" "Income" "Expenses")
  for tab in "${tabs[@]}"; do
    echo "--- ${tab} ---"
    nav_to "$tab"
    screenshot "$(echo "$tab" | tr ' ' '_' | tr '[:upper:]' '[:lower:]')"
  done
  echo "Tour complete."
}
