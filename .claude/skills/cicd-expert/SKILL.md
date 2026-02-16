---
name: cicd-expert
description: Complete CI/CD pipeline expertise for KMP projects using GitHub Actions. Use for build pipelines, quality gates, automated testing, signing, release automation, and app store deployment across Android, iOS, and Desktop targets.
---

# CI/CD Expert Skill

## Overview

CI/CD for Kotlin Multiplatform requires orchestrating builds across multiple platforms (Android, iOS, Desktop, Web) on different runner environments, enforcing quality gates that catch issues early, managing platform-specific signing credentials, and automating releases to multiple distribution channels. This skill covers the full pipeline from PR checks through production deployment using GitHub Actions.

## When to Use

- **Build Pipelines**: Setting up CI workflows for KMP projects with multi-platform matrix builds.
- **Quality Gates**: Enforcing formatting, static analysis, tests, and coverage on every PR.
- **Automated Testing**: Running unit, integration, and UI tests across all targets in CI.
- **Release Automation**: Tag-triggered builds with changelog generation and artifact publishing.
- **App Store Deployment**: Automating Android (Google Play) and iOS (App Store Connect) releases.
- **Signing Configuration**: Managing keystores, certificates, and provisioning profiles in CI.
- **Cache Optimization**: Reducing CI build times with aggressive Gradle and Kotlin/Native caching.

## Quick Reference

See [reference.md](reference.md) for workflow syntax, action configurations, and secrets management.
See [examples.md](examples.md) for complete, copy-paste-ready workflow files.

## Build Matrix

| Platform | Runner | Build Command | Notes |
|----------|--------|---------------|-------|
| Android | `ubuntu-latest` | `./gradlew :app:assembleRelease` | Needs JDK 17 |
| iOS | `macos-14` (M1) | `./gradlew :shared:linkReleaseFrameworkIosArm64` | Xcode 15+ |
| Desktop | `ubuntu-latest` | `./gradlew :desktop:packageDistributable` | JDK 17 |
| Web/JS | `ubuntu-latest` | `./gradlew :web:jsBrowserDistribution` | Node.js |

### Runner Cost Awareness

macOS runners cost 10x more than Linux runners on GitHub Actions. Structure your pipelines so that macOS is used only for iOS-specific builds and tests. All other work (quality gates, Android builds, Desktop builds, JS builds) runs on `ubuntu-latest`.

## Quality Gate Pipeline

Quality gates run on every PR and are ordered from fastest to slowest so developers get early feedback.

| Order | Task | Tool | Approximate Time |
|-------|------|------|-----------------|
| 1 | Formatting | `spotlessCheck` | ~10s |
| 2 | Static Analysis | `detekt` | ~30s |
| 3 | API Compatibility | `apiCheck` | ~5s |
| 4 | Unit + Integration Tests | `allTests` | ~60s |
| 5 | Code Coverage | `koverVerify` | ~60s |
| 6 | Android Lint | `lint` | ~90s |

### Aggregated Command

```bash
./gradlew spotlessCheck detekt apiCheck allTests koverVerify lint --continue
```

The `--continue` flag runs all checks even when one fails, so developers see every issue in one CI pass.

## Key Workflow Files

| File | Trigger | Purpose |
|------|---------|---------|
| `.github/workflows/ci.yml` | Pull requests, pushes to main | Quality gates, build verification |
| `.github/workflows/release.yml` | Git tags (`v*`) | Build, sign, publish release artifacts |
| `.github/workflows/nightly.yml` | Scheduled (cron) | Extended test suites, performance benchmarks |

## Secrets Management

### Android Signing

| Secret | Description |
|--------|-------------|
| `KEYSTORE_BASE64` | Base64-encoded `.jks` keystore file |
| `KEY_ALIAS` | Keystore key alias |
| `KEY_PASSWORD` | Keystore key password |
| `STORE_PASSWORD` | Keystore store password |

### iOS Signing

| Secret | Description |
|--------|-------------|
| `APPLE_CERTIFICATE_P12` | Base64-encoded P12 distribution certificate |
| `APPLE_CERTIFICATE_PASSWORD` | P12 certificate password |
| `APPLE_PROVISIONING_PROFILE` | Base64-encoded provisioning profile |
| `APPLE_TEAM_ID` | Apple Developer Team ID |

### General

| Secret | Description |
|--------|-------------|
| `GRADLE_ENCRYPTION_KEY` | Configuration cache encryption key |
| `CODECOV_TOKEN` | Codecov upload token |
| `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` | Google Play Console service account |
| `APP_STORE_CONNECT_API_KEY` | App Store Connect API key (P8) |

## Caching Strategy

Gradle builds in CI benefit enormously from caching. The key caches to maintain are:

1. **Gradle wrapper** -- The Gradle distribution itself.
2. **Gradle dependencies** -- Downloaded JARs from Maven repositories.
3. **Gradle build cache** -- Task output caching for incremental builds.
4. **KSP generated sources** -- Code generation outputs (Metro, Room, etc.).
5. **Kotlin/Native compiler caches** -- Precompiled klibs for native targets.

The `gradle/actions/setup-gradle@v4` action handles caches 1-3 automatically. Kotlin/Native caches require explicit configuration.

## Core Rules

1. **Quality gates run on every PR.** No exceptions. No "skip CI" unless infrastructure is broken.
2. **Use `--continue` flag** to surface all failures, not just the first one.
3. **Cache Gradle aggressively.** Wrapper + dependencies + build cache. Use `cache-read-only` on PRs.
4. **Use macOS runners ONLY for iOS builds.** Everything else runs on Linux to save cost.
5. **Never store secrets in workflow files.** Use GitHub encrypted secrets or OIDC.
6. **Pin action versions to full SHA**, not tags, for supply chain security.
7. **Set `timeout-minutes` on every job.** Prevent runaway builds from consuming resources.
8. **Use concurrency groups** to cancel redundant builds on the same branch.
9. **Fail fast in matrix builds** unless you need all platform results.
10. **Keep workflow files under 200 lines.** Extract reusable steps into composite actions.

## Common Pitfalls

1. **Running iOS builds on every PR.** macOS runners are expensive and slow to provision. Gate iOS builds behind path filters or make them optional status checks.

2. **Not using `fetch-depth: 0`.** Spotless `ratchetFrom` and changelog generation need full git history. Shallow clones break them silently.

3. **Forgetting `--no-daemon` in CI.** The Gradle daemon leaks memory across builds in CI. Always use `--no-daemon` or configure the setup-gradle action to handle this.

4. **Caching build outputs on PRs.** PRs from forks can poison the cache. Use `cache-read-only: true` for PR builds and only write caches on main branch pushes.

5. **Hardcoding JDK version in multiple places.** Define it once (in the workflow env block or a matrix variable) and reference it everywhere.

6. **Not setting `concurrency` groups.** Without them, pushing two commits quickly runs two full CI pipelines instead of canceling the first.

7. **Ignoring Kotlin/Native cache.** Native compilation is the slowest part of KMP builds. Caching `~/.konan` can save 5-10 minutes per build.
