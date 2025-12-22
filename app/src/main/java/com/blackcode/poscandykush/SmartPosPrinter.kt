package com.blackcode.poscandykush

import android.util.Log
import com.paydevice.smartpos.sdk.printer.PrinterManager
import com.paydevice.smartpos.sdk.SmartPosException

/**
 * SmartPos SDK Printer Wrapper
 * Handles thermal printing using PayDevice SmartPos SDK
 *
 * SDK Features:
 * - Serial Printers (Built-in): PRN2103, FH070H-A, FH100H-A, FH100-A3-D
 * - USB Printers: External USB-connected thermal printers
 * - Supports text, bitmap, QR code, and barcode printing
 * - Multiple encodings: CP437, GB18030, CP1251, etc.
 */
open class SmartPosPrinter(private val printerManager: PrinterManager?) {

    companion object {
        private const val TAG = "SmartPosPrinter"

        // Printer connection types
        const val PRINTER_TYPE_SERIAL = 0
        const val PRINTER_TYPE_USB = 1
    }

    private var isInitialized = false
    private var printerManagerInstance: PrinterManager? = printerManager

    /**
     * Initialize the SmartPos SDK printer
     * @param printerType Either PRINTER_TYPE_SERIAL or PRINTER_TYPE_USB
     * @return true if initialization successful
     */
    open fun initialize(printerType: Int = PRINTER_TYPE_SERIAL): Boolean {
        if (printerManagerInstance == null) {
            Log.w(TAG, "SmartPos SDK not available")
            return false
        }

        return try {
            Log.d(TAG, "Initializing SmartPos SDK - Type: $printerType")

            // Connect to printer
            printerManagerInstance?.connect()

            isInitialized = true
            Log.d(TAG, "SmartPos SDK initialized successfully")
            true
        } catch (e: SmartPosException) {
            Log.e(TAG, "SmartPos SDK exception: ${e.message}", e)
            isInitialized = false
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SmartPos SDK", e)
            isInitialized = false
            false
        }
    }

