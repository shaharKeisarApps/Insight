#!/bin/bash
#
# KMP Dependency Version Checker (Portable)
# Checks Maven Central for latest versions of KMP ecosystem dependencies.
# Auto-detects dependencies from gradle/libs.versions.toml.
#
# Usage: ./check-dependencies.sh [OPTIONS]
#
# Options:
#   --no-cache           Bypass cache and fetch fresh data
#   --include-prerelease Include alpha/beta/rc versions
#   --json               Output as JSON
#   --llm                Output optimized for LLM consumption (markdown)
#   --action-plan        Generate step-by-step update plan
#   --quiet              Only show updates available
#   --color              Force colored output
#   --no-color           Disable colored output
#   --help               Show this help message
#
# Exit codes:
#   0 - All dependencies up-to-date
#   1 - Updates available
#   2 - Error occurred
#
# Requirements: curl, jq

set -euo pipefail

# Configuration
REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
TOML_FILE="$REPO_ROOT/gradle/libs.versions.toml"
CACHE_DIR="$REPO_ROOT/.gradle/dependency-cache"
CACHE_DURATION=3600  # 1 hour

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'
BOLD='\033[1m'
DIM='\033[2m'

# Options
USE_CACHE=true
INCLUDE_PRERELEASE=false
OUTPUT_JSON=false
OUTPUT_LLM=false
ACTION_PLAN=false
QUIET_MODE=false
FORCE_COLOR=""
SKILL_VERSIONS=false

# Counters
TOTAL_DEPS=0
UP_TO_DATE=0
UPDATES_AVAILABLE=0
ERRORS=0

# Results storage (pipe-delimited)
MAJOR_UPDATES=""
MINOR_UPDATES=""
PATCH_UPDATES=""
UPTODATE_DEPS=""
ERROR_DEPS=""

