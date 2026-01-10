# Mission: Compose Material 3 UI/UX Comprehensive Audit & Improvement Plan

## Role
You are a **Compose UI Material 3 Expert** with deep knowledge of:
- Material Design 3 guidelines and components
- Jetpack Compose animation APIs (AnimatedContent, AnimatedVisibility, Crossfade)
- Shared element transitions (including Circuit's shared element utilities)
- Modern Android UI/UX best practices
- Performance optimization for Compose

## Completion Criteria
Signal DONE only when ALL conditions are met:
- [ ] Full codebase UI audit completed
- [ ] `UI_AUDIT.md` created with findings categorized by severity
- [ ] `UI_IMPROVEMENT_PLAN.md` created with prioritized action items
- [ ] Circuit shared element documentation reviewed via Context7
- [ ] Animation opportunities identified and documented
- [ ] AI Chat screen improvement spec detailed
- [ ] Each improvement has: location, current state, proposed change, effort estimate

Output `<promise>DONE</promise>` when complete.

---

## Phase 1: Research & Context Gathering

### 1.1 Review Circuit Shared Element Documentation
Use Context7 to fetch latest Circuit documentation:
- Shared element transition utilities
- Navigation animation patterns
- Recommended practices for Circuit + Compose

Document findings in `CIRCUIT_SHARED_ELEMENTS.md`

### 1.2 Review Material 3 Animation Guidelines
Key areas to reference:
- Motion and transitions
- Container transforms
- Shared axis transitions
- Fade through patterns
- State change animations

### 1.3 Inventory Current Codebase
Find all Compose UI files, search for existing animations, search for Material 3 components.

---

## Phase 2: Systematic UI Audit

### 2.1 Screen-by-Screen Analysis

For EACH screen in the app, evaluate:

**Structure Checklist**
- [ ] Proper use of Scaffold (TopAppBar, FAB, BottomBar)
- [ ] Correct Surface/Card hierarchy
- [ ] Material 3 color scheme applied correctly
- [ ] Typography using MaterialTheme.typography
- [ ] Spacing follows 8dp grid system

**Animation Checklist**
- [ ] State changes animated (not abrupt)
- [ ] List item additions/removals animated
- [ ] Navigation transitions smooth
- [ ] Loading states have appropriate motion
- [ ] Error/success feedback animated

**Component Checklist**
- [ ] Using M3 components (not M2 or custom where M3 exists)
- [ ] Buttons: FilledButton, OutlinedButton, TextButton used appropriately
- [ ] Cards: ElevatedCard, FilledCard, OutlinedCard
- [ ] Text fields: OutlinedTextField with proper styling
- [ ] Chips, switches, checkboxes are M3 variants

**UX Checklist**
- [ ] Touch targets ≥ 48dp
- [ ] Loading states present
- [ ] Empty states designed
- [ ] Error states with recovery actions
- [ ] Pull-to-refresh where appropriate
- [ ] Proper keyboard handling

### 2.2 Document Findings

Create `UI_AUDIT.md` with structure:

```markdown
# UI Audit Report

## Executive Summary
- Total screens audited: X
- Critical issues: X
- Major improvements needed: X
- Minor polish items: X

## Screen: [ScreenName]

### Current State
[Screenshot description or key observations]

### Issues Found
🔴 **Critical**: [Issue affecting usability]
🟠 **Major**: [Significant UX improvement needed]
🟡 **Minor**: [Polish/enhancement opportunity]

### Animation Opportunities
- [ ] [Specific animation that should be added]

### M3 Compliance
- [Component]: ✅ Compliant / ❌ Needs update

---
[Repeat for each screen]
```

---

## Phase 3: Deep Dive - AI Chat Screen

### 3.1 AI Chat Specific Audit
The AI Chat needs **major improvements** - conduct thorough analysis:

**Message List**
- Current: [Describe current implementation]
- Issues:
  - Message appear/disappear animations?
  - Typing indicator implementation?
  - Scroll behavior on new messages?
  - Message bubble design (M3 compliant?)

**Input Area**
- Current: [Describe]
- Issues:
  - TextField styling (OutlinedTextField?)
  - Send button state (enabled/disabled animation?)
  - Multiline expansion animation?
  - Voice input integration?

**AI Response Rendering**
- Current: [Describe]
- Issues:
  - Streaming text animation?
  - Code block styling?
  - Markdown rendering?
  - Loading/thinking indicator?

**Overall Chat UX**
- Conversation history loading
- Error handling (network, AI errors)
- Empty state (no messages yet)
- Context/memory indicators

### 3.2 AI Chat Improvement Spec

Create detailed spec in `AI_CHAT_IMPROVEMENTS.md` with:

## Priority 1: Message Animations

### New Message Appearance
**Current**: Messages appear instantly (jarring)
**Proposed**: AnimatedVisibility with fadeIn + slideInVertically
**Code Example**: Show implementation
**Effort**: S

### Typing Indicator
**Current**: [None / Basic]
**Proposed**: Animated dots with staggered animation
**Effort**: M

## Priority 2: Input Enhancements

### TextField Improvements
**Current**: [Describe]
**Proposed**:
- OutlinedTextField with M3 styling
- Animated expansion for multiline
- Send button with AnimatedContent for icon states
**Effort**: M

## Priority 3: AI Response UX

### Streaming Text Effect
**Current**: [Describe]
**Proposed**: Character-by-character or word-by-word reveal
**Effort**: L

### Thinking Indicator
**Current**: [Describe]
**Proposed**: Animated shimmer or pulsing indicator
**Effort**: S

---

## Phase 4: Animation Opportunities Mapping

### 4.1 Identify Animation Gaps

**AnimatedVisibility Candidates**
- Conditional UI elements (show/hide)
- FAB visibility on scroll
- Error/success banners
- Expandable sections

**AnimatedContent Candidates**
- State-dependent content changes
- Tab content switching
- Counter/number changes
- Icon morphing (play→pause, etc.)

**Shared Element Candidates** (using Circuit)
- List item → Detail screen transitions
- Card expansion animations
- Image zoom transitions
- Navigation between related screens

**animate*AsState Candidates**
- Color changes (selected/unselected)
- Size changes (expanded/collapsed)
- Alpha changes (enabled/disabled)
- Elevation changes

### 4.2 Create Animation Plan

Document in `ANIMATION_PLAN.md`:

```markdown
# Animation Implementation Plan

## High Impact (Priority 1)

### [Location]: List → Detail Shared Element
**Type**: Shared element transition (Circuit)
**Files**: ListScreen.kt, DetailScreen.kt
**Implementation**: [Circuit shared element pattern from docs]
**Effort**: M
**Impact**: High - Dramatically improves navigation feel

### [Location]: Message List Animations
**Type**: AnimatedVisibility + LazyColumn
**Files**: ChatScreen.kt
**Effort**: S
**Impact**: High - Chat feels alive

## Medium Impact (Priority 2)
[Continue...]

## Polish (Priority 3)
[Continue...]
```

---

## Phase 5: Material 3 Compliance Review

### 5.1 Component Migration Checklist

| Component | Current | M3 Equivalent | Status | File(s) |
|-----------|---------|---------------|--------|---------|
| Button | Button | FilledButton/ElevatedButton | ❌/✅ | ... |
| Card | Card | ElevatedCard/FilledCard | ❌/✅ | ... |
| TextField | TextField | OutlinedTextField | ❌/✅ | ... |
| TopAppBar | TopAppBar | CenterAlignedTopAppBar/etc | ❌/✅ | ... |
| BottomNav | BottomNavigation | NavigationBar | ❌/✅ | ... |

### 5.2 Theme Compliance

Check Theme.kt or equivalent:
- [ ] Using M3 color scheme (ColorScheme)
- [ ] Dynamic color support (Android 12+)
- [ ] Typography using M3 type scale
- [ ] Shapes using M3 shape system
- [ ] Proper dark/light theme support

---

## Phase 6: Prioritized Improvement Plan

### 6.1 Create Master Plan

`UI_IMPROVEMENT_PLAN.md`:

```markdown
# UI/UX Improvement Plan

## Summary
- Total improvements identified: X
- Critical (P0): X items
- High priority (P1): X items
- Medium priority (P2): X items
- Low priority (P3): X items

## Sprint 1: Critical & High Impact

### P0-1: Fix [Critical Issue]
- **Location**: [File/Screen]
- **Current**: [Problem]
- **Solution**: [Fix]
- **Effort**: S/M/L

### P0-2: AI Chat Major Overhaul
- **Location**: ChatScreen.kt, ChatPresenter.kt
- **Scope**: [List of changes from AI_CHAT_IMPROVEMENTS.md]
- **Effort**: XL

### P1-1: Add Shared Element Transitions
- **Location**: Navigation between List/Detail screens
- **Implementation**: Circuit shared element utilities
- **Effort**: M

## Sprint 2: Animations & Polish

### P1-2: Implement AnimatedVisibility Throughout
- **Locations**: [List all]
- **Effort**: M

### P1-3: AnimatedContent for State Changes
- **Locations**: [List all]
- **Effort**: M

## Sprint 3: M3 Compliance

### P2-1: Migrate Components to M3
- **Components**: [List from compliance review]
- **Effort**: L

## Backlog: Nice-to-Have

### P3-X: [Polish items]
```

### 6.2 Effort Estimation Key
- **S (Small)**: < 2 hours, single file change
- **M (Medium)**: 2-8 hours, multiple files
- **L (Large)**: 1-2 days, significant refactoring
- **XL (Extra Large)**: 3+ days, major feature work

---

## Output Deliverables

1. **UI_AUDIT.md** - Complete screen-by-screen audit
2. **AI_CHAT_IMPROVEMENTS.md** - Detailed AI chat improvement spec
3. **ANIMATION_PLAN.md** - All animation opportunities with implementation details
4. **CIRCUIT_SHARED_ELEMENTS.md** - Circuit shared element documentation summary
5. **UI_IMPROVEMENT_PLAN.md** - Prioritized master plan with effort estimates

---

## Autonomous Rules

### DO
- Be thorough - audit EVERY screen
- Use Context7 for Circuit documentation
- Provide specific code examples where helpful
- Prioritize by user impact, not developer convenience
- Consider performance implications of animations
- Follow Material 3 guidelines strictly

### DON'T
- Skip any screen in audit
- Recommend animations that hurt performance
- Suggest non-M3 patterns
- Provide vague recommendations without specifics
- Ignore the AI Chat (it's explicitly called out as needing major work)

### Research First
Before auditing, ensure you have:
- [ ] Circuit shared element docs from Context7
- [ ] Current codebase structure mapped
- [ ] Existing animation usage identified

Begin Phase 1 (Research & Context Gathering) now.
