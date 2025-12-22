package com.blackcode.poscandykush

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class StockHistoryDetailActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var progressBar: ProgressBar
    private lateinit var llHistoryList: LinearLayout
    private lateinit var statusBarBackground: View
    private lateinit var tvItemName: TextView
    private lateinit var tvCurrentStock: TextView
    private lateinit var tvCategory: TextView
    private lateinit var tvBuyPrice: TextView
    private lateinit var tvSellPrice: TextView
    private lateinit var btnEditCost: Button

    private lateinit var itemData: JSONObject
    private var isLoadingData = false

    companion object {
        private const val EDIT_COST_REQUEST_CODE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stock_history_detail)

        setupStatusBar()

        prefs = getSharedPreferences("admin_prefs", MODE_PRIVATE)

        // Check authentication
        if (!isAuthenticated()) {
            navigateToLogin()
            return
        }

        // Get item data from intent
        val itemDataString = intent.getStringExtra("item_data")
        val stockHistoryDataString = intent.getStringExtra("stock_history_data")

        if (itemDataString == null) {
            Toast.makeText(this, "No item data provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        itemData = JSONObject(itemDataString)

        initializeViews()

        // Use passed stock history data or fetch if not available
        if (!stockHistoryDataString.isNullOrEmpty()) {
            try {
                val stockHistoryData = JSONObject(stockHistoryDataString)
                // Extract movements for this specific item
                val movements = extractItemMovements(stockHistoryData)
                // Display movements
                updateUI(movements)
            } catch (e: Exception) {
                // If parsing fails, fall back to fetching
                loadStockHistory()
            }
        } else {
            // No cached data, fetch from API
            loadStockHistory()
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
        llHistoryList = findViewById(R.id.ll_history_list)
        statusBarBackground = findViewById(R.id.status_bar_background)
        tvItemName = findViewById(R.id.tv_item_name)
        tvCurrentStock = findViewById(R.id.tv_current_stock)
        tvCategory = findViewById(R.id.tv_category)
        tvBuyPrice = findViewById(R.id.tv_buy_price)
        tvSellPrice = findViewById(R.id.tv_sell_price)
        btnEditCost = findViewById(R.id.btn_edit_cost)

        // Set status bar height
        val statusBarHeight = getStatusBarHeight()
        statusBarBackground.layoutParams.height = statusBarHeight

        // Setup back button
        findViewById<View>(R.id.btn_back).setOnClickListener {
            finish()
        }

        // Display item info
        tvItemName.text = itemData.optString("product_name", "Unknown Item")
        tvCurrentStock.text = "Current Stock: ${NumberFormatter.formatInteger(itemData.optInt("current_stock", 0))}"
        tvCategory.text = itemData.optString("category", "Unknown Category")
        tvBuyPrice.text = "Buy: ${NumberFormatter.formatCurrency(itemData.optDouble("cost", 0.0))}"
        tvSellPrice.text = "Sell: ${NumberFormatter.formatCurrency(itemData.optDouble("price", 0.0))}"

        btnEditCost.setOnClickListener {
            // Handle edit cost action
            val intent = Intent(this, EditCostActivity::class.java)
            intent.putExtra("item_data", itemData.toString())
            startActivityForResult(intent, EDIT_COST_REQUEST_CODE)
        }
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    /**
     * Load stock history for this specific item
     */
    private fun loadStockHistory() {
        if (isLoadingData) return
        isLoadingData = true

        showProgress(true)

        lifecycleScope.launch(Dispatchers.Main) {
            try {
                // Fetch stock history from API
                val stockHistoryData = fetchStockHistory()

                // Extract movements for this specific item
                val movements = extractItemMovements(stockHistoryData)

                // Display movements
                updateUI(movements)

                showProgress(false)

            } catch (e: Exception) {
                showProgress(false)
                showEmptyState("Failed to load stock history: ${e.message}")
                Toast.makeText(this@StockHistoryDetailActivity, "Failed to load stock history: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isLoadingData = false
            }
        }
    }

    /**
     * Fetch stock history from API
     */
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

            // Debug logging
            println("DEBUG: Stock history API response: $responseBody")

            JSONObject(responseBody)
        }
    }

    /**
     * Extract movements for this specific item from the stock history
     */
    private fun extractItemMovements(stockHistoryData: JSONObject): JSONArray {
        val movements = JSONArray()

        if (stockHistoryData.optBoolean("success", false)) {
            val historyData = stockHistoryData.optJSONObject("data")
            val products = historyData?.optJSONArray("products")

            if (products != null) {
                val itemName = itemData.optString("product_name", "")
                val itemId = itemData.optString("product_id", "")

                // Debug logging
                println("DEBUG: Looking for item - Name: '$itemName', ID: '$itemId'")
                println("DEBUG: Found ${products.length()} products in stock history")

                // Find the product that matches our item
                for (i in 0 until products.length()) {
                    val product = products.optJSONObject(i) ?: continue
                    val productName = product.optString("product_name", "")
                    val productId = product.optString("product_id", "")

                    println("DEBUG: Checking product $i - Name: '$productName', ID: '$productId'")

                    // Match by product_id first, then by product_name
                    if ((itemId.isNotEmpty() && productId == itemId) ||
                        (itemName.isNotEmpty() && productName == itemName)) {

                        println("DEBUG: Found matching product! Getting movements...")

                        // Found our product, get its movements
                        val productMovements = product.optJSONArray("movements")
                        if (productMovements != null) {
                            println("DEBUG: Found ${productMovements.length()} movements")
                            // Add all movements for this product
                            for (j in 0 until productMovements.length()) {
                                movements.put(productMovements.optJSONObject(j))
                            }
                        } else {
                            println("DEBUG: No movements array found")
                        }
                        break
                    }
                }

                // If no matching product found, it means the product has no stock movements yet
                if (movements.length() == 0) {
                    println("DEBUG: No matching product found in stock history - product may have no movements yet")
                }
            } else {
                println("DEBUG: No products array found in stock history")
            }
        } else {
            println("DEBUG: Stock history API call was not successful")
        }

        println("DEBUG: Returning ${movements.length()} movements")
        return movements
    }

    /**
     * Update UI with stock movements
     */
    private fun updateUI(movements: JSONArray) {
        llHistoryList.removeAllViews()

        if (movements.length() > 0) {
            // Sort movements by timestamp (newest first for display)
            val sortedMovements = sortMovementsByTimestamp(movements)

            for (i in 0 until sortedMovements.length()) {
                val movement = sortedMovements.optJSONObject(i) ?: continue
                val movementView = createMovementRow(movement)
                llHistoryList.addView(movementView)
            }
        } else {
            showEmptyState("No stock movements found for this item")
        }
    }

    /**
     * Sort movements by timestamp (newest first)
     */
    private fun sortMovementsByTimestamp(movements: JSONArray): JSONArray {
        val movementsList = mutableListOf<JSONObject>()

        for (i in 0 until movements.length()) {
            movements.optJSONObject(i)?.let { movementsList.add(it) }
        }

        // Sort by timestamp (newest first)
        movementsList.sortByDescending { movement ->
            val timestamp = movement.optString("timestamp", "")
            try {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                    .parse(timestamp.replace("Z", "").replace(".000", ""))?.time ?: 0L
            } catch (e: Exception) {
                0L
            }
        }

        val sortedArray = JSONArray()
        movementsList.forEach { sortedArray.put(it) }
        return sortedArray
    }

    /**
     * Create a row for displaying a stock movement
     */
    private fun createMovementRow(movement: JSONObject): View {
        val view = layoutInflater.inflate(R.layout.item_stock_movement, null)

        val tvMovementType = view.findViewById<TextView>(R.id.tv_movement_type)
        val tvQuantity = view.findViewById<TextView>(R.id.tv_quantity)
        val tvStockBefore = view.findViewById<TextView>(R.id.tv_stock_before)
        val tvStockAfter = view.findViewById<TextView>(R.id.tv_stock_after)
        val tvReason = view.findViewById<TextView>(R.id.tv_reason)
        val tvUser = view.findViewById<TextView>(R.id.tv_user)
        val tvTimestamp = view.findViewById<TextView>(R.id.tv_timestamp)

        val type = movement.optString("type", "unknown")
        val quantity = movement.optInt("quantity", 0)
        val previousStock = movement.optInt("previous_stock", 0)
        val newStock = movement.optInt("new_stock", 0)
        val reason = movement.optString("reason", "No reason provided")
        val userName = movement.optString("user_name", "Unknown user")
        val timestamp = movement.optString("timestamp", "")

        // Format movement type
        val formattedType = when (type) {
            "sale" -> "Sale"
            "purchase_order" -> "Purchase Order"
            "adjustment" -> "Adjustment"
            "initial" -> "Initial Stock"
            else -> type.replace("_", " ").replaceFirstChar { it.uppercase() }
        }

        tvMovementType.text = formattedType
        tvQuantity.text = if (quantity > 0) "+${NumberFormatter.formatInteger(quantity)}" else NumberFormatter.formatInteger(quantity)
        tvStockBefore.text = "Before: ${NumberFormatter.formatInteger(previousStock)}"
        tvStockAfter.text = "After: ${NumberFormatter.formatInteger(newStock)}"
        tvReason.text = reason
        tvUser.text = "By: $userName"

        // Format timestamp
        val formattedTime = formatTimestamp(timestamp)
        tvTimestamp.text = formattedTime

        // Color code quantity (green for in, red for out)
        val quantityColor = if (quantity > 0) {
            ContextCompat.getColor(this, R.color.primary_green)
        } else {
            ContextCompat.getColor(this, R.color.error_red)
        }
        tvQuantity.setTextColor(quantityColor)

        return view
    }

    /**
     * Format timestamp for display
     */
    private fun formatTimestamp(timestamp: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            val date = inputFormat.parse(timestamp.replace("Z", "").replace(".000", ""))
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            timestamp // Return original if parsing fails
        }
    }

    private fun showEmptyState(message: String) {
        llHistoryList.removeAllViews()

        val textView = TextView(this).apply {
            text = message
            setTextColor(ContextCompat.getColor(this@StockHistoryDetailActivity, R.color.text_secondary))
            textSize = 14f
            setPadding(32, 48, 32, 48)
            gravity = android.view.Gravity.CENTER
        }
        llHistoryList.addView(textView)
    }

    private fun showProgress(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == EDIT_COST_REQUEST_CODE && resultCode == RESULT_OK) {
            // Refresh item data and UI
            val updatedItemDataString = data?.getStringExtra("updated_item_data")
            if (!updatedItemDataString.isNullOrEmpty()) {
                itemData = JSONObject(updatedItemDataString)
                initializeViews() // Re-initialize views with updated data
                loadStockHistory() // Reload stock history

                // Return updated data to parent activity
                val resultIntent = Intent()
                resultIntent.putExtra("updated_item_data", updatedItemDataString)
                setResult(RESULT_OK, resultIntent)
            }
        }
    }
}