# === KMP Ecosystem: version_key -> group|artifact|display_name|category|changelog_url ===
# This mapping covers the standard KMP ecosystem. If a version key from libs.versions.toml
# matches one of these, we know which Maven artifact to check.
declare -A KMP_REGISTRY
KMP_REGISTRY=(
  # Build Tools
  ["kotlin"]="org.jetbrains.kotlin|kotlin-gradle-plugin|Kotlin|Build Tools|https://kotlinlang.org/docs/whatsnew.html"
  ["ksp"]="com.google.devtools.ksp|symbol-processing-gradle-plugin|KSP|Build Tools|https://github.com/google/ksp/releases"
  ["agp"]="com.android.tools.build|gradle|Android Gradle Plugin|Build Tools|https://developer.android.com/build/releases/gradle-plugin"
  # UI Framework
  ["composeMultiplatform"]="org.jetbrains.compose|compose-gradle-plugin|Compose Multiplatform|UI Framework|https://github.com/JetBrains/compose-multiplatform/releases"
  ["compose-multiplatform"]="org.jetbrains.compose|compose-gradle-plugin|Compose Multiplatform|UI Framework|https://github.com/JetBrains/compose-multiplatform/releases"
  ["compose"]="org.jetbrains.compose|compose-gradle-plugin|Compose Multiplatform|UI Framework|https://github.com/JetBrains/compose-multiplatform/releases"
  # DI
  ["metro"]="dev.zacsweers.metro|runtime|Metro DI|Dependency Injection|https://zacsweers.github.io/metro/changelog/"
  # Architecture
  ["circuit"]="com.slack.circuit|circuit-foundation|Circuit MVI|Architecture|https://slackhq.github.io/circuit/changelog/"
  # Data
  ["sqldelight"]="app.cash.sqldelight|gradle-plugin|SQLDelight|Database|https://cashapp.github.io/sqldelight/2.0.0/changelog/"
  ["room"]="androidx.room|room-runtime|Room KMP|Database|https://developer.android.com/jetpack/androidx/releases/room"
  ["room-kmp"]="androidx.room|room-runtime|Room KMP|Database|https://developer.android.com/jetpack/androidx/releases/room"
  ["datastore"]="androidx.datastore|datastore-preferences|DataStore|Data|https://developer.android.com/jetpack/androidx/releases/datastore"
  # Networking
  ["ktor"]="io.ktor|ktor-client-core|Ktor|Networking|https://ktor.io/changelog/"
  ["store5"]="org.mobilenativefoundation.store|store5|Store5|Data Layer|https://github.com/MobileNativeFoundation/Store/releases"
  ["store"]="org.mobilenativefoundation.store|store5|Store5|Data Layer|https://github.com/MobileNativeFoundation/Store/releases"
  # Serialization
  ["kotlinx-serialization"]="org.jetbrains.kotlinx|kotlinx-serialization-json|Kotlinx Serialization|Serialization|https://github.com/Kotlin/kotlinx.serialization/releases"
  ["kotlinxSerialization"]="org.jetbrains.kotlinx|kotlinx-serialization-json|Kotlinx Serialization|Serialization|https://github.com/Kotlin/kotlinx.serialization/releases"
  # Utilities
  ["kotlinx-datetime"]="org.jetbrains.kotlinx|kotlinx-datetime|Kotlinx DateTime|Utilities|https://github.com/Kotlin/kotlinx-datetime/releases"
  ["kotlinxDatetime"]="org.jetbrains.kotlinx|kotlinx-datetime|Kotlinx DateTime|Utilities|https://github.com/Kotlin/kotlinx-datetime/releases"
  ["kotlinx-coroutines"]="org.jetbrains.kotlinx|kotlinx-coroutines-core|Kotlinx Coroutines|Async|https://github.com/Kotlin/kotlinx.coroutines/releases"
  ["kotlinxCoroutines"]="org.jetbrains.kotlinx|kotlinx-coroutines-core|Kotlinx Coroutines|Async|https://github.com/Kotlin/kotlinx.coroutines/releases"
  ["coroutines"]="org.jetbrains.kotlinx|kotlinx-coroutines-core|Kotlinx Coroutines|Async|https://github.com/Kotlin/kotlinx.coroutines/releases"
  # Image Loading
  ["coil"]="io.coil-kt.coil3|coil-compose|Coil|Image Loading|https://coil-kt.github.io/coil/changelog/"
  ["coil3"]="io.coil-kt.coil3|coil-compose|Coil 3|Image Loading|https://coil-kt.github.io/coil/changelog/"
  # Testing
  ["turbine"]="app.cash.turbine|turbine|Turbine|Testing|https://github.com/cashapp/turbine/releases"
  ["paparazzi"]="app.cash.paparazzi|paparazzi-gradle-plugin|Paparazzi|Testing|https://github.com/cashapp/paparazzi/releases"
  ["roborazzi"]="io.github.takahirom.roborazzi|roborazzi|Roborazzi|Testing|https://github.com/takahirom/roborazzi/releases"
  # AndroidX
  ["androidx-lifecycle"]="org.jetbrains.androidx.lifecycle|lifecycle-viewmodel-compose|AndroidX Lifecycle|AndroidX|https://developer.android.com/jetpack/androidx/releases/lifecycle"
  ["androidxLifecycle"]="org.jetbrains.androidx.lifecycle|lifecycle-viewmodel-compose|AndroidX Lifecycle|AndroidX|https://developer.android.com/jetpack/androidx/releases/lifecycle"
  ["androidx-navigation"]="org.jetbrains.androidx.navigation|navigation-compose|AndroidX Navigation|AndroidX|https://developer.android.com/jetpack/androidx/releases/navigation"
  # Logging
  ["kermit"]="co.touchlab|kermit|Kermit|Logging|https://github.com/touchlab/Kermit/releases"
  # iOS Interop
  ["skie"]="co.touchlab.skie|gradle-plugin|SKIE|iOS Interop|https://skie.touchlab.co/changelog"
  # Code Quality
  ["detekt"]="io.gitlab.arturbosch.detekt|detekt-gradle-plugin|Detekt|Quality|https://detekt.dev/changelog"
  ["spotless"]="com.diffplug.spotless|spotless-plugin-gradle|Spotless|Quality|https://github.com/diffplug/spotless/releases"
  # Molecule
  ["molecule"]="app.cash.molecule|molecule-runtime|Molecule|Architecture|https://github.com/cashapp/molecule/releases"
)

