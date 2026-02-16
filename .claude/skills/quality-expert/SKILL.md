---
name: quality-expert
description: Elite code quality enforcement for KMP projects. Use for Detekt static analysis, Spotless/ktlint formatting, Android Lint, binary compatibility validation, code coverage with Kover, and CI quality gates.
---

# Quality Expert Skill

## Overview

Code quality in Kotlin Multiplatform demands a layered approach: static analysis catches bugs and code smells, formatting enforcement eliminates style debates, binary compatibility validation prevents accidental API breaks, and code coverage ensures test adequacy. This skill covers the full quality toolchain from local development through CI enforcement.

## When to Use

- **Static Analysis**: Configuring Detekt rules, writing custom rules, managing baselines.
- **Formatting**: Enforcing consistent style with Spotless + ktlint across all source sets.
- **Linting**: Android Lint custom checks for KMP modules with Android targets.
- **API Compatibility**: Preventing binary-incompatible changes with kotlinx-binary-compatibility-validator.
- **Code Coverage**: Measuring and enforcing coverage thresholds with Kover.
- **CI Quality Gates**: Blocking merges when quality standards are not met.

## Quick Reference

See [reference.md](reference.md) for configuration syntax and rule catalogs.
See [examples.md](examples.md) for complete, copy-paste-ready configurations.

## Tool Versions

| Tool | Version | Notes |
|------|---------|-------|
| Detekt | 1.23.8 | Kotlin 2.0 compatible, type-resolution support |
| Spotless | 8.2.1 | **Requires Java 17+** (breaking change from 6.x) |
| ktlint | 1.5.0 | Used via Spotless integration, not standalone |
| Binary Compatibility Validator | 0.17.0 | Gradle plugin for API dump/check |
| Kover | 0.9.1 | JetBrains official KMP coverage tool |

## Detekt (v1.23.8)

### What It Does

Detekt performs static code analysis on Kotlin sources. It detects code smells, complexity violations, naming issues, potential bugs, and style inconsistencies. It supports type resolution for deeper analysis and custom rule authoring.

### Configuration Strategy

1. **Start with defaults**: Use `buildUponDefaultConfig = true` to inherit sane defaults.
2. **Override selectively**: Only customize rules you disagree with in `detekt.yml`.
3. **Use baselines**: Generate a baseline for existing violations, then enforce zero new violations.
4. **Enable type resolution**: Adds cross-file analysis (catches more issues, slower).

### Gradle Setup

```kotlin
plugins {
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    baseline = file("$rootDir/config/detekt/baseline.xml")
    parallel = true
}

// Enable type resolution for deeper analysis
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
    reports {
        html.required.set(true)
        xml.required.set(true)   // For CI integration
        sarif.required.set(true) // For GitHub Code Scanning
    }
}
```

### Baseline Workflow

Baselines let you adopt Detekt on an existing codebase without fixing every legacy issue upfront.

```bash
# Generate baseline from current violations
./gradlew detektBaseline

# This creates config/detekt/baseline.xml
# Commit the baseline to version control
# From now on, only NEW violations cause failures
```

**Rule**: Re-generate the baseline only when intentionally accepting violations. Never auto-regenerate in CI.

### Key Rule Categories

| Category | Purpose | Example Rules |
|----------|---------|---------------|
| `complexity` | Cyclomatic/cognitive complexity | `LongMethod`, `ComplexCondition`, `TooManyFunctions` |
| `naming` | Naming conventions | `FunctionNaming`, `VariableNaming`, `PackageNaming` |
| `style` | Code style | `MagicNumber`, `ReturnCount`, `WildcardImport` |
| `potential-bugs` | Likely bugs | `UnreachableCode`, `UnsafeCallOnNullableType` |
| `performance` | Performance issues | `SpreadOperator`, `ArrayPrimitive` |
| `coroutines` | Coroutine misuse | `GlobalCoroutineUsage`, `RedundantSuspendModifier` |

### Custom Rules

Detekt supports writing project-specific rules. Place custom rules in a separate module.

```
project/
  detekt-rules/
    build.gradle.kts        # Depends on detekt-api
    src/main/kotlin/
      NoGlobalScopeRule.kt   # Custom rule implementation
      CustomRuleSetProvider.kt
```

Register custom rules in the root build:

```kotlin
dependencies {
    detektPlugins(project(":detekt-rules"))
}
```

## Spotless (v8.2.1)

### What It Does

Spotless is a formatting enforcement tool. It does not lint -- it formats. Combined with ktlint, it ensures every Kotlin file in the project follows identical style rules.

### Breaking Change Warning

Spotless 8.x requires **Java 17+**. If your CI or local environment uses Java 11, you must upgrade Java before upgrading Spotless. This is the most common migration failure.

### Gradle Setup

```kotlin
plugins {
    id("com.diffplug.spotless") version "8.2.1"
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        ktlint("1.5.0")
            .editorConfigOverride(
                mapOf(
                    "ktlint_standard_no-wildcard-imports" to "disabled",
                    "ktlint_code_style" to "ktlint_official",
                    "max_line_length" to "120",
                )
            )
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        ktlint("1.5.0")
    }
}
```

### ratchetFrom for Gradual Adoption

If you cannot format the entire codebase at once, use `ratchetFrom` to only enforce formatting on changed files:

```kotlin
spotless {
    ratchetFrom("origin/main")
    // ... rest of config
}
```

This means only files modified since `origin/main` must pass formatting. Over time, more of the codebase becomes formatted.

### Usage

```bash
# Check formatting (CI)
./gradlew spotlessCheck

# Auto-fix formatting (local)
./gradlew spotlessApply
```

