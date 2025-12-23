package protect.card_locker

import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.google.zxing.BarcodeFormat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import protect.card_locker.viewmodels.BarcodeState
import protect.card_locker.viewmodels.CardLoadState
import kotlinx.coroutines.test.StandardTestDispatcher
import protect.card_locker.viewmodels.LoyaltyCardEditActivityViewModel
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class LoyaltyCardEditActivityBarcodeTest {

    private lateinit var viewModel: LoyaltyCardEditActivityViewModel
    private lateinit var dbHelper: DBHelper
    private lateinit var repository: CardRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        dbHelper = TestHelpers.getEmptyDb(ApplicationProvider.getApplicationContext())
        repository = CardRepository(dbHelper.writableDatabase, ApplicationProvider.getApplicationContext())
        viewModel = LoyaltyCardEditActivityViewModel(ApplicationProvider.getApplicationContext(), repository, testDispatcher)
    }

    @After
    fun tearDown() {
        dbHelper.close()
    }

    @Test
    fun `loading a saved card with a barcode should trigger barcode generation`() = runTest(testDispatcher) {
        // Arrange: Create a card with a valid barcode in the database
        val cardId = DBHelper.insertLoyaltyCard(
            dbHelper.writableDatabase,
            "Test Store", "note", null, null, BigDecimal.ZERO, null,
            "123456789012", null, CatimaBarcode.fromBarcode(BarcodeFormat.EAN_13),
            Color.BLACK, 0, null, 0
        )

        // Act & Assert
        viewModel.cardState.test {
            // 1. Await the initial Loading state
            assertThat(awaitItem()).isInstanceOf(CardLoadState.Loading::class.java)

            // 2. Tell the ViewModel to load the card
            viewModel.loadCard(cardId.toInt())

            // 3. Await the Success state after the database load
            val successState = awaitItem() as CardLoadState.Success
            assertThat(successState.barcodeState).isInstanceOf(BarcodeState.None::class.java)
            
            // 4. The Activity would now call generateBarcode(). We simulate this.
            viewModel.generateBarcode(
                cardId = successState.loyaltyCard.cardId ?: "",
                format = successState.loyaltyCard.barcodeType ?: CatimaBarcode.fromBarcode(BarcodeFormat.QR_CODE), // Provide a fallback format for the test
                width = 100,
                height = 100
            )

            // 5. Await the final state update after barcode generation
            val finalState = awaitItem() as CardLoadState.Success

            // THE BUG: This is where it fails. The barcode is not being generated on load.
            assertThat(finalState.barcodeState).isInstanceOf(BarcodeState.Generated::class.java)
        }
    }
}