# Quality Expert Production Examples

## 1. Full detekt.yml Configuration

```yaml
# config/detekt/detekt.yml
# Tailored for a KMP project with Compose, coroutines, and Metro DI

build:
  maxIssues: 0

complexity:
  ComplexCondition:
    active: true
    threshold: 4
  ComplexMethod:
    active: true
    threshold: 15
    ignoreSingleWhenExpression: true
    ignoreSimpleWhenEntries: true
  LargeClass:
    active: true
    threshold: 600
  LongMethod:
    active: true
    threshold: 60
  LongParameterList:
    active: true
    functionThreshold: 6
    constructorThreshold: 8
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
    ignoreDeprecated: true
    ignorePrivate: true
    ignoreOverridden: true

naming:
  FunctionNaming:
    active: true
    functionPattern: "[a-z][a-zA-Z0-9]*"
    excludeClassPattern: ".*Test"
    ignoreAnnotated: ["Composable"]
  VariableNaming:
    active: true
    variablePattern: "[a-z][a-zA-Z0-9]*"
    privateVariablePattern: "_?[a-z][a-zA-Z0-9]*"
  TopLevelPropertyNaming:
    active: true
    constantPattern: "[A-Z][A-Z_0-9]*"
  PackageNaming:
    active: true
    packagePattern: "[a-z]+(\\.[a-z][a-z0-9]*)*"
  EnumNaming:
    active: true
    enumEntryPattern: "[A-Z][a-zA-Z0-9]*"

style:
  MagicNumber:
    active: true
    ignoreHashCodeFunction: true
    ignorePropertyDeclaration: true
    ignoreAnnotation: true
    ignoreCompanionObjectPropertyDeclaration: true
    ignoreEnums: true
    ignoreNumbers: ["-1", "0", "1", "2"]
  MaxLineLength:
    active: true
    maxLineLength: 120
    excludeCommentStatements: true
    excludePackageStatements: true
    excludeImportStatements: true
  ReturnCount:
    active: true
    max: 3
    excludeGuardClauses: true
  WildcardImport:
    active: true
    excludeImports:
      - "kotlinx.coroutines.*"
      - "kotlinx.coroutines.flow.*"
  ForbiddenComment:
    active: true
    values:
      - reason: "Resolve TODOs before merging"
        value: "TODO:"
      - reason: "FIXME should be resolved"
        value: "FIXME:"
    allowedPatterns: "TODO\\(\\w+\\)"

potential-bugs:
  UnsafeCallOnNullableType:
    active: true
  UnreachableCode:
    active: true
  EqualsWithHashCodeExist:
    active: true

performance:
  SpreadOperator:
    active: true
  ArrayPrimitive:
    active: true

coroutines:
  GlobalCoroutineUsage:
    active: true
  RedundantSuspendModifier:
    active: true
  SleepInsteadOfDelay:
    active: true
  SuspendFunWithFlowReturnType:
    active: true

exceptions:
  TooGenericExceptionCaught:
    active: true
    exceptionNames: ["Exception", "RuntimeException", "Throwable"]
    allowedExceptionNameRegex: "_|(ignored|expected).*"
  SwallowedException:
    active: true
    ignoredExceptionTypes: ["InterruptedException", "CancellationException"]
```

## 2. Spotless build.gradle.kts Configuration

```kotlin
// Root build.gradle.kts
plugins {
    id("com.diffplug.spotless") version "8.2.1"
}

spotless {
    ratchetFrom("origin/main")

    kotlin {
        target("**/*.kt")
        targetExclude(
            "**/build/**",
            "**/generated/**",
            "**/buildSrc/**/.gradle/**",
        )
        ktlint("1.5.0")
            .editorConfigOverride(
                mapOf(
                    "ktlint_code_style" to "ktlint_official",
                    "max_line_length" to "120",
                    "indent_size" to "4",
                    "insert_final_newline" to "true",
                    "ktlint_standard_function-naming" to "disabled",
                    "ktlint_standard_trailing-comma-on-call-site" to "enabled",
                    "ktlint_standard_trailing-comma-on-declaration-site" to "enabled",
                    "ktlint_standard_no-wildcard-imports" to "disabled",
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

## 3. Custom Detekt Rule: No GlobalScope

```kotlin
// detekt-rules/src/main/kotlin/com/example/rules/NoGlobalScopeRule.kt
package com.example.rules

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

