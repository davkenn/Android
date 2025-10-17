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

    var loyaltyCard: LoyaltyCard = LoyaltyCard()


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