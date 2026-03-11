# Insight — Interview-Readiness Audit & Hardening Plan

> **Goal**: Elevate this project from "working side-project" to "portfolio piece that demonstrates Senior/Staff KMP + Android expertise in an interview setting."
>
> Each section below is a **self-contained prompt** designed for a Claude Code agent teammate. They can be executed in parallel where noted, or sequentially where dependencies exist.

---

## Phase 0 — Critical Fixes (Do First)

### 0A. Revoke Leaked API Key & Scrub Git History

```
PRIORITY: CRITICAL — Do this before anything else.

The OpenAI API key in `local.properties` may have been committed to git history
at some point. Even though `.gitignore` excludes the file now, the key
`sk-proj-opIVp4...` is compromised.

Steps:
1. Go to https://platform.openai.com → API Keys → Revoke the leaked key immediately.
2. Run `git log --all --full-history -- local.properties` to check if the file was
   ever committed. If so, use `git filter-repo` (NOT BFG — it's unmaintained) to
   purge it from all history:
   ```
   pip install git-filter-repo
   git filter-repo --path local.properties --invert-paths
   ```
3. Force-push all branches after rewriting history.
4. Generate a fresh API key and store it only in `local.properties` (never committed).
5. Add a `local.properties.example` template to the repo:
   ```properties
   # Copy this to local.properties and fill in your keys
   OPENAI_API_KEY=your-key-here
   ```
6. Verify `.gitignore` entries: `local.properties`, `*.keystore`, `*.jks`,
   `secrets.properties`, `google-services.json`.
```

### 0B. Okio + Application Refactor (LlamatikAiService & ModelRepositoryImpl)

```
Refactor `LlamatikAiService` and `ModelRepositoryImpl` in `core/ai` to eliminate
the `android.app.Application` dependency and replace all `java.io.File` usage with Okio.

WHY THIS MATTERS FOR INTERVIEW:
- Injecting `Application` into a core module is a DI anti-pattern — it leaks the
  Android framework into layers that should be platform-agnostic.
- `java.io.File` blocks KMP portability and is untestable (can't use FakeFileSystem).
- An interviewer scanning this module will immediately flag both.

WHAT TO CHANGE:

1. Create a Metro `@Qualifier` annotation `@ModelsDir` in `core/common/`:
   ```kotlin
   @Qualifier
   annotation class ModelsDir
   ```

2. Provide the models directory path and FileSystem from the DI graph.
   Create a `@ContributesTo(AppScope::class)` interface in `core/ai`:
   ```kotlin
   @ContributesTo(AppScope::class)
   interface FileSystemModule {
       @Provides @SingleIn(AppScope::class)
       fun provideFileSystem(): FileSystem = FileSystem.SYSTEM

       @Provides @ModelsDir
       fun provideModelsDir(application: Application): Path =
           application.filesDir.toOkioPath() / "models"
   }
   ```

3. Refactor `LlamatikAiService` constructor:
   - Remove: `application: Application`
   - Add: `@ModelsDir private val modelsDir: Path, private val fileSystem: FileSystem`

4. Refactor `ModelRepositoryImpl` constructor the same way.

5. Replace ALL `java.io.File` operations with Okio equivalents:
   - `File(dir, name)` → `dir / name`
   - `file.exists()` → `fileSystem.exists(path)`
   - `file.listFiles()` → `fileSystem.list(path)` or `fileSystem.listOrNull(path)`
   - `file.extension` → `path.name.substringAfterLast('.', "")`
   - `file.absolutePath` → `path.toString()`
   - `file.nameWithoutExtension` → `path.name.substringBeforeLast('.')`
   - `file.length()` → `fileSystem.metadata(path).size ?: 0L`
   - `file.mkdirs()` → `fileSystem.createDirectories(path)`
   - `file.delete()` → `fileSystem.delete(path)`
   - `file.renameTo(target)` → `fileSystem.atomicMove(source, target)`
   - `file.outputStream()` → `fileSystem.sink(path).buffer()`
   - `output.write(buffer, 0, n)` → `sink.write(buffer, 0, n)`

6. At the LlamaBridge boundary, convert: `LlamaBridge.initGenerateModel(path.toString())`

7. Add explicit Okio dependency in `core/ai/build.gradle.kts` if not resolved transitively:
   ```kotlin
   implementation(libs.okio)  // add to version catalog if missing
   ```

8. After refactor: zero imports of `java.io` or `android.app` should remain in `core/ai`.

CONSTRAINTS:
- Do NOT change public API signatures on `AiService`, `ModelRepository`, etc.
- Keep `@SingleIn(AppScope::class)` scoping as-is.
- Ensure the download stream in `ModelRepositoryImpl.startDownload()` works with
  `BufferedSink` — the Ktor `readAvailable(ByteArray)` call writes to a byte array,
  then sink.write(buffer, 0, bytesRead).
```

