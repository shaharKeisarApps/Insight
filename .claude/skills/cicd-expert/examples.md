# CI/CD Expert Production Examples

## 1. Complete PR Quality Gate

A full `ci.yml` that enforces formatting, static analysis, API compatibility, tests, coverage, and Android lint on every pull request.

```yaml
# .github/workflows/ci.yml
name: CI

on:
  pull_request:
    branches: [main, develop]
    paths-ignore:
      - "**.md"
      - "docs/**"
      - "LICENSE"
  push:
    branches: [main]

concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true

env:
  JAVA_VERSION: "17"
  JAVA_DISTRIBUTION: "zulu"
  GRADLE_OPTS: "-Dorg.gradle.jvmargs=-Xmx4g -Dkotlin.incremental=false"

jobs:
  quality-gate:
    name: Quality Gate
    runs-on: ubuntu-latest
    timeout-minutes: 30

    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0             # Full history for Spotless ratchetFrom

      - uses: actions/setup-java@v4
        with:
          distribution: ${{ env.JAVA_DISTRIBUTION }}
          java-version: ${{ env.JAVA_VERSION }}

      - uses: gradle/actions/setup-gradle@v4
        with:
          cache-read-only: ${{ github.ref != 'refs/heads/main' }}

      - name: Check formatting
        run: ./gradlew spotlessCheck --no-daemon

      - name: Static analysis
        run: ./gradlew detekt --no-daemon

      - name: API compatibility
        run: ./gradlew apiCheck --no-daemon

      - name: Unit and integration tests
        run: ./gradlew allTests --no-daemon --continue

      - name: Code coverage verification
        run: ./gradlew koverVerify koverXmlReport --no-daemon

      - name: Android lint
        run: ./gradlew lint --no-daemon

      - name: Upload Detekt SARIF
        uses: github/codeql-action/upload-sarif@v3
        if: always()
        with:
          sarif_file: build/reports/detekt/detekt.sarif
          category: detekt

      - name: Upload coverage to Codecov
        uses: codecov/codecov-action@v4
        if: always()
        with:
          token: ${{ secrets.CODECOV_TOKEN }}
          files: build/reports/kover/report.xml
          flags: unittests
          fail_ci_if_error: false

      - name: Upload test reports
        uses: actions/upload-artifact@v4
        if: failure()
        with:
          name: test-reports
          path: |
            **/build/reports/tests/
            **/build/reports/detekt/
            **/build/reports/kover/
          retention-days: 7
```

---

## 2. KMP Build Matrix

Building for Android, iOS, and Desktop in parallel using a matrix strategy.

```yaml
# .github/workflows/build-matrix.yml
name: Build All Platforms

on:
  push:
    branches: [main]
  workflow_dispatch:

concurrency:
  group: build-${{ github.ref }}
  cancel-in-progress: true

env:
  JAVA_VERSION: "17"
  JAVA_DISTRIBUTION: "zulu"

jobs:
  build:
    name: Build ${{ matrix.platform }}
    runs-on: ${{ matrix.runner }}
    timeout-minutes: ${{ matrix.timeout }}
    strategy:
      fail-fast: false
      matrix:
        include:
          - platform: android
            runner: ubuntu-latest
            task: ":app:assembleRelease"
            timeout: 20
          - platform: ios
            runner: macos-14
            task: ":shared:linkReleaseFrameworkIosArm64"
            timeout: 30
          - platform: desktop
            runner: ubuntu-latest
            task: ":desktop:packageDistributable"
            timeout: 20

    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: ${{ env.JAVA_DISTRIBUTION }}
          java-version: ${{ env.JAVA_VERSION }}

      - uses: gradle/actions/setup-gradle@v4
        with:
          cache-read-only: ${{ github.ref != 'refs/heads/main' }}

      - name: Setup Xcode
        if: matrix.platform == 'ios'
        uses: maxim-lobanov/setup-xcode@v1
        with:
          xcode-version: "15.4"

      - name: Cache Kotlin/Native
        if: matrix.platform == 'ios'
        uses: actions/cache@v4
        with:
          path: ~/.konan
          key: konan-${{ runner.os }}-${{ hashFiles('gradle/libs.versions.toml') }}
          restore-keys: |
            konan-${{ runner.os }}-

      - name: Build ${{ matrix.platform }}
        run: ./gradlew ${{ matrix.task }} --no-daemon

      - name: Upload artifacts
        uses: actions/upload-artifact@v4
        with:
          name: ${{ matrix.platform }}-build
          path: |
            app/build/outputs/apk/release/*.apk
            shared/build/bin/iosArm64/releaseFramework/*.framework
            desktop/build/compose/binaries/main/
          if-no-files-found: warn
          retention-days: 7
```

