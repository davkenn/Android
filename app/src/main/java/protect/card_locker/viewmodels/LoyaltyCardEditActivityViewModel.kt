package protect.card_locker.viewmodels

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import protect.card_locker.BarcodeImageWriterTask
import protect.card_locker.CardRepository
import protect.card_locker.CatimaBarcode
import protect.card_locker.Group
import protect.card_locker.LoyaltyCard
import protect.card_locker.LoyaltyCardField
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
    data class Success(val cardId: Int) : SaveState
    data class Error(val message: String) : SaveState
}

sealed interface CardLoadState {
    object Loading : CardLoadState
    data class Success(
        var loyaltyCard: LoyaltyCard,
        val allGroups: List<Group>,
        val loyaltyCardGroups: List<Group>
    ) : CardLoadState
    data class Error(val message: String) : CardLoadState
}

class LoyaltyCardEditActivityViewModel(
    private val application: Application,
    private val cardRepository: CardRepository
) : ViewModel() {

    constructor(application: Application) : this(application, CardRepository(application))
    private companion object {
        private const val TAG = "Catima"
    }

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState = _saveState.asStateFlow()

    private val _cardState = MutableStateFlow<CardLoadState>(CardLoadState.Loading)
    val cardState = _cardState.asStateFlow()

    private val _storeNameError = MutableStateFlow<String?>(null)
    val storeNameError = _storeNameError.asStateFlow()

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
        barcodeGenerationJob = viewModelScope.launch {
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
    var loyaltyCardId: Int = 0
    var updateLoyaltyCard: Boolean = false
    var duplicateFromLoyaltyCardId: Boolean = false
    var importLoyaltyCardUri: Uri? = null

    var tabIndex: Int = 0
    var requestedImageType: Int = 0
    var currentImageOperation: protect.card_locker.LoyaltyCardEditActivity.ImageOperation? = null
    var tempLoyaltyCardField: LoyaltyCardField? = null

    var loyaltyCard: LoyaltyCard
        get() = (_cardState.value as? CardLoadState.Success)?.loyaltyCard ?: LoyaltyCard()
        set(value) {
            val currentState = _cardState.value
            if (currentState is CardLoadState.Success) {
                _cardState.value = currentState.copy(loyaltyCard = value)
            }
        }

    val allGroups: List<Group>
        get() = (_cardState.value as? CardLoadState.Success)?.allGroups ?: emptyList()

    val loyaltyCardGroups: List<Group>
        get() = (_cardState.value as? CardLoadState.Success)?.loyaltyCardGroups ?: emptyList()

    fun loadCard(
        cardId: Int = 0,
        importUri: Uri? = null,
        isDuplicate: Boolean = false
    ) {
        _cardState.value = CardLoadState.Loading

        viewModelScope.launch {
            val result = cardRepository.loadCardData(cardId, importUri, isDuplicate)

            _cardState.value = result.fold(
                onSuccess = { data ->
                    CardLoadState.Success(
                        loyaltyCard = data.loyaltyCard,
                        allGroups = data.allGroups,
                        loyaltyCardGroups = data.loyaltyCardGroups
                    )
                },
                onFailure = { exception ->
                    CardLoadState.Error(
                        exception.message ?: "An unknown error occurred while loading card data."
                    )
                }
            )
        }
    }

    fun onStoreNameChanged(newName: String) {
        val trimmedName = newName.trim()

        val currentState = _cardState.value
        if (currentState is CardLoadState.Success) {
            currentState.loyaltyCard.store = trimmedName
            hasChanged = true
        }

        _storeNameError.value = if (trimmedName.isEmpty()) {
            application.getString(protect.card_locker.R.string.field_must_not_be_empty)
        } else {
            null
        }
    }

    fun onNoteChanged(newNote: String) {
        val currentState = _cardState.value
        if (currentState is CardLoadState.Success) {
            currentState.loyaltyCard.note = newNote
            hasChanged = true
        }
    }

    fun onCardIdChanged(newCardId: String) {
        val currentState = _cardState.value
        if (currentState is CardLoadState.Success) {
            val card = currentState.loyaltyCard
            card.cardId = newCardId

            if (card.barcodeId == null || card.barcodeId == card.cardId) {
                card.barcodeId = newCardId
            }
            hasChanged = true
        }
    }

    fun onBarcodeIdChanged(newBarcodeId: String) {
        val currentState = _cardState.value
        if (currentState is CardLoadState.Success) {
            currentState.loyaltyCard.barcodeId = newBarcodeId.ifEmpty { null }
            hasChanged = true
        }
    }

    fun setValidFrom(validFrom: Date?) {
        loyaltyCard.setValidFrom(validFrom)
        hasChanged = true
    }

    fun setExpiry(expiry: Date?) {
        loyaltyCard.setExpiry(expiry)
        hasChanged = true
    }

    fun setBalance(balance: BigDecimal) {
        loyaltyCard.setBalance(balance)
        hasChanged = true
    }

    fun setBalanceType(balanceType: Currency?) {
        loyaltyCard.setBalanceType(balanceType)
        hasChanged = true
    }

    fun setBarcodeId(barcodeId: String?) {
        loyaltyCard.setBarcodeId(barcodeId)
        hasChanged = true
    }

    fun setBarcodeType(barcodeType: CatimaBarcode?) {
        loyaltyCard.setBarcodeType(barcodeType)
        hasChanged = true
    }

    fun setHeaderColor(headerColor: Int?) {
        loyaltyCard.setHeaderColor(headerColor)
        hasChanged = true
    }

    fun setCardImage(imageLocationType: ImageLocationType, bitmap: Bitmap?, path: String?) {
        when (imageLocationType) {
            ImageLocationType.icon -> loyaltyCard.setImageThumbnail(bitmap, path)
            ImageLocationType.front -> loyaltyCard.setImageFront(bitmap, path)
            ImageLocationType.back -> loyaltyCard.setImageBack(bitmap, path)
        }
    }

    fun getImage(imageLocationType: ImageLocationType): Bitmap? {
        return when (imageLocationType) {
            ImageLocationType.icon -> loyaltyCard.getImageThumbnail(application)
            ImageLocationType.front -> loyaltyCard.getImageFront(application)
            ImageLocationType.back -> loyaltyCard.getImageBack(application)
        }
    }

    fun saveCard(selectedGroups: List<Group>) {
        if (_saveState.value is SaveState.Saving) return

        _saveState.value = SaveState.Saving

        viewModelScope.launch {
            val result = cardRepository.saveCard(loyaltyCard, selectedGroups)
            result.fold(
                onSuccess = { cardId ->
                    _saveState.value = SaveState.Success(cardId)
                },
                onFailure = { exception ->
                    _saveState.value = SaveState.Error(exception.message ?: "An unknown error occurred during save.")
                }
            )
        }
    }

    fun onSaveComplete() {
        _saveState.value = SaveState.Idle
    }

}
class LoyaltyCardEditViewModelFactory(private val application: Application) : ViewModelProvider.AndroidViewModelFactory(application) {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LoyaltyCardEditActivityViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LoyaltyCardEditActivityViewModel(application,CardRepository(application)) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}