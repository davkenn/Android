#!/bin/bash

# Flow Recorder Setup Script
# Automates copying FlowMonitor.kt into your Android project

set -e

# Configuration - edit these paths if needed
ANDROID_PROJECT="${HOME}/StudioProjects/Android"
DEBUG_PACKAGE="app/src/main/java/protect/card_locker/debug"

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo ""
echo "═══════════════════════════════════════════════"
echo "   Flow Recorder Setup"
echo "═══════════════════════════════════════════════"
echo ""

# Check if Android project exists
if [ ! -d "${ANDROID_PROJECT}" ]; then
    echo -e "${RED}✗ Android project not found at: ${ANDROID_PROJECT}${NC}"
    echo "Please edit setup.sh and set ANDROID_PROJECT to your project path"
    exit 1
fi

TARGET_DIR="${ANDROID_PROJECT}/${DEBUG_PACKAGE}"
TARGET_FILE="${TARGET_DIR}/FlowMonitor.kt"

# Check if already installed
if [ -f "${TARGET_FILE}" ]; then
    echo -e "${YELLOW}⚠ FlowMonitor.kt already exists${NC}"
    echo "Location: ${TARGET_FILE}"
    echo ""
    read -p "Overwrite? (y/n) " -n 1 -r
    echo ""
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Setup cancelled."
        exit 0
    fi
fi

# Create debug directory
echo -e "${BLUE}📁 Creating debug package...${NC}"
mkdir -p "${TARGET_DIR}"

# Copy FlowMonitor.kt
echo -e "${BLUE}📋 Copying FlowMonitor.kt...${NC}"
cp FlowMonitor.kt "${TARGET_FILE}"

echo -e "${GREEN}✓ Setup complete!${NC}"
echo ""
echo "Next steps:"
echo "  1. Add import to your Activity:"
echo "     ${BLUE}import protect.card_locker.debug.monitor${NC}"
echo ""
echo "  2. Add .monitor() to your flows:"
echo "     ${BLUE}viewModel.cardState.monitor(\"cardState\").collectLatest { ... }${NC}"
echo ""
echo "  3. Rebuild your app in Android Studio"
echo ""
echo "  4. Run a recording session:"
echo "     ${BLUE}./record_session.sh${NC}"
echo ""
echo "  5. When done, remove the monitor:"
echo "     ${BLUE}./cleanup.sh${NC}"
echo ""

echo "📖 See INTEGRATION_EXAMPLE.md for detailed instructions"
