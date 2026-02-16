---
name: accessibility-expert
description: Expert guidance on Compose Multiplatform accessibility. Use for screen reader support, semantics, touch targets, focus management, color contrast, and WCAG compliance.
---

# Accessibility Expert Skill

## Overview

Accessibility in Compose Multiplatform ensures apps work with screen readers (TalkBack on Android, VoiceOver on iOS), keyboard navigation, and assistive technologies. Compose builds a **semantics tree** that mirrors the UI tree and maps to platform accessibility APIs. Every composable can attach semantic properties that describe its meaning, state, and available actions to assistive technologies.

The semantics tree is separate from the layout tree. Platform accessibility services (AccessibilityNodeInfo on Android, UIAccessibility on iOS) consume the semantics tree to present UI information to users who rely on assistive technology.

## When to Use

- **Content descriptions**: Labeling images, icons, and visual-only elements.
- **Touch target sizing**: Ensuring interactive elements meet minimum 48dp.
- **Focus management**: Controlling focus order in forms, dialogs, and complex layouts.
- **Screen reader support**: Providing meaningful traversal order and announcements.
- **Color contrast**: Meeting WCAG AA contrast ratios.
- **Dynamic text sizing**: Supporting font scale up to 200%.
- **Custom actions**: Exposing swipe/long-press actions to accessibility services.
- **Live regions**: Announcing dynamic content changes (timers, counters, status).
- **Headings**: Structuring content for screen reader heading navigation.
- **Grouped semantics**: Merging related elements into a single accessibility node.

## Core APIs

| API | Purpose |
|-----|---------|
| `Modifier.semantics { }` | Attach semantic properties to a node |
| `Modifier.clearAndSetSemantics { }` | Replace all child semantics with custom ones |
| `Modifier.clickable` | Automatically sets click action semantics |
| `contentDescription` parameter | On `Image`, `Icon`, `AsyncImage` for labeling |
| `Modifier.testTag` | Testing identifier (also used by accessibility services) |

## Key Semantic Properties

| Property | Type | Purpose |
|----------|------|---------|
| `contentDescription` | String | Text description for screen readers |
| `role` | Role | Semantic role (Button, Checkbox, Switch, etc.) |
| `stateDescription` | String | Current state in human-readable form |
| `heading()` | Unit | Marks node as a heading for navigation |
| `liveRegion` | LiveRegionMode | Announces changes (Polite or Assertive) |
| `traversalGroup` | Boolean | Groups children for traversal ordering |
| `mergeDescendants` | Boolean | Merges children into single accessibility node |
| `disabled()` | Unit | Marks element as disabled |
| `selected` | Boolean | Marks element as selected |
| `toggleableState` | ToggleableState | On/Off/Indeterminate for toggles |
| `customActions` | List | Additional actions beyond tap |
| `invisibleToUser()` | Unit | Hides node from accessibility services |

## Minimum Touch Targets

Interactive elements must be at least **48dp x 48dp**. Material 3 components enforce this by default. For custom composables, use `Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)` or `Modifier.minimumInteractiveComponentSize()`.

## Focus Management

- `FocusRequester`: Programmatically request focus on a composable.
- `Modifier.focusable()`: Makes a composable focusable.
- `Modifier.focusGroup()`: Groups children for focus traversal.
- `FocusDirection`: Up, Down, Left, Right, Next, Previous for keyboard navigation.

## Live Regions

- `LiveRegionMode.Polite`: Announces when the user is idle. Use for non-urgent updates.
- `LiveRegionMode.Assertive`: Interrupts the current announcement. Use for critical alerts.

## Color Contrast (WCAG AA)

| Text Type | Minimum Ratio |
|-----------|---------------|
| Normal text (< 18sp) | 4.5:1 |
| Large text (>= 18sp or >= 14sp bold) | 3:1 |
| UI components and graphical objects | 3:1 |

## Platform Differences

| Platform | Screen Reader | Switch Access | Notes |
|----------|--------------|---------------|-------|
| Android | TalkBack | Switch Access | Uses AccessibilityNodeInfo |
| iOS | VoiceOver | Switch Control | Maps to UIAccessibility |
| Desktop | Platform readers | Keyboard only | Varies by OS |

## Core Rules

1. Every meaningful image needs `contentDescription`; decorative images use `null`.
2. Touch targets must be at least 48dp x 48dp.
3. Never convey information through color alone -- always pair with text, icons, or patterns.
4. Group related elements with `mergeDescendants = true` to reduce screen reader verbosity.
5. Test with TalkBack (Android) and VoiceOver (iOS) enabled during development.
6. Support font scaling to 200% without content clipping or overlap.
7. Provide visible focus indicators for keyboard navigation.
8. Use `liveRegion` for content that updates dynamically (counters, timers, status).
9. Mark section headings with `heading()` for screen reader heading navigation.
10. Provide `stateDescription` for custom stateful components.

## Common Pitfalls

- **Missing contentDescription on icons**: Icon buttons without descriptions are invisible to screen readers.
- **Modifier order**: `semantics` must come before `clickable` if both are present, or the clickable semantics will override.
- **Over-merging**: Using `mergeDescendants` on containers with interactive children hides those children from accessibility.
- **Hardcoded sizes in dp for text**: Use `sp` for text so it respects system font scaling.
- **Forgetting null contentDescription**: Decorative images without `contentDescription = null` generate unhelpful announcements (e.g., the file name).
- **Missing role**: Custom interactive elements without `role = Role.Button` are not announced as buttons.
- **Live region on static content**: Only use `liveRegion` on content that changes dynamically.
- **Ignoring traversal order**: Complex layouts may produce unexpected reading order for screen readers.

## Testing Accessibility

```kotlin
// Test for content description
composeTestRule.onNodeWithContentDescription("Profile picture").assertExists()

// Test for click action
composeTestRule.onNodeWithContentDescription("Submit").assertHasClickAction()

// Test for role
composeTestRule.onNode(hasRole(Role.Button)).assertExists()

// Test for state
composeTestRule.onNodeWithContentDescription("Toggle")
    .assertIsToggleable()
    .assertIsOn()
```

## See Also

- [compose-ui-expert](../compose-ui-expert/SKILL.md) -- Semantics modifiers, content descriptions
- [compose-material3-expert](../compose-material3-expert/SKILL.md) -- M3 accessible component patterns
- [testing-expert](../testing-expert/SKILL.md) -- Accessibility testing with ComposeTestRule
