# Accessibility Examples

## 1. Image ContentDescription

Meaningful images need a description. Decorative images must pass `null`.

```kotlin
// Meaningful image -- screen reader announces "Product photo: Running shoes"
@Composable
fun ProductImage(imageUrl: String, productName: String) {
    AsyncImage(
        model = imageUrl,
        contentDescription = "Product photo: $productName",
        modifier = Modifier.size(200.dp)
    )
}

// Decorative image -- screen reader skips entirely
@Composable
fun DecorativeDivider() {
    Image(
        painter = painterResource("divider_ornament.xml"),
        contentDescription = null,
        modifier = Modifier.fillMaxWidth()
    )
}

// Icon button -- description on the Icon component
@Composable
fun CartButton(itemCount: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Filled.ShoppingCart,
            contentDescription = "Shopping cart, $itemCount items"
        )
    }
}

// Icon next to text -- icon is decorative because text conveys meaning
@Composable
fun ErrorMessage(message: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.width(8.dp))
        Text(message, color = MaterialTheme.colorScheme.error)
    }
}
```

## 2. Custom Button with Semantics

Custom interactive elements need explicit `role`, `contentDescription`, and state.

```kotlin
@Composable
fun FavoriteToggle(
    isFavorite: Boolean,
    itemName: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .semantics {
                role = Role.Checkbox
                contentDescription = if (isFavorite) "Remove $itemName from favorites"
                    else "Add $itemName to favorites"
                toggleableState = if (isFavorite) ToggleableState.On else ToggleableState.Off
            }
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = null // Handled by parent semantics
        )
    }
}

// Custom button with disabled and loading states
@Composable
fun SubmitButton(enabled: Boolean, isLoading: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .semantics {
                role = Role.Button
                if (!enabled) disabled()
                if (isLoading) stateDescription = "Loading, please wait"
            }
            .clickable(enabled = enabled, onClick = onClick)
            .background(
                if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) CircularProgressIndicator(Modifier.size(24.dp))
        else Text("Submit", color = MaterialTheme.colorScheme.onPrimary)
    }
}
```

## 3. Form with Focus Management

Chain focus between fields using `FocusRequester`. Auto-focus the first field on entry.

```kotlin
@Composable
fun LoginForm(onSubmit: (String, String) -> Unit, errorMessage: String? = null) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Login", style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() })

        if (errorMessage != null) {
            Text(errorMessage, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics {
                    liveRegion = LiveRegionMode.Assertive
                    contentDescription = "Error: $errorMessage"
                })
        }

        OutlinedTextField(
            value = email, onValueChange = { email = it },
            label = { Text("Email address") },
            modifier = Modifier.fillMaxWidth().focusRequester(emailFocus),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
            singleLine = true
        )

        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth().focusRequester(passwordFocus),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); onSubmit(email, password) }),
            singleLine = true
        )

        Button(onClick = { focusManager.clearFocus(); onSubmit(email, password) },
            modifier = Modifier.fillMaxWidth(),
            enabled = email.isNotBlank() && password.isNotBlank()
        ) { Text("Sign In") }
    }

    LaunchedEffect(Unit) { emailFocus.requestFocus() }
}
```

## 4. Live Region for Dynamic Content

Use `liveRegion` to announce changes without user interaction.

```kotlin
// Countdown -- polite announcement so it does not interrupt
@Composable
fun CountdownTimer(totalSeconds: Int, onFinished: () -> Unit) {
    var remaining by remember { mutableIntStateOf(totalSeconds) }
    LaunchedEffect(totalSeconds) {
        while (remaining > 0) { delay(1000); remaining-- }
        onFinished()
    }
    Text("Time remaining: ${remaining}s",
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

// Status announcer -- assertive for errors, polite for info
@Composable
fun StatusAnnouncer(message: String?, isError: Boolean = false) {
    if (message != null) {
        Text(message, modifier = Modifier.semantics {
            liveRegion = if (isError) LiveRegionMode.Assertive else LiveRegionMode.Polite
        })
    }
}

// Cart badge -- announces count changes
@Composable
fun CartBadge(count: Int) {
    if (count > 0) {
        Badge(modifier = Modifier.semantics {
            contentDescription = "$count items in cart"
            liveRegion = LiveRegionMode.Polite
        }) { Text(count.toString()) }
    }
}
```

## 5. Grouped Semantics

Merge related elements into a single node to reduce screen reader verbosity.

