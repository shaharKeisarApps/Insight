---
name: device-validate
description: >
  Device validation for the Insight personal finance app. Extends the base KMP device-validate
  skill with Insight-specific screens, coordinates, data models, and product acceptance tests.
  Orchestrates M3 compliance, Compose patterns, product acceptance, runtime adb testing,
  and accessibility checks.
argument-hint: "[full|keyboard|design|accessibility|layout|product|smoke]"
version: "2.0.0"
updated: "2026-02-15"
extends: device-validate (claude-flow-kmp base)
---

# Device Validation — Insight App

> **Base skill**: This extends the generic KMP `device-validate` from `claude-flow-kmp`.
> Follow the same 4-phase structure (Code Analysis → Product Acceptance → Runtime Measurement → Cross-Reference Report).
> This file provides **Insight-specific configuration** that plugs into the base framework.

## Arguments

Parse `$ARGUMENTS` to determine scope:
- **`full`** (default): All 4 phases
- **`smoke`**: Quick smoke — P1a (add expense) → P1c (check reports) → V1 (screens render) → V2 (keyboard)
- **`product`**: Full product acceptance (Phase 2 all scenarios + Phase 3)
- **`keyboard`**: IME spacing only (Phase 1 insets review + Phase 3 V2)
- **`design`**: M3 compliance only (Phase 1a)
- **`accessibility`**: A11y only (Phase 1 accessibility + Phase 3 V4)
- **`layout`**: Layout spacing only (Phase 1 layout + Phase 3 V3)
- **`code`**: Code analysis only, no device (Phase 1)

---

## Project Context

- **Package**: `com.keisardev.insight`
- **Activity**: `.MainActivity`
- **Screen resolution**: 1080x2404 @ 390dpi
- **Architecture**: NavigationSuiteScaffold (80dp bottom nav) wraps Circuit screens
- **Edge-to-edge**: `enableEdgeToEdge()` + `android:windowSoftInputMode="adjustNothing"`
- **DI**: Metro (compile-time) with `@AssistedInject` presenters
- **MVI**: Circuit with `Presenter<State>` + `@CircuitInject` composables

### Known Gotcha: imePadding() Double-Count

`imePadding()` inside NavigationSuiteScaffold measures from window bottom (y=2404), but content sits above the nav bar (y=2150). The 254px difference (195px M3 NavigationBar + 59px system gesture bar) causes over-padding. **Fix**: Manual IME calculation subtracting both system nav bar AND 80dp M3 NavigationBar (already applied in `AiChatScreen.kt`).

---

## UI Interaction Strategy

### PREFERRED: Semantic Tapping (device-independent)

Use `.claude/scripts/adb-tap.sh` helper functions instead of hardcoded coordinates.
These find elements by `content-desc`, `text`, or `resource-id` in the uiautomator XML dump,
then tap the center of the element's bounds. This works regardless of device, DPI, or layout direction.

```bash
source .claude/scripts/adb-tap.sh
refresh_dump

# Navigate to tabs
tap_by_text "Settings"    # Tap by visible text label
tap_by_text "AI Chat"
tap_by_text "Expenses"

# Tap by content description
tap_by_desc "Add expense"  # FAB
tap_by_desc "Send"         # Chat send button

# Assert elements exist
assert_text_exists "AI Engine"
assert_desc_exists "Add income"

# Navigate and screenshot
nav_to "Reports"           # tap + sleep + refresh
screenshot "reports"       # saves to /tmp/insight_reports_*.png

# Tour all screens
tour_all                   # visits all 5 tabs, screenshots each
```

### Navigation Tab Labels
Tabs have `text` attributes in uiautomator: `"Settings"`, `"AI Chat"`, `"Reports"`, `"Income"`, `"Expenses"`.

### Screen Element Identifiers (content-desc / text)

**Expenses**: FAB=`desc:"Add expense"`, list items have `text` with amount/category
**Income**: FAB=`desc:"Add income"`, badges=`text:"Recurring"` or `text:"One-time"`
**AI Chat**: Send=`desc:"Send"`, Back=`desc:"Back"`, input has hint text
**Settings**: AI segments=`text:"On-Device"`, `text:"Cloud"`, `text:"Auto"`; Clear=`text:"Clear All Data"`
**Reports**: View chips=`text:"Spending"`, `text:"Earnings"`, `text:"Balance"`
**Forms**: Categories by `text` (e.g., `text:"Food"`), Save by `text` (e.g., `text:"Add Expense"`)

### FALLBACK: Pixel Coordinates (Pixel 10 Pro XL, RTL, 1080x2404 @ 390dpi)

Only use these if `xmllint` is unavailable or semantic tapping fails.
Full fallback coordinates are in `.claude/device-config.json` under `fallback_coordinates`.

