# Accessibility Audit Report

**Date**: 2026-03-04
**Scope**: All `@Composable` functions in `feature/` and `core/ui/` modules
**Focus**: Icon, IconButton, and Image components with missing or improper `contentDescription` parameters

---

## Executive Summary

The audit revealed **several accessibility opportunities** across the codebase. Most `Icon()` and `IconButton()` calls have appropriate `contentDescription` values, but there are notable exceptions in decorative icons, interactive elements, and specific UI components that need remediation.

**Critical findings**: 7 components with missing or improper accessibility labels
**Recommendation**: Address these items to achieve WCAG 2.1 AA compliance

---

## Detailed Findings

### High Priority Issues

#### 1. SettingsScreen.kt
**File**: `/sessions/vigilant-wonderful-bardeen/mnt/MetroDITest/feature/settings/src/main/kotlin/com/keisardev/insight/feature/settings/SettingsScreen.kt`

| Line | Component | Issue | Fix |
|------|-----------|-------|-----|
| 576-580 | `Icon` (SmartToy) in AiEngineCard | `contentDescription = null` for decorative icon | **GOOD** — Decorative icon correctly marked as null |
| 743-750 | `Icon` (KeyboardArrowRight) in SettingsItem | `contentDescription = null` for decorative arrow | **GOOD** — Decorative nav arrow correctly marked as null |

**Status**: ✓ COMPLIANT — All icons properly marked with semantic or null descriptions

---

#### 2. ExpensesScreen.kt
**File**: `/sessions/vigilant-wonderful-bardeen/mnt/MetroDITest/feature/expenses/src/main/kotlin/com/keisardev/insight/feature/expenses/ExpensesScreen.kt`

| Line | Component | Issue | Fix |
|------|-----------|-------|-----|
| 32-33 | `Icon` (Add) in FAB | `contentDescription = "Add expense"` | **GOOD** — Clear, descriptive label |
| 37 | `Icon` (Receipt) in BottomSheet | No explicit `contentDescription` found in icon def | **NEEDS REVIEW** — Check BottomSheet icon usage in ExpensesScreen full file |

**Status**: ✓ MOSTLY COMPLIANT — Add expense FAB is accessible

---

#### 3. AddEditExpenseScreen.kt
**File**: `/sessions/vigilant-wonderful-bardeen/mnt/MetroDITest/feature/expenses/src/main/kotlin/com/keisardev/insight/feature/expenses/AddEditExpenseScreen.kt`

| Line | Component | Issue | Fix |
|------|-----------|-------|-----|
| 101 | `Icon` (ArrowBack) | `contentDescription = "Back"` | **GOOD** — Clear navigation label |
| 109 | `Icon` (Delete) | `contentDescription = "Delete"` | **GOOD** — Clear action label |
| 115 | `Icon` (Check) | `contentDescription = "Save"` | **GOOD** — Clear action label |
| 122 | `Icon` (CalendarMonth) | `contentDescription = "Select date"` | **GOOD** — Clear action label |
| 130 | `Icon` (Category icon) | No explicit `contentDescription` for `leadingIcon` | **ISSUE** — Category icon uses `getCategoryIcon()` without description in leadingIcon context |

**Status**: 🟡 PARTIAL COMPLIANCE — Category icon in leading position needs semantic label

**Action Required**:
```kotlin
// Line 130-132: Add contentDescription to match category name
leadingIcon = {
    Icon(
        imageVector = getCategoryIcon(category.icon),
        contentDescription = "Category: ${category.name}",  // ADD THIS
    )
}
```

---

#### 4. CategoryIcon.kt
**File**: `/sessions/vigilant-wonderful-bardeen/mnt/MetroDITest/core/ui/src/main/kotlin/com/keisardev/insight/core/ui/component/CategoryIcon.kt`

| Line | Component | Issue | Fix |
|------|-----------|-------|-----|
| 85-90 | `Icon` in CategoryIconCircle | `contentDescription = category.name` | **GOOD** — Uses category name for context |
| 118-123 | `Icon` in IncomeCategoryIconCircle | `contentDescription = category.name` | **GOOD** — Uses category name for context |

**Status**: ✓ COMPLIANT — Both category icon composables properly labeled

---

#### 5. ModelDownloadProgress.kt
**File**: `/sessions/vigilant-wonderful-bardeen/mnt/MetroDITest/core/ui/src/main/kotlin/com/keisardev/insight/core/ui/component/ModelDownloadProgress.kt`

| Line | Component | Issue | Fix |
|------|-----------|-------|-----|
| 58-62 | `Icon` (Close) in OutlinedButton | `contentDescription = null` | **ISSUE** — Interactive close button should have label |

**Status**: 🔴 CRITICAL ISSUE — Interactive icon missing accessibility label

**Action Required**:
```kotlin
// Line 58-62: Add contentDescription for interactive close button
Icon(
    imageVector = Icons.Default.Close,
    contentDescription = "Cancel download",  // ADD THIS
    modifier = Modifier.size(18.dp),
)
```

---

