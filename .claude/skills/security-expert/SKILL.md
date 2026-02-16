---
name: security-expert
description: Expert guidance on KMP Security. Use for certificate pinning, R8/ProGuard rules, secure storage, biometric auth, API key protection, root/jailbreak detection, and OWASP Mobile Top 10 compliance.
---

# Security Expert Skill (Kotlin 2.3.10 / Ktor 3.4.0)

## Overview

Mobile apps operate in hostile environments where the binary is fully accessible to attackers. KMP adds a layer of complexity: shared Kotlin code compiles to JVM bytecode on Android and native binary on iOS, each with distinct attack surfaces. This skill provides defense-in-depth guidance covering network security, data protection, code hardening, authentication, and runtime integrity across both platforms.

## When to Use

- **Network**: Certificate pinning with Ktor OkHttp (Android) and Darwin (iOS) engines.
- **Storage**: Encrypting tokens, credentials, and PII at rest.
- **Build**: Configuring R8/ProGuard rules for kotlinx-serialization, coroutines, Metro, Circuit.
- **Auth**: Biometric authentication (BiometricPrompt / LAContext).
- **Keys**: Protecting API keys from decompilation and static analysis.
- **Runtime**: Detecting rooted/jailbroken devices.
- **WebView**: Hardening WebView configurations.
- **Compliance**: Mapping OWASP Mobile Top 10 to KMP-specific controls.

## Quick Reference

See [reference.md](reference.md) for ProGuard rules, cert pinning APIs, Keychain/EncryptedSharedPreferences setup, and security headers.
See [examples.md](examples.md) for complete, production-ready implementations with Metro DI wiring.

---

## OWASP Mobile Top 10 -- KMP Mitigations

| # | OWASP Category | KMP Mitigation |
|---|----------------|----------------|
| M1 | Improper Credential Usage | SecureStorage `expect`/`actual` (EncryptedSharedPreferences / Keychain); never hardcode credentials |
| M2 | Inadequate Supply Chain Security | Dependency verification via Gradle `verification-metadata.xml`; lock files; SBOM generation |
| M3 | Insecure Authentication/Authorization | Biometric gating with `@Assisted` Metro injection; short-lived JWTs with refresh rotation |
| M4 | Insufficient Input/Output Validation | kotlinx.serialization strict mode (`isLenient = false`); Ktor `HttpResponseValidator`; parameterized SQL in SQLDelight |
| M5 | Insecure Communication | Certificate pinning (OkHttp `CertificatePinner` / Darwin `URLSessionDelegate`); network security config; ATS |
| M6 | Inadequate Privacy Controls | Minimize PII in logs (`sanitizeHeader`); encrypt local DB columns; data retention policies |
| M7 | Insufficient Binary Protections | R8 full mode; obfuscation; root/jailbreak detection; debug detection |
| M8 | Security Misconfiguration | `android:debuggable=false`; `android:allowBackup=false`; ATS with no exceptions in release |
| M9 | Insecure Data Storage | EncryptedSharedPreferences (Android); Keychain with `kSecAttrAccessibleWhenUnlockedThisDeviceOnly` (iOS) |
| M10 | Insufficient Cryptography | Use platform KMS (Android Keystore / iOS Secure Enclave); no custom crypto; AES-256-GCM minimum |

---

## Network Security

### Certificate Pinning

Pin the leaf or intermediate certificate SHA-256 hash for your API domain. Always pin at least two hashes (primary + backup) to allow key rotation without bricking clients.

- **Android (OkHttp engine)**: Configure `CertificatePinner` via `engine { config { ... } }`.
- **iOS (Darwin engine)**: Implement `URLSessionDelegate` with `SecTrustEvaluateWithError` for manual pinning, or integrate TrustKit for declarative config.

### Network Security Config (Android)

Place `res/xml/network_security_config.xml` and reference it in `AndroidManifest.xml`. Use `<debug-overrides>` to allow Charles/Proxyman certificates in debug builds only.

### App Transport Security (iOS)

ATS enforces TLS 1.2+ by default on iOS. Do NOT add blanket `NSAllowsArbitraryLoads` exceptions. If a third-party domain requires HTTP, add a domain-specific exception with justification.

---

## Data Protection

### Secure Storage Pattern

Define an `expect`/`actual` `SecureStorage` interface in `commonMain`. Platform implementations:

- **Android**: `EncryptedSharedPreferences` backed by Android Keystore `AES256-GCM` master key.
- **iOS**: Keychain Services with `kSecAttrAccessibleWhenUnlockedThisDeviceOnly` to prevent access from backups on other devices.

Wire via Metro DI using `@ContributesBinding(AppScope::class)` so all consumers depend on the `SecureStorage` interface.

