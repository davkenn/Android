#!/usr/bin/env python3
"""
Automated Test Data Loader for Catima
Loads test loyalty cards into a running Catima app
Works on both rooted and non-rooted devices
"""

import subprocess
import sys
import time
from pathlib import Path

# Configuration
ADB_PATH = "/Users/dkenn2/Library/Android/sdk/platform-tools/adb"
PACKAGE = "me.hackerchick.catima.debug"  # Debug build package name
TEST_CSV = "app/src/test/res/protect/card_locker/catima_v2.csv"
DEVICE_CSV_PATH = "/sdcard/Download/catima_test_auto.csv"

# Colors
GREEN = '\033[0;32m'
BLUE = '\033[0;34m'
YELLOW = '\033[1;33m'
RED = '\033[0;31m'
NC = '\033[0m'


def run_adb(command: list[str], check=True, capture_output=True) -> subprocess.CompletedProcess:
    """Run an adb command"""
    return subprocess.run([ADB_PATH] + command, check=check, capture_output=capture_output, text=True)


def check_device():
    """Check if device is connected"""
    result = run_adb(['devices'])
    if '\tdevice' not in result.stdout:
        print(f"{RED}❌ No Android device connected{NC}")
        sys.exit(1)
    print(f"{GREEN}✓{NC} Device connected")


def check_app_installed():
    """Check if Catima is installed"""
    result = run_adb(['shell', 'pm', 'list', 'packages'])
    if f'package:{PACKAGE}' not in result.stdout:
        print(f"{RED}❌ Catima not installed{NC}")
        sys.exit(1)
    print(f"{GREEN}✓{NC} Catima installed")


def push_csv():
    """Push test CSV to device"""
    if not Path(TEST_CSV).exists():
        print(f"{RED}❌ Test CSV not found: {TEST_CSV}{NC}")
        sys.exit(1)

    print(f"{BLUE}📤{NC} Pushing test CSV to device...")
    run_adb(['push', TEST_CSV, DEVICE_CSV_PATH])
    print(f"{GREEN}✓{NC} CSV uploaded to device")


def trigger_import_via_intent():
    """Trigger import using Android intent"""
    print(f"{BLUE}📥{NC} Triggering import via intent...")

    # Try to trigger import intent if Catima supports it
    result = run_adb([
        'shell', 'am', 'start',
        '-a', 'android.intent.action.VIEW',
        '-d', f'file://{DEVICE_CSV_PATH}',
        '-t', 'text/csv',
        PACKAGE
    ], check=False)

    if result.returncode == 0:
        print(f"{GREEN}✓{NC} Import triggered")
        return True
    else:
        return False


def simulate_ui_import():
    """Simulate UI interaction to import the file"""
    print(f"{BLUE}🤖{NC} Simulating UI import...")
    print(f"{YELLOW}⚠{NC}  This requires the app to be open at main screen")
    print(f"{YELLOW}⚠{NC}  Manual step: Tap Menu → Import/Export → Import")
    print(f"{YELLOW}⚠{NC}  Then select: catima_test_auto.csv")
    print("")
    print(f"File location: {DEVICE_CSV_PATH}")


def try_direct_db_insert():
    """Try to insert directly into database (requires root or debuggable app)"""
    print(f"{BLUE}💾{NC} Attempting direct database insertion...")

    db_path = f"/data/data/{PACKAGE}/databases/Catima.db"

    sql = """
    DELETE FROM LoyaltyCards;
    DELETE FROM LoyaltyCardGroups;
    DELETE FROM CardGroups;

    INSERT INTO CardGroups (_id, groupName) VALUES
        ('Test', 'Test Cards');

    INSERT INTO LoyaltyCards (_id, store, note, cardid, barcodeid, headercolor, barcodetype, starstatus, lastused, archiveStatus, zoomLevel) VALUES
        (1, 'Test Store 1', 'QR Code card', '1234567890', NULL, 1256210, 'QR_CODE', 0, strftime('%s', 'now'), 0, 100),
        (2, 'Test Store 2', 'Barcode card', '9876543210', NULL, -5317, 'CODE_128', 1, strftime('%s', 'now'), 0, 100);

    INSERT INTO LoyaltyCardGroups (cardId, groupId) VALUES (1, 'Test'), (2, 'Test');
    """

    # Stop app first
    run_adb(['shell', 'am', 'force-stop', PACKAGE], check=False)
    time.sleep(1)

    # Try with run-as (works on debuggable builds)
    result = run_adb([
        'shell', f'run-as {PACKAGE} sqlite3 {db_path} "{sql}"'
    ], check=False)

    if result.returncode == 0:
        print(f"{GREEN}✓{NC} Direct database insertion successful")
        run_adb(['shell', 'am', 'start', '-n', f'{PACKAGE}/.MainActivity'], check=False)
        return True
    else:
        print(f"{YELLOW}⚠{NC}  Direct DB access not available (app not debuggable or no root)")
        return False


def main():
    print()
    print("═══════════════════════════════════════════")
    print("  Catima Test Data Loader")
    print("═══════════════════════════════════════════")
    print()

    check_device()
    check_app_installed()

    # Try methods in order of preference
    # Method 1: Direct DB insertion (fastest, but requires debug build)
    if try_direct_db_insert():
        print()
        print(f"{GREEN}✅ Success!{NC} Test cards loaded")
        print("   Catima should now show 2 test cards")
        return

    # Method 2: Push CSV and trigger import intent
    push_csv()
    if trigger_import_via_intent():
        print()
        print(f"{GREEN}✅ Import triggered!{NC}")
        print("   Complete the import in the app UI")
        return

    # Method 3: Manual UI import (fallback)
    print()
    print(f"{YELLOW}ℹ{NC}  Automatic import not available")
    simulate_ui_import()


if __name__ == '__main__':
    try:
        main()
    except KeyboardInterrupt:
        print(f"\n{YELLOW}Cancelled{NC}")
        sys.exit(1)
    except subprocess.CalledProcessError as e:
        print(f"\n{RED}❌ ADB command failed: {e}{NC}")
        sys.exit(1)
