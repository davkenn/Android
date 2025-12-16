package protect.card_locker.viewmodels

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import protect.card_locker.CardRepository
import protect.card_locker.CatimaBarcode
import protect.card_locker.Group
import protect.card_locker.LoyaltyCard
import java.math.BigDecimal
import java.util.Currency
import java.util.Date
import protect.card_locker.ImageLocationType
import android.graphics.Bitmap
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import kotlinx.coroutines.withContext

sealed interface SaveState {
    object Idle : SaveState
    object Saving : SaveState
}

sealed interface CardLoadState {
    object Loading : CardLoadState
    data class Success(
        var loyaltyCard: LoyaltyCard,
        val allGroups: List<Group>,
        val loyaltyCardGroups: List<Group>,
        val images: Map<ImageLocationType, Bitmap?> = emptyMap(),
        val barcodeState: BarcodeState = BarcodeState.None,
        val version: Long = 0
    ) : CardLoadState
}

sealed interface UiEvent {
    data class ShowToast(val message: String) : UiEvent
    data class ShowError(val message: String) : UiEvent
    object SaveSuccess : UiEvent
    object LoadFailed : UiEvent
}

/**
 * Barcode display state - follows same pattern as displayImages StateFlow.
 * Keeps barcode bitmap in ViewModel so Activity just observes and renders.
 */
sealed interface BarcodeState {
    /** No barcode (empty card ID or no barcode type selected) */
    object None : BarcodeState

    /** Barcode generated successfully */
    data class Generated(
        val bitmap: Bitmap,
        val format: CatimaBarcode,
        val isValid: Boolean = true  // false when showing fallback barcode
    ) : BarcodeState

    /** Barcode generation failed */
    object Error : BarcodeState
}

