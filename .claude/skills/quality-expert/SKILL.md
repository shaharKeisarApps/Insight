---
name: quality-expert
description: Elite code quality expertise for KMP projects. Use when configuring Detekt, ktlint, Spotless, custom lint rules, or enforcing code standards. Triggers on linting setup, code quality configuration, formatting rules, or static analysis questions.
---

# Quality Expert Skill

## Overview

Code quality tools for KMP:
- **Detekt**: Static analysis (complexity, style, bugs)
- **Spotless/ktlint**: Formatting
- **Custom Lint**: Project-specific rules
- **Architecture Rules**: Module boundary enforcement

## Detekt Setup

### Installation

```kotlin
// build.gradle.kts (root)
plugins {
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

// Configure for all subprojects
subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")

    detekt {
        buildUponDefaultConfig = true
        config.setFrom(files("${rootProject.projectDir}/config/detekt/detekt.yml"))
        baseline = file("${rootProject.projectDir}/config/detekt/baseline.xml")

        // KMP support
        source.setFrom(
            "src/commonMain/kotlin",
            "src/androidMain/kotlin",
            "src/iosMain/kotlin",
            "src/desktopMain/kotlin",
        )
    }

    dependencies {
        detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.8")
        detektPlugins("io.gitlab.arturbosch.detekt:detekt-rules-libraries:1.23.8")
        detektPlugins("io.nlopez.compose.rules:detekt:0.4.22") // Compose rules
    }
}
```

### Configuration File

