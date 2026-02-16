---
name: circuit-file-structure
description: Expert guidance on Circuit MVI 3-file structure pattern and Compose Multiplatform previews. Use when splitting monolithic screen files into Contract/Presenter/Ui, adding @Preview annotations, and creating PreviewParameterProvider or per-state previews for KMP projects.
---

# Circuit File Structure & Compose Previews Skill

## Overview

This skill defines the **3-file split pattern** for Circuit MVI screens and the **Compose Multiplatform preview pattern** for visual state documentation. It ensures consistent, AI-friendly file organization across KMP projects using Circuit + Metro DI.

## When to Use

- **Splitting screen files**: When a single-file Circuit screen exceeds ~250 lines
- **Adding previews**: When a screen or component has zero `@Preview` annotations
- **New feature creation**: When creating new screens from scratch (always use 3-file pattern)
- **Refactoring**: When restructuring existing Circuit screens for better maintainability
- **AI optimization**: When you want Claude Code to efficiently read/edit focused sections

## 3-File Split Pattern

### Structure

```
screens/{feature}/
├── {Feature}Contract.kt      # Screen definition + State + Event (~40-80 lines)
├── {Feature}Presenter.kt     # Presenter + Factory + present() (~100-250 lines)
├── {Feature}Ui.kt            # @CircuitInject UI + helpers + Previews (~150-300 lines)
```

### Why This Split Works

| File | AI Reads First | Purpose |
|------|----------------|---------|
| `Contract.kt` | Always | Understand the feature API (types, events, state shape) |
| `Presenter.kt` | For logic changes | Edit business logic without loading UI code |
| `Ui.kt` | For visual changes | Edit UI without loading business logic |

### When NOT to Split

- Files under ~150 lines (e.g., HomeScreen, SplashScreen)
- Pure navigation routers with minimal UI
- Screens that are unlikely to grow

---

## File 1: Contract (Screen Definition)

Contains ONLY the Screen class, State data class, and Event sealed interface. No Compose imports, no Metro DI, no presenter logic.

```kotlin
package org.example.screens.feed

import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import org.example.domain.model.Listing
import org.example.util.CommonParcelize

@CommonParcelize
data object FeedScreen : Screen {

    // Enums specific to this screen
    enum class FeedMode(val label: String) {
        LATEST("Latest"),
        FOR_YOU("For You"),
        TRENDING("Trending"),
    }

    data class State(
        val listings: List<Listing> = emptyList(),
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val eventSink: (Event) -> Unit = {},
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data object Refresh : Event
        data class ListingClicked(val listingId: String) : Event
    }
}
```

### Contract Rules
- **Default eventSink**: Always `val eventSink: (Event) -> Unit = {}` (enables previews)
- **No Compose imports**: Pure data definitions only
- **No DI imports**: No Metro, no Circuit codegen
- **Screen-specific types**: Enums, nested data classes go here
- **Parcelizable**: Always `@CommonParcelize` for state restoration

---

## File 2: Presenter (Logic)

Contains the Presenter class, AssistedFactory, and the `present()` composable function. All business logic, state management, and navigation lives here.

```kotlin
package org.example.screens.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import org.example.di.AppScope

@AssistedInject
class FeedPresenter(
    @Assisted private val navigator: Navigator,
    private val listingRepository: ListingRepository,
) : Presenter<FeedScreen.State> {

    @CircuitInject(FeedScreen::class, AppScope::class)
    @AssistedFactory
    fun interface Factory {
        fun create(navigator: Navigator): FeedPresenter
    }

    @Composable
    override fun present(): FeedScreen.State {
        var listings by rememberRetained { mutableStateOf(emptyList<Listing>()) }
        var isLoading by rememberRetained { mutableStateOf(true) }

        LaunchedEffect(Unit) {
            // Load data...
        }

        return FeedScreen.State(
            listings = listings,
            isLoading = isLoading,
        ) { event ->
            when (event) {
                is FeedScreen.Event.Refresh -> { /* ... */ }
                is FeedScreen.Event.ListingClicked -> { /* ... */ }
            }
        }
    }
}
```

