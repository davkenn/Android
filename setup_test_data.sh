#!/bin/bash
# Setup Test Data for Flow Recording Sessions
# Run this before ./record_session.sh to have populated app

set -e

ADB="/Users/dkenn2/Library/Android/sdk/platform-tools/adb"
PACKAGE="me.hackerchick.catima.debug"
DB_PATH="/data/data/${PACKAGE}/databases/Catima.db"

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo ""
echo "═══════════════════════════════════════════"
echo "  Flow Recording Test Data Setup"
echo "═══════════════════════════════════════════"
echo ""

# Check device
if ! "$ADB" devices | grep -q "device$"; then
    echo "❌ No device connected"
    exit 1
fi

echo -e "${GREEN}✓${NC} Device connected"

# Stop app
echo -e "${BLUE}⏸${NC}  Stopping Catima..."
"$ADB" shell am force-stop "$PACKAGE" 2>/dev/null
sleep 1

# Clear existing data
echo -e "${BLUE}🗑${NC}  Clearing existing data..."
"$ADB" shell "run-as $PACKAGE sqlite3 $DB_PATH 'DELETE FROM cards; DELETE FROM cardsGroups; DELETE FROM groups; DELETE FROM fts;'"

# Insert test groups (_id is the group name itself)
echo -e "${BLUE}📁${NC} Creating groups..."
"$ADB" shell "run-as $PACKAGE sqlite3 $DB_PATH \"INSERT INTO groups (_id, orderId) VALUES ('Retail', 1), ('Food', 2), ('Health', 3);\""

# Insert test cards with various barcode types
echo -e "${BLUE}💳${NC} Creating test cards..."
"$ADB" shell "run-as $PACKAGE sqlite3 $DB_PATH \"
INSERT INTO cards (store, note, cardid, barcodeid, barcodetype, headercolor, starstatus, lastused, balance, zoomlevel) VALUES
('Starbucks', 'Rewards member since 2020', '1234567890', NULL, 'QR_CODE', 5025651, 1, strftime('%s', 'now'), '25.50', 100),
('Target', 'RedCard 5% discount', '9876543210', NULL, 'CODE_128', 12451629, 0, strftime('%s', 'now'), '0', 100),
('Whole Foods', 'Prime member discount', 'WF-123456789', NULL, 'CODE_39', 5763719, 0, strftime('%s', 'now'), '0', 100),
('CVS Pharmacy', 'ExtraCare card', '4455667788', NULL, 'EAN_13', 14423100, 1, strftime('%s', 'now'), '0', 100),
('Best Buy', 'Rewards zone member', 'BB987654321', NULL, 'AZTEC', 62079, 0, strftime('%s', 'now'), '150.00', 100),
('Planet Fitness', 'Black card membership', 'PF-1122334455', NULL, 'PDF_417', 5025651, 0, strftime('%s', 'now'), '0', 100);

INSERT INTO fts (docid, store, note) VALUES
(1, 'Starbucks', 'Rewards member since 2020'),
(2, 'Target', 'RedCard 5% discount'),
(3, 'Whole Foods', 'Prime member discount'),
(4, 'CVS Pharmacy', 'ExtraCare card'),
(5, 'Best Buy', 'Rewards zone member'),
(6, 'Planet Fitness', 'Black card membership');
\""

# Link cards to groups
echo -e "${BLUE}🔗${NC} Linking cards to groups..."
"$ADB" shell "run-as $PACKAGE sqlite3 $DB_PATH \"
INSERT INTO cardsGroups (cardId, groupId) VALUES
(1, 'Food'),
(2, 'Retail'),
(3, 'Food'),
(4, 'Health'),
(5, 'Retail'),
(6, 'Health');
\""

echo -e "${GREEN}✓${NC} Test data loaded successfully"
echo ""
echo -e "${BLUE}📊 Created:${NC}"
echo "  • 6 loyalty cards (various barcode types)"
echo "  • 3 groups (Retail, Food, Health)"
echo "  • 2 starred cards"
echo ""

# Launch app
echo -e "${BLUE}🚀${NC} Launching Catima..."
"$ADB" shell am start -n "$PACKAGE/protect.card_locker.MainActivity" > /dev/null 2>&1

echo -e "${GREEN}✅ Ready for Flow recording!${NC}"
echo ""
echo "Next: Run ./record_session.sh and interact with the app"
echo ""