---

## 3. Android Release Signing

Decoding a keystore from GitHub secrets and building a signed APK and AAB.

```yaml
# .github/workflows/android-release.yml
name: Android Release

on:
  push:
    tags:
      - "v*"

env:
  JAVA_VERSION: "17"
  JAVA_DISTRIBUTION: "zulu"

jobs:
  android-release:
    name: Build Signed Android Release
    runs-on: ubuntu-latest
    timeout-minutes: 20

    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: ${{ env.JAVA_DISTRIBUTION }}
          java-version: ${{ env.JAVA_VERSION }}

      - uses: gradle/actions/setup-gradle@v4

      - name: Decode keystore
        run: |
          echo "${{ secrets.KEYSTORE_BASE64 }}" | base64 --decode > app/release.jks

      - name: Build signed APK
        env:
          SIGNING_STORE_FILE: release.jks
          SIGNING_STORE_PASSWORD: ${{ secrets.STORE_PASSWORD }}
          SIGNING_KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          SIGNING_KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        run: ./gradlew :app:assembleRelease --no-daemon

      - name: Build signed AAB
        env:
          SIGNING_STORE_FILE: release.jks
          SIGNING_STORE_PASSWORD: ${{ secrets.STORE_PASSWORD }}
          SIGNING_KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          SIGNING_KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        run: ./gradlew :app:bundleRelease --no-daemon

      - name: Verify APK signature
        run: |
          APK_FILE=$(find app/build/outputs/apk/release -name "*.apk" | head -1)
          $ANDROID_HOME/build-tools/34.0.0/apksigner verify --verbose "$APK_FILE"

      - name: Upload signed APK
        uses: actions/upload-artifact@v4
        with:
          name: signed-apk
          path: app/build/outputs/apk/release/*.apk
          if-no-files-found: error

      - name: Upload signed AAB
        uses: actions/upload-artifact@v4
        with:
          name: signed-aab
          path: app/build/outputs/bundle/release/*.aab
          if-no-files-found: error

      - name: Clean up keystore
        if: always()
        run: rm -f app/release.jks
```

### Gradle Signing Configuration

```kotlin
// app/build.gradle.kts
android {
    signingConfigs {
        create("release") {
            val storeFilePath = System.getenv("SIGNING_STORE_FILE")
            if (storeFilePath != null) {
                storeFile = file(storeFilePath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}
```

---

## 4. iOS Build on macOS

Building the KMP shared framework and creating an Xcode archive with proper signing.

```yaml
# .github/workflows/ios-build.yml
name: iOS Build

on:
  push:
    tags:
      - "v*"
  workflow_dispatch:

jobs:
  ios-build:
    name: Build iOS
    runs-on: macos-14
    timeout-minutes: 45

    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: "zulu"
          java-version: "17"

      - uses: maxim-lobanov/setup-xcode@v1
        with:
          xcode-version: "15.4"

      - uses: gradle/actions/setup-gradle@v4

      - name: Cache Kotlin/Native
        uses: actions/cache@v4
        with:
          path: ~/.konan
          key: konan-${{ runner.os }}-${{ hashFiles('gradle/libs.versions.toml') }}
          restore-keys: |
            konan-${{ runner.os }}-

      - name: Build KMP shared framework
        run: ./gradlew :shared:linkReleaseFrameworkIosArm64 --no-daemon

      - name: Install Apple certificate and provisioning profile
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
            CODE_SIGN_STYLE=Manual \
            DEVELOPMENT_TEAM=${{ secrets.APPLE_TEAM_ID }}

      - name: Export IPA
        run: |
          xcodebuild -exportArchive \
            -archivePath $RUNNER_TEMP/iosApp.xcarchive \
            -exportOptionsPlist iosApp/ExportOptions.plist \
            -exportPath $RUNNER_TEMP/export

      - name: Upload IPA
        uses: actions/upload-artifact@v4
        with:
          name: ios-ipa
          path: ${{ runner.temp }}/export/*.ipa
          if-no-files-found: error

      - name: Clean up keychain
        if: always()
        run: security delete-keychain $RUNNER_TEMP/app-signing.keychain-db
```

