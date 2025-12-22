package com.blackcode.poscandykush

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import com.paydevice.smartpos.sdk.printer.PrinterManager
import com.paydevice.smartpos.sdk.printer.SerialPortPrinter
import com.paydevice.smartpos.sdk.printer.UsbPrinter
import java.io.IOException
import java.io.OutputStream
import java.util.*

class StoragePermissionRequiredException(message: String) : RuntimeException(message)

class BluetoothThermalPrinter(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private val smartPosPrinter: SmartPosPrinter? by lazy {
        // SmartPos SDK integration with actual JAR from factory
        try {
            // Create SerialPortPrinter for serial/built-in printers
            val printer = SerialPortPrinter()
            // Create PrinterManager with printer and type
            val printerManager = PrinterManager(printer, PrinterManager.PRINTER_TYPE_SERIAL)
            SmartPosPrinter(printerManager)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SmartPos SDK: ${e.message}", e)
            null // Return null instead of throwing
        }
    }

    companion object {
        private const val TAG = "BluetoothThermalPrinter"
        // Standard UUID for Bluetooth Serial Port Profile
        private val UUID_SERIAL_PORT = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    fun connectToDevice(deviceName: String): Boolean {
        return try {
            if (bluetoothAdapter == null) {
                Log.e(TAG, "Bluetooth not supported on this device")
                return false
            }

            // Check permissions for Android 12+
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.BLUETOOTH_CONNECT
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    Log.e(TAG, "BLUETOOTH_CONNECT permission not granted")
                    return false
                }
            }

            val pairedDevices: Set<BluetoothDevice> = bluetoothAdapter.bondedDevices
            var targetDevice: BluetoothDevice? = null

            for (device in pairedDevices) {
                if (device.name == deviceName) {
                    targetDevice = device
                    break
                }
            }

            if (targetDevice == null) {
                Log.e(TAG, "Device not found: $deviceName")
                return false
            }

            bluetoothSocket = targetDevice.createRfcommSocketToServiceRecord(UUID_SERIAL_PORT)
            bluetoothSocket?.connect()
            outputStream = bluetoothSocket?.outputStream
            Log.d(TAG, "Connected to $deviceName")
            true
        } catch (e: IOException) {
            Log.e(TAG, "Connection failed", e)
            false
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: Permission denied", e)
            false
        }
    }

    fun disconnect() {
        try {
            outputStream?.close()
            bluetoothSocket?.close()
            try {
                smartPosPrinter?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to release SmartPos SDK", e)
            }
            Log.d(TAG, "Disconnected from printer")
        } catch (e: IOException) {
            Log.e(TAG, "Disconnect error", e)
        }
    }

    fun sendData(data: ByteArray): Boolean {
        return try {
            if (outputStream == null) {
                Log.e(TAG, "OutputStream is null - printer not connected")
                return false
            }
            Log.d(TAG, "Sending ${data.size} bytes to printer")

            // For large data (like receipts with QR codes), send in chunks to avoid buffer overflow
            if (data.size > 1024) {
                val chunkSize = 512
                var offset = 0
                while (offset < data.size) {
                    val remaining = data.size - offset
                    val length = if (remaining > chunkSize) chunkSize else remaining
                    outputStream?.write(data, offset, length)
                    outputStream?.flush()
                    offset += length
                    // Small delay to let printer buffer process
                    if (offset < data.size) {
                        Thread.sleep(50)
                    }
                }
            } else {
                outputStream?.write(data)
                outputStream?.flush()
            }

            // Add delay after sending to ensure printer processes all data
            Thread.sleep(100)

            Log.d(TAG, "Data sent successfully (${data.size} bytes)")
            true
        } catch (e: IOException) {
            Log.e(TAG, "Send data failed", e)
            false
        } catch (e: InterruptedException) {
            Log.e(TAG, "Send data interrupted", e)
            false
        }
    }

    fun printReceipt(cartItems: List<CartItem>, total: Double, cartLastUpdated: String, protocol: String = "Plain Text") {
        try {
            // Handle DantSu ESC/POS library mode
            if (protocol == "DantSu ESC/POS") {
                printWithDantSuLibrary(cartItems, total, cartLastUpdated)
                Log.d(TAG, "Receipt printed via DantSu ESC/POS library")
                return
            }

            // Handle PeriPage Image mode
            if (protocol == "PeriPage Image") {
                val periPagePrinter = PeriPagePrinter(context)
                val bitmap = periPagePrinter.createReceiptBitmap(cartItems, total, cartLastUpdated)
                val imageData = periPagePrinter.convertBitmapToPeriPageData(bitmap)
                sendData(imageData)
                Log.d(TAG, "Receipt printed via PeriPage Image mode")
                return
            }

            // Handle SmartPos SDK separately
            if (protocol == "SmartPos SDK") {
                try {
                    smartPosPrinter?.let {
                        if (it.initialize(SmartPosPrinter.PRINTER_TYPE_SERIAL)) {
                            // printReceipt now throws exceptions instead of returning boolean
                            it.printReceipt(cartItems, total, cartLastUpdated)
                            Log.d(TAG, "Receipt printed via SmartPos SDK")
                            return
                        } else {
                            Log.e(TAG, "SmartPos SDK initialization failed, falling back to standard printing")
                        }
                    } ?: run {
                        Log.e(TAG, "SmartPos SDK not available, falling back to standard printing")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "SmartPos SDK print failed: ${e.message}, falling back to standard printing", e)
                }
            }
            // Fallback to standard printing
            val receiptData = buildReceiptData(cartItems, total, cartLastUpdated, protocol)
            sendData(receiptData)
        } catch (e: Exception) {
            Log.e(TAG, "Print receipt failed", e)
            throw e // Re-throw to let caller handle the error
        }
    }

    /**
     * Print arbitrary text to the thermal printer
     * @param text The text to print
     * @param protocol The printing protocol to use (default: "Plain Text")
     */
    fun printText(text: String, protocol: String = "Plain Text") {
        try {
            // Handle DantSu ESC/POS library mode
            if (protocol == "DantSu ESC/POS") {
                printTextWithDantSuLibrary(text)
                Log.d(TAG, "Text printed via DantSu ESC/POS library")
                return
            }

            // Handle PeriPage Image mode - convert text to image
            if (protocol == "PeriPage Image") {
                val periPagePrinter = PeriPagePrinter(context)
                val bitmap = periPagePrinter.createTextBitmap(text)
                val imageData = periPagePrinter.convertBitmapToPeriPageData(bitmap)
                sendData(imageData)
                Log.d(TAG, "Text printed via PeriPage Image mode")
                return
            }

            // Handle SmartPos SDK
            if (protocol == "SmartPos SDK") {
                try {
                    smartPosPrinter?.let {
                        if (it.initialize(SmartPosPrinter.PRINTER_TYPE_SERIAL)) {
                            it.printRawData(text.toByteArray(Charsets.UTF_8))
                            Log.d(TAG, "Text printed via SmartPos SDK")
                            return
                        } else {
                            Log.e(TAG, "SmartPos SDK initialization failed, falling back to standard printing")
                        }
                    } ?: run {
                        Log.e(TAG, "SmartPos SDK not available, falling back to standard printing")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "SmartPos SDK print failed: ${e.message}, falling back to standard printing", e)
                }
            }

            // Fallback to standard printing - convert text to ESC/POS commands
            val textData = buildTextData(text, protocol)
            sendData(textData)
        } catch (e: Exception) {
            Log.e(TAG, "Print text failed", e)
            throw e // Re-throw to let caller handle the error
        }
    }

    private fun buildReceiptData(cartItems: List<CartItem>, total: Double, cartLastUpdated: String, protocol: String): ByteArray {
        val baos = java.io.ByteArrayOutputStream()

        // Load settings from SharedPreferences
        val prefs = context.getSharedPreferences("printer_prefs", Context.MODE_PRIVATE)
        val shopName = prefs.getString("shop_name", "CANDY KUSH").takeIf { !it.isNullOrEmpty() } ?: "CANDY KUSH"
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
                baos.write(byteArrayOf(0x1b, 0x40)) // Initialize printer
                baos.write(byteArrayOf(0x1b, 0x61, 0x01)) // Center align

                // Print logo if available
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
                val qrSize = 90
                if (!qrLink.isNullOrEmpty()) {
                    generateQRCode(qrLink, 300)?.let { qrBitmap ->
                        baos.write(convertBitmapToEscPos(qrBitmap, qrSize, printerDotsWidth))
                        baos.write("\n\n".toByteArray(Charsets.ISO_8859_1))
                        qrBitmap.recycle()
                    }
                }

                baos.write("$footer\n".toByteArray(Charsets.ISO_8859_1))

                // Use configurable paper feed lines
                val paperFeedLines = prefs.getInt("paper_feed_lines", 3)
                baos.write("\n\n".toByteArray(Charsets.ISO_8859_1))
                baos.write(byteArrayOf(0x1b, 0x61, 0x00)) // Left align
                baos.write(byteArrayOf(0x1b, 0x64, paperFeedLines.toByte())) // Feed configurable lines
            }
            "ESC/POS Basic", "ESC General" -> {
                baos.write(byteArrayOf(0x1b, 0x40)) // Initialize
                baos.write(byteArrayOf(0x1b, 0x61, 0x01)) // Center align

                // Print logo if available
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
                val qrSize = 90
                if (!qrLink.isNullOrEmpty()) {
                    generateQRCode(qrLink, 300)?.let { qrBitmap ->
                        baos.write(convertBitmapToEscPos(qrBitmap, qrSize, printerDotsWidth))
                        baos.write("\n\n".toByteArray(Charsets.ISO_8859_1))
                        qrBitmap.recycle()
                    }
                }

                baos.write("$footer\n".toByteArray(Charsets.ISO_8859_1))

                // Use configurable paper feed lines
                val paperFeedLines = prefs.getInt("paper_feed_lines", 3)
                baos.write("\n\n".toByteArray(Charsets.ISO_8859_1))
                baos.write(byteArrayOf(0x1b, 0x61, 0x00)) // Left align
                baos.write(byteArrayOf(0x1b, 0x64, paperFeedLines.toByte())) // Feed paper
                baos.write(byteArrayOf(0x1d, 0x56, 0x42, 0x00)) // Cut
            }
            "ESC/POS Full" -> {
                baos.write(byteArrayOf(0x1b, 0x40)) // Initialize
                baos.write(byteArrayOf(0x1b, 0x61, 0x01)) // Center align

                // Print logo if available
                logoBitmap?.let { bitmap ->
                    baos.write(convertBitmapToEscPos(bitmap, logoSize, printerDotsWidth))
                    baos.write("\n".toByteArray(Charsets.ISO_8859_1))
                }

                baos.write(byteArrayOf(0x1b, 0x21, 0x30)) // Bold + Double height
                baos.write("$shopName\n".toByteArray(Charsets.ISO_8859_1))
                baos.write(byteArrayOf(0x1b, 0x21, 0x00)) // Normal

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
                baos.write(byteArrayOf(0x1b, 0x21, 0x10)) // Bold
                val totalLine = formatTotalLine("TOTAL", total, paperWidth, currencySymbol)
                baos.write("$totalLine\n".toByteArray(Charsets.ISO_8859_1))
                baos.write(byteArrayOf(0x1b, 0x21, 0x00)) // Normal
                baos.write("$separator\n".toByteArray(Charsets.ISO_8859_1))

                baos.write(byteArrayOf(0x1b, 0x61, 0x01)) // Center

                // Print QR code if link is provided
                val qrLink = prefs.getString("qr_link", "")
                val qrSize = 90
                if (!qrLink.isNullOrEmpty()) {
                    generateQRCode(qrLink, 300)?.let { qrBitmap ->
                        baos.write(convertBitmapToEscPos(qrBitmap, qrSize, printerDotsWidth))
                        baos.write("\n\n".toByteArray(Charsets.ISO_8859_1))
                        qrBitmap.recycle()
                    }
                }

                baos.write("$footer\n".toByteArray(Charsets.ISO_8859_1))

                // Use configurable paper feed lines
                val paperFeedLines = prefs.getInt("paper_feed_lines", 3)
                baos.write("\n\n".toByteArray(Charsets.ISO_8859_1))
                baos.write(byteArrayOf(0x1b, 0x64, paperFeedLines.toByte())) // Feed configurable lines
                baos.write(byteArrayOf(0x1d, 0x56, 0x42, 0x00)) // Cut
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
    private fun convertBitmapToEscPos(bitmap: android.graphics.Bitmap, sizePercent: Int, printerDotsWidth: Int): ByteArray {
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
            canvas.drawColor(android.graphics.Color.WHITE) // White background
            canvas.drawBitmap(scaledBitmap, 0f, 0f, null)

            val monoBitmap = convertToMonochrome(bitmapWithBg)

            // ESC/POS raster image command
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
            Log.e(TAG, "Error converting bitmap to ESC/POS", e)
        }

        return baos.toByteArray()
    }

    /**
     * Convert bitmap to monochrome for thermal printing
     */
    private fun convertToMonochrome(bitmap: android.graphics.Bitmap): android.graphics.Bitmap {
        val monoBitmap = android.graphics.Bitmap.createBitmap(bitmap.width, bitmap.height, android.graphics.Bitmap.Config.ARGB_8888)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val gray = (android.graphics.Color.red(pixel) * 0.299 +
                           android.graphics.Color.green(pixel) * 0.587 +
                           android.graphics.Color.blue(pixel) * 0.114).toInt()
                val monoColor = if (gray < 128) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                monoBitmap.setPixel(x, y, monoColor)
            }
        }
        return monoBitmap
    }

    private fun centerText(text: String, width: Int): String {
        return if (text.length >= width) {
            text.substring(0, width)
        } else {
            val padding = (width - text.length) / 2
            " ".repeat(padding) + text
        }
    }

    /**
     * Generate QR code bitmap from text/URL
     */
    private fun generateQRCode(text: String, size: Int = 300): android.graphics.Bitmap? {
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
            Log.e(TAG, "Error generating QR code", e)
            null
        }
    }

    fun getPairedDevices(): List<String> {
        val devices = mutableListOf<String>()
        try {
            // Check permissions for Android 12+
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.BLUETOOTH_CONNECT
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    Log.e(TAG, "BLUETOOTH_CONNECT permission not granted")
                    return devices
                }
            }

            bluetoothAdapter?.bondedDevices?.forEach { device ->
                devices.add(device.name)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException getting paired devices", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting paired devices", e)
        }
        return devices
    }

    fun isConnected(): Boolean {
        return bluetoothSocket?.isConnected == true
    }

    private fun printWithDantSuLibrary(cartItems: List<CartItem>, total: Double, timestamp: String) {
        try {
            // Check for storage permission required for DantSu library image processing
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                throw StoragePermissionRequiredException("Storage permission is required for printing with images")
            }

            if (bluetoothSocket == null || !bluetoothSocket!!.isConnected) {
                Log.e(TAG, "DantSu ESC/POS: Not connected to printer")
                throw IOException("Not connected to printer")
            }

            // Use DantSu library's BluetoothConnection with our connected socket
            val deviceName = bluetoothSocket!!.remoteDevice?.name ?: "Unknown"

            // Find the printer in DantSu's connection list
            val connection = try {
                com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections.selectFirstPaired()
                    ?: throw IOException("No paired Bluetooth printer found")
            } catch (e: Exception) {
                Log.w(TAG, "DantSu couldn't find paired printer, using fallback")
                // Fallback: generate ESC/POS bytes directly and send via our socket
                val formattedText = buildDantSuFormattedReceipt(cartItems, total, timestamp)
                val escPosBytes = generateDantSuBytes(formattedText)
                outputStream?.write(escPosBytes)
                outputStream?.flush()
                Log.d(TAG, "DantSu ESC/POS: Receipt printed via fallback method")
                return
            }

            // Create DantSu printer instance
            val printer = com.dantsu.escposprinter.EscPosPrinter(
                connection,
                203, // DPI - standard for thermal printers
                48f, // Width in mm (58mm paper)
                32  // Characters per line
            )

            // Build formatted receipt text using DantSu markup
            val formattedText = buildDantSuFormattedReceipt(cartItems, total, timestamp)

            // Print the receipt with paper feed and cut
            printer.printFormattedTextAndCut(formattedText, 5f)

            Log.d(TAG, "DantSu ESC/POS: Receipt printed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "DantSu ESC/POS: Print failed", e)
            throw e
        }
    }

    private fun generateDantSuBytes(formattedText: String): ByteArray {
        // Simple converter for DantSu markup to ESC/POS
        val baos = java.io.ByteArrayOutputStream()
        baos.write(byteArrayOf(0x1b, 0x40)) // Initialize

        // Remove markup tags and convert to plain ESC/POS
        val plainText = formattedText
            .replace(Regex("\\[C]"), "")
            .replace(Regex("\\[L]"), "")
            .replace(Regex("\\[R]"), "")
            .replace(Regex("<font size='big'>"), "")
            .replace(Regex("<font size='wide'>"), "")
            .replace(Regex("</font>"), "")
            .replace(Regex("<b>"), "")
            .replace(Regex("</b>"), "")
            .replace(Regex("<u>"), "")
            .replace(Regex("</u>"), "")

        baos.write(plainText.toByteArray(Charsets.ISO_8859_1))
        baos.write(byteArrayOf(0x1b, 0x64, 0x03)) // Feed
        baos.write(byteArrayOf(0x1d, 0x56, 0x42, 0x00)) // Cut

        return baos.toByteArray()
    }

    private fun buildDantSuFormattedReceipt(cartItems: List<CartItem>, total: Double, timestamp: String): String {
        val sb = StringBuilder()

        // Header - Centered, large font
        sb.append("[C]<font size='big'>CANDY KUSH</font>\n")
        sb.append("[C]================================\n")
        sb.append("[L]\n")

        // Items header
        sb.append("[L]<b>ITEM</b>[C]<b>QTY</b>[R]<b>TOTAL</b>\n")
        sb.append("[L]--------------------------------\n")

        // Print each item
        for (item in cartItems) {
            val itemName = if (item.name.length > 20) {
                item.name.substring(0, 17) + "..."
            } else {
                item.name
            }

            // Item name on one line
            sb.append("[L]${itemName}\n")

            // Quantity and total on next line, aligned
            val qtyStr = String.format("%.1f", item.quantity)
            val totalStr = String.format("฿%.2f", item.total)
            sb.append("[L]  [C]${qtyStr}[R]${totalStr}\n")
        }

        sb.append("[L]--------------------------------\n")
        sb.append("[L]\n")

        // Total - Bold and larger
        sb.append("[L]<font size='wide'><b>TOTAL</b></font>[R]<font size='wide'><b>${String.format("฿%.2f", total)}</b></font>\n")
        sb.append("[L]\n")
        sb.append("[C]================================\n")
        sb.append("[L]\n")

        // QR code if link is provided - DantSu library will handle QR generation
        val prefs = context.getSharedPreferences("printer_prefs", Context.MODE_PRIVATE)
        val qrLink = prefs.getString("qr_link", "")
        if (!qrLink.isNullOrEmpty()) {
            sb.append("[C]<qrcode>$qrLink</qrcode>\n")
            sb.append("[L]\n")
        }

        // Footer - Centered
        val footer = prefs.getString("receipt_footer", "Thank you!")
        sb.append("[C]<u>$footer</u>\n")
        sb.append("[C]${timestamp}\n")
        sb.append("[L]\n")

        return sb.toString()
    }

    private fun buildTextData(text: String, protocol: String): ByteArray {
        val baos = java.io.ByteArrayOutputStream()

        // Load paper size settings
        val prefs = context.getSharedPreferences("printer_prefs", Context.MODE_PRIVATE)
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

    private fun printTextWithDantSuLibrary(text: String) {
        try {
            // Check for storage permission required for DantSu library image processing
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                throw StoragePermissionRequiredException("Storage permission is required for printing with images")
            }

            if (bluetoothSocket == null || !bluetoothSocket!!.isConnected) {
                Log.e(TAG, "DantSu ESC/POS: Not connected to printer")
                throw IOException("Not connected to printer")
            }

            // Try to use DantSu library
            val connection = try {
                com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections.selectFirstPaired()
                    ?: throw IOException("No paired Bluetooth printer found")
            } catch (e: Exception) {
                Log.w(TAG, "DantSu couldn't find paired printer, using fallback")
                // Fallback: generate ESC/POS bytes directly and send via our socket
                val escPosBytes = generateDantSuTextBytes(text)
                outputStream?.write(escPosBytes)
                outputStream?.flush()
                Log.d(TAG, "DantSu ESC/POS: Text printed via fallback method")
                return
            }

            // Create DantSu printer instance
            val printer = com.dantsu.escposprinter.EscPosPrinter(
                connection,
                203, // DPI - standard for thermal printers
                48f, // Width in mm (58mm paper)
                32  // Characters per line
            )

            // Print the text with paper feed and cut
            printer.printFormattedTextAndCut(text, 5f)

            Log.d(TAG, "DantSu ESC/POS: Text printed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "DantSu ESC/POS: Print text failed", e)
            throw e
        }
    }

    private fun generateDantSuTextBytes(text: String): ByteArray {
        // Simple converter for plain text to ESC/POS
        val baos = java.io.ByteArrayOutputStream()
        baos.write(byteArrayOf(0x1b, 0x40)) // Initialize

        baos.write(text.toByteArray(Charsets.ISO_8859_1))
        baos.write(byteArrayOf(0x1b, 0x64, 0x03)) // Feed
        baos.write(byteArrayOf(0x1d, 0x56, 0x42, 0x00)) // Cut

        return baos.toByteArray()
    }
}
