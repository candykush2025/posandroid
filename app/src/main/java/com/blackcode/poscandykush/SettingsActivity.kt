package com.blackcode.poscandykush

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.google.android.material.bottomnavigation.BottomNavigationView

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var statusBarBackground: View
    private lateinit var cache: SalesDataCache

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        setupStatusBar()

        prefs = getSharedPreferences("admin_prefs", MODE_PRIVATE)
        cache = SalesDataCache(this)

        initializeViews()
        setupBottomNavigation()
        displayUserInfo()
        displayCacheInfo()
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
        bottomNavigation = findViewById(R.id.bottom_navigation)
        statusBarBackground = findViewById(R.id.status_bar_background)

        // Set status bar height
        val statusBarHeight = getStatusBarHeight()
        statusBarBackground.layoutParams.height = statusBarHeight

        findViewById<CardView>(R.id.cv_logout).setOnClickListener {
            showLogoutConfirmation()
        }

        findViewById<Button>(R.id.btn_clear_cache).setOnClickListener {
            showClearCacheConfirmation()
        }
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    private fun setupBottomNavigation() {
        bottomNavigation.selectedItemId = R.id.nav_settings

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
                R.id.nav_finance -> {
                    startActivity(Intent(this, FinanceActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_settings -> true
                else -> false
            }
        }
    }

    private fun displayUserInfo() {
        val userName = prefs.getString("user_name", "Admin")
        val userEmail = prefs.getString("user_email", "")
        val userRole = prefs.getString("user_role", "")

        findViewById<TextView>(R.id.tv_user_name).text = userName
        findViewById<TextView>(R.id.tv_user_email).text = userEmail
        findViewById<TextView>(R.id.tv_user_role).text = userRole?.uppercase()
    }

    private fun displayCacheInfo() {
        val cachedMonths = cache.getCachedMonths()
        val cacheInfo = if (cachedMonths.isNotEmpty()) {
            "Cached data: ${cachedMonths.size} months"
        } else {
            "No cached data"
        }
        findViewById<TextView>(R.id.tv_cache_info).text = cacheInfo
    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ ->
                performLogout()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showClearCacheConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Clear Cache")
            .setMessage("This will delete all locally cached sales data. Are you sure?")
            .setPositiveButton("Clear") { _, _ ->
                cache.clearCache()
                displayCacheInfo()
                Toast.makeText(this, "Cache cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performLogout() {
        // Clear all saved data
        prefs.edit().clear().apply()

        // Clear cache
        cache.clearCache()

        // Navigate to login
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        bottomNavigation.selectedItemId = R.id.nav_settings
    }
}
