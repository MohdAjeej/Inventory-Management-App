# Build the debug APK, pointing at the VPS
.\gradlew.bat assembleDebug -PapiBaseUrl=http://147.93.108.99:8082/

# Check the phone is connected (shows serial + "device")
adb devices

# Install (reinstall, keep data)
adb install -r app\build\outputs\apk\debug\app-debug.apk

# Launch the app
adb shell am start -n com.b2binventory.app/.MainActivity

# Only your app's logs (app must be running)
adb logcat --pid=$(adb shell pidof -s com.b2binventory.app)

# Filter by a specific tag (e.g. Log.d("TAG_NAME", ...))
adb logcat -s TAG_NAME

# Errors only (E = error, F = fatal, W = warning)
adb logcat *:E
adb logcat *:W

# Search logs for a string
adb logcat | Select-String "Exception"
adb logcat | Select-String "147.93.108.99"
adb logcat | Select-String "Retrofit"

# Clear the buffer (do this before reproducing a bug)
adb logcat -c

# Dump current logs to a file, then exit
adb logcat -d > logcat.txt

# Dump only your app's logs to a file
adb logcat -d --pid=$(adb shell pidof -s com.b2binventory.app) > app-logs.txt

# Capture a crash: clear, reproduce, dump errors
adb logcat -c
adb logcat -d *:E > crash.txt

# Full stack trace on build failure
.\gradlew.bat assembleDebug -PapiBaseUrl=http://147.93.108.99:8082/ --stacktrace

# Very verbose (rarely needed)
.\gradlew.bat assembleDebug --info
.\gradlew.bat assembleDebug --debug

# 1. Clear old noise
adb logcat -c

# 2. Launch the app
adb shell am start -n com.b2binventory.app/.MainActivity

# 3. Use the app (try login/register), then dump errors
adb logcat -d *:E