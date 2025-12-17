#!/bin/bash

# Inject Test Data Directly into Catima Database
# Fully automated - no UI interaction needed

set -e

# Configuration
ADB="/Users/dkenn2/Library/Android/sdk/platform-tools/adb"
PACKAGE="me.hackerchick.catima.debug"  # Debug build package
DB_PATH="/data/data/${PACKAGE}/databases/Catima.db"

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo ""
echo "═══════════════════════════════════════════"
echo "  Catima Test Data Injector"
echo "═══════════════════════════════════════════"
echo ""

# Check if device is connected
if ! "$ADB" devices | grep -q "device$"; then
    echo "❌ Error: No Android device connected"
    exit 1
fi

echo -e "${GREEN}✓${NC} Device connected"

# Check if app is installed
if ! "$ADB" shell pm list packages | grep -q "^package:${PACKAGE}$"; then
    echo "❌ Error: Catima not installed on device"
    exit 1
fi

echo -e "${GREEN}✓${NC} Catima installed"

# Stop the app to ensure database isn't locked
echo -e "${BLUE}⏸${NC}  Stopping Catima..."
"$ADB" shell am force-stop "$PACKAGE" 2>/dev/null || true
sleep 1

# Generate SQL to insert test cards
read -r -d '' SQL_COMMANDS << 'EOF' || true
-- Clear existing data (optional - comment out to append instead)
DELETE FROM LoyaltyCards;
DELETE FROM LoyaltyCardGroups;
DELETE FROM CardGroups;

-- Insert groups
INSERT INTO CardGroups (_id, groupName) VALUES
    ('Health', 'Health'),
    ('Food', 'Food'),
    ('Fashion', 'Fashion');

-- Insert cards with various barcode types
INSERT INTO LoyaltyCards (_id, store, note, validfrom, expiry, balance, balancetype, cardid, barcodeid, headercolor, barcodetype, starstatus, lastused, archiveStatus, zoomLevel, zoomLevelWidth) VALUES
    (1, 'Starbucks', 'My rewards card', NULL, NULL, 25.50, 'USD', '1234567890', NULL, 1256210, 'QR_CODE', 0, strftime('%s', 'now'), 0, 100, 100),
    (2, 'Target', 'RedCard discount', NULL, strftime('%s', 'now', '+1 year'), 0, NULL, '9876543210', NULL, -5317, 'CODE_128', 1, strftime('%s', 'now'), 0, 100, 100),
    (3, 'Whole Foods', 'Organic groceries', NULL, NULL, 0, NULL, 'WF123456', NULL, -9977996, 'EAN_13', 0, strftime('%s', 'now'), 0, 100, 100),
    (4, 'CVS Pharmacy', 'Extra care card', NULL, NULL, 0, NULL, '9988776655', NULL, -10902850, 'CODE_39', 0, strftime('%s', 'now'), 0, 100, 100),
    (5, 'Nike Store', 'Member card', NULL, NULL, 150, 'USD', 'NIKE9876', NULL, 1256210, 'AZTEC', 0, strftime('%s', 'now'), 0, 100, 100),
    (6, 'Planet Fitness', 'Black card membership', strftime('%s', 'now', '-1 year'), strftime('%s', 'now', '+1 year'), 0, NULL, 'PF123456789', NULL, -5317, 'PDF_417', 1, strftime('%s', 'now'), 0, 100, 100);

-- Link cards to groups
INSERT INTO LoyaltyCardGroups (cardId, groupId) VALUES
    (1, 'Food'),
    (2, 'Fashion'),
    (3, 'Food'),
    (4, 'Health'),
    (5, 'Fashion'),
    (6, 'Health');
EOF

echo -e "${BLUE}💾${NC} Injecting test data into database..."

# Execute SQL commands on device
"$ADB" shell "su root sqlite3 ${DB_PATH} <<< \"${SQL_COMMANDS}\"" 2>/dev/null || \
"$ADB" shell "run-as ${PACKAGE} sqlite3 ${DB_PATH} <<< \"${SQL_COMMANDS}\"" 2>/dev/null || {
    echo -e "${YELLOW}⚠${NC}  Direct DB access failed (device may not be rooted)"
    echo ""
    echo "Alternative: Using CSV import method"
    echo "Run: ./load_test_data.sh"
    exit 1
}

echo -e "${GREEN}✓${NC} Test data injected successfully"
echo ""
echo -e "${BLUE}📊 Inserted:${NC}"
echo "  • 6 loyalty cards with different barcode types"
echo "  • 3 groups (Health, Food, Fashion)"
echo "  • Some starred, some with expiry dates"
echo ""

# Start the app
echo -e "${BLUE}🚀${NC} Launching Catima..."
"$ADB" shell am start -n "${PACKAGE}/.MainActivity" > /dev/null 2>&1

echo -e "${GREEN}✅ Done!${NC} Catima should now show your test cards"
echo ""
