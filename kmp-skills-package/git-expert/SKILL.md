---
name: git-expert
description: Elite Git expertise for KMP development teams. Use when establishing branching strategies, commit conventions, PR workflows, release management, or resolving complex Git scenarios. Triggers on Git workflow design, commit message formatting, branch management, merge strategies, or Git troubleshooting.
---

# Git Expert Skill

## Branching Strategy

### Git Flow (Recommended for KMP)

```
main (production)
  │
  ├── develop (integration)
  │     │
  │     ├── feature/add-user-profile
  │     ├── feature/offline-sync
  │     └── feature/settings-screen
  │
  ├── release/1.2.0
  │
  └── hotfix/critical-crash-fix
```

### Branch Naming

```bash
# Feature branches
feature/add-user-authentication
feature/JIRA-123-user-profile
feature/circuit-migration

# Bug fixes
fix/login-crash-on-rotation
fix/JIRA-456-memory-leak
bugfix/incorrect-date-format

# Releases
release/1.2.0
release/2024.01

# Hotfixes
hotfix/critical-auth-bypass
hotfix/1.2.1

# Experiments
experiment/new-di-framework
spike/compose-performance

# Platform-specific
android/material3-update
ios/swiftui-integration
desktop/menubar-support
```

## Conventional Commits

### Format

```
<type>(<scope>): <subject>

[optional body]

[optional footer(s)]
```

### Types

| Type | Description | Example |
|------|-------------|---------|
| `feat` | New feature | `feat(auth): add biometric login` |
| `fix` | Bug fix | `fix(profile): resolve avatar upload crash` |
| `docs` | Documentation | `docs(readme): update setup instructions` |
| `style` | Formatting | `style: apply ktlint formatting` |
| `refactor` | Code restructure | `refactor(di): migrate to Metro` |
| `perf` | Performance | `perf(list): optimize recomposition` |
| `test` | Testing | `test(presenter): add unit tests` |
| `build` | Build system | `build(gradle): update to Kotlin 2.0` |
| `ci` | CI/CD | `ci: add screenshot test workflow` |
| `chore` | Maintenance | `chore(deps): bump Ktor to 2.3.12` |
| `revert` | Revert commit | `revert: feat(auth): add biometric login` |

### Scopes (KMP Project)

```
# Layer scopes
feat(ui): ...
feat(domain): ...
feat(data): ...

# Module scopes
feat(feature-home): ...
feat(core-network): ...
feat(core-database): ...

# Platform scopes
feat(android): ...
feat(ios): ...
feat(desktop): ...

# Cross-cutting
feat(di): ...
feat(navigation): ...
feat(testing): ...
```

### Examples

```bash
# Feature with body
feat(profile): add avatar upload functionality

Implements image selection from gallery and camera.
Includes compression to reduce upload size.
Uses Coil for image loading.

Closes #123

# Breaking change
feat(api)!: change user response format

BREAKING CHANGE: UserResponse.createdAt is now Instant instead of String.
Migration: Use Instant.parse() for existing String values.

# Bug fix with issue reference
fix(auth): prevent token refresh race condition

Multiple concurrent requests were triggering parallel token refreshes.
Added mutex lock to ensure single refresh at a time.

Fixes #456

# Multi-platform change
feat(android,ios): add haptic feedback on button press

Uses platform-specific vibration APIs.
Android: VibrationEffect
iOS: UIImpactFeedbackGenerator

# Dependencies update
chore(deps): bump dependencies

- Kotlin 2.0.20 -> 2.0.21
- Compose 1.6.0 -> 1.7.0
- Circuit 0.23.0 -> 0.24.0
```

## Commit Best Practices

### Atomic Commits

```bash
# ✅ Good - single logical change
git commit -m "feat(profile): add email validation"
git commit -m "test(profile): add email validation tests"
git commit -m "docs(profile): document email requirements"

# ❌ Bad - multiple unrelated changes
git commit -m "add email validation, fix header, update deps"
```

### Commit Frequency

```bash
# ✅ Good - commit working increments
git commit -m "feat(profile): add ProfileScreen skeleton"
git commit -m "feat(profile): implement ProfilePresenter"
git commit -m "feat(profile): add ProfileUi composables"
git commit -m "test(profile): add presenter tests"

# ❌ Bad - giant commits
git commit -m "feat(profile): add entire profile feature"
```

