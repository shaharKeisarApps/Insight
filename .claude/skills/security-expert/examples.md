# Security -- Production Examples

> All examples target `commonMain` unless noted. Stack: Metro DI 0.10.1, Circuit MVI 0.32.0, Ktor 3.4.0, Kotlin 2.3.10.

---

## 1. SecureStorage Interface + Platform Implementations + Metro DI

```kotlin
// commonMain/data/security/SecureStorage.kt
interface SecureStorage {
    suspend fun save(key: String, value: String)
    suspend fun load(key: String): String?
    suspend fun delete(key: String)
    suspend fun clear()
}
```

```kotlin
// androidMain/data/security/AndroidSecureStorage.kt
import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class AndroidSecureStorage(
    private val context: Context,
) : SecureStorage {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "secure_storage",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override suspend fun save(key: String, value: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(key, value).apply()
    }

    override suspend fun load(key: String): String? = withContext(Dispatchers.IO) {
        prefs.getString(key, null)
    }

    override suspend fun delete(key: String) = withContext(Dispatchers.IO) {
        prefs.edit().remove(key).apply()
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
    }
}
```

```kotlin
// iosMain/data/security/IosSecureStorage.kt
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.cinterop.*
import platform.Foundation.*
import platform.Security.*

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class IosSecureStorage : SecureStorage {

    private val serviceName = "com.example.app.securestorage"

    override suspend fun save(key: String, value: String) {
        val data = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return

        // Remove existing entry to avoid duplicates
        delete(key)

        val query = mapOf<Any?, Any?>(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to serviceName,
            kSecAttrAccount to key,
            kSecValueData to data,
            kSecAttrAccessible to kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
        ).toNSDictionary()

        SecItemAdd(query, null)
    }

    override suspend fun load(key: String): String? {
        val query = mapOf<Any?, Any?>(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to serviceName,
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

    override suspend fun delete(key: String) {
        val query = mapOf<Any?, Any?>(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to serviceName,
            kSecAttrAccount to key,
        ).toNSDictionary()
        SecItemDelete(query)
    }

    override suspend fun clear() {
        val query = mapOf<Any?, Any?>(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to serviceName,
        ).toNSDictionary()
        SecItemDelete(query)
    }
}
```

Consumers depend only on the `SecureStorage` interface. Metro resolves the correct platform implementation at compile time via `@ContributesBinding`.

---

## 2. Ktor HttpClient with Certificate Pinning (expect/actual)

```kotlin
// commonMain/data/network/PinnedHttpClientProvider.kt
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

// Platform-specific HttpClient with cert pinning baked in
expect fun createPinnedHttpClient(json: Json, baseUrl: String): HttpClient

@ContributesTo(AppScope::class)
interface PinnedHttpClientBindings {
    companion object {
        @Provides
        @SingleIn(AppScope::class)
        fun provideHttpClient(json: Json, appConfig: AppConfig): HttpClient =
            createPinnedHttpClient(json, appConfig.baseUrl)
    }
}
```

```kotlin
// androidMain/data/network/PinnedHttpClient.android.kt
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import okhttp3.CertificatePinner

actual fun createPinnedHttpClient(json: Json, baseUrl: String): HttpClient =
    HttpClient(OkHttp) {
        expectSuccess = true

        engine {
            config {
                certificatePinner(
                    CertificatePinner.Builder()
                        .add(
                            "api.example.com",
                            "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                            "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=",
                        )
                        .build()
                )
            }
        }

        install(DefaultRequest) {
            url(baseUrl)
            contentType(ContentType.Application.Json)
        }

        install(ContentNegotiation) { json(json) }

        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 30_000
        }
    }
```

