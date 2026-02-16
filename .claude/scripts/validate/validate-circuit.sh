#!/bin/bash
# Circuit MVI Pattern Validator
# Verifies a KMP project follows Circuit conventions.
#
# Usage: ./validate-circuit.sh [PROJECT_ROOT]
# Exit: 0 = pass, 1 = violations found

set -e
ROOT="${1:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
VIOLATIONS=0

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
log_pass() { echo -e "${GREEN}PASS${NC}: $1"; }
log_fail() { echo -e "${RED}FAIL${NC}: $1"; VIOLATIONS=$((VIOLATIONS + 1)); }
log_warn() { echo -e "${YELLOW}WARN${NC}: $1"; }

echo "Circuit MVI Pattern Validation"
echo "=============================="
echo "Project: $ROOT"
echo ""

# 1. No `remember {}` in Presenter files (use rememberRetained)
echo "Check: No remember {} in Presenter files"
PRESENTERS=$(find "$ROOT" -name "*Presenter*.kt" -not -path "*/build/*" -not -path "*/.gradle/*" 2>/dev/null || true)
BAD_REMEMBER=""
if [[ -n "$PRESENTERS" ]]; then
    for f in $PRESENTERS; do
        # Match `remember {` or `remember(` but not `rememberRetained` or `rememberSaveable`
        if grep -n "remember[[:space:]]*[({]" "$f" 2>/dev/null | grep -v "rememberRetained\|rememberSaveable\|rememberCoroutineScope\|rememberUpdatedState" >/dev/null 2>&1; then
            BAD_REMEMBER="$BAD_REMEMBER$f\n"
        fi
    done
fi
if [[ -n "$BAD_REMEMBER" ]]; then
    log_fail "remember {} found in Presenter files (use rememberRetained):"
    echo -e "$BAD_REMEMBER" | head -5 | while read -r f; do [[ -n "$f" ]] && echo "  $f"; done
else
    log_pass "No remember {} in Presenter files"
fi

# 2. @CircuitInject present on Presenter factories
echo ""
echo "Check: @CircuitInject on Presenter factories"
CI=$(grep -rl "@CircuitInject" --include="*.kt" "$ROOT" 2>/dev/null | grep -v "/build/" || true)
if [[ -z "$CI" ]]; then
    # Only warn if Circuit is actually used
    HAS_CIRCUIT=$(grep -rl "com.slack.circuit" --include="*.kts" --include="*.toml" "$ROOT" 2>/dev/null || true)
    if [[ -n "$HAS_CIRCUIT" ]]; then
        log_warn "@CircuitInject not found (ensure all Presenters have @CircuitInject on AssistedFactory)"
    fi
else
    CI_COUNT=$(echo "$CI" | wc -l | tr -d ' ')
    log_pass "@CircuitInject found in $CI_COUNT file(s)"
fi

# 3. No collectAsState (must use collectAsRetainedState in Circuit)
echo ""
echo "Check: No collectAsState (use collectAsRetainedState)"
BAD_COLLECT=$(grep -rn "collectAsState[^W]" --include="*.kt" "$ROOT" 2>/dev/null | grep -v "/build/" | grep -v "collectAsStateWithLifecycle\|collectAsRetainedState" || true)
if [[ -n "$BAD_COLLECT" ]]; then
    log_fail "collectAsState found (use collectAsRetainedState for Circuit):"
    echo "$BAD_COLLECT" | head -5 | while read -r line; do echo "  $line"; done
else
    log_pass "No bare collectAsState usage"
fi

# 4. No LocalOverlayNavigator (replaced by LocalOverlayHost in Circuit 0.31+)
echo ""
echo "Check: No deprecated LocalOverlayNavigator"
OLD_OVERLAY=$(grep -rn "LocalOverlayNavigator" --include="*.kt" "$ROOT" 2>/dev/null | grep -v "/build/" || true)
if [[ -n "$OLD_OVERLAY" ]]; then
    log_fail "LocalOverlayNavigator found (use LocalOverlayHost.current since Circuit 0.31):"
    echo "$OLD_OVERLAY" | head -3 | while read -r line; do echo "  $line"; done
else
    log_pass "No deprecated LocalOverlayNavigator"
fi

# 5. Screens implement Circuit Screen interface
echo ""
echo "Check: Screen classes implement Circuit Screen"
SCREENS=$(grep -rn "data class.*Screen\b\|data object.*Screen\b" --include="*.kt" "$ROOT" 2>/dev/null | grep -v "/build/" | grep -v "import\|//\|test\|Test" || true)
if [[ -n "$SCREENS" ]]; then
    SCREENS_WITHOUT_CIRCUIT=$(echo "$SCREENS" | grep -v "Circuit\|circuit\|Screen<" || true)
    if [[ -n "$SCREENS_WITHOUT_CIRCUIT" ]]; then
        log_warn "Screen classes that may not implement Circuit Screen<State>:"
        echo "$SCREENS_WITHOUT_CIRCUIT" | head -3 | while read -r line; do echo "  $line"; done
    else
        log_pass "Screen classes implement Circuit Screen"
    fi
fi

echo ""
echo "=============================="
if [[ $VIOLATIONS -gt 0 ]]; then
    echo -e "${RED}$VIOLATIONS violation(s) found${NC}"
    exit 1
else
    echo -e "${GREEN}All Circuit checks passed${NC}"
    exit 0
fi
