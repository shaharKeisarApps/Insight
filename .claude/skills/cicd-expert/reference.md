# CI/CD Expert API Reference

## GitHub Actions Workflow Syntax for KMP

### Workflow Triggers

```yaml
on:
  pull_request:
    branches: [main, develop]
    paths-ignore:
      - "**.md"
      - "docs/**"
  push:
    branches: [main]
  schedule:
    - cron: "0 3 * * *"            # Nightly at 3 AM UTC
  workflow_dispatch:                 # Manual trigger
    inputs:
      build_type:
        description: "Build type"
        required: true
        default: "debug"
        type: choice
        options: [debug, release]
```

### Concurrency Control

```yaml
concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true          # Cancel older runs on same branch
```

### Environment Variables

```yaml
env:
  JAVA_VERSION: "17"
  JAVA_DISTRIBUTION: "zulu"
  GRADLE_OPTS: "-Dorg.gradle.jvmargs=-Xmx4g -Dorg.gradle.parallel=true"
```

---

## JDK Setup

Use Azul Zulu for consistent behavior across platforms. Zulu provides macOS ARM64 (M1) builds, which is critical for iOS runners.

```yaml
- uses: actions/setup-java@v4
  with:
    distribution: "zulu"
    java-version: "17"
```

### Why Zulu

| Distribution | macOS ARM64 | License | LTS |
|--------------|-------------|---------|-----|
| Zulu (Azul) | Yes | Free | Yes |
| Temurin (Adoptium) | Yes | Free | Yes |
| Corretto (Amazon) | Yes | Free | Yes |
| Oracle JDK | Yes | Commercial | Yes |

Zulu is the most commonly used in KMP CI due to early ARM64 support and broad compatibility with Gradle and Kotlin compiler.

---

## Gradle Setup with gradle/actions/setup-gradle@v4

This is the official Gradle GitHub Action. It handles wrapper validation, dependency caching, and build scan publishing.

```yaml
- uses: gradle/actions/setup-gradle@v4
  with:
    cache-read-only: ${{ github.ref != 'refs/heads/main' }}
```

### Key Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `cache-read-only` | `false` | Set `true` for PRs to prevent cache poisoning |
| `gradle-version` | wrapper | Use `wrapper` to respect `gradle-wrapper.properties` |
| `build-scan-publish` | `false` | Publish Gradle Build Scan for debugging |
| `build-scan-terms-of-use-url` | - | Required if `build-scan-publish` is `true` |
| `build-scan-terms-of-use-agree` | - | Set to `"yes"` to accept terms |
| `dependency-graph` | `disabled` | Set `generate-and-submit` for Dependency Submission API |

### Cache Behavior

The action caches:
- `~/.gradle/caches` -- Downloaded dependencies
- `~/.gradle/wrapper` -- Gradle distribution
- `~/.gradle/caches/build-cache-*` -- Local build cache

Cache keys are derived from `gradle-wrapper.properties`, `*.gradle.kts`, and `gradle.properties`.

### Dependency Submission (Security Alerts)

```yaml
- uses: gradle/actions/setup-gradle@v4
  with:
    dependency-graph: generate-and-submit
```

This submits the dependency graph to GitHub so Dependabot and security alerts work for Gradle projects.

---

## Kotlin/Native Caching

Kotlin/Native compilation is the slowest part of KMP CI. Caching the Konan directory saves significant time.

```yaml
- name: Cache Kotlin/Native compiler
  uses: actions/cache@v4
  with:
    path: ~/.konan
    key: konan-${{ runner.os }}-${{ hashFiles('gradle/libs.versions.toml') }}
    restore-keys: |
      konan-${{ runner.os }}-
```

### What Gets Cached

| Directory | Contents | Size |
|-----------|----------|------|
| `~/.konan/dependencies` | LLVM, platform libs | ~2 GB |
| `~/.konan/kotlin-native-prebuilt-*` | Kotlin/Native compiler | ~500 MB |
| `~/.konan/cache` | Precompiled klibs | Variable |

---

## Android Signing in CI

### Decoding Keystore from Secrets

```yaml
- name: Decode keystore
  run: |
    echo "${{ secrets.KEYSTORE_BASE64 }}" | base64 --decode > app/release.jks

- name: Build signed release
  env:
    SIGNING_STORE_FILE: release.jks
    SIGNING_STORE_PASSWORD: ${{ secrets.STORE_PASSWORD }}
    SIGNING_KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
    SIGNING_KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
  run: ./gradlew :app:assembleRelease --no-daemon
```

### Gradle Signing Configuration

```kotlin
// app/build.gradle.kts
android {
    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("SIGNING_STORE_FILE") ?: "release.jks")
            storePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: ""
            keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: ""
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: ""
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}
```

### Encoding a Keystore

```bash
# One-time local operation to create the secret value
base64 -i app/release.jks | pbcopy   # macOS: copies to clipboard
# Paste into GitHub > Settings > Secrets > KEYSTORE_BASE64
```

