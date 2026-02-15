# Circuit Shared Elements Documentation Summary

**Source**: Circuit Documentation (https://slackhq.github.io/circuit/shared-elements)
**Library**: `/websites/slackhq_github_io_circuit`
**Project Circuit Version**: 0.31.0 (as per gradle/libs.versions.toml)
**Feature Added**: Circuit 0.26.0

---

## Overview

Circuit provides a Shared Elements API for creating visually appealing transitions between screens by animating shared UI elements. This feature was added in version 0.26.0.

## Key Components

### 1. SharedElementTransitionLayout

Wraps your content to provide a `SharedTransitionScope` for nested composables.

```kotlin
@Composable
fun MyScreen() {
    SharedElementTransitionLayout {
        // Your Composables that use shared element modifiers go here
        // Access SharedTransitionScope and AnimatedVisibilityScope within this block
    }
}
```

### 2. Modifier.sharedElement()

Used for elements that should transition while morphing (e.g., an image that changes size/position).

```kotlin
Image(
    Icons.Default.Person,
    modifier = Modifier.sharedElement(
        sharedContentState = rememberSharedContentState(
            EmailSharedTransitionKey(
                id = email.id,
                type = EmailSharedTransitionKey.ElementType.SenderImage,
            )
        ),
        animatedVisibilityScope = requireAnimatedScope(
            SharedElementTransitionScope.AnimatedScope.Navigation
        ),
    )
    .size(40.dp)
    .clip(CircleShape)
    .background(Color.Magenta)
    .padding(4.dp),
    contentDescription = null,
)
```

### 3. Modifier.sharedBounds()

Used for elements whose bounds should animate but content may change (e.g., text that transitions smoothly).

```kotlin
Text(
    text = email.body,
    modifier = Modifier.sharedBounds(
        sharedContentState = rememberSharedContentState(
            EmailSharedTransitionKey(
                id = email.id,
                type = EmailSharedTransitionKey.ElementType.Body,
            )
        ),
        animatedVisibilityScope = requireAnimatedScope(
            SharedElementTransitionScope.AnimatedScope.Navigation
        ),
    ),
    // ...
)
```

## Implementation Steps for Circuit

1. **Wrap content with SharedElementTransitionLayout**
   - Place at screen level or navigation container level

2. **Access the SharedTransitionScope**
   - Automatically available within SharedElementTransitionLayout

3. **Create shared element keys** (Optional but recommended)
   - Use data classes to uniquely identify shared elements
   - Include item ID and element type for clarity

```kotlin
@Parcelize
data class EmailSharedTransitionKey(
    val id: Long,
    val type: ElementType,
) : Parcelable {
    enum class ElementType {
        SenderImage,
        Subject,
        Body
    }
}
```

4. **Apply Modifier.sharedElement() or Modifier.sharedBounds()**
   - Choose based on whether content morphs or just bounds animate

5. **Specify animation specs** (Optional)
```kotlin
modifier = Modifier.sharedElementTransition(
    state.sharedElementId,
    animationSpec = tween(durationMillis = 500)
)
```

## Best Practices

1. **Use shared element keys for complex transitions**
   - Makes debugging easier
   - Provides type-safe element identification

2. **Choose the right modifier**
   - `sharedElement()`: When the same element appears in both screens (images, icons)
   - `sharedBounds()`: When content changes but bounds should animate (text fields, containers)

3. **Navigation scope**
   - Use `SharedElementTransitionScope.AnimatedScope.Navigation` for screen transitions
   - This ensures animations work with Circuit's Navigator

4. **Performance considerations**
   - Shared element transitions are optimized by Compose
   - Avoid excessive nesting within shared elements

## Integration with Circuit

Circuit's architecture makes shared element transitions straightforward:

- Circuit screens already use Screen objects as keys
- Presenters manage state, making it easy to pass shared element keys
- Circuit's Navigator works seamlessly with SharedTransitionScope

## Example Use Cases for This App

1. **Expense List → Expense Detail**
   - Shared element: Category icon
   - Shared bounds: Amount text, description

2. **Income List → Income Detail**
   - Similar pattern to expenses

3. **Chat message appearance**
   - Use AnimatedVisibility with shared bounds for message bubbles

4. **Reports screen tab switching**
   - Shared bounds for content containers
   - AnimatedContent for tab content changes

---

## Additional Resources

- Circuit Changelog: https://slackhq.github.io/circuit/changelog
- Tutorial: https://slackhq.github.io/circuit/shared-elements-tutorial
- Compose Shared Elements: Official Compose documentation
