package protect.card_locker

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import protect.card_locker.DBHelper
import protect.card_locker.Group
import protect.card_locker.ImageLocationType
import protect.card_locker.LoyaltyCard
import protect.card_locker.ShortcutHelper
import protect.card_locker.Utils
import java.io.FileNotFoundException
import java.io.InvalidObjectException

/**
 * Data class bundling all the data needed for the card edit screen.
 */
data class LoadedCardData(
    val loyaltyCard: LoyaltyCard,
    val allGroups: List<Group>,
    val loyaltyCardGroups: List<Group>
)

/**
 * Repository for handling all data operations related to Loyalty Cards.
 */
class CardRepository(context: Context) {

    private val mDatabase = DBHelper(context).writableDatabase
    private val appContext = context.applicationContext

    /**
     * Saves a loyalty card and its associated data. This is a suspending function
     * and must be called from a coroutine.
     * @return A Result containing the ID of the saved card.
     */
    suspend fun saveCard(loyaltyCard: LoyaltyCard, selectedGroups: List<Group>): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val cardId: Int
            if (loyaltyCard.id > 0) { // A card with an ID > 0 is an existing one
                // Update existing card
                DBHelper.updateLoyaltyCard(
                    mDatabase, loyaltyCard.id, loyaltyCard.store, loyaltyCard.note,
                    loyaltyCard.validFrom, loyaltyCard.expiry, loyaltyCard.balance,
                    loyaltyCard.balanceType, loyaltyCard.cardId, loyaltyCard.barcodeId,
                    loyaltyCard.barcodeType, loyaltyCard.headerColor, loyaltyCard.starStatus,
                    null, // lastUsed is handled by DBHelper to set the current time
                    loyaltyCard.archiveStatus
                )
                cardId = loyaltyCard.id
            } else {
                // Insert new card
                cardId = DBHelper.insertLoyaltyCard(
                    mDatabase, loyaltyCard.store, loyaltyCard.note,
                    loyaltyCard.validFrom, loyaltyCard.expiry, loyaltyCard.balance,
                    loyaltyCard.balanceType, loyaltyCard.cardId, loyaltyCard.barcodeId,
                    loyaltyCard.barcodeType, loyaltyCard.headerColor, 0,
                    null, // lastUsed is handled by DBHelper
                    0
                ).toInt()
            }

            // Save the card images
            saveCardImages(loyaltyCard, cardId)

            // Associate the card with the selected groups
            DBHelper.setLoyaltyCardGroups(mDatabase, cardId, selectedGroups)

            // Update the app shortcuts if needed
            val savedCard = DBHelper.getLoyaltyCard(appContext, mDatabase, cardId)
            if (savedCard != null) {
                ShortcutHelper.updateShortcuts(appContext, savedCard)
            }

            // Return the ID on success
            Result.success(cardId)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Loads all necessary data for the card edit screen.
     * @param cardId The ID of the card to load (0 for new card)
     * @param importUri Optional URI to import card data from
     * @param isDuplicate If true, loads the card but clears its ID (for duplicating)
     * @return Result containing LoadedCardData or an error
     */
    suspend fun loadCardData(
        cardId: Int = 0,
        importUri: Uri? = null,
        isDuplicate: Boolean = false
    ): Result<LoadedCardData> = withContext(Dispatchers.IO) {
        try {
            // Determine which card to load based on the parameters
            val loyaltyCard = when {
                importUri != null -> {
                    // Load card from URI (import case)
                    try {
                        ImportURIHelper(appContext).parse(importUri)
                    } catch (e: InvalidObjectException) {
                        throw Exception("Failed to parse card from URI: ${e.message}")
                    }
                }
                cardId > 0 -> {
                    // Load existing card from database
                    val card = DBHelper.getLoyaltyCard(appContext, mDatabase, cardId)
                        ?: throw Exception("Card with ID $cardId not found in database")

                    if (isDuplicate) {
                        // Duplicate mode: reset the ID to 0 so it saves as a new card
                        card.id = 0
                        card
                    } else {
                        // Normal edit mode
                        card
                    }
                }
                else -> {
                    // New card case (default)
                    LoyaltyCard()
                }
            }

            // Fetch all available groups
            val allGroups = DBHelper.getGroups(mDatabase)

            // Fetch the groups this card currently belongs to
            // (only if editing an existing card, not for new/duplicate/import)
            val loyaltyCardGroups = if (cardId > 0 && !isDuplicate) {
                DBHelper.getLoyaltyCardGroups(mDatabase, cardId)
            } else {
                emptyList()
            }

            // Bundle everything together and return
            val loadedData = LoadedCardData(
                loyaltyCard = loyaltyCard,
                allGroups = allGroups,
                loyaltyCardGroups = loyaltyCardGroups
            )

            Result.success(loadedData)

        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private fun saveCardImages(loyaltyCard: LoyaltyCard, cardId: Int) {
        try {
            Utils.saveCardImage(appContext, loyaltyCard.getImageFront(appContext), cardId, ImageLocationType.front)
            Utils.saveCardImage(appContext, loyaltyCard.getImageBack(appContext), cardId, ImageLocationType.back)
            Utils.saveCardImage(appContext, loyaltyCard.getImageThumbnail(appContext), cardId, ImageLocationType.icon)
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        }
    }
}