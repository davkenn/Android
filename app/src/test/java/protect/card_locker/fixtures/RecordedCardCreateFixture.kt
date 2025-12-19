package protect.card_locker.fixtures

import com.google.zxing.BarcodeFormat
import protect.card_locker.CatimaBarcode
import protect.card_locker.Group
import protect.card_locker.LoadedCardData
import protect.card_locker.LoyaltyCard

/**
 * Test fixture from recording: flow_recording_20251219_021109.json
 *
 * Scenario: Create new card with barcode type selection
 * Duration: 34.8 seconds
 * Emissions: 26 (19 cardState, 6 saveState, 1 uiEvent)
 *
 * User actions captured:
 * 1. Created new card (id=-1, cardId="afdsdf")
 * 2. Tested barcode types: No barcode → AZTEC → CODE_39 → No barcode → CODABAR (error) → CODE_128
 * 3. Entered store name: "a"
 * 4. Saved card (received id=1)
 * 5. Reloaded saved card
 *
 * This fixture provides the initial loaded card data for tests.
 */
object RecordedCardCreateFixture {

    /**
     * Initial card state when creating a new card with cardId from bundle.
     * Corresponds to emission #3 at timestamp 1766135490482 (line 49 in JSON).
     */
    fun initialNewCardData(cardId: String = "afdsdf"): LoadedCardData {
        return LoadedCardData(
            loyaltyCard = LoyaltyCard().apply {
                id = -1  // Not yet saved
                store = ""
                note = ""
                validFrom = null
                expiry = null
                balance = null
                balanceType = null
                this.cardId = cardId
                barcodeId = null
                barcodeType = null
                headerColor = null  // Will be auto-generated
                starStatus = 0
                zoomLevel = 100
                zoomLevelWidth = 100
                archiveStatus = 0
            },
            allGroups = emptyList(),
            loyaltyCardGroups = emptyList()
        )
    }

    /**
     * Saved card state after user saves.
     * Corresponds to emission #19 at timestamp 1766135524973 (line 169 in JSON).
     */
    fun savedCardData(
        cardId: String = "afdsdf",
        storeName: String = "a",
        barcodeType: CatimaBarcode = CatimaBarcode.fromBarcode(BarcodeFormat.CODE_128)
    ): LoadedCardData {
        return LoadedCardData(
            loyaltyCard = LoyaltyCard().apply {
                id = 1  // Assigned by database
                store = storeName
                note = ""
                validFrom = null
                expiry = null
                balance = null
                balanceType = null
                this.cardId = cardId
                barcodeId = null
                this.barcodeType = barcodeType
                headerColor = -957596  // From recording
                starStatus = 0
                zoomLevel = 100
                zoomLevelWidth = 100
                archiveStatus = 0
            },
            allGroups = emptyList(),
            loyaltyCardGroups = emptyList()
        )
    }

    /**
     * Complete test scenario: load → modify → save → reload
     */
    fun completeScenarioResults(): List<Result<LoadedCardData>> {
        return listOf(
            Result.success(initialNewCardData()),  // Initial load
            Result.success(savedCardData())         // Reload after save
        )
    }
}
