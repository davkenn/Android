#!/bin/bash

# Flow Recording Session Script
# Automates the process of capturing Flow emissions via ADB logcat

set -e  # Exit on error

# Configuration
ADB="/Users/dkenn2/Library/Android/sdk/platform-tools/adb"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RECORDINGS_DIR="${SCRIPT_DIR}/recordings"
SESSION_ID="flow_recording_$(date +%Y%m%d_%H%M%S)"
RAW_OUTPUT="${RECORDINGS_DIR}/${SESSION_ID}.txt"
JSON_OUTPUT="${RECORDINGS_DIR}/${SESSION_ID}.json"

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Ensure recordings directory exists
mkdir -p "${RECORDINGS_DIR}"

# Banner
echo ""
echo "═══════════════════════════════════════════════"
echo "   Flow Recording Tool for Android Testing"
echo "═══════════════════════════════════════════════"
echo ""

# Check if device is connected
if ! "$ADB" devices | grep -q "device$"; then
    echo -e "${RED}✗ Error: No Android device connected${NC}"
    echo "Please connect a device or start an emulator"
    exit 1
fi

DEVICE_NAME=$("$ADB" shell getprop ro.product.model 2>/dev/null | tr -d '\r')
echo -e "${GREEN}✓${NC} Connected to: ${DEVICE_NAME}"

# Check if FlowMonitor is integrated
echo ""
echo -e "${YELLOW}Pre-flight checklist:${NC}"
echo "  1. Have you copied FlowMonitor.kt into your project?"
echo "  2. Have you added .monitor() calls to your flows?"
echo "  3. Have you rebuilt and installed the app?"
echo ""
read -p "Continue recording? (y/n) " -n 1 -r
echo ""

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Recording cancelled."
    exit 0
fi

echo ""
echo -e "${BLUE}🎬 Starting recording session: ${SESSION_ID}${NC}"
echo -e "${BLUE}📁 Output: ${RAW_OUTPUT}${NC}"
echo ""
echo -e "${GREEN}👆 Interact with your app now...${NC}"
echo -e "${YELLOW}⏹️  Press Ctrl+C when done${NC}"
echo ""

# Cleanup function on exit
cleanup() {
    echo ""
    echo ""
    echo -e "${BLUE}🛑 Stopping recording...${NC}"

    # Check if we captured any data
    if [ -f "${RAW_OUTPUT}" ] && [ -s "${RAW_OUTPUT}" ]; then
        EMISSION_COUNT=$(grep -c "FLOW_EVENT:" "${RAW_OUTPUT}" || echo "0")

        if [ "${EMISSION_COUNT}" -gt "0" ]; then
            echo -e "${GREEN}✓${NC} Captured ${EMISSION_COUNT} flow emissions"
            echo ""
            echo -e "${BLUE}📊 Parsing logs to JSON...${NC}"

            # Run parser if it exists
            if [ -f "${SCRIPT_DIR}/parse_flows.py" ]; then
                python3 "${SCRIPT_DIR}/parse_flows.py" "${RAW_OUTPUT}" "${JSON_OUTPUT}"
            else
                echo -e "${YELLOW}⚠ Warning: parse_flows.py not found${NC}"
                echo "Raw logs saved to: ${RAW_OUTPUT}"
            fi
        else
            echo -e "${RED}✗ No flow emissions captured${NC}"
            echo ""
            echo "Troubleshooting:"
            echo "  1. Check if FlowMonitor.kt is in your project"
            echo "  2. Verify .monitor() is called on your flows"
            echo "  3. Make sure you interacted with the monitored features"
            echo '  4. Check if app crashed: "$ADB" logcat | grep AndroidRuntime'
        fi
    else
        echo -e "${RED}✗ No data captured${NC}"
    fi

    echo ""
    echo -e "${GREEN}Session complete${NC}"
}

# Set trap to run cleanup on script exit
trap cleanup EXIT

# Clear logcat buffer and start recording
"$ADB" logcat -c
"$ADB" logcat -s FlowRecorder:D | tee "${RAW_OUTPUT}"
