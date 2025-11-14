package com.blackcode.poscandykush

import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class CartApiService(private val baseUrl: String = "https://pos-candy-kush.vercel.app/api") {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    fun getCart(): CartResponse? {
        return try {
            val request = Request.Builder()
                .url("$baseUrl/cart")
                .get()
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            android.util.Log.d("CartApiService", "getCart response code: ${response.code}, body: $body")

            if (response.isSuccessful && body != null) {
                val cartResponse = gson.fromJson(body, CartResponse::class.java)
                android.util.Log.d("CartApiService", "Successfully parsed cart response: ${cartResponse.success}")
                cartResponse
            } else {
                android.util.Log.e("CartApiService", "API call unsuccessful: ${response.code}")
                CartResponse(success = false, error = "HTTP ${response.code}: ${response.message}", timestamp = System.currentTimeMillis().toString())
            }
        } catch (e: Exception) {
            android.util.Log.e("CartApiService", "Exception in getCart", e)
            CartResponse(success = false, error = e.message ?: "Unknown error", timestamp = System.currentTimeMillis().toString())
        }
    }

    fun getPaymentStatus(): PaymentStatusResponse? {
        return try {
            val request = Request.Builder()
                .url("$baseUrl/cart/payment")
                .get()
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            android.util.Log.d("CartApiService", "getPaymentStatus response code: ${response.code}, body: $body")

            if (response.isSuccessful && body != null) {
                gson.fromJson(body, PaymentStatusResponse::class.java)
            } else {
                android.util.Log.e("CartApiService", "Payment status API unsuccessful: ${response.code}")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("CartApiService", "Exception in getPaymentStatus", e)
            null
        }
    }
}