package com.blackcode.poscandykush

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ExpenseActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var progressBar: ProgressBar
    private lateinit var rvExpenses: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var tvTotal: TextView
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var fabAddExpense: FloatingActionButton
    private lateinit var statusBarBackground: View
    private lateinit var cache: SalesDataCache
    private val gson = Gson()

    private lateinit var expenseAdapter: ExpenseAdapter
    private var isLoadingData = false

    companion object {
        private const val ADD_EXPENSE_REQUEST_CODE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_expense)

        initializeViews()
        setupStatusBar()

        prefs = getSharedPreferences("admin_prefs", MODE_PRIVATE)
        cache = SalesDataCache(this)

        if (!isAuthenticated()) {
            navigateToLogin()
            return
        }

        setupBottomNavigation()
        setupSwipeRefresh()
        setupRecyclerView()
        setupFAB()

        clearUI()
        loadExpenseData()
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
        rvExpenses = findViewById(R.id.rv_expenses)
        tvEmpty = findViewById(R.id.tv_empty)
        tvTotal = findViewById(R.id.tv_total)
        bottomNavigation = findViewById(R.id.bottom_navigation)
        swipeRefresh = findViewById(R.id.swipe_refresh)
        fabAddExpense = findViewById(R.id.fab_add_expense)
        statusBarBackground = findViewById(R.id.status_bar_background)

        val statusBarHeight = getStatusBarHeight()
        statusBarBackground.layoutParams.height = statusBarHeight

        findViewById<View>(R.id.btn_back).setOnClickListener {
            finish()
        }
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
            loadExpenseData()
        }
    }

    private fun setupRecyclerView() {
        rvExpenses.layoutManager = LinearLayoutManager(this)
        expenseAdapter = ExpenseAdapter(mutableListOf())
        rvExpenses.adapter = expenseAdapter

        expenseAdapter.setOnExpenseClickListener(object : ExpenseAdapter.OnExpenseClickListener {
            override fun onExpenseClick(expense: Expense) {
                val intent = Intent(this@ExpenseActivity, ExpenseDetailActivity::class.java)
                intent.putExtra("expense_id", expense.id)
                startActivity(intent)
            }

            override fun onExpenseDelete(expense: Expense) {
                showDeleteConfirmationDialog(expense)
            }
        })
    }

    private fun setupFAB() {
        fabAddExpense.setOnClickListener {
            val intent = Intent(this, AddExpenseActivity::class.java)
            startActivityForResult(intent, ADD_EXPENSE_REQUEST_CODE)
        }
    }

    private fun clearUI() {
        expenseAdapter.updateExpenses(emptyList())
        tvEmpty.visibility = View.GONE
        tvTotal.visibility = View.GONE
    }

    private fun loadExpenseData() {
        if (isLoadingData) return
        isLoadingData = true

        showProgress(true)

        lifecycleScope.launch(Dispatchers.Main) {
            var cachedData: Pair<JSONObject, Long>? = null
            try {
                cachedData = cache.getItemsFromCache("expenses")
                if (cachedData != null) {
                    val cachedResponse = gson.fromJson(cachedData.first.toString(), ExpenseListResponse::class.java)
                    updateUI(cachedResponse)
                }

                val data = fetchExpenseData()

                if (data != null) {
                    val jsonData = JSONObject(gson.toJson(data))
                    cache.saveItemsToCache("expenses", jsonData)
                    updateUI(data)
                } else {
                    if (cachedData == null) {
                        showEmptyState("Failed to load expenses")
                    } else {
                        Toast.makeText(this@ExpenseActivity, "Failed to refresh, using cached data", Toast.LENGTH_SHORT).show()
                    }
                }

                showProgress(false)
                swipeRefresh.isRefreshing = false

            } catch (e: Exception) {
                showProgress(false)
                swipeRefresh.isRefreshing = false

                if (cachedData == null) {
                    showEmptyState("Failed to load expenses: ${e.message}")
                    Toast.makeText(this@ExpenseActivity, "Failed to load expenses: ${e.message}", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@ExpenseActivity, "Failed to refresh, using cached data", Toast.LENGTH_SHORT).show()
                }
            } finally {
                isLoadingData = false
            }
        }
    }

    private suspend fun fetchExpenseData(): ExpenseListResponse? {
        return withContext(Dispatchers.IO) {
            val token = prefs.getString("jwt_token", "") ?: ""
            val apiService = ExpenseApiService()
            apiService.getExpenses(token)
        }
    }

    private fun updateUI(response: ExpenseListResponse) {
        if (response.success && response.data != null) {
            val expenses = response.data.expenses

            if (expenses.isNotEmpty()) {
                expenseAdapter.updateExpenses(expenses)
                rvExpenses.visibility = View.VISIBLE
                tvEmpty.visibility = View.GONE

                // Show total
                val total = response.data.total ?: expenses.sumOf { it.amount }
                tvTotal.text = "Total: ${NumberFormatter.formatCurrency(total)}"
                tvTotal.visibility = View.VISIBLE
            } else {
                showEmptyState("No expenses found")
            }
        } else {
            showEmptyState(response.error ?: "Failed to load expenses")
        }
    }

    private fun showEmptyState(message: String) {
        tvEmpty.text = message
        tvEmpty.visibility = View.VISIBLE
        rvExpenses.visibility = View.GONE
        tvTotal.visibility = View.GONE
    }

    private fun showProgress(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showDeleteConfirmationDialog(expense: Expense) {
        AlertDialog.Builder(this)
            .setTitle("Delete Expense")
            .setMessage("Are you sure you want to delete this expense? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteExpense(expense)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteExpense(expense: Expense) {
        showProgress(true)

        lifecycleScope.launch(Dispatchers.Main) {
            try {
                val success = performDeleteExpense(expense.id)
                showProgress(false)

                if (success) {
                    Toast.makeText(this@ExpenseActivity, "Expense deleted successfully", Toast.LENGTH_SHORT).show()
                    loadExpenseData()
                } else {
                    Toast.makeText(this@ExpenseActivity, "Failed to delete expense", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                showProgress(false)
                Toast.makeText(this@ExpenseActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun performDeleteExpense(expenseId: String): Boolean {
        return withContext(Dispatchers.IO) {
            val token = prefs.getString("jwt_token", "") ?: ""
            val apiService = ExpenseApiService()
            val response = apiService.deleteExpense(token, expenseId)
            response?.success == true
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ADD_EXPENSE_REQUEST_CODE && resultCode == RESULT_OK) {
            loadExpenseData()
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh data when returning from detail or edit screens
        if (::expenseAdapter.isInitialized && expenseAdapter.itemCount > 0) {
            loadExpenseData()
        }
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }
}
