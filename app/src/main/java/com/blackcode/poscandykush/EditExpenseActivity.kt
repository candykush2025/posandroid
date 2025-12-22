package com.blackcode.poscandykush

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class EditExpenseActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var etDescription: TextInputEditText
    private lateinit var etAmount: TextInputEditText
    private lateinit var etDate: TextInputEditText
    private lateinit var etTime: TextInputEditText
    private lateinit var btnSaveExpense: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var statusBarBackground: View
    private lateinit var tvTitle: TextView

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    private var expenseId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_expense) // Reuse add expense layout

        setupStatusBar()

        prefs = getSharedPreferences("admin_prefs", MODE_PRIVATE)

        if (!isAuthenticated()) {
            navigateToLogin()
            return
        }

        expenseId = intent.getStringExtra("expense_id")
        if (expenseId == null) {
            Toast.makeText(this, "Invalid expense ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializeViews()
        setupListeners()
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
        statusBarBackground = findViewById(R.id.status_bar_background)
        etDescription = findViewById(R.id.et_description)
        etAmount = findViewById(R.id.et_amount)
        etDate = findViewById(R.id.et_date)
        etTime = findViewById(R.id.et_time)
        btnSaveExpense = findViewById(R.id.btn_save_expense)
        progressBar = findViewById(R.id.progress_bar)
        tvTitle = findViewById(R.id.tv_title)

        // Change button text to "Update Expense"
        btnSaveExpense.text = "Update Expense"
        tvTitle.text = "Edit Expense"

        val statusBarHeight = getStatusBarHeight()
        statusBarBackground.layoutParams.height = statusBarHeight

        findViewById<View>(R.id.btn_back).setOnClickListener {
            finish()
        }
    }

    private fun setupListeners() {
        etDate.setOnClickListener { showDatePicker() }
        etTime.setOnClickListener { showTimePicker() }
        btnSaveExpense.setOnClickListener { updateExpense() }
    }

    private fun loadExpenseData() {
        showProgress(true)

        lifecycleScope.launch(Dispatchers.Main) {
            try {
                val expense = fetchExpenseData()
                if (expense != null) {
                    populateFields(expense)
                    showProgress(false)
                } else {
                    showProgress(false)
                    Toast.makeText(this@EditExpenseActivity, "Failed to load expense data", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                showProgress(false)
                Toast.makeText(this@EditExpenseActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private suspend fun fetchExpenseData(): Expense? {
        return withContext(Dispatchers.IO) {
            val token = prefs.getString("jwt_token", "") ?: ""
            val apiService = ExpenseApiService()
            val response = apiService.getExpense(token, expenseId!!)
            if (response?.success == true) {
                response.data
            } else {
                null
            }
        }
    }

    private fun populateFields(expense: Expense) {
        etDescription.setText(expense.description ?: "")
        etAmount.setText(expense.amount.toString())
        etDate.setText(expense.date ?: "")
        etTime.setText(expense.time ?: "")
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            val selectedDate = Calendar.getInstance()
            selectedDate.set(selectedYear, selectedMonth, selectedDay)
            val dateString = dateFormat.format(selectedDate.time)
            etDate.setText(dateString)
        }, year, month, day).show()
    }

    private fun showTimePicker() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        TimePickerDialog(this, { _, selectedHour, selectedMinute ->
            val timeCalendar = Calendar.getInstance()
            timeCalendar.set(Calendar.HOUR_OF_DAY, selectedHour)
            timeCalendar.set(Calendar.MINUTE, selectedMinute)
            val timeString = timeFormat.format(timeCalendar.time)
            etTime.setText(timeString)
        }, hour, minute, true).show()
    }

    private fun updateExpense() {
        val description = etDescription.text.toString().trim()
        val amountStr = etAmount.text.toString().trim()
        val date = etDate.text.toString().trim()
        val time = etTime.text.toString().trim()

        if (description.isEmpty()) {
            Toast.makeText(this, "Please enter description", Toast.LENGTH_SHORT).show()
            return
        }

        if (amountStr.isEmpty()) {
            Toast.makeText(this, "Please enter amount", Toast.LENGTH_SHORT).show()
            return
        }

        val amount = amountStr.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            Toast.makeText(this, "Please enter valid amount", Toast.LENGTH_SHORT).show()
            return
        }

        if (date.isEmpty()) {
            Toast.makeText(this, "Please select date", Toast.LENGTH_SHORT).show()
            return
        }

        if (time.isEmpty()) {
            Toast.makeText(this, "Please select time", Toast.LENGTH_SHORT).show()
            return
        }

        showProgress(true)
        btnSaveExpense.isEnabled = false

        lifecycleScope.launch(Dispatchers.Main) {
            try {
                val response = performUpdateExpense(description, amount, date, time)
                if (response?.success == true) {
                    Toast.makeText(this@EditExpenseActivity, "Expense updated successfully", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                } else {
                    val errorMessage = response?.error ?: "Unknown error occurred"
                    Toast.makeText(this@EditExpenseActivity, "Failed to update expense: $errorMessage", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@EditExpenseActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                showProgress(false)
                btnSaveExpense.isEnabled = true
            }
        }
    }

    private suspend fun performUpdateExpense(
        description: String,
        amount: Double,
        date: String,
        time: String
    ): ExpenseResponse? {
        return withContext(Dispatchers.IO) {
            try {
                val token = prefs.getString("jwt_token", "") ?: ""

                val request = EditExpenseRequest(
                    id = expenseId!!,
                    description = description,
                    amount = amount,
                    date = date,
                    time = time
                )

                val apiService = ExpenseApiService()
                apiService.editExpense(token, request)

            } catch (e: Exception) {
                ExpenseResponse(success = false, error = e.message ?: "Unknown error", data = null)
            }
        }
    }

    private fun showProgress(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }
}
