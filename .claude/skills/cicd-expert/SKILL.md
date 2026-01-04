---
name: cicd-expert
description: Elite CI/CD expertise for KMP projects using GitHub Actions. Use when setting up workflows, optimizing builds, configuring caching, matrix builds, artifact publishing, or release automation. Triggers on CI configuration, workflow optimization, deployment pipelines, or GitHub Actions questions.
---

# CI/CD Expert Skill

## Workflow Structure

```
.github/
├── workflows/
│   ├── ci.yml              # Main CI pipeline
│   ├── release.yml         # Release automation
│   ├── screenshots.yml     # Screenshot test verification
│   └── dependency-update.yml
├── actions/
│   └── setup-gradle/       # Reusable setup action
│       └── action.yml
└── CODEOWNERS
```

## Main CI Workflow

```yaml
# .github/workflows/ci.yml
name: CI

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main, develop]

concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

env:
  GRADLE_OPTS: "-Dorg.gradle.daemon=true -Dorg.gradle.parallel=true -Dorg.gradle.caching=true"

jobs:
  # Quick checks first
  validation:
    name: Validate
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Validate Gradle wrapper
        uses: gradle/wrapper-validation-action@v3
      
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v3
        with:
          cache-read-only: ${{ github.ref != 'refs/heads/main' }}
      
      - name: Check formatting
        run: ./gradlew spotlessCheck
      
      - name: Run Detekt
        run: ./gradlew detekt
      
      - name: Upload Detekt report
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: detekt-report
          path: '**/build/reports/detekt/'

  # Build all targets
  build:
    name: Build
    needs: validation
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v3
        with:
          cache-read-only: ${{ github.ref != 'refs/heads/main' }}
      
      - name: Build Android
        run: ./gradlew :app:android:assembleDebug
      
      - name: Build Desktop
        run: ./gradlew :app:desktop:assemble
      
      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: debug-apk
          path: app/android/build/outputs/apk/debug/*.apk
          retention-days: 7

  # iOS build on macOS
  build-ios:
    name: Build iOS
    needs: validation
    runs-on: macos-14
    steps:
      - uses: actions/checkout@v4
      
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v3
        with:
          cache-read-only: ${{ github.ref != 'refs/heads/main' }}
      
      - name: Build iOS Framework
        run: ./gradlew :shared:linkReleaseFrameworkIosArm64
      
      - name: Build iOS App
        run: |
          cd iosApp
          xcodebuild build \
            -scheme iosApp \
            -configuration Debug \
            -destination 'platform=iOS Simulator,name=iPhone 15' \
            CODE_SIGN_IDENTITY="" \
            CODE_SIGNING_REQUIRED=NO

  # Unit tests
  test:
    name: Unit Tests
    needs: build
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v3
        with:
          cache-read-only: ${{ github.ref != 'refs/heads/main' }}
      
      - name: Run unit tests
        run: ./gradlew check
      
      - name: Upload test results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-results
          path: '**/build/reports/tests/'
      
      - name: Publish test report
        if: always()
        uses: mikepenz/action-junit-report@v4
        with:
          report_paths: '**/build/test-results/**/TEST-*.xml'

  # Screenshot tests
  screenshot-tests:
    name: Screenshot Tests
    needs: build
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          lfs: true  # If using Git LFS for screenshots
      
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v3
        with:
          cache-read-only: ${{ github.ref != 'refs/heads/main' }}
      
      - name: Verify screenshots
        run: ./gradlew verifyPaparazziDebug
      
      - name: Upload screenshot diffs
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: screenshot-diffs
          path: '**/build/paparazzi/failures/'

  # iOS tests
  test-ios:
    name: iOS Tests
    needs: build-ios
    runs-on: macos-14
    steps:
      - uses: actions/checkout@v4
      
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v3
        with:
          cache-read-only: ${{ github.ref != 'refs/heads/main' }}
      
      - name: Run iOS tests
        run: ./gradlew :shared:iosSimulatorArm64Test
```

## Release Workflow

