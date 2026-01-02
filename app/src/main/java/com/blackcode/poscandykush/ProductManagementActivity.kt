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
            // Force refresh - fetch fresh data from API
            refreshStockData()
        }
    }

    /**
     * Refresh stock data - always fetches fresh from API
     * Called when user swipes down to refresh
     */
    private fun refreshStockData() {
        if (isLoadingData) {
            swipeRefresh.isRefreshing = false
            return
        }

        isLoadingData = true
        android.util.Log.d("ProductManagement", "🔄 USER INITIATED REFRESH - Fetching fresh data...")

        lifecycleScope.launch(Dispatchers.Main) {
            try {
                fetchAndDisplayFreshData()
                Toast.makeText(this@ProductManagementActivity, "Data refreshed", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                swipeRefresh.isRefreshing = false
                android.util.Log.e("ProductManagement", "ERROR refreshing: ${e.message}")
                Toast.makeText(this@ProductManagementActivity, "Refresh failed: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoadingData = false
            }
        }
    }

    /**
     * Clear UI before loading new data
     */
    private fun clearUI() {
        llItemsList.removeAllViews()
    }

    /**
     * Load stock data - USE CACHED DATA FIRST FOR FAST LOADING
     */
    private fun loadStockData() {
        if (isLoadingData) return
        isLoadingData = true

        showProgress(true)

        lifecycleScope.launch(Dispatchers.Main) {
            try {
                android.util.Log.d("ProductManagement", "========================================")
                android.util.Log.d("ProductManagement", "LOADING STOCK DATA")
                android.util.Log.d("ProductManagement", "========================================")

                // STEP 1: Try to load from cache FIRST for instant display
                val cachedStock = cache.getItemsFromCache("product_management_stock")
                val cachedHistory = cache.getItemsFromCache("product_management_history")

                if (cachedStock != null) {
                    android.util.Log.d("ProductManagement", "✓ Using CACHED data (fast load)")
                    updateUI(cachedStock.first, cachedHistory?.first)
                    showProgress(false)
                    swipeRefresh.isRefreshing = false
                    isLoadingData = false
                    return@launch
                }

                // STEP 2: If no cache, fetch from API (first time or after cache clear)
                android.util.Log.d("ProductManagement", "⟳ No cache found, fetching from API...")
                fetchAndDisplayFreshData()

            } catch (e: Exception) {
                showProgress(false)
                swipeRefresh.isRefreshing = false

                android.util.Log.e("ProductManagement", "ERROR loading stock data: ${e.message}")
                e.printStackTrace()

                // Show empty state with error
                showEmptyState("Failed to load stock data: ${e.message}")
                Toast.makeText(this@ProductManagementActivity, "Failed to load stock data: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isLoadingData = false
            }
        }
    }

    /**
     * Fetch fresh data from API and update display
     * Called on swipe refresh or when cache is empty
     */
    private suspend fun fetchAndDisplayFreshData() {
        android.util.Log.d("ProductManagement", "FETCHING FRESH DATA FROM API")

        // Fetch fresh data from API
        val data = fetchStockData()

        // Log the raw response
        android.util.Log.d("ProductManagement", "Raw API Response Length: ${data.toString().length} characters")

        val stockHistoryData = try {
            fetchStockHistoryData()
        } catch (e: Exception) {
            android.util.Log.w("ProductManagement", "Failed to fetch stock history: ${e.message}")
            null
        }

        // Save to cache for next time
        cache.saveItemsToCache("product_management_stock", data)
        if (stockHistoryData != null) {
            cache.saveItemsToCache("product_management_history", stockHistoryData)
        }
        android.util.Log.d("ProductManagement", "✓ Fresh data cached")

        // Update UI with fresh data
        updateUI(data, stockHistoryData)

        showProgress(false)
        swipeRefresh.isRefreshing = false
    }

    /**
     * Fetch stock data from API
     */
    private suspend fun fetchStockData(): JSONObject {
        return withContext(Dispatchers.IO) {
            val token = prefs.getString("jwt_token", "") ?: ""
            val url = URL("https://pos-candy-kush.vercel.app/api/mobile?action=stock")

            android.util.Log.d("ProductManagement", "Fetching from: $url")

            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 30000
                readTimeout = 30000
            }

            val responseCode = connection.responseCode
            android.util.Log.d("ProductManagement", "API Response Code: $responseCode")

            val responseBody = if (responseCode == 200) {
                connection.inputStream.bufferedReader().readText()
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "No error body"
                connection.disconnect()
                android.util.Log.e("ProductManagement", "API Error: $errorBody")
                throw Exception("API call failed with code: $responseCode")
            }

            connection.disconnect()

            // Log first 500 characters of response
            val preview = if (responseBody.length > 500) responseBody.substring(0, 500) + "..." else responseBody
            android.util.Log.d("ProductManagement", "API Response Preview: $preview")

            val jsonResponse = JSONObject(responseBody)

            // Log structure
            android.util.Log.d("ProductManagement", "Response has 'success': ${jsonResponse.has("success")}")
            android.util.Log.d("ProductManagement", "Response has 'data': ${jsonResponse.has("data")}")

            if (jsonResponse.has("data")) {
                val data = jsonResponse.optJSONObject("data")
                android.util.Log.d("ProductManagement", "Data has 'items': ${data?.has("items")}")
                val items = data?.optJSONArray("items")
                android.util.Log.d("ProductManagement", "Items array length: ${items?.length() ?: 0}")
            }

            jsonResponse
        }
    }

    /**
     * Fetch stock history data from API
     */
    private suspend fun fetchStockHistoryData(): JSONObject {
        return withContext(Dispatchers.IO) {
            val token = prefs.getString("jwt_token", "") ?: ""
            val url = URL("https://pos-candy-kush.vercel.app/api/mobile?action=stock-history")

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
                throw Exception("Stock history API call failed with code: $responseCode")
            }

            connection.disconnect()

            JSONObject(responseBody)
        }
    }

    /**
     * Calculate buy price from stock movements
     * Uses the latest purchase order cost if available, otherwise returns the default cost
     */
    private fun calculateBuyPriceFromMovements(productId: String, productName: String, defaultCost: Double, stockHistoryData: JSONObject?): Double {
        if (stockHistoryData == null) {
            return defaultCost
        }

        try {
            // New structure: { "products": [...], "total_movements": 1000 }
            val products = stockHistoryData.optJSONArray("products")

            if (products != null) {
                // Find matching product
                for (i in 0 until products.length()) {
                    val product = products.optJSONObject(i) ?: continue
                    val prodId = product.optString("product_id", "")
                    val prodName = product.optString("product_name", "")

                    // Match by product_id first, then by product_name
                    if ((productId.isNotEmpty() && prodId == productId) ||
                        (productName.isNotEmpty() && prodName == productName)) {

                        // Get movements for this product
                        val movements = product.optJSONArray("movements")
                        if (movements != null && movements.length() > 0) {
                            // Find the most recent purchase order with a cost
                            var latestPurchaseCost: Double? = null
                            var latestTimestamp = 0L

                            for (j in 0 until movements.length()) {
                                val movement = movements.optJSONObject(j) ?: continue
                                val type = movement.optString("type", "")

                                // Only consider purchase orders
                                if (type == "purchase_order") {
                                    // Note: In the new JSON structure, movements don't have "cost" field
                                    // You may need to add cost to movements or fetch it separately
                                    val cost = movement.optDouble("cost", -1.0)  // This will be -1.0 if not present
                                    if (cost > 0) {
                                        // Parse timestamp
                                        val timestamp = movement.optString("timestamp", "")
                                        val time = try {
                                            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
                                                .parse(timestamp.replace("Z", "").replace(".000", ""))?.time ?: 0L
                                        } catch (e: Exception) {
                                            0L
                                        }

                                        // Keep the most recent purchase cost
                                        if (time > latestTimestamp) {
                                            latestTimestamp = time
                                            latestPurchaseCost = cost
                                        }
                                    }
                                }
                            }

                            // Return the latest purchase cost if found, otherwise default
                            if (latestPurchaseCost != null) {
                                android.util.Log.d("ProductManagement", "Using stock movement cost for $productName: $latestPurchaseCost (from purchase order)")
                                return latestPurchaseCost
                            }
                        }
                        break
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ProductManagement", "Error calculating buy price from movements: ${e.message}")
        }

        // Return default cost if no purchase movements found or cost not available
        return defaultCost
    }

    /**
     * Update UI with stock data - organized by category
     */
    private fun updateUI(data: JSONObject, stockHistoryData: JSONObject?) {
        llItemsList.removeAllViews()

        if (data.optBoolean("success", false)) {
            val dataObj = data.optJSONObject("data")
            val items = dataObj?.optJSONArray("items")

            if (items != null && items.length() > 0) {
                // NO DEDUPLICATION - Show all products exactly as received from API
                android.util.Log.d("ProductManagement", "=== DISPLAYING ALL PRODUCTS (NO FILTERING) ===")
                android.util.Log.d("ProductManagement", "Total items from API: ${items.length()}")

                // Convert all items to list (NO FILTERING)
                val allItems = mutableListOf<JSONObject>()
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i)
                    if (item != null) {
                        allItems.add(item)
                        val id = item.optString("product_id", "[NO ID]")
                        val name = item.optString("product_name", "[NO NAME]")
                        val category = item.optString("category", "[NO CATEGORY]")
                        android.util.Log.d("ProductManagement", "[$i] Displaying: ID: $id | Name: $name | Category: $category")
                    }
                }

                android.util.Log.d("ProductManagement", "Total products to display: ${allItems.size}")

                // Group all items by category (NO FILTERING)
                val itemsByCategory = mutableMapOf<String, MutableList<JSONObject>>()

                allItems.forEach { item ->
                    val category = item.optString("category", "Other")
                    itemsByCategory.getOrPut(category) { mutableListOf() }.add(item)
                }

                // DETAILED CATEGORY ANALYSIS
                android.util.Log.d("ProductManagement", "=== CATEGORY ANALYSIS ===")
                android.util.Log.d("ProductManagement", "Total categories found: ${itemsByCategory.size}")
                itemsByCategory.forEach { (category, items) ->
                    android.util.Log.d("ProductManagement", "Category '$category': ${items.size} products")
                    if (category.lowercase().contains("accessories") || category.lowercase().contains("accessory")) {
                        android.util.Log.d("ProductManagement", "🎯 ACCESSORIES CATEGORY FOUND: '$category' with ${items.size} products")
                        items.forEachIndexed { index, item ->
                            val name = item.optString("product_name", "Unknown")
                            val id = item.optString("product_id", "[NO ID]")
                            android.util.Log.d("ProductManagement", "  [$index] $name (ID: $id)")
                        }
                    }
                }
                android.util.Log.d("ProductManagement", "=== END CATEGORY ANALYSIS ===")

                // Sort categories and display them
                val sortedCategories = itemsByCategory.keys.sorted()

                for (category in sortedCategories) {
                    val categoryItems = itemsByCategory[category] ?: continue

                    // SPECIFIC ACCESSORIES LOGGING
                    if (category.lowercase().contains("accessories") || category.lowercase().contains("accessory")) {
                        android.util.Log.d("ProductManagement", "🎯 DISPLAYING ACCESSORIES CATEGORY: '$category' with ${categoryItems.size} items")
                    }

                    // Add category header
                    addCategoryHeader(category, categoryItems.size)

                    // Sort items within category by name
                    val sortedItems = categoryItems.sortedBy { it.optString("product_name", "") }

                    // Add items for this category
                    for (item in sortedItems) {
                        val productId = item.optString("product_id", "")
                        val productName = item.optString("product_name", "Unknown")
                        val defaultCost = item.optDouble("cost", 0.0)

                        // Calculate buy price from stock movements if available
                        val buyPrice = calculateBuyPriceFromMovements(productId, productName, defaultCost, stockHistoryData)

                        val itemView = createProductRow(
                            productName,
                            item.optString("category", "Uncategorized"),
                            buyPrice,  // Use calculated buy price from movements
                            item.optDouble("price", 0.0),
                            productId
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



