# Integration Example

This guide shows exactly how to integrate FlowMonitor.kt into your Android project for recording sessions.

## Step 1: Copy FlowMonitor.kt

```bash
# From the flow-recorder directory
cp FlowMonitor.kt ~/StudioProjects/Android/app/src/main/java/protect/card_locker/debug/
```

**Important**: Create the `debug` directory if it doesn't exist:
```bash
mkdir -p ~/StudioProjects/Android/app/src/main/java/protect/card_locker/debug/
```

---

## Step 2: Update Your Activity

Here's how to modify `LoyaltyCardEditActivity.kt` to use the monitor:

### Before (your current code):

```kotlin
lifecycleScope.launch {
    viewModel.cardState.collectLatest { state ->
        when (state) {
            is CardLoadState.Loading -> {
                Log.d(TAG, "Loading card data...")
            }
            // ... rest of when
        }
    }
}
```

### After (with monitoring):

```kotlin
import protect.card_locker.debug.monitor  // Add this import

lifecycleScope.launch {
    viewModel.cardState
        .monitor("cardState")  // Add monitoring here
        .collectLatest { state ->
            when (state) {
                is CardLoadState.Loading -> {
                    Log.d(TAG, "Loading card data...")
                }
                // ... rest of when
            }
        }
}
```

**Note**: You can **replace** your existing `.monitor("FlowInspector")` with the new one from FlowMonitor.kt, or keep both (the new one has better JSON logging).

---

## Step 3: Update Your ViewModel (Optional)

You can also monitor at the ViewModel level for cleaner code:

### In `LoyaltyCardEditActivityViewModel.kt`:

```kotlin
import protect.card_locker.debug.monitor  // Add import

class LoyaltyCardEditActivityViewModel(...) : ViewModel() {

    // Option 1: Monitor at declaration (affects ALL collectors)
    val cardState = _cardState.asStateFlow().monitor("VM_cardState")
    val saveState = _saveState.asStateFlow().monitor("VM_saveState")
    val uiEvents = _uiEvents.asSharedFlow().monitor("VM_uiEvents")

    // Option 2: Keep original declarations, monitor in Activity
    // (Better if you only want to record specific scenarios)
}
```

**Recommendation**: Monitor in the Activity for more control over when recording happens.

---

## Step 4: Your Current Code Integration

Based on the code you showed me, here's the **exact change** to make:

### File: `LoyaltyCardEditActivity.kt`

**Current code (line 427-432):**
```kotlin
lifecycleScope.launch {
    viewModel.cardState
        .monitor("FlowInspector")  // Your current monitor
        .collectLatest { state ->
```

**Change to:**
```kotlin
import protect.card_locker.debug.monitor  // Add at top of file

lifecycleScope.launch {
    viewModel.cardState
        .monitor("cardState")  // JSON-logging monitor
        .collectLatest { state ->
```

**Or keep both for comparison:**
```kotlin
lifecycleScope.launch {
    viewModel.cardState
        .monitor("FlowInspector")     // Your original (logs changes)
        .monitor("cardState")          // JSON logger (for recording)
        .collectLatest { state ->
```

---

## Step 5: Rebuild and Record

```bash
# 1. Rebuild the app in Android Studio
# 2. Install on device/emulator
# 3. Run recording script
cd ~/AndroidTestingTools/flow-recorder
./record_session.sh

# 4. Interact with the app
# 5. Press Ctrl+C to stop
```

---

## Step 6: Remove Before Committing

```bash
# Remove the temporary monitor
rm ~/StudioProjects/Android/app/src/main/java/protect/card_locker/debug/FlowMonitor.kt

# Also remove the debug directory if empty
rmdir ~/StudioProjects/Android/app/src/main/java/protect/card_locker/debug/ 2>/dev/null || true

# Remove the import and .monitor() call from your Activity
# (Use git diff to see what to revert)
cd ~/StudioProjects/Android
git diff app/src/main/java/protect/card_locker/LoyaltyCardEditActivity.kt
```

---

## Multiple Flow Monitoring

If you want to record multiple flows at once:

```kotlin
// In Activity
lifecycleScope.launch {
    // Monitor card state
    launch {
        viewModel.cardState
            .monitor("cardState")
            .collectLatest { /* ... */ }
    }

    // Monitor save state
    launch {
        viewModel.saveState
            .monitor("saveState")
            .collectLatest { /* ... */ }
    }

    // Monitor UI events
    launch {
        viewModel.uiEvents
            .monitor("uiEvents")
            .collect { /* ... */ }
    }
}
```

This will capture all three flows in a single recording session!

---

## Verifying It Works

After adding the monitor and rebuilding:

```bash
# Clear logs and watch for flow events
adb logcat -c
adb logcat -s FlowRecorder:D

# You should see lines like:
# FlowRecorder: FLOW_EVENT:{"flow":"cardState","timestamp":1702683099730,"value":"Loading"}
# FlowRecorder: FLOW_EVENT:{"flow":"cardState","timestamp":1702683100135,"value":"Success(...)"}
```

If you see these, you're ready to record!

---

## Troubleshooting

### "Cannot resolve symbol 'monitor'"
- Check that FlowMonitor.kt is in `app/src/main/java/protect/card_locker/debug/`
- Verify the package declaration: `package protect.card_locker.debug`
- Sync Gradle / Rebuild project

### "No flow events in logcat"
- Verify the monitor is actually called (not skipped by conditional)
- Check that you're importing the right `monitor` function
- Make sure you interacted with features that trigger flow emissions

### App won't build
- Check for syntax errors in the import statement
- Ensure `org.json.JSONObject` is available (should be in Android SDK)
- Try File → Invalidate Caches / Restart in Android Studio

---

## Quick Reference

```bash
# Setup (one time)
cp ~/AndroidTestingTools/flow-recorder/FlowMonitor.kt \
   ~/StudioProjects/Android/app/src/main/java/protect/card_locker/debug/

# Add .monitor() calls to your code
# Rebuild the app

# Record
cd ~/AndroidTestingTools/flow-recorder
./record_session.sh

# Generate fixture
python3 parse_flows.py recordings/flow_recording_*.txt recordings/my_test.json --generate-fixture

# Copy fixture to tests
cp recordings/my_test.kt ~/StudioProjects/Android/app/src/test/java/protect/card_locker/fixtures/

# Cleanup
rm ~/StudioProjects/Android/app/src/main/java/protect/card_locker/debug/FlowMonitor.kt
# Revert .monitor() calls in your code
```
