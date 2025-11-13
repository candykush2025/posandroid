# Cart API Documentation

## Overview

This API provides real-time cart and payment status information for Android POS integration. The API allows Android applications to monitor cart contents and payment status in real-time.

**Base URL:** `https://pos-candy-kush.vercel.app/api`

**CORS:** All origins allowed (`*`) for cross-platform access

## Authentication

Currently no authentication required. All endpoints are publicly accessible.

## Endpoints

### 1. Cart Management (`/api/cart`)

#### GET `/api/cart`

Retrieves the current cart contents and status.

**Response:**
```json
{
  "success": true,
  "cart": {
    "items": [
      {
        "id": "string",
        "productId": "string",
        "name": "string",
        "quantity": 1,
        "price": 10.99,
        "total": 10.99,
        "weight": 0.5,
        "unit": "g"
      }
    ],
    "discount": {
      "type": "percentage",
      "value": 0
    },
    "tax": {
      "rate": 0,
      "amount": 0
    },
    "customer": {
      "id": "string",
      "name": "string",
      "phone": "string"
    },
    "notes": "string",
    "total": 10.99,
    "lastUpdated": "2025-11-13T12:00:45.371Z"
  },
  "timestamp": "2025-11-13T12:00:45.371Z"
}
```

#### POST `/api/cart`

Updates the cart contents from the POS system.

**Request Body:**
```json
{
  "items": [
    {
      "id": "string",
      "productId": "string",
      "name": "string",
      "quantity": 1,
      "price": 10.99,
      "total": 10.99,
      "weight": 0.5,
      "unit": "g"
    }
  ],
  "discount": {
    "type": "percentage",
    "value": 0
  },
  "tax": {
    "rate": 0,
    "amount": 0
  },
  "customer": {
    "id": "string",
    "name": "string",
    "phone": "string"
  },
  "notes": "string",
  "total": 10.99
}
```

**Response:**
```json
{
  "success": true,
  "message": "Cart updated successfully",
  "cart": { /* updated cart object */ },
  "timestamp": "2025-11-13T12:00:45.371Z"
}
```

#### PUT `/api/cart`

Processes payment and clears the cart.

**Request Body:**
```json
{
  "action": "process_payment",
  "paymentData": {
    "amount": 10.99,
    "method": "cash|card|other",
    "transactionId": "string"
  }
}
```

**Response:**
```json
{
  "success": true,
  "message": "Payment processed successfully",
  "cart": { /* empty cart object */ }
}
```

### 2. Payment Status (`/api/cart/payment`)

#### GET `/api/cart/payment`

Retrieves the current payment status.

**Response:**
```json
{
  "success": true,
  "paymentStatus": {
    "status": "idle|processing|completed|failed",
    "timestamp": "2025-11-13T12:00:45.554Z",
    "amount": 10.99,
    "method": "cash|card|other",
    "transactionId": "string"
  }
}
```

#### POST `/api/cart/payment`

Updates the payment status.

**Request Body:**
```json
{
  "status": "idle|processing|completed|failed",
  "amount": 10.99,
  "method": "cash|card|other",
  "transactionId": "string"
}
```

**Response:**
```json
{
  "success": true,
  "paymentStatus": { /* updated payment status object */ }
}
```

## Data Types

### Cart Item
```json
{
  "id": "string",           // Unique cart item ID
  "productId": "string",    // Product database ID
  "name": "string",         // Product display name
  "quantity": 1,            // Number of items
  "price": 10.99,           // Unit price
  "total": 10.99,           // Line total (price * quantity)
  "weight": 0.5,            // Weight for sold-by-weight items
  "unit": "g"               // Unit of measurement (g, kg, oz, lb)
}
```

### Customer
```json
{
  "id": "string",           // Customer database ID
  "name": "string",         // Customer full name
  "phone": "string"         // Customer phone number
}
```

### Discount
```json
{
  "type": "percentage|fixed",  // Discount type
  "value": 0                  // Discount amount/value
}
```

### Tax
```json
{
  "rate": 0,        // Tax rate (percentage)
  "amount": 0       // Calculated tax amount
}
```

## Integration Guide

### Real-time Cart Monitoring

1. **Poll Cart Status**: Make periodic GET requests to `/api/cart` (every 1-5 seconds)
2. **Display Updates**: Update your Android UI when cart contents change
3. **Handle Empty Cart**: Show appropriate message when cart is empty

### Payment Status Tracking

1. **Monitor Payment**: Poll GET `/api/cart/payment` during checkout
2. **Status Changes**:
   - `idle`: No payment in progress
   - `processing`: Payment is being processed
   - `completed`: Payment successful, cart cleared
   - `failed`: Payment failed

### Android Implementation Example

```kotlin
// Kotlin example for Android app
class CartMonitor(private val baseUrl: String = "https://pos-candy-kush.vercel.app/api") {

    private val client = OkHttpClient()

    fun getCart(): Cart? {
        val request = Request.Builder()
            .url("$baseUrl/cart")
            .get()
            .build()

        return try {
            val response = client.newCall(request).execute()
            val json = response.body?.string()
            // Parse JSON to Cart object
            parseCartJson(json)
        } catch (e: Exception) {
            null
        }
    }

    fun getPaymentStatus(): PaymentStatus? {
        val request = Request.Builder()
            .url("$baseUrl/cart/payment")
            .get()
            .build()

        return try {
            val response = client.newCall(request).execute()
            val json = response.body?.string()
            // Parse JSON to PaymentStatus object
            parsePaymentJson(json)
        } catch (e: Exception) {
            null
        }
    }
}
```

```java
// Java example
public class CartMonitor {
    private final String baseUrl = "https://pos-candy-kush.vercel.app/api";
    private final OkHttpClient client = new OkHttpClient();

    public Cart getCart() throws IOException {
        Request request = new Request.Builder()
            .url(baseUrl + "/cart")
            .get()
            .build();

        try (Response response = client.newCall(request).execute()) {
            String json = response.body().string();
            return parseCartJson(json);
        }
    }

    public PaymentStatus getPaymentStatus() throws IOException {
        Request request = new Request.Builder()
            .url(baseUrl + "/cart/payment")
            .get()
            .build();

        try (Response response = client.newCall(request).execute()) {
            String json = response.body().string();
            return parsePaymentJson(json);
        }
    }
}
```

## Error Handling

All endpoints return consistent error responses:

```json
{
  "success": false,
  "error": "Error description"
}
```

**Common HTTP Status Codes:**
- `200`: Success
- `400`: Bad Request (invalid data)
- `500`: Internal Server Error

## Rate Limiting

Currently no rate limiting implemented. Recommended polling interval: 1-5 seconds.

## Production Notes

- **Data Persistence**: Currently uses in-memory storage. For production, implement database persistence.
- **Security**: Add authentication/authorization for production use.
- **Scaling**: Consider WebSocket connections for real-time updates instead of polling.

## Testing

Test endpoints using curl:

```bash
# Get cart
curl -X GET "https://pos-candy-kush.vercel.app/api/cart" \
  -H "Content-Type: application/json"

# Get payment status
curl -X GET "https://pos-candy-kush.vercel.app/api/cart/payment" \
  -H "Content-Type: application/json"
```

## Support

For integration issues or questions, please provide:
- Android app version
- API endpoint being called
- Request/response examples
- Error messages (if any)