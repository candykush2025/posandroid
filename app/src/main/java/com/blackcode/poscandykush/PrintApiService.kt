// kotlin
package com.blackcode.poscandykush

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Print Job API Service following the complete print job flow:
 *
 * 1. GET /api/print - Fetch pending print job (polls every 2 seconds)
 * 2. Print receipt to thermal printer
 * 3. PUT /api/print - Confirm print status (printed/failed)
 *
 * Job Status Lifecycle:
 * pending → processing → printed ✓ (removed from queue)
 *                     → failed → pending (retry up to 3x)
 */
class PrintApiService(
    private val baseUrl: String = "https://pos-candy-kush.vercel.app/api/print"
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    companion object {
        private const val TAG = "PrintApiService"
    }

    /**
     * Fetch next pending print job from the API.
     * When a job is retrieved, its status changes to "processing".
     * IMPORTANT: After printing, you MUST call confirmPrinted() or reportFailed()
     *
     * @return PrintJobResponse on success, null on failure/no pending job
     */
    fun getPrintJob(): PrintJobResponse? {
        return try {
            Log.d(TAG, "=== getPrintJob START - URL: $baseUrl ===")
            val request = Request.Builder()
                .url(baseUrl)
                .get()
                .addHeader("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            Log.d(TAG, "Response code: ${response.code}")
            Log.d(TAG, "Response body length: ${body?.length}")
            Log.d(TAG, "Response body: $body")

            if (response.isSuccessful && body != null) {
                try {
                    val printJobResponse = gson.fromJson(body, PrintJobResponse::class.java)
                    Log.d(TAG, "✓ Parsed PrintJobResponse:")
                    Log.d(TAG, "  - success: ${printJobResponse.success}")
                    Log.d(TAG, "  - jobId: ${printJobResponse.jobId}")
                    Log.d(TAG, "  - message: ${printJobResponse.message}")
                    Log.d(TAG, "  - has data: ${printJobResponse.data != null}")
                    if (printJobResponse.data != null) {
                        Log.d(TAG, "  - data.order: ${printJobResponse.data.order != null}")
                        Log.d(TAG, "  - data.timestamp: ${printJobResponse.data.timestamp}")
                        if (printJobResponse.data.order != null) {
                            Log.d(TAG, "  - order.total: ${printJobResponse.data.order.total}")
                            Log.d(TAG, "  - order.items: ${printJobResponse.data.order.items?.size ?: 0}")
                            Log.d(TAG, "  - order.line_items: ${printJobResponse.data.order.line_items?.size ?: 0}")
                        }
                    }
                    Log.d(TAG, "=== getPrintJob END (SUCCESS) ===")
                    printJobResponse
                } catch (e: Exception) {
                    Log.e(TAG, "✗ JSON parse error: ${e.message}", e)
                    Log.d(TAG, "=== getPrintJob END (PARSE ERROR) ===")
                    null
                }
            } else {
                Log.d(TAG, "✗ Unsuccessful response or empty body - code: ${response.code}")
                Log.d(TAG, "=== getPrintJob END (UNSUCCESSFUL) ===")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "✗ Network error in getPrintJob: ${e.message}", e)
            Log.d(TAG, "=== getPrintJob END (NETWORK ERROR) ===")
            null
        }
    }

    /**
     * Confirm that the print job was completed successfully.
     * This removes the job from the queue.
     *
     * @param jobId The job ID received from getPrintJob()
     * @return true if confirmation was successful, false otherwise
     */
    fun confirmPrinted(jobId: String): Boolean {
        return sendStatus(jobId, "printed", null)
    }

    /**
     * Report that the print job failed.
     * The job will be retried automatically (max 3 attempts).
     *
     * @param jobId The job ID received from getPrintJob()
     * @param error Description of the error
     * @return true if report was successful, false otherwise
     */
    fun reportFailed(jobId: String, error: String): Boolean {
        return sendStatus(jobId, "failed", error)
    }

    /**
     * Send print job status update to the API.
     *
     * @param jobId The job ID
     * @param status Either "printed" or "failed"
     * @param error Optional error message (only for failed status)
     * @return true if update was successful, false otherwise
     */
    private fun sendStatus(jobId: String, status: String, error: String?): Boolean {
        return try {
            val json = JsonObject().apply {
                addProperty("jobId", jobId)
                addProperty("status", status)
                error?.let { addProperty("error", it) }
            }

            val requestBody = json.toString()
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(baseUrl)
                .put(requestBody)
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            Log.d(TAG, "sendStatus($status) for job $jobId: code=${response.code} body=$responseBody")

            if (response.isSuccessful) {
                Log.d(TAG, "Successfully updated job $jobId to status: $status")
                true
            } else {
                Log.e(TAG, "Failed to update job status: code=${response.code}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error in sendStatus for job $jobId", e)
            false
        }
    }

    /**
     * Test method to diagnose the print API response
     * Call this to see exactly what the API is returning
     */
    fun testPrintApiDiagnostics(): String {
        return try {
            Log.d(TAG, "🔧 TEST: Starting API diagnostics...")

            val request = Request.Builder()
                .url(baseUrl)
                .get()
                .addHeader("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            val diagnostics = StringBuilder()
            diagnostics.append("=== PRINT API DIAGNOSTICS ===\n")
            diagnostics.append("URL: $baseUrl\n")
            diagnostics.append("HTTP Code: ${response.code}\n")
            diagnostics.append("Response Headers:\n")
            response.headers.forEach { (name, value) ->
                diagnostics.append("  $name: $value\n")
            }
            diagnostics.append("\nRaw Response Body:\n")
            diagnostics.append(body ?: "(empty)\n")
            diagnostics.append("\nResponse Length: ${body?.length ?: 0} bytes\n")

            if (body != null) {
                try {
                    val json = gson.fromJson(body, com.google.gson.JsonElement::class.java)
                    diagnostics.append("\nJSON Pretty Print:\n")
                    diagnostics.append(gson.toJson(json))
                    diagnostics.append("\n\nParsed as PrintJobResponse:\n")
                    val printJob = gson.fromJson(body, PrintJobResponse::class.java)
                    diagnostics.append("  success: ${printJob.success}\n")
                    diagnostics.append("  jobId: ${printJob.jobId}\n")
                    diagnostics.append("  message: ${printJob.message}\n")
                    diagnostics.append("  data: ${printJob.data}\n")
                    diagnostics.append("  timestamp: ${printJob.timestamp}\n")
                    diagnostics.append("  attempts: ${printJob.attempts}\n")
                } catch (e: Exception) {
                    diagnostics.append("\nJSON Parse Error: ${e.message}\n")
                }
            }

            diagnostics.append("\n=== END DIAGNOSTICS ===\n")
            val result = diagnostics.toString()
            Log.d(TAG, result)
            result
        } catch (e: Exception) {
            val error = "❌ Diagnostics Error: ${e.message}\n${e.stackTraceToString()}"
            Log.e(TAG, error, e)
            error
        }
    }

    /**
     * List all jobs in the queue (for debugging purposes).
     *
     * @return JSON string of all jobs, or null on failure
     */
    fun listAllJobs(): String? {
        return try {
            val request = Request.Builder()
                .url("$baseUrl?list=true")
                .get()
                .addHeader("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            Log.d(TAG, "listAllJobs response: $body")
            body
        } catch (e: Exception) {
            Log.e(TAG, "Network error in listAllJobs", e)
            null
        }
    }

    /**
     * Clear all jobs from the queue (for debugging purposes).
     *
     * @return true if successful, false otherwise
     */
    fun clearAllJobs(): Boolean {
        return try {
            val request = Request.Builder()
                .url(baseUrl)
                .delete()
                .build()

            val response = client.newCall(request).execute()
            Log.d(TAG, "clearAllJobs response: code=${response.code}")
            response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "Network error in clearAllJobs", e)
            false
        }
    }
}

/**
 * Response from GET /api/print
 */
data class PrintJobResponse(
    val success: Boolean = false,
    val data: PrintData? = null,
    val jobId: String? = null,        // IMPORTANT: Save this for confirmation
    val message: String? = null,
    val timestamp: String? = null,
    val attempts: Int = 0
)

data class PrintData(
    val order: Order? = null,
    val shiftReport: ShiftReportData? = null,
    val cashier: String? = null,
    val timestamp: String? = null,
    val type: String? = null
)

data class Order(
    val orderNumber: String? = null,
    val receiptNumber: String? = null,
    val total_money: Double = 0.0,
    val total: Double = 0.0,
    val subtotal: Double = 0.0,
    val total_tax: Double = 0.0,
    val total_discount: Double = 0.0,
    val tip: Double = 0.0,
    val surcharge: Double = 0.0,
    val cashierName: String? = null,
    val line_items: List<LineItem>? = null,
    val items: List<OrderItem>? = null,
    val payments: List<Payment>? = null,
    val paymentMethod: String? = null,
    val cashReceived: Double? = null,
    val change: Double? = null,
    val customer: CustomerData? = null
)

data class LineItem(
    val id: String? = null,
    val item_id: String? = null,
    val item_name: String? = null,
    val quantity: Double = 0.0,
    val price: Double = 0.0,
    val total_money: Double = 0.0
)

data class OrderItem(
    val name: String? = null,
    val quantity: Int = 0,
    val price: Double = 0.0,
    val total: Double = 0.0,
    val sku: String? = null
)

data class CustomerData(
    val name: String? = null,
    val phone: String? = null,
    val email: String? = null
)

data class Payment(
    val payment_type_id: String? = null,
    val name: String? = null,
    val type: String? = null,
    val money_amount: Double = 0.0,
    val paid_at: String? = null,
    val payment_details: Any? = null
)

data class ShiftReportData(
    val cashierName: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val startingCash: Double = 0.0,
    val cashPayments: Double = 0.0,
    val cashRefunds: Double = 0.0,
    val paidIn: Double = 0.0,
    val paidOut: Double = 0.0,
    val expectedCash: Double = 0.0,
    val actualCash: Double = 0.0,
    val variance: Double = 0.0,
    val varianceStatus: String? = null,
    val grossSales: Double = 0.0,
    val totalRefunds: Double = 0.0,
    val totalDiscounts: Double = 0.0,
    val netSales: Double = 0.0,
    val transactionCount: Int = 0,
    val notes: String? = null
)