### Database Encryption

For SQLDelight databases containing sensitive data, use SQLCipher (Android) or encrypted SQLite (iOS) as the driver. Inject the passphrase from SecureStorage at open time.

---

## Code Protection

### R8 / ProGuard

Enable R8 full mode in release builds. Critical keep rules for KMP projects:

1. **kotlinx-serialization**: Keep `@Serializable` classes, companion objects, and serializer factories.
2. **Coroutines**: Keep `MainDispatcherFactory`, exception handler service loaders.
3. **Ktor**: Keep engine service loaders and WebSocket frame classes.
4. **Metro**: Keep `@DependencyGraph` interfaces and `@DependencyGraph.Factory` types.
5. **Circuit**: Keep `Presenter.Factory` and `Ui.Factory` implementations.
6. **Room/SQLDelight**: Keep DAO and query interfaces.

See [reference.md](reference.md) for complete rules.

### Obfuscation Strategy

- Enable `minifyEnabled = true` and `shrinkResources = true` in release `buildTypes`.
- Use `proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")`.
- Map obfuscated stack traces back using the `mapping.txt` file from `build/outputs/mapping/release/`.

---

## Authentication

### Biometric Auth

Use `expect`/`actual` to provide a unified biometric API:

- **Android**: `BiometricPrompt` with `BiometricPrompt.PromptInfo.Builder()`. Check `BiometricManager.canAuthenticate(BIOMETRIC_STRONG)` before prompting.
- **iOS**: `LAContext().evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics)`. Check `canEvaluatePolicy` first.

Gate sensitive operations (token decryption, payment confirmation) behind biometric success.

### Token Storage

- Store access and refresh tokens in `SecureStorage` (not plain SharedPreferences or UserDefaults).
- Clear tokens on logout AND on authentication failure (401 after refresh attempt).
- Use Ktor `Auth` plugin with `bearer { refreshTokens { ... } }` for automatic rotation.

---

## API Key Protection

1. **Never commit API keys to source control**. Add patterns to `.gitignore`.
2. **Android**: Use the Secrets Gradle Plugin (`com.google.android.libraries.mapsplatform.secrets-gradle-plugin`) to inject keys from `local.properties` into `BuildConfig` fields.
3. **iOS**: Store keys in `.xcconfig` files excluded from git; reference via `Info.plist` build variables.
4. **Runtime**: Inject keys via `expect`/`actual` platform functions or Metro `@Provides` bindings sourced from build config.
5. **Obfuscation**: Even BuildConfig fields are visible in decompiled APKs. For high-value keys, use a backend proxy or server-signed requests.

---

## Root / Jailbreak Detection

Detect compromised devices at app startup and degrade gracefully (warn users, disable sensitive features, or block launch entirely depending on risk profile).

- **Android**: Check for `su` binary, Magisk markers, test-keys, system properties, and running in emulator.
- **iOS**: Check for Cydia/Sileo, writable system paths (`/private/var/lib/apt`), sandbox integrity, and dynamic library injection.

Use `expect fun isDeviceCompromised(): Boolean` with platform `actual` implementations.

---

## WebView Security

When loading web content in `WebView` (Android) or `WKWebView` (iOS):

1. Disable JavaScript unless strictly required.
2. Disable file access (`setAllowFileAccess(false)`).
3. Set `setDomStorageEnabled(false)` unless needed.
4. Validate all URLs against an allowlist before loading.
5. On Android, set `WebSettings.setMixedContentMode(MIXED_CONTENT_NEVER_ALLOW)`.
6. On iOS, set `WKWebViewConfiguration` with `websiteDataStore = .nonPersistent()` for ephemeral sessions.

---

## Security Testing

### Static Analysis

- Run `detekt` with the security rule set enabled (`potential-bugs`, `complexity`).
- Use `dependency-analysis-gradle-plugin` to detect unused or vulnerable dependencies.
- Integrate OWASP Dependency Check or Snyk in CI to catch known CVEs.

### Runtime Verification

- Verify certificate pinning with `mitmproxy` or Charles Proxy in debug builds (pins should reject the proxy cert in release).
- Test root/jailbreak detection on real rooted devices or emulators with Magisk.
- Validate that `EncryptedSharedPreferences` files are unreadable without the app's Keystore key.

### Penetration Testing Checklist

| Area | Test | Tool |
|------|------|------|
| Network | MITM with proxy | mitmproxy, Charles |
| Storage | Extract data from device backup | adb backup, idevicebackup2 |
| Binary | Decompile and search for secrets | jadx (Android), Hopper (iOS) |
| Runtime | Hook function calls | Frida |
| Auth | Replay expired tokens | Burp Suite |

---

## Cross-Skill References

