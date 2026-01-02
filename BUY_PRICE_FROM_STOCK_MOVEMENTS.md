# Buy Price Calculation from Stock Movements

## Overview
The `ProductManagementActivity` now calculates product buy prices from actual stock movements (purchase orders) instead of relying solely on the static cost field from the stock API.

## Implementation Summary

### Key Changes

#### 1. Enhanced Data Fetching
- **Before**: Only fetched stock data from `/api/mobile?action=stock`
- **After**: Fetches both stock data AND stock history data from `/api/mobile?action=stock-history`

#### 2. New Function: `fetchStockHistoryData()`
```kotlin
private suspend fun fetchStockHistoryData(): JSONObject
```
- Fetches stock movement history from the API
- Returns detailed movement data including purchase orders with costs
- Gracefully handles failures (falls back to default cost if unavailable)

#### 3. New Function: `calculateBuyPriceFromMovements()`
```kotlin
private fun calculateBuyPriceFromMovements(
    productId: String, 
    productName: String, 
    defaultCost: Double, 
    stockHistoryData: JSONObject?
): Double
```

**Logic:**
1. **Priority**: Latest purchase order cost takes precedence
2. **Matching**: Matches products by `product_id` first, then falls back to `product_name`
3. **Filtering**: Only considers movements of type `"purchase_order"`
4. **Cost Extraction**: Looks for the `cost` field in each purchase order movement
5. **Latest Selection**: Uses the most recent purchase order based on timestamp
6. **Fallback**: Returns default cost if:
   - No stock history data available
   - No purchase orders found
   - No cost in purchase orders

#### 4. Updated `updateUI()` Function
- **New signature**: `updateUI(data: JSONObject, stockHistoryData: JSONObject?)`
- **Enhanced logic**: For each product, calculates buy price using stock movements
- **Display**: Shows the calculated buy price (from latest purchase order) instead of static cost

#### 5. Updated `loadStockData()` Function
- Fetches both stock and stock history data in parallel
- Passes both data sets to `updateUI()`
- Handles stock history fetch failures gracefully (continues with null)

## How It Works

### Flow Diagram
```
1. User opens ProductManagementActivity
   ↓
2. loadStockData() called
   ↓
3. Fetch stock data (products with static costs)
   ↓
4. Fetch stock history data (movements with purchase costs)
   ↓
5. For each product:
   - Extract product_id and product_name
   - Find matching product in stock history
   - Get all purchase_order movements
   - Find latest purchase order with cost
   - Use that cost as buy price
   ↓
6. Display products with calculated buy prices
```

### Example Data Flow

**Stock API Response:**
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "product_id": "abc123",
        "product_name": "Blue Dream - 3.5g",
        "cost": 15.00,  // Static/old cost
        "price": 25.00,
        "category": "Flower"
      }
    ]
  }
}
```

**Stock History API Response:**
```json
{
  "success": true,
  "data": {
    "products": [
      {
        "product_id": "abc123",
        "product_name": "Blue Dream - 3.5g",
        "movements": [
          {
            "type": "purchase_order",
            "quantity": 50,
            "cost": 12.50,  // Newer cost from recent purchase
            "timestamp": "2025-12-15T10:30:00Z"
          },
          {
            "type": "purchase_order",
            "quantity": 30,
            "cost": 13.00,  // Older cost
            "timestamp": "2025-11-20T14:20:00Z"
          }
        ]
      }
    ]
  }
}
```

**Result:**
- **Buy Price Displayed**: $12.50 (from latest purchase order, not the static $15.00)
- **Sell Price**: $25.00
- **Margin**: $12.50 (25.00 - 12.50)

## Benefits

1. **Accurate Cost Tracking**: Shows actual purchase costs from recent orders
2. **Real-Time Profit Margins**: Calculates margins based on actual costs, not outdated static values
3. **Better Inventory Management**: Helps identify products with outdated cost information
4. **Visual Feedback**: Red text for zero costs indicates products needing cost updates
5. **Graceful Degradation**: Falls back to static cost if stock history unavailable

## Visual Indicators

### Buy Price Display
- **Green/Normal**: Cost from recent purchase order
- **Red**: Zero cost (needs attention)

### Logging
The implementation includes detailed logging:
```
DEBUG: Using stock movement cost for Blue Dream - 3.5g: 12.50 (from purchase order)
```

## Edge Cases Handled

1. **No Stock History Available**: Uses default cost from stock API
2. **No Purchase Orders**: Uses default cost
3. **Multiple Purchase Orders**: Uses the most recent one
4. **Missing Cost in Purchase Order**: Ignores that movement, tries others
5. **Product Not Found in History**: Uses default cost
6. **API Failure**: Gracefully continues with default costs

## Future Enhancements

Potential improvements for future iterations:

1. **Weighted Average**: Calculate weighted average cost based on quantity remaining from each purchase
2. **FIFO/LIFO**: Implement First-In-First-Out or Last-In-First-Out cost accounting
3. **Cost History Chart**: Show cost trends over time
4. **Alert System**: Notify when buy price exceeds sell price (negative margin)
5. **Bulk Update**: Allow updating default costs based on latest purchase orders

## Testing

To verify the implementation:

1. Add stock via purchase order with specific cost
2. Open Product Management screen
3. Verify buy price matches the purchase order cost
4. Check logs for "Using stock movement cost" messages
5. Compare with default cost in database

## Notes

- The feature requires the `stock-history` API endpoint to be available
- If the API is not available, the app continues to work with static costs
- Timestamp parsing handles both ISO 8601 formats with/without milliseconds
- Product matching prioritizes `product_id` over `product_name` for accuracy