print_usage() {
    cat << 'EOF'
KMP Dependency Version Checker (Portable)

Usage: check-dependencies.sh [OPTIONS]

Options:
    --no-cache           Bypass cache and fetch fresh data
    --include-prerelease Include alpha/beta/rc versions
    --json               Output as JSON
    --llm                Output optimized for LLM consumption (markdown)
    --action-plan        Generate step-by-step update plan
    --quiet              Only show updates available
    --skill-versions     Also check skill file versions against latest
    --color              Force colored output
    --no-color           Disable colored output
    --help               Show this help message

Output Modes:
    Default (TTY):    Colored terminal output with symbols
    Default (pipe):   Plain text (auto-detects non-TTY)
    --llm:            Structured markdown for AI/LLM consumption
    --json:           Machine-parseable JSON
    --llm --action-plan: Full LLM report with phased update plan

Exit Codes:
    0 - All dependencies up-to-date
    1 - Updates available
    2 - Error occurred
EOF
}

parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --no-cache) USE_CACHE=false; shift ;;
            --include-prerelease) INCLUDE_PRERELEASE=true; shift ;;
            --json) OUTPUT_JSON=true; shift ;;
            --llm) OUTPUT_LLM=true; shift ;;
            --action-plan) ACTION_PLAN=true; shift ;;
            --quiet) QUIET_MODE=true; shift ;;
            --color) FORCE_COLOR="true"; shift ;;
            --no-color) FORCE_COLOR="false"; shift ;;
            --skill-versions) SKILL_VERSIONS=true; shift ;;
            --help) print_usage; exit 0 ;;
            *) echo "Unknown option: $1"; print_usage; exit 2 ;;
        esac
    done
}

setup_colors() {
    local use_color=false
    if [[ "$FORCE_COLOR" == "true" ]]; then
        use_color=true
    elif [[ "$FORCE_COLOR" == "false" || "$OUTPUT_LLM" == "true" || "$OUTPUT_JSON" == "true" ]]; then
        use_color=false
    elif [[ -t 1 ]]; then
        use_color=true
    fi
    if [[ "$use_color" == "false" ]]; then
        RED='' GREEN='' YELLOW='' BLUE='' CYAN='' NC='' BOLD='' DIM=''
    fi
}

check_requirements() {
    local missing=""
    command -v curl &>/dev/null || missing="$missing curl"
    command -v jq &>/dev/null || missing="$missing jq"
    if [[ -n "$missing" ]]; then
        echo "Error: Missing required tools:$missing" >&2
        exit 2
    fi
    if [[ ! -f "$TOML_FILE" ]]; then
        echo "Error: Version catalog not found: $TOML_FILE" >&2
        echo "Run this script from a project with gradle/libs.versions.toml" >&2
        exit 2
    fi
}

init_cache() { mkdir -p "$CACHE_DIR"; }

is_cache_valid() {
    local cache_file=$1
    [[ "$USE_CACHE" == "false" ]] && return 1
    [[ ! -f "$cache_file" ]] && return 1
    local cache_age
    if [[ "$(uname)" == "Darwin" ]]; then
        cache_age=$(( $(date +%s) - $(stat -f %m "$cache_file") ))
    else
        cache_age=$(( $(date +%s) - $(stat -c %Y "$cache_file") ))
    fi
    [[ $cache_age -lt $CACHE_DURATION ]]
}

# === Version comparison ===
parse_version() {
    local version=$1 base_version prerelease=""
    if [[ "$version" == *"-"* ]]; then
        base_version="${version%%-*}"; prerelease="${version#*-}"
    else
        base_version="$version"
    fi
    IFS='.' read -ra parts <<< "$base_version"
    local major="${parts[0]:-0}" minor="${parts[1]:-0}" patch="${parts[2]:-0}"
    patch="${patch%%[^0-9]*}"
    major="${major//[^0-9]/}"; minor="${minor//[^0-9]/}"; patch="${patch//[^0-9]/}"
    echo "${major:-0}.${minor:-0}.${patch:-0}|${prerelease}"
}

