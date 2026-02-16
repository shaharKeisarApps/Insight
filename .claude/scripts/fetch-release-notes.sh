#!/bin/bash
#
# Fetch Release Notes for KMP Libraries
# Retrieves release notes from GitHub Releases API with CHANGELOG.md fallback.
#
# Usage:
#   ./fetch-release-notes.sh <library>              # Fetch latest release notes
#   ./fetch-release-notes.sh <library> <version>     # Fetch specific version
#   ./fetch-release-notes.sh --all                   # Fetch for all libraries with updates
#   ./fetch-release-notes.sh --diff <library>        # Show diff since skill version
#   ./fetch-release-notes.sh --list                  # List available libraries
#   ./fetch-release-notes.sh --json <library>        # Output as JSON
#
# Requirements: gh (GitHub CLI), jq, curl
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SOURCES_FILE="$SCRIPT_DIR/skill-update-sources.json"
SKILLS_DIR="$(dirname "$SCRIPT_DIR")/skills"

# Output options
OUTPUT_JSON=false
OUTPUT_DIFF=false
FETCH_ALL=false
LIST_ONLY=false

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
DIM='\033[2m'
NC='\033[0m'

# Disable colors if not TTY
if [[ ! -t 1 ]]; then
    RED='' GREEN='' YELLOW='' BLUE='' CYAN='' BOLD='' DIM='' NC=''
fi

print_usage() {
    cat << 'EOF'
Fetch Release Notes for KMP Libraries

Usage:
    fetch-release-notes.sh <library>              Fetch latest release notes
    fetch-release-notes.sh <library> <version>    Fetch specific version
    fetch-release-notes.sh --all                  Fetch for all libraries
    fetch-release-notes.sh --diff <library>       Show diff since skill version
    fetch-release-notes.sh --list                 List available libraries
    fetch-release-notes.sh --json <library>       Output as JSON
    fetch-release-notes.sh --help                 Show this help

Examples:
    fetch-release-notes.sh circuit
    fetch-release-notes.sh circuit 0.33.0
    fetch-release-notes.sh --diff metro
    fetch-release-notes.sh --all --json
EOF
}

check_requirements() {
    local missing=""
    command -v gh &>/dev/null || missing="$missing gh"
    command -v jq &>/dev/null || missing="$missing jq"
    command -v curl &>/dev/null || missing="$missing curl"
    if [[ -n "$missing" ]]; then
        echo -e "${RED}Error: Missing required tools:${NC}$missing" >&2
        exit 2
    fi
    if [[ ! -f "$SOURCES_FILE" ]]; then
        echo -e "${RED}Error: Sources file not found: $SOURCES_FILE${NC}" >&2
        exit 2
    fi
}

# Read a library's metadata from sources JSON
get_lib_field() {
    local lib=$1 field=$2
    jq -r ".libraries[\"$lib\"].$field // empty" "$SOURCES_FILE"
}

# List all available libraries
list_libraries() {
    echo -e "${BOLD}Available Libraries:${NC}"
    echo ""
    jq -r '.libraries | to_entries[] | "  \(.key)\t\(.value.display_name)\t(\(.value.github))"' "$SOURCES_FILE" | column -t -s$'\t'
}

# Get current skill version from SKILL.md header
get_skill_version() {
    local lib=$1
    local skill_dir
    skill_dir=$(get_lib_field "$lib" "skill_dir")
    [[ -z "$skill_dir" ]] && return

    local skill_file="$SKILLS_DIR/$skill_dir/SKILL.md"
    [[ ! -f "$skill_file" ]] && return

    # Extract version from YAML frontmatter or H1 header (portable, no -P flag)
    local version
    version=$(head -20 "$skill_file" | grep -oE '[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z]+[0-9.]*)*' | head -1)
    echo "${version:-unknown}"
}

# Fetch release notes from GitHub Releases API
fetch_github_release() {
    local repo=$1 version=${2:-latest}
    local release_body=""

    if [[ "$version" == "latest" ]]; then
        release_body=$(gh api "repos/$repo/releases/latest" --jq '.body // ""' 2>/dev/null || echo "")
        if [[ -z "$release_body" ]]; then
            # Try listing releases and taking first
            release_body=$(gh api "repos/$repo/releases" --jq '.[0].body // ""' 2>/dev/null || echo "")
        fi
    else
        # Try tag formats: v0.33.0, 0.33.0
        release_body=$(gh api "repos/$repo/releases/tags/v$version" --jq '.body // ""' 2>/dev/null || echo "")
        if [[ -z "$release_body" ]]; then
            release_body=$(gh api "repos/$repo/releases/tags/$version" --jq '.body // ""' 2>/dev/null || echo "")
        fi
    fi

    echo "$release_body"
}

