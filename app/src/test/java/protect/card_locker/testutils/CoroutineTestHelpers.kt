package protect.card_locker.testutils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Before

/**
 * Test dispatcher utilities for coroutine testing.
 *
 * When to use:
 * - StandardTestDispatcher: Default choice. Requires advanceUntilIdle() for deterministic tests.
 * - UnconfinedTestDispatcher: For simple tests where order doesn't matter.
 *
 * Example:
 * ```kotlin
 * class MyViewModelTest {
 *     private val testDispatcher = TestDispatchers.standard()
 *
 *     @Test
 *     fun test() = runTest(testDispatcher) {
 *         viewModel.doSomething()
 *         advanceUntilIdle()
 *         assertEquals(expected, viewModel.state.value)
 *     }
 * }
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class)
object TestDispatchers {
    /**
     * Standard dispatcher for most tests.
     * Requires explicit advanceUntilIdle() to process coroutines.
     * Best for testing state sequences and timing.
     */
    fun standard(): TestDispatcher = StandardTestDispatcher()

    /**
     * Unconfined dispatcher for simple tests.
     * Executes eagerly without needing advanceUntilIdle().
     * Use when you don't care about execution order.
     */
    fun unconfined(): TestDispatcher = UnconfinedTestDispatcher()
}

/**
 * Base class for coroutine tests.
 *
 * Provides standard setup for tests that use coroutines,
 * including a test dispatcher that can be injected into ViewModels.
 *
 * Usage:
 * ```kotlin
 * class MyViewModelTest : CoroutineTestBase() {
 *     private lateinit var viewModel: MyViewModel
 *
 *     @Before
 *     override fun setUp() {
 *         super.setUp()  // Sets up test dispatcher
 *         viewModel = MyViewModel(testDispatcher)
 *     }
 *
 *     @Test
 *     fun `test async operation`() = runTest(testDispatcher) {
 *         viewModel.doSomething()
 *         advanceUntilIdle()  // Process all coroutines
 *         assertEquals(expected, viewModel.state.value)
 *     }
 * }
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class CoroutineTestBase {
    protected lateinit var testDispatcher: TestDispatcher

    @Before
    open fun setUp() {
        testDispatcher = StandardTestDispatcher()
    }
}