compare_version_numbers() {
    local parsed1 parsed2
    parsed1=$(parse_version "$1"); parsed2=$(parse_version "$2")
    local base1="${parsed1%%|*}" pre1="${parsed1#*|}"
    local base2="${parsed2%%|*}" pre2="${parsed2#*|}"
    IFS='.' read -ra p1 <<< "$base1"; IFS='.' read -ra p2 <<< "$base2"
    for i in 0 1 2; do
        local n1="${p1[$i]:-0}" n2="${p2[$i]:-0}"
        [[ $n1 -lt $n2 ]] && { echo "-1"; return; }
        [[ $n1 -gt $n2 ]] && { echo "1"; return; }
    done
    [[ -z "$pre1" && -n "$pre2" ]] && { echo "1"; return; }
    [[ -n "$pre1" && -z "$pre2" ]] && { echo "-1"; return; }
    echo "0"
}

is_prerelease() {
    [[ "$1" =~ -alpha|-beta|-rc|-dev|-SNAPSHOT|\.alpha|\.beta|\.rc|-M[0-9] ]]
}

get_update_type() {
    local cp lp
    cp=$(parse_version "$1"); lp=$(parse_version "$2")
    local cb="${cp%%|*}" lb="${lp%%|*}"
    IFS='.' read -ra cv <<< "$cb"; IFS='.' read -ra lv <<< "$lb"
    [[ "${lv[0]:-0}" -gt "${cv[0]:-0}" ]] && { echo "major"; return; }
    [[ "${lv[1]:-0}" -gt "${cv[1]:-0}" ]] && { echo "minor"; return; }
    [[ "${lv[2]:-0}" -gt "${cv[2]:-0}" ]] && { echo "patch"; return; }
    echo "none"
}

# === Maven Central query ===
get_latest_version() {
    local group=$1 artifact=$2
    local cache_file="$CACHE_DIR/${group}_${artifact}.json"

    if is_cache_valid "$cache_file"; then
        local cached
        cached=$(jq -r '.version // empty' "$cache_file" 2>/dev/null)
        [[ -n "$cached" ]] && { echo "$cached"; return 0; }
    fi

    local url="https://search.maven.org/solrsearch/select?q=g:${group}+AND+a:${artifact}&core=gav&rows=200&wt=json"
    sleep 0.3  # Rate limit
    local response
    response=$(curl -s --max-time 15 "$url" 2>/dev/null) || { echo "error"; return 0; }

    local num_found
    num_found=$(echo "$response" | jq -r '.response.numFound // 0' 2>/dev/null)
    [[ "$num_found" == "0" || -z "$num_found" ]] && { echo "not_found"; return 0; }

    local all_versions latest_version=""
    all_versions=$(echo "$response" | jq -r '.response.docs[].v' 2>/dev/null | sort -u)
    [[ -z "$all_versions" ]] && { echo "not_found"; return 0; }

    while IFS= read -r version; do
        [[ -z "$version" ]] && continue
        if [[ "$INCLUDE_PRERELEASE" == "false" ]] && is_prerelease "$version"; then continue; fi
        if [[ -z "$latest_version" ]]; then latest_version="$version"; continue; fi
        local cmp
        cmp=$(compare_version_numbers "$version" "$latest_version")
        [[ "$cmp" == "1" ]] && latest_version="$version"
    done <<< "$all_versions"

    # Fallback: if no stable found, use any version
    if [[ -z "$latest_version" && "$INCLUDE_PRERELEASE" == "false" ]]; then
        while IFS= read -r version; do
            [[ -z "$version" ]] && continue
            if [[ -z "$latest_version" ]]; then latest_version="$version"; continue; fi
            local cmp
            cmp=$(compare_version_numbers "$version" "$latest_version")
            [[ "$cmp" == "1" ]] && latest_version="$version"
        done <<< "$all_versions"
    fi

    if [[ -n "$latest_version" && "$latest_version" != "not_found" ]]; then
        echo "{\"version\": \"$latest_version\"}" > "$cache_file"
    fi
    echo "${latest_version:-not_found}"
}

# === TOML parsing ===
get_toml_version() {
    local key=$1
    awk -F'=' "/^${key}[[:space:]]*=/ {gsub(/[[:space:]\"]/,\"\",\$2); print \$2; exit}" "$TOML_FILE"
}

