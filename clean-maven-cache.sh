#!/bin/bash
# Maven Cache Cleanup Script
# Fixes corrupted JAR files and failed downloads in Maven local repository

set -e

echo "=========================================="
echo "Maven Cache Cleanup Script"
echo "=========================================="
echo ""

# Detect Maven local repository location
if [ -n "$M2_HOME" ]; then
    MAVEN_REPO="$M2_HOME/repository"
elif [ -d "$HOME/.m2/repository" ]; then
    MAVEN_REPO="$HOME/.m2/repository"
else
    echo "Error: Could not locate Maven local repository"
    echo "Expected location: ~/.m2/repository"
    exit 1
fi

echo "Maven repository: $MAVEN_REPO"
echo ""

# Count issues before cleanup
LAST_UPDATED_COUNT=$(find "$MAVEN_REPO" -name "*.lastUpdated" 2>/dev/null | wc -l)
RESOLVER_STATUS_COUNT=$(find "$MAVEN_REPO" -name "resolver-status.properties" 2>/dev/null | wc -l)

echo "Found issues:"
echo "  - *.lastUpdated files: $LAST_UPDATED_COUNT"
echo "  - resolver-status.properties files: $RESOLVER_STATUS_COUNT"
echo ""

# Check for specific corrupted dependency mentioned in error
CHATCOLOR_PATH="$MAVEN_REPO/me/dave/ChatColorHandler"
if [ -d "$CHATCOLOR_PATH" ]; then
    echo "WARNING: Found ChatColorHandler dependency at: $CHATCOLOR_PATH"
    echo "  This dependency is NOT required by the Wpets project!"
    echo "  It may be corrupted and will be removed."
    echo ""
fi

# Prompt user for confirmation
read -p "Proceed with cleanup? [y/N] " -n 1 -r
echo ""
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Cleanup cancelled."
    exit 0
fi

echo ""
echo "Starting cleanup..."
echo ""

# Remove failed download markers
echo "[1/3] Removing *.lastUpdated files..."
find "$MAVEN_REPO" -name "*.lastUpdated" -delete
echo "  Removed $LAST_UPDATED_COUNT files"

echo "[2/3] Removing resolver-status.properties files..."
find "$MAVEN_REPO" -name "resolver-status.properties" -delete
echo "  Removed $RESOLVER_STATUS_COUNT files"

# Remove corrupted ChatColorHandler if exists
if [ -d "$CHATCOLOR_PATH" ]; then
    echo "[3/3] Removing corrupted ChatColorHandler dependency..."
    rm -rf "$CHATCOLOR_PATH"
    echo "  Removed: $CHATCOLOR_PATH"
else
    echo "[3/3] No ChatColorHandler dependency to remove"
fi

echo ""
echo "=========================================="
echo "Cleanup Complete!"
echo "=========================================="
echo ""
echo "Next steps:"
echo "  1. Run: mvn clean compile"
echo "  2. Maven will re-download any missing dependencies"
echo "  3. Build should now succeed"
echo ""
