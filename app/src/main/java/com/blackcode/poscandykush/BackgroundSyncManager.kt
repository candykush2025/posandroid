package com.blackcode.poscandykush

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

/**
 * Background sync manager for downloading historical sales data and items stock
 * Syncs data month by month and day by day for accurate dashboard display
 * Supports pause/resume for swipe refresh functionality
 */
class BackgroundSyncManager(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val cache: SalesDataCache
) {
    private var syncJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val monthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val yearFormat = SimpleDateFormat("yyyy", Locale.getDefault())
    private val weekFormat = SimpleDateFormat("yyyy-'w'ww", Locale.getDefault())

    // Pause state for swipe refresh
    @Volatile
    private var isPaused = false

    // Track current sync position for resume
    private var currentSyncMonthIndex = 0
    private var monthsToSyncList = mutableListOf<String>()

    // Data processor for raw data processing
    private lateinit var dataProcessor: SalesDataProcessor

    // Cache freshness thresholds
    companion object {
        const val CURRENT_DATA_MAX_AGE = 2 * 60 * 1000L // 2 minutes for current day/month
        const val HISTORICAL_DATA_MAX_AGE = 24 * 60 * 60 * 1000L // 24 hours for historical data
        const val ITEMS_STOCK_MAX_AGE = 5 * 60 * 1000L // 5 minutes for stock data
        const val BASE_URL = "https://pos-candy-kush.vercel.app/api/mobile"

        // Earliest date to sync from (adjust based on when your POS started)
        const val EARLIEST_YEAR = 2020
    }

    interface SyncCallback {
        fun onSyncProgress(monthsSynced: Int, totalMonths: Int)
        fun onSyncComplete()
        fun onSyncError(error: String)
        fun onSyncPaused()
        fun onSyncResumed()
    }

    private var callback: SyncCallback? = null

    fun setSyncCallback(callback: SyncCallback?) {
        this.callback = callback
    }

    /**
     * Initialize the data processor
     */
    fun initProcessor() {
        if (!::dataProcessor.isInitialized) {
            dataProcessor = SalesDataProcessor(cache)
        }
    }

    /**
     * Pause sync (for swipe refresh)
     */
    fun pauseSync() {
        isPaused = true
        scope.launch(Dispatchers.Main) {
            callback?.onSyncPaused()
        }
    }

    /**
     * Resume sync (after swipe refresh completes)
     */
    fun resumeSync() {
        isPaused = false
        scope.launch(Dispatchers.Main) {
            callback?.onSyncResumed()
        }

        // If sync job was running, resume from where we left off
        if (syncJob?.isActive == true && currentSyncMonthIndex < monthsToSyncList.size) {
            // The sync loop will continue automatically when isPaused becomes false
        } else if (monthsToSyncList.isNotEmpty() && currentSyncMonthIndex < monthsToSyncList.size) {
            // Restart sync from current position
            startBackgroundSyncFromPosition()
        }
    }

    /**
     * Check if sync is complete (no more months to sync)
     */
    fun isSyncComplete(): Boolean {
        return monthsToSyncList.isEmpty() || currentSyncMonthIndex >= monthsToSyncList.size
    }

    /**
     * Check if sync is paused
     */
    fun isSyncPaused(): Boolean = isPaused

    /**
     * Start background sync - downloads ALL historical data month by month
     * Goes back to EARLIEST_YEAR or until no more data is found
     */
    fun startBackgroundSync(monthsBack: Int = -1) {
        initProcessor()

        if (syncJob?.isActive == true) {
            return // Already syncing
        }

        // Reset state
        isPaused = false
        currentSyncMonthIndex = 0
        monthsToSyncList.clear()

        syncJob = scope.launch {
            try {
                // First sync items/stock data (most important)
                syncItemsStockData()
                delay(300)

                val calendar = Calendar.getInstance()
                val currentMonth = monthFormat.format(calendar.time)

                // Build list of months to sync - ALL historical data
                if (monthsBack == -1) {
                    // Sync all historical data back to EARLIEST_YEAR
                    val earliestCalendar = Calendar.getInstance()
                    earliestCalendar.set(EARLIEST_YEAR, 0, 1)

                    while (calendar.after(earliestCalendar) ||
                           (calendar.get(Calendar.YEAR) == earliestCalendar.get(Calendar.YEAR) &&
                            calendar.get(Calendar.MONTH) >= earliestCalendar.get(Calendar.MONTH))) {
                        monthsToSyncList.add(monthFormat.format(calendar.time))
                        calendar.add(Calendar.MONTH, -1)
                    }
                } else {
                    // Sync specific number of months
                    for (i in 0 until monthsBack) {
                        monthsToSyncList.add(monthFormat.format(calendar.time))
                        calendar.add(Calendar.MONTH, -1)
                    }
                }

                // Start syncing from beginning
                startBackgroundSyncFromPosition()

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback?.onSyncError(e.message ?: "Unknown error")
                }
            }
        }
    }

    /**
     * Internal method to continue sync from current position
     */
    private fun startBackgroundSyncFromPosition() {
        syncJob = scope.launch {
            try {
                val currentMonth = monthFormat.format(Calendar.getInstance().time)

                while (currentSyncMonthIndex < monthsToSyncList.size) {
                    // Wait while paused
                    while (isPaused) {
                        delay(500)
                    }

                    if (!isActive) break

                    val monthKey = monthsToSyncList[currentSyncMonthIndex]
                    val isCurrentMonth = monthKey == currentMonth
                    val maxAge = if (isCurrentMonth) CURRENT_DATA_MAX_AGE else HISTORICAL_DATA_MAX_AGE

                    // Check if already cached and fresh
                    if (cache.isCacheFreshByDate("sales-summary", "this_month", monthKey, maxAge)) {
                        currentSyncMonthIndex++
                        withContext(Dispatchers.Main) {
                            callback?.onSyncProgress(currentSyncMonthIndex, monthsToSyncList.size)
                        }
                        continue
                    }

                    try {
                        // Get date range for this month
                        val (startDate, endDate) = getMonthDateRange(monthKey)

                        // Fetch raw data from API
                        val salesSummary = fetchApiData("sales-summary", startDate, endDate)
                        delay(200)

                        // Check for pause after each API call
                        while (isPaused) { delay(500) }

                        val salesByItem = fetchApiData("sales-by-item", startDate, endDate)
                        delay(200)

                        while (isPaused) { delay(500) }

                        val salesByCategory = fetchApiData("sales-by-category", startDate, endDate)
                        delay(200)

                        while (isPaused) { delay(500) }

                        val salesByEmployee = fetchApiData("sales-by-employee", startDate, endDate)
                        delay(200)

                        // Process raw data on device
                        val processedData = dataProcessor.processRawMonthData(
                            monthKey, startDate, endDate,
                            salesSummary, salesByItem, salesByCategory, salesByEmployee
                        )

                        // Save processed data to cache
                        saveProcessedMonthData(processedData)

                        // Also sync individual days of current month for day-by-day view
                        if (isCurrentMonth) {
                            syncCurrentMonthDays(monthKey)
                        }

                        currentSyncMonthIndex++
                        withContext(Dispatchers.Main) {
                            callback?.onSyncProgress(currentSyncMonthIndex, monthsToSyncList.size)
                        }

                        delay(300) // Rate limiting between months

                    } catch (e: Exception) {
                        // Continue with next month on error
                        e.printStackTrace()
                        currentSyncMonthIndex++
                    }
                }

                // Save last sync timestamp
                prefs.edit().putLong("last_background_sync", System.currentTimeMillis()).apply()

                withContext(Dispatchers.Main) {
                    callback?.onSyncComplete()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback?.onSyncError(e.message ?: "Unknown error")
                }
            }
        }
    }

    /**
     * Save processed month data to cache
     */
    private fun saveProcessedMonthData(processedData: ProcessedMonthData) {
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
            if (dayData.summary.length() > 0) {
                cache.saveToCacheWithDate("sales-summary", "today", dayKey, dayKey, dayKey, dayData.summary)
            }
            if (dayData.items.length() > 0) {
                cache.saveToCacheWithDate("sales-by-item", "today", dayKey, dayKey, dayKey, dayData.items)
            }
            if (dayData.categories.length() > 0) {
                cache.saveToCacheWithDate("sales-by-category", "today", dayKey, dayKey, dayKey, dayData.categories)
            }
            if (dayData.employees.length() > 0) {
                cache.saveToCacheWithDate("sales-by-employee", "today", dayKey, dayKey, dayKey, dayData.employees)
            }
        }
    }

    /**
     * Fetch data from API
     */
    private suspend fun fetchApiData(action: String, startDate: String, endDate: String): JSONObject {
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
                    JSONObject().apply { put("success", false) }
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    /**
     * Sync current month's individual days
     */
    private suspend fun syncCurrentMonthDays(monthKey: String) {
        val calendar = Calendar.getInstance()
        val today = dayFormat.format(calendar.time)

        // Sync last 7 days
        for (i in 0..6) {
            // Wait while paused
            while (isPaused) { delay(500) }

            if (!scope.isActive) break

            val dayKey = dayFormat.format(calendar.time)
            val isCurrent = dayKey == today
            val maxAge = if (isCurrent) CURRENT_DATA_MAX_AGE else HISTORICAL_DATA_MAX_AGE

            if (!cache.isCacheFreshByDate("sales-summary", "today", dayKey, maxAge)) {
                try {
                    syncDayData("sales-summary", dayKey)
                    delay(200)

                    while (isPaused) { delay(500) }

                    syncDayData("sales-by-item", dayKey)
                    delay(200)

                    while (isPaused) { delay(500) }

                    syncDayData("sales-by-category", dayKey)
                    delay(200)

                    while (isPaused) { delay(500) }

                    syncDayData("sales-by-employee", dayKey)
                    delay(300)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            calendar.add(Calendar.DAY_OF_MONTH, -1)
        }
    }

    /**
     * Sync single day data for a specific action type
     */
    private suspend fun syncDayData(action: String, dayKey: String) {
        val token = prefs.getString("jwt_token", "") ?: ""
        val urlString = "$BASE_URL?action=$action&period=custom&start_date=$dayKey&end_date=$dayKey"

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
                val rawData = JSONObject(responseBody)

                // Process the single day data
                val processedData = processSingleDayData(action, rawData)
                cache.saveToCacheWithDate(action, "today", dayKey, dayKey, dayKey, processedData)
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Process single day raw data into the expected format
     */
    private fun processSingleDayData(action: String, rawData: JSONObject): JSONObject {
        if (!rawData.optBoolean("success", false)) {
            return rawData
        }

        val dataObj = rawData.optJSONObject("data") ?: return rawData
        val dailyDataArray = dataObj.optJSONArray("daily_data")

        if (dailyDataArray == null || dailyDataArray.length() == 0) {
            return rawData
        }

        // Get the first (and should be only) day's data
        val dayObj = dailyDataArray.optJSONObject(0) ?: return rawData

        return when (action) {
            "sales-summary" -> {
                val metrics = dayObj.optJSONObject("metrics") ?: JSONObject()
                val transactions = dayObj.optJSONObject("transactions") ?: JSONObject()
                JSONObject().apply {
                    put("success", true)
                    put("action", action)
                    put("data", JSONObject().apply {
                        put("metrics", metrics)
                        put("transactions", transactions)
                        put("chart_data", org.json.JSONArray())
                    })
                }
            }
            "sales-by-item" -> {
                val items = dayObj.optJSONArray("items") ?: org.json.JSONArray()
                JSONObject().apply {
                    put("success", true)
                    put("action", action)
                    put("data", JSONObject().apply {
                        put("items", items)
                    })
                }
            }
            "sales-by-category" -> {
                val categories = dayObj.optJSONArray("categories") ?: org.json.JSONArray()
                JSONObject().apply {
                    put("success", true)
                    put("action", action)
                    put("data", JSONObject().apply {
                        put("categories", categories)
                    })
                }
            }
            "sales-by-employee" -> {
                val employees = dayObj.optJSONArray("employees") ?: org.json.JSONArray()
                JSONObject().apply {
                    put("success", true)
                    put("action", action)
                    put("data", JSONObject().apply {
                        put("employees", employees)
                    })
                }
            }
            else -> rawData
        }
    }

    /**
     * Sync single month data for a specific action type (legacy method)
     */
    private suspend fun syncMonthData(action: String, monthKey: String, startDate: String, endDate: String) {
        val data = fetchApiData(action, startDate, endDate)
        if (data.optBoolean("success", false)) {
            // Process and save the data
            cache.saveToCacheWithDate(action, "this_month", monthKey, startDate, endDate, data)
        }
    }

    /**
     * Sync items/stock data from stock-history API
     */
    private suspend fun syncItemsStockData() {
        if (cache.isItemsCacheFresh("items-stock", ITEMS_STOCK_MAX_AGE)) {
            return
        }

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
                        put("category", "Other") // Category determined on display
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

            // Parse timestamp to get sortable value
            val timeMillis = try {
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
                    .parse(timestamp.replace("Z", "").replace(".000", ""))?.time ?: 0L
            } catch (e: Exception) {
                0L
            }

            movementsList.add(Pair(timeMillis, newStock))
        }

        // Sort by timestamp (newest last) and return the most recent new_stock value
        if (movementsList.isEmpty()) {
            return 0
        }

        val sortedMovements = movementsList.sortedBy { it.first }
        return sortedMovements.last().second
    }

    /**
     * Sync specific date range (for manual refresh)
     */
    suspend fun syncDateRange(startDate: String, endDate: String, period: String, dateKey: String) {
        initProcessor()

        val actions = listOf("sales-summary", "sales-by-item", "sales-by-category", "sales-by-employee")

        // Fetch all raw data
        val salesSummary = fetchApiData("sales-summary", startDate, endDate)
        delay(200)
        val salesByItem = fetchApiData("sales-by-item", startDate, endDate)
        delay(200)
        val salesByCategory = fetchApiData("sales-by-category", startDate, endDate)
        delay(200)
        val salesByEmployee = fetchApiData("sales-by-employee", startDate, endDate)

        // Process and save the data
        val processedData = dataProcessor.processRawMonthData(
            dateKey, startDate, endDate,
            salesSummary, salesByItem, salesByCategory, salesByEmployee
        )

        // Save processed data
        cache.saveToCacheWithDate("sales-summary", period, dateKey, startDate, endDate, processedData.aggregatedSummary)
        cache.saveToCacheWithDate("sales-by-item", period, dateKey, startDate, endDate, processedData.aggregatedItems)
        cache.saveToCacheWithDate("sales-by-category", period, dateKey, startDate, endDate, processedData.aggregatedCategories)
        cache.saveToCacheWithDate("sales-by-employee", period, dateKey, startDate, endDate, processedData.aggregatedEmployees)
    }

    /**
     * Get start and end dates for a month
     */
    private fun getMonthDateRange(monthKey: String): Pair<String, String> {
        val calendar = Calendar.getInstance()
        val parts = monthKey.split("-")
        calendar.set(Calendar.YEAR, parts[0].toInt())
        calendar.set(Calendar.MONTH, parts[1].toInt() - 1)
        calendar.set(Calendar.DAY_OF_MONTH, 1)

        val startDate = dayFormat.format(calendar.time)

        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        val endDate = dayFormat.format(calendar.time)

        return Pair(startDate, endDate)
    }

    /**
     * Get date key for a specific period type and date
     */
    fun getDateKey(period: String, date: Calendar): String {
        return when (period) {
            "today", "custom" -> dayFormat.format(date.time)
            "this_week" -> weekFormat.format(date.time)
            "this_month" -> monthFormat.format(date.time)
            "this_year" -> yearFormat.format(date.time)
            else -> dayFormat.format(date.time)
        }
    }

    /**
     * Get date range for a specific period type and date
     */
    fun getDateRange(period: String, date: Calendar): Pair<String, String> {
        val cal = date.clone() as Calendar

        return when (period) {
            "today", "custom" -> {
                val dayStr = dayFormat.format(cal.time)
                Pair(dayStr, dayStr)
            }
            "this_week" -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                val startDate = dayFormat.format(cal.time)
                cal.add(Calendar.DAY_OF_WEEK, 6)
                val endDate = dayFormat.format(cal.time)
                Pair(startDate, endDate)
            }
            "this_month" -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val startDate = dayFormat.format(cal.time)
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                val endDate = dayFormat.format(cal.time)
                Pair(startDate, endDate)
            }
            "this_year" -> {
                cal.set(Calendar.MONTH, 0)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val startDate = dayFormat.format(cal.time)
                cal.set(Calendar.MONTH, 11)
                cal.set(Calendar.DAY_OF_MONTH, 31)
                val endDate = dayFormat.format(cal.time)
                Pair(startDate, endDate)
            }
            else -> {
                val dayStr = dayFormat.format(cal.time)
                Pair(dayStr, dayStr)
            }
        }
    }

    /**
     * Stop background sync
     */
    fun stopSync() {
        syncJob?.cancel()
        syncJob = null
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        stopSync()
        scope.cancel()
    }
}

