package com.blackcode.poscandykush

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit

class PurchaseApiService(private val baseUrl: String = "https://pos-candy-kush.vercel.app/api/mobile") {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // Get all purchases
    fun getPurchases(jwtToken: String): PurchaseListResponse? {
        return try {
            val request = Request.Builder()
                .url("$baseUrl?action=get-purchases")
                .get()
                .addHeader("Authorization", "Bearer $jwtToken")
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            android.util.Log.d("PurchaseApiService", "getPurchases response code: ${response.code}, body: $body")

            if (response.isSuccessful && body != null) {
                val purchaseResponse = gson.fromJson(body, PurchaseListResponse::class.java)
                android.util.Log.d("PurchaseApiService", "Successfully parsed purchase list response: ${purchaseResponse.success}")
                purchaseResponse
            } else {
                val errorMsg = "HTTP ${response.code}: ${response.message}"
                android.util.Log.e("PurchaseApiService", "API call unsuccessful: $errorMsg")
                PurchaseListResponse(success = false, error = errorMsg, data = null)
            }
        } catch (e: Exception) {
            android.util.Log.e("PurchaseApiService", "Exception in getPurchases", e)
            PurchaseListResponse(success = false, error = e.message ?: "Unknown error", data = null)
        }
    }

    // Get single purchase
    fun getPurchase(jwtToken: String, purchaseId: String): PurchaseResponse? {
        return try {
            val request = Request.Builder()
                .url("$baseUrl?action=get-purchase&id=$purchaseId")
                .get()
                .addHeader("Authorization", "Bearer $jwtToken")
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            android.util.Log.d("PurchaseApiService", "╔════════════════════════════════════════╗")
            android.util.Log.d("PurchaseApiService", "║   GET PURCHASE API CALL                ║")
            android.util.Log.d("PurchaseApiService", "╚════════════════════════════════════════╝")
            android.util.Log.d("PurchaseApiService", "Response code: ${response.code}")
            android.util.Log.d("PurchaseApiService", "Response body: $body")

            if (response.isSuccessful && body != null) {
                try {
                    val purchaseResponse = gson.fromJson(body, PurchaseResponse::class.java)
                    android.util.Log.d("PurchaseApiService", "✅ Successfully parsed: success=${purchaseResponse.success}")
                    android.util.Log.d("PurchaseApiService", "Purchase data exists: ${purchaseResponse.data != null}")
                    if (purchaseResponse.data != null) {
                        android.util.Log.d("PurchaseApiService", "Purchase ID: ${purchaseResponse.data.id}")
                        android.util.Log.d("PurchaseApiService", "Supplier: ${purchaseResponse.data.supplierName}")
                    }
                    purchaseResponse
                } catch (e: Exception) {
                    android.util.Log.e("PurchaseApiService", "❌ JSON parsing error", e)
                    android.util.Log.e("PurchaseApiService", "Failed to parse: $body")
                    PurchaseResponse(success = false, error = "Parsing error: ${e.message}", data = null)
                }
            } else {
                android.util.Log.e("PurchaseApiService", "API call unsuccessful: ${response.code}")
                PurchaseResponse(success = false, error = "HTTP ${response.code}: ${response.message}", data = null)
            }
        } catch (e: Exception) {
            android.util.Log.e("PurchaseApiService", "Exception in getPurchase", e)
            PurchaseResponse(success = false, error = e.message ?: "Unknown error", data = null)
        }
    }

    // Create purchase
    fun createPurchase(jwtToken: String, request: CreatePurchaseRequest): PurchaseResponse? {
        return try {
            val jsonBody = gson.toJson(request)
            val requestBody = jsonBody.toRequestBody(jsonMediaType)

            android.util.Log.d("PurchaseApiService", "Creating purchase with request: $jsonBody")

            val httpRequest = Request.Builder()
                .url(baseUrl)
                .post(requestBody)
                .addHeader("Authorization", "Bearer $jwtToken")
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(httpRequest).execute()
            val body = response.body?.string()

            android.util.Log.d("PurchaseApiService", "createPurchase response code: ${response.code}, body: $body")

            if (response.isSuccessful && body != null) {
                val purchaseResponse = gson.fromJson(body, PurchaseResponse::class.java)
                android.util.Log.d("PurchaseApiService", "Successfully created purchase: ${purchaseResponse.success}")
                purchaseResponse
            } else {
                val errorMsg = "HTTP ${response.code}: ${response.message}"
                android.util.Log.e("PurchaseApiService", "API call unsuccessful: $errorMsg")
                PurchaseResponse(success = false, error = errorMsg, data = null)
            }
        } catch (e: Exception) {
            android.util.Log.e("PurchaseApiService", "Exception in createPurchase", e)
            PurchaseResponse(success = false, error = e.message ?: "Unknown error", data = null)
        }
    }

    // Edit purchase
    fun editPurchase(jwtToken: String, request: EditPurchaseRequest): PurchaseResponse? {
        return try {
            val jsonBody = gson.toJson(request)
            val requestBody = jsonBody.toRequestBody(jsonMediaType)

            val httpRequest = Request.Builder()
                .url(baseUrl)
                .post(requestBody)
                .addHeader("Authorization", "Bearer $jwtToken")
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(httpRequest).execute()
            val body = response.body?.string()

            android.util.Log.d("PurchaseApiService", "editPurchase response code: ${response.code}")

            if (response.isSuccessful && body != null) {
                val purchaseResponse = gson.fromJson(body, PurchaseResponse::class.java)
                android.util.Log.d("PurchaseApiService", "Successfully edited purchase: ${purchaseResponse.success}")
                purchaseResponse
            } else {
                android.util.Log.e("PurchaseApiService", "API call unsuccessful: ${response.code}")
                PurchaseResponse(success = false, error = "HTTP ${response.code}: ${response.message}", data = null)
            }
        } catch (e: Exception) {
            android.util.Log.e("PurchaseApiService", "Exception in editPurchase", e)
            PurchaseResponse(success = false, error = e.message ?: "Unknown error", data = null)
        }
    }

