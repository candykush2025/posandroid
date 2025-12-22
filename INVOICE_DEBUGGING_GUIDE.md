# Invoice Details Debugging Guide

## Quick Logcat Check

Run this command in your terminal:
```powershell
adb logcat -c; adb logcat -v time | Select-String "InvoiceDetail|InvoiceApi|poscandykush"
```

Then open the Invoice Details screen in the app.

## What to Look For

### 1. Check if invoice_id is received:
```
D/InvoiceDetailActivity: Loading invoice detail for ID: inv_xxx
```
- If you see "Invalid invoice ID" → The intent extra is not being passed
- If you see the ID → Good, moving to next step

### 2. Check API call:
```
D/InvoiceDetailActivity: === Fetching Invoice Details ===
D/InvoiceDetailActivity: Invoice ID: inv_xxx
D/InvoiceDetailActivity: Token available: true
```

### 3. Check API response:
```
D/InvoiceApiService: getInvoice response code: 200, body: {...}
D/InvoiceDetailActivity: === API Response ===
D/InvoiceDetailActivity: Success: true
```
- If response code is 404 → Invoice not found in database
- If response code is 401 → Token expired/invalid
- If success is false → Check the error message

### 4. Check invoice location:
```
D/InvoiceDetailActivity: Invoice at root: null
D/InvoiceDetailActivity: Invoice in data: Invoice(...)
```
This tells you where the invoice was found in the JSON response.

### 5. Success:
```
D/InvoiceDetailActivity: === Invoice Found ===
D/InvoiceDetailActivity: Number: INV-2025-001
D/InvoiceDetailActivity: Customer: John Doe
```

## Common Issues

### Issue 1: "Invalid invoice ID"
**Cause:** Intent extra not being passed correctly
**Fix:** Check CustomerInvoiceActivity line 176:
```kotlin
intent.putExtra("invoice_id", invoice.id)
```
Make sure `invoice.id` is not empty.

### Issue 2: Invoice shows null in all locations
**Cause:** API response doesn't match expected format
**Solution:** Check the actual API response body in logcat:
```
D/InvoiceApiService: getInvoice response code: 200, body: {"success":true,...}
```
Copy the body and check the structure.

### Issue 3: Network/Token errors
**Symptoms:**
```
E/InvoiceApiService: API call unsuccessful: 401
```
or
```
E/InvoiceDetailActivity: Exception: Connection timeout
```

**Fix:**
- Check internet connection
- Verify JWT token hasn't expired
- Check API server is running

## Manual Testing Steps

1. **Clear logcat:**
   ```powershell
   adb logcat -c
   ```

2. **Start monitoring:**
   ```powershell
   adb logcat -v time | Select-String "InvoiceDetail"
   ```

3. **In the app:**
   - Go to Customer Invoices
   - Click on any invoice
   - Watch the logcat output

4. **Analyze:**
   - Find where it stops in the sequence above
   - Check the error message at that point

## Expected Full Log Sequence

```
12-20 21:00:00.123 D/InvoiceDetailActivity: Loading invoice detail for ID: inv_123
12-20 21:00:00.124 D/InvoiceDetailActivity: === Fetching Invoice Details ===
12-20 21:00:00.125 D/InvoiceDetailActivity: Invoice ID: inv_123
12-20 21:00:00.126 D/InvoiceDetailActivity: Token available: true
12-20 21:00:00.200 D/InvoiceApiService: getInvoice response code: 200, body: {...}
12-20 21:00:00.201 D/InvoiceDetailActivity: === API Response ===
12-20 21:00:00.202 D/InvoiceDetailActivity: Success: true
12-20 21:00:00.203 D/InvoiceDetailActivity: Error: null
12-20 21:00:00.204 D/InvoiceDetailActivity: Data object: InvoiceData(...)
12-20 21:00:00.205 D/InvoiceDetailActivity: Invoice at root: null
12-20 21:00:00.206 D/InvoiceDetailActivity: Invoice in data: Invoice(...)
12-20 21:00:00.207 D/InvoiceDetailActivity: === Invoice Found ===
12-20 21:00:00.208 D/InvoiceDetailActivity: Number: INV-2025-001
12-20 21:00:00.209 D/InvoiceDetailActivity: Status: pending
12-20 21:00:00.210 D/InvoiceDetailActivity: Customer: John Doe
12-20 21:00:00.211 D/InvoiceDetailActivity: Total: 150.0
12-20 21:00:00.212 D/InvoiceDetailActivity: Items count: 3
12-20 21:00:00.213 D/InvoiceDetailActivity: Invoice fetched successfully, updating UI
```

If any step is missing, that's where the problem is!

## Quick PowerShell Script

Save this as `check_invoice.ps1`:
```powershell
Write-Host "=== Invoice Details Debugger ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "Clearing logcat..." -ForegroundColor Yellow
adb logcat -c
Write-Host ""
Write-Host "Monitoring logs. Open an invoice in the app now..." -ForegroundColor Green
Write-Host "Press Ctrl+C to stop" -ForegroundColor Yellow
Write-Host ""
adb logcat -v time | Select-String "InvoiceDetailActivity|InvoiceApiService" | ForEach-Object { 
    if ($_ -match "ERROR|Exception") {
        Write-Host $_ -ForegroundColor Red
    } elseif ($_ -match "Invoice Found") {
        Write-Host $_ -ForegroundColor Green
    } else {
        Write-Host $_
    }
}
```

Run it: `powershell .\check_invoice.ps1`

