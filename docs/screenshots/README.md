# Insight Screenshot Capture Guide

This document lists all required screenshots for the Insight app, the naming convention,
and the exact ADB commands to capture each one.

## Prerequisites

```bash
# 1. Build and install the debug APK
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 2. Source the semantic tapping helpers
source .claude/scripts/adb-tap.sh

# 3. Initial dump
refresh_dump
```

## Naming Convention

```
docs/screenshots/{theme}/{screen_name}_{variant}.png
```

- **theme**: `light`, `dark`, or `accessibility`
- **screen_name**: lowercase snake_case screen identifier
- **variant**: optional state descriptor (e.g., `empty`, `with_data`, `dialog`)

## Theme Switching

```bash
# Switch to dark theme
adb shell cmd uimode night yes

# Switch to light theme
adb shell cmd uimode night no

# Wait for theme transition
sleep 2
refresh_dump
```

## Required Screenshots

### 1. Expenses Screen

| File | State | Description |
|------|-------|-------------|
| `expenses_empty.png` | Empty | Empty state with "No expenses yet" message |
| `expenses_with_data.png` | With items | List showing 3+ expense items with categories |
| `expenses_loading.png` | Loading | Skeleton shimmer loading state |

```bash
# Expenses - Empty state
nav_to "Expenses"
screenshot "expenses_empty"

# Expenses - With data (add expenses first)
tap_by_desc "Add expense"
sleep 1; refresh_dump
# Fill form: amount
tap_by_text "Amount"
sleep 0.5
adb shell input text "42.50"
adb shell input keyevent 111  # dismiss keyboard
sleep 0.5; refresh_dump
# Select category
tap_by_text "Food"
sleep 0.5; refresh_dump
# Save
tap_by_desc "Save"
sleep 1; refresh_dump
# Repeat for more items, then:
nav_to "Expenses"
screenshot "expenses_with_data"
```

### 2. Add/Edit Expense Form

| File | State | Description |
|------|-------|-------------|
| `add_expense_empty.png` | Blank form | Fresh add expense screen |
| `add_expense_filled.png` | Filled form | Form with amount, category selected |
| `add_expense_date_picker.png` | Date picker | DatePicker dialog open |
| `edit_expense.png` | Edit mode | Editing existing expense with delete button |

```bash
# Add Expense - Empty form
nav_to "Expenses"
tap_by_desc "Add expense"
sleep 1; refresh_dump
screenshot "add_expense_empty"

# Add Expense - Filled
tap_by_text "Amount"
sleep 0.5
adb shell input text "25.50"
adb shell input keyevent 111
sleep 0.5; refresh_dump
tap_by_text "Food"
sleep 0.5; refresh_dump
screenshot "add_expense_filled"

# Add Expense - Date picker
tap_by_desc "Select date"
sleep 1; refresh_dump
screenshot "add_expense_date_picker"
tap_by_text "Cancel"
sleep 0.5; refresh_dump

# Navigate back
tap_by_desc "Back"
sleep 1; refresh_dump
```

### 3. Income Screen

| File | State | Description |
|------|-------|-------------|
| `income_empty.png` | Empty | Empty state with "No earnings recorded yet" |
| `income_with_data.png` | With items | List showing income items with type badges |

```bash
# Income - Empty state
nav_to "Income"
screenshot "income_empty"

# Income - With data (add income first, then screenshot)
# After adding items:
nav_to "Income"
screenshot "income_with_data"
```

### 4. Add/Edit Income Form

| File | State | Description |
|------|-------|-------------|
| `add_income_empty.png` | Blank form | Fresh add income screen |
| `add_income_filled.png` | Filled form | Form with type toggle, amount, category |

```bash
# Add Income - Empty form
nav_to "Income"
tap_by_desc "Add income"
sleep 1; refresh_dump
screenshot "add_income_empty"

# Add Income - Filled
tap_by_text "Recurring"
sleep 0.5; refresh_dump
tap_by_text "Amount"
sleep 0.5
adb shell input text "5000"
adb shell input keyevent 111
sleep 0.5; refresh_dump
tap_by_text "Salary"
sleep 0.5; refresh_dump
screenshot "add_income_filled"

# Navigate back
tap_by_desc "Back"
sleep 1; refresh_dump
```

