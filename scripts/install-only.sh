#!/bin/bash
# ============================================================
# install-only.sh
# Install APK ke emulator tanpa build ulang (jika APK sudah ada)
# ============================================================
# Usage: ./scripts/install-only.sh [build_type]
#   build_type : debug | release (default: release)
# ============================================================

ANDROID_SDK="$HOME/Library/Android/sdk"
export PATH="$ANDROID_SDK/platform-tools:$PATH"

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_ROOT"

BUILD_TYPE="${1:-release}"
APP_PACKAGE="com.yue.browser"

GREEN='\033[1;32m'; RED='\033[1;31m'; YELLOW='\033[1;33m'; BLUE='\033[1;34m'; NC='\033[0m'
log()  { echo -e "${BLUE}[install]${NC} $1"; }
ok()   { echo -e "${GREEN}[OK]${NC} $1"; }
fail() { echo -e "${RED}[ERROR]${NC} $1"; }

# Cek device
if ! adb devices 2>/dev/null | grep -q "emulator"; then
    fail "Tidak ada emulator/device yang terhubung."
    exit 1
fi

if [ "$BUILD_TYPE" = "release" ]; then
    APK_PATH="$PROJECT_ROOT/app/build/outputs/apk/release/app-release.apk"
else
    APK_PATH="$PROJECT_ROOT/app/build/outputs/apk/debug/app-debug.apk"
fi

if [ ! -f "$APK_PATH" ]; then
    fail "APK tidak ditemukan: $APK_PATH"
    log "Build dulu dengan: ./scripts/start-emulator.sh"
    exit 1
fi

log "Uninstall lama..."
adb uninstall "$APP_PACKAGE" 2>/dev/null || true

log "Install: $(basename "$APK_PATH") ($(du -h "$APK_PATH" | cut -f1))..."
adb install -r "$APK_PATH"

log "Buka aplikasi..."
adb shell monkey -p "$APP_PACKAGE" -c android.intent.category.LAUNCHER 1 &>/dev/null

ok "✅ Install selesai!"
