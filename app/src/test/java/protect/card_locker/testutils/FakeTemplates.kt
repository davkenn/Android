package protect.card_locker.testutils

/**
 * Template Fakes for common testing scenarios.
 *
 * Copy and customize these for your specific tests.
 * Prefer Fakes over Mockito mocks for better test readability and maintainability.
 *
 * Why Fakes over Mocks?
 * ✅ More readable: `FakeRepo(loadResults = listOf(...))` vs `whenever(repo.load()).thenReturn(...)`
 * ✅ Reusable across tests
 * ✅ Self-documenting test data
 * ✅ Easier to debug
 * ✅ No Mockito magic - just plain Kotlin
 *
 * See actual examples:
 * - FakeCardRepository.kt - Repository with sequential replay
 * - FakeBarcodeImageWriterTask.kt - Async operation fake
 */

/**
 * TEMPLATE: Fake Repository with Sequential Replay
 *
 * Pattern: Pre-configure results that replay in sequence.
 * Best for: ViewModel tests with predictable data flows.
 *
 * HOW TO USE THIS TEMPLATE:
 * 1. Copy this class
 * 2. Rename to FakeYourRepository
 * 3. Replace LoadResult, SaveResult with your actual types
 * 4. Replace LoadCall, SaveCall data classes with your actual parameters
 * 5. Implement your repository's methods using the pattern shown
 *
 * Example usage after customization:
 * ```kotlin
 * val fakeRepo = FakeCardRepository(
 *     loadResults = listOf(
 *         Result.success(LoadedCardData(...)),
 *         Result.success(LoadedCardData(...))
 *     ),
 *     saveResults = listOf(Result.success(1))
 * )
 *
 * val viewModel = MyViewModel(app, fakeRepo, testDispatcher)
 *
 * // Later, verify calls:
 * assertEquals(2, fakeRepo.loadCalls.size)
 * assertEquals("expected value", fakeRepo.saveCalls[0].card.store)
 * ```
 */
class FakeRepositoryTemplate<LoadResult, SaveResult>(
    private val loadResults: List<Result<LoadResult>> = emptyList(),
    private val saveResults: List<Result<SaveResult>> = emptyList()
) {
    private var loadCallCount = 0
    private var saveCallCount = 0

    // Track all calls for verification
    data class LoadCall(
        val id: Int // Replace with your actual load parameters
    )

    data class SaveCall(
        val data: Any  // Replace with your actual save parameters
    )

    val loadCalls = mutableListOf<LoadCall>()
    val saveCalls = mutableListOf<SaveCall>()

    /**
     * Example load method - customize for your repository
     *
     * Pattern: Sequential replay of configured results
     */
    suspend fun load(id: Int): Result<LoadResult> {
        loadCalls.add(LoadCall(id))

        return if (loadCallCount < loadResults.size) {
            loadResults[loadCallCount++]
        } else {
            error("Fake: No more load results. Called ${loadCallCount + 1} times but only ${loadResults.size} configured.")
        }
    }

    /**
     * Example save method - customize for your repository
     *
     * Pattern: Sequential replay of configured results
     */
    suspend fun save(data: Any): Result<SaveResult> {
        saveCalls.add(SaveCall(data))

        return if (saveCallCount < saveResults.size) {
            saveResults[saveCallCount++]
        } else {
            error("Fake: No more save results. Called ${saveCallCount + 1} times but only ${saveResults.size} configured.")
        }
    }

    /**
     * Reset for multi-stage tests.
     *
     * Use when you need to test multiple scenarios in one test
     * and want to reconfigure results between stages.
     */
    fun reset() {
        loadCallCount = 0
        saveCallCount = 0
        loadCalls.clear()
        saveCalls.clear()
    }
}

/**
 * REAL EXAMPLE: See FakeCardRepository.kt
 *
 * This shows how the template is actually used in this project.
 * It's a complete implementation following the same pattern.
 *
 * Key features shown in FakeCardRepository:
 * - Extends CardRepository with mocked dependencies
 * - Sequential replay of pre-configured LoadedCardData results
 * - Call tracking for both load and save operations
 * - Proper error handling when running out of results
 * - Reset capability for multi-stage tests
 *
 * Location: /app/src/test/java/protect/card_locker/FakeCardRepository.kt
 */
