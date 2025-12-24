# Flow Recording Session Guide - Barcode Loading Investigation

## Goal
Capture and compare two scenarios:
1. **Broken**: Open card from view activity → edit activity (barcode doesn't load)
2. **Working**: In edit activity, change barcode type dropdown (barcode loads)

## What We're Tracking

The following events will be logged with JSON payloads:

| Event | Location | What It Tells Us |
|-------|----------|------------------|
| `FLOW_EVENT` (cardState) | Activity | When CardLoadState changes (Loading → Success) |
| `ACTIVITY_GENERATE_BARCODE_CALLED` | Activity | When Activity tries to trigger barcode generation |
| `WAITING_FOR_LAYOUT` | Activity | When ViewTreeObserver is waiting for dimensions |
| `LAYOUT_COMPLETE` | Activity | When onGlobalLayout fires with dimensions |
| `USING_EXISTING_DIMENSIONS` | Activity | When dimensions already available |
| `BARCODE_DIMENSIONS_UPDATE` | ViewModel | When updateBarcodeDimensions() is called successfully |
| `BARCODE_DIMENSIONS_INVALID` | ViewModel | When dimensions are invalid (0 or negative) |
| `BARCODE_GENERATION_TRIGGER` | ViewModel | When combine() flow decides to generate |
| `BARCODE_GENERATION_START` | ViewModel | When generateBarcode() begins |
| `BARCODE_GENERATION_COMPLETE` | ViewModel | When barcode bitmap is ready |
| `BARCODE_GENERATION_ERROR` | ViewModel | If generation fails |

## Step 1: Build Recording Variant

```bash
# Clean build
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew clean

# Build recording debug variant
./gradlew assembleFossRecordingDebug

# Install to device/emulator
./gradlew installFossRecordingDebug
```

**Verify**: App name should show "(recording)" suffix

## Step 2: Create Test Data

Create a few loyalty cards with barcodes using the database directly:

```bash
# Option 1: Use adb shell to insert via SQL
adb shell

# Open the app's database
cd /data/data/me.hackerchick.catima.recording/databases/
sqlite3 Catima.db

# Insert test cards with barcodes
INSERT INTO LoyaltyCards (store, note, cardId, barcodeType, headerColor, starStatus)
VALUES ('Target', 'Test card with Aztec barcode', 'ABC123', 'AZTEC', -65536, 0);

INSERT INTO LoyaltyCards (store, note, cardId, barcodeType, headerColor, starStatus)
VALUES ('Walmart', 'Test card with QR code', 'QR123456', 'QR_CODE', -16711936, 0);

INSERT INTO LoyaltyCards (store, note, cardId, barcodeType, headerColor, starStatus)
VALUES ('Costco', 'Test card with EAN-13', '1234567890128', 'EAN_13', -16776961, 0);

.quit
exit
```

**Or Option 2**: Create cards manually in the app (faster):
1. Open app → "+" button
2. Enter store name (e.g., "Target")
3. Enter card ID (e.g., "ABC123")
4. Select barcode type (e.g., "Aztec")
5. Save
6. Repeat for 2-3 cards

## Step 3: Prepare for Recording

```bash
# Clear logcat to have clean output
adb logcat -c

# Start recording to a file (in a separate terminal window)
adb logcat -v time > ~/flow_recording_$(date +%Y%m%d_%H%M%S).log
```

**Keep this terminal open** while you interact with the app!

## Step 4: Perform Test Scenario

### Scenario A: Broken Flow (Barcode Doesn't Load on Initial Open)

1. **Open app** → Main screen with list of cards
2. **Tap a card with barcode** (e.g., "Target" with Aztec)
3. **View activity opens** → You should see the barcode displayed
4. **Tap Edit button** (FAB or icon)
5. **Edit activity opens** → **Barcode layout should be GONE** (this is the bug!)
6. **Wait 2-3 seconds** to let any delayed generation complete
7. **Take mental note**: Barcode is NOT visible

### Scenario B: Working Flow (Barcode Loads When Changing Dropdown)

**Without leaving the edit activity:**

8. **Tap barcode type dropdown**
9. **Select "No Barcode"**
10. **Wait 1 second** → Barcode layout disappears (expected)
11. **Tap barcode type dropdown again**
12. **Select original barcode type** (e.g., "Aztec")
13. **Wait 1 second** → **Barcode appears!** (this works)

### Scenario C: Return to View and Edit Again

14. **Save the card** (or go back)
15. **Repeat Scenario A** with a different card to capture more data

## Step 5: Stop Recording

In the terminal with `adb logcat`:
- Press **Ctrl+C** to stop recording
- Note the filename (e.g., `flow_recording_20231223_143000.log`)

## Step 6: Filter Relevant Logs

```bash
# Extract only FlowRecorder events
grep "FlowRecorder" ~/flow_recording_*.log > ~/flow_recording_filtered.log

# Also extract the specific flow monitoring events
grep "FLOW_EVENT" ~/flow_recording_*.log >> ~/flow_recording_filtered.log
```

## Step 7: Analyze the Logs

### What to Look For

#### In the **Broken Flow** (edit activity opens, barcode doesn't load):

**Expected sequence**:
1. `FLOW_EVENT` - cardState becomes Success
2. `ACTIVITY_GENERATE_BARCODE_CALLED` - Activity calls generateBarcode()
3. Either:
   - `WAITING_FOR_LAYOUT` → `LAYOUT_COMPLETE` → `BARCODE_DIMENSIONS_UPDATE`
   - OR `USING_EXISTING_DIMENSIONS` → `BARCODE_DIMENSIONS_UPDATE`
4. `BARCODE_GENERATION_TRIGGER` - combine() flow triggers
5. `BARCODE_GENERATION_START`
6. `BARCODE_GENERATION_COMPLETE`
7. `FLOW_EVENT` - cardState updates with BarcodeState.Generated

**Possible Issues to Check**:
- ❌ `ACTIVITY_GENERATE_BARCODE_CALLED` **never happens** → Activity didn't call generateBarcode()
- ❌ `WAITING_FOR_LAYOUT` happens but `LAYOUT_COMPLETE` **never fires** → ViewTreeObserver listener didn't trigger
- ❌ `BARCODE_DIMENSIONS_UPDATE` happens but `BARCODE_GENERATION_TRIGGER` **never fires** → Reactive flow not triggering
- ❌ `BARCODE_GENERATION_TRIGGER` happens but takes >1 second → Debounce or coroutine delay issue

#### In the **Working Flow** (changing dropdown):

**Expected sequence**:
1. User changes dropdown
2. `BARCODE_DIMENSIONS_UPDATE` might fire again (or might use cached)
3. `BARCODE_GENERATION_TRIGGER`
4. `BARCODE_GENERATION_START`
5. `BARCODE_GENERATION_COMPLETE`
6. `FLOW_EVENT` - cardState updates with new barcode

### Timeline Analysis

Create a timeline showing time deltas:

```
T+0ms    FLOW_EVENT: cardState=Success
T+50ms   ACTIVITY_GENERATE_BARCODE_CALLED: width=0, height=0
T+52ms   WAITING_FOR_LAYOUT
T+???    LAYOUT_COMPLETE: width=1080, height=400  ← Does this ever fire?
T+???    BARCODE_DIMENSIONS_UPDATE
T+???    BARCODE_GENERATION_TRIGGER
T+???    BARCODE_GENERATION_COMPLETE
```

## Step 8: Share Results

Copy the filtered log and paste it here, organized by scenario:

```
=== BROKEN FLOW (edit activity opens, barcode missing) ===
[paste relevant logs]

=== WORKING FLOW (dropdown change, barcode appears) ===
[paste relevant logs]
```

## Common Findings

### If LAYOUT_COMPLETE never fires:
- **Cause**: ViewTreeObserver.onGlobalLayout() doesn't fire reliably
- **Solution**: We might need to proactively call updateBarcodeDimensions() when bindCardToUi() is called, using measured dimensions or fallback values

### If BARCODE_GENERATION_TRIGGER doesn't fire:
- **Cause**: Reactive combine() flow isn't emitting
- **Possible reasons**:
  - cardState and barcodeDimensions aren't both available
  - distinctUntilChanged() is filtering it out as duplicate
  - Coroutine dispatcher not running

### If dimensions are 0:
- **Cause**: View hasn't been measured yet
- **Solution**: Force a measure pass or use fixed dimensions for initial generation

## Quick Analysis Commands

```bash
# Count events in broken vs working flow
grep "T+.*BARCODE" flow_recording_filtered.log | wc -l

# Show timeline with timestamps
grep "FlowRecorder" flow_recording_filtered.log | awk '{print $2, $0}'

# Extract just the JSON payloads
grep -o '{.*}' flow_recording_filtered.log | jq .

# Show all dimensions updates
grep "BARCODE_DIMENSIONS" flow_recording_filtered.log
```

## Expected Outcome

After analyzing, we should know:
1. **Where the flow breaks** in the initial load scenario
2. **Why it works** when changing the dropdown
3. **The exact timing** of when events fire (or don't fire)
4. **A concrete fix** based on the difference between working and broken flows

---

**Ready to begin!** Follow the steps above and capture the logs. The detailed event tracking will show us exactly what's different between the two scenarios.
