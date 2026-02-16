#!/bin/bash
# Metro DI Pattern Validator
# Verifies a KMP project follows Metro DI conventions (not Hilt/Dagger/Koin).
#
# Usage: ./validate-metro.sh [PROJECT_ROOT]
# Exit: 0 = pass, 1 = violations found

set -e
ROOT="${1:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
VIOLATIONS=0

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
log_pass() { echo -e "${GREEN}PASS${NC}: $1"; }
log_fail() { echo -e "${RED}FAIL${NC}: $1"; VIOLATIONS=$((VIOLATIONS + 1)); }
log_warn() { echo -e "${YELLOW}WARN${NC}: $1"; }

echo "Metro DI Pattern Validation"
echo "==========================="
echo "Project: $ROOT"
echo ""

# 1. No Hilt/Dagger/Koin imports
echo "Check: No prohibited DI frameworks"
HILT=$(grep -rl "dagger\.hilt\|@HiltViewModel\|@AndroidEntryPoint\|@HiltAndroidApp" --include="*.kt" "$ROOT/src" "$ROOT/app" "$ROOT/feature" "$ROOT/shared" 2>/dev/null || true)
KOIN=$(grep -rl "org\.koin\|inject()\|koinModule\|KoinApplication" --include="*.kt" "$ROOT/src" "$ROOT/app" "$ROOT/feature" "$ROOT/shared" 2>/dev/null || true)
DAGGER=$(grep -rl "dagger\.Component\|dagger\.Module\|@Component\b" --include="*.kt" "$ROOT/src" "$ROOT/app" "$ROOT/feature" "$ROOT/shared" 2>/dev/null | grep -v "Metro\|metro\|DependencyGraph" || true)

if [[ -n "$HILT" ]]; then
    log_fail "Hilt imports found (use Metro instead):"
    echo "$HILT" | head -5 | while read -r f; do echo "  $f"; done
elif [[ -n "$KOIN" ]]; then
    log_fail "Koin imports found (use Metro instead):"
    echo "$KOIN" | head -5 | while read -r f; do echo "  $f"; done
elif [[ -n "$DAGGER" ]]; then
    log_fail "Dagger imports found (use Metro instead):"
    echo "$DAGGER" | head -5 | while read -r f; do echo "  $f"; done
else
    log_pass "No Hilt/Dagger/Koin imports"
fi

# 2. @DependencyGraph present
echo ""
echo "Check: @DependencyGraph defined"
DG=$(grep -rl "@DependencyGraph" --include="*.kt" "$ROOT" 2>/dev/null || true)
if [[ -z "$DG" ]]; then
    log_warn "@DependencyGraph not found (may be auto-generated or project not yet set up)"
else
    DG_COUNT=$(echo "$DG" | wc -l | tr -d ' ')
    log_pass "@DependencyGraph found in $DG_COUNT file(s)"
fi

# 3. Metro plugin in build files
echo ""
echo "Check: Metro Gradle plugin configured"
METRO_PLUGIN=$(grep -rl "dev.zacsweers.metro\|metro(" --include="*.kts" --include="*.gradle" "$ROOT" 2>/dev/null || true)
if [[ -z "$METRO_PLUGIN" ]]; then
    log_warn "Metro plugin not found in build files"
else
    log_pass "Metro plugin configured"
fi

# 4. No @Inject without Metro context (common Dagger leftover)
echo ""
echo "Check: No javax.inject / dagger.Inject"
JAVAX=$(grep -rl "javax\.inject\.Inject\|import dagger\." --include="*.kt" "$ROOT/src" "$ROOT/app" "$ROOT/feature" "$ROOT/shared" 2>/dev/null || true)
if [[ -n "$JAVAX" ]]; then
    log_fail "javax.inject / dagger imports found (use Metro @Inject):"
    echo "$JAVAX" | head -5 | while read -r f; do echo "  $f"; done
else
    log_pass "No javax.inject / dagger imports"
fi

echo ""
echo "==========================="
if [[ $VIOLATIONS -gt 0 ]]; then
    echo -e "${RED}$VIOLATIONS violation(s) found${NC}"
    exit 1
else
    echo -e "${GREEN}All Metro DI checks passed${NC}"
    exit 0
fi