---

## Phase 1 — Architecture & Code Quality (Parallelizable)

### 1A. Eliminate `java.util.*` Leaks — Use Kotlin-first APIs

```
Audit the entire codebase for `java.util.*` imports that have Kotlin/KMP equivalents.

KNOWN ISSUES:
- `LlamatikAiService.kt` lines 143-148 use `java.util.Currency` and `java.util.Locale`
  for currency symbol resolution. This is Android-only and breaks KMP.

FIX:
- Move currency resolution to a dedicated `CurrencyProvider` interface in `core/common`
  with an Android implementation contributed via `@ContributesBinding`.
- The Android impl can use `java.util.Currency` internally — the point is the
  abstraction lives in the shared layer.

AUDIT SCOPE:
- Search for: `import java.util.`, `import java.text.`, `import java.io.`
- Replace `String.format("%.2f", ...)` with Kotlin's `"%.2f".format(...)` or
  a multiplatform-safe formatting utility.
- Ensure no `java.*` leaks remain in `core/` modules (except `core/database` where
  SQLDelight drivers may need them).
```

### 1B. Add Missing `LICENSE` File

```
The README references Apache 2.0 but no LICENSE file exists at the repo root.

1. Add a standard Apache 2.0 LICENSE file at the project root.
2. Optionally add license headers to source files using a Gradle plugin like
   `com.diffplug.spotless` with a license header template.
3. Verify the license is compatible with all dependencies (Metro, Circuit, SQLDelight,
   Llamatik, Koog are all Apache 2.0 compatible).
```

### 1C. Static Analysis & Formatting Setup

```
The project has NO static analysis or formatting enforcement. For a senior-level
portfolio piece, this is a gap interviewers will notice.

ADD:
1. **`.editorconfig`** at the project root:
   ```ini
   root = true
   [*]
   indent_style = space
   indent_size = 4
   end_of_line = lf
   charset = utf-8
   trim_trailing_whitespace = true
   insert_final_newline = true
   max_line_length = 120
   [*.{yml,yaml}]
   indent_size = 2
   [*.md]
   trim_trailing_whitespace = false
   ```

2. **Detekt** for static analysis:
   - Add `io.gitlab.arturbosch.detekt` plugin to root build.gradle.kts
   - Create `config/detekt/detekt.yml` with sensible defaults
   - Configure: complexity thresholds, naming conventions, empty blocks, magic numbers
   - Suppress known false positives (e.g., Metro `@Inject` constructors)
   - Add `detekt` check to CI pipeline (`ci.yml`)

3. **Spotless or ktfmt** for formatting:
   - Add `com.diffplug.spotless` to root build.gradle.kts
   - Configure `ktfmt(style = "GOOGLE")` or `ktlint()`
   - Add format check to CI pipeline

4. **Explicit API mode** for library modules:
   In convention plugin for `insight.android.library`:
   ```kotlin
   kotlin { explicitApi() }
   ```
   This forces all public declarations to have explicit visibility modifiers — a
   strong signal of intentional API design.
```

