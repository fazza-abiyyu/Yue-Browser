#!/bin/bash

# Exit on error
set -e

echo "🚀 Starting Yue Browser build and install process..."

# Check if gradlew exists. If not, try to generate it using system gradle.
if [ ! -f "./gradlew" ]; then
    echo "⚠️ gradlew not found. Trying to generate it using system gradle..."
    if command -v gradle >/dev/null 2>&1; then
        gradle wrapper
    else
        echo "❌ Error: 'gradle' command line tool is not installed on your system and './gradlew' is missing."
        echo "💡 Tip: Open this project folder in Android Studio first, and it will generate the wrapper files automatically."
        exit 1
    fi
fi

# Build the debug APK
echo "⚙️ Building debug APK..."
./gradlew assembleDebug

# Path to the built APK
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

if [ -f "$APK_PATH" ]; then
    echo "✅ APK built successfully: $APK_PATH"
    
    # Check if adb is available
    if command -v adb >/dev/null 2>&1; then
        # Check if any device is connected
        DEVICES_CONNECTED=$(adb devices | grep -v "List" | grep "device" | wc -l)
        if [ "$DEVICES_CONNECTED" -eq 0 ]; then
            echo "❌ Error: No Android devices or emulators connected. Run an emulator or connect a phone with USB Debugging enabled."
            exit 1
        fi
        
        echo "📲 Installing APK onto device/emulator..."
        adb install -r "$APK_PATH"
        
        # Launch the MainActivity
        echo "🏃 Launching Yue Browser on your device..."
        adb shell am start -n "com.yue.browser/com.yue.browser.MainActivity"
        
        echo "🎉 Done! Yue Browser is now running."
    else
        echo "❌ Error: 'adb' command not found. Please add the Android SDK platform-tools to your system PATH."
        exit 1
    fi
else
    echo "❌ Error: Built APK not found at $APK_PATH"
    exit 1
fi
