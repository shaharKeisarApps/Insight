# Accessibility Reference

## Modifier.semantics

Attaches semantic properties to a composable node for accessibility services.

```kotlin
fun Modifier.semantics(
    mergeDescendants: Boolean = false,
    properties: SemanticsPropertyReceiver.() -> Unit
): Modifier
```

- `mergeDescendants = true`: Merges all child semantics into this node. Interactive children are hidden -- only use on containers without clickable children.
- `mergeDescendants = false` (default): Each child retains its own semantics node.

## Modifier.clearAndSetSemantics

Removes all child semantics and replaces with the provided properties.

```kotlin
fun Modifier.clearAndSetSemantics(
    properties: SemanticsPropertyReceiver.() -> Unit
): Modifier
```

## SemanticsPropertyReceiver Properties

### contentDescription

```kotlin
var SemanticsPropertyReceiver.contentDescription: String
// Usage:
Modifier.semantics { contentDescription = "User avatar for John Doe" }
```

### role

```kotlin
var SemanticsPropertyReceiver.role: Role
```

| Role | Announced As | Use For |
|------|-------------|---------|
| `Role.Button` | "Button" | Custom clickable elements |
| `Role.Checkbox` | "Checkbox" | Custom toggle with checked state |
| `Role.Switch` | "Switch" | Custom on/off toggle |
| `Role.RadioButton` | "Radio button" | Custom radio selection |
| `Role.Tab` | "Tab" | Custom tab elements |
| `Role.Image` | "Image" | Custom image containers |
| `Role.DropdownList` | "Dropdown list" | Custom dropdown elements |

### stateDescription

```kotlin
var SemanticsPropertyReceiver.stateDescription: String
// Usage: custom state text when default announcement is insufficient
Modifier.semantics { stateDescription = "Expanded, showing 5 items" }
```

### heading()

```kotlin
fun SemanticsPropertyReceiver.heading()
// Marks node as heading for screen reader heading navigation
Modifier.semantics { heading() }
```

### liveRegion

```kotlin
var SemanticsPropertyReceiver.liveRegion: LiveRegionMode
```

| Mode | Behavior | Use For |
|------|----------|---------|
| `LiveRegionMode.Polite` | Waits for current speech to finish | Status updates, counters |
| `LiveRegionMode.Assertive` | Interrupts current speech | Errors, critical alerts |

### traversalGroup

```kotlin
var SemanticsPropertyReceiver.traversalGroup: Boolean
// Groups children so they are traversed together before moving to next group
```

### disabled()

```kotlin
fun SemanticsPropertyReceiver.disabled()
// Screen readers announce "dimmed" or "disabled"
```

### selected

```kotlin
var SemanticsPropertyReceiver.selected: Boolean
// For tabs, list items, chips
Modifier.semantics { selected = isSelected }
```

### toggleableState

```kotlin
var SemanticsPropertyReceiver.toggleableState: ToggleableState
// Values: ToggleableState.On, ToggleableState.Off, ToggleableState.Indeterminate
```

### progressBarRangeInfo

```kotlin
var SemanticsPropertyReceiver.progressBarRangeInfo: ProgressBarRangeInfo
// Usage:
Modifier.semantics {
    progressBarRangeInfo = ProgressBarRangeInfo(current = 0.75f, range = 0f..1f, steps = 0)
}
```

### customActions

```kotlin
var SemanticsPropertyReceiver.customActions: List<CustomAccessibilityAction>
// Additional actions exposed in screen reader actions menu
Modifier.semantics {
    customActions = listOf(
        CustomAccessibilityAction("Delete") { deleteItem(); true },
        CustomAccessibilityAction("Archive") { archiveItem(); true }
    )
}
```

### password()

```kotlin
fun SemanticsPropertyReceiver.password()
// Marks content as password; screen readers will not read individual characters
```

### invisibleToUser()

```kotlin
fun SemanticsPropertyReceiver.invisibleToUser()
// Hides node from accessibility services entirely
```

## Focus APIs

### FocusRequester

```kotlin
val focusRequester = remember { FocusRequester() }
TextField(
    value = text,
    onValueChange = { text = it },
    modifier = Modifier.focusRequester(focusRequester)
)
LaunchedEffect(Unit) { focusRequester.requestFocus() }
```

### Modifier.focusable() / Modifier.focusGroup()

- `focusable()`: Makes a non-interactive composable focusable for keyboard navigation.
- `focusGroup()`: Groups children for focus traversal; focus moves through children before leaving.

### Keyboard Navigation

```kotlin
Modifier.onKeyEvent { keyEvent ->
    when (keyEvent.key) {
        Key.Tab -> { focusManager.moveFocus(FocusDirection.Next); true }
        Key.Enter -> { performAction(); true }
        else -> false
    }
}
```

### FocusDirection

| Direction | Description |
|-----------|-------------|
| `FocusDirection.Next` / `Previous` | Sequential navigation |
| `FocusDirection.Up` / `Down` / `Left` / `Right` | Directional navigation |
| `FocusDirection.Enter` / `Exit` | Enter or exit a focus group |

## Touch Target Sizing

```kotlin
Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)   // Explicit minimum
Modifier.minimumInteractiveComponentSize()               // Material 3 helper
```

## Testing APIs

### Finders

```kotlin
composeTestRule.onNodeWithContentDescription("Close")
composeTestRule.onNodeWithText("Submit")
composeTestRule.onNode(hasContentDescription("Avatar") and hasClickAction())
composeTestRule.onNode(hasRole(Role.Button))
composeTestRule.onAllNodesWithContentDescription("Star")
```

### Semantic Matchers

| Matcher | Description |
|---------|-------------|
| `hasContentDescription(value)` | Node has content description |
| `hasClickAction()` | Node has click action |
| `hasRole(role)` | Node has specified role |
| `isEnabled()` / `isFocusable()` / `isFocused()` | Focus and enabled state |
| `isSelected()` / `isToggleable()` / `isOn()` / `isOff()` | Selection and toggle state |
| `isHeading()` | Node is marked as heading |

### Assertions

```kotlin
.assertExists()             .assertDoesNotExist()
.assertIsDisplayed()        .assertIsEnabled()          .assertIsNotEnabled()
.assertHasClickAction()     .assertHasNoClickAction()
.assertIsToggleable()       .assertIsOn()               .assertIsOff()
.assertIsFocused()          .assertIsSelected()
.assertContentDescriptionEquals("text")
```

## Key Imports

```kotlin
// Semantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.traversalGroup
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.state.ToggleableState
// Focus
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusGroup
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.Key
// Testing
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasRole
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.isHeading
```

## WCAG Guidelines Quick Reference

| Guideline | Level | Requirement |
|-----------|-------|-------------|
| 1.1.1 Non-text Content | A | All non-text content has text alternative |
| 1.3.1 Info and Relationships | A | Structure conveyed programmatically |
| 1.4.3 Contrast (Minimum) | AA | 4.5:1 normal text, 3:1 large text |
| 1.4.4 Resize Text | AA | Text resizable to 200% without loss |
| 1.4.11 Non-text Contrast | AA | 3:1 for UI components |
| 2.1.1 Keyboard | A | All functionality via keyboard |
| 2.4.3 Focus Order | A | Logical and meaningful focus order |
| 2.4.6 Headings and Labels | AA | Headings describe topic or purpose |
| 2.5.5 Target Size | AAA | At least 44x44 CSS px (48dp in Android) |
| 4.1.2 Name, Role, Value | A | All UI components have accessible name and role |
