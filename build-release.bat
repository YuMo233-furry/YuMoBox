@echo off
setlocal enabledelayedexpansion

rem Change to repo root no matter where the script is launched from
cd /d "%~dp0" || (
    echo [ERROR] Unable to change to script directory.
    pause
    exit /b 1
)

rem Use Gradle wrapper to build the signed release APK
echo [INFO] Starting release build...
call "%~dp0gradlew.bat" clean assembleRelease
if errorlevel 1 (
    echo [ERROR] Build failed.
    pause
    exit /b 1
)

rem Location of the generated APK (default Gradle output path)
set APK_PATH=app\build\outputs\apk\release\app-release.apk

if exist "%APK_PATH%" (
    echo [INFO] Build succeeded.
    echo [INFO] APK: %APK_PATH%
) else (
    echo [WARN] Build completed but APK not found at %APK_PATH%.
)

pause
endlocal