# Get latest release version from GitHub
get_latest_release_version() {
    local repo=$1
    gh api "repos/$repo/releases/latest" --jq '.tag_name // ""' 2>/dev/null | sed 's/^v//'
}

# Fetch CHANGELOG.md from GitHub as fallback
fetch_changelog() {
    local repo=$1 changelog_file=$2
    [[ -z "$changelog_file" || "$changelog_file" == "null" ]] && return

    local content
    content=$(gh api "repos/$repo/contents/$changelog_file" --jq '.content' 2>/dev/null || echo "")
    if [[ -n "$content" && "$content" != "null" ]]; then
        echo "$content" | base64 -d 2>/dev/null | head -200
    fi
}

# Fetch AndroidX release notes from developer.android.com
fetch_androidx_notes() {
    local release_url=$1
    [[ -z "$release_url" || "$release_url" == "null" ]] && return

    # Use curl to fetch the page, extract text content
    local page_content
    page_content=$(curl -sL --max-time 15 "$release_url" 2>/dev/null || echo "")
    if [[ -n "$page_content" ]]; then
        # Extract latest version section (rough text extraction)
        echo "$page_content" | \
            sed 's/<[^>]*>//g' | \
            grep -A 30 'Version\|Latest\|Release' | \
            head -50 | \
            sed 's/^[[:space:]]*//' | \
            grep -v '^$' | \
            head -30
    fi
}

# Extract key information from release notes
extract_key_info() {
    local notes=$1
    local breaking_changes="" new_features="" deprecations="" bug_fixes=""

    # Extract breaking changes
    breaking_changes=$(echo "$notes" | grep -iA 5 'breaking\|BREAKING' | head -10)

    # Extract new features
    new_features=$(echo "$notes" | grep -iA 3 'new\|added\|feature\|NEW\|ADDED' | head -10)

    # Extract deprecations
    deprecations=$(echo "$notes" | grep -iA 3 'deprecat\|removed\|DEPRECAT\|REMOVED' | head -10)

    # Extract bug fixes
    bug_fixes=$(echo "$notes" | grep -iA 3 'fix\|bug\|FIX\|BUG' | head -10)

    if [[ -n "$breaking_changes" ]]; then
        echo -e "\n${RED}${BOLD}Breaking Changes:${NC}"
        echo "$breaking_changes"
    fi
    if [[ -n "$new_features" ]]; then
        echo -e "\n${GREEN}${BOLD}New Features:${NC}"
        echo "$new_features"
    fi
    if [[ -n "$deprecations" ]]; then
        echo -e "\n${YELLOW}${BOLD}Deprecations:${NC}"
        echo "$deprecations"
    fi
    if [[ -n "$bug_fixes" ]]; then
        echo -e "\n${BLUE}${BOLD}Bug Fixes:${NC}"
        echo "$bug_fixes"
    fi
}

# Format output as JSON
format_json() {
    local lib=$1 version=$2 notes=$3 skill_version=$4
    local display_name repo
    display_name=$(get_lib_field "$lib" "display_name")
    repo=$(get_lib_field "$lib" "github")

    jq -n \
        --arg lib "$lib" \
        --arg name "$display_name" \
        --arg repo "$repo" \
        --arg version "$version" \
        --arg skill_version "$skill_version" \
        --arg notes "$notes" \
        '{
            library: $lib,
            display_name: $name,
            github: $repo,
            latest_version: $version,
            skill_version: $skill_version,
            needs_update: ($version != $skill_version),
            release_notes: $notes
        }'
}