```yaml
# .github/workflows/release.yml
name: Release

on:
  push:
    tags:
      - 'v*'

permissions:
  contents: write
  packages: write

jobs:
  create-release:
    name: Create Release
    runs-on: ubuntu-latest
    outputs:
      upload_url: ${{ steps.create_release.outputs.upload_url }}
      version: ${{ steps.get_version.outputs.version }}
    steps:
      - uses: actions/checkout@v4
      
      - name: Get version
        id: get_version
        run: echo "version=${GITHUB_REF#refs/tags/v}" >> $GITHUB_OUTPUT
      
      - name: Create Release
        id: create_release
        uses: actions/create-release@v1
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        with:
          tag_name: ${{ github.ref }}
          release_name: Release ${{ steps.get_version.outputs.version }}
          draft: true
          prerelease: false

  build-android:
    name: Build Android Release
    needs: create-release
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v3
      
      - name: Decode Keystore
        run: |
          echo "${{ secrets.KEYSTORE_BASE64 }}" | base64 -d > keystore.jks
      
      - name: Build Release APK
        env:
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        run: ./gradlew :app:android:assembleRelease
      
      - name: Build Release AAB
        env:
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        run: ./gradlew :app:android:bundleRelease
      
      - name: Upload APK
        uses: actions/upload-release-asset@v1
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        with:
          upload_url: ${{ needs.create-release.outputs.upload_url }}
          asset_path: app/android/build/outputs/apk/release/app-release.apk
          asset_name: app-${{ needs.create-release.outputs.version }}.apk
          asset_content_type: application/vnd.android.package-archive
      
      - name: Upload AAB
        uses: actions/upload-release-asset@v1
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        with:
          upload_url: ${{ needs.create-release.outputs.upload_url }}
          asset_path: app/android/build/outputs/bundle/release/app-release.aab
          asset_name: app-${{ needs.create-release.outputs.version }}.aab
          asset_content_type: application/octet-stream

  build-desktop:
    name: Build Desktop
    needs: create-release
    strategy:
      matrix:
        include:
          - os: ubuntu-latest
            format: deb
            artifact: "*.deb"
          - os: macos-14
            format: dmg
            artifact: "*.dmg"
          - os: windows-latest
            format: msi
            artifact: "*.msi"
    runs-on: ${{ matrix.os }}
    steps:
      - uses: actions/checkout@v4
      
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v3
      
      - name: Build package
        run: ./gradlew :app:desktop:package${{ matrix.format }}
      
      - name: Upload artifact
        uses: actions/upload-release-asset@v1
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        with:
          upload_url: ${{ needs.create-release.outputs.upload_url }}
          asset_path: app/desktop/build/compose/binaries/main/${{ matrix.format }}/${{ matrix.artifact }}
          asset_name: app-${{ needs.create-release.outputs.version }}.${{ matrix.format }}
          asset_content_type: application/octet-stream
```

## Caching Strategy

```yaml
# Optimal Gradle caching setup
- name: Setup Gradle
  uses: gradle/actions/setup-gradle@v3
  with:
    # Only write cache from main branch
    cache-read-only: ${{ github.ref != 'refs/heads/main' }}
    
    # Cache configuration
    gradle-home-cache-includes: |
      caches
      notifications
      jdks
    
    # Exclude volatile files
    gradle-home-cache-excludes: |
      caches/modules-2/modules-2.lock
      caches/*/plugin-resolution/

# Additional caching for KMP
- name: Cache Kotlin Native
  uses: actions/cache@v4
  with:
    path: ~/.konan
    key: ${{ runner.os }}-konan-${{ hashFiles('**/*.gradle.kts') }}
    restore-keys: |
      ${{ runner.os }}-konan-

# Cocoapods cache for iOS
- name: Cache CocoaPods
  uses: actions/cache@v4
  with:
    path: |
      iosApp/Pods
      ~/.cocoapods
    key: ${{ runner.os }}-pods-${{ hashFiles('iosApp/Podfile.lock') }}
```

## Matrix Builds

```yaml
# Parallel testing across configurations
test-matrix:
  name: Test (${{ matrix.target }})
  strategy:
    fail-fast: false
    matrix:
      include:
        - target: jvmTest
          os: ubuntu-latest
        - target: androidUnitTest
          os: ubuntu-latest
        - target: iosSimulatorArm64Test
          os: macos-14
        - target: desktopTest
          os: ubuntu-latest
  runs-on: ${{ matrix.os }}
  steps:
    - uses: actions/checkout@v4
    
    - uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'
    
    - name: Setup Gradle
      uses: gradle/actions/setup-gradle@v3
    
    - name: Run tests
      run: ./gradlew ${{ matrix.target }}
```

