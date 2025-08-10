package protect.card_locker.viewmodels

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import protect.card_locker.BarcodeImageWriterTask
import protect.card_locker.LoyaltyCard
import protect.card_locker.LoyaltyCardField
import protect.card_locker.async.TaskHandler
import protect.card_locker.async.runSuspending


class LoyaltyCardEditActivityViewModel : ViewModel() {

    var initDone = false
    var onRestoring = false
    var onResuming = false
    var initialized: Boolean = false
    var hasChanged: Boolean = false

    private var barcodeGenerationJob: Job? = null

    fun executeTask(
        type: TaskHandler.TYPE, callable: BarcodeImageWriterTask
    ) {
        val job = viewModelScope.launch {
            try {
                callable.runSuspending()
            } catch (e: Exception) {

            }
        }
        if (type == TaskHandler.TYPE.BARCODE) {
            barcodeGenerationJob = job
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
    var tempLoyaltyCardField: LoyaltyCardField? = null

    var loyaltyCard: LoyaltyCard = LoyaltyCard()
}
