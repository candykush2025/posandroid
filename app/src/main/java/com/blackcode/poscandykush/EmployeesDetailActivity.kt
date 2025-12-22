package com.blackcode.poscandykush

import android.app.DatePickerDialog
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
import android.widget.*
import androidx.appcompat.app.AlertDialog
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
import java.text.SimpleDateFormat
import java.util.*

class EmployeesDetailActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var tvDate: TextView
    private lateinit var btnPrevDate: ImageButton
    private lateinit var btnNextDate: ImageButton
    private lateinit var btnBack: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var llEmployeesList: LinearLayout
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
        setContentView(R.layout.activity_employees_detail)

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
        loadEmployeesData()
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
        llEmployeesList = findViewById(R.id.ll_employees_list)
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
            // Show message that refresh should be done from dashboard
            Toast.makeText(this, "Please refresh data from the Dashboard to update employee information.", Toast.LENGTH_LONG).show()
            swipeRefresh.isRefreshing = false
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
                    loadEmployeesData()
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
                loadEmployeesData()
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
        loadEmployeesData()
    }

    private fun loadEmployeesData() {
        showProgress(true)
        llEmployeesList.removeAllViews()

        val dateKey = getCurrentDateKey()
        val cachedEmployees = cache.getFromCacheByDate("sales-by-employee", currentPeriod, dateKey)

        if (cachedEmployees != null) {
            updateUI(cachedEmployees.first)
            showProgress(false)
            swipeRefresh.isRefreshing = false
        } else {
            showProgress(false)
            swipeRefresh.isRefreshing = false
            showEmptyState("No cached data for this period. Please refresh the dashboard first.")
        }
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

    private fun showEmptyState(message: String) {
        llEmployeesList.removeAllViews()
        val textView = TextView(this).apply {
            text = message
            setTextColor(ContextCompat.getColor(this@EmployeesDetailActivity, R.color.text_secondary))
            textSize = 14f
            setPadding(32, 48, 32, 48)
            gravity = android.view.Gravity.CENTER
        }
        llEmployeesList.addView(textView)
    }

    private fun updateUI(data: JSONObject) {
        llEmployeesList.removeAllViews()

        if (data.optBoolean("success", false)) {
            val dataObj = data.optJSONObject("data")
            val employees = dataObj?.optJSONArray("employees")

            if (employees != null && employees.length() > 0) {
                for (i in 0 until employees.length()) {
                    val employee = employees.optJSONObject(i)
                    if (employee != null) {
                        val employeeView = createDetailEmployeeRow(
                            employee.optString("employee_name", "Unknown"),
                            employee.optDouble("gross_sales", 0.0),
                            employee.optInt("transaction_count", 0),
                            employee.optInt("items_sold", 0),
                            employee.optDouble("average_transaction", 0.0)
                        )
                        llEmployeesList.addView(employeeView)
                    }
                }
            } else {
                showEmptyState("No employee sales in this period")
            }
        } else {
            showEmptyState("No employee data available")
        }
    }

    private fun createDetailEmployeeRow(
        name: String,
        sales: Double,
        transactions: Int,
        itemsSold: Int,
        averageTransaction: Double
    ): View {
        val employeeView = layoutInflater.inflate(R.layout.item_employee_detail_row, null)

        employeeView.findViewById<TextView>(R.id.tv_employee_name).text = name
        employeeView.findViewById<TextView>(R.id.tv_employee_sales).text = NumberFormatter.formatCurrency(sales)
        employeeView.findViewById<TextView>(R.id.tv_employee_transactions).text = "${NumberFormatter.formatInteger(transactions)} transactions"
        employeeView.findViewById<TextView>(R.id.tv_employee_items).text = "${NumberFormatter.formatInteger(itemsSold)} items"
        employeeView.findViewById<TextView>(R.id.tv_employee_average).text = "Avg: ${NumberFormatter.formatCurrency(averageTransaction)}"

        // Set avatar initials
        val avatar = employeeView.findViewById<TextView>(R.id.tv_employee_avatar)
        val initials = name.split(" ").map { it.firstOrNull()?.uppercase() ?: "" }.joinToString("")
        avatar.text = initials.take(2)

        return employeeView
    }

    private fun showProgress(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }
}
