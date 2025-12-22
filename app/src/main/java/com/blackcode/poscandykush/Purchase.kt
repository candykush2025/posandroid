package com.blackcode.poscandykush

import com.google.gson.annotations.SerializedName

data class Purchase(
    val id: String,
    @SerializedName("supplier_name") val supplierName: String,
    @SerializedName("purchase_date") val purchaseDate: String,
    @SerializedName("due_date") val dueDate: String,
    val items: List<PurchaseItem>,
    val total: Double,
    val status: String = "pending", // pending, completed
    @SerializedName("reminder_type") val reminderType: String? = null, // "days_before" or "specific_date"
    @SerializedName("reminder_value") val reminderValue: String? = null, // Number of days or date/time
    @SerializedName("reminder_time") val reminderTime: String? = null, // Time for notification (HH:mm)
    val createdAt: String? = null, // API sends "createdAt" directly
    @SerializedName("updated_at") val updatedAt: String? = null
) {
    // Backward compatibility
    val date: String
        get() = purchaseDate

    fun getFormattedTotal(): String {
        return NumberFormatter.formatCurrency(total)
    }

    fun getStatusText(): String {
        return when (status) {
            "completed" -> "Completed"
            "pending" -> "Pending"
            else -> status
        }
    }
}

data class PurchaseItem(
    @SerializedName("product_id") val productId: String,
    @SerializedName("product_name") val productName: String,
    var quantity: Double,
    var price: Double,
    var total: Double
) {
    fun getFormattedTotal(): String {
        return NumberFormatter.formatCurrency(total)
    }
}
