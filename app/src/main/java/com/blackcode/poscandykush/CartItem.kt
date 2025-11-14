package com.blackcode.poscandykush

data class CartItem(
    val id: String,
    val productId: String,
    val name: String,
    val quantity: Double,
    val price: Double,
    val total: Double,
    val weight: Double? = null,
    val unit: String? = null,
    val variantId: String? = null,
    val originalPrice: Double? = null,
    val memberPrice: Double? = null,
    val source: String? = null,
    val discount: Double? = null,
    val barcode: String? = null,
    val sku: String? = null,
    val cost: Double? = null,
    val soldBy: String? = null
)

data class Discount(
    val type: String,
    val value: Double
)

data class Tax(
    val rate: Double,
    val amount: Double
)

data class Customer(
    val id: String? = null,
    val name: String? = null,
    val phone: String? = null
)

data class Cart(
    val items: List<CartItem>,
    val discount: Discount,
    val tax: Tax,
    val customer: Customer? = null,
    val notes: String? = null,
    val total: Double,
    val lastUpdated: String
)

data class CartResponse(
    val success: Boolean,
    val cart: Cart? = null,
    val error: String? = null,
    val timestamp: String
)

data class PaymentStatus(
    val status: String,
    val timestamp: String,
    val amount: Double? = null,
    val method: String? = null,
    val transactionId: String? = null
)

data class PaymentStatusResponse(
    val success: Boolean,
    val paymentStatus: PaymentStatus? = null
)