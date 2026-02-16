# Compose Multiplatform Resources Examples

## 1. Basic String Resources

Define strings in XML and display them in a Composable.

**File: `src/commonMain/composeResources/values/strings.xml`**

```xml
<resources>
    <string name="app_name">My KMP App</string>
    <string name="welcome_message">Welcome to our application</string>
    <string name="login_button">Sign In</string>
    <string name="logout_button">Sign Out</string>
    <string name="settings_title">Settings</string>
</resources>
```

**File: `src/commonMain/kotlin/com/myapp/ui/HomeScreen.kt`**

```kotlin
package com.myapp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.myapp.resources.Res
import org.jetbrains.compose.resources.stringResource

@Composable
fun HomeScreen(
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(Res.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(Res.string.welcome_message),
            style = MaterialTheme.typography.bodyLarge,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = onLoginClick) {
            Text(text = stringResource(Res.string.login_button))
        }
    }
}
```

---

## 2. Localized Strings

Provide translations in qualifier directories. The system automatically selects the correct language based on the device locale.

**File: `src/commonMain/composeResources/values/strings.xml`** (default -- English)

```xml
<resources>
    <string name="greeting">Hello!</string>
    <string name="item_added">Item added to cart</string>
    <string name="checkout">Checkout</string>
    <string name="total_label">Total</string>
    <string name="empty_cart">Your cart is empty</string>
</resources>
```

**File: `src/commonMain/composeResources/values-fr/strings.xml`** (French)

```xml
<resources>
    <string name="greeting">Bonjour !</string>
    <string name="item_added">Article ajout au panier</string>
    <string name="checkout">Passer la commande</string>
    <string name="total_label">Total</string>
    <string name="empty_cart">Votre panier est vide</string>
</resources>
```

**File: `src/commonMain/composeResources/values-es/strings.xml`** (Spanish)

```xml
<resources>
    <string name="greeting">Hola!</string>
    <string name="item_added">Articulo agregado al carrito</string>
    <string name="checkout">Finalizar compra</string>
    <string name="total_label">Total</string>
    <string name="empty_cart">Tu carrito esta vacio</string>
</resources>
```

**File: `src/commonMain/composeResources/values-pt-rBR/strings.xml`** (Portuguese -- Brazil)

```xml
<resources>
    <string name="greeting">Ola!</string>
    <string name="item_added">Item adicionado ao carrinho</string>
    <string name="checkout">Finalizar compra</string>
    <string name="total_label">Total</string>
    <string name="empty_cart">Seu carrinho esta vazio</string>
</resources>
```

**File: `src/commonMain/kotlin/com/myapp/ui/CartScreen.kt`**

```kotlin
package com.myapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.myapp.resources.Res
import org.jetbrains.compose.resources.stringResource

@Composable
fun CartScreen(
    itemCount: Int,
    total: String,
    onCheckout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        // Automatically displays in the user's locale:
        // English: "Hello!", French: "Bonjour !", Spanish: "Hola!"
        Text(
            text = stringResource(Res.string.greeting),
            style = MaterialTheme.typography.headlineMedium,
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (itemCount == 0) {
            Text(text = stringResource(Res.string.empty_cart))
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = stringResource(Res.string.total_label))
                Text(text = total)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onCheckout,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(Res.string.checkout))
            }
        }
    }
}
```

---

## 3. Image Resources

Load PNG and SVG images using `painterResource`. The function auto-detects the format.

**Directory structure:**

```
src/commonMain/composeResources/
    drawable/
        app_logo.svg          # Vector logo (SVG)
        hero_banner.png       # Raster hero image
        placeholder.webp      # WebP placeholder
    drawable-xxhdpi/
        hero_banner.png       # High-density variant
```

**File: `src/commonMain/kotlin/com/myapp/ui/BrandingHeader.kt`**

```kotlin
package com.myapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.myapp.resources.Res
import org.jetbrains.compose.resources.painterResource

@Composable
fun BrandingHeader(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // SVG logo -- painterResource handles SVG automatically
        Image(
            painter = painterResource(Res.drawable.app_logo),
            contentDescription = "App logo",
            modifier = Modifier.size(120.dp),
        )

        Spacer(modifier = Modifier.height(16.dp))

        // PNG banner -- density-qualified variant is selected automatically
        // On xxhdpi devices, drawable-xxhdpi/hero_banner.png is used
        Image(
            painter = painterResource(Res.drawable.hero_banner),
            contentDescription = "Hero banner",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
        )
    }
}
```

**Using XML vector drawables as icons:**

```kotlin
package com.myapp.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.myapp.resources.Res
import org.jetbrains.compose.resources.vectorResource

@Composable
fun BackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        // vectorResource specifically loads XML vector drawables as ImageVector
        val arrowBack: ImageVector = vectorResource(Res.drawable.ic_arrow_back)
        Icon(
            imageVector = arrowBack,
            contentDescription = "Navigate back",
        )
    }
}
```

