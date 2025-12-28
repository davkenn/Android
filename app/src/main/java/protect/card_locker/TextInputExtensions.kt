package protect.card_locker

import android.text.Editable
import android.widget.EditText
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Extension function to convert EditText text changes into a Flow.
 *
 * This replaces the traditional TextWatcher pattern with a reactive Flow-based approach,
 * enabling cleaner unidirectional data flow in MVVM architecture.
 *
 * @param emitInitialValue If true, emits the current text value immediately upon collection
 * @return Flow<String> that emits text changes
 *
 * Example usage:
 * ```
 * binding.storeNameEdit.textChangesAsFlow()
 *     .debounce(300)
 *     .onEach { viewModel.validateStoreName(it) }
 *     .launchIn(lifecycleScope)
 * ```
 */
fun EditText.textChangesAsFlow(emitInitialValue: Boolean = true): Flow<String> = callbackFlow {
    val listener = object : SimpleTextWatcher() {
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            trySend(s.toString())
        }
    }

    addTextChangedListener(listener)

    // Emit initial value if requested
    if (emitInitialValue) {
        trySend(text.toString())
    }

    awaitClose {
        removeTextChangedListener(listener)
    }
}

/**
 * Extension function to convert EditText text changes into a trimmed Flow.
 * Automatically trims whitespace from emitted values.
 *
 * @param emitInitialValue If true, emits the current trimmed text immediately
 * @return Flow<String> that emits trimmed text changes
 */
fun EditText.trimmedTextChangesAsFlow(emitInitialValue: Boolean = true): Flow<String> = callbackFlow {
    val listener = object : SimpleTextWatcher() {
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            trySend(s.toString().trim())
        }
    }

    addTextChangedListener(listener)

    if (emitInitialValue) {
        trySend(text.toString().trim())
    }

    awaitClose {
        removeTextChangedListener(listener)
    }
}

/**
 * Convenience extension to collect a Flow in a lifecycle-aware manner.
 * Automatically cancels collection when the lifecycle is destroyed.
 *
 * @param lifecycleOwner The lifecycle owner (typically an Activity or Fragment)
 * @param minActiveState Minimum lifecycle state for collection (default: STARTED)
 * @param action The action to perform on each emitted value
 *
 * Example:
 * ```
 * binding.noteEdit.textChangesAsFlow()
 *     .collectIn(this) { text ->
 *         viewModel.updateNote(text)
 *     }
 * ```
 */
fun <T> Flow<T>.collectIn(
    lifecycleOwner: LifecycleOwner,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    action: suspend (T) -> Unit
) {
    lifecycleOwner.lifecycleScope.launch {
        lifecycleOwner.repeatOnLifecycle(minActiveState) {
            collect { action(it) }
        }
    }
}

/**
 * Helper extension that combines text flow with debouncing, deduplication,
 * and lifecycle-aware collection in one call.
 *
 * Perfect for validation scenarios where you want to:
 * - Avoid excessive validation calls while typing
 * - Only validate when the value actually changes
 * - Automatically stop when the Activity/Fragment is paused
 *
 * @param lifecycleOwner The lifecycle owner
 * @param debounceMs Milliseconds to wait after typing stops (0 = no debounce)
 * @param action The validation/processing action
 *
 * Example:
 * ```
 * binding.balanceField.observeTextChanges(this, debounceMs = 300) { balance ->
 *     viewModel.validateBalance(balance)
 * }
 * ```
 */
fun EditText.observeTextChanges(
    lifecycleOwner: LifecycleOwner,
    debounceMs: Long = 0,
    emitInitialValue: Boolean = true,
    trim: Boolean = false,
    action: suspend (String) -> Unit
) {
    val flow = if (trim) {
        trimmedTextChangesAsFlow(emitInitialValue)
    } else {
        textChangesAsFlow(emitInitialValue)
    }

    flow
        .let { if (debounceMs > 0) it.debounce(debounceMs) else it }
        .distinctUntilChanged()
        .collectIn(lifecycleOwner) { action(it) }
}

/**
 * Extension function to track text changes along with the previous value.
 * Useful for scenarios where you need to know what the text was before it changed.
 *
 * @param emitInitialValue If true, emits (null, currentText) immediately
 * @return Flow<Pair<String?, String>> where first is previous value, second is current value
 *
 * Example:
 * ```
 * binding.dateField.textChangesWithPrevious()
 *     .onEach { (previous, current) ->
 *         if (current == "Choose Date" && previous != "Choose Date") {
 *             // Show date picker
 *         }
 *     }
 *     .launchIn(lifecycleScope)
 * ```
 */
fun EditText.textChangesWithPrevious(emitInitialValue: Boolean = true): Flow<Pair<String?, String>> = callbackFlow {
    var previousValue: String? = null

    val listener = object : SimpleTextWatcher() {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            previousValue = s?.toString()
        }

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            trySend(Pair(previousValue, s.toString()))
        }
    }

    addTextChangedListener(listener)

    // Emit initial value if requested
    if (emitInitialValue) {
        trySend(Pair(null, text.toString()))
    }

    awaitClose {
        removeTextChangedListener(listener)
    }
}
