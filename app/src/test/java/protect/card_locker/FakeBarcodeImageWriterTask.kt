package protect.card_locker


import android.content.Context
import android.graphics.Bitmap
import android.widget.ImageView
import android.widget.TextView

class FakeBarcodeImageWriterTask(
    context: Context,
    imageView: ImageView,
    cardIdString: String,
    barcodeFormat: CatimaBarcode,
    textView: TextView?,
    showFallback: Boolean,
    callback: BarcodeImageWriterResultCallback?,
    roundCornerPadding: Boolean,
    isFullscreen: Boolean
) : BarcodeImageWriterTask(
    context, imageView, cardIdString, barcodeFormat, textView,
    showFallback, callback, roundCornerPadding, isFullscreen
) {

    var wasExecuted = false
    var shouldFail = false
    var shouldThrowException = false
    var mockBitmap: Bitmap? = null

    override fun doInBackground(vararg params: Void?): Bitmap? {
        wasExecuted = true

        if (shouldThrowException) {
            throw RuntimeException("Test exception")
        }

        if (shouldFail) {
            return null
        }

        // Return mock bitmap or create a simple test bitmap
        return mockBitmap ?: Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
    }

    override fun call(): Bitmap? {
        return doInBackground()
    }

    override fun onPostExecute(castResult: Any?) {
        // Override to prevent actual UI updates during tests
        // or call super.onPostExecute(castResult) if you want to test UI updates
    }

    override fun onPreExecute() {
        // No action needed for tests
    }
}
