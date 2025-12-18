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
import protect.card_locker.BarcodeGenerator
import protect.card_locker.CardRepository
import protect.card_locker.CatimaBarcode
import protect.card_locker.Group
import protect.card_locker.LoyaltyCard
import java.math.BigDecimal
import java.util.Currency
import java.util.Date
import protect.card_locker.ImageLocationType
import protect.card_locker.Utils
import android.graphics.Bitmap
import androidx.annotation.StringRes
import kotlinx.coroutines.withContext
import protect.card_locker.R

sealed interface SaveState {
    object Idle : SaveState
    object Saving : SaveState

    /** Save failed - user can retry */
    data class Error(
        val message: String,
        @StringRes val messageResId: Int = R.string.generic_error_please_retry
    ) : SaveState
}

enum class EditTab {
    CARD, OPTIONS, PHOTOS
}

sealed interface CardLoadState {
    object Loading : CardLoadState

    data class Success(
        var loyaltyCard: LoyaltyCard,
        val allGroups: List<Group>,
        val loyaltyCardGroups: List<Group>,
        val images: Map<ImageLocationType, Bitmap?> = emptyMap(),
        val barcodeState: BarcodeState = BarcodeState.None,
        val thumbnailState: ThumbnailState = ThumbnailState.None,
        val currentTab: EditTab = EditTab.CARD,
        val version: Long = 0
    ) : CardLoadState

    /** Fatal error loading card - Activity should show error and finish */
    data class Error(
        val message: String,
        @StringRes val messageResId: Int = R.string.noCardExistsError
    ) : CardLoadState
}

/**
 * One-time UI events (side effects).
 * Use for toasts and navigation - NOT for errors that affect rendering.
 * Errors that affect UI should be in State (CardLoadState.Error, SaveState.Error, etc.)
 */
sealed interface UiEvent {

    data class ShowToast(val message: String) : UiEvent

    data class ShowToastRes(@StringRes val messageResId: Int) : UiEvent

    object SaveSuccess : UiEvent
}

/**
 * Barcode display state - follows same pattern as displayImages StateFlow.
 * Keeps barcode bitmap in ViewModel so Activity just observes and renders.
 *
 * Includes padding information from BarcodeGenerator to match BarcodeImageWriterTask behavior.
 */
sealed interface BarcodeState {
    /** No barcode (empty card ID or no barcode type selected) */
    object None : BarcodeState

    data class Generated(
        val bitmap: Bitmap,
        val format: CatimaBarcode,
        val isValid: Boolean = true,  // false when showing fallback barcode
        val imagePadding: Int = 0,
        val widthPadding: Boolean = false
    ) : BarcodeState

    object Error : BarcodeState
}

sealed interface ThumbnailState {
    object None : ThumbnailState

    /**
     * Thumbnail ready to display.
     * @param iconBitmap Custom icon image, or null to show generated letter tile (Activity generates it)
     * @param storeName Store name (used for letter tile generation)
     * @param headerColor The card's header color (used for letter tile background)
     * @param needsDarkForeground Whether text/icons should be dark (for contrast)
     */
    data class Ready(
        val iconBitmap: Bitmap?,
        val storeName: String,
        val headerColor: Int,
        val needsDarkForeground: Boolean
    ) : ThumbnailState

    data class Error(
        val fallbackColor: Int,
        val storeName: String
    ) : ThumbnailState
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

    // Store last barcode dimensions for regeneration
    private var lastBarcodeWidth: Int? = null
    private var lastBarcodeHeight: Int? = null

    /** Update barcodeState within the unified CardLoadState */
    private fun updateBarcodeState(newState: BarcodeState) {
        val state = _cardState.value
        if (state is CardLoadState.Success) {
            _cardState.value = state.copy(barcodeState = newState)
        }
    }

    private fun updateImages(update: (Map<ImageLocationType, Bitmap?>) -> Map<ImageLocationType, Bitmap?>) {
        val state = _cardState.value
        if (state is CardLoadState.Success) {
            _cardState.value = state.copy(images = update(state.images))
        }
    }

    /** Update thumbnailState within the unified CardLoadState */
    private fun updateThumbnailState(newState: ThumbnailState) {
        val state = _cardState.value
        if (state is CardLoadState.Success) {
            _cardState.value = state.copy(thumbnailState = newState)
        }
    }

