package protect.card_locker.templates

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog

/**
 * TEMPLATE: ViewModel Unit Test
 *
 * Purpose: Test pure business logic in ViewModels
 * When to use: Testing state transformations, coroutine flows, business rules
 * When NOT to use: UI interactions, lifecycle events (use Activity integration test)
 *
 * HOW TO USE THIS TEMPLATE:
 * 1. Copy this file to your test directory
 * 2. Rename: ViewModelTestTemplate → MyViewModelTest
 * 3. Replace placeholders:
 *    - YourViewModel → your actual ViewModel class
 *    - YourRepository → your repository/dependency
 *    - FakeYourRepository → your fake implementation
 *    - YourState → your state sealed interface/class
 * 4. Keep the patterns, adapt the specifics
 *
 * Key patterns:
 * 1. Use StandardTestDispatcher for deterministic coroutine execution
 * 2. Call advanceUntilIdle() after async operations
 * 3. Use InstantTaskExecutorRule for LiveData/StateFlow synchronization
 * 4. Inject test dispatcher into ViewModel constructor
 * 5. Verify state with StateFlow.value assertions
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ViewModelTestTemplate {

    /**
     * Required: Synchronizes LiveData/StateFlow for immediate value access.
     * Without this, StateFlow.value will throw "No value present" error.
     */
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    /**
     * StandardTestDispatcher: Requires manual advanceUntilIdle()
     * Provides deterministic test execution - you control when coroutines run.
     *
     * Alternative: UnconfinedTestDispatcher executes eagerly (less control)
     */
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var application: Application
    private lateinit var fakeRepository: ViewModelTemplate_FakeYourRepository
    private lateinit var viewModel: ViewModelTemplate_YourViewModel

    @Before
    fun setUp() {
        // Route logs to stdout for test visibility
        ShadowLog.stream = System.out

        application = ApplicationProvider.getApplicationContext()

        // Configure fake with test data
        fakeRepository = ViewModelTemplate_FakeYourRepository(
            loadResults = listOf(
                Result.success(Any()) // Replace with: your test data
            ),
            saveResults = listOf(Result.success(1))
        )

        // CRITICAL: Inject test dispatcher for controlled async execution
        viewModel = ViewModelTemplate_YourViewModel(
            application,
            fakeRepository,
            testDispatcher  // This makes advanceUntilIdle() work!
        )
    }

    /**
     * Pattern: Verify initial state
     *
     * Good practice: Always test the initial state your ViewModel starts in.
     */
    @Test
    fun `initial state should be loading`() {
        // For StateFlows, .value gives you the current state immediately
        assertEquals(ViewModelTemplate_YourState.Loading, viewModel.state.value)
    }

    /**
     * Pattern: Async operation with state verification
     *
     * Key steps:
     * 1. Arrange: Setup (done in setUp())
     * 2. Act: Trigger the async operation
     * 3. advanceUntilIdle(): Process all pending coroutines
     * 4. Assert: Verify the final state
     */
    @Test
    fun `load data should update state to success`() = runTest(testDispatcher) {
        // Arrange: (setUp already configured fakeRepository)

        // Act: Trigger async operation
        viewModel.loadData(id = 1)

        // CRITICAL: Process all pending coroutines
        // Without this, the coroutine hasn't run yet!
        advanceUntilIdle()

        // Assert: Verify final state
        val state = viewModel.state.value
        assertTrue("Expected Success state", state is ViewModelTemplate_YourState.Success)

        val successState = state as ViewModelTemplate_YourState.Success
        assertEquals("expected value", successState.data.field)
    }

    /**
     * Pattern: User action updates state (synchronous)
     *
     * For operations that don't launch coroutines, you don't need advanceUntilIdle().
     * State updates are immediate.
     */
    @Test
    fun `updating field should mark as changed`() = runTest(testDispatcher) {
        // Arrange: Load initial data
        viewModel.loadData(id = 1)
        advanceUntilIdle()

        // Act: User action (synchronous state change)
        viewModel.onFieldChanged("new value")

        // Assert: No advanceUntilIdle() needed for sync operations
        assertEquals("new value", viewModel.currentData.field)
        assertTrue("ViewModel should be marked as changed", viewModel.hasChanged)
    }

    /**
     * Pattern: Error handling
     *
     * Configure fake to return errors, verify ViewModel handles them correctly.
     */
    @Test
    fun `load failure should show error state`() = runTest(testDispatcher) {
        // Arrange: Configure fake to return error
        fakeRepository = ViewModelTemplate_FakeYourRepository(
            loadResults = listOf(
                Result.failure(Exception("Network error"))
            ),
            saveResults = emptyList() // Not testing save in this test
        )
        viewModel = ViewModelTemplate_YourViewModel(application, fakeRepository, testDispatcher)

        // Act
        viewModel.loadData(id = 1)
        advanceUntilIdle()

        // Assert: ViewModel should handle error gracefully
        val state = viewModel.state.value
        assertTrue("Expected Error state", state is ViewModelTemplate_YourState.Error)

        val errorState = state as ViewModelTemplate_YourState.Error
        assertEquals("Network error", errorState.message)
    }

    /**
     * Pattern: Verify repository/dependency calls
     *
     * Fakes track all calls, making it easy to verify ViewModel
     * called dependencies with the correct parameters.
     */
    @Test
    fun `save should call repository with correct data`() = runTest(testDispatcher) {
        // Arrange
        viewModel.loadData(id = 1)
        advanceUntilIdle()

        viewModel.onFieldChanged("updated value")

        // Act
        viewModel.save()
        advanceUntilIdle()

        // Assert: Verify fake was called correctly
        assertEquals(1, fakeRepository.saveCalls.size)

        val saveCall = fakeRepository.saveCalls[0]
        assertEquals("updated value", saveCall.data.field)
        assertEquals(1, saveCall.data.id)
    }

    /**
     * Pattern: Multiple async operations in sequence
     *
     * Test realistic multi-step flows.
     */
    @Test
    fun `create then reload should maintain data`() = runTest(testDispatcher) {
        // Step 1: Create new item
        viewModel.loadData(id = 0)  // 0 = new item
        advanceUntilIdle()

        viewModel.onFieldChanged("new item")
        viewModel.save()
        advanceUntilIdle()

        // Verify save was called
        assertEquals(1, fakeRepository.saveCalls.size)

        // Step 2: Reload saved item (fake returns second configured result)
        viewModel.loadData(id = 1)  // Saved with id = 1
        advanceUntilIdle()

        // Verify reloaded state matches saved data
        val state = viewModel.state.value as ViewModelTemplate_YourState.Success
        assertEquals("new item", state.data.field)
    }

    /**
     * Pattern: StateFlow emission testing
     *
     * For testing that a flow emits expected values over time.
     * Uses Turbine library (see FlowTestTemplate for more patterns).
     */
    @Test
    fun `state should emit loading then success`() = runTest(testDispatcher) {
        // Using Turbine's test extension
        viewModel.state.test {
            // Initial emission
            assertEquals(ViewModelTemplate_YourState.Loading, awaitItem())

            // Trigger load
            viewModel.loadData(id = 1)
            advanceUntilIdle()

            // Should emit Success
            val successState = awaitItem()
            assertTrue(successState is ViewModelTemplate_YourState.Success)

            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Pattern: Test edge cases
     *
     * Don't just test the happy path!
     */
    @Test
    fun `save without loading should fail gracefully`() = runTest(testDispatcher) {
        // Act: Try to save without loading data first
        viewModel.save()
        advanceUntilIdle()

        // Assert: Should show error, not crash
        val state = viewModel.state.value
        assertTrue("Should show error for invalid operation", state is ViewModelTemplate_YourState.Error)
    }

    @Test
    fun `empty field should not be saved`() = runTest(testDispatcher) {
        viewModel.loadData(id = 1)
        advanceUntilIdle()

        // Act: Try to save with empty required field
        viewModel.onFieldChanged("")
        viewModel.save()
        advanceUntilIdle()

        // Assert: Save should not be called
        assertEquals(0, fakeRepository.saveCalls.size)

        // ViewModel should show validation error
        val state = viewModel.state.value
        assertTrue("Should show validation error", state is ViewModelTemplate_YourState.Error)
    }
}

/**
 * Placeholder classes - replace with your actual types
 * (Prefixed to avoid conflicts with other template files)
 */

private class ViewModelTemplate_YourViewModel(
    app: Application,
    repo: ViewModelTemplate_FakeYourRepository,
    dispatcher: CoroutineDispatcher
) {
    val state = MutableStateFlow<ViewModelTemplate_YourState>(ViewModelTemplate_YourState.Loading)
    var hasChanged: Boolean = false
    var currentData: ViewModelTemplate_DataClass = ViewModelTemplate_DataClass()

    suspend fun loadData(id: Int) {}
    fun onFieldChanged(value: String) {}
    suspend fun save() {}
}

private class ViewModelTemplate_FakeYourRepository(
    val loadResults: List<Result<Any>>,
    val saveResults: List<Result<Int>>
) {
    val saveCalls = mutableListOf<SaveCall>()
    data class SaveCall(val data: ViewModelTemplate_DataClass)
}

private sealed interface ViewModelTemplate_YourState {
    object Loading : ViewModelTemplate_YourState
    data class Success(val data: ViewModelTemplate_DataClass) : ViewModelTemplate_YourState
    data class Error(val message: String) : ViewModelTemplate_YourState
}

private data class ViewModelTemplate_DataClass(
    val id: Int = -1,
    val field: String = ""
)
