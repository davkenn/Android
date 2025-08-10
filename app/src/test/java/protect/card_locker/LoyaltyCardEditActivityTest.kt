package protect.card_locker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.errorprone.annotations.DoNotMock
import com.google.zxing.BarcodeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowActivity
import org.robolectric.shadows.ShadowLog
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import protect.card_locker.async.TaskHandler
import java.lang.reflect.Method
import kotlin.text.get

@RunWith(RobolectricTestRunner::class)
class LoyaltyCardEditActivityTest {
    @Mock
    private lateinit var mockTextView: TextView
    @Mock
    private lateinit var mockImageView: ImageView


    private lateinit var shadowActivity: ShadowActivity

    private lateinit var context: android.content.Context

    @Before
    fun setUp() {
        ShadowLog.stream = System.out
        MockitoAnnotations.openMocks(this)
        context = ApplicationProvider.getApplicationContext()
    }


    private fun buildActivity(): LoyaltyCardEditActivity {
        return Robolectric.buildActivity(LoyaltyCardEditActivity::class.java)
            .create()
            .start()
            .resume()
            .visible()
            .get()
    }


    // Helper for building activity with intent extras
    private fun buildActivity(intentSetup: Intent.() -> Unit): LoyaltyCardEditActivity {
        val intent = Intent().apply(intentSetup)
        return Robolectric.buildActivity(LoyaltyCardEditActivity::class.java, intent)
            .create()
            .start()
            .resume()
            .visible()
            .get()
    }

    // Helper for test cases that need just the controller (like your openSetIconMenu test)
    private fun buildActivityController(intentSetup: Intent.() -> Unit = {}): org.robolectric.android.controller.ActivityController<LoyaltyCardEditActivity> {
        val intent = Intent().apply(intentSetup)
        return Robolectric.buildActivity(LoyaltyCardEditActivity::class.java, intent)
    }

    @Test
    fun testActivityCreation() {
        val activity = buildActivity()

        // Verify activity title is set correctly
        assertEquals(activity.title.toString(),
            activity.getString(R.string.addCardTitle, activity.getString(R.string.app_name)))

        // Check key elements are initialized
        assertNotNull(activity.findViewById(R.id.toolbar))
        assertNotNull(activity.findViewById(R.id.cardIdView))
        assertNotNull(activity.findViewById(R.id.barcode))
    }

    @Test
    fun testToolbarExists() {
        val activity = buildActivity()



        val toolbar = activity.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)

