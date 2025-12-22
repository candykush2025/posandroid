package com.blackcode.poscandykush

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ItemsDetailActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var progressBar: ProgressBar
    private lateinit var llItemsList: LinearLayout
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var statusBarBackground: View
    private lateinit var cache: SalesDataCache

    private lateinit var btnBack: ImageButton
    private lateinit var tvTitle: TextView

    private var selectedItem: JSONObject? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_items_detail)

        setupStatusBar()

        prefs = getSharedPreferences("admin_prefs", MODE_PRIVATE)
        cache = SalesDataCache(this)

        // Get selected item from intent
        val selectedItemStr = intent.getStringExtra("selected_item")
        if (selectedItemStr != null) {
            selectedItem = JSONObject(selectedItemStr)
        }

        initializeViews()

        // Display the selected item immediately from passed data
        selectedItem?.let { item ->
            val itemName = item.optString("item_name", "Unknown")
            val category = item.optString("category", "Uncategorized")
            val currentStock = item.optInt("current_stock", 0)

            // Set title immediately
            tvTitle.text = itemName

            // Create and display detail view
            val detailView = layoutInflater.inflate(R.layout.item_detail_view, llItemsList, false)
            detailView.findViewById<TextView>(R.id.tv_item_name).text = itemName
            detailView.findViewById<TextView>(R.id.tv_category).text = "Category: $category"
            detailView.findViewById<TextView>(R.id.tv_quantity).text = "Current Stock: ${NumberFormatter.formatInteger(currentStock)}"
            detailView.findViewById<TextView>(R.id.tv_sales).text = getStockStatus(currentStock)
            llItemsList.addView(detailView)
        }

        // Load stock history
        loadStockHistory()
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
        progressBar = findViewById(R.id.progress_bar)
        llItemsList = findViewById(R.id.ll_items_list)
        swipeRefresh = findViewById(R.id.swipe_refresh)
        statusBarBackground = findViewById(R.id.status_bar_background)
        btnBack = findViewById(R.id.btn_back)
        tvTitle = findViewById(R.id.tv_title)

        // Set status bar height
        val statusBarHeight = getStatusBarHeight()
        statusBarBackground.layoutParams.height = statusBarHeight

        btnBack.setOnClickListener {
            finish()
        }

        swipeRefresh.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.primary_green)
        )
        swipeRefresh.setOnRefreshListener {
            loadStockHistory()
        }
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    private fun loadStockHistory() {
        showProgress(true)

        lifecycleScope.launch(Dispatchers.Main) {
            try {
                // Check cache first
                val cachedData = cache.getItemsFromCache("stock_history")
                if (cachedData != null) {
                    // Show cached data immediately
                    updateUIWithStockHistory(cachedData.first)
                    showProgress(false)
                    swipeRefresh.isRefreshing = false
                    return@launch
                }

                // Fetch fresh data from API
                val data = fetchStockHistory()

                // Save to cache
                cache.saveItemsToCache("stock_history", data)

                // Update UI with stock movements
                updateUIWithStockHistory(data)

                showProgress(false)
                swipeRefresh.isRefreshing = false

            } catch (e: Exception) {
                showProgress(false)
                swipeRefresh.isRefreshing = false

                // Show error message
                showEmptyState("Failed to load stock history: ${e.message}")
                Toast.makeText(this@ItemsDetailActivity, "Failed to load stock history: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun fetchStockHistory(): JSONObject {
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
                throw Exception("API call failed with code: $responseCode")
            }

            connection.disconnect()

            JSONObject(responseBody)
        }
    }

    private fun updateUIWithStockHistory(data: JSONObject) {
        if (data.optBoolean("success", false)) {
            val dataObj = data.optJSONObject("data")
            val products = dataObj?.optJSONArray("products")

            if (products != null && products.length() > 0) {
                selectedItem?.let { selected ->
                    val selectedProductId = selected.optString("product_id", "")
                    val selectedName = selected.optString("item_name", "").trim().lowercase()

                    // Find the product that matches our selected item
                    var targetProduct: JSONObject? = null
                    for (i in 0 until products.length()) {
                        val product = products.optJSONObject(i)
                        if (product != null) {
                            val productId = product.optString("product_id", "")
                            val productName = product.optString("product_name", "").trim().lowercase()

                            // Match by product_id first, then by name
                            if ((selectedProductId.isNotEmpty() && productId == selectedProductId) ||
                                (selectedName.isNotEmpty() && productName == selectedName)) {
                                targetProduct = product
                                break
                            }
                        }
                    }

                    // Display movements for the found product
                    if (targetProduct != null) {
                        val movements = targetProduct.optJSONArray("movements")
                        if (movements != null && movements.length() > 0) {
                            // Sort by timestamp descending (most recent first)
                            val sortedMovements = mutableListOf<JSONObject>()
                            for (i in 0 until movements.length()) {
                                movements.optJSONObject(i)?.let { sortedMovements.add(it) }
                            }
                            sortedMovements.sortByDescending { it.optString("timestamp", "") }

                            for (movement in sortedMovements) {
                                displayStockMovement(movement)
                            }
                        } else {
                            showEmptyState("No stock movements found for this item")
                        }
                    } else {
                        showEmptyState("Product not found in stock history")
                    }
                }
            } else {
                showEmptyState("No stock history available")
            }
        } else {
            showEmptyState("Failed to load stock history")
        }
    }

    private fun displayStockMovement(movement: JSONObject) {
        val timestampStr = movement.optString("timestamp", "Unknown")
        val type = movement.optString("type", "Unknown")
        val quantity = movement.optInt("quantity", 0)
        val reason = movement.optString("reason", "")
        val userName = movement.optString("user_name", "")

        // Format timestamp nicely
        val date = try {
            val inputFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.getDefault())
            inputFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val outputFormat = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
            val parsedDate = inputFormat.parse(timestampStr)
            if (parsedDate != null) {
                outputFormat.format(parsedDate)
            } else {
                timestampStr // fallback to original if parsing fails
            }
        } catch (e: Exception) {
            timestampStr // fallback to original if parsing fails
        }

        // Format type for display
        val displayType = when (type) {
            "sale" -> "Sale"
            "purchase_order" -> "Purchase"
            "adjustment" -> "Adjustment"
            "initial" -> "Initial Stock"
            else -> type.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }

        // Create movement view
        val movementView = layoutInflater.inflate(R.layout.stock_movement_row, llItemsList, false)

        movementView.findViewById<TextView>(R.id.tv_date).text = date
        movementView.findViewById<TextView>(R.id.tv_type).text = displayType
        movementView.findViewById<TextView>(R.id.tv_quantity).text = if (quantity < 0) "${NumberFormatter.formatInteger(quantity)}" else "+${NumberFormatter.formatInteger(quantity)}"
        movementView.findViewById<TextView>(R.id.tv_reason).text = if (userName.isNotEmpty()) "$reason ($userName)" else reason

        llItemsList.addView(movementView)
    }

    private fun getStockStatus(stock: Int): String {
        return when {
            stock > 10 -> "In Stock"
            stock > 0 -> "Low Stock"
            else -> "Empty Stock"
        }
    }

    private fun showEmptyState(message: String) {
        // Add empty state after the detail view
        val textView = TextView(this).apply {
            text = message
            setTextColor(ContextCompat.getColor(this@ItemsDetailActivity, R.color.text_secondary))
            textSize = 14f
            setPadding(32, 48, 32, 48)
            gravity = android.view.Gravity.CENTER
        }
        llItemsList.addView(textView)
    }

    private fun showProgress(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }
}