### Presenter Rules
- **Private constants**: `private const val PAGE_SIZE = 20` go at file top
- **Private helper functions**: Validation functions, data transforms go at file bottom
- **@CircuitInject on Factory**: NOT on the Presenter class itself
- **@AssistedInject on Presenter**: Constructor injection pattern
- **Screen-arg presenters**: Use `@Assisted private val screen: FeatureScreen` for data class screens

---

## File 3: Ui (Composable + Previews)

Contains the `@CircuitInject` composable, all private helper composables, and preview functions at the bottom.

```kotlin
package org.example.screens.feed

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.slack.circuit.codegen.annotations.CircuitInject
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.example.di.AppScope
import org.example.ui.preview.PreviewData
import org.example.ui.theme.AppTheme

@CircuitInject(FeedScreen::class, AppScope::class)
@Composable
fun FeedUi(state: FeedScreen.State, modifier: Modifier = Modifier) {
    // Main UI implementation...
}

@Composable
private fun FeedListItem(/* ... */) { /* ... */ }

// --- Previews ---

@Preview
@Composable
private fun FeedLoadingPreview() {
    AppTheme {
        FeedUi(state = FeedScreen.State(isLoading = true))
    }
}

@Preview
@Composable
private fun FeedLoadedPreview() {
    AppTheme {
        FeedUi(
            state = FeedScreen.State(
                listings = PreviewData.listings,
            ),
        )
    }
}

@Preview
@Composable
private fun FeedEmptyPreview() {
    AppTheme {
        FeedUi(state = FeedScreen.State())
    }
}

@Preview
@Composable
private fun FeedErrorPreview() {
    AppTheme {
        FeedUi(state = FeedScreen.State(errorMessage = "Network error"))
    }
}
```

### Ui Rules
- **@CircuitInject on the top-level composable**: `@CircuitInject(Screen::class, AppScope::class)`
- **Private helpers**: All sub-composables are `private`
- **Previews at bottom**: Separated by `// --- Previews ---` comment
- **4 standard preview states**: Loading, Loaded, Empty, Error
- **Theme wrapper**: Always wrap in `AppTheme { ... }` or `CurioTheme { ... }`
- **Default eventSink**: Contract's `eventSink = {}` default enables previews without event handling

---

## Compose Multiplatform Preview Pattern

### Import (CMP - NOT AndroidX)

```kotlin
// CORRECT for Compose Multiplatform:
import org.jetbrains.compose.ui.tooling.preview.Preview

// WRONG (AndroidX only - won't compile in commonMain):
// import androidx.compose.ui.tooling.preview.Preview
```

### PreviewParameterProvider (CMP Availability)

As of Compose Multiplatform 1.10.0, `PreviewParameterProvider` and `@PreviewParameter` are available:

```kotlin
import org.jetbrains.compose.ui.tooling.preview.PreviewParameter
import org.jetbrains.compose.ui.tooling.preview.PreviewParameterProvider
```

If they are NOT available in the project's CMP version, use individual `@Preview` functions instead (one per state).

### Individual Preview Pattern (Recommended)

```kotlin
@Preview
@Composable
private fun ScreenLoadingPreview() {
    AppTheme { ScreenUi(state = Screen.State(isLoading = true)) }
}

@Preview
@Composable
private fun ScreenLoadedPreview() {
    AppTheme { ScreenUi(state = Screen.State(data = PreviewData.items)) }
}

@Preview
@Composable
private fun ScreenEmptyPreview() {
    AppTheme { ScreenUi(state = Screen.State()) }
}

@Preview
@Composable
private fun ScreenErrorPreview() {
    AppTheme { ScreenUi(state = Screen.State(errorMessage = "Network error")) }
}
```

### PreviewParameterProvider Pattern (Alternative)

```kotlin
private class ScreenStateProvider : PreviewParameterProvider<Screen.State> {
    override val values = sequenceOf(
        Screen.State(isLoading = true),
        Screen.State(data = PreviewData.items),
        Screen.State(),
        Screen.State(errorMessage = "Network error"),
    )
}

@Preview
@Composable
private fun ScreenPreview(
    @PreviewParameter(ScreenStateProvider::class) state: Screen.State,
) {
    AppTheme { ScreenUi(state = state) }
}
```

---

## PreviewData Object

Create a shared `PreviewData` object for sample data reused across all previews:

