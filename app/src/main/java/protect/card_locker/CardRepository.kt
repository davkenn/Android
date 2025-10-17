package protect.card_locker

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import protect.card_locker.DBHelper
import protect.card_locker.Group
import protect.card_locker.ImageLocationType
import protect.card_locker.LoyaltyCard
import protect.card_locker.ShortcutHelper
import protect.card_locker.Utils
import java.io.FileNotFoundException

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