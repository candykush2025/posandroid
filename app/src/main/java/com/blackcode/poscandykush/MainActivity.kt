package com.blackcode.poscandykush

import android.Manifest
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var displayManager: DisplayManager
    private var customerPresentation: CustomerPresentation? = null
    private lateinit var viewModel: CartViewModel
    private var hasCheckedPrinter = false

    // Print job polling (for single display setup)
    private val printApiService = PrintApiService()
    private var bluetoothPrinter: BluetoothThermalPrinter? = null
    private val handler = Handler(Looper.getMainLooper())
    private val printCheckRunnable = object : Runnable {
        override fun run() {
            checkForPrintJobMainActivity()
            handler.postDelayed(this, 2000) // Check every 2 seconds
        }
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) { showPresentationIfAvailable() }
        override fun onDisplayChanged(displayId: Int) { /* optional */ }
        override fun onDisplayRemoved(displayId: Int) {
            customerPresentation?.let {
                if (it.display.displayId == displayId) dismissPresentation()
            }
        }
    }

    private var pendingDownloadUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        // Immersive full-screen WebView on primary display
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)

        val webView: WebView = findViewById(R.id.webview)
        webView.webViewClient = WebViewClient()
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
        }
        webView.loadUrl("https://pos-candy-kush.vercel.app")

        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        viewModel = ViewModelProvider(this).get(CartViewModel::class.java)

        // Initialize Bluetooth thermal printer for single display setup
        bluetoothPrinter = BluetoothThermalPrinter(this)

        // Start polling cart
        viewModel.startPollingCart()

        // Start print job polling (for single display setup)
        handler.post(printCheckRunnable)
        android.util.Log.d("MainActivity", "🖨️ Started print job polling for single display setup")

        // Start update checker
        startUpdateChecker()

        // Observe cart updates and forward to presentation
        lifecycleScope.launch {
            viewModel.cartState.collect { cartResponse ->
                customerPresentation?.updateCart(cartResponse)
            }
        }

        lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                customerPresentation?.setLoading(isLoading)
            }
        }

        lifecycleScope.launch {
            viewModel.errorMessage.collect { error ->
                customerPresentation?.setError(error)
            }
        }
    }


    override fun onResume() {
        super.onResume()
        displayManager.registerDisplayListener(displayListener, null)
        showPresentationIfAvailable()

        // Check if printer is set up, if not, show printer settings (only check once per app launch)
        if (!hasCheckedPrinter) {
            hasCheckedPrinter = true
            val prefs = getSharedPreferences("printer_prefs", Context.MODE_PRIVATE)
            val setupCompleted = prefs.getBoolean("setup_completed", false)
            val setupSkipped = prefs.getBoolean("setup_skipped", false)
            if (!setupCompleted && !setupSkipped) {
                val intent = Intent(this, PrinterSetupActivity::class.java)
                startActivity(intent)
            }

            // Test print API on app start
            Thread {
                android.util.Log.d("MainActivity", "🧪 Testing Print API on app start...")
                val apiService = PrintApiService()
                val diagnostics = apiService.testPrintApiDiagnostics()
                android.util.Log.d("MainActivity", diagnostics)
            }.start()
        }
    }

    override fun onPause() {
        super.onPause()
        displayManager.unregisterDisplayListener(displayListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        dismissPresentation()
        // Stop print job polling
        handler.removeCallbacks(printCheckRunnable)
        android.util.Log.d("MainActivity", "🛑 Stopped print job polling")
    }

    private fun showPresentationIfAvailable() {
        val displays: Array<Display> = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        if (displays.isNotEmpty()) {
            val external = displays[0]
            if (customerPresentation?.display?.displayId != external.displayId) {
                dismissPresentation()
                customerPresentation = CustomerPresentation(this, external, object : ToastCallback {
                    override fun showToast(message: String) {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }, object : PrinterSettingsCallback {
                    override fun launchPrinterSettings() {
                        val intent = android.content.Intent(this@MainActivity, PrinterSetupActivity::class.java)
                        startActivity(intent)
                    }
                })
                customerPresentation?.show()
            }
        } else {
            dismissPresentation()
        }
    }

    private fun dismissPresentation() {
        try {
            customerPresentation?.dismiss()
        } catch (_: Throwable) { }
        customerPresentation = null
    }

    private fun startUpdateChecker() {
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                checkForUpdate()
                handler.postDelayed(this, 30000) // 30 seconds
            }
        }
        handler.post(runnable)
    }

    private fun checkForUpdate() {
        Thread {
            try {
                val url = URL("https://pos-candy-kush.vercel.app/api/apk")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val latestVersion = json.getString("version")
                val latestVersionCode = json.getInt("versionCode")
                val packageManager = packageManager
                val packageInfo = packageManager.getPackageInfo(packageName, 0)
                val currentVersionCode = packageInfo.versionCode
                if (latestVersionCode > currentVersionCode) {
                    runOnUiThread {
                        Toast.makeText(this, "New version available: $latestVersion", Toast.LENGTH_SHORT).show()
                    }
                    downloadAndInstall(json.getString("downloadUrl"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun downloadAndInstall(downloadUrl: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingDownloadUrl = downloadUrl
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), UPDATE_STORAGE_PERMISSION_REQUEST_CODE)
            return
        }

        val fullUrl = "https://pos-candy-kush.vercel.app" + downloadUrl
        val request = DownloadManager.Request(Uri.parse(fullUrl))
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "candy-kush-pos-update.apk")
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        // Register receiver for download complete
        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    installApk()
                }
            }
        }
        ContextCompat.registerReceiver(this, onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun installApk() {
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "candy-kush-pos-update.apk")
        if (file.exists()) {
            AlertDialog.Builder(this)
                .setTitle("Update Downloaded")
                .setMessage("A new version has been downloaded. Install now?")
                .setPositiveButton("Install") { _, _ ->
                    val intent = Intent(Intent.ACTION_VIEW)
                    val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
                    intent.setDataAndType(uri, "application/vnd.android.package-archive")
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                    startActivity(intent)
                }
                .setNegativeButton("Later", null)
                .show()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }

    /**
     * Check for print jobs in MainActivity (single display setup)
     * This method handles print jobs when no secondary display is connected
     */
    private fun checkForPrintJobMainActivity() {
        Thread {
            try {
                android.util.Log.d("MainActivity", "📡 [MAIN POLL] Checking for print jobs...")
                val printResponse = printApiService.getPrintJob()

                android.util.Log.d("MainActivity", "📡 [MAIN POLL RESPONSE]")
                android.util.Log.d("MainActivity", "  - success: ${printResponse?.success}")
                android.util.Log.d("MainActivity", "  - jobId: ${printResponse?.jobId}")
                android.util.Log.d("MainActivity", "  - has data: ${printResponse?.data != null}")

                if (printResponse?.success == true && printResponse.data != null && printResponse.jobId != null) {
                    android.util.Log.e("MainActivity", "✅ [PRINT JOB FOUND IN MAIN] Job: ${printResponse.jobId}")

                    runOnUiThread {
                        Toast.makeText(this, "🖨️ Print job received: ${printResponse.jobId}", Toast.LENGTH_LONG).show()
                    }

                    // Handle the print job using the same logic as CustomerPresentation
                    handlePrintJobInMain(printResponse.data, printResponse.jobId)
                } else {
                    android.util.Log.d("MainActivity", "⏭️  [NO JOB IN MAIN] No pending print jobs")
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "❌ [MAIN POLL ERROR] ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(this, "⚠️ Print check error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    /**
     * Handle print job in MainActivity (mirrors CustomerPresentation logic)
     */
    private fun handlePrintJobInMain(printData: PrintData, jobId: String) {
        try {
            android.util.Log.e("MainActivity", "🖨️🖨️🖨️ PRINT JOB RECEIVED IN MAIN! Job ID: $jobId 🖨️🖨️🖨️")

            val prefs = getSharedPreferences("printer_prefs", Context.MODE_PRIVATE)
            val savedPrinter = prefs.getString("selected_printer", null)

            android.util.Log.e("MainActivity", "Saved printer from prefs: $savedPrinter")

            val printerType = when {
                savedPrinter?.startsWith("Bluetooth: ") == true -> "bluetooth"
                savedPrinter?.startsWith("USB: ") == true -> "usb"
                savedPrinter?.startsWith("Network: ") == true -> "network"
                else -> "unknown"
            }

            val deviceName = savedPrinter?.substringAfter(": ")

            if (deviceName == null) {
                val errorMsg = "❌ NO PRINTER CONFIGURED - Please configure printer in settings"
                android.util.Log.e("MainActivity", errorMsg)
                runOnUiThread {
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
                }
                Thread {
                    printApiService.reportFailed(jobId, errorMsg)
                }.start()
                return
            }

            runOnUiThread {
                Toast.makeText(this, "🔄 Connecting to printer: $deviceName", Toast.LENGTH_SHORT).show()
            }

            // Check print job type and handle accordingly
            when (printData.type) {
                "shift_report" -> {
                    // Handle shift report printing
                    val shiftReport = printData.shiftReport
                    if (shiftReport == null) {
                        val errorMsg = "No shift report data in print job"
                        android.util.Log.e("MainActivity", errorMsg)
                        runOnUiThread {
                            Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
                        }
                        Thread {
                            printApiService.reportFailed(jobId, errorMsg)
                        }.start()
                        return
                    }

                    // Format shift report text
                    val shiftReportText = formatShiftReport(shiftReport, printData.cashier ?: "Unknown", printData.timestamp ?: "")

                    var printSuccess = false
                    var printError: String? = null

                    when (printerType) {
                        "bluetooth" -> {
                            android.util.Log.d("MainActivity", "Attempting Bluetooth print for shift report to: $deviceName")
                            if (bluetoothPrinter?.isConnected() == false) {
                                val connected = bluetoothPrinter?.connectToDevice(deviceName)
                                android.util.Log.d("MainActivity", "Bluetooth connect result: $connected")
                                if (connected != true) {
                                    printError = "Failed to connect to Bluetooth printer: $deviceName"
                                    android.util.Log.e("MainActivity", printError)
                                    runOnUiThread {
                                        Toast.makeText(this, "❌ Failed to connect to printer", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }

                            if (printError == null) {
                                val protocol = prefs.getString("print_protocol", "Plain Text") ?: "Plain Text"
                                try {
                                    bluetoothPrinter?.printText(shiftReportText, protocol)
                                    printSuccess = true
                                    android.util.Log.d("MainActivity", "Shift report printed successfully via Bluetooth")
                                    runOnUiThread {
                                        Toast.makeText(this, "✅ Shift report printed successfully!", Toast.LENGTH_LONG).show()
                                    }
                                } catch (e: StoragePermissionRequiredException) {
                                    printError = "Storage permission required for printing"
                                    android.util.Log.e("MainActivity", printError, e)
                                    runOnUiThread {
                                        showStoragePermissionDialog()
                                    }
                                } catch (e: Exception) {
                                    printError = "Bluetooth print failed: ${e.message}"
                                    android.util.Log.e("MainActivity", printError, e)
                                    runOnUiThread {
                                        Toast.makeText(this, "❌ Print failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                        else -> {
                            printError = "Unsupported printer type: $printerType"
                            android.util.Log.e("MainActivity", printError)
                            runOnUiThread {
                                Toast.makeText(this, printError, Toast.LENGTH_LONG).show()
                            }
                        }
                    }

                    // Confirm print status to API
                    Thread {
                        if (printSuccess) {
                            val confirmed = printApiService.confirmPrinted(jobId)
                            android.util.Log.d("MainActivity", "Job $jobId confirmed result: $confirmed")
                        } else {
                            val reported = printApiService.reportFailed(jobId, printError ?: "Unknown error")
                            android.util.Log.d("MainActivity", "Job $jobId failed report result: $reported")
                        }
                    }.start()
                }
                "receipt" -> {
                    // Handle receipt printing (existing logic)
                    val order = printData.order
                    if (order == null) {
                        val errorMsg = "No order data in print job"
                        android.util.Log.e("MainActivity", errorMsg)
                        runOnUiThread {
                            Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
                        }
                        Thread {
                            printApiService.reportFailed(jobId, errorMsg)
                        }.start()
                        return
                    }

                    // Convert to CartItem list
                    val cartItems: List<CartItem> = when {
                        !order.line_items.isNullOrEmpty() -> {
                            order.line_items.map { item ->
                                CartItem(
                                    id = item.id ?: "",
                                    productId = item.item_id ?: "",
                                    name = item.item_name ?: "Unknown Item",
                                    quantity = item.quantity,
                                    price = item.price,
                                    total = item.total_money
                                )
                            }
                        }
                        !order.items.isNullOrEmpty() -> {
                            order.items.map { item ->
                                CartItem(
                                    id = "",
                                    productId = "",
                                    name = item.name ?: "Unknown Item",
                                    quantity = item.quantity.toDouble(),
                                    price = item.price,
                                    total = item.total
                                )
                            }
                        }
                        else -> {
                            val errorMsg = "No items in order"
                            android.util.Log.e("MainActivity", errorMsg)
                            runOnUiThread {
                                Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
                            }
                            Thread {
                                printApiService.reportFailed(jobId, errorMsg)
                            }.start()
                            return
                        }
                    }

                    val orderTotal = if (order.total_money > 0) order.total_money else order.total

                    android.util.Log.d("MainActivity", "Order Total: $orderTotal, Items: ${cartItems.size}")

                    var printSuccess = false
                    var printError: String? = null

                    when (printerType) {
                        "bluetooth" -> {
                            android.util.Log.d("MainActivity", "Attempting Bluetooth print to: $deviceName")
                            if (bluetoothPrinter?.isConnected() == false) {
                                val connected = bluetoothPrinter?.connectToDevice(deviceName)
                                android.util.Log.d("MainActivity", "Bluetooth connect result: $connected")
                                if (connected != true) {
                                    printError = "Failed to connect to Bluetooth printer: $deviceName"
                                    android.util.Log.e("MainActivity", printError)
                                    runOnUiThread {
                                        Toast.makeText(this, "❌ Failed to connect to printer", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }

                            if (printError == null) {
                                val protocol = prefs.getString("print_protocol", "Plain Text") ?: "Plain Text"
                                try {
                                    bluetoothPrinter?.printReceipt(
                                        cartItems,
                                        orderTotal,
                                        printData.timestamp ?: "",
                                        protocol
                                    )
                                    printSuccess = true
                                    android.util.Log.d("MainActivity", "Receipt printed successfully via Bluetooth")
                                    runOnUiThread {
                                        Toast.makeText(this, "✅ Receipt printed successfully!", Toast.LENGTH_LONG).show()
                                    }
                                } catch (e: StoragePermissionRequiredException) {
                                    printError = "Storage permission required for printing"
                                    android.util.Log.e("MainActivity", printError, e)
                                    runOnUiThread {
                                        showStoragePermissionDialog()
                                    }
                                } catch (e: Exception) {
                                    printError = "Bluetooth print failed: ${e.message}"
                                    android.util.Log.e("MainActivity", printError, e)
                                    runOnUiThread {
                                        Toast.makeText(this, "❌ Print failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                        else -> {
                            printError = "Unsupported printer type: $printerType"
                            android.util.Log.e("MainActivity", printError)
                            runOnUiThread {
                                Toast.makeText(this, printError, Toast.LENGTH_LONG).show()
                            }
                        }
                    }

                    // Confirm print status to API
                    Thread {
                        if (printSuccess) {
                            val confirmed = printApiService.confirmPrinted(jobId)
                            android.util.Log.d("MainActivity", "Job $jobId confirmed result: $confirmed")
                        } else {
                            val reported = printApiService.reportFailed(jobId, printError ?: "Unknown error")
                            android.util.Log.d("MainActivity", "Job $jobId failed report result: $reported")
                        }
                    }.start()
                }
                else -> {
                    val errorMsg = "Unknown print job type: ${printData.type}"
                    android.util.Log.e("MainActivity", errorMsg)
                    runOnUiThread {
                        Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
                    }
                    Thread {
                        printApiService.reportFailed(jobId, errorMsg)
                    }.start()
                }
            }

        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error handling print job in main", e)
            runOnUiThread {
                Toast.makeText(this, "❌ Print error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showStoragePermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Storage Permission Required")
            .setMessage("Storage permission is required to print receipts with images. Please grant permission to continue.")
            .setPositiveButton("Grant Permission") { _, _ ->
                requestStoragePermission()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun requestStoragePermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            STORAGE_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission granted. You can now print receipts.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permission denied. Printing with images may not work.", Toast.LENGTH_SHORT).show()
            }
        } else if (requestCode == UPDATE_STORAGE_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pendingDownloadUrl?.let { downloadAndInstall(it) }
                pendingDownloadUrl = null
            } else {
                Toast.makeText(this, "Permission denied. Update download failed.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun formatShiftReport(shiftReport: ShiftReportData, cashier: String, timestamp: String): String {
        val sb = StringBuilder()

        // Load settings from SharedPreferences
        val prefs = getSharedPreferences("printer_prefs", Context.MODE_PRIVATE)
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

    private fun centerText(text: String, width: Int): String {
        return if (text.length >= width) {
            text.substring(0, width)
        } else {
            val padding = (width - text.length) / 2
            " ".repeat(padding) + text
        }
    }

    companion object {
        private const val STORAGE_PERMISSION_REQUEST_CODE = 1001
        private const val UPDATE_STORAGE_PERMISSION_REQUEST_CODE = 1002
    }
}
