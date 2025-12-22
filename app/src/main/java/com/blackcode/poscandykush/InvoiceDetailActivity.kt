package com.blackcode.poscandykush

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class InvoiceDetailActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var toolbar: Toolbar
    private lateinit var progressBar: ProgressBar
    private lateinit var scrollView: ScrollView
    private lateinit var tvEmpty: TextView
    private lateinit var tvInvoiceNumber: TextView
    private lateinit var tvInvoiceDate: TextView
    private lateinit var tvDueDate: TextView
    private lateinit var tvCustomerName: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvTotalAmount: TextView
    private lateinit var rvInvoiceItems: RecyclerView
    private lateinit var statusBarBackground: View
    private lateinit var btnEditInvoice: Button
    private lateinit var btnDeleteInvoice: Button
    private lateinit var btnMarkPaid: Button
    private lateinit var btnMarkCancelled: Button

    private lateinit var invoiceItemAdapter: InvoiceItemAdapter
    private var currentInvoice: Invoice? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_invoice_detail)

        initializeViews()
        setupStatusBar()

        prefs = getSharedPreferences("admin_prefs", MODE_PRIVATE)

        setupToolbar()
        setupRecyclerView()
        setupButtons()

        val invoiceId = intent.getStringExtra("invoice_id")
        android.util.Log.d("InvoiceDetailActivity", "onCreate - Received invoice_id: $invoiceId")

        if (invoiceId != null && invoiceId.isNotEmpty()) {
            android.util.Log.d("InvoiceDetailActivity", "Invoice ID is valid, loading details...")
            loadInvoiceDetail(invoiceId)
        } else {
            android.util.Log.e("InvoiceDetailActivity", "Invoice ID is null or empty!")
            showEmptyState("No invoice ID provided.\n\nPlease return to invoice list and try again.")
            Toast.makeText(this, "Error: No invoice ID", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupStatusBar() {
        window.statusBarColor = ContextCompat.getColor(this, R.color.primary_green)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.white)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                0,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
            window.insetsController?.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        progressBar = findViewById(R.id.progress_bar)
        scrollView = findViewById(R.id.scroll_view)
        tvEmpty = findViewById(R.id.tv_empty)
        tvInvoiceNumber = findViewById(R.id.tv_invoice_number)
        tvInvoiceDate = findViewById(R.id.tv_invoice_date)
        tvDueDate = findViewById(R.id.tv_due_date)
        tvCustomerName = findViewById(R.id.tv_customer_name)
        tvStatus = findViewById(R.id.tv_invoice_number) // Reuse invoice number view for status temporarily
        tvTotalAmount = findViewById(R.id.tv_total_amount)
        rvInvoiceItems = findViewById(R.id.rv_invoice_items)
        statusBarBackground = findViewById(R.id.status_bar_background)
        btnEditInvoice = findViewById(R.id.btn_edit_invoice)
        btnDeleteInvoice = findViewById(R.id.btn_delete_invoice)

        // Use edit and delete buttons for status changes
        btnMarkPaid = btnEditInvoice
        btnMarkCancelled = btnDeleteInvoice

        val statusBarHeight = getStatusBarHeight()
        statusBarBackground.layoutParams.height = statusBarHeight
    }


    private fun setupToolbar() {
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        rvInvoiceItems.layoutManager = LinearLayoutManager(this)
        invoiceItemAdapter = InvoiceItemAdapter(mutableListOf(), {}, false)
        rvInvoiceItems.adapter = invoiceItemAdapter
    }

    private fun setupButtons() {
        btnMarkPaid.setOnClickListener {
            currentInvoice?.let { invoice ->
                if (invoice.status != "paid") {
                    showStatusChangeDialog(invoice, "paid")
                } else {
                    Toast.makeText(this, "Invoice is already paid", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnMarkCancelled.setOnClickListener {
            currentInvoice?.let { invoice ->
                showStatusChangeDialog(invoice, "cancelled")
            }
        }
    }

    private fun showStatusChangeDialog(invoice: Invoice, newStatus: String) {
        val statusText = when (newStatus) {
            "paid" -> "Paid"
            "cancelled" -> "Cancelled"
            else -> newStatus
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Change Invoice Status")
            .setMessage("Mark invoice ${invoice.number} as $statusText?")
            .setPositiveButton("Confirm") { _, _ ->
                updateInvoiceStatus(invoice, newStatus)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadInvoiceDetail(invoiceId: String) {
        showProgress(true)

        android.util.Log.d("InvoiceDetailActivity", "========================================")
        android.util.Log.d("InvoiceDetailActivity", "Loading invoice detail for ID: $invoiceId")
        android.util.Log.d("InvoiceDetailActivity", "========================================")

        lifecycleScope.launch(Dispatchers.Main) {
            try {
                val invoice = fetchInvoiceDetail(invoiceId)
                if (invoice != null) {
                    android.util.Log.d("InvoiceDetailActivity", "✅ SUCCESS: Invoice fetched, updating UI")
                    currentInvoice = invoice
                    updateUI(invoice)
                    showProgress(false)
                } else {
                    android.util.Log.e("InvoiceDetailActivity", "❌ ERROR: Invoice is null after fetch")
                    showProgress(false)
                    showEmptyState(
                        "Invoice not found!\n\n" +
                        "Invoice ID: $invoiceId\n\n" +
                        "Possible reasons:\n" +
                        "• Invoice doesn't exist in database\n" +
                        "• Wrong invoice ID\n" +
                        "• API connection issue\n\n" +
                        "Check Logcat for details:\n" +
                        "adb logcat | grep InvoiceDetail"
                    )
                    Toast.makeText(
                        this@InvoiceDetailActivity,
                        "Invoice not found. Check Logcat for details.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                showProgress(false)
                android.util.Log.e("InvoiceDetailActivity", "❌ EXCEPTION in loadInvoiceDetail", e)
                val errorMsg =
                    "Failed to load invoice!\n\n" +
                    "Invoice ID: $invoiceId\n" +
                    "Error: ${e.message}\n\n" +
                    "Check your:\n" +
                    "• Internet connection\n" +
                    "• API server status\n" +
                    "• JWT token validity\n\n" +
                    "Logcat command:\n" +
                    "adb logcat | grep InvoiceDetail"
                showEmptyState(errorMsg)
                Toast.makeText(this@InvoiceDetailActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun fetchInvoiceDetail(invoiceId: String): Invoice? {
        return withContext(Dispatchers.IO) {
            try {
                val token = prefs.getString("jwt_token", "") ?: ""
                android.util.Log.d("InvoiceDetailActivity", "")
                android.util.Log.d("InvoiceDetailActivity", "╔════════════════════════════════════════╗")
                android.util.Log.d("InvoiceDetailActivity", "║   FETCHING INVOICE DETAILS             ║")
                android.util.Log.d("InvoiceDetailActivity", "╚════════════════════════════════════════╝")
                android.util.Log.d("InvoiceDetailActivity", "Invoice ID: $invoiceId")
                android.util.Log.d("InvoiceDetailActivity", "Token available: ${token.isNotEmpty()}")
                android.util.Log.d("InvoiceDetailActivity", "Token length: ${token.length}")

                val apiService = InvoiceApiService()
                val response = apiService.getInvoice(token, invoiceId)

                android.util.Log.d("InvoiceDetailActivity", "")
                android.util.Log.d("InvoiceDetailActivity", "╔════════════════════════════════════════╗")
                android.util.Log.d("InvoiceDetailActivity", "║   API RESPONSE                         ║")
                android.util.Log.d("InvoiceDetailActivity", "╚════════════════════════════════════════╝")
                android.util.Log.d("InvoiceDetailActivity", "Response object: ${response != null}")
                android.util.Log.d("InvoiceDetailActivity", "Success: ${response?.success}")
                android.util.Log.d("InvoiceDetailActivity", "Error: ${response?.error}")
                android.util.Log.d("InvoiceDetailActivity", "Action: ${response?.action}")
                android.util.Log.d("InvoiceDetailActivity", "Generated at: ${response?.generatedAt}")
                android.util.Log.d("InvoiceDetailActivity", "Data exists: ${response?.data != null}")
                android.util.Log.d("InvoiceDetailActivity", "Invoice exists: ${response?.data?.invoice != null}")

                if (response?.data != null) {
                    android.util.Log.d("InvoiceDetailActivity", "Data class: ${response.data.javaClass.simpleName}")
                }

                if (response?.success == true) {
                    // Invoice is nested under data.invoice
                    val invoice = response.data?.invoice

                    if (invoice != null) {
                        android.util.Log.d("InvoiceDetailActivity", "")
                        android.util.Log.d("InvoiceDetailActivity", "╔════════════════════════════════════════╗")
                        android.util.Log.d("InvoiceDetailActivity", "║   INVOICE FOUND ✅                     ║")
                        android.util.Log.d("InvoiceDetailActivity", "╚════════════════════════════════════════╝")
                        android.util.Log.d("InvoiceDetailActivity", "ID: ${invoice.id}")
                        android.util.Log.d("InvoiceDetailActivity", "Number: ${invoice.number}")
                        android.util.Log.d("InvoiceDetailActivity", "Status: ${invoice.status}")
                        android.util.Log.d("InvoiceDetailActivity", "Customer: ${invoice.customerName}")
                        android.util.Log.d("InvoiceDetailActivity", "Date: ${invoice.date}")
                        android.util.Log.d("InvoiceDetailActivity", "Due Date: ${invoice.dueDate}")
                        android.util.Log.d("InvoiceDetailActivity", "Total: ${invoice.total}")
                        android.util.Log.d("InvoiceDetailActivity", "Items count: ${invoice.items.size}")
                        android.util.Log.d("InvoiceDetailActivity", "")
                        invoice
                    } else {
                        android.util.Log.e("InvoiceDetailActivity", "")
                        android.util.Log.e("InvoiceDetailActivity", "╔════════════════════════════════════════╗")
                        android.util.Log.e("InvoiceDetailActivity", "║   INVOICE DATA IS NULL ❌              ║")
                        android.util.Log.e("InvoiceDetailActivity", "╚════════════════════════════════════════╝")
                        android.util.Log.e("InvoiceDetailActivity", "Response was successful but data is null!")
                        android.util.Log.e("InvoiceDetailActivity", "This means the API returned success:true but no invoice data")
                        android.util.Log.e("InvoiceDetailActivity", "")
                        null
                    }
                } else {
                    android.util.Log.e("InvoiceDetailActivity", "")
                    android.util.Log.e("InvoiceDetailActivity", "╔════════════════════════════════════════╗")
                    android.util.Log.e("InvoiceDetailActivity", "║   API RETURNED FAILURE ❌              ║")
                    android.util.Log.e("InvoiceDetailActivity", "╚════════════════════════════════════════╝")
                    android.util.Log.e("InvoiceDetailActivity", "Success: false")
                    android.util.Log.e("InvoiceDetailActivity", "Error message: ${response?.error}")
                    android.util.Log.e("InvoiceDetailActivity", "")
                    null
                }
            } catch (e: Exception) {
                android.util.Log.e("InvoiceDetailActivity", "")
                android.util.Log.e("InvoiceDetailActivity", "╔════════════════════════════════════════╗")
                android.util.Log.e("InvoiceDetailActivity", "║   EXCEPTION OCCURRED ❌                ║")
                android.util.Log.e("InvoiceDetailActivity", "╚════════════════════════════════════════╝")
                android.util.Log.e("InvoiceDetailActivity", "Exception type: ${e.javaClass.simpleName}")
                android.util.Log.e("InvoiceDetailActivity", "Message: ${e.message}")
                android.util.Log.e("InvoiceDetailActivity", "Cause: ${e.cause}")
                android.util.Log.e("InvoiceDetailActivity", "Stack trace:", e)
                android.util.Log.e("InvoiceDetailActivity", "")
                null
            }
        }
    }

    private fun updateInvoiceStatus(invoice: Invoice, newStatus: String) {
        showProgress(true)

        lifecycleScope.launch(Dispatchers.Main) {
            try {
                val success = performStatusUpdate(invoice.id, newStatus)
                showProgress(false)

                if (success) {
                    val statusText = when (newStatus) {
                        "paid" -> "paid"
                        "cancelled" -> "cancelled"
                        else -> "updated"
                    }
                    Toast.makeText(this@InvoiceDetailActivity, "Invoice marked as $statusText", Toast.LENGTH_SHORT).show()

                    // Reload invoice to show updated status
                    loadInvoiceDetail(invoice.id)
                } else {
                    Toast.makeText(this@InvoiceDetailActivity, "Failed to update invoice status", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                showProgress(false)
                Toast.makeText(this@InvoiceDetailActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun performStatusUpdate(invoiceId: String, status: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val token = prefs.getString("jwt_token", "") ?: ""
                android.util.Log.d("InvoiceDetailActivity", "Updating invoice $invoiceId to status: $status")

                val apiService = InvoiceApiService()
                val response = apiService.updateInvoiceStatus(token, invoiceId, status)

                android.util.Log.d("InvoiceDetailActivity", "Status update response: ${response?.success}, error: ${response?.error}")
                response?.success == true
            } catch (e: Exception) {
                android.util.Log.e("InvoiceDetailActivity", "Exception updating status", e)
                false
            }
        }
    }

    private fun updateUI(invoice: Invoice) {
        // Format and display invoice information
        val statusText = invoice.getStatusText()
        tvInvoiceNumber.text = "Invoice ${invoice.number} - ${statusText}"
        tvInvoiceDate.text = "Date: ${formatDate(invoice.date)}"
        tvDueDate.text = if (!invoice.dueDate.isNullOrEmpty()) {
            "Due Date: ${formatDate(invoice.dueDate)}"
        } else {
            "Due Date: Not set"
        }
        tvCustomerName.text = "Customer: ${invoice.customerName}"
        tvTotalAmount.text = "Total: ${invoice.getFormattedTotal()}"

        // Set status color
        val statusColor = when (invoice.status.lowercase()) {
            "paid" -> getColor(R.color.status_completed)
            "pending" -> getColor(R.color.status_pending)
            "cancelled" -> getColor(R.color.error_red)
            else -> getColor(R.color.black)
        }
        tvInvoiceNumber.setTextColor(statusColor)

        // Update button labels and visibility based on status
        when (invoice.status.lowercase()) {
            "paid" -> {
                btnMarkPaid.text = "Paid ✓"
                btnMarkPaid.isEnabled = false
                btnMarkCancelled.text = "Delete"
            }
            "cancelled" -> {
                btnMarkPaid.text = "Mark Paid"
                btnMarkPaid.isEnabled = false
                btnMarkCancelled.text = "Cancelled"
                btnMarkCancelled.isEnabled = false
            }
            else -> { // pending
                btnMarkPaid.text = "Mark Paid"
                btnMarkPaid.isEnabled = true
                btnMarkCancelled.text = "Cancel Invoice"
                btnMarkCancelled.isEnabled = true
            }
        }

        invoiceItemAdapter.updateItems(invoice.items)

        scrollView.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE
    }

    private fun formatDate(dateString: String): String {
        return try {
            if (dateString.isEmpty()) return "Not set"

            val inputFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val outputFormat = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
            val date = inputFormat.parse(dateString)
            date?.let { outputFormat.format(it) } ?: dateString
        } catch (e: Exception) {
            android.util.Log.e("InvoiceDetailActivity", "Error formatting date: $dateString", e)
            dateString
        }
    }

    private fun showEmptyState(message: String) {
        tvEmpty.text = message
        tvEmpty.visibility = View.VISIBLE
        scrollView.visibility = View.GONE
        progressBar.visibility = View.GONE
    }

    private fun showProgress(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            scrollView.visibility = View.GONE
            tvEmpty.visibility = View.GONE
        }
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    private fun showDeleteConfirmationDialog(invoice: Invoice) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete Invoice")
            .setMessage("Are you sure you want to delete invoice ${invoice.number}? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteInvoice(invoice)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteInvoice(invoice: Invoice) {
        showProgress(true)

        lifecycleScope.launch(Dispatchers.Main) {
            try {
                val success = performDeleteInvoice(invoice.id)
                showProgress(false)

                if (success) {
                    Toast.makeText(this@InvoiceDetailActivity, "Invoice deleted successfully", Toast.LENGTH_SHORT).show()
                    finish() // Close the detail activity
                } else {
                    Toast.makeText(this@InvoiceDetailActivity, "Failed to delete invoice", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                showProgress(false)
                Toast.makeText(this@InvoiceDetailActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun performDeleteInvoice(invoiceId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val token = prefs.getString("jwt_token", "") ?: ""
                val url = URL("https://pos-candy-kush.vercel.app/api/mobile?action=delete-invoice&id=$invoiceId")
                val connection = url.openConnection() as HttpURLConnection

                connection.requestMethod = "DELETE"
                connection.setRequestProperty("Authorization", "Bearer $token")

                val responseCode = connection.responseCode
                connection.disconnect()

                responseCode == HttpURLConnection.HTTP_OK
            } catch (e: Exception) {
                false
            }
        }
    }
}
