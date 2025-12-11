package protect.card_locker;

import static android.os.Looper.getMainLooper;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.bottomappbar.BottomAppBar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.zxing.BarcodeFormat;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowLog;

import java.math.BigDecimal;

@RunWith(RobolectricTestRunner.class)
public class LoyaltyCardViewActivityTest {
    private final String BARCODE_DATA = "428311627547";
    private final CatimaBarcode BARCODE_TYPE = CatimaBarcode.fromBarcode(BarcodeFormat.UPC_A);

    enum FieldTypeView {
        TextView,
        ImageView
    }

    @Before
    public void setUp() {
        ShadowLog.stream = System.out;
    }

    private void checkFieldProperties(final Activity activity, final int id, final int visibility,
                                      final Object contents, final FieldTypeView fieldType) {
        final View view = activity.findViewById(id);
        assertTrue(view != null);
        assertEquals(visibility, view.getVisibility());

        if (fieldType == FieldTypeView.TextView) {
            TextView textView = (TextView) view;
            assertEquals(contents, textView.getText().toString());
        } else if (fieldType == FieldTypeView.ImageView) {
            ImageView imageView = (ImageView) view;
            Bitmap image = null;
            try {
                image = ((BitmapDrawable) imageView.getDrawable()).getBitmap();
            } catch (ClassCastException e) {
                // This is probably a VectorDrawable, the placeholder image. Aka: No image.
            }

            if (contents == null && image == null) {
                return;
            }

            assertTrue(image.sameAs((Bitmap) contents));
        } else {
            throw new UnsupportedOperationException();
        }
    }

    private void checkAllViewFields(final Activity activity, final String cardId) {
        checkFieldProperties(activity, R.id.main_image_description, View.VISIBLE, cardId, FieldTypeView.TextView);
    }

    private ActivityController<LoyaltyCardViewActivity> createActivityWithLoyaltyCard(int loyaltyCardId) {
        Intent intent = new Intent();
        final Bundle bundle = new Bundle();
        bundle.putInt(LoyaltyCardViewActivity.BUNDLE_ID, loyaltyCardId);
        intent.putExtras(bundle);

        return Robolectric.buildActivity(LoyaltyCardViewActivity.class, intent).create();
    }

    @Test
    public void startWithLoyaltyCardViewModeCheckDisplay() {
        final Context context = ApplicationProvider.getApplicationContext();
        SQLiteDatabase database = TestHelpers.getEmptyDb(context).getWritableDatabase();

        long cardId = DBHelper.insertLoyaltyCard(database, "store", "note", null, null, new BigDecimal("0"), null, BARCODE_DATA, null, BARCODE_TYPE, Color.BLACK, 0, null, 0);

        ActivityController activityController = createActivityWithLoyaltyCard((int) cardId);
        Activity activity = (Activity) activityController.get();

        activityController.start();
        activityController.visible();
        activityController.resume();

        checkAllViewFields(activity, BARCODE_DATA);

        database.close();
    }

    @Test
    public void checkMenu() {
        final Context context = ApplicationProvider.getApplicationContext();
        SQLiteDatabase database = TestHelpers.getEmptyDb(context).getWritableDatabase();

        long cardId = DBHelper.insertLoyaltyCard(database, "store", "note", null, null, new BigDecimal("0"), null, BARCODE_DATA, null, BARCODE_TYPE, Color.BLACK, 0, null, 0);

        ActivityController activityController = createActivityWithLoyaltyCard((int) cardId);
        Activity activity = (Activity) activityController.get();

        activityController.start();
        activityController.visible();
        activityController.resume();

        shadowOf(getMainLooper()).idle();

        final Menu menu = shadowOf(activity).getOptionsMenu();
        assertTrue(menu != null);

        // The share, star and overflow options should be present
        assertEquals(menu.size(), 3);

        assertEquals("Share", menu.findItem(R.id.action_share).getTitle().toString());
        assertEquals("Add to favorites", menu.findItem(R.id.action_star_unstar).getTitle().toString());

        database.close();
    }

    @Test
    public void startWithoutParametersViewBack() {
        final Context context = ApplicationProvider.getApplicationContext();
        SQLiteDatabase database = TestHelpers.getEmptyDb(context).getWritableDatabase();

        long cardId = DBHelper.insertLoyaltyCard(database, "store", "note", null, null, new BigDecimal("0"), null, BARCODE_DATA, null, BARCODE_TYPE, Color.BLACK, 0, null, 0);

        ActivityController activityController = createActivityWithLoyaltyCard((int) cardId);
        Activity activity = (Activity) activityController.get();

        activityController.start();
        activityController.visible();
        activityController.resume();

        assertEquals(false, activity.isFinishing());
        shadowOf(activity).clickMenuItem(android.R.id.home);
        assertEquals(true, activity.isFinishing());

        database.close();
    }

