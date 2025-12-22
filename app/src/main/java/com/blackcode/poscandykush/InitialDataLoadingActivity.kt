package com.blackcode.poscandykush

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

/**
 * Initial data loading screen shown after login success.
 * Loads current month's day-by-day data, processes it, and stores in cache.
 * Then navigates to Dashboard and continues background sync for all historical data.
 */
class InitialDataLoadingActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var cache: SalesDataCache
    private lateinit var dataProcessor: SalesDataProcessor

    private lateinit var tvLoadingStatus: TextView
    private lateinit var tvLoadingDetail: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgressPercent: TextView

    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val monthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())

    private var loadingJob: Job? = null

    companion object {
        const val BASE_URL = "https://pos-candy-kush.vercel.app/api/mobile"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_initial_loading)

        prefs = getSharedPreferences("admin_prefs", MODE_PRIVATE)
        cache = SalesDataCache(this)
        dataProcessor = SalesDataProcessor(cache)

        initViews()
        startInitialDataLoad()
    }

    private fun initViews() {
        tvLoadingStatus = findViewById(R.id.tv_loading_status)
        tvLoadingDetail = findViewById(R.id.tv_loading_detail)
        progressBar = findViewById(R.id.progress_bar_loading)
        tvProgressPercent = findViewById(R.id.tv_progress_percent)
    }

    private fun startInitialDataLoad() {
        loadingJob = lifecycleScope.launch(Dispatchers.Main) {
            try {
                updateStatus("Preparing...", "Setting up data sync")
                updateProgress(0)

                // Step 1: Load current month data (day by day)
                updateStatus("Loading sales data...", "Fetching current month data")
                updateProgress(5)

                val currentMonthData = loadCurrentMonthData()
                updateProgress(40)

                // Step 2: Process and aggregate the data
                updateStatus("Processing data...", "Calculating summaries")
                val processedData: ProcessedMonthData = withContext(Dispatchers.Default) {
                    dataProcessor.processMonthlyData(currentMonthData)
                }
                updateProgress(60)

                // Step 3: Save processed data to cache
                updateStatus("Saving data...", "Storing in local database")
                withContext(Dispatchers.IO) {
                    saveProcessedDataToCache(processedData)
                }
                updateProgress(80)

                // Step 4: Load stock data
                updateStatus("Loading inventory...", "Fetching stock levels")
                loadStockData()
                updateProgress(95)

                // Step 5: Mark initial load complete
                prefs.edit().putBoolean("initial_load_complete", true).apply()
                prefs.edit().putLong("last_full_sync_time", System.currentTimeMillis()).apply()

                updateStatus("Complete!", "Launching dashboard...")
                updateProgress(100)

                delay(500) // Brief pause to show completion

                // Navigate to dashboard
                navigateToDashboard()

            } catch (e: Exception) {
                e.printStackTrace()
                updateStatus("Error", "Failed to load data: ${e.message}")

                // Still navigate to dashboard even on error - it will retry
                delay(2000)
                navigateToDashboard()
            }
        }
    }

    private suspend fun loadCurrentMonthData(): CurrentMonthRawData {
        return withContext(Dispatchers.IO) {
            val calendar = Calendar.getInstance()
            val currentMonth = monthFormat.format(calendar.time)

            // Get current month date range
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            val startDate = dayFormat.format(calendar.time)
            calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
            val endDate = dayFormat.format(calendar.time)

            withContext(Dispatchers.Main) {
                updateStatus("Loading sales data...", "Fetching $currentMonth sales")
            }

            // Fetch all data types for current month
            val salesSummary = fetchData("sales-summary", startDate, endDate)
            withContext(Dispatchers.Main) { updateProgress(15) }

            val salesByItem = fetchData("sales-by-item", startDate, endDate)
            withContext(Dispatchers.Main) { updateProgress(25) }

            val salesByCategory = fetchData("sales-by-category", startDate, endDate)
            withContext(Dispatchers.Main) { updateProgress(30) }

            val salesByEmployee = fetchData("sales-by-employee", startDate, endDate)
            withContext(Dispatchers.Main) { updateProgress(35) }

            CurrentMonthRawData(
                monthKey = currentMonth,
                startDate = startDate,
                endDate = endDate,
                salesSummary = salesSummary,
                salesByItem = salesByItem,
                salesByCategory = salesByCategory,
                salesByEmployee = salesByEmployee
            )
        }
    }

    private suspend fun fetchData(action: String, startDate: String, endDate: String): JSONObject {
        return withContext(Dispatchers.IO) {
            val token = prefs.getString("jwt_token", "") ?: ""
            val urlString = "$BASE_URL?action=$action&period=custom&start_date=$startDate&end_date=$endDate"

            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection

            try {
                connection.apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 30000
                    readTimeout = 30000
                }

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val responseBody = connection.inputStream.bufferedReader().readText()
                    JSONObject(responseBody)
                } else {
                    throw Exception("API error: $responseCode")
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    private suspend fun loadStockData() {
        withContext(Dispatchers.IO) {
            try {
                val token = prefs.getString("jwt_token", "") ?: ""
                val urlString = "$BASE_URL?action=stock-history"
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection

                try {
                    connection.apply {
                        requestMethod = "GET"
                        setRequestProperty("Authorization", "Bearer $token")
                        setRequestProperty("Content-Type", "application/json")
                        connectTimeout = 30000
                        readTimeout = 30000
                    }

                    val responseCode = connection.responseCode
                    if (responseCode == 200) {
                        val responseBody = connection.inputStream.bufferedReader().readText()
                        val stockHistoryData = JSONObject(responseBody)

                        // Calculate stock from history and save
                        val processedStockData = calculateStockFromHistory(stockHistoryData)
                        cache.saveItemsToCache("items-stock", processedStockData)
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Non-critical error, continue anyway
            }
        }
    }

    /**
     * Calculate current stock for each product from stock history movements
     */
    private fun calculateStockFromHistory(stockHistoryResponse: JSONObject): JSONObject {
        val result = JSONObject()
        result.put("success", true)
        result.put("action", "stock")

        val dataObj = JSONObject()
        val itemsArray = org.json.JSONArray()

        if (stockHistoryResponse.optBoolean("success", false)) {
            val historyData = stockHistoryResponse.optJSONObject("data")
            val products = historyData?.optJSONArray("products")

            if (products != null) {
                for (i in 0 until products.length()) {
                    val product = products.optJSONObject(i) ?: continue

                    val productId = product.optString("product_id", "")
                    val productName = product.optString("product_name", "Unknown")
                    val productSku = product.optString("product_sku", "")
                    val movements = product.optJSONArray("movements")

                    // Calculate current stock from movements
                    val currentStock = calculateCurrentStockFromMovements(movements)

                    // Create item object
                    val itemObj = JSONObject().apply {
                        put("product_id", productId)
                        put("product_name", productName)
                        put("sku", productSku)
                        put("category", "Other")
                        put("current_stock", currentStock)
                        put("price", 0.0)
                        put("is_low_stock", currentStock <= 10)
                        put("is_out_of_stock", currentStock <= 0)
                    }

                    itemsArray.put(itemObj)
                }
            }
        }

        dataObj.put("items", itemsArray)
        result.put("data", dataObj)

        return result
    }

    /**
     * Calculate current stock from an array of movements
     */
    private fun calculateCurrentStockFromMovements(movements: org.json.JSONArray?): Int {
        if (movements == null || movements.length() == 0) {
            return 0
        }

        // Collect all movements with their timestamps
        val movementsList = mutableListOf<Pair<Long, Int>>()

        for (i in 0 until movements.length()) {
            val movement = movements.optJSONObject(i) ?: continue
            val timestamp = movement.optString("timestamp", "")
            val newStock = movement.optInt("new_stock", 0)

            // Parse timestamp
            val timeMillis = try {
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
                    .parse(timestamp.replace("Z", "").replace(".000", ""))?.time ?: 0L
            } catch (e: Exception) {
                0L
            }

            movementsList.add(Pair(timeMillis, newStock))
        }

        // Return the most recent new_stock value
        if (movementsList.isEmpty()) {
            return 0
        }

        val sortedMovements = movementsList.sortedBy { it.first }
        return sortedMovements.last().second
    }

    private fun saveProcessedDataToCache(processedData: ProcessedMonthData) {
        // Save the processed/aggregated data for quick dashboard display
        cache.saveToCacheWithDate(
            "sales-summary",
            "this_month",
            processedData.monthKey,
            processedData.startDate,
            processedData.endDate,
            processedData.aggregatedSummary
        )

        cache.saveToCacheWithDate(
            "sales-by-item",
            "this_month",
            processedData.monthKey,
            processedData.startDate,
            processedData.endDate,
            processedData.aggregatedItems
        )

        cache.saveToCacheWithDate(
            "sales-by-category",
            "this_month",
            processedData.monthKey,
            processedData.startDate,
            processedData.endDate,
            processedData.aggregatedCategories
        )

        cache.saveToCacheWithDate(
            "sales-by-employee",
            "this_month",
            processedData.monthKey,
            processedData.startDate,
            processedData.endDate,
            processedData.aggregatedEmployees
        )

        // Also save daily breakdown data
        processedData.dailyData.forEach { (dayKey, dayData) ->
            cache.saveToCacheWithDate(
                "sales-summary",
                "today",
                dayKey,
                dayKey,
                dayKey,
                dayData.summary
            )
            cache.saveToCacheWithDate(
                "sales-by-item",
                "today",
                dayKey,
                dayKey,
                dayKey,
                dayData.items
            )
            cache.saveToCacheWithDate(
                "sales-by-category",
                "today",
                dayKey,
                dayKey,
                dayKey,
                dayData.categories
            )
            cache.saveToCacheWithDate(
                "sales-by-employee",
                "today",
                dayKey,
                dayKey,
                dayKey,
                dayData.employees
            )
        }
    }

    private fun updateStatus(status: String, detail: String) {
        tvLoadingStatus.text = status
        tvLoadingDetail.text = detail
    }

    private fun updateProgress(percent: Int) {
        progressBar.progress = percent
        tvProgressPercent.text = "$percent%"
    }

    private fun navigateToDashboard() {
        val intent = Intent(this, DashboardActivity::class.java)
        intent.putExtra("start_background_sync", true)
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        loadingJob?.cancel()
    }
}

/**
 * Data class to hold raw API response data for current month
 */
data class CurrentMonthRawData(
    val monthKey: String,
    val startDate: String,
    val endDate: String,
    val salesSummary: JSONObject,
    val salesByItem: JSONObject,
    val salesByCategory: JSONObject,
    val salesByEmployee: JSONObject
)

/**
 * Data class for processed/aggregated month data
 */
data class ProcessedMonthData(
    val monthKey: String,
    val startDate: String,
    val endDate: String,
    val aggregatedSummary: JSONObject,
    val aggregatedItems: JSONObject,
    val aggregatedCategories: JSONObject,
    val aggregatedEmployees: JSONObject,
    val dailyData: Map<String, DailyProcessedData>
)

data class DailyProcessedData(
    val summary: JSONObject,
    val items: JSONObject,
    val categories: JSONObject,
    val employees: JSONObject
)

