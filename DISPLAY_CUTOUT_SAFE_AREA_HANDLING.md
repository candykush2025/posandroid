# Display Cutout (Notch/Camera Hole) Safe Area Handling

## Overview
This document explains how the POS Candy Kush Android application handles display cutouts (notches, camera holes, punch-hole displays) to ensure UI elements don't appear behind these hardware features.

## What are Display Cutouts?
Display cutouts are areas on modern Android devices where the screen is cut out to accommodate front-facing cameras, speakers, or other sensors. Common types include:
- **Notches** (e.g., iPhone-style notches)
- **Punch-hole cameras** (circular cutouts)
- **Waterfall displays** (curved edges)

## Problem
Without proper handling, UI elements like headers, text, and buttons can appear behind these cutouts, making them unreadable or inaccessible.

## Solution: Window Insets and Display Cutout Handling

### 1. Enable Content Extension into Cutouts
Set the window's `layoutInDisplayCutoutMode` to allow content to extend into the cutout area:

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
}
```

### 2. Apply Window Insets to Affected Views
Use `ViewCompat.setOnApplyWindowInsetsListener` to apply padding based on display cutout insets:

```kotlin
ViewCompat.setOnApplyWindowInsetsListener(mainContent) { view, insets ->
    val cutoutInsets = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
    val systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
    val topInset = maxOf(cutoutInsets.top, systemBarInsets.top)
    
    // Extend status bar background to cover the full top area (including cutouts)
    statusBarBackground.layoutParams.height = topInset
    
    // Apply padding for cutouts, but no top padding since status bar background handles it
    view.setPadding(
        cutoutInsets.left,
        0,  // No top padding needed
        cutoutInsets.right,
        view.paddingBottom
    )
    insets
}
```

## Implementation in POS Candy Kush

### Files Modified
- `CustomerInvoiceActivity.kt`
- `activity_customer_invoice.xml`

### Layout Changes
Added IDs to the main content and header LinearLayouts for programmatic access:

```xml
<LinearLayout
    android:id="@+id/main_content"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="#FAFAFA">

<LinearLayout
    android:id="@+id/header_layout"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="@color/primary_green"
    android:orientation="vertical"
    android:paddingHorizontal="20dp"
    android:paddingTop="16dp"
    android:paddingBottom="24dp"
    android:elevation="4dp">
```

### Kotlin Implementation
```kotlin
// In setupStatusBar()
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
}

// In initializeViews()
val mainContent = findViewById<View>(R.id.main_content)
ViewCompat.setOnApplyWindowInsetsListener(mainContent) { view, insets ->
    val cutoutInsets = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
    val systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
    val topInset = maxOf(cutoutInsets.top, systemBarInsets.top)
    
    // Extend status bar background to cover the full top area (including cutouts)
    statusBarBackground.layoutParams.height = topInset
    
    // Apply padding for cutouts, but no top padding since status bar background handles it
    view.setPadding(
        cutoutInsets.left,
        0,  // No top padding needed
        cutoutInsets.right,
        view.paddingBottom
    )
    insets
}
```

## How It Works
1. **Content Extension**: `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES` allows the app to draw behind the cutout area.
2. **Inset Detection**: `WindowInsetsCompat.Type.displayCutout()` and `WindowInsetsCompat.Type.systemBars()` provide the dimensions of cutouts and system bars.
3. **Dynamic Background Extension**: The status bar background View height is set to the maximum of cutout and system bar top insets, ensuring the green background covers the entire top area.
4. **Content Positioning**: The main content is positioned below this extended background, with side padding for cutouts but no top padding needed.

## Result
- ✅ Header text no longer appears behind camera notches
- ✅ UI remains readable on all device types
- ✅ No extra padding on devices without cutouts
- ✅ Consistent appearance across different Android versions

## Supported Android Versions
- **API 28+ (Android 9)**: Basic display cutout support
- **API 29+ (Android 10)**: Enhanced cutout handling with `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES`
- **API 30+ (Android 11)**: Full WindowInsets API

## Testing
Test on devices with:
- Punch-hole cameras (e.g., Samsung, Google Pixel)
- Notched displays (e.g., Huawei, Asus)
- No cutouts (standard devices)

## Additional Resources
- [Android Display Cutout Documentation](https://developer.android.com/guide/topics/display-cutout)
- [WindowInsets API Guide](https://developer.android.com/reference/androidx/core/view/WindowInsetsCompat)
- [Handling Display Cutouts - Android Developers](https://developer.android.com/develop/ui/views/layout/display-cutout)
