# Circuit Overlays Examples

> Circuit Version: 0.32.0

## Example 1: BottomSheetOverlay (CatchUp Pattern)

The canonical pattern for showing a bottom sheet from a Presenter. Uses `LocalOverlayHost.current` and `rememberStableCoroutineScope()` to launch the overlay imperatively.

```kotlin
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slack.circuit.overlay.LocalOverlayHost
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuitx.overlays.BottomSheetOverlay
import kotlinx.collections.immutable.ImmutableList
import com.slack.circuit.retained.rememberStableCoroutineScope
import kotlinx.coroutines.launch

data class ShareOption(val label: String, val id: String)

sealed interface ShareResult {
    data class Selected(val optionId: String) : ShareResult
    data object Dismissed : ShareResult
}

@Composable
fun SharePresenter(options: ImmutableList<ShareOption>): ShareScreen.State {
    val overlayHost = LocalOverlayHost.current
    val scope = rememberStableCoroutineScope()

    return ShareScreen.State(
        eventSink = { event ->
            when (event) {
                ShareScreen.Event.ShowShareSheet -> {
                    scope.launch {
                        val result = overlayHost.show(
                            BottomSheetOverlay(
                                model = options,
                                onDismiss = { ShareResult.Dismissed },
                            ) { model, navigator ->
                                ShareSheetContent(model) { selectedId ->
                                    navigator.finish(ShareResult.Selected(selectedId))
                                }
                            }
                        )
                        when (result) {
                            is ShareResult.Selected -> {
                                // Handle selected share option
                            }
                            ShareResult.Dismissed -> {
                                // User dismissed the sheet
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun ShareSheetContent(
    options: ImmutableList<ShareOption>,
    onSelect: (String) -> Unit,
) {
    Column {
        options.forEach { option ->
            Text(
                text = option.label,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(option.id) }
                    .padding(16.dp),
            )
        }
    }
}
```

---

## Example 2: Confirmation Dialog with alertDialogOverlay

Using the `alertDialogOverlay()` factory function for a standard confirm/cancel dialog that returns `DialogResult`.

```kotlin
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.slack.circuit.overlay.LocalOverlayHost
import com.slack.circuit.retained.rememberStableCoroutineScope
import com.slack.circuitx.overlays.DialogResult
import com.slack.circuitx.overlays.alertDialogOverlay
import kotlinx.coroutines.launch

@Composable
fun DeleteItemPresenter(itemId: String): DeleteItemScreen.State {
    val overlayHost = LocalOverlayHost.current
    val scope = rememberStableCoroutineScope()
    var isDeleted by rememberRetained { mutableStateOf(false) }

    return DeleteItemScreen.State(
        isDeleted = isDeleted,
        eventSink = { event ->
            when (event) {
                DeleteItemScreen.Event.ConfirmDelete -> {
                    scope.launch {
                        val result = overlayHost.show(
                            alertDialogOverlay(
                                title = { Text("Delete Item") },
                                text = { Text("Are you sure you want to delete this item? This action cannot be undone.") },
                                confirmButton = { onClick ->
                                    TextButton(onClick = { onClick() }) {
                                        Text("Delete")
                                    }
                                },
                                dismissButton = { onClick ->
                                    TextButton(onClick = { onClick() }) {
                                        Text("Cancel")
                                    }
                                },
                            )
                        )
                        when (result) {
                            DialogResult.Confirm -> {
                                isDeleted = true
                                // Proceed with deletion
                            }
                            DialogResult.Cancel -> {
                                // User cancelled via button
                            }
                            DialogResult.Dismiss -> {
                                // User dismissed by tapping outside
                            }
                        }
                    }
                }
            }
        }
    )
}
```

---

## Example 3: Custom Picker Overlay

A fully custom `Overlay<PickerResult>` for a list-based selection picker, demonstrating how to implement the `Overlay` fun interface directly.

