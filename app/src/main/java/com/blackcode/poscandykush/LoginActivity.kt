package com.blackcode.poscandykush

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initialize views
        etEmail = findViewById(R.id.et_email)
        etPassword = findViewById(R.id.et_password)
        btnLogin = findViewById(R.id.btn_login)
        progressBar = findViewById(R.id.progress_bar)

        prefs = getSharedPreferences("admin_prefs", MODE_PRIVATE)

        // Check if already logged in
        val token = prefs.getString("jwt_token", null)
        val tokenExpiry = prefs.getLong("token_expiry", 0)

        if (token != null && System.currentTimeMillis() < tokenExpiry) {
            // Token is valid, check if initial load was completed
            val initialLoadComplete = prefs.getBoolean("initial_load_complete", false)
            if (initialLoadComplete) {
                // Go directly to dashboard
                navigateToDashboard()
            } else {
                // Need to do initial data load
                navigateToInitialLoading()
            }
            return
        }

        btnLogin.setOnClickListener {
            attemptLogin()
        }
    }

    private fun attemptLogin() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
            return
        }

        showProgress(true)

        lifecycleScope.launch(Dispatchers.Main) {
            try {
                val result = performLogin(email, password)
                handleLoginResult(result)
            } catch (e: Exception) {
                showProgress(false)
                Toast.makeText(this@LoginActivity, "Login failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun performLogin(email: String, password: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://pos-candy-kush.vercel.app/api/mobile?action=login")
                val connection = url.openConnection() as HttpURLConnection

                connection.apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    connectTimeout = 15000
                    readTimeout = 15000
                }

                val requestBody = JSONObject().apply {
                    put("email", email)
                    put("password", password)
                }

                connection.outputStream.use { outputStream ->
                    outputStream.write(requestBody.toString().toByteArray())
                }

                val responseCode = connection.responseCode
                val responseBody = if (responseCode == 200) {
                    connection.inputStream.bufferedReader().readText()
                } else {
                    connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                }

                connection.disconnect()
                responseBody
            } catch (e: Exception) {
                e.printStackTrace()
                throw Exception("Network error: ${e.message}")
            }
        }
    }

    private fun handleLoginResult(response: String) {
        showProgress(false)

        try {
            val jsonResponse = JSONObject(response)

            if (jsonResponse.getBoolean("success")) {
                // Login successful
                val token = jsonResponse.getString("token")
                val expiresIn = jsonResponse.getLong("expires_in")
                val user = jsonResponse.getJSONObject("user")

                // Save login data
                prefs.edit().apply {
                    putString("jwt_token", token)
                    putLong("token_expiry", System.currentTimeMillis() + (expiresIn * 1000))
                    putString("user_id", user.getString("id"))
                    putString("user_name", user.getString("name"))
                    putString("user_email", user.getString("email"))
                    putString("user_role", user.getString("role"))
                    // Reset initial load flag on new login
                    putBoolean("initial_load_complete", false)
                    apply()
                }

                Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show()

                // Navigate to initial loading screen to fetch data
                navigateToInitialLoading()
            } else {
                val error = jsonResponse.getString("error")
                Toast.makeText(this, "Login failed: $error", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Invalid response format", Toast.LENGTH_LONG).show()
        }
    }

    private fun navigateToInitialLoading() {
        val intent = Intent(this, InitialDataLoadingActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun navigateToDashboard() {
        val intent = Intent(this, DashboardActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun showProgress(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        btnLogin.isEnabled = !show
        etEmail.isEnabled = !show
        etPassword.isEnabled = !show
    }
}
