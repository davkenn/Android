package protect.card_locker.templates

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.robolectric.shadows.ShadowLog

/**
 * TEMPLATE: Recording-Based Test
 *
 * Purpose: Test with realistic data from actual user flows
 * When to use: Complex user journeys, regression tests, realistic scenarios
 *
 * Workflow:
 * 1. Record a real user session with FlowMonitor (in recording build variant)
 * 2. Generate fixture with parse_flows.py --generate-fixture
 * 3. Write test using the fixture
 * 4. Test replays the exact data sequence from the recording
 *
 * Benefits:
 * - Tests with real-world data complexity
 * - Captures edge cases from actual usage
 * - Documents user flows
 * - Prevents regressions of real bugs
 *
 * HOW TO USE THIS TEMPLATE:
 * 1. Record a session (see docs/FLOW_RECORDING.md)
 * 2. Generate fixture from the recording
 * 3. Copy this file and adapt to your recorded scenario
 * 4. Replace RecordingTemplate_YourRecordedFixture with your actual fixture name
 */
@Ignore("Template - copy and adapt, don't run directly")
@OptIn(ExperimentalCoroutinesApi::class)
class RecordingBasedTestTemplate {

    private lateinit var application: Application
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        ShadowLog.stream = System.out
        application = ApplicationProvider.getApplicationContext()
    }

    /**
     * Pattern: Replay complete user flow from recording
     *
     * This test replays the exact sequence of actions a user performed,
     * using data captured from a real app session.
     */
    @Test
    fun `test scenario from recording - user creates card with barcode`() = runTest(testDispatcher) {
        // 1. Create fake repository with recorded data
        val fakeRepo = RecordingTemplate_FakeRepository(
            loadResults = RecordingTemplate_YourRecordedFixture.createCardScenarioResults()
        )

        val viewModel = RecordingTemplate_YourViewModel(application, fakeRepo, testDispatcher)

        // 2. Replay the recorded sequence

        // Step 1: Load initial state (from recording at t=0s)
        viewModel.loadCard(cardId = 0)
        advanceUntilIdle()

        val initialState = viewModel.state.value as RecordingTemplate_YourState.Success
        assertEquals(-1, initialState.card.id)  // New card from fixture
        assertEquals("", initialState.card.store)  // Empty from fixture

        // Step 2: User enters store name (from recording at t=10s)
        viewModel.onStoreNameChanged("Target")
        assertEquals("Target", viewModel.card.store)

        // Step 3: User saves (from recording at t=20s)
        viewModel.save()
        advanceUntilIdle()

        // Verify save was called with data from recording
        assertEquals(1, fakeRepo.saveCalls.size)
        assertEquals("Target", fakeRepo.saveCalls[0].card.store)
    }

    /**
     * Pattern: Test specific bug captured in recording
     *
     * Document bugs found in real usage with tests.
     */
    @Test
    fun `bug from recording - CODABAR barcode fails for alphanumeric cardId`() = runTest(testDispatcher) {
        // This bug was discovered in flow_recording_YYYYMMDD_HHMMSS.json
        // Emission #10: User selects CODABAR → BarcodeState.Error

        val fakeRepo = RecordingTemplate_FakeRepository(
            loadResults = listOf(
                Result.success(RecordingTemplate_YourRecordedFixture.newCardWithAlphanumericId())
            )
        )

        val viewModel = RecordingTemplate_YourViewModel(application, fakeRepo, testDispatcher)

        viewModel.loadCard(cardId = 0)
        advanceUntilIdle()

        // User selects CODABAR (requires numeric-only card ID)
        viewModel.setBarcodeType("CODABAR")

        // Verify error state (documents current behavior from recording)
        val state = viewModel.state.value as RecordingTemplate_YourState.Success
        assertEquals("ERROR", state.barcodeState)
    }
}

// Placeholder classes - replace with your types
// (Prefixed to avoid conflicts with other template files)
private class RecordingTemplate_YourViewModel(app: Application, repo: RecordingTemplate_FakeRepository, dispatcher: Any) {
    val state = kotlinx.coroutines.flow.MutableStateFlow<RecordingTemplate_YourState>(RecordingTemplate_YourState.Loading)
    val card: RecordingTemplate_Card = RecordingTemplate_Card()

    suspend fun loadCard(cardId: Int) {}
    fun onStoreNameChanged(name: String) { card.store = name }
    suspend fun save() {}
    fun setBarcodeType(type: String) {}
}

private class RecordingTemplate_FakeRepository(
    val loadResults: List<Result<Any>>
) {
    val saveCalls = mutableListOf<SaveCall>()
    data class SaveCall(val card: RecordingTemplate_Card)
}

private sealed interface RecordingTemplate_YourState {
    object Loading : RecordingTemplate_YourState
    data class Success(val card: RecordingTemplate_Card, val barcodeState: String = "NONE") : RecordingTemplate_YourState
}

private data class RecordingTemplate_Card(var id: Int = -1, var store: String = "")

/**
 * Example fixture object (generated by parse_flows.py --generate-fixture)
 *
 * See: app/src/test/java/protect/card_locker/fixtures/RecordedCardCreateFixture.kt
 * for a real example of what your fixture would look like.
 */
private object RecordingTemplate_YourRecordedFixture {
    fun createCardScenarioResults(): List<Result<Any>> {
        return listOf(
            Result.success(Any()), // Replace with: initial load data
            Result.success(Any())  // Replace with: reload after save data
        )
    }

    fun newCardWithAlphanumericId(): Any {
        return RecordingTemplate_Card(id = -1, store = "")
    }
}