    @Test
    public void startWithoutColors() {
        final Context context = ApplicationProvider.getApplicationContext();
        SQLiteDatabase database = TestHelpers.getEmptyDb(context).getWritableDatabase();

        long cardId = DBHelper.insertLoyaltyCard(database, "store", "note", null, null, new BigDecimal("0"), null, BARCODE_DATA, null, BARCODE_TYPE, null, 0, null, 0);

        ActivityController activityController = createActivityWithLoyaltyCard((int) cardId);
        Activity activity = (Activity) activityController.get();

        activityController.start();
        activityController.visible();
        activityController.resume();

        assertEquals(false, activity.isFinishing());
        shadowOf(activity).clickMenuItem(android.R.id.home);
        assertEquals(true, activity.isFinishing());

        database.close();
    }

    @Test
    public void checkPushStarIcon() {
        final Context context = ApplicationProvider.getApplicationContext();
        SQLiteDatabase database = TestHelpers.getEmptyDb(context).getWritableDatabase();

        long cardId = DBHelper.insertLoyaltyCard(database, "store", "note", null, null, new BigDecimal("0"), null, BARCODE_DATA, null, BARCODE_TYPE, Color.BLACK, 0, null, 0);

        ActivityController activityController = createActivityWithLoyaltyCard((int) cardId);
        Activity activity = (Activity) activityController.get();

        activityController.start();
        activityController.visible();
        activityController.resume();

        assertEquals(false, activity.isFinishing());

        shadowOf(getMainLooper()).idle();

        final Menu menu = shadowOf(activity).getOptionsMenu();
        assertTrue(menu != null);

        // The share, star and overflow options should be present
        assertEquals(menu.size(), 3);

        assertEquals("Add to favorites", menu.findItem(R.id.action_star_unstar).getTitle().toString());

        shadowOf(activity).clickMenuItem(R.id.action_star_unstar);
        shadowOf(getMainLooper()).idle();
        assertEquals("Remove from favorites", menu.findItem(R.id.action_star_unstar).getTitle().toString());

        shadowOf(activity).clickMenuItem(R.id.action_star_unstar);
        shadowOf(getMainLooper()).idle();
        assertEquals("Add to favorites", menu.findItem(R.id.action_star_unstar).getTitle().toString());

        database.close();
    }

