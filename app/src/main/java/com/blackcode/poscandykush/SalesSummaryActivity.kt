package com.blackcode.poscandykush

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class SalesSummaryActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var progressBar: ProgressBar
    private lateinit var tvGrossSales: TextView
    private lateinit var tvRefunds: TextView
    private lateinit var tvDiscounts: TextView
    private lateinit var tvNetSales: TextView
    private lateinit var tvCostOfGoods: TextView
    private lateinit var tvGrossProfit: TextView
    private lateinit var tvTotalTransactions: TextView
    private lateinit var tvItemsSold: TextView
    private lateinit var cache: SalesDataCache

    private var period = "today"
    private var dateKey = ""
    private var startDate = ""
    private var endDate = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sales_summary)

        prefs = getSharedPreferences("admin_prefs", MODE_PRIVATE)
        cache = SalesDataCache(this)

        // Get date parameters from intent
        period = intent.getStringExtra("period") ?: "today"
        dateKey = intent.getStringExtra("date_key") ?: ""
        startDate = intent.getStringExtra("start_date") ?: ""
        endDate = intent.getStringExtra("end_date") ?: ""

        initializeViews()
        setupToolbar()
        clearUI()
        loadSalesSummary()
    }

    private fun initializeViews() {
        progressBar = findViewById(R.id.progress_bar)
        tvGrossSales = findViewById(R.id.tv_gross_sales)
        tvRefunds = findViewById(R.id.tv_refunds)
        tvDiscounts = findViewById(R.id.tv_discounts)
        tvNetSales = findViewById(R.id.tv_net_sales)
        tvCostOfGoods = findViewById(R.id.tv_cost_of_goods)
        tvGrossProfit = findViewById(R.id.tv_gross_profit)
        tvTotalTransactions = findViewById(R.id.tv_total_transactions)
        tvItemsSold = findViewById(R.id.tv_items_sold)
    }

    private fun setupToolbar() {
        findViewById<TextView>(R.id.tv_title).text = "Sales Summary"
        findViewById<View>(R.id.btn_back).setOnClickListener {
            finish()
        }
    }

    private fun clearUI() {
        tvGrossSales.text = "---"
        tvRefunds.text = "---"
        tvDiscounts.text = "---"
        tvNetSales.text = "---"
        tvCostOfGoods.text = "---"
        tvGrossProfit.text = "---"
        tvTotalTransactions.text = "---"
        tvItemsSold.text = "---"
    }

    private fun loadSalesSummary() {
        showProgress(true)

        lifecycleScope.launch(Dispatchers.Main) {
            try {
                // Try cache first
                val cachedData = if (dateKey.isNotEmpty()) {
                    cache.getFromCacheByDate("sales-summary", period, dateKey)
                } else null

                if (cachedData != null) {
                    updateUI(cachedData.first)
                    showProgress(false)
                    return@launch
                }

                // Fetch from API with date range
                val data = if (startDate.isNotEmpty() && endDate.isNotEmpty()) {
                    makeApiCallWithDateRange("sales-summary", startDate, endDate)
                } else {
                    makeApiCall("sales-summary")
                }

                // Cache the data
                if (dateKey.isNotEmpty() && startDate.isNotEmpty() && endDate.isNotEmpty()) {
                    cache.saveToCacheWithDate("sales-summary", period, dateKey, startDate, endDate, data)
                }

                updateUI(data)
                showProgress(false)
            } catch (e: Exception) {
                showProgress(false)
                showEmptyState()
                Toast.makeText(this@SalesSummaryActivity, "Failed to load data: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun makeApiCall(action: String): JSONObject {
        return withContext(Dispatchers.IO) {
            val token = prefs.getString("jwt_token", "") ?: ""
            val url = URL("https://pos-candy-kush.vercel.app/api/mobile?action=$action&period=$period")

            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 15000
                readTimeout = 15000
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

    private suspend fun makeApiCallWithDateRange(action: String, startDate: String, endDate: String): JSONObject {
        return withContext(Dispatchers.IO) {
            val token = prefs.getString("jwt_token", "") ?: ""
            val url = URL("https://pos-candy-kush.vercel.app/api/mobile?action=$action&period=custom&start_date=$startDate&end_date=$endDate")

            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 15000
                readTimeout = 15000
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

    private fun updateUI(data: JSONObject) {
        if (data.optBoolean("success", false)) {
            val dataObj = data.optJSONObject("data")
            val metrics = dataObj?.optJSONObject("metrics")
            val transactions = dataObj?.optJSONObject("transactions")

            if (metrics != null) {
                tvGrossSales.text = NumberFormatter.formatCurrency(metrics.optDouble("gross_sales", 0.0))
                tvRefunds.text = NumberFormatter.formatCurrency(metrics.optDouble("refunds", 0.0))
                tvDiscounts.text = NumberFormatter.formatCurrency(metrics.optDouble("discounts", 0.0))
                tvNetSales.text = NumberFormatter.formatCurrency(metrics.optDouble("net_sales", 0.0))
                tvCostOfGoods.text = NumberFormatter.formatCurrency(metrics.optDouble("cost_of_goods", 0.0))
                tvGrossProfit.text = NumberFormatter.formatCurrency(metrics.optDouble("gross_profit", 0.0))
            } else {
                showEmptyState()
            }

            if (transactions != null) {
                tvTotalTransactions.text = NumberFormatter.formatInteger(transactions.optInt("total_count", 0))
                tvItemsSold.text = NumberFormatter.formatInteger(transactions.optInt("items_sold", 0))
            } else {
                tvTotalTransactions.text = "0"
                tvItemsSold.text = "0"
            }
        } else {
            showEmptyState()
        }
    }

    private fun showEmptyState() {
        tvGrossSales.text = "$0.00"
        tvRefunds.text = "$0.00"
        tvDiscounts.text = "$0.00"
        tvNetSales.text = "$0.00"
        tvCostOfGoods.text = "$0.00"
        tvGrossProfit.text = "$0.00"
        tvTotalTransactions.text = "0"
        tvItemsSold.text = "0"
    }

    private fun showProgress(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }
}