## Reusable Actions

```yaml
# .github/actions/setup-gradle/action.yml
name: Setup Gradle Environment
description: Sets up Java and Gradle with caching

inputs:
  java-version:
    description: Java version to use
    default: '17'
  cache-read-only:
    description: Whether to only read from cache
    default: 'false'

runs:
  using: composite
  steps:
    - name: Setup JDK
      uses: actions/setup-java@v4
      with:
        java-version: ${{ inputs.java-version }}
        distribution: 'temurin'
    
    - name: Setup Gradle
      uses: gradle/actions/setup-gradle@v3
      with:
        cache-read-only: ${{ inputs.cache-read-only }}
    
    - name: Cache Kotlin Native
      uses: actions/cache@v4
      with:
        path: ~/.konan
        key: ${{ runner.os }}-konan-${{ hashFiles('**/*.gradle.kts') }}
        restore-keys: |
          ${{ runner.os }}-konan-
```

```yaml
# Usage in workflow
steps:
  - uses: actions/checkout@v4
  - uses: ./.github/actions/setup-gradle
    with:
      cache-read-only: ${{ github.ref != 'refs/heads/main' }}
```

## Dependency Updates

```yaml
# .github/workflows/dependency-update.yml
name: Dependency Updates

on:
  schedule:
    - cron: '0 6 * * 1'  # Every Monday at 6am
  workflow_dispatch:

jobs:
  update:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v3
      
      - name: Check for updates
        run: ./gradlew dependencyUpdates -Drevision=release
      
      - name: Upload report
        uses: actions/upload-artifact@v4
        with:
          name: dependency-updates
          path: build/dependencyUpdates/report.txt
```

## PR Checks

```yaml
# .github/workflows/pr-checks.yml
name: PR Checks

on:
  pull_request:
    types: [opened, synchronize, reopened, ready_for_review]

jobs:
  size-label:
    runs-on: ubuntu-latest
    steps:
      - uses: codelytv/pr-size-labeler@v1
        with:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          xs_label: 'size/XS'
          xs_max_size: 10
          s_label: 'size/S'
          s_max_size: 100
          m_label: 'size/M'
          m_max_size: 500
          l_label: 'size/L'
          l_max_size: 1000
          xl_label: 'size/XL'
          fail_if_xl: false

  conventional-commits:
    runs-on: ubuntu-latest
    steps:
      - uses: amannn/action-semantic-pull-request@v5
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        with:
          types: |
            feat
            fix
            docs
            style
            refactor
            perf
            test
            build
            ci
            chore
            revert
```

## Secrets Management

```yaml
# Required secrets for workflows:
# - KEYSTORE_BASE64: Base64-encoded Android keystore
# - KEYSTORE_PASSWORD: Keystore password
# - KEY_ALIAS: Key alias
# - KEY_PASSWORD: Key password
# - PLAY_STORE_SERVICE_ACCOUNT: Google Play service account JSON

# Encode keystore:
# base64 -i keystore.jks | pbcopy

# Use environment for staging vs production
jobs:
  deploy:
    environment: production  # Requires approval
    steps:
      - name: Deploy
        env:
          API_KEY: ${{ secrets.PROD_API_KEY }}
```

## Performance Tips

```yaml
# 1. Use concurrency to cancel redundant runs
concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

# 2. Fail fast in matrix builds (or set to false for full results)
strategy:
  fail-fast: true

# 3. Use job dependencies to parallelize
jobs:
  lint:
    runs-on: ubuntu-latest
  build:
    needs: lint  # Only start after lint passes
  test:
    needs: build
    
# 4. Separate quick checks from slow operations
# Run validation first, then expensive builds

# 5. Use ubuntu-latest for speed (macOS is slower)
# Only use macOS for iOS-specific tasks

# 6. Archive minimal artifacts
- uses: actions/upload-artifact@v4
  with:
    retention-days: 7  # Don't keep forever
    compression-level: 9  # Maximum compression
```

## References

- GitHub Actions: https://docs.github.com/en/actions
- Gradle Action: https://github.com/gradle/actions
- KMP CI: https://kotlinlang.org/docs/multiplatform-publish-lib.html