### ExportOptions.plist

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>method</key>
    <string>app-store</string>
    <key>teamID</key>
    <string>YOUR_TEAM_ID</string>
    <key>uploadBitcode</key>
    <false/>
    <key>uploadSymbols</key>
    <true/>
    <key>signingStyle</key>
    <string>manual</string>
    <key>provisioningProfiles</key>
    <dict>
        <key>com.example.myapp</key>
        <string>MyApp Distribution Profile</string>
    </dict>
</dict>
</plist>
```

---

## 5. Nightly Extended Tests

A scheduled workflow that runs the full test suite including slow integration tests and performance benchmarks that are skipped during PR checks.

```yaml
# .github/workflows/nightly.yml
name: Nightly

on:
  schedule:
    - cron: "0 3 * * 1-5"           # Weeknights at 3 AM UTC
  workflow_dispatch:

env:
  JAVA_VERSION: "17"
  JAVA_DISTRIBUTION: "zulu"
  GRADLE_OPTS: "-Dorg.gradle.jvmargs=-Xmx6g -Dkotlin.incremental=false"

jobs:
  extended-tests:
    name: Extended Test Suite
    runs-on: ubuntu-latest
    timeout-minutes: 60

    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - uses: actions/setup-java@v4
        with:
          distribution: ${{ env.JAVA_DISTRIBUTION }}
          java-version: ${{ env.JAVA_VERSION }}

      - uses: gradle/actions/setup-gradle@v4

      - name: Run all tests including slow tests
        run: ./gradlew allTests -PincludeSlowTests=true --no-daemon --continue

      - name: Run integration tests
        run: ./gradlew integrationTest --no-daemon --continue

      - name: Generate full coverage report
        run: ./gradlew koverHtmlReport koverXmlReport --no-daemon

      - name: Upload coverage to Codecov
        uses: codecov/codecov-action@v4
        with:
          token: ${{ secrets.CODECOV_TOKEN }}
          files: build/reports/kover/report.xml
          flags: nightly
          fail_ci_if_error: false

      - name: Upload reports
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: nightly-reports
          path: |
            **/build/reports/tests/
            **/build/reports/kover/
          retention-days: 30

  ios-tests:
    name: iOS Tests
    runs-on: macos-14
    timeout-minutes: 45

    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: "zulu"
          java-version: "17"

      - uses: maxim-lobanov/setup-xcode@v1
        with:
          xcode-version: "15.4"

      - uses: gradle/actions/setup-gradle@v4

      - name: Cache Kotlin/Native
        uses: actions/cache@v4
        with:
          path: ~/.konan
          key: konan-${{ runner.os }}-${{ hashFiles('gradle/libs.versions.toml') }}
          restore-keys: |
            konan-${{ runner.os }}-

      - name: Run iOS tests
        run: ./gradlew :shared:iosSimulatorArm64Test --no-daemon

      - name: Upload iOS test results
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: ios-test-reports
          path: shared/build/reports/tests/iosSimulatorArm64Test/
          retention-days: 30

  dependency-check:
    name: Dependency Security Check
    runs-on: ubuntu-latest
    timeout-minutes: 15

    permissions:
      contents: write

    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: "zulu"
          java-version: "17"

      - uses: gradle/actions/setup-gradle@v4
        with:
          dependency-graph: generate-and-submit

      - name: Generate dependency graph
        run: ./gradlew dependencies --no-daemon

  notify:
    name: Notify on Failure
    runs-on: ubuntu-latest
    needs: [extended-tests, ios-tests, dependency-check]
    if: failure()

    steps:
      - name: Send Slack notification
        uses: slackapi/slack-github-action@v1
        with:
          payload: |
            {
              "text": "Nightly build failed: ${{ github.server_url }}/${{ github.repository }}/actions/runs/${{ github.run_id }}"
            }
        env:
          SLACK_WEBHOOK_URL: ${{ secrets.SLACK_WEBHOOK_URL }}
