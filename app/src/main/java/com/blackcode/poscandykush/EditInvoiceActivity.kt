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

class EditInvoiceActivity : AppCompatActivity() {

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
    private var invoiceId: String? = null

    companion object {
        private const val BASE_URL = "https://pos-candy-kush.vercel.app/api/mobile"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_invoice) // Reuse the same layout

        setupStatusBar()

        prefs = getSharedPreferences("admin_prefs", MODE_PRIVATE)

        invoiceId = intent.getStringExtra("invoice_id")

        initializeViews()
        setupRecyclerView()
        setupDatePickers()
        setupButtons()

        if (invoiceId != null) {
            loadInvoiceForEditing(invoiceId!!)
        } else {
            Toast.makeText(this, "Invalid invoice ID", Toast.LENGTH_SHORT).show()
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
        etCustomerName = findViewById(R.id.et_customer_name)
        etInvoiceDate = findViewById(R.id.et_invoice_date)
        etDueDate = findViewById(R.id.et_due_date)
        rvInvoiceItems = findViewById(R.id.rv_invoice_items)
        btnAddItem = findViewById(R.id.btn_add_item)
        btnSaveInvoice = findViewById(R.id.btn_save_invoice)
        tvTotal = findViewById(R.id.tv_total)
        progressBar = findViewById(R.id.progress_bar)
        statusBarBackground = findViewById(R.id.status_bar_background)

        // Set status bar height
        val statusBarHeight = getStatusBarHeight()
        statusBarBackground.layoutParams.height = statusBarHeight

        // Setup back button
        findViewById<View>(R.id.btn_back).setOnClickListener {
            finish()
        }

        // Change save button text
        btnSaveInvoice.text = "Update Invoice"
    }

    private fun setupRecyclerView() {
        rvInvoiceItems.layoutManager = LinearLayoutManager(this)
        invoiceItemAdapter = InvoiceItemAdapter(invoiceItems, ::updateTotal)
        rvInvoiceItems.adapter = invoiceItemAdapter
    }

    private fun setupDatePickers() {
        etInvoiceDate.setOnClickListener {
            showDatePicker { date ->
                etInvoiceDate.setText(date)
            }
        }

        etDueDate.setOnClickListener {
            showDatePicker { date ->
                etDueDate.setText(date)
            }
        }
    }

    private fun setupButtons() {
        btnAddItem.setOnClickListener {
            // Add new item logic (reuse from AddInvoiceActivity)
            addNewItem()
        }

        btnSaveInvoice.setOnClickListener {
            updateInvoice()
        }
    }

    private fun loadInvoiceForEditing(invoiceId: String) {
        showProgress(true)

        lifecycleScope.launch(Dispatchers.Main) {
            try {
                val invoice = fetchInvoiceDetail(invoiceId)
                if (invoice != null) {
                    populateFields(invoice)
                    showProgress(false)
                } else {
                    Toast.makeText(this@EditInvoiceActivity, "Invoice not found", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                showProgress(false)
                Toast.makeText(this@EditInvoiceActivity, "Failed to load invoice: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private suspend fun fetchInvoiceDetail(invoiceId: String): Invoice? {
        return withContext(Dispatchers.IO) {
            val token = prefs.getString("jwt_token", "") ?: ""
            val apiService = InvoiceApiService()
            val response = apiService.getInvoice(token, invoiceId)
            if (response?.success == true) {
                response.data?.invoice // Invoice is nested under data.invoice
            } else {
                null
            }
        }
    }

    private fun populateFields(invoice: Invoice) {
        etCustomerName.setText(invoice.customerName)
        etInvoiceDate.setText(invoice.date)
        etDueDate.setText(invoice.dueDate ?: "")

        invoiceItems.clear()
        invoiceItems.addAll(invoice.items)
        invoiceItemAdapter.notifyDataSetChanged()

        updateTotal()
    }

    private fun addNewItem() {
        // Simplified: just add a default item
        val newItem = InvoiceItem(
            productId = "",
            productName = "New Item",
            quantity = 1.0,
            unitPrice = 0.0,
            total = 0.0
        )
        invoiceItems.add(newItem)
        invoiceItemAdapter.notifyItemInserted(invoiceItems.size - 1)
        updateTotal()
    }

    private fun updateTotal() {
        val total = invoiceItems.sumOf { it.total }
        tvTotal.text = "Total: ${NumberFormatter.formatCurrency(total)}"
    }

    private fun updateInvoice() {
        val customerName = etCustomerName.text.toString().trim()
        val invoiceDate = etInvoiceDate.text.toString().trim()
        val dueDate = etDueDate.text.toString().trim()

        if (customerName.isEmpty() || invoiceDate.isEmpty()) {
            Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show()
            return
        }

        showProgress(true)

        lifecycleScope.launch(Dispatchers.Main) {
            try {
                val success = performUpdateInvoice(invoiceId!!, customerName, invoiceDate, dueDate, invoiceItems)
                showProgress(false)

                if (success) {
                    Toast.makeText(this@EditInvoiceActivity, "Invoice updated successfully", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                } else {
                    Toast.makeText(this@EditInvoiceActivity, "Failed to update invoice", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                showProgress(false)
                Toast.makeText(this@EditInvoiceActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun performUpdateInvoice(
        invoiceId: String,
        customerName: String,
        invoiceDate: String,
        dueDate: String,
        items: List<InvoiceItem>
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val token = prefs.getString("jwt_token", "") ?: ""
                val url = URL("$BASE_URL?action=edit-invoice&id=$invoiceId")
                val connection = url.openConnection() as HttpURLConnection

                connection.requestMethod = "PUT"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.doOutput = true

                val jsonBody = JSONObject().apply {
                    put("customer_name", customerName)
                    put("date", invoiceDate)
                    if (dueDate.isNotEmpty()) put("due_date", dueDate)
                    put("items", JSONArray().apply {
                        items.forEach { item ->
                            put(JSONObject().apply {
                                put("product_id", item.productId)
                                put("quantity", item.quantity)
                                put("unit_price", item.price)
                            })
                        }
                    })
                }

                connection.outputStream.use { it.write(jsonBody.toString().toByteArray()) }

                val responseCode = connection.responseCode
                connection.disconnect()

                responseCode == HttpURLConnection.HTTP_OK
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun showDatePicker(onDateSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, day ->
                calendar.set(year, month, day)
                onDateSelected(dateFormat.format(calendar.time))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showProgress(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }
}
