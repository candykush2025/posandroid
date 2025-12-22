package com.blackcode.poscandykush

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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

class AddPurchaseActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var etSupplierName: TextInputEditText
    private lateinit var etPurchaseDate: TextInputEditText
    private lateinit var etDueDate: TextInputEditText
    private lateinit var rgReminderType: RadioGroup
    private lateinit var rbNoReminder: RadioButton
    private lateinit var rbDaysBefore: RadioButton
    private lateinit var rbSpecificDate: RadioButton
    private lateinit var tilDaysBefore: View
    private lateinit var etDaysBefore: TextInputEditText
    private lateinit var tilReminderDate: View
    private lateinit var etReminderDate: TextInputEditText
    private lateinit var tilReminderTime: View
    private lateinit var etReminderTime: TextInputEditText
    private lateinit var rvPurchaseItems: RecyclerView
    private lateinit var btnAddItem: Button
    private lateinit var btnSavePurchase: Button
    private lateinit var tvTotal: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var statusBarBackground: View

    private lateinit var purchaseItemAdapter: PurchaseItemAdapter
    private val purchaseItems = mutableListOf<PurchaseItem>()
    private val products = mutableListOf<Product>()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_purchase)

        setupStatusBar()

        prefs = getSharedPreferences("admin_prefs", MODE_PRIVATE)

        if (!isAuthenticated()) {
            navigateToLogin()
            return
        }

        initializeViews()
        setupListeners()
        setupRecyclerView()
        setDefaultDate()
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
        etSupplierName = findViewById(R.id.et_supplier_name)
        etPurchaseDate = findViewById(R.id.et_purchase_date)
        etDueDate = findViewById(R.id.et_due_date)
        rgReminderType = findViewById(R.id.rg_reminder_type)
        rbNoReminder = findViewById(R.id.rb_no_reminder)
        rbDaysBefore = findViewById(R.id.rb_days_before)
        rbSpecificDate = findViewById(R.id.rb_specific_date)
        tilDaysBefore = findViewById(R.id.til_days_before)
        etDaysBefore = findViewById(R.id.et_days_before)
        tilReminderDate = findViewById(R.id.til_reminder_date)
        etReminderDate = findViewById(R.id.et_reminder_date)
        tilReminderTime = findViewById(R.id.til_reminder_time)
        etReminderTime = findViewById(R.id.et_reminder_time)
        rvPurchaseItems = findViewById(R.id.rv_purchase_items)
        btnAddItem = findViewById(R.id.btn_add_item)
        btnSavePurchase = findViewById(R.id.btn_save_purchase)
        tvTotal = findViewById(R.id.tv_total)
        progressBar = findViewById(R.id.progress_bar)

        val statusBarHeight = getStatusBarHeight()
        statusBarBackground.layoutParams.height = statusBarHeight

        findViewById<View>(R.id.btn_back).setOnClickListener {
            finish()
        }
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    private fun setupListeners() {
        etPurchaseDate.setOnClickListener {
            showDatePicker(false)
        }

        etDueDate.setOnClickListener {
            showWeekdayDatePicker()
        }

        etReminderDate.setOnClickListener {
            showDatePicker(true)
        }

        etReminderTime.setOnClickListener {
            showTimePicker()
        }

        rgReminderType.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rb_no_reminder -> {
                    tilDaysBefore.visibility = View.GONE
                    tilReminderDate.visibility = View.GONE
                    tilReminderTime.visibility = View.GONE
                }
                R.id.rb_days_before -> {
                    tilDaysBefore.visibility = View.VISIBLE
                    tilReminderDate.visibility = View.GONE
                    tilReminderTime.visibility = View.VISIBLE
                }
                R.id.rb_specific_date -> {
                    tilDaysBefore.visibility = View.GONE
                    tilReminderDate.visibility = View.VISIBLE
                    tilReminderTime.visibility = View.VISIBLE
                }
            }
        }

        btnAddItem.setOnClickListener {
            showCategorySelectionDialog()
        }

        btnSavePurchase.setOnClickListener {
            savePurchase()
        }
    }

    private fun setupRecyclerView() {
        rvPurchaseItems.layoutManager = LinearLayoutManager(this)
        purchaseItemAdapter = PurchaseItemAdapter(purchaseItems, { updateTotal() }, true)
        rvPurchaseItems.adapter = purchaseItemAdapter
    }

    private fun setDefaultDate() {
        val today = dateFormat.format(Date())
        etPurchaseDate.setText(today)

        // Set default due date to 30 days from today, adjusted to weekday
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, 30)
        adjustToWeekday(calendar)
        val dueDate = dateFormat.format(calendar.time)
        etDueDate.setText(dueDate)

        // Set default reminder time to 9:00 AM
        etReminderTime.setText("09:00")
    }

    private fun showDatePicker(isReminderDate: Boolean) {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            val selectedDate = Calendar.getInstance()
            selectedDate.set(selectedYear, selectedMonth, selectedDay)
            val dateString = dateFormat.format(selectedDate.time)

            if (isReminderDate) {
                etReminderDate.setText(dateString)
            } else {
                etPurchaseDate.setText(dateString)
            }
        }, year, month, day).show()
    }

    private fun showWeekdayDatePicker() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePicker = DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            val selectedDate = Calendar.getInstance()
            selectedDate.set(selectedYear, selectedMonth, selectedDay)

            // Adjust to next Monday if Saturday
            adjustToWeekday(selectedDate)

            val dateString = dateFormat.format(selectedDate.time)
            etDueDate.setText(dateString)

            // Show message if adjusted
            val originalDay = Calendar.getInstance()
            originalDay.set(selectedYear, selectedMonth, selectedDay)
            if (originalDay.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY) {
                Toast.makeText(this, "Adjusted to next Monday", Toast.LENGTH_SHORT).show()
            } else if (originalDay.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                Toast.makeText(this, "Adjusted to next Monday", Toast.LENGTH_SHORT).show()
            }
        }, year, month, day)

        datePicker.show()
    }

    private fun adjustToWeekday(calendar: Calendar) {
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        when (dayOfWeek) {
            Calendar.SATURDAY -> calendar.add(Calendar.DAY_OF_MONTH, 2) // Move to Monday
            Calendar.SUNDAY -> calendar.add(Calendar.DAY_OF_MONTH, 1) // Move to Monday
        }
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
            etReminderTime.setText(timeString)
        }, hour, minute, true).show()
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
                                id = item.optString("id", ""),
                                productId = item.optString("product_id", ""),
                                name = item.optString("name", ""),
                                description = item.optString("description", ""),
                                sku = item.optString("sku", ""),
                                categoryId = item.optString("category_id", null),
                                categoryName = item.optString("category_name", "Uncategorized"),
                                categoryImage = item.optString("category_image", null),
                                price = item.optDouble("price", 0.0),
                                cost = item.optDouble("cost", 0.0),
                                stock = item.optInt("stock", 0),
                                trackStock = item.optBoolean("track_stock", true),
                                lowStockThreshold = item.optInt("low_stock_threshold", 10),
                                isActive = item.optBoolean("is_active", true),
                                availableForSale = item.optBoolean("available_for_sale", true),
                                createdAt = item.optString("created_at", null),
                                updatedAt = item.optString("updated_at", null)
                            )
                            // Only add active products available for sale
                            if (product.isActive && product.availableForSale) {
                                products.add(product)
                            }
                        }
                    }
                    android.util.Log.d("AddPurchaseActivity", "Loaded ${products.size} products")
                }
            } catch (e: Exception) {
                android.util.Log.e("AddPurchaseActivity", "Failed to load products", e)
                Toast.makeText(this@AddPurchaseActivity, "Failed to load products: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                showProgress(false)
            }
        }
    }

    private suspend fun fetchProducts(): JSONObject {
        return withContext(Dispatchers.IO) {
            val token = prefs.getString("jwt_token", "") ?: ""
            val url = URL("https://pos-candy-kush.vercel.app/api/mobile?action=get-items")

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

    private fun showCategorySelectionDialog() {
        if (products.isEmpty()) {
            Toast.makeText(this, "No products available. Load products first or create a new one.", Toast.LENGTH_LONG).show()
            showCreateProductDialog()
            return
        }

        // Group products by category and filter out null/empty categories
        val categories = products.groupBy {
            val cat = it.category
            when {
                cat.isNullOrBlank() -> "Uncategorized"
                else -> cat.trim()
            }
        }

        val categoryNames = categories.keys.sorted().toMutableList()

        // Add option to create new product at the top
        categoryNames.add(0, "➕ Create New Product")

        android.util.Log.d("AddPurchaseActivity", "Total products: ${products.size}")
        android.util.Log.d("AddPurchaseActivity", "Categories found: ${categoryNames.joinToString(", ")}")
        categories.forEach { (cat, prods) ->
            android.util.Log.d("AddPurchaseActivity", "Category '$cat' has ${prods.size} products")
        }

        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Select Category")
        builder.setItems(categoryNames.toTypedArray()) { _, which ->
            if (which == 0) {
                // Create new product option
                showCreateProductDialog()
            } else {
                val selectedCategory = categoryNames[which]
                val productsInCategory = categories[selectedCategory] ?: emptyList()
                android.util.Log.d("AddPurchaseActivity", "Selected '$selectedCategory', showing ${productsInCategory.size} products")
                showProductSelectionDialog(selectedCategory, productsInCategory)
            }
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun showProductSelectionDialog(category: String, productsInCategory: List<Product>) {
        if (productsInCategory.isEmpty()) {
            Toast.makeText(this, "No products in $category category", Toast.LENGTH_SHORT).show()
            return
        }

        val productNames = productsInCategory.map { it.name }.toTypedArray()
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Select Product from $category")
        builder.setItems(productNames) { _, which ->
            val selectedProduct = productsInCategory[which]
            addPurchaseItem(selectedProduct)
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun showCreateProductDialog() {
        val dialogView = layoutInflater.inflate(android.R.layout.simple_list_item_1, null)
        val linearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
        }

        val etProductName = EditText(this).apply {
            hint = "Product Name"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }

        val etProductPrice = EditText(this).apply {
            hint = "Price"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        val etProductCategory = EditText(this).apply {
            hint = "Category (optional)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }

        linearLayout.addView(etProductName)
        linearLayout.addView(etProductPrice)
        linearLayout.addView(etProductCategory)

        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Create New Product")
        builder.setView(linearLayout)
        builder.setPositiveButton("Add") { _, _ ->
            val name = etProductName.text.toString().trim()
            val priceStr = etProductPrice.text.toString().trim()
            val category = etProductCategory.text.toString().trim().ifBlank { "Uncategorized" }

            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter product name", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            val price = priceStr.toDoubleOrNull()
            if (price == null || price <= 0) {
                Toast.makeText(this, "Please enter valid price", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            // Create temporary product (will be saved when purchase is created)
            val newProduct = Product(
                id = "temp_${System.currentTimeMillis()}", // Temporary ID
                productId = "temp_${System.currentTimeMillis()}",
                name = name,
                price = price,
                cost = 0.0,
                categoryName = category,
                categoryId = null,
                description = "",
                sku = "",
                stock = 0,
                trackStock = false,
                isActive = true,
                availableForSale = true
            )

            // Add to local products list
            products.add(newProduct)

            // Add directly to purchase items
            addPurchaseItem(newProduct)

            Toast.makeText(this, "Product added. Save purchase to create it permanently.", Toast.LENGTH_LONG).show()
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun addPurchaseItem(product: Product) {
        val purchaseItem = PurchaseItem(
            productId = product.productId.ifEmpty { product.id },
            productName = product.name,
            quantity = 1.0,
            price = product.price,
            total = product.price * 1.0
        )
        purchaseItems.add(purchaseItem)
        purchaseItemAdapter.notifyItemInserted(purchaseItems.size - 1)
        updateTotal()
    }

    private fun updateTotal() {
        val total = purchaseItems.sumOf { it.quantity * it.price }
        tvTotal.text = NumberFormatter.formatCurrency(total)
    }

    private fun savePurchase() {
        val supplierName = etSupplierName.text.toString().trim()
        val purchaseDate = etPurchaseDate.text.toString().trim()
        val dueDate = etDueDate.text.toString().trim()

        if (supplierName.isEmpty()) {
            Toast.makeText(this, "Please enter supplier name", Toast.LENGTH_SHORT).show()
            return
        }

        if (purchaseDate.isEmpty()) {
            Toast.makeText(this, "Please select purchase date", Toast.LENGTH_SHORT).show()
            return
        }

        if (dueDate.isEmpty()) {
            Toast.makeText(this, "Please select due date", Toast.LENGTH_SHORT).show()
            return
        }

        if (purchaseItems.isEmpty()) {
            Toast.makeText(this, "Please add at least one item", Toast.LENGTH_SHORT).show()
            return
        }

        // Get reminder settings
        var reminderType: String? = null
        var reminderValue: String? = null
        var reminderTime: String? = null

        when (rgReminderType.checkedRadioButtonId) {
            R.id.rb_days_before -> {
                val days = etDaysBefore.text.toString().trim()
                if (days.isEmpty()) {
                    Toast.makeText(this, "Please enter days before", Toast.LENGTH_SHORT).show()
                    return
                }
                reminderType = "days_before"
                reminderValue = days
                reminderTime = etReminderTime.text.toString().trim()
            }
            R.id.rb_specific_date -> {
                val date = etReminderDate.text.toString().trim()
                if (date.isEmpty()) {
                    Toast.makeText(this, "Please select reminder date", Toast.LENGTH_SHORT).show()
                    return
                }
                reminderType = "specific_date"
                reminderValue = date
                reminderTime = etReminderTime.text.toString().trim()
            }
        }

        showProgress(true)
        btnSavePurchase.isEnabled = false

        lifecycleScope.launch(Dispatchers.Main) {
            try {
                val response = createPurchase(supplierName, purchaseDate, dueDate, purchaseItems, reminderType, reminderValue, reminderTime)
                if (response?.success == true) {
                    Toast.makeText(this@AddPurchaseActivity, "Purchase created successfully", Toast.LENGTH_SHORT).show()

                    // Schedule notification if reminder is set
                    if (reminderType != null && reminderValue != null && reminderTime != null) {
                        scheduleReminder(response.data?.id ?: "", reminderType, reminderValue, reminderTime, supplierName)
                    }

                    setResult(RESULT_OK)
                    finish()
                } else {
                    val errorMessage = response?.error ?: "Unknown error occurred"
                    Toast.makeText(this@AddPurchaseActivity, "Failed to create purchase: $errorMessage", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@AddPurchaseActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                showProgress(false)
                btnSavePurchase.isEnabled = true
            }
        }
    }

    private suspend fun createPurchase(
        supplierName: String,
        date: String,
        dueDate: String,
        items: List<PurchaseItem>,
        reminderType: String?,
        reminderValue: String?,
        reminderTime: String?
    ): PurchaseResponse? {
        return withContext(Dispatchers.IO) {
            try {
                val token = prefs.getString("jwt_token", "") ?: ""
                val total = items.sumOf { it.total }

                android.util.Log.d("AddPurchaseActivity", "Creating purchase - Supplier: $supplierName, Date: $date, Due: $dueDate, Total: $total")

                val request = CreatePurchaseRequest(
                    supplierName = supplierName,
                    purchaseDate = date,
                    dueDate = dueDate,
                    items = items,
                    total = total,
                    reminderType = reminderType,
                    reminderValue = reminderValue,
                    reminderTime = reminderTime
                )

                val apiService = PurchaseApiService()
                val response = apiService.createPurchase(token, request)

                android.util.Log.d("AddPurchaseActivity", "Create purchase response: ${response?.success}, error: ${response?.error}")

                response

            } catch (e: Exception) {
                android.util.Log.e("AddPurchaseActivity", "Exception creating purchase", e)
                PurchaseResponse(success = false, error = e.message ?: "Unknown error", data = null)
            }
        }
    }

    private fun scheduleReminder(purchaseId: String, reminderType: String, reminderValue: String, reminderTime: String, supplierName: String) {
        // TODO: Implement WorkManager notification scheduling
        // This will be handled by PurchaseReminderWorker
        Toast.makeText(this, "Reminder scheduled", Toast.LENGTH_SHORT).show()
    }

    private fun showProgress(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }
}

