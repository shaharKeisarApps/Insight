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

## Navigation Coordinates (Device: 1080x2404 @ 390dpi)

Nav bar bounds: `[0,2150][1080,2345]` — center y=2248

| Tab | Bounds | Tap X | Tap Y |
|-----|--------|-------|-------|
| Settings | [0,2150][200,2345] | 100 | 2248 |
| AI Chat | [220,2150][420,2345] | 320 | 2248 |
| Reports | [440,2150][640,2345] | 540 | 2248 |
| Income | [660,2150][860,2345] | 760 | 2248 |
| Expenses | [880,2150][1080,2345] | 980 | 2248 |

### Screen-Specific Coordinates

**Expenses Screen**:
- FAB (+): x=108, y=2042
- List items: start y≈130

**Add Expense Form**:
- Amount: x=540, y=290
- Description: x=540, y=650
- Date: x=540, y=535
- Category chips row 1: Food x=190, Entertainment x=410, Bills x=625 (y≈1130)
- Category chips row 2: Shopping x=240, Other x=430, Health x=610 (y≈1200)
- Category chips row 3: Transport x=590 (y≈1270)
- Add Expense button: x=540, y=1638

**Add Income Form**:
- Income Type toggle: One-time x=200, Recurring x=520 (y≈332)
- Amount: x=540, y=460
- Description: x=540, y=590
- Add Income button: x=540, y=1205

**AI Chat Screen**:
- Text input (keyboard closed): x=608, y=2062
- Text input (keyboard open): bounds [176,1146][1041,1283]
- Send button (keyboard open): x=97, y=1224 (tap parent container [39,1166][156,1283])

### Coordinate Update Protocol

Coordinates shift when UI changes. After any UI modification:
1. `adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml /tmp/`
2. Parse and update coordinates in this file AND in `memory/device-validation.md`
3. Screenshot to visually confirm: `adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png /tmp/`

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
adb shell am start -n com.keisardev.insight/.MainActivity && sleep 2
# Navigate to Settings → Clear All Data
adb shell input tap 100 2248 && sleep 1.5
# Find and tap "Clear All Data" then confirm dialog
```

### P1: Expense CRUD Flow

**P1a. Add Expense**
```bash
adb shell input tap 980 2248 && sleep 1.5      # Expenses tab
# Verify empty state via uiautomator
adb shell input tap 108 2042 && sleep 1          # FAB
adb shell input tap 540 290 && sleep 0.3 && adb shell input text "42.50"
adb shell input keyevent 111 && sleep 0.3        # Dismiss keyboard (ESCAPE)
adb shell input tap 540 650 && sleep 0.3 && adb shell input text "TestLunch"
adb shell input keyevent 111 && sleep 0.3        # Dismiss keyboard
adb shell input tap 190 1130 && sleep 0.3        # Food category
adb shell input tap 540 1638 && sleep 1          # Add Expense
```
**Verify**: uiautomator shows "$42.50", "TestLunch", "Food", today's date in list.

**P1b. Edit Expense**
- Tap expense item (get bounds from dump) → change amount to "55.00" → Save
- **Verify**: List shows "$55.00"

**P1c. Cross-Screen: Reports**
```bash
adb shell input tap 540 2248 && sleep 1.5        # Reports tab
```
**Verify**: Total Spending "$55.00" (or "$42.50"), Food 100%, Spending view active.

**P1d. Cross-Screen: AI Chat**
```bash
adb shell input tap 320 2248 && sleep 1.5        # AI Chat tab
adb shell input tap 608 2062 && sleep 0.5        # Text input
adb shell input text "How%smuch%sdid%sI%sspend" && sleep 0.3
# Find send button via uiautomator, tap parent container
adb shell input tap 97 1224 && sleep 5            # Send + wait for AI
```
**Verify**: AI response mentions expense amount and "Food" category.

### P2: Income CRUD Flow

**P2a. Add One-Time Income**
```bash
adb shell input tap 760 2248 && sleep 1.5        # Income tab
adb shell input tap 108 2042 && sleep 1          # FAB
# One-time selected by default
# Fill amount "3000", description "TestSalary", select Salary category
adb shell input tap 540 1205 && sleep 1          # Add Income
```
**Verify**: "$3,000.00", "One-time" badge, "Salary" category.

**P2b. Add Recurring Income**
- FAB → Toggle "Recurring" (x=520, y=332) → Amount "500" → Freelance category → Save
- **Verify**: "$500.00", "Recurring" badge, "Freelance" category.

**P2c. Reports Earnings**
```bash
adb shell input tap 540 2248 && sleep 1.5        # Reports tab
# Tap "Earnings" chip
```
**Verify**: Total "$3,500.00", Salary + Freelance breakdown.

**P2d. Reports Balance**
- Tap "Balance" chip
- **Verify**: Net balance = $3,500 - $55 = $3,445, positive indicator, savings rate shown, green/tertiary card.

### P3: Month Navigation
- Previous month arrow → month label changes → totals $0 → Next month → totals restore.

### P4: Clear Data Cascade
```bash
adb shell input tap 100 2248 && sleep 1.5        # Settings tab
# Tap Clear All Data → Confirm
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
for tab_x in 980 760 540 320 100; do
  adb shell input tap $tab_x 2248 && sleep 1.5
  adb shell screencap -p /sdcard/validate_${tab_x}.png
  adb pull /sdcard/validate_${tab_x}.png /tmp/
done
```
**Check**: 5 screens render, no crash, InsightTheme applied.

### V2: Keyboard Spacing — QUANTITATIVE
```bash
adb shell input tap 320 2248 && sleep 1.5        # AI Chat
adb shell input tap 608 2062 && sleep 1.5        # Open keyboard
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