```kotlin
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.slack.circuit.overlay.LocalOverlayHost
import com.slack.circuit.overlay.Overlay
import com.slack.circuit.overlay.OverlayNavigator
import com.slack.circuit.retained.rememberStableCoroutineScope
import kotlinx.coroutines.launch

data class PickerItem(val id: String, val label: String)

sealed interface PickerResult {
    data class Selected(val item: PickerItem) : PickerResult
    data object Cancelled : PickerResult
}

class ItemPickerOverlay(
    private val title: String,
    private val items: List<PickerItem>,
) : Overlay<PickerResult> {

    @Composable
    override fun Content(navigator: OverlayNavigator<PickerResult>) {
        Dialog(onDismissRequest = { navigator.finish(PickerResult.Cancelled) }) {
            Surface(
                shape = MaterialTheme.shapes.large,
                tonalElevation = 6.dp,
            ) {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(16.dp),
                    )
                    HorizontalDivider()
                    LazyColumn {
                        items(items, key = { it.id }) { item ->
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        navigator.finish(PickerResult.Selected(item))
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

// Usage in a Presenter:
@Composable
fun CategoryPresenter(categories: List<PickerItem>): CategoryScreen.State {
    val overlayHost = LocalOverlayHost.current
    val scope = rememberStableCoroutineScope()

    return CategoryScreen.State(
        eventSink = { event ->
            when (event) {
                CategoryScreen.Event.PickCategory -> {
                    scope.launch {
                        val result = overlayHost.show(
                            ItemPickerOverlay(
                                title = "Select Category",
                                items = categories,
                            )
                        )
                        when (result) {
                            is PickerResult.Selected -> {
                                // Handle selected category
                            }
                            PickerResult.Cancelled -> {
                                // User cancelled the picker
                            }
                        }
                    }
                }
            }
        }
    )
}
```

---

## Example 4: OverlayEffect Usage

Using `OverlayEffect` composable for declarative overlay management. This is the recommended modern API -- no need for manual `scope.launch` or `LocalOverlayHost.current`.

```kotlin
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.slack.circuit.overlay.OverlayEffect
import com.slack.circuit.retained.rememberRetained
import com.slack.circuitx.overlays.DialogResult
import com.slack.circuitx.overlays.alertDialogOverlay

@Composable
fun LogoutPresenter(): LogoutScreen.State {
    var pendingLogout by rememberRetained { mutableStateOf(false) }
    var loggedOut by rememberRetained { mutableStateOf(false) }

    // OverlayEffect triggers when pendingLogout becomes true
    if (pendingLogout) {
        OverlayEffect(pendingLogout) {
            val result = show(
                alertDialogOverlay(
                    title = { Text("Log Out") },
                    text = { Text("You will need to sign in again to continue.") },
                    confirmButton = { onClick ->
                        TextButton(onClick = { onClick() }) {
                            Text("Log Out")
                        }
                    },
                    dismissButton = { onClick ->
                        TextButton(onClick = { onClick() }) {
                            Text("Stay")
                        }
                    },
                )
            )
            pendingLogout = false
            when (result) {
                DialogResult.Confirm -> {
                    loggedOut = true
                }
                DialogResult.Cancel,
                DialogResult.Dismiss -> {
                    // User decided to stay
                }
            }
        }
    }

    return LogoutScreen.State(
        loggedOut = loggedOut,
        eventSink = { event ->
            when (event) {
                LogoutScreen.Event.RequestLogout -> {
                    pendingLogout = true
                }
            }
        }
    )
}
```

### OverlayEffect with BottomSheetOverlay

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.slack.circuit.overlay.OverlayEffect
import com.slack.circuit.retained.rememberRetained
import com.slack.circuitx.overlays.BottomSheetOverlay

