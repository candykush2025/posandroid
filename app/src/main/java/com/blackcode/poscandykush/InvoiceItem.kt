package com.blackcode.poscandykush

import com.google.gson.annotations.SerializedName

data class InvoiceItem(
    val id: String? = null,
    @SerializedName("product_id") val productId: String,
    @SerializedName("product_name") val productName: String,
    val sku: String? = null,
    var quantity: Double,
    @SerializedName("unit_price") val unitPrice: Double,
    val discount: Double = 0.0,
    @SerializedName("tax_rate") val taxRate: Double = 0.0,
    @SerializedName("tax_amount") val taxAmount: Double = 0.0,
    var total: Double,
    @SerializedName("category_name") val categoryName: String? = null
) {
    // Backward compatibility
    val price: Double
        get() = unitPrice

    fun getFormattedTotal(): String {
        return NumberFormatter.formatCurrency(total)
    }
}
