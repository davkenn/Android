package protect.card_locker

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import org.mockito.kotlin.mock

/**
 * Fake implementation of CardRepository for testing.
 *
 * Replays recorded load and save results in sequence, allowing tests to verify
 * ViewModel behavior with realistic data flows captured from actual app usage.
 *
 * Example:
 * ```
 * val fakeRepo = FakeCardRepository(
 *     loadResults = listOf(
 *         Result.success(LoadedCardData(...)),  // First load
 *         Result.success(LoadedCardData(...))   // Second load after save
 *     )
 * )
 * ```
 */
class FakeCardRepository(
    private val loadResults: List<Result<LoadedCardData>> = emptyList(),
    private val saveResults: List<Result<Int>> = listOf(Result.success(1))
) : CardRepository(
    database = mock<SQLiteDatabase>(),  // Mock - not used by overridden methods
    appContext = mock<Context>()        // Mock - not used by overridden methods
) {
    private var loadCallCount = 0
    private var saveCallCount = 0

    /** Track calls for verification in tests */
    val loadCalls = mutableListOf<LoadCall>()
    val saveCalls = mutableListOf<SaveCall>()

    data class LoadCall(
        val cardId: Int,
        val importUri: Uri?,
        val isDuplicate: Boolean
    )

    data class SaveCall(
        val loyaltyCard: LoyaltyCard,
        val selectedGroups: List<Group>
    )

    override suspend fun loadCardData(
        cardId: Int,
        importUri: Uri?,
        isDuplicate: Boolean
    ): Result<LoadedCardData> {
        loadCalls.add(LoadCall(cardId, importUri, isDuplicate))

        return if (loadCallCount < loadResults.size) {
            loadResults[loadCallCount++]
        } else {
            error("FakeCardRepository: No more load results configured. Called ${loadCallCount + 1} times but only ${loadResults.size} results provided.")
        }
    }

    override suspend fun saveCard(
        loyaltyCard: LoyaltyCard,
        selectedGroups: List<Group>
    ): Result<Int> {
        saveCalls.add(SaveCall(loyaltyCard, selectedGroups))

        return if (saveCallCount < saveResults.size) {
            saveResults[saveCallCount++]
        } else {
            error("FakeCardRepository: No more save results configured. Called ${saveCallCount + 1} times but only ${saveResults.size} results provided.")
        }
    }

    /** Reset call counts and history - useful for multi-stage tests */
    fun reset() {
        loadCallCount = 0
        saveCallCount = 0
        loadCalls.clear()
        saveCalls.clear()
    }
}
