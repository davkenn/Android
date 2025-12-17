#!/bin/bash
# Open a loyalty card directly in edit mode
# Usage: ./open_card.sh [card_id]
#   or:  ./open_card.sh new

ADB="/Users/dkenn2/Library/Android/sdk/platform-tools/adb"
PACKAGE="me.hackerchick.catima.debug"
ACTIVITY="protect.card_locker.LoyaltyCardEditActivity"

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Check if card ID provided
if [ -z "$1" ]; then
    echo "Available cards:"
    "$ADB" shell "run-as $PACKAGE sqlite3 /data/data/$PACKAGE/databases/Catima.db 'SELECT _id, store FROM cards;'"
    echo ""
    echo "Usage:"
    echo "  ./open_card.sh [card_id]    - Open existing card"
    echo "  ./open_card.sh new          - Create new card"
    echo ""
    echo "Example: ./open_card.sh 1"
    exit 0
fi

if [ "$1" = "new" ]; then
    echo -e "${BLUE}🆕${NC} Opening new card screen..."
    "$ADB" shell am start -n "$PACKAGE/$ACTIVITY"
else
    echo -e "${BLUE}📝${NC} Opening card ID: $1"
    "$ADB" shell am start -n "$PACKAGE/$ACTIVITY" \
        --ei "id" "$1" \
        --ez "update" true
fi

echo -e "${GREEN}✓${NC} Card opened"