```kotlin
// List item merged into: "John Doe, Senior Engineer, Online"
@Composable
fun ContactListItem(name: String, title: String, isOnline: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "$name, $title, ${if (isOnline) "Online" else "Offline"}"
            }
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(12.dp).background(if (isOnline) Color.Green else Color.Gray, CircleShape))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(title, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// Star rating -- replace 5 icons with single description
@Composable
fun StarRating(rating: Int, maxRating: Int = 5) {
    Row(modifier = Modifier.clearAndSetSemantics {
        contentDescription = "Rating: $rating out of $maxRating stars"
    }) {
        repeat(maxRating) { i ->
            Icon(
                if (i < rating) Icons.Filled.Star else Icons.Outlined.Star,
                contentDescription = null,
                tint = if (i < rating) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        }
    }
}
```

## 6. Custom Actions

Expose swipe/long-press actions through the screen reader actions menu.

```kotlin
@Composable
fun EmailListItem(
    sender: String, subject: String, preview: String, isRead: Boolean,
    onOpen: () -> Unit, onArchive: () -> Unit,
    onDelete: () -> Unit, onToggleRead: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
            .semantics(mergeDescendants = true) {
                customActions = listOf(
                    CustomAccessibilityAction("Archive") { onArchive(); true },
                    CustomAccessibilityAction("Delete") { onDelete(); true },
                    CustomAccessibilityAction(
                        if (isRead) "Mark as unread" else "Mark as read"
                    ) { onToggleRead(); true }
                )
            }
            .clickable(onClick = onOpen)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(sender, style = MaterialTheme.typography.titleSmall)
            Text(subject, style = MaterialTheme.typography.bodyMedium)
            Text(preview, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
    }
}
```

## 7. Accessibility Testing

Compose test assertions for verifying semantic properties.

```kotlin
class AccessibilityTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test fun productImage_hasContentDescription() {
        composeTestRule.setContent { ProductImage("https://example.com/shoe.jpg", "Running shoes") }
        composeTestRule.onNodeWithContentDescription("Product photo: Running shoes")
            .assertExists().assertIsDisplayed()
    }

    @Test fun favoriteToggle_hasCorrectSemantics() {
        composeTestRule.setContent { FavoriteToggle(true, "Blue sneakers", onToggle = {}) }
        composeTestRule.onNodeWithContentDescription("Remove Blue sneakers from favorites")
            .assertExists().assertHasClickAction().assertIsToggleable().assertIsOn()
    }

    @Test fun favoriteToggle_whenNotFavorite_isOff() {
        composeTestRule.setContent { FavoriteToggle(false, "Blue sneakers", onToggle = {}) }
        composeTestRule.onNodeWithContentDescription("Add Blue sneakers to favorites")
            .assertIsToggleable().assertIsOff()
    }

    @Test fun submitButton_hasButtonRole() {
        composeTestRule.setContent { SubmitButton(enabled = true, isLoading = false, onClick = {}) }
        composeTestRule.onNode(hasRole(Role.Button) and hasClickAction())
            .assertExists().assertIsEnabled()
    }

    @Test fun submitButton_whenDisabled_isNotEnabled() {
        composeTestRule.setContent { SubmitButton(enabled = false, isLoading = false, onClick = {}) }
        composeTestRule.onNode(hasRole(Role.Button)).assertIsNotEnabled()
    }

    @Test fun loginForm_hasHeading() {
        composeTestRule.setContent { LoginForm(onSubmit = { _, _ -> }) }
        composeTestRule.onNode(hasText("Login") and isHeading()).assertExists()
    }

    @Test fun contactListItem_mergesDescendants() {
        composeTestRule.setContent {
            ContactListItem("John Doe", "Senior Engineer", true, onClick = {})
        }
        composeTestRule.onNodeWithContentDescription("John Doe, Senior Engineer, Online")
            .assertExists().assertHasClickAction()
    }

    @Test fun starRating_hasCorrectDescription() {
        composeTestRule.setContent { StarRating(rating = 4, maxRating = 5) }
        composeTestRule.onNodeWithContentDescription("Rating: 4 out of 5 stars").assertExists()
    }

    @Test fun emailListItem_hasCustomActions() {
        composeTestRule.setContent {
            EmailListItem("Jane", "Meeting", "Can we reschedule...", false,
                onOpen = {}, onArchive = {}, onDelete = {}, onToggleRead = {})
        }
        val node = composeTestRule.onNodeWithText("Jane").fetchSemanticsNode()
        val actions = node.config[SemanticsActions.CustomActions]
        assert(actions.size == 3)
        assert(actions.any { it.label == "Archive" })
        assert(actions.any { it.label == "Delete" })
        assert(actions.any { it.label == "Mark as read" })
    }
}
```
