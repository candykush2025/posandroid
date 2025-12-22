package com.blackcode.poscandykush

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class EditCostActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var statusBarBackground: View
    private lateinit var tvItemInfo: TextView
    private lateinit var tvCurrentCost: TextView
    private lateinit var etNewCost: EditText
    private lateinit var btnSave: Button
    private lateinit var progressBar: ProgressBar

    private lateinit var itemData: JSONObject

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_cost)

        setupStatusBar()

        prefs = getSharedPreferences("admin_prefs", MODE_PRIVATE)

        // Check authentication
        if (!isAuthenticated()) {
            navigateToLogin()
            return
        }

        // Get item data from intent
        val itemDataString = intent.getStringExtra("item_data")
        if (itemDataString == null) {
            Toast.makeText(this, "No item data provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        itemData = JSONObject(itemDataString)

        initializeViews()
        setupListeners()
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
        tvItemInfo = findViewById(R.id.tv_item_info)
        tvCurrentCost = findViewById(R.id.tv_current_cost)
        etNewCost = findViewById(R.id.et_new_cost)
        btnSave = findViewById(R.id.btn_save)
        progressBar = findViewById(R.id.progress_bar)

        // Set status bar height
        val statusBarHeight = getStatusBarHeight()
        statusBarBackground.layoutParams.height = statusBarHeight

        // Display item info
        val productName = itemData.optString("product_name", "Unknown Item")
        val category = itemData.optString("category", "Unknown Category")
        val currentCost = itemData.optDouble("cost", 0.0)

        tvItemInfo.text = "Item: $productName\nCategory: $category"
        tvCurrentCost.text = "${NumberFormatter.formatCurrency(currentCost)}"

        // Set current cost as hint in edit text
        etNewCost.hint = "Current: ${NumberFormatter.formatCurrency(currentCost)}"
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    private fun setupListeners() {
        findViewById<View>(R.id.btn_back).setOnClickListener {
            finish()
        }

        btnSave.setOnClickListener {
            saveNewCost()
        }
    }

    private fun saveNewCost() {
        val newCostText = etNewCost.text.toString().trim()

        if (newCostText.isEmpty()) {
            Toast.makeText(this, "Please enter a new cost", Toast.LENGTH_SHORT).show()
            return
        }

        val newCost = try {
            newCostText.toDouble()
        } catch (e: NumberFormatException) {
            Toast.makeText(this, "Please enter a valid number", Toast.LENGTH_SHORT).show()
            return
        }

        if (newCost < 0) {
            Toast.makeText(this, "Cost cannot be negative", Toast.LENGTH_SHORT).show()
            return
        }

        // Show progress and disable button
        showProgress(true)
        btnSave.isEnabled = false

        lifecycleScope.launch(Dispatchers.Main) {
            try {
                val success = updateProductCost(newCost)

                if (success) {
                    Toast.makeText(this@EditCostActivity, "Purchase cost updated successfully", Toast.LENGTH_SHORT).show()

                    // Update the item data with new cost
                    itemData.put("cost", newCost)

                    // Return to previous activity with updated data
                    val intent = Intent()
                    intent.putExtra("updated_item_data", itemData.toString())
                    setResult(RESULT_OK, intent)
                    finish()
                } else {
                    Toast.makeText(this@EditCostActivity, "Failed to update purchase cost", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@EditCostActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                showProgress(false)
                btnSave.isEnabled = true
            }
        }
    }

    private suspend fun updateProductCost(newCost: Double): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val token = prefs.getString("jwt_token", "") ?: ""
                val productId = itemData.optString("product_id", "")

                if (productId.isEmpty()) {
                    return@withContext false
                }

                // Create request body for new API endpoint
                val requestBody = JSONObject().apply {
                    put("action", "edit-product-cost")
                    put("productId", productId)
                    put("cost", newCost)
                }

                val url = URL("https://pos-candy-kush.vercel.app/api/mobile")

                val connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "POST"
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    connectTimeout = 30000
                    readTimeout = 30000
                }

                // Write request body
                connection.outputStream.use { outputStream ->
                    outputStream.write(requestBody.toString().toByteArray(Charsets.UTF_8))
                }

                val responseCode = connection.responseCode
                val responseBody = if (responseCode == 200) {
                    connection.inputStream.bufferedReader().readText()
                } else {
                    connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                }

                connection.disconnect()

                // Check if the response indicates success
                val responseJson = JSONObject(responseBody)
                responseJson.optBoolean("success", false)

            } catch (e: Exception) {
                false
            }
        }
    }

    private fun showProgress(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        btnSave.visibility = if (show) View.GONE else View.VISIBLE
    }
}