---

## iOS Build with Xcode

### Framework Build (Shared Module)

```yaml
- name: Build iOS framework
  run: ./gradlew :shared:linkReleaseFrameworkIosArm64 --no-daemon
```

### Xcode Archive and Export

```yaml
- name: Install Apple certificate
  env:
    P12_BASE64: ${{ secrets.APPLE_CERTIFICATE_P12 }}
    P12_PASSWORD: ${{ secrets.APPLE_CERTIFICATE_PASSWORD }}
    PROFILE_BASE64: ${{ secrets.APPLE_PROVISIONING_PROFILE }}
  run: |
    CERTIFICATE_PATH=$RUNNER_TEMP/certificate.p12
    PROFILE_PATH=$RUNNER_TEMP/profile.mobileprovision
    KEYCHAIN_PATH=$RUNNER_TEMP/app-signing.keychain-db

    echo "$P12_BASE64" | base64 --decode > "$CERTIFICATE_PATH"
    echo "$PROFILE_BASE64" | base64 --decode > "$PROFILE_PATH"

    security create-keychain -p "" "$KEYCHAIN_PATH"
    security set-keychain-settings -lut 21600 "$KEYCHAIN_PATH"
    security unlock-keychain -p "" "$KEYCHAIN_PATH"
    security import "$CERTIFICATE_PATH" -P "$P12_PASSWORD" -A -t cert -f pkcs12 -k "$KEYCHAIN_PATH"
    security set-key-partition-list -S apple-tool:,apple: -k "" "$KEYCHAIN_PATH"
    security list-keychains -d user -s "$KEYCHAIN_PATH"

    mkdir -p ~/Library/MobileDevice/Provisioning\ Profiles
    cp "$PROFILE_PATH" ~/Library/MobileDevice/Provisioning\ Profiles/

- name: Build Xcode archive
  run: |
    xcodebuild archive \
      -workspace iosApp/iosApp.xcworkspace \
      -scheme iosApp \
      -archivePath $RUNNER_TEMP/iosApp.xcarchive \
      -sdk iphoneos \
      -configuration Release \
      -destination "generic/platform=iOS" \
      CODE_SIGN_STYLE=Manual

- name: Export IPA
  run: |
    xcodebuild -exportArchive \
      -archivePath $RUNNER_TEMP/iosApp.xcarchive \
      -exportOptionsPlist iosApp/ExportOptions.plist \
      -exportPath $RUNNER_TEMP/export

- name: Clean up keychain
  if: always()
  run: security delete-keychain $RUNNER_TEMP/app-signing.keychain-db
```

---

## Artifact Upload and Download

### Upload Build Artifacts

```yaml
- uses: actions/upload-artifact@v4
  with:
    name: android-release-apk
    path: app/build/outputs/apk/release/*.apk
    retention-days: 14
    if-no-files-found: error
```

### Download Across Jobs

```yaml
- uses: actions/download-artifact@v4
  with:
    name: android-release-apk
    path: artifacts/
```

---

## Matrix Builds

```yaml
strategy:
  fail-fast: false                   # Do not cancel other platforms on failure
  matrix:
    include:
      - platform: android
        runner: ubuntu-latest
        task: ":app:assembleRelease"
      - platform: ios
        runner: macos-14
        task: ":shared:linkReleaseFrameworkIosArm64"
      - platform: desktop
        runner: ubuntu-latest
        task: ":desktop:packageDistributable"
```

### Conditional Steps in Matrix

```yaml
- name: Setup Xcode
  if: matrix.platform == 'ios'
  uses: maxim-lobanov/setup-xcode@v1
  with:
    xcode-version: "15.4"
```

---

## Code Coverage Reports (Kover to Codecov)

```yaml
- name: Generate coverage report
  run: ./gradlew koverXmlReport --no-daemon

- name: Upload to Codecov
  uses: codecov/codecov-action@v4
  with:
    token: ${{ secrets.CODECOV_TOKEN }}
    files: build/reports/kover/report.xml
    flags: unittests
    fail_ci_if_error: false
```

---

## SARIF Upload for Detekt

```yaml
- name: Run Detekt
  run: ./gradlew detekt --no-daemon
  continue-on-error: true            # Upload SARIF even on failure

- name: Upload Detekt SARIF
  uses: github/codeql-action/upload-sarif@v3
  if: always()
  with:
    sarif_file: build/reports/detekt/detekt.sarif
    category: detekt
```

This enables inline code annotations on PRs through GitHub Code Scanning.

---

## Release Automation

### Tag-Triggered Release

