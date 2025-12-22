package com.blackcode.poscandykush

import android.app.Presentation
import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.View
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.EditText
import android.text.TextWatcher
import android.text.Editable
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*

interface ToastCallback {
    fun showToast(message: String)
}

interface PrinterSettingsCallback {
    fun launchPrinterSettings()
}

class CustomerPresentation(context: Context, display: Display, private val toastCallback: ToastCallback?, private val printerSettingsCallback: PrinterSettingsCallback?) : Presentation(context, display) {

    private lateinit var adapter: CartItemAdapter
    private var discountText: TextView? = null
    private var taxText: TextView? = null
    private var totalText: TextView? = null
    private var updateTimeText: TextView? = null
    private var customerNameText: TextView? = null
    private var customerPhoneText: TextView? = null
    private var customerContainer: LinearLayout? = null
    private var progressBar: ProgressBar? = null
    private var errorText: TextView? = null

    // Welcome screen and cart view containers
    private var welcomeScreen: LinearLayout? = null
    private var cartView: LinearLayout? = null

    private val printApiService = PrintApiService()
    private var bluetoothPrinter: BluetoothThermalPrinter? = null
    private var currentCart: Cart? = null
    private val prefs = context.getSharedPreferences("printer_prefs", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    // Print polling is now handled by MainActivity to avoid double printing
    // private val printCheckRunnable = object : Runnable {
    //     override fun run() {
    //         checkForPrintJob()
    //         handler.postDelayed(this, 2000) // Check every 2 seconds per API documentation
    //     }
    // }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_presentation)

        val recyclerView: RecyclerView? = findViewById(R.id.rv_items)
        adapter = CartItemAdapter()
        recyclerView?.layoutManager = LinearLayoutManager(context)
        recyclerView?.adapter = adapter

        // Initialize view containers
        welcomeScreen = findViewById(R.id.welcome_screen)
        cartView = findViewById(R.id.cart_view)

        discountText = findViewById(R.id.discount_amount)
        taxText = findViewById(R.id.tax_amount)
        totalText = findViewById(R.id.total_amount)
        updateTimeText = findViewById(R.id.update_time)
        customerNameText = findViewById(R.id.customer_name)
        customerPhoneText = findViewById(R.id.customer_phone)
        customerContainer = findViewById(R.id.customer_container)
        progressBar = findViewById(R.id.progress_bar)
        errorText = findViewById(R.id.error_message)

        // Show welcome screen initially
        showWelcomeScreen()

        // Initialize Bluetooth thermal printer (for display purposes only)
        bluetoothPrinter = BluetoothThermalPrinter(context)

        // Print polling is now handled by MainActivity to avoid double printing
        // handler.post(printCheckRunnable)
    }

    fun updateCart(cartResponse: CartResponse?) {
        window?.decorView?.post {
            try {
                // Always update the timestamp
                val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                updateTimeText?.text = "Last updated: ${sdf.format(Date())}"

                if (cartResponse?.success == true && cartResponse.cart != null) {
                    val cart = cartResponse.cart
                    currentCart = cart

                    // Check if cart is empty
                    if (cart.items.isEmpty()) {
                        // Show welcome screen when cart is empty
                        showWelcomeScreen()
                    } else {
                        // Show cart view when cart has items
                        showCartView()

                        adapter.submitList(cart.items)


                        findViewById<LinearLayout>(R.id.row_discount)?.visibility = if (cart.discount.value > 0) View.VISIBLE else View.GONE
                        discountText?.text = String.format("-฿%.2f", cart.discount.value)

                        findViewById<LinearLayout>(R.id.row_tax)?.visibility = if (cart.tax.amount > 0) View.VISIBLE else View.GONE
                        taxText?.text = String.format("฿%.2f", cart.tax.amount)

                        totalText?.text = String.format("฿%.2f", cart.total)

                        // Handle customer display - hide if no customer info
                        if (cart.customer != null && !cart.customer.name.isNullOrEmpty() && !cart.customer.phone.isNullOrEmpty()) {
                            customerNameText?.text = cart.customer.name
                            customerPhoneText?.text = cart.customer.phone
                            customerContainer?.visibility = View.VISIBLE
                        } else {
                            customerContainer?.visibility = View.GONE
                        }

                        errorText?.visibility = View.GONE
                    }
                } else {
                    // Handle API failure - show welcome screen
                    showWelcomeScreen()
                    val errorMsg = cartResponse?.error ?: "API call failed"
                    android.util.Log.e("CustomerPresentation", "Cart update failed: $errorMsg")
                }
            } catch (e: Throwable) {
                android.util.Log.e("CustomerPresentation", "Exception in updateCart", e)
                showWelcomeScreen()
            }
        }
    }


