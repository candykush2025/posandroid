// kotlin
package com.blackcode.poscandykush

import android.util.Log
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class PrintApiService(
    private val baseUrl: String = "https://pos-candy-kush.vercel.app/api/print"
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * Synchronous call intended to be run from a background thread.
     * Returns a parsed PrintJobResponse on success, or null on failure/exception.
     */
    fun getPrintJob(): PrintJobResponse? {
        return try {
            val request = Request.Builder()
                .url(baseUrl)
                .get()
                .addHeader("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            Log.d("PrintApiService", "getPrintJob response code=${response.code} body=$body")

            if (response.isSuccessful && body != null) {
                try {
                    gson.fromJson(body, PrintJobResponse::class.java)
                } catch (e: Exception) {
                    Log.e("PrintApiService", "JSON parse error", e)
                    null
                }
            } else {
                Log.e("PrintApiService", "Unsuccessful response: code=${response.code}")
                null
            }
        } catch (e: Exception) {
            Log.e("PrintApiService", "Network error in getPrintJob", e)
            null
        }
    }
}

data class PrintJobResponse(
    val success: Boolean = false,
    val data: String? = null,
    val message: String? = null,
    val timestamp: String? = null
)