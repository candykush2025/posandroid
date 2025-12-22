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

class ItemsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var progressBar: ProgressBar
    private lateinit var llItemsList: LinearLayout
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var statusBarBackground: View
    private lateinit var cache: SalesDataCache

    // Track loading state
    private var isLoadingData = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_items)

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
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    private fun setupBottomNavigation() {
        bottomNavigation.selectedItemId = R.id.nav_items

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_sales -> {
                    startActivity(Intent(this, DashboardActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_items -> true
                R.id.nav_finance -> {
                    startActivity(Intent(this, FinanceActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
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
                Toast.makeText(this@ItemsActivity, "Failed to load stock data: ${e.message}", Toast.LENGTH_LONG).show()
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

                    // Sort items within category by stock quantity (descending)
                    val sortedItems = categoryItems.sortedByDescending { it.optInt("current_stock", 0) }

                    // Add items for this category
                    for (item in sortedItems) {
                        val itemView = createItemRow(
                            item.optString("product_name", "Unknown"),
                            item.optString("category", "Uncategorized"),
                            item.optInt("current_stock", 0),
                            item.optDouble("price", 0.0),
                            item.optString("product_id", "")
                        )
                        llItemsList.addView(itemView)
                    }

                    // Add spacing between categories
                    addCategorySpacing()
                }
            } else {
                showEmptyState("No items in stock")
            }
        } else {
            showEmptyState("No item data available")
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
        tvCategoryCount.text = "$itemCount items"

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
            setTextColor(ContextCompat.getColor(this@ItemsActivity, R.color.text_secondary))
            textSize = 14f
            setPadding(32, 48, 32, 48)
            gravity = android.view.Gravity.CENTER
        }
        llItemsList.addView(textView)
    }

    private fun createItemRow(name: String, category: String, stockQuantity: Int, salesAmount: Double, productId: String): View {
        val view = layoutInflater.inflate(R.layout.item_stock_row, null)

        view.findViewById<TextView>(R.id.tv_item_name).text = name
        view.findViewById<TextView>(R.id.tv_category).text = category
        view.findViewById<TextView>(R.id.tv_quantity).text = "Stock: ${NumberFormatter.formatInteger(stockQuantity)}"
        view.findViewById<TextView>(R.id.tv_sales).text = getStockStatus(stockQuantity)

        // Make item clickable to show item details
        view.setOnClickListener {
            // Create item data to pass to detail activity
            val itemData = JSONObject().apply {
                put("item_name", name)
                put("category", category)
                put("current_stock", stockQuantity)
                put("product_id", productId)
                put("gross_sales", salesAmount)
            }
            openItemDetail(itemData)
        }

        return view
    }

    /**
     * Get stock status based on quantity
     */
    private fun getStockStatus(stock: Int): String {
        return when {
            stock > 10 -> "In Stock"
            stock > 0 -> "Low Stock"
            else -> "Empty Stock"
        }
    }

    /**
     * Open item detail page for selected item
     */
    private fun openItemDetail(itemData: JSONObject) {
        val intent = Intent(this, ItemsDetailActivity::class.java)
        intent.putExtra("selected_item", itemData.toString())
        intent.putExtra("period", "this_month")
        intent.putExtra("date_key", "2024-12")
        intent.putExtra("start_date", "2024-12-01")
        intent.putExtra("end_date", "2024-12-31")
        startActivity(intent)
    }

    private fun showProgress(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        bottomNavigation.selectedItemId = R.id.nav_items
    }
}
