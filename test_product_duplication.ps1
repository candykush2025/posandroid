# Test Product Duplication - API Response Checker
# This script fetches the stock API and checks for duplicate products

Write-Host "================================================" -ForegroundColor Cyan
Write-Host "   Product Duplication API Test" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""

# Step 1: Get JWT token from SharedPreferences
Write-Host "Step 1: Getting JWT token from app..." -ForegroundColor Yellow

# You need to provide your JWT token here
# To get it: Run the app, login, then check Android Studio's Device File Explorer:
# /data/data/com.blackcode.poscandykush/shared_prefs/admin_prefs.xml
$token = Read-Host "Enter your JWT token (from SharedPreferences)"

if ([string]::IsNullOrWhiteSpace($token)) {
    Write-Host "ERROR: No token provided!" -ForegroundColor Red
    Write-Host ""
    Write-Host "To get your token:" -ForegroundColor Yellow
    Write-Host "1. Open Android Studio" -ForegroundColor White
    Write-Host "2. Run the app and login" -ForegroundColor White
    Write-Host "3. Go to View > Tool Windows > Device File Explorer" -ForegroundColor White
    Write-Host "4. Navigate to: /data/data/com.blackcode.poscandykush/shared_prefs/" -ForegroundColor White
    Write-Host "5. Open admin_prefs.xml" -ForegroundColor White
    Write-Host "6. Copy the value of jwt_token" -ForegroundColor White
    Write-Host ""
    exit
}

Write-Host "✓ Token received" -ForegroundColor Green
Write-Host ""

# Step 2: Fetch stock data from API
Write-Host "Step 2: Fetching stock data from API..." -ForegroundColor Yellow

$apiUrl = "https://pos-candy-kush.vercel.app/api/mobile?action=stock"
$headers = @{
    "Authorization" = "Bearer $token"
    "Content-Type" = "application/json"
}

try {
    $response = Invoke-RestMethod -Uri $apiUrl -Headers $headers -Method Get -ErrorAction Stop
    Write-Host "✓ API call successful" -ForegroundColor Green
    Write-Host ""

    # Save full response to file
    $response | ConvertTo-Json -Depth 10 | Out-File "stock_api_response.json" -Encoding UTF8
    Write-Host "✓ Full response saved to: stock_api_response.json" -ForegroundColor Green
    Write-Host ""

} catch {
    Write-Host "ERROR: API call failed!" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    Write-Host ""
    Write-Host "Possible reasons:" -ForegroundColor Yellow
    Write-Host "- Token expired (login again and get new token)" -ForegroundColor White
    Write-Host "- Network connection issue" -ForegroundColor White
    Write-Host "- API endpoint changed" -ForegroundColor White
    exit
}

# Step 3: Analyze for duplicates
Write-Host "Step 3: Analyzing for duplicate products..." -ForegroundColor Yellow
Write-Host ""

if ($response.success -eq $false) {
    Write-Host "ERROR: API returned success=false" -ForegroundColor Red
    Write-Host "Error message: $($response.error)" -ForegroundColor Red
    exit
}

$items = $response.data.items
$totalItems = $items.Count

Write-Host "================================================" -ForegroundColor Cyan
Write-Host "   ANALYSIS RESULTS" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "Total items from API: $totalItems" -ForegroundColor White

# Group by product_id
$byProductId = $items | Group-Object product_id
$uniqueProductIds = $byProductId.Count

Write-Host "Unique product IDs: $uniqueProductIds" -ForegroundColor White

# Group by product_name
$byProductName = $items | Group-Object product_name
$uniqueProductNames = $byProductName.Count

Write-Host "Unique product names: $uniqueProductNames" -ForegroundColor White
Write-Host ""

# Find duplicates by product_id
Write-Host "------------------------------------------------" -ForegroundColor Cyan
Write-Host "Checking for duplicate product_id values..." -ForegroundColor Cyan
Write-Host "------------------------------------------------" -ForegroundColor Cyan

$duplicateIds = $byProductId | Where-Object { $_.Count -gt 1 }