# === Output functions ===
print_result() {
    local name=$1 current=$2 latest=$3 status=$4
    [[ "$OUTPUT_JSON" == "true" || "$OUTPUT_LLM" == "true" ]] && return
    case $status in
        "up-to-date")
            [[ "$QUIET_MODE" == "false" ]] && echo -e "${GREEN}+${NC} ${name}: ${current} ${GREEN}(latest)${NC}" ;;
        "update-available")
            echo -e "${YELLOW}!${NC} ${name}: ${current} ${YELLOW}->${NC} ${BLUE}${latest}${NC}" ;;
        "newer")
            [[ "$QUIET_MODE" == "false" ]] && echo -e "${BLUE}*${NC} ${name}: ${current} ${BLUE}(newer than ${latest})${NC}" ;;
        "error"|"not-found")
            [[ "$QUIET_MODE" == "false" ]] && echo -e "${RED}x${NC} ${name}: ${current} ${RED}(${status})${NC}" ;;
    esac
}

store_result() {
    local name=$1 key=$2 current=$3 latest=$4 status=$5 category=$6
    local update_type=""
    [[ "$status" == "update-available" ]] && update_type=$(get_update_type "$current" "$latest")
    local result="$name|$key|$current|$latest|$status|$category|$update_type"
    case $status in
        "up-to-date"|"newer") UPTODATE_DEPS+="${result}"$'\n' ;;
        "update-available")
            case $update_type in
                "major") MAJOR_UPDATES+="${result}"$'\n' ;;
                "minor") MINOR_UPDATES+="${result}"$'\n' ;;
                "patch") PATCH_UPDATES+="${result}"$'\n' ;;
            esac ;;
        *) ERROR_DEPS+="${result}"$'\n' ;;
    esac
}

count_entries() {
    [[ -z "$1" ]] && { echo "0"; return; }
    echo "$1" | grep -c '^' 2>/dev/null || echo "0"
}

# === LLM output ===
print_llm_output() {
    local project_name
    project_name=$(basename "$REPO_ROOT")
    local date_str
    date_str=$(date '+%Y-%m-%d')

    cat << EOF
# Dependency Update Report

**Project**: ${project_name} (Kotlin Multiplatform)
**Generated**: ${date_str}
**Status**: ${UP_TO_DATE}/${TOTAL_DEPS} up-to-date
**Cache**: $([[ "$USE_CACHE" == "true" ]] && echo "enabled (1 hour)" || echo "disabled")

---

EOF

    if [[ -n "$MAJOR_UPDATES" ]]; then
        echo "## BREAKING: Major Version Updates"
        echo ""
        echo "> These updates may contain breaking API changes. Review changelogs before updating."
        echo ""
        while IFS='|' read -r name key current latest status category update_type; do
            [[ -z "$name" ]] && continue
            local changelog_url=""
            [[ -n "${KMP_REGISTRY[$key]+x}" ]] && changelog_url=$(echo "${KMP_REGISTRY[$key]}" | cut -d'|' -f5)
            echo "### ${name}"
            echo "- **Current**: \`${current}\` -> **Latest**: \`${latest}\`"
            echo "- **Category**: ${category}"
            [[ -n "$changelog_url" ]] && echo "- **Changelog**: ${changelog_url}"
            echo ""
            echo '```toml'
            echo "${key} = \"${latest}\""
            echo '```'
            echo ""
            echo "---"
            echo ""
        done <<< "$MAJOR_UPDATES"
    fi

    if [[ -n "$MINOR_UPDATES" ]]; then
        echo "## Feature Updates (Minor Versions)"
        echo ""
        while IFS='|' read -r name key current latest status category update_type; do
            [[ -z "$name" ]] && continue
            echo "- **${name}**: \`${current}\` -> \`${latest}\` (${category})"
        done <<< "$MINOR_UPDATES"
        echo ""
        echo "---"
        echo ""
    fi

    if [[ -n "$PATCH_UPDATES" ]]; then
        echo "## Bug Fixes (Patch Versions)"
        echo ""
        echo "> Safe to update. Bug fixes and security patches only."
        echo ""
        while IFS='|' read -r name key current latest status category update_type; do
            [[ -z "$name" ]] && continue
            echo "- **${name}**: \`${current}\` -> \`${latest}\` (${category})"
        done <<< "$PATCH_UPDATES"
        echo ""
        echo "---"
        echo ""
    fi

    if [[ -n "$UPTODATE_DEPS" ]]; then
        echo "## Up-to-Date Dependencies"
        echo ""
        while IFS='|' read -r name key current latest status category update_type; do
            [[ -z "$name" ]] && continue
            echo "- ${name}: \`${current}\`"
        done <<< "$UPTODATE_DEPS"
        echo ""
        echo "---"
        echo ""
    fi

    cat << EOF
## Summary

| Metric | Count |
|--------|-------|
| Total Dependencies | ${TOTAL_DEPS} |
| Up-to-Date | ${UP_TO_DATE} |
| Updates Available | ${UPDATES_AVAILABLE} |
| Errors | ${ERRORS} |

EOF

    [[ "$ACTION_PLAN" == "true" ]] && print_action_plan
}