- **Ktor Client**: See [ktor-client-expert](../ktor-client-expert/SKILL.md) for full HttpClient setup with Auth plugin.
- **Metro DI**: See [metro-expert](../metro-expert/SKILL.md) for `@ContributesBinding`, `@SingleIn`, and graph wiring.
- **Error Handling**: See [error-handling-expert](../error-handling-expert/SKILL.md) for mapping Ktor exceptions to domain errors.
- **Testing**: See [testing-expert](../testing-expert/SKILL.md) for mocking `SecureStorage` in unit tests.
- **iOS Interop**: See [ios-interop-expert](../ios-interop-expert/SKILL.md) for Kotlin/Native interop with Security.framework.

---

## Core Rules

1. **Never store secrets in plain text**. Use EncryptedSharedPreferences (Android) and Keychain (iOS) via a shared `SecureStorage` interface.
2. **Always pin certificates** for production API endpoints. Pin at least two hashes for rotation safety.
3. **Enable R8 full mode** with keep rules for all reflection-dependent libraries (serialization, Metro, Circuit).
4. **Validate all server responses** with Ktor `HttpResponseValidator`. Never trust unvalidated data.
5. **Use biometric authentication** to gate access to sensitive data and operations.
6. **Never ship API keys in source**. Use Secrets Gradle Plugin or backend proxies.
7. **Detect rooted/jailbroken devices** and enforce security policy accordingly.
8. **Scope security implementations to Metro DI** so they are testable and swappable.
9. **Log securely**: use `sanitizeHeader` in Ktor Logging; never log tokens, passwords, or PII.
10. **Treat both platforms equally**: every Android security measure must have an iOS counterpart and vice versa.

---

## Common Pitfalls

| Pitfall | Consequence | Fix |
|---------|-------------|-----|
| Storing tokens in plain `SharedPreferences` / `UserDefaults` | Tokens readable by any rooted device or backup extraction | Use `EncryptedSharedPreferences` / Keychain |
| Hardcoding API keys in Kotlin source | Keys visible in decompiled APK/binary | Secrets Gradle Plugin + `.xcconfig`; backend proxy for high-value keys |
| Missing R8 keep rules for `@Serializable` | `SerializationException` crashes in release builds | Add `-keepclasseswithmembers` rules for serialization |
| Blanket `NSAllowsArbitraryLoads = true` | Disables ATS, allows plaintext HTTP | Add domain-specific exceptions only; remove blanket override |
| Logging request/response bodies in production | Leaks tokens, PII, and session data | Set `LogLevel.NONE` in release; `sanitizeHeader` for Authorization |
| Single certificate pin without backup | App bricked on server key rotation | Always pin primary + backup hash |
| Catching generic `Exception` around Ktor calls | Swallows `CancellationException`, breaks structured concurrency | Rethrow `CancellationException`; catch specific exception types |
| Using `MODE_WORLD_READABLE` for files | Any app on device can read the file | Use `MODE_PRIVATE`; prefer `EncryptedFile` for sensitive data |
| No R8 rules for Metro `@DependencyGraph` | DI graph wiring fails at runtime with reflection errors | Keep annotated interfaces and their factory types |
| Skipping jailbreak detection | Attackers on compromised devices can intercept runtime state | Implement `expect`/`actual` device integrity checks |

---

## Gradle Dependencies

```toml
# libs.versions.toml
[versions]
security-crypto = "1.1.0-alpha06"
biometric = "1.2.0-alpha05"
secrets-plugin = "2.0.1"

[libraries]
# Android Secure Storage
androidx-security-crypto = { module = "androidx.security:security-crypto", version.ref = "security-crypto" }

# Biometric Authentication (Android)
androidx-biometric = { module = "androidx.biometric:biometric", version.ref = "biometric" }

[plugins]
secrets = { id = "com.google.android.libraries.mapsplatform.secrets-gradle-plugin", version.ref = "secrets-plugin" }
```

```kotlin
// build.gradle.kts (shared module)
kotlin {
    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.security.crypto)
            implementation(libs.androidx.biometric)
        }
        // iOS uses platform frameworks (Security.framework, LocalAuthentication.framework) -- no Gradle deps
    }
}
```

```kotlin
// app/build.gradle.kts
plugins {
    alias(libs.plugins.secrets)
}

secrets {
    propertiesFileName = "secrets.properties"
    defaultPropertiesFileName = "secrets.defaults.properties"
}
```

## See Also

- [ktor-client-expert](../ktor-client-expert/SKILL.md) -- Certificate pinning, TLS configuration
- [quality-expert](../quality-expert/SKILL.md) -- Static analysis, lint rules
- [error-handling-expert](../error-handling-expert/SKILL.md) -- Secure error handling patterns
