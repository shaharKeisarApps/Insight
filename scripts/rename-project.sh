#!/bin/bash

# Project Renaming Script
# Renames package names, directories, and updates all references
#
# Usage: ./scripts/rename-project.sh <old_name> <new_name>
# Example: ./scripts/rename-project.sh metroditest insight

set -e

OLD_NAME="${1}"
NEW_NAME="${2}"

if [[ -z "$OLD_NAME" || -z "$NEW_NAME" ]]; then
    echo "Usage: $0 <old_name> <new_name>"
    echo "Example: $0 metroditest insight"
    exit 1
fi

# Derive variations
OLD_NAME_LOWER=$(echo "$OLD_NAME" | tr '[:upper:]' '[:lower:]')
NEW_NAME_LOWER=$(echo "$NEW_NAME" | tr '[:upper:]' '[:lower:]')
OLD_NAME_CAPITALIZED=$(echo "$OLD_NAME_LOWER" | sed 's/\b\(.\)/\u\1/')
NEW_NAME_CAPITALIZED=$(echo "$NEW_NAME_LOWER" | sed 's/\b\(.\)/\u\1/')

# For PascalCase (e.g., MetroDITest -> Insight)
OLD_NAME_PASCAL="${OLD_NAME_CAPITALIZED}"
NEW_NAME_PASCAL="${NEW_NAME_CAPITALIZED}"

echo "=== Project Renaming Script ==="
echo "Old name: $OLD_NAME_LOWER (Pascal: $OLD_NAME_PASCAL)"
echo "New name: $NEW_NAME_LOWER (Pascal: $NEW_NAME_PASCAL)"
echo ""

# Get the project root (parent of scripts directory)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_ROOT"

echo "Project root: $PROJECT_ROOT"
echo ""

# Step 1: Update settings.gradle.kts
echo "[1/7] Updating settings.gradle.kts..."
if [[ -f "settings.gradle.kts" ]]; then
    sed -i '' "s/rootProject.name = \".*\"/rootProject.name = \"$NEW_NAME_PASCAL\"/" settings.gradle.kts
    echo "  ✓ Updated rootProject.name"
fi

# Step 2: Update build.gradle.kts files (namespace and applicationId)
echo "[2/7] Updating build.gradle.kts files..."
find . -name "build.gradle.kts" -type f | while read -r file; do
    if grep -q "com.keisardev.$OLD_NAME_LOWER" "$file" 2>/dev/null; then
        sed -i '' "s/com\.keisardev\.$OLD_NAME_LOWER/com.keisardev.$NEW_NAME_LOWER/g" "$file"
        echo "  ✓ Updated $file"
    fi
    # Update plugin IDs (e.g., metroditest.android.* -> insight.android.*)
    if grep -q "$OLD_NAME_LOWER\.android\." "$file" 2>/dev/null; then
        sed -i '' "s/$OLD_NAME_LOWER\.android\./$NEW_NAME_LOWER.android./g" "$file"
        echo "  ✓ Updated plugin IDs in $file"
    fi
done

# Step 3: Update convention plugin IDs
echo "[3/7] Updating convention plugins..."
CONVENTION_BUILD="build-logic/convention/build.gradle.kts"
if [[ -f "$CONVENTION_BUILD" ]]; then
    sed -i '' "s/group = \"$OLD_NAME_LOWER\"/group = \"$NEW_NAME_LOWER\"/g" "$CONVENTION_BUILD"
    sed -i '' "s/id(\"$OLD_NAME_LOWER\./id(\"$NEW_NAME_LOWER./g" "$CONVENTION_BUILD"
    echo "  ✓ Updated $CONVENTION_BUILD"
fi

# Update convention plugin kotlin files
find build-logic -name "*.kt" -type f | while read -r file; do
    if grep -q "$OLD_NAME_LOWER\.android\." "$file" 2>/dev/null; then
        sed -i '' "s/$OLD_NAME_LOWER\.android\./$NEW_NAME_LOWER.android./g" "$file"
        echo "  ✓ Updated $file"
    fi