```

### Gradle Configuration for Slow Tests

```kotlin
// shared/build.gradle.kts
tasks.withType<Test>().configureEach {
    val includeSlowTests = project.findProperty("includeSlowTests")?.toString()?.toBoolean() ?: false
    if (!includeSlowTests) {
        useJUnitPlatform {
            excludeTags("slow")
        }
    }
}
```

```kotlin
// Usage in test code
@Tag("slow")
class LargeDatasetTest {
    @Test
    fun `process 100k records`() { /* ... */ }
}
```

---

## 6. Release Automation

A tag-triggered workflow that builds all platforms, generates a changelog, creates a GitHub release, and deploys to app stores.

```yaml
# .github/workflows/release.yml
name: Release

on:
  push:
    tags:
      - "v*"

permissions:
  contents: write

env:
  JAVA_VERSION: "17"
  JAVA_DISTRIBUTION: "zulu"

jobs:
  build-android:
    name: Build Android Release
    runs-on: ubuntu-latest
    timeout-minutes: 20

    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: ${{ env.JAVA_DISTRIBUTION }}
          java-version: ${{ env.JAVA_VERSION }}

      - uses: gradle/actions/setup-gradle@v4

      - name: Decode keystore
        run: echo "${{ secrets.KEYSTORE_BASE64 }}" | base64 --decode > app/release.jks

      - name: Build signed APK and AAB
        env:
          SIGNING_STORE_FILE: release.jks
          SIGNING_STORE_PASSWORD: ${{ secrets.STORE_PASSWORD }}
          SIGNING_KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          SIGNING_KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        run: |
          ./gradlew :app:assembleRelease :app:bundleRelease --no-daemon

      - name: Upload Android artifacts
        uses: actions/upload-artifact@v4
        with:
          name: android-release
          path: |
            app/build/outputs/apk/release/*.apk
            app/build/outputs/bundle/release/*.aab
          if-no-files-found: error

      - name: Clean up
        if: always()
        run: rm -f app/release.jks

  build-ios:
    name: Build iOS Release
    runs-on: macos-14
    timeout-minutes: 45

    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: "zulu"
          java-version: "17"

      - uses: maxim-lobanov/setup-xcode@v1
        with:
          xcode-version: "15.4"

      - uses: gradle/actions/setup-gradle@v4

      - name: Cache Kotlin/Native
        uses: actions/cache@v4
        with:
          path: ~/.konan
          key: konan-${{ runner.os }}-${{ hashFiles('gradle/libs.versions.toml') }}
          restore-keys: |
            konan-${{ runner.os }}-

      - name: Build shared framework
        run: ./gradlew :shared:linkReleaseFrameworkIosArm64 --no-daemon

      - name: Upload iOS framework
        uses: actions/upload-artifact@v4
        with:
          name: ios-framework
          path: shared/build/bin/iosArm64/releaseFramework/
          if-no-files-found: error

  build-desktop:
    name: Build Desktop Release
    runs-on: ubuntu-latest
    timeout-minutes: 20

    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: ${{ env.JAVA_DISTRIBUTION }}
          java-version: ${{ env.JAVA_VERSION }}

      - uses: gradle/actions/setup-gradle@v4

      - name: Package desktop app
        run: ./gradlew :desktop:packageDistributable --no-daemon

      - name: Upload desktop artifacts
        uses: actions/upload-artifact@v4
        with:
          name: desktop-release
          path: desktop/build/compose/binaries/main/
          if-no-files-found: warn

  create-release:
    name: Create GitHub Release
    runs-on: ubuntu-latest
    needs: [build-android, build-ios, build-desktop]
    timeout-minutes: 10

    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - name: Download all artifacts
        uses: actions/download-artifact@v4
        with:
          path: release-artifacts/

      - name: Generate changelog
        id: changelog
        run: |
          TAG_NAME=${GITHUB_REF#refs/tags/}
          PREVIOUS_TAG=$(git describe --tags --abbrev=0 HEAD^ 2>/dev/null || echo "")

          echo "## What's Changed" > changelog.md
          echo "" >> changelog.md

          if [ -z "$PREVIOUS_TAG" ]; then
            git log --pretty=format:"- %s (%h)" HEAD >> changelog.md
          else
            echo "**Full Changelog**: $PREVIOUS_TAG...$TAG_NAME" >> changelog.md
            echo "" >> changelog.md

            # Group by conventional commit type
            for TYPE in feat fix docs refactor perf test chore; do
              COMMITS=$(git log --pretty=format:"- %s (%h)" "$PREVIOUS_TAG"..HEAD | grep "^- $TYPE" || true)
              if [ -n "$COMMITS" ]; then
                case $TYPE in
                  feat) echo "### New Features" >> changelog.md ;;
                  fix) echo "### Bug Fixes" >> changelog.md ;;
                  docs) echo "### Documentation" >> changelog.md ;;
                  refactor) echo "### Refactoring" >> changelog.md ;;
                  perf) echo "### Performance" >> changelog.md ;;
                  test) echo "### Tests" >> changelog.md ;;
                  chore) echo "### Maintenance" >> changelog.md ;;
                esac
                echo "$COMMITS" >> changelog.md
                echo "" >> changelog.md
              fi
            done
          fi

      - name: Create GitHub Release
        uses: softprops/action-gh-release@v2
        with:
          body_path: changelog.md
          files: |
            release-artifacts/android-release/**/*.apk
            release-artifacts/android-release/**/*.aab
          generate_release_notes: true
          draft: false
          prerelease: ${{ contains(github.ref, '-rc') || contains(github.ref, '-beta') || contains(github.ref, '-alpha') }}

  deploy-play-store:
    name: Deploy to Google Play
    runs-on: ubuntu-latest
    needs: [create-release]
    timeout-minutes: 15
    if: "!contains(github.ref, '-rc') && !contains(github.ref, '-beta') && !contains(github.ref, '-alpha')"

    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: "zulu"
          java-version: "17"

      - uses: gradle/actions/setup-gradle@v4

      - name: Decode keystore and service account
        run: |
          echo "${{ secrets.KEYSTORE_BASE64 }}" | base64 --decode > app/release.jks
          echo "${{ secrets.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON }}" | base64 --decode > app/play-service-account.json

      - name: Publish to Play Store internal track
        env:
          SIGNING_STORE_FILE: release.jks
          SIGNING_STORE_PASSWORD: ${{ secrets.STORE_PASSWORD }}
          SIGNING_KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          SIGNING_KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        run: ./gradlew :app:publishReleaseBundle --no-daemon

      - name: Clean up secrets
        if: always()
        run: |
          rm -f app/release.jks
          rm -f app/play-service-account.json