        assertNotNull(toolbar)
    }

    @Test
    fun testFabSaveButtonExists() {
        val activityController = Robolectric.buildActivity(LoyaltyCardEditActivity::class.java).create()
        activityController.start().resume()

        val activity = activityController.get()
        val fabSave = activity.findViewById<FloatingActionButton>(R.id.fabSave)

        assertNotNull(fabSave)
        assertEquals(View.VISIBLE, fabSave.visibility)
    }

    @Test
    fun testRequiredFieldsPresent() {
        val activityController = Robolectric.buildActivity(LoyaltyCardEditActivity::class.java).create()
        activityController.start().resume()

        val activity = activityController.get()

        assertNotNull(activity.findViewById<EditText>(R.id.storeNameEdit))
        assertNotNull(activity.findViewById<EditText>(R.id.noteEdit))
        assertNotNull(activity.findViewById<TextView>(R.id.cardIdView))
        assertNotNull(activity.findViewById<EditText>(R.id.barcodeIdField))
        assertNotNull(activity.findViewById<EditText>(R.id.barcodeTypeField))
    }

    @Test
    fun testTabLayoutExists() {
        val activityController = Robolectric.buildActivity(LoyaltyCardEditActivity::class.java).create()
        activityController.start().resume()

        val activity = activityController.get()
        val tabLayout = activity.findViewById<TabLayout>(R.id.tabs)

        assertNotNull(tabLayout)
        assertTrue(tabLayout.tabCount > 0)
    }

    @Test
    fun testBarcodeGenerationCancellation() {
        val activityController = Robolectric.buildActivity(LoyaltyCardEditActivity::class.java).create()
        activityController.start().resume()

        val activity = activityController.get()

        // Test that barcode generation can be cancelled
        activity.viewModel.cancelBarcodeGeneration()

        // Should not throw exception
        assertTrue(true)
    }

    @Test
    fun testBarcodeGeneration() {


        val activity = buildActivity ()

        val fakeTask = FakeBarcodeImageWriterTask(
            context, mockImageView, "12345", CatimaBarcode.fromBarcode(BarcodeFormat.CODE_128),
            mockTextView, false, null, false, false
        )
        assertFalse(fakeTask.wasExecuted)
        activity.viewModel.executeTask(TaskHandler.TYPE.BARCODE, fakeTask)
        shadowOf(Looper.getMainLooper()).idle()
        shadowOf(Looper.getMainLooper()).runToEndOfTasks()
        assertTrue(fakeTask.wasExecuted)

        // Test that barcode generation can be cancelled
 //       activity.viewModel.cancelBarcodeGeneration()

        // Should not throw exception
   //     assertTrue(true)
    }



        @Test
        fun startWithUpdateMode_setsViewModelFlags() {
            val intent = Intent().apply {
                putExtras(Bundle().apply {
                    putInt(LoyaltyCardEditActivity.BUNDLE_ID, 42)
                    putBoolean(LoyaltyCardEditActivity.BUNDLE_UPDATE, true)
                })
            }
            val activity = Robolectric.buildActivity(LoyaltyCardEditActivity::class.java, intent)
                .create()
                .start()
                .resume()
                .visible()
                .get()


            shadowOf(Looper.getMainLooper()).idle()

            assertTrue(activity.viewModel.updateLoyaltyCard)
            assertEquals(42, activity.viewModel.loyaltyCardId)
        }

        @Test
        fun startWithDuplicateMode_setsDuplicateFlag() {
            val intent = Intent().apply {
                putExtras(Bundle().apply {
                    putInt(LoyaltyCardEditActivity.BUNDLE_ID, 99)
                    putBoolean(LoyaltyCardEditActivity.BUNDLE_DUPLICATE_ID, true)
                })
            }
            val activity = Robolectric
                .buildActivity(LoyaltyCardEditActivity::class.java, intent)
                .create()
                .start()
                .resume()
                .visible()
                .get()

            assertTrue(activity.viewModel.duplicateFromLoyaltyCardId)
            assertEquals(99, activity.viewModel.loyaltyCardId)
        }

        @Test
        fun startWithOpenSetIconMenu_showsIconMenuFlag() {
            val intent = Intent().apply {
                putExtras(Bundle().apply {
                    putBoolean(LoyaltyCardEditActivity.BUNDLE_OPEN_SET_ICON_MENU, true)
                })
            }

            val activity1 = Robolectric
                .buildActivity(LoyaltyCardEditActivity::class.java, intent)
                .create()

               // .visible()

            assertTrue(activity1.get().viewModel.openSetIconMenu)
            activity1.start().resume().visible()
        //    context = activity1.applicationContext
      //      mockTextView = TextView(activity)
        //    mockImageView = ImageView(activity)

//            shadowOf(Looper.getMainLooper()).idle()
  //          assertTrue(activity1.viewModel.openSetIconMenu)
        }

        @Test
        fun startWithAddGroup_setsAddGroup() {
            val groupName = "NewGroup"
            val intent = Intent().apply {
                putExtras(Bundle().apply {
                    putString(LoyaltyCardEditActivity.BUNDLE_ADDGROUP, groupName)
                })
            }
            val activity = Robolectric
                .buildActivity(LoyaltyCardEditActivity::class.java, intent)
                .create()
                .start()
                .resume()
                .visible()
                .get()

            assertEquals(groupName, activity.viewModel.addGroup)
        }



    @Test
    fun testActivityDestruction() {

        val activityController = buildActivityController()
        val activity = activityController.create().start().resume().get()
        // Verify a view exists before destruction
        assertNotNull(activity.findViewById(R.id.frontImageHolder))

        activityController.pause().stop().destroy()

        // Verify activity was destroyed
        assertTrue(activity.isDestroyed)
    }



}
