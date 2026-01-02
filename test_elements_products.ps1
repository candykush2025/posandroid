# Test Script: Check for Elements Products
# This script helps diagnose why "Elements 5m Roll" and "Element 5m rolls" might not both show

Write-Host "=== CHECKING FOR ELEMENTS PRODUCTS ===" -ForegroundColor Cyan
Write-Host ""

# Clear logcat
Write-Host "Clearing logcat..." -ForegroundColor Yellow
adb logcat -c

# Start monitoring logcat in background
Write-Host "Monitoring logcat for product data..." -ForegroundColor Yellow
$logFile = "elements_products_debug.txt"

# Launch the app and navigate to Product Management
Write-Host ""
Write-Host "Please:" -ForegroundColor Green
Write-Host "  1. Open the app" -ForegroundColor White
Write-Host "  2. Navigate to Product Management" -ForegroundColor White
Write-Host "  3. Wait for products to load" -ForegroundColor White
Write-Host "  4. Press ENTER when done" -ForegroundColor White
Write-Host ""

# Capture logcat
$process = Start-Process -FilePath "adb" -ArgumentList "logcat -s ProductManagement:*" -NoNewWindow -RedirectStandardOutput $logFile -PassThru

# Wait for user
Read-Host "Press ENTER when you've loaded Product Management"

# Stop logcat capture
Stop-Process -Id $process.Id -Force

Write-Host ""
Write-Host "=== ANALYZING LOGS ===" -ForegroundColor Cyan

# Read the log file
$logs = Get-Content $logFile

# Find all product entries
Write-Host ""
Write-Host "--- All Products from API ---" -ForegroundColor Yellow
$logs | Select-String -Pattern "\[.*\] ID: .* \| Name: .* \|" | ForEach-Object {
    Write-Host $_.Line
}

Write-Host ""
Write-Host "--- Searching for 'Element' products ---" -ForegroundColor Yellow
$elementProducts = $logs | Select-String -Pattern "Element" -SimpleMatch
if ($elementProducts) {
    $elementProducts | ForEach-Object {
        $line = $_.Line
        if ($line -match "Name: (.*)") {
            Write-Host $line -ForegroundColor White
        }
    }
} else {
    Write-Host "No 'Element' products found in logs!" -ForegroundColor Red
}

Write-Host ""
Write-Host "--- Added Products ---" -ForegroundColor Yellow
$logs | Select-String -Pattern "✓ Added product:" | ForEach-Object {
    if ($_.Line -match "Element") {
        Write-Host $_.Line -ForegroundColor Green
    }
}

Write-Host ""
Write-Host "--- Skipped/Duplicate Products ---" -ForegroundColor Yellow
$logs | Select-String -Pattern "✗ Skipping DUPLICATE:" | ForEach-Object {
    if ($_.Line -match "Element") {
        Write-Host $_.Line -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "--- Summary Statistics ---" -ForegroundColor Yellow
$totalCount = ($logs | Select-String -Pattern "Total items from API:" | Select-Object -Last 1).Line
$uniqueCount = ($logs | Select-String -Pattern "After deduplication:" | Select-Object -Last 1).Line
Write-Host $totalCount -ForegroundColor White
Write-Host $uniqueCount -ForegroundColor White

Write-Host ""
Write-Host "=== FULL LOG SAVED TO: $logFile ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "Next Steps:" -ForegroundColor Green
Write-Host "  1. Check if both 'Elements 5m Roll' and 'Element 5m rolls' appear in 'All Products from API'" -ForegroundColor White
Write-Host "  2. If yes, check if both are in 'Added Products' or if one is in 'Skipped/Duplicate'" -ForegroundColor White
Write-Host "  3. Compare their product_id values" -ForegroundColor White
Write-Host ""

