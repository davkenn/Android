//package protect.card_locker.viewmodels
//
//import android.app.Application
//import androidx.arch.core.executor.testing.InstantTaskExecutorRule
//import androidx.test.core.app.ApplicationProvider
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.ExperimentalCoroutinesApi
//import kotlinx.coroutines.test.StandardTestDispatcher
//import kotlinx.coroutines.test.advanceUntilIdle
//import kotlinx.coroutines.test.resetMain
//import kotlinx.coroutines.test.runTest
//import kotlinx.coroutines.test.setMain
//import org.junit.After
//import org.junit.Assert.assertEquals
//import org.junit.Assert.assertNotNull
//import org.junit.Assert.assertTrue
//import org.junit.Before
//import org.junit.Rule
//import org.junit.Test
//import org.junit.runner.RunWith
//import org.robolectric.RobolectricTestRunner
//import org.robolectric.shadows.ShadowLog
//import protect.card_locker.CardRepository
//import protect.card_locker.LoyaltyCard
//
//@OptIn(ExperimentalCoroutinesApi::class)
//@RunWith(RobolectricTestRunner::class)
//class LoyaltyCardEditActivityViewModelTest {
//
//    @get:Rule
//    val instantExecutorRule = InstantTaskExecutorRule()
//
//    private val testDispatcher = StandardTestDispatcher()
//
//    private lateinit var application: Application
//    private lateinit var cardRepository: CardRepository
//    private lateinit var viewModel: LoyaltyCardEditActivityViewModel
//
//    @Before
//    fun setUp() {
//        ShadowLog.stream = System.out
//        Dispatchers.setMain(testDispatcher)
//
//        application = ApplicationProvider.getApplicationContext()
//        cardRepository = CardRepository(application)
//        viewModel = LoyaltyCardEditActivityViewModel(application, cardRepository)
//    }
//
//    @After
//    fun tearDown() {
//        Dispatchers.resetMain()
//    }
//
//    @Test
//    fun testViewModelCreation() {
//        assertNotNull(viewModel)
//        assertEquals(CardLoadState.Loading, viewModel.cardState.value)
//        assertEquals(SaveState.Idle, viewModel.saveState.value)
//    }
//
//    @Test
//    fun testInitialState() {
//        // Verify initial values
//        assertEquals(false, viewModel.initialized)
//        assertEquals(false, viewModel.hasChanged)
//        assertEquals(false, viewModel.updateLoyaltyCard)
//        assertEquals(0, viewModel.loyaltyCardId)
//    }
//
//    @Test
//    fun testLoadNewCard() = runTest {
//        // Load a new card (cardId = 0)
//        viewModel.loadCard(cardId = 0)
//
//        // Advance coroutines
//        advanceUntilIdle()
//
//        // Verify state is Success
//        val state = viewModel.cardState.value
//        assertTrue(state is CardLoadState.Success)
//
//        // Verify card data
//        val successState = state as CardLoadState.Success
//        assertNotNull(successState.loyaltyCard)
//        assertEquals(0, successState.loyaltyCard.id)
//    }
//
//    @Test
//    fun testLoyaltyCardProperty_returnsNewCardWhenNotLoaded() {
//        // Before loading, loyaltyCard should return empty card
//        val card = viewModel.loyaltyCard
//        assertNotNull(card)
//        assertEquals(0, card.id)
//    }
//
//    @Test
//    fun testAllGroupsProperty_returnsEmptyListWhenNotLoaded() {
//        // Before loading, allGroups should return empty list
//        val groups = viewModel.allGroups
//        assertNotNull(groups)
//        assertTrue(groups.isEmpty())
//    }
//
//    @Test
//    fun testLoyaltyCardGroupsProperty_returnsEmptyListWhenNotLoaded() {
//        // Before loading, loyaltyCardGroups should return empty list
//        val groups = viewModel.loyaltyCardGroups
//        assertNotNull(groups)
//        assertTrue(groups.isEmpty())
//    }
//
//    @Test
//    fun testSaveStateInitiallyIdle() {
//        assertEquals(SaveState.Idle, viewModel.saveState.value)
//    }
//
//    @Test
//    fun testOnSaveComplete_resetsStateToIdle() {
//        // Manually set state to Error
//        viewModel.onSaveComplete()
//
//        // Verify it's Idle
//        assertEquals(SaveState.Idle, viewModel.saveState.value)
//    }
//
//    @Test
//    fun testBarcodeGenerationCancellation() {
//        // Should not throw exception
//        viewModel.cancelBarcodeGeneration()
//        assertTrue(true)
//    }
//
//    @Test
//    fun testStoreNameValidation_emptyName() = runTest {
//        // Load a card first so we have CardLoadState.Success
//        viewModel.loadCard(cardId = 0)
//        advanceUntilIdle()
//
//        // Change store name to empty string
//        viewModel.onStoreNameChanged("")
//
//        // Should set error
//        assertNotNull(viewModel.storeNameError.value)
//        assertTrue(viewModel.storeNameError.value!!.contains("must not be empty"))
//    }
//
//    @Test
//    fun testStoreNameValidation_validName() = runTest {
//        // Load a card first
//        viewModel.loadCard(cardId = 0)
//        advanceUntilIdle()
//
//        // Change store name to valid string
//        viewModel.onStoreNameChanged("Target")
//
//        // Should clear error
//        assertEquals(null, viewModel.storeNameError.value)
//    }
//
//    @Test
//    fun testStoreNameUpdatesCard() = runTest {
//        // Load a card first
//        viewModel.loadCard(cardId = 0)
//        advanceUntilIdle()
//
//        // Change store name
//        viewModel.onStoreNameChanged("Costco")
//
//        // Should update the loyalty card
//        assertEquals("Costco", viewModel.loyaltyCard.store)
//        assertEquals(true, viewModel.hasChanged)
//    }
//}
