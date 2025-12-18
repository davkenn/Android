package protect.card_locker.debug

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import org.json.JSONObject
import org.json.JSONArray
import java.security.MessageDigest

/**
 * Flow Recording Tool for Testing
 *
 * TEMPORARY FILE - Not for production or OSS commits
 *
 * Usage:
 * 1. Copy this file into your project at: app/src/main/java/protect/card_locker/debug/
 * 2. Add .monitor("flowName") to flows you want to record
 * 3. Run the app and interact normally
 * 4. Use record_session.sh to capture and parse logs
 * 5. Remove this file before committing
 *
 * Example:
 *   val cardState = _cardState.asStateFlow().monitor("cardState")
 */

/**
 * Extension function that monitors Flow emissions and logs them as JSON
 *
 * @param tag Unique identifier for this flow (e.g., "cardState", "saveState")
 * @return The original flow with monitoring attached
 */
fun <T> Flow<T>.monitor(tag: String): Flow<T> {
    return this.onEach { value ->
        try {
            val json = createFlowEventJson(tag, value)
            // Special marker for easy grep: FLOW_EVENT followed by JSON
            Log.d("FlowRecorder", "FLOW_EVENT:$json")
        } catch (e: Exception) {
            Log.e("FlowRecorder", "Failed to serialize flow emission for $tag", e)
        }
    }
}

/**
 * Creates a JSON representation of a flow emission
 *
 * Format:
 * {
 *   "flow": "cardState",
 *   "timestamp": 1702683099730,
 *   "value": "Success(loyaltyCard=..., version=123)"
 * }
 */
private fun <T> createFlowEventJson(tag: String, value: T): JSONObject {
    return JSONObject().apply {
        put("flow", tag)
        put("timestamp", System.currentTimeMillis())
        put("value", serializeValue(value))
    }
}

/**
 * Converts flow emission values to JSON-serializable format
 *
 * For complex types (like data classes), falls back to toString()
 * The parser will handle converting these strings back to typed objects
 */
private fun serializeValue(value: Any?): Any {
    return when (value) {
        null -> JSONObject.NULL
        is String -> value
        is Number -> value
        is Boolean -> value
        is Bitmap -> serializeBitmap(value)
        is List<*> -> JSONArray(value.map { serializeValue(it) })
        is Map<*, *> -> JSONObject(value.mapKeys { it.key.toString() }.mapValues { serializeValue(it.value) })
        else -> value.toString() // Complex types become strings for now
    }
}

/**
 * Serializes a Bitmap to a JSON object with useful metadata
 *
 * Instead of logging the object reference (which changes on every new Bitmap),
 * we compute a content hash so we can identify when two bitmaps have the same pixels.
 *
 * Format:
 * {
 *   "type": "Bitmap",
 *   "width": 500,
 *   "height": 300,
 *   "config": "ARGB_8888",
 *   "byteCount": 600000,
 *   "contentHash": "a1b2c3d4e5f6..."
 * }
 */
private fun serializeBitmap(bitmap: Bitmap): JSONObject {
    return JSONObject().apply {
        put("type", "Bitmap")
        put("width", bitmap.width)
        put("height", bitmap.height)
        put("config", bitmap.config?.toString() ?: "null")
        put("byteCount", bitmap.byteCount)

        // Compute a hash of the bitmap pixels to identify identical content
        // This is expensive but only runs during debug recording
        try {
            val hash = computeBitmapHash(bitmap)
            put("contentHash", hash)
        } catch (e: Exception) {
            put("contentHash", "error:${e.message}")
        }
    }
}

/**
 * Computes a SHA-256 hash of the bitmap's pixel data
 *
 * Two bitmaps with the same pixels will have the same hash,
 * even if they're different objects in memory.
 */
private fun computeBitmapHash(bitmap: Bitmap): String {
    // Get pixel data as IntArray
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

    // Convert IntArray to ByteArray for hashing
    val bytes = ByteArray(pixels.size * 4)
    pixels.forEachIndexed { index, pixel ->
        bytes[index * 4] = (pixel shr 24).toByte()     // Alpha
        bytes[index * 4 + 1] = (pixel shr 16).toByte() // Red
        bytes[index * 4 + 2] = (pixel shr 8).toByte()  // Green
        bytes[index * 4 + 3] = pixel.toByte()           // Blue
    }

    // Compute SHA-256 hash
    val digest = MessageDigest.getInstance("SHA-256")
    val hashBytes = digest.digest(bytes)

    // Convert to hex string (first 16 hex chars = 8 bytes for readability)
    return hashBytes.take(8).joinToString("") { "%02x".format(it) }
}