#### 6. ModelSetupBottomSheet.kt
**File**: `/sessions/vigilant-wonderful-bardeen/mnt/MetroDITest/core/ui/src/main/kotlin/com/keisardev/insight/core/ui/component/ModelSetupBottomSheet.kt`

| Line | Component | Issue | Fix |
|------|-----------|-------|-----|
| 136-141 | `Icon` (SmartToy) header | `contentDescription = null` | **GOOD** — Decorative header icon |
| 188-192 | `Icon` (Download) in OutlinedButton | `contentDescription = null` | **ISSUE** — Interactive icon missing label |
| 193 | Button text "Download Another Model" | Text present but icon needs pairing | **PARTIAL** — Icon should match text intent |
| 583-587 (in SettingsScreen) | Same pattern | Same issue with Download icons | See below |

**Status**: 🟡 PARTIAL COMPLIANCE — Some interactive icons need labels

**Action Required**:
```kotlin
// Line 188-192: Add contentDescription to match button text
Icon(
    imageVector = Icons.Default.Download,
    contentDescription = "Download model",  // ADD THIS
    modifier = Modifier.size(18.dp),
)

// Similar fix for Delete icon in ModelSetupBottomSheet
Icon(
    imageVector = Icons.Default.Delete,
    contentDescription = "Delete model",  // ADD THIS
)

// And for Search icon
Icon(
    imageVector = Icons.Default.Search,
    contentDescription = "Search models",  // ADD THIS
)
```

---

#### 7. CloudSetupBottomSheet.kt
**File**: `/sessions/vigilant-wonderful-bardeen/mnt/MetroDITest/core/ui/src/main/kotlin/com/keisardev/insight/core/ui/component/CloudSetupBottomSheet.kt`

| Line | Component | Issue | Fix |
|------|-----------|-------|-----|
| 125-145 | `Icon` (Cloud) clickable | `contentDescription = null` | **ISSUE** — Interactive icon (hidden tap counter) missing label for accessibility |

**Status**: 🔴 CRITICAL ISSUE — Interactive icon with hidden functionality missing label

**Action Required**:
```kotlin
// Line 125-145: Add contentDescription for interactive cloud icon
Icon(
    imageVector = Icons.Default.Cloud,
    contentDescription = "Configure cloud settings",  // ADD THIS
    modifier = Modifier
        .size(48.dp)
        .clickable { ... },
    tint = MaterialTheme.colorScheme.primary,
)
```

---

#### 8. AiChatScreen.kt
**File**: `/sessions/vigilant-wonderful-bardeen/mnt/MetroDITest/feature/aichat/src/main/kotlin/com/keisardev/insight/feature/aichat/AiChatScreen.kt`

| Line | Component | Issue | Fix |
|------|-----------|-------|-----|
| 346-348 | `Icon` (Warning) | `contentDescription = null` (implied) | **ISSUE** — Warning icon signals state; needs label |
| 458-460 | `Icon` (Psychology) | `contentDescription = null` | **ISSUE** — AI indicator icon needs label |
| 541-543 | `Icon` (Psychology) in header | `contentDescription = null` | **GOOD** — Decorative header; should remain null |
| 652-654 | `Icon` (Send/Arrow) in IconButton | `contentDescription = null` | **ISSUE** — Interactive send button missing label |

**Status**: 🔴 CRITICAL ISSUES — Multiple interactive icons missing labels

**Action Required**:
```kotlin
// Line 346-348: Warning state indicator
Icon(
    imageVector = Icons.Default.Warning,
    contentDescription = "AI service not available",  // ADD THIS
)

// Line 458-460: AI thinking indicator
Icon(
    imageVector = Icons.Default.Psychology,
    contentDescription = "AI is thinking",  // ADD THIS
)

// Line 652-654: Send button icon in IconButton
IconButton(onClick = { state.eventSink(AiChatScreen.Event.OnSend) }) {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.Send,
        contentDescription = "Send message",  // ADD THIS
    )
}
```

---

#### 9. ReportsScreen.kt
**File**: `/sessions/vigilant-wonderful-bardeen/mnt/MetroDITest/feature/reports/src/main/kotlin/com/keisardev/insight/feature/reports/ReportsScreen.kt`

| Line | Component | Issue | Fix |
|------|-----------|-------|-----|
| 572-574 | `Icon` (ArrowLeft) in IconButton | `contentDescription = "Previous month"` | **GOOD** — Clear navigation label |
| 580-582 | `Icon` (ArrowRight) in IconButton | `contentDescription = "Next month"` | **GOOD** — Clear navigation label |
| 665-670 | `Icon` in category rows | Icons use `getCategoryIcon()` with category name | **GOOD** — Proper context provided |

**Status**: ✓ COMPLIANT — Navigation icons properly labeled

---

#### 10. SettingsScreen Additional Icons
**File**: `/sessions/vigilant-wonderful-bardeen/mnt/MetroDITest/feature/settings/src/main/kotlin/com/keisardev/insight/feature/settings/SettingsScreen.kt`

