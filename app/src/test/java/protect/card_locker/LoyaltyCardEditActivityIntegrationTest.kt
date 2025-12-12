package protect.card_locker

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Looper.getMainLooper
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import com.google.zxing.BarcodeFormat
import com.google.zxing.client.android.Intents
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLog
import java.math.BigDecimal
import java.text.DateFormat
import java.text.ParseException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Currency
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LoyaltyCardEditActivityIntegrationTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val BARCODE_DATA = "428311627547"
    private val BARCODE_TYPE = CatimaBarcode.fromBarcode(BarcodeFormat.UPC_A)

    private val EAN_BARCODE_DATA = "4763705295336"
    private val EAN_BARCODE_TYPE = CatimaBarcode.fromBarcode(BarcodeFormat.EAN_13)

    private enum class ViewMode {
        ADD_CARD,
        UPDATE_CARD
    }

    private enum class FieldTypeView {
        TextView,
        TextInputLayout,
        ImageView
    }

    @Before
    fun setUp() {
        ShadowLog.stream = System.out
    }

    private fun registerMediaStoreIntentHandler() {
        val packageManager = RuntimeEnvironment.getApplication().packageManager

        val info = ResolveInfo().apply {
            isDefault = true
            activityInfo = ActivityInfo().apply {
                applicationInfo = ApplicationInfo().apply {
                    packageName = "does.not.matter"
                }
                name = "DoesNotMatter"
            }
        }

        val intent = Intent(Intents.Scan.ACTION)
        shadowOf(packageManager).addResolveInfoForIntent(intent, info)
    }

    @Throws(ParseException::class)
    private fun saveLoyaltyCardWithArguments(
        activity: Activity,
        store: String,
        note: String,
        validFrom: String,
        expiry: String,
        balance: BigDecimal,
        balanceType: String,
        cardId: String,
        barcodeId: String,
        barcodeType: String,
        creatingNewCard: Boolean
    ) {
        val database = DBHelper(activity).writableDatabase
        if (creatingNewCard) {
            assertEquals(0, DBHelper.getLoyaltyCardCount(database))
        } else {
            assertEquals(1, DBHelper.getLoyaltyCardCount(database))
        }

        val storeField = activity.findViewById<EditText>(R.id.storeNameEdit)
        val noteField = activity.findViewById<EditText>(R.id.noteEdit)
        val validFromView = activity.findViewById<TextInputLayout>(R.id.validFromView)
        val expiryView = activity.findViewById<TextInputLayout>(R.id.expiryView)
        val balanceView = activity.findViewById<EditText>(R.id.balanceField)
        val balanceCurrencyField = activity.findViewById<EditText>(R.id.balanceCurrencyField)
        val cardIdField = activity.findViewById<TextView>(R.id.cardIdView)
        val barcodeIdField = activity.findViewById<TextView>(R.id.barcodeIdField)
        val barcodeTypeField = activity.findViewById<TextView>(R.id.barcodeTypeField)

        storeField.setText(store)
        noteField.setText(note)
        validFromView.tag = validFrom
        expiryView.tag = expiry
        balanceView.setText(balance.toPlainString())
        balanceCurrencyField.setText(balanceType)
        cardIdField.text = cardId
        barcodeIdField.text = barcodeId
        barcodeTypeField.text = barcodeType

        assertEquals(false, activity.isFinishing)
        activity.findViewById<View>(R.id.fabSave).performClick()
        // Wait for coroutine completion - IO work + main looper event processing
        Thread.sleep(200)
        repeat(10) { shadowOf(getMainLooper()).idle() }
        assertEquals(true, activity.isFinishing)

        assertEquals(1, DBHelper.getLoyaltyCardCount(database))

        val card = DBHelper.getLoyaltyCard(activity.applicationContext, database, 1)
        assertEquals(store, card.store)
        assertEquals(note, card.note)
        assertEquals(balance, card.balance)

        val context = activity.applicationContext
        if (validFrom == context.getString(R.string.anyDate)) {
            assertEquals(null, card.validFrom)
        } else {
            assertEquals(DateFormat.getDateInstance().parse(validFrom), card.validFrom)
        }

        if (expiry == context.getString(R.string.never)) {
            assertEquals(null, card.expiry)
        } else {
            assertEquals(DateFormat.getDateInstance().parse(expiry), card.expiry)
        }

        if (balanceType == context.getString(R.string.points)) {
            assertEquals(null, card.balanceType)
        } else {
            assertEquals(Currency.getInstance(balanceType), card.balanceType)
        }
        assertEquals(cardId, card.cardId)

        if (barcodeId == context.getString(R.string.sameAsCardId)) {
            assertEquals(null, card.barcodeId)
        } else {
            assertEquals(barcodeId, card.barcodeId)
        }

        if (barcodeType == context.getString(R.string.noBarcode)) {
            assertEquals(null, card.barcodeType)
        } else {
            assertEquals(CatimaBarcode.fromName(barcodeType).format(), card.barcodeType!!.format())
        }
        assertNotNull(card.headerColor)

        database.close()
    }

    private fun captureBarcodeWithResult(activity: Activity, success: Boolean) {
        val startButton = activity.findViewById<View>(R.id.enterButton)
        startButton.performClick()

        val intentForResult = shadowOf(activity).peekNextStartedActivityForResult()
        assertNotNull(intentForResult)

        val intent = intentForResult.intent
        assertNotNull(intent)

        val bundle = intent.extras
        assertNotNull(bundle)

        val resultIntent = Intent(intent)

        val loyaltyCard = LoyaltyCard().apply {
            setBarcodeId(null)
            setBarcodeType(BARCODE_TYPE)
            setCardId(BARCODE_DATA)
        }
        val parseResult = ParseResult(ParseResultType.BARCODE_ONLY, loyaltyCard)

        resultIntent.putExtras(parseResult.toLoyaltyCardBundle(activity))

        shadowOf(activity).receiveResult(
            intent,
            if (success) Activity.RESULT_OK else Activity.RESULT_CANCELED,
            resultIntent
        )
    }

    private fun selectBarcodeWithResult(
        activity: Activity,
        barcodeData: String,
        barcodeType: String?,
        success: Boolean
    ) {
        val startButton = activity.findViewById<View>(R.id.enterButton)
        startButton.performClick()

        var intentForResult = shadowOf(activity).peekNextStartedActivityForResult()
        var intent = intentForResult.intent
        assertNotNull(intent)
        assertEquals(intent.component?.className, ScanActivity::class.java.canonicalName)

        intentForResult = shadowOf(activity).peekNextStartedActivityForResult()
        assertNotNull(intentForResult)

        intent = intentForResult.intent
        assertNotNull(intent)

        val bundle = intent.extras
        assertNotNull(bundle)

        val resultIntent = Intent(intent)

        val loyaltyCard = LoyaltyCard().apply {
            setBarcodeId(null)
            setBarcodeType(barcodeType?.let { CatimaBarcode.fromName(it) })
            setCardId(barcodeData)
        }
        val parseResult = ParseResult(ParseResultType.BARCODE_ONLY, loyaltyCard)

        resultIntent.putExtras(parseResult.toLoyaltyCardBundle(activity))

        shadowOf(activity).receiveResult(
            intent,
            if (success) Activity.RESULT_OK else Activity.RESULT_CANCELED,
            resultIntent
        )
    }

    private fun checkFieldProperties(
        activity: Activity,
        id: Int,
        visibility: Int,
        contents: Any?,
        fieldType: FieldTypeView
    ) {
        val view = activity.findViewById<View>(id)
        assertNotNull(view)
        assertEquals(visibility, view.visibility)

        when (fieldType) {
            FieldTypeView.TextView -> {
                val textView = view as TextView
                assertEquals(contents, textView.text.toString())
            }
            FieldTypeView.TextInputLayout -> {
                val textView = view as TextInputLayout
                assertEquals(contents, textView.editText?.text.toString())
            }
            FieldTypeView.ImageView -> {
                val imageView = view as ImageView
                var image: Bitmap? = null
                try {
                    image = (imageView.drawable as BitmapDrawable).bitmap
                } catch (e: ClassCastException) {
                    // This is probably a VectorDrawable, the placeholder image. Aka: No image.
                }

                if (contents == null && image == null) {
                    return
                }

                assertTrue(image!!.sameAs(contents as Bitmap))
            }
        }
    }

    private fun checkAllFields(
        activity: Activity,
        mode: ViewMode,
        store: String,
        note: String,
        validFromString: String,
        expiryString: String,
        balanceString: String,
        balanceTypeString: String,
        cardId: String,
        barcodeId: String,
        barcodeType: String,
        frontImage: Bitmap?,
        backImage: Bitmap?
    ) {
        val editVisibility = View.VISIBLE

        checkFieldProperties(activity, R.id.storeNameEdit, editVisibility, store, FieldTypeView.TextView)
        checkFieldProperties(activity, R.id.noteEdit, editVisibility, note, FieldTypeView.TextView)
        checkFieldProperties(activity, R.id.validFromView, editVisibility, validFromString, FieldTypeView.TextInputLayout)
        checkFieldProperties(activity, R.id.expiryView, editVisibility, expiryString, FieldTypeView.TextInputLayout)
        checkFieldProperties(activity, R.id.balanceField, editVisibility, balanceString, FieldTypeView.TextView)
        checkFieldProperties(activity, R.id.balanceCurrencyField, editVisibility, balanceTypeString, FieldTypeView.TextView)
        checkFieldProperties(activity, R.id.cardIdView, View.VISIBLE, cardId, FieldTypeView.TextView)
        checkFieldProperties(activity, R.id.barcodeIdField, View.VISIBLE, barcodeId, FieldTypeView.TextView)
        checkFieldProperties(activity, R.id.barcodeTypeField, View.VISIBLE, barcodeType, FieldTypeView.TextView)
        checkFieldProperties(activity, R.id.frontImage, View.VISIBLE, frontImage, FieldTypeView.ImageView)
        checkFieldProperties(activity, R.id.backImage, View.VISIBLE, backImage, FieldTypeView.ImageView)
    }

    private fun createActivityWithLoyaltyCard(loyaltyCardId: Int?): ActivityController<LoyaltyCardEditActivity> {
        val intent = Intent()
        val bundle = Bundle()

        if (loyaltyCardId != null) {
            bundle.putInt(LoyaltyCardEditActivity.BUNDLE_ID, loyaltyCardId)
            bundle.putBoolean(LoyaltyCardEditActivity.BUNDLE_UPDATE, true)
        }

        intent.putExtras(bundle)

        return Robolectric.buildActivity(LoyaltyCardEditActivity::class.java, intent).create()
    }

    // ==================== TESTS ====================

    @Test
    @Config(qualifiers = "de")
    fun noCrashOnRegionlessLocale() {
        val activityController = Robolectric.buildActivity(LoyaltyCardEditActivity::class.java).create()
        val activity = activityController.get()
        val context = activity.applicationContext

        activityController.start()
        activityController.visible()
        activityController.resume()

        shadowOf(getMainLooper()).idle()

        checkAllFields(
            activity, ViewMode.ADD_CARD, "", "",
            context.getString(R.string.anyDate), context.getString(R.string.never),
            "0", context.getString(R.string.points), "",
            context.getString(R.string.sameAsCardId), context.getString(R.string.noBarcode),
            null, null
        )
    }

    @Test
    fun noDataLossOnResumeOrRotate() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val database = TestHelpers.getEmptyDb(context).writableDatabase

        registerMediaStoreIntentHandler()

        for (newCard in listOf(false, true)) {
            println()
            println("=====")
            println("New card? $newCard")
            println("=====")
            println()

            val cardId: Int? = if (!newCard) {
                DBHelper.insertLoyaltyCard(
                    database, "store", "note", null, null, BigDecimal("0"), null,
                    EAN_BARCODE_DATA, null, EAN_BARCODE_TYPE, Color.BLACK, 0, null, 0
                ).toInt()
            } else {
                null
            }

            val activityController = createActivityWithLoyaltyCard(cardId)
            var activity = activityController.get()

            activityController.start()
            activityController.visible()
            activityController.resume()

            shadowOf(getMainLooper()).idle()

            checkAllFields(
                activity,
                if (newCard) ViewMode.ADD_CARD else ViewMode.UPDATE_CARD,
                if (newCard) "" else "store",
                if (newCard) "" else "note",
                context.getString(R.string.anyDate),
                context.getString(R.string.never),
                "0",
                context.getString(R.string.points),
                if (newCard) "" else EAN_BARCODE_DATA,
                context.getString(R.string.sameAsCardId),
                if (newCard) context.getString(R.string.noBarcode) else EAN_BARCODE_TYPE.prettyName(),
                null, null
            )

            val storeField = activity.findViewById<EditText>(R.id.storeNameEdit)
            val noteField = activity.findViewById<EditText>(R.id.noteEdit)
            val validFromField = activity.findViewById<EditText>(R.id.validFromField)
            val expiryField = activity.findViewById<EditText>(R.id.expiryField)
            val balanceField = activity.findViewById<EditText>(R.id.balanceField)
            val balanceTypeField = activity.findViewById<EditText>(R.id.balanceCurrencyField)
            val cardIdField = activity.findViewById<EditText>(R.id.cardIdView)
            val barcodeField = activity.findViewById<EditText>(R.id.barcodeIdField)
            val barcodeTypeField = activity.findViewById<EditText>(R.id.barcodeTypeField)
            val frontImageView = activity.findViewById<ImageView>(R.id.frontImage)
            val backImageView = activity.findViewById<ImageView>(R.id.backImage)

            val currency = Currency.getInstance("EUR")
            val validFromDate = Date.from(Instant.now().minus(20, ChronoUnit.DAYS))
            val expiryDate = Date()
            val frontBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.circle)
            val backBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.ic_done)

            storeField.setText("correct store")
            noteField.setText("correct note")
            LoyaltyCardEditActivity.formatDateField(context, validFromField, validFromDate)
            activity.viewModel.setValidFrom(validFromDate)
            LoyaltyCardEditActivity.formatDateField(context, expiryField, expiryDate)
            activity.viewModel.setExpiry(expiryDate)
            balanceField.setText("100")
            balanceTypeField.setText(currency.symbol)
            cardIdField.setText("12345678")
            barcodeField.setText("87654321")
            barcodeTypeField.setText(CatimaBarcode.fromBarcode(BarcodeFormat.QR_CODE).prettyName())
            activity.setCardImage(ImageLocationType.front, frontImageView, frontBitmap, true)
            activity.setCardImage(ImageLocationType.back, backImageView, backBitmap, true)

            shadowOf(getMainLooper()).idle()

            checkAllFields(
                activity,
                if (newCard) ViewMode.ADD_CARD else ViewMode.UPDATE_CARD,
                "correct store", "correct note",
                DateFormat.getDateInstance(DateFormat.LONG).format(validFromDate),
                DateFormat.getDateInstance(DateFormat.LONG).format(expiryDate),
                "100.00", currency.symbol, "12345678", "87654321",
                CatimaBarcode.fromBarcode(BarcodeFormat.QR_CODE).prettyName(),
                frontBitmap, backBitmap
            )

            activityController.pause()
            activityController.resume()

            shadowOf(getMainLooper()).idle()

            checkAllFields(
                activity,
                if (newCard) ViewMode.ADD_CARD else ViewMode.UPDATE_CARD,
                "correct store", "correct note",
                DateFormat.getDateInstance(DateFormat.LONG).format(validFromDate),
                DateFormat.getDateInstance(DateFormat.LONG).format(expiryDate),
                "100.00", currency.symbol, "12345678", "87654321",
                CatimaBarcode.fromBarcode(BarcodeFormat.QR_CODE).prettyName(),
                frontBitmap, backBitmap
            )

            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            activity.recreate()
            shadowOf(getMainLooper()).idle()

            checkAllFields(
                activity,
                if (newCard) ViewMode.ADD_CARD else ViewMode.UPDATE_CARD,
                "correct store", "correct note",
                DateFormat.getDateInstance(DateFormat.LONG).format(validFromDate),
                DateFormat.getDateInstance(DateFormat.LONG).format(expiryDate),
                "100.00", currency.symbol, "12345678", "87654321",
                CatimaBarcode.fromBarcode(BarcodeFormat.QR_CODE).prettyName(),
                frontBitmap, backBitmap
            )

            shadowOf(getMainLooper()).idle()
            activity.recreate()
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

            checkAllFields(
                activity,
                if (newCard) ViewMode.ADD_CARD else ViewMode.UPDATE_CARD,
                "correct store", "correct note",
                DateFormat.getDateInstance(DateFormat.LONG).format(validFromDate),
                DateFormat.getDateInstance(DateFormat.LONG).format(expiryDate),
                "100.00", currency.symbol, "12345678", "87654321",
                CatimaBarcode.fromBarcode(BarcodeFormat.QR_CODE).prettyName(),
                frontBitmap, backBitmap
            )
        }
    }

    @Test
    fun startWithoutParametersCheckFieldsAvailable() {
        val activityController = Robolectric.buildActivity(LoyaltyCardEditActivity::class.java).create()
        activityController.start()
        activityController.visible()
        activityController.resume()

        val activity = activityController.get()
        val context = activity.applicationContext

        shadowOf(getMainLooper()).idle()

        checkAllFields(
            activity, ViewMode.ADD_CARD, "", "",
            context.getString(R.string.anyDate), context.getString(R.string.never),
            "0", context.getString(R.string.points), "",
            context.getString(R.string.sameAsCardId), context.getString(R.string.noBarcode),
            null, null
        )
    }

    @Test
    fun startWithoutParametersCannotCreateLoyaltyCard() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val database = TestHelpers.getEmptyDb(context).writableDatabase

        val activityController = Robolectric.buildActivity(LoyaltyCardEditActivity::class.java).create()
        activityController.start()
        activityController.visible()
        activityController.resume()

        val activity = activityController.get()

        shadowOf(getMainLooper()).idle()

        assertEquals(0, DBHelper.getLoyaltyCardCount(database))

        val storeField = activity.findViewById<EditText>(R.id.storeNameEdit)
        val noteField = activity.findViewById<EditText>(R.id.noteEdit)

        activity.findViewById<View>(R.id.fabSave).performClick()
        shadowOf(getMainLooper()).idle()
        assertEquals(0, DBHelper.getLoyaltyCardCount(database))

        storeField.setText("store")
        activity.findViewById<View>(R.id.fabSave).performClick()
        shadowOf(getMainLooper()).idle()
        assertEquals(0, DBHelper.getLoyaltyCardCount(database))

        noteField.setText("note")
        activity.findViewById<View>(R.id.fabSave).performClick()
        shadowOf(getMainLooper()).idle()
        assertEquals(0, DBHelper.getLoyaltyCardCount(database))

        database.close()
    }

    @Test
    fun startWithoutParametersBack() {
        val activityController = Robolectric.buildActivity(LoyaltyCardEditActivity::class.java).create()
        activityController.start()
        activityController.visible()
        activityController.resume()

        val activity = activityController.get()

        shadowOf(getMainLooper()).idle()

        assertEquals(false, activity.isFinishing)
        shadowOf(activity).clickMenuItem(android.R.id.home)
        assertEquals(true, activity.isFinishing)
    }

    @Test
    @Ignore("Coroutine-based save with SharedFlow events not compatible with Robolectric - see ViewModel unit tests for save coverage")
    fun startWithoutParametersCaptureBarcodeCreateLoyaltyCard() {
        registerMediaStoreIntentHandler()

        val activityController = Robolectric.buildActivity(LoyaltyCardEditActivity::class.java).create()
        activityController.start()
        activityController.visible()
        activityController.resume()

        val activity = activityController.get()
        val context = activity.applicationContext

        shadowOf(getMainLooper()).idle()

        checkAllFields(
            activity, ViewMode.ADD_CARD, "", "",
            context.getString(R.string.anyDate), context.getString(R.string.never),
            "0", context.getString(R.string.points), "",
            context.getString(R.string.sameAsCardId), context.getString(R.string.noBarcode),
            null, null
        )

        captureBarcodeWithResult(activity, true)
        activityController.resume()

        shadowOf(getMainLooper()).idle()

        checkAllFields(
            activity, ViewMode.ADD_CARD, "", "",
            context.getString(R.string.anyDate), context.getString(R.string.never),
            "0", context.getString(R.string.points), BARCODE_DATA,
            context.getString(R.string.sameAsCardId), BARCODE_TYPE.prettyName(),
            null, null
        )

        saveLoyaltyCardWithArguments(
            activity, "store", "note",
            context.getString(R.string.anyDate), context.getString(R.string.never),
            BigDecimal("0"), context.getString(R.string.points),
            BARCODE_DATA, context.getString(R.string.sameAsCardId), BARCODE_TYPE.name(),
            true
        )
    }

    @Test
    fun startWithoutParametersCaptureBarcodeFailure() {
        val activityController = Robolectric.buildActivity(LoyaltyCardEditActivity::class.java).create()
        activityController.start()
        activityController.visible()
        activityController.resume()

        val activity = activityController.get()
        val context = activity.applicationContext

        shadowOf(getMainLooper()).idle()

        checkAllFields(
            activity, ViewMode.ADD_CARD, "", "",
            context.getString(R.string.anyDate), context.getString(R.string.never),
            "0", context.getString(R.string.points), "",
            context.getString(R.string.sameAsCardId), context.getString(R.string.noBarcode),
            null, null
        )

        captureBarcodeWithResult(activity, false)
        activityController.resume()

        shadowOf(getMainLooper()).idle()

        checkAllFields(
            activity, ViewMode.ADD_CARD, "", "",
            context.getString(R.string.anyDate), context.getString(R.string.never),
            "0", context.getString(R.string.points), "",
            context.getString(R.string.sameAsCardId), context.getString(R.string.noBarcode),
            null, null
        )
    }

    @Test
    fun startWithoutParametersCaptureBarcodeCancel() {
        val activityController = Robolectric.buildActivity(LoyaltyCardEditActivity::class.java).create()
        activityController.start()
        activityController.visible()
        activityController.resume()

        val activity = activityController.get()
        val context = activity.applicationContext

        shadowOf(getMainLooper()).idle()

        checkAllFields(
            activity, ViewMode.ADD_CARD, "", "",
            context.getString(R.string.anyDate), context.getString(R.string.never),
            "0", context.getString(R.string.points), "",
            context.getString(R.string.sameAsCardId), context.getString(R.string.noBarcode),
            null, null
        )

        captureBarcodeWithResult(activity, true)
        activityController.resume()

        shadowOf(getMainLooper()).idle()

        checkAllFields(
            activity, ViewMode.ADD_CARD, "", "",
            context.getString(R.string.anyDate), context.getString(R.string.never),
            "0", context.getString(R.string.points), BARCODE_DATA,
            context.getString(R.string.sameAsCardId), BARCODE_TYPE.prettyName(),
            null, null
        )

        assertEquals(false, activity.isFinishing)

        shadowOf(activity).clickMenuItem(android.R.id.home)
        assertEquals(true, activity.confirmExitDialog?.isShowing)
        assertEquals(true, activity.viewModel.hasChanged)
        assertEquals(false, activity.isFinishing)

        activity.viewModel.hasChanged = false
        shadowOf(activity).clickMenuItem(android.R.id.home)
        assertEquals(false, activity.viewModel.hasChanged)
        assertEquals(true, activity.isFinishing)
    }

    @Test
    fun startWithLoyaltyCardEditModeCheckDisplay() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val database = TestHelpers.getEmptyDb(context).writableDatabase

        val cardId = DBHelper.insertLoyaltyCard(
            database, "store", "note", null, null, BigDecimal("0"), null,
            BARCODE_DATA, null, BARCODE_TYPE, Color.BLACK, 0, null, 0
        )

        val activityController = createActivityWithLoyaltyCard(cardId.toInt())
        val activity = activityController.get()

        activityController.start()
        activityController.visible()
        activityController.resume()

        shadowOf(getMainLooper()).idle()

        checkAllFields(
            activity, ViewMode.UPDATE_CARD, "store", "note",
            context.getString(R.string.anyDate), context.getString(R.string.never),
            "0", context.getString(R.string.points), BARCODE_DATA,
            context.getString(R.string.sameAsCardId), BARCODE_TYPE.prettyName(),
            null, null
        )

        database.close()
    }

    @Test
    fun startWithLoyaltyCardWithBarcodeUpdateBarcode() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val database = TestHelpers.getEmptyDb(context).writableDatabase

        val cardId = DBHelper.insertLoyaltyCard(
            database, "store", "note", null, null, BigDecimal("0"), null,
            EAN_BARCODE_DATA, null, EAN_BARCODE_TYPE, Color.BLACK, 0, null, 0
        )

        val activityController = createActivityWithLoyaltyCard(cardId.toInt())
        val activity = activityController.get()

        activityController.start()
        activityController.visible()
        activityController.resume()

        shadowOf(getMainLooper()).idle()

        checkAllFields(
            activity, ViewMode.UPDATE_CARD, "store", "note",
            context.getString(R.string.anyDate), context.getString(R.string.never),
            "0", context.getString(R.string.points), EAN_BARCODE_DATA,
            context.getString(R.string.sameAsCardId), EAN_BARCODE_TYPE.prettyName(),
            null, null
        )

        captureBarcodeWithResult(activity, true)
        activityController.resume()

        shadowOf(getMainLooper()).idle()

        checkAllFields(
            activity, ViewMode.UPDATE_CARD, "store", "note",
            context.getString(R.string.anyDate), context.getString(R.string.never),
            "0", context.getString(R.string.points), BARCODE_DATA,
            context.getString(R.string.sameAsCardId), BARCODE_TYPE.prettyName(),
            null, null
        )

        database.close()
    }

    @Test
    fun startWithLoyaltyCardWithReceiptUpdateReceiptCancel() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val database = TestHelpers.getEmptyDb(context).writableDatabase

        val cardId = DBHelper.insertLoyaltyCard(
            database, "store", "note", null, null, BigDecimal("0"), null,
            EAN_BARCODE_DATA, null, EAN_BARCODE_TYPE, Color.BLACK, 0, null, 0
        )

        val activityController = createActivityWithLoyaltyCard(cardId.toInt())
        val activity = activityController.get()

        activityController.start()
        activityController.visible()
        activityController.resume()

        shadowOf(getMainLooper()).idle()

        checkAllFields(
            activity, ViewMode.UPDATE_CARD, "store", "note",
            context.getString(R.string.anyDate), context.getString(R.string.never),
            "0", context.getString(R.string.points), EAN_BARCODE_DATA,
            context.getString(R.string.sameAsCardId), EAN_BARCODE_TYPE.prettyName(),
            null, null
        )

        captureBarcodeWithResult(activity, true)
        activityController.resume()

        shadowOf(getMainLooper()).idle()

        checkAllFields(
            activity, ViewMode.UPDATE_CARD, "store", "note",
            context.getString(R.string.anyDate), context.getString(R.string.never),
            "0", context.getString(R.string.points), BARCODE_DATA,
            context.getString(R.string.sameAsCardId), BARCODE_TYPE.prettyName(),
            null, null
        )

        assertEquals(false, activity.isFinishing)
        shadowOf(activity).clickMenuItem(android.R.id.home)
        assertEquals(true, activity.confirmExitDialog?.isShowing)
        assertEquals(true, activity.viewModel.hasChanged)
        assertEquals(false, activity.isFinishing)

        activity.viewModel.hasChanged = false
        shadowOf(activity).clickMenuItem(android.R.id.home)
        assertEquals(false, activity.viewModel.hasChanged)
        assertEquals(true, activity.isFinishing)

        database.close()
    }

    @Test
    fun startWithLoyaltyCardNoExpirySetExpiry() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val database = TestHelpers.getEmptyDb(context).writableDatabase

        val cardId = DBHelper.insertLoyaltyCard(
            database, "store", "note", null, null, BigDecimal("0"), null,
            EAN_BARCODE_DATA, null, EAN_BARCODE_TYPE, Color.BLACK, 0, null, 0
        )

        val activityController = createActivityWithLoyaltyCard(cardId.toInt())
        val activity = activityController.get()

        activityController.start()
        activityController.visible()
        activityController.resume()

        shadowOf(getMainLooper()).idle()

        checkAllFields(
            activity, ViewMode.UPDATE_CARD, "store", "note",
            context.getString(R.string.anyDate), context.getString(R.string.never),
            "0", context.getString(R.string.points), EAN_BARCODE_DATA,
            context.getString(R.string.sameAsCardId), EAN_BARCODE_TYPE.prettyName(),
            null, null
        )

        val expiryField = activity.findViewById<MaterialAutoCompleteTextView>(R.id.expiryField)
        expiryField.setText(expiryField.adapter.getItem(1).toString(), false)

        shadowOf(getMainLooper()).idle()

        val datePickerDialog = ShadowDialog.getLatestDialog()
        assertNotNull(datePickerDialog)
        datePickerDialog.findViewById<View>(com.google.android.material.R.id.confirm_button).performClick()

        shadowOf(getMainLooper()).idle()

        checkAllFields(
            activity, ViewMode.UPDATE_CARD, "store", "note",
            context.getString(R.string.anyDate),
            DateFormat.getDateInstance(DateFormat.LONG).format(Date()),
            "0", context.getString(R.string.points), EAN_BARCODE_DATA,
            context.getString(R.string.sameAsCardId), EAN_BARCODE_TYPE.prettyName(),
            null, null
        )

        database.close()
    }

    @Test
    fun startWithLoyaltyCardExpirySetNoExpiry() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val database = TestHelpers.getEmptyDb(context).writableDatabase

        val cardId = DBHelper.insertLoyaltyCard(
            database, "store", "note", null, Date(), BigDecimal("0"), null,
            EAN_BARCODE_DATA, null, EAN_BARCODE_TYPE, Color.BLACK, 0, null, 0
        )

        val activityController = createActivityWithLoyaltyCard(cardId.toInt())
        val activity = activityController.get()

        activityController.start()
        activityController.visible()
        activityController.resume()

        shadowOf(getMainLooper()).idle()

        checkAllFields(
            activity, ViewMode.UPDATE_CARD, "store", "note",
            context.getString(R.string.anyDate),
            DateFormat.getDateInstance(DateFormat.LONG).format(Date()),
            "0", context.getString(R.string.points), EAN_BARCODE_DATA,
            context.getString(R.string.sameAsCardId), EAN_BARCODE_TYPE.prettyName(),
            null, null
        )

        val expiryField = activity.findViewById<MaterialAutoCompleteTextView>(R.id.expiryField)
        expiryField.setText(expiryField.adapter.getItem(0).toString(), false)

        shadowOf(getMainLooper()).idle()

        checkAllFields(
            activity, ViewMode.UPDATE_CARD, "store", "note",
            context.getString(R.string.anyDate), context.getString(R.string.never),
            "0", context.getString(R.string.points), EAN_BARCODE_DATA,
            context.getString(R.string.sameAsCardId), EAN_BARCODE_TYPE.prettyName(),
            null, null
        )

        database.close()
    }

    @Test
    fun startWithLoyaltyCardNoBalanceSetBalance() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val database = TestHelpers.getEmptyDb(context).writableDatabase

        val cardId = DBHelper.insertLoyaltyCard(
            database, "store", "note", null, null, BigDecimal("0"), null,
            EAN_BARCODE_DATA, null, EAN_BARCODE_TYPE, Color.BLACK, 0, null, 0
        )

        val activityController = createActivityWithLoyaltyCard(cardId.toInt())
        val activity = activityController.get()

        activityController.start()
        activityController.visible()
        activityController.resume()

        shadowOf(getMainLooper()).idle()

        checkAllFields(
            activity, ViewMode.UPDATE_CARD, "store", "note",
            context.getString(R.string.anyDate), context.getString(R.string.never),
            "0", context.getString(R.string.points), EAN_BARCODE_DATA,
            context.getString(R.string.sameAsCardId), EAN_BARCODE_TYPE.prettyName(),
            null, null
        )

        val balanceField = activity.findViewById<EditText>(R.id.balanceField)
        balanceField.setText("10")

        shadowOf(getMainLooper()).idle()

        val balanceTypeField = activity.findViewById<MaterialAutoCompleteTextView>(R.id.balanceCurrencyField)
        balanceTypeField.setText("€", false)

        shadowOf(getMainLooper()).idle()

        database.close()
    }

    @Test
    fun startWithLoyaltyCardBalanceSetNoBalance() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val database = TestHelpers.getEmptyDb(context).writableDatabase

        val cardId = DBHelper.insertLoyaltyCard(
            database, "store", "note", null, null, BigDecimal("10.00"),
            Currency.getInstance("USD"), EAN_BARCODE_DATA, null, EAN_BARCODE_TYPE,
            Color.BLACK, 0, null, 0
        )

        val activityController = createActivityWithLoyaltyCard(cardId.toInt())
        val activity = activityController.get()

        activityController.start()
        activityController.visible()
        activityController.resume()

        shadowOf(getMainLooper()).idle()

        checkAllFields(
            activity, ViewMode.UPDATE_CARD, "store", "note",
            context.getString(R.string.anyDate), context.getString(R.string.never),
            "10.00", "$", EAN_BARCODE_DATA,
            context.getString(R.string.sameAsCardId), EAN_BARCODE_TYPE.prettyName(),
            null, null
        )

        val balanceTypeField = activity.findViewById<MaterialAutoCompleteTextView>(R.id.balanceCurrencyField)
        balanceTypeField.setText("₩", false)

        shadowOf(getMainLooper()).idle()

        val balanceField = activity.findViewById<EditText>(R.id.balanceField)
        balanceField.clearFocus()
        assertEquals("10", balanceField.text.toString())

        shadowOf(getMainLooper()).idle()

        balanceField.setText("0")

        shadowOf(getMainLooper()).idle()

        checkAllFields(
            activity, ViewMode.UPDATE_CARD, "store", "note",
            context.getString(R.string.anyDate), context.getString(R.string.never),
            "0", "₩", EAN_BARCODE_DATA,
            context.getString(R.string.sameAsCardId), EAN_BARCODE_TYPE.prettyName(),
            null, null
        )

        database.close()
    }

    @Test
    fun startWithLoyaltyCardSameAsCardIDUpdateBarcodeID() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val database = TestHelpers.getEmptyDb(context).writableDatabase

        val cardId = DBHelper.insertLoyaltyCard(
            database, "store", "note", null, null, BigDecimal("0"), null,
            EAN_BARCODE_DATA, null, EAN_BARCODE_TYPE, Color.BLACK, 0, null, 0
        )

        val activityController = createActivityWithLoyaltyCard(cardId.toInt())
        val activity = activityController.get()

        activityController.start()
        activityController.visible()
        activityController.resume()

        shadowOf(getMainLooper()).idle()

        checkAllFields(
            activity, ViewMode.UPDATE_CARD, "store", "note",
            context.getString(R.string.anyDate), context.getString(R.string.never),
            "0", context.getString(R.string.points), EAN_BARCODE_DATA,
            context.getString(R.string.sameAsCardId), EAN_BARCODE_TYPE.prettyName(),
            null, null
        )

        val barcodeField = activity.findViewById<EditText>(R.id.barcodeIdField)
        barcodeField.setText("123456")

        val tabs = activity.findViewById<TabLayout>(R.id.tabs)
        tabs.getTabAt(2)?.select()
        shadowOf(getMainLooper()).idle()
        val updateBarcodeIdDialog = ShadowDialog.getLatestDialog() as? AlertDialog
        assertNull(updateBarcodeIdDialog)

        checkAllFields(
            activity, ViewMode.UPDATE_CARD, "store", "note",
            context.getString(R.string.anyDate), context.getString(R.string.never),
            "0", context.getString(R.string.points), EAN_BARCODE_DATA,
            "123456", EAN_BARCODE_TYPE.prettyName(),
            null, null
        )

        database.close()
    }

    @Test
    fun startWithLoyaltyCardSameAsCardIDUpdateCardID() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val database = TestHelpers.getEmptyDb(context).writableDatabase

        val cardId = DBHelper.insertLoyaltyCard(
            database, "store", "note", null, null, BigDecimal("0"), null,
            EAN_BARCODE_DATA, null, EAN_BARCODE_TYPE, Color.BLACK, 0, null, 0
        )

        val activityController = createActivityWithLoyaltyCard(cardId.toInt())
        val activity = activityController.get()

        activityController.start()
        activityController.visible()
        activityController.resume()

        shadowOf(getMainLooper()).idle()

        checkAllFields(
            activity, ViewMode.UPDATE_CARD, "store", "note",
            context.getString(R.string.anyDate), context.getString(R.string.never),
            "0", context.getString(R.string.points), EAN_BARCODE_DATA,
            context.getString(R.string.sameAsCardId), EAN_BARCODE_TYPE.prettyName(),
            null, null
        )

        val cardIdField = activity.findViewById<EditText>(R.id.cardIdView)
        cardIdField.setText("123456")

        val tabs = activity.findViewById<TabLayout>(R.id.tabs)
        tabs.getTabAt(2)?.select()
        shadowOf(getMainLooper()).idle()
        val updateBarcodeIdDialog = ShadowDialog.getLatestDialog() as? AlertDialog
        assertNull(updateBarcodeIdDialog)

        shadowOf(getMainLooper()).idle()

        checkAllFields(
            activity, ViewMode.UPDATE_CARD, "store", "note",
            context.getString(R.string.anyDate), context.getString(R.string.never),
            "0", context.getString(R.string.points), "123456",
            context.getString(R.string.sameAsCardId), EAN_BARCODE_TYPE.prettyName(),
            null, null
        )

        database.close()
    }

    @Test
    fun startWithLoyaltyCardDifferentFromCardIDUpdateCardIDUpdate() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val database = TestHelpers.getEmptyDb(context).writableDatabase

        val cardId = DBHelper.insertLoyaltyCard(
            database, "store", "note", null, null, BigDecimal("0"), null,
            EAN_BARCODE_DATA, "123456", EAN_BARCODE_TYPE, Color.BLACK, 0, null, 0
        )

        val activityController = createActivityWithLoyaltyCard(cardId.toInt())
        val activity = activityController.get()

        activityController.start()
        activityController.visible()
        activityController.resume()

        shadowOf(getMainLooper()).idle()

        checkAllFields(
            activity, ViewMode.UPDATE_CARD, "store", "note",
            context.getString(R.string.anyDate), context.getString(R.string.never),
            "0", context.getString(R.string.points), EAN_BARCODE_DATA,
            "123456", EAN_BARCODE_TYPE.prettyName(),
            null, null
        )

        val cardIdField = activity.findViewById<EditText>(R.id.cardIdView)
        cardIdField.setText("654321")

        shadowOf(getMainLooper()).idle()

        val tabs = activity.findViewById<TabLayout>(R.id.tabs)
        tabs.getTabAt(2)?.select()
        shadowOf(getMainLooper()).idle()
        val updateBarcodeIdDialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertNotNull(updateBarcodeIdDialog)
        updateBarcodeIdDialog.getButton(Dialog.BUTTON_POSITIVE).performClick()

        shadowOf(getMainLooper()).idle()

        checkAllFields(
            activity, ViewMode.UPDATE_CARD, "store", "note",
            context.getString(R.string.anyDate), context.getString(R.string.never),
            "0", context.getString(R.string.points), "654321",
            context.getString(R.string.sameAsCardId), EAN_BARCODE_TYPE.prettyName(),
            null, null
        )

        database.close()
    }

    @Test
    fun startWithLoyaltyCardDifferentFromCardIDUpdateCardIDDoNotUpdate() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val database = TestHelpers.getEmptyDb(context).writableDatabase

        val cardId = DBHelper.insertLoyaltyCard(
            database, "store", "note", null, null, BigDecimal("0"), null,
            EAN_BARCODE_DATA, "123456", EAN_BARCODE_TYPE, Color.BLACK, 0, null, 0
        )

        val activityController = createActivityWithLoyaltyCard(cardId.toInt())
        val activity = activityController.get()

        activityController.start()
        activityController.visible()
        activityController.resume()

        shadowOf(getMainLooper()).idle()

        checkAllFields(
            activity, ViewMode.UPDATE_CARD, "store", "note",
            context.getString(R.string.anyDate), context.getString(R.string.never),
            "0", context.getString(R.string.points), EAN_BARCODE_DATA,
            "123456", EAN_BARCODE_TYPE.prettyName(),
            null, null
        )

        val cardIdField = activity.findViewById<EditText>(R.id.cardIdView)
        cardIdField.setText("654321")

        shadowOf(getMainLooper()).idle()

        val tabs = activity.findViewById<TabLayout>(R.id.tabs)
        tabs.getTabAt(2)?.select()
        shadowOf(getMainLooper()).idle()
        val updateBarcodeIdDialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertNotNull(updateBarcodeIdDialog)
        updateBarcodeIdDialog.getButton(Dialog.BUTTON_NEGATIVE).performClick()

        shadowOf(getMainLooper()).idle()

        checkAllFields(
            activity, ViewMode.UPDATE_CARD, "store", "note",
            context.getString(R.string.anyDate), context.getString(R.string.never),
            "0", context.getString(R.string.points), "654321",
            "123456", EAN_BARCODE_TYPE.prettyName(),
            null, null
        )

        database.close()
    }

    @Test
    fun startWithMissingLoyaltyCard() {
        val activityController = createActivityWithLoyaltyCard(1)
        val activity = activityController.get()

        activityController.start()
        activityController.visible()

        shadowOf(getMainLooper()).idle()

        assertTrue(activity.isFinishing)

        activityController.pause()
        activityController.stop()
        activityController.destroy()
    }

    @Test
    @Ignore("Coroutine-based save with SharedFlow events not compatible with Robolectric")
    fun startLoyaltyCardWithoutColorsSave() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val database = TestHelpers.getEmptyDb(context).writableDatabase

        val cardId = DBHelper.insertLoyaltyCard(
            database, "store", "note", null, null, BigDecimal("0"), null,
            BARCODE_DATA, null, BARCODE_TYPE, null, 0, null, 0
        )

        val activityController = createActivityWithLoyaltyCard(cardId.toInt())
        val activity = activityController.get()

        activityController.start()
        activityController.visible()
        activityController.resume()

        shadowOf(getMainLooper()).idle()

        saveLoyaltyCardWithArguments(
            activity, "store", "note",
            activity.applicationContext.getString(R.string.anyDate),
            activity.applicationContext.getString(R.string.never),
            BigDecimal("0"),
            activity.applicationContext.getString(R.string.points),
            BARCODE_DATA,
            activity.applicationContext.getString(R.string.sameAsCardId),
            BARCODE_TYPE.name(),
            false
        )

        database.close()
    }

    @Test
    @Ignore("Coroutine-based save with SharedFlow events not compatible with Robolectric")
    fun startLoyaltyCardWithExplicitNoBarcodeSave() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val database = TestHelpers.getEmptyDb(context).writableDatabase

        val cardId = DBHelper.insertLoyaltyCard(
            database, "store", "note", null, null, BigDecimal("0"), null,
            BARCODE_DATA, null, null, Color.BLACK, 0, null, 0
        )

        val activityController = createActivityWithLoyaltyCard(cardId.toInt())
        val activity = activityController.get()

        activityController.start()
        activityController.visible()
        activityController.resume()

        shadowOf(getMainLooper()).idle()

        saveLoyaltyCardWithArguments(
            activity, "store", "note",
            activity.applicationContext.getString(R.string.anyDate),
            activity.applicationContext.getString(R.string.never),
            BigDecimal("0"),
            activity.applicationContext.getString(R.string.points),
            BARCODE_DATA,
            activity.applicationContext.getString(R.string.sameAsCardId),
            activity.applicationContext.getString(R.string.noBarcode),
            false
        )

        database.close()
    }

    @Test
    @Ignore("Coroutine-based save with SharedFlow events not compatible with Robolectric")
    fun removeBarcodeFromLoyaltyCard() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val database = TestHelpers.getEmptyDb(context).writableDatabase

        val cardId = DBHelper.insertLoyaltyCard(
            database, "store", "note", null, null, BigDecimal("0"), null,
            BARCODE_DATA, null, BARCODE_TYPE, Color.BLACK, 0, null, 0
        )

        val activityController = createActivityWithLoyaltyCard(cardId.toInt())
        val activity = activityController.get()

        activityController.start()
        activityController.visible()
        activityController.resume()

        shadowOf(getMainLooper()).idle()

        checkAllFields(
            activity, ViewMode.UPDATE_CARD, "store", "note",
            context.getString(R.string.anyDate), context.getString(R.string.never),
            "0", context.getString(R.string.points), BARCODE_DATA,
            context.getString(R.string.sameAsCardId), BARCODE_TYPE.prettyName(),
            null, null
        )

        selectBarcodeWithResult(activity, BARCODE_DATA, null, true)
        activityController.resume()

        shadowOf(getMainLooper()).idle()

        checkAllFields(
            activity, ViewMode.UPDATE_CARD, "store", "note",
            context.getString(R.string.anyDate), context.getString(R.string.never),
            "0", context.getString(R.string.points), BARCODE_DATA,
            context.getString(R.string.sameAsCardId), context.getString(R.string.noBarcode),
            null, null
        )
        assertEquals(View.GONE, activity.findViewById<View>(R.id.barcodeLayout).visibility)

        saveLoyaltyCardWithArguments(
            activity, "store", "note",
            context.getString(R.string.anyDate), context.getString(R.string.never),
            BigDecimal("0"), context.getString(R.string.points),
            BARCODE_DATA, context.getString(R.string.sameAsCardId),
            context.getString(R.string.noBarcode),
            false
        )

        database.close()
    }

    @Test
    fun importCard() {
        val date = Date()

        val importUri = Uri.parse(
            "https://catima.app/share#store%3DExample%2BStore%26note%3D%26validfrom%3D${date.time}%26expiry%3D${date.time}%26balance%3D10.00%26balancetype%3DUSD%26cardid%3D123456%26barcodetype%3DAZTEC%26headercolor%3D-416706"
        )

        val intent = Intent().apply {
            data = importUri
        }

        val activityController = Robolectric.buildActivity(LoyaltyCardEditActivity::class.java, intent).create()

        activityController.start()
        activityController.visible()
        activityController.resume()

        val activity = activityController.get()
        val context = activity.applicationContext

        shadowOf(getMainLooper()).idle()

        checkAllFields(
            activity, ViewMode.ADD_CARD, "Example Store", "",
            DateFormat.getDateInstance(DateFormat.LONG).format(date),
            DateFormat.getDateInstance(DateFormat.LONG).format(date),
            "10.00", "$", "123456",
            context.getString(R.string.sameAsCardId), "Aztec",
            null, null
        )
        assertEquals(-416706, (activity.findViewById<View>(R.id.thumbnail).background as ColorDrawable).color)
    }

    @Test
    fun importCardOldFormat() {
        val importUri = Uri.parse(
            "https://brarcher.github.io/loyalty-card-locker/share?store=Example%20Store&note=&cardid=123456&barcodetype=AZTEC&headercolor=-416706&headertextcolor=-1"
        )

        val intent = Intent().apply {
            data = importUri
        }

        val activityController = Robolectric.buildActivity(LoyaltyCardEditActivity::class.java, intent).create()

        activityController.start()
        activityController.visible()
        activityController.resume()

        val activity = activityController.get()
        val context = activity.applicationContext

        shadowOf(getMainLooper()).idle()

        checkAllFields(
            activity, ViewMode.ADD_CARD, "Example Store", "",
            context.getString(R.string.anyDate), context.getString(R.string.never),
            "0", context.getString(R.string.points), "123456",
            context.getString(R.string.sameAsCardId), "Aztec",
            null, null
        )
        assertEquals(-416706, (activity.findViewById<View>(R.id.thumbnail).background as ColorDrawable).color)
    }
}
