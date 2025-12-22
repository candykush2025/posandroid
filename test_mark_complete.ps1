# Test Purchase Mark Complete Feature
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host "Testing Purchase Mark Complete Fix" -ForegroundColor Cyan
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host ""

# Clear logcat
Write-Host "Clearing logcat..." -ForegroundColor Yellow
adb logcat -c

Write-Host ""
Write-Host "Now please:" -ForegroundColor Green
Write-Host "1. Open the app" -ForegroundColor White
Write-Host "2. Go to Purchasing section" -ForegroundColor White
Write-Host "3. Open a pending purchase" -ForegroundColor White
Write-Host "4. Click 'Mark Complete' button" -ForegroundColor White
Write-Host ""
Write-Host "Press ENTER when you're ready to see the logs..." -ForegroundColor Yellow
Read-Host

Write-Host ""
Write-Host "Fetching logs..." -ForegroundColor Cyan
Write-Host ""

# Get the logs
adb logcat -d | Select-String -Pattern "PurchaseDetailActivity|PurchaseApiService" | Select-Object -Last 30

Write-Host ""
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host "Look for these success indicators:" -ForegroundColor Green
Write-Host "  - Request body: {`"id`":`"...<purchase_id>...`"}" -ForegroundColor White
Write-Host "  - Response code: 200" -ForegroundColor White
Write-Host "  - Returned purchase status: completed" -ForegroundColor White
Write-Host "=====================================" -ForegroundColor Cyan