class LoyaltyCardEditActivityViewModel(
    private val application: Application,
    private val cardRepository: CardRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main
) : ViewModel() {
    private companion object {
        private const val TAG = "Catima"
    }

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState = _saveState.asStateFlow()

    private val _cardState = MutableStateFlow<CardLoadState>(CardLoadState.Loading)
    val cardState = _cardState.asStateFlow()

    private val _uiEvents = MutableSharedFlow<UiEvent>(replay = 0)
    val uiEvents = _uiEvents.asSharedFlow()

    var tempStoredOldBarcodeValue: String? = null

    var initDone = false
    var onRestoring = false
    var onResuming = false
    var initialized: Boolean = false
    var hasChanged: Boolean = false

    private var barcodeGenerationJob: Job? = null

    /** Update barcodeState within the unified CardLoadState */
    private fun updateBarcodeState(newState: BarcodeState) {
        val state = _cardState.value
        if (state is CardLoadState.Success) {
            _cardState.value = state.copy(barcodeState = newState)
        }
    }

    /** Update images within the unified CardLoadState */
    private fun updateImages(update: (Map<ImageLocationType, Bitmap?>) -> Map<ImageLocationType, Bitmap?>) {
        val state = _cardState.value
        if (state is CardLoadState.Success) {
            _cardState.value = state.copy(images = update(state.images))
        }
    }

    /**
     * Generate barcode and store in unified cardState.
     * Activity observes cardState and renders.
     */
    fun generateBarcode(width: Int, height: Int) {
        val card = loyaltyCard
        val cardIdToUse = card.barcodeId ?: card.cardId
        val format = card.barcodeType

        // No barcode if missing required data
        if (format == null || cardIdToUse.isNullOrEmpty()) {
            updateBarcodeState(BarcodeState.None)
            return
        }

        // Cancel any in-progress generation
        barcodeGenerationJob?.cancel()

        barcodeGenerationJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    generateBarcodeInternal(cardIdToUse, format, width, height)
                }
                updateBarcodeState(result)
            } catch (e: Exception) {
                Log.e(TAG, "Barcode generation failed", e)
                updateBarcodeState(BarcodeState.Error)
            }
        }
    }

    private fun generateBarcodeInternal(
        cardId: String,
        format: CatimaBarcode,
        width: Int,
        height: Int
    ): BarcodeState {
        var bitmap = generateBitmap(cardId, format, width, height)
        var isValid = true

        // Try fallback if generation failed
        if (bitmap == null) {
            isValid = false
            getFallbackString(format)?.let { fallbackId ->
                bitmap = generateBitmap(fallbackId, format, width, height)
            }
        }

        return bitmap?.let {
            BarcodeState.Generated(it, format, isValid)
        } ?: BarcodeState.Error
    }

    private fun generateBitmap(cardId: String, format: CatimaBarcode, width: Int, height: Int): Bitmap? {
        if (cardId.isEmpty() || width <= 0 || height <= 0) return null

        return try {
            val writer = MultiFormatWriter()
            val bitMatrix = writer.encode(cardId, format.format(), width, height, null)

            val bitMatrixWidth = bitMatrix.width
            val bitMatrixHeight = bitMatrix.height
            val pixels = IntArray(bitMatrixWidth * bitMatrixHeight)

            for (y in 0 until bitMatrixHeight) {
                val offset = y * bitMatrixWidth
                for (x in 0 until bitMatrixWidth) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                }
            }

            var bitmap = Bitmap.createBitmap(bitMatrixWidth, bitMatrixHeight, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, bitMatrixWidth, 0, 0, bitMatrixWidth, bitMatrixHeight)

            // Scale up small barcodes for sharp rendering
            val scalingFactor = minOf(height / bitMatrixHeight, width / bitMatrixWidth)
            if (scalingFactor > 1) {
                bitmap = Bitmap.createScaledBitmap(
                    bitmap,
                    bitMatrixWidth * scalingFactor,
                    bitMatrixHeight * scalingFactor,
                    false
                )
            }

            bitmap
        } catch (e: WriterException) {
            Log.e(TAG, "Failed to generate barcode: $cardId", e)
            null
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "OOM generating barcode", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error generating barcode", e)
            null
        }
    }

    private fun getFallbackString(format: CatimaBarcode): String? = when (format.format()) {
        com.google.zxing.BarcodeFormat.AZTEC -> "AZTEC"
        com.google.zxing.BarcodeFormat.DATA_MATRIX -> "DATA_MATRIX"
        com.google.zxing.BarcodeFormat.PDF_417 -> "PDF_417"
        com.google.zxing.BarcodeFormat.QR_CODE -> "QR_CODE"
        com.google.zxing.BarcodeFormat.CODABAR -> "C0C"
        com.google.zxing.BarcodeFormat.CODE_39 -> "CODE_39"
        com.google.zxing.BarcodeFormat.CODE_93 -> "CODE_93"
        com.google.zxing.BarcodeFormat.CODE_128 -> "CODE_128"
        com.google.zxing.BarcodeFormat.EAN_8 -> "32123456"
        com.google.zxing.BarcodeFormat.EAN_13 -> "5901234123457"
        com.google.zxing.BarcodeFormat.ITF -> "1003"
        com.google.zxing.BarcodeFormat.UPC_A -> "123456789012"
        com.google.zxing.BarcodeFormat.UPC_E -> "0123456"
        else -> null
    }

    var addGroup: String? = null
    var openSetIconMenu: Boolean = false
    var updateLoyaltyCard: Boolean = false
    var duplicateFromLoyaltyCardId: Boolean = false
    var importLoyaltyCardUri: Uri? = null
    var loyaltyCardId: Int = 0
    var tabIndex: Int = 0
    var currentImageOperation: protect.card_locker.LoyaltyCardEditActivity.ImageOperation? = null

    var loyaltyCard: LoyaltyCard
        get() = (_cardState.value as? CardLoadState.Success)?.loyaltyCard ?: LoyaltyCard()
        private set(value) {
            val currentState = _cardState.value
            if (currentState is CardLoadState.Success) {
                _cardState.value = currentState.copy(loyaltyCard = value)
            }
        }

    fun loadCard(
        cardId: Int = 0,
        importUri: Uri? = null,
        isDuplicate: Boolean = false
    ) {
        loyaltyCardId = cardId
        _cardState.value = CardLoadState.Loading

        viewModelScope.launch(dispatcher) {
            val result = cardRepository.loadCardData(cardId, importUri, isDuplicate)

            result.fold(
                onSuccess = { data ->
                    _cardState.value = CardLoadState.Success(
                        loyaltyCard = data.loyaltyCard,
                        allGroups = data.allGroups,
                        loyaltyCardGroups = data.loyaltyCardGroups,
                        images = mapOf(
                            ImageLocationType.icon to data.loyaltyCard.getImageThumbnail(application),
                            ImageLocationType.front to data.loyaltyCard.getImageFront(application),
                            ImageLocationType.back to data.loyaltyCard.getImageBack(application)
                        )
                    )
                },
                onFailure = { exception ->
                    val message = exception.message ?: "An unknown error occurred while loading card data."
                    Log.w(TAG, "Failed to load card: $message")
                    _uiEvents.emit(UiEvent.ShowError(message))
                    _uiEvents.emit(UiEvent.LoadFailed)
                }
            )
        }
    }

    private inline fun modifyCard(block: LoyaltyCard.() -> Unit) {
        if (onResuming || onRestoring) return

        val state = _cardState.value
        if (state is CardLoadState.Success) {
            state.loyaltyCard.block()
            hasChanged = true
            _cardState.value = state.copy(version = System.currentTimeMillis())
        }
    }

    fun onStoreNameChanged(newName: String) = modifyCard { store = newName.trim() }

    fun onNoteChanged(newNote: String) = modifyCard { note = newNote }

    fun onCardIdChanged(newCardId: String) = modifyCard {
        if (barcodeId != null && barcodeId == cardId) {
            barcodeId = newCardId
        }
        cardId = newCardId
    }

    fun setValidFrom(validFrom: Date?) = modifyCard { setValidFrom(validFrom) }
    fun setExpiry(expiry: Date?) = modifyCard { setExpiry(expiry) }
    fun setBalance(balance: BigDecimal) = modifyCard { setBalance(balance) }
    fun setBalanceType(balanceType: Currency?) = modifyCard { setBalanceType(balanceType) }
    fun setBarcodeId(barcodeId: String?) = modifyCard { setBarcodeId(barcodeId) }
    fun setBarcodeType(barcodeType: CatimaBarcode?) = modifyCard { setBarcodeType(barcodeType) }
    fun setHeaderColor(headerColor: Int?) = modifyCard { setHeaderColor(headerColor) }
    
    fun updateCardFromBundle(bundle: android.os.Bundle) = modifyCard {
        updateFromBundle(bundle, false)
    }

    fun setCardImage(imageLocationType: ImageLocationType, bitmap: Bitmap?, path: String?) {
        // Update images in unified state
        updateImages { images -> images + (imageLocationType to bitmap) }

        // Store in LoyaltyCard for persistence (it will copy defensively)
        modifyCard {
            when (imageLocationType) {
                ImageLocationType.icon -> setImageThumbnail(bitmap, path)
                ImageLocationType.front -> setImageFront(bitmap, path)
                ImageLocationType.back -> setImageBack(bitmap, path)
            }
        }
    }

    fun getImage(imageLocationType: ImageLocationType): Bitmap? {
        val state = _cardState.value
        return if (state is CardLoadState.Success) {
            state.images[imageLocationType] ?: loyaltyCard.getImageForImageLocationType(application, imageLocationType)
        } else {
            loyaltyCard.getImageForImageLocationType(application, imageLocationType)
        }
    }

    fun saveCard(selectedGroups: List<Group>) {
        if (_saveState.value is SaveState.Saving) return

        _saveState.value = SaveState.Saving

        viewModelScope.launch(dispatcher) {
            val result = cardRepository.saveCard(loyaltyCard, selectedGroups)
            result.fold(
                onSuccess = { cardId ->
                    _saveState.value = SaveState.Idle
                    _uiEvents.emit(UiEvent.SaveSuccess)
                },
                onFailure = { exception ->
                    _saveState.value = SaveState.Idle
                    val message = exception.message ?: "An unknown error occurred during save."
                    Log.e(TAG, "Failed to save card: $message")
                    _uiEvents.emit(UiEvent.ShowError(message))
                }
            )
        }
    }

}
class LoyaltyCardEditViewModelFactory(
    private val application: Application,
    private val database: SQLiteDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LoyaltyCardEditActivityViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LoyaltyCardEditActivityViewModel(
                application,
                CardRepository(database, application),
                dispatcher
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}