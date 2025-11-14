package com.blackcode.poscandykush

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.View
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.TextView
import android.widget.ProgressBar
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
    private var subtotalText: TextView? = null
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
    private val printCheckRunnable = object : Runnable {
        override fun run() {
            checkForPrintJob()
            handler.postDelayed(this, 2000) // Check every 2 seconds
        }
    }

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

        subtotalText = findViewById(R.id.subtotal_amount)
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

        // Initialize Bluetooth thermal printer
        bluetoothPrinter = BluetoothThermalPrinter(context)

        // Start polling for print jobs
        handler.post(printCheckRunnable)
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

                        val subtotal = cart.items.sumOf { it.total }
                        subtotalText?.text = String.format("฿%.2f", subtotal)

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

    private fun checkForPrintJob() {
        Thread {
            try {
                val printResponse = printApiService.getPrintJob()
                if (printResponse?.success == true && !printResponse.data.isNullOrEmpty()) {
                    android.util.Log.d("CustomerPresentation", "Print job found: ${printResponse.data}")
                    toastCallback?.showToast("Print job found")
                    handlePrintJob(printResponse.data)
                }
            } catch (e: Exception) {
                android.util.Log.e("CustomerPresentation", "Error checking print job", e)
                toastCallback?.showToast("Error checking print job: ${e.message}")
            }
        }.start()
    }

    private fun handlePrintJob(printData: String) {
        try {
            // Get saved printer, or extract from data, or first bonded, or default
            val savedPrinter = prefs.getString("selected_printer", null)
            val printerDeviceName = parsePrinterName(savedPrinter) ?: extractPrinterName(printData) ?: getFirstBondedDeviceName() ?: "Built-In Printer"

            if (bluetoothPrinter?.isConnected() == false) {
                val connected = bluetoothPrinter?.connectToDevice(printerDeviceName)
                if (!connected!!) {
                    android.util.Log.e("CustomerPresentation", "Failed to connect to printer: $printerDeviceName")
                    toastCallback?.showToast("Failed to connect to printer. Opening settings...")
                    printerSettingsCallback?.launchPrinterSettings()
                    return
                }
            }

            // Print the current cart if available
            if (currentCart != null) {
                bluetoothPrinter?.printReceipt(
                    currentCart!!.items,
                    currentCart!!.total,
                    currentCart!!.lastUpdated
                )
                android.util.Log.d("CustomerPresentation", "Receipt printed successfully")
                toastCallback?.showToast("Receipt printed successfully")
            } else {
                android.util.Log.w("CustomerPresentation", "No cart data available to print")
                toastCallback?.showToast("No cart data available to print")
            }
        } catch (e: Exception) {
            android.util.Log.e("CustomerPresentation", "Error handling print job", e)
            toastCallback?.showToast("Error handling print job: ${e.message}")
            printerSettingsCallback?.launchPrinterSettings()
        }
    }

    private fun parsePrinterName(savedPrinter: String?): String? {
        return when {
            savedPrinter?.startsWith("Bluetooth: ") == true -> savedPrinter.substringAfter("Bluetooth: ")
            savedPrinter?.startsWith("Wired: ") == true -> savedPrinter.substringAfter("Wired: ")
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
        handler.removeCallbacks(printCheckRunnable)
        bluetoothPrinter?.disconnect()
    }
}