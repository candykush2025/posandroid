# App Structure Diagram

## Application Entry Points

```
AndroidManifest.xml
│
├── LAUNCHER (App Icon Click)
│   └── MenuActivity ★ NEW ENTRY POINT
│       │
│       ├── [Admin Dashboard Button]
│       │   └── AdminActivity (Future Development)
│       │       ├── Sales Reports (Placeholder)
│       │       ├── Inventory Management (Placeholder)
│       │       ├── User Management (Placeholder)
│       │       └── Settings (Placeholder)
│       │
│       └── [POS Terminal Button]
│           ├── Check Bluetooth Permissions
│           │   ├── ✓ Granted → MainActivity (POS)
│           │   └── ✗ Denied → Request Dialog
│           │
│           └── MainActivity (Original POS System)
│               ├── WebView (POS Interface)
│               ├── Cart Polling
│               ├── Print Job Handling
│               ├── Dual Display Support
│               └── Printer Configuration
│
├── PrinterSetupActivity (from MainActivity and MenuActivity)
└── CustomerPresentation (Secondary Display)
```

## Activity Relationships

```
┌────────────────────────────────────────────────────────────┐
│                    MenuActivity                            │
│  ┌──────────────┐              ┌──────────────┐           │
│  │    Admin     │              │     POS      │           │
│  │  Dashboard   │              │   Terminal   │           │
│  └──────────────┘              └──────────────┘           │
└────────┬──────────────────────────────┬──────────────────┘
         │                               │
         ▼                               ▼
┌─────────────────┐         ┌─────────────────────────────┐
│ AdminActivity   │         │ Bluetooth Permission Check  │
│                 │         └──────────────┬──────────────┘
│ • Back Button   │                        │
│ • Feature Cards │                        ▼
│                 │         ┌─────────────────────────────┐
└─────────────────┘         │    MainActivity (POS)       │
                            │                             │
                            │ • WebView POS Interface     │
                            │ • Cart Management           │
                            │ • Printer Integration       │
                            │ • Update Checker            │
                            │ • Customer Display Support  │
                            └─────────────────────────────┘
```

## Permission Flow

```
User Clicks "POS Terminal"
         │
         ▼
┌─────────────────────────────────┐
│ Check Bluetooth Permissions     │
└─────────┬───────────────────────┘
          │
    ┌─────┴─────┐
    │           │
    ▼           ▼
GRANTED      DENIED
    │           │
    │           ▼
    │   ┌──────────────────────┐
    │   │ Show Rationale Dialog│
    │   │  "Bluetooth needed   │
    │   │   for printing"      │
    │   └──────┬───────────────┘
    │          │
    │     ┌────┴────┐
    │     │         │
    │     ▼         ▼
    │  GRANT    DENY
    │     │         │
    │     │         ▼
    │     │   ┌──────────────┐
    │     │   │ Show message │
    │     │   │ "Enable in   │
    │     │   │  Settings"   │
    │     │   └──────────────┘
    │     │
    └─────┴────────┐
                   ▼
          ┌─────────────────┐
          │ Open MainActivity│
          │    (POS Mode)    │
          └─────────────────┘
```

## File Structure

```
app/src/main/
│
├── java/com/blackcode/poscandykush/
│   ├── MenuActivity.kt          ★ NEW - Main menu launcher
│   ├── AdminActivity.kt         ★ NEW - Admin dashboard
│   ├── MainActivity.kt          ✓ MODIFIED - POS system
│   ├── CartViewModel.kt
│   ├── CartApiService.kt
│   ├── PrintApiService.kt
│   ├── BluetoothThermalPrinter.kt
│   ├── CustomerPresentation.kt
│   └── PrinterSetupActivity.kt
│
├── res/layout/
│   ├── activity_menu.xml        ★ NEW - Menu screen layout
│   ├── activity_admin.xml       ★ NEW - Admin dashboard layout
│   ├── activity_main.xml
│   ├── activity_printer_setup.xml
│   └── activity_presentation.xml
│
└── AndroidManifest.xml          ✓ MODIFIED - New launcher config
```

## User Scenarios

### Scenario 1: Admin User (Mobile Device)
```
Launch App → Menu → Admin Dashboard → View/Manage → Back to Menu
```

### Scenario 2: POS User (Tablet)
```
Launch App → Menu → POS Terminal → Grant Bluetooth → POS Interface
```

### Scenario 3: First-Time POS User
```
Launch App → Menu → POS Terminal 
    → Request Bluetooth Permission
    → Explain Why Needed
    → Grant Permission
    → POS Interface
    → Printer Setup Wizard (if first time)
```

## Key Changes Summary

| Component | Before | After |
|-----------|--------|-------|
| **Launcher** | MainActivity | MenuActivity |
| **POS Access** | Direct | Via Menu + Permissions |
| **Admin Features** | None | AdminActivity (placeholder) |
| **Permission Handling** | In MainActivity | In MenuActivity |
| **User Choice** | None | Admin or POS mode |

## Benefits

✅ **Clear Separation**: Admin and POS functions are separate
✅ **Better UX**: Users know what mode they're entering
✅ **Permission Flow**: Bluetooth only requested for POS
✅ **Scalability**: Easy to add more modes/features
✅ **Mobile-Friendly**: Admin dashboard can be mobile-optimized
✅ **Tablet-Optimized**: POS remains tablet-focused