```kotlin
// iosMain/data/network/PinnedHttpClient.ios.kt
import io.ktor.client.*
import io.ktor.client.engine.darwin.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import platform.Foundation.*
import platform.Security.*

actual fun createPinnedHttpClient(json: Json, baseUrl: String): HttpClient =
    HttpClient(Darwin) {
        expectSuccess = true

        engine {
            handleChallenge { session, task, challenge, completionHandler ->
                val protectionSpace = challenge.protectionSpace
                if (protectionSpace.authenticationMethod != NSURLAuthenticationMethodServerTrust) {
                    completionHandler(
                        NSURLSessionAuthChallengePerformDefaultHandling,
                        null,
                    )
                    return@handleChallenge
                }

                val serverTrust = protectionSpace.serverTrust
                if (serverTrust == null) {
                    completionHandler(
                        NSURLSessionAuthChallengeCancelAuthenticationChallenge,
                        null,
                    )
                    return@handleChallenge
                }

                // Validate the certificate chain against pinned hashes
                val pinnedHashes = setOf(
                    "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                    "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=",
                )

                val isValid = validateServerTrust(serverTrust, pinnedHashes)
                if (isValid) {
                    val credential = NSURLCredential.credentialForTrust(serverTrust)
                    completionHandler(NSURLSessionAuthChallengeUseCredential, credential)
                } else {
                    completionHandler(
                        NSURLSessionAuthChallengeCancelAuthenticationChallenge,
                        null,
                    )
                }
            }
        }

        install(DefaultRequest) {
            url(baseUrl)
            contentType(ContentType.Application.Json)
        }

        install(ContentNegotiation) { json(json) }

        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 30_000
        }
    }

/**
 * Extracts the public key hash from the server trust and checks it against
 * the set of pinned SHA-256 base64 hashes.
 */
private fun validateServerTrust(serverTrust: SecTrustRef, pinnedHashes: Set<String>): Boolean {
    val certCount = SecTrustGetCertificateCount(serverTrust)
    if (certCount == 0L) return false

    // Check each certificate in the chain against pinned hashes
    for (i in 0 until certCount) {
        val cert = SecTrustCopyCertificateChain(serverTrust)
            ?: continue
        // Extract public key data and compute SHA-256 hash
        // Compare against pinnedHashes
        // Return true if any certificate in the chain matches
    }
    return false // Replace with real hash comparison
}
```

---

## 3. R8 / ProGuard Rules File (Complete Production Rules)

```proguard
# proguard-rules.pro -- Complete KMP production rules

# ============================================================
# kotlinx-serialization
# ============================================================
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static ** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <init>(...);
}

# ============================================================
# Kotlin Coroutines
# ============================================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.android.AndroidExceptionPreHandler { <init>(); }
-keepclassmembers class kotlinx.coroutines.android.AndroidDispatcherFactory { <init>(); }
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.flow.**

# ============================================================
# Ktor Client
# ============================================================
-keep class io.ktor.client.engine.** { *; }
-keep class io.ktor.serialization.** { *; }
-keep class io.ktor.websocket.Frame$** { *; }
-keep class io.ktor.client.plugins.** { *; }
-dontwarn io.ktor.**

# ============================================================
# Metro DI
# ============================================================
-keep @dev.zacsweers.metro.DependencyGraph class * { *; }
-keep class **$$MetroGraph { *; }
-keep class **$$MetroGraph$* { *; }
-keep class * extends dev.zacsweers.metro.DependencyGraph$Factory { *; }
-keepclasseswithmembers class * {
    @dev.zacsweers.metro.Inject <init>(...);
}
-keep @dev.zacsweers.metro.ContributesTo class * { *; }
-keep @dev.zacsweers.metro.ContributesBinding class * { *; }

# ============================================================
# Circuit
# ============================================================
-keep class * implements com.slack.circuit.runtime.presenter.Presenter$Factory { *; }
-keep class * implements com.slack.circuit.runtime.ui.Ui$Factory { *; }
-keep class * implements com.slack.circuit.runtime.screen.Screen { *; }
-keep @com.slack.circuit.codegen.annotations.CircuitInject class * { *; }

# ============================================================
# Room / SQLDelight
# ============================================================
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Entity class * { *; }
-keep class **.sqldelight.** { *; }
-keep class * implements app.cash.sqldelight.Query$Listener { *; }

# ============================================================
# General Android
# ============================================================
-keepattributes Signature
-keepattributes Exceptions
-keepattributes SourceFile,LineNumberTable  # for readable crash reports
-renamesourcefileattribute SourceFile
```