### 1D. Error Handling Audit

```
Audit all `catch (_: Exception)` blocks in the codebase. The project has many
silent exception swallowing, which is a red flag in interviews.

SEARCH: `catch (_:` and `catch (e:` where the exception is unused.

FOR EACH OCCURRENCE:
1. Determine if the exception should be logged, propagated, or truly ignored.
2. At minimum, add a `Logger` or `Timber` call so failures are debuggable.
3. NEVER catch `CancellationException` silently — it breaks structured concurrency.
   Check that every `catch (e: Exception)` block re-throws CancellationException:
   ```kotlin
   catch (e: Exception) {
       if (e is CancellationException) throw e
       // handle...
   }
   ```
   Or use `runCatching` with `onFailure` which does NOT catch CancellationException.

4. Consider creating a `Result`-based error type in `core/common` for operations
   that can fail (AI calls, network, database), rather than returning null.

KNOWN LOCATIONS:
- `LlamatikAiService.kt`: lines 82, 133, 147, 308
- `ModelRepositoryImpl.kt`: lines 107, 247, 303
- Check all repository implementations in `core/data`
```

---

## Phase 2 — Testing (Parallelizable)

### 2A. Core Module Test Coverage

```
The project has good presenter-level testing but ZERO tests for core modules
(except `FinancialSummaryTest` in core/model). This is a major interview gap.

ADD TESTS FOR:

1. **`core/data` — Repository tests** (highest priority):
   - `ExpenseRepositoryImpl` — insert, observe, monthly totals, category breakdown
   - `IncomeRepositoryImpl` — same coverage
   - `ChatRepositoryImpl` — message persistence, clearing, ordering
   - `UserSettingsRepositoryImpl` — settings persistence, defaults
   Use in-memory SQLDelight driver + real repository implementations.

2. **`core/ai` — LlamatikAiService tests**:
   - `classifyIntent()` — test all keyword paths (expense vs income vs query)
   - `classifyQuestionType()` — test classification accuracy
   - `cleanResponse()` — test all stop-token patterns
   - `buildFinancialSummary()` — test with mock repository data
   These are pure functions/methods — extract them or test via the class with
   a mock LlamaBridge (or just test the non-native methods directly).

3. **`core/model` — Domain model tests**:
   - Expand `FinancialSummaryTest` with edge cases
   - Test any computed properties or business logic on domain models

4. **`core/database` — Migration tests** (if migrations exist):
   - Test schema creation and any future migrations with SQLDelight's
     `VerifyMigrationTask`

TESTING PATTERNS TO USE:
- Turbine for Flow assertions
- Truth for readable assertions
- Coroutines test for `runTest {}` blocks
- Create shared test fixtures in a `core/testing` module or `testFixtures` source set
```

### 2B. Deduplicate Test Fakes

```
The project has duplicated fake implementations across feature test directories:
- `FakeCategoryRepository` exists in both `app/src/test` AND `feature/expenses/src/test`
- `FakeExpenseRepository` is duplicated the same way
- `FakeUserSettingsRepository` is duplicated across expenses, income, and reports

REFACTOR:
1. Create a `core/testing` module (using `insight.android.library` convention):
   ```kotlin
   // core/testing/build.gradle.kts
   plugins { id("insight.android.library") }
   android.namespace = "com.keisardev.insight.core.testing"
   dependencies {
       implementation(project(":core:data"))
       implementation(project(":core:model"))
       implementation(project(":core:ai"))
       implementation(libs.kotlinx.coroutines.test)
       implementation(libs.circuit.test)
   }
   ```
2. Move ALL fake implementations into `core/testing/src/main/kotlin/.../fakes/`
3. Update all feature modules: `testImplementation(project(":core:testing"))`
4. Delete the duplicated fakes from each feature module
5. Add `TestData.kt` shared test fixtures (sample expenses, incomes, categories)
```

### 2C. Screenshot Test Hygiene

```
Verify all 7 screenshot tests compile and the reference images are committed.

1. Check that `src/screenshotTest/` reference images exist in each feature module.
2. Ensure the `update-screenshots.yml` workflow is functional.
3. Add a screenshot test for the Settings screen if missing (only module with
   screenshotTest dir but verify it has reference images).
4. Consider adding dark theme variants for each screenshot test.
```

---

## Phase 3 — Build & Release Pipeline (Sequential)

### 3A. Harden CI Pipeline

```
The existing CI is solid but has gaps for a senior-level portfolio.

IMPROVE `ci.yml`:

1. **Add Detekt + Spotless checks** (after Phase 1C):
   ```yaml
   - name: Static Analysis
     run: ./gradlew detekt
   - name: Format Check
     run: ./gradlew spotlessCheck
   ```

2. **Add dependency verification**:
   Enable Gradle's dependency verification:
   ```
   ./gradlew --write-verification-metadata sha256
   ```
   Commit `gradle/verification-metadata.xml` — this proves supply-chain awareness.

3. **Add build cache analytics** (optional but impressive):
   ```yaml
   - name: Build with scan
     run: ./gradlew assembleDebug --scan
   ```

4. **Pin action versions** to SHA for supply-chain security:
   ```yaml
   - uses: actions/checkout@<full-sha>  # not just @v4
   ```

5. **Add a `check-api` task** if you add explicit API mode — ensures binary
   compatibility tracking for library modules.
```

### 3B. Release Pipeline Hardening

```
The existing `release.yml` is functional but could be more impressive.

IMPROVE:

1. **Automate version bumping**:
   Currently hardcoded (versionCode=2, versionName="0.1.1").
   Use a Git tag-based versioning scheme:
   ```kotlin
   // app/build.gradle.kts
   val versionTag = providers.exec {
       commandLine("git", "describe", "--tags", "--abbrev=0")
   }.standardOutput.asText.get().trim().removePrefix("v")

   defaultConfig {
       versionCode = providers.exec {
           commandLine("git", "rev-list", "--count", "HEAD")
       }.standardOutput.asText.get().trim().toInt()
       versionName = versionTag
   }
   ```

2. **Generate release notes automatically**:
   Use `git log --oneline v0.1.0..v0.1.1` or parse CHANGELOG.md (already done
   partially — verify it works).

3. **Add APK size tracking**:
   After building, log the APK/AAB size in CI output. Consider adding a size
   budget check (e.g., fail if debug APK exceeds 30MB).

4. **R8 mapping upload to GitHub Release** — already done, verify it works.

5. **Consider adding Firebase App Distribution** or internal testing track upload
   for the release workflow (demonstrates real-world release management).
```

### 3C. Benchmark Module Validation

```
The project has a `benchmark/` module. Verify it's functional and demonstrates
performance awareness.

1. Read benchmark module source files — check if macrobenchmarks cover:
   - App startup (cold + warm)
   - Screen transition latency
   - Database query performance
   - If missing, add at least a startup benchmark

2. Ensure `benchmark/build.gradle.kts` is properly configured with:
   - `managedDevices` or documented physical device requirements
   - Baseline profile generation integration

3. Check if `baselineprofile` generation is set up. If not, add it:
   - This is a STRONG interview signal for Android performance awareness
   - Circuit apps benefit significantly from baseline profiles for Compose
```

---

## Phase 4 — GitHub & Presentation (Parallelizable)

### 4A. README Polish

```
The README is good but can be elevated to portfolio-grade.

IMPROVE:

1. **Add badges** at the top:
   ```markdown
   ![CI](https://github.com/<user>/insight/actions/workflows/ci.yml/badge.svg)
   ![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)
   ![Kotlin](https://img.shields.io/badge/Kotlin-2.3.0-purple.svg)
   ![Android](https://img.shields.io/badge/Min%20SDK-33-green.svg)
   ```

2. **Add an architecture diagram** — replace the text-based module list with a
   Mermaid diagram showing module dependencies and data flow:
   ```mermaid
   graph TD
     app --> feature-expenses
     app --> feature-income
     app --> feature-reports
     app --> feature-ai-chat
     app --> feature-settings
     feature-expenses --> core-data
     feature-expenses --> core-ui
     ...
   ```

3. **Add a "Why These Choices" section** explaining key technical decisions:
   - Why Metro over Dagger/Hilt (compile-time, KMP-ready, Kotlin-first)
   - Why Circuit over MVVM (unidirectional data flow, testability, navigation)
   - Why SQLDelight over Room (KMP, type-safe SQL, compile-time verification)
   - Why dual AI backends (offline-first philosophy, privacy-conscious)

4. **Add a "Testing Strategy" section** showing the test pyramid:
   - Unit tests (presenters, repositories, domain logic)
   - Screenshot tests (UI regression)
   - Benchmark tests (performance baselines)

5. **Ensure screenshots are up to date** with the latest UI.
```

### 4B. GitHub Repository Hygiene

```
Polish the GitHub repository itself for interview presentation.

1. **Issue templates**: Create `.github/ISSUE_TEMPLATE/`:
   - `bug_report.md` — standard bug template
   - `feature_request.md` — feature request template
   This shows process awareness even for a solo project.

2. **PR template**: Create `.github/pull_request_template.md`:
   ```markdown
   ## Summary
   ## Changes
   ## Testing
   ## Screenshots (if UI change)
   ```

3. **Branch protection** (if using GitHub):
   - Require CI to pass before merge
   - Require PR reviews (even if self-reviewed — shows process)

4. **Create meaningful GitHub Issues** for future work:
   - "KMP: Extract core modules to shared KMP targets"
   - "Accessibility: Add content descriptions to all interactive elements"
   - "Performance: Implement pagination for expense/income lists"
   This shows you think ahead and maintain a backlog.

5. **Tag releases properly**:
   - Ensure v0.1.0 and v0.1.1 tags exist
   - GitHub Releases should have changelogs attached

6. **Repository description & topics** on GitHub:
   - Description: "Personal finance Android app — Metro DI, Circuit MVI, SQLDelight, on-device AI"
   - Topics: kotlin, android, compose, metro-di, circuit, sqldelight, llama-cpp, material3
```

### 4C. Git History Quality

```
Review git history for interview readiness.

AUDIT:
1. Run `git log --oneline -50` — check commit messages are meaningful.
   - BAD: "fix", "wip", "stuff", "update"
   - GOOD: "feat(ai): add streaming token support for on-device LLM"
   - If history is messy, consider squashing into logical commits before
     publishing (only if the repo isn't already public).

2. Adopt **Conventional Commits** format going forward:
   ```
   feat(expenses): add swipe-to-delete gesture
   fix(ai-chat): prevent CancellationException crash on screen rotation
   refactor(core/ai): replace java.io.File with Okio FileSystem
   test(expenses): add presenter unit tests for add/edit flow
   ci: add Detekt static analysis to pipeline
   ```

3. Ensure `.gitignore` is comprehensive (already looks good — verify no
   build artifacts are committed).
```

---

## Phase 5 — Advanced Polish (Demonstrates Seniority)

### 5A. Accessibility Audit

```
Accessibility is a senior-level concern that most portfolio projects miss.

AUDIT ALL COMPOSABLES:
1. Search for `Image(`, `Icon(`, `IconButton(` — ensure ALL have `contentDescription`.
2. Search for clickable modifiers — ensure they have semantic labels.
3. Check color contrast ratios in the design system (Material3 dynamic color
   generally handles this, but verify custom colors).
4. Add `Modifier.semantics {}` blocks where needed for screen readers.
5. Test with TalkBack (document in README that you did this).

SPECIFIC CHECKS:
- NavigationSuiteScaffold items — do they have accessibility labels?
- Chart/graph in reports — does it have a text alternative?
- AI chat input — is the send button labeled?
- Expense/income list items — are they properly described?
```

### 5B. Performance & Memory Audit

```
Demonstrate performance awareness — a key senior-level signal.

1. **Compose stability audit**:
   - Run the Compose compiler metrics:
     ```kotlin
     // In app/build.gradle.kts
     composeCompiler {
         reportsDestination = layout.buildDirectory.dir("compose_compiler")
         metricsDestination = layout.buildDirectory.dir("compose_compiler")
     }
     ```
   - Check for unstable classes being passed to Composables
   - Add `@Immutable` or `@Stable` annotations where needed
   - Ensure Circuit state classes are stable (data classes with immutable fields)

2. **Database query performance**:
   - Verify indexes exist on frequently queried columns (date, categoryId)
     — already present in SQLDelight schema, but verify with EXPLAIN QUERY PLAN
   - Check for N+1 query patterns in repositories

3. **Memory leak check**:
   - Verify `LlamaBridge.shutdown()` is called when the app is destroyed
   - Check that `callbackFlow` in `chatStream` properly closes resources
   - Ensure `Mutex` usage doesn't cause deadlocks under rapid screen rotation

4. **Baseline profile** (if not already set up):
   - Add baseline profile generation in the benchmark module
   - This alone can improve startup by 30%+ and is a strong interview talking point
```

### 5C. KMP Readiness Assessment

```
Even though this is Android-only today, demonstrating KMP readiness shows
forward-thinking architecture. This is a MAJOR interview differentiator.

AUDIT for KMP portability of core modules:

1. **core/model** — Should be 100% KMP-ready:
   - Check for any Android imports (should be zero)
   - Uses kotlinx-datetime ✓, kotlinx-serialization ✓
   - Flag any `android.*` or `java.*` imports

2. **core/common** — Should be KMP-ready:
   - `AppScope` is just a marker object — KMP-safe ✓
   - Check for Android dependencies

3. **core/data** — Partially KMP-ready:
   - DataStore with Okio is KMP-ready ✓
   - Repository interfaces should be in a shared module
   - Implementations may need `expect/actual` for platform-specific bits

4. **core/database** — SQLDelight is KMP-ready by design:
   - Schema `.sq` files are cross-platform
   - Only the driver setup needs `expect/actual`

5. **core/ai** — After Okio refactor, evaluate:
   - Llamatik is Android-only (JNI) — needs `expect/actual` abstraction
   - Koog may already be KMP (check their docs)
   - The AI abstraction layer (`AiService` interface) is the right pattern

DOCUMENT findings in a GitHub Issue: "KMP Migration Plan" with a checklist
of what's ready and what needs work. This shows architectural vision.
```

### 5D. Security Hardening

```
Demonstrate security awareness — increasingly important for senior roles.

1. **API Key Management**:
   - Verify `BuildConfig.OPENAI_API_KEY` is only in debug builds
   - Add R8 rules to strip BuildConfig fields from release builds
   - Consider Android Keystore for runtime key storage

2. **Network Security**:
   - Add `network_security_config.xml` if not present:
     ```xml
     <network-security-config>
         <base-config cleartextTrafficPermitted="false" />
     </network-security-config>
     ```
   - Verify all API calls use HTTPS (HuggingFace URLs are HTTPS ✓)

3. **Data at rest**:
   - SQLDelight database is unencrypted — document this as a known limitation
   - Consider SQLCipher for sensitive financial data (or note it in issues)

4. **ProGuard/R8 review**:
   - Current rules look reasonable
   - Add `-assumenosideeffects` for logging in release:
     ```
     -assumenosideeffects class android.util.Log {
         public static int d(...);
         public static int v(...);
     }
     ```

5. **Dependency vulnerability scan**:
   - Add OWASP dependency-check or Snyk to CI
   - Or at minimum, ensure Dependabot alerts are enabled on GitHub
```

---

## Phase 6 — Final Validation Checklist

### 6A. Full Build & Test Verification

```
Run the complete validation suite to ensure everything works together.

Execute IN ORDER:
1. ./gradlew clean
2. ./gradlew spotlessCheck (or ktfmtCheck)
3. ./gradlew detekt
4. ./gradlew testDebugUnitTest
5. ./gradlew lintDebug
6. ./gradlew assembleRelease (verify R8 doesn't break anything)
7. ./gradlew validateDebugScreenshotTest
8. ./gradlew :benchmark:connectedBenchmarkAndroidTest (if device available)

ALL must pass with zero warnings in lint (or documented suppressions).
Document any suppressed warnings with clear justification.
```

### 6B. Interview Talking Points Document

```
Create a `docs/ARCHITECTURE_DECISIONS.md` (ADR-style) documenting:

1. **Why Metro DI over Dagger/Hilt**:
   - Compile-time safety without annotation processing overhead
   - KMP-ready from day one
   - Kotlin-first API (no Java interop baggage)
   - Anvil-style aggregation without separate plugin

2. **Why Circuit over MVVM/MVI libraries**:
   - First-class Compose integration
   - Built-in navigation with type-safe screens
   - Unidirectional data flow enforced by architecture
   - Excellent testability with FakeNavigator

3. **Why SQLDelight over Room**:
   - SQL-first approach — write real SQL, get type-safe Kotlin
   - KMP-ready with platform-specific drivers
   - Compile-time query verification
   - Flow-based reactive queries

4. **Why dual AI (cloud + on-device)**:
   - Privacy-first: financial data stays on device
   - Offline capability for core features
   - Graceful degradation when network unavailable
   - Demonstrates understanding of ML deployment tradeoffs

5. **Why multi-module architecture**:
   - Build time optimization (parallel compilation, incremental builds)
   - Enforced dependency boundaries (features can't depend on each other)
   - Clear ownership and separation of concerns
   - Matches NowInAndroid reference architecture

Each decision should follow ADR format:
- Context (what problem were we solving)
- Decision (what we chose)
- Consequences (tradeoffs accepted)
```

---

## Execution Order Summary

```
PARALLEL GROUP 1 (Critical — Do First):
  ├── 0A: Revoke API key & scrub history
  └── 0B: Okio + Application refactor

PARALLEL GROUP 2 (Code Quality):
  ├── 1A: Eliminate java.util.* leaks
  ├── 1B: Add LICENSE file
  ├── 1C: Static analysis setup (Detekt + Spotless + .editorconfig)
  └── 1D: Error handling audit

PARALLEL GROUP 3 (Testing):
  ├── 2A: Core module test coverage
  ├── 2B: Deduplicate test fakes (core/testing module)
  └── 2C: Screenshot test hygiene

SEQUENTIAL GROUP 4 (Build & Release) — depends on Group 2:
  ├── 3A: Harden CI pipeline
  ├── 3B: Release pipeline hardening
  └── 3C: Benchmark module validation

PARALLEL GROUP 5 (GitHub & Presentation):
  ├── 4A: README polish
  ├── 4B: GitHub repository hygiene
  └── 4C: Git history quality

PARALLEL GROUP 6 (Advanced Polish):
  ├── 5A: Accessibility audit
  ├── 5B: Performance & memory audit
  ├── 5C: KMP readiness assessment
  └── 5D: Security hardening

FINAL (Sequential):
  ├── 6A: Full build & test verification
  └── 6B: Architecture decision records
```

---

*Generated for the Insight project — targeting Senior/Staff KMP + Android interview readiness.*