## Android Lint in KMP

Android Lint can run on KMP modules that have Android targets. It catches Android-specific issues (missing permissions, deprecated API usage, accessibility problems).

### Configuration

```kotlin
android {
    lint {
        warningsAsErrors = true
        abortOnError = true
        baseline = file("lint-baseline.xml")
        disable += setOf("ObsoleteLintCustomCheck")
        enable += setOf("Interoperability")
    }
}
```

### Custom Lint Checks for KMP

For KMP-specific lint rules (e.g., ensuring `expect`/`actual` declarations follow conventions), create a custom lint check module:

```kotlin
// lint-checks/build.gradle.kts
plugins {
    id("java-library")
    id("kotlin")
}

dependencies {
    compileOnly("com.android.tools.lint:lint-api:31.7.3")
    compileOnly("com.android.tools.lint:lint-checks:31.7.3")
}
```

## Binary Compatibility Validator

### What It Does

`kotlinx-binary-compatibility-validator` dumps the public API of your Kotlin modules to `.api` files. On subsequent builds, it compares the current API against the dump. If the API changed in a binary-incompatible way, the build fails.

### Why It Matters for KMP

KMP libraries are consumed as binary artifacts. An accidental signature change (removing a parameter default, changing a return type) breaks downstream consumers silently. The validator catches this at build time.

### Gradle Setup

```kotlin
plugins {
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.17.0"
}

apiValidation {
    ignoredClasses.add("com.example.internal.InternalClass")
    ignoredPackages.add("com.example.internal")
    nonPublicMarkers.add("com.example.InternalApi")
}
```

### Workflow

```bash
# Dump current API (after intentional changes)
./gradlew apiDump

# Check API compatibility (CI)
./gradlew apiCheck
```

**Rule**: `apiDump` must be a conscious, reviewed action. Never auto-dump in CI. The `.api` files must be committed and reviewed in PRs.

## Code Coverage with Kover

### What It Does

Kover is JetBrains' official code coverage tool for Kotlin. It supports KMP projects and generates coverage reports for `jvm` and `android` targets.

### Gradle Setup

```kotlin
plugins {
    id("org.jetbrains.kotlinx.kover") version "0.9.1"
}

kover {
    reports {
        filters {
            excludes {
                classes("*.di.*", "*.BuildConfig", "*_Factory")
                annotatedBy("Generated")
            }
        }
        verify {
            rule {
                minBound(80) // Enforce 80% minimum coverage
            }
        }
    }
}
```

### Limitation

Kover only measures coverage for JVM-based targets (JVM, Android). Native/iOS coverage requires separate instrumentation (e.g., Xcode coverage tools).

## Quality Gates in CI

### Gate Strategy

Quality gates should be layered and fail fast:

| Gate | Tool | Blocks PR | Speed |
|------|------|-----------|-------|
| 1. Formatting | `spotlessCheck` | Yes | Fast (~10s) |
| 2. Static Analysis | `detekt` | Yes | Medium (~30s) |
| 3. API Compatibility | `apiCheck` | Yes | Fast (~5s) |
| 4. Unit Tests | `allTests` | Yes | Medium (~60s) |
| 5. Coverage | `koverVerify` | Yes | Medium (~60s) |
| 6. Android Lint | `lint` | Yes (warnings as errors) | Slow (~90s) |

### CI Command

```bash
./gradlew spotlessCheck detekt apiCheck allTests koverVerify --continue
```

The `--continue` flag runs all checks even if one fails, so developers see all issues in one pass.

## Best Practices

1. **Gradual Adoption with Baselines**: Use Detekt baselines and Spotless `ratchetFrom` to adopt quality tools on existing codebases without a massive reformatting PR.

2. **Pre-commit Hooks**: Run `spotlessApply` and `detekt` before commits to catch issues locally. Use a Git hook or a tool like `lefthook`.

3. **Fail on New Violations Only**: Configure baselines so CI blocks new violations while allowing tracked legacy issues to be fixed incrementally.

4. **Review .api File Changes**: Treat `.api` file diffs like you treat database migration diffs -- every change is intentional and reviewed.

5. **Separate Formatting from Logic PRs**: If you need to reformat a large section of code, do it in a dedicated PR. Mixing formatting with logic changes makes review impossible.

6. **SARIF Reports for GitHub**: Configure Detekt to output SARIF format and upload to GitHub Code Scanning for inline annotations on PRs.

## Common Pitfalls

1. **Suppressing Without Tracking**: Using `@Suppress("detekt:RuleName")` without a comment explaining why. Always document suppressions.

2. **Not Updating Baselines**: The baseline grows stale if violations are fixed but the baseline is not regenerated. Periodically regenerate and shrink the baseline.

3. **Spotless + Java 11**: Spotless 8.x silently fails or throws cryptic errors on Java 11. Always verify `java -version` in CI.

4. **Auto-dumping .api Files**: Running `apiDump` in CI or in a pre-commit hook defeats the purpose of the validator. The dump must be a deliberate, reviewed action.

5. **Ignoring KMP Source Sets**: Detekt and Spotless must target all source sets (`commonMain`, `androidMain`, `iosMain`, etc.), not just `src/main`. Use `target("**/*.kt")` patterns carefully.

6. **Coverage Threshold Too High Too Early**: Starting with 90% coverage on a new project creates pressure to write low-value tests. Start at 60-70% and increase as the codebase matures.

7. **Detekt Without Type Resolution**: Running Detekt without type resolution misses cross-file issues. Enable it for CI even if it is slower.