if ($duplicateIds.Count -gt 0) {
    Write-Host "⚠️  DUPLICATES FOUND: $($duplicateIds.Count) product IDs appear multiple times!" -ForegroundColor Red
    Write-Host ""

    foreach ($dup in $duplicateIds) {
        Write-Host "Product ID '$($dup.Name)' appears $($dup.Count) times:" -ForegroundColor Yellow
        foreach ($item in $dup.Group) {
            Write-Host "  - Name: '$($item.product_name)', Category: '$($item.category)'" -ForegroundColor White
        }
        Write-Host ""
    }
} else {
    Write-Host "✓ No duplicate product IDs found" -ForegroundColor Green
    Write-Host ""
}

# Find duplicates by product_name
Write-Host "------------------------------------------------" -ForegroundColor Cyan
Write-Host "Checking for duplicate product_name values..." -ForegroundColor Cyan
Write-Host "------------------------------------------------" -ForegroundColor Cyan

$duplicateNames = $byProductName | Where-Object { $_.Count -gt 1 }

if ($duplicateNames.Count -gt 0) {
    Write-Host "⚠️  DUPLICATES FOUND: $($duplicateNames.Count) product names appear multiple times!" -ForegroundColor Red
    Write-Host ""

    # Save detailed duplicate report
    $duplicateReport = @()

    foreach ($dup in $duplicateNames) {
        Write-Host "Product Name '$($dup.Name)' appears $($dup.Count) times:" -ForegroundColor Yellow

        foreach ($item in $dup.Group) {
            Write-Host "  - ID: '$($item.product_id)', Category: '$($item.category)', Cost: $($item.cost), Price: $($item.price)" -ForegroundColor White

            $duplicateReport += [PSCustomObject]@{
                ProductName = $item.product_name
                ProductId = $item.product_id
                Category = $item.category
                Cost = $item.cost
                Price = $item.price
                CurrentStock = $item.current_stock
            }
        }
        Write-Host ""
    }

    # Save duplicate report to CSV
    $duplicateReport | Export-Csv -Path "duplicate_products_report.csv" -NoTypeInformation -Encoding UTF8
    Write-Host "✓ Detailed duplicate report saved to: duplicate_products_report.csv" -ForegroundColor Green
    Write-Host ""

} else {
    Write-Host "✓ No duplicate product names found" -ForegroundColor Green
    Write-Host ""
}

# Summary
Write-Host "================================================" -ForegroundColor Cyan
Write-Host "   SUMMARY" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""

$duplicatesFound = ($duplicateIds.Count -gt 0) -or ($duplicateNames.Count -gt 0)

if ($duplicatesFound) {
    Write-Host "⚠️  ISSUE CONFIRMED: The API is returning duplicate products!" -ForegroundColor Red
    Write-Host ""
    Write-Host "Impact:" -ForegroundColor Yellow
    Write-Host "- Users see products multiple times in the list" -ForegroundColor White
    Write-Host "- API returns $totalItems items but only $uniqueProductIds are unique" -ForegroundColor White
    Write-Host ""
    Write-Host "Good News:" -ForegroundColor Green
    Write-Host "✓ The Android app fix (already implemented) will filter these out" -ForegroundColor White
    Write-Host "✓ Users won't see duplicates in the app anymore" -ForegroundColor White
    Write-Host ""
    Write-Host "Next Steps:" -ForegroundColor Yellow
    Write-Host "1. Share duplicate_products_report.csv with backend developer" -ForegroundColor White
    Write-Host "2. Backend needs to fix the SQL query or database" -ForegroundColor White
    Write-Host "3. Test again after backend fix" -ForegroundColor White

} else {
    Write-Host "✓ NO DUPLICATES: The API is returning clean data!" -ForegroundColor Green
    Write-Host ""
    Write-Host "If you're still seeing duplicates in the app:" -ForegroundColor Yellow
    Write-Host "1. Clear app cache" -ForegroundColor White
    Write-Host "2. Force pull fresh data" -ForegroundColor White
    Write-Host "3. Check if another API endpoint is being used" -ForegroundColor White
}

Write-Host ""
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""

# Open files
Write-Host "Opening generated files..." -ForegroundColor Yellow
Start-Process "stock_api_response.json"

if (Test-Path "duplicate_products_report.csv") {
    Start-Process "duplicate_products_report.csv"
}

Write-Host ""
Write-Host "Test complete!" -ForegroundColor Green