```yaml
# config/detekt/detekt.yml

build:
  maxIssues: 0
  excludeCorrectable: false
  weights:
    complexity: 2
    formatting: 1
    LongParameterList: 1
    style: 1
    comments: 1

config:
  validation: true
  warningsAsErrors: true
  checkExhaustiveness: true

processors:
  active: true
  exclude:
    - 'DetektProgressListener'

console-reports:
  active: true

output-reports:
  active: true
  exclude:
    - 'TxtOutputReport'
    - 'XmlOutputReport'

# Rule sets
complexity:
  active: true
  ComplexCondition:
    active: true
    threshold: 4
  ComplexMethod:
    active: true
    threshold: 15
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
    ignoreAnnotatedParameter: ['Inject', 'Assisted', 'Provides']
  NestedBlockDepth:
    active: true
    threshold: 4
  TooManyFunctions:
    active: true
    thresholdInFiles: 15
    thresholdInClasses: 15
    thresholdInInterfaces: 15
    thresholdInObjects: 15
    thresholdInEnums: 15

coroutines:
  active: true
  GlobalCoroutineUsage:
    active: true
  InjectDispatcher:
    active: true
    dispatcherNames:
      - 'IO'
      - 'Default'
      - 'Unconfined'
  RedundantSuspendModifier:
    active: true
  SleepInsteadOfDelay:
    active: true
  SuspendFunWithCoroutineScopeReceiver:
    active: true
  SuspendFunSwallowedCancellation:
    active: true

empty-blocks:
  active: true
  EmptyCatchBlock:
    active: true
    allowedExceptionNameRegex: '_|(ignore|expected).*'
  EmptyClassBlock:
    active: true
  EmptyDefaultConstructor:
    active: true
  EmptyDoWhileBlock:
    active: true
  EmptyElseBlock:
    active: true
  EmptyFinallyBlock:
    active: true
  EmptyForBlock:
    active: true
  EmptyFunctionBlock:
    active: true
    ignoreOverridden: true
  EmptyIfBlock:
    active: true
  EmptyInitBlock:
    active: true
  EmptyKtFile:
    active: true
  EmptySecondaryConstructor:
    active: true
  EmptyTryBlock:
    active: true
  EmptyWhenBlock:
    active: true
  EmptyWhileBlock:
    active: true

exceptions:
  active: true
  ExceptionRaisedInUnexpectedLocation:
    active: true
    methodNames:
      - 'equals'
      - 'finalize'
      - 'hashCode'
      - 'toString'
  InstanceOfCheckForException:
    active: true
  NotImplementedDeclaration:
    active: true
  ObjectExtendsThrowable:
    active: true
  PrintStackTrace:
    active: true
  RethrowCaughtException:
    active: true
  ReturnFromFinally:
    active: true
  SwallowedException:
    active: true
    ignoredExceptionTypes:
      - 'InterruptedException'
      - 'MalformedURLException'
      - 'NumberFormatException'
      - 'ParseException'
    allowedExceptionNameRegex: '_|(ignore|expected).*'
  ThrowingExceptionFromFinally:
    active: true
  ThrowingExceptionInMain:
    active: false
  ThrowingExceptionsWithoutMessageOrCause:
    active: true
    excludes: ['**/test/**', '**/androidTest/**']
    exceptions:
      - 'ArrayIndexOutOfBoundsException'
      - 'Exception'
      - 'IllegalArgumentException'
      - 'IllegalMonitorStateException'
      - 'IllegalStateException'
      - 'IndexOutOfBoundsException'
      - 'NullPointerException'
      - 'RuntimeException'
      - 'Throwable'
  ThrowingNewInstanceOfSameException:
    active: true
  TooGenericExceptionCaught:
    active: true
    excludes: ['**/test/**', '**/androidTest/**']
    exceptionNames:
      - 'ArrayIndexOutOfBoundsException'
      - 'Error'
      - 'Exception'
      - 'IllegalMonitorStateException'
      - 'IndexOutOfBoundsException'
      - 'NullPointerException'
      - 'RuntimeException'
      - 'Throwable'
    allowedExceptionNameRegex: '_|(ignore|expected).*'
  TooGenericExceptionThrown:
    active: true
    exceptionNames:
      - 'Error'
      - 'Exception'
      - 'RuntimeException'
      - 'Throwable'

naming:
  active: true
  ClassNaming:
    active: true
    classPattern: '[A-Z][a-zA-Z0-9]*'
  ConstructorParameterNaming:
    active: true
    parameterPattern: '[a-z][A-Za-z0-9]*'
    privateParameterPattern: '[a-z][A-Za-z0-9]*'
    excludeClassPattern: '$^'
  EnumNaming:
    active: true
    enumEntryPattern: '[A-Z][_a-zA-Z0-9]*'
  ForbiddenClassName:
    active: false
  FunctionMaxLength:
    active: true
    maximumFunctionNameLength: 50
  FunctionMinLength:
    active: false
  FunctionNaming:
    active: true
    excludes: ['**/test/**', '**/androidTest/**']
    functionPattern: '[a-z][a-zA-Z0-9]*'
    excludeClassPattern: '$^'
    # Allow Compose naming
    ignoreAnnotated: ['Composable']
  FunctionParameterNaming:
    active: true
    parameterPattern: '[a-z][A-Za-z0-9]*'
    excludeClassPattern: '$^'
  InvalidPackageDeclaration:
    active: true
    rootPackage: 'com.app'
  LambdaParameterNaming:
    active: true
    parameterPattern: '[a-z][A-Za-z0-9]*|_'
  MatchingDeclarationName:
    active: true
    mustBeFirst: true
  MemberNameEqualsClassName:
    active: true
    ignoreOverridden: true
  NoNameShadowing:
    active: true
  NonBooleanPropertyPrefixedWithIs:
    active: true
  ObjectPropertyNaming:
    active: true
    constantPattern: '[A-Za-z][_A-Za-z0-9]*'
    propertyPattern: '[A-Za-z][_A-Za-z0-9]*'
    privatePropertyPattern: '[A-Za-z][_A-Za-z0-9]*'
  PackageNaming:
    active: true
    packagePattern: '[a-z]+(\.[a-z][A-Za-z0-9]*)*'
  TopLevelPropertyNaming:
    active: true
    constantPattern: '[A-Z][_A-Z0-9]*'
    propertyPattern: '[A-Za-z][_A-Za-z0-9]*'
    privatePropertyPattern: '_?[A-Za-z][_A-Za-z0-9]*'
  VariableMaxLength:
    active: true
    maximumVariableNameLength: 40
  VariableMinLength:
    active: true
    minimumVariableNameLength: 1
  VariableNaming:
    active: true
    variablePattern: '[a-z][A-Za-z0-9]*'
    privateVariablePattern: '[a-z][A-Za-z0-9]*'
    excludeClassPattern: '$^'

performance:
  active: true
  ArrayPrimitive:
    active: true
  CouldBeSequence:
    active: true
    threshold: 3
  ForEachOnRange:
    active: true
    excludes: ['**/test/**', '**/androidTest/**']
  SpreadOperator:
    active: true
    excludes: ['**/test/**', '**/androidTest/**']
  UnnecessaryPartOfBinaryExpression:
    active: true
  UnnecessaryTemporaryInstantiation:
    active: true

potential-bugs:
  active: true
  AvoidReferentialEquality:
    active: true
  CastToNullableType:
    active: true
  Deprecation:
    active: false
  DontDowncastCollectionTypes:
    active: true
  DoubleMutabilityForCollection:
    active: true
  ElseCaseInsteadOfExhaustiveWhen:
    active: true
  EqualsAlwaysReturnsTrueOrFalse:
    active: true
  EqualsWithHashCodeExist:
    active: true
  ExitOutsideMain:
    active: true
  ExplicitGarbageCollectionCall:
    active: true
  HasPlatformType:
    active: true
  IgnoredReturnValue:
    active: true
    restrictToConfig: true
    returnValueAnnotations:
      - 'CheckResult'
      - 'CheckReturnValue'
    ignoreReturnValueAnnotations:
      - 'CanIgnoreReturnValue'
    returnValueTypes:
      - 'arrow.core.Either'
      - 'arrow.core.Option'
      - 'kotlin.Result'
  ImplicitDefaultLocale:
    active: true
  ImplicitUnitReturnType:
    active: true
  InvalidRange:
    active: true
  IteratorHasNextCallsNextMethod:
    active: true
  IteratorNotThrowingNoSuchElementException:
    active: true
  LateinitUsage:
    active: true
    excludes: ['**/test/**', '**/androidTest/**']
    ignoreAnnotated: ['Inject']
  MapGetWithNotNullAssertionOperator:
    active: true
  MissingPackageDeclaration:
    active: true
  NullCheckOnMutableProperty:
    active: true
  NullableToStringCall:
    active: true
  PropertyUsedBeforeDeclaration:
    active: true
  UnconditionalJumpStatementInLoop:
    active: true
  UnnecessaryNotNullCheck:
    active: true
  UnnecessaryNotNullOperator:
    active: true
  UnnecessarySafeCall:
    active: true
  UnreachableCatchBlock:
    active: true
  UnreachableCode:
    active: true
  UnsafeCallOnNullableType:
    active: true
    excludes: ['**/test/**', '**/androidTest/**']
  UnsafeCast:
    active: true
  UnusedUnaryOperator:
    active: true
  UselessPostfixExpression:
    active: true
  WrongEqualsTypeParameter:
    active: true

style:
  active: true
  AlsoCouldBeApply:
    active: true
  BracesOnIfStatements:
    active: true
    singleLine: 'never'
    multiLine: 'always'
  BracesOnWhenStatements:
    active: true
    singleLine: 'necessary'
    multiLine: 'consistent'
  CanBeNonNullable:
    active: true
  CascadingCallWrapping:
    active: true
    includeElvis: true
  ClassOrdering:
    active: true
  CollapsibleIfStatements:
    active: true
  DataClassContainsFunctions:
    active: false
  DataClassShouldBeImmutable:
    active: true
  DestructuringDeclarationWithTooManyEntries:
    active: true
    maxDestructuringEntries: 4
  EqualsNullCall:
    active: true
  EqualsOnSignatureLine:
    active: true
  ExplicitCollectionElementAccessMethod:
    active: true
  ExplicitItLambdaParameter:
    active: true
  ExpressionBodySyntax:
    active: true
    includeLineWrapping: false
  ForbiddenAnnotation:
    active: true
    annotations:
      - reason: 'Use @Inject instead'
        value: 'javax.inject.Inject'
  ForbiddenComment:
    active: true
    values:
      - reason: 'Remove TODO before merging'
        value: 'TODO:'
      - reason: 'Remove FIXME before merging'
        value: 'FIXME:'
      - reason: 'Remove STOPSHIP before merging'
        value: 'STOPSHIP'
    allowedPatterns: 'TODO\\(\\w+\\):'  # Allow TODO(username):
  ForbiddenImport:
    active: true
    imports:
      - value: 'java.util.stream.*'
        reason: 'Use Kotlin stdlib instead'
  ForbiddenMethodCall:
    active: true
    methods:
      - reason: 'Use println for debug only'
        value: 'kotlin.io.println'
      - reason: 'Use proper logging'
        value: 'java.io.PrintStream.println'
  ForbiddenVoid:
    active: true
    ignoreOverridden: false
    ignoreUsageInGenerics: false
  FunctionOnlyReturningConstant:
    active: true
    ignoreOverridableFunction: true
    ignoreActualFunction: true
    excludedFunctions: []
  LoopWithTooManyJumpStatements:
    active: true
    maxJumpCount: 2
  MagicNumber:
    active: true
    excludes: ['**/test/**', '**/androidTest/**', '**/*Test.kt']
    ignoreNumbers:
      - '-1'
      - '0'
      - '1'
      - '2'
    ignoreHashCodeFunction: true
    ignorePropertyDeclaration: true
    ignoreLocalVariableDeclaration: false
    ignoreConstantDeclaration: true
    ignoreCompanionObjectPropertyDeclaration: true
    ignoreAnnotation: true
    ignoreNamedArgument: true
    ignoreEnums: true
    ignoreRanges: true
    ignoreExtensionFunctions: true
  MandatoryBracesLoops:
    active: true
  MaxChainedCallsOnSameLine:
    active: true
    maxChainedCalls: 5
  MaxLineLength:
    active: true
    maxLineLength: 120
    excludePackageStatements: true
    excludeImportStatements: true
    excludeCommentStatements: true
    excludeRawStrings: true
  MayBeConst:
    active: true
  ModifierOrder:
    active: true
  MultilineLambdaItParameter:
    active: true
  MultilineRawStringIndentation:
    active: true
    indentSize: 4
    trimmingMethod: 'trimIndent'
  NestedClassesVisibility:
    active: true
  NewLineAtEndOfFile:
    active: true
  NoTabs:
    active: true
  NullableBooleanCheck:
    active: true
  ObjectLiteralToLambda:
    active: true
  OptionalAbstractKeyword:
    active: true
  OptionalUnit:
    active: false
  PreferToOverPairSyntax:
    active: true
  ProtectedMemberInFinalClass:
    active: true
  RedundantExplicitType:
    active: true
  RedundantHigherOrderMapUsage:
    active: true
  RedundantVisibilityModifierRule:
    active: true
  ReturnCount:
    active: true
    max: 4
    excludedFunctions:
      - 'equals'
    excludeLabeled: true
    excludeReturnFromLambda: true
    excludeGuardClauses: true
  SafeCast:
    active: true
  SerialVersionUIDInSerializableClass:
    active: true
  SpacingBetweenPackageAndImports:
    active: true
  StringShouldBeRawString:
    active: true
    maxEscapedCharacterCount: 2
    ignoredCharacters: []
  ThrowsCount:
    active: true
    max: 3
    excludeGuardClauses: true
  TrailingWhitespace:
    active: true
  TrimMultilineRawString:
    active: true
    trimmingMethod: 'trimIndent'
  UnderscoresInNumericLiterals:
    active: true
    acceptableLength: 4
    allowNonStandardGrouping: false
  UnnecessaryAbstractClass:
    active: true
  UnnecessaryAnnotationUseSiteTarget:
    active: true
  UnnecessaryApply:
    active: true
  UnnecessaryBackticks:
    active: true
  UnnecessaryBracesAroundTrailingLambda:
    active: true
  UnnecessaryFilter:
    active: true
  UnnecessaryInheritance:
    active: true
  UnnecessaryInnerClass:
    active: true
  UnnecessaryLet:
    active: true
  UnnecessaryParentheses:
    active: true
    allowForUnclearPrecedence: false
  UntilInsteadOfRangeTo:
    active: true
  UnusedImports:
    active: true
  UnusedParameter:
    active: true
    allowedNames: '_|ignored|expected|serialVersionUID'
  UnusedPrivateClass:
    active: true
  UnusedPrivateMember:
    active: true
    allowedNames: '(_|ignored|expected|serialVersionUID)'
  UnusedPrivateProperty:
    active: true
    allowedNames: '_|ignored|expected|serialVersionUID'
  UseAnyOrNoneInsteadOfFind:
    active: true
  UseArrayLiteralsInAnnotations:
    active: true
  UseCheckNotNull:
    active: true
  UseCheckOrError:
    active: true
  UseDataClass:
    active: true
    allowVars: false
  UseEmptyCounterpart:
    active: true
  UseIfEmptyOrIfBlank:
    active: true
  UseIfInsteadOfWhen:
    active: true
    minimumBranches: 2
  UseIsNullOrEmpty:
    active: true
  UseLet:
    active: true
  UseOrEmpty:
    active: true
  UseRequire:
    active: true
  UseRequireNotNull:
    active: true
  UseSumOfInsteadOfFlatMapSize:
    active: true
  UselessCallOnNotNull:
    active: true
  UtilityClassWithPublicConstructor:
    active: true
  VarCouldBeVal:
    active: true
    ignoreLateinitVar: false
  WildcardImport:
    active: true
    excludeImports:
      - 'java.util.*'
      - 'kotlinx.coroutines.*'
```

