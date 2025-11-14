# Thermal Printer Quick Start Guide

## 🚀 Quick Setup

### Step 1: Pair Thermal Printer
1. Go to **Settings** → **Bluetooth** on your Android device
2. Put thermal printer in pairing mode
3. Select printer from available devices
4. Confirm pairing (usually prints a test page)
5. **Note the exact device name** (e.g., "THERMAL_PRINTER", "ESC_POS_32", etc.)

### Step 2: Verify Print API
- Ensure print API is accessible: `https://pos-candy-kush.vercel.app/api/print`
- Test with Postman or similar:
  ```json
  POST /api/print
  {
    "data": "THERMAL_PRINTER:test_print_data"
  }
  ```

### Step 3: App Permissions
- Android 6+: Grant Bluetooth permissions when app starts
  - BLUETOOTH
  - BLUETOOTH_ADMIN
  - BLUETOOTH_CONNECT
  - BLUETOOTH_SCAN

### Step 4: Run App
1. Build and run the app
2. App automatically starts polling for print jobs
3. When print job arrives, receipt prints automatically

## 📋 Print Job Format

When submitting print jobs to `/api/print`:

```json
{
  "data": "THERMAL_PRINTER_NAME:print_data"
}
```

Or without device name (uses default):
```json
{
  "data": "print_data"
}
```

## 🔍 How to Check Printer Name

**Option 1: Settings**
- Settings → Bluetooth → Paired devices
- Find your thermal printer
- Device name shown is what you need

**Option 2: Logcat**
- When app attempts connection, check logs:
  ```
  BluetoothThermalPrinter: Connected to [DEVICE_NAME]
  BluetoothThermalPrinter: Device not found: [DEVICE_NAME]  <- Wrong name
  ```

## ✅ Testing

### Test 1: Check Print Polling
- Open app
- Check Logcat: should see "Print job retrieved" every 2 seconds

### Test 2: Send Test Print Job
```bash
curl -X POST https://pos-candy-kush.vercel.app/api/print \
  -H "Content-Type: application/json" \
  -d '{"data": "THERMAL_PRINTER:test"}'
```

### Test 3: Monitor Receipt Printing
1. Send print job with printer name
2. Check Logcat for:
   ```
   Receipt printed successfully
   ```
3. Check printer output

## 🐛 Troubleshooting

| Problem | Solution |
|---------|----------|
| **No print happens** | Check Logcat for printer name errors |
| **"Device not found"** | Verify printer device name exactly matches |
| **Bluetooth permission error** | Grant permissions in app settings |
| **API not connecting** | Verify internet connection and API URL |
| **Garbled text on printer** | Check printer charset (UTF-8 expected) |

## 📊 Logcat Tags to Monitor

```
BluetoothThermalPrinter   - Bluetooth operations
PrintApiService           - Print API calls
CustomerPresentation      - Print flow
```

## 💡 Pro Tips

1. **Always pair printer first** - App needs pre-paired devices
2. **Keep printer nearby** - Bluetooth range ~10 meters
3. **Power on printer** - It must be active to connect
4. **Check printer paper** - Some won't connect if out of paper
5. **Monitor logs** - Logcat is your best friend for debugging

## 🔧 Configuration

### Change Poll Interval
In `CustomerPresentation.kt`:
```kotlin
handler.postDelayed(this, 2000) // milliseconds (currently 2 seconds)
```

### Change Default Printer Name
In `CustomerPresentation.kt`:
```kotlin
val printerDeviceName = "YOUR_PRINTER_NAME" ?: "THERMAL_PRINTER"
```

### Customize Receipt Format
In `BluetoothThermalPrinter.kt`, modify `buildReceiptData()`:
- Change title from "CANDY KUSH"
- Adjust column widths
- Add/remove sections

## 📱 Supported Android Versions

- **Min:** Android 5.0 (API 21)
- **Target:** Android 14+ (API 34+)
- **Tested on:** Android 11+

## ⚙️ Hardware Requirements

- Thermal printer with Bluetooth (ESC/POS compatible)
- Android device with Bluetooth 4.0+
- Paper roll for thermal printer

## 📞 Support

Check these files for more info:
- `THERMAL_PRINTER_INTEGRATION.md` - Full technical details
- `PrintApiService.kt` - API client code
- `BluetoothThermalPrinter.kt` - Printer control code
- `CustomerPresentation.kt` - Integration point

