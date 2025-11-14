package com.blackcode.poscandykush

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class PrinterSettingsActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var saveButton: Button
    private lateinit var prefs: SharedPreferences
    private var selectedPrinter: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_printer_settings)

        prefs = getSharedPreferences("printer_prefs", Context.MODE_PRIVATE)

        listView = findViewById(R.id.printer_list)
        saveButton = findViewById(R.id.save_button)

        loadAvailablePrinters()

        saveButton.setOnClickListener {
            if (selectedPrinter != null) {
                prefs.edit().putString("selected_printer", selectedPrinter).apply()
                Toast.makeText(this, "Printer saved: $selectedPrinter", Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_OK)
                finish()
            } else {
                Toast.makeText(this, "Please select a printer", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadAvailablePrinters() {
        val printers = mutableListOf<String>()

        // Add Bluetooth devices
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
            val bondedDevices = bluetoothAdapter.bondedDevices
            for (device in bondedDevices) {
                printers.add("Bluetooth: ${device.name}")
            }
        }

        // For wired printers, assuming USB or network, but for simplicity, add a placeholder
        // In a real app, you'd scan for USB devices or network printers
        printers.add("Wired: Default USB Printer") // Placeholder

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, printers)
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_SINGLE

        listView.setOnItemClickListener { _, _, position, _ ->
            selectedPrinter = printers[position]
        }

        // Load previously selected
        val saved = prefs.getString("selected_printer", null)
        if (saved != null) {
            val index = printers.indexOf(saved)
            if (index >= 0) {
                listView.setItemChecked(index, true)
                selectedPrinter = saved
            }
        }
    }
}
