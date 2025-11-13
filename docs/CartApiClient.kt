// CartApiClient.kt - Android Kotlin implementation for POS Cart API
package com.candykush.pos.api

import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.gson.Gson
import kotlinx.coroutines.*
import java.io.IOException
import java.util.concurrent.TimeUnit

data class CartItem(
    val id: String,
    val productId: String,
    val name: String,
    val quantity: Int,
    val price: Double,
    val total: Double,
    val weight: Double? = null,
    val unit: String? = null
)

data class Customer(
    val id: String? = null,
    val name: String? = null,
    val phone: String? = null
)

data class Discount(
    val type: String, // "percentage" or "fixed"
    val value: Double
)

data class Tax(
    val rate: Double,
    val amount: Double
)

data class Cart(
    val items: List<CartItem>,
    val discount: Discount,
    val tax: Tax,
    val customer: Customer?,
    val notes: String,
    val total: Double,
    val lastUpdated: String?
)

data class CartResponse(
    val success: Boolean,
    val cart: Cart,
    val timestamp: String
)

data class PaymentStatus(
    val status: String, // "idle", "processing", "completed", "failed"
    val timestamp: String?,
    val amount: Double,
    val method: String?,
    val transactionId: String?
)

data class PaymentResponse(
    val success: Boolean,
    val paymentStatus: PaymentStatus
)

class CartApiClient(
    private val baseUrl: String = "https://pos-candy-kush.vercel.app/api",
    private val gson: Gson = Gson()
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    // Get current cart contents
    suspend fun getCart(): Cart? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/cart")
                .get()
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val json = response.body?.string()

            if (response.isSuccessful && json != null) {
                val cartResponse = gson.fromJson(json, CartResponse::class.java)
                if (cartResponse.success) {
                    return@withContext cartResponse.cart
                }
            }
            null
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    // Get payment status
    suspend fun getPaymentStatus(): PaymentStatus? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/cart/payment")
                .get()
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val json = response.body?.string()

            if (response.isSuccessful && json != null) {
                val paymentResponse = gson.fromJson(json, PaymentResponse::class.java)
                if (paymentResponse.success) {
                    return@withContext paymentResponse.paymentStatus
                }
            }
            null
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }
}

// Usage example in Activity/Fragment
class CustomerDisplayActivity : AppCompatActivity() {
    private val apiClient = CartApiClient()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customer_display)

        // Start monitoring cart
        startCartMonitoring()
    }

    private fun startCartMonitoring() {
        scope.launch {
            while (isActive) {
                try {
                    val cart = apiClient.getCart()
                    updateCartDisplay(cart)

                    val paymentStatus = apiClient.getPaymentStatus()
                    updatePaymentDisplay(paymentStatus)

                    // Poll every 2 seconds
                    delay(2000)
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Handle error - maybe show offline message
                }
            }
        }
    }

    private fun updateCartDisplay(cart: Cart?) {
        if (cart == null) return

        if (cart.items.isEmpty()) {
            // Show empty cart message
            findViewById<TextView>(R.id.cartStatusText).text = "Cart is empty"
            findViewById<RecyclerView>(R.id.cartItemsRecyclerView).visibility = View.GONE
        } else {
            // Show cart items
            findViewById<TextView>(R.id.cartStatusText).text = "Current Order"
            findViewById<RecyclerView>(R.id.cartItemsRecyclerView).visibility = View.VISIBLE

            // Update total
            findViewById<TextView>(R.id.totalTextView).text =
                String.format("$%.2f", cart.total)

            // Update customer name if available
            cart.customer?.name?.let { name ->
                findViewById<TextView>(R.id.customerNameTextView).text = "Customer: $name"
            }
        }
    }

    private fun updatePaymentDisplay(paymentStatus: PaymentStatus?) {
        if (paymentStatus == null) return

        when (paymentStatus.status) {
            "processing" -> {
                findViewById<TextView>(R.id.paymentStatusText).text = "Processing payment..."
                // Show loading animation
            }
            "completed" -> {
                findViewById<TextView>(R.id.paymentStatusText).text = "Payment completed!"
                // Show success animation, maybe clear cart display
            }
            "failed" -> {
                findViewById<TextView>(R.id.paymentStatusText).text = "Payment failed"
                // Show error message
            }
            else -> {
                findViewById<TextView>(R.id.paymentStatusText).text = "Ready for payment"
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}