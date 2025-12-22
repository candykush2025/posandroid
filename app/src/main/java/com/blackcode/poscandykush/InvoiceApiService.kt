package com.blackcode.poscandykush

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit

class InvoiceApiService(private val baseUrl: String = "https://pos-candy-kush.vercel.app/api/mobile") {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // Get all invoices
    fun getInvoices(jwtToken: String): InvoiceListResponse? {
        return try {
            val request = Request.Builder()
                .url("$baseUrl?action=get-invoices")
                .get()
                .addHeader("Authorization", "Bearer $jwtToken")
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            android.util.Log.d("InvoiceApiService", "getInvoices response code: ${response.code}, body: $body")

            if (response.isSuccessful && body != null) {
                val invoiceResponse = gson.fromJson(body, InvoiceListResponse::class.java)
                android.util.Log.d("InvoiceApiService", "Successfully parsed invoice list response: ${invoiceResponse.success}, invoices count: ${invoiceResponse.data?.invoices?.size ?: 0}")
                android.util.Log.d("InvoiceApiService", "Parsed response object: success=${invoiceResponse.success}, data=${invoiceResponse.data}, error=${invoiceResponse.error}")
                if (invoiceResponse.data?.invoices?.isNotEmpty() == true) {
                    android.util.Log.d("InvoiceApiService", "First invoice: ${invoiceResponse.data.invoices[0]}")
                }
                invoiceResponse
            } else {
                val errorMsg = "HTTP ${response.code}: ${response.message}"
                android.util.Log.e("InvoiceApiService", "API call unsuccessful: $errorMsg, body: $body")
                InvoiceListResponse(success = false, error = errorMsg, data = null, action = null, generated_at = null)
            }
        } catch (e: Exception) {
            android.util.Log.e("InvoiceApiService", "Exception in getInvoices", e)
            InvoiceListResponse(success = false, error = e.message ?: "Unknown error", data = null, action = null, generated_at = null)
        }
    }

    // Get single invoice
    fun getInvoice(jwtToken: String, invoiceId: String): InvoiceResponse? {
        return try {
            val request = Request.Builder()
                .url("$baseUrl?action=get-invoice&id=$invoiceId")
                .get()
                .addHeader("Authorization", "Bearer $jwtToken")
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            android.util.Log.d("InvoiceApiService", "getInvoice response code: ${response.code}, body: $body")

            if (response.isSuccessful && body != null) {
                val invoiceResponse = gson.fromJson(body, InvoiceResponse::class.java)
                android.util.Log.d("InvoiceApiService", "Successfully parsed single invoice response: ${invoiceResponse.success}")
                invoiceResponse
            } else {
                android.util.Log.e("InvoiceApiService", "API call unsuccessful: ${response.code}")
                InvoiceResponse(success = false, error = "HTTP ${response.code}: ${response.message}", data = null)
            }
        } catch (e: Exception) {
            android.util.Log.e("InvoiceApiService", "Exception in getInvoice", e)
            InvoiceResponse(success = false, error = e.message ?: "Unknown error", data = null)
        }
    }

    // Create invoice
    fun createInvoice(jwtToken: String, request: CreateInvoiceRequest): InvoiceResponse? {
        return try {
            val jsonBody = gson.toJson(request)
            val requestBody = jsonBody.toRequestBody(jsonMediaType)

            android.util.Log.d("InvoiceApiService", "Creating invoice with request: $jsonBody")

            val httpRequest = Request.Builder()
                .url(baseUrl)
                .post(requestBody)
                .addHeader("Authorization", "Bearer $jwtToken")
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(httpRequest).execute()
            val body = response.body?.string()

            android.util.Log.d("InvoiceApiService", "createInvoice response code: ${response.code}, body: $body")

            if (response.isSuccessful && body != null) {
                val invoiceResponse = gson.fromJson(body, InvoiceResponse::class.java)
                android.util.Log.d("InvoiceApiService", "Successfully parsed create invoice response: ${invoiceResponse.success}")
                invoiceResponse
            } else {
                val errorMsg = "HTTP ${response.code}: ${response.message}"
                android.util.Log.e("InvoiceApiService", "API call unsuccessful: $errorMsg, body: $body")
                InvoiceResponse(success = false, error = errorMsg, data = null)
            }
        } catch (e: Exception) {
            android.util.Log.e("InvoiceApiService", "Exception in createInvoice", e)
            InvoiceResponse(success = false, error = e.message ?: "Unknown error", data = null)
        }
    }

