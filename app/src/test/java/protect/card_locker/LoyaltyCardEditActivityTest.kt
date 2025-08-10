package protect.card_locker

import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
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
import protect.card_locker.async.TaskHandler
import java.lang.reflect.Method
import kotlin.text.get

@RunWith(RobolectricTestRunner::class)
class LoyaltyCardEditActivityTest {
    private lateinit var mockTextView: TextView
    private lateinit var mockImageView: ImageView
    private lateinit var activityController: org.robolectric.android.controller.ActivityController<LoyaltyCardEditActivity>
    private lateinit var activity: LoyaltyCardEditActivity
    private lateinit var shadowActivity: ShadowActivity

    private lateinit var context: android.content.Context

    @Before
    fun setUp() {
        ShadowLog.stream = System.out
        activityController = Robolectric.buildActivity(LoyaltyCardEditActivity::class.java)
        activity = activityController.get()
        context = activity.applicationContext
        mockTextView = TextView(activity)
        mockImageView = ImageView(activity)
        shadowActivity = shadowOf(activity)
    }

    @Test
    fun testActivityCreation() {
        activityController.create().start().resume()

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
        val activityController = Robolectric.buildActivity(LoyaltyCardEditActivity::class.java).create()
        activityController.start().resume()

        val activity = activityController.get()
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
        val activityController = Robolectric.buildActivity(LoyaltyCardEditActivity::class.java).create()
        activityController.start().resume()

        val activity = activityController.get()



        val fakeTask = FakeBarcodeImageWriterTask(
            context, mockImageView, "12345", CatimaBarcode.fromBarcode(BarcodeFormat.CODE_128),
            mockTextView, false, null, false, false
        )

        activity.viewModel.executeTask(TaskHandler.TYPE.BARCODE, fakeTask)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(fakeTask.wasExecuted)

        // Test that barcode generation can be cancelled
        activity.viewModel.cancelBarcodeGeneration()

        // Should not throw exception
        assertTrue(true)
    }



    @Test
    fun testActivityDestruction() {
        activityController.create().start().resume()

        // Verify a view exists before destruction
        assertNotNull(activity.findViewById(R.id.frontImageHolder))

        activityController.pause().stop().destroy()

        // Verify activity was destroyed
        assertTrue(activity.isDestroyed)
    }



}
