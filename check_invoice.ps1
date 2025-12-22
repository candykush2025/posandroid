Write-Host "=== Invoice Details Debugger ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "Clearing logcat..." -ForegroundColor Yellow
adb logcat -c
Start-Sleep -Seconds 1
Write-Host ""
Write-Host "Monitoring logs. Open an invoice in the app now..." -ForegroundColor Green
Write-Host "Press Ctrl+C to stop" -ForegroundColor Yellow
Write-Host ""
Write-Host "Waiting for logs..." -ForegroundColor Gray
Write-Host "============================================" -ForegroundColor Gray
Write-Host ""

adb logcat -v time | ForEach-Object {
    if ($_ -match "InvoiceDetailActivity|InvoiceApiService") {
        if ($_ -match "ERROR|Exception") {
            Write-Host $_ -ForegroundColor Red
        } elseif ($_ -match "Invoice Found|successfully") {
            Write-Host $_ -ForegroundColor Green
        } elseif ($_ -match "=== ") {
            Write-Host $_ -ForegroundColor Cyan
        } else {
            Write-Host $_
        }
    }
}

