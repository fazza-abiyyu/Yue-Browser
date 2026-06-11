#!/bin/bash

# Exit on error
set -e

APK_PATH="app/build/outputs/apk/release/app-release.apk"

if [ -f "$APK_PATH" ]; then
    echo "📲 Installing APK onto device/emulator..."
    adb install -r "$APK_PATH"

    echo "🏃 Launching Yue Browser..."
    adb shell am start -n "com.yue.browser/com.yue.browser.MainActivity"

    echo "🎉 Done!"
else
    echo "❌ Error: APK not found at $APK_PATH"
    exit 1
fi
