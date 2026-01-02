package com.blackcode.poscandykush

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class CustomerInvoiceActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var progressBar: ProgressBar
    private lateinit var rvInvoices: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var fabAddInvoice: FloatingActionButton
    private lateinit var statusBarBackground: View
    private lateinit var cache: SalesDataCache
    private val gson = Gson()

    private lateinit var invoiceAdapter: InvoiceAdapter

    // Track loading state
    private var isLoadingData = false

    companion object {
        private const val ADD_INVOICE_REQUEST_CODE = 1
        private const val EDIT_INVOICE_REQUEST_CODE = 2
        private const val BASE_URL = "https://pos-candy-kush.vercel.app/api/mobile"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customer_invoice)

        setupStatusBar()

        prefs = getSharedPreferences("admin_prefs", MODE_PRIVATE)
        cache = SalesDataCache(this)

        // Check authentication
        if (!isAuthenticated()) {
            navigateToLogin()
            return
        }

        initializeViews()
        setupSwipeRefresh()
        setupRecyclerView()
        setupFAB()

        // Clear UI and load invoice data
        clearUI()
        loadInvoiceData() // Always load from cache first, then fetch new data
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

    private fun isAuthenticated(): Boolean {
        val token = prefs.getString("jwt_token", null)
        val tokenExpiry = prefs.getLong("token_expiry", 0)
        return token != null && System.currentTimeMillis() < tokenExpiry
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun initializeViews() {
        progressBar = findViewById(R.id.progress_bar)
        rvInvoices = findViewById(R.id.rv_invoices)
        tvEmpty = findViewById(R.id.tv_empty)
        swipeRefresh = findViewById(R.id.swipe_refresh)
        fabAddInvoice = findViewById(R.id.fab_add_invoice)
        statusBarBackground = findViewById(R.id.status_bar_background)

        // Set status bar height
        val statusBarHeight = getStatusBarHeight()
        statusBarBackground.layoutParams.height = statusBarHeight

        // Setup back button
        findViewById<View>(R.id.btn_back).setOnClickListener {
            finish()
        }
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.primary_green)
        )
        swipeRefresh.setOnRefreshListener {
            loadInvoiceData() // Load from cache first, then fetch new data
        }
    }

    private fun setupRecyclerView() {
        rvInvoices.layoutManager = LinearLayoutManager(this)
        invoiceAdapter = InvoiceAdapter(mutableListOf())
        rvInvoices.adapter = invoiceAdapter

        // Set click listener for invoice actions
        invoiceAdapter.setOnInvoiceClickListener(object : InvoiceAdapter.OnInvoiceClickListener {
            override fun onInvoiceClick(invoice: Invoice) {
                // Open invoice detail view
                val intent = Intent(this@CustomerInvoiceActivity, InvoiceDetailActivity::class.java)
                intent.putExtra("invoice_id", invoice.id)
                startActivity(intent)
            }
        })
    }

    private fun setupFAB() {
        fabAddInvoice.setOnClickListener {
            val intent = Intent(this, AddInvoiceActivity::class.java)
            startActivityForResult(intent, ADD_INVOICE_REQUEST_CODE)
        }
    }

    /**
     * Clear UI before loading new data
     */
    private fun clearUI() {
        invoiceAdapter.updateInvoices(emptyList())
        tvEmpty.visibility = View.GONE
    }

    /**
     * Load invoice data with proper caching
     */
    private fun loadInvoiceData() {
        if (isLoadingData) return
        isLoadingData = true

        showProgress(true)

        lifecycleScope.launch(Dispatchers.Main) {
            var cachedData: Pair<JSONObject, Long>? = null
            try {
                // Check cache first
                cachedData = cache.getItemsFromCache("invoices")
                if (cachedData != null) {
                    // Show cached data immediately
                    val cachedResponse = gson.fromJson(cachedData.first.toString(), InvoiceListResponse::class.java)
                    updateUI(cachedResponse)
                }

                // Fetch fresh data from API
                val data = fetchInvoiceData()

                if (data != null) {
                    // Save to cache
                    val jsonData = JSONObject(gson.toJson(data))
                    cache.saveItemsToCache("invoices", jsonData)

                    // Update UI
                    updateUI(data)
                } else {
                    // If fetch failed and no cached data, show empty
                    if (cachedData == null) {
                        showEmptyState("Failed to load invoices")
                    } else {
                        // Fetch failed but cached data available
                        Toast.makeText(this@CustomerInvoiceActivity, "Failed to refresh, using cached data", Toast.LENGTH_SHORT).show()
                    }
                }

                showProgress(false)
                swipeRefresh.isRefreshing = false

            } catch (e: Exception) {
                showProgress(false)
                swipeRefresh.isRefreshing = false

                // If no cached data, show empty state with error
                if (cachedData == null) {
                    showEmptyState("Failed to load invoices: ${e.message}")
                    Toast.makeText(this@CustomerInvoiceActivity, "Failed to load invoices: ${e.message}", Toast.LENGTH_LONG).show()
                } else {
                    // Exception occurred but cached data available
                    Toast.makeText(this@CustomerInvoiceActivity, "Failed to refresh, using cached data", Toast.LENGTH_SHORT).show()
                }
            } finally {
                isLoadingData = false
            }
        }
    }

    /**
     * Fetch invoice data from API
     */
    private suspend fun fetchInvoiceData(): InvoiceListResponse? {
        return withContext(Dispatchers.IO) {
            val token = prefs.getString("jwt_token", "") ?: ""
            val apiService = InvoiceApiService()
            val response = apiService.getInvoices(token)
            android.util.Log.d("CustomerInvoiceActivity", "fetchInvoiceData response: success=${response?.success}, error=${response?.error}, invoices count=${response?.data?.invoices?.size ?: 0}")
            response
        }
    }

    /**
     * Update UI with invoice data
     */
    private fun updateUI(response: InvoiceListResponse) {
        android.util.Log.d("CustomerInvoiceActivity", "updateUI called with success=${response.success}, data=${response.data}, error=${response.error}")
        if (response.success && response.data != null) {
            val invoices = response.data.invoices
            android.util.Log.d("CustomerInvoiceActivity", "Updating UI with ${invoices.size} invoices")

            if (invoices.isNotEmpty()) {
                invoiceAdapter.updateInvoices(invoices)
                rvInvoices.visibility = View.VISIBLE
                tvEmpty.visibility = View.GONE
            } else {
                showEmptyState("No invoices found")
            }
        } else {
            showEmptyState(response.error ?: "Failed to load invoices")
        }
    }

    /**
     * Show empty state
     */
    private fun showEmptyState(message: String) {
        tvEmpty.text = message
        tvEmpty.visibility = View.VISIBLE
        rvInvoices.visibility = View.GONE
    }

    /**
     * Show/hide progress
     */
    private fun showProgress(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ADD_INVOICE_REQUEST_CODE && resultCode == RESULT_OK) {
            // Refresh the invoice list
            loadInvoiceData()
        } else if (requestCode == EDIT_INVOICE_REQUEST_CODE && resultCode == RESULT_OK) {
            // Refresh the invoice list
            loadInvoiceData()
        }
    }


    /**
     * Show delete confirmation dialog
     */
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

    /**
     * Delete invoice
     */
    private fun deleteInvoice(invoice: Invoice) {
        showProgress(true)

        lifecycleScope.launch(Dispatchers.Main) {
            try {
                val success = performDeleteInvoice(invoice.id)
                showProgress(false)

                if (success) {
                    Toast.makeText(this@CustomerInvoiceActivity, "Invoice deleted successfully", Toast.LENGTH_SHORT).show()
                    loadInvoiceData() // Refresh the list
                } else {
                    Toast.makeText(this@CustomerInvoiceActivity, "Failed to delete invoice", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                showProgress(false)
                Toast.makeText(this@CustomerInvoiceActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun performDeleteInvoice(invoiceId: String): Boolean {
        return withContext(Dispatchers.IO) {
            val token = prefs.getString("jwt_token", "") ?: ""
            val apiService = InvoiceApiService()
            val response = apiService.deleteInvoice(token, invoiceId)
            response?.success == true
        }
    }
}
