package protect.card_locker

import protect.card_locker.R
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.datepicker.CalendarConstraints

import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.datepicker.MaterialPickerOnPositiveButtonClickListener
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import com.yalantis.ucrop.UCrop
import com.yalantis.ucrop.model.AspectRatio
import protect.card_locker.async.TaskHandler
import protect.card_locker.databinding.LayoutChipChoiceBinding
import protect.card_locker.databinding.LoyaltyCardEditActivityBinding
import protect.card_locker.viewmodels.LoyaltyCardEditActivityViewModel
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InvalidObjectException
import java.math.BigDecimal
import java.text.DateFormat
import java.text.ParseException
import java.util.Calendar
import java.util.Collections
import java.util.Currency
import java.util.Date
import java.util.Locale
import java.util.concurrent.Callable
import androidx.core.net.toUri
import androidx.core.os.registerForAllProfilingResults
import androidx.core.view.size
import androidx.core.view.isEmpty
import java.io.File

class LoyaltyCardEditActivity : CatimaAppCompatActivity(), BarcodeImageWriterResultCallback,
    ColorPickerDialogListener {
    lateinit var viewModel: LoyaltyCardEditActivityViewModel
    private lateinit var binding: LoyaltyCardEditActivityBinding

    private val TEMP_CAMERA_IMAGE_NAME =
        LoyaltyCardEditActivity::class.java.simpleName + "_camera_image.jpg"
    private val TEMP_CROP_IMAGE_NAME =
        LoyaltyCardEditActivity::class.java.simpleName + "_crop_image.png"
    private val TEMP_CROP_IMAGE_FORMAT = CompressFormat.PNG

    private lateinit var groupChips: ChipGroup
    private lateinit var validFromField: AutoCompleteTextView
    private lateinit var expiryField: AutoCompleteTextView
    private lateinit var balanceField: EditText
    private lateinit var balanceCurrencyField: AutoCompleteTextView
    private lateinit var cardIdFieldView: TextView
    private lateinit var barcodeIdField: AutoCompleteTextView
    private lateinit var barcodeTypeField: AutoCompleteTextView
    private lateinit var barcodeImage: ImageView
    private lateinit var barcodeImageLayout: View
    private lateinit var barcodeCaptureLayout: View
    private lateinit var cardImageFrontHolder: View
    private lateinit var cardImageBackHolder: View
    private lateinit var cardImageFront: ImageView
    private lateinit var cardImageBack: ImageView
    private lateinit var enterButton: Button
    private lateinit var toolbar: Toolbar
    private lateinit var mDatabase: SQLiteDatabase

    var confirmExitDialog: AlertDialog? = null
    var validBalance: Boolean = true
    var currencies: MutableMap<String?, Currency?> = mutableMapOf()
    var currencySymbols: MutableMap<String?, String?> = mutableMapOf()

    private lateinit var mPhotoTakerLauncher: ActivityResultLauncher<Uri?>
    private lateinit var mPhotoPickerLauncher: ActivityResultLauncher<Intent?>
    private lateinit var mCardIdAndBarCodeEditorLauncher: ActivityResultLauncher<Intent?>

    private lateinit var mCropperLauncher: ActivityResultLauncher<Intent?>
    private lateinit var mCropperOptions: UCrop.Options

    // store system locale for Build.VERSION.SDK_INT < Build.VERSION_CODES.N
    private lateinit var mSystemLocale: Locale

    override fun attachBaseContext(base: Context?) {
        // store system locale
        mSystemLocale = Locale.getDefault()
        super.attachBaseContext(base)
    }

    protected fun setLoyaltyCardStore(store: String) {
        viewModel.loyaltyCard.setStore(store)

        viewModel.hasChanged = true
    }

    protected fun setLoyaltyCardNote(note: String) {
        viewModel.loyaltyCard.setNote(note)
        viewModel.hasChanged = true
    }

    fun setLoyaltyCardValidFrom(validFrom: Date?) {
        viewModel.loyaltyCard.setValidFrom(validFrom)
        viewModel.hasChanged = true
    }

    fun setLoyaltyCardExpiry(expiry: Date?) {
        viewModel.loyaltyCard.setExpiry(expiry)
        viewModel.hasChanged = true
    }

    protected fun setLoyaltyCardBalance(balance: BigDecimal) {
        viewModel.loyaltyCard.setBalance(balance)
        viewModel.hasChanged = true
    }

    protected fun setLoyaltyCardBalanceType(balanceType: Currency?) {
        viewModel.loyaltyCard.setBalanceType(balanceType)
        viewModel.hasChanged = true
    }

    protected fun setLoyaltyCardCardId(cardId: String) {
        viewModel.loyaltyCard.setCardId(cardId)
        generateBarcode()
        viewModel.hasChanged = true
    }

    protected fun setLoyaltyCardBarcodeId(barcodeId: String?) {
        viewModel.loyaltyCard.setBarcodeId(barcodeId)
        generateBarcode()
        viewModel.hasChanged = true
    }

    protected fun setLoyaltyCardBarcodeType(barcodeType: CatimaBarcode?) {
        viewModel.loyaltyCard.setBarcodeType(barcodeType)
        generateBarcode()
        viewModel.hasChanged = true
    }

    protected fun setLoyaltyCardHeaderColor(headerColor: Int?) {
        viewModel.loyaltyCard.setHeaderColor(headerColor)
        viewModel.hasChanged = true
    }

    /* Extract intent fields and return if code should keep running */
    private fun extractIntentFields(intent: Intent): Boolean {
        val b = intent.extras
        viewModel.addGroup = b?.getString(BUNDLE_ADDGROUP)
        viewModel.openSetIconMenu = b?.getBoolean(BUNDLE_OPEN_SET_ICON_MENU, false) ?: false
        viewModel.loyaltyCardId = b?.getInt(BUNDLE_ID) ?: 0
        viewModel.updateLoyaltyCard = b?.getBoolean(BUNDLE_UPDATE, false) ?: false
        viewModel.duplicateFromLoyaltyCardId = b?.getBoolean(BUNDLE_DUPLICATE_ID, false) ?: false
        viewModel.importLoyaltyCardUri = intent.data

        val importLoyaltyCardUri = viewModel.importLoyaltyCardUri
        // If we have to import a loyalty card, do so
        if (viewModel.updateLoyaltyCard || viewModel.duplicateFromLoyaltyCardId) {
            // Retrieve from database
            val loyaltyCard = DBHelper.getLoyaltyCard(this, mDatabase, viewModel.loyaltyCardId)
            if (loyaltyCard == null) {
                Log.w(TAG, "Could not lookup loyalty card " + viewModel.loyaltyCardId)
                Toast.makeText(this, R.string.noCardExistsError, Toast.LENGTH_LONG).show()
                finish()
                return false
            }
            viewModel.loyaltyCard = loyaltyCard
        } else if (importLoyaltyCardUri != null) {
            // Load from URI
            try {
                viewModel.loyaltyCard = ImportURIHelper(this).parse(importLoyaltyCardUri)
            } catch (_: InvalidObjectException) {
                Toast.makeText(this, R.string.failedParsingImportUriError, Toast.LENGTH_LONG).show()
                finish()
                return false
            }
        }

        // If the intent contains any loyalty card fields, override those fields in our current temp card
        if (b != null) {
            val loyaltyCard = viewModel.loyaltyCard
            loyaltyCard.updateFromBundle(b, false)
            viewModel.loyaltyCard = loyaltyCard
        }

        Log.d(
            TAG, ("Edit activity: id=" + viewModel.loyaltyCardId
                    + ", updateLoyaltyCard=" + viewModel.updateLoyaltyCard)
        )

        return true
    }

    public override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
    }

    public override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        viewModel.onRestoring = true
        super.onRestoreInstanceState(savedInstanceState)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[LoyaltyCardEditActivityViewModel::class.java]
        binding = LoyaltyCardEditActivityBinding.inflate(layoutInflater)
        setContentView(binding.getRoot())
        
        // Clean up any leftover temporary files from previous sessions
        cleanUpTempImages()
        groupChips = binding.groupChips
        validFromField = binding.validFromField
        expiryField = binding.expiryField
        balanceField = binding.balanceField
        balanceCurrencyField = binding.balanceCurrencyField
        cardIdFieldView = binding.cardIdView
        barcodeIdField = binding.barcodeIdField
        barcodeTypeField = binding.barcodeTypeField
        barcodeImage = binding.barcode
        barcodeImage.clipToOutline = true
        barcodeImageLayout = binding.barcodeLayout
        barcodeCaptureLayout = binding.barcodeCaptureLayout
        cardImageFrontHolder = binding.frontImageHolder
        cardImageBackHolder = binding.backImageHolder
        cardImageFront = binding.frontImage
        cardImageBack = binding.backImage
        enterButton = binding.enterButton
        Utils.applyWindowInsetsAndFabOffset(binding.getRoot(), binding.fabSave)

        toolbar = binding.toolbar
        setSupportActionBar(toolbar)
        enableToolbarBackButton()

        mDatabase = DBHelper(this).writableDatabase

        viewModel.openSetIconMenu = intent.getBooleanExtra(
            BUNDLE_OPEN_SET_ICON_MENU,
            false
        )

        if (!viewModel.initialized) {
            if (!extractIntentFields(intent)) {
                return
            }
            viewModel.initialized = true
        }

        for (currency in Currency.getAvailableCurrencies()) {
            currencies.put(currency.symbol, currency)
            currencySymbols.put(currency.currencyCode, currency.symbol)
        }


        binding.storeNameEdit.addTextChangedListener(object : SimpleTextWatcher() {
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                val storeName = s.toString().trim()
                setLoyaltyCardStore(storeName)
                generateIcon(storeName)

                if (storeName.isEmpty()) {
                    binding.storeNameEdit.error = getString(R.string.field_must_not_be_empty)
                } else {
                    binding.storeNameEdit.error = null
                }
            }
        })

        binding.noteEdit.addTextChangedListener(object : SimpleTextWatcher() {
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                setLoyaltyCardNote(s.toString())
            }
        })

        addDateFieldTextChangedListener(
            validFromField,
            R.string.anyDate,
            R.string.chooseValidFromDate,
            LoyaltyCardField.validFrom
        )

        addDateFieldTextChangedListener(
            expiryField,
            R.string.never,
            R.string.chooseExpiryDate,
            LoyaltyCardField.expiry
        )

        setMaterialDatePickerResultListener()

        balanceField.setOnFocusChangeListener { v: View?, hasFocus: Boolean ->
            if (!hasFocus && !viewModel.onResuming && !viewModel.onRestoring) {
                if (balanceField.text.toString().isEmpty()) {
                    setLoyaltyCardBalance(BigDecimal.valueOf(0))
                }

                balanceField.setText(
                    Utils.formatBalanceWithoutCurrencySymbol(
                        viewModel.loyaltyCard.balance,
                        viewModel.loyaltyCard.balanceType
                    )
                )
            }
        }

        balanceField.addTextChangedListener(object : SimpleTextWatcher() {
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (viewModel.onResuming || viewModel.onRestoring) return
                try {
                    val balance =
                        Utils.parseBalance(s.toString(), viewModel.loyaltyCard.balanceType)
                    setLoyaltyCardBalance(balance)
                    balanceField.error = null
                    validBalance = true
                } catch (e: ParseException) {
                    e.printStackTrace()
                    balanceField.error = getString(R.string.balanceParsingFailed)
                    validBalance = false
                }
            }
        })

        balanceCurrencyField.addTextChangedListener(object : SimpleTextWatcher() {
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {

                val currency: Currency? = if (s.toString() == getString(R.string.points)) {
                    null
                } else {
                    currencies.get(s.toString())
                }

                setLoyaltyCardBalanceType(currency)

                if (viewModel.loyaltyCard.balance != null && !viewModel.onResuming && !viewModel.onRestoring) {
                    balanceField.setText(
                        Utils.formatBalanceWithoutCurrencySymbol(
                            viewModel.loyaltyCard.balance,
                            currency
                        )
                    )
                }
            }

            override fun afterTextChanged(s: Editable?) {
                val currencyList = ArrayList(currencies.keys)
                Collections.sort<String?>(currencyList, Comparator { o1: String?, o2: String? ->
                    val o1ascii = o1!!.matches("^[^a-zA-Z]*$".toRegex())
                    val o2ascii = o2!!.matches("^[^a-zA-Z]*$".toRegex())

                    if (!o1ascii && o2ascii) {
                        return@Comparator 1
                    } else if (o1ascii && !o2ascii) {
                        return@Comparator -1
                    }
                    o1.compareTo(o2)
                })

                // Sort locale currencies on top
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val locales =
                        applicationContext.resources.configuration.locales

                    for (i in locales.size() - 1 downTo 0) {
                        val locale = locales.get(i)
                        currencyPrioritizeLocaleSymbols(currencyList, locale)
                    }
                } else {
                    currencyPrioritizeLocaleSymbols(currencyList, mSystemLocale)
                }

                currencyList.add(0, getString(R.string.points))
                val currencyAdapter = ArrayAdapter<String?>(
                    this@LoyaltyCardEditActivity,
                    android.R.layout.select_dialog_item,
                    currencyList
                )
                balanceCurrencyField.setAdapter(currencyAdapter)
            }
        })

        cardIdFieldView.addTextChangedListener(object : SimpleTextWatcher() {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (viewModel.initDone && !viewModel.onResuming) {
                    if (viewModel.tempStoredOldBarcodeValue == null) {
                        // We changed the card ID, save the current barcode ID in a temp
                        // variable and make sure to ask the user later if they also want to
                        // update the barcode ID
                        if (viewModel.loyaltyCard.barcodeId != null) {
                            // If it is not set to "same as Card ID", save as tempStoredOldBarcodeValue
                            viewModel.tempStoredOldBarcodeValue = barcodeIdField.text.toString()
                        }
                    }
                }
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                setLoyaltyCardCardId(s.toString())

                if (s.isEmpty()) {
                    cardIdFieldView.error = getString(R.string.field_must_not_be_empty)
                } else {
                    cardIdFieldView.error = null
                }
            }
        })

        barcodeIdField.addTextChangedListener(object : SimpleTextWatcher() {
            var lastValue: CharSequence? = null

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                lastValue = s
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (s.toString() == getString(R.string.sameAsCardId)) {
                    // If the user manually changes the barcode again make sure we disable the
                    // request to update it to match the card id (if changed)
                    viewModel.tempStoredOldBarcodeValue = null

                    setLoyaltyCardBarcodeId(null)
                } else if (s.toString() == getString(R.string.setBarcodeId)) {
                    if (lastValue.toString() != getString(R.string.setBarcodeId)) {
                        barcodeIdField.setText(lastValue)
                    }

                    val builder: AlertDialog.Builder =
                        MaterialAlertDialogBuilder(this@LoyaltyCardEditActivity)
                    builder.setTitle(R.string.setBarcodeId)
                    val input = EditText(this@LoyaltyCardEditActivity)
                    input.inputType = InputType.TYPE_CLASS_TEXT

                    val container = FrameLayout(this@LoyaltyCardEditActivity)
                    val params = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    val contentPadding =
                        getResources().getDimensionPixelSize(R.dimen.alert_dialog_content_padding)
                    params.leftMargin = contentPadding
                    params.topMargin = contentPadding / 2
                    params.rightMargin = contentPadding

                    input.layoutParams = params
                    container.addView(input)
                    if (viewModel.loyaltyCard.barcodeId != null) {
                        input.setText(viewModel.loyaltyCard.barcodeId)
                    }
                    builder.setView(container)

                    builder.setPositiveButton(
                        getString(R.string.ok)
                    ) { _, _ ->
                        // If the user manually changes the barcode again make sure we disable the
                        // request to update it to match the card id (if changed)
                        viewModel.tempStoredOldBarcodeValue = null
                        barcodeIdField.setText(input.text)
                    }
                    builder.setNegativeButton(
                        getString(R.string.cancel)
                    ) { dialog, _ -> dialog.cancel() }
                    val dialog = builder.create()
                    dialog.show()
                    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
                    input.requestFocus()
                } else {
                    setLoyaltyCardBarcodeId(s.toString())
                }
            }

            override fun afterTextChanged(s: Editable?) {
                val barcodeIdList = mutableListOf<String?>()
                barcodeIdList.add(0, getString(R.string.sameAsCardId))
                barcodeIdList.add(1, getString(R.string.setBarcodeId))
                val barcodeIdAdapter = ArrayAdapter<String?>(
                    this@LoyaltyCardEditActivity,
                    android.R.layout.select_dialog_item,
                    barcodeIdList
                )
                barcodeIdField.setAdapter(barcodeIdAdapter)
            }
        })

        barcodeTypeField.addTextChangedListener(object : SimpleTextWatcher() {
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (s.isNotEmpty()) {
                    if (s.toString() == getString(R.string.noBarcode)) {
                        setLoyaltyCardBarcodeType(null)
                    } else {
                        try {
                            val barcodeFormat = CatimaBarcode.fromPrettyName(s.toString())

                            setLoyaltyCardBarcodeType(barcodeFormat)

                            if (!barcodeFormat.isSupported) {
                                Toast.makeText(
                                    this@LoyaltyCardEditActivity,
                                    getString(R.string.unsupportedBarcodeType),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        } catch (_: IllegalArgumentException) {
                        }
                    }
                }
            }

            override fun afterTextChanged(s: Editable?) {
                val barcodeList = ArrayList<String?>(CatimaBarcode.barcodePrettyNames)
                barcodeList.add(0, getString(R.string.noBarcode))
                val barcodeAdapter = ArrayAdapter<String?>(
                    this@LoyaltyCardEditActivity,
                    android.R.layout.select_dialog_item,
                    barcodeList
                )
                barcodeTypeField.setAdapter(barcodeAdapter)
            }
        })

        binding.tabs.addOnTabSelectedListener(object : OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewModel.tabIndex = tab.position
                showPart(tab.text.toString())
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
            }

            override fun onTabReselected(tab: TabLayout.Tab) {
                viewModel.tabIndex = tab.position
                showPart(tab.text.toString())
            }
        })

        selectTab(viewModel.tabIndex)

        mPhotoTakerLauncher = registerForActivityResult(
            TakePicture()
        ) { result: Boolean? ->
            if (result == true) {
                startCropper("$cacheDir/$TEMP_CAMERA_IMAGE_NAME")
            }
        }

        // android 11: wanted to swap it to ActivityResultContracts.GetContent but then it shows a file browsers that shows image mime types, offering gallery in the file browser
        mPhotoPickerLauncher = registerForActivityResult(
            StartActivityForResult()
        ) { result: ActivityResult ->
            if (result.resultCode == RESULT_OK) {
                val intent = result.data ?: run {
                    Log.d("photo picker", "photo picker returned without an intent")
                    return@registerForActivityResult
                }
                val uri = intent.data
                if (uri == null) {
                    Log.d("photo picker", "photo picker returned intent without a uri")
                    return@registerForActivityResult
                }
                startCropperUri(uri)
            }
        }

        mCardIdAndBarCodeEditorLauncher = registerForActivityResult(
            StartActivityForResult()
        ) { result: ActivityResult ->
            if (result.resultCode == RESULT_OK) {
                val resultIntent = result.data
                if (resultIntent == null) {
                    Log.d(TAG, "barcode and card id editor picker returned without an intent")
                    return@registerForActivityResult
                }

                val resultIntentBundle = resultIntent.extras
                if (resultIntentBundle == null) {
                    Log.d(TAG, "barcode and card id editor picker returned without a bundle")
                    return@registerForActivityResult
                }

                val loyaltyCard = viewModel.loyaltyCard
                loyaltyCard.updateFromBundle(resultIntentBundle, false)
                viewModel.loyaltyCard = loyaltyCard
                generateBarcode()
                viewModel.hasChanged = true
            }
        }

        mCropperLauncher = registerForActivityResult(
            StartActivityForResult()
        ) { result: ActivityResult ->
            val intent = result.data
            if (intent == null) {
                Log.d("cropper", "ucrop returned a null intent")
                return@registerForActivityResult
            }
            if (result.resultCode == RESULT_OK) {
                val debugUri = UCrop.getOutput(intent)
                if (debugUri == null) {
                    throw RuntimeException("ucrop returned success but not destination uri!")
                }
                Log.d("cropper", "ucrop produced image at $debugUri")
                val bitmap =
                    BitmapFactory.decodeFile("$cacheDir/$TEMP_CROP_IMAGE_NAME")

                if (bitmap != null) {
                    if (requestedFrontImage()) {
                        setCardImage(
                            ImageLocationType.front,
                            cardImageFront,
                            Utils.resizeBitmap(bitmap, Utils.BITMAP_SIZE_BIG.toDouble()),
                            true
                        )
                    } else if (requestedBackImage()) {
                        setCardImage(
                            ImageLocationType.back,
                            cardImageBack,
                            Utils.resizeBitmap(bitmap, Utils.BITMAP_SIZE_BIG.toDouble()),
                            true
                        )
                    } else if (requestedIcon()) {
                        setThumbnailImage(
                            Utils.resizeBitmap(
                                bitmap,
                                Utils.BITMAP_SIZE_SMALL.toDouble()
                            )
                        )
                    } else {
                        Toast.makeText(
                            this,
                            R.string.generic_error_please_retry,
                            Toast.LENGTH_LONG
                        ).show()
                        return@registerForActivityResult
                    }
                    Log.d("cropper", "requestedImageType: " + viewModel.requestedImageType)
                    viewModel.hasChanged = true
                    
                    // Clean up temporary files after successful image processing
                    cleanUpTempImages()
                } else {
                    Toast.makeText(
                        this@LoyaltyCardEditActivity,
                        R.string.errorReadingImage,
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else if (result.resultCode == UCrop.RESULT_ERROR) {
                val e = UCrop.getError(intent)
                if (e == null) {
                    throw RuntimeException("ucrop returned error state but not an error!")
                }
                Log.e("cropper error", e.toString())
            }
        }

        mCropperOptions = UCrop.Options()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                askBeforeQuitIfChanged()
            }
        })
    }

    private fun cleanUpTempImages() {
        try {
            val cameraTempFile = File(cacheDir, TEMP_CAMERA_IMAGE_NAME)
            if (cameraTempFile.exists()) {
                cameraTempFile.delete()
            }

            val cropTempFile = File(cacheDir, TEMP_CROP_IMAGE_NAME)
            if (cropTempFile.exists()) {
                cropTempFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up temporary image files", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanUpTempImages()
    }

    private fun selectTab(index: Int) {
        binding.tabs.selectTab(binding.tabs.getTabAt(index))
        viewModel.tabIndex = index
    }

    // ucrop 2.2.6 initial aspect ratio is glitched when 0x0 is used as the initial ratio option
    // https://github.com/Yalantis/uCrop/blob/281c8e6438d81f464d836fc6b500517144af264a/ucrop/src/main/java/com/yalantis/ucrop/UCropActivity.java#L264
    // so source width height has to be provided for now, depending on whether future versions of ucrop will support 0x0 as the default option
    private fun setCropperOptions(
        cardShapeDefault: Boolean,
        sourceWidth: Float,
        sourceHeight: Float
    ) {
        mCropperOptions.setCompressionFormat(TEMP_CROP_IMAGE_FORMAT)
        mCropperOptions.setFreeStyleCropEnabled(true)
        mCropperOptions.setHideBottomControls(false)
        // default aspect ratio workaround
        var selectedByDefault = 1
        if (cardShapeDefault) {
            selectedByDefault = 2
        }
        mCropperOptions.setAspectRatioOptions(
            selectedByDefault,
            AspectRatio(null, 1f, 1f),
            AspectRatio(
                getResources().getString(com.yalantis.ucrop.R.string.ucrop_label_original)
                    .uppercase(
                        Locale.getDefault()
                    ), sourceWidth, sourceHeight
            ),
            AspectRatio(
                getResources().getString(R.string.card)
                    .uppercase(Locale.getDefault()), 85.6f, 53.98f
            )
        )

        // Fix theming
        val colorPrimary = MaterialColors.getColor(
            this,
            androidx.appcompat.R.attr.colorPrimary,
            ContextCompat.getColor(this, R.color.md_theme_light_primary)
        )
        val colorOnPrimary = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorOnPrimary,
            ContextCompat.getColor(this, R.color.md_theme_light_onPrimary)
        )
        val colorSurface = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorSurface,
            ContextCompat.getColor(this, R.color.md_theme_light_surface)
        )
        val colorOnSurface = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorOnSurface,
            ContextCompat.getColor(this, R.color.md_theme_light_onSurface)
        )
        val colorBackground = MaterialColors.getColor(
            this,
            android.R.attr.colorBackground,
            ContextCompat.getColor(this, R.color.md_theme_light_onSurface)
        )
        mCropperOptions.setToolbarColor(colorSurface)
        mCropperOptions.setStatusBarColor(colorSurface)
        mCropperOptions.setToolbarWidgetColor(colorOnSurface)
        mCropperOptions.setRootViewBackgroundColor(colorBackground)
        // set tool tip to be the darker of primary color
        if (Utils.isDarkModeEnabled(this)) {
            mCropperOptions.setActiveControlsWidgetColor(colorOnPrimary)
        } else {
            mCropperOptions.setActiveControlsWidgetColor(colorPrimary)
        }
    }

    private fun getCurrentImageOperation(): ImageOperation? = viewModel.currentImageOperation

    private fun requestedFrontImage(): Boolean = getCurrentImageOperation() == ImageOperation.FRONT

    private fun requestedBackImage(): Boolean = getCurrentImageOperation() == ImageOperation.BACK

    private fun requestedIcon(): Boolean = getCurrentImageOperation() == ImageOperation.ICON

    @SuppressLint("DefaultLocale")
    override fun onResume() {
        super.onResume()

        Log.i(TAG, "To view card: " + viewModel.loyaltyCardId)

        viewModel.onResuming = true

        if (viewModel.updateLoyaltyCard) {
            setTitle(R.string.editCardTitle)
        } else {
            setTitle(R.string.addCardTitle)
        }

        val hadChanges = viewModel.hasChanged

        binding.storeNameEdit.setText(viewModel.loyaltyCard.store)
        binding.noteEdit.setText(viewModel.loyaltyCard.note)
        formatDateField(this, validFromField, viewModel.loyaltyCard.validFrom)
        formatDateField(this, expiryField, viewModel.loyaltyCard.expiry)
        cardIdFieldView.text = viewModel.loyaltyCard.cardId
        val barcodeId = viewModel.loyaltyCard.barcodeId
        barcodeIdField.setText(
            barcodeId.takeIf { !it.isNullOrEmpty() } ?: getString(R.string.sameAsCardId)
        )
        val barcodeType = viewModel.loyaltyCard.barcodeType
        barcodeTypeField.setText(barcodeType?.prettyName() ?: getString(R.string.noBarcode))

        // We set the balance here (with onResuming/onRestoring == true) to prevent formatBalanceCurrencyField() from setting it (via onTextChanged),
        // which can cause issues when switching locale because it parses the balance and e.g. the decimal separator may have changed.
        formatBalanceCurrencyField(viewModel.loyaltyCard.balanceType)
        val balance = viewModel.loyaltyCard.balance ?: BigDecimal("0")
        setLoyaltyCardBalance(balance)
        balanceField.setText(
            Utils.formatBalanceWithoutCurrencySymbol(
                viewModel.loyaltyCard.balance,
                viewModel.loyaltyCard.balanceType
            )
        )
        validBalance = true
        Log.d(TAG, "Setting balance to $balance")

        if (groupChips.isEmpty()) {
            val existingGroups = DBHelper.getGroups(mDatabase)

            val loyaltyCardGroups =
                DBHelper.getLoyaltyCardGroups(mDatabase, viewModel.loyaltyCardId)

            if (existingGroups.isEmpty()) {
                groupChips.visibility = View.GONE
            } else {
                groupChips.visibility = View.VISIBLE
            }

            for (group in DBHelper.getGroups(mDatabase)) {
                val chipChoiceBinding = LayoutChipChoiceBinding
                    .inflate(LayoutInflater.from(groupChips.context), groupChips, false)
                val chip = chipChoiceBinding.getRoot()
                chip.text = group._id
                chip.tag = group

                if (group._id == viewModel.addGroup) {
                    chip.isChecked = true
                } else {
                    chip.isChecked = false
                    for (loyaltyCardGroup in loyaltyCardGroups) {
                        if (loyaltyCardGroup._id == group._id) {
                            chip.isChecked = true
                            break
                        }
                    }
                }

                chip.setOnTouchListener { v: View?, event: MotionEvent? ->
                    viewModel.hasChanged = true
                    false
                }

                groupChips.addView(chip)
            }
        }

        if (viewModel.loyaltyCard.headerColor == null) {
            // If name is set, pick colour relevant for name. Otherwise pick randomly
            setLoyaltyCardHeaderColor(
                if (viewModel.loyaltyCard.store.isEmpty()) Utils.getRandomHeaderColor(
                    this
                ) else Utils.getHeaderColor(this, viewModel.loyaltyCard)
            )
        }

        setThumbnailImage(viewModel.loyaltyCard.getImageThumbnail(this))
        setCardImage(
            ImageLocationType.front,
            cardImageFront,
            viewModel.loyaltyCard.getImageFront(this),
            true
        )
        setCardImage(
            ImageLocationType.back,
            cardImageBack,
            viewModel.loyaltyCard.getImageBack(this),
            true
        )

        // Initialization has finished
        if (!viewModel.initDone) {
            viewModel.initDone = true
            viewModel.hasChanged = hadChanges
        }

        generateBarcode()

        enterButton.setOnClickListener(EditCardIdAndBarcode())
        barcodeImage.setOnClickListener(EditCardIdAndBarcode())

        cardImageFrontHolder.setOnClickListener(ChooseCardImage())
        cardImageBackHolder.setOnClickListener(ChooseCardImage())

        val saveButton = binding.fabSave
        saveButton.setOnClickListener { v: View? -> doSave() }
        saveButton.bringToFront()

        generateIcon(binding.storeNameEdit.text.toString().trim())

        val headerColor = viewModel.loyaltyCard.headerColor
        headerColor?.let { color ->
            binding.thumbnail.setOnClickListener(ChooseCardImage())
            binding.thumbnailEditIcon.setBackgroundColor(if (Utils.needsDarkForeground(color)) Color.BLACK else Color.WHITE)
            binding.thumbnailEditIcon.setColorFilter(if (Utils.needsDarkForeground(color)) Color.WHITE else Color.BLACK)
        }

        viewModel.onResuming = false
        viewModel.onRestoring = false

        // Fake click on the edit icon to cause the set icon option to pop up if the icon was
        // long-pressed in the view activity
        if (viewModel.openSetIconMenu) {
            viewModel.openSetIconMenu = false
            binding.thumbnail.callOnClick()
        }
    }

    protected fun setThumbnailImage(bitmap: Bitmap?) {
        setCardImage(ImageLocationType.icon, binding.thumbnail, bitmap, false)

        if (bitmap != null) {
            val headerColor = Utils.getHeaderColorFromImage(
                bitmap,
                Utils.getHeaderColor(this, viewModel.loyaltyCard)
            )

            setLoyaltyCardHeaderColor(headerColor)

            binding.thumbnail.setBackgroundColor(if (Utils.needsDarkForeground(headerColor)) Color.BLACK else Color.WHITE)

            binding.thumbnailEditIcon.setBackgroundColor(if (Utils.needsDarkForeground(headerColor)) Color.BLACK else Color.WHITE)
            binding.thumbnailEditIcon.setColorFilter(if (Utils.needsDarkForeground(headerColor)) Color.WHITE else Color.BLACK)

        } else {
            generateIcon(binding.storeNameEdit.text.toString().trim())

            val headerColor = viewModel.loyaltyCard.headerColor

            if (headerColor != null) {
                binding.thumbnailEditIcon.setBackgroundColor(
                    if (Utils.needsDarkForeground(
                            headerColor
                        )
                    ) Color.BLACK else Color.WHITE
                )
                binding.thumbnailEditIcon.setColorFilter(if (Utils.needsDarkForeground(headerColor)) Color.WHITE else Color.BLACK)
            }
        }
    }

    fun setCardImage(
        imageLocationType: ImageLocationType?,
        imageView: ImageView,
        bitmap: Bitmap?,
        applyFallback: Boolean
    ) {
        when (imageLocationType) {
            ImageLocationType.icon -> {
                viewModel.loyaltyCard.setImageThumbnail(bitmap, null)
            }

            ImageLocationType.front -> {
                viewModel.loyaltyCard.setImageFront(bitmap, null)
            }

            ImageLocationType.back -> {
                viewModel.loyaltyCard.setImageBack(bitmap, null)
            }

            else -> {
                throw IllegalArgumentException("Unknown image type")
            }
        }

        bitmap?.let { imageView.setImageBitmap(it) } ?: run {
            if (applyFallback) {
                imageView.setImageResource(R.drawable.ic_camera_white)
            }
        }
    }

    protected fun addDateFieldTextChangedListener(
        dateField: AutoCompleteTextView,
        @StringRes defaultOptionStringId: Int,
        @StringRes chooseDateOptionStringId: Int,
        loyaltyCardField: LoyaltyCardField
    ) {
        dateField.addTextChangedListener(object : SimpleTextWatcher() {
            var lastValue: CharSequence? = null

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                lastValue = s
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (s.toString() == getString(defaultOptionStringId)) {
                    dateField.tag = null
                    when (loyaltyCardField) {
                        LoyaltyCardField.validFrom -> setLoyaltyCardValidFrom(null)
                        LoyaltyCardField.expiry -> setLoyaltyCardExpiry(null)
                        else -> throw AssertionError("Unexpected field: $loyaltyCardField")
                    }
                } else if (s.toString() == getString(chooseDateOptionStringId)) {
                    if (lastValue.toString() != getString(chooseDateOptionStringId)) {
                        dateField.setText(lastValue)
                    }
                    showDatePicker(
                        loyaltyCardField,
                        dateField.tag as Date?,  // if the expiry date is being set, set date picker's minDate to the 'valid from' date
                        if (loyaltyCardField == LoyaltyCardField.expiry) validFromField.tag as Date? else null,  // if the 'valid from' date is being set, set date picker's maxDate to the expiry date
                        if (loyaltyCardField == LoyaltyCardField.validFrom) expiryField.tag as Date? else null
                    )
                }
            }

            override fun afterTextChanged(s: Editable?) {
                val dropdownOptions = mutableListOf<String?>()
                dropdownOptions.add(0, getString(defaultOptionStringId))
                dropdownOptions.add(1, getString(chooseDateOptionStringId))
                val dropdownOptionsAdapter = ArrayAdapter<String?>(
                    this@LoyaltyCardEditActivity,
                    android.R.layout.select_dialog_item,
                    dropdownOptions
                )
                dateField.setAdapter(dropdownOptionsAdapter)
            }
        })
    }

    private fun formatBalanceCurrencyField(balanceType: Currency?) {
        if (balanceType == null) {
            balanceCurrencyField.setText(getString(R.string.points))
        } else {
            balanceCurrencyField.setText(getCurrencySymbol(balanceType))
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        onMockedRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun performActionWithPermissionCheck(requestCode: Int, granted: Boolean): Int? {
        if (!granted) {
            return when (requestCode) {
                PERMISSION_REQUEST_CAMERA_IMAGE_FRONT,
                PERMISSION_REQUEST_CAMERA_IMAGE_BACK,
                PERMISSION_REQUEST_CAMERA_IMAGE_ICON -> R.string.cameraPermissionRequired
                PERMISSION_REQUEST_STORAGE_IMAGE_FRONT,
                PERMISSION_REQUEST_STORAGE_IMAGE_BACK,
                PERMISSION_REQUEST_STORAGE_IMAGE_ICON -> R.string.storageReadPermissionRequired
                else -> R.string.generic_error_please_retry
            }
        }

        try {
            when (requestCode) {
                PERMISSION_REQUEST_CAMERA_IMAGE_FRONT,
                PERMISSION_REQUEST_CAMERA_IMAGE_BACK,
                PERMISSION_REQUEST_CAMERA_IMAGE_ICON -> {
                    val operation = ImageOperation.fromCameraPermission(requestCode)
                    takePhotoForCard(operation.cameraTypeConstant)
                }

                PERMISSION_REQUEST_STORAGE_IMAGE_FRONT,
                PERMISSION_REQUEST_STORAGE_IMAGE_BACK,
                PERMISSION_REQUEST_STORAGE_IMAGE_ICON -> {
                    val operation = ImageOperation.fromStoragePermission(requestCode)
                    selectImageFromGallery(operation.fileTypeConstant)
                }
            }
        } catch (e: IllegalArgumentException) {
            return R.string.generic_error_please_retry
        }
        return null
    }


    override fun onMockedRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        val granted =
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        val failureReason = performActionWithPermissionCheck(requestCode, granted)

        failureReason?.let { resourceId ->
            Toast.makeText(this, resourceId, Toast.LENGTH_LONG).show()
        }
    }

    private fun askBarcodeChange(callback: Runnable?) {
        if (viewModel.tempStoredOldBarcodeValue == cardIdFieldView.text.toString()) {
            // They are the same, don't ask
            barcodeIdField.setText(R.string.sameAsCardId)
            viewModel.tempStoredOldBarcodeValue = null

            callback?.run()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.updateBarcodeQuestionTitle)
            .setMessage(R.string.updateBarcodeQuestionText)
            .setPositiveButton(
                R.string.yes
            ) { dialog, _ ->
                barcodeIdField.setText(R.string.sameAsCardId)
                dialog.dismiss()
            }
            .setNegativeButton(
                R.string.no
            ) { dialog, _ -> dialog.dismiss() }
            .setOnDismissListener { _ ->
                viewModel.tempStoredOldBarcodeValue?.let { value ->
                    barcodeIdField.setText(value)
                    viewModel.tempStoredOldBarcodeValue = null
                }
                callback?.run()
            }
            .show()
    }

    private fun askBeforeQuitIfChanged() {
        if (!viewModel.hasChanged) {
            viewModel.tempStoredOldBarcodeValue?.let {
                askBarcodeChange { this.askBeforeQuitIfChanged() }
                return
            }

            finish()
            return
        }

        if (confirmExitDialog == null) {
            val builder: AlertDialog.Builder = MaterialAlertDialogBuilder(this)
            builder.setTitle(R.string.leaveWithoutSaveTitle)
            builder.setMessage(R.string.leaveWithoutSaveConfirmation)
            builder.setPositiveButton(
                R.string.confirm
            ) { dialog,_->
                finish()
                dialog.dismiss()
            }
            builder.setNegativeButton(
                R.string.cancel
            ) { dialog, _ -> dialog.dismiss() }
            confirmExitDialog = builder.create()
        }
        confirmExitDialog?.show()
    }


    private fun takePhotoForCard(type: Int) {
        val photoURI = FileProvider.getUriForFile(
            this@LoyaltyCardEditActivity,
            BuildConfig.APPLICATION_ID,
            Utils.createTempFile(this, TEMP_CAMERA_IMAGE_NAME)
        )
        viewModel.requestedImageType = type
        viewModel.currentImageOperation = ImageOperation.fromImageType(type)

        try {
            mPhotoTakerLauncher.launch(photoURI)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                applicationContext,
                R.string.cameraPermissionDeniedTitle,
                Toast.LENGTH_LONG
            ).show()
            Log.e(TAG, "No activity found to handle intent", e)
        }
    }

    private fun selectImageFromGallery(type: Int) {
        viewModel.requestedImageType = type
        viewModel.currentImageOperation = ImageOperation.fromImageType(type)

        val photoPickerIntent = Intent(Intent.ACTION_PICK)
        photoPickerIntent.type = "image/*"
        val contentIntent = Intent(Intent.ACTION_GET_CONTENT)
        contentIntent.type = "image/*"
        val chooserIntent = Intent.createChooser(
            photoPickerIntent,
            getString(R.string.addFromImage)
        )
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(contentIntent))

        try {
            mPhotoPickerLauncher.launch(chooserIntent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                applicationContext,
                R.string.failedLaunchingPhotoPicker,
                Toast.LENGTH_LONG
            ).show()
            Log.e(TAG, "No activity found to handle intent", e)
        }
    }

    override fun onBarcodeImageWriterResult(success: Boolean) {
        if (!success) {
            barcodeImageLayout.visibility = View.GONE
            Toast.makeText(
                this@LoyaltyCardEditActivity,
                getString(R.string.wrongValueForBarcodeType),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    internal inner class EditCardIdAndBarcode : View.OnClickListener {
        override fun onClick(v: View?) {
            val i = Intent(applicationContext, ScanActivity::class.java)
            val b = Bundle()
            b.putString(
                LoyaltyCard.BUNDLE_LOYALTY_CARD_CARD_ID,
                cardIdFieldView.text.toString()
            )
            i.putExtras(b)
            mCardIdAndBarCodeEditorLauncher.launch(i)
        }
    }

    internal inner class ChooseCardImage : View.OnClickListener {
        @Throws(NoSuchElementException::class)
        override fun onClick(v: View) {
            val operation = try {
                ImageOperation.fromResourceId(v.id)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid IMAGE ID ${v.id}", e)
            }

            val currentImage = when (operation.locationType) {
                ImageLocationType.front -> viewModel.loyaltyCard.getImageFront(this@LoyaltyCardEditActivity)
                ImageLocationType.back -> viewModel.loyaltyCard.getImageBack(this@LoyaltyCardEditActivity)
                ImageLocationType.icon -> viewModel.loyaltyCard.getImageThumbnail(this@LoyaltyCardEditActivity)
            }

            val targetView = when (operation) {
                ImageOperation.FRONT -> cardImageFront
                ImageOperation.BACK -> cardImageBack
                ImageOperation.ICON -> binding.thumbnail
            }

            val cardOptions = linkedMapOf<String, Callable<Void>>()
            
            if (currentImage != null && operation != ImageOperation.ICON) {
                cardOptions[getString(R.string.removeImage)] = Callable {
                    setCardImage(operation.locationType, targetView, null, true)
                    null
                }
            }

            if (operation == ImageOperation.ICON) {
                cardOptions[getString(R.string.selectColor)] = Callable {
                    val dialogBuilder = ColorPickerDialog.newBuilder()
                    viewModel.loyaltyCard.headerColor?.let { dialogBuilder.setColor(it) }
                    val dialog = dialogBuilder.create()
                    dialog.show(supportFragmentManager, "color-picker-dialog")
                    null
                }
            }

            cardOptions[getString(R.string.takePhoto)] = Callable {
                PermissionUtils.requestCameraPermission(
                    this@LoyaltyCardEditActivity,
                    operation.cameraPermissionCode
                )
                null
            }

            cardOptions[getString(R.string.addFromImage)] = Callable {
                PermissionUtils.requestStorageReadPermission(
                    this@LoyaltyCardEditActivity,
                    operation.storagePermissionCode
                )
                null
            }

            if (operation == ImageOperation.ICON) {
                val imageFront = viewModel.loyaltyCard.getImageFront(this@LoyaltyCardEditActivity)
                if (imageFront != null) {
                    cardOptions[getString(R.string.useFrontImage)] = Callable {
                        setThumbnailImage(
                            Utils.resizeBitmap(imageFront, Utils.BITMAP_SIZE_SMALL.toDouble())
                        )
                        null
                    }
                }

                val imageBack = viewModel.loyaltyCard.getImageBack(this@LoyaltyCardEditActivity)
                if (imageBack != null) {
                    cardOptions[getString(R.string.useBackImage)] = Callable {
                        setThumbnailImage(
                            Utils.resizeBitmap(imageBack, Utils.BITMAP_SIZE_SMALL.toDouble())
                        )
                        null
                    }
                }
            }

            MaterialAlertDialogBuilder(this@LoyaltyCardEditActivity)
                .setTitle(getString(operation.titleResource))
                .setItems(cardOptions.keys.toTypedArray<CharSequence?>()) { _, which ->
                    val callables = cardOptions.values.iterator()
                    var callable = callables.next()
                    for (i in 0..<which) {
                        callable = callables.next()
                    }
                    try {
                        callable.call()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        throw NoSuchElementException(e.message)
                    }
                }
                .show()
        }
    }

    // ColorPickerDialogListener callback used by the ColorPickerDialog created in ChooseCardImage to set the thumbnail color
    // We don't need to set or check the dialogId since it's only used for that single dialog
    override fun onColorSelected(dialogId: Int, color: Int) {
        // Save new colour
        setLoyaltyCardHeaderColor(color)
        // Unset image if set
        setThumbnailImage(null)
    }

    // ColorPickerDialogListener callback
    override fun onDialogDismissed(dialogId: Int) {
        // Nothing to do, no change made
    }

    private fun showDatePicker(
        loyaltyCardField: LoyaltyCardField,
        selectedDate: Date?,
        minDate: Date?,
        maxDate: Date?
    ) {
        // Create a new instance of MaterialDatePicker and return it
        val startDate = minDate?.time ?: this.defaultMinDateOfDatePicker
        val endDate = maxDate?.time ?: this.defaultMaxDateOfDatePicker
        val dateValidator = when (loyaltyCardField) {
            LoyaltyCardField.validFrom -> DateValidatorPointBackward.before(endDate)
            LoyaltyCardField.expiry -> DateValidatorPointForward.from(startDate)
            else -> throw AssertionError("Unexpected field: $loyaltyCardField")
        }

        val calendarConstraints = CalendarConstraints.Builder()
            .setValidator(dateValidator)
            .setStart(startDate)
            .setEnd(endDate)
            .build()

        // Use the selected date as the default date in the picker
        val calendar = Calendar.getInstance()
        selectedDate?.let { calendar.setTime(it) }

        val materialDatePicker = MaterialDatePicker.Builder.datePicker()
            .setSelection(calendar.timeInMillis)
            .setCalendarConstraints(calendarConstraints)
            .build()

        // Required to handle configuration changes
        // See https://github.com/material-components/material-components-android/issues/1688
        viewModel.tempLoyaltyCardField = loyaltyCardField
        supportFragmentManager.addFragmentOnAttachListener { fragmentManager: FragmentManager?, fragment: Fragment? ->
            if (fragment is MaterialDatePicker<*> && fragment.tag == PICK_DATE_REQUEST_KEY) {
                (fragment as MaterialDatePicker<Long>).addOnPositiveButtonClickListener(
                    MaterialPickerOnPositiveButtonClickListener { selection: Long ->
                        val args = Bundle()
                        args.putLong(NEWLY_PICKED_DATE_ARGUMENT_KEY, selection)
                        supportFragmentManager.setFragmentResult(PICK_DATE_REQUEST_KEY, args)
                    })
            }
        }

        materialDatePicker.show(supportFragmentManager, PICK_DATE_REQUEST_KEY)
    }

    // Required to handle configuration changes
    // See https://github.com/material-components/material-components-android/issues/1688
    private fun setMaterialDatePickerResultListener() {
        val fragment =
            supportFragmentManager.findFragmentByTag(PICK_DATE_REQUEST_KEY) as MaterialDatePicker<Long>?
        fragment?.addOnPositiveButtonClickListener(MaterialPickerOnPositiveButtonClickListener { selection: Long ->
            val args = Bundle()
            args.putLong(NEWLY_PICKED_DATE_ARGUMENT_KEY, selection)
            supportFragmentManager.setFragmentResult(PICK_DATE_REQUEST_KEY, args)
        })

        supportFragmentManager.setFragmentResultListener(
            PICK_DATE_REQUEST_KEY,
            this
        ) { requestKey: String?, result: Bundle? ->
            val selection = result!!.getLong(
                NEWLY_PICKED_DATE_ARGUMENT_KEY
            )
            val newDate = Date(selection)

            val tempLoyaltyCardField = viewModel.tempLoyaltyCardField
            if (tempLoyaltyCardField == null) {
                throw AssertionError("tempLoyaltyCardField is null unexpectedly!")
            }
            when (tempLoyaltyCardField) {
                LoyaltyCardField.validFrom -> {
                    formatDateField(
                        this,
                        validFromField,
                        newDate
                    )
                    setLoyaltyCardValidFrom(newDate)
                }

                LoyaltyCardField.expiry -> {
                    formatDateField(
                        this@LoyaltyCardEditActivity,
                        expiryField,
                        newDate
                    )
                    setLoyaltyCardExpiry(newDate)
                }

                else -> throw AssertionError("Unexpected field: $tempLoyaltyCardField")
            }
        }
    }

    private val defaultMinDateOfDatePicker: Long
        get() {
            val minDateCalendar = Calendar.getInstance()
            minDateCalendar.set(1970, 0, 1)
            return minDateCalendar.timeInMillis
        }

    private val defaultMaxDateOfDatePicker: Long
        get() {
            val maxDateCalendar = Calendar.getInstance()
            maxDateCalendar.set(2100, 11, 31)
            return maxDateCalendar.timeInMillis
        }

    private fun doSave() {
        if (isFinishing) {
            // If we are done saving, ignore any queued up save button presses
            return
        }

        viewModel.tempStoredOldBarcodeValue?.let {
            askBarcodeChange { this.doSave() }
            return
        }

        var hasError = false

        if (viewModel.loyaltyCard.store.isEmpty()) {
            binding.storeNameEdit.error = getString(R.string.field_must_not_be_empty)

            // Focus element
            selectTab(0)
            binding.storeNameEdit.requestFocus()

            hasError = true
        }

        if (viewModel.loyaltyCard.cardId.isEmpty()) {
            cardIdFieldView.error = getString(R.string.field_must_not_be_empty)

            // Focus element if first error element
            if (!hasError) {
                selectTab(0)
                cardIdFieldView.requestFocus()
                hasError = true
            }
        }

        if (!validBalance) {
            balanceField.error = getString(R.string.balanceParsingFailed)

            // Focus element if first error element
            if (!hasError) {
                selectTab(1)
                balanceField.requestFocus()
                hasError = true
            }
        }

        if (hasError) {
            return
        }

        val selectedGroups: MutableList<Group?> = mutableListOf()

        for (chipId in groupChips.checkedChipIds) {
            val chip = groupChips.findViewById<Chip>(chipId)
            selectedGroups.add(chip.tag as Group?)
        }

        // Both update and new card save with lastUsed set to null
        // This makes the DBHelper set it to the current date
        // So that new and edited card are always on top when sorting by recently used
        if (viewModel.updateLoyaltyCard) {
            DBHelper.updateLoyaltyCard(
                mDatabase,
                viewModel.loyaltyCardId,
                viewModel.loyaltyCard.store,
                viewModel.loyaltyCard.note,
                viewModel.loyaltyCard.validFrom,
                viewModel.loyaltyCard.expiry,
                viewModel.loyaltyCard.balance,
                viewModel.loyaltyCard.balanceType,
                viewModel.loyaltyCard.cardId,
                viewModel.loyaltyCard.barcodeId,
                viewModel.loyaltyCard.barcodeType,
                viewModel.loyaltyCard.headerColor,
                viewModel.loyaltyCard.starStatus,
                null,
                viewModel.loyaltyCard.archiveStatus
            )
        } else {
            viewModel.loyaltyCardId = DBHelper.insertLoyaltyCard(
                mDatabase,
                viewModel.loyaltyCard.store,
                viewModel.loyaltyCard.note,
                viewModel.loyaltyCard.validFrom,
                viewModel.loyaltyCard.expiry,
                viewModel.loyaltyCard.balance,
                viewModel.loyaltyCard.balanceType,
                viewModel.loyaltyCard.cardId,
                viewModel.loyaltyCard.barcodeId,
                viewModel.loyaltyCard.barcodeType,
                viewModel.loyaltyCard.headerColor,
                0,
                null,
                0
            ).toInt()
        }

        try {
            Utils.saveCardImage(
                this,
                viewModel.loyaltyCard.getImageFront(this),
                viewModel.loyaltyCardId,
                ImageLocationType.front
            )
            Utils.saveCardImage(
                this,
                viewModel.loyaltyCard.getImageBack(this),
                viewModel.loyaltyCardId,
                ImageLocationType.back
            )
            Utils.saveCardImage(
                this,
                viewModel.loyaltyCard.getImageThumbnail(this),
                viewModel.loyaltyCardId,
                ImageLocationType.icon
            )
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        }

        DBHelper.setLoyaltyCardGroups(mDatabase, viewModel.loyaltyCardId, selectedGroups)

        ShortcutHelper.updateShortcuts(
            this,
            DBHelper.getLoyaltyCard(this, mDatabase, viewModel.loyaltyCardId)
        )

        if (viewModel.duplicateFromLoyaltyCardId) {
            val intent = Intent(applicationContext, MainActivity::class.java)
            startActivity(intent)
        }

        finish()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.card_add_menu, menu)

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            askBeforeQuitIfChanged()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    fun startCropper(sourceImagePath: String?) {
        startCropperUri(("file://$sourceImagePath").toUri())
    }

    fun startCropperUri(sourceUri: Uri) {
        Log.d("cropper", "launching cropper with image " + sourceUri.path)
        val cropOutput = Utils.createTempFile(this, TEMP_CROP_IMAGE_NAME)
        val destUri = ("file://" + cropOutput.absolutePath).toUri()
        Log.d("cropper", "asking cropper to output to $destUri")

        val currentOperation = getCurrentImageOperation()
        if (currentOperation != null) {
            mCropperOptions.setToolbarTitle(getString(currentOperation.titleResource))
        } else {
            Toast.makeText(
                this,
                R.string.generic_error_please_retry,
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (requestedIcon()) {
            setCropperOptions(true, 0f, 0f)
        } else {
            // sniff the input image for width and height to work around a ucrop bug
            var image: Bitmap? = null
            try {
                image = BitmapFactory.decodeStream(contentResolver.openInputStream(sourceUri))
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
                Log.d(
                    "cropper",
                    "failed opening bitmap for initial width and height for ucrop $sourceUri"
                )
            }
            if (image == null) {
                Log.d(
                    "cropper",
                    "failed loading bitmap for initial width and height for ucrop $sourceUri"
                )
                setCropperOptions(true, 0f, 0f)
            } else {
                try {
                    val imageRotated = Utils.rotateBitmap(
                        image,
                        ExifInterface(contentResolver.openInputStream(sourceUri)!!)
                    )
                    setCropperOptions(
                        false,
                        imageRotated.width.toFloat(),
                        imageRotated.height.toFloat()
                    )
                } catch (e: FileNotFoundException) {
                    e.printStackTrace()
                    Log.d(
                        "cropper",
                        "failed opening image for exif reading before setting initial width and height for ucrop"
                    )
                    setCropperOptions(
                        false,
                        image.width.toFloat(),
                        image.height.toFloat()
                    )
                } catch (e: IOException) {
                    e.printStackTrace()
                    Log.d(
                        "cropper",
                        "exif reading failed before setting initial width and height for ucrop"
                    )
                    setCropperOptions(
                        false,
                        image.width.toFloat(),
                        image.height.toFloat()
                    )
                }
            }
        }
        val ucropIntent = UCrop.of(
            sourceUri,
            destUri
        ).withOptions(mCropperOptions)
            .getIntent(this)
        ucropIntent.setClass(this, UCropWrapper::class.java)
        for (i in 0..<toolbar.size) {
            // send toolbar font details to ucrop wrapper
            val child = toolbar.getChildAt(i)
            if (child is AppCompatTextView) {
                val childTextView = child
                ucropIntent.putExtra(
                    UCropWrapper.UCROP_TOOLBAR_TYPEFACE_STYLE,
                    childTextView.typeface.style
                )
                break
            }
        }
        mCropperLauncher.launch(ucropIntent)
    }

    private fun generateBarcode() {

        val cardIdString =
            if (viewModel.loyaltyCard.barcodeId != null) viewModel.loyaltyCard.barcodeId else viewModel.loyaltyCard.cardId
        val barcodeFormat = viewModel.loyaltyCard.barcodeType

        if (cardIdString == null || cardIdString.isEmpty() || barcodeFormat == null) {
            barcodeImageLayout.visibility = View.GONE
            return
        }

        barcodeImageLayout.visibility = View.VISIBLE

        if (barcodeImage.height == 0) {
            Log.d(TAG, "ImageView size is not known known at start, waiting for load")
            // The size of the ImageView is not yet available as it has not
            // yet been drawn. Wait for it to be drawn so the size is available.
            barcodeImage.viewTreeObserver.addOnGlobalLayoutListener(
                object : OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        barcodeImage.viewTreeObserver.removeOnGlobalLayoutListener(this)

                        Log.d(TAG, "ImageView size now known")
                        val barcodeWriter = BarcodeImageWriterTask(
                            applicationContext,
                            barcodeImage,
                            cardIdString,
                            barcodeFormat,
                            null,
                            false,
                            this@LoyaltyCardEditActivity,
                            true,
                            false
                        )

                        viewModel.executeTask(TaskHandler.TYPE.BARCODE, barcodeWriter)
                    }
                })
        } else {
            Log.d(TAG, "ImageView size known known, creating barcode")
            val barcodeWriter = BarcodeImageWriterTask(
                applicationContext,
                barcodeImage,
                cardIdString,
                barcodeFormat,
                null,
                false,
                this,
                true,
                false
            )
            viewModel.executeTask(TaskHandler.TYPE.BARCODE, barcodeWriter)
        }
    }

    private fun generateIcon(store: String?) {
        val headerColor = viewModel.loyaltyCard.headerColor ?: return

        if (viewModel.loyaltyCard.getImageThumbnail(this) == null) {
            binding.thumbnail.setBackgroundColor(headerColor)

            val letterBitmap = Utils.generateIcon(this, store, headerColor)

            if (letterBitmap != null) {
                binding.thumbnail.setImageBitmap(letterBitmap.letterTile)
            } else {
                binding.thumbnail.setImageBitmap(null)
            }
        }

        binding.thumbnail.minimumWidth = binding.thumbnail.height
    }

    private fun showPart(part: String?) {
        viewModel.tempStoredOldBarcodeValue?.let {
            askBarcodeChange{ showPart(part) }
            return
        }

        val cardPart: View = binding.cardPart
        val optionsPart: View = binding.optionsPart
        val picturesPart: View = binding.picturesPart

        when (part) {
            getString(R.string.card) -> {
                cardPart.visibility = View.VISIBLE
                optionsPart.visibility = View.GONE
                picturesPart.visibility = View.GONE

                // Redraw barcode due to size change (Visibility.GONE sets it to 0)
                generateBarcode()
            }

            getString(R.string.options) -> {
                cardPart.visibility = View.GONE
                optionsPart.visibility = View.VISIBLE
                picturesPart.visibility = View.GONE
            }

            getString(R.string.photos) -> {
                cardPart.visibility = View.GONE
                optionsPart.visibility = View.GONE
                picturesPart.visibility = View.VISIBLE
            }

            else -> {
                throw UnsupportedOperationException()
            }
        }
    }

    private fun currencyPrioritizeLocaleSymbols(currencyList: ArrayList<String?>, locale: Locale?) {
        try {
            val currencySymbol = getCurrencySymbol(Currency.getInstance(locale))
            currencyList.remove(currencySymbol)
            currencyList.add(0, currencySymbol)
        } catch (e: IllegalArgumentException) {
            Log.d(TAG, "Could not get currency data for locale info: $e")
        }
    }

    private fun getCurrencySymbol(currency: Currency): String? {
        // Workaround for Android bug where the output of Currency.getSymbol() changes.
        return currencySymbols.get(currency.currencyCode)
    }

    enum class ImageOperation(
        val resourceId: Int,
        val locationType: ImageLocationType,
        val cameraPermissionCode: Int,
        val storagePermissionCode: Int,
        val cameraTypeConstant: Int,
        val fileTypeConstant: Int,
        val titleResource: Int
    ) {
        FRONT(
            R.id.frontImageHolder,
            ImageLocationType.front,
            PERMISSION_REQUEST_CAMERA_IMAGE_FRONT,
            PERMISSION_REQUEST_STORAGE_IMAGE_FRONT,
            Utils.CARD_IMAGE_FROM_CAMERA_FRONT,
            Utils.CARD_IMAGE_FROM_FILE_FRONT,
            R.string.setFrontImage
        ),
        BACK(
            R.id.backImageHolder,
            ImageLocationType.back,
            PERMISSION_REQUEST_CAMERA_IMAGE_BACK,
            PERMISSION_REQUEST_STORAGE_IMAGE_BACK,
            Utils.CARD_IMAGE_FROM_CAMERA_BACK,
            Utils.CARD_IMAGE_FROM_FILE_BACK,
            R.string.setBackImage
        ),
        ICON(
            R.id.thumbnail,
            ImageLocationType.icon,
            PERMISSION_REQUEST_CAMERA_IMAGE_ICON,
            PERMISSION_REQUEST_STORAGE_IMAGE_ICON,
            Utils.CARD_IMAGE_FROM_CAMERA_ICON,
            Utils.CARD_IMAGE_FROM_FILE_ICON,
            R.string.setIcon
        );

        companion object {
            fun fromResourceId(resourceId: Int): ImageOperation =
                entries.find { it.resourceId == resourceId }
                    ?: throw IllegalArgumentException("Unknown resource ID: $resourceId")

            fun fromCameraPermission(permissionCode: Int): ImageOperation =
                entries.find { it.cameraPermissionCode == permissionCode }
                    ?: throw IllegalArgumentException("Unknown camera permission code: $permissionCode")

            fun fromStoragePermission(permissionCode: Int): ImageOperation =
                entries.find { it.storagePermissionCode == permissionCode }
                    ?: throw IllegalArgumentException("Unknown storage permission code: $permissionCode")

            fun fromImageType(imageType: Int): ImageOperation =
                entries.find { it.cameraTypeConstant == imageType || it.fileTypeConstant == imageType }
                    ?: throw IllegalArgumentException("Unknown image type: $imageType")
        }

        fun matches(imageType: Int): Boolean =
            cameraTypeConstant == imageType || fileTypeConstant == imageType
    }

    companion object {
        private const val TAG = "Catima"
        private const val PICK_DATE_REQUEST_KEY = "pick_date_request"
        private const val NEWLY_PICKED_DATE_ARGUMENT_KEY = "newly_picked_date"

        private const val PERMISSION_REQUEST_CAMERA_IMAGE_FRONT = 100
        private const val PERMISSION_REQUEST_CAMERA_IMAGE_BACK = 101
        private const val PERMISSION_REQUEST_CAMERA_IMAGE_ICON = 102
        private const val PERMISSION_REQUEST_STORAGE_IMAGE_FRONT = 103
        private const val PERMISSION_REQUEST_STORAGE_IMAGE_BACK = 104
        private const val PERMISSION_REQUEST_STORAGE_IMAGE_ICON = 105

        const val BUNDLE_ID: String = "id"
        const val BUNDLE_DUPLICATE_ID: String = "duplicateId"
        const val BUNDLE_UPDATE: String = "update"
        const val BUNDLE_OPEN_SET_ICON_MENU: String = "openSetIconMenu"
        const val BUNDLE_ADDGROUP: String = "addGroup"


        @JvmStatic
        fun formatDateField(context: Context, textField: EditText, date: Date?) {
            textField.tag = date

            if (date == null) {
                val text = when (textField.id) {
                    R.id.validFromField -> context.getString(R.string.anyDate)
                    R.id.expiryField -> context.getString(R.string.never)
                    else -> throw IllegalArgumentException("Unknown textField Id " + textField.id)
                }
                textField.setText(text)
            } else {
                textField.setText(DateFormat.getDateInstance(DateFormat.LONG).format(date))
            }
        }
    }
}
