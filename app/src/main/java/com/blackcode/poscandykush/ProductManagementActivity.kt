package com.blackcode.poscandykush

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ProductManagementActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var progressBar: ProgressBar
    private lateinit var llItemsList: LinearLayout
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var statusBarBackground: View
    private lateinit var cache: SalesDataCache

    // Track loading state
    private var isLoadingData = false

    companion object {
        private const val STOCK_HISTORY_REQUEST_CODE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_management)

        setupStatusBar()

        prefs = getSharedPreferences("admin_prefs", MODE_PRIVATE)
        cache = SalesDataCache(this)

        // Check authentication
        if (!isAuthenticated()) {
            navigateToLogin()
            return
        }

        initializeViews()
        setupBottomNavigation()
        setupSwipeRefresh()

        // Clear UI and load stock data
        clearUI()
        loadStockData()
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
        llItemsList = findViewById(R.id.ll_items_list)
        bottomNavigation = findViewById(R.id.bottom_navigation)
        swipeRefresh = findViewById(R.id.swipe_refresh)
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

    private fun setupBottomNavigation() {
        bottomNavigation.selectedItemId = R.id.nav_finance

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_sales -> {
                    startActivity(Intent(this, DashboardActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_items -> {
                    startActivity(Intent(this, ItemsActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_finance -> true
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.primary_green)
        )
        swipeRefresh.setOnRefreshListener {
            loadStockData()
        }
    }

    /**
     * Clear UI before loading new data
     */
    private fun clearUI() {
        llItemsList.removeAllViews()
    }

    /**
     * Load stock data with proper caching
     */
    private fun loadStockData() {
        if (isLoadingData) return
        isLoadingData = true

        showProgress(true)

        lifecycleScope.launch(Dispatchers.Main) {
            try {
                // Check cache first
                val cachedData = cache.getItemsFromCache("stock")
                if (cachedData != null) {
                    // Show cached data immediately
                    updateUI(cachedData.first)
                    showProgress(false)
                    swipeRefresh.isRefreshing = false
                    isLoadingData = false
                    return@launch
                }

                // Fetch fresh data from API
                val data = fetchStockData()

                // Save to cache
                cache.saveItemsToCache("stock", data)

                // Update UI
                updateUI(data)

                showProgress(false)
                swipeRefresh.isRefreshing = false

            } catch (e: Exception) {
                showProgress(false)
                swipeRefresh.isRefreshing = false

                // If no cached data, show empty state with error
                showEmptyState("Failed to load stock data: ${e.message}")
                Toast.makeText(this@ProductManagementActivity, "Failed to load stock data: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isLoadingData = false
            }
        }
    }

    /**
     * Fetch stock data from API
     */
    private suspend fun fetchStockData(): JSONObject {
        return withContext(Dispatchers.IO) {
            val token = prefs.getString("jwt_token", "") ?: ""
            val url = URL("https://pos-candy-kush.vercel.app/api/mobile?action=stock")

            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 30000
                readTimeout = 30000
            }

            val responseCode = connection.responseCode
            val responseBody = if (responseCode == 200) {
                connection.inputStream.bufferedReader().readText()
            } else {
                connection.disconnect()
                throw Exception("API call failed with code: $responseCode")
            }

            connection.disconnect()

            JSONObject(responseBody)
        }
    }

    /**
     * Update UI with stock data - organized by category
     */
    private fun updateUI(data: JSONObject) {
        llItemsList.removeAllViews()

        if (data.optBoolean("success", false)) {
            val dataObj = data.optJSONObject("data")
            val items = dataObj?.optJSONArray("items")

            if (items != null && items.length() > 0) {
                // Group items by category
                val itemsByCategory = mutableMapOf<String, MutableList<JSONObject>>()

                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i)
                    if (item != null) {
                        val category = item.optString("category", "Other")
                        itemsByCategory.getOrPut(category) { mutableListOf() }.add(item)
                    }
                }

                // Sort categories and display them
                val sortedCategories = itemsByCategory.keys.sorted()

                for (category in sortedCategories) {
                    val categoryItems = itemsByCategory[category] ?: continue

                    // Add category header
                    addCategoryHeader(category, categoryItems.size)

                    // Sort items within category by name
                    val sortedItems = categoryItems.sortedBy { it.optString("product_name", "") }

                    // Add items for this category
                    for (item in sortedItems) {
                        val itemView = createProductRow(
                            item.optString("product_name", "Unknown"),
                            item.optString("category", "Uncategorized"),
                            item.optDouble("cost", 0.0),
                            item.optDouble("price", 0.0),
                            item.optString("product_id", "")
                        )
                        llItemsList.addView(itemView)
                    }

                    // Add spacing between categories
                    addCategorySpacing()
                }
            } else {
                showEmptyState("No products available")
            }
        } else {
            showEmptyState("No product data available")
        }
    }

    /**
     * Add a category header with count
     */
    private fun addCategoryHeader(categoryName: String, itemCount: Int) {
        val headerView = layoutInflater.inflate(R.layout.item_category_header, null)

        val tvCategoryTitle = headerView.findViewById<TextView>(R.id.tv_category_title)
        val tvCategoryCount = headerView.findViewById<TextView>(R.id.tv_category_count)

        tvCategoryTitle.text = categoryName
        tvCategoryCount.text = "$itemCount products"

        llItemsList.addView(headerView)
    }

    /**
     * Add spacing between categories
     */
    private fun addCategorySpacing() {
        val spacingView = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                8
            )
        }
        llItemsList.addView(spacingView)
    }

    private fun showEmptyState(message: String) {
        llItemsList.removeAllViews()

        val textView = TextView(this).apply {
            text = message
            setTextColor(ContextCompat.getColor(this@ProductManagementActivity, R.color.text_secondary))
            textSize = 14f
            setPadding(32, 48, 32, 48)
            gravity = android.view.Gravity.CENTER
        }
        llItemsList.addView(textView)
    }

    private fun createProductRow(name: String, category: String, buyPrice: Double, sellPrice: Double, productId: String): View {
        val view = layoutInflater.inflate(R.layout.item_product_row, null)

        val tvItemName = view.findViewById<TextView>(R.id.tv_item_name)
        val tvCategory = view.findViewById<TextView>(R.id.tv_category)
        val tvBuyPrice = view.findViewById<TextView>(R.id.tv_buy_price)
        val tvSellPrice = view.findViewById<TextView>(R.id.tv_sell_price)
        val tvMargin = view.findViewById<TextView>(R.id.tv_margin)

        tvItemName.text = name
        tvCategory.text = category

        // Set buy price with red text if 0
        tvBuyPrice.text = "Buy: ${NumberFormatter.formatCurrency(buyPrice)}"
        if (buyPrice == 0.0) {
            tvBuyPrice.setTextColor(ContextCompat.getColor(this, R.color.error_red))
        } else {
            tvBuyPrice.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }

        tvSellPrice.text = "Sell: ${NumberFormatter.formatCurrency(sellPrice)}"

        // Set margin with red text if negative
        val margin = sellPrice - buyPrice
        tvMargin.text = "Margin: ${NumberFormatter.formatCurrency(margin)}"
        if (margin < 0) {
            tvMargin.setTextColor(ContextCompat.getColor(this, R.color.error_red))
        } else {
            tvMargin.setTextColor(ContextCompat.getColor(this, R.color.primary_green))
        }

        // Make row clickable to show stock history
        view.setOnClickListener {
            val itemData = JSONObject().apply {
                put("product_name", name)
                put("category", category)
                put("product_id", productId)
                put("cost", buyPrice)
                put("price", sellPrice)
            }
            openStockHistory(itemData)
        }

        return view
    }

    private fun showProgress(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun openStockHistory(itemData: JSONObject) {
        val intent = Intent(this, StockHistoryDetailActivity::class.java)
        intent.putExtra("item_data", itemData.toString())
        startActivityForResult(intent, STOCK_HISTORY_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == STOCK_HISTORY_REQUEST_CODE && resultCode == RESULT_OK) {
            // Check if item data was updated
            val updatedItemDataString = data?.getStringExtra("updated_item_data")
            if (!updatedItemDataString.isNullOrEmpty()) {
                // Item cost was updated, refresh the data to show updated costs
                loadStockData()
            }
        }
    }
}