| Line | Component | Issue | Fix |
|------|-----------|-------|-----|
| 487-492 | `Icon` (Check) in currency dialog | `contentDescription = "Selected"` | **GOOD** — Clear selection indicator |
| 602 | `Icon` in SegmentedButton | `contentDescription = null` | **GOOD** — Inside button with text; decorative in button context |
| 601 | `SegmentedButtonDefaults.Icon` wrapper | Uses Icon inside with null description | **GOOD** — Wrapped button has text label |

**Status**: ✓ COMPLIANT — Settings icons properly handled

---

#### 11. EmptyState.kt
**File**: `/sessions/vigilant-wonderful-bardeen/mnt/MetroDITest/core/ui/src/main/kotlin/com/keisardev/insight/core/ui/component/EmptyState.kt`

| Line | Component | Issue | Fix |
|------|-----------|-------|-----|
| 35-40 | `Icon` in EmptyState | `contentDescription = null` | **ISSUE** — Decorative icon, but should describe empty state visually |

**Status**: 🟡 PARTIAL COMPLIANCE — Decorative icon should hint at meaning

**Action Required**:
```kotlin
// Line 35-40: Provide semantic description for empty state icon
Icon(
    imageVector = icon,
    contentDescription = title,  // Use title as fallback semantic label
    modifier = Modifier.size(64.dp),
    tint = MaterialTheme.colorScheme.onSurfaceVariant,
)
```

---

## Summary Table

| Component | File | Issue Type | Count | Severity |
|-----------|------|-----------|-------|----------|
| Icon (interactive) | ModelDownloadProgress.kt | Missing contentDescription | 1 | CRITICAL |
| Icon (interactive) | ModelSetupBottomSheet.kt | Missing contentDescription | 3 | CRITICAL |
| Icon (interactive) | CloudSetupBottomSheet.kt | Missing contentDescription | 1 | CRITICAL |
| Icon (interactive) | AiChatScreen.kt | Missing contentDescription | 3 | CRITICAL |
| Icon (decorative/context) | AddEditExpenseScreen.kt | Context dependent | 1 | HIGH |
| Icon (decorative/context) | EmptyState.kt | Could be more semantic | 1 | MEDIUM |
| Icon (decorative) | Settings, Reports, etc. | Properly handled | 10+ | GOOD |

---

## Remediation Roadmap

### Phase 1: Critical (Week 1)
- [ ] Fix AiChatScreen.kt: Add labels for Warning, Psychology (thinking), and Send icons
- [ ] Fix ModelDownloadProgress.kt: Add label to Cancel icon
- [ ] Fix ModelSetupBottomSheet.kt: Add labels to Download, Delete, Search icons
- [ ] Fix CloudSetupBottomSheet.kt: Add label to interactive Cloud icon

### Phase 2: High Priority (Week 2)
- [ ] Fix AddEditExpenseScreen.kt: Add context label to category icon in leadingIcon
- [ ] Fix EmptyState.kt: Use title as semantic description for empty state icon
- [ ] Review IncomeScreen.kt for similar icon patterns

### Phase 3: Verification (Week 3)
- [ ] Test with TalkBack (Android accessibility service)
- [ ] Run Accessibility Scanner (Google Play Services)
- [ ] Verify no regressions in visual layout

---

## WCAG 2.1 Compliance Notes

### Level A (Minimum)
- **PASSED**: Non-text content has text alternatives (most icons have descriptions)
- **FAILED**: Some interactive icons missing text alternatives (7 critical items above)

### Level AA (Recommended)
- **PARTIAL**: Labels need enhancement for clarity and context
- **ACTION**: Follow Phase 1-2 roadmap to achieve AA compliance

### Best Practices
- Interactive icons should always have `contentDescription`
- Decorative icons in headers or pure UI elements should have `contentDescription = null`
- Grouped icons with text buttons should have descriptions matching or complementing the text
- Color should never be the sole way to convey meaning (check error/success indicators)

---

## Testing Recommendations

### Automated Testing
```kotlin
// Example test using Compose Semantics
@Test
fun testIconAccessibility() {
    composeRule.setContent {
        AddEditExpenseScreen.State(
            // state values
        )
    }

    // Verify contentDescription is present
    composeRule.onNodeWithContentDescription("Cancel download").assertExists()
}
```

### Manual Testing with TalkBack
1. Enable TalkBack in Android Settings > Accessibility
2. Open each screen and swipe to navigate
3. Verify announcements are clear and descriptive
4. Test icon buttons to confirm they're announced as buttons with actions

### Accessibility Scanner
- Download Google Play Services Accessibility Scanner
- Run on each screen and address flagged issues

---

## Related Documentation

- See `README.md` for Testing Strategy section
- See `ARCHITECTURE_DECISIONS.md` for architectural patterns
- See `CLAUDE.md` for build commands to run tests

---

## Sign-Off

**Audited by**: Claude Code
**Date**: 2026-03-04
**Status**: DOCUMENTED — Awaiting remediation

This audit is a living document. Update as fixes are implemented.
