package com.blackcode.poscandykush

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.widget.AdapterView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

class PrinterSetupActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    // Left panel - Printer Selection
    private lateinit var printerRecyclerView: RecyclerView
    private lateinit var printerAdapter: PrinterListAdapter
    private val printers = mutableListOf<PrinterItem>()
    private var selectedPrinter: PrinterItem? = null

    // Right panel - Receipt Setup
    private lateinit var settingsScrollView: ScrollView
    private lateinit var shopNameEdit: EditText
    private lateinit var shopAddressEdit: EditText
    private lateinit var shopPhoneEdit: EditText
    private lateinit var footerEdit: EditText
    private lateinit var qrLinkEdit: EditText
    private lateinit var qrDescriptionEdit: EditText
    private lateinit var logoImageView: ImageView
    private lateinit var logoSizeSeekBar: SeekBar
    private lateinit var logoSizeLabel: TextView
    private lateinit var uploadLogoButton: Button
    private lateinit var removeLogoButton: Button

    // Preview
    private lateinit var previewContainer: LinearLayout
    private lateinit var previewLogo: ImageView
    private lateinit var previewShopName: TextView
    private lateinit var previewShopAddress: TextView
    private lateinit var previewShopPhone: TextView
    private lateinit var previewDate: TextView
    private lateinit var previewTime: TextView
    private lateinit var previewItems: LinearLayout
    private lateinit var previewFooter: TextView
    private lateinit var previewQr: ImageView

    // Buttons
    private lateinit var testPrintButton: Button
    private lateinit var saveButton: Button
    private lateinit var skipButton: Button

    // Permission container
    private lateinit var permissionContainer: LinearLayout
    private lateinit var mainContainer: LinearLayout
    private lateinit var requestPermissionButton: Button

    // Protocol and Settings Spinners
    private lateinit var protocolSpinner: Spinner
    private lateinit var paperSizeSpinner: Spinner
    private lateinit var dateFormatSpinner: Spinner
    private lateinit var timeFormatSpinner: Spinner
    private lateinit var currencySpinner: Spinner

    // Logo data
    private var logoBitmap: Bitmap? = null
    private var logoSizePercent: Int = 50

    // Paper feed setting
    private lateinit var paperFeedSeekBar: SeekBar
    private lateinit var paperFeedLabel: TextView
    private var paperFeedLines: Int = 3

    // Paper size (characters per line)
    private var paperWidth: Int = 32 // 58mm = 32 chars, 80mm = 48 chars
    private var printerDotsWidth: Int = 384 // 58mm = 384 dots, 80mm = 576 dots

    // Bluetooth
    private var bluetoothAdapter: BluetoothAdapter? = null

    private val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN
        )
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            showMainContent()
            loadAvailablePrinters()
        } else {
            showPermissionRequired()
        }
    }

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                loadLogoFromUri(uri)
            }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let { addBluetoothDevice(it) }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    // Discovery finished
                }
            }
        }
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if ("android.hardware.usb.action.USB_PERMISSION" == intent?.action) {
                val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    device?.let {
                        Toast.makeText(this@PrinterSetupActivity, "USB permission granted", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_printer_setup)

        prefs = getSharedPreferences("printer_prefs", Context.MODE_PRIVATE)
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        initViews()
        loadSavedSettings()

        // Check permissions first
        if (hasBluetoothPermissions()) {
            showMainContent()
            loadAvailablePrinters()
        } else {
            showPermissionRequired()
        }
    }

    private fun initViews() {
        // Permission views
        permissionContainer = findViewById(R.id.permission_container)
        mainContainer = findViewById(R.id.main_container)
        requestPermissionButton = findViewById(R.id.request_permission_button)

        requestPermissionButton.setOnClickListener {
            requestBluetoothPermissions()
        }

        // Printer list
        printerRecyclerView = findViewById(R.id.printer_recycler_view)
        printerRecyclerView.layoutManager = LinearLayoutManager(this)
        printerAdapter = PrinterListAdapter(printers) { printer ->
            selectPrinter(printer)
        }
        printerRecyclerView.adapter = printerAdapter

        // Receipt setup
        settingsScrollView = findViewById(R.id.settings_scroll_view)
        shopNameEdit = findViewById(R.id.shop_name_edit)
        shopAddressEdit = findViewById(R.id.shop_address_edit)
        shopPhoneEdit = findViewById(R.id.shop_phone_edit)
        footerEdit = findViewById(R.id.footer_edit)
        qrLinkEdit = findViewById(R.id.qr_link_edit)
        qrDescriptionEdit = findViewById(R.id.qr_description_edit)
        logoImageView = findViewById(R.id.logo_image_view)
        logoSizeSeekBar = findViewById(R.id.logo_size_seekbar)
        logoSizeLabel = findViewById(R.id.logo_size_label)
        uploadLogoButton = findViewById(R.id.upload_logo_button)
        removeLogoButton = findViewById(R.id.remove_logo_button)

        // Settings spinners
        protocolSpinner = findViewById(R.id.protocol_spinner)
        paperSizeSpinner = findViewById(R.id.paper_size_spinner)
        dateFormatSpinner = findViewById(R.id.date_format_spinner)
        timeFormatSpinner = findViewById(R.id.time_format_spinner)
        currencySpinner = findViewById(R.id.currency_spinner)

        // Paper feed setting
        paperFeedSeekBar = findViewById(R.id.paper_feed_seekbar)
        paperFeedLabel = findViewById(R.id.paper_feed_label)

        // Paper size change listener
        paperSizeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                when (position) {
                    0 -> { // 58mm
                        paperWidth = 32
                        printerDotsWidth = 384
                    }
                    1 -> { // 80mm
                        paperWidth = 48
                        printerDotsWidth = 576
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Date/Time/Currency spinner listeners for live preview
        val previewUpdateListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateDateTimePreview()
                updatePreviewItems()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        dateFormatSpinner.onItemSelectedListener = previewUpdateListener
        timeFormatSpinner.onItemSelectedListener = previewUpdateListener
        currencySpinner.onItemSelectedListener = previewUpdateListener

        // Add focus listeners to scroll to the focused EditText
        val focusListener = View.OnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                settingsScrollView.postDelayed({
                    // Calculate scroll position to bring view to visible area
                    val scrollTo = view.top - 50 // 50dp offset from top
                    settingsScrollView.smoothScrollTo(0, scrollTo.coerceAtLeast(0))
                }, 300) // Delay to wait for keyboard to appear
            }
        }
        shopNameEdit.onFocusChangeListener = focusListener
        shopAddressEdit.onFocusChangeListener = focusListener
        shopPhoneEdit.onFocusChangeListener = focusListener

        // Special handling for footer - scroll to bottom
        footerEdit.onFocusChangeListener = View.OnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                settingsScrollView.postDelayed({
                    settingsScrollView.smoothScrollTo(0, view.bottom)
                }, 300)
            }
        }

        // Preview
        previewContainer = findViewById(R.id.preview_container)
        previewLogo = findViewById(R.id.preview_logo)
        previewShopName = findViewById(R.id.preview_shop_name)
        previewShopAddress = findViewById(R.id.preview_shop_address)
        previewShopPhone = findViewById(R.id.preview_shop_phone)
        previewDate = findViewById(R.id.preview_date)
        previewTime = findViewById(R.id.preview_time)
        previewItems = findViewById(R.id.preview_items)
        previewFooter = findViewById(R.id.preview_footer)
        previewQr = findViewById(R.id.preview_qr)

        // Buttons
        testPrintButton = findViewById(R.id.test_print_button)
        saveButton = findViewById(R.id.save_button)
        skipButton = findViewById(R.id.skip_button)

        // Disable test print until printer is selected
        testPrintButton.isEnabled = false
        testPrintButton.alpha = 0.5f

        // Setup listeners
        setupListeners()

        // Add sample items to preview
        addSampleItemsToPreview()
    }

    private fun setupListeners() {
        // Text change listeners for live preview
        val textWatcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                updatePreview()
            }
        }

        shopNameEdit.addTextChangedListener(textWatcher)
        shopAddressEdit.addTextChangedListener(textWatcher)
        shopPhoneEdit.addTextChangedListener(textWatcher)
        footerEdit.addTextChangedListener(textWatcher)
        qrLinkEdit.addTextChangedListener(textWatcher)

        // Logo size seekbar
        logoSizeSeekBar.max = 100
        logoSizeSeekBar.progress = logoSizePercent
        logoSizeLabel.text = "Logo Size: ${logoSizePercent}%"

        logoSizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                logoSizePercent = if (progress < 10) 10 else progress
                logoSizeLabel.text = "Logo Size: ${logoSizePercent}%"
                updatePreviewLogo()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Paper feed seekbar
        paperFeedSeekBar.max = 10
        paperFeedSeekBar.progress = paperFeedLines
        paperFeedLabel.text = "Feed Lines: $paperFeedLines"

        paperFeedSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                paperFeedLines = progress
                paperFeedLabel.text = "Feed Lines: $paperFeedLines"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Upload logo button
        uploadLogoButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            imagePickerLauncher.launch(intent)
        }

        // Remove logo button
        removeLogoButton.setOnClickListener {
            logoBitmap = null
            logoImageView.setImageResource(R.drawable.ic_add_photo)
            previewLogo.visibility = View.GONE
            removeLogoButton.visibility = View.GONE
        }

        // Test print button
        testPrintButton.setOnClickListener {
            testPrintPreview()
        }

        // Save button
        saveButton.setOnClickListener {
            saveSettings()
        }

        // Skip button
        skipButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Skip Printer Setup?")
                .setMessage("You can configure the printer later from settings. Continue without printing capability?")
                .setPositiveButton("Skip") { _, _ ->
                    prefs.edit().putBoolean("setup_skipped", true).apply()
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun addSampleItemsToPreview() {
        updatePreviewItems()
    }

    private fun updatePreviewItems() {
        previewItems.removeAllViews()

        // Get selected currency symbol for display
        val currencyDisplay = getPreviewCurrencySymbol(currencySpinner.selectedItemPosition)

        val sampleItems = listOf(
            Triple("Milk 1L", 2, 45.00),
            Triple("Bread Whole Wheat", 1, 35.00),
            Triple("Eggs (12 pcs)", 1, 89.00),
            Triple("Orange Juice 500ml", 3, 120.00)
        )

        for (item in sampleItems) {
            val itemView = LayoutInflater.from(this).inflate(R.layout.preview_item_row, previewItems, false)
            itemView.findViewById<TextView>(R.id.item_name).text = item.first
            itemView.findViewById<TextView>(R.id.item_qty).text = "x${item.second}"
            itemView.findViewById<TextView>(R.id.item_total).text = String.format("%s%.2f", currencyDisplay, item.third)
            previewItems.addView(itemView)
        }

        // Add total
        val totalView = LayoutInflater.from(this).inflate(R.layout.preview_total_row, previewItems, false)
        totalView.findViewById<TextView>(R.id.total_label).text = "TOTAL"
        totalView.findViewById<TextView>(R.id.total_amount).text = String.format("%s%.2f", currencyDisplay, 289.00)
        previewItems.addView(totalView)
    }

    private fun updateDateTimePreview() {
        val dateFormat = getDateFormat(dateFormatSpinner.selectedItemPosition)
        val timeFormat = getTimeFormat(timeFormatSpinner.selectedItemPosition)

        val currentDate = SimpleDateFormat(dateFormat, Locale.getDefault()).format(Date())
        val currentTime = SimpleDateFormat(timeFormat, Locale.getDefault()).format(Date())

        previewDate.text = "Date: $currentDate"
        previewTime.text = "Time: $currentTime"
    }

    // Currency symbol for preview display (can use ฿ since it's just for UI)
    private fun getPreviewCurrencySymbol(index: Int): String {
        return when (index) {
            0 -> "THB "           // THB 100.00
            1 -> "฿"             // ฿100.00 (for preview display)
            2 -> "Baht "         // Baht 100.00
            3 -> "$"             // $100.00
            4 -> ""              // No symbol
            else -> "THB "
        }
    }

    private fun updatePreview() {
        val shopName = shopNameEdit.text.toString()
        val shopAddress = shopAddressEdit.text.toString()
        val shopPhone = shopPhoneEdit.text.toString()
        val footer = footerEdit.text.toString()
        val qrLink = qrLinkEdit.text.toString()

        previewShopName.visibility = if (shopName.isNotEmpty()) View.VISIBLE else View.GONE
        previewShopName.text = shopName

        previewShopAddress.visibility = if (shopAddress.isNotEmpty()) View.VISIBLE else View.GONE
        previewShopAddress.text = shopAddress

        previewShopPhone.visibility = if (shopPhone.isNotEmpty()) View.VISIBLE else View.GONE
        previewShopPhone.text = shopPhone

        previewFooter.visibility = if (footer.isNotEmpty()) View.VISIBLE else View.GONE
        previewFooter.text = footer

        // Update QR preview
        if (qrLink.isNotEmpty()) {
            val qrSizePercent = 90
            val scaleFactor = qrSizePercent / 100f
            val scaledSize = (200 * scaleFactor).toInt().coerceAtLeast(50)
            generateQRCode(qrLink, 300)?.let { qrBitmap ->
                val scaledBitmap = Bitmap.createScaledBitmap(qrBitmap, scaledSize, scaledSize, true)
                previewQr.setImageBitmap(scaledBitmap)
                previewQr.visibility = View.VISIBLE
                qrBitmap.recycle()
            } ?: run {
                previewQr.visibility = View.GONE
            }
        } else {
            previewQr.visibility = View.GONE
        }

        // Also update date/time preview
        updateDateTimePreview()
    }

    private fun updatePreviewLogo() {
        logoBitmap?.let { bitmap ->
            val maxWidth = 200 // Max width in dp
            val scaledWidth = (maxWidth * logoSizePercent / 100f).toInt()
            val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
            val scaledHeight = (scaledWidth / aspectRatio).toInt()

            val params = previewLogo.layoutParams
            params.width = (scaledWidth * resources.displayMetrics.density).toInt()
            params.height = (scaledHeight * resources.displayMetrics.density).toInt()
            previewLogo.layoutParams = params
            previewLogo.setImageBitmap(bitmap)
            previewLogo.visibility = View.VISIBLE
        }
    }

    private fun loadLogoFromUri(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            logoBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            logoBitmap?.let {
                logoImageView.setImageBitmap(it)
                removeLogoButton.visibility = View.VISIBLE
                updatePreviewLogo()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to load image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hasBluetoothPermissions(): Boolean {
        return bluetoothPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestBluetoothPermissions() {
        permissionLauncher.launch(bluetoothPermissions)
    }

    private fun showPermissionRequired() {
        permissionContainer.visibility = View.VISIBLE
        mainContainer.visibility = View.GONE
    }

    private fun showMainContent() {
        permissionContainer.visibility = View.GONE
        mainContainer.visibility = View.VISIBLE
    }

    @SuppressLint("MissingPermission")
    private fun loadAvailablePrinters() {
        printers.clear()

        // Bluetooth printers
        if (bluetoothAdapter != null && bluetoothAdapter!!.isEnabled) {
            try {
                val bondedDevices = bluetoothAdapter!!.bondedDevices
                for (device in bondedDevices) {
                    printers.add(PrinterItem(
                        name = device.name ?: "Unknown Device",
                        address = device.address,
                        type = PrinterType.BLUETOOTH
                    ))
                }
            } catch (e: SecurityException) {
                // Permission denied
            }
        }

        // USB printers
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val deviceList = usbManager.deviceList
        for (device in deviceList.values) {
            if (hasPrinterInterface(device)) {
                printers.add(PrinterItem(
                    name = device.productName ?: device.deviceName,
                    address = device.deviceName,
                    type = PrinterType.USB
                ))
            }
        }

        printerAdapter.notifyDataSetChanged()

        // Start Bluetooth discovery
        startBluetoothDiscovery()

        // Register USB receiver
        val usbFilter = IntentFilter("android.hardware.usb.action.USB_PERMISSION")
        ContextCompat.registerReceiver(this, usbReceiver, usbFilter, ContextCompat.RECEIVER_NOT_EXPORTED)

        // Load saved printer selection
        loadSavedPrinterSelection()
    }

    @SuppressLint("MissingPermission")
    private fun startBluetoothDiscovery() {
        try {
            val filter = IntentFilter()
            filter.addAction(BluetoothDevice.ACTION_FOUND)
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            registerReceiver(receiver, filter)

            if (bluetoothAdapter?.isDiscovering == false) {
                bluetoothAdapter?.startDiscovery()
            }
        } catch (e: SecurityException) {
            // Permission denied
        }
    }

    @SuppressLint("MissingPermission")
    private fun addBluetoothDevice(device: BluetoothDevice) {
        try {
            val deviceName = device.name ?: return
            val exists = printers.any { it.address == device.address }
            if (!exists) {
                printers.add(PrinterItem(
                    name = deviceName,
                    address = device.address,
                    type = PrinterType.BLUETOOTH
                ))
                printerAdapter.notifyDataSetChanged()
            }
        } catch (e: SecurityException) {
            // Permission denied
        }
    }

    private fun hasPrinterInterface(device: UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass == UsbConstants.USB_CLASS_PRINTER) return true
        }
        return false
    }

    private fun selectPrinter(printer: PrinterItem) {
        selectedPrinter = printer
        printerAdapter.setSelectedPrinter(printer)
        testPrintButton.isEnabled = true
        testPrintButton.alpha = 1.0f
    }

    private fun loadSavedPrinterSelection() {
        val savedPrinter = prefs.getString("selected_printer", null)
        if (savedPrinter != null) {
            val printer = printers.find {
                "${it.type.prefix}${it.name}" == savedPrinter
            }
            printer?.let { selectPrinter(it) }
        }
    }

    private fun loadSavedSettings() {
        shopNameEdit.setText(prefs.getString("shop_name", ""))
        shopAddressEdit.setText(prefs.getString("shop_address", ""))
        shopPhoneEdit.setText(prefs.getString("shop_phone", ""))
        footerEdit.setText(prefs.getString("receipt_footer", "Thank you for shopping!"))
        qrLinkEdit.setText(prefs.getString("qr_link", ""))
        qrDescriptionEdit.setText(prefs.getString("qr_description", ""))
        logoSizePercent = prefs.getInt("logo_size", 50)
        logoSizeSeekBar.progress = logoSizePercent
        logoSizeLabel.text = "Logo Size: ${logoSizePercent}%"

        // Load logo
        val logoBase64 = prefs.getString("logo_base64", null)
        if (logoBase64 != null) {
            try {
                val bytes = Base64.decode(logoBase64, Base64.DEFAULT)
                logoBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                logoBitmap?.let {
                    logoImageView.setImageBitmap(it)
                    removeLogoButton.visibility = View.VISIBLE
                    updatePreviewLogo()
                }
            } catch (e: Exception) {
                // Failed to load logo
            }
        }

        // Load protocol
        val savedProtocol = prefs.getString("print_protocol", "ESC/POS Basic")
        val protocols = resources.getStringArray(R.array.print_protocols)
        val index = protocols.indexOf(savedProtocol)
        if (index >= 0) protocolSpinner.setSelection(index)

        // Load paper size
        val savedPaperSize = prefs.getInt("paper_size_index", 0)
        paperSizeSpinner.setSelection(savedPaperSize)
        when (savedPaperSize) {
            0 -> { paperWidth = 32; printerDotsWidth = 384 }
            1 -> { paperWidth = 48; printerDotsWidth = 576 }
        }

        // Load date format
        val savedDateFormat = prefs.getInt("date_format_index", 2) // Default: YYYY-MM-DD
        dateFormatSpinner.setSelection(savedDateFormat)

        // Load time format
        val savedTimeFormat = prefs.getInt("time_format_index", 0) // Default: HH:mm:ss
        timeFormatSpinner.setSelection(savedTimeFormat)

        // Load currency symbol
        val savedCurrency = prefs.getInt("currency_index", 0) // Default: THB
        currencySpinner.setSelection(savedCurrency)

        // Load paper feed lines
        paperFeedLines = prefs.getInt("paper_feed_lines", 3) // Default: 3 lines
        paperFeedSeekBar.progress = paperFeedLines
        paperFeedLabel.text = "Feed Lines: $paperFeedLines"

        updatePreview()
    }

    private fun saveSettings() {
        val editor = prefs.edit()

        // Save shop info
        editor.putString("shop_name", shopNameEdit.text.toString())
        editor.putString("shop_address", shopAddressEdit.text.toString())
        editor.putString("shop_phone", shopPhoneEdit.text.toString())
        editor.putString("receipt_footer", footerEdit.text.toString())
        editor.putString("qr_link", qrLinkEdit.text.toString())
        editor.putString("qr_description", qrDescriptionEdit.text.toString())
        editor.putInt("logo_size", logoSizePercent)

        // Save logo as base64
        logoBitmap?.let { bitmap ->
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
            val logoBase64 = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT)
            editor.putString("logo_base64", logoBase64)
        } ?: editor.remove("logo_base64")

        // Save printer selection
        selectedPrinter?.let { printer ->
            editor.putString("selected_printer", "${printer.type.prefix}${printer.name}")
        }

        // Save protocol
        editor.putString("print_protocol", protocolSpinner.selectedItem.toString())

        // Save paper size
        editor.putInt("paper_size_index", paperSizeSpinner.selectedItemPosition)
        editor.putInt("paper_width", paperWidth)
        editor.putInt("printer_dots_width", printerDotsWidth)

        // Save date format
        editor.putInt("date_format_index", dateFormatSpinner.selectedItemPosition)

        // Save time format
        editor.putInt("time_format_index", timeFormatSpinner.selectedItemPosition)

        // Save currency symbol
        editor.putInt("currency_index", currencySpinner.selectedItemPosition)

        // Save paper feed lines
        editor.putInt("paper_feed_lines", paperFeedLines)

        // Mark setup as complete
        editor.putBoolean("setup_completed", true)
        editor.putBoolean("setup_skipped", false)

        editor.apply()

        Toast.makeText(this, "Settings saved successfully!", Toast.LENGTH_SHORT).show()
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun testPrintPreview() {
        if (selectedPrinter == null) {
            Toast.makeText(this, "Please select a printer first", Toast.LENGTH_SHORT).show()
            return
        }

        val protocol = protocolSpinner.selectedItem.toString()
        val receiptData = buildPreviewReceiptData(protocol)

        when (selectedPrinter!!.type) {
            PrinterType.BLUETOOTH -> {
                val printerManager = BluetoothThermalPrinter(this)
                Thread {
                    if (printerManager.connectToDevice(selectedPrinter!!.name)) {
                        val success = printerManager.sendData(receiptData)
                        runOnUiThread {
                            if (success) {
                                Toast.makeText(this, "Test print sent!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this, "Failed to send print data", Toast.LENGTH_SHORT).show()
                            }
                        }
                        Thread.sleep(500)
                        printerManager.disconnect()
                    } else {
                        runOnUiThread {
                            Toast.makeText(this, "Failed to connect to printer", Toast.LENGTH_SHORT).show()
                        }
                    }
                }.start()
            }
            PrinterType.USB -> {
                usbTestPrint(selectedPrinter!!.address, receiptData)
            }
        }
    }

    private fun buildPreviewReceiptData(protocol: String): ByteArray {
        val baos = ByteArrayOutputStream()
        val shopName = shopNameEdit.text.toString().ifEmpty { "SAMPLE SHOP" }
        val shopAddress = shopAddressEdit.text.toString()
        val shopPhone = shopPhoneEdit.text.toString()
        val footer = footerEdit.text.toString().ifEmpty { "Thank you!" }

        // Use dynamic paper width and feed lines
        val lineWidth = paperWidth
        val paperFeedLines = this.paperFeedLines // Use the class variable
        val separator = "=".repeat(lineWidth)
        val dottedLine = "-".repeat(lineWidth)

        // Get date and time format based on selection
        val dateFormat = getDateFormat(dateFormatSpinner.selectedItemPosition)
        val timeFormat = getTimeFormat(timeFormatSpinner.selectedItemPosition)
        val currencySymbol = getCurrencySymbol(currencySpinner.selectedItemPosition)

        val currentDate = SimpleDateFormat(dateFormat, Locale.getDefault()).format(Date())
        val currentTime = SimpleDateFormat(timeFormat, Locale.getDefault()).format(Date())

        val sampleItems = listOf(
            Triple("Milk 1L", 2, 45.00),
            Triple("Bread Whole Wheat", 1, 35.00),
            Triple("Eggs (12 pcs)", 1, 89.00),
            Triple("Orange Juice 500ml", 3, 120.00)
        )
        val total = 289.00

        // Calculate widths for proper alignment
        // Format: Name (left) | Qty (right) | Price (right)
        val qtyWidth = 4  // "x99 "
        val priceWidth = 10 // " 9,999.00"
        val nameWidth = lineWidth - qtyWidth - priceWidth

        when (protocol) {
            "DantSu ESC/POS", "ESC/POS Full" -> {
                baos.write(byteArrayOf(0x1b, 0x40)) // Initialize
                baos.write(byteArrayOf(0x1b, 0x61, 0x01)) // Center

                // Print logo if available
                logoBitmap?.let { bitmap ->
                    val logoData = convertBitmapToEscPos(bitmap, logoSizePercent)
                    baos.write(logoData)
                    baos.write("\n".toByteArray(Charsets.ISO_8859_1))
                }

                // Shop name (bold, double height) - centered
                baos.write(byteArrayOf(0x1b, 0x21, 0x30)) // Bold + Double height
                baos.write("$shopName\n".toByteArray(Charsets.ISO_8859_1))
                baos.write(byteArrayOf(0x1b, 0x21, 0x00)) // Normal

                // Address and phone - centered
                if (shopAddress.isNotEmpty()) {
                    baos.write("$shopAddress\n".toByteArray(Charsets.ISO_8859_1))
                }
                if (shopPhone.isNotEmpty()) {
                    baos.write("Tel: $shopPhone\n".toByteArray(Charsets.ISO_8859_1))
                }

                baos.write("$separator\n".toByteArray(Charsets.ISO_8859_1))
                baos.write(byteArrayOf(0x1b, 0x61, 0x00)) // Left align

                // Date and Time on separate lines
                baos.write("Date: $currentDate\n".toByteArray(Charsets.ISO_8859_1))
                baos.write("Time: $currentTime\n".toByteArray(Charsets.ISO_8859_1))
                baos.write("$dottedLine\n".toByteArray(Charsets.ISO_8859_1))

                // Items with proper alignment (name left, qty and price right)
                for (item in sampleItems) {
                    val line = formatReceiptLine(item.first, item.second, item.third, nameWidth, currencySymbol)
                    baos.write("$line\n".toByteArray(Charsets.ISO_8859_1))
                }

                baos.write("$dottedLine\n".toByteArray(Charsets.ISO_8859_1))
                baos.write(byteArrayOf(0x1b, 0x21, 0x10)) // Bold

                // Total line with currency symbol, right aligned
                val totalLine = formatTotalLine("TOTAL", total, lineWidth, currencySymbol)
                baos.write("$totalLine\n".toByteArray(Charsets.ISO_8859_1))
                baos.write(byteArrayOf(0x1b, 0x21, 0x00)) // Normal

                baos.write("$separator\n".toByteArray(Charsets.ISO_8859_1))
                baos.write(byteArrayOf(0x1b, 0x61, 0x01)) // Center
                baos.write("$footer\n".toByteArray(Charsets.ISO_8859_1))

                // Print QR code if link is provided
                val qrLink = qrLinkEdit.text.toString()
                val qrDescription = qrDescriptionEdit.text.toString()
                val qrSize = 90
                if (qrLink.isNotEmpty()) {
                    generateQRCode(qrLink, 300)?.let { qrBitmap ->
                        baos.write(convertBitmapToEscPos(qrBitmap, qrSize, printerDotsWidth))
                        baos.write("\n\n".toByteArray(Charsets.ISO_8859_1))
                        if (qrDescription.isNotEmpty()) {
                            baos.write("$qrDescription\n".toByteArray(Charsets.ISO_8859_1))
                        }
                        qrBitmap.recycle()
                    }
                }

                baos.write("\n\n".toByteArray(Charsets.ISO_8859_1))
                baos.write(byteArrayOf(0x1b, 0x64, paperFeedLines.toByte())) // Feed configurable lines
                baos.write(byteArrayOf(0x1d, 0x56, 0x42, 0x00)) // Cut
            }
            else -> {
                // Plain text / ESC Basic / ESC General
                baos.write(byteArrayOf(0x1b, 0x40)) // Initialize
                baos.write(byteArrayOf(0x1b, 0x61, 0x01)) // Center

                // Print logo if available
                logoBitmap?.let { bitmap ->
                    val logoData = convertBitmapToEscPos(bitmap, logoSizePercent)
                    baos.write(logoData)
                    baos.write("\n".toByteArray(Charsets.ISO_8859_1))
                }

                // Shop header - centered
                baos.write("$shopName\n".toByteArray(Charsets.ISO_8859_1))
                if (shopAddress.isNotEmpty()) {
                    baos.write("$shopAddress\n".toByteArray(Charsets.ISO_8859_1))
                }
                if (shopPhone.isNotEmpty()) {
                    baos.write("Tel: $shopPhone\n".toByteArray(Charsets.ISO_8859_1))
                }
                baos.write("$separator\n".toByteArray(Charsets.ISO_8859_1))

                baos.write(byteArrayOf(0x1b, 0x61, 0x00)) // Left align

                // Date and Time on separate lines
                baos.write("Date: $currentDate\n".toByteArray(Charsets.ISO_8859_1))
                baos.write("Time: $currentTime\n".toByteArray(Charsets.ISO_8859_1))
                baos.write("$dottedLine\n".toByteArray(Charsets.ISO_8859_1))

                // Items with proper alignment
                for (item in sampleItems) {
                    val line = formatReceiptLine(item.first, item.second, item.third, nameWidth, currencySymbol)
                    baos.write("$line\n".toByteArray(Charsets.ISO_8859_1))
                }

                baos.write("$dottedLine\n".toByteArray(Charsets.ISO_8859_1))

                // Total line
                val totalLine = formatTotalLine("TOTAL", total, lineWidth, currencySymbol)
                baos.write("$totalLine\n".toByteArray(Charsets.ISO_8859_1))
                baos.write("$separator\n".toByteArray(Charsets.ISO_8859_1))

                baos.write(byteArrayOf(0x1b, 0x61, 0x01)) // Center
                baos.write("$footer\n".toByteArray(Charsets.ISO_8859_1))

                // Print QR code if link is provided
                val qrLink = qrLinkEdit.text.toString()
                val qrDescription = qrDescriptionEdit.text.toString()
                val qrSize = 90
                if (qrLink.isNotEmpty()) {
                    generateQRCode(qrLink, 300)?.let { qrBitmap ->
                        baos.write(convertBitmapToEscPos(qrBitmap, qrSize, printerDotsWidth))
                        baos.write("\n\n".toByteArray(Charsets.ISO_8859_1))
                        if (qrDescription.isNotEmpty()) {
                            baos.write("$qrDescription\n".toByteArray(Charsets.ISO_8859_1))
                        }
                        qrBitmap.recycle()
                    }
                }

                baos.write("\n\n".toByteArray(Charsets.ISO_8859_1))
                baos.write(byteArrayOf(0x1b, 0x61, 0x00)) // Left align
                baos.write(byteArrayOf(0x1b, 0x64, paperFeedLines.toByte())) // Feed configurable lines

                if (protocol != "Plain Text") {
                    baos.write(byteArrayOf(0x1d, 0x56, 0x42, 0x00)) // Cut
                }
            }
        }

        return baos.toByteArray()
    }

    /**
     * Generate QR code bitmap from text/URL
     */
    private fun generateQRCode(text: String, size: Int = 300): Bitmap? {
        return try {
            val writer = com.google.zxing.qrcode.QRCodeWriter()
            val bitMatrix = writer.encode(text, com.google.zxing.BarcodeFormat.QR_CODE, size, size)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            android.util.Log.e("PrinterSetupActivity", "Error generating QR code", e)
            null
        }
    }

    // Helper function to get date format string
    private fun getDateFormat(index: Int): String {
        return when (index) {
            0 -> "dd/MM/yyyy"      // DD/MM/YYYY
            1 -> "MM/dd/yyyy"      // MM/DD/YYYY
            2 -> "yyyy-MM-dd"      // YYYY-MM-DD
            3 -> "dd-MM-yyyy"      // DD-MM-YYYY
            4 -> "dd MMM yyyy"     // DD MMM YYYY
            else -> "yyyy-MM-dd"
        }
    }

    // Helper function to get time format string
    private fun getTimeFormat(index: Int): String {
        return when (index) {
            0 -> "HH:mm:ss"        // 24-hour with seconds
            1 -> "HH:mm"           // 24-hour without seconds
            2 -> "hh:mm:ss a"      // 12-hour with seconds
            3 -> "hh:mm a"         // 12-hour without seconds
            else -> "HH:mm:ss"
        }
    }

    // Helper function to get currency symbol
    private fun getCurrencySymbol(index: Int): String {
        return when (index) {
            0 -> "THB "            // THB 100.00
            1 -> "B"               // Use B instead of ฿ for compatibility
            2 -> "Baht "           // Baht 100.00
            3 -> "$"               // $100.00
            4 -> ""                // No symbol
            else -> "THB "
        }
    }

    // Format a receipt line with name left-aligned, qty and price right-aligned
    private fun formatReceiptLine(name: String, qty: Int, price: Double, nameWidth: Int, currency: String): String {
        val truncatedName = name.take(nameWidth).padEnd(nameWidth)
        val qtyStr = "x$qty"
        val priceStr = String.format("%s%.2f", currency, price)
        return "$truncatedName $qtyStr ${priceStr.padStart(8)}"
    }

    // Format total line with label left and amount right
    private fun formatTotalLine(label: String, amount: Double, lineWidth: Int, currency: String): String {
        val amountStr = "$currency${String.format("%.2f", amount)}"
        val padding = lineWidth - label.length - amountStr.length
        return "$label${" ".repeat(maxOf(1, padding))}$amountStr"
    }

    /**
     * Convert bitmap to ESC/POS raster image format
     * This method converts a bitmap to a format that thermal printers can understand
     * Handles transparent PNG by treating transparent pixels as white
     */
    private fun convertBitmapToEscPos(bitmap: Bitmap, sizePercent: Int, printerDotsWidth: Int = 384): ByteArray {
        val baos = ByteArrayOutputStream()

        try {
            // Use dynamic printer width based on paper size
            val maxWidth = printerDotsWidth
            val targetWidth = (maxWidth * sizePercent / 100).coerceIn(100, maxWidth)

            // Scale bitmap while maintaining aspect ratio
            val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
            val targetHeight = (targetWidth / aspectRatio).toInt()

            // Scale bitmap
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)

            // Convert to bitmap with white background (handles transparency)
            val bitmapWithBg = Bitmap.createBitmap(scaledBitmap.width, scaledBitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmapWithBg)
            canvas.drawColor(android.graphics.Color.WHITE) // White background
            canvas.drawBitmap(scaledBitmap, 0f, 0f, null)

            val monoBitmap = convertToMonochrome(bitmapWithBg)

            // ESC/POS raster image command
            // GS v 0 m xL xH yL yH d1...dk
            baos.write(byteArrayOf(0x1d, 0x76, 0x30, 0x00)) // GS v 0 (normal mode)

            // Calculate width and height in bytes
            val widthBytes = (monoBitmap.width + 7) / 8
            val heightBytes = monoBitmap.height

            // xL, xH (width in bytes)
            baos.write(widthBytes and 0xFF)
            baos.write((widthBytes shr 8) and 0xFF)

            // yL, yH (height in dots)
            baos.write(heightBytes and 0xFF)
            baos.write((heightBytes shr 8) and 0xFF)

            // Image data
            for (y in 0 until monoBitmap.height) {
                for (x in 0 until widthBytes) {
                    var byte = 0
                    for (bit in 0..7) {
                        val px = x * 8 + bit
                        if (px < monoBitmap.width) {
                            val pixel = monoBitmap.getPixel(px, y)
                            // If pixel is black (or dark), set bit to 1
                            if (android.graphics.Color.red(pixel) < 128) {
                                byte = byte or (1 shl (7 - bit))
                            }
                        }
                    }
                    baos.write(byte)
                }
            }

            if (scaledBitmap != bitmap) scaledBitmap.recycle()
            bitmapWithBg.recycle()
            monoBitmap.recycle()

        } catch (e: Exception) {
            android.util.Log.e("PrinterSetup", "Error converting bitmap to ESC/POS", e)
        }

        return baos.toByteArray()
    }

    /**
     * Convert bitmap to monochrome (black and white) using Floyd-Steinberg dithering
     */
    private fun convertToMonochrome(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val monoBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

        // Create a mutable copy for dithering
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Floyd-Steinberg dithering
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                val pixel = pixels[index]
                val gray = (android.graphics.Color.red(pixel) * 0.299 +
                           android.graphics.Color.green(pixel) * 0.587 +
                           android.graphics.Color.blue(pixel) * 0.114).toInt()

                val newGray = if (gray < 128) 0 else 255
                val error = gray - newGray

                pixels[index] = android.graphics.Color.rgb(newGray, newGray, newGray)

                // Distribute error to neighboring pixels
                if (x + 1 < width) {
                    val nextIndex = index + 1
                    val nextPixel = pixels[nextIndex]
                    val nextGray = (android.graphics.Color.red(nextPixel) + error * 7 / 16).coerceIn(0, 255)
                    pixels[nextIndex] = android.graphics.Color.rgb(nextGray, nextGray, nextGray)
                }

                if (y + 1 < height) {
                    if (x > 0) {
                        val nextIndex = (y + 1) * width + (x - 1)
                        val nextPixel = pixels[nextIndex]
                        val nextGray = (android.graphics.Color.red(nextPixel) + error * 3 / 16).coerceIn(0, 255)
                        pixels[nextIndex] = android.graphics.Color.rgb(nextGray, nextGray, nextGray)
                    }

                    val nextIndex = (y + 1) * width + x
                    val nextPixel = pixels[nextIndex]
                    val nextGray = (android.graphics.Color.red(nextPixel) + error * 5 / 16).coerceIn(0, 255)
                    pixels[nextIndex] = android.graphics.Color.rgb(nextGray, nextGray, nextGray)

                    if (x + 1 < width) {
                        val nextIndex = (y + 1) * width + (x + 1)
                        val nextPixel = pixels[nextIndex]
                        val nextGray = (android.graphics.Color.red(nextPixel) + error * 1 / 16).coerceIn(0, 255)
                        pixels[nextIndex] = android.graphics.Color.rgb(nextGray, nextGray, nextGray)
                    }
                }
            }
        }

        monoBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return monoBitmap
    }

    private fun centerText(text: String, width: Int): String {
        val padding = (width - text.length) / 2
        return " ".repeat(maxOf(0, padding)) + text
    }

    private fun usbTestPrint(deviceAddress: String, data: ByteArray) {
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val device = usbManager.deviceList[deviceAddress]

        if (device == null) {
            Toast.makeText(this, "USB device not found", Toast.LENGTH_SHORT).show()
            return
        }

        if (!usbManager.hasPermission(device)) {
            val permissionIntent = PendingIntent.getBroadcast(
                this, 0,
                Intent("android.hardware.usb.action.USB_PERMISSION"),
                PendingIntent.FLAG_IMMUTABLE
            )
            usbManager.requestPermission(device, permissionIntent)
            return
        }

        Thread {
            try {
                val connection = usbManager.openDevice(device)
                if (connection == null) {
                    runOnUiThread { Toast.makeText(this, "Failed to open USB device", Toast.LENGTH_SHORT).show() }
                    return@Thread
                }

                val usbInterface = device.getInterface(0)
                if (!connection.claimInterface(usbInterface, true)) {
                    runOnUiThread { Toast.makeText(this, "Failed to claim interface", Toast.LENGTH_SHORT).show() }
                    connection.close()
                    return@Thread
                }

                // Find bulk out endpoint
                var endpoint: android.hardware.usb.UsbEndpoint? = null
                for (i in 0 until usbInterface.endpointCount) {
                    val ep = usbInterface.getEndpoint(i)
                    if (ep.type == android.hardware.usb.UsbConstants.USB_ENDPOINT_XFER_BULK &&
                        ep.direction == android.hardware.usb.UsbConstants.USB_DIR_OUT) {
                        endpoint = ep
                        break
                    }
                }

                if (endpoint == null) {
                    runOnUiThread { Toast.makeText(this, "No suitable endpoint found", Toast.LENGTH_SHORT).show() }
                    connection.releaseInterface(usbInterface)
                    connection.close()
                    return@Thread
                }

                val result = connection.bulkTransfer(endpoint, data, data.size, 5000)
                connection.releaseInterface(usbInterface)
                connection.close()

                runOnUiThread {
                    if (result >= 0) {
                        Toast.makeText(this, "Test print sent!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "USB print failed", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "USB error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(receiver) } catch (e: Exception) {}
        try { unregisterReceiver(usbReceiver) } catch (e: Exception) {}

        if (hasBluetoothPermissions()) {
            try { bluetoothAdapter?.cancelDiscovery() } catch (e: Exception) {}
        }
    }
}

// Data classes
enum class PrinterType(val prefix: String) {
    BLUETOOTH("Bluetooth: "),
    USB("USB: ")
}

data class PrinterItem(
    val name: String,
    val address: String,
    val type: PrinterType
)

// Printer list adapter
class PrinterListAdapter(
    private val printers: List<PrinterItem>,
    private val onSelect: (PrinterItem) -> Unit
) : RecyclerView.Adapter<PrinterListAdapter.ViewHolder>() {

    private var selectedPosition = -1

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val radioButton: RadioButton = view.findViewById(R.id.printer_radio)
        val nameText: TextView = view.findViewById(R.id.printer_name)
        val typeText: TextView = view.findViewById(R.id.printer_type)
        val iconImage: ImageView = view.findViewById(R.id.printer_icon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_printer, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val printer = printers[position]
        holder.nameText.text = printer.name
        holder.typeText.text = when (printer.type) {
            PrinterType.BLUETOOTH -> "Bluetooth"
            PrinterType.USB -> "USB"
        }
        holder.iconImage.setImageResource(
            when (printer.type) {
                PrinterType.BLUETOOTH -> R.drawable.ic_bluetooth
                PrinterType.USB -> R.drawable.ic_usb
            }
        )
        holder.radioButton.isChecked = position == selectedPosition

        holder.itemView.setOnClickListener {
            val oldPosition = selectedPosition
            selectedPosition = holder.adapterPosition
            notifyItemChanged(oldPosition)
            notifyItemChanged(selectedPosition)
            onSelect(printer)
        }

        holder.radioButton.setOnClickListener {
            val oldPosition = selectedPosition
            selectedPosition = holder.adapterPosition
            notifyItemChanged(oldPosition)
            notifyItemChanged(selectedPosition)
            onSelect(printer)
        }
    }

    override fun getItemCount() = printers.size

    fun setSelectedPrinter(printer: PrinterItem) {
        val index = printers.indexOf(printer)
        if (index >= 0) {
            val oldPosition = selectedPosition
            selectedPosition = index
            notifyItemChanged(oldPosition)
            notifyItemChanged(selectedPosition)
        }
    }
}