| Tab | X | Y |
|-----|---|---|
| Settings | 100 | 2248 |
| AI Chat | 320 | 2248 |
| Reports | 540 | 2248 |
| Income | 760 | 2248 |
| Expenses | 980 | 2248 |

AI Engine segments: On-Device x=848,y=935; Cloud x=540,y=935; Auto x=233,y=935

---

## Critical Files

| Screen | File | Key Concerns |
|--------|------|--------------|
| Expenses | `feature/expenses/.../ExpensesScreen.kt` | FAB, list animations, empty state, pull-to-refresh |
| Add/Edit Expense | `feature/expenses/.../AddEditExpenseScreen.kt` | Form validation, category chips, date picker |
| Income | `feature/income/.../IncomeScreen.kt` | Same as Expenses + income type badge |
| Add/Edit Income | `feature/income/.../AddEditIncomeScreen.kt` | Recurring/One-time toggle |
| Reports | `feature/reports/.../ReportsScreen.kt` | Month nav, Spending/Earnings/Balance views |
| AI Chat | `feature/ai-chat/.../AiChatScreen.kt` | IME padding (manual calc), message list, send |
| Settings | `feature/settings/.../SettingsScreen.kt` | Clear data, coming-soon items |
| Theme | `core/designsystem/.../Theme.kt` | InsightTheme, color scheme, typography |
| Category Icons | `core/ui/.../CategoryIcon.kt` | Icon mapping (AutoMirrored.Filled.TrendingUp) |
| Main Nav | `app/.../MainActivity.kt` | NavigationSuiteScaffold, edge-to-edge |
| AI Tools | `core/ai/.../tools/ExpenseTools.kt` | getTotalExpenses, searchExpenses, etc. |
| Repositories | `core/data/.../` | ExpenseRepository, IncomeRepository, ChatRepository |

---

## Phase 1: Code Analysis (Subagents in Parallel)

Launch these using the Task tool **in parallel**.

### 1a. Material Design 3 Compliance
**Subagent**: `material-design-expert`
**Prompt**: Review the Insight app's Compose UI files for M3 compliance. Check:
- Correct M3 component usage (Surface, Card, Scaffold, NavigationBar)
- Proper color token usage (no hardcoded colors, correct semantic roles)
- Typography scale adherence (bodyLarge, titleMedium, etc.)
- Spacing consistency (M3 scale: 4, 8, 12, 16, 24, 32dp)
- Edge-to-edge and window insets (manual IME padding in AiChatScreen.kt)
- Dark mode support (uses `MaterialTheme.colorScheme` throughout)
- `InsightTheme` wraps `MaterialTheme` correctly

Files: All composable files in `feature/` and `core/designsystem/`.
Report: CRITICAL/WARNING/INFO with file:line.

### 1b. Compose Pattern Analysis
**Subagent**: `code-reviewer`
**Prompt**: Review Compose best practices in the Insight app:
- `Modifier` parameter on all public composables
- State hoisting (state lives in Circuit Presenters, not in composables)
- Recomposition stability (`@Immutable`/`@Stable` on Circuit State/Event classes)
- LazyColumn key usage for expenses, income items, chat messages
- `collectAsRetainedState` for Circuit state collection
- No side effects in composition scope
- Proper `remember`/`derivedStateOf` usage

Files: All feature/ composable files.

### 1c. Accessibility Review
**Subagent**: `code-reviewer`
**Prompt**: Review accessibility in Insight's Compose UI:
- All `Icon()` calls have `contentDescription` (null for decorative only)
- Category chip icons labeled, FAB labeled
- Touch targets >= 48dp on all interactive elements
- Send button in AI Chat has `content-desc="Send"`
- Navigation items have proper labels
- Screen titles marked as headings
- Empty states provide meaningful screen reader feedback

Files: All `@Composable` functions in feature/ and core/ui/.

### 1d. Business Logic Review
**Subagent**: `code-reviewer`
**Prompt**: Review Insight's business logic:
- **Expense validation**: amount > 0, category required, description max 100 chars, date required
- **Income validation**: amount > 0, income type (RECURRING/ONE_TIME) required, category required
- **Reports date ranges**: month boundaries (first day to last day inclusive)
- **Financial summary**: netBalance = totalIncome - totalExpenses, savingsRate = netBalance/totalIncome*100
- **AI tools**: ExpenseTools date parsing correct, searchExpenses case-insensitive
- **Cross-screen reactivity**: Repositories use Flow, presenters use collectAsRetainedState
- **Category seeding**: 7 expense + 7 income categories on first launch
- **Clear data**: Only clears expenses (NOT income) — verify intentional

Files: Presenters in feature/, repositories in core/data/, ExpenseTools in core/ai/.

---

## Phase 2: Product Acceptance Testing

