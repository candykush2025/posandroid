package com.blackcode.poscandykush

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit

class ExpenseApiService(private val baseUrl: String = "https://pos-candy-kush.vercel.app/api/mobile") {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // Get all expenses
    fun getExpenses(jwtToken: String, startDate: String? = null, endDate: String? = null): ExpenseListResponse? {
        return try {
            var url = "$baseUrl?action=get-expenses"
            if (startDate != null && endDate != null) {
                url += "&start_date=$startDate&end_date=$endDate"
            }

            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("Authorization", "Bearer $jwtToken")
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            android.util.Log.d("ExpenseApiService", "getExpenses response code: ${response.code}")

            if (response.isSuccessful && body != null) {
                val expenseResponse = gson.fromJson(body, ExpenseListResponse::class.java)
                android.util.Log.d("ExpenseApiService", "Successfully parsed expense list response: ${expenseResponse.success}")
                expenseResponse
            } else {
                val errorMsg = "HTTP ${response.code}: ${response.message}"
                android.util.Log.e("ExpenseApiService", "API call unsuccessful: $errorMsg")
                ExpenseListResponse(success = false, error = errorMsg, data = null)
            }
        } catch (e: Exception) {
            android.util.Log.e("ExpenseApiService", "Exception in getExpenses", e)
            ExpenseListResponse(success = false, error = e.message ?: "Unknown error", data = null)
        }
    }

    // Get single expense
    fun getExpense(jwtToken: String, expenseId: String): ExpenseResponse? {
        return try {
            val request = Request.Builder()
                .url("$baseUrl?action=get-expense&id=$expenseId")
                .get()
                .addHeader("Authorization", "Bearer $jwtToken")
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            android.util.Log.d("ExpenseApiService", "getExpense response code: ${response.code}")

            if (response.isSuccessful && body != null) {
                val expenseResponse = gson.fromJson(body, ExpenseResponse::class.java)
                android.util.Log.d("ExpenseApiService", "Successfully parsed single expense response: ${expenseResponse.success}")
                expenseResponse
            } else {
                android.util.Log.e("ExpenseApiService", "API call unsuccessful: ${response.code}")
                ExpenseResponse(success = false, error = "HTTP ${response.code}: ${response.message}", data = null)
            }
        } catch (e: Exception) {
            android.util.Log.e("ExpenseApiService", "Exception in getExpense", e)
            ExpenseResponse(success = false, error = e.message ?: "Unknown error", data = null)
        }
    }

    // Create expense
    fun createExpense(jwtToken: String, request: CreateExpenseRequest): ExpenseResponse? {
        return try {
            val jsonBody = gson.toJson(request)
            val requestBody = jsonBody.toRequestBody(jsonMediaType)

            android.util.Log.d("ExpenseApiService", "Creating expense with request: $jsonBody")

            val httpRequest = Request.Builder()
                .url(baseUrl)
                .post(requestBody)
                .addHeader("Authorization", "Bearer $jwtToken")
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(httpRequest).execute()
            val body = response.body?.string()

            android.util.Log.d("ExpenseApiService", "createExpense response code: ${response.code}")

            if (response.isSuccessful && body != null) {
                val expenseResponse = gson.fromJson(body, ExpenseResponse::class.java)
                android.util.Log.d("ExpenseApiService", "Successfully created expense: ${expenseResponse.success}")
                expenseResponse
            } else {
                val errorMsg = "HTTP ${response.code}: ${response.message}"
                android.util.Log.e("ExpenseApiService", "API call unsuccessful: $errorMsg")
                ExpenseResponse(success = false, error = errorMsg, data = null)
            }
        } catch (e: Exception) {
            android.util.Log.e("ExpenseApiService", "Exception in createExpense", e)
            ExpenseResponse(success = false, error = e.message ?: "Unknown error", data = null)
        }
    }

