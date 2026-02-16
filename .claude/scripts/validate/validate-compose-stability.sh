#!/bin/bash
# Compose Stability Pattern Validator
# Verifies Compose best practices for performance and correctness.
#
# Usage: ./validate-compose-stability.sh [PROJECT_ROOT]
# Exit: 0 = pass, 1 = violations found

set -e
ROOT="${1:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
VIOLATIONS=0

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
log_pass() { echo -e "${GREEN}PASS${NC}: $1"; }
log_fail() { echo -e "${RED}FAIL${NC}: $1"; VIOLATIONS=$((VIOLATIONS + 1)); }
log_warn() { echo -e "${YELLOW}WARN${NC}: $1"; }

echo "Compose Stability Pattern Validation"
echo "====================================="
echo "Project: $ROOT"
echo ""

# 1. No collectAsState (must use collectAsRetainedState or collectAsStateWithLifecycle)
echo "Check: No bare collectAsState"
BAD_COLLECT=$(grep -rn "\.collectAsState(" --include="*.kt" "$ROOT" 2>/dev/null | grep -v "/build/" | grep -v "collectAsStateWithLifecycle\|collectAsRetainedState" || true)
if [[ -n "$BAD_COLLECT" ]]; then
    log_fail "collectAsState() found (use collectAsRetainedState for Circuit or collectAsStateWithLifecycle for ViewModel):"
    echo "$BAD_COLLECT" | head -5 | while read -r line; do echo "  $line"; done
else
    log_pass "No bare collectAsState usage"
fi

# 2. No GlobalScope
echo ""
echo "Check: No GlobalScope"
GLOBAL=$(grep -rn "GlobalScope" --include="*.kt" "$ROOT" 2>/dev/null | grep -v "/build/" | grep -v "import" || true)
if [[ -n "$GLOBAL" ]]; then
    log_fail "GlobalScope found (use viewModelScope, rememberCoroutineScope, or structured concurrency):"
    echo "$GLOBAL" | head -5 | while read -r line; do echo "  $line"; done
else
    log_pass "No GlobalScope usage"
fi

# 3. @Stable / @Immutable annotations on state classes
echo ""
echo "Check: Stability annotations on state classes"
STATE_CLASSES=$(grep -rn "data class.*State\b\|sealed.*State\b" --include="*.kt" "$ROOT" 2>/dev/null | grep -v "/build/" | grep -v "import\|test\|Test" || true)
if [[ -n "$STATE_CLASSES" ]]; then
    ANNOTATED=$(grep -B1 "@Stable\|@Immutable" --include="*.kt" "$ROOT" 2>/dev/null | grep "State" || true)
    if [[ -z "$ANNOTATED" ]]; then
        log_warn "State classes found but none have @Stable/@Immutable annotations (consider adding for recomposition performance)"
    else
        log_pass "Stability annotations found on state classes"
    fi
fi

# 4. WhileSubscribed(5000) for stateIn (not Eagerly/Lazily without timeout)
echo ""
echo "Check: stateIn with WhileSubscribed timeout"
STATEIN=$(grep -rn "\.stateIn(" --include="*.kt" "$ROOT" 2>/dev/null | grep -v "/build/" || true)
if [[ -n "$STATEIN" ]]; then
    BAD_STATEIN=$(echo "$STATEIN" | grep -i "SharingStarted\.Eagerly\|SharingStarted\.Lazily" || true)
    GOOD_STATEIN=$(echo "$STATEIN" | grep "WhileSubscribed" || true)
    if [[ -n "$BAD_STATEIN" && -z "$GOOD_STATEIN" ]]; then
        log_warn "stateIn uses Eagerly/Lazily without WhileSubscribed (prefer WhileSubscribed(5000) for lifecycle awareness):"
        echo "$BAD_STATEIN" | head -3 | while read -r line; do echo "  $line"; done
    else
        log_pass "stateIn uses WhileSubscribed pattern"
    fi
fi

# 5. No mutableStateListOf / mutableStateMapOf in ViewModels (use StateFlow instead)
echo ""
echo "Check: No Compose mutable state in ViewModels"
VM_FILES=$(find "$ROOT" -name "*ViewModel*.kt" -not -path "*/build/*" -not -path "*/.gradle/*" 2>/dev/null || true)
BAD_STATE=""
if [[ -n "$VM_FILES" ]]; then
    for f in $VM_FILES; do
        if grep -n "mutableStateOf\|mutableStateListOf\|mutableStateMapOf" "$f" 2>/dev/null >/dev/null; then
            BAD_STATE="$BAD_STATE$f\n"
        fi
    done
fi
if [[ -n "$BAD_STATE" ]]; then
    log_warn "Compose mutable state in ViewModel (prefer MutableStateFlow for ViewModel, mutableStateOf is for Compose UI):"
    echo -e "$BAD_STATE" | head -3 | while read -r f; do [[ -n "$f" ]] && echo "  $f"; done
else
    log_pass "No Compose mutable state in ViewModels"
fi

# 6. LazyColumn/LazyRow use key parameter
echo ""
echo "Check: LazyColumn/LazyRow items use key parameter"
LAZY=$(grep -rn "items(" --include="*.kt" "$ROOT" 2>/dev/null | grep -v "/build/" | grep -v "import\|test\|Test" || true)
if [[ -n "$LAZY" ]]; then
    NO_KEY=$(echo "$LAZY" | grep -v "key\s*=" || true)
    if [[ -n "$NO_KEY" ]]; then
        log_warn "LazyColumn/LazyRow items() without key parameter (add key for stable recomposition):"
        echo "$NO_KEY" | head -3 | while read -r line; do echo "  $line"; done
    else
        log_pass "LazyColumn/LazyRow items use key parameter"
    fi
fi

echo ""
echo "====================================="
if [[ $VIOLATIONS -gt 0 ]]; then
    echo -e "${RED}$VIOLATIONS violation(s) found${NC}"
    exit 1
else
    echo -e "${GREEN}All Compose stability checks passed${NC}"
    exit 0
fi