### 5. Reports Screen

| File | State | Description |
|------|-------|-------------|
| `reports_spending.png` | Spending view | Total spending card + category breakdown |
| `reports_spending_empty.png` | Spending empty | "No expenses this month" empty state |
| `reports_earnings.png` | Earnings view | Total earnings card + category breakdown |
| `reports_balance.png` | Balance view | Net balance card with income vs expense |

```bash
# Reports - Spending view (default)
nav_to "Reports"
screenshot "reports_spending"

# Reports - Earnings view
tap_by_text "Earnings"
sleep 1; refresh_dump
screenshot "reports_earnings"

# Reports - Balance view
tap_by_text "Balance"
sleep 1; refresh_dump
screenshot "reports_balance"

# Reports - Month navigation
tap_by_desc "Previous month"
sleep 1; refresh_dump
screenshot "reports_spending_empty"
```

### 6. AI Chat Screen

| File | State | Description |
|------|-------|-------------|
| `ai_chat_empty.png` | Welcome | Welcome message from AI assistant |
| `ai_chat_conversation.png` | With messages | User and AI messages in conversation |
| `ai_chat_typing.png` | AI typing | Typing indicator (bouncing dots) |
| `ai_chat_disabled.png` | Disabled | API key not configured warning |
| `ai_chat_model_setup.png` | Model setup | Bottom sheet for model download |

```bash
# AI Chat - Welcome / empty
nav_to "AI Chat"
screenshot "ai_chat_empty"

# AI Chat - Send a message
tap_by_id "input_chat"
sleep 0.5
adb shell input text "How%smuch%sdid%sI%sspend%sthis%smonth?"
sleep 0.5; refresh_dump
tap_by_desc "Send"
sleep 3; refresh_dump
screenshot "ai_chat_conversation"

# AI Chat - Typing (capture quickly after sending)
tap_by_id "input_chat"
sleep 0.5
adb shell input text "What%sare%smy%stop%scategories?"
tap_by_desc "Send"
sleep 0.5  # capture quickly before response arrives
screenshot "ai_chat_typing"
sleep 3; refresh_dump
```

### 7. Settings Screen

| File | State | Description |
|------|-------|-------------|
| `settings_main.png` | Main view | All settings items visible |
| `settings_ai_local.png` | AI: On-Device | Segmented button with On-Device selected |
| `settings_ai_cloud.png` | AI: Cloud | Segmented button with Cloud selected |
| `settings_ai_auto.png` | AI: Auto | Segmented button with Auto selected |
| `settings_clear_data_dialog.png` | Clear data | Confirmation dialog |
| `settings_currency_picker.png` | Currency | Currency selection dialog |
| `settings_model_setup.png` | Model setup | Model setup bottom sheet |
| `settings_model_ready.png` | Model ready | Model installed state with manage options |

```bash
# Settings - Main view
nav_to "Settings"
screenshot "settings_main"

# Settings - AI mode: On-Device
tap_by_text "On-Device"
sleep 1; refresh_dump
screenshot "settings_ai_local"

# Settings - AI mode: Cloud
tap_by_text "Cloud"
sleep 1; refresh_dump
screenshot "settings_ai_cloud"

# Settings - AI mode: Auto
tap_by_text "Auto"
sleep 1; refresh_dump
screenshot "settings_ai_auto"

# Settings - Clear data confirmation
tap_by_text "Clear All Data"
sleep 1; refresh_dump
screenshot "settings_clear_data_dialog"
tap_by_text "Cancel"
sleep 0.5; refresh_dump

# Settings - Currency picker
tap_by_text "Currency"
sleep 1; refresh_dump
screenshot "settings_currency_picker"
tap_by_text "Cancel"
sleep 0.5; refresh_dump
```

### 8. Delete Confirmation Dialogs

| File | State | Description |
|------|-------|-------------|
| `delete_expense_dialog.png` | Delete expense | "Delete Expense?" confirmation |
| `delete_income_dialog.png` | Delete income | "Delete Income?" confirmation |

