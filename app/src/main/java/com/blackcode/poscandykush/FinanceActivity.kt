package com.blackcode.poscandykush

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.google.android.material.bottomnavigation.BottomNavigationView

class FinanceActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var progressBar: ProgressBar
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var statusBarBackground: View
    private lateinit var cvProductManagement: CardView
    private lateinit var cvCustomerInvoice: CardView
    private lateinit var cvPurchasing: CardView
    private lateinit var cvExpenses: CardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_finance)

        setupStatusBar()

        prefs = getSharedPreferences("admin_prefs", MODE_PRIVATE)

        // Check authentication
        if (!isAuthenticated()) {
            navigateToLogin()
            return
        }

        initializeViews()
        setupBottomNavigation()
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
        progressBar = findViewById(R.id.progress_bar)
        bottomNavigation = findViewById(R.id.bottom_navigation)
        statusBarBackground = findViewById(R.id.status_bar_background)
        cvProductManagement = findViewById(R.id.cv_product_management)
        cvCustomerInvoice = findViewById(R.id.cv_customer_invoice)
        cvPurchasing = findViewById(R.id.cv_purchasing)
        cvExpenses = findViewById(R.id.cv_expenses)

        // Set status bar height
        val statusBarHeight = getStatusBarHeight()
        statusBarBackground.layoutParams.height = statusBarHeight

        // Setup card clicks
        cvProductManagement.setOnClickListener {
            startActivity(Intent(this, ProductManagementActivity::class.java))
        }

        cvCustomerInvoice.setOnClickListener {
            startActivity(Intent(this, CustomerInvoiceActivity::class.java))
        }

        cvPurchasing.setOnClickListener {
            startActivity(Intent(this, PurchasingActivity::class.java))
        }

        cvExpenses.setOnClickListener {
            startActivity(Intent(this, ExpenseActivity::class.java))
        }
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
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

    private fun showToast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
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
}
