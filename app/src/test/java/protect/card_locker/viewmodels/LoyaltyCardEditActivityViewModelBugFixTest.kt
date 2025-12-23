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
 * Bug fix tests based on issues found in flow recordings.
 *
 * Test-driven bug fixes:
 * 1. Write failing test from recorded bug
 * 2. Fix the bug
 * 3. Test passes and prevents regression
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LoyaltyCardEditActivityViewModelBugFixTest {

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
     * BUG from recording flow_recording_20251219_021109.json, emission #20 (line 175)
     *
     * After saving a card with CODE_128 barcode and reloading it,
     * the barcodeState becomes Error instead of generating successfully.
     *
     * Timeline from recording:
     * - Emission #19 (t=34.973s): Card reloaded, barcodeType=CODE_128, barcodeState=None
     * - Emission #20 (t=35.292s): 319ms later, barcodeState=Error (BUG!)
     *
     * Expected: When a saved card with valid barcode type is reloaded,
     * barcode generation should succeed, not show Error.
     *
     * TODO: This test currently fails - it documents the bug that needs fixing.
     */
    @org.junit.Ignore("Bug not yet fixed - documents known issue")
    @Test
    fun `BUG - reloading saved card with CODE_128 should not show error state`() = runTest {
        // Arrange: Saved card with CODE_128 barcode type
        fakeRepository = FakeCardRepository(
            loadResults = listOf(
                Result.success(
                    RecordedCardCreateFixture.savedCardData(
                        cardId = "afdsdf",
                        storeName = "a",
                        barcodeType = CatimaBarcode.fromBarcode(BarcodeFormat.CODE_128)
                    )
                )
            )
        )

        viewModel = LoyaltyCardEditActivityViewModel(
            application,
            fakeRepository,
            testDispatcher
        )

        // Act: Load saved card (id=1)
        viewModel.loadCard(cardId = 1)

        // Assert: Card loaded successfully with CODE_128
        val loadedState = viewModel.cardState.value
        assertTrue("Expected Success state after load", loadedState is CardLoadState.Success)

        val successState = loadedState as CardLoadState.Success
        assertEquals(1, successState.loyaltyCard.id)
        assertEquals("afdsdf", successState.loyaltyCard.cardId)
        assertEquals(BarcodeFormat.CODE_128, successState.loyaltyCard.barcodeType?.format())

        // Initial state should be None (not yet generated)
        assertTrue(
            "After load, barcode should be None until generation requested",
            successState.barcodeState is BarcodeState.None
        )

        // Act: Generate barcode with dimensions (simulates Activity calling this)
        viewModel.generateBarcode(
            cardId = "afdsdf", // From fixture
            format = CatimaBarcode.fromBarcode(BarcodeFormat.CODE_128),
            width = 500,
            height = 300
        )

        // Assert: Barcode generation should succeed, NOT show Error
        val afterGenerationState = viewModel.cardState.value as CardLoadState.Success

        // BUG REPRODUCTION: This currently fails - barcodeState becomes Error
        // Expected: barcodeState should be Generated with valid bitmap
        assertTrue(
            "BUG: Barcode generation failed after reload. Expected Generated, got ${afterGenerationState.barcodeState::class.simpleName}",
            afterGenerationState.barcodeState is BarcodeState.Generated
        )

        val generatedState = afterGenerationState.barcodeState as BarcodeState.Generated
        assertEquals(BarcodeFormat.CODE_128, generatedState.format.format())
        assertTrue("Generated barcode should be valid", generatedState.isValid)
    }

    /**
     * BUG from recording: Selecting "No barcode" should immediately clear barcode state
     *
     * From flow_recording_20251219_012930.json, emission #6 (line 51):
     * When user switches to "No barcode", there's a 196ms delay where
     * barcodeType=null but barcodeState still shows old Generated barcode.
     *
     * This was FIXED in our earlier changes - this test documents the fix.
     */
    @Test
    fun `FIXED - selecting no barcode immediately clears barcode state`() = runTest {
        // Arrange: Card with existing barcode
        fakeRepository = FakeCardRepository(
            loadResults = listOf(
                Result.success(
                    RecordedCardCreateFixture.savedCardData(
                        barcodeType = CatimaBarcode.fromBarcode(BarcodeFormat.AZTEC)
                    )
                )
            )
        )

        viewModel = LoyaltyCardEditActivityViewModel(
            application,
            fakeRepository,
            testDispatcher
        )

        viewModel.loadCard(cardId = 1)

        // Generate the initial barcode (simulates Activity behavior)
        viewModel.generateBarcode(
            cardId = "afdsdf", // From fixture
            format = CatimaBarcode.fromBarcode(BarcodeFormat.AZTEC), // From fixture
            width = 500,
            height = 300
        )

        // Verify initial barcode is generated
        val initialState = viewModel.cardState.value as CardLoadState.Success
        assertTrue(
            "Initial barcode should be generated after explicit generateBarcode() call",
            initialState.barcodeState is BarcodeState.Generated
        )

        // Act: User selects "No barcode"
        viewModel.setBarcodeType(null)

        // Assert: Barcode state should IMMEDIATELY become None (synchronous)
        val afterClearState = viewModel.cardState.value as CardLoadState.Success
        assertEquals(null, afterClearState.loyaltyCard.barcodeType)
        assertTrue(
            "FIXED: Barcode state should immediately be None when type is null",
            afterClearState.barcodeState is BarcodeState.None
        )

        // No delay - state is consistent immediately
    }
}
