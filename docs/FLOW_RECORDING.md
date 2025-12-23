# Flow Recording Guide

## Overview

Flow Recording is a testing tool that captures real Flow emissions during manual app usage to generate test fixtures. This approach allows you to:

1. **Record real user interactions** instead of manually writing expected values
2. **Generate comprehensive test data** from actual app behavior
3. **Catch regressions** by comparing recorded flows against test runs

## How It Works

```
┌─────────────┐      ┌──────────────┐      ┌─────────────┐
│ Run app in  │ ───> │ Interact     │ ───> │ Parse logs  │
│ recording   │      │ normally     │      │ to generate │
│ mode        │      │ (logs flows) │      │ test data   │
└─────────────┘      └──────────────┘      └─────────────┘
                                                    │
                                                    ▼
                                            ┌─────────────┐
                                            │ Write tests │
                                            │ using       │
                                            │ fixtures    │
                                            └─────────────┘
```

## Quick Start

### 1. Switch to Recording Build Variant

In Android Studio:
- **Build Variants panel** (bottom-left or View → Tool Windows → Build Variants)
- Select `fossRecordingDebug` or `gplayRecordingDebug`
- Sync project

### 2. Add Monitoring to Flows

The `.monitor()` extension is **only available in recording builds** (compile-time safety).

```kotlin
// In your Activity
lifecycleScope.launch {
    viewModel.cardState
        .monitor("cardState")  // Only compiles in recording variant!
        .collectLatest { state ->
            when (state) {
                is CardLoadState.Success -> bindCardToUi(state)
                // ...
            }
        }
}
```

### 3. Run the App & Perform Actions

1. Build and install the recording variant
2. Open the app
3. Perform the user journey you want to test (e.g., "edit card", "save card")
4. The app will log all flow emissions to Logcat

### 4. Capture & Parse Logs

```bash
# From project root
cd AndroidTestingTools/flow-recorder

# Capture current logcat (interactive session)
./record_session.sh

# Or parse existing logcat file
python3 parse_flows.py < my_captured_logcat.txt > test_data.json
```

This generates a JSON file with all recorded flow emissions:

```json
{
  "cardState": [
    {
      "timestamp": 1702683099730,
      "value": "Loading"
    },
    {
      "timestamp": 1702683099850,
      "value": "Success(loyaltyCard=LoyaltyCard(id=1, store='Target', ...), ...)"
    }
  ],
  "saveState": [
    {
      "timestamp": 1702683100200,
      "value": "Idle"
    }
  ]
}
```

### 5. Write Tests Using Recorded Data

Copy the `RecordingBasedTestTemplate.kt` and use the captured JSON:

```kotlin
@Test
fun `should load card and display success state`() = runTest {
    // Load recorded flow emissions from JSON
    val recordedFlows = loadRecordedFlows("edit_card_journey.json")

    // Assert your ViewModel produces the same emissions
    viewModel.cardState.test {
        viewModel.loadCard(cardId = 1)

        // Compare against recorded emissions
        assertEquals(recordedFlows["cardState"][0], awaitItem())
        assertEquals(recordedFlows["cardState"][1], awaitItem())

        cancelAndIgnoreRemainingEvents()
    }
}
```

## Compilation Safety

### ✅ Recording Builds (fossRecording/gplayRecording)

```kotlin
// ✅ Compiles successfully
viewModel.cardState
    .monitor("cardState")
    .collectLatest { ... }
```

Uses the real implementation from `app/src/recording/java/.../FlowMonitor.kt` which logs to Logcat.

### ❌ Standard Builds (fossStandard/gplayStandard)

```kotlin
// ✅ Also compiles successfully (no-op)
viewModel.cardState
    .monitor("cardState")  // Does nothing, optimized away
    .collectLatest { ... }
```

Uses the no-op stub from `app/src/standard/java/.../FlowMonitor.kt` which just returns the original flow.

### Why Two Implementations?

- **Standard builds**: Production-ready, zero overhead, no logging
- **Recording builds**: Debug-only, logs everything for test generation
- **Same source code**: You don't need to add/remove `.monitor()` calls when switching variants
- **Compile-time enforcement**: Can't accidentally ship logging code to production

## Advanced: Recording Complex State

### Bitmaps

The FlowMonitor automatically handles Bitmaps:

```kotlin
// In your ViewModel
data class Success(
    val images: Map<ImageLocationType, Bitmap?>,
    ...
)
```

Logs:
```json
{
  "images": {
    "icon": {
      "type": "Bitmap",
      "width": 100,
      "height": 100,
      "contentHash": "a1b2c3d4e5f6",
      "byteCount": 40000
    }
  }
}
```

The `contentHash` lets you verify "same pixels" without storing the entire image in test fixtures.

