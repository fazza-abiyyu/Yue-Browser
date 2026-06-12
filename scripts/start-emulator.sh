#!/bin/bash
# ============================================================
# start-emulator.sh
# Menjalankan Android Emulator di BACKGROUND + build + install APK
# Emulator akan TERJALAN TERUS meskipun script selesai.
# ============================================================
# Usage: ./scripts/start-emulator.sh [avd_name] [build_type]
#   avd_name   : nama AVD (default: Pixel_Arm)
#   build_type : debug | release (default: release)
# ============================================================

ANDROID_SDK="$HOME/Library/Android/sdk"
export PATH="$ANDROID_SDK/cmdline-tools/latest/bin:$ANDROID_SDK/emulator:$ANDROID_SDK/platform-tools:$PATH"

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_ROOT"

AVD_NAME="${1:-Pixel_Arm}"
BUILD_TYPE="${2:-release}"
APP_PACKAGE="com.yue.browser"
LOG_FILE="$PROJECT_ROOT/.emulator.log"

# ---------- WARNA ----------
GREEN='\033[1;32m'; RED='\033[1;31m'; YELLOW='\033[1;33m'; BLUE='\033[1;34m'; NC='\033[0m'
log()    { echo -e "${BLUE}[emulator]${NC} $1"; }
ok()     { echo -e "${GREEN}[OK]${NC} $1"; }
warn()   { echo -e "${YELLOW}[!]${NC}  $1"; }
fail()   { echo -e "${RED}[ERROR]${NC} $1"; }

# ---------- CEK KETERSEDIAAN ----------
if ! command -v emulator &>/dev/null; then
    fail "Emulator tidak ditemukan. Pastikan Android SDK terinstall."
    exit 1
fi

if ! emulator -list-avds 2>/dev/null | grep -q "^${AVD_NAME}$"; then
    warn "AVD '$AVD_NAME' tidak ada. Pilihan tersedia:"
    emulator -list-avds | sed 's/^/  - /'
    fail "Buat AVD dulu atau pilih nama yang benar."
    exit 1
fi

# ---------- CEK APAKAH EMULATOR SUDAH JALAN ----------
EMULATOR_RUNNING=0
if adb devices 2>/dev/null | grep -q "emulator"; then
    warn "Emulator sudah berjalan. Gunakan instance yang sudah ada."
    EMULATOR_RUNNING=1
else
    log "Menjalankan emulator: $AVD_NAME (background mode)..."
    log "Log emulator: $LOG_FILE"

    # Jalankan emulator di BACKGROUND dengan nohup - tidak ikut mati ketika script selesai
    nohup emulator -avd "$AVD_NAME" -no-boot-anim -no-audio -dns-server 8.8.8.8 > "$LOG_FILE" 2>&1 &
    EMULATOR_PID=$!

    log "PID emulator: $EMULATOR_PID"
    echo "$EMULATOR_PID" > "$PROJECT_ROOT/.emulator.pid"

    # Tunggu sampai emulator siap (boot complete)
    log "Menunggu emulator booting..."
    BOOT_TIMEOUT=300
    ELAPSED=0
    BOOTED=""
    while [ $ELAPSED -lt $BOOT_TIMEOUT ]; do
        if adb devices 2>/dev/null | grep -q "emulator"; then
            BOOTED=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
            if [ "$BOOTED" = "1" ]; then
                break
            fi
        fi
        printf "."
        sleep 3
        ELAPSED=$((ELAPSED + 3))
    done
    echo ""

    if [ "$BOOTED" != "1" ]; then
        fail "Timeout menunggu emulator boot (${BOOT_TIMEOUT}s)"
        warn "Cek log: $LOG_FILE"
        exit 1
    fi
    ok "Emulator siap! 🎉"
fi

# ---------- SYNC ARCH FOLDER ----------
log "Syncing /Users/fazza_abiyyu/Downloads/Arch to /sdcard/Download/Arch..."
adb shell mkdir -p /sdcard/Download/Arch
adb push /Users/fazza_abiyyu/Downloads/Arch/. /sdcard/Download/Arch/

# ---------- BUILD APK ----------
log "Build APK ($BUILD_TYPE)..."
set -e
if [ "$BUILD_TYPE" = "release" ]; then
    ./gradlew assembleRelease --no-daemon -q 2>&1 | tail -15 || true
    APK_PATH="$PROJECT_ROOT/app/build/outputs/apk/release/app-release.apk"
else
    ./gradlew assembleDebug --no-daemon -q 2>&1 | tail -15 || true
    APK_PATH="$PROJECT_ROOT/app/build/outputs/apk/debug/app-debug.apk"
fi
set +e

if [ ! -f "$APK_PATH" ]; then
    fail "APK tidak ditemukan: $APK_PATH"
    exit 1
fi
ok "Build selesai: $(du -h "$APK_PATH" | cut -f1)"

# ---------- UNINSTALL LAMA + INSTALL BARU ----------
log "Install APK ke emulator..."
adb uninstall "$APP_PACKAGE" 2>/dev/null || true
if ! adb install -r "$APK_PATH" 2>&1 | tail -3; then
    warn "Install gagal, coba uninstall lalu install ulang..."
    adb uninstall "$APP_PACKAGE" 2>/dev/null || true
    adb install -r "$APK_PATH"
fi

# ---------- BUKA APP ----------
log "Membuka aplikasi..."
adb shell monkey -p "$APP_PACKAGE" -c android.intent.category.LAUNCHER 1 &>/dev/null

ok ""
ok "✅ Selesai! Aplikasi berjalan di emulator."
ok ""
log "📱  Tips:"
log "   • ./scripts/watch-build.sh — auto-refresh ketika edit kode"
log "   • ./scripts/stop-emulator.sh — hentikan emulator"
log "   • ./scripts/start-emulator.sh — start emulator lagi (jika sudah jalan, skip boot ulang)"
log "   • ./scripts/install-only.sh — install APK tanpa build ulang"