---

## 4. Biometric Authentication (expect/actual)

```kotlin
// commonMain/data/security/BiometricAuth.kt

sealed interface BiometricResult {
    data object Success : BiometricResult
    data class Error(val code: Int, val message: String) : BiometricResult
    data object NotAvailable : BiometricResult
}

interface BiometricAuthenticator {
    fun isAvailable(): Boolean
    suspend fun authenticate(title: String, subtitle: String): BiometricResult
}
```

```kotlin
// androidMain/data/security/AndroidBiometricAuthenticator.kt
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class AndroidBiometricAuthenticator(
    private val activityProvider: ActivityProvider,
) : BiometricAuthenticator {

    override fun isAvailable(): Boolean {
        val activity = activityProvider.currentActivity ?: return false
        val manager = BiometricManager.from(activity)
        return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    override suspend fun authenticate(
        title: String,
        subtitle: String,
    ): BiometricResult = suspendCancellableCoroutine { cont ->
        val activity = activityProvider.currentActivity as? FragmentActivity
        if (activity == null) {
            cont.resume(BiometricResult.NotAvailable)
            return@suspendCancellableCoroutine
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                cont.resume(BiometricResult.Success)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                cont.resume(BiometricResult.Error(errorCode, errString.toString()))
            }

            override fun onAuthenticationFailed() {
                // Called on each failed attempt; prompt stays open.
                // Do NOT resume here -- wait for onAuthenticationError or onAuthenticationSucceeded.
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        BiometricPrompt(activity, executor, callback).authenticate(promptInfo)
    }
}
```

```kotlin
// iosMain/data/security/IosBiometricAuthenticator.kt
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.cinterop.*
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSError
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics
import kotlin.coroutines.resume

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class IosBiometricAuthenticator : BiometricAuthenticator {

    override fun isAvailable(): Boolean {
        val context = LAContext()
        return memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            context.canEvaluatePolicy(
                LAPolicyDeviceOwnerAuthenticationWithBiometrics,
                error.ptr,
            )
        }
    }

    override suspend fun authenticate(
        title: String,
        subtitle: String,
    ): BiometricResult = suspendCancellableCoroutine { cont ->
        val context = LAContext()
        context.localizedFallbackTitle = "" // hide "Enter Password" fallback

        context.evaluatePolicy(
            LAPolicyDeviceOwnerAuthenticationWithBiometrics,
            localizedReason = subtitle,
        ) { success, error ->
            if (success) {
                cont.resume(BiometricResult.Success)
            } else {
                val code = error?.code?.toInt() ?: -1
                val message = error?.localizedDescription ?: "Authentication failed"
                cont.resume(BiometricResult.Error(code, message))
            }
        }
    }
}
```

---

## 5. Root / Jailbreak Detection (expect/actual)

```kotlin
// commonMain/data/security/DeviceIntegrity.kt
interface DeviceIntegrityChecker {
    fun isDeviceCompromised(): Boolean
}
```

```kotlin
// androidMain/data/security/AndroidDeviceIntegrityChecker.kt
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.io.File

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class AndroidDeviceIntegrityChecker : DeviceIntegrityChecker {

    override fun isDeviceCompromised(): Boolean =
        hasSuBinary() || hasMagisk() || hasTestKeys() || hasRootManagementApps()

    private fun hasSuBinary(): Boolean {
        val paths = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/data/local/xbin/su", "/data/local/bin/su", "/data/local/su",
            "/system/sd/xbin/su", "/system/app/Superuser.apk",
        )
        return paths.any { File(it).exists() }
    }

    private fun hasMagisk(): Boolean {
        val markers = listOf(
            "/sbin/.magisk", "/data/adb/magisk",
            "/cache/.disable_magisk", "/dev/.magisk.unblock",
        )
        return markers.any { File(it).exists() }
    }

    private fun hasTestKeys(): Boolean {
        val buildTags = android.os.Build.TAGS ?: return false
        return buildTags.contains("test-keys")
    }

    private fun hasRootManagementApps(): Boolean {
        val packages = listOf(
            "com.topjohnwu.magisk", "eu.chainfire.supersu",
            "com.koushikdutta.superuser", "com.noshufou.android.su",
        )
        return try {
            val pm = android.app.ActivityThread.currentApplication()?.packageManager
            packages.any { pkg ->
                try {
                    pm?.getPackageInfo(pkg, 0)
                    true
                } catch (_: Exception) {
                    false
                }
            }
        } catch (_: Exception) {
            false
        }
    }
}
```

