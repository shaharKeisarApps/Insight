# Parcelize Pattern for KMP

Circuit's `Screen` interface requires screens to be `Parcelable` on Android for back stack persistence. However, `Parcelable` is an Android-specific API. This document explains the expect/actual pattern to make `@Parcelize` work in KMP.

## The Problem

Circuit screens must implement `Parcelable`:

```kotlin
// This won't compile in commonMain - Parcelable is Android-only!
@Parcelize
data object ExpensesScreen : Screen, Parcelable
```

Errors you'll see:
- `Unresolved reference: Parcelize`
- `Unresolved reference: Parcelable`

## The Solution: expect/actual Pattern

Create multiplatform abstractions that map to real implementations on Android and no-ops on iOS.

### Step 1: Add kotlin-parcelize Plugin

In `core/common/build.gradle.kts`:

```kotlin
plugins {
    id("insight.kmp.library")
    alias(libs.plugins.kotlin.parcelize)  // Required for Android actual
}
```

### Step 2: Create expect Declarations

**core/common/src/commonMain/kotlin/.../parcelize/Parcelize.kt:**

```kotlin
package com.keisardev.insight.core.common.parcelize

/**
 * Multiplatform Parcelable interface.
 * On Android, this maps to android.os.Parcelable.
 * On other platforms, it's an empty marker interface.
 */
expect interface Parcelable

/**
 * Multiplatform Parcelize annotation.
 * On Android, this maps to kotlinx.parcelize.Parcelize.
 * On other platforms, it's a no-op annotation.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
expect annotation class Parcelize()
```

### Step 3: Create Android actual Implementations

**core/common/src/androidMain/kotlin/.../parcelize/Parcelize.android.kt:**

```kotlin
package com.keisardev.insight.core.common.parcelize

/**
 * Android implementation - maps to actual Android Parcelable.
 */
actual typealias Parcelable = android.os.Parcelable

/**
 * Android implementation - maps to kotlinx.parcelize.Parcelize.
 */
actual typealias Parcelize = kotlinx.parcelize.Parcelize
```

### Step 4: Create iOS actual Implementations

**core/common/src/iosMain/kotlin/.../parcelize/Parcelize.ios.kt:**

```kotlin
package com.keisardev.insight.core.common.parcelize

/**
 * iOS implementation - empty marker interface (no Parcelable on iOS).
 */
actual interface Parcelable

/**
 * iOS implementation - no-op annotation.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
actual annotation class Parcelize()
```

## Usage in Screen Classes

Now you can use these in your Circuit screens:

```kotlin
package com.keisardev.insight.feature.expenses

import com.keisardev.insight.core.common.parcelize.Parcelize
import com.keisardev.insight.core.common.parcelize.Parcelable
import com.slack.circuit.runtime.screen.Screen

@Parcelize
data object ExpensesScreen : Screen, Parcelable {
    data class State(
        val isLoading: Boolean,
        val expenses: List<Expense>,
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data object AddExpense : Event
        data class ExpenseClick(val id: Long) : Event
    }
}

// For screens with parameters
@Parcelize
data class AddEditExpenseScreen(
    val expenseId: Long? = null
) : Screen, Parcelable {
    // State and Event definitions...
}
```

## Key Points

1. **Import from your package, not kotlinx.parcelize:**
   ```kotlin
   // CORRECT
   import com.keisardev.insight.core.common.parcelize.Parcelize
   import com.keisardev.insight.core.common.parcelize.Parcelable

   // WRONG - won't work in commonMain
   import kotlinx.parcelize.Parcelize
   import android.os.Parcelable
   ```

2. **Both @Parcelize AND Parcelable are needed:**
   ```kotlin
   @Parcelize                              // Annotation
   data object MyScreen : Screen, Parcelable  // Interface
   ```

3. **kotlin-parcelize plugin must be applied:**
   The Android actual uses `typealias` to `kotlinx.parcelize.Parcelize`, which requires the plugin.

4. **Works with data objects and data classes:**
   ```kotlin
   @Parcelize
   data object SettingsScreen : Screen, Parcelable

   @Parcelize
   data class DetailScreen(val id: Long) : Screen, Parcelable
   ```

## File Structure

```
core/common/src/
├── commonMain/kotlin/com/keisardev/insight/core/common/
│   └── parcelize/
│       └── Parcelize.kt                 # expect declarations
├── androidMain/kotlin/com/keisardev/insight/core/common/
│   └── parcelize/
│       └── Parcelize.android.kt         # Android actual (typealias)
└── iosMain/kotlin/com/keisardev/insight/core/common/
    └── parcelize/
        └── Parcelize.ios.kt             # iOS actual (no-op)
```

## Why This Works

- **Android:** `typealias` makes our annotations identical to the real ones, so the Kotlin compiler's parcelize plugin generates the proper `Parcelable` implementation.
- **iOS:** The no-op annotation and empty interface are completely ignored at runtime. iOS doesn't need parcelization since there's no Android back stack restoration mechanism.

## Checklist for Each Screen

When converting a Screen to KMP:

- [ ] Add `@Parcelize` annotation (from your package)
- [ ] Implement `Parcelable` interface (from your package)
- [ ] Update imports to use your package, not kotlinx.parcelize
- [ ] Ensure `core:common` module is a dependency
- [ ] Verify build compiles for both Android and iOS