```kotlin
// ui/preview/PreviewData.kt
package org.example.ui.preview

import org.example.domain.model.*

object PreviewData {
    val user = UserProfile(
        id = "user-1",
        username = "johndoe",
        displayName = "John Doe",
    )

    val listings = listOf(
        Listing(
            id = "1",
            sellerId = "seller-1",
            title = "Sample Item",
            description = "A sample listing for previews.",
            category = "Electronics",
            condition = ItemCondition.GOOD,
            price = 99.99,
            imageUrls = emptyList(),
            createdAt = "2026-01-01",
        ),
        // ... more sample items
    )

    val sellerNames = mapOf("seller-1" to "Jane's Shop")
}
```

### PreviewData Rules
- **Location**: `ui/preview/PreviewData.kt`
- **Single object**: One `PreviewData` object for the whole app
- **Realistic data**: Use plausible names, prices, descriptions
- **No network deps**: Never fetch data; use hardcoded values
- **Cover all models**: Include samples for every domain model used in previews

---

## Component Previews

Shared UI components (`ui/components/`) should also have previews, added at the bottom of each component file:

```kotlin
// ui/components/ListingCard.kt

@Composable
fun ListingCard(/* params */) { /* implementation */ }

// --- Previews ---

@Preview
@Composable
private fun ListingCardPreview() {
    AppTheme {
        ListingCard(
            imageUrl = "",
            title = "Vintage Camera",
            price = 120.0,
            condition = "Good",
            sellerName = "Jane's Shop",
            isSaved = false,
            onClick = {},
            onSaveClick = {},
        )
    }
}

@Preview
@Composable
private fun ListingCardSavedPreview() {
    AppTheme {
        ListingCard(
            imageUrl = "",
            title = "Running Shoes",
            price = 45.99,
            condition = "Like New",
            sellerName = "Mike's Gear",
            isSaved = true,
            onClick = {},
            onSaveClick = {},
        )
    }
}
```

---

## Chat Screens (Multi-Screen Files)

When a single file contains multiple screens (e.g., `ChatScreens.kt` with ConversationsScreen + ChatDetailScreen), split into **6 files** in the SAME package directory:

```
screens/chat/
├── ConversationsContract.kt
├── ConversationsPresenter.kt
├── ConversationsUi.kt
├── ChatDetailContract.kt
├── ChatDetailPresenter.kt
├── ChatDetailUi.kt
```

---

## Migration Checklist

When splitting an existing monolithic screen file:

1. **Create Contract.kt**: Extract Screen/State/Event (no Compose/DI imports)
2. **Create Presenter.kt**: Extract Presenter class + Factory + present() + private helpers
3. **Create Ui.kt**: Extract @CircuitInject UI + private composables
4. **Add Previews**: 4 standard states (Loading, Loaded, Empty, Error)
5. **Delete Original**: Remove the monolithic file
6. **Verify Tests**: Same package = no import changes needed
7. **Compile Check**: `./gradlew compileKotlinAndroid`
8. **Run Tests**: `./gradlew allTests`

### Import Safety

Since all 3 files stay in the **same package**, test imports DO NOT change. Kotlin resolves classes by package, not file name.

### Build Dependencies

The preview dependency must be in `build.gradle.kts`:
```kotlin
commonMain.dependencies {
    implementation(compose.uiToolingPreview)  // Enables @Preview in commonMain
}
```

For Android debug builds:
```kotlin
androidMain.dependencies {
    implementation(compose.uiTooling)  // Renders previews in Android Studio
}
```

---

## Anti-Patterns

| Anti-Pattern | Correct Pattern |
|-------------|-----------------|
| Splitting files under 150 lines | Keep small screens as single files |
| Moving Contract to different package | Keep all 3 files in same package |
| Preview with real data fetching | Use `PreviewData` hardcoded object |
| Preview without theme wrapper | Always wrap in `AppTheme { }` |
| Using `androidx.compose.ui.tooling.preview` in commonMain | Use `org.jetbrains.compose.ui.tooling.preview` |
| Putting validation functions in Contract | Put them in Presenter (logic file) |
| Making helper composables `internal` | Make them `private` |
| Previews without `eventSink = {}` | Contract State MUST have default `eventSink = {}` |
