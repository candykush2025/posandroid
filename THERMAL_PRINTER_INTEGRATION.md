# Thermal Printer Integration - Implementation Complete ✅

## Overview
The POS system now integrates with the thermal print API and Bluetooth thermal printers to automatically print receipts when print jobs are available.

## What Was Added

### 1. **PrintApiService.kt** - Print API Client
- Polls the `/api/print` endpoint every 2 seconds
- Retrieves print jobs from the server
- Automatically deletes jobs after retrieval (prevents duplicates)
- Includes error handling and detailed logging

### 2. **BluetoothThermalPrinter.kt** - Bluetooth Printer Manager
- Manages Bluetooth connections to thermal printers
- Sends formatted receipt data to printer
- Formats receipts with:
  - "CANDY KUSH" title (centered)
  - Item list with quantity and total
  - Grand total
  - Timestamp
  - Thank you message
  - Paper cut command

### 3. **Updated CustomerPresentation.kt**
- Stores current cart data
- Polls for print jobs every 2 seconds
- Automatically connects to printer when needed
- Prints formatted receipt with all cart details
- Properly cleans up resources on exit

### 4. **Updated AndroidManifest.xml**
- Added required Bluetooth permissions:
  - `BLUETOOTH`
  - `BLUETOOTH_ADMIN`
  - `BLUETOOTH_CONNECT`
  - `BLUETOOTH_SCAN`

## How It Works

### Flow:
1. **Cart Update** → CustomerPresentation receives cart data and stores it in `currentCart`
2. **Continuous Polling** → Every 2 seconds, checks `/api/print` endpoint for print jobs
3. **Print Job Found** → API returns print job with printer device name
4. **Connect to Printer** → Bluetooth connection established to named device
5. **Generate Receipt** → Formats receipt with:
   ```
   ════════════════════════════════
                 CANDY KUSH
   ════════════════════════════════
   
   ITEM              QTY       TOTAL
   ─────────────────────────────────
   L.C.G           0.20      ฿24.00
   ─────────────────────────────────
                        TOTAL: ฿24.00
   
                   Thank you!
               2025-11-13T13:36:54.758Z
   ════════════════════════════════
   ```
6. **Send to Printer** → Data sent via Bluetooth
7. **Job Deleted** → API automatically deletes job after retrieval
8. **Repeat** → Continue polling every 2 seconds

## Usage

### Prerequisites:
1. Thermal printer paired with Android device via Bluetooth
2. Device has Bluetooth enabled
3. Correct printer device name in print job data

### Print Job Format:
The print job data can be in two formats:

**Format 1 - With Printer Name:**
```
THERMAL_PRINTER:print_command_data
```

**Format 2 - Default Printer:**
```
print_command_data
```

The system will extract "THERMAL_PRINTER" from Format 1 or use default name for Format 2.

### Example Workflow:

1. **Admin sends print command** via web interface
2. **API creates print job** at `/api/print` endpoint
3. **CustomerPresentation polls** and finds the job
4. **System connects** to Bluetooth printer
5. **Receipt prints** with all cart details
6. **Job deleted** automatically

## Configuration

### Change Poll Interval:
In `CustomerPresentation.kt`, modify:
```kotlin
handler.postDelayed(this, 2000) // Change 2000 to desired milliseconds
```

### Change Printer Device Name:
The system extracts printer name from print data. If using default:
```kotlin
val printerDeviceName = "THERMAL_PRINTER"
```

### Change Receipt Format:
In `BluetoothThermalPrinter.kt`, modify `buildReceiptData()` function

## Troubleshooting

### Print Not Working:
1. **Check Logcat** for errors with tag `BluetoothThermalPrinter` or `CustomerPresentation`
2. **Verify Bluetooth** pairing in device settings
3. **Check device name** - must match printer's paired name exactly
4. **Check permissions** - need runtime permission grants on Android 6+
5. **Verify API** - ensure print job endpoint is accessible

### Connection Issues:
- Ensure printer is powered on
- Confirm printer is in discoverable mode
- Check that printer device name in print data matches paired device name
- Verify socket UUID is correct (standard: `00001101-0000-1000-8000-00805F9B34FB`)

### Print Quality Issues:
- Adjust character width in receipt formatting
- Check printer paper width (currently formatted for ~32 characters)
- Verify thermal printer font settings

## Files Created/Modified

**New Files:**
- `PrintApiService.kt` - Print API client
- `BluetoothThermalPrinter.kt` - Bluetooth printer manager

**Modified Files:**
- `CustomerPresentation.kt` - Added print polling and printing logic
- `AndroidManifest.xml` - Added Bluetooth permissions

## Logging Tags

Monitor these in Logcat:
- `PrintApiService` - Print API communications
- `BluetoothThermalPrinter` - Bluetooth operations
- `CustomerPresentation` - Print flow and errors

## Security Notes

- Bluetooth connection is local-only (no internet)
- Print jobs are deleted immediately after retrieval
- Device must be paired beforehand (no ad-hoc pairing)
- Printer name is not hardcoded - comes from API response

## Future Enhancements

Potential improvements:
1. Add print queue management
2. Implement retry logic for failed prints
3. Add print preview on screen before sending to printer
4. Support multiple printers
5. Add receipt customization options
6. Implement printer status checking
7. Add barcode/QR code generation for receipts

