# Security API Reference

> Target: Kotlin 2.3.10, Ktor 3.4.0, Android API 35, iOS 17+, Metro DI 0.10.1

---

## R8 / ProGuard Rules

### kotlinx-serialization

```proguard
# Keep @Serializable classes and their generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep companion objects with serializer() method
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Keep serializer implementations
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep generated serializer classes
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static ** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep @Serializable class members (fields must survive for reflection)
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <init>(...);
}
```

### Kotlin Coroutines

```proguard
# ServiceLoader-based discovery
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Android main dispatcher
-keepclassmembers class kotlinx.coroutines.android.AndroidExceptionPreHandler {
    <init>();
}
-keepclassmembers class kotlinx.coroutines.android.AndroidDispatcherFactory {
    <init>();
}

# Prevent R8 from removing suspend function state machines
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.flow.**
```

### Ktor Client

```proguard
# Engine service loaders
-keep class io.ktor.client.engine.** { *; }

# Content negotiation serializers
-keep class io.ktor.serialization.** { *; }

# WebSocket frame types
-keep class io.ktor.websocket.Frame$** { *; }

# Ktor internal utils
-dontwarn io.ktor.**
-keep class io.ktor.client.plugins.** { *; }
```

### Metro DI

```proguard
# Keep DependencyGraph interfaces and their factories
-keep @dev.zacsweers.metro.DependencyGraph class * { *; }
-keep class **$$MetroGraph { *; }
-keep class **$$MetroGraph$* { *; }

# Keep Factory fun interfaces
-keep class * extends dev.zacsweers.metro.DependencyGraph$Factory { *; }

# Keep @Inject constructors
-keepclasseswithmembers class * {
    @dev.zacsweers.metro.Inject <init>(...);
}

# Keep @ContributesTo modules
-keep @dev.zacsweers.metro.ContributesTo class * { *; }
-keep @dev.zacsweers.metro.ContributesBinding class * { *; }
```

### Circuit

```proguard
# Keep Presenter and Ui factories for runtime lookup
-keep class * implements com.slack.circuit.runtime.presenter.Presenter$Factory { *; }
-keep class * implements com.slack.circuit.runtime.ui.Ui$Factory { *; }

# Keep Screen marker interfaces
-keep class * implements com.slack.circuit.runtime.screen.Screen { *; }

# Keep CircuitInject-generated bindings
-keep @com.slack.circuit.codegen.annotations.CircuitInject class * { *; }
```

### Room / SQLDelight

```proguard
# Room DAOs
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Entity class * { *; }

# SQLDelight generated queries
-keep class **.sqldelight.** { *; }
-keep class * implements app.cash.sqldelight.Query$Listener { *; }
```

---

## Android network_security_config.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- res/xml/network_security_config.xml -->
<network-security-config>

    <!-- Production: pin certificates for API domain -->
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">api.example.com</domain>
        <pin-set expiration="2027-01-01">
            <!-- Primary pin (current leaf/intermediate cert SHA-256) -->
            <pin digest="SHA-256">AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=</pin>
            <!-- Backup pin (next rotation cert) -->
            <pin digest="SHA-256">BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=</pin>
        </pin-set>
    </domain-config>

    <!-- Base config: no cleartext for any domain -->
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>

    <!-- Debug overrides: trust user-installed certs (Charles, Proxyman) -->
    <debug-overrides>
        <trust-anchors>
            <certificates src="user" />
            <certificates src="system" />
        </trust-anchors>
    </debug-overrides>

</network-security-config>
```

Reference in `AndroidManifest.xml`:

```xml
<application
    android:networkSecurityConfig="@xml/network_security_config"
    android:allowBackup="false"
    android:debuggable="false"
    ... >
```

---

## iOS App Transport Security (Info.plist)

```xml
<!-- Info.plist -->
<key>NSAppTransportSecurity</key>
<dict>
    <!-- Do NOT set NSAllowsArbitraryLoads to true -->

    <!-- Domain-specific exceptions (only if third-party requires HTTP) -->
    <key>NSExceptionDomains</key>
    <dict>
        <key>legacy-partner-api.example.com</key>
        <dict>
            <key>NSExceptionAllowsInsecureHTTPLoads</key>
            <true/>
            <key>NSExceptionMinimumTLSVersion</key>
            <string>TLSv1.2</string>
            <key>NSIncludesSubdomains</key>
            <true/>
        </dict>
    </dict>
