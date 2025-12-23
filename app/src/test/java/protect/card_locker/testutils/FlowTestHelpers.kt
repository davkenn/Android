package protect.card_locker.testutils

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Flow testing utilities using Turbine.
 *
 * Turbine provides a clean API for testing Flow emissions over time.
 * Use this for complex flow scenarios where you need to:
 * - Verify emission order
 * - Test multiple emissions
 * - Handle timeouts
 * - Skip intermediate values
 *
 * See: https://github.com/cashapp/turbine
 */

/**
 * Assert a StateFlow emits an expected value.
 *
 * This is useful for quick assertions on StateFlows where you just
 * want to verify the current value.
 *
 * Example:
 * ```kotlin
 * viewModel.cardState.assertEmits(CardLoadState.Success(...))
 * ```
 *
 * @param expected The expected value
 * @param timeout Maximum time to wait for emission (default 1 second)
 */
suspend fun <T> StateFlow<T>.assertEmits(expected: T, timeout: Duration = 1.seconds) {
    test(timeout = timeout) {
        assertEquals(expected, awaitItem())
    }
}

/**
 * Collect all emissions from a Flow within a test block.
 *
 * This is the main pattern for testing Flows with Turbine.
 * The test block gives you access to `awaitItem()`, `expectNoEvents()`,
 * `awaitComplete()`, and other Turbine utilities.
 *
 * Example:
 * ```kotlin
 * viewModel.uiEvents.testEmissions {
 *     viewModel.saveCard(emptyList())
 *
 *     // Assert first emission
 *     val event1 = awaitItem()
 *     assertTrue(event1 is UiEvent.ShowToast)
 *
 *     // Assert second emission
 *     val event2 = awaitItem()
 *     assertTrue(event2 is UiEvent.SaveSuccess)
 *
 *     // Assert completion
 *     awaitComplete()
 * }
 * ```
 *
 * @param timeout Maximum time to wait for emissions (default 1 second)
 * @param validate Lambda with Turbine's ReceiveTurbine receiver for assertions
 */
suspend fun <T> Flow<T>.testEmissions(
    timeout: Duration = 1.seconds,
    validate: suspend ReceiveTurbine<T>.() -> Unit
) {
    test(timeout = timeout, validate = validate)
}

/**
 * Wait for a specific StateFlow value.
 *
 * This is useful when you want to wait for a StateFlow to reach
 * a particular state, skipping intermediate values.
 *
 * Example:
 * ```kotlin
 * // Wait for save to complete
 * viewModel.saveState.awaitValue { it is SaveState.Idle }
 *
 * // Or with more complex condition
 * viewModel.cardState.awaitValue { state ->
 *     state is CardLoadState.Success && state.loyaltyCard.id == 1
 * }
 * ```
 *
 * @param timeout Maximum time to wait (default 1 second)
 * @param predicate Condition to wait for
 */
suspend fun <T> StateFlow<T>.awaitValue(
    timeout: Duration = 1.seconds,
    predicate: (T) -> Boolean
) {
    test(timeout = timeout) {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) break
        }
    }
}
