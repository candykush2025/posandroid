package com.blackcode.poscandykush

import com.google.gson.annotations.SerializedName

data class Product(
    val id: String,
    @SerializedName("product_id") val productId: String = "",
    val name: String,
    val description: String = "",
    val sku: String = "",
    @SerializedName("category_id") val categoryId: String? = null,
    @SerializedName("category_name") val categoryName: String? = null,
    @SerializedName("category_image") val categoryImage: String? = null,
    val price: Double,
    val cost: Double = 0.0,
    val stock: Int = 0,
    @SerializedName("track_stock") val trackStock: Boolean = true,
    @SerializedName("low_stock_threshold") val lowStockThreshold: Int = 10,
    @SerializedName("is_active") val isActive: Boolean = true,
    @SerializedName("available_for_sale") val availableForSale: Boolean = true,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null
) {
    // Backward compatibility with old category field
    val category: String?
        get() = categoryName ?: "Uncategorized"
}

data class Category(
    val id: String,
    @SerializedName("category_id") val categoryId: String,
    val name: String,
    val description: String = "",
    val image: String? = null,
    val color: String? = null,
    val icon: String? = null,
    @SerializedName("is_active") val isActive: Boolean = true,
    @SerializedName("sort_order") val sortOrder: Int = 0,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null
)