    /**
     * Compute thumbnail state based on icon image and header color.
     * Centralizes all the color derivation logic that was scattered in Activity.
     * Wrapped in try-catch for graceful degradation.
     */
    private fun computeThumbnailState(
        iconBitmap: Bitmap?,
        storeName: String,
        currentHeaderColor: Int?
    ): ThumbnailState {
        return try {
            // Derive header color: from icon image if present, otherwise use existing or generate
            val headerColor = when {
                iconBitmap != null -> Utils.getHeaderColorFromImage(
                    iconBitmap,
                    currentHeaderColor ?: Utils.getHeaderColor(application, loyaltyCard)
                )
                currentHeaderColor != null -> currentHeaderColor
                else -> Utils.getHeaderColor(application, loyaltyCard)
            }

            val needsDarkForeground = Utils.needsDarkForeground(headerColor)

            ThumbnailState.Ready(
                iconBitmap = iconBitmap,
                storeName = storeName,
                headerColor = headerColor,
                needsDarkForeground = needsDarkForeground
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to compute thumbnail state", e)
            ThumbnailState.Error(
                fallbackColor = Utils.getRandomHeaderColor(application),
                storeName = storeName
            )
        }
    }

    /**
     * Recompute thumbnail state. Call when icon, store name, or header color changes.
     */
    fun refreshThumbnailState() {
        val state = _cardState.value
        if (state is CardLoadState.Success) {
            val iconBitmap = state.images[ImageLocationType.icon]
            val storeName = state.loyaltyCard.store ?: ""
            val headerColor = state.loyaltyCard.headerColor

            val thumbnailState = computeThumbnailState(iconBitmap, storeName, headerColor)

            // Also update headerColor in loyaltyCard if it was derived from icon
            if (thumbnailState is ThumbnailState.Ready) {
                state.loyaltyCard.headerColor = thumbnailState.headerColor
            }

            updateThumbnailState(thumbnailState)
        }
    }

    /**
     * Generate barcode using BarcodeGenerator and store in unified cardState.
     * Activity observes cardState and renders.
     *
     * Uses BarcodeGenerator which extracts the logic from BarcodeImageWriterTask,
     * including proper dimension calculations, scaling, and fallback handling.
     */
    fun generateBarcode(width: Int, height: Int, showFallback: Boolean = false) {
        // Store dimensions for future regeneration
        lastBarcodeWidth = width
        lastBarcodeHeight = height

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
                    BarcodeGenerator.generate(
                        context = application,
                        cardId = cardIdToUse,
                        format = format,
                        imageViewWidth = width,
                        imageViewHeight = height,
                        showFallback = showFallback,
                        roundCornerPadding = true,
                        isFullscreen = false
                    )
                }

                val state = if (result.bitmap != null) {
                    BarcodeState.Generated(
                        bitmap = result.bitmap,
                        format = result.format,
                        isValid = result.isValid,
                        imagePadding = result.imagePadding,
                        widthPadding = result.widthPadding
                    )
                } else {
                    BarcodeState.Error
                }
                updateBarcodeState(state)
            } catch (e: Exception) {
                Log.e(TAG, "Barcode generation failed", e)
                updateBarcodeState(BarcodeState.Error)
            }
        }
    }

    /**
     * Regenerate barcode with stored dimensions.
     * Called when cardId, barcodeId, or barcodeType changes.
     * Matches Java behavior where these changes trigger immediate barcode regeneration.
     */
    private fun regenerateBarcode() {
        val width = lastBarcodeWidth
        val height = lastBarcodeHeight

        // Only regenerate if we have dimensions from a previous generation
        if (width != null && height != null) {
            generateBarcode(width, height)
        }
        // If no dimensions yet, first generation will happen when Activity calls generateBarcode()
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
        isDuplicate: Boolean = false,
        bundle: android.os.Bundle? = null
    ) {
        loyaltyCardId = cardId
        _cardState.value = CardLoadState.Loading

        viewModelScope.launch(dispatcher) {
            val result = cardRepository.loadCardData(cardId, importUri, isDuplicate)

            result.fold(
                onSuccess = { data ->
                    // Apply bundle updates if present
                    if (bundle != null) {
                        data.loyaltyCard.updateFromBundle(bundle, false)
                    }

                    val iconBitmap = data.loyaltyCard.getImageThumbnail(application)
                    val storeName = data.loyaltyCard.store ?: ""
                    val headerColor = data.loyaltyCard.headerColor

                    // Compute initial thumbnail state
                    val thumbnailState = computeThumbnailState(iconBitmap, storeName, headerColor)

                    // Update header color if derived from icon
                    if (thumbnailState is ThumbnailState.Ready && iconBitmap != null) {
                        data.loyaltyCard.headerColor = thumbnailState.headerColor
                    }

                    _cardState.value = CardLoadState.Success(
                        loyaltyCard = data.loyaltyCard,
                        allGroups = data.allGroups,
                        loyaltyCardGroups = data.loyaltyCardGroups,
                        images = mapOf(
                            ImageLocationType.icon to iconBitmap,
                            ImageLocationType.front to data.loyaltyCard.getImageFront(application),
                            ImageLocationType.back to data.loyaltyCard.getImageBack(application)
                        ),
                        thumbnailState = thumbnailState,
                        currentTab = EditTab.entries.getOrElse(tabIndex) { EditTab.CARD }
                    )
                },
                onFailure = { exception ->
                    val message = exception.message ?: "An unknown error occurred while loading card data."
                    Log.w(TAG, "Failed to load card: $message")

                    // Determine appropriate error message based on context
                    val messageResId = when {
                        importUri != null -> R.string.failedParsingImportUriError
                        cardId > 0 -> R.string.noCardExistsError
                        else -> R.string.generic_error_please_retry
                    }

                    // Use State for error - Activity observes and responds
                    _cardState.value = CardLoadState.Error(
                        message = message,
                        messageResId = messageResId
                    )
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

    fun selectTab(index: Int) {
        tabIndex = index
        val tab = EditTab.entries.getOrElse(index) { EditTab.CARD }
        val state = _cardState.value
        if (state is CardLoadState.Success) {
            _cardState.value = state.copy(currentTab = tab)
        }
    }

    fun onStoreNameChanged(newName: String) {
        modifyCard { store = newName.trim() }
        // Recompute thumbnail state since letter tile depends on store name
        refreshThumbnailState()
    }

    fun onNoteChanged(newNote: String) = modifyCard { note = newNote }

    fun onCardIdChanged(newCardId: String) {
        modifyCard {
            if (barcodeId != null && barcodeId == cardId) {
                barcodeId = newCardId
            }
            cardId = newCardId
        }
      //should this be sameasid or something rather than null?
        if (loyaltyCard.barcodeId == null && loyaltyCard.barcodeType != null) {
            regenerateBarcode()
        }
    }

    fun setValidFrom(validFrom: Date?) = modifyCard { setValidFrom(validFrom) }
    fun setExpiry(expiry: Date?) = modifyCard { setExpiry(expiry) }
    fun setBalance(balance: BigDecimal) = modifyCard { setBalance(balance) }
    fun setBalanceType(balanceType: Currency?) = modifyCard { setBalanceType(balanceType) }

    fun setBarcodeId(barcodeId: String?) {
        modifyCard { setBarcodeId(barcodeId) }
        // Regenerate barcode when barcodeId changes (matches Java behavior)
        regenerateBarcode()
    }

    fun setBarcodeType(barcodeType: CatimaBarcode?) {
        modifyCard { setBarcodeType(barcodeType) }
        regenerateBarcode()
    }

    fun setHeaderColor(headerColor: Int?) {
        modifyCard { setHeaderColor(headerColor) }
        refreshThumbnailState()
    }
    
    fun updateCardFromBundle(bundle: android.os.Bundle) = modifyCard {
        updateFromBundle(bundle, false)
    }

    fun setCardImage(imageLocationType: ImageLocationType, bitmap: Bitmap?, path: String?) {
        val state = _cardState.value
        if (state !is CardLoadState.Success) return

        // 1. Update Images Map
        val newImages = state.images + (imageLocationType to bitmap)

        // 2. Update LoyaltyCard Persistence
        state.loyaltyCard.apply {
            when (imageLocationType) {
                ImageLocationType.icon -> setImageThumbnail(bitmap, path)
                ImageLocationType.front -> setImageFront(bitmap, path)
                ImageLocationType.back -> setImageBack(bitmap, path)
            }
        }
        hasChanged = true

        // 3. Compute new Thumbnail State (if icon)
        var newThumbnailState = state.thumbnailState
        if (imageLocationType == ImageLocationType.icon) {
            val storeName = state.loyaltyCard.store
            // Pass current header color; computeThumbnailState will derive new one from bitmap if present
            newThumbnailState = computeThumbnailState(bitmap, storeName, state.loyaltyCard.headerColor)

            if (newThumbnailState is ThumbnailState.Ready) {
                state.loyaltyCard.headerColor = newThumbnailState.headerColor
            }
        }

        _cardState.value = state.copy(
            images = newImages,
            loyaltyCard = state.loyaltyCard,
            thumbnailState = newThumbnailState,
            version = System.currentTimeMillis()
        )
    }

    fun setThumbnailColor(color: Int) {
        updateImages { images -> images + (ImageLocationType.icon to null) }
        
        modifyCard {
            setImageThumbnail(null, null)
            setHeaderColor(color)
        }
        
        refreshThumbnailState()
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
                    loyaltyCardId = cardId
                    _saveState.value = SaveState.Idle
                    _uiEvents.emit(UiEvent.SaveSuccess)
                },
                onFailure = { exception ->
                    val message = exception.message ?: "An unknown error occurred during save."
                    Log.e(TAG, "Failed to save card: $message")

                    // Use SaveState.Error so Activity can respond appropriately
                    _saveState.value = SaveState.Error(
                        message = message,
                        messageResId = R.string.generic_error_please_retry
                    )

                    // Also emit toast for immediate user feedback
                    _uiEvents.emit(UiEvent.ShowToastRes(R.string.generic_error_please_retry))
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