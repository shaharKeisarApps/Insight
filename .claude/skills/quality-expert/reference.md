# Quality Expert API Reference

## Detekt 1.23.8

### Gradle Plugin Configuration

```kotlin
plugins {
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

detekt {
    buildUponDefaultConfig = true          // Inherit default rules, override selectively
    allRules = false                        // Do not enable all rules by default
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    baseline = file("$rootDir/config/detekt/baseline.xml")
    parallel = true                         // Run analysis in parallel
    autoCorrect = false                     // Set true only for local development
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
    reports {
        html.required.set(true)
        xml.required.set(true)              // For CI tools
        sarif.required.set(true)            // For GitHub Code Scanning
    }
}
```

### detekt.yml Structure

The YAML configuration file overrides the default rule set. Only rules you wish to customize need to appear.

```yaml
build:
  maxIssues: 0                             # Zero new issues allowed (use baseline for legacy)

complexity:
  ComplexCondition:
    active: true
    threshold: 4                           # Max boolean operators in a single condition
  ComplexMethod:
    active: true
    threshold: 15                          # Max cyclomatic complexity per method
    ignoreSingleWhenExpression: true
    ignoreSimpleWhenEntries: true
  LargeClass:
    active: true
    threshold: 600                         # Max lines per class
  LongMethod:
    active: true
    threshold: 60                          # Max lines per method
  LongParameterList:
    active: true
    functionThreshold: 6
    constructorThreshold: 8                # Higher for DI constructors
    ignoreDefaultParameters: true
    ignoreDataClasses: true
    ignoreAnnotatedParameter: ["Inject"]
  NestedBlockDepth:
    active: true
    threshold: 4
  TooManyFunctions:
    active: true
    thresholdInFiles: 20
    thresholdInClasses: 15
    thresholdInInterfaces: 10
    thresholdInObjects: 10
    ignoreDeprecated: true
    ignorePrivate: true
    ignoreOverridden: true
```

### Severity Levels

| Level | Meaning | Build Impact |
|-------|---------|-------------|
| `error` | Blocks build when `maxIssues: 0` | Fails CI |
| `warning` | Reported but does not block by default | Fails if `warningsAsErrors = true` |
| `info` | Informational only | Never blocks |

### Baseline Workflow

```bash
# Generate baseline from current violations
./gradlew detektBaseline

# Output: config/detekt/baseline.xml
# Commit to version control
# Only NEW violations will cause failures going forward
```

Never auto-regenerate baselines in CI. The baseline is a deliberate, reviewed artifact.

### Custom Rule Module Structure

```
detekt-rules/
  build.gradle.kts                         # depends on detekt-api:1.23.8
  src/main/kotlin/
    com/example/rules/
      NoGlobalScopeRule.kt                 # Rule implementation
      CustomRuleSetProvider.kt             # Registers rules with detekt
  src/main/resources/META-INF/services/
    io.gitlab.arturbosch.detekt.api.RuleSetProvider  # Service loader file
  src/test/kotlin/
    com/example/rules/
      NoGlobalScopeRuleTest.kt             # Uses detekt-test:1.23.8
```

### Custom Rule API

```kotlin
class MyRule(config: Config = Config.empty) : Rule(config) {
    override val issue = Issue(
        id = javaClass.simpleName,
        severity = Severity.Defect,            // Defect, CodeSmell, Style, Warning, etc.
        description = "Human-readable description",
        debt = Debt.TWENTY_MINS,               // Estimated fix time
    )

    override fun visitReferenceExpression(expression: KtReferenceExpression) {
        super.visitReferenceExpression(expression)
        if (/* violation detected */) {
            report(CodeSmell(issue, Entity.from(expression), "Specific message"))
        }
    }
}
```

### Key Rule Categories

| Category | Example Rules |
|----------|---------------|
| `complexity` | `LongMethod`, `ComplexCondition`, `TooManyFunctions`, `LargeClass` |
| `naming` | `FunctionNaming`, `VariableNaming`, `PackageNaming`, `EnumNaming` |
| `style` | `MagicNumber`, `ReturnCount`, `WildcardImport`, `MaxLineLength` |
| `potential-bugs` | `UnsafeCallOnNullableType`, `UnreachableCode`, `EqualsWithHashCodeExist` |
| `performance` | `SpreadOperator`, `ArrayPrimitive`, `ForEachOnRange` |
| `coroutines` | `GlobalCoroutineUsage`, `RedundantSuspendModifier`, `SleepInsteadOfDelay` |
| `exceptions` | `TooGenericExceptionCaught`, `SwallowedException` |

---

## Spotless 8.2.1

**Requires Java 17+.** Spotless 8.x will fail with cryptic errors on Java 11.

### Gradle Plugin Configuration

