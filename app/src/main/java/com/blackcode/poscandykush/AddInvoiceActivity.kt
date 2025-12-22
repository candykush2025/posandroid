package com.blackcode.poscandykush

import android.app.DatePickerDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class AddInvoiceActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var etCustomerName: TextInputEditText
    private lateinit var etInvoiceDate: TextInputEditText
    private lateinit var etDueDate: TextInputEditText
    private lateinit var rvInvoiceItems: RecyclerView
    private lateinit var btnAddItem: Button
    private lateinit var btnSaveInvoice: Button
    private lateinit var tvTotal: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var statusBarBackground: View

    private lateinit var invoiceItemAdapter: InvoiceItemAdapter
    private val invoiceItems = mutableListOf<InvoiceItem>()
    private val products = mutableListOf<Product>()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_invoice)

        setupStatusBar()

        prefs = getSharedPreferences("admin_prefs", MODE_PRIVATE)

        // Check authentication
        if (!isAuthenticated()) {
            navigateToLogin()
            return
        }

        initializeViews()
        setupListeners()
        setupRecyclerView()
        setDefaultDate()

        // Load products for selection
        loadProducts()
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
        etCustomerName = findViewById(R.id.et_customer_name)
        etInvoiceDate = findViewById(R.id.et_invoice_date)
        etDueDate = findViewById(R.id.et_due_date)
        rvInvoiceItems = findViewById(R.id.rv_invoice_items)
        btnAddItem = findViewById(R.id.btn_add_item)
        btnSaveInvoice = findViewById(R.id.btn_save_invoice)
        tvTotal = findViewById(R.id.tv_total)
        progressBar = findViewById(R.id.progress_bar)

        // Set status bar height
        val statusBarHeight = getStatusBarHeight()
        statusBarBackground.layoutParams.height = statusBarHeight

        // Setup back button
        findViewById<View>(R.id.btn_back).setOnClickListener {
            finish()
        }
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    private fun setupListeners() {
        etInvoiceDate.setOnClickListener {
            showDatePicker(false)
        }

        etDueDate.setOnClickListener {
            showDatePicker(true)
        }

        btnAddItem.setOnClickListener {
            showProductSelectionDialog()
        }

        btnSaveInvoice.setOnClickListener {
            saveInvoice()
        }
    }

    private fun setupRecyclerView() {
        rvInvoiceItems.layoutManager = LinearLayoutManager(this)
        invoiceItemAdapter = InvoiceItemAdapter(invoiceItems, { updateTotal() }, true)
        rvInvoiceItems.adapter = invoiceItemAdapter
    }

    private fun setDefaultDate() {
        val today = dateFormat.format(Date())
        etInvoiceDate.setText(today)

        // Set default due date to 30 days from today
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, 30)
        val dueDate = dateFormat.format(calendar.time)
        etDueDate.setText(dueDate)
    }

    private fun showDatePicker(isDueDate: Boolean) {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            val selectedDate = Calendar.getInstance()
            selectedDate.set(selectedYear, selectedMonth, selectedDay)
            val dateString = dateFormat.format(selectedDate.time)

            if (isDueDate) {
                etDueDate.setText(dateString)
            } else {
                etInvoiceDate.setText(dateString)
            }
        }, year, month, day).show()
    }

    private fun loadProducts() {
        showProgress(true)

        lifecycleScope.launch(Dispatchers.Main) {
            try {
                val data = fetchProducts()
                if (data.optBoolean("success", false)) {
                    val dataObj = data.optJSONObject("data")
                    val items = dataObj?.optJSONArray("items") ?: JSONArray()

                    products.clear()
                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i)
                        if (item != null) {
                            val product = Product(
                                id = item.optString("product_id", ""),
                                name = item.optString("product_name", ""),
                                price = item.optDouble("price", 0.0)
                            )
                            products.add(product)
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@AddInvoiceActivity, "Failed to load products: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                showProgress(false)
            }
        }
    }

    private suspend fun fetchProducts(): JSONObject {
        return withContext(Dispatchers.IO) {
            val token = prefs.getString("jwt_token", "") ?: ""
            val url = URL("https://pos-candy-kush.vercel.app/api/mobile?action=stock")

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

    private fun showProductSelectionDialog() {
        val productNames = products.map { it.name }.toTypedArray()
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Select Product")
        builder.setItems(productNames) { _, which ->
            val selectedProduct = products[which]
            addInvoiceItem(selectedProduct)
        }
        builder.show()
    }

    private fun addInvoiceItem(product: Product) {
        val invoiceItem = InvoiceItem(
            productId = product.id,
            productName = product.name,
            quantity = 1.0,
            unitPrice = product.price,
            total = product.price * 1.0
        )
        invoiceItems.add(invoiceItem)
        invoiceItemAdapter.notifyItemInserted(invoiceItems.size - 1)
        updateTotal()
    }

    private fun updateTotal() {
        val total = invoiceItems.sumOf { it.quantity * it.price }
        tvTotal.text = NumberFormatter.formatCurrency(total)
    }

    private fun saveInvoice() {
        val customerName = etCustomerName.text.toString().trim()
        val invoiceDate = etInvoiceDate.text.toString().trim()
        val dueDateText = etDueDate.text.toString().trim()
        val dueDate = if (dueDateText.isEmpty()) null else dueDateText

        if (customerName.isEmpty()) {
            Toast.makeText(this, "Please enter customer name", Toast.LENGTH_SHORT).show()
            return
        }

        if (invoiceDate.isEmpty()) {
            Toast.makeText(this, "Please select invoice date", Toast.LENGTH_SHORT).show()
            return
        }

        if (invoiceItems.isEmpty()) {
            Toast.makeText(this, "Please add at least one item", Toast.LENGTH_SHORT).show()
            return
        }

        showProgress(true)
        btnSaveInvoice.isEnabled = false

        lifecycleScope.launch(Dispatchers.Main) {
            try {
                val response = createInvoice(customerName, invoiceDate, dueDate, invoiceItems)
                if (response?.success == true) {
                    Toast.makeText(this@AddInvoiceActivity, "Invoice created successfully", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                } else {
                    val errorMessage = response?.error ?: "Unknown error occurred"
                    android.util.Log.e("AddInvoiceActivity", "Failed to create invoice: $errorMessage")
                    Toast.makeText(this@AddInvoiceActivity, "Failed to create invoice: $errorMessage", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("AddInvoiceActivity", "Exception creating invoice", e)
                Toast.makeText(this@AddInvoiceActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                showProgress(false)
                btnSaveInvoice.isEnabled = true
            }
        }
    }

    private suspend fun createInvoice(customerName: String, date: String, dueDate: String?, items: List<InvoiceItem>): InvoiceResponse? {
        return withContext(Dispatchers.IO) {
            try {
                val token = prefs.getString("jwt_token", "") ?: ""

                val total = items.sumOf { it.total }

                val request = CreateInvoiceRequest(
                    customerName = customerName,
                    date = date,
                    dueDate = dueDate,
                    items = items,
                    total = total
                )

                val apiService = InvoiceApiService()
                apiService.createInvoice(token, request)

            } catch (e: Exception) {
                InvoiceResponse(success = false, error = e.message ?: "Unknown error", data = null)
            }
        }
    }

    private fun showProgress(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }
}
