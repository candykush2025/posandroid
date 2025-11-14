package com.blackcode.poscandykush

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var displayManager: DisplayManager
    private var customerPresentation: CustomerPresentation? = null
    private lateinit var viewModel: CartViewModel

    private val bluetoothPermissions = arrayOf(
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN
    )

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) { showPresentationIfAvailable() }
        override fun onDisplayChanged(displayId: Int) { /* optional */ }
        override fun onDisplayRemoved(displayId: Int) {
            customerPresentation?.let {
                if (it.display.displayId == displayId) dismissPresentation()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Check and request Bluetooth permissions
        if (bluetoothPermissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            // Permissions granted, proceed
        } else {
            ActivityCompat.requestPermissions(this, bluetoothPermissions, REQUEST_CODE_BLUETOOTH)
        }

        // Immersive full-screen WebView on primary display
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)

        val webView: WebView = findViewById(R.id.webview)
        webView.webViewClient = WebViewClient()
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
        }
        webView.loadUrl("https://pos-candy-kush.vercel.app")

        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        viewModel = ViewModelProvider(this).get(CartViewModel::class.java)

        // Start polling cart
        viewModel.startPollingCart()

        // Observe cart updates and forward to presentation
        lifecycleScope.launch {
            viewModel.cartState.collect { cartResponse ->
                customerPresentation?.updateCart(cartResponse)
            }
        }

        lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                customerPresentation?.setLoading(isLoading)
            }
        }

        lifecycleScope.launch {
            viewModel.errorMessage.collect { error ->
                customerPresentation?.setError(error)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_BLUETOOTH) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // Granted, proceed
                Toast.makeText(this, "Bluetooth permissions granted", Toast.LENGTH_SHORT).show()
            } else {
                // Denied: show rationale dialog
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.BLUETOOTH_CONNECT)) {
                    AlertDialog.Builder(this)
                        .setTitle("Bluetooth Permission Needed")
                        .setMessage("This app requires Bluetooth access to print receipts.")
                        .setPositiveButton("Grant") { _, _ ->
                            ActivityCompat.requestPermissions(this, bluetoothPermissions, REQUEST_CODE_BLUETOOTH)
                        }
                        .setNegativeButton("Deny", null)
                        .show()
                } else {
                    Toast.makeText(this, "Bluetooth permissions denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        displayManager.registerDisplayListener(displayListener, null)
        showPresentationIfAvailable()
    }

    override fun onPause() {
        super.onPause()
        displayManager.unregisterDisplayListener(displayListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        dismissPresentation()
    }

    private fun showPresentationIfAvailable() {
        val displays: Array<Display> = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        if (displays.isNotEmpty()) {
            val external = displays[0]
            if (customerPresentation?.display?.displayId != external.displayId) {
                dismissPresentation()
                customerPresentation = CustomerPresentation(this, external, object : ToastCallback {
                    override fun showToast(message: String) {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }, object : PrinterSettingsCallback {
                    override fun launchPrinterSettings() {
                        val intent = android.content.Intent(this@MainActivity, PrinterSettingsActivity::class.java)
                        startActivity(intent)
                    }
                })
                customerPresentation?.show()
            }
        } else {
            dismissPresentation()
        }
    }

    private fun dismissPresentation() {
        try {
            customerPresentation?.dismiss()
        } catch (_: Throwable) { }
        customerPresentation = null
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }

    companion object {
        const val REQUEST_CODE_BLUETOOTH = 1001
    }
}