package protect.card_locker.testutils

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.robolectric.shadows.ShadowLog
import protect.card_locker.DBHelper
import protect.card_locker.ImageLocationType
import protect.card_locker.Utils
import java.io.FileNotFoundException

/**
 * Robolectric test utilities.
 *
 * Helpers for Robolectric-based tests (Activities, integration tests).
 * These utilities handle common setup tasks like logging configuration
 * and database management.
 */

/**
 * Base class for Robolectric tests.
 *
 * Provides standard setup for tests that use Robolectric,
 * including logging configuration and Context access.
 *
 * Usage:
 * ```kotlin
 * @RunWith(RobolectricTestRunner::class)
 * class MyActivityTest : RobolectricTestBase() {
 *     @Before
 *     override fun setUp() {
 *         super.setUp()  // Configures ShadowLog and context
 *         // Your test setup
 *     }
 *
 *     @Test
 *     fun testSomething() {
 *         // context is available from base class
 *         val activity = Robolectric.setupActivity(MyActivity::class.java)
 *         // ...
 *     }
 * }
 * ```
 */
abstract class RobolectricTestBase {
    protected lateinit var context: Context

    @Before
    open fun setUp() {
        // Route Robolectric logs to stdout for visibility in test output
        ShadowLog.stream = System.out
        context = ApplicationProvider.getApplicationContext()
    }
}

/**
 * Database test helpers.
 *
 * Utilities for managing test databases in Robolectric tests.
 */
object DatabaseTestHelpers {
    /**
     * Get a clean, empty database for testing.
     *
     * This creates a fresh database with all existing data removed:
     * - Deletes all card images from filesystem
     * - Clears all tables (cards, groups, card-group associations)
     *
     * Example:
     * ```kotlin
     * @Before
     * fun setUp() {
     *     database = DatabaseTestHelpers.getEmptyDatabase(context)
     *     // database is now empty and ready for test data
     * }
     * ```
     *
     * @param context Application context
     * @return Clean SQLiteDatabase ready for testing
     */
    fun getEmptyDatabase(context: Context): SQLiteDatabase {
        val dbHelper = DBHelper(context)
        val database = dbHelper.writableDatabase

        // Clean up any existing card images from filesystem
        val cursor = DBHelper.getLoyaltyCardCursor(database)
        cursor.moveToFirst()
        while (!cursor.isAfterLast) {
            val cardID = cursor.getInt(cursor.getColumnIndexOrThrow(DBHelper.LoyaltyCardDbIds.ID))

            for (imageType in ImageLocationType.entries) {
                try {
                    Utils.saveCardImage(context, null, cardID, imageType)
                } catch (ignored: FileNotFoundException) {
                    // Image didn't exist, that's fine
                }
            }
            cursor.moveToNext()
        }
        cursor.close()

        // Clear all tables
        database.execSQL("DELETE FROM ${DBHelper.LoyaltyCardDbIds.TABLE}")
        database.execSQL("DELETE FROM ${DBHelper.LoyaltyCardDbGroups.TABLE}")
        database.execSQL("DELETE FROM ${DBHelper.LoyaltyCardDbIdsGroups.TABLE}")

        return database
    }
}