print_action_plan() {
    local patch_count minor_count major_count
    patch_count=$(count_entries "$PATCH_UPDATES")
    minor_count=$(count_entries "$MINOR_UPDATES")
    major_count=$(count_entries "$MAJOR_UPDATES")
    local total=$((patch_count + minor_count + major_count))
    [[ $total -eq 0 ]] && return

    echo "## Update Action Plan"
    echo ""
    echo "### Phase 1: Patch Updates (Safe)"
    echo ""
    if [[ -n "$PATCH_UPDATES" ]]; then
        echo '```bash'
        echo 'git checkout -b chore/dependency-updates'
        while IFS='|' read -r name key current latest status category update_type; do
            [[ -z "$name" ]] && continue
            echo "# ${name}: ${current} -> ${latest}"
            echo "sed -i '' 's/${key} = \"${current}\"/${key} = \"${latest}\"/' gradle/libs.versions.toml"
        done <<< "$PATCH_UPDATES"
        echo './gradlew build && ./gradlew allTests'
        echo 'git add gradle/libs.versions.toml && git commit -m "chore(deps): patch updates"'
        echo '```'
    else
        echo "No patch updates."
    fi

    echo ""
    echo "### Phase 2: Minor Updates (Features)"
    echo ""
    if [[ -n "$MINOR_UPDATES" ]]; then
        echo '```bash'
        while IFS='|' read -r name key current latest status category update_type; do
            [[ -z "$name" ]] && continue
            echo "# ${name}: ${current} -> ${latest}"
            echo "sed -i '' 's/${key} = \"${current}\"/${key} = \"${latest}\"/' gradle/libs.versions.toml"
            echo './gradlew build && ./gradlew allTests'
            echo "git add gradle/libs.versions.toml && git commit -m \"chore(deps): update ${name} to ${latest}\""
            echo ''
        done <<< "$MINOR_UPDATES"
        echo '```'
    else
        echo "No minor updates."
    fi

    echo ""
    echo "### Phase 3: Major Updates (Breaking)"
    echo ""
    if [[ -n "$MAJOR_UPDATES" ]]; then
        while IFS='|' read -r name key current latest status category update_type; do
            [[ -z "$name" ]] && continue
            local changelog_url=""
            [[ -n "${KMP_REGISTRY[$key]+x}" ]] && changelog_url=$(echo "${KMP_REGISTRY[$key]}" | cut -d'|' -f5)
            echo "#### ${name} (${current} -> ${latest})"
            echo ""
            echo "1. Review changelog: ${changelog_url:-"search for ${name} changelog"}"
            echo "2. Update: \`sed -i '' 's/${key} = \"${current}\"/${key} = \"${latest}\"/' gradle/libs.versions.toml\`"
            echo "3. Fix compilation errors"
            echo "4. Run: \`./gradlew clean build allTests\`"
            echo "5. Commit: \`git commit -am \"chore(deps): migrate ${name} to ${latest}\"\`"
            echo ""
        done <<< "$MAJOR_UPDATES"
    else
        echo "No major updates."
    fi

    cat << 'EOF'

### Post-Update Checklist

- [ ] All builds pass (`./gradlew build`)
- [ ] All tests pass (`./gradlew allTests`)
- [ ] App launches on Android emulator
- [ ] App launches on iOS simulator
- [ ] No new deprecation warnings
- [ ] PR created and reviewed

EOF
}

