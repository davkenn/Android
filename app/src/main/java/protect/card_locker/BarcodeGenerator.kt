package protect.card_locker

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.util.TypedValue
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException

/**
 * Result of barcode generation containing the bitmap and metadata needed for display.
 * Follows the same patterns as BarcodeImageWriterTask but decoupled from Views.
 */
data class BarcodeGenerationResult(
    val bitmap: Bitmap?,
    val format: CatimaBarcode,
    val isValid: Boolean,
    val imagePadding: Int,
    val widthPadding: Boolean
)

/**
 * Pure barcode generation utility extracted from BarcodeImageWriterTask.
 * Generates barcodes without touching any Views - suitable for ViewModel/StateFlow usage.
 *
 * The original BarcodeImageWriterTask coupled generation with View manipulation.
 * This class separates concerns: generate bitmap here, let Activity handle rendering.
 */
object BarcodeGenerator {
    private const val TAG = "Catima"

    // When drawn in a smaller window 1D barcodes for some reason end up
    // squished, whereas 2D barcodes look fine.
    private const val MAX_WIDTH_1D = 1500
    private const val MAX_WIDTH_2D = 500

    /**
     * Generate a barcode bitmap with all the sizing/scaling logic from BarcodeImageWriterTask.
     *
     * @param context Context for display metrics
     * @param cardId The card ID to encode
     * @param format The barcode format
     * @param imageViewWidth Width of the ImageView that will display the barcode
     * @param imageViewHeight Height of the ImageView that will display the barcode
     * @param showFallback Whether to show a fallback barcode if generation fails
     * @param roundCornerPadding Whether to add padding for rounded corners
     * @param isFullscreen Whether this is being displayed fullscreen
     * @return BarcodeGenerationResult containing bitmap and display metadata
     */
    fun generate(
        context: Context,
        cardId: String,
        format: CatimaBarcode,
        imageViewWidth: Int,
        imageViewHeight: Int,
        showFallback: Boolean = false,
        roundCornerPadding: Boolean = true,
        isFullscreen: Boolean = false
    ): BarcodeGenerationResult {
        if (cardId.isEmpty()) {
            return BarcodeGenerationResult(
                bitmap = null,
                format = format,
                isValid = false,
                imagePadding = 0,
                widthPadding = false
            )
        }

        // Calculate dimensions - matches BarcodeImageWriterTask constructor logic
        val (imageWidth, imageHeight, imagePadding, widthPadding) = calculateDimensions(
            context = context,
            format = format,
            imageViewWidth = imageViewWidth,
            imageViewHeight = imageViewHeight,
            roundCornerPadding = roundCornerPadding,
            isFullscreen = isFullscreen
        )

        // Try to generate the barcode
        var bitmap = generateBitmap(cardId, format, imageWidth, imageHeight)
        var isValid = true

        // If generation failed, try fallback
        if (bitmap == null) {
            isValid = false
            if (showFallback) {
                Log.i(TAG, "Barcode generation failed, generating fallback...")
                val fallbackId = getFallbackString(format)
                if (fallbackId != null) {
                    bitmap = generateBitmap(fallbackId, format, imageWidth, imageHeight)
                }
            }
        }

        return BarcodeGenerationResult(
            bitmap = bitmap,
            format = format,
            isValid = isValid,
            imagePadding = imagePadding,
            widthPadding = widthPadding
        )
    }

    /**
     * Calculate image dimensions with padding - extracted from BarcodeImageWriterTask constructor.
     */
    private fun calculateDimensions(
        context: Context,
        format: CatimaBarcode,
        imageViewWidth: Int,
        imageViewHeight: Int,
        roundCornerPadding: Boolean,
        isFullscreen: Boolean
    ): DimensionResult {
        var adjustedWidth = imageViewWidth
        var adjustedHeight = imageViewHeight

        // Some barcodes already have internal whitespace and shouldn't get extra padding
        val imagePadding = if (roundCornerPadding && !format.hasInternalPadding()) {
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                10f,
                context.resources.displayMetrics
            ).toInt()
        } else {
            0
        }

        val widthPadding = if (format.isSquare && adjustedWidth > adjustedHeight) {
            adjustedWidth -= imagePadding
            true
        } else {
            adjustedHeight -= imagePadding
            false
        }

        val maxWidth = getMaxWidth(format)

        val (finalWidth, finalHeight) = when {
            format.isSquare -> {
                val size = minOf(adjustedHeight, minOf(maxWidth, adjustedWidth))
                size to size
            }
            imageViewWidth < maxWidth && !isFullscreen -> {
                adjustedWidth to adjustedHeight
            }
            else -> {
                // Scale down the image to reduce the memory needed to produce it
                val scaledWidth = minOf(maxWidth, context.resources.displayMetrics.widthPixels)
                val ratio = scaledWidth.toDouble() / imageViewWidth.toDouble()
                scaledWidth to (imageViewHeight * ratio).toInt()
            }
        }