```bash
# Delete expense dialog (need existing expense, navigate to edit mode)
nav_to "Expenses"
# Tap on an existing expense item to enter edit mode
# tap_by_text "<expense description>"
# sleep 1; refresh_dump
# tap_by_desc "Delete"
# sleep 1; refresh_dump
# screenshot "delete_expense_dialog"
# tap_by_text "Cancel"
```

## Automated Full Capture Script

Run this to capture all main screens in both themes:

```bash
#!/bin/bash
source .claude/scripts/adb-tap.sh

capture_all_screens() {
  local theme="$1"
  local dir="docs/screenshots/${theme}"

  echo "=== Capturing ${theme} theme ==="

  # Set theme
  if [ "$theme" = "dark" ]; then
    adb shell cmd uimode night yes
  else
    adb shell cmd uimode night no
  fi
  sleep 2
  refresh_dump

  # Main screens
  for tab in "Expenses" "Income" "Reports" "AI Chat" "Settings"; do
    local name=$(echo "$tab" | tr ' ' '_' | tr '[:upper:]' '[:lower:]')
    nav_to "$tab"
    adb shell screencap -p /sdcard/screenshot.png
    adb pull /sdcard/screenshot.png "${dir}/${name}_main.png" 2>/dev/null
    echo "Captured: ${dir}/${name}_main.png"
  done

  # Reports sub-views
  nav_to "Reports"
  tap_by_text "Earnings"
  sleep 1; refresh_dump
  adb shell screencap -p /sdcard/screenshot.png
  adb pull /sdcard/screenshot.png "${dir}/reports_earnings.png" 2>/dev/null

  tap_by_text "Balance"
  sleep 1; refresh_dump
  adb shell screencap -p /sdcard/screenshot.png
  adb pull /sdcard/screenshot.png "${dir}/reports_balance.png" 2>/dev/null

  # Settings sub-states
  nav_to "Settings"
  tap_by_text "On-Device"
  sleep 1; refresh_dump
  adb shell screencap -p /sdcard/screenshot.png
  adb pull /sdcard/screenshot.png "${dir}/settings_ai_local.png" 2>/dev/null

  tap_by_text "Cloud"
  sleep 1; refresh_dump
  adb shell screencap -p /sdcard/screenshot.png
  adb pull /sdcard/screenshot.png "${dir}/settings_ai_cloud.png" 2>/dev/null

  tap_by_text "Auto"
  sleep 1; refresh_dump
  adb shell screencap -p /sdcard/screenshot.png
  adb pull /sdcard/screenshot.png "${dir}/settings_ai_auto.png" 2>/dev/null

  echo "=== ${theme} theme capture complete ==="
}

# Capture both themes
capture_all_screens "light"
capture_all_screens "dark"

echo "All screenshots captured!"
```

## Accessibility Screenshots

For accessibility testing, capture the following with large font enabled:

```bash
# Enable large font scale
adb shell settings put system font_scale 1.5
sleep 2; refresh_dump

# Capture key screens
for tab in "Expenses" "Income" "Reports" "Settings"; do
  local name=$(echo "$tab" | tr ' ' '_' | tr '[:upper:]' '[:lower:]')
  nav_to "$tab"
  adb shell screencap -p /sdcard/screenshot.png
  adb pull /sdcard/screenshot.png "docs/screenshots/accessibility/${name}_large_font.png" 2>/dev/null
done

# Reset font scale
adb shell settings put system font_scale 1.0
```

## Expected Screenshot Count

| Category | Count |
|----------|-------|
| Light theme | ~20 |
| Dark theme | ~20 |
| Accessibility | ~5 |
| **Total** | **~45** |

## Notes

- All commands use the semantic tapping script (`.claude/scripts/adb-tap.sh`)
- Device config is stored in `.claude/device-config.json`
- Use `keyevent 111` (ESCAPE) to dismiss keyboard, NOT `keyevent 4` (BACK)
- `%s` in adb input text represents spaces
- Screenshots are saved as PNG for lossless quality
- Ensure the app has sample data before capturing "with data" screenshots