**Prerequisite**: `adb devices` shows connected device. Skip gracefully if none.

### Test Data Setup (full/product runs only)
```bash
source .claude/scripts/adb-tap.sh
adb shell am start -n com.keisardev.insight/.MainActivity && sleep 2
nav_to "Settings"
tap_by_text "Clear All Data" && sleep 0.5
refresh_dump
tap_by_text "Clear" && sleep 1  # Confirm dialog
```

### P1: Expense CRUD Flow

**P1a. Add Expense**
```bash
nav_to "Expenses"
assert_text_exists "No expenses yet"       # Verify empty state
tap_by_desc "Add expense" && sleep 1       # FAB
# Fill form using text-based tapping
refresh_dump
tap_by_text "Amount" && sleep 0.3 && adb shell input text "42.50"
adb shell input keyevent 111 && sleep 0.3  # Dismiss keyboard (ESCAPE)
refresh_dump
tap_by_text "Description" && sleep 0.3 && adb shell input text "TestLunch"
adb shell input keyevent 111 && sleep 0.3
refresh_dump
tap_by_text "Food" && sleep 0.3            # Category
tap_by_text "Add Expense" && sleep 1       # Save
```
**Verify**: `refresh_dump && assert_text_exists "$42.50" && assert_text_exists "Food"`

**P1b. Edit Expense**
- Tap expense item (get bounds from dump) → change amount to "55.00" → Save
- **Verify**: `assert_text_exists "$55.00"`

**P1c. Cross-Screen: Reports**
```bash
nav_to "Reports"
```
**Verify**: `assert_text_exists "$55.00"` (or "$42.50"), Food 100%, Spending view active.

**P1d. Cross-Screen: AI Chat**
```bash
nav_to "AI Chat"
refresh_dump
tap_by_desc "Send" # First find/tap the input area nearby
# Type message
adb shell input text "How%smuch%sdid%sI%sspend" && sleep 0.3
tap_by_desc "Send" && sleep 5              # Send + wait for AI
```
**Verify**: AI response mentions expense amount and "Food" category.

### P2: Income CRUD Flow

**P2a. Add One-Time Income**
```bash
nav_to "Income"
tap_by_desc "Add income" && sleep 1              # FAB
refresh_dump
# One-time selected by default
tap_by_text "Amount" && sleep 0.3 && adb shell input text "3000"
adb shell input keyevent 111 && sleep 0.3
refresh_dump
tap_by_text "Salary" && sleep 0.3                # Category
tap_by_text "Add Income" && sleep 1              # Save
```
**Verify**: `assert_text_exists "$3,000.00"` + `assert_text_exists "One-time"` + `assert_text_exists "Salary"`

**P2b. Add Recurring Income**
```bash
tap_by_desc "Add income" && sleep 1
refresh_dump
tap_by_text "Recurring" && sleep 0.3             # Toggle type
tap_by_text "Amount" && sleep 0.3 && adb shell input text "500"
adb shell input keyevent 111 && sleep 0.3
refresh_dump
tap_by_text "Freelance" && sleep 0.3
tap_by_text "Add Income" && sleep 1
```
**Verify**: `assert_text_exists "$500.00"` + `assert_text_exists "Recurring"` + `assert_text_exists "Freelance"`

**P2c. Reports Earnings**
```bash
nav_to "Reports"
tap_by_text "Earnings" && sleep 1
```
**Verify**: `assert_text_exists "$3,500.00"`, Salary + Freelance breakdown.

**P2d. Reports Balance**
```bash
tap_by_text "Balance" && sleep 1
```
**Verify**: Net balance = $3,500 - $55 = $3,445, positive indicator, savings rate shown.

### P3: Month Navigation
- Previous month arrow → month label changes → totals $0 → Next month → totals restore.

### P4: Clear Data Cascade
```bash
nav_to "Settings"
tap_by_text "Clear All Data" && sleep 0.5
refresh_dump
tap_by_text "Clear" && sleep 1                   # Confirm dialog
```
**Verify across screens**:
- Expenses: empty state "No expenses yet"
- Reports Spending: $0 or empty
- Reports Balance: net = total income (expenses cleared)
- AI Chat: expense query returns zero
- Income: **STILL HAS DATA** (income NOT cleared)

### P5: AI Chat Disabled State
If no API key: shows disabled message, no input field, no crash.

### P6: Form Validation
- Amount = 0 → Add button disabled
- No category → Add button disabled
- Long description (>100 chars) → truncated
- Invalid amount "1.2.3" → filtered to valid number

---

## Phase 3: Runtime Technical Validation

### V1: All Screens Render
```bash
source .claude/scripts/adb-tap.sh
tour_all  # Visits all 5 tabs, screenshots each to /tmp/insight_*.png
```
**Check**: 5 screens render, no crash, InsightTheme applied.

