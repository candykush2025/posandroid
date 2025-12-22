package com.blackcode.poscandykush

import android.app.DatePickerDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class DashboardActivity : AppCompatActivity(), BackgroundSyncManager.SyncCallback {

    private lateinit var prefs: SharedPreferences
    private lateinit var tvDate: TextView
    private lateinit var btnPrevDate: ImageButton
    private lateinit var btnNextDate: ImageButton
    private lateinit var cvSalesSummary: CardView
    private lateinit var tvGrossSales: TextView
    private lateinit var tvNetSales: TextView
    private lateinit var salesChart: LineChart
    private lateinit var llItemsList: LinearLayout
    private lateinit var llCategoriesList: LinearLayout
    private lateinit var llEmployeesList: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var llSyncStatus: LinearLayout
    private lateinit var tvSyncStatus: TextView
    private lateinit var statusBarBackground: View

    private lateinit var cache: SalesDataCache
    private lateinit var syncManager: BackgroundSyncManager
    private lateinit var dataProcessor: SalesDataProcessor

    private var currentPeriod = "this_month"
    private var currentDate = Calendar.getInstance()

    // Date formatters
    private val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
    private val monthYearFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    private val dayKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val monthKeyFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
    private val yearKeyFormat = SimpleDateFormat("yyyy", Locale.getDefault())
    private val weekKeyFormat = SimpleDateFormat("yyyy-'w'ww", Locale.getDefault())

    // Track if data is being loaded to prevent duplicate requests
    private var isLoadingData = false

    // Track if background sync is paused for swipe refresh
    private var isSyncPausedForRefresh = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        // Setup status bar
        setupStatusBar()

        prefs = getSharedPreferences("admin_prefs", MODE_PRIVATE)
        cache = SalesDataCache(this)
        dataProcessor = SalesDataProcessor(cache)
        syncManager = BackgroundSyncManager(this, prefs, cache)
        syncManager.setSyncCallback(this)

        // Check authentication
        if (!isAuthenticated()) {
            navigateToLogin()
            return
        }

        initializeViews()
        setupBottomNavigation()
        setupDateNavigation()
        setupSwipeRefresh()

        // Clear UI first to show we're loading fresh
        clearAllUI()

        // Load data for current selection
        loadDashboardData(forceRefresh = false)

        // Check if we should start full background sync (coming from initial loading)
        val startFullSync = intent.getBooleanExtra("start_background_sync", false)
        if (startFullSync) {
            // Start background sync for ALL historical data (not just 12 months)
            syncManager.startBackgroundSync(-1) // -1 means sync all historical data
        } else {
            // Start background sync for last 12 months only
            syncManager.startBackgroundSync(12)
        }
    }

    private fun setupStatusBar() {
        // Set status bar color
        window.statusBarColor = ContextCompat.getColor(this, R.color.primary_green)

        // Set navigation bar color
        window.navigationBarColor = ContextCompat.getColor(this, R.color.white)

        // Make status bar icons light (white) on green background
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
        tvDate = findViewById(R.id.tv_date)
        btnPrevDate = findViewById(R.id.btn_prev_date)
        btnNextDate = findViewById(R.id.btn_next_date)
        cvSalesSummary = findViewById(R.id.cv_sales_summary)
        tvGrossSales = findViewById(R.id.tv_gross_sales)
        tvNetSales = findViewById(R.id.tv_net_sales)
        salesChart = findViewById(R.id.sales_chart)
        llItemsList = findViewById(R.id.ll_items_list)
        llCategoriesList = findViewById(R.id.ll_categories_list)
        llEmployeesList = findViewById(R.id.ll_employees_list)
        progressBar = findViewById(R.id.progress_bar)
        bottomNavigation = findViewById(R.id.bottom_navigation)
        swipeRefresh = findViewById(R.id.swipe_refresh)
        llSyncStatus = findViewById(R.id.ll_sync_status)
        tvSyncStatus = findViewById(R.id.tv_sync_status)
        statusBarBackground = findViewById(R.id.status_bar_background)

        // Set status bar height
        val statusBarHeight = getStatusBarHeight()
        statusBarBackground.layoutParams.height = statusBarHeight

        // Setup chart
        setupSalesChart()

        // Setup click listeners
        cvSalesSummary.setOnClickListener {
            openSalesSummaryDetail()
        }

        findViewById<TextView>(R.id.tv_view_all_items).setOnClickListener {
            openItemsDetail()
        }

        findViewById<TextView>(R.id.tv_view_all_categories).setOnClickListener {
            openCategoriesDetail()
        }

        findViewById<TextView>(R.id.tv_view_all_employees).setOnClickListener {
            openEmployeesDetail()
        }
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    private fun setupBottomNavigation() {
        bottomNavigation.selectedItemId = R.id.nav_sales

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_sales -> true
                R.id.nav_items -> {
                    startActivity(Intent(this, ItemsActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
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
            // Only pause sync if it's still running (not complete)
            if (!syncManager.isSyncComplete()) {
                syncManager.pauseSync()
                isSyncPausedForRefresh = true
            }

            // Force refresh from API - this is the ONLY place that fetches new data
            // Navigation arrows use local cache only
            // For single day view, only fetch that day's data (fast)
            // For month/year view, fetch the full range
            loadDashboardData(forceRefresh = true)
        }
    }

    private fun setupDateNavigation() {
        updateDateDisplay()

        tvDate.setOnClickListener {
            showDateFilterDialog()
        }

        btnPrevDate.setOnClickListener {
            navigateDate(-1)
        }

        btnNextDate.setOnClickListener {
            navigateDate(1)
        }
    }

    private fun updateDateDisplay() {
        tvDate.text = when (currentPeriod) {
            "today", "custom" -> dateFormat.format(currentDate.time)
            "this_week" -> getWeekDisplayText()
            "this_month" -> monthYearFormat.format(currentDate.time)
            "this_year" -> "Year ${currentDate.get(Calendar.YEAR)}"
            else -> dateFormat.format(currentDate.time)
        }
    }

    private fun getWeekDisplayText(): String {
        val cal = currentDate.clone() as Calendar
        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        val startDay = SimpleDateFormat("dd MMM", Locale.getDefault()).format(cal.time)
        cal.add(Calendar.DAY_OF_WEEK, 6)
        val endDay = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(cal.time)
        return "$startDay - $endDay"
    }

    private fun showDateFilterDialog() {
        val options = arrayOf("Today", "This Week", "This Month", "This Year", "Custom")

        AlertDialog.Builder(this)
            .setTitle("Select Period")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        currentPeriod = "today"
                        currentDate = Calendar.getInstance()
                    }
                    1 -> {
                        currentPeriod = "this_week"
                        currentDate = Calendar.getInstance()
                    }
                    2 -> {
                        currentPeriod = "this_month"
                        currentDate = Calendar.getInstance()
                    }
                    3 -> {
                        currentPeriod = "this_year"
                        currentDate = Calendar.getInstance()
                    }
                    4 -> showCustomDatePicker()
                }
                if (which != 4) {
                    updateDateDisplay()
                    clearAllUI() // Clear old data before loading new
                    // Use local cache only - don't fetch from API
                    loadFromCacheOnly()
                }
            }
            .show()
    }

    private fun showCustomDatePicker() {
        val cal = currentDate.clone() as Calendar
        DatePickerDialog(
            this,
            { _, year, month, day ->
                currentDate.set(year, month, day)
                currentPeriod = "custom"
                updateDateDisplay()
                clearAllUI() // Clear old data before loading new
                // Use local cache only - don't fetch from API
                loadFromCacheOnly()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun navigateDate(direction: Int) {
        when (currentPeriod) {
            "today", "custom" -> currentDate.add(Calendar.DAY_OF_MONTH, direction)
            "this_week" -> currentDate.add(Calendar.WEEK_OF_YEAR, direction)
            "this_month" -> currentDate.add(Calendar.MONTH, direction)
            "this_year" -> currentDate.add(Calendar.YEAR, direction)
        }
        updateDateDisplay()
        clearAllUI() // Clear old data immediately when navigating
        // Use local cache only - don't fetch from API
        loadFromCacheOnly()
    }

    private fun setupSalesChart() {
        salesChart.apply {
            description = Description().apply { text = "" }
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)
            setNoDataText("No sales data available")
            setNoDataTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.text_secondary))
        }
    }

    /**
     * Get the unique date key for the current period and date
     * This ensures each date/month/year has its own cache entry
     */
    private fun getCurrentDateKey(): String {
        return when (currentPeriod) {
            "today", "custom" -> dayKeyFormat.format(currentDate.time)
            "this_week" -> weekKeyFormat.format(currentDate.time)
            "this_month" -> monthKeyFormat.format(currentDate.time)
            "this_year" -> yearKeyFormat.format(currentDate.time)
            else -> dayKeyFormat.format(currentDate.time)
        }
    }

    /**
     * Get the date range for API calls based on current period and date
     */
    private fun getCurrentDateRange(): Pair<String, String> {
        val cal = currentDate.clone() as Calendar

        return when (currentPeriod) {
            "today", "custom" -> {
                val dayStr = dayKeyFormat.format(cal.time)
                Pair(dayStr, dayStr)
            }
            "this_week" -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                val startDate = dayKeyFormat.format(cal.time)
                cal.add(Calendar.DAY_OF_WEEK, 6)
                val endDate = dayKeyFormat.format(cal.time)
                Pair(startDate, endDate)
            }
            "this_month" -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val startDate = dayKeyFormat.format(cal.time)
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                val endDate = dayKeyFormat.format(cal.time)
                Pair(startDate, endDate)
            }
            "this_year" -> {
                cal.set(Calendar.MONTH, 0)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val startDate = dayKeyFormat.format(cal.time)
                cal.set(Calendar.MONTH, 11)
                cal.set(Calendar.DAY_OF_MONTH, 31)
                val endDate = dayKeyFormat.format(cal.time)
                Pair(startDate, endDate)
            }
            else -> {
                val dayStr = dayKeyFormat.format(cal.time)
                Pair(dayStr, dayStr)
            }
        }
    }

    /**
     * Check if the current date selection is for current/recent data (needs fresher cache)
     */
    private fun isCurrentPeriod(): Boolean {
        val today = Calendar.getInstance()
        return when (currentPeriod) {
            "today", "custom" -> {
                currentDate.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                currentDate.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
            }
            "this_month" -> {
                currentDate.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                currentDate.get(Calendar.MONTH) == today.get(Calendar.MONTH)
            }
            "this_year" -> {
                currentDate.get(Calendar.YEAR) == today.get(Calendar.YEAR)
            }
            else -> false
        }
    }

    /**
     * Clear UI to show empty state before loading new data
     */
    private fun clearAllUI() {
        tvGrossSales.text = "---"
        tvNetSales.text = "---"
        llItemsList.removeAllViews()
        llCategoriesList.removeAllViews()
        llEmployeesList.removeAllViews()
        salesChart.clear()
        salesChart.invalidate()
    }

    /**
     * Clear cache for current date selection
     */
    private fun clearCacheForCurrentDate() {
        val dateKey = getCurrentDateKey()
        cache.clearCacheForDate(dateKey)
    }

    /**
     * Load data from local cache ONLY - no API calls
     * Used when navigating dates with arrows or changing period filter
     */
    private fun loadFromCacheOnly() {
        val dateKey = getCurrentDateKey()

        // Try to load from cache
        val cachedSummary = cache.getFromCacheByDate("sales-summary", currentPeriod, dateKey)
        val cachedItems = cache.getFromCacheByDate("sales-by-item", currentPeriod, dateKey)
        val cachedCategories = cache.getFromCacheByDate("sales-by-category", currentPeriod, dateKey)
        val cachedEmployees = cache.getFromCacheByDate("sales-by-employee", currentPeriod, dateKey)

        if (cachedSummary != null) {
            // Show cached data
            updateSalesSummary(cachedSummary.first)
            updateSalesChart(cachedSummary.first)
            if (cachedItems != null) updateItemsList(cachedItems.first)
            if (cachedCategories != null) updateCategoriesList(cachedCategories.first)
            if (cachedEmployees != null) updateEmployeesList(cachedEmployees.first)
        } else {
            // No cached data for this date - show empty state with message
            showNoDataState("No cached data for this date. Swipe down to refresh.")
        }
    }

    /**
     * Show no data state with custom message
     */
    private fun showNoDataState(message: String) {
        tvGrossSales.text = "$0.00"
        tvNetSales.text = "$0.00"
        llItemsList.removeAllViews()
        llCategoriesList.removeAllViews()
        llEmployeesList.removeAllViews()

        // Add message to each section
        addEmptyStateMessage(llItemsList, message)
        addEmptyStateMessage(llCategoriesList, message)
        addEmptyStateMessage(llEmployeesList, message)

        salesChart.clear()
        salesChart.invalidate()
    }

    /**
     * Main data loading function with proper date-based caching
     * This fetches from API - use loadFromCacheOnly() for local-only loading
     */
    private fun loadDashboardData(forceRefresh: Boolean = false) {
        if (isLoadingData) return
        isLoadingData = true

        showProgress(true)

        val dateKey = getCurrentDateKey()
        val (startDate, endDate) = getCurrentDateRange()
        val maxAge = if (isCurrentPeriod()) {
            BackgroundSyncManager.CURRENT_DATA_MAX_AGE
        } else {
            BackgroundSyncManager.HISTORICAL_DATA_MAX_AGE
        }

        lifecycleScope.launch(Dispatchers.Main) {
            try {
                var hasValidCachedData = false

                // Try to load from cache first (only if not force refreshing)
                if (!forceRefresh) {
                    val cachedSummary = cache.getFromCacheByDate("sales-summary", currentPeriod, dateKey)
                    val cachedItems = cache.getFromCacheByDate("sales-by-item", currentPeriod, dateKey)
                    val cachedCategories = cache.getFromCacheByDate("sales-by-category", currentPeriod, dateKey)
                    val cachedEmployees = cache.getFromCacheByDate("sales-by-employee", currentPeriod, dateKey)

                    // Check if cache is fresh enough
                    val isCacheFresh = cachedSummary != null &&
                        (System.currentTimeMillis() - cachedSummary.second) < maxAge

                    if (isCacheFresh) {
                        // Use cached data - it's fresh enough
                        updateSalesSummary(cachedSummary!!.first)
                        updateSalesChart(cachedSummary.first)
                        if (cachedItems != null) updateItemsList(cachedItems.first)
                        if (cachedCategories != null) updateCategoriesList(cachedCategories.first)
                        if (cachedEmployees != null) updateEmployeesList(cachedEmployees.first)

                        hasValidCachedData = true
                        showProgress(false)
                        swipeRefresh.isRefreshing = false
                        isLoadingData = false

                        // For current periods, still fetch in background to update
                        if (isCurrentPeriod()) {
                            fetchAndUpdateInBackground(dateKey, startDate, endDate)
                        }
                        return@launch
                    } else if (cachedSummary != null) {
                        // Show stale cached data while fetching fresh data
                        updateSalesSummary(cachedSummary.first)
                        updateSalesChart(cachedSummary.first)
                        if (cachedItems != null) updateItemsList(cachedItems.first)
                        if (cachedCategories != null) updateCategoriesList(cachedCategories.first)
                        if (cachedEmployees != null) updateEmployeesList(cachedEmployees.first)
                        hasValidCachedData = true
                    }
                }

                // Fetch fresh RAW data from API
                val rawSalesSummary = fetchRawApiData("sales-summary", startDate, endDate)
                val rawItemsData = fetchRawApiData("sales-by-item", startDate, endDate)
                val rawCategoriesData = fetchRawApiData("sales-by-category", startDate, endDate)
                val rawEmployeesData = fetchRawApiData("sales-by-employee", startDate, endDate)

                // For single day (today/custom), process differently than month/year
                val isSingleDay = (currentPeriod == "today" || currentPeriod == "custom") && startDate == endDate

                if (isSingleDay) {
                    // Single day - process directly without month aggregation (fast)
                    val processedSummary = processSingleDayResponse("sales-summary", rawSalesSummary)
                    val processedItems = processSingleDayResponse("sales-by-item", rawItemsData)
                    val processedCategories = processSingleDayResponse("sales-by-category", rawCategoriesData)
                    val processedEmployees = processSingleDayResponse("sales-by-employee", rawEmployeesData)

                    // Save processed data to cache
                    cache.saveToCacheWithDate("sales-summary", currentPeriod, dateKey, startDate, endDate, processedSummary)
                    cache.saveToCacheWithDate("sales-by-item", currentPeriod, dateKey, startDate, endDate, processedItems)
                    cache.saveToCacheWithDate("sales-by-category", currentPeriod, dateKey, startDate, endDate, processedCategories)
                    cache.saveToCacheWithDate("sales-by-employee", currentPeriod, dateKey, startDate, endDate, processedEmployees)

                    // Update UI
                    updateSalesSummary(processedSummary)
                    updateSalesChart(processedSummary)
                    updateItemsList(processedItems)
                    updateCategoriesList(processedCategories)
                    updateEmployeesList(processedEmployees)
                } else {
                    // Month/Year/Week - process with full aggregation
                    val processedData = withContext(Dispatchers.Default) {
                        dataProcessor.processRawMonthData(
                            dateKey, startDate, endDate,
                            rawSalesSummary, rawItemsData, rawCategoriesData, rawEmployeesData
                        )
                    }

                    // Save processed data to cache with proper date key
                    cache.saveToCacheWithDate("sales-summary", currentPeriod, dateKey, startDate, endDate, processedData.aggregatedSummary)
                    cache.saveToCacheWithDate("sales-by-item", currentPeriod, dateKey, startDate, endDate, processedData.aggregatedItems)
                    cache.saveToCacheWithDate("sales-by-category", currentPeriod, dateKey, startDate, endDate, processedData.aggregatedCategories)
                    cache.saveToCacheWithDate("sales-by-employee", currentPeriod, dateKey, startDate, endDate, processedData.aggregatedEmployees)

                    // Update UI with processed data
                    updateSalesSummary(processedData.aggregatedSummary)
                    updateSalesChart(processedData.aggregatedSummary)
                    updateItemsList(processedData.aggregatedItems)
                    updateCategoriesList(processedData.aggregatedCategories)
                    updateEmployeesList(processedData.aggregatedEmployees)
                }

                showProgress(false)
                swipeRefresh.isRefreshing = false

                // Resume background sync if it was paused for refresh
                if (isSyncPausedForRefresh) {
                    isSyncPausedForRefresh = false
                    syncManager.resumeSync()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                showProgress(false)
                swipeRefresh.isRefreshing = false

                // Resume background sync if it was paused for refresh
                if (isSyncPausedForRefresh) {
                    isSyncPausedForRefresh = false
                    syncManager.resumeSync()
                }

                // Try to load from cache if API failed
                val cachedSummary = cache.getFromCacheByDate("sales-summary", currentPeriod, dateKey)
                if (cachedSummary != null) {
                    // Show cached data even if stale
                    updateSalesSummary(cachedSummary.first)
                    updateSalesChart(cachedSummary.first)

                    val cachedItems = cache.getFromCacheByDate("sales-by-item", currentPeriod, dateKey)
                    val cachedCategories = cache.getFromCacheByDate("sales-by-category", currentPeriod, dateKey)
                    val cachedEmployees = cache.getFromCacheByDate("sales-by-employee", currentPeriod, dateKey)

                    if (cachedItems != null) updateItemsList(cachedItems.first)
                    if (cachedCategories != null) updateCategoriesList(cachedCategories.first)
                    if (cachedEmployees != null) updateEmployeesList(cachedEmployees.first)

                    Toast.makeText(this@DashboardActivity, "Using cached data. Refresh failed: ${e.message}", Toast.LENGTH_SHORT).show()
                } else {
                    showEmptyState()
                    Toast.makeText(this@DashboardActivity, "Failed to load data: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                isLoadingData = false
            }
        }
    }

    /**
     * Fetch raw API data (returns unprocessed API response)
     */
    private suspend fun fetchRawApiData(action: String, startDate: String, endDate: String): JSONObject {
        return withContext(Dispatchers.IO) {
            val token = prefs.getString("jwt_token", "") ?: ""
            val urlString = "https://pos-candy-kush.vercel.app/api/mobile?action=$action&period=custom&start_date=$startDate&end_date=$endDate"
            val url = URL(urlString)

            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 30000
                readTimeout = 30000
            }

            try {
                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val responseBody = connection.inputStream.bufferedReader().readText()
                    JSONObject(responseBody)
                } else {
                    throw Exception("API call failed with code: $responseCode")
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    /**
     * Process single day API response - extracts data from daily_data array
     * Much faster than full month processing
     */
    private fun processSingleDayResponse(action: String, rawData: JSONObject): JSONObject {
        if (!rawData.optBoolean("success", false)) {
            return rawData
        }

        val dataObj = rawData.optJSONObject("data") ?: return rawData
        val dailyDataArray = dataObj.optJSONArray("daily_data")

        // If no daily_data, return as-is (might already be processed format)
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
                        put("chart_data", org.json.JSONArray()) // Empty chart for single day
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
     * Fetch data in background without blocking UI (for updating current period data)
     */
    private fun fetchAndUpdateInBackground(dateKey: String, startDate: String, endDate: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Fetch raw data from API
                val rawSalesSummary = fetchRawApiData("sales-summary", startDate, endDate)
                val rawItemsData = fetchRawApiData("sales-by-item", startDate, endDate)
                val rawCategoriesData = fetchRawApiData("sales-by-category", startDate, endDate)
                val rawEmployeesData = fetchRawApiData("sales-by-employee", startDate, endDate)

                // Process raw data on device
                val processedData = withContext(Dispatchers.Default) {
                    dataProcessor.processRawMonthData(
                        dateKey, startDate, endDate,
                        rawSalesSummary, rawItemsData, rawCategoriesData, rawEmployeesData
                    )
                }

                // Save processed data to cache
                cache.saveToCacheWithDate("sales-summary", currentPeriod, dateKey, startDate, endDate, processedData.aggregatedSummary)
                cache.saveToCacheWithDate("sales-by-item", currentPeriod, dateKey, startDate, endDate, processedData.aggregatedItems)
                cache.saveToCacheWithDate("sales-by-category", currentPeriod, dateKey, startDate, endDate, processedData.aggregatedCategories)
                cache.saveToCacheWithDate("sales-by-employee", currentPeriod, dateKey, startDate, endDate, processedData.aggregatedEmployees)

                // Update UI on main thread
                withContext(Dispatchers.Main) {
                    // Only update if still viewing the same date
                    if (getCurrentDateKey() == dateKey) {
                        updateSalesSummary(processedData.aggregatedSummary)
                        updateSalesChart(processedData.aggregatedSummary)
                        updateItemsList(processedData.aggregatedItems)
                        updateCategoriesList(processedData.aggregatedCategories)
                        updateEmployeesList(processedData.aggregatedEmployees)
                    }
                }
            } catch (e: Exception) {
                // Silently fail for background updates
                e.printStackTrace()
            }
        }
    }

    /**
     * Show empty state when no data is available
     */
    private fun showEmptyState() {
        tvGrossSales.text = "$0.00"
        tvNetSales.text = "$0.00"
        llItemsList.removeAllViews()
        llCategoriesList.removeAllViews()
        llEmployeesList.removeAllViews()

        // Add empty state message
        addEmptyStateMessage(llItemsList, "No item data available")
        addEmptyStateMessage(llCategoriesList, "No category data available")
        addEmptyStateMessage(llEmployeesList, "No employee data available")

        salesChart.clear()
        salesChart.invalidate()
    }

    private fun addEmptyStateMessage(container: LinearLayout, message: String) {
        val textView = TextView(this).apply {
            text = message
            setTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.text_secondary))
            textSize = 14f
            setPadding(0, 24, 0, 24)
        }
        container.addView(textView)
    }

    private fun updateSalesSummary(data: JSONObject) {
        if (data.optBoolean("success", false)) {
            val dataObj = data.optJSONObject("data")
            val metrics = dataObj?.optJSONObject("metrics")
            if (metrics != null) {
                tvGrossSales.text = NumberFormatter.formatCurrency(metrics.optDouble("gross_sales", 0.0))
                tvNetSales.text = NumberFormatter.formatCurrency(metrics.optDouble("net_sales", 0.0))
            } else {
                tvGrossSales.text = "$0.00"
                tvNetSales.text = "$0.00"
            }
        } else {
            tvGrossSales.text = "$0.00"
            tvNetSales.text = "$0.00"
        }
    }

    private fun updateSalesChart(data: JSONObject) {
        val entries = mutableListOf<Entry>()

        if (data.optBoolean("success", false)) {
            val dataObj = data.optJSONObject("data")
            val chartData = dataObj?.optJSONArray("chart_data")

            if (chartData != null && chartData.length() > 0) {
                for (i in 0 until chartData.length()) {
                    val point = chartData.optJSONObject(i)
                    if (point != null) {
                        val x = point.optDouble("x", i.toDouble()).toFloat()
                        val y = point.optDouble("y", 0.0).toFloat()
                        entries.add(Entry(x, y))
                    }
                }
            } else {
                // No chart data available - show empty chart
                salesChart.clear()
                salesChart.invalidate()
                return
            }
        }

        if (entries.isEmpty()) {
            salesChart.clear()
            salesChart.invalidate()
            return
        }

        val dataSet = LineDataSet(entries, "Sales").apply {
            color = ContextCompat.getColor(this@DashboardActivity, R.color.primary_green)
            setCircleColor(ContextCompat.getColor(this@DashboardActivity, R.color.primary_green))
            lineWidth = 2f
            circleRadius = 4f
            setDrawFilled(true)
            fillColor = ContextCompat.getColor(this@DashboardActivity, R.color.primary_green)
            fillAlpha = 50
        }

        salesChart.data = LineData(dataSet)
        salesChart.invalidate()
    }

    private fun updateItemsList(data: JSONObject) {
        llItemsList.removeAllViews()

        if (data.optBoolean("success", false)) {
            val dataObj = data.optJSONObject("data")
            val items = dataObj?.optJSONArray("items")

            if (items != null && items.length() > 0) {
                val maxItems = minOf(5, items.length())

                for (i in 0 until maxItems) {
                    val item = items.optJSONObject(i)
                    if (item != null) {
                        val itemView = createItemRow(
                            item.optString("item_name", "Unknown"),
                            item.optInt("quantity_sold", 0),
                            item.optDouble("gross_sales", 0.0)
                        )
                        llItemsList.addView(itemView)
                    }
                }
            } else {
                addEmptyStateMessage(llItemsList, "No items sold")
            }
        } else {
            addEmptyStateMessage(llItemsList, "No item data available")
        }
    }

    private fun updateCategoriesList(data: JSONObject) {
        llCategoriesList.removeAllViews()

        if (data.optBoolean("success", false)) {
            val dataObj = data.optJSONObject("data")
            val categories = dataObj?.optJSONArray("categories")

            if (categories != null && categories.length() > 0) {
                val maxCategories = minOf(5, categories.length())

                for (i in 0 until maxCategories) {
                    val category = categories.optJSONObject(i)
                    if (category != null) {
                        val categoryView = createItemRow(
                            category.optString("category_name", "Unknown"),
                            category.optInt("quantity_sold", 0),
                            category.optDouble("gross_sales", 0.0)
                        )
                        llCategoriesList.addView(categoryView)
                    }
                }
            } else {
                addEmptyStateMessage(llCategoriesList, "No category sales")
            }
        } else {
            addEmptyStateMessage(llCategoriesList, "No category data available")
        }
    }

    private fun updateEmployeesList(data: JSONObject) {
        llEmployeesList.removeAllViews()

        if (data.optBoolean("success", false)) {
            val dataObj = data.optJSONObject("data")
            val employees = dataObj?.optJSONArray("employees")

            if (employees != null && employees.length() > 0) {
                val maxEmployees = minOf(5, employees.length())

                for (i in 0 until maxEmployees) {
                    val employee = employees.optJSONObject(i)
                    if (employee != null) {
                        val employeeView = createEmployeeRow(
                            employee.optString("employee_name", "Unknown"),
                            employee.optDouble("gross_sales", 0.0)
                        )
                        llEmployeesList.addView(employeeView)
                    }
                }
            } else {
                addEmptyStateMessage(llEmployeesList, "No employee sales")
            }
        } else {
            addEmptyStateMessage(llEmployeesList, "No employee data available")
        }
    }

    private fun createItemRow(name: String, quantity: Int, sales: Double): View {
        val itemView = layoutInflater.inflate(R.layout.item_dashboard_row, null)

        itemView.findViewById<TextView>(R.id.tv_item_name).text = name
        itemView.findViewById<TextView>(R.id.tv_item_quantity).text = NumberFormatter.formatQuantity(quantity)
        itemView.findViewById<TextView>(R.id.tv_item_sales).text = NumberFormatter.formatCurrency(sales)

        return itemView
    }

    private fun createEmployeeRow(name: String, sales: Double): View {
        val employeeView = layoutInflater.inflate(R.layout.item_employee_row, null)

        employeeView.findViewById<TextView>(R.id.tv_employee_name).text = name
        employeeView.findViewById<TextView>(R.id.tv_employee_sales).text = NumberFormatter.formatCurrency(sales)

        // Set avatar initials
        val avatar = employeeView.findViewById<TextView>(R.id.tv_employee_avatar)
        val initials = name.split(" ").map { it.firstOrNull()?.uppercase() ?: "" }.joinToString("")
        avatar.text = initials.take(2)

        return employeeView
    }

    private fun openSalesSummaryDetail() {
        val intent = Intent(this, SalesSummaryActivity::class.java)
        intent.putExtra("period", currentPeriod)
        intent.putExtra("date_key", getCurrentDateKey())
        intent.putExtra("start_date", getCurrentDateRange().first)
        intent.putExtra("end_date", getCurrentDateRange().second)
        startActivity(intent)
    }

    private fun openItemsDetail() {
        val intent = Intent(this, SalesByItemsActivity::class.java)
        intent.putExtra("period", currentPeriod)
        intent.putExtra("date_key", getCurrentDateKey())
        intent.putExtra("start_date", getCurrentDateRange().first)
        intent.putExtra("end_date", getCurrentDateRange().second)
        startActivity(intent)
    }

    private fun openCategoriesDetail() {
        val intent = Intent(this, CategoriesDetailActivity::class.java)
        intent.putExtra("period", currentPeriod)
        intent.putExtra("date_key", getCurrentDateKey())
        intent.putExtra("start_date", getCurrentDateRange().first)
        intent.putExtra("end_date", getCurrentDateRange().second)
        startActivity(intent)
    }

    private fun openEmployeesDetail() {
        val intent = Intent(this, EmployeesDetailActivity::class.java)
        intent.putExtra("period", currentPeriod)
        intent.putExtra("date_key", getCurrentDateKey())
        intent.putExtra("start_date", getCurrentDateRange().first)
        intent.putExtra("end_date", getCurrentDateRange().second)
        startActivity(intent)
    }

    private fun showProgress(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    // BackgroundSyncManager.SyncCallback implementations
    override fun onSyncProgress(monthsSynced: Int, totalMonths: Int) {
        llSyncStatus.visibility = View.VISIBLE
        tvSyncStatus.text = "Syncing data... $monthsSynced/$totalMonths months"
    }

    override fun onSyncComplete() {
        llSyncStatus.visibility = View.GONE
        // Refresh current view with newly synced data if viewing historical data
        if (!isCurrentPeriod()) {
            loadDashboardData(forceRefresh = false)
        }
    }

    override fun onSyncError(error: String) {
        llSyncStatus.visibility = View.GONE
    }

    override fun onSyncPaused() {
        llSyncStatus.visibility = View.VISIBLE
        tvSyncStatus.text = "Sync paused..."
    }

    override fun onSyncResumed() {
        llSyncStatus.visibility = View.VISIBLE
        tvSyncStatus.text = "Resuming sync..."
    }

    override fun onResume() {
        super.onResume()
        bottomNavigation.selectedItemId = R.id.nav_sales
    }

    override fun onDestroy() {
        super.onDestroy()
        syncManager.cleanup()
    }
}