# Fetch release notes for a single library
fetch_library() {
    local lib=$1 target_version=${2:-}
    local repo changelog_file quality skill_dir release_url
    repo=$(get_lib_field "$lib" "github")
    changelog_file=$(get_lib_field "$lib" "changelog_file")
    quality=$(get_lib_field "$lib" "release_notes_quality")
    skill_dir=$(get_lib_field "$lib" "skill_dir")
    release_url=$(get_lib_field "$lib" "release_url")

    if [[ -z "$repo" ]]; then
        echo -e "${RED}Error: Unknown library '$lib'. Use --list to see available libraries.${NC}" >&2
        return 1
    fi

    local display_name
    display_name=$(get_lib_field "$lib" "display_name")

    # Get versions
    local latest_version skill_version
    if [[ -n "$target_version" ]]; then
        latest_version="$target_version"
    else
        latest_version=$(get_latest_release_version "$repo" 2>/dev/null || echo "unknown")
    fi
    skill_version=$(get_skill_version "$lib")

    local notes=""

    # Strategy based on release_notes_quality
    case "$quality" in
        "rich")
            notes=$(fetch_github_release "$repo" "${target_version:-latest}")
            ;;
        "minimal"|"empty")
            # Try GitHub release first, fall back to CHANGELOG
            notes=$(fetch_github_release "$repo" "${target_version:-latest}")
            if [[ -z "$notes" || ${#notes} -lt 200 ]]; then
                local changelog_content
                changelog_content=$(fetch_changelog "$repo" "$changelog_file")
                if [[ -n "$changelog_content" ]]; then
                    notes="$changelog_content"
                fi
            fi
            ;;
        "none")
            # AndroidX — use release URL
            if [[ -n "$release_url" && "$release_url" != "null" ]]; then
                notes=$(fetch_androidx_notes "$release_url")
            fi
            ;;
    esac

    # Output
    if [[ "$OUTPUT_JSON" == "true" ]]; then
        format_json "$lib" "${latest_version:-unknown}" "$notes" "${skill_version:-unknown}"
    else
        echo -e "${BOLD}${CYAN}$display_name${NC} ($repo)"
        echo -e "${DIM}────────────────────────────────────────${NC}"
        echo -e "  Latest version:  ${GREEN}${latest_version:-unknown}${NC}"
        echo -e "  Skill version:   ${YELLOW}${skill_version:-unknown}${NC}"

        if [[ -n "$skill_version" && -n "$latest_version" && "$skill_version" != "$latest_version" && "$skill_version" != "unknown" ]]; then
            echo -e "  Status:          ${RED}UPDATE NEEDED${NC}"
        elif [[ "$skill_version" == "$latest_version" ]]; then
            echo -e "  Status:          ${GREEN}Up to date${NC}"
        else
            echo -e "  Status:          ${YELLOW}Unknown${NC}"
        fi

        echo ""

        if [[ -n "$notes" ]]; then
            if [[ "$OUTPUT_DIFF" == "true" ]]; then
                extract_key_info "$notes"
            else
                echo -e "${BOLD}Release Notes:${NC}"
                echo "$notes" | head -80
            fi
        else
            echo -e "${DIM}No release notes available.${NC}"
        fi

        echo ""
    fi
}

# Fetch all libraries
fetch_all() {
    local libs
    libs=$(jq -r '.libraries | keys[]' "$SOURCES_FILE")
    local json_results="["
    local first=true

    while IFS= read -r lib; do
        [[ -z "$lib" ]] && continue
        if [[ "$OUTPUT_JSON" == "true" ]]; then
            local result
            result=$(fetch_library "$lib" 2>/dev/null || echo "{}")
            if [[ "$first" == "true" ]]; then
                first=false
            else
                json_results+=","
            fi
            json_results+="$result"
        else
            fetch_library "$lib" || true
            echo -e "${DIM}═══════════════════════════════════════${NC}"
            echo ""
        fi
    done <<< "$libs"

    if [[ "$OUTPUT_JSON" == "true" ]]; then
        json_results+="]"
        echo "$json_results" | jq '.'
    fi
}

# Parse arguments
parse_args() {
    local positional=()
    while [[ $# -gt 0 ]]; do
        case $1 in
            --json) OUTPUT_JSON=true; shift ;;
            --diff) OUTPUT_DIFF=true; shift ;;
            --all) FETCH_ALL=true; shift ;;
            --list) LIST_ONLY=true; shift ;;
            --help|-h) print_usage; exit 0 ;;
            -*) echo "Unknown option: $1"; print_usage; exit 2 ;;
            *) positional+=("$1"); shift ;;
        esac
    done
    set -- "${positional[@]+"${positional[@]}"}"
    POSITIONAL_ARGS=("${positional[@]+"${positional[@]}"}")
}

main() {
    POSITIONAL_ARGS=()
    parse_args "$@"
    check_requirements

    if [[ "$LIST_ONLY" == "true" ]]; then
        list_libraries
        exit 0
    fi

    if [[ "$FETCH_ALL" == "true" ]]; then
        fetch_all
        exit 0
    fi

    if [[ ${#POSITIONAL_ARGS[@]} -eq 0 ]]; then
        echo -e "${RED}Error: Library name required. Use --list to see available libraries.${NC}" >&2
        print_usage
        exit 2
    fi

    local library="${POSITIONAL_ARGS[0]}"
    local version="${POSITIONAL_ARGS[1]:-}"

    fetch_library "$library" "$version"
}

main "$@"
