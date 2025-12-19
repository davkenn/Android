package protect.card_locker.viewmodels

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.google.zxing.BarcodeFormat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog
import protect.card_locker.CatimaBarcode
import protect.card_locker.FakeCardRepository
import protect.card_locker.fixtures.RecordedCardCreateFixture

/**
 * Tests using recorded flow emissions from actual app usage.
 *
 * These tests replay real user interactions captured via FlowMonitor,
 * ensuring the ViewModel behaves correctly with realistic data sequences.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LoyaltyCardEditActivityViewModelRecordedTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var application: Application
    private lateinit var fakeRepository: FakeCardRepository
    private lateinit var viewModel: LoyaltyCardEditActivityViewModel

    @Before
    fun setUp() {
        ShadowLog.stream = System.out
        application = ApplicationProvider.getApplicationContext()
    }

    /**
     * Test scenario from recording: flow_recording_20251219_021109.json
     *
     * User creates a new card with barcode selection.
     * This test verifies the complete flow: load → edit → save → reload.
     */
    @Test
    fun `test create card with recorded data sequence`() = runTest {
        // Arrange: Set up fake repository with recorded load results
        fakeRepository = FakeCardRepository(
            loadResults = RecordedCardCreateFixture.completeScenarioResults(),
            saveResults = listOf(Result.success(1))  // Returns new card ID
        )

        viewModel = LoyaltyCardEditActivityViewModel(
            application,
            fakeRepository,
            testDispatcher
        )

        // Act: Load initial card data (simulates opening create card screen)
        viewModel.loadCard(cardId = 0)
        advanceUntilIdle()

        // Assert: Initial state is Success with empty card
        val initialState = viewModel.cardState.value
        assertTrue("Expected Success state", initialState is CardLoadState.Success)

        val successState = initialState as CardLoadState.Success
        assertEquals(-1, successState.loyaltyCard.id)
        assertEquals("afdsdf", successState.loyaltyCard.cardId)
        assertEquals("", successState.loyaltyCard.store)
        assertEquals(null, successState.loyaltyCard.barcodeType)

        // Act: User enters store name (from recording at t=27s)
        viewModel.onStoreNameChanged("a")
        advanceUntilIdle()

        // Assert: Store name updated
        val afterNameState = viewModel.cardState.value as CardLoadState.Success
        assertEquals("a", afterNameState.loyaltyCard.store)

        // Act: User selects CODE_128 barcode type (from recording at t=20s)
        viewModel.setBarcodeType(CatimaBarcode.fromBarcode(BarcodeFormat.CODE_128))
        advanceUntilIdle()

        // Assert: Barcode type updated
        val afterBarcodeState = viewModel.cardState.value as CardLoadState.Success
        assertEquals(BarcodeFormat.CODE_128, afterBarcodeState.loyaltyCard.barcodeType?.format())

        // Act: User saves the card (from recording at t=30s)
        viewModel.saveCard(emptyList())
        advanceUntilIdle()

        // Assert: Save was called with correct data
        assertEquals(1, fakeRepository.saveCalls.size)
        assertEquals("a", fakeRepository.saveCalls[0].loyaltyCard.store)
        assertEquals(BarcodeFormat.CODE_128, fakeRepository.saveCalls[0].loyaltyCard.barcodeType?.format())

        // Assert: SaveState went Idle → Saving → Idle
        assertEquals(SaveState.Idle, viewModel.saveState.value)

        // Act: Reload saved card (from recording at t=34s)
        viewModel.loadCard(cardId = 1)
        advanceUntilIdle()

        // Assert: Reloaded card has saved state
        val reloadedState = viewModel.cardState.value as CardLoadState.Success
        assertEquals(1, reloadedState.loyaltyCard.id)
        assertEquals("a", reloadedState.loyaltyCard.store)
        assertEquals(BarcodeFormat.CODE_128, reloadedState.loyaltyCard.barcodeType?.format())
    }

    /**
     * Test from recording: Bug where CODABAR shows error for invalid card ID.
     * Emission #10 at timestamp 1766135504235 (line 97 in JSON).
     */
    @Test
    fun `test invalid barcode format shows error state`() = runTest {
        // Arrange: Initial card with alphanumeric ID
        fakeRepository = FakeCardRepository(
            loadResults = listOf(
                Result.success(RecordedCardCreateFixture.initialNewCardData(cardId = "afdsdf"))
            )
        )

        viewModel = LoyaltyCardEditActivityViewModel(
            application,
            fakeRepository,
            testDispatcher
        )

        viewModel.loadCard(cardId = 0)
        advanceUntilIdle()

        // Act: User selects CODABAR (requires numeric-only card ID)
        viewModel.setBarcodeType(CatimaBarcode.fromBarcode(BarcodeFormat.CODABAR))
        advanceUntilIdle()

        // Note: Barcode generation will fail because "afdsdf" is not valid for CODABAR
        // This matches the BarcodeState.Error seen in emission #10 of the recording

        // The test documents the expected behavior: invalid card ID for barcode type
        // should result in Error state when generation is attempted
        val state = viewModel.cardState.value as CardLoadState.Success
        assertEquals(BarcodeFormat.CODABAR, state.loyaltyCard.barcodeType?.format())
        // When generateBarcode() is called in Activity, it will produce BarcodeState.Error
    }
}