    // Delete purchase
    fun deletePurchase(jwtToken: String, purchaseId: String): PurchaseResponse? {
        return try {
            val request = Request.Builder()
                .url("$baseUrl?action=delete-purchase&id=$purchaseId")
                .delete()
                .addHeader("Authorization", "Bearer $jwtToken")
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            android.util.Log.d("PurchaseApiService", "deletePurchase response code: ${response.code}")

            if (response.isSuccessful && body != null) {
                val purchaseResponse = gson.fromJson(body, PurchaseResponse::class.java)
                android.util.Log.d("PurchaseApiService", "Successfully deleted purchase: ${purchaseResponse.success}")
                purchaseResponse
            } else {
                android.util.Log.e("PurchaseApiService", "API call unsuccessful: ${response.code}")
                PurchaseResponse(success = false, error = "HTTP ${response.code}: ${response.message}", data = null)
            }
        } catch (e: Exception) {
            android.util.Log.e("PurchaseApiService", "Exception in deletePurchase", e)
            PurchaseResponse(success = false, error = e.message ?: "Unknown error", data = null)
        }
    }

    // Mark purchase as complete
    fun completePurchase(jwtToken: String, purchaseId: String): PurchaseResponse? {
        return try {
            val request = CompletePurchaseRequest(id = purchaseId)
            val jsonBody = gson.toJson(request)
            val requestBody = jsonBody.toRequestBody(jsonMediaType)

            android.util.Log.d("PurchaseApiService", "")
            android.util.Log.d("PurchaseApiService", "╔════════════════════════════════════════╗")
            android.util.Log.d("PurchaseApiService", "║   COMPLETE PURCHASE REQUEST            ║")
            android.util.Log.d("PurchaseApiService", "╚════════════════════════════════════════╝")
            android.util.Log.d("PurchaseApiService", "Purchase ID: $purchaseId")
            android.util.Log.d("PurchaseApiService", "Request body: $jsonBody")

            val httpRequest = Request.Builder()
                .url(baseUrl)
                .post(requestBody)
                .addHeader("Authorization", "Bearer $jwtToken")
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(httpRequest).execute()
            val body = response.body?.string()

            android.util.Log.d("PurchaseApiService", "")
            android.util.Log.d("PurchaseApiService", "╔════════════════════════════════════════╗")
            android.util.Log.d("PurchaseApiService", "║   COMPLETE PURCHASE RESPONSE           ║")
            android.util.Log.d("PurchaseApiService", "╚════════════════════════════════════════╝")
            android.util.Log.d("PurchaseApiService", "Response code: ${response.code}")
            android.util.Log.d("PurchaseApiService", "Response body: $body")

            if (response.isSuccessful && body != null) {
                val purchaseResponse = gson.fromJson(body, PurchaseResponse::class.java)
                android.util.Log.d("PurchaseApiService", "✅ Successfully completed purchase: ${purchaseResponse.success}")
                android.util.Log.d("PurchaseApiService", "Returned purchase status: ${purchaseResponse.data?.status}")
                purchaseResponse
            } else {
                android.util.Log.e("PurchaseApiService", "❌ API call unsuccessful: ${response.code}")
                PurchaseResponse(success = false, error = "HTTP ${response.code}: ${response.message}", data = null)
            }
        } catch (e: Exception) {
            android.util.Log.e("PurchaseApiService", "❌ Exception in completePurchase", e)
            PurchaseResponse(success = false, error = e.message ?: "Unknown error", data = null)
        }
    }
}

// API Response wrappers
data class PurchaseListResponse(
    val success: Boolean,
    val data: PurchaseListData?,
    val error: String?
)

data class PurchaseListData(
    val purchases: List<Purchase>
)

data class PurchaseResponse(
    val success: Boolean,
    val action: String? = null,
    @SerializedName("generated_at") val generatedAt: String? = null,
    val data: Purchase?, // FIXED: Purchase is directly in data, not nested
    val error: String?
)


// Request models
data class CreatePurchaseRequest(
    val action: String = "create-purchase",
    @SerializedName("supplier_name") val supplierName: String,
    @SerializedName("purchase_date") val purchaseDate: String,
    @SerializedName("due_date") val dueDate: String,
    val items: List<PurchaseItem>,
    val total: Double,
    @SerializedName("reminder_type") val reminderType: String? = null,
    @SerializedName("reminder_value") val reminderValue: String? = null,
    @SerializedName("reminder_time") val reminderTime: String? = null
)

data class EditPurchaseRequest(
    val action: String = "edit-purchase",
    @SerializedName("purchase_id") val purchaseId: String,
    @SerializedName("supplier_name") val supplierName: String? = null,
    @SerializedName("purchase_date") val purchaseDate: String? = null,
    @SerializedName("due_date") val dueDate: String? = null,
    val items: List<PurchaseItem>? = null,
    val total: Double? = null,
    @SerializedName("reminder_type") val reminderType: String? = null,
    @SerializedName("reminder_value") val reminderValue: String? = null,
    @SerializedName("reminder_time") val reminderTime: String? = null
)

data class CompletePurchaseRequest(
    val action: String = "complete-purchase",
    val id: String
)

