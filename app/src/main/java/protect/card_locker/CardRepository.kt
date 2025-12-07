package protect.card_locker

import android.content.Context
import android.database.sqlite.SQLiteDatabase
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

data class LoadedCardData(
    val loyaltyCard: LoyaltyCard,
    val allGroups: List<Group>,
    val loyaltyCardGroups: List<Group>
)

class CardRepository(
    private val database: SQLiteDatabase,
    private val appContext: Context
) {

    suspend fun saveCard(loyaltyCard: LoyaltyCard, selectedGroups: List<Group>): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val cardId: Int
            if (loyaltyCard.id > 0) {
                DBHelper.updateLoyaltyCard(
                    database, loyaltyCard.id, loyaltyCard.store, loyaltyCard.note,
                    loyaltyCard.validFrom, loyaltyCard.expiry, loyaltyCard.balance,
                    loyaltyCard.balanceType, loyaltyCard.cardId, loyaltyCard.barcodeId,
                    loyaltyCard.barcodeType, loyaltyCard.headerColor, loyaltyCard.starStatus,
                    null,
                    loyaltyCard.archiveStatus
                )
                cardId = loyaltyCard.id
            } else {
                cardId = DBHelper.insertLoyaltyCard(
                    database, loyaltyCard.store, loyaltyCard.note,
                    loyaltyCard.validFrom, loyaltyCard.expiry, loyaltyCard.balance,
                    loyaltyCard.balanceType, loyaltyCard.cardId, loyaltyCard.barcodeId,
                    loyaltyCard.barcodeType, loyaltyCard.headerColor, 0,
                    null,
                    0
                ).toInt()
            }

            saveCardImages(loyaltyCard, cardId)
            DBHelper.setLoyaltyCardGroups(database, cardId, selectedGroups)

            val savedCard = DBHelper.getLoyaltyCard(appContext, database, cardId)
            if (savedCard != null) {
                ShortcutHelper.updateShortcuts(appContext, savedCard)
            }

            Result.success(cardId)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun loadCardData(
        cardId: Int = 0,
        importUri: Uri? = null,
        isDuplicate: Boolean = false
    ): Result<LoadedCardData> = withContext(Dispatchers.IO) {
        try {
            val loyaltyCard = when {
                importUri != null -> {
                    try {
                        ImportURIHelper(appContext).parse(importUri)
                    } catch (e: InvalidObjectException) {
                        throw Exception("Failed to parse card from URI: ${e.message}")
                    }
                }
                cardId > 0 -> {
                    val card = DBHelper.getLoyaltyCard(appContext, database, cardId)
                        ?: throw Exception("Card with ID $cardId not found in database")

                    if (isDuplicate) {
                        card.id = 0
                    }
                    card
                }
                else -> {
                    LoyaltyCard()
                }
            }

            val allGroups = DBHelper.getGroups(database)

            val loyaltyCardGroups = if (cardId > 0 && !isDuplicate) {
                DBHelper.getLoyaltyCardGroups(database, cardId)
            } else {
                emptyList()
            }

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