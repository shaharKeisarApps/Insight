#!/bin/bash
# Store5 Pattern Validator
# Verifies a KMP project follows Store5 best practices.
#
# Usage: ./validate-store5.sh [PROJECT_ROOT]
# Exit: 0 = pass, 1 = violations found

set -e
ROOT="${1:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
VIOLATIONS=0

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
log_pass() { echo -e "${GREEN}PASS${NC}: $1"; }
log_fail() { echo -e "${RED}FAIL${NC}: $1"; VIOLATIONS=$((VIOLATIONS + 1)); }
log_warn() { echo -e "${YELLOW}WARN${NC}: $1"; }

echo "Store5 Pattern Validation"
echo "========================="
echo "Project: $ROOT"
echo ""

# Check if Store5 is in the project
HAS_STORE=$(grep -rl "store5\|mobilenativefoundation.*store" --include="*.toml" --include="*.kts" "$ROOT" 2>/dev/null || true)
if [[ -z "$HAS_STORE" ]]; then
    echo "Store5 not detected in project. Skipping."
    exit 0
fi

# 1. StoreReadResponse, NOT StoreResponse (deprecated name)
echo "Check: Using StoreReadResponse (not deprecated StoreResponse)"
BAD_RESPONSE=$(grep -rn "StoreResponse[^.]" --include="*.kt" "$ROOT" 2>/dev/null | grep -v "/build/" | grep -v "StoreReadResponse\|StoreWriteResponse" || true)
if [[ -n "$BAD_RESPONSE" ]]; then
    log_fail "Deprecated StoreResponse found (use StoreReadResponse):"
    echo "$BAD_RESPONSE" | head -5 | while read -r line; do echo "  $line"; done
else
    log_pass "No deprecated StoreResponse usage"
fi

# 2. SourceOfTruth present (offline-first pattern)
echo ""
echo "Check: SourceOfTruth configured"
SOT=$(grep -rn "SourceOfTruth\|sourceOfTruth" --include="*.kt" "$ROOT" 2>/dev/null | grep -v "/build/" || true)
if [[ -z "$SOT" ]]; then
    log_warn "No SourceOfTruth found (Store5 without SOT is just a network cache)"
else
    SOT_COUNT=$(echo "$SOT" | wc -l | tr -d ' ')
    log_pass "SourceOfTruth found in $SOT_COUNT location(s)"
fi

# 3. Fetcher present
echo ""
echo "Check: Fetcher configured"
FETCHER=$(grep -rn "Fetcher\.\|fetcher(" --include="*.kt" "$ROOT" 2>/dev/null | grep -v "/build/" | grep -v "import" || true)
if [[ -z "$FETCHER" ]]; then
    log_warn "No Fetcher found"
else
    log_pass "Fetcher configured"
fi

# 4. No direct API calls in Presenter/ViewModel (should go through Store)
echo ""
echo "Check: No direct API calls in presentation layer"
PRESENTATION_FILES=$(find "$ROOT" \( -name "*Presenter*.kt" -o -name "*ViewModel*.kt" \) -not -path "*/build/*" -not -path "*/.gradle/*" 2>/dev/null || true)
DIRECT_API=""
if [[ -n "$PRESENTATION_FILES" ]]; then
    for f in $PRESENTATION_FILES; do
        if grep -n "\.get(\|\.post(\|HttpClient\|apiService\.\|api\.\(get\|post\|put\|delete\)" "$f" 2>/dev/null >/dev/null; then
            DIRECT_API="$DIRECT_API$f\n"
        fi
    done
fi
if [[ -n "$DIRECT_API" ]]; then
    log_warn "Possible direct API calls in presentation layer (should use Repository/Store):"
    echo -e "$DIRECT_API" | head -3 | while read -r f; do [[ -n "$f" ]] && echo "  $f"; done
else
    log_pass "No direct API calls in presentation layer"
fi

# 5. StoreReadRequest used for reads (not stream directly)
echo ""
echo "Check: StoreReadRequest usage"
READ_REQ=$(grep -rn "StoreReadRequest\|store\.stream\|store\.fresh\|store\.cached" --include="*.kt" "$ROOT" 2>/dev/null | grep -v "/build/" || true)
if [[ -n "$READ_REQ" ]]; then
    log_pass "Store read patterns found"
else
    log_warn "No StoreReadRequest/stream/fresh/cached calls found"
fi

echo ""
echo "========================="
if [[ $VIOLATIONS -gt 0 ]]; then
    echo -e "${RED}$VIOLATIONS violation(s) found${NC}"
    exit 1
else
    echo -e "${GREEN}All Store5 checks passed${NC}"
    exit 0
fi
