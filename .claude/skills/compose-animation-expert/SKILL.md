---
name: compose-animation-expert
description: Expert guidance on Compose animations for KMP. Use for AnimatedVisibility, transitions, shared elements, gesture-driven animation, and Material Motion patterns.
---

# Compose Animation Expert Skill

## Overview

Animations separate polished production apps from prototypes. This skill covers the full Compose animation toolkit: visibility transitions, content swaps, value-driven animations, coordinated multi-property transitions, gesture-driven motion, and shared element transitions across screens.

## When to use

- **Screen transitions**: Navigating between screens with shared elements or cross-fades.
- **Loading states**: Shimmer effects, skeleton screens, progress indicators.
- **Micro-interactions**: Button press feedback, toggle switches, icon morphing.
- **Content swaps**: Switching between Loading, Success, and Error states.
- **Gesture feedback**: Swipe to dismiss, drag to reorder, pull to refresh.

## Core Rules

| Pattern | API | When |
|---------|-----|------|
| Show/Hide | `AnimatedVisibility` | Toggle element presence with enter/exit transitions |
| Content swap | `AnimatedContent` | Replace one composable with another (e.g., state changes) |
| Simple value | `animate*AsState` | Animate a single value (Dp, Color, Float, Int, Size, Offset) |
| Coordinated | `updateTransition` | Animate multiple properties together on a single state change |
| Infinite | `rememberInfiniteTransition` | Looping animations (shimmer, pulse, rotation) |
| Low-level | `Animatable` | Full control with `snapTo`, `animateTo`, coroutine-driven |
| Size change | `Modifier.animateContentSize` | Smoothly resize when content dimensions change |
| Shared elements | `SharedTransitionLayout` | Shared element transitions between screens |

## Animation Spec Types

| Spec | Use case | Notes |
|------|----------|-------|
| `spring()` | **Recommended default** | Physics-based, no fixed duration, natural feel |
| `tween()` | Fixed-duration, easing curves | Good for opacity fades, color changes |
| `keyframes()` | Multi-step value sequences | Define values at specific time points |
| `snap()` | Instant jump | No animation, immediate value change |

## Best Practices

- **Prefer spring physics** for movement and scaling -- they feel natural and handle interruption gracefully.
- **Use tween for opacity and color** where a fixed duration with easing is expected.
- **Avoid recomposition during animation** -- hoist animated values and pass them down as parameters.
- **Use `derivedStateOf`** when computing values from animated state to prevent unnecessary recompositions.
- **Always provide the `label` parameter** on `animate*AsState` and `updateTransition` for debugging in the Animation Preview inspector.
- **Use `animateItemPlacement`** (or `animateItem` in newer APIs) in `LazyColumn`/`LazyRow` for smooth list reordering.
- **Test animations** with `ComposeTestRule` by advancing the clock manually.

## Common Pitfalls

- **Animating in LazyColumn without `key`**: Items recompose instead of moving. Always provide a stable `key` per item.
- **Using `tween` when `spring` is better**: Tween animations feel robotic for position/scale changes. Use spring for physical movement.
- **Forgetting `label` parameter**: Makes the Animation Preview inspector unusable for debugging.
- **Nesting `AnimatedVisibility` inside `AnimatedContent`**: Causes competing animations. Choose one wrapping strategy.
- **Infinite animations without `DisposableEffect` cleanup**: Can leak if the composable leaves composition mid-animation (though `rememberInfiniteTransition` handles this correctly).
- **Animating layout size in tight loops**: Can cause frame drops. Use `Modifier.animateContentSize()` instead of manually animating width/height.

## See Also

- [compose-ui-expert](../compose-ui-expert/SKILL.md) -- Layout and modifier fundamentals
- [compose-material3-expert](../compose-material3-expert/SKILL.md) -- M3 motion tokens and component animations
- [performance-expert](../performance-expert/SKILL.md) -- Profiling animation frame drops