```kotlin
// iosMain/data/security/IosDeviceIntegrityChecker.kt
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.stringByAppendingPathComponent
import platform.UIKit.UIApplication

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class IosDeviceIntegrityChecker : DeviceIntegrityChecker {

    override fun isDeviceCompromised(): Boolean =
        hasJailbreakFiles() || canOpenCydia() || hasWritableSystemPaths() || hasDylibs()

    private fun hasJailbreakFiles(): Boolean {
        val paths = listOf(
            "/Applications/Cydia.app",
            "/Applications/Sileo.app",
            "/Library/MobileSubstrate/MobileSubstrate.dylib",
            "/usr/sbin/sshd",
            "/etc/apt",
            "/private/var/lib/apt/",
            "/bin/bash",
            "/usr/bin/ssh",
        )
        val fileManager = NSFileManager.defaultManager
        return paths.any { fileManager.fileExistsAtPath(it) }
    }

    private fun canOpenCydia(): Boolean =
        UIApplication.sharedApplication.canOpenURL(
            platform.Foundation.NSURL(string = "cydia://package/com.example.package")!!
        )

    private fun hasWritableSystemPaths(): Boolean =
        try {
            val testPath = "/private/jailbreak_test_\${kotlin.random.Random.nextInt()}"
            val data = ("test" as NSString).dataUsingEncoding(4u) // NSUTF8StringEncoding
            val written = data?.writeToFile(testPath, atomically = true) ?: false
            if (written) {
                NSFileManager.defaultManager.removeItemAtPath(testPath, null)
            }
            written
        } catch (_: Exception) {
            false
        }

    private fun hasDylibs(): Boolean =
        try {
            val fileManager = NSFileManager.defaultManager
            fileManager.fileExistsAtPath("/Library/MobileSubstrate/MobileSubstrate.dylib")
        } catch (_: Exception) {
            false
        }
}
```

---

## 6. Token Refresh with Secure Storage (Auth Interceptor Pattern)

```kotlin
// commonMain/data/auth/SecureTokenStorage.kt
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long,
)

interface TokenStorage {
    suspend fun load(): AuthTokens?
    suspend fun save(tokens: AuthTokens)
    suspend fun clear()
}

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class SecureTokenStorage(
    private val secureStorage: SecureStorage,
    private val json: Json,
) : TokenStorage {

    private companion object {
        const val KEY_AUTH_TOKENS = "auth_tokens"
    }

    override suspend fun load(): AuthTokens? {
        val raw = secureStorage.load(KEY_AUTH_TOKENS) ?: return null
        return try {
            json.decodeFromString<AuthTokens>(raw)
        } catch (_: Exception) {
            // Corrupted data -- clear it
            secureStorage.delete(KEY_AUTH_TOKENS)
            null
        }
    }

    override suspend fun save(tokens: AuthTokens) {
        secureStorage.save(KEY_AUTH_TOKENS, json.encodeToString(tokens))
    }

    override suspend fun clear() {
        secureStorage.delete(KEY_AUTH_TOKENS)
    }
}
```