## PR Workflow

### PR Template

```markdown
<!-- .github/PULL_REQUEST_TEMPLATE.md -->

## Summary
<!-- Brief description of changes -->

## Type of Change
- [ ] 🚀 Feature
- [ ] 🐛 Bug fix
- [ ] 📝 Documentation
- [ ] 🔧 Refactoring
- [ ] ⚡ Performance
- [ ] 🧪 Tests
- [ ] 🔨 Build/CI

## Changes
<!-- Detailed list of changes -->
- 
- 
- 

## Screenshots/Videos
<!-- If UI changes, add before/after -->

## Testing
<!-- How was this tested? -->
- [ ] Unit tests added/updated
- [ ] Screenshot tests added/updated
- [ ] Manual testing on Android
- [ ] Manual testing on iOS
- [ ] Manual testing on Desktop

## Checklist
- [ ] Code follows project style guidelines
- [ ] Self-review completed
- [ ] Documentation updated (if needed)
- [ ] No new warnings introduced
- [ ] Tests pass locally

## Related Issues
<!-- Link related issues -->
Closes #
Related to #
```

### PR Size Guidelines

| Size | Lines Changed | Review Time |
|------|---------------|-------------|
| XS | < 50 | 5-10 min |
| S | 50-200 | 15-30 min |
| M | 200-500 | 30-60 min |
| L | 500-1000 | 1-2 hours |
| XL | > 1000 | Split it! |

### Stacked PRs (Large Features)

```bash
# Base feature branch
git checkout -b feature/user-profile

# Stack 1: Domain layer
git checkout -b feature/user-profile-domain
# ... make changes
git push -u origin feature/user-profile-domain
# Create PR: feature/user-profile-domain -> feature/user-profile

# Stack 2: Data layer (based on stack 1)
git checkout -b feature/user-profile-data
# ... make changes
git push -u origin feature/user-profile-data
# Create PR: feature/user-profile-data -> feature/user-profile-domain

# Stack 3: UI layer (based on stack 2)
git checkout -b feature/user-profile-ui
# ... make changes
# Create PR: feature/user-profile-ui -> feature/user-profile-data
```

## Merge Strategies

### Squash Merge (Recommended for Features)

```bash
# On GitHub: "Squash and merge"
# Results in single commit on target branch

# Commit message format:
feat(profile): add user profile feature (#123)

* Add ProfileScreen and ProfilePresenter
* Implement UserRepository
* Add unit and screenshot tests
```

### Rebase Merge (For Clean History)

```bash
# Before merging, rebase on latest
git checkout feature/my-feature
git rebase develop

# Resolve conflicts if any
git rebase --continue

# Force push (only for your own branches!)
git push --force-with-lease

# Then merge (fast-forward)
git checkout develop
git merge feature/my-feature
```

### Merge Commit (For Releases)

```bash
# Release branches merge with commit
git checkout main
git merge --no-ff release/1.2.0 -m "Release 1.2.0"
git tag -a v1.2.0 -m "Version 1.2.0"
git push origin main --tags
```

## Release Management

### Semantic Versioning

```
MAJOR.MINOR.PATCH

1.0.0 - Initial release
1.1.0 - New feature (backwards compatible)
1.1.1 - Bug fix
2.0.0 - Breaking change
```

### Release Process

```bash
# 1. Create release branch
git checkout develop
git checkout -b release/1.2.0

# 2. Update version
# Update version in gradle.properties, Info.plist, etc.

# 3. Final testing and fixes
git commit -m "chore: bump version to 1.2.0"
git commit -m "fix: last-minute bug fix"

# 4. Merge to main
git checkout main
git merge --no-ff release/1.2.0

# 5. Tag release
git tag -a v1.2.0 -m "Release 1.2.0"

# 6. Merge back to develop
git checkout develop
git merge --no-ff release/1.2.0

# 7. Push everything
git push origin main develop --tags

# 8. Delete release branch
git branch -d release/1.2.0
git push origin --delete release/1.2.0
```

### Hotfix Process

