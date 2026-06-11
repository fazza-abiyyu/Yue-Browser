#!/bin/bash
# ============================================================
# stop-emulator.sh
# Hentikan emulator yang sedang berjalan
# ============================================================

ANDROID_SDK="$HOME/Library/Android/sdk"
export PATH="$ANDROID_SDK/platform-tools:$ANDROID_SDK/emulator:$PATH"

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PID_FILE="$PROJECT_ROOT/.emulator.pid"

GREEN='\033[1;32m'; RED='\033[1;31m'; YELLOW='\033[1;33m'; BLUE='\033[1;34m'; NC='\033[0m'
log()  { echo -e "${BLUE}[stop]${NC} $1"; }
ok()   { echo -e "${GREEN}[OK]${NC} $1"; }
warn() { echo -e "${YELLOW}[!]${NC}  $1"; }

EMU_COUNT=$(adb devices 2>/dev/null | grep "emulator" | wc -l | tr -d ' ')

if [ "$EMU_COUNT" -eq 0 ]; then
    warn "Tidak ada emulator yang berjalan."
else
    log "Menemukan $EMU_COUNT emulator. Menghentikan..."
    for dev in $(adb devices | grep emulator | awk '{print $1}'); do
        adb -s "$dev" shell reboot -p 2>/dev/null &
    done
fi

# Hentikan process emulator berdasarkan PID (jika ada)
if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE" 2>/dev/null)
    if [ -n "$PID" ] && kill -0 "$PID" 2>/dev/null; then
        log "Menghentikan process PID=$PID..."
        kill "$PID" 2>/dev/null
        sleep 2
        kill -9 "$PID" 2>/dev/null || true
    fi
    rm -f "$PID_FILE"
fi

# Hentikan process emulator lainnya
EMU_PROCS=$(pgrep -f "emulator.*-avd" 2>/dev/null || true)
if [ -n "$EMU_PROCS" ]; then
    log "Memberhentikan process emulator lain..."
    kill $EMU_PROCS 2>/dev/null || true
    sleep 1
    kill -9 $EMU_PROCS 2>/dev/null || true
fi

ok "Selesai."
