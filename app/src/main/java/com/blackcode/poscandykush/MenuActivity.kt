package com.blackcode.poscandykush

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class MenuActivity : AppCompatActivity() {

    private val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN
        )
    }

    private lateinit var progressDialog: AlertDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)

        val btnAdmin: Button = findViewById(R.id.btn_admin)
        val btnPos: Button = findViewById(R.id.btn_pos)
        val btnPrinterSettings: Button = findViewById(R.id.btn_printer_settings)
        val btnCheckUpdates: Button = findViewById(R.id.btn_check_updates)

        btnAdmin.setOnClickListener {
            // Open Login Activity for admin access
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }

        btnPos.setOnClickListener {
            // Check Bluetooth permissions before opening POS
            if (bluetoothPermissions.all {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }) {
                // Permissions granted, open POS
                openPosActivity()
            } else {
                // Request Bluetooth permissions
                ActivityCompat.requestPermissions(this, bluetoothPermissions, REQUEST_CODE_BLUETOOTH)
            }
        }

        btnPrinterSettings.setOnClickListener {
            // Open Printer Settings Activity
            val intent = Intent(this, PrinterSetupActivity::class.java)
            startActivity(intent)
        }

        btnCheckUpdates.setOnClickListener {
            checkForUpdates()
        }

        // Set version text
        val tvVersion: TextView = findViewById(R.id.tv_version)
        tvVersion.text = "Version: ${getCurrentVersion()}"

        // Auto-check for updates on app start
        checkForUpdatesSilently()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_BLUETOOTH) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // Permissions granted, open POS
                Toast.makeText(this, "Bluetooth permissions granted", Toast.LENGTH_SHORT).show()
                openPosActivity()
            } else {
                // Denied: show rationale dialog
                if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                )) {
                    AlertDialog.Builder(this)
                        .setTitle("Bluetooth Permission Needed")
                        .setMessage("POS requires Bluetooth access to print receipts. Please grant permission to continue.")
                        .setPositiveButton("Grant") { _, _ ->
                            ActivityCompat.requestPermissions(
                                this,
                                bluetoothPermissions,
                                REQUEST_CODE_BLUETOOTH
                            )
                        }
                        .setNegativeButton("Cancel") { dialog, _ ->
                            dialog.dismiss()
                            Toast.makeText(
                                this,
                                "Bluetooth permission is required for POS functionality",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        .show()
                } else {
                    Toast.makeText(
                        this,
                        "Bluetooth permissions denied. Please enable them in Settings to use POS.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun openPosActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
    }

    private fun checkForUpdates() {
        // Permissions not needed for app-specific storage
        performUpdateCheck()
    }

    private fun performUpdateCheck() {
        progressDialog = AlertDialog.Builder(this)
            .setTitle("Checking for Updates")
            .setMessage("Please wait while we check for the latest version...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        lifecycleScope.launch(Dispatchers.Main) {
            try {
                val updateInfo = withContext(Dispatchers.IO) {
                    fetchUpdateInfo()
                }

                progressDialog.dismiss()

                if (updateInfo != null) {
                    val currentVersion = getCurrentVersion()
                    val latestVersion = updateInfo.version

                    if (isNewerVersion(latestVersion, currentVersion)) {
                        showUpdateDialog(updateInfo)
                    } else {
                        Toast.makeText(this@MenuActivity, "Your app is up to date (v$currentVersion)", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@MenuActivity, "Unable to check for updates", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                progressDialog.dismiss()
                e.printStackTrace()
                Toast.makeText(this@MenuActivity, "Error checking for updates: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun fetchUpdateInfo(): UpdateInfo? {
        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL("https://pos-candy-kush.vercel.app/api/apk")
                connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                }

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    parseUpdateInfo(JSONObject(response))
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun parseUpdateInfo(json: JSONObject): UpdateInfo {
        return UpdateInfo(
            name = json.optString("name", "POS Candy Kush"),
            version = json.optString("version", "1.0.0"),
            versionCode = json.optInt("versionCode", 1),
            sizeFormatted = json.optString("sizeFormatted", "Unknown"),
            developer = json.optString("developer", "Unknown"),
            packageName = json.optString("packageName", "com.blackcode.poscandykush"),
            downloadUrl = "https://pos-candy-kush.vercel.app${json.optString("downloadUrl", "/api/apk/download")}",
            lastUpdated = json.optString("lastUpdated", ""),
            minAndroidVersion = json.optString("minAndroidVersion", "8.0")
        )
    }

    private fun getCurrentVersion(): String {
        return try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    private fun isNewerVersion(latestVersion: String, currentVersion: String): Boolean {
        return try {
            val latestParts = latestVersion.split(".").map { it.toInt() }
            val currentParts = currentVersion.split(".").map { it.toInt() }

            for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
                val latest = latestParts.getOrElse(i) { 0 }
                val current = currentParts.getOrElse(i) { 0 }

                if (latest > current) return true
                if (latest < current) return false
            }
            false // versions are equal
        } catch (e: Exception) {
            false
        }
    }

    private fun showUpdateDialog(updateInfo: UpdateInfo) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("🎉 Update Available!")
            .setMessage("""
                A new version of ${updateInfo.name} is available!

                📱 Version: ${updateInfo.version}
                📏 Size: ${updateInfo.sizeFormatted}
                👨‍💻 Developer: ${updateInfo.developer}
                📅 Last Updated: ${formatLastUpdated(updateInfo.lastUpdated)}

                Would you like to download and install this update?
            """.trimIndent())
            .setPositiveButton("Download & Install") { _, _ ->
                downloadAndInstallUpdate(updateInfo)
            }
            .setNegativeButton("Later") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(true)
            .create()

        dialog.show()
    }

    private fun formatLastUpdated(lastUpdated: String): String {
        return try {
            val inputFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
            val outputFormat = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
            val date = inputFormat.parse(lastUpdated.replace("Z", "").replace(".000", ""))
            outputFormat.format(date ?: java.util.Date())
        } catch (e: Exception) {
            "Recently"
        }
    }

    private fun downloadAndInstallUpdate(updateInfo: UpdateInfo) {
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("Downloading Update")
            .setMessage("Please wait while we download the latest version...")
            .setCancelable(false)
            .create()

        progressDialog.show()

        lifecycleScope.launch(Dispatchers.Main) {
            try {
                val apkFile = withContext(Dispatchers.IO) {
                    downloadApk(updateInfo.downloadUrl)
                }

                progressDialog.dismiss()

                if (apkFile != null) {
                    installApk(apkFile)
                } else {
                    Toast.makeText(this@MenuActivity, "Download failed. Please try again.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                progressDialog.dismiss()
                e.printStackTrace()
                Toast.makeText(this@MenuActivity, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun downloadApk(downloadUrl: String): File? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(downloadUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "GET"
                    connectTimeout = 30000
                    readTimeout = 30000
                }

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val fileName = "pos_candy_kush_update.apk"
                    val apkFile = File(getExternalFilesDir(null), fileName)

                    connection.inputStream.use { input ->
                        FileOutputStream(apkFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    apkFile
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun installApk(apkFile: File) {
        try {
            val apkUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Unable to install update. Please check your device settings.", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkForUpdatesSilently() {
        // Only check once per day to avoid spam
        val prefs = getSharedPreferences("update_prefs", MODE_PRIVATE)
        val lastCheck = prefs.getLong("last_update_check", 0)
        val currentTime = System.currentTimeMillis()
        val oneDayInMillis = 24 * 60 * 60 * 1000

        if (currentTime - lastCheck > oneDayInMillis) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val updateInfo = fetchUpdateInfo()
                    if (updateInfo != null) {
                        val currentVersion = getCurrentVersion()
                        if (isNewerVersion(updateInfo.version, currentVersion)) {
                            // Show a subtle notification that update is available
                            withContext(Dispatchers.Main) {
                                showUpdateNotification(updateInfo)
                            }
                        }
                    }

                    // Update last check time
                    prefs.edit().putLong("last_update_check", currentTime).apply()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun showUpdateNotification(updateInfo: UpdateInfo) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("📱 Update Available")
            .setMessage("Version ${updateInfo.version} is ready to download!")
            .setPositiveButton("Update Now") { _, _ ->
                downloadAndInstallUpdate(updateInfo)
            }
            .setNegativeButton("Remind Me Later") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(true)
            .create()

        dialog.show()
    }

    companion object {
        private const val REQUEST_CODE_BLUETOOTH = 1001
        private const val REQUEST_CODE_STORAGE = 1002
    }
}

// Data class to hold update information
data class UpdateInfo(
    val name: String,
    val version: String,
    val versionCode: Int,
    val sizeFormatted: String,
    val developer: String,
    val packageName: String,
    val downloadUrl: String,
    val lastUpdated: String,
    val minAndroidVersion: String
)
