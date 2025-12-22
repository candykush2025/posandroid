package com.blackcode.poscandykush

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class CategoryApiService(private val baseUrl: String = "https://pos-candy-kush.vercel.app/api/mobile") {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    // Get all categories
    fun getCategories(jwtToken: String): CategoryListResponse? {
        return try {
            val request = Request.Builder()
                .url("$baseUrl?action=get-categories")
                .get()
                .addHeader("Authorization", "Bearer $jwtToken")
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            android.util.Log.d("CategoryApiService", "getCategories response code: ${response.code}, body: $body")

            if (response.isSuccessful && body != null) {
                val categoryResponse = gson.fromJson(body, CategoryListResponse::class.java)
                android.util.Log.d("CategoryApiService", "Successfully parsed category list response: ${categoryResponse.success}")
                categoryResponse
            } else {
                val errorMsg = "HTTP ${response.code}: ${response.message}"
                android.util.Log.e("CategoryApiService", "API call unsuccessful: $errorMsg")
                CategoryListResponse(success = false, error = errorMsg, data = null)
            }
        } catch (e: Exception) {
            android.util.Log.e("CategoryApiService", "Exception in getCategories", e)
            CategoryListResponse(success = false, error = e.message ?: "Unknown error", data = null)
        }
    }
}

// Response models
data class CategoryListResponse(
    val success: Boolean,
    val action: String? = null,
    @SerializedName("generated_at") val generatedAt: String? = null,
    val data: CategoryListData? = null,
    val error: String? = null
)

data class CategoryListData(
    val categories: List<Category>,
    @SerializedName("total_count") val totalCount: Int = 0,
    @SerializedName("generated_at") val generatedAt: String? = null
)

