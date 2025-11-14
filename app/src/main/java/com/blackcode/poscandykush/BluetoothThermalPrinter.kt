package com.blackcode.poscandykush

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import java.io.IOException
import java.io.OutputStream
import java.util.*

class BluetoothThermalPrinter(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    companion object {
        private const val TAG = "BluetoothThermalPrinter"
        // Standard UUID for Bluetooth Serial Port Profile
        private val UUID_SERIAL_PORT = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(deviceName: String): Boolean {
        return try {
            if (bluetoothAdapter == null) {
                Log.e(TAG, "Bluetooth not supported on this device")
                return false
            }

            val pairedDevices: Set<BluetoothDevice> = bluetoothAdapter.bondedDevices
            var targetDevice: BluetoothDevice? = null

            for (device in pairedDevices) {
                if (device.name == deviceName) {
                    targetDevice = device
                    break
                }
            }

            if (targetDevice == null) {
                Log.e(TAG, "Device not found: $deviceName")
                return false
            }

            bluetoothSocket = targetDevice.createRfcommSocketToServiceRecord(UUID_SERIAL_PORT)
            bluetoothSocket?.connect()
            outputStream = bluetoothSocket?.outputStream
            Log.d(TAG, "Connected to $deviceName")
            true
        } catch (e: IOException) {
            Log.e(TAG, "Connection failed", e)
            false
        }
    }

    fun disconnect() {
        try {
            outputStream?.close()
            bluetoothSocket?.close()
            Log.d(TAG, "Disconnected from printer")
        } catch (e: IOException) {
            Log.e(TAG, "Disconnect error", e)
        }
    }

    fun sendData(data: ByteArray): Boolean {
        return try {
            outputStream?.write(data)
            outputStream?.flush()
            Log.d(TAG, "Data sent successfully")
            true
        } catch (e: IOException) {
            Log.e(TAG, "Send data failed", e)
            false
        }
    }

    fun printReceipt(cartItems: List<CartItem>, total: Double, cartLastUpdated: String) {
        try {
            val receiptData = buildReceiptData(cartItems, total, cartLastUpdated)
            sendData(receiptData)
        } catch (e: Exception) {
            Log.e(TAG, "Print receipt failed", e)
        }
    }

    private fun buildReceiptData(cartItems: List<CartItem>, total: Double, cartLastUpdated: String): ByteArray {
        val sb = StringBuilder()

        // Title
        sb.append(centerText("CANDY KUSH", 32)).append("\n")
        sb.append("=".repeat(32)).append("\n\n")

        // Items header
        sb.append(String.format("%-20s %8s %s\n", "ITEM", "QTY", "TOTAL"))
        sb.append("-".repeat(32)).append("\n")

        // Items
        for (item in cartItems) {
            val itemName = if (item.name.length > 20) item.name.substring(0, 17) + "..." else item.name
            val quantity = String.format("%.2f", item.quantity)
            val itemTotal = String.format("฿%.2f", item.total)
            sb.append(String.format("%-20s %8s %s\n", itemName, quantity, itemTotal))
        }

        sb.append("-".repeat(32)).append("\n")

        // Total
        val totalText = String.format("฿%.2f", total)
        sb.append(String.format("%24s %s\n", "TOTAL:", totalText))

        // Footer
        sb.append("\n")
        sb.append(centerText("Thank you!", 32)).append("\n")
        sb.append(centerText(cartLastUpdated, 32)).append("\n")
        sb.append("=".repeat(32)).append("\n\n\n")

        // Cut paper
        sb.append("\u001B\u0069") // ESC i - partial cut

        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    private fun centerText(text: String, width: Int): String {
        return if (text.length >= width) {
            text.substring(0, width)
        } else {
            val padding = (width - text.length) / 2
            " ".repeat(padding) + text
        }
    }

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<String> {
        val devices = mutableListOf<String>()
        try {
            bluetoothAdapter?.bondedDevices?.forEach { device ->
                devices.add(device.name)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting paired devices", e)
        }
        return devices
    }

    fun isConnected(): Boolean {
        return bluetoothSocket?.isConnected == true
    }
}