```kotlin
// commonMain/di/AuthenticatedHttpClientBindings.kt
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

@ContributesTo(AppScope::class)
interface AuthenticatedHttpClientBindings {

    companion object {

        @Provides
        @SingleIn(AppScope::class)
        fun provideAuthenticatedHttpClient(
            json: Json,
            tokenStorage: TokenStorage,
            appConfig: AppConfig,
        ): HttpClient = createPinnedHttpClient(json, appConfig.baseUrl).config {

            install(Auth) {
                bearer {
                    loadTokens {
                        tokenStorage.load()?.let {
                            BearerTokens(it.accessToken, it.refreshToken)
                        }
                    }
                    refreshTokens {
                        val refreshToken = oldTokens?.refreshToken
                        if (refreshToken.isNullOrBlank()) {
                            // No refresh token -- force re-login
                            tokenStorage.clear()
                            return@refreshTokens null
                        }

                        try {
                            val response: TokenRefreshResponse = client.post("${appConfig.authUrl}/token") {
                                markAsRefreshTokenRequest()
                                contentType(ContentType.Application.Json)
                                setBody(TokenRefreshRequest(refreshToken))
                            }.body()

                            val newTokens = AuthTokens(
                                accessToken = response.accessToken,
                                refreshToken = response.refreshToken,
                                expiresAtMillis = response.expiresAtMillis,
                            )
                            tokenStorage.save(newTokens)
                            BearerTokens(newTokens.accessToken, newTokens.refreshToken)
                        } catch (e: Exception) {
                            // Refresh failed -- clear tokens and force re-login
                            tokenStorage.clear()
                            null
                        }
                    }
                    sendWithoutRequest { request ->
                        request.url.host == appConfig.apiHost
                    }
                }
            }

            install(Logging) {
                logger = object : io.ktor.client.plugins.logging.Logger {
                    override fun log(message: String) {
                        co.touchlab.kermit.Logger.d("HttpClient") { message }
                    }
                }
                level = if (appConfig.isDebug) LogLevel.HEADERS else LogLevel.NONE
                sanitizeHeader { it == HttpHeaders.Authorization }
            }
        }
    }
}

@kotlinx.serialization.Serializable
data class TokenRefreshRequest(val refreshToken: String)

@kotlinx.serialization.Serializable
data class TokenRefreshResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long,
)
```

---

## 7. Android network_security_config.xml (Debug + Release)

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- app/src/main/res/xml/network_security_config.xml -->
<network-security-config>

    <!-- Base: No cleartext anywhere -->
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>

    <!-- API domain: certificate pinning -->
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">api.example.com</domain>
        <pin-set expiration="2027-06-01">
            <!-- Current leaf cert hash -->
            <pin digest="SHA-256">AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=</pin>
            <!-- Backup cert hash (next rotation) -->
            <pin digest="SHA-256">BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=</pin>
        </pin-set>
    </domain-config>

    <!-- CDN domain: separate pin set -->
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">cdn.example.com</domain>
        <pin-set expiration="2027-06-01">
            <pin digest="SHA-256">CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC=</pin>
            <pin digest="SHA-256">DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD=</pin>
        </pin-set>
    </domain-config>

    <!-- Debug overrides: allow proxy tools (Charles, Proxyman, mitmproxy) -->
    <debug-overrides>
        <trust-anchors>
            <certificates src="user" />
            <certificates src="system" />
        </trust-anchors>
    </debug-overrides>

</network-security-config>
```

```xml
<!-- AndroidManifest.xml -->
<application
    android:networkSecurityConfig="@xml/network_security_config"
    android:allowBackup="false"
    android:fullBackupContent="false"
    android:dataExtractionRules="@xml/data_extraction_rules"
    tools:targetApi="35">
    ...
</application>
```

```xml
<!-- app/src/main/res/xml/data_extraction_rules.xml (Android 12+) -->
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="sharedpref" path="secure_storage.xml" />
        <exclude domain="database" path="." />
    </cloud-backup>
    <device-transfer>
        <exclude domain="sharedpref" path="secure_storage.xml" />
    </device-transfer>
</data-extraction-rules>
```

---

## Gradle Build Types Configuration

```kotlin
// app/build.gradle.kts
android {
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Do NOT set debuggable = true in release
        }
        debug {
            isMinifyEnabled = false
            // network_security_config debug-overrides active only when debuggable = true
        }
    }

    buildFeatures {
        buildConfig = true // required for Secrets Gradle Plugin fields
    }
}
```
