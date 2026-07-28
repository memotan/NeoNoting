@echo off
echo === NeoNoting Capacitor Setup ===
echo.
echo Installing dependencies...
call npm install
echo.
echo Adding Android platform...
call npx cap add android
echo.
echo Syncing web assets...
call npx cap sync
echo.
echo === Setup complete! ===
echo Run "npx cap open android" to open in Android Studio.
pause
