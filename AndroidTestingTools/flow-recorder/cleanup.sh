#!/bin/bash

# Flow Recorder Cleanup Script
# Removes FlowMonitor.kt from your Android project

set -e

# Configuration - should match setup.sh
ANDROID_PROJECT="${HOME}/StudioProjects/Android"
DEBUG_PACKAGE="app/src/main/java/protect/card_locker/debug"

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo ""
echo "═══════════════════════════════════════════════"
echo "   Flow Recorder Cleanup"
echo "═══════════════════════════════════════════════"
echo ""

TARGET_DIR="${ANDROID_PROJECT}/${DEBUG_PACKAGE}"
TARGET_FILE="${TARGET_DIR}/FlowMonitor.kt"

# Check if file exists
if [ ! -f "${TARGET_FILE}" ]; then
    echo -e "${YELLOW}⚠ FlowMonitor.kt not found${NC}"
    echo "Already removed or never installed."
    exit 0
fi

echo "This will remove:"
echo "  ${TARGET_FILE}"
echo ""
read -p "Continue? (y/n) " -n 1 -r
echo ""

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Cleanup cancelled."
    exit 0
fi

# Remove the file
rm "${TARGET_FILE}"
echo -e "${GREEN}✓ Removed FlowMonitor.kt${NC}"

# Try to remove the debug directory if empty
if rmdir "${TARGET_DIR}" 2>/dev/null; then
    echo -e "${GREEN}✓ Removed empty debug directory${NC}"
fi

echo ""
echo -e "${YELLOW}⚠ Remember to:${NC}"
echo "  1. Remove .monitor() calls from your code"
echo "  2. Remove the import: import protect.card_locker.debug.monitor"
echo "  3. Rebuild your app"
echo ""
echo "Check what needs reverting:"
echo "  ${YELLOW}cd ${ANDROID_PROJECT}${NC}"
echo "  ${YELLOW}git diff${NC}"
