---
name: compose-runtime-expert
description: Deep dive into Compose Runtime internals. Use for advanced optimization, custom tree management, Snapshot system manipulation, or debugging obscure recomposition issues.
---

# Compose Runtime Expert Skill

## Overview

Deep expertise in Compose Runtime internals including the Snapshot system, recomposition mechanics, custom Appliers, side effects, and CompositionLocals. This skill covers the engine beneath Compose UI.

## When to Use

- Debugging unexpected recompositions or performance issues
- Building custom Compose targets (non-UI trees, terminal UI, test appliers)
- Understanding Snapshot isolation, merge conflicts, or threading
- Optimizing with `derivedStateOf`, `snapshotFlow`, or custom `SnapshotMutationPolicy`
- Working with `CompositionLocal` (static vs dynamic) tradeoffs

## The Snapshot System

Compose uses a snapshot-based state system.

### Snapshot Mechanics

- **Global Snapshot**: The committed state.
- **MutableSnapshot**: A transaction-like state for a specific thread/scope.

```kotlin
val snapshot = Snapshot.takeMutableSnapshot()
try {
    snapshot.enter {
        // Modifications here are isolated
        state.value = "New Value"
    }
    snapshot.apply().check() // Attempt commit
} finally {
    snapshot.dispose()
}
```

### SnapshotMutationPolicy

Controls how equality is determined and how merges happen.

- `structuralEqualityPolicy()`: `a == b`
- `referentialEqualityPolicy()`: `a === b`
- `neverEqualPolicy()`: Always invalidates.

## Recomposition Internals

### Scopes and Groups

Compose compiler inserts "Groups" to track structure.
- **Restart Group**: Can be recomposed independently (e.g., functions).
- **Replace Group**: Structure changing (if/else).

### Skipping

A Composable skips if:
1.  All parameters are Stable.
2.  Input values haven't changed (using `equals`).

## Custom Appliers

You can run Compose on things that aren't UI (e.g., a DOM, a Node Tree, terminal UI).

```kotlin
class NodeApplier(root: Node) : AbstractApplier<Node>(root) {
    override fun onClear() { root.children.clear() }
    override fun insertTopDown(index: Int, instance: Node) { /* ... */ }
    override fun insertBottomUp(index: Int, instance: Node) {
        current.children.add(index, instance)
    }
    // ... move, remove
}

fun Composition(content: @Composable () -> Unit) {
    val composition = Composition(NodeApplier(root), Recomposer(currentCoroutineContext()))
    composition.setContent(content)
}
```

## Side Effects (Internal)

- **SideEffect**: Runs after composition is **committed** (post-commit side effect). Executes after the composition successfully applies, not during composition.
- **LaunchedEffect**: Connects to the composition's lifecycle (via `RememberObserver`).

## CompositionLocals

- **Static**: `staticCompositionLocalOf`. Reads are **NOT** tracked by the snapshot system. When the value changes, the **entire** `CompositionLocalProvider` subtree is recomposed (expensive but rare changes).
- **Dynamic**: `compositionLocalOf`. Reads **ARE** tracked by the snapshot system. When the value changes, **only the Composables that read** the value are recomposed (efficient for frequent changes).

## See Also

- [compose-ui-expert](../compose-ui-expert/SKILL.md) -- Layout system, modifiers, drawing
- [compose-stability-expert](../compose-stability-expert/SKILL.md) -- Stability annotations, skipping optimization
- [performance-expert](../performance-expert/SKILL.md) -- Runtime profiling, Baseline Profiles