### Custom Types

For custom data classes, FlowMonitor uses `toString()`:

```kotlin
data class CardLoadState.Success(
    var loyaltyCard: LoyaltyCard,
    val allGroups: List<Group>,
    ...
)
```

Logs:
```json
{
  "value": "Success(loyaltyCard=LoyaltyCard(...), allGroups=[Group(...), ...])"
}
```

You'll need to parse these strings in your test fixture loader.

## Workflow Best Practices

### DO ✅

1. **Switch back to standard after recording**
   ```bash
   # After capturing logs, immediately switch back
   # to prevent accidental commits with .monitor() calls
   ```

2. **Record full user journeys**
   - Start from a clean state
   - Perform a complete flow (e.g., open edit screen → modify → save → close)
   - Capture all relevant state transitions

3. **Use descriptive flow names**
   ```kotlin
   .monitor("cardState")        // ✅ Good
   .monitor("saveState")        // ✅ Good
   .monitor("state1")           // ❌ Bad - unclear
   ```

4. **Version your recordings**
   ```
   recordings/
     edit_card_v1_2024_12_01.json
     save_card_v2_2024_12_15.json
   ```

### DON'T ❌

1. **Don't commit with recording variant active**
   - Easy to accidentally leave `.monitor()` calls in committed code
   - Switch back to `fossStandardDebug` before committing

2. **Don't record too many flows at once**
   - Start with 1-2 key flows
   - Add more as needed
   - Too much data makes parsing difficult

3. **Don't use recordings as the only tests**
   - Recordings test "what currently happens"
   - You still need unit tests for "what should happen"
   - Use recordings to **supplement** traditional tests

## Troubleshooting

### "Unresolved reference: monitor"

**Problem**: You're in a standard build variant

**Solution**:
```
Build Variants → fossRecordingDebug or gplayRecordingDebug → Sync
```

### No FLOW_EVENT logs appearing

**Problem**: LogCat filter or log level

**Solution**:
```bash
# In terminal (adb)
adb logcat | grep "FLOW_EVENT"

# In Android Studio Logcat
# Filter: "FlowRecorder"
# Level: Debug
```

### Parse script errors

**Problem**: Malformed JSON in logs

**Solution**:
- Check that you captured the full session (from app start to finish)
- Look for stack traces or errors in the logs that disrupted JSON output
- Try a fresh capture with a simpler user journey

## File Structure

```
app/src/
├── main/
│   └── java/
│       └── ... (no FlowMonitor here!)
│
├── standard/              # Production builds
│   └── java/
│       └── protect/card_locker/debug/
│           └── FlowMonitor.kt  (no-op stub)
│
└── recording/             # Debug/test builds only
    └── java/
        └── protect/card_locker/debug/
            └── FlowMonitor.kt  (real logging)
```

## Related Documentation

- [Testing Guide](TESTING_GUIDE.md) - General testing overview
- [RecordingBasedTestTemplate.kt](../app/src/test/java/protect/card_locker/templates/RecordingBasedTestTemplate.kt) - Test template using recordings
- [Flow Testing with Turbine](TESTING_GUIDE.md#flow-testing-with-turbine) - Manual flow testing

## Example Workflow

```bash
# 1. Switch to recording build
# (Android Studio: Build Variants → fossRecordingDebug)

# 2. Add monitoring in Activity
vim app/src/main/java/protect/card_locker/LoyaltyCardEditActivity.kt
# Add .monitor("cardState") to the flow collection

# 3. Build and install
./gradlew installFossRecordingDebug

# 4. Run app and perform actions
# (Open app, edit card, save, close)

# 5. Capture logs
cd AndroidTestingTools/flow-recorder
./record_session.sh
# ... interact with app ...
# Press Ctrl+C when done
# Output: session_2024_12_23_14_30_00.json

# 6. Switch back to standard
# (Android Studio: Build Variants → fossStandardDebug)

# 7. Write test using captured data
cp app/src/test/.../templates/RecordingBasedTestTemplate.kt \
   app/src/test/.../EditCardFlowTest.kt
# Load session_2024_12_23_14_30_00.json in your test

# 8. Run tests
./gradlew test --tests "*.EditCardFlowTest"
```

## Philosophy

**Traditional testing**: You predict what should happen and write expectations.

**Recording-based testing**: You capture what actually happens and verify it stays consistent.

Both approaches are valuable:
- **Unit tests**: Define correct behavior
- **Recording tests**: Catch unintended changes to existing behavior

Use recordings to:
- Bootstrap tests for complex flows
- Document current app behavior
- Detect regressions in multi-step workflows
- Generate realistic test fixtures

Don't use recordings to:
- Replace thinking about correctness
- Test edge cases (use unit tests)
- Validate business rules (use assertions)