```bash
# 1. Create hotfix from main
git checkout main
git checkout -b hotfix/1.2.1

# 2. Fix the issue
git commit -m "fix(auth): resolve critical security vulnerability"

# 3. Bump version
git commit -m "chore: bump version to 1.2.1"

# 4. Merge to main
git checkout main
git merge --no-ff hotfix/1.2.1
git tag -a v1.2.1 -m "Hotfix 1.2.1"

# 5. Merge to develop
git checkout develop
git merge --no-ff hotfix/1.2.1

# 6. Push and cleanup
git push origin main develop --tags
git branch -d hotfix/1.2.1
```

## Git Hooks

### Pre-commit Hook

```bash
#!/bin/sh
# .git/hooks/pre-commit

echo "Running pre-commit checks..."

# Format check
./gradlew spotlessCheck --quiet
if [ $? -ne 0 ]; then
    echo "❌ Formatting issues found. Run: ./gradlew spotlessApply"
    exit 1
fi

# Lint check
./gradlew detekt --quiet
if [ $? -ne 0 ]; then
    echo "❌ Lint issues found. Check detekt report."
    exit 1
fi

echo "✅ Pre-commit checks passed"
```

### Commit-msg Hook (Conventional Commits)

```bash
#!/bin/sh
# .git/hooks/commit-msg

commit_regex='^(feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert)(\(.+\))?(!)?: .{1,72}'

if ! grep -qE "$commit_regex" "$1"; then
    echo "❌ Invalid commit message format."
    echo ""
    echo "Expected: <type>(<scope>): <subject>"
    echo "Example:  feat(auth): add biometric login"
    echo ""
    echo "Types: feat, fix, docs, style, refactor, perf, test, build, ci, chore, revert"
    exit 1
fi
```

### Setup with Husky (Node.js)

```json
// package.json
{
  "husky": {
    "hooks": {
      "pre-commit": "./gradlew spotlessCheck detekt",
      "commit-msg": "commitlint -E HUSKY_GIT_PARAMS"
    }
  }
}
```

## Common Scenarios

### Undo Last Commit (Keep Changes)

```bash
git reset --soft HEAD~1
```

### Undo Last Commit (Discard Changes)

```bash
git reset --hard HEAD~1
```

### Amend Last Commit

```bash
# Change message
git commit --amend -m "new message"

# Add forgotten files
git add forgotten_file.kt
git commit --amend --no-edit
```

### Interactive Rebase (Clean Up History)

```bash
# Squash last 3 commits
git rebase -i HEAD~3

# In editor:
pick abc1234 First commit
squash def5678 Second commit
squash ghi9012 Third commit
```

### Cherry-pick

```bash
# Apply specific commit to current branch
git cherry-pick abc1234

# Cherry-pick without committing
git cherry-pick --no-commit abc1234
```

### Resolve Merge Conflicts

```bash
# During merge/rebase
git status  # See conflicted files

# Edit files to resolve conflicts
# Look for: <<<<<<<, =======, >>>>>>>

# Mark as resolved
git add resolved_file.kt

# Continue
git merge --continue
# or
git rebase --continue
```

### Stash Changes

```bash
# Stash with message
git stash push -m "WIP: profile feature"

# List stashes
git stash list

# Apply and keep stash
git stash apply stash@{0}

# Apply and remove stash
git stash pop

# Apply specific stash
git stash apply stash@{2}
```

## .gitignore for KMP

```gitignore
# Gradle
.gradle/
build/
!gradle/wrapper/gradle-wrapper.jar

# IDE
.idea/
*.iml
.DS_Store

# Kotlin
*.class
*.kotlin_module

# Android
local.properties
*.apk
*.aab

# iOS
*.xcuserdata
*.xcworkspace
Pods/
DerivedData/

# Desktop
*.jks
*.dmg
*.msi
*.deb

# Generated
generated/
**/generated/
*.g.dart

# Secrets
*.keystore
*.jks
secrets.properties
google-services.json
GoogleService-Info.plist

# Test outputs
test-results/
screenshots/
paparazzi/out/

# Coverage
coverage/
*.exec
*.ec
```

## References

- Conventional Commits: https://www.conventionalcommits.org/
- Git Flow: https://nvie.com/posts/a-successful-git-branching-model/
- Semantic Versioning: https://semver.org/
- GitHub Flow: https://docs.github.com/en/get-started/quickstart/github-flow