    @Test
    public void checkBarcodeFullscreenWorkflow() {
        final Context context = ApplicationProvider.getApplicationContext();
        SQLiteDatabase database = TestHelpers.getEmptyDb(context).getWritableDatabase();

        long cardId = DBHelper.insertLoyaltyCard(database, "store", "note", null, null, new BigDecimal("0"), null, BARCODE_DATA, null, BARCODE_TYPE, Color.BLACK, 0, null, 0);

        ActivityController activityController = createActivityWithLoyaltyCard((int) cardId);
        AppCompatActivity activity = (AppCompatActivity) activityController.get();

        activityController.start();
        activityController.visible();
        activityController.resume();

        assertFalse(activity.isFinishing());

        BottomAppBar bottomAppBar = activity.findViewById(R.id.bottom_app_bar);
        ImageView mainImage = activity.findViewById(R.id.main_image);
        LinearLayout container = activity.findViewById(R.id.container);
        ConstraintLayout fullScreenLayout = activity.findViewById(R.id.fullscreen_layout);
        ImageButton minimizeButton = activity.findViewById(R.id.fullscreen_button_minimize);
        FloatingActionButton editButton = activity.findViewById(R.id.fabEdit);

        // Android should not be in fullscreen mode
        assertTrue(activity.getWindow().getDecorView().getRootWindowInsets().isVisible(WindowInsets.Type.statusBars()));
        assertTrue(activity.getWindow().getDecorView().getRootWindowInsets().isVisible(WindowInsets.Type.navigationBars()));
        assertEquals(WindowInsetsController.BEHAVIOR_DEFAULT, activity.getWindow().getInsetsController().getSystemBarsBehavior());

        // Elements should be visible (except minimize button and scaler)
        assertEquals(View.VISIBLE, bottomAppBar.getVisibility());
        assertEquals(View.VISIBLE, container.getVisibility());
        assertEquals(View.GONE, fullScreenLayout.getVisibility());
        assertEquals(View.VISIBLE, editButton.getVisibility());

        // Click maximize button to activate fullscreen
        mainImage.performClick();
        shadowOf(getMainLooper()).idle();

        // Android should be in fullscreen mode
        assertFalse(activity.getWindow().getDecorView().getRootWindowInsets().isVisible(WindowInsets.Type.statusBars()));
        assertFalse(activity.getWindow().getDecorView().getRootWindowInsets().isVisible(WindowInsets.Type.navigationBars()));
        assertEquals(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE, activity.getWindow().getInsetsController().getSystemBarsBehavior());

        // Elements should not be visible (except minimize button and scaler)
        assertEquals(View.GONE, bottomAppBar.getVisibility());
        assertEquals(View.GONE, container.getVisibility());
        assertEquals(View.VISIBLE, fullScreenLayout.getVisibility());
        assertEquals(View.GONE, editButton.getVisibility());

        // Clicking minimize button should deactivate fullscreen mode
        minimizeButton.performClick();
        shadowOf(getMainLooper()).idle();

        assertTrue(activity.getWindow().getDecorView().getRootWindowInsets().isVisible(WindowInsets.Type.statusBars()));
        assertTrue(activity.getWindow().getDecorView().getRootWindowInsets().isVisible(WindowInsets.Type.navigationBars()));
        assertEquals(WindowInsetsController.BEHAVIOR_DEFAULT, activity.getWindow().getInsetsController().getSystemBarsBehavior());

        assertEquals(View.VISIBLE, bottomAppBar.getVisibility());
        assertEquals(View.VISIBLE, container.getVisibility());
        assertEquals(View.GONE, fullScreenLayout.getVisibility());
        assertEquals(View.VISIBLE, editButton.getVisibility());

        // Another click back to fullscreen
        mainImage.performClick();
        shadowOf(getMainLooper()).idle();

        assertFalse(activity.getWindow().getDecorView().getRootWindowInsets().isVisible(WindowInsets.Type.statusBars()));
        assertFalse(activity.getWindow().getDecorView().getRootWindowInsets().isVisible(WindowInsets.Type.navigationBars()));
        assertEquals(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE, activity.getWindow().getInsetsController().getSystemBarsBehavior());

        assertEquals(View.GONE, bottomAppBar.getVisibility());
        assertEquals(View.GONE, container.getVisibility());
        assertEquals(View.VISIBLE, fullScreenLayout.getVisibility());
        assertEquals(View.GONE, editButton.getVisibility());

        // In full screen mode, back button should disable fullscreen
        activity.getOnBackPressedDispatcher().onBackPressed();
        shadowOf(getMainLooper()).idle();

        assertTrue(activity.getWindow().getDecorView().getRootWindowInsets().isVisible(WindowInsets.Type.statusBars()));
        assertTrue(activity.getWindow().getDecorView().getRootWindowInsets().isVisible(WindowInsets.Type.navigationBars()));
        assertEquals(WindowInsetsController.BEHAVIOR_DEFAULT, activity.getWindow().getInsetsController().getSystemBarsBehavior());

        assertEquals(View.VISIBLE, bottomAppBar.getVisibility());
        assertEquals(View.VISIBLE, container.getVisibility());
        assertEquals(View.GONE, fullScreenLayout.getVisibility());
        assertEquals(View.VISIBLE, editButton.getVisibility());

        // Pressing back when not in full screen should finish activity
        activity.getOnBackPressedDispatcher().onBackPressed();
        shadowOf(getMainLooper()).idle();
        assertTrue(activity.isFinishing());

        database.close();
    }

    @Test
    public void checkNoBarcodeFullscreenWorkflow() {
        final Context context = ApplicationProvider.getApplicationContext();
        SQLiteDatabase database = TestHelpers.getEmptyDb(context).getWritableDatabase();

        long cardId = DBHelper.insertLoyaltyCard(database, "store", "note", null, null, new BigDecimal("0"), null, BARCODE_DATA, null, null, Color.BLACK, 0, null, 0);

        ActivityController activityController = createActivityWithLoyaltyCard((int) cardId);
        AppCompatActivity activity = (AppCompatActivity) activityController.get();

        activityController.start();
        activityController.visible();
        activityController.resume();

        assertEquals(false, activity.isFinishing());

        BottomAppBar bottomAppBar = activity.findViewById(R.id.bottom_app_bar);
        ImageView mainImage = activity.findViewById(R.id.main_image);
        ConstraintLayout fullScreenLayout = activity.findViewById(R.id.fullscreen_layout);
        FloatingActionButton editButton = activity.findViewById(R.id.fabEdit);

        // Android should not be in fullscreen mode
        int uiOptions = activity.getWindow().getDecorView().getSystemUiVisibility();
        assertNotEquals(uiOptions | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY, uiOptions);
        assertNotEquals(uiOptions | View.SYSTEM_UI_FLAG_FULLSCREEN, uiOptions);

        // Elements should be visible (except minimize/maximize buttons and barcode and scaler)
        assertEquals(View.VISIBLE, bottomAppBar.getVisibility());
        assertEquals(View.GONE, mainImage.getVisibility());
        assertEquals(View.GONE, fullScreenLayout.getVisibility());
        assertEquals(View.VISIBLE, editButton.getVisibility());

        // Pressing back when not in full screen should finish activity
        activity.getOnBackPressedDispatcher().onBackPressed();
        shadowOf(getMainLooper()).idle();
        assertEquals(true, activity.isFinishing());

        database.close();
    }
}
