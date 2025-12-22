package com.blackcode.poscandykush

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ExpenseDetailActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var toolbar: Toolbar
    private lateinit var progressBar: ProgressBar
    private lateinit var scrollView: ScrollView
    private lateinit var tvEmpty: TextView
    private lateinit var tvDescription: TextView
    private lateinit var tvAmount: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvTime: TextView
    private lateinit var statusBarBackground: View
    private lateinit var btnEditExpense: Button
    private lateinit var btnDeleteExpense: Button

    private var currentExpense: Expense? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            setContentView(R.layout.activity_expense_detail)

            initializeViews()
            setupStatusBar()

            prefs = getSharedPreferences("admin_prefs", MODE_PRIVATE)

            setupToolbar()
            setupButtons()

            val expenseId = intent.getStringExtra("expense_id")
            android.util.Log.d("ExpenseDetailActivity", "📋 Received expense_id: $expenseId")

            if (expenseId != null && expenseId.isNotEmpty()) {
                loadExpenseDetail(expenseId)
            } else {
                android.util.Log.e("ExpenseDetailActivity", "❌ Invalid expense ID: $expenseId")
                showEmptyState("Invalid expense ID")
                Toast.makeText(this, "Invalid expense ID", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("ExpenseDetailActivity", "❌ FATAL ERROR in onCreate", e)
            Toast.makeText(this, "Error loading expense: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
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

    private fun initializeViews() {
        try {
            toolbar = findViewById(R.id.toolbar)
            progressBar = findViewById(R.id.progress_bar)
            scrollView = findViewById(R.id.scroll_view)
            tvEmpty = findViewById(R.id.tv_empty)
            tvDescription = findViewById(R.id.tv_customer_name) // Reuse customer name for description
            tvAmount = findViewById(R.id.tv_total_amount)
            tvDate = findViewById(R.id.tv_invoice_date)
            tvTime = findViewById(R.id.tv_due_date) // Reuse due date for time
            statusBarBackground = findViewById(R.id.status_bar_background)
            btnEditExpense = findViewById(R.id.btn_edit_invoice)
            btnDeleteExpense = findViewById(R.id.btn_delete_invoice)

            val statusBarHeight = getStatusBarHeight()
            statusBarBackground.layoutParams.height = statusBarHeight

            android.util.Log.d("ExpenseDetailActivity", "✅ Views initialized successfully")
        } catch (e: Exception) {
            android.util.Log.e("ExpenseDetailActivity", "❌ ERROR initializing views", e)
            Toast.makeText(this, "Error initializing screen: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun setupToolbar() {
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupButtons() {
        try {
            btnEditExpense.setOnClickListener {
                currentExpense?.let { expense ->
                    try {
                        val intent = Intent(this@ExpenseDetailActivity, EditExpenseActivity::class.java)
                        intent.putExtra("expense_id", expense.id)
                        startActivity(intent)
                    } catch (e: Exception) {
                        android.util.Log.e("ExpenseDetailActivity", "❌ ERROR starting edit activity", e)
                        Toast.makeText(this@ExpenseDetailActivity, "Error opening edit screen: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } ?: run {
                    Toast.makeText(this@ExpenseDetailActivity, "Expense data not loaded", Toast.LENGTH_SHORT).show()
                }
            }

            btnDeleteExpense.setOnClickListener {
                currentExpense?.let { expense ->
                    showDeleteConfirmationDialog(expense)
                } ?: run {
                    Toast.makeText(this@ExpenseDetailActivity, "Expense data not loaded", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ExpenseDetailActivity", "❌ ERROR setting up buttons", e)
        }
    }

    private fun loadExpenseDetail(expenseId: String) {
        showProgress(true)

        android.util.Log.d("ExpenseDetailActivity", "========================================")
        android.util.Log.d("ExpenseDetailActivity", "Loading expense detail for ID: $expenseId")
        android.util.Log.d("ExpenseDetailActivity", "========================================")

        lifecycleScope.launch(Dispatchers.Main) {
            try {
                val expense = fetchExpenseDetail(expenseId)
                if (expense != null) {
                    android.util.Log.d("ExpenseDetailActivity", "✅ SUCCESS: Expense fetched, updating UI")
                    currentExpense = expense
                    updateUI(expense)
                    showProgress(false)
                } else {
                    android.util.Log.e("ExpenseDetailActivity", "❌ ERROR: Expense is null after fetch")
                    showProgress(false)
                    showEmptyState(
                        "Expense not found!\n\n" +
                        "Expense ID: $expenseId\n\n" +
                        "Possible reasons:\n" +
                        "• Expense doesn't exist in database\n" +
                        "• Wrong expense ID\n" +
                        "• API connection issue\n\n" +
                        "Check Logcat for details:\n" +
                        "adb logcat | grep ExpenseDetail"
                    )
                    Toast.makeText(
                        this@ExpenseDetailActivity,
                        "Expense not found. Check Logcat for details.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                showProgress(false)
                android.util.Log.e("ExpenseDetailActivity", "❌ EXCEPTION in loadExpenseDetail", e)
                val errorMsg =
                    "Failed to load expense!\n\n" +
                    "Expense ID: $expenseId\n" +
                    "Error: ${e.message}\n\n" +
                    "Check your:\n" +
                    "• Internet connection\n" +
                    "• API server status\n" +
                    "• JWT token validity\n\n" +
                    "Logcat command:\n" +
                    "adb logcat | grep ExpenseDetail"
                showEmptyState(errorMsg)
                Toast.makeText(this@ExpenseDetailActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun fetchExpenseDetail(expenseId: String): Expense? {
        return withContext(Dispatchers.IO) {
            try {
                val token = prefs.getString("jwt_token", "") ?: ""
                android.util.Log.d("ExpenseDetailActivity", "")
                android.util.Log.d("ExpenseDetailActivity", "╔════════════════════════════════════════╗")
                android.util.Log.d("ExpenseDetailActivity", "║   FETCHING EXPENSE DETAILS             ║")
                android.util.Log.d("ExpenseDetailActivity", "╚════════════════════════════════════════╝")
                android.util.Log.d("ExpenseDetailActivity", "Expense ID: $expenseId")
                android.util.Log.d("ExpenseDetailActivity", "Token available: ${token.isNotEmpty()}")
                android.util.Log.d("ExpenseDetailActivity", "Token length: ${token.length}")

                val apiService = ExpenseApiService()
                val response = apiService.getExpense(token, expenseId)

                android.util.Log.d("ExpenseDetailActivity", "")
                android.util.Log.d("ExpenseDetailActivity", "╔════════════════════════════════════════╗")
                android.util.Log.d("ExpenseDetailActivity", "║   API RESPONSE                         ║")
                android.util.Log.d("ExpenseDetailActivity", "╚════════════════════════════════════════╝")
                android.util.Log.d("ExpenseDetailActivity", "Response object: ${response != null}")
                android.util.Log.d("ExpenseDetailActivity", "Success: ${response?.success}")
                android.util.Log.d("ExpenseDetailActivity", "Error: ${response?.error}")
                android.util.Log.d("ExpenseDetailActivity", "Data (expense): ${response?.data}")

                if (response?.success == true) {
                    // Expense is directly in data
                    val expense = response.data

                    if (expense != null) {
                        android.util.Log.d("ExpenseDetailActivity", "")
                        android.util.Log.d("ExpenseDetailActivity", "╔════════════════════════════════════════╗")
                        android.util.Log.d("ExpenseDetailActivity", "║   EXPENSE FOUND ✅                     ║")
                        android.util.Log.d("ExpenseDetailActivity", "╚════════════════════════════════════════╝")
                        android.util.Log.d("ExpenseDetailActivity", "ID: ${expense.id}")
                        android.util.Log.d("ExpenseDetailActivity", "Description: ${expense.description}")
                        android.util.Log.d("ExpenseDetailActivity", "Amount: ${expense.amount}")
                        android.util.Log.d("ExpenseDetailActivity", "Date: ${expense.date}")
                        android.util.Log.d("ExpenseDetailActivity", "Time: ${expense.time}")
                        android.util.Log.d("ExpenseDetailActivity", "")
                        expense
                    } else {
                        android.util.Log.e("ExpenseDetailActivity", "")
                        android.util.Log.e("ExpenseDetailActivity", "╔════════════════════════════════════════╗")
                        android.util.Log.e("ExpenseDetailActivity", "║   EXPENSE DATA IS NULL ❌              ║")
                        android.util.Log.e("ExpenseDetailActivity", "╚════════════════════════════════════════╝")
                        android.util.Log.e("ExpenseDetailActivity", "Response was successful but data is null!")
                        android.util.Log.e("ExpenseDetailActivity", "")
                        null
                    }
                } else {
                    android.util.Log.e("ExpenseDetailActivity", "")
                    android.util.Log.e("ExpenseDetailActivity", "╔════════════════════════════════════════╗")
                    android.util.Log.e("ExpenseDetailActivity", "║   API RETURNED FAILURE ❌              ║")
                    android.util.Log.e("ExpenseDetailActivity", "╚════════════════════════════════════════╝")
                    android.util.Log.e("ExpenseDetailActivity", "Success: false")
                    android.util.Log.e("ExpenseDetailActivity", "Error message: ${response?.error}")
                    android.util.Log.e("ExpenseDetailActivity", "")
                    null
                }
            } catch (e: Exception) {
                android.util.Log.e("ExpenseDetailActivity", "")
                android.util.Log.e("ExpenseDetailActivity", "╔════════════════════════════════════════╗")
                android.util.Log.e("ExpenseDetailActivity", "║   EXCEPTION OCCURRED ❌                ║")
                android.util.Log.e("ExpenseDetailActivity", "╚════════════════════════════════════════╝")
                android.util.Log.e("ExpenseDetailActivity", "Exception type: ${e.javaClass.simpleName}")
                android.util.Log.e("ExpenseDetailActivity", "Message: ${e.message}")
                android.util.Log.e("ExpenseDetailActivity", "Cause: ${e.cause}")
                android.util.Log.e("ExpenseDetailActivity", "Stack trace:", e)
                android.util.Log.e("ExpenseDetailActivity", "")
                null
            }
        }
    }

    private fun updateUI(expense: Expense) {
        try {
            android.util.Log.d("ExpenseDetailActivity", "🎨 Updating UI with expense data")

            // Handle description
            tvDescription.text = if (expense.description?.isNotEmpty() == true) {
                expense.description
            } else {
                "No description"
            }

            // Handle amount
            tvAmount.text = "Amount: ${expense.getFormattedAmount()}"

            // Handle date with formatting
            tvDate.text = if (expense.date?.isNotEmpty() == true) {
                "Date: ${formatDate(expense.date)}"
            } else {
                "Date: Not set"
            }

            // Handle time
            tvTime.text = if (expense.time?.isNotEmpty() == true) {
                "Time: ${expense.time}"
            } else {
                "Time: Not set"
            }

            scrollView.visibility = View.VISIBLE
            tvEmpty.visibility = View.GONE

            android.util.Log.d("ExpenseDetailActivity", "✅ UI updated successfully")
        } catch (e: Exception) {
            android.util.Log.e("ExpenseDetailActivity", "❌ ERROR updating UI", e)
            Toast.makeText(this, "Error displaying expense: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEmptyState(message: String) {
        try {
            tvEmpty.text = message
            tvEmpty.visibility = View.VISIBLE
            scrollView.visibility = View.GONE
            progressBar.visibility = View.GONE
        } catch (e: Exception) {
            android.util.Log.e("ExpenseDetailActivity", "❌ ERROR in showEmptyState", e)
        }
    }

    private fun showProgress(show: Boolean) {
        try {
            progressBar.visibility = if (show) View.VISIBLE else View.GONE
            if (show) {
                scrollView.visibility = View.GONE
                tvEmpty.visibility = View.GONE
            }
        } catch (e: Exception) {
            android.util.Log.e("ExpenseDetailActivity", "❌ ERROR in showProgress", e)
        }
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
                    Toast.makeText(this@ExpenseDetailActivity, "Expense deleted successfully", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                } else {
                    Toast.makeText(this@ExpenseDetailActivity, "Failed to delete expense", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                showProgress(false)
                Toast.makeText(this@ExpenseDetailActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
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

    override fun onResume() {
        super.onResume()
        currentExpense?.let {
            loadExpenseDetail(it.id)
        }
    }

    private fun formatDate(dateString: String): String {
        return try {
            if (dateString.isEmpty()) return "Not set"

            val inputFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val outputFormat = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
            val date = inputFormat.parse(dateString)
            date?.let { outputFormat.format(it) } ?: dateString
        } catch (e: Exception) {
            android.util.Log.e("ExpenseDetailActivity", "Error formatting date: $dateString", e)
            dateString
        }
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }
}