```yaml
on:
  push:
    tags:
      - "v*"

jobs:
  release:
    runs-on: ubuntu-latest
    permissions:
      contents: write                # Required for creating GitHub releases
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - name: Generate changelog
        id: changelog
        run: |
          PREVIOUS_TAG=$(git describe --tags --abbrev=0 HEAD^ 2>/dev/null || echo "")
          if [ -z "$PREVIOUS_TAG" ]; then
            CHANGELOG=$(git log --pretty=format:"- %s (%h)" HEAD)
          else
            CHANGELOG=$(git log --pretty=format:"- %s (%h)" "$PREVIOUS_TAG"..HEAD)
          fi
          echo "changelog<<EOF" >> "$GITHUB_OUTPUT"
          echo "$CHANGELOG" >> "$GITHUB_OUTPUT"
          echo "EOF" >> "$GITHUB_OUTPUT"

      - name: Create GitHub Release
        uses: softprops/action-gh-release@v2
        with:
          body: ${{ steps.changelog.outputs.changelog }}
          files: |
            app/build/outputs/apk/release/*.apk
            app/build/outputs/bundle/release/*.aab
          generate_release_notes: true
```

---

## Google Play Deployment (Gradle Play Publisher)

```kotlin
// app/build.gradle.kts
plugins {
    id("com.github.triplet.play") version "3.10.1"
}

play {
    serviceAccountCredentials.set(file("play-service-account.json"))
    track.set("internal")            // internal -> alpha -> beta -> production
    defaultToAppBundles.set(true)
}
```

```yaml
# CI workflow step
- name: Decode Play service account
  run: echo "${{ secrets.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON }}" | base64 --decode > app/play-service-account.json

- name: Publish to Play Store
  run: ./gradlew :app:publishReleaseBundle --no-daemon
```

---

## Fastlane Integration for iOS

```yaml
- name: Install Fastlane
  run: |
    gem install bundler
    cd iosApp && bundle install

- name: Deploy to TestFlight
  env:
    APP_STORE_CONNECT_API_KEY: ${{ secrets.APP_STORE_CONNECT_API_KEY }}
    APP_STORE_CONNECT_API_KEY_ID: ${{ secrets.APP_STORE_KEY_ID }}
    APP_STORE_CONNECT_API_ISSUER_ID: ${{ secrets.APP_STORE_ISSUER_ID }}
  run: cd iosApp && bundle exec fastlane beta
```

### Minimal Fastfile

```ruby
# iosApp/fastlane/Fastfile
default_platform(:ios)

platform :ios do
  desc "Push to TestFlight"
  lane :beta do
    app_store_connect_api_key(
      key_id: ENV["APP_STORE_CONNECT_API_KEY_ID"],
      issuer_id: ENV["APP_STORE_CONNECT_API_ISSUER_ID"],
      key_content: ENV["APP_STORE_CONNECT_API_KEY"],
      is_key_content_base64: true,
    )
    build_app(
      workspace: "iosApp.xcworkspace",
      scheme: "iosApp",
      configuration: "Release",
      export_method: "app-store",
    )
    upload_to_testflight(skip_waiting_for_build_processing: true)
  end
end
```

---

## Key GitHub Actions References

| Action | Version | Purpose |
|--------|---------|---------|
| `actions/checkout` | `v4` | Clone repository |
| `actions/setup-java` | `v4` | Install JDK |
| `actions/cache` | `v4` | Manual cache management |
| `actions/upload-artifact` | `v4` | Upload build artifacts |
| `actions/download-artifact` | `v4` | Download artifacts across jobs |
| `gradle/actions/setup-gradle` | `v4` | Gradle wrapper, caching, dependency submission |
| `maxim-lobanov/setup-xcode` | `v1` | Select Xcode version on macOS runners |
| `codecov/codecov-action` | `v4` | Upload coverage reports |
| `github/codeql-action/upload-sarif` | `v3` | Upload SARIF for code scanning |
| `softprops/action-gh-release` | `v2` | Create GitHub releases |

### Pinning Actions to SHA

For supply chain security, pin actions to their full commit SHA instead of a mutable tag.

```yaml
# Instead of:
- uses: actions/checkout@v4

# Use:
- uses: actions/checkout@b4ffde65f46336ab88eb53be808477a3936bae11  # v4.1.1
```

Use Dependabot to keep SHA-pinned actions updated:

```yaml
# .github/dependabot.yml
version: 2
updates:
  - package-ecosystem: "github-actions"
    directory: "/"
    schedule:
      interval: "weekly"
```

---

## Gradle Configuration for CI

### gradle.properties (CI-specific)

```properties
# Increase memory for CI builds
org.gradle.jvmargs=-Xmx4g -XX:+HeapDumpOnOutOfMemoryError
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true
org.gradle.configuration-cache.problems=warn

# Kotlin settings
kotlin.incremental=false
kotlin.daemon.jvmargs=-Xmx2g
```

### Disabling Incremental Compilation in CI

Incremental compilation in CI can cause stale output issues. Disable it explicitly:

```yaml
env:
  GRADLE_OPTS: >-
    -Dkotlin.incremental=false
    -Dorg.gradle.jvmargs=-Xmx4g
```

---

## Version Catalog Entries

```toml
[versions]
gradle-play-publisher = "3.10.1"

[plugins]
play-publisher = { id = "com.github.triplet.play", version.ref = "gradle-play-publisher" }
```
