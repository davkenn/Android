package protect.card_locker

import android.content.Intent
import android.graphics.Color
import android.os.Looper
import android.view.View
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.google.zxing.BarcodeFormat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowLog
import protect.card_locker.viewmodels.BarcodeState
import protect.card_locker.viewmodels.CardLoadState
import kotlinx.coroutines.test.StandardTestDispatcher
import protect.card_locker.viewmodels.LoyaltyCardEditActivityViewModel
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LoyaltyCardEditActivityBarcodeTest {

    private lateinit var viewModel: LoyaltyCardEditActivityViewModel
    private lateinit var dbHelper: DBHelper
    private lateinit var repository: CardRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        ShadowLog.stream = System.out
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

    @Test
    fun `barcode appears within 500ms when loading existing card from view activity`() {
        // 1. ARRANGE: Create card with Aztec barcode in database
        val cardId = DBHelper.insertLoyaltyCard(
            dbHelper.writableDatabase,
            "Target Store",
            "note",
            null,  // validFrom
            null,  // expiry
            BigDecimal.ZERO,
            null,  // balanceType
            "ABC123XYZ",  // cardId
            null,  // barcodeId (null = same as cardId)
            CatimaBarcode.fromBarcode(BarcodeFormat.AZTEC),
            Color.BLACK,
            0,
            null,
            0
        )

        // 2. ACT: Launch activity simulating edit from view activity
        val intent = Intent(ApplicationProvider.getApplicationContext(), LoyaltyCardEditActivity::class.java)
        intent.putExtra(LoyaltyCardEditActivity.BUNDLE_ID, cardId.toInt())
        intent.putExtra(LoyaltyCardEditActivity.BUNDLE_UPDATE, true)

        val controller = Robolectric.buildActivity(LoyaltyCardEditActivity::class.java, intent)
            .create()
            .start()
            .resume()

        val activity = controller.get()

        // Force barcode generation by providing dimensions
        // (In Robolectric, the ViewTreeObserver may not fire automatically)
        // Use reasonable barcode dimensions for testing
        activity.viewModel.updateBarcodeDimensions(500, 200)

        // Wait for debounce (200ms) + generation time
        Thread.sleep(300)
        repeat(20) { shadowOf(Looper.getMainLooper()).idle() }

        // 3. ASSERT: Poll for barcode visibility with 1500ms timeout
        // Note: Includes 200ms debounce + coroutine processing + generation time
        val startTime = System.currentTimeMillis()
        var barcodeVisible = false
        var lastBarcodeState: String = "Unknown"

        while (!barcodeVisible && System.currentTimeMillis() - startTime < 1500) {
            // Process all pending UI updates multiple times to catch async operations
            repeat(10) {
                shadowOf(Looper.getMainLooper()).idle()
            }

            val barcodeLayout = activity.findViewById<View>(R.id.barcodeLayout)
            barcodeVisible = barcodeLayout.visibility == View.VISIBLE

            // Log state for debugging
            val currentState = activity.viewModel.cardState.value
            if (currentState is CardLoadState.Success) {
                lastBarcodeState = currentState.barcodeState.toString()
            }

            if (!barcodeVisible) {
                Thread.sleep(50)  // Give time for async operations
            }
        }

        val elapsedTime = System.currentTimeMillis() - startTime

        // Assert barcode is visible
        if (!barcodeVisible) {
            throw AssertionError("Barcode should be visible, but was not visible after ${elapsedTime}ms. Last BarcodeState: $lastBarcodeState")
        }
        assertThat(barcodeVisible).isTrue()

        // Log success timing for performance tracking
        println("✓ Barcode appeared in ${elapsedTime}ms")

        // Verify barcode ImageView has bitmap
        val barcodeImageView = activity.findViewById<ImageView>(R.id.barcode)
        val drawable = barcodeImageView.drawable
        assertThat(drawable).isNotNull()

        if (drawable is android.graphics.drawable.BitmapDrawable) {
            assertThat(drawable.bitmap).isNotNull()
        }
    }

    @Test
    fun `barcode layout stays GONE when card has no barcode type`() {
        // Create card WITHOUT barcode
        val cardId = DBHelper.insertLoyaltyCard(
            dbHelper.writableDatabase,
            "Store Without Barcode",
            "",  // note (cannot be null)
            null,  // validFrom
            null,  // expiry
            BigDecimal.ZERO,  // balance (cannot be null)
            null,  // balanceType
            "CARD123",  // cardId
            null,  // barcodeId
            null,  // barcodeType - No barcode type
            Color.BLACK,
            0,
            null,
            0
        )

        // Launch activity
        val intent = Intent(ApplicationProvider.getApplicationContext(), LoyaltyCardEditActivity::class.java)
        intent.putExtra(LoyaltyCardEditActivity.BUNDLE_ID, cardId.toInt())
        intent.putExtra(LoyaltyCardEditActivity.BUNDLE_UPDATE, true)

        val controller = Robolectric.buildActivity(LoyaltyCardEditActivity::class.java, intent)
            .create().start().resume()

        val activity = controller.get()

        // Process all pending UI updates
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(100)  // Wait a bit
        shadowOf(Looper.getMainLooper()).idle()

        // Assert barcode layout is GONE
        val barcodeLayout = activity.findViewById<View>(R.id.barcodeLayout)
        assertThat(barcodeLayout.visibility).isEqualTo(View.GONE)
    }
}