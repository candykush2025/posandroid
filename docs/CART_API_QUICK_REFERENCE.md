# Cart API Quick Reference

## Base URL
```
https://pos-candy-kush.vercel.app/api
```

## Endpoints Summary

| Method | Endpoint | Purpose | Poll Interval |
|--------|----------|---------|---------------|
| GET | `/cart` | Get current cart contents | 1-5 seconds |
| POST | `/cart` | Update cart (POS only) | - |
| PUT | `/cart` | Process payment (POS only) | - |
| GET | `/cart/payment` | Get payment status | 1-2 seconds |
| POST | `/cart/payment` | Update payment status (POS only) | - |

## Quick Examples

### Monitor Cart (Android)
```kotlin
// Get cart every 2 seconds
val cart = cartMonitor.getCart()
if (cart?.items?.isNotEmpty() == true) {
    // Display cart items to customer
    updateCustomerDisplay(cart)
} else {
    // Show "Cart is empty" message
    showEmptyCartMessage()
}
```

### Monitor Payment Status (Android)
```kotlin
// Check payment status during checkout
val paymentStatus = cartMonitor.getPaymentStatus()
when (paymentStatus?.status) {
    "processing" -> showProcessingAnimation()
    "completed" -> showPaymentSuccess()
    "failed" -> showPaymentError()
    else -> showWaitingForPayment()
}
```

## Key Data Fields

### Cart Response
```json
{
  "cart": {
    "items": [{"name": "Product", "quantity": 1, "price": 10.99}],
    "total": 10.99,
    "customer": {"name": "John Doe"}
  }
}
```

### Payment Response
```json
{
  "paymentStatus": {
    "status": "completed",
    "amount": 10.99,
    "method": "cash"
  }
}
```

## Integration Checklist

- [ ] Add OkHttp dependency to Android project
- [ ] Create CartMonitor class
- [ ] Implement periodic polling (Handler/Thread/Coroutines)
- [ ] Handle network errors gracefully
- [ ] Update UI on cart/payment changes
- [ ] Test with live POS system

## Common Issues

**Q: Getting CORS errors?**
A: API allows all origins (`*`), should work from any domain.

**Q: Cart not updating?**
A: Check if POS is sending POST requests to `/api/cart`.

**Q: Payment status not changing?**
A: POS should call PUT `/api/cart` with `process_payment` action.

**Q: High battery usage?**
A: Increase polling interval or use WebSocket for real-time updates.