class NoGlobalScopeRule(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = javaClass.simpleName,
        severity = Severity.Defect,
        description = "GlobalScope usage leads to unstructured concurrency. " +
            "Use a lifecycle-aware CoroutineScope instead.",
        debt = Debt.TWENTY_MINS,
    )

    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
        super.visitDotQualifiedExpression(expression)
        val receiver = expression.receiverExpression
        if (receiver is KtNameReferenceExpression &&
            receiver.getReferencedName() == "GlobalScope"
        ) {
            report(
                CodeSmell(
                    issue = issue,
                    entity = Entity.from(expression),
                    message = "Replace GlobalScope with a structured CoroutineScope " +
                        "(e.g., viewModelScope, lifecycleScope, or an injected scope).",
                ),
            )
        }
    }
}

// detekt-rules/src/main/kotlin/com/example/rules/CustomRuleSetProvider.kt
package com.example.rules

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

class CustomRuleSetProvider : RuleSetProvider {
    override val ruleSetId: String = "custom-rules"

    override fun instance(config: Config): RuleSet = RuleSet(
        ruleSetId,
        listOf(NoGlobalScopeRule(config)),
    )
}

// detekt-rules/build.gradle.kts
plugins {
    kotlin("jvm")
}

dependencies {
    compileOnly("io.gitlab.arturbosch.detekt:detekt-api:1.23.8")
    testImplementation("io.gitlab.arturbosch.detekt:detekt-test:1.23.8")
    testImplementation(kotlin("test"))
}

// Root build.gradle.kts -- register the custom rules module
dependencies {
    detektPlugins(project(":detekt-rules"))
}

// Service file:
// detekt-rules/src/main/resources/META-INF/services/io.gitlab.arturbosch.detekt.api.RuleSetProvider
// com.example.rules.CustomRuleSetProvider
```

## 4. GitHub Actions CI Step for Quality Checks

```yaml
# .github/workflows/quality.yml
name: Quality Gate

on:
  pull_request:
    branches: [main, develop]
  push:
    branches: [main]

concurrency:
  group: quality-${{ github.ref }}
  cancel-in-progress: true

jobs:
  quality:
    runs-on: ubuntu-latest
    timeout-minutes: 30

    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - uses: actions/setup-java@v4
        with:
          distribution: "zulu"
          java-version: "17"

      - uses: gradle/actions/setup-gradle@v4
        with:
          cache-read-only: ${{ github.ref != 'refs/heads/main' }}

      - name: Check formatting
        run: ./gradlew spotlessCheck --no-daemon

      - name: Run Detekt
        run: ./gradlew detekt --no-daemon

      - name: Run tests with coverage
        run: ./gradlew allTests koverXmlReport koverVerify --no-daemon --continue

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
          files: build/reports/kover/report.xml
          fail_ci_if_error: false

      - name: Upload reports
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: quality-reports
          path: |
            **/build/reports/detekt/
            **/build/reports/kover/
          retention-days: 14
```

## 5. Baseline File Generation

Generate a baseline to adopt Detekt on an existing codebase without fixing every legacy issue upfront.

```bash
# Generate baseline from all current violations
./gradlew detektBaseline

# Output file: config/detekt/baseline.xml
# Commit this file to version control

# From this point forward, only NEW violations cause build failures
# The baseline file tracks existing violations by location + rule ID

# To shrink the baseline after fixing violations:
./gradlew detektBaseline
git diff config/detekt/baseline.xml
# Review the diff, then commit

# Never auto-regenerate in CI -- always a deliberate, reviewed action
```

The generated `baseline.xml` contains entries like:

```xml
<?xml version="1.0" ?>
<SmellBaseline>
    <ManuallySuppressedIssues/>
    <CurrentIssues>
        <ID>LongMethod:UserRepository.kt$UserRepository$fun syncAll()</ID>
        <ID>MagicNumber:Constants.kt$Constants$42</ID>
    </CurrentIssues>
</SmellBaseline>
```