## Spotless / ktlint Setup

```kotlin
// build.gradle.kts (root)
plugins {
    id("com.diffplug.spotless") version "8.1.0"
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**/*.kt")

        ktlint("1.8.0")
            .setEditorConfigPath("${rootProject.projectDir}/.editorconfig")
            .editorConfigOverride(
                mapOf(
                    "ktlint_standard_filename" to "disabled",
                    "ktlint_standard_function-naming" to "disabled",  // Compose
                    "ktlint_standard_property-naming" to "disabled",  // Constants
                    "ktlint_standard_trailing-comma-on-call-site" to "disabled",
                    "ktlint_standard_trailing-comma-on-declaration-site" to "disabled",
                )
            )

        trimTrailingWhitespace()
        indentWithSpaces(4)
        endWithNewline()
    }

    kotlinGradle {
        target("**/*.gradle.kts")
        ktlint("1.8.0")
    }
}
```

### EditorConfig

```ini
# .editorconfig
root = true

[*]
charset = utf-8
end_of_line = lf
indent_size = 4
indent_style = space
insert_final_newline = true
max_line_length = 120
tab_width = 4
trim_trailing_whitespace = true

[*.{kt,kts}]
ij_kotlin_allow_trailing_comma = true
ij_kotlin_allow_trailing_comma_on_call_site = true
ij_kotlin_imports_layout = *,java.**,javax.**,kotlin.**,^

# Compose
ktlint_function_naming_ignore_when_annotated_with = Composable
```

