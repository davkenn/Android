# Flow Recording Tool for Android Testing

A standalone tool for recording Flow emissions from Android apps to generate realistic test fixtures.

## Overview

This tool helps you capture real user interactions and convert them into test data:

1. **Record**: Drop in `FlowMonitor.kt` temporarily to log Flow emissions
2. **Capture**: Use `record_session.sh` to automate ADB logcat capture
3. **Parse**: Convert logs to structured JSON with `parse_flows.py`
4. **Test**: Use the generated fixtures in your unit tests

**Key Feature**: Keep this tool separate from your OSS project. Only commit the generated test fixtures, not the recording infrastructure.

---

## Quick Start

### 1. Install the Tool

```bash
cd ~
git clone <your-repo> AndroidTestingTools
cd AndroidTestingTools/flow-recorder
```

### 2. Set Up Your Project for Recording

**Copy the monitor into your project:**
```bash
cp ~/AndroidTestingTools/flow-recorder/FlowMonitor.kt \
   ~/StudioProjects/Android/app/src/main/java/protect/card_locker/debug/
```

**Add monitoring to your flows** (example):
```kotlin
// In LoyaltyCardEditActivityViewModel.kt
import protect.card_locker.debug.monitor

class LoyaltyCardEditActivityViewModel(...) : ViewModel() {
    // Add .monitor() to flows you want to record
    val cardState = _cardState.asStateFlow().monitor("cardState")
    val saveState = _saveState.asStateFlow().monitor("saveState")
    val uiEvents = _uiEvents.asSharedFlow().monitor("uiEvents")
}
```

### 3. Record a Session

**Rebuild and install your app**, then:

```bash
cd ~/AndroidTestingTools/flow-recorder
./record_session.sh
```

**Interact with your app** (type, navigate, save, etc.)

**Press Ctrl+C when done**

### 4. Review the Output

The script generates:
- `recordings/flow_recording_YYYYMMDD_HHMMSS.txt` - Raw logcat
- `recordings/flow_recording_YYYYMMDD_HHMMSS.json` - Parsed JSON

**Generate a Kotlin fixture:**
```bash
python3 parse_flows.py \
  recordings/flow_recording_20231215_233139.txt \
  recordings/flow_recording_20231215_233139.json \
  --generate-fixture
```

### 5. Use in Tests

Copy the generated `.kt` file to your test directory:
```bash
cp recordings/flow_recording_20231215_233139.kt \
   ~/StudioProjects/Android/app/src/test/java/protect/card_locker/fixtures/
```

### 6. Clean Up

**Remove the monitor before committing:**
```bash
rm ~/StudioProjects/Android/app/src/main/java/protect/card_locker/debug/FlowMonitor.kt
```

**Commit only the fixture:**
```bash
cd ~/StudioProjects/Android
git add app/src/test/java/protect/card_locker/fixtures/flow_recording_20231215_233139.kt
git commit -m "Add flow recording fixture for card edit tests"
```

---

## File Structure

```
~/AndroidTestingTools/flow-recorder/
├── FlowMonitor.kt           # Drop-in flow monitor (copy to project)
├── record_session.sh        # ADB automation script
├── parse_flows.py           # Log parser and fixture generator
├── README.md                # This file
└── recordings/              # Session data (gitignored)
    ├── flow_recording_*.txt
    ├── flow_recording_*.json
    └── flow_recording_*.kt
```

---

## JSON Output Format

```json
{
  "meta": {
    "sessionId": "flow_recording_20231215_233139",
    "recordedAt": "2023-12-15T23:31:39.123456",
    "tool": "flow-recorder",
    "version": "1.0"
  },
  "statistics": {
    "totalEmissions": 42,
    "flowCount": 3,
    "emissionsByFlow": {
      "cardState": 35,
      "saveState": 5,
      "uiEvents": 2
    },
    "durationMs": 45123,
    "firstEmission": 1702683099730,
    "lastEmission": 1702683144853
  },
  "emissions": [
    {
      "flow": "cardState",
      "timestamp": 1702683099730,
      "value": "Loading",
      "_logLine": 42
    },
    {
      "flow": "cardState",
      "timestamp": 1702683100135,
      "value": "Success(loyaltyCard=..., version=0)",
      "_logLine": 43
    }
  ]
}
```

---

## Using Fixtures in Tests

### Example: FakeRepository Pattern

