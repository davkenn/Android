package protect.card_locker.debug

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import org.json.JSONObject
import org.json.JSONArray

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
        is List<*> -> JSONArray(value.map { serializeValue(it) })
        is Map<*, *> -> JSONObject(value.mapKeys { it.key.toString() }.mapValues { serializeValue(it.value) })
        else -> value.toString() // Complex types become strings for now
    }
}
