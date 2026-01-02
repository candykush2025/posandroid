package com.blackcode.poscandykush

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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

class PurchasingActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var progressBar: ProgressBar
    private lateinit var rvPurchases: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var fabAddPurchase: FloatingActionButton
    private lateinit var statusBarBackground: View
    private lateinit var cache: SalesDataCache
    private val gson = Gson()

    private lateinit var purchaseAdapter: PurchaseAdapter
    private var isLoadingData = false

    companion object {
        private const val ADD_PURCHASE_REQUEST_CODE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_purchasing)

        setupStatusBar()

        prefs = getSharedPreferences("admin_prefs", MODE_PRIVATE)
        cache = SalesDataCache(this)

        if (!isAuthenticated()) {
            navigateToLogin()
            return
        }

        initializeViews()
        setupSwipeRefresh()
        setupRecyclerView()
        setupFAB()

        clearUI()
        loadPurchaseData()
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
        rvPurchases = findViewById(R.id.rv_purchases)
        tvEmpty = findViewById(R.id.tv_empty)
        swipeRefresh = findViewById(R.id.swipe_refresh)
        fabAddPurchase = findViewById(R.id.fab_add_purchase)
        statusBarBackground = findViewById(R.id.status_bar_background)

        // Set status bar height
        val statusBarHeight = getStatusBarHeight()
        statusBarBackground.layoutParams.height = statusBarHeight

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
            loadPurchaseData()
        }
    }

    private fun setupRecyclerView() {
        rvPurchases.layoutManager = LinearLayoutManager(this)
        purchaseAdapter = PurchaseAdapter(mutableListOf())
        rvPurchases.adapter = purchaseAdapter

        purchaseAdapter.setOnPurchaseClickListener(object : PurchaseAdapter.OnPurchaseClickListener {
            override fun onPurchaseClick(purchase: Purchase) {
                val intent = Intent(this@PurchasingActivity, PurchaseDetailActivity::class.java)
                intent.putExtra("purchase_id", purchase.id)
                startActivity(intent)
            }

            override fun onMarkComplete(purchase: Purchase) {
                showCompleteConfirmationDialog(purchase)
            }
        })
    }

    private fun setupFAB() {
        fabAddPurchase.setOnClickListener {
            val intent = Intent(this, AddPurchaseActivity::class.java)
            startActivityForResult(intent, ADD_PURCHASE_REQUEST_CODE)
        }
    }

    private fun clearUI() {
        purchaseAdapter.updatePurchases(emptyList())
        tvEmpty.visibility = View.GONE
    }

    private fun loadPurchaseData() {
        if (isLoadingData) return
        isLoadingData = true

        showProgress(true)

        lifecycleScope.launch(Dispatchers.Main) {
            var cachedData: Pair<JSONObject, Long>? = null
            try {
                cachedData = cache.getItemsFromCache("purchases")
                if (cachedData != null) {
                    val cachedResponse = gson.fromJson(cachedData.first.toString(), PurchaseListResponse::class.java)
                    updateUI(cachedResponse)
                }

                val data = fetchPurchaseData()

                if (data != null) {
                    val jsonData = JSONObject(gson.toJson(data))
                    cache.saveItemsToCache("purchases", jsonData)
                    updateUI(data)
                } else {
                    if (cachedData == null) {
                        showEmptyState("Failed to load purchases")
                    } else {
                        Toast.makeText(this@PurchasingActivity, "Failed to refresh, using cached data", Toast.LENGTH_SHORT).show()
                    }
                }

                showProgress(false)
                swipeRefresh.isRefreshing = false

            } catch (e: Exception) {
                showProgress(false)
                swipeRefresh.isRefreshing = false

                if (cachedData == null) {
                    showEmptyState("Failed to load purchases: ${e.message}")
                    Toast.makeText(this@PurchasingActivity, "Failed to load purchases: ${e.message}", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@PurchasingActivity, "Failed to refresh, using cached data", Toast.LENGTH_SHORT).show()
                }
            } finally {
                isLoadingData = false
            }
        }
    }

    private suspend fun fetchPurchaseData(): PurchaseListResponse? {
        return withContext(Dispatchers.IO) {
            val token = prefs.getString("jwt_token", "") ?: ""
            val apiService = PurchaseApiService()
            apiService.getPurchases(token)
        }
    }

    private fun updateUI(response: PurchaseListResponse) {
        if (response.success && response.data != null) {
            val purchases = response.data.purchases

            if (purchases.isNotEmpty()) {
                purchaseAdapter.updatePurchases(purchases)
                rvPurchases.visibility = View.VISIBLE
                tvEmpty.visibility = View.GONE
            } else {
                showEmptyState("No purchases found")
            }
        } else {
            showEmptyState(response.error ?: "Failed to load purchases")
        }
    }

    private fun showEmptyState(message: String) {
        tvEmpty.text = message
        tvEmpty.visibility = View.VISIBLE
        rvPurchases.visibility = View.GONE
    }

    private fun showProgress(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
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

    private fun completePurchase(purchase: Purchase) {
        showProgress(true)

        lifecycleScope.launch(Dispatchers.Main) {
            try {
                val success = performCompletePurchase(purchase.id)
                showProgress(false)

                if (success) {
                    Toast.makeText(this@PurchasingActivity, "Purchase completed successfully", Toast.LENGTH_SHORT).show()
                    loadPurchaseData()
                } else {
                    Toast.makeText(this@PurchasingActivity, "Failed to complete purchase", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                showProgress(false)
                Toast.makeText(this@PurchasingActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun performCompletePurchase(purchaseId: String): Boolean {
        return withContext(Dispatchers.IO) {
            val token = prefs.getString("jwt_token", "") ?: ""
            val apiService = PurchaseApiService()
            val response = apiService.completePurchase(token, purchaseId)
            response?.success == true
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ADD_PURCHASE_REQUEST_CODE && resultCode == RESULT_OK) {
            loadPurchaseData()
        }
    }
}
