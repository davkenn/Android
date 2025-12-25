package protect.card_locker.viewmodels

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog
import protect.card_locker.CardRepository
import protect.card_locker.LoadedCardData
import protect.card_locker.LoyaltyCard

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LoyaltyCardEditActivityViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var application: Application
    private lateinit var cardRepository: CardRepository
    private lateinit var viewModel: LoyaltyCardEditActivityViewModel

    @Before
    fun setUp() {
        ShadowLog.stream = System.out

        application = ApplicationProvider.getApplicationContext()
        cardRepository = mock()

        // Inject the test dispatcher - no Dispatchers.setMain() needed
        viewModel = LoyaltyCardEditActivityViewModel(
            application,
            cardRepository,
            testDispatcher
        )
    }

    @Test
    fun testViewModelCreation() {
        assertNotNull(viewModel)
        assertEquals(CardLoadState.Loading, viewModel.cardState.value)
        assertEquals(SaveState.Idle, viewModel.saveState.value)
    }

    @Test
    fun testInitialState() {
        assertEquals(false, viewModel.initialized)
        assertEquals(false, viewModel.hasChanged)
        assertEquals(false, viewModel.updateLoyaltyCard)
    }

    @Test
    fun testLoadNewCard() = runTest(testDispatcher) {
        whenever(cardRepository.loadCardData(0, null, false)).thenReturn(
            Result.success(
                LoadedCardData(
                    loyaltyCard = LoyaltyCard(),
                    allGroups = emptyList(),
                    loyaltyCardGroups = emptyList()
                )
            )
        )

        viewModel.loadCard(cardId = 0)
        advanceUntilIdle()

        val state = viewModel.cardState.value
        assertTrue(state is CardLoadState.Success)

        val successState = state as CardLoadState.Success
        assertNotNull(successState.loyaltyCard)
        // Default LoyaltyCard() sets id to -1
        assertEquals(-1, successState.loyaltyCard.id)
    }

    @Test
    fun testLoyaltyCardProperty_returnsNewCardWhenNotLoaded() {
        // Before loading, loyaltyCard returns a new LoyaltyCard with default id of -1
        val card = viewModel.loyaltyCard
        assertNotNull(card)
        assertEquals(-1, card.id)
    }

    @Test
    fun testSaveStateInitiallyIdle() {
        assertEquals(SaveState.Idle, viewModel.saveState.value)
    }

    @Test
    fun testCardStateInitiallyLoading() {
        // ViewModel starts in Loading state before loadCard() is called
        // This test verifies the initial state before any data is loaded
        assertEquals(CardLoadState.Loading, viewModel.cardState.value)
    }

    @Test
    fun testStoreNameUpdatesCard() = runTest(testDispatcher) {
        whenever(cardRepository.loadCardData(0, null, false)).thenReturn(
            Result.success(
                LoadedCardData(
                    loyaltyCard = LoyaltyCard(),
                    allGroups = emptyList(),
                    loyaltyCardGroups = emptyList()
                )
            )
        )

        viewModel.loadCard(cardId = 0)
        advanceUntilIdle()

        viewModel.onStoreNameChanged("Costco")

        assertEquals("Costco", viewModel.loyaltyCard.store)
        assertEquals(true, viewModel.hasChanged)
    }

    @Test
    fun testValidateStoreNameChanged_EmptyString_UpdatesCard() = runTest(testDispatcher) {
        whenever(cardRepository.loadCardData(0, null, false)).thenReturn(
            Result.success(
                LoadedCardData(
                    loyaltyCard = LoyaltyCard(),
                    allGroups = emptyList(),
                    loyaltyCardGroups = emptyList()
                )
            )
        )

        viewModel.loadCard(cardId = 0)
        advanceUntilIdle()

        viewModel.validateStoreNameChanged("")
        advanceUntilIdle()

        // Validate that the card was updated
        assertEquals("", viewModel.loyaltyCard.store)
        assertEquals(true, viewModel.hasChanged)
    }

    @Test
    fun testValidateStoreNameChanged_NonEmptyString_UpdatesCard() = runTest(testDispatcher) {
        whenever(cardRepository.loadCardData(0, null, false)).thenReturn(
            Result.success(
                LoadedCardData(
                    loyaltyCard = LoyaltyCard(),
                    allGroups = emptyList(),
                    loyaltyCardGroups = emptyList()
                )
            )
        )

        viewModel.loadCard(cardId = 0)
        advanceUntilIdle()

        viewModel.validateStoreNameChanged("Costco")
        advanceUntilIdle()

        // Validate that the card was updated
        assertEquals("Costco", viewModel.loyaltyCard.store)
        assertEquals(true, viewModel.hasChanged)
    }

    @Test
    fun testValidateCardIdChanged_EmptyString_UpdatesCard() = runTest(testDispatcher) {
        whenever(cardRepository.loadCardData(0, null, false)).thenReturn(
            Result.success(
                LoadedCardData(
                    loyaltyCard = LoyaltyCard(),
                    allGroups = emptyList(),
                    loyaltyCardGroups = emptyList()
                )
            )
        )

        viewModel.loadCard(cardId = 0)
        advanceUntilIdle()

        viewModel.validateCardIdChanged("")
        advanceUntilIdle()

        // Validate that the card was updated
        assertEquals("", viewModel.loyaltyCard.cardId)
        assertEquals(true, viewModel.hasChanged)
    }

    @Test
    fun testValidateCardIdChanged_NonEmptyString_UpdatesCard() = runTest(testDispatcher) {
        whenever(cardRepository.loadCardData(0, null, false)).thenReturn(
            Result.success(
                LoadedCardData(
                    loyaltyCard = LoyaltyCard(),
                    allGroups = emptyList(),
                    loyaltyCardGroups = emptyList()
                )
            )
        )

        viewModel.loadCard(cardId = 0)
        advanceUntilIdle()

        viewModel.validateCardIdChanged("12345")
        advanceUntilIdle()

        // Validate that the card was updated
        assertEquals("12345", viewModel.loyaltyCard.cardId)
        assertEquals(true, viewModel.hasChanged)
    }
}