---

## 4. Custom Font

Load TTF or OTF fonts and use them as a `FontFamily`.

**Directory structure:**

```
src/commonMain/composeResources/
    font/
        inter_regular.ttf
        inter_medium.ttf
        inter_semibold.ttf
        inter_bold.ttf
        inter_italic.ttf
```

**File: `src/commonMain/kotlin/com/myapp/ui/theme/AppTypography.kt`**

```kotlin
package com.myapp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.myapp.resources.Res
import org.jetbrains.compose.resources.Font

@Composable
fun appFontFamily(): FontFamily = FontFamily(
    Font(Res.font.inter_regular, FontWeight.Normal),
    Font(Res.font.inter_medium, FontWeight.Medium),
    Font(Res.font.inter_semibold, FontWeight.SemiBold),
    Font(Res.font.inter_bold, FontWeight.Bold),
    Font(Res.font.inter_italic, FontWeight.Normal, FontStyle.Italic),
)

@Composable
fun appTypography(): Typography {
    val fontFamily = appFontFamily()

    return Typography(
        displayLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 57.sp,
            lineHeight = 64.sp,
        ),
        headlineLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 32.sp,
            lineHeight = 40.sp,
        ),
        titleLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 22.sp,
            lineHeight = 28.sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        ),
        labelLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        ),
    )
}
```

**Applying in theme:**

```kotlin
package com.myapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        typography = appTypography(),
        content = content,
    )
}
```

---

## 5. String Formatting and Plurals

Use format arguments and plural-aware strings for dynamic content.

**File: `src/commonMain/composeResources/values/strings.xml`**

```xml
<resources>
    <string name="welcome_user">Welcome, %1$s!</string>
    <string name="last_login">Last login: %1$s at %2$s</string>
    <string name="search_results">Found %1$d results for \"%2$s\"</string>
    <string name="temperature">Current temperature: %1$.1f degrees</string>
</resources>
```

**File: `src/commonMain/composeResources/values/plurals.xml`**

```xml
<resources>
    <plurals name="items_in_cart">
        <item quantity="zero">No items in your cart</item>
        <item quantity="one">%d item in your cart</item>
        <item quantity="other">%d items in your cart</item>
    </plurals>

    <plurals name="unread_messages">
        <item quantity="one">%d unread message</item>
        <item quantity="other">%d unread messages</item>
    </plurals>

    <plurals name="days_remaining">
        <item quantity="one">%d day remaining</item>
        <item quantity="other">%d days remaining</item>
    </plurals>
</resources>
```

**File: `src/commonMain/composeResources/values-fr/plurals.xml`**

```xml
<resources>
    <plurals name="items_in_cart">
        <item quantity="zero">Aucun article dans votre panier</item>
        <item quantity="one">%d article dans votre panier</item>
        <item quantity="other">%d articles dans votre panier</item>
    </plurals>

    <plurals name="unread_messages">
        <item quantity="one">%d message non lu</item>
        <item quantity="other">%d messages non lus</item>
    </plurals>

    <plurals name="days_remaining">
        <item quantity="one">%d jour restant</item>
        <item quantity="other">%d jours restants</item>
    </plurals>
</resources>
```

**File: `src/commonMain/kotlin/com/myapp/ui/DashboardScreen.kt`**

```kotlin
package com.myapp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.myapp.resources.Res
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun DashboardScreen(
    userName: String,
    lastLoginDate: String,
    lastLoginTime: String,
    cartItemCount: Int,
    unreadMessageCount: Int,
    daysRemaining: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(16.dp)) {
        // String formatting with arguments
        // Output: "Welcome, Alice!"
        Text(
            text = stringResource(Res.string.welcome_user, userName),
            style = MaterialTheme.typography.headlineMedium,
        )

        // Multiple format arguments
        // Output: "Last login: Jan 15 at 3:42 PM"
        Text(
            text = stringResource(Res.string.last_login, lastLoginDate, lastLoginTime),
            style = MaterialTheme.typography.bodySmall,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Plural strings -- automatically handles singular/plural forms
        // cartItemCount=0 -> "No items in your cart"
        // cartItemCount=1 -> "1 item in your cart"
        // cartItemCount=5 -> "5 items in your cart"
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = pluralStringResource(
                    Res.plurals.items_in_cart,
                    cartItemCount,
                    cartItemCount,
                ),
                modifier = Modifier.padding(16.dp),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Another plural example
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = pluralStringResource(
                    Res.plurals.unread_messages,
                    unreadMessageCount,
                    unreadMessageCount,
                ),
                modifier = Modifier.padding(16.dp),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = pluralStringResource(
                    Res.plurals.days_remaining,
                    daysRemaining,
                    daysRemaining,
                ),
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
```

---

## 6. Raw File Reading

Load arbitrary files (JSON, text, binary) from the `files/` directory.

