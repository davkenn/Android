package protect.card_locker

import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.zxing.BarcodeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLog
import protect.card_locker.LoyaltyCardEditActivity.Companion.BUNDLE_OPEN_SET_ICON_MENU
import protect.card_locker.LoyaltyCardEditActivity.Companion.BUNDLE_UPDATE
import protect.card_locker.LoyaltyCardViewActivity.BUNDLE_ID
import protect.card_locker.async.TaskHandler

@RunWith(RobolectricTestRunner::class)
class LoyaltyCardEditActivityTest {
    @Mock
    private lateinit var mockTextView: TextView
    @Mock
    private lateinit var mockImageView: ImageView
    private lateinit var context: android.content.Context

    @Before
    fun setUp() {
        ShadowLog.stream = System.out
        MockitoAnnotations.openMocks(this)
        context = ApplicationProvider.getApplicationContext()
    }

    /**
     * Launch activity with default intent
     */
    private fun launchActivity(): ActivityScenario<LoyaltyCardEditActivity> {
        return ActivityScenario.launch(LoyaltyCardEditActivity::class.java)
    }

    /**
     * Launch activity with custom intent setup
     */
    private fun launchActivity(intentSetup: Intent.() -> Unit): ActivityScenario<LoyaltyCardEditActivity> {
        val intent = Intent(context, LoyaltyCardEditActivity::class.java).apply(intentSetup)
        return ActivityScenario.launch(intent)
    }

    @Test
    fun testActivityCreation() {
        launchActivity().use { scenario ->
            scenario.onActivity { activity ->
                // Verify activity title is set correctly
                assertEquals(
                    activity.title.toString(),
                    activity.getString(R.string.addCardTitle, activity.getString(R.string.app_name))
                )

                // Check key elements are initialized
                assertNotNull(activity.findViewById(R.id.toolbar))
                assertNotNull(activity.findViewById(R.id.cardIdView))
                assertNotNull(activity.findViewById(R.id.barcode))
            }
        }
    }

    @Test
    fun testToolbarExists() {
        launchActivity().use { scenario ->
            scenario.onActivity { activity ->
                val toolbar = activity.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
                assertNotNull(toolbar)
            }
        }
    }

    @Test
    fun testFabSaveButtonExists() {
        launchActivity().use { scenario ->
            scenario.onActivity { activity ->
                val fabSave = activity.findViewById<FloatingActionButton>(R.id.fabSave)
                assertNotNull(fabSave)
                assertEquals(View.VISIBLE, fabSave.visibility)
            }
        }
    }

    @Test
    fun testRequiredFieldsPresent() {
        launchActivity().use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull(activity.findViewById<EditText>(R.id.storeNameEdit))
                assertNotNull(activity.findViewById<EditText>(R.id.noteEdit))
                assertNotNull(activity.findViewById<TextView>(R.id.cardIdView))
                assertNotNull(activity.findViewById<EditText>(R.id.barcodeIdField))
                assertNotNull(activity.findViewById<EditText>(R.id.barcodeTypeField))
            }
        }
    }

    @Test
    fun testTabLayoutExists() {
        launchActivity().use { scenario ->
            scenario.onActivity { activity ->
                val tabLayout = activity.findViewById<TabLayout>(R.id.tabs)
                assertNotNull(tabLayout)
                assertTrue(tabLayout.tabCount > 0)
            }
        }
    }

    @Test
    fun testBarcodeGenerationCancellation() {
        launchActivity().use { scenario ->
            scenario.onActivity { activity ->
                // Test that barcode generation can be cancelled without throwing
                activity.viewModel.cancelBarcodeGeneration()
                assertTrue(true)
            }
        }
    }

    @Test
    fun testBarcodeGeneration() {
        launchActivity().use { scenario ->
            scenario.onActivity { activity ->
                val fakeTask = FakeBarcodeImageWriterTask(
                    context, mockImageView, "12345", CatimaBarcode.fromBarcode(BarcodeFormat.CODE_128),
                    mockTextView, false, null, false, false
                )
                assertFalse(fakeTask.wasExecuted)
                activity.viewModel.executeTask(TaskHandler.TYPE.BARCODE, fakeTask)
                shadowOf(Looper.getMainLooper()).idle()
                shadowOf(Looper.getMainLooper()).runToEndOfTasks()
                assertTrue(fakeTask.wasExecuted)
            }
        }
    }

    @Test
    fun startWithUpdateMode_setsViewModelFlags() {
        launchActivity {
            putExtras(Bundle().apply {
                putInt(LoyaltyCardEditActivity.BUNDLE_ID, 42)
                putBoolean(BUNDLE_UPDATE, true)
            })
        }.use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                assertTrue(activity.viewModel.updateLoyaltyCard)
                assertEquals(42, activity.viewModel.loyaltyCardId)
            }
        }
    }

    @Test
    fun startWithDuplicateMode_setsDuplicateFlag() {
        launchActivity {
            putExtras(Bundle().apply {
                putInt(LoyaltyCardEditActivity.BUNDLE_ID, 99)
                putBoolean(LoyaltyCardEditActivity.BUNDLE_DUPLICATE_ID, true)
            })
        }.use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(activity.viewModel.duplicateFromLoyaltyCardId)
                assertEquals(99, activity.viewModel.loyaltyCardId)
            }
        }
    }

    @Test
    fun startWithOpenSetIconMenu_showsIconMenuFlag() {
        // Note: We include BUNDLE_ID with a non-existent card to prevent the flag from being
        // consumed by bindCardToUi() which runs on successful card load. This tests that the
        // flag is correctly parsed from the intent extras.
        launchActivity {
            putExtras(Bundle().apply {
                putBoolean(BUNDLE_OPEN_SET_ICON_MENU, true)
                putInt(LoyaltyCardEditActivity.BUNDLE_ID, 999)
                putBoolean(BUNDLE_UPDATE, true)
            })
        }.use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                assertTrue(activity.viewModel.openSetIconMenu)
            }
        }
    }

    @Test
    fun startWithOpenSetIconMenu_showsIconMenuFlag2() {
        launchActivity {
            putExtras(Bundle().apply {
                putBoolean(BUNDLE_OPEN_SET_ICON_MENU, true)
                putInt(BUNDLE_ID, 98)
                putBoolean(BUNDLE_UPDATE, true)
            })
        }.use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(activity.viewModel.openSetIconMenu)
            }
        }
    }

    @Test
    fun startWithAddGroup_setsAddGroup() {
        val groupName = "NewGroup"
        launchActivity {
            putExtras(Bundle().apply {
                putString(LoyaltyCardEditActivity.BUNDLE_ADDGROUP, groupName)
            })
        }.use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(groupName, activity.viewModel.addGroup)
            }
        }
    }

    @Test
    fun testActivityDestruction() {
        launchActivity().use { scenario ->
            scenario.onActivity { activity ->
                // Verify a view exists before destruction
                assertNotNull(activity.findViewById(R.id.frontImageHolder))
            }
            // Move to destroyed state
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.DESTROYED)
        }
        // Scenario is now destroyed - test passes if no exceptions
    }
}
