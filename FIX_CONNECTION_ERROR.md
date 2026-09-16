# 🔧 Fix Connection Error: Failed to connect to 192.168.29.166:8082

## Error You're Seeing
```
Error: failed to connect 192.168.29.166 (port 8082) from /10.47.86.18:49500 after 10000ms
```

## 🎯 Quick Fix (Try This First!)

### Step 1: Check Backend is Running
Open PowerShell in backend folder:
```bash
cd C:\Users\azizp\Downloads\b2b-inventory-kotlin-java-java21\b2b-inventory-app\backend
.\start-backend.bat
```

**Wait for**: `Started B2bInventoryApplication`

### Step 2: Add Windows Firewall Rule
**Open PowerShell as Administrator** (Right-click → Run as Administrator):

```powershell
New-NetFirewallRule -DisplayName "B2B Inventory Backend" -Direction Inbound -LocalPort 8082 -Protocol TCP -Action Allow
```

### Step 3: Verify Firewall Rule
```powershell
Get-NetFirewallRule -DisplayName "B2B Inventory Backend"
```

### Step 4: Test Connection from Phone
1. Open Chrome browser on your phone
2. Visit: `http://192.168.29.166:8082/api/inventory/products?businessId=1`
3. You should see `[]` or product list

---

## 🔍 Detailed Troubleshooting

### Issue 1: Windows Firewall Blocking (Most Common!)

**Solution - Add Firewall Rule Manually:**

1. **Open Windows Firewall:**
   - Press `Win + R`
   - Type: `wf.msc`
   - Press Enter

2. **Create Inbound Rule:**
   - Click "Inbound Rules" on the left
   - Click "New Rule..." on the right
   - Select "Port" → Next
   - Select "TCP"
   - Enter port: `8082`
   - Click Next
   - Select "Allow the connection"
   - Click Next
   - Check all: Domain, Private, Public
   - Click Next
   - Name: `B2B Backend Port 8082`
   - Click Finish

3. **Restart Backend:**
   ```bash
   cd backend
   .\start-backend.bat
   ```

### Issue 2: Phone Not on Same WiFi

**Check WiFi Connection:**
- Computer WiFi: Should be on `192.168.29.x` network
- Phone WiFi: Must be on same WiFi network
- Both devices should see each other

**Test:**
On phone, open browser and go to:
```
http://192.168.29.166:8082
```

If you see "Whitelabel Error Page", backend is reachable! ✅

### Issue 3: Backend Not Running

**Start Backend:**
```bash
cd C:\Users\azizp\Downloads\b2b-inventory-kotlin-java-java21\b2b-inventory-app\backend
.\start-backend.bat
```

**Check if Running:**
```powershell
netstat -ano | findstr :8082
```

Should show something like:
```
TCP    0.0.0.0:8082    0.0.0.0:0    LISTENING    12345
```

### Issue 4: IP Address Changed

**Get Current IP:**
```powershell
ipconfig
```

Look for "IPv4 Address" under your WiFi adapter (usually 192.168.x.x)

**If IP is different**, update ApiConfig.kt:
1. Open: `android/app/src/main/java/com/b2binventory/app/data/ApiConfig.kt`
2. Change:
   ```kotlin
   const val BASE_URL = "http://NEW_IP_HERE:8082/"
   ```
3. Rebuild and reinstall app

### Issue 5: Antivirus Blocking

**Temporarily Disable:**
- Windows Defender
- Any third-party antivirus
- Try connection again

---

## 🚀 Complete Fix Process

### Step-by-Step:

**1. Check Computer IP:**
```powershell
ipconfig
```
Note down the WiFi IPv4 address (should be 192.168.29.166)

**2. Check Phone WiFi:**
- Settings → WiFi
- Make sure connected to same WiFi as computer
- Note the phone's IP (should be 192.168.29.x)

**3. Start Backend:**
```bash
cd backend
.\start-backend.bat
```

**4. Add Firewall Rule (Administrator PowerShell):**
```powershell
New-NetFirewallRule -DisplayName "B2B Backend 8082" -Direction Inbound -LocalPort 8082 -Protocol TCP -Action Allow
```

**5. Test from Computer:**
```powershell
Invoke-RestMethod -Uri "http://192.168.29.166:8082/api/inventory/products?businessId=1" -Method GET
```

**6. Test from Phone Browser:**
Open Chrome on phone → Visit:
```
http://192.168.29.166:8082/api/inventory/products?businessId=1
```

**7. Rebuild App (if IP changed):**
```bash
cd android
.\gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

---

## 🔥 Nuclear Option (If Nothing Works)

### Use Computer's Hotspot:

1. **Create Hotspot on Computer:**
   - Settings → Network & Internet → Mobile hotspot
   - Turn on "Share my Internet connection"
   - Note the password

2. **Connect Phone to Hotspot:**
   - Phone → WiFi → Connect to computer's hotspot

3. **Get New IP:**
   ```powershell
   ipconfig
   ```
   Look for IP under "Local Area Connection* X" (usually 192.168.137.1)

4. **Update App:**
   - Edit `ApiConfig.kt`
   - Change BASE_URL to new IP
   - Rebuild and install

---

## ✅ How to Verify Fix Worked

### Test 1: Browser Test (Phone)
Open Chrome on phone:
```
http://192.168.29.166:8082
```
✅ Should see: "Whitelabel Error Page" or JSON data

### Test 2: API Test (Phone)
```
http://192.168.29.166:8082/api/inventory/products?businessId=1
```
✅ Should see: `[]` or list of products

### Test 3: App Test
1. Open B2B Inventory app
2. Try to add inventory
3. Should work without connection error

---

## 📋 Checklist Before Adding Inventory

- [ ] Backend running? (`.\start-backend.bat`)
- [ ] Firewall rule added? (Port 8082)
- [ ] Same WiFi? (Both 192.168.29.x)
- [ ] IP correct in app? (192.168.29.166)
- [ ] Browser test works? (http://192.168.29.166:8082)

---

## 🆘 Still Not Working?

### Check Backend Logs:
Look at the PowerShell window where backend is running
- Should show "Started B2bInventoryApplication"
- Should show incoming requests when you try to add inventory

### Check Android Logcat:
```bash
adb logcat | findstr "B2B"
```

### Alternative: Use Emulator
If physical device continues to have issues:
1. Use Android Emulator instead
2. Change ApiConfig.kt to:
   ```kotlin
   const val BASE_URL = "http://10.0.2.2:8082/"
   ```
3. Rebuild app

---

## 📞 Need More Help?

Run this diagnostic and share output:
```powershell
Write-Host "=== Computer IP ===" ; ipconfig | Select-String "IPv4"
Write-Host "`n=== Port 8082 Status ===" ; netstat -ano | findstr :8082
Write-Host "`n=== Firewall Rules ===" ; Get-NetFirewallRule -DisplayName "*8082*" | Select-Object DisplayName, Enabled
Write-Host "`n=== Backend Test ===" ; Invoke-RestMethod -Uri "http://192.168.29.166:8082/api/inventory/products?businessId=1" -Method GET -ErrorAction SilentlyContinue
```

---

**TIP**: The most common cause is Windows Firewall blocking external connections to port 8082. The firewall rule fix solves 90% of connection issues!
