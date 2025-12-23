package protect.card_locker.templates

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowLog
import org.junit.Assert.*
import protect.card_locker.R

/**
 * TEMPLATE: Activity Integration Test
 *
 * Purpose: Test Activity-ViewModel integration, UI binding, lifecycle
 * When to use: Testing full activity behavior with real UI components
 * When NOT to use: Pure business logic (use ViewModel unit test)
 *
 * Key differences from ViewModel tests:
 * - Tests the full Activity + ViewModel integration
 * - Verifies UI updates from ViewModel state
 * - Tests lifecycle events (onCreate, onResume, etc.)
 * - Can test view bindings and click handlers
 *
 * Note: Keep these tests focused on integration.
 * Heavy business logic should be in ViewModel unit tests.
 *
 * HOW TO USE THIS TEMPLATE:
 * 1. Copy this file
 * 2. Replace YourActivity, YourViewModel, YourState with your types
 * 3. Adapt the test patterns to your specific Activity
 */
@RunWith(RobolectricTestRunner::class)
class ActivityIntegrationTestTemplate {

    private lateinit var context: Context

    @Before
    fun setUp() {
        ShadowLog.stream = System.out
        context = ApplicationProvider.getApplicationContext()
    }

    /**
     * Helper: Create Activity with Intent extras
     */
    private fun createActivity(cardId: Int = 0): ActivityController<ActivityIntegrationTemplate_YourActivity> {
        val intent = Intent(context, ActivityIntegrationTemplate_YourActivity::class.java).apply {
            putExtras(Bundle().apply {
                putInt("CARD_ID", cardId)
            })
        }

        return Robolectric.buildActivity(ActivityIntegrationTemplate_YourActivity::class.java, intent)
    }

    /**
     * Pattern: Test lifecycle events (rotation)
     */
    @Test
    fun `activity should handle rotation`() {
        val controller = createActivity(cardId = 1)

        // Initial creation
        val activity1 = controller.create().start().resume().get()
        activity1.viewModel.updateData("Before rotation")

        // Simulate rotation
        val bundle = Bundle()
        controller.saveInstanceState(bundle)
            .pause()
            .stop()
            .destroy()

        // Recreate
        val controller2 = createActivity(cardId = 1)
        controller2.create(bundle).start().resume()
        val activity2 = controller2.get()

        // ViewModel should restore or reload state
        assertNotNull(activity2.viewModel.state.value)
    }
}

// Placeholder classes (uniquely named to avoid conflicts with other templates)
private class ActivityIntegrationTemplate_YourActivity : AppCompatActivity() {
    val viewModel: ActivityIntegrationTemplate_YourViewModel by lazy { ActivityIntegrationTemplate_YourViewModel() }
}

private class ActivityIntegrationTemplate_YourViewModel {
    val state = MutableStateFlow<String>("initial")
    fun updateData(value: String) { state.value = value }
}