### V2: Keyboard Spacing — QUANTITATIVE
```bash
nav_to "AI Chat"
# Tap input area to open keyboard (use hint text or desc)
refresh_dump && tap_by_desc "Send"  # Nearby the input
sleep 1.5
```
Measure: `keyboard_top - textfield_bottom` in px → convert to dp (×160/390).
**Threshold**: <= 24dp → PASS.
**Root cause if fails**: Manual IME calc in AiChatScreen.kt not subtracting enough.

### V3: Layout Spacing — QUANTITATIVE
Per screen: dump UI, find views >500px wide AND >300px tall with no text/content-desc.
**Threshold**: 0 unexplained empty views.

### V4: Accessibility Scan
Per screen: dump UI, find `clickable=true` nodes missing `content-desc` AND `text`.
**Threshold**: 0 missing labels.

### V5: Touch Target Size
Parse all `clickable=true` bounds, check >= 48dp (117px at 390dpi).
**Threshold**: All targets >= 48x48dp.

---

## Phase 4: Cross-Reference Report

```markdown
# Insight Validation Report — [date]

## Summary
- Critical: X issues (confirmed by code + runtime)
- Warning: Y issues
- Product: X/Y tests passed
- Info: Z findings

## Product Acceptance
| Test | Expected | Actual | Result |
|------|----------|--------|--------|
| P1a: Add Expense | "$42.50" in list | ... | PASS/FAIL |
| P1b: Edit Expense | "$55.00" in list | ... | PASS/FAIL |
| P1c: Reports Spending | "$55.00" total | ... | PASS/FAIL |
| P1d: AI sees expense | mentions amount | ... | PASS/FAIL |
| P2a: One-Time Income | "$3,000" + badge | ... | PASS/FAIL |
| P2b: Recurring Income | "$500" + badge | ... | PASS/FAIL |
| P2c: Reports Earnings | "$3,500" total | ... | PASS/FAIL |
| P2d: Reports Balance | positive, green | ... | PASS/FAIL |
| P3: Month navigation | $0 in prev month | ... | PASS/FAIL |
| P4: Clear data cascade | expenses empty, income kept | ... | PASS/FAIL |
| P5: AI disabled state | no crash, message | ... | PASS/FAIL |
| P6: Form validation | prevents invalid save | ... | PASS/FAIL |

## Design Compliance
[From Phase 1a]

## Code Quality
[From Phase 1b-1d]

## Technical Measurements
| Check | Value | Threshold | Result |
|-------|-------|-----------|--------|
| Keyboard gap (AI Chat) | Xdp | <=24dp | PASS/FAIL |
| Empty views | N found | 0 | PASS/WARN |
| Missing a11y labels | N found | 0 | PASS/FAIL |
| Small touch targets | N found | 0 | PASS/WARN |

## Cross-Referenced Issues
[Issues confirmed by BOTH code analysis AND runtime evidence]

## Recommendations
[Prioritized: CRITICAL → WARNING → INFO]
```

---

## Data Models

### Expense
`amount: Double (>0)`, `description: String (0-100 chars)`, `category: Category (required)`, `date: LocalDate`

### Income
`amount: Double (>0)`, `incomeType: RECURRING | ONE_TIME`, `category: IncomeCategory (required)`, `date: LocalDate`

### Default Categories (7 each)
- **Expense**: Food, Transport, Entertainment, Shopping, Bills, Health, Other
- **Income**: Salary, Freelance, Investments, Rental, Gifts, Bonus, Other

### Cross-Screen Data Flow
```
Add/Edit Expense ──→ Expenses list
                 ──→ Reports Spending/Balance
                 ──→ AI Chat (via ExpenseTools)

Add/Edit Income  ──→ Income list
                 ──→ Reports Earnings/Balance

Clear All Data   ──→ Expenses → empty
                 ──→ Reports Spending → $0
                 ──→ Reports Balance → income only
                 ──→ Income → UNCHANGED
```

### AI Chat Tools (ExpenseTools)
| Tool | Purpose |
|------|---------|
| `getTotalExpenses(start, end)` | Sum in date range |
| `getExpensesByCategory(start, end)` | Breakdown with percentages |
| `getRecentExpenses(count)` | Last N expenses |
| `searchExpenses(keyword)` | Search description/category |
| `getCategories()` | List all categories |

---

## Important Notes

- Use `keyevent 111` (ESCAPE) to dismiss keyboard, NOT `keyevent 4` (BACK) — BACK triggers Navigator.pop() in Circuit
- `%s` in `adb shell input text` represents spaces
- Always verify data with `uiautomator dump`, not just screenshots
- Read `memory/device-validation.md` for latest coordinates if this file is stale
- NavigationSuiteScaffold's 80dp nav bar must be accounted for in all inset calculations
