package com.blackcode.poscandykush

import com.google.gson.annotations.SerializedName

data class Expense(
    val id: String, // API returns "id" directly, not "expense_id"
    val description: String?,
    val amount: Double,
    val date: String?,
    val time: String?,
    val createdAt: String? = null, // API returns "createdAt" directly
    @SerializedName("updated_at") val updatedAt: String? = null
) {
    fun getFormattedAmount(): String {
        return NumberFormatter.formatCurrency(amount)
    }

    fun getDateTime(): String {
        return "${date ?: "No date"} ${time ?: "No time"}"
    }
}