    // Edit expense
    fun editExpense(jwtToken: String, request: EditExpenseRequest): ExpenseResponse? {
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

            android.util.Log.d("ExpenseApiService", "editExpense response code: ${response.code}")

            if (response.isSuccessful && body != null) {
                val expenseResponse = gson.fromJson(body, ExpenseResponse::class.java)
                android.util.Log.d("ExpenseApiService", "Successfully edited expense: ${expenseResponse.success}")
                expenseResponse
            } else {
                android.util.Log.e("ExpenseApiService", "API call unsuccessful: ${response.code}")
                ExpenseResponse(success = false, error = "HTTP ${response.code}: ${response.message}", data = null)
            }
        } catch (e: Exception) {
            android.util.Log.e("ExpenseApiService", "Exception in editExpense", e)
            ExpenseResponse(success = false, error = e.message ?: "Unknown error", data = null)
        }
    }

    // Delete expense
    fun deleteExpense(jwtToken: String, expenseId: String): ExpenseResponse? {
        return try {
            val request = Request.Builder()
                .url("$baseUrl?action=delete-expense&id=$expenseId")
                .delete()
                .addHeader("Authorization", "Bearer $jwtToken")
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            android.util.Log.d("ExpenseApiService", "deleteExpense response code: ${response.code}")

            if (response.isSuccessful && body != null) {
                val expenseResponse = gson.fromJson(body, ExpenseResponse::class.java)
                android.util.Log.d("ExpenseApiService", "Successfully deleted expense: ${expenseResponse.success}")
                expenseResponse
            } else {
                android.util.Log.e("ExpenseApiService", "API call unsuccessful: ${response.code}")
                ExpenseResponse(success = false, error = "HTTP ${response.code}: ${response.message}", data = null)
            }
        } catch (e: Exception) {
            android.util.Log.e("ExpenseApiService", "Exception in deleteExpense", e)
            ExpenseResponse(success = false, error = e.message ?: "Unknown error", data = null)
        }
    }

    // Update expense
    fun updateExpense(jwtToken: String, request: EditExpenseRequest): ExpenseResponse? {
        return try {
            val jsonBody = gson.toJson(request)
            val requestBody = jsonBody.toRequestBody(jsonMediaType)

            android.util.Log.d("ExpenseApiService", "Updating expense with request: $jsonBody")

            val httpRequest = Request.Builder()
                .url(baseUrl)
                .post(requestBody)
                .addHeader("Authorization", "Bearer $jwtToken")
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(httpRequest).execute()
            val body = response.body?.string()

            android.util.Log.d("ExpenseApiService", "updateExpense response code: ${response.code}")

            if (response.isSuccessful && body != null) {
                val expenseResponse = gson.fromJson(body, ExpenseResponse::class.java)
                android.util.Log.d("ExpenseApiService", "Successfully updated expense: ${expenseResponse.success}")
                expenseResponse
            } else {
                val errorMsg = "HTTP ${response.code}: ${response.message}"
                android.util.Log.e("ExpenseApiService", "API call unsuccessful: $errorMsg")
                ExpenseResponse(success = false, error = errorMsg, data = null)
            }
        } catch (e: Exception) {
            android.util.Log.e("ExpenseApiService", "Exception in updateExpense", e)
            ExpenseResponse(success = false, error = e.message ?: "Unknown error", data = null)
        }
    }
}

// API Response wrappers
data class ExpenseListResponse(
    val success: Boolean,
    val data: ExpenseListData?,
    val error: String?
)

data class ExpenseListData(
    val expenses: List<Expense>,
    val total: Double? = null
)

data class ExpenseResponse(
    val success: Boolean,
    val action: String? = null,
    @SerializedName("generated_at") val generatedAt: String? = null,
    val data: Expense?, // FIXED: Expense directly in data, not nested
    val error: String?
)

data class ExpenseData(
    val expense: Expense
)

// Request models
data class CreateExpenseRequest(
    val action: String = "create-expense",
    val description: String,
    val amount: Double,
    val date: String,
    val time: String
)

data class EditExpenseRequest(
    val action: String = "edit-expense",
    val id: String,
    val description: String? = null,
    val amount: Double? = null,
    val date: String? = null,
    val time: String? = null
)