# === JSON output ===
build_json_output() {
    local project_name
    project_name=$(basename "$REPO_ROOT")
    jq -s '{
        report: {
            project: "'"$project_name"'",
            timestamp: (now | strftime("%Y-%m-%dT%H:%M:%SZ")),
            summary: {
                total: (length),
                upToDate: (map(select(.status == "up-to-date" or .status == "newer")) | length),
                updatesAvailable: (map(select(.status == "update-available")) | length),
                errors: (map(select(.status == "error")) | length)
            }
        },
        dependencies: .
    }'
}

# === Main check ===
check_dependencies() {
    local json_results=""

    # Parse all version keys from [versions] section of TOML
    local in_versions=false
    while IFS= read -r line; do
        # Detect section headers
        if [[ "$line" =~ ^\[versions\] ]]; then in_versions=true; continue; fi
        if [[ "$line" =~ ^\[ ]]; then in_versions=false; continue; fi
        [[ "$in_versions" == "false" ]] && continue

        # Skip comments and empty lines
        [[ "$line" =~ ^[[:space:]]*# ]] && continue
        [[ -z "${line// /}" ]] && continue

        # Extract key = "value"
        local version_key version_value
        version_key=$(echo "$line" | sed 's/[[:space:]]*=.*//' | tr -d ' ')
        version_value=$(echo "$line" | sed 's/.*=[[:space:]]*//' | tr -d '"' | tr -d ' ')

        [[ -z "$version_key" || -z "$version_value" ]] && continue

        # Look up in KMP registry
        if [[ -n "${KMP_REGISTRY[$version_key]+x}" ]]; then
            local entry="${KMP_REGISTRY[$version_key]}"
            local group artifact display_name category changelog_url
            IFS='|' read -r group artifact display_name category changelog_url <<< "$entry"

            ((TOTAL_DEPS++))

            local latest
            latest=$(get_latest_version "$group" "$artifact")

            if [[ -z "$latest" || "$latest" == "error" || "$latest" == "not_found" ]]; then
                print_result "$display_name" "$version_value" "unknown" "error"
                store_result "$display_name" "$version_key" "$version_value" "unknown" "error" "$category"
                ((ERRORS++))
            else
                local cmp
                cmp=$(compare_version_numbers "$version_value" "$latest")
                local status
                case "$cmp" in
                    "-1") status="update-available"; ((UPDATES_AVAILABLE++)) ;;
                    "0") status="up-to-date"; ((UP_TO_DATE++)) ;;
                    "1") status="newer"; ((UP_TO_DATE++)) ;;
                    *) status="unknown"; ((ERRORS++)) ;;
                esac
                print_result "$display_name" "$version_value" "$latest" "$status"
                store_result "$display_name" "$version_key" "$version_value" "$latest" "$status" "$category"
            fi

            if [[ "$OUTPUT_JSON" == "true" ]]; then
                local ut=""
                [[ "$status" == "update-available" ]] && ut=$(get_update_type "$version_value" "$latest")
                json_results+='{"name":"'"$display_name"'","key":"'"$version_key"'","current":"'"$version_value"'","latest":"'"${latest}"'","status":"'"${status:-error}"'","category":"'"$category"'","updateType":"'"$ut"'"}'$'\n'
            fi
        fi
    done < "$TOML_FILE"

    if [[ "$OUTPUT_JSON" == "true" ]]; then
        echo "$json_results" | build_json_output
    fi
}

# === Skill version checking ===
SKILL_OUTDATED=0