**File: `src/commonMain/composeResources/files/app_config.json`**

```json
{
  "apiBaseUrl": "https://api.myapp.com/v2",
  "maxRetries": 3,
  "timeoutSeconds": 30,
  "featureFlags": {
    "darkMode": true,
    "experimentalSearch": false,
    "offlineMode": true
  }
}
```

**File: `src/commonMain/composeResources/files/onboarding_steps.json`**

```json
[
  {
    "title": "Welcome",
    "description": "Get started with our app",
    "imageKey": "onboarding_welcome"
  },
  {
    "title": "Explore",
    "description": "Discover features and content",
    "imageKey": "onboarding_explore"
  },
  {
    "title": "Connect",
    "description": "Join our community",
    "imageKey": "onboarding_connect"
  }
]
```

**File: `src/commonMain/kotlin/com/myapp/data/ConfigLoader.kt`**

```kotlin
package com.myapp.data

import com.myapp.resources.Res
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class FeatureFlags(
    val darkMode: Boolean = false,
    val experimentalSearch: Boolean = false,
    val offlineMode: Boolean = false,
)

@Serializable
data class AppConfig(
    val apiBaseUrl: String,
    val maxRetries: Int,
    val timeoutSeconds: Int,
    val featureFlags: FeatureFlags,
)

@Serializable
data class OnboardingStep(
    val title: String,
    val description: String,
    val imageKey: String,
)

private val json = Json { ignoreUnknownKeys = true }

suspend fun loadAppConfig(): AppConfig {
    val bytes = Res.readBytes("files/app_config.json")
    val jsonString = bytes.decodeToString()
    return json.decodeFromString<AppConfig>(jsonString)
}

suspend fun loadOnboardingSteps(): List<OnboardingStep> {
    val bytes = Res.readBytes("files/onboarding_steps.json")
    val jsonString = bytes.decodeToString()
    return json.decodeFromString<List<OnboardingStep>>(jsonString)
}
```

**Using in a ViewModel:**

```kotlin
package com.myapp.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.data.OnboardingStep
import com.myapp.data.loadOnboardingSteps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class OnboardingViewModel : ViewModel() {
    private val _steps = MutableStateFlow<List<OnboardingStep>>(emptyList())
    val steps: StateFlow<List<OnboardingStep>> = _steps

    init {
        viewModelScope.launch {
            _steps.value = loadOnboardingSteps()
        }
    }
}
```

---

## 7. Themed Resources

Provide dark-mode-specific drawables and colors that switch automatically.

**Directory structure:**

```
src/commonMain/composeResources/
    drawable/
        app_logo.svg              # Light mode logo (dark ink on transparent)
        card_background.png       # Light card background
    drawable-dark/
        app_logo.svg              # Dark mode logo (light ink on transparent)
        card_background.png       # Dark card background
    values/
        strings.xml
    values-dark/
        strings.xml               # Optional: different strings for dark mode (rare)
```

**File: `src/commonMain/composeResources/values/strings.xml`**

```xml
<resources>
    <string name="theme_label">Light Mode</string>
    <string name="app_name">My App</string>
</resources>
```

**File: `src/commonMain/composeResources/values-dark/strings.xml`**

```xml
<resources>
    <string name="theme_label">Dark Mode</string>
</resources>
```

**File: `src/commonMain/kotlin/com/myapp/ui/ThemedContent.kt`**

```kotlin
package com.myapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.myapp.resources.Res
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun ThemedContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // In light mode: loads drawable/app_logo.svg
        // In dark mode:  loads drawable-dark/app_logo.svg
        // The switch is automatic based on system theme
        Image(
            painter = painterResource(Res.drawable.app_logo),
            contentDescription = "App logo",
            modifier = Modifier.size(100.dp),
        )

        Spacer(modifier = Modifier.height(16.dp))

        // In light mode: "Light Mode"
        // In dark mode:  "Dark Mode"
        Text(
            text = stringResource(Res.string.theme_label),
            style = MaterialTheme.typography.titleMedium,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Card with themed background image
        Card(modifier = Modifier.fillMaxWidth()) {
            Box {
                // Automatically picks light or dark variant
                Image(
                    painter = painterResource(Res.drawable.card_background),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                )

                Text(
                    text = stringResource(Res.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                )
            }
        }
    }
}
```

**Combining theme with language qualifiers:**

```
src/commonMain/composeResources/
    values/strings.xml                  # English, light mode (default)
    values-dark/strings.xml             # English, dark mode
    values-fr/strings.xml               # French, light mode
    values-fr-dark/strings.xml          # French, dark mode (if needed)
    drawable/background.png             # Light mode default
    drawable-dark/background.png        # Dark mode default
    drawable-dark-xxhdpi/background.png # Dark mode, high density
```

The resource system resolves the most specific match automatically. A French user in dark mode on an xxhdpi device would resolve `drawable-dark-xxhdpi/background.png` for that drawable resource.
