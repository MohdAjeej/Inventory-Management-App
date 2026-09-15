# Test Add Inventory - Troubleshooting Guide

## Backend Test ✅ WORKING
Tested via PowerShell:
```
POST http://192.168.29.166:8082/api/inventory/products?businessId=1&userId=1
Body: {"category":"Electronics","name":"Test Product","brand":"Test Brand","unit":"Piece","quantity":10,"minimumStock":5}
Result: SUCCESS - Product created with ID: 4
```

## Checklist to Debug Android App Issue

### 1. Check Backend is Running
```bash
cd backend
.\start-backend.bat
```
Wait for: "Started B2bInventoryApplication"

### 2. Check Device/Emulator Network
- Physical Device: Both phone and computer must be on same WiFi
- Computer IP: 192.168.29.166
- Backend Port: 8082
- Test: Open browser on phone and visit: http://192.168.29.166:8082/api/inventory/products?businessId=1

### 3. Check App Permissions
- AndroidManifest.xml should have: `<uses-permission android:name="android.permission.INTERNET" />`
- Network Security Config should allow cleartext HTTP

### 4. Check Error Message in App
When you click "Save Product", what happens?
- Does it show "Saving..."?
- Does it show an error message?
- Does it navigate back immediately?
- Does it freeze/hang?

### 5. Common Issues and Fixes

**Issue 1: "Unable to save inventory" error**
- Backend might not be running
- Wrong IP address
- Network connectivity issue

**Issue 2: No error, but product not added**
- Check if businessId/userId are correct
- Check backend logs

**Issue 3: App crashes**
- Check Android Studio Logcat for stack trace

## Quick Test Steps

1. Open the app
2. Login/Register
3. Click "Add Inventory" or "+" button
4. Fill in:
   - Category: Electronics
   - Product Name: Test Item
   - Quantity: 10
   - Unit: Piece
5. Click "Save Product"
6. **What happens?** → Tell me the exact behavior

## Backend API Endpoints

All working ✅:
- POST /api/inventory/products?businessId={id}&userId={id}
- GET /api/inventory/products?businessId={id}
- GET /api/inventory/products/{productId}?businessId={id}

## Network Security Config

File: `android/app/src/main/res/xml/network_security_config.xml`

Should allow:
- 192.168.29.166
- localhost
- 127.0.0.1
- 10.0.2.2

## To Fix: Provide These Details

1. **What error message** do you see in the app?
2. **Does the loading spinner** show when you click Save?
3. **Check backend console** - do you see any requests coming in?
4. **Try backend test** - Can you run the PowerShell command above successfully?

## Expected Flow

1. User fills form → Clicks "Save Product"
2. Loading state shows (button shows spinner)
3. API call made to backend
4. Backend creates product and returns data
5. App navigates back to inventory list
6. New product appears in list

## If Still Not Working

Try this in Android app directly - I'll add debugging:
1. Add console logging to see exact error
2. Add Toast notification to show error on screen
3. Check if businessId/userId are being passed correctly
