
@echo off
echo ========================================
echo B2B Inventory Backend Server
echo ========================================
echo.
echo Setting JAVA_HOME to Java 21
set JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot
echo JAVA_HOME: %JAVA_HOME%
echo.
echo Starting Spring Boot Application on port 8082...
echo.
echo Wait for message: "Started B2bInventoryApplication"
echo Then you can use the Android app!
echo.
echo Press Ctrl+C to stop the server
echo ========================================
echo.

gradlew.bat bootRun
