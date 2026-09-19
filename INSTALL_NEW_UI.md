# 📱 Install New Splash & Login UI

## Quick Install Steps

### 1. Connect Your Phone

**Enable USB Debugging:**
1. Go to **Settings** → **About Phone**
2. Tap **Build Number** 7 times (enables Developer Options)
3. Go back to **Settings** → **Developer Options**
4. Enable **USB Debugging**
5. Connect phone to computer with USB cable

**Verify Connection:**
```powershell
adb devices
```

You should see your device listed. If not, approve the "Allow USB Debugging" popup on your phone.

### 2. Install the App

```powershell
cd C:\Users\azizp\Downloads\b2b-inventory-kotlin-java-java21\b2b-inventory-app
adb install -r android\app\build\outputs\apk\debug\app-debug.apk
```

The `-r` flag replaces the old version if already installed.

### 3. Open the App

**What You'll See:**

**Splash Screen (2.5 seconds):**
- Blue gradient background
- Animated logo (📦 box icon)
- "B2B Inventory Management" title
- "Manage Today. Grow Tomorrow." tagline
- Loading spinner
- Version 1.0.0

**Login Screen:**
- Blue gradient header with logo
- White card with shadow
- "Welcome Back!" greeting
- Email/Mobile input field (with @ icon)
- Password field (with lock icon and show/hide toggle)
- Remember me checkbox
- Forgot Password link
- Blue "Sign In" button with arrow
- Register business card at bottom
- Feature badges: Secure, Reliable, Built for Businesses

## Fix Connection Error First!

**BEFORE you test login**, fix the network issue:

### Problem
Your phone IP: `10.47.86.x` (wrong network)
Computer IP: `192.168.29.166` (correct network)
**They're on different networks!**

### Solution 1: Same WiFi Network (Easiest)
1. On your phone: Settings → WiFi
2. Connect to the SAME WiFi as your computer
3. Check the IP address - should be `192.168.29.xxx`
4. Now open the app and try login

### Solution 2: Fix Firewall
```powershell
# Right-click and "Run as Administrator"
.\FIX-CONNECTION.bat
```

### Test Backend Connection
Open browser on your PHONE and go to:
```
http://192.168.29.166:8082
```

If you see JSON or backend response = working!
If you see "Can't reach" = network blocked!

## Rebuild & Reinstall (If Needed)

If you made code changes:

```powershell
# 1. Build new APK
cd android
.\gradlew.bat assembleDebug

# 2. Install on phone
cd ..
adb install -r android\app\build\outputs\apk\debug\app-debug.apk
```

## Uninstall Old Version

If UI looks old after installing:

```powershell
# Completely remove old app
adb uninstall com.b2binventory.app

# Install fresh
adb install android\app\build\outputs\apk\debug\app-debug.apk
```

## Test Login

Use your registered credentials:
- **Email/Mobile**: Your registered email or mobile number
- **Password**: Your password

**Don't have an account?**
- Click the "Register business" card at bottom
- Fill in business details
- Create account

## Verify Backend is Running

```powershell
# Check Docker container
docker ps | Select-String "b2b-inventory"

# Test API endpoint
Invoke-RestMethod -Uri "http://192.168.29.166:8082/api/inventory/products?businessId=1"
```

If backend not running:
```powershell
cd backend
docker-compose up -d
```

## Troubleshooting

### "adb is not recognized"
Install Android SDK Platform Tools:
https://developer.android.com/tools/releases/platform-tools

Or use Android Studio's bundled adb:
```powershell
# Usually at:
C:\Users\azizp\AppData\Local\Android\Sdk\platform-tools\adb.exe
```

### "No devices/emulators found"
1. Enable USB Debugging on phone
2. Connect USB cable
3. Approve "Allow USB Debugging" popup on phone
4. Try: `adb devices`

### "Installation failed"
```powershell
# Uninstall first
adb uninstall com.b2binventory.app

# Then install
adb install android\app\build\outputs\apk\debug\app-debug.apk
```

### UI Still Looks Old
1. Check APK build date (should be today)
2. Uninstall completely: `adb uninstall com.b2binventory.app`
3. Rebuild: `cd android; .\gradlew.bat assembleDebug`
4. Reinstall: `adb install android\app\build\outputs\apk\debug\app-debug.apk`

### Login Shows Connection Error
1. **Check WiFi**: Phone and computer on SAME network
2. **Check IP**: Phone should be 192.168.29.xxx (not 10.47.86.xxx)
3. **Test browser**: http://192.168.29.166:8082 on phone
4. **Fix firewall**: Run `FIX-CONNECTION.bat` as Administrator
5. **Check backend**: `docker ps` should show b2b-inventory-db

### App Crashes on Launch
Check logcat:
```powershell
adb logcat | Select-String "B2BInventory"
```

## What Success Looks Like

✅ App opens with splash screen
✅ Logo animates smoothly
✅ Auto-navigates to login after 2.5s
✅ Login screen looks professional with gradient
✅ All icons visible (email, lock, arrow, etc.)
✅ Can type in email and password
✅ Password show/hide toggle works
✅ Sign In button is blue with white text
✅ Register business card at bottom

## If Everything Works

You should see:
1. **Splash Screen** → 2.5 seconds animation
2. **Login Screen** → Professional UI with gradient
3. **Enter credentials** → Email + Password
4. **Click Sign In** → Loading spinner
5. **Success** → Navigate to Dashboard

---

**Ready to Test!** 🚀

APK Location: `android/app/build/outputs/apk/debug/app-debug.apk`
Build Status: ✅ Successful (no errors)