        return DimensionResult(finalWidth, finalHeight, imagePadding, widthPadding)
    }

    private data class DimensionResult(
        val imageWidth: Int,
        val imageHeight: Int,
        val imagePadding: Int,
        val widthPadding: Boolean
    )

    /**
     * Get max width based on barcode format - extracted from BarcodeImageWriterTask.
     */
    private fun getMaxWidth(format: CatimaBarcode): Int {
        return when (format.format()) {
            // 2D barcodes
            BarcodeFormat.AZTEC,
            BarcodeFormat.MAXICODE,
            BarcodeFormat.PDF_417,
            BarcodeFormat.QR_CODE -> MAX_WIDTH_2D

            // 2D but rectangular versions get blurry otherwise
            BarcodeFormat.DATA_MATRIX -> MAX_WIDTH_1D

            // 1D barcodes
            BarcodeFormat.CODABAR,
            BarcodeFormat.CODE_39,
            BarcodeFormat.CODE_93,
            BarcodeFormat.CODE_128,
            BarcodeFormat.EAN_8,
            BarcodeFormat.EAN_13,
            BarcodeFormat.ITF,
            BarcodeFormat.UPC_A,
            BarcodeFormat.UPC_E,
            BarcodeFormat.RSS_14,
            BarcodeFormat.RSS_EXPANDED,
            BarcodeFormat.UPC_EAN_EXTENSION -> MAX_WIDTH_1D

            else -> MAX_WIDTH_1D
        }
    }

    /**
     * Get fallback string for barcode format - extracted from BarcodeImageWriterTask.
     */
    private fun getFallbackString(format: CatimaBarcode): String? {
        return when (format.format()) {
            // 2D barcodes
            BarcodeFormat.AZTEC -> "AZTEC"
            BarcodeFormat.DATA_MATRIX -> "DATA_MATRIX"
            BarcodeFormat.PDF_417 -> "PDF_417"
            BarcodeFormat.QR_CODE -> "QR_CODE"

            // 1D barcodes
            BarcodeFormat.CODABAR -> "C0C"
            BarcodeFormat.CODE_39 -> "CODE_39"
            BarcodeFormat.CODE_93 -> "CODE_93"
            BarcodeFormat.CODE_128 -> "CODE_128"
            BarcodeFormat.EAN_8 -> "32123456"
            BarcodeFormat.EAN_13 -> "5901234123457"
            BarcodeFormat.ITF -> "1003"
            BarcodeFormat.UPC_A -> "123456789012"
            BarcodeFormat.UPC_E -> "0123456"

            else -> null
        }
    }

    /**
     * Generate the actual bitmap - extracted from BarcodeImageWriterTask.generate().
     */
    private fun generateBitmap(
        cardId: String,
        format: CatimaBarcode,
        imageWidth: Int,
        imageHeight: Int
    ): Bitmap? {
        if (cardId.isEmpty() || imageWidth <= 0 || imageHeight <= 0) {
            return null
        }

        return try {
            val writer = MultiFormatWriter()
            val bitMatrix = try {
                writer.encode(cardId, format.format(), imageWidth, imageHeight, null)
            } catch (e: Exception) {
                // Cast a wider net here and catch any exception, as there are some
                // cases where an encoder may fail if the data is invalid for the
                // barcode type. If this happens, we want to fail gracefully.
                throw WriterException(e)
            }

            val bitMatrixWidth = bitMatrix.width
            val bitMatrixHeight = bitMatrix.height
            val pixels = IntArray(bitMatrixWidth * bitMatrixHeight)

            for (y in 0 until bitMatrixHeight) {
                val offset = y * bitMatrixWidth
                for (x in 0 until bitMatrixWidth) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) BLACK else WHITE
                }
            }

            var bitmap = Bitmap.createBitmap(
                bitMatrixWidth,
                bitMatrixHeight,
                Bitmap.Config.ARGB_8888
            )
            bitmap.setPixels(pixels, 0, bitMatrixWidth, 0, 0, bitMatrixWidth, bitMatrixHeight)

            // Determine if the image needs to be scaled.
            // This is necessary because the datamatrix barcode generator
            // ignores the requested size and returns the smallest image necessary
            // to represent the barcode. If we let the ImageView scale the image
            // it will use bi-linear filtering, which results in a blurry barcode.
            // To avoid this, if scaling is needed do so without filtering.
            val heightScale = imageHeight / bitMatrixHeight
            val widthScale = imageWidth / bitMatrixHeight
            val scalingFactor = minOf(heightScale, widthScale)

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
            Log.e(TAG, "Failed to generate barcode of type $format: $cardId", e)
            null
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Insufficient memory to render barcode, ${imageWidth}x$imageHeight, ${format.name()}, length=${cardId.length}", e)
            null
        }
    }

    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val BLACK = 0xFF000000.toInt()
}