@Composable
fun SortPresenter(currentSort: SortOption): SortScreen.State {
    var showSortPicker by rememberRetained { mutableStateOf(false) }
    var selectedSort by rememberRetained { mutableStateOf(currentSort) }

    if (showSortPicker) {
        OverlayEffect(showSortPicker) {
            val result = show(
                BottomSheetOverlay(
                    model = SortOption.entries,
                    onDismiss = { null },
                ) { options, navigator ->
                    SortOptionList(options, selectedSort) { chosen ->
                        navigator.finish(chosen)
                    }
                }
            )
            showSortPicker = false
            if (result != null) {
                selectedSort = result
            }
        }
    }

    return SortScreen.State(
        currentSort = selectedSort,
        eventSink = { event ->
            when (event) {
                SortScreen.Event.ShowSortPicker -> {
                    showSortPicker = true
                }
            }
        }
    )
}
```

---

## Example 5: Full Screen Overlay with Predictive Back

Using `showFullScreenOverlay()` to display a screen as a full-screen overlay. Supports predictive back gestures (v0.29.0+). The overlay returns `PopResult?` when dismissed.

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.slack.circuit.overlay.LocalOverlayHost
import com.slack.circuit.overlay.OverlayEffect
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.retained.rememberStableCoroutineScope
import com.slack.circuit.runtime.screen.PopResult
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuitx.overlays.showFullScreenOverlay
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

// Define the screen to show as a full-screen overlay
@Parcelize
data class ImageViewerScreen(
    val imageUrl: String,
    val imageId: String,
) : Screen {
    // Optional: define a PopResult for typed return values
    @Parcelize
    data class CloseResult(val action: String) : PopResult
}

// Approach 1: Using OverlayEffect (recommended)
@Composable
fun PhotoGalleryPresenter(): PhotoGalleryScreen.State {
    var selectedImageUrl by rememberRetained { mutableStateOf<String?>(null) }

    selectedImageUrl?.let { url ->
        OverlayEffect(url) {
            val result = showFullScreenOverlay(
                ImageViewerScreen(imageUrl = url, imageId = "img-1")
            )
            selectedImageUrl = null
            // result is PopResult? -- handle if the screen returned data
        }
    }

    return PhotoGalleryScreen.State(
        eventSink = { event ->
            when (event) {
                is PhotoGalleryScreen.Event.ImageClicked -> {
                    selectedImageUrl = event.url
                }
            }
        }
    )
}

// Approach 2: Using scope.launch (imperative)
@Composable
fun PhotoGalleryPresenterImperative(): PhotoGalleryScreen.State {
    val overlayHost = LocalOverlayHost.current
    val scope = rememberStableCoroutineScope()

    return PhotoGalleryScreen.State(
        eventSink = { event ->
            when (event) {
                is PhotoGalleryScreen.Event.ImageClicked -> {
                    scope.launch {
                        val popResult = overlayHost.showFullScreenOverlay(
                            ImageViewerScreen(
                                imageUrl = event.url,
                                imageId = event.imageId,
                            )
                        )
                        // popResult is PopResult? from the full-screen
                        // User can swipe back with predictive back gesture
                    }
                }
            }
        }
    )
}
```

---

## Example 6: Chained Overlays

Showing one overlay after another based on the result. Because `OverlayHost.show()` suspends and only one overlay shows at a time, sequential calls naturally chain.

```kotlin
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slack.circuit.overlay.LocalOverlayHost
import com.slack.circuit.retained.rememberStableCoroutineScope
import com.slack.circuitx.overlays.BottomSheetOverlay
import com.slack.circuitx.overlays.DialogResult
import com.slack.circuitx.overlays.alertDialogOverlay
import kotlinx.coroutines.launch

sealed interface FileAction {
    data object Delete : FileAction
    data object Rename : FileAction
    data object Share : FileAction
    data object Cancelled : FileAction
}

@Composable
fun FileOptionsPresenter(fileName: String): FileOptionsScreen.State {
    val overlayHost = LocalOverlayHost.current
    val scope = rememberStableCoroutineScope()

    return FileOptionsScreen.State(
        eventSink = { event ->
            when (event) {
                FileOptionsScreen.Event.ShowOptions -> {
                    scope.launch {
                        // Step 1: Show bottom sheet with options
                        val action = overlayHost.show(
                            BottomSheetOverlay(
                                model = fileName,
                                onDismiss = { FileAction.Cancelled },
                            ) { name, navigator ->
                                Column {
                                    Text(
                                        text = "Delete \"$name\"",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { navigator.finish(FileAction.Delete) }
                                            .padding(16.dp),
                                    )
                                    Text(
                                        text = "Rename",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { navigator.finish(FileAction.Rename) }
                                            .padding(16.dp),
                                    )
                                    Text(
                                        text = "Share",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { navigator.finish(FileAction.Share) }
                                            .padding(16.dp),
                                    )
                                }
                            }
                        )

                        // Step 2: If delete was selected, show confirmation dialog
                        when (action) {
                            FileAction.Delete -> {
                                val confirmation = overlayHost.show(
                                    alertDialogOverlay(
                                        title = { Text("Delete \"$fileName\"?") },
                                        text = { Text("This file will be permanently deleted.") },
                                        confirmButton = { onClick ->
                                            TextButton(onClick = { onClick() }) {
                                                Text("Delete")
                                            }
                                        },
                                        dismissButton = { onClick ->
                                            TextButton(onClick = { onClick() }) {
                                                Text("Cancel")
                                            }
                                        },
                                    )
                                )
                                if (confirmation == DialogResult.Confirm) {
                                    // Proceed with actual deletion
                                }
                            }
                            FileAction.Rename -> {
                                // Could chain to a rename input overlay
                            }
                            FileAction.Share -> {
                                // Could chain to a share target picker overlay
                            }
                            FileAction.Cancelled -> {
                                // User dismissed without choosing
                            }
                        }
                    }
                }
            }
        }
    )
}
```