check_skill_versions() {
    local sources_file="$SCRIPT_DIR/skill-update-sources.json"
    local skills_dir
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    sources_file="$SCRIPT_DIR/skill-update-sources.json"
    skills_dir="$(dirname "$SCRIPT_DIR")/skills"

    [[ ! -f "$sources_file" ]] && { echo "Skill sources file not found: $sources_file" >&2; return; }

    local libs
    libs=$(jq -r '.libraries | keys[]' "$sources_file")

    if [[ "$OUTPUT_LLM" == "true" ]]; then
        echo ""
        echo "## Skill File Version Status"
        echo ""
    elif [[ "$OUTPUT_JSON" != "true" ]]; then
        echo ""
        echo -e "${BOLD}Skill File Version Status${NC}"
        echo "──────────────────────────"
    fi

    while IFS= read -r lib; do
        [[ -z "$lib" ]] && continue
        local skill_dir display_name
        skill_dir=$(jq -r ".libraries[\"$lib\"].skill_dir" "$sources_file")
        display_name=$(jq -r ".libraries[\"$lib\"].display_name" "$sources_file")

        local skill_file="$skills_dir/$skill_dir/SKILL.md"
        [[ ! -f "$skill_file" ]] && continue

        # Extract version from skill file header (first version-like string in first 20 lines)
        local skill_version
        skill_version=$(head -20 "$skill_file" | grep -oE '\b[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z]+[0-9.]*)*\b' | head -1)
        [[ -z "$skill_version" ]] && continue

        # Get Maven coordinates and check latest
        local version_key group artifact
        version_key=$(jq -r ".libraries[\"$lib\"].version_key" "$sources_file")
        if [[ -n "${KMP_REGISTRY[$version_key]+x}" ]]; then
            IFS='|' read -r group artifact _ _ _ <<< "${KMP_REGISTRY[$version_key]}"
            local latest
            latest=$(get_latest_version "$group" "$artifact")
            if [[ -n "$latest" && "$latest" != "error" && "$latest" != "not_found" ]]; then
                local cmp
                cmp=$(compare_version_numbers "$skill_version" "$latest")
                if [[ "$cmp" == "-1" ]]; then
                    ((SKILL_OUTDATED++))
                    if [[ "$OUTPUT_LLM" == "true" ]]; then
                        echo "- **$display_name**: skill says \`$skill_version\`, latest is \`$latest\` — **OUTDATED**"
                    elif [[ "$OUTPUT_JSON" != "true" ]]; then
                        echo -e "${YELLOW}!${NC} ${display_name}: skill ${skill_version} ${YELLOW}->${NC} ${BLUE}${latest}${NC}"
                    fi
                else
                    if [[ "$QUIET_MODE" == "false" ]]; then
                        if [[ "$OUTPUT_LLM" == "true" ]]; then
                            echo "- $display_name: \`$skill_version\` (current)"
                        elif [[ "$OUTPUT_JSON" != "true" ]]; then
                            echo -e "${GREEN}+${NC} ${display_name}: ${skill_version} ${GREEN}(current)${NC}"
                        fi
                    fi
                fi
            fi
        fi
    done <<< "$libs"

    if [[ "$OUTPUT_LLM" == "true" && $SKILL_OUTDATED -gt 0 ]]; then
        echo ""
        echo "> **$SKILL_OUTDATED skill(s) reference outdated versions.** Run \`fetch-release-notes.sh --all\` and invoke the skill-updater agent."
    fi
}

# === Entry point ===
main() {
    parse_args "$@"
    setup_colors
    check_requirements
    init_cache

    if [[ "$OUTPUT_JSON" != "true" && "$OUTPUT_LLM" != "true" ]]; then
        local project_name
        project_name=$(basename "$REPO_ROOT")
        echo ""
        echo -e "${BOLD}Dependency Version Check Report${NC}"
        echo "================================"
        echo "Project: ${project_name} (Kotlin Multiplatform)"
        echo "Date: $(date '+%Y-%m-%d %H:%M:%S')"
        echo "Cache: $([[ "$USE_CACHE" == "true" ]] && echo "enabled (1h)" || echo "disabled")"
        echo ""
    fi

    check_dependencies

    if [[ "$OUTPUT_JSON" != "true" && "$OUTPUT_LLM" != "true" ]]; then
        echo ""
        echo "================================"
        echo -e "${BOLD}Summary${NC}: ${UP_TO_DATE}/${TOTAL_DEPS} up-to-date"
        [[ $UPDATES_AVAILABLE -gt 0 ]] && echo -e "${YELLOW}Updates available: ${UPDATES_AVAILABLE}${NC}"
        [[ $ERRORS -gt 0 ]] && echo -e "${RED}Errors: ${ERRORS}${NC}"
        echo ""
    fi

    [[ "$OUTPUT_LLM" == "true" ]] && print_llm_output

    [[ "$SKILL_VERSIONS" == "true" ]] && check_skill_versions

    if [[ $ERRORS -gt 0 ]]; then exit 2
    elif [[ $UPDATES_AVAILABLE -gt 0 ]]; then exit 1
    else exit 0
    fi
}

main "$@"