```kotlin
plugins {
    id("com.diffplug.spotless") version "8.2.1"
}

spotless {
    ratchetFrom("origin/main")             // Only format files changed since main

    kotlin {
        target("src/**/*.kt")
        targetExclude("**/build/**", "**/generated/**")
        ktlint("1.5.0")
            .editorConfigOverride(
                mapOf(
                    "ktlint_code_style" to "ktlint_official",
                    "max_line_length" to "120",
                    "indent_size" to "4",
                    "insert_final_newline" to "true",
                    "ktlint_standard_no-wildcard-imports" to "disabled",
                    "ktlint_standard_function-naming" to "disabled",      // Composable PascalCase
                    "ktlint_standard_trailing-comma-on-call-site" to "enabled",
                    "ktlint_standard_trailing-comma-on-declaration-site" to "enabled",
                )
            )
        trimTrailingWhitespace()
        endWithNewline()
    }

    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint("1.5.0")
    }
}
```

### Formatter Options

| Formatter | Use Case | Config Method |
|-----------|----------|---------------|
| `ktlint(version)` | ktlint rules via `.editorConfigOverride()` | `editorConfigOverride(map)` |
| `ktfmt(version)` | Google/Meta style formatting | `.googleStyle()`, `.metaStyle()`, `.kotlinlangStyle()` |
| `licenseHeader(header)` | File license headers | `licenseHeader("/* (C) 2026 */")` |

### Commands

```bash
./gradlew spotlessCheck                    # Verify formatting (CI)
./gradlew spotlessApply                    # Auto-fix formatting (local)
```

### License Headers

```kotlin
spotless {
    kotlin {
        licenseHeader("/* Copyright 2026 MyCompany. All rights reserved. */")
    }
}
```

---

## EditorConfig for ktlint

Place at the project root. Spotless reads this automatically when no `editorConfigOverride` is set.

```ini
root = true

[*]
indent_style = space
indent_size = 4
end_of_line = lf
charset = utf-8
trim_trailing_whitespace = true
insert_final_newline = true

[*.{kt,kts}]
max_line_length = 120
ktlint_code_style = ktlint_official
ktlint_standard_no-wildcard-imports = disabled
ktlint_standard_trailing-comma-on-call-site = enabled
ktlint_standard_trailing-comma-on-declaration-site = enabled
ktlint_standard_function-naming = disabled
```

---

## KMP-Specific Lint Considerations

### Source Set Targeting

Detekt and Spotless must cover all source sets. Use glob patterns that include `commonMain`, `androidMain`, `iosMain`, etc.

```kotlin
spotless {
    kotlin {
        target("src/**/*.kt")              // Covers all source sets under src/
        targetExclude("**/build/**")
    }
}
```

### Compose-Specific Detekt Overrides

```yaml
naming:
  FunctionNaming:
    ignoreAnnotated: ["Composable"]        # Composable functions use PascalCase

style:
  MagicNumber:
    ignoreAnnotation: true                 # Compose previews use literal values
```

### Coroutine Rules for KMP

```yaml
coroutines:
  GlobalCoroutineUsage:
    active: true                           # Prevent GlobalScope in shared code
  SuspendFunWithFlowReturnType:
    active: true                           # Suspend functions should not return Flow
```

---

## CI Integration (GitHub Actions)

### Quality Gate Workflow

```yaml
name: Quality Gate
on:
  pull_request:
    branches: [main]

jobs:
  quality:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0                   # Full history for ratchetFrom
      - uses: actions/setup-java@v4
        with:
          distribution: "zulu"
          java-version: "17"               # Required for Spotless 8.x
      - uses: gradle/actions/setup-gradle@v4

      - name: Check formatting
        run: ./gradlew spotlessCheck --no-daemon

      - name: Static analysis
        run: ./gradlew detekt --no-daemon

      - name: Upload SARIF
        uses: github/codeql-action/upload-sarif@v3
        if: always()
        with:
          sarif_file: build/reports/detekt/detekt.sarif
```

### Gate Order (fast to slow)

| Order | Task | Approximate Time |
|-------|------|-----------------|
| 1 | `spotlessCheck` | ~10s |
| 2 | `detekt` | ~30s |
| 3 | `apiCheck` | ~5s |
| 4 | `allTests` | ~60s |
| 5 | `koverVerify` | ~60s |
| 6 | `lint` (Android) | ~90s |

### Aggregated CI Command

```bash
./gradlew spotlessCheck detekt apiCheck allTests koverVerify --continue
```

The `--continue` flag ensures all checks run even if one fails, so developers see every issue in a single pass.

---

## Version Catalog Entries

```toml
[versions]
detekt = "1.23.8"
spotless = "8.2.1"
ktlint = "1.5.0"

[plugins]
detekt = { id = "io.gitlab.arturbosch.detekt", version.ref = "detekt" }
spotless = { id = "com.diffplug.spotless", version.ref = "spotless" }

[libraries]
detekt-api = { module = "io.gitlab.arturbosch.detekt:detekt-api", version.ref = "detekt" }
detekt-test = { module = "io.gitlab.arturbosch.detekt:detekt-test", version.ref = "detekt" }
```