    // Edit invoice
    fun editInvoice(jwtToken: String, request: EditInvoiceRequest): InvoiceResponse? {
        return try {
            val jsonBody = gson.toJson(request)
            val requestBody = jsonBody.toRequestBody(jsonMediaType)

            android.util.Log.d("InvoiceApiService", "Editing invoice with request: $jsonBody")

            val httpRequest = Request.Builder()
                .url(baseUrl)
                .post(requestBody)
                .addHeader("Authorization", "Bearer $jwtToken")
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(httpRequest).execute()
            val body = response.body?.string()

            android.util.Log.d("InvoiceApiService", "editInvoice response code: ${response.code}, body: $body")

            if (response.isSuccessful && body != null) {
                val invoiceResponse = gson.fromJson(body, InvoiceResponse::class.java)
                android.util.Log.d("InvoiceApiService", "Successfully parsed edit invoice response: ${invoiceResponse.success}")
                invoiceResponse
            } else {
                val errorMsg = "HTTP ${response.code}: ${response.message}"
                android.util.Log.e("InvoiceApiService", "API call unsuccessful: $errorMsg")
                InvoiceResponse(success = false, error = errorMsg, data = null)
            }
        } catch (e: Exception) {
            android.util.Log.e("InvoiceApiService", "Exception in editInvoice", e)
            InvoiceResponse(success = false, error = e.message ?: "Unknown error", data = null)
        }
    }

    // Delete invoice
    fun deleteInvoice(jwtToken: String, invoiceId: String): InvoiceResponse? {
        return try {
            val request = Request.Builder()
                .url("$baseUrl?action=delete-invoice&id=$invoiceId")
                .delete()
                .addHeader("Authorization", "Bearer $jwtToken")
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            android.util.Log.d("InvoiceApiService", "deleteInvoice response code: ${response.code}, body: $body")

            if (response.isSuccessful && body != null) {
                val invoiceResponse = gson.fromJson(body, InvoiceResponse::class.java)
                android.util.Log.d("InvoiceApiService", "Successfully deleted invoice: ${invoiceResponse.success}")
                invoiceResponse
            } else {
                android.util.Log.e("InvoiceApiService", "API call unsuccessful: ${response.code}")
                InvoiceResponse(success = false, error = "HTTP ${response.code}: ${response.message}", data = null)
            }
        } catch (e: Exception) {
            android.util.Log.e("InvoiceApiService", "Exception in deleteInvoice", e)
            InvoiceResponse(success = false, error = e.message ?: "Unknown error", data = null)
        }
    }

    // Update invoice status (paid, pending, cancelled)
    fun updateInvoiceStatus(jwtToken: String, invoiceId: String, status: String): InvoiceResponse? {
        return try {
            val requestData = UpdateInvoiceStatusRequest(
                invoiceId = invoiceId,
                status = status
            )

            val jsonBody = gson.toJson(requestData)
            val requestBody = jsonBody.toRequestBody(jsonMediaType)

            android.util.Log.d("InvoiceApiService", "Updating invoice status with request: $jsonBody")

            val request = Request.Builder()
                .url(baseUrl)
                .post(requestBody)
                .addHeader("Authorization", "Bearer $jwtToken")
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            android.util.Log.d("InvoiceApiService", "updateInvoiceStatus response code: ${response.code}, body: $body")

            if (response.isSuccessful && body != null) {
                val invoiceResponse = gson.fromJson(body, InvoiceResponse::class.java)
                android.util.Log.d("InvoiceApiService", "Successfully updated invoice status: ${invoiceResponse.success}")
                invoiceResponse
            } else {
                android.util.Log.e("InvoiceApiService", "API call unsuccessful: ${response.code}")
                InvoiceResponse(success = false, error = "HTTP ${response.code}: ${response.message}", data = null)
            }
        } catch (e: Exception) {
            android.util.Log.e("InvoiceApiService", "Exception in updateInvoiceStatus", e)
            InvoiceResponse(success = false, error = e.message ?: "Unknown error", data = null)
        }
    }
}

// API Response wrappers
data class InvoiceListResponse(
    val success: Boolean,
    val action: String?, // Extra field in actual response
    val generated_at: String?, // Extra field in actual response
    val data: InvoiceListData?,
    val error: String?
)

data class InvoiceListData(
    val invoices: List<Invoice>
)

data class InvoiceResponse(
    val success: Boolean,
    val action: String? = null,
    @SerializedName("generated_at") val generatedAt: String? = null,
    val data: InvoiceData?, // FIXED: Back to nested structure
    val error: String?
)

data class InvoiceData(
    val invoice: Invoice // Invoice is nested under data.invoice
)

// Request models
data class CreateInvoiceRequest(
    val action: String = "create-invoice",
    @SerializedName("customer_name") val customerName: String,
    val date: String,
    @SerializedName("due_date") val dueDate: String? = null,
    val items: List<InvoiceItem>,
    val total: Double
)

data class EditInvoiceRequest(
    val action: String = "edit-invoice",
    val id: String,
    @SerializedName("customer_name") val customerName: String? = null,
    val date: String? = null,
    @SerializedName("due_date") val dueDate: String? = null,
    val items: List<InvoiceItem>? = null,
    val total: Double? = null
)

data class UpdateInvoiceStatusRequest(
    val action: String = "update-invoice-status",
    @SerializedName("invoice_id") val invoiceId: String,
    val status: String // "paid", "pending", "cancelled"
)