    /**
     * Print receipt using SmartPos SDK
     * @param cartItems List of items to print
     * @param total Total amount
     * @param timestamp Receipt timestamp
     * @throws SmartPosException if printing fails
     * @throws Exception if other errors occur
     */
    open fun printReceipt(cartItems: List<CartItem>, total: Double, timestamp: String) {
        if (!isInitialized || printerManagerInstance == null) {
            throw IllegalStateException("SmartPos SDK not initialized or not available")
        }

        try {
            printerManagerInstance?.let { pm ->
                Log.d(TAG, "Printing receipt via SmartPos SDK")

                // Set alignment to center for header
                pm.cmdSetAlignMode(PrinterManager.ALIGN_MIDDLE)

                // Print store name with double height/width
                pm.cmdSetPrintMode(PrinterManager.FONT_DOUBLE_HEIGHT or PrinterManager.FONT_DOUBLE_WIDTH or PrinterManager.FONT_EMPHASIZED)
                pm.sendData("CANDY KUSH\n")
                pm.cmdLineFeed()

                // Reset to normal font and left alignment
                pm.cmdSetPrintMode(PrinterManager.FONT_DEFAULT)
                pm.cmdSetAlignMode(PrinterManager.ALIGN_LEFT)

                // Print separator
                pm.sendData("================================\n\n")

                // Print column headers
                pm.sendData(String.format("%-20s %4s %6s\n", "ITEM", "QTY", "TOTAL"))
                pm.sendData("--------------------------------\n")

                // Print items
                for (item in cartItems) {
                    val itemName = if (item.name.length > 20) item.name.substring(0, 17) + "..." else item.name
                    val quantity = String.format("%.2f", item.quantity)
                    val itemTotal = String.format("%.2f", item.total)
                    pm.sendData(String.format("%-20s %4s %6s\n", itemName, quantity, itemTotal))
                }

                // Print total
                pm.sendData("--------------------------------\n")
                val totalText = String.format("%.2f", total)
                pm.sendData(String.format("%20s: %s\n", "TOTAL", totalText))
                pm.cmdLineFeed()

                // Print footer
                pm.cmdSetAlignMode(PrinterManager.ALIGN_MIDDLE)
                pm.sendData("Thank you!\n")
                pm.sendData("$timestamp\n")
                pm.sendData("================================\n")
                pm.cmdLineFeed(3)

                // Cut paper if supported
                try {
                    if (pm.getPrinterType() == PrinterManager.PRINTER_TYPE_USB) {
                        pm.cmdCutPaper(PrinterManager.FULL_CUT)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Paper cut not supported or failed: ${e.message}")
                }

                Log.d(TAG, "Receipt printed successfully via SmartPos SDK")
            } ?: throw IllegalStateException("PrinterManager is null")
        } catch (e: SmartPosException) {
            Log.e(TAG, "SmartPos SDK print failed: ${e.message}", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "SmartPos SDK print failed", e)
            throw e
        }
    }

    /**
     * Print raw data using SmartPos SDK
     * @param data Raw byte data to print
     */
    open fun printRawData(data: ByteArray): Boolean {
        if (!isInitialized || printerManagerInstance == null) {
            Log.e(TAG, "SmartPos SDK not initialized or not available")
            return false
        }

        return try {
            printerManagerInstance?.let { pm ->
                Log.d(TAG, "Printing raw data via SmartPos SDK")
                pm.sendData(String(data))
                Log.d(TAG, "Raw data printed successfully")
                true
            } ?: run {
                Log.e(TAG, "PrinterManager is null")
                false
            }
        } catch (e: SmartPosException) {
            Log.e(TAG, "SmartPos SDK raw print failed: ${e.message}", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "SmartPos SDK raw print failed", e)
            false
        }
    }

    /**
     * Check if printer is ready
     */
    open fun isPrinterReady(): Boolean {
        return try {
            if (!isInitialized || printerManagerInstance == null) return false
            // SmartPos SDK doesn't have a direct status check, assume ready if initialized
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check printer status", e)
            false
        }
    }

    /**
     * Release printer resources
     */
    open fun release() {
        try {
            printerManagerInstance?.let { pm ->
                // Disconnect with power control for serial printers
                if (pm.getPrinterType() == PrinterManager.PRINTER_TYPE_SERIAL) {
                    pm.disconnect(true) // Turn off printer power
                } else {
                    pm.disconnect()
                }
                Log.d(TAG, "SmartPos SDK printer disconnected")
            }
            printerManagerInstance = null
            isInitialized = false
            Log.d(TAG, "SmartPos SDK released")
        } catch (e: SmartPosException) {
            Log.e(TAG, "Failed to release SmartPos SDK: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release SmartPos SDK", e)
        }
    }

    /**
     * Test print to verify printer functionality
     */
    open fun testPrint(): Boolean {
        if (!isInitialized || printerManagerInstance == null) {
            Log.e(TAG, "SmartPos SDK not initialized or not available")
            return false
        }

        return try {
            printerManagerInstance?.let { pm ->
                Log.d(TAG, "Printing test via SmartPos SDK")

                pm.cmdSetAlignMode(PrinterManager.ALIGN_MIDDLE)
                pm.cmdSetPrintMode(PrinterManager.FONT_DOUBLE_HEIGHT or PrinterManager.FONT_DOUBLE_WIDTH)
                pm.sendData("Test Print\n")
                pm.cmdSetPrintMode(PrinterManager.FONT_DEFAULT)
                pm.sendData("CANDY KUSH\n")
                pm.sendData("Test successful\n")
                pm.cmdLineFeed(3)

                try {
                    if (pm.getPrinterType() == PrinterManager.PRINTER_TYPE_USB) {
                        pm.cmdCutPaper(PrinterManager.FULL_CUT)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Paper cut not supported: ${e.message}")
                }

                Log.d(TAG, "Test print completed successfully")
                true
            } ?: run {
                Log.e(TAG, "PrinterManager is null")
                false
            }
        } catch (e: SmartPosException) {
            Log.e(TAG, "SmartPos SDK test print failed: ${e.message}", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "SmartPos SDK test print failed", e)
            false
        }
    }
}