    fun setLoading(isLoading: Boolean) {
        window?.decorView?.post {
            progressBar?.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }

    fun setError(error: String?) {
        window?.decorView?.post {
            if (error != null) {
                errorText?.visibility = View.VISIBLE
                errorText?.text = "Error: $error"
            } else {
                errorText?.visibility = View.GONE
            }
        }
    }

    private fun showWelcomeScreen() {
        welcomeScreen?.visibility = View.VISIBLE
        cartView?.visibility = View.GONE
        android.util.Log.d("CustomerPresentation", "Showing welcome screen (cart is empty)")
    }

    private fun showCartView() {
        welcomeScreen?.visibility = View.GONE
        cartView?.visibility = View.VISIBLE
        android.util.Log.d("CustomerPresentation", "Showing cart view (cart has items)")
    }

    /**
     * Synchronous network print for proper error handling
     */
    private fun networkPrintSync(ip: String, data: ByteArray) {
        val socket = java.net.Socket(ip, 9100)
        val output = socket.getOutputStream()
        output.write(data)
        output.flush()
        socket.close()
    }

    /**
     * Synchronous USB print for proper error handling
     */
    private fun usbPrintSync(deviceName: String, data: ByteArray): Boolean {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as android.hardware.usb.UsbManager
        val deviceList = usbManager.deviceList
        val device = deviceList.values.find { it.productName == deviceName }
            ?: throw Exception("USB device not found: $deviceName")

        if (!usbManager.hasPermission(device)) {
            throw Exception("USB permission not granted for: $deviceName")
        }

        val connection = usbManager.openDevice(device)
            ?: throw Exception("Failed to open USB device: $deviceName")

        val usbInterface = device.getInterface(0)
        if (!connection.claimInterface(usbInterface, true)) {
            connection.close()
            throw Exception("Failed to claim USB interface")
        }

        val endpoint = usbInterface.getEndpoint(1) // Bulk out
        val result = connection.bulkTransfer(endpoint, data, data.size, 5000)
        connection.releaseInterface(usbInterface)
        connection.close()

        return result >= 0
    }

    private fun parsePrinterName(savedPrinter: String?): String? {
        return when {
            savedPrinter?.startsWith("Bluetooth: ") == true -> savedPrinter.substringAfter("Bluetooth: ")
            savedPrinter?.startsWith("USB: ") == true -> savedPrinter.substringAfter("USB: ")
            savedPrinter?.startsWith("Network: ") == true -> savedPrinter.substringAfter("Network: ")
            else -> savedPrinter
        }
    }

    private fun extractPrinterName(printData: String): String? {
        // Try to extract printer name from print data if it's in format "PRINTER_NAME:data"
        return if (printData.contains(":")) {
            printData.substringBefore(":").takeIf { it.isNotEmpty() }
        } else {
            null
        }
    }

    private fun getFirstBondedDeviceName(): String? {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.BLUETOOTH_CONNECT
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    android.util.Log.w("CustomerPresentation", "BLUETOOTH_CONNECT permission not granted")
                    return null
                }
            }