</dict>
```

---

## Certificate Pinning API

### OkHttp CertificatePinner (Android)

```kotlin
import okhttp3.CertificatePinner

val certificatePinner = CertificatePinner.Builder()
    .add(
        "api.example.com",
        "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",  // primary
        "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=",  // backup
    )
    .add(
        "cdn.example.com",
        "sha256/CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC=",
    )
    .build()

// Apply in Ktor OkHttp engine config
HttpClient(OkHttp) {
    engine {
        config {
            certificatePinner(certificatePinner)
        }
    }
}
```

### Darwin URLSessionDelegate (iOS)

```kotlin
import platform.Foundation.*
import platform.Security.*

// Manual SSL pinning via URLSessionDelegate
class PinningSessionDelegate(
    private val pinnedHashes: Map<String, Set<String>>,
) : NSObject(), NSURLSessionDelegateProtocol {

    override fun URLSession(
        session: NSURLSession,
        didReceiveChallenge: NSURLAuthenticationChallenge,
        completionHandler: (NSURLSessionAuthChallengeDisposition, NSURLCredential?) -> Unit,
    ) {
        val serverTrust = didReceiveChallenge.protectionSpace.serverTrust
        val host = didReceiveChallenge.protectionSpace.host

        if (serverTrust == null || !pinnedHashes.containsKey(host)) {
            completionHandler(NSURLSessionAuthChallengeCancelAuthenticationChallenge, null)
            return
        }

        val serverCertHash = extractCertHash(serverTrust)
        val expectedHashes = pinnedHashes[host] ?: emptySet()

        if (serverCertHash in expectedHashes) {
            val credential = NSURLCredential.credentialForTrust(serverTrust)
            completionHandler(NSURLSessionAuthChallengeUseCredential, credential)
        } else {
            completionHandler(NSURLSessionAuthChallengeCancelAuthenticationChallenge, null)
        }
    }
}
```

---

## EncryptedSharedPreferences (Android)

```kotlin
import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

fun createEncryptedPrefs(context: Context): SharedPreferences {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    return EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
}
```

Key details:
- `MasterKey` is backed by Android Keystore (hardware-backed on supported devices).
- File name (`"secure_prefs"`) is NOT encrypted -- do not use sensitive names.
- Thread-safe. Use `apply()` for async writes, `commit()` for synchronous.

---

## iOS Keychain Wrapper

```kotlin
import platform.Foundation.*
import platform.Security.*
import kotlinx.cinterop.*

fun keychainSave(key: String, value: String): Boolean {
    val data = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return false

    // Delete existing item first
    val deleteQuery = mapOf<Any?, Any?>(
        kSecClass to kSecClassGenericPassword,
        kSecAttrAccount to key,
    ).toNSDictionary()
    SecItemDelete(deleteQuery)

    val addQuery = mapOf<Any?, Any?>(
        kSecClass to kSecClassGenericPassword,
        kSecAttrAccount to key,
        kSecValueData to data,
        kSecAttrAccessible to kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
    ).toNSDictionary()

    return SecItemAdd(addQuery, null) == errSecSuccess
}

fun keychainLoad(key: String): String? {
    val query = mapOf<Any?, Any?>(
        kSecClass to kSecClassGenericPassword,
        kSecAttrAccount to key,
        kSecReturnData to kCFBooleanTrue,
        kSecMatchLimit to kSecMatchLimitOne,
    ).toNSDictionary()

    val result = memScoped {
        val ref = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, ref.ptr)
        if (status == errSecSuccess) ref.value else null
    }

    val data = result as? NSData ?: return null
    return NSString.create(data = data, encoding = NSUTF8StringEncoding) as? String
}

