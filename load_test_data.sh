#!/bin/bash

# Load Test Data into Catima
# Pushes sample CSV to device and launches import intent

set -e

# Configuration
ADB="/Users/dkenn2/Library/Android/sdk/platform-tools/adb"

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

TEST_CSV="app/src/test/res/protect/card_locker/catima_v2.csv"
DEVICE_PATH="/sdcard/Download/catima_test_cards.csv"

echo -e "${BLUE}Loading test cards into Catima...${NC}"

# Check if device is connected
if ! "$ADB" devices | grep -q "device$"; then
    echo "Error: No Android device connected"
    exit 1
fi

# Push CSV to device
echo -e "${BLUE}Pushing test CSV to device...${NC}"
"$ADB" push "$TEST_CSV" "$DEVICE_PATH"

echo -e "${GREEN}✓ Test cards file ready at: $DEVICE_PATH${NC}"
echo ""
echo "Now in the app:"
echo "  1. Open Catima"
echo "  2. Menu (⋮) → Import/Export"
echo "  3. Import → Select 'catima_test_cards.csv'"
echo ""
echo "This will load 6 cards with various barcode types for testing"
