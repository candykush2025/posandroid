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
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class SalesByItemsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var tvDate: TextView
    private lateinit var btnPrevDate: ImageButton
    private lateinit var btnNextDate: ImageButton
    private lateinit var btnBack: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var llItemsList: LinearLayout
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var statusBarBackground: View

    private lateinit var cache: SalesDataCache

    private var currentPeriod = "this_month"
    private var currentDate = Calendar.getInstance()

    // Date formatters
    private val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
    private val monthYearFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    private val dayKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val monthKeyFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
    private val yearKeyFormat = SimpleDateFormat("yyyy", Locale.getDefault())
    private val weekKeyFormat = SimpleDateFormat("yyyy-'w'ww", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sales_by_items)

        setupStatusBar()

        prefs = getSharedPreferences("admin_prefs", MODE_PRIVATE)
        cache = SalesDataCache(this)

        // Get period from intent if passed
        intent.getStringExtra("period")?.let { currentPeriod = it }
        intent.getStringExtra("date_key")?.let { dateKey ->
            // Parse date key back to currentDate
            when (currentPeriod) {
                "today", "custom" -> {
                    try {
                        val date = dayKeyFormat.parse(dateKey)
                        currentDate.time = date ?: Calendar.getInstance().time
                    } catch (e: Exception) {
                        // Keep default
                    }
                }
                "this_week" -> {
                    try {
                        val date = weekKeyFormat.parse(dateKey)
                        currentDate.time = date ?: Calendar.getInstance().time
                    } catch (e: Exception) {
                        // Keep default
                    }
                }
                "this_month" -> {
                    try {
                        val date = monthKeyFormat.parse(dateKey)
                        currentDate.time = date ?: Calendar.getInstance().time
                    } catch (e: Exception) {
                        // Keep default
                    }
                }
                "this_year" -> {
                    try {
                        val date = yearKeyFormat.parse(dateKey)
                        currentDate.time = date ?: Calendar.getInstance().time
                    } catch (e: Exception) {
                        // Keep default
                    }
                }
            }
        }

        initializeViews()
        setupDateNavigation()
        setupSwipeRefresh()

        // Load data
        loadItemsData()
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
        tvDate = findViewById(R.id.tv_date)
        btnPrevDate = findViewById(R.id.btn_prev_date)
        btnNextDate = findViewById(R.id.btn_next_date)
        btnBack = findViewById(R.id.btn_back)
        progressBar = findViewById(R.id.progress_bar)
        llItemsList = findViewById(R.id.ll_items_list)
        swipeRefresh = findViewById(R.id.swipe_refresh)
        statusBarBackground = findViewById(R.id.status_bar_background)

        // Set status bar height
        val statusBarHeight = getStatusBarHeight()
        statusBarBackground.layoutParams.height = statusBarHeight

        // Setup back button
        btnBack.setOnClickListener {
            finish()
        }
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
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

    private fun setupSwipeRefresh() {
        swipeRefresh.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.primary_green)
        )
        swipeRefresh.setOnRefreshListener {
            loadItemsData()
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
                    loadItemsData()
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
                loadItemsData()
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
        loadItemsData()
    }

    private fun loadItemsData() {
        showProgress(true)
        llItemsList.removeAllViews()

        val dateKey = getCurrentDateKey()
        val cachedItems = cache.getFromCacheByDate("sales-by-item", currentPeriod, dateKey)

        if (cachedItems != null) {
            updateUIWithItems(cachedItems.first)
            showProgress(false)
            swipeRefresh.isRefreshing = false
        } else {
            showProgress(false)
            swipeRefresh.isRefreshing = false
            showEmptyState("No cached data for this period. Please refresh the dashboard first.")
        }
    }

    private fun updateUIWithItems(data: JSONObject) {
        if (data.optBoolean("success", false)) {
            val dataObj = data.optJSONObject("data")
            val items = dataObj?.optJSONArray("items")
            if (items != null && items.length() > 0) {
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i)
                    if (item != null) {
                        displayItem(item)
                    }
                }
            } else {
                showEmptyState("No items found for this period")
            }
        } else {
            showEmptyState("Failed to load items")
        }
    }

    private fun displayItem(item: JSONObject) {
        val itemName = item.optString("item_name", "Unknown")
        val quantity = item.optInt("quantity_sold", 0)
        val totalSales = item.optDouble("gross_sales", 0.0)

        val itemView = layoutInflater.inflate(R.layout.item_sales_row, llItemsList, false)

        itemView.findViewById<TextView>(R.id.tv_item_name).text = itemName
        itemView.findViewById<TextView>(R.id.tv_quantity).text = "Qty: ${NumberFormatter.formatInteger(quantity)}"
        itemView.findViewById<TextView>(R.id.tv_total_sales).text = "Total: ${NumberFormatter.formatCurrency(totalSales)}"

        itemView.setOnClickListener {
            // Open item detail
            val intent = Intent(this, ItemsDetailActivity::class.java).apply {
                putExtra("selected_item", item.toString())
            }
            startActivity(intent)
        }

        llItemsList.addView(itemView)
    }

    private fun showEmptyState(message: String) {
        val textView = TextView(this).apply {
            text = message
            setTextColor(ContextCompat.getColor(this@SalesByItemsActivity, R.color.text_secondary))
            textSize = 14f
            setPadding(32, 48, 32, 48)
            gravity = android.view.Gravity.CENTER
        }
        llItemsList.addView(textView)
    }

    private fun showProgress(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun getCurrentDateKey(): String {
        return when (currentPeriod) {
            "today", "custom" -> dayKeyFormat.format(currentDate.time)
            "this_week" -> weekKeyFormat.format(currentDate.time)
            "this_month" -> monthKeyFormat.format(currentDate.time)
            "this_year" -> yearKeyFormat.format(currentDate.time)
            else -> dayKeyFormat.format(currentDate.time)
        }
    }
}