## Compose Detekt Rules

```yaml
# Additional rules for Compose in detekt.yml

compose:
  active: true
  ComposableAnnotationNaming:
    active: true
  ComposableNaming:
    active: true
  CompositionLocalAllowlist:
    active: true
    allowedCompositionLocals:
      - LocalExtendedColors
      - LocalSpacing
  ContentEmitterReturningValues:
    active: true
  ModifierClickableOrder:
    active: true
  ModifierComposable:
    active: true
  ModifierMissing:
    active: true
  ModifierNotDefaultParam:
    active: true
  ModifierReused:
    active: true
  ModifierWithoutDefault:
    active: true
  MultipleEmitters:
    active: true
  MutableParams:
    active: true
  PreviewNaming:
    active: true
  PreviewPublic:
    active: true
  RememberMissing:
    active: true
  UnstableCollections:
    active: true
  ViewModelForwarding:
    active: true
  ViewModelInjection:
    active: true
```

## Module Boundary Rules

```kotlin
// build-logic/src/main/kotlin/ModuleBoundaryPlugin.kt

class ModuleBoundaryPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        afterEvaluate {
            val modulePath = path
            
            // Define allowed dependencies per layer
            val allowedDependencies = when {
                modulePath.startsWith(":features:") -> setOf(
                    ":core:common",
                    ":core:ui", 
                    ":domain:models",
                    ":domain:usecases",
                )
                modulePath.startsWith(":domain:usecases") -> setOf(
                    ":core:common",
                    ":domain:models",
                    ":data:repositories",
                )
                modulePath.startsWith(":domain:models") -> setOf(
                    ":core:common",
                )
                modulePath.startsWith(":data:") -> setOf(
                    ":core:common",
                    ":core:network",
                    ":core:database",
                    ":domain:models",
                )
                else -> null
            }
            
            allowedDependencies?.let { allowed ->
                configurations.all {
                    resolutionStrategy.eachDependency {
                        if (requested.group == rootProject.name) {
                            val depPath = ":${requested.name}"
                            if (depPath !in allowed && !depPath.startsWith(modulePath)) {
                                throw GradleException(
                                    "Module $modulePath cannot depend on $depPath. " +
                                    "Allowed: $allowed"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
```