fun keychainDelete(key: String): Boolean {
    val query = mapOf<Any?, Any?>(
        kSecClass to kSecClassGenericPassword,
        kSecAttrAccount to key,
    ).toNSDictionary()
    return SecItemDelete(query) == errSecSuccess
}
```

Accessibility constants:
| Constant | Meaning |
|----------|---------|
| `kSecAttrAccessibleWhenUnlockedThisDeviceOnly` | Only when device unlocked, not in backups |
| `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` | After first unlock until reboot, not in backups |
| `kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly` | Requires passcode set, deleted if passcode removed |

---

## Biometric Auth API Surface

### Android -- BiometricPrompt

```kotlin
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

fun canUseBiometrics(activity: FragmentActivity): Boolean {
    val manager = BiometricManager.from(activity)
    return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
        BiometricManager.BIOMETRIC_SUCCESS
}

fun showBiometricPrompt(
    activity: FragmentActivity,
    onSuccess: () -> Unit,
    onError: (Int, String) -> Unit,
) {
    val executor = ContextCompat.getMainExecutor(activity)
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onSuccess()
        }
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            onError(errorCode, errString.toString())
        }
    }

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Authenticate")
        .setSubtitle("Verify your identity")
        .setNegativeButtonText("Cancel")
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        .build()

    BiometricPrompt(activity, executor, callback).authenticate(promptInfo)
}
```

### iOS -- LAContext

```kotlin
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics
import platform.Foundation.NSError
import kotlinx.cinterop.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

suspend fun authenticateWithBiometrics(reason: String): Result<Unit> =
    suspendCancellableCoroutine { cont ->
        val context = LAContext()
        val error = memScoped { alloc<ObjCObjectVar<NSError?>>() }

        if (!context.canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, error.ptr)) {
            cont.resume(Result.failure(Exception(error.value?.localizedDescription ?: "Biometrics unavailable")))
            return@suspendCancellableCoroutine
        }

        context.evaluatePolicy(
            LAPolicyDeviceOwnerAuthenticationWithBiometrics,
            localizedReason = reason,
        ) { success, evalError ->
            if (success) {
                cont.resume(Result.success(Unit))
            } else {
                cont.resume(Result.failure(Exception(evalError?.localizedDescription ?: "Auth failed")))
            }
        }
    }
```

---

## SecureRandom / Token Generation

```kotlin
// commonMain
expect fun secureRandomBytes(size: Int): ByteArray

// androidMain
import java.security.SecureRandom
actual fun secureRandomBytes(size: Int): ByteArray {
    val bytes = ByteArray(size)
    SecureRandom().nextBytes(bytes)
    return bytes
}

// iosMain
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault
import kotlinx.cinterop.*
actual fun secureRandomBytes(size: Int): ByteArray {
    val bytes = ByteArray(size)
    bytes.usePinned { pinned ->
        SecRandomCopyBytes(kSecRandomDefault, size.toULong(), pinned.addressOf(0))
    }
    return bytes
}
```

---

## Security Headers (Ktor)

```kotlin
// Custom Ktor plugin for security headers on every request
val SecurityHeadersPlugin = createClientPlugin("SecurityHeaders") {
    onRequest { request, _ ->
        request.headers.apply {
            append("X-Content-Type-Options", "nosniff")
            append("X-Request-Id", generateRequestId())
            append("X-Platform", platformName())   // "Android" or "iOS"
            append("X-App-Version", appVersion())
            // Do NOT send device ID or other fingerprinting data
        }
    }
}

// Install alongside other plugins
HttpClient(httpEngine()) {
    install(SecurityHeadersPlugin)
    // ... other plugins
}
```

Headers to set server-side (verify in responses):
| Header | Value | Purpose |
|--------|-------|---------|
| `Strict-Transport-Security` | `max-age=63072000; includeSubDomains` | Force HTTPS |
| `X-Content-Type-Options` | `nosniff` | Prevent MIME sniffing |
| `X-Frame-Options` | `DENY` | Prevent clickjacking |
| `Cache-Control` | `no-store` | Prevent caching sensitive responses |
| `Content-Security-Policy` | domain-specific | Restrict resource loading (WebView) |
