package com.blackcode.poscandykush

import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale

/**
 * Utility class for formatting numbers with thousand separators
 */
object NumberFormatter {

    private val currencyFormat = DecimalFormat("#,##0.00")
    private val integerFormat = DecimalFormat("#,##0")
    private val percentFormat = DecimalFormat("#,##0.0")

    /**
     * Format currency value with thousand separator
     * @param value - The numeric value
     * @param prefix - Currency symbol prefix (default "฿")
     * @return Formatted string like "฿1,234.56"
     */
    fun formatCurrency(value: Double, prefix: String = "฿"): String {
        return "$prefix${currencyFormat.format(value)}"
    }

    /**
     * Format integer with thousand separator
     * @param value - The integer value
     * @return Formatted string like "1,234"
     */
    fun formatInteger(value: Int): String {
        return integerFormat.format(value)
    }

    /**
     * Format integer with thousand separator
     * @param value - The long value
     * @return Formatted string like "1,234,567"
     */
    fun formatInteger(value: Long): String {
        return integerFormat.format(value)
    }

    /**
     * Format quantity with prefix
     * @param value - The quantity value
     * @param prefix - Prefix string (default "x")
     * @return Formatted string like "x1,234"
     */
    fun formatQuantity(value: Int, prefix: String = "x"): String {
        return "$prefix${integerFormat.format(value)}"
    }

    /**
     * Format percentage
     * @param value - The percentage value (0-100)
     * @param suffix - Suffix string (default "%")
     * @return Formatted string like "12.5%"
     */
    fun formatPercent(value: Double, suffix: String = "%"): String {
        return "${percentFormat.format(value)}$suffix"
    }

    /**
     * Format double with thousand separator
     * @param value - The double value
     * @param decimals - Number of decimal places
     * @return Formatted string
     */
    fun formatDouble(value: Double, decimals: Int = 2): String {
        val pattern = if (decimals > 0) {
            "#,##0.${"0".repeat(decimals)}"
        } else {
            "#,##0"
        }
        val formatter = DecimalFormat(pattern)
        return formatter.format(value)
    }
}
