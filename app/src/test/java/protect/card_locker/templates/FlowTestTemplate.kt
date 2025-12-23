package protect.card_locker.templates

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

/**
 * TEMPLATE: Flow Testing with Turbine
 *
 * Purpose: Test Kotlin Flows (StateFlow, SharedFlow, hot/cold flows)
 * When to use: Testing flow emissions over time, event sequences
 *
 * Turbine provides a clean DSL for flow testing:
 * - awaitItem() - get next emission
 * - expectNoEvents() - assert silence
 * - awaitComplete() - assert flow completed
 * - cancelAndIgnoreRemainingEvents() - cleanup
 *
 * See: https://github.com/cashapp/turbine
 *
 * HOW TO USE THIS TEMPLATE:
 * 1. Copy the pattern that matches your scenario
 * 2. Replace placeholder types with your actual types
 * 3. Run the test!
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FlowTestTemplate {

    /**
     * Pattern: StateFlow value assertions (simple case)
     */
    @Test
    fun `stateflow should emit current value`() = runTest {
        val stateFlow = MutableStateFlow("initial")

        // StateFlow always has a value - can assert directly
        assertEquals("initial", stateFlow.value)

        // Or test emissions over time
        stateFlow.test {
            assertEquals("initial", awaitItem())

            stateFlow.value = "updated"
            assertEquals("updated", awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Pattern: SharedFlow event testing
     */
    @Test
    fun `sharedflow should emit events to collectors`() = runTest {
        val eventFlow = MutableSharedFlow<String>()

        eventFlow.test {
            // No initial value - SharedFlow starts empty
            expectNoEvents()

            // Emit events
            eventFlow.emit("event1")
            assertEquals("event1", awaitItem())

            eventFlow.emit("event2")
            assertEquals("event2", awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Pattern: Testing flow transformations
     */
    @Test
    fun `map should transform emissions`() = runTest {
        val source = flowOf(1, 2, 3)
        val transformed = source.map { it * 2 }

        transformed.test {
            assertEquals(2, awaitItem())
            assertEquals(4, awaitItem())
            assertEquals(6, awaitItem())
            awaitComplete()
        }
    }

    /**
     * Pattern: ViewModel flow testing (realistic example)
     *
     * Test that a ViewModel's event flow emits correctly.
     */
    @Test
    fun `viewmodel uiEvents should emit on user action`() = runTest {
        val viewModel = FakeViewModel()

        viewModel.uiEvents.test {
            // Trigger action that emits event
            viewModel.performAction()

            val event = awaitItem()
            assertTrue(event is UiEvent.ShowToast)
            assertEquals("Action complete", (event as UiEvent.ShowToast).message)

            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Pattern: Testing debounce/throttle
     */
    @Test
    fun `debounce should delay emissions`() = runTest {
        val source = MutableSharedFlow<String>()
        val debounced = source.debounce(100)

        debounced.test {
            source.emit("fast1")
            source.emit("fast2")
            source.emit("fast3")

            // Only last value emits after debounce timeout
            assertEquals("fast3", awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }
}

// Placeholder classes
private class FakeViewModel {
    private val _uiEvents = MutableSharedFlow<UiEvent>()
    val uiEvents = _uiEvents.asSharedFlow()

    suspend fun performAction() {
        _uiEvents.emit(UiEvent.ShowToast("Action complete"))
    }
}

private sealed interface UiEvent {
    data class ShowToast(val message: String) : UiEvent
}
