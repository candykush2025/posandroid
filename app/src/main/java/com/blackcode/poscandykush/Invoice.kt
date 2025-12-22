package com.blackcode.poscandykush

import com.google.gson.annotations.SerializedName

data class Invoice(
    val id: String,
    @SerializedName("invoice_number") val invoiceNumber: String = "",
    @SerializedName("customer_id") val customerId: String? = null,
    @SerializedName("customer_name") val customerName: String,
    @SerializedName("customer_email") val customerEmail: String? = null,
    @SerializedName("customer_phone") val customerPhone: String? = null,
    @SerializedName("invoice_date") val invoiceDate: String,
    @SerializedName("due_date") val dueDate: String?,
    val status: String = "pending", // pending, paid, cancelled
    @SerializedName("payment_method") val paymentMethod: String? = null,
    val subtotal: Double = 0.0,
    @SerializedName("tax_amount") val taxAmount: Double = 0.0,
    @SerializedName("discount_amount") val discountAmount: Double = 0.0,
    val total: Double,
    @SerializedName("paid_amount") val paidAmount: Double = 0.0,
    @SerializedName("balance_due") val balanceDue: Double = 0.0,
    val notes: String? = null,
    val items: List<InvoiceItem>,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?
) {
    // Backward compatibility
    val number: String
        get() = invoiceNumber

    val date: String
        get() = invoiceDate

    fun getItemsCount(): Int {
        return items.size
    }

    fun getFormattedTotal(): String {
        return NumberFormatter.formatCurrency(total)
    }

    fun getStatusText(): String {
        return when (status.lowercase()) {
            "paid" -> "Paid"
            "pending" -> "Pending"
            "cancelled" -> "Cancelled"
            else -> status
        }
    }

    fun getPaymentStatusText(): String {
        return when {
            balanceDue <= 0 && paidAmount > 0 -> "Paid"
            paidAmount > 0 && balanceDue > 0 -> "Partially Paid"
            balanceDue > 0 -> "Pending Payment"
            else -> "Pending"
        }
    }
}