done

# Step 4: Update package declarations and imports in Kotlin files
echo "[4/7] Updating Kotlin source files..."
find . -name "*.kt" -type f ! -path "./build/*" ! -path "./.gradle/*" | while read -r file; do
    if grep -q "com\.keisardev\.$OLD_NAME_LOWER" "$file" 2>/dev/null; then
        sed -i '' "s/com\.keisardev\.$OLD_NAME_LOWER/com.keisardev.$NEW_NAME_LOWER/g" "$file"
        echo "  ✓ Updated $file"
    fi
    # Update class name references (e.g., MetroDITestApp -> InsightApp)
    if grep -q "${OLD_NAME_PASCAL}App" "$file" 2>/dev/null; then
        sed -i '' "s/${OLD_NAME_PASCAL}App/${NEW_NAME_PASCAL}App/g" "$file"
        sed -i '' "s/${OLD_NAME_PASCAL}AppComponentFactory/${NEW_NAME_PASCAL}AppComponentFactory/g" "$file"
        echo "  ✓ Updated class references in $file"
    fi
done

# Step 5: Update SQLDelight files
echo "[5/7] Updating SQLDelight files..."
find . -name "*.sq" -type f | while read -r file; do
    if grep -q "com\.keisardev\.$OLD_NAME_LOWER" "$file" 2>/dev/null; then
        sed -i '' "s/com\.keisardev\.$OLD_NAME_LOWER/com.keisardev.$NEW_NAME_LOWER/g" "$file"
        echo "  ✓ Updated $file"
    fi
done

# Step 6: Rename directories
echo "[6/7] Renaming package directories..."
find . -type d -path "*/$OLD_NAME_LOWER" ! -path "./build/*" ! -path "./.gradle/*" | sort -r | while read -r dir; do
    parent_dir=$(dirname "$dir")
    if [[ -d "$dir" ]]; then
        mv "$dir" "$parent_dir/$NEW_NAME_LOWER"
        echo "  ✓ Renamed $dir -> $parent_dir/$NEW_NAME_LOWER"
    fi
done

# Step 7: Update Android resources
echo "[7/7] Updating Android resources..."

# Update strings.xml
STRINGS_FILE="app/src/main/res/values/strings.xml"
if [[ -f "$STRINGS_FILE" ]]; then
    sed -i '' "s/>$OLD_NAME_PASCAL</>$NEW_NAME_PASCAL</g" "$STRINGS_FILE"
    echo "  ✓ Updated app name in strings.xml"
fi

# Update themes.xml
THEMES_FILE="app/src/main/res/values/themes.xml"
if [[ -f "$THEMES_FILE" ]]; then
    sed -i '' "s/Theme\.$OLD_NAME_PASCAL/Theme.$NEW_NAME_PASCAL/g" "$THEMES_FILE"
    echo "  ✓ Updated theme name in themes.xml"
fi

# Update AndroidManifest.xml
MANIFEST_FILE="app/src/main/AndroidManifest.xml"
if [[ -f "$MANIFEST_FILE" ]]; then
    sed -i '' "s/\.$OLD_NAME_PASCAL/.$NEW_NAME_PASCAL/g" "$MANIFEST_FILE"
    sed -i '' "s/Theme\.$OLD_NAME_PASCAL/Theme.$NEW_NAME_PASCAL/g" "$MANIFEST_FILE"
    echo "  ✓ Updated AndroidManifest.xml"
fi

echo ""
echo "=== Renaming Complete ==="
echo ""
echo "Next steps:"
echo "  1. Update README.md and CLAUDE.md manually if needed"
echo "  2. Run './gradlew clean assembleDebug' to verify the build"
echo "  3. Run './gradlew test' to verify tests pass"
echo "  4. Commit the changes"
echo ""
