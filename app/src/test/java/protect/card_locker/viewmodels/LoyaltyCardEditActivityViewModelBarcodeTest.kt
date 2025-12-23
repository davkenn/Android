package protect.card_locker.viewmodels

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog
import protect.card_locker.CardRepository
import protect.card_locker.CatimaBarcode
import protect.card_locker.LoadedCardData
import protect.card_locker.LoyaltyCard
import com.google.zxing.BarcodeFormat

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LoyaltyCardEditActivityViewModelBarcodeTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var application: Application
    private lateinit var cardRepository: CardRepository
    private lateinit var viewModel: LoyaltyCardEditActivityViewModel

    @Before
    fun setUp() {
        ShadowLog.stream = System.out
        application = ApplicationProvider.getApplicationContext()
        cardRepository = mock()
        viewModel = LoyaltyCardEditActivityViewModel(application, cardRepository, testDispatcher)
    }

    @Test
    fun `test setting barcode type to null results in BarcodeState None`() = runTest(testDispatcher) {
        // Setup a card with an existing barcode
        val card = LoyaltyCard().apply {
            cardId = "123456"
            barcodeType = CatimaBarcode.fromBarcode(com.google.zxing.BarcodeFormat.QR_CODE)
        }
        
        whenever(cardRepository.loadCardData(0, null, false)).thenReturn(
            Result.success(LoadedCardData(card, emptyList(), emptyList()))
        )

        viewModel.loadCard(cardId = 0)
        advanceUntilIdle()

        // Simulate initial generation (Activity usually calls this)
        viewModel.generateBarcode(
            cardId = card.cardId ?: "",
            format = card.barcodeType ?: CatimaBarcode.fromBarcode(BarcodeFormat.QR_CODE),
            width = 100,
            height = 100
        )
        advanceUntilIdle()

        // Verify initial state is Generated
        val initialState = (viewModel.cardState.value as CardLoadState.Success).barcodeState
        assertTrue("Initial state should be Generated", initialState is BarcodeState.Generated)

        // ACT: Set barcode type to null (simulating "No Barcode")
        viewModel.setBarcodeType(null)
        advanceUntilIdle()

        // ASSERT: State should be None, not Error
        val newState = (viewModel.cardState.value as CardLoadState.Success).barcodeState
        println("Actual state: $newState")
        assertTrue("State should be None but was $newState", newState is BarcodeState.None)
    }
}