            val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
            val bondedDevices = bluetoothAdapter?.bondedDevices
            bondedDevices?.firstOrNull()?.name
        } catch (e: SecurityException) {
            android.util.Log.e("CustomerPresentation", "SecurityException getting bonded devices", e)
            null
        } catch (e: Exception) {
            android.util.Log.e("CustomerPresentation", "Error getting bonded devices", e)
            null
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Print polling is now handled by MainActivity
        bluetoothPrinter?.disconnect()
    }

    private fun centerText(text: String, width: Int): String {
        val padding = (width - text.length) / 2
        return " ".repeat(maxOf(0, padding)) + text
    }

    private fun buildReceiptData(cartItems: List<CartItem>, total: Double, timestamp: String, protocol: String): ByteArray {
        val baos = java.io.ByteArrayOutputStream()

        // Load saved shop settings
        val shopName = prefs.getString("shop_name", "SHOP").takeIf { !it.isNullOrEmpty() } ?: "SHOP"
        val shopAddress = prefs.getString("shop_address", "")
        val shopPhone = prefs.getString("shop_phone", "")
        val footer = prefs.getString("receipt_footer", "Thank you!").takeIf { !it.isNullOrEmpty() } ?: "Thank you!"
        val logoBase64 = prefs.getString("logo_base64", null)
        val logoSize = prefs.getInt("logo_size", 50)

        // Load paper size settings
        val paperWidth = prefs.getInt("paper_width", 32) // 32 for 58mm, 48 for 80mm
        val printerDotsWidth = prefs.getInt("printer_dots_width", 384) // 384 for 58mm, 576 for 80mm

        // Load date/time format settings
        val dateFormatIndex = prefs.getInt("date_format_index", 2)
        val timeFormatIndex = prefs.getInt("time_format_index", 0)
        val currencyIndex = prefs.getInt("currency_index", 0)

        val dateFormatStr = getDateFormat(dateFormatIndex)
        val timeFormatStr = getTimeFormat(timeFormatIndex)
        val currencySymbol = getCurrencySymbol(currencyIndex)

        val currentDate = java.text.SimpleDateFormat(dateFormatStr, java.util.Locale.getDefault()).format(java.util.Date())
        val currentTime = java.text.SimpleDateFormat(timeFormatStr, java.util.Locale.getDefault()).format(java.util.Date())

        // Dynamic formatting based on paper size
        val separator = "=".repeat(paperWidth)
        val dottedLine = "-".repeat(paperWidth)

        // Calculate widths for proper alignment
        val qtyWidth = 4  // "x99 "
        val priceWidth = 10 // " 9,999.00"
        val nameWidth = paperWidth - qtyWidth - priceWidth

        // Load logo bitmap if available
        val logoBitmap = logoBase64?.let {
            try {
                val bytes = android.util.Base64.decode(it, android.util.Base64.DEFAULT)
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (e: Exception) {
                null
            }
        }

        when (protocol) {
            "Plain Text" -> {
                baos.write(byteArrayOf(0x1b, 0x40)) // Initialize
                baos.write(byteArrayOf(0x1b, 0x61, 0x01)) // Center

                // Print logo
                logoBitmap?.let { bitmap ->
                    baos.write(convertBitmapToEscPos(bitmap, logoSize, printerDotsWidth))
                    baos.write("\n".toByteArray(Charsets.ISO_8859_1))
                }

                // Shop header - centered
                baos.write("$shopName\n".toByteArray(Charsets.ISO_8859_1))
                if (!shopAddress.isNullOrEmpty()) baos.write("$shopAddress\n".toByteArray(Charsets.ISO_8859_1))
                if (!shopPhone.isNullOrEmpty()) baos.write("Tel: $shopPhone\n".toByteArray(Charsets.ISO_8859_1))
                baos.write("$separator\n".toByteArray(Charsets.ISO_8859_1))

                baos.write(byteArrayOf(0x1b, 0x61, 0x00)) // Left align

                // Date and Time on separate lines
                baos.write("Date: $currentDate\n".toByteArray(Charsets.ISO_8859_1))
                baos.write("Time: $currentTime\n".toByteArray(Charsets.ISO_8859_1))
                baos.write("$dottedLine\n".toByteArray(Charsets.ISO_8859_1))

                // Items with proper alignment
                for (item in cartItems) {
                    val line = formatReceiptLine(item.name, item.quantity.toInt(), item.total, nameWidth, currencySymbol)
                    baos.write("$line\n".toByteArray(Charsets.ISO_8859_1))
                }

                baos.write("$dottedLine\n".toByteArray(Charsets.ISO_8859_1))
                val totalLine = formatTotalLine("TOTAL", total, paperWidth, currencySymbol)
                baos.write("$totalLine\n".toByteArray(Charsets.ISO_8859_1))
                baos.write("$separator\n".toByteArray(Charsets.ISO_8859_1))

                baos.write(byteArrayOf(0x1b, 0x61, 0x01)) // Center

                // Print QR code if link is provided
                val qrLink = prefs.getString("qr_link", "")
                if (!qrLink.isNullOrEmpty()) {
                    generateQRCode(qrLink, 200)?.let { qrBitmap ->
                        baos.write(convertBitmapToEscPos(qrBitmap, 50, printerDotsWidth))
                        baos.write("\n".toByteArray(Charsets.ISO_8859_1))
                        qrBitmap.recycle()
                    }
                }

                baos.write("$footer\n".toByteArray(Charsets.ISO_8859_1))
                baos.write("\n\n\n".toByteArray(Charsets.ISO_8859_1))
                baos.write(byteArrayOf(0x1b, 0x61, 0x00)) // Left align
            }
            "ESC/POS Basic", "ESC General" -> {
                baos.write(byteArrayOf(0x1b, 0x40)) // Initialize
                baos.write(byteArrayOf(0x1b, 0x61, 0x01)) // Center

                // Print logo
                logoBitmap?.let { bitmap ->
                    baos.write(convertBitmapToEscPos(bitmap, logoSize, printerDotsWidth))
                    baos.write("\n".toByteArray(Charsets.ISO_8859_1))
                }

                // Shop header - centered
                baos.write("$shopName\n".toByteArray(Charsets.ISO_8859_1))
                if (!shopAddress.isNullOrEmpty()) baos.write("$shopAddress\n".toByteArray(Charsets.ISO_8859_1))
                if (!shopPhone.isNullOrEmpty()) baos.write("Tel: $shopPhone\n".toByteArray(Charsets.ISO_8859_1))
                baos.write("$separator\n".toByteArray(Charsets.ISO_8859_1))

                baos.write(byteArrayOf(0x1b, 0x61, 0x00)) // Left align

                // Date and Time on separate lines
                baos.write("Date: $currentDate\n".toByteArray(Charsets.ISO_8859_1))
                baos.write("Time: $currentTime\n".toByteArray(Charsets.ISO_8859_1))
                baos.write("$dottedLine\n".toByteArray(Charsets.ISO_8859_1))

                // Items with proper alignment
                for (item in cartItems) {
                    val line = formatReceiptLine(item.name, item.quantity.toInt(), item.total, nameWidth, currencySymbol)
                    baos.write("$line\n".toByteArray(Charsets.ISO_8859_1))
                }

                baos.write("$dottedLine\n".toByteArray(Charsets.ISO_8859_1))
                val totalLine = formatTotalLine("TOTAL", total, paperWidth, currencySymbol)
                baos.write("$totalLine\n".toByteArray(Charsets.ISO_8859_1))
                baos.write("$separator\n".toByteArray(Charsets.ISO_8859_1))

                baos.write(byteArrayOf(0x1b, 0x61, 0x01)) // Center

                // Print QR code if link is provided
                val qrLink = prefs.getString("qr_link", "")
                if (!qrLink.isNullOrEmpty()) {
                    generateQRCode(qrLink, 200)?.let { qrBitmap ->
                        baos.write(convertBitmapToEscPos(qrBitmap, 50, printerDotsWidth))
                        baos.write("\n".toByteArray(Charsets.ISO_8859_1))
                        qrBitmap.recycle()
                    }
                }

                baos.write("$footer\n".toByteArray(Charsets.ISO_8859_1))
                baos.write("\n\n\n".toByteArray(Charsets.ISO_8859_1))
                baos.write(byteArrayOf(0x1b, 0x61, 0x00)) // Left align
                baos.write(byteArrayOf(0x1d, 0x56, 0x42, 0x00)) // Cut
            }
            "ESC/POS Full", "DantSu ESC/POS" -> {
                baos.write(byteArrayOf(0x1b, 0x40)) // Initialize
                baos.write(byteArrayOf(0x1b, 0x61, 0x01)) // Center align

                // Print logo
                logoBitmap?.let { bitmap ->
                    baos.write(convertBitmapToEscPos(bitmap, logoSize, printerDotsWidth))
                    baos.write("\n".toByteArray(Charsets.ISO_8859_1))
                }

                baos.write(byteArrayOf(0x1b, 0x21, 0x30)) // Bold + Double height
                baos.write("$shopName\n".toByteArray(Charsets.ISO_8859_1))
                baos.write(byteArrayOf(0x1b, 0x21, 0x00)) // Normal

                if (!shopAddress.isNullOrEmpty()) {
                    baos.write("$shopAddress\n".toByteArray(Charsets.ISO_8859_1))
                }
                if (!shopPhone.isNullOrEmpty()) {
                    baos.write("Tel: $shopPhone\n".toByteArray(Charsets.ISO_8859_1))
                }

                baos.write("$separator\n".toByteArray(Charsets.ISO_8859_1))
                baos.write(byteArrayOf(0x1b, 0x61, 0x00)) // Left align

                // Date and Time on separate lines
                baos.write("Date: $currentDate\n".toByteArray(Charsets.ISO_8859_1))
                baos.write("Time: $currentTime\n".toByteArray(Charsets.ISO_8859_1))
                baos.write("$dottedLine\n".toByteArray(Charsets.ISO_8859_1))

                // Items with proper alignment
                for (item in cartItems) {
                    val line = formatReceiptLine(item.name, item.quantity.toInt(), item.total, nameWidth, currencySymbol)
                    baos.write("$line\n".toByteArray(Charsets.ISO_8859_1))
                }

                baos.write("$dottedLine\n".toByteArray(Charsets.ISO_8859_1))
                baos.write(byteArrayOf(0x1b, 0x21, 0x10)) // Bold
                val totalLine = formatTotalLine("TOTAL", total, paperWidth, currencySymbol)
                baos.write("$totalLine\n".toByteArray(Charsets.ISO_8859_1))
                baos.write(byteArrayOf(0x1b, 0x21, 0x00)) // Normal
                baos.write("$separator\n".toByteArray(Charsets.ISO_8859_1))

                baos.write(byteArrayOf(0x1b, 0x61, 0x01)) // Center

                // Print QR code if link is provided
                val qrLink = prefs.getString("qr_link", "")
                if (!qrLink.isNullOrEmpty()) {
                    generateQRCode(qrLink, 200)?.let { qrBitmap ->
                        baos.write(convertBitmapToEscPos(qrBitmap, 50, printerDotsWidth))
                        baos.write("\n".toByteArray(Charsets.ISO_8859_1))
                        qrBitmap.recycle()
                    }
                }

                baos.write("$footer\n".toByteArray(Charsets.ISO_8859_1))
                baos.write("\n\n\n".toByteArray(Charsets.ISO_8859_1))
                baos.write(byteArrayOf(0x1d, 0x56, 0x42, 0x00)) // Cut
            }
            "SmartPos SDK" -> {
                // SmartPos SDK is handled in BluetoothThermalPrinter
                // For USB/Network printing, fallback to plain text
                baos.write(byteArrayOf(0x1b, 0x40)) // Initialize
                baos.write(byteArrayOf(0x1b, 0x61, 0x01)) // Center

                // Print logo
                logoBitmap?.let { bitmap ->
                    baos.write(convertBitmapToEscPos(bitmap, logoSize, printerDotsWidth))
                    baos.write("\n".toByteArray(Charsets.ISO_8859_1))
                }

                // Shop header - centered
                baos.write("$shopName\n".toByteArray(Charsets.ISO_8859_1))
                if (!shopAddress.isNullOrEmpty()) baos.write("$shopAddress\n".toByteArray(Charsets.ISO_8859_1))
                if (!shopPhone.isNullOrEmpty()) baos.write("Tel: $shopPhone\n".toByteArray(Charsets.ISO_8859_1))
                baos.write("$separator\n".toByteArray(Charsets.ISO_8859_1))

                baos.write(byteArrayOf(0x1b, 0x61, 0x00)) // Left align

                // Date and Time on separate lines
                baos.write("Date: $currentDate\n".toByteArray(Charsets.ISO_8859_1))
                baos.write("Time: $currentTime\n".toByteArray(Charsets.ISO_8859_1))
                baos.write("$dottedLine\n".toByteArray(Charsets.ISO_8859_1))

                // Items with proper alignment
                for (item in cartItems) {
                    val line = formatReceiptLine(item.name, item.quantity.toInt(), item.total, nameWidth, currencySymbol)
                    baos.write("$line\n".toByteArray(Charsets.ISO_8859_1))
                }

                baos.write("$dottedLine\n".toByteArray(Charsets.ISO_8859_1))
                val totalLine = formatTotalLine("TOTAL", total, paperWidth, currencySymbol)
                baos.write("$totalLine\n".toByteArray(Charsets.ISO_8859_1))
                baos.write("$separator\n".toByteArray(Charsets.ISO_8859_1))

                baos.write(byteArrayOf(0x1b, 0x61, 0x01)) // Center

                // Print QR code if link is provided
                val qrLink = prefs.getString("qr_link", "")
                if (!qrLink.isNullOrEmpty()) {
                    generateQRCode(qrLink, 200)?.let { qrBitmap ->
                        baos.write(convertBitmapToEscPos(qrBitmap, 50, printerDotsWidth))
                        baos.write("\n".toByteArray(Charsets.ISO_8859_1))
                        qrBitmap.recycle()
                    }
                }

                baos.write("$footer\n".toByteArray(Charsets.ISO_8859_1))
                baos.write("\n\n\n".toByteArray(Charsets.ISO_8859_1))
                baos.write(byteArrayOf(0x1b, 0x61, 0x00)) // Left align
            }
        }

        logoBitmap?.recycle()
        return baos.toByteArray()
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
     * Handles transparent PNG by treating transparent pixels as white
     */
    private fun convertBitmapToEscPos(bitmap: android.graphics.Bitmap, sizePercent: Int, printerDotsWidth: Int = 384): ByteArray {
        val baos = java.io.ByteArrayOutputStream()

        try {
            // Use dynamic printer width based on paper size
            val maxWidth = printerDotsWidth
            val targetWidth = (maxWidth * sizePercent / 100).coerceIn(100, maxWidth)

            // Scale bitmap while maintaining aspect ratio
            val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
            val targetHeight = (targetWidth / aspectRatio).toInt()

            // Scale bitmap
            val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)

            // Convert to bitmap with white background (handles transparency)
            val bitmapWithBg = android.graphics.Bitmap.createBitmap(scaledBitmap.width, scaledBitmap.height, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmapWithBg)
            canvas.drawColor(android.graphics.Color.WHITE) // White background for transparent areas
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
            android.util.Log.e("CustomerPresentation", "Error converting bitmap to ESC/POS", e)
        }

        return baos.toByteArray()
    }

    /**
     * Convert bitmap to monochrome (black and white) using Floyd-Steinberg dithering
     */
    private fun convertToMonochrome(bitmap: android.graphics.Bitmap): android.graphics.Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val monoBitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.RGB_565)

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

    /**
     * Generate QR code bitmap from text/URL
     */
    private fun generateQRCode(text: String, size: Int = 200): android.graphics.Bitmap? {
        return try {
            val writer = com.google.zxing.qrcode.QRCodeWriter()
            val bitMatrix = writer.encode(text, com.google.zxing.BarcodeFormat.QR_CODE, size, size)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.RGB_565)

            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            android.util.Log.e("CustomerPresentation", "Error generating QR code", e)
            null
        }
    }

    private fun networkPrint(ip: String, data: ByteArray) {
        Thread {
            try {
                val socket = java.net.Socket(ip, 9100)
                val output = socket.getOutputStream()
                output.write(data)
                output.flush()
                socket.close()
                android.util.Log.d("CustomerPresentation", "Network receipt printed successfully")
                toastCallback?.showToast("Receipt printed successfully")
            } catch (e: Exception) {
                android.util.Log.e("CustomerPresentation", "Network print failed", e)
                toastCallback?.showToast("Network print failed: ${e.message}")
            }
        }.start()
    }

    private fun usbPrint(deviceName: String, data: ByteArray) {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val deviceList = usbManager.deviceList
        val device = deviceList.values.find { it.productName == deviceName }
        if (device == null) {
            toastCallback?.showToast("USB device not found")
            return
        }
        if (!usbManager.hasPermission(device)) {
            toastCallback?.showToast("USB permission not granted")
            return
        }
        val connection = usbManager.openDevice(device)
        if (connection == null) {
            toastCallback?.showToast("Failed to open USB device")
            return
        }
        val usbInterface = device.getInterface(0)
        if (!connection.claimInterface(usbInterface, true)) {
            toastCallback?.showToast("Failed to claim USB interface")
            connection.close()
            return
        }
        val endpoint = usbInterface.getEndpoint(1) // Bulk out
        val result = connection.bulkTransfer(endpoint, data, data.size, 5000)
        if (result >= 0) {
            android.util.Log.d("CustomerPresentation", "USB receipt printed successfully")
            toastCallback?.showToast("Receipt printed successfully")
        } else {
            android.util.Log.e("CustomerPresentation", "USB print failed")
            toastCallback?.showToast("USB print failed")
        }
        connection.releaseInterface(usbInterface)
        connection.close()
    }

    private fun formatShiftReport(shiftReport: ShiftReportData, cashier: String, timestamp: String): String {
        val sb = StringBuilder()

        // Load settings from SharedPreferences
        val shopName = prefs.getString("shop_name", "CANDY KUSH").takeIf { !it.isNullOrEmpty() } ?: "CANDY KUSH"
        val shopAddress = prefs.getString("shop_address", "")
        val shopPhone = prefs.getString("shop_phone", "")
        val footer = prefs.getString("receipt_footer", "Thank you!").takeIf { !it.isNullOrEmpty() } ?: "Thank you!"

        // Load paper size settings
        val paperWidth = prefs.getInt("paper_width", 32) // 32 for 58mm, 48 for 80mm
        val separator = "=".repeat(paperWidth)
        val dottedLine = "-".repeat(paperWidth)

        // Header
        sb.appendLine(separator)
        sb.appendLine(centerText(shopName, paperWidth))
        if (!shopAddress.isNullOrEmpty()) sb.appendLine(centerText(shopAddress, paperWidth))
        if (!shopPhone.isNullOrEmpty()) sb.appendLine(centerText("Tel: $shopPhone", paperWidth))
        sb.appendLine(separator)
        sb.appendLine()
        sb.appendLine(centerText("SHIFT REPORT", paperWidth))
        sb.appendLine(separator)
        sb.appendLine()

        // Cashier info
        sb.appendLine("Cashier: ${shiftReport.cashierName ?: "Unknown"}")
        sb.appendLine("Start: ${shiftReport.startTime ?: "N/A"}")
        sb.appendLine("End: ${shiftReport.endTime ?: "N/A"}")
        sb.appendLine(dottedLine)

        // Cash drawer section
        sb.appendLine()
        sb.appendLine(centerText("CASH DRAWER", paperWidth))
        sb.appendLine(dottedLine)
        sb.appendLine(String.format("Starting Cash:    %10.2f", shiftReport.startingCash))
        sb.appendLine(String.format("+ Cash Sales:     %10.2f", shiftReport.cashPayments))
        sb.appendLine(String.format("- Cash Refunds:   %10.2f", shiftReport.cashRefunds))
        sb.appendLine(String.format("+ Paid In:        %10.2f", shiftReport.paidIn))
        sb.appendLine(String.format("- Paid Out:       %10.2f", shiftReport.paidOut))
        sb.appendLine(dottedLine)
        sb.appendLine(String.format("Expected Cash:    %10.2f", shiftReport.expectedCash))
        sb.appendLine(String.format("Actual Cash:      %10.2f", shiftReport.actualCash))
        sb.appendLine(dottedLine)

        // Variance
        val varianceStr = when(shiftReport.varianceStatus) {
            "PERFECT" -> "PERFECT"
            "SHORT" -> String.format("-%.2f SHORT", Math.abs(shiftReport.variance))
            "OVER" -> String.format("+%.2f OVER", Math.abs(shiftReport.variance))
            else -> String.format("%.2f", shiftReport.variance)
        }
        sb.appendLine(String.format("VARIANCE:         %10s", varianceStr))
        sb.appendLine()

        // Sales summary
        sb.appendLine(centerText("SALES SUMMARY", paperWidth))
        sb.appendLine(dottedLine)
        sb.appendLine(String.format("Gross Sales:      %10.2f", shiftReport.grossSales))
        sb.appendLine(String.format("Refunds:          %10.2f", shiftReport.totalRefunds))
        sb.appendLine(String.format("Discounts:        %10.2f", shiftReport.totalDiscounts))
        sb.appendLine(dottedLine)
        sb.appendLine(String.format("NET SALES:        %10.2f", shiftReport.netSales))
        sb.appendLine(String.format("Transactions:     %10d", shiftReport.transactionCount))

        // Notes
        if (!shiftReport.notes.isNullOrEmpty()) {
            sb.appendLine()
            sb.appendLine("Notes: ${shiftReport.notes}")
        }

        sb.appendLine()
        sb.appendLine(separator)
        sb.appendLine(centerText("END OF SHIFT REPORT", paperWidth))
        sb.appendLine(separator)
        sb.appendLine()
        sb.appendLine(centerText(footer, paperWidth))
        sb.appendLine(centerText(timestamp, paperWidth))
        sb.appendLine()
        sb.appendLine()

        return sb.toString()
    }

    private fun buildTextData(text: String, protocol: String): ByteArray {
        val baos = java.io.ByteArrayOutputStream()

        // Load paper size settings
        val paperWidth = prefs.getInt("paper_width", 32) // 32 for 58mm, 48 for 80mm

        when (protocol) {
            "Plain Text" -> {
                baos.write(byteArrayOf(0x1b, 0x40)) // Initialize printer
                baos.write(byteArrayOf(0x1b, 0x61, 0x00)) // Left align

                // Split text into lines and print
                val lines = text.split("\n")
                for (line in lines) {
                    baos.write("$line\n".toByteArray(Charsets.ISO_8859_1))
                }

                baos.write("\n\n\n".toByteArray(Charsets.ISO_8859_1))
                baos.write(byteArrayOf(0x1b, 0x64, 0x03)) // Feed 3 lines
            }
            "ESC/POS Basic", "ESC General" -> {
                baos.write(byteArrayOf(0x1b, 0x40)) // Initialize
                baos.write(byteArrayOf(0x1b, 0x61, 0x00)) // Left align

                // Split text into lines and print
                val lines = text.split("\n")
                for (line in lines) {
                    baos.write("$line\n".toByteArray(Charsets.ISO_8859_1))
                }

                baos.write("\n\n\n".toByteArray(Charsets.ISO_8859_1))
                baos.write(byteArrayOf(0x1b, 0x64, 0x03)) // Feed paper
                baos.write(byteArrayOf(0x1d, 0x56, 0x42, 0x00)) // Cut
            }
            "ESC/POS Full" -> {
                baos.write(byteArrayOf(0x1b, 0x40)) // Initialize
                baos.write(byteArrayOf(0x1b, 0x61, 0x00)) // Left align

                // Split text into lines and print
                val lines = text.split("\n")
                for (line in lines) {
                    baos.write("$line\n".toByteArray(Charsets.ISO_8859_1))
                }

                baos.write("\n\n\n".toByteArray(Charsets.ISO_8859_1))
                baos.write(byteArrayOf(0x1d, 0x56, 0x42, 0x00)) // Cut
            }
        }

        return baos.toByteArray()
    }
}
