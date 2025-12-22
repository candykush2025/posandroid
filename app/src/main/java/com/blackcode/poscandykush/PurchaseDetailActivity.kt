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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PurchaseDetailActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var toolbar: Toolbar
    private lateinit var progressBar: ProgressBar
    private lateinit var scrollView: ScrollView
    private lateinit var tvEmpty: TextView
    private lateinit var tvSupplierName: TextView
    private lateinit var tvPurchaseDate: TextView
    private lateinit var tvDueDate: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvTotalAmount: TextView
    private lateinit var tvReminderInfo: TextView
    private lateinit var rvPurchaseItems: RecyclerView
    private lateinit var statusBarBackground: View
    private lateinit var btnEditPurchase: Button
    private lateinit var btnDeletePurchase: Button

    private lateinit var purchaseItemAdapter: PurchaseItemAdapter
    private var currentPurchase: Purchase? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_purchase_detail)

        initializeViews()
        setupStatusBar()

        prefs = getSharedPreferences("admin_prefs", MODE_PRIVATE)

        setupToolbar()
        setupRecyclerView()
        setupButtons()

        val purchaseId = intent.getStringExtra("purchase_id")
        android.util.Log.d("PurchaseDetailActivity", "onCreate - Received purchase_id: $purchaseId")

        if (purchaseId != null && purchaseId.isNotEmpty()) {
            android.util.Log.d("PurchaseDetailActivity", "Purchase ID is valid, loading details...")
            loadPurchaseDetail(purchaseId)
        } else {
            android.util.Log.e("PurchaseDetailActivity", "Purchase ID is null or empty!")
            showEmptyState("No purchase ID provided.\n\nPlease return to purchasing list and try again.")
            Toast.makeText(this, "Error: No purchase ID", Toast.LENGTH_LONG).show()
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
        tvSupplierName = findViewById(R.id.tv_customer_name) // Use customer_name field for supplier
        tvPurchaseDate = findViewById(R.id.tv_invoice_date) // Use invoice_date for purchase date
        tvDueDate = findViewById(R.id.tv_due_date)
        tvStatus = findViewById(R.id.tv_invoice_number) // Use invoice_number for status
        tvTotalAmount = findViewById(R.id.tv_total_amount)
        tvReminderInfo = findViewById(R.id.tv_invoice_number) // Same as status for now
        rvPurchaseItems = findViewById(R.id.rv_invoice_items)
        statusBarBackground = findViewById(R.id.status_bar_background)
        btnEditPurchase = findViewById(R.id.btn_edit_invoice)
        btnDeletePurchase = findViewById(R.id.btn_delete_invoice)

        val statusBarHeight = getStatusBarHeight()
        statusBarBackground.layoutParams.height = statusBarHeight
    }

    private fun setupToolbar() {
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        rvPurchaseItems.layoutManager = LinearLayoutManager(this)
        purchaseItemAdapter = PurchaseItemAdapter(mutableListOf(), {}, false)
        rvPurchaseItems.adapter = purchaseItemAdapter
    }

    private fun setupButtons() {
        btnEditPurchase.setOnClickListener {
            currentPurchase?.let { purchase ->
                if (purchase.status == "pending") {
                    // If pending, this button acts as "Mark Complete"
                    showCompleteConfirmationDialog(purchase)
                } else {
                    // If completed, show edit message
                    Toast.makeText(this, "Edit feature coming soon", Toast.LENGTH_SHORT).show()
                    // TODO: Implement EditPurchaseActivity
                    // val intent = Intent(this@PurchaseDetailActivity, EditPurchaseActivity::class.java)
                    // intent.putExtra("purchase_id", purchase.id)
                    // startActivity(intent)
                }
            }
        }

        btnDeletePurchase.setOnClickListener {
            currentPurchase?.let { purchase ->
                showDeleteConfirmationDialog(purchase)
            }
        }
    }

    private fun loadPurchaseDetail(purchaseId: String) {
        showProgress(true)

        android.util.Log.d("PurchaseDetailActivity", "========================================")
        android.util.Log.d("PurchaseDetailActivity", "Loading purchase detail for ID: $purchaseId")
        android.util.Log.d("PurchaseDetailActivity", "========================================")

        lifecycleScope.launch(Dispatchers.Main) {
            try {
                val purchase = fetchPurchaseDetail(purchaseId)
                if (purchase != null) {
                    android.util.Log.d("PurchaseDetailActivity", "✅ SUCCESS: Purchase fetched, updating UI")
                    currentPurchase = purchase
                    updateUI(purchase)
                    showProgress(false)
                } else {
                    android.util.Log.e("PurchaseDetailActivity", "❌ ERROR: Purchase is null after fetch")
                    showProgress(false)
                    showEmptyState(
                        "Purchase not found!\n\n" +
                        "Purchase ID: $purchaseId\n\n" +
                        "Possible reasons:\n" +
                        "• Purchase doesn't exist in database\n" +
                        "• Wrong purchase ID\n" +
                        "• API connection issue\n\n" +
                        "Check Logcat for details:\n" +
                        "adb logcat | grep PurchaseDetail"
                    )
                    Toast.makeText(
                        this@PurchaseDetailActivity,
                        "Purchase not found. Check Logcat for details.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                showProgress(false)
                android.util.Log.e("PurchaseDetailActivity", "❌ EXCEPTION in loadPurchaseDetail", e)
                val errorMsg =
                    "Failed to load purchase!\n\n" +
                    "Purchase ID: $purchaseId\n" +
                    "Error: ${e.message}\n\n" +
                    "Check your:\n" +
                    "• Internet connection\n" +
                    "• API server status\n" +
                    "• JWT token validity\n\n" +
                    "Logcat command:\n" +
                    "adb logcat | grep PurchaseDetail"
                showEmptyState(errorMsg)
                Toast.makeText(this@PurchaseDetailActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun fetchPurchaseDetail(purchaseId: String): Purchase? {
        return withContext(Dispatchers.IO) {
            try {
                val token = prefs.getString("jwt_token", "") ?: ""
                android.util.Log.d("PurchaseDetailActivity", "")
                android.util.Log.d("PurchaseDetailActivity", "╔════════════════════════════════════════╗")
                android.util.Log.d("PurchaseDetailActivity", "║   FETCHING PURCHASE DETAILS            ║")
                android.util.Log.d("PurchaseDetailActivity", "╚════════════════════════════════════════╝")
                android.util.Log.d("PurchaseDetailActivity", "Purchase ID: $purchaseId")
                android.util.Log.d("PurchaseDetailActivity", "Token available: ${token.isNotEmpty()}")
                android.util.Log.d("PurchaseDetailActivity", "Token length: ${token.length}")

                val apiService = PurchaseApiService()
                val response = apiService.getPurchase(token, purchaseId)

                android.util.Log.d("PurchaseDetailActivity", "")
                android.util.Log.d("PurchaseDetailActivity", "╔════════════════════════════════════════╗")
                android.util.Log.d("PurchaseDetailActivity", "║   API RESPONSE                         ║")
                android.util.Log.d("PurchaseDetailActivity", "╚════════════════════════════════════════╝")
                android.util.Log.d("PurchaseDetailActivity", "Response object: ${response != null}")
                android.util.Log.d("PurchaseDetailActivity", "Success: ${response?.success}")
                android.util.Log.d("PurchaseDetailActivity", "Error: ${response?.error}")
                android.util.Log.d("PurchaseDetailActivity", "Data (purchase): ${response?.data}")

                if (response?.success == true) {
                    // Purchase is directly in data field (not nested!)
                    val purchase = response.data

                    if (purchase != null) {
                        android.util.Log.d("PurchaseDetailActivity", "")
                        android.util.Log.d("PurchaseDetailActivity", "╔════════════════════════════════════════╗")
                        android.util.Log.d("PurchaseDetailActivity", "║   PURCHASE FOUND ✅                    ║")
                        android.util.Log.d("PurchaseDetailActivity", "╚════════════════════════════════════════╝")
                        android.util.Log.d("PurchaseDetailActivity", "ID: ${purchase.id}")
                        android.util.Log.d("PurchaseDetailActivity", "Supplier: ${purchase.supplierName}")
                        android.util.Log.d("PurchaseDetailActivity", "Status: ${purchase.status}")
                        android.util.Log.d("PurchaseDetailActivity", "Date: ${purchase.date}")
                        android.util.Log.d("PurchaseDetailActivity", "Due Date: ${purchase.dueDate}")
                        android.util.Log.d("PurchaseDetailActivity", "Total: ${purchase.total}")
                        android.util.Log.d("PurchaseDetailActivity", "Items count: ${purchase.items.size}")
                        android.util.Log.d("PurchaseDetailActivity", "")
                        purchase
                    } else {
                        android.util.Log.e("PurchaseDetailActivity", "")
                        android.util.Log.e("PurchaseDetailActivity", "╔════════════════════════════════════════╗")
                        android.util.Log.e("PurchaseDetailActivity", "║   PURCHASE DATA IS NULL ❌             ║")
                        android.util.Log.e("PurchaseDetailActivity", "╚════════════════════════════════════════╝")
                        android.util.Log.e("PurchaseDetailActivity", "Response was successful but data is null!")
                        android.util.Log.e("PurchaseDetailActivity", "")
                        null
                    }
                } else {
                    android.util.Log.e("PurchaseDetailActivity", "")
                    android.util.Log.e("PurchaseDetailActivity", "╔════════════════════════════════════════╗")
                    android.util.Log.e("PurchaseDetailActivity", "║   API RETURNED FAILURE ❌              ║")
                    android.util.Log.e("PurchaseDetailActivity", "╚════════════════════════════════════════╝")
                    android.util.Log.e("PurchaseDetailActivity", "Success: false")
                    android.util.Log.e("PurchaseDetailActivity", "Error message: ${response?.error}")
                    android.util.Log.e("PurchaseDetailActivity", "")
                    null
                }
            } catch (e: Exception) {
                android.util.Log.e("PurchaseDetailActivity", "")
                android.util.Log.e("PurchaseDetailActivity", "╔════════════════════════════════════════╗")
                android.util.Log.e("PurchaseDetailActivity", "║   EXCEPTION OCCURRED ❌                ║")
                android.util.Log.e("PurchaseDetailActivity", "╚════════════════════════════════════════╝")
                android.util.Log.e("PurchaseDetailActivity", "Exception type: ${e.javaClass.simpleName}")
                android.util.Log.e("PurchaseDetailActivity", "Message: ${e.message}")
                android.util.Log.e("PurchaseDetailActivity", "Cause: ${e.cause}")
                android.util.Log.e("PurchaseDetailActivity", "Stack trace:", e)
                android.util.Log.e("PurchaseDetailActivity", "")
                null
            }
        }
    }

    private fun updateUI(purchase: Purchase) {
        // Show status in the invoice_number field (top)
        val statusText = "Purchase #${purchase.id.take(8)} - ${purchase.getStatusText()}"
        tvStatus.text = statusText

        // Set status color
        val statusColor = when (purchase.status) {
            "completed" -> getColor(R.color.status_completed)
            "pending" -> getColor(R.color.status_pending)
            else -> getColor(R.color.black)
        }
        tvStatus.setTextColor(statusColor)

        // Show supplier in customer_name field
        tvSupplierName.text = "Supplier: ${purchase.supplierName}"

        // Handle purchase date
        tvPurchaseDate.text = if (purchase.date.isNotEmpty()) {
            "Purchase Date: ${formatDate(purchase.date)}"
        } else {
            "Purchase Date: Not set"
        }

        // Handle due date
        tvDueDate.text = if (purchase.dueDate.isNotEmpty()) {
            "Due Date: ${formatDate(purchase.dueDate)}"
        } else {
            "Due Date: Not set"
        }

        // Show total
        tvTotalAmount.text = "Total: ${purchase.getFormattedTotal()}"

        // Update items
        purchaseItemAdapter.updateItems(purchase.items)

        // Update button text and behavior based on status
        if (purchase.status == "completed") {
            btnEditPurchase.text = "Edit Purchase"
            btnEditPurchase.isEnabled = false
            btnEditPurchase.alpha = 0.5f // Make it look disabled
        } else {
            btnEditPurchase.text = "Mark Complete"
            btnEditPurchase.isEnabled = true
            btnEditPurchase.alpha = 1.0f
        }

        scrollView.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE
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

    private fun showDeleteConfirmationDialog(purchase: Purchase) {
        AlertDialog.Builder(this)
            .setTitle("Delete Purchase")
            .setMessage("Are you sure you want to delete this purchase from ${purchase.supplierName}? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deletePurchase(purchase)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCompleteConfirmationDialog(purchase: Purchase) {
        AlertDialog.Builder(this)
            .setTitle("Mark as Complete")
            .setMessage("Mark purchase from ${purchase.supplierName} as completed?")
            .setPositiveButton("Complete") { _, _ ->
                completePurchase(purchase)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deletePurchase(purchase: Purchase) {
        showProgress(true)

        lifecycleScope.launch(Dispatchers.Main) {
            try {
                val success = performDeletePurchase(purchase.id)
                showProgress(false)

                if (success) {
                    Toast.makeText(this@PurchaseDetailActivity, "Purchase deleted successfully", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                } else {
                    Toast.makeText(this@PurchaseDetailActivity, "Failed to delete purchase", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                showProgress(false)
                Toast.makeText(this@PurchaseDetailActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun completePurchase(purchase: Purchase) {
        showProgress(true)

        android.util.Log.d("PurchaseDetailActivity", "")
        android.util.Log.d("PurchaseDetailActivity", "╔════════════════════════════════════════╗")
        android.util.Log.d("PurchaseDetailActivity", "║   COMPLETING PURCHASE                  ║")
        android.util.Log.d("PurchaseDetailActivity", "╚════════════════════════════════════════╝")
        android.util.Log.d("PurchaseDetailActivity", "Purchase ID: ${purchase.id}")
        android.util.Log.d("PurchaseDetailActivity", "Current Status: ${purchase.status}")
        android.util.Log.d("PurchaseDetailActivity", "Supplier: ${purchase.supplierName}")

        lifecycleScope.launch(Dispatchers.Main) {
            try {
                val success = performCompletePurchase(purchase.id)
                showProgress(false)

                android.util.Log.d("PurchaseDetailActivity", "")
                android.util.Log.d("PurchaseDetailActivity", "╔════════════════════════════════════════╗")
                android.util.Log.d("PurchaseDetailActivity", "║   COMPLETE PURCHASE RESULT             ║")
                android.util.Log.d("PurchaseDetailActivity", "╚════════════════════════════════════════╝")
                android.util.Log.d("PurchaseDetailActivity", "Success: $success")

                if (success) {
                    Toast.makeText(this@PurchaseDetailActivity, "Purchase completed successfully", Toast.LENGTH_SHORT).show()
                    android.util.Log.d("PurchaseDetailActivity", "Reloading purchase details to update UI...")
                    loadPurchaseDetail(purchase.id) // Reload to update UI
                } else {
                    android.util.Log.e("PurchaseDetailActivity", "Failed to complete purchase - API returned false")
                    Toast.makeText(this@PurchaseDetailActivity, "Failed to complete purchase", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                showProgress(false)
                android.util.Log.e("PurchaseDetailActivity", "Exception in completePurchase", e)
                Toast.makeText(this@PurchaseDetailActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun performDeletePurchase(purchaseId: String): Boolean {
        return withContext(Dispatchers.IO) {
            val token = prefs.getString("jwt_token", "") ?: ""
            val apiService = PurchaseApiService()
            val response = apiService.deletePurchase(token, purchaseId)
            response?.success == true
        }
    }

    private suspend fun performCompletePurchase(purchaseId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val token = prefs.getString("jwt_token", "") ?: ""
                android.util.Log.d("PurchaseDetailActivity", "Calling API to complete purchase...")
                android.util.Log.d("PurchaseDetailActivity", "Token available: ${token.isNotEmpty()}")

                val apiService = PurchaseApiService()
                val response = apiService.completePurchase(token, purchaseId)

                android.util.Log.d("PurchaseDetailActivity", "API Response received")
                android.util.Log.d("PurchaseDetailActivity", "Response success: ${response?.success}")
                android.util.Log.d("PurchaseDetailActivity", "Response error: ${response?.error}")
                android.util.Log.d("PurchaseDetailActivity", "Updated purchase status: ${response?.data?.status}")

                response?.success == true
            } catch (e: Exception) {
                android.util.Log.e("PurchaseDetailActivity", "Exception in performCompletePurchase", e)
                false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        currentPurchase?.let {
            loadPurchaseDetail(it.id)
        }
    }

    private fun formatDate(dateString: String): String {
        return try {
            if (dateString.isEmpty()) return "Not set"

            val inputFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val outputFormat = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
            val date = inputFormat.parse(dateString)
            date?.let { outputFormat.format(it) } ?: dateString
        } catch (e: Exception) {
            android.util.Log.e("PurchaseDetailActivity", "Error formatting date: $dateString", e)
            dateString
        }
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }
}