```

---

## 7. Gradle Cache Optimization

Advanced caching strategies to minimize CI build times for KMP projects.

```yaml
# .github/workflows/ci-cached.yml
name: CI (Optimized Cache)

on:
  pull_request:
    branches: [main]
  push:
    branches: [main]

concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true

env:
  JAVA_VERSION: "17"
  JAVA_DISTRIBUTION: "zulu"
  GRADLE_OPTS: >-
    -Dorg.gradle.jvmargs=-Xmx4g
    -Dorg.gradle.parallel=true
    -Dorg.gradle.caching=true
    -Dkotlin.incremental=false
    -Dorg.gradle.configuration-cache=true
    -Dorg.gradle.configuration-cache.problems=warn

jobs:
  quality:
    name: Quality Gate
    runs-on: ubuntu-latest
    timeout-minutes: 30

    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - uses: actions/setup-java@v4
        with:
          distribution: ${{ env.JAVA_DISTRIBUTION }}
          java-version: ${{ env.JAVA_VERSION }}

      # The setup-gradle action handles:
      # - Gradle wrapper caching
      # - Dependency caching (~/.gradle/caches)
      # - Build cache (~/.gradle/caches/build-cache-*)
      # - Cache key derived from wrapper props + build files + gradle properties
      - uses: gradle/actions/setup-gradle@v4
        with:
          # PRs only READ cache (prevents cache poisoning from forks)
          # Main branch WRITES cache
          cache-read-only: ${{ github.event_name == 'pull_request' }}
          # Publish build scan for slow build debugging
          build-scan-publish: true
          build-scan-terms-of-use-url: "https://gradle.com/help/legal-terms-of-use"
          build-scan-terms-of-use-agree: "yes"

      # Cache KSP generated sources separately (changes less frequently)
      - name: Cache KSP outputs
        uses: actions/cache@v4
        with:
          path: |
            **/build/generated/ksp
          key: ksp-${{ runner.os }}-${{ hashFiles('**/build.gradle.kts', 'gradle/libs.versions.toml') }}
          restore-keys: |
            ksp-${{ runner.os }}-

      # Run all quality checks in one Gradle invocation for maximum cache reuse
      - name: Run quality checks
        run: |
          ./gradlew \
            spotlessCheck \
            detekt \
            apiCheck \
            allTests \
            koverVerify \
            koverXmlReport \
            lint \
            --no-daemon \
            --continue \
            --build-cache

      - name: Upload Detekt SARIF
        uses: github/codeql-action/upload-sarif@v3
        if: always()
        with:
          sarif_file: build/reports/detekt/detekt.sarif
          category: detekt

      - name: Upload coverage
        uses: codecov/codecov-action@v4
        if: always()
        with:
          token: ${{ secrets.CODECOV_TOKEN }}
          files: build/reports/kover/report.xml
          fail_ci_if_error: false

  ios-check:
    name: iOS Compilation Check
    runs-on: macos-14
    timeout-minutes: 30
    # Only run iOS check when relevant files change
    if: >-
      github.event_name == 'push' ||
      contains(github.event.pull_request.labels.*.name, 'ios') ||
      true

    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: "zulu"
          java-version: "17"

      - uses: gradle/actions/setup-gradle@v4
        with:
          cache-read-only: ${{ github.event_name == 'pull_request' }}

      # Kotlin/Native cache is critical -- saves 5-10 minutes
      # Key includes libs.versions.toml because Kotlin version changes invalidate the cache
      - name: Cache Kotlin/Native
        uses: actions/cache@v4
        with:
          path: |
            ~/.konan/dependencies
            ~/.konan/kotlin-native-prebuilt-*
          key: konan-${{ runner.os }}-${{ hashFiles('gradle/libs.versions.toml') }}
          restore-keys: |
            konan-${{ runner.os }}-

      # Cache compiled klibs separately (changes more frequently than compiler)
      - name: Cache Kotlin/Native klibs
        uses: actions/cache@v4
        with:
          path: ~/.konan/cache
          key: konan-klibs-${{ runner.os }}-${{ hashFiles('shared/src/**/*.kt', 'gradle/libs.versions.toml') }}
          restore-keys: |
            konan-klibs-${{ runner.os }}-

      - name: Compile iOS framework
        run: ./gradlew :shared:compileKotlinIosArm64 --no-daemon --build-cache

      - name: Run iOS simulator tests
        run: ./gradlew :shared:iosSimulatorArm64Test --no-daemon --build-cache