```kotlin
// In your test file
class FakeCardRepository(
    private val recordedStates: List<String>
) {
    private var emissionIndex = 0

    suspend fun loadCardData(...): Result<LoadedCardData> {
        // Replay recorded state
        val stateString = recordedStates[emissionIndex++]
        // Parse stateString back to your types
        // ...
        return Result.success(parsedData)
    }
}

@Test
fun `test card edit flow with recorded data`() {
    // Use fixture from recording
    val fixture = flow_recording_20231215_233139Fixture
    val fakeRepo = FakeCardRepository(fixture.cardStateEmissions)

    val viewModel = LoyaltyCardEditActivityViewModel(app, fakeRepo)

    // Your test assertions...
}
```

---

## Tips & Best Practices

### Recording Tips

1. **Record focused scenarios**: Each recording should capture one specific user flow
   - ✅ "Create new card with barcode"
   - ✅ "Edit existing card and save"
   - ❌ "Random clicking around the app"

2. **Name your sessions**: Manually rename the output files to describe what they contain
   ```bash
   mv flow_recording_20231215_233139.json create_card_with_barcode.json
   ```

3. **Record on clean state**: Start from a known state (fresh install, specific test data)

4. **Multiple recordings**: Create different fixtures for different test scenarios

### Development Workflow

```bash
# 1. Start recording session
./record_session.sh

# 2. Perform specific user flow on device
#    (e.g., create card → add barcode → save)

# 3. Stop recording (Ctrl+C)

# 4. Generate fixture with meaningful name
python3 parse_flows.py \
  recordings/flow_recording_*.txt \
  recordings/create_card_scenario.json \
  --generate-fixture

# 5. Review the fixture
cat recordings/create_card_scenario.kt

# 6. Copy to test directory
cp recordings/create_card_scenario.kt \
   ~/StudioProjects/Android/app/src/test/java/protect/card_locker/fixtures/

# 7. Write tests using the fixture

# 8. Clean up - remove FlowMonitor.kt before committing
```

### Debugging

**No emissions captured?**
- Check if `FlowMonitor.kt` is in the correct package
- Verify `.monitor()` calls are present in your code
- Rebuild the app after adding monitor
- Check for import errors in Android Studio

**JSON parse errors?**
- Complex objects with special characters may need custom serialization
- Modify `serializeValue()` in `FlowMonitor.kt` for your types

**Too much data?**
- Monitor only the flows you need for your test
- Record shorter interaction sessions
- Filter emissions in post-processing

---

## Advanced Usage

### Custom Value Serialization

For complex types, you can enhance `FlowMonitor.kt`:

```kotlin
private fun serializeValue(value: Any?): Any {
    return when (value) {
        // Add custom serialization for your types
        is CardLoadState.Success -> JSONObject().apply {
            put("type", "Success")
            put("store", value.loyaltyCard.store)
            put("cardId", value.loyaltyCard.cardId)
            // ... other fields
        }
        // ... rest of the when expression
    }
}
```

### Filtering During Recording

Add filters to `FlowMonitor.kt`:

```kotlin
fun <T> Flow<T>.monitor(tag: String, predicate: (T) -> Boolean = { true }): Flow<T> {
    return this.onEach { value ->
        if (predicate(value)) {
            // ... log as normal
        }
    }
}

// Usage: only log Success states
val cardState = _cardState.asStateFlow()
    .monitor("cardState") { it is CardLoadState.Success }
```

### Post-Processing Scripts

Create custom parsers for your specific needs:

```python
# custom_fixture_generator.py
import json
from parse_flows import parse_logcat_file

emissions = parse_logcat_file(input_path)

# Custom fixture generation logic
# - Convert string values to typed objects
# - Merge emissions from multiple flows
# - Generate specific test scenarios
```

---

## Troubleshooting

### Issue: "No Android device connected"
- Start an emulator or connect a physical device
- Run `adb devices` to verify connection

### Issue: Parser can't find Python 3
- Install Python 3: `brew install python3` (macOS)
- Or specify full path in `record_session.sh`

### Issue: App crashes when recording
- Check logcat for actual error: `adb logcat`
- FlowMonitor shouldn't cause crashes, but check for import issues

### Issue: Fixture too large for version control
- Record shorter sessions
- Split into multiple focused fixtures
- Use `.gitattributes` to compress fixture files

---

## Contributing

This is a personal testing tool, but feel free to:
- Add more sophisticated value serialization
- Create language-specific parsers (Rust, Swift, etc.)
- Build visualizations of flow emissions
- Share your custom fixture generators

---

## License

Personal testing tool - use freely for your own testing needs.
