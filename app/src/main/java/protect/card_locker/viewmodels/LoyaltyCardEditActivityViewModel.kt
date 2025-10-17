package protect.card_locker.viewmodels

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import protect.card_locker.BarcodeImageWriterTask
import protect.card_locker.CardRepository
import protect.card_locker.Group
import protect.card_locker.LoyaltyCard
import protect.card_locker.LoyaltyCardField
import protect.card_locker.async.TaskHandler
import protect.card_locker.async.runSuspending


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

    /**
     * Single source of truth: access the current loyalty card from cardState.
     * Returns the loaded card or a new card if not yet loaded.
     */
    var loyaltyCard: LoyaltyCard
        get() = (_cardState.value as? CardLoadState.Success)?.loyaltyCard ?: LoyaltyCard()
        set(value) {
            // When setting the card, we need to update the state
            val currentState = _cardState.value
            if (currentState is CardLoadState.Success) {
                _cardState.value = currentState.copy(loyaltyCard = value)
            }
        }

    /**
     * Access all groups from the current cardState.
     */
    val allGroups: List<Group>
        get() = (_cardState.value as? CardLoadState.Success)?.allGroups ?: emptyList()

    /**
     * Access loyalty card groups from the current cardState.
     */
    val loyaltyCardGroups: List<Group>
        get() = (_cardState.value as? CardLoadState.Success)?.loyaltyCardGroups ?: emptyList()

    /**
     * Loads card data from the repository and updates the cardState flow.
     * @param cardId The ID of the card to load (0 for new card)
     * @param importUri Optional URI to import card from
     * @param isDuplicate If true, loads card but clears ID for duplication
     */
    fun loadCard(
        cardId: Int = 0,
        importUri: Uri? = null,
        isDuplicate: Boolean = false
    ) {
        // Set loading state
        _cardState.value = CardLoadState.Loading

        viewModelScope.launch {
            val result = cardRepository.loadCardData(cardId, importUri, isDuplicate)

            _cardState.value = result.fold(
                onSuccess = { data ->
                    // Single source of truth: store everything in cardState
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

    fun saveCard(selectedGroups: List<Group>) {
        if (_saveState.value is SaveState.Saving) return // Debounce if already saving

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
    /**
     * Resets the save state to Idle. Should be called by the UI after handling
     * a Success or Error state.
     */
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