---

## Example 7: Overlay in Presenter with Navigation

Show an overlay and based on its result, navigate to a different screen using Circuit's `Navigator`.

```kotlin
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slack.circuit.overlay.LocalOverlayHost
import com.slack.circuit.retained.rememberStableCoroutineScope
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuitx.overlays.BottomSheetOverlay
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

@Parcelize data object SettingsScreen : Screen
@Parcelize data object ProfileScreen : Screen
@Parcelize data object HelpScreen : Screen

sealed interface MenuAction {
    data object Settings : MenuAction
    data object Profile : MenuAction
    data object Help : MenuAction
    data object Dismissed : MenuAction
}

@Composable
fun HomePresenter(navigator: Navigator): HomeScreen.State {
    val overlayHost = LocalOverlayHost.current
    val scope = rememberStableCoroutineScope()

    return HomeScreen.State(
        eventSink = { event ->
            when (event) {
                HomeScreen.Event.ShowMenu -> {
                    scope.launch {
                        val action = overlayHost.show(
                            BottomSheetOverlay(
                                model = Unit,
                                onDismiss = { MenuAction.Dismissed },
                            ) { _, overlayNavigator ->
                                Column {
                                    Text(
                                        text = "Settings",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                overlayNavigator.finish(MenuAction.Settings)
                                            }
                                            .padding(16.dp),
                                    )
                                    Text(
                                        text = "Profile",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                overlayNavigator.finish(MenuAction.Profile)
                                            }
                                            .padding(16.dp),
                                    )
                                    Text(
                                        text = "Help",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                overlayNavigator.finish(MenuAction.Help)
                                            }
                                            .padding(16.dp),
                                    )
                                }
                            }
                        )

                        // Navigate based on overlay result
                        when (action) {
                            MenuAction.Settings -> navigator.goTo(SettingsScreen)
                            MenuAction.Profile -> navigator.goTo(ProfileScreen)
                            MenuAction.Help -> navigator.goTo(HelpScreen)
                            MenuAction.Dismissed -> {
                                // No navigation needed
                            }
                        }
                    }
                }
            }
        }
    )
}
```

### Alternative: OverlayEffect + Navigation

Using `OverlayEffect` with navigation based on overlay result:

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.slack.circuit.overlay.OverlayEffect
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.Navigator
import com.slack.circuitx.overlays.BottomSheetOverlay

@Composable
fun HomePresenterDeclarative(navigator: Navigator): HomeScreen.State {
    var showMenu by rememberRetained { mutableStateOf(false) }

    if (showMenu) {
        OverlayEffect(showMenu) {
            val action = show(
                BottomSheetOverlay(
                    model = Unit,
                    onDismiss = { MenuAction.Dismissed },
                ) { _, overlayNavigator ->
                    MenuContent { chosen -> overlayNavigator.finish(chosen) }
                }
            )
            showMenu = false
            when (action) {
                MenuAction.Settings -> navigator.goTo(SettingsScreen)
                MenuAction.Profile -> navigator.goTo(ProfileScreen)
                MenuAction.Help -> navigator.goTo(HelpScreen)
                MenuAction.Dismissed -> { /* no-op */ }
            }
        }
    }

    return HomeScreen.State(
        eventSink = { event ->
            when (event) {
                HomeScreen.Event.ShowMenu -> {
                    showMenu = true
                }
            }
        }
    )
}
```
