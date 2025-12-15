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
import protect.card_locker.BarcodeImageWriterTask
import protect.card_locker.CardRepository
import protect.card_locker.CatimaBarcode
import protect.card_locker.Group
import protect.card_locker.LoyaltyCard
import protect.card_locker.async.TaskHandler
import protect.card_locker.async.runSuspending
import java.math.BigDecimal
import java.util.Currency
import java.util.Date
import protect.card_locker.ImageLocationType
import android.graphics.Bitmap

sealed interface SaveState {
    object Idle : SaveState
    object Saving : SaveState
}

sealed interface CardLoadState {
    object Loading : CardLoadState
    data class Success(
        var loyaltyCard: LoyaltyCard,
        val allGroups: List<Group>,
        val loyaltyCardGroups: List<Group>
    ) : CardLoadState
}

sealed interface UiEvent {
    data class ShowToast(val message: String) : UiEvent
    data class ShowError(val message: String) : UiEvent
    object SaveSuccess : UiEvent
    object LoadFailed : UiEvent
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

    // Display images cached separately from LoyaltyCard to avoid defensive copy overhead
    private val _displayImages = MutableStateFlow<Map<ImageLocationType, Bitmap?>>(emptyMap())
    val displayImages = _displayImages.asStateFlow()

    var tempStoredOldBarcodeValue: String? = null

    var initDone = false
    var onRestoring = false
    var onResuming = false
    var initialized: Boolean = false
    var hasChanged: Boolean = false

    private var barcodeGenerationJob: Job? = null

    fun executeTask(
        type: TaskHandler.TYPE, callable: BarcodeImageWriterTask
    ) {
        if (type == TaskHandler.TYPE.BARCODE) {
            cancelBarcodeGeneration()
        }
        barcodeGenerationJob = viewModelScope.launch(dispatcher) {
            try {
                callable.runSuspending()
            } catch (e: Exception) {
                Log.e(TAG, "Barcode generation failed", e)
            }
        }
    }


    fun cancelBarcodeGeneration(){
        barcodeGenerationJob?.cancel()
        barcodeGenerationJob = null
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
                        loyaltyCardGroups = data.loyaltyCardGroups
                    )
                    // Populate display images cache from loaded card (one-time copy on load)
                    _displayImages.value = mapOf(
                        ImageLocationType.icon to data.loyaltyCard.getImageThumbnail(application),
                        ImageLocationType.front to data.loyaltyCard.getImageFront(application),
                        ImageLocationType.back to data.loyaltyCard.getImageBack(application)
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
        }
    }

    fun onStoreNameChanged(newName: String) = modifyCard { store = newName.trim() }

    fun onNoteChanged(newNote: String) = modifyCard { note = newNote }

    fun onCardIdChanged(newCardId: String) = modifyCard {
        cardId = newCardId
        if (barcodeId == null || barcodeId == cardId) {
            barcodeId = newCardId
        }
    }

    fun setValidFrom(validFrom: Date?) = modifyCard { setValidFrom(validFrom) }
    fun setExpiry(expiry: Date?) = modifyCard { setExpiry(expiry) }
    fun setBalance(balance: BigDecimal) = modifyCard { setBalance(balance) }
    fun setBalanceType(balanceType: Currency?) = modifyCard { setBalanceType(balanceType) }
    fun setBarcodeId(barcodeId: String?) = modifyCard { setBarcodeId(barcodeId) }
    fun setBarcodeType(barcodeType: CatimaBarcode?) = modifyCard { setBarcodeType(barcodeType) }
    fun setHeaderColor(headerColor: Int?) = modifyCard { setHeaderColor(headerColor) }

    fun setCardImage(imageLocationType: ImageLocationType, bitmap: Bitmap?, path: String?) {
        // Cache original reference for display (no copy)
        _displayImages.value = _displayImages.value + (imageLocationType to bitmap)

        // Store in LoyaltyCard for persistence (it will copy defensively)
        modifyCard {
            when (imageLocationType) {
                ImageLocationType.icon -> setImageThumbnail(bitmap, path)
                ImageLocationType.front -> setImageFront(bitmap, path)
                ImageLocationType.back -> setImageBack(bitmap, path)
            }
        }
    }

    fun getImage(imageLocationType: ImageLocationType): Bitmap? =
        _displayImages.value[imageLocationType]
            ?: loyaltyCard.getImageForImageLocationType(application, imageLocationType)

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