## Gradle Quality Tasks

```kotlin
// build.gradle.kts (root)

// Combined quality check
tasks.register("quality") {
    group = "verification"
    description = "Runs all quality checks"
    
    dependsOn("spotlessCheck")
    dependsOn("detekt")
    dependsOn(subprojects.mapNotNull { it.tasks.findByName("lint") })
}

// Auto-fix task
tasks.register("qualityFix") {
    group = "verification"
    description = "Fixes auto-fixable quality issues"
    
    dependsOn("spotlessApply")
}

// Pre-commit hook installer
tasks.register("installGitHooks") {
    group = "setup"
    description = "Installs git hooks"
    
    doLast {
        val hooksDir = file(".git/hooks")
        val preCommit = file("$hooksDir/pre-commit")
        
        preCommit.writeText("""
            #!/bin/bash
            echo "Running quality checks..."
            ./gradlew spotlessCheck detekt --daemon
            
            if [ $? -ne 0 ]; then
                echo "Quality checks failed. Run './gradlew qualityFix' to auto-fix."
                exit 1
            fi
        """.trimIndent())
        
        preCommit.setExecutable(true)
        println("Git hooks installed!")
    }
}
```

## CI Integration

```yaml
# .github/workflows/quality.yml
name: Quality

on:
  pull_request:
  push:
    branches: [main]

jobs:
  quality:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Setup JDK
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      
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
```

## References

- Detekt: https://detekt.dev/
- Detekt Rules: https://detekt.dev/docs/rules/
- ktlint: https://pinterest.github.io/ktlint/
- Spotless: https://github.com/diffplug/spotless
- Compose Rules: https://mrmans0n.github.io/compose-rules/