```

### Cache Size Summary

| Cache | Typical Size | Hit Rate | Time Saved |
|-------|-------------|----------|------------|
| Gradle wrapper | ~100 MB | >99% | ~30s |
| Gradle dependencies | ~500 MB | ~90% | ~60s |
| Gradle build cache | ~200 MB | ~70% | ~120s |
| Kotlin/Native compiler | ~2 GB | ~95% | ~300s |
| Kotlin/Native klibs | ~500 MB | ~60% | ~180s |
| KSP generated sources | ~50 MB | ~80% | ~30s |

### Cache Key Strategy

The cache key hierarchy is designed so that:
1. **Exact match** restores the full cache -- no work needed.
2. **Prefix match** (`restore-keys`) restores a stale but usable cache -- only incremental work needed.
3. **No match** does a cold build -- slowest but self-healing.

```yaml
# Example: Kotlin/Native cache key strategy
key: konan-macOS-abc123       # Exact: matches current libs.versions.toml hash
restore-keys: |
  konan-macOS-                # Prefix: matches any previous version -- stale but reusable
```

### Reducing Cache Write Frequency

Only write caches on `main` branch pushes. PRs read from cache but never write. This prevents:
- Cache thrashing from many concurrent PR branches
- Cache poisoning from forked repository PRs
- Exceeding GitHub's 10 GB cache quota

```yaml
cache-read-only: ${{ github.event_name == 'pull_request' }}
```
