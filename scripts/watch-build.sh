#!/bin/bash
# ============================================================
# watch-build.sh  (v3 — FIXED for macOS bash 3.2 + cooldown logic)
# Auto-build & install APK ketika ada perubahan kode
#
# FIXES from v2:
#   • LAST_BUILD_TS di-set 0 (bukan waktu sekarang) — cooldown TIDAK menghalangi build PERTAMA
#   • read -t 0.1 (pecahan detik TIDAK didukung bash 3.2 macOS) → ganti ke read -t 1 (integer)
#   • COOLDOWN_AFTER_BUILD: 15 → 8 detik (lebih responsif)
#   • Keyboard check pakai integer timeout agar kompatibel bash 3.2
# ============================================================
# Usage: ./scripts/watch-build.sh [build_type] [interval_sec]
#   build_type  : debug | release (default: debug)
#   interval_sec: polling interval dalam detik (default: 5)
# ============================================================

set -e

ANDROID_SDK="$HOME/Library/Android/sdk"
export PATH="$ANDROID_SDK/platform-tools:$PATH"

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_ROOT"

# ---------- CONFIG ----------
BUILD_TYPE="${1:-debug}"
INTERVAL="${2:-5}"
DEBOUNCE_SECONDS=4          # file harus "diam" minimal ini sebelum trigger build
COOLDOWN_AFTER_BUILD=8      # minimal jeda setelah build selesai (default 8s, dulu 15s)
APP_PACKAGE="com.yue.browser"

# Hanya file jenis INI yang mempengaruhi build Android
SOURCE_FILE_TYPES=(
    -name "*.kt" -o
    -name "*.java" -o
    -name "*.xml" -o
    -name "*.kts" -o
    -name "*.gradle" -o
    -name "*.properties" -o
    -name "AndroidManifest.xml"
)

# Direktori yang dipantau (hanya sumber kode + konfigurasi build)
WATCH_DIRS=(
    "app/src/main/java"
    "app/src/main/res"
    "app/src/main/AndroidManifest.xml"
    "app/build.gradle"
    "app/build.gradle.kts"
    "build.gradle"
    "build.gradle.kts"
    "gradle.properties"
    "settings.gradle"
    "settings.gradle.kts"
)

# Pattern file YANG DIABAINKAN (tidak pernah trigger build)
IGNORE_PATTERNS=(
    ".DS_Store"
    "*.swp"
    "*.swo"
    "*.tmp"
    "*~"
    ".idea/"
    ".git/"
    "build/"
    "*.apk"
    "*.dex"
    "*.class"
    "*.jar"
)

# ---------- WARNA ----------
GREEN='\033[1;32m'; RED='\033[1;31m'; YELLOW='\033[1;33m';
BLUE='\033[1;34m';  MAGENTA='\033[1;35m'; GRAY='\033[0;90m'; NC='\033[0m'

log()    { echo -e "${BLUE}[watch]${NC} $1"; }
ok()     { echo -e "${GREEN}[OK]${NC}    $1"; }
warn()   { echo -e "${YELLOW}[!]${NC}     $1"; }
fail()   { echo -e "${RED}[ERROR]${NC} $1"; }
info()   { echo -e "${MAGENTA}[build]${NC} $1"; }
muted()  { echo -e "${GRAY}         $1${NC}"; }

# ---------- CEK EMULATOR ----------
if ! adb devices 2>/dev/null | grep -q "emulator"; then
    fail "Tidak ada emulator/device yang terhubung."
    log "Jalankan dulu: ./scripts/start-emulator.sh di terminal lain"
    exit 1
fi
ok "Device ditemukan: $(adb devices | grep emulator | awk '{print $1}')"

# ---------- HASHING FUNCTION ----------
# Hitung signature file. Hasil = max(mtime) diantara semua file relevant
# (tidak perlu sha256sum, cuma compare mtime max saja jauh lebih ringan)
hash_files() {
    local latest_mtime=0
    local found=0

    for dir in "${WATCH_DIRS[@]}"; do
        local target="$PROJECT_ROOT/$dir"
        [ -e "$target" ] || continue

        # Untuk file biasa (bukan direktori)
        if [ -f "$target" ]; then
            local mt
            mt=$(stat -f '%m' "$target" 2>/dev/null) || continue
            if [ "$mt" -gt "$latest_mtime" ]; then
                latest_mtime="$mt"
                found=1
            fi
            continue
        fi

        # Untuk direktori: cari file source relevant, ignore pattern sampah
        while IFS= read -r -d '' file; do
            # Skip ignored patterns
            local skip=0
            for pat in "${IGNORE_PATTERNS[@]}"; do
                # basename match atau path match
                if [[ "$file" == *"$pat"* ]]; then
                    skip=1
                    break
                fi
            done
            [ $skip -eq 1 ] && continue

            local mt
            mt=$(stat -f '%m' "$file" 2>/dev/null) || continue
            if [ "$mt" -gt "$latest_mtime" ]; then
                latest_mtime="$mt"
                found=1
            fi
        done < <(
            find "$target" -type f \( "${SOURCE_FILE_TYPES[@]}" \) -print0 2>/dev/null
        )
    done

    if [ "$found" -eq 0 ]; then
        echo "0"
    else
        echo "$latest_mtime"
    fi
}

# ---------- LIST CHANGED FILES ----------
# Daftar file yang mtime-nya LEBIH BARU dari timestamp reference
# Hasil: list path (relative ke PROJECT_ROOT) dipisah newline
list_changed_files() {
    local ref_time="$1"
    [ -z "$ref_time" ] || [ "$ref_time" -le 0 ] && return 0

    local tmp_results=()

    for dir in "${WATCH_DIRS[@]}"; do
        local target="$PROJECT_ROOT/$dir"
        [ -e "$target" ] || continue

        if [ -f "$target" ]; then
            local mt
            mt=$(stat -f '%m' "$target" 2>/dev/null) || continue
            if [ "$mt" -gt "$ref_time" ]; then
                tmp_results+=("$dir")
            fi
            continue
        fi

        while IFS= read -r -d '' file; do
            local skip=0
            for pat in "${IGNORE_PATTERNS[@]}"; do
                if [[ "$file" == *"$pat"* ]]; then skip=1; break; fi
            done
            [ $skip -eq 1 ] && continue

            local mt
            mt=$(stat -f '%m' "$file" 2>/dev/null) || continue
            if [ "$mt" -gt "$ref_time" ]; then
                # Relative path (lebih pendek & readable)
                local rel="${file#$PROJECT_ROOT/}"
                tmp_results+=("$rel")
            fi
        done < <(find "$target" -type f \( "${SOURCE_FILE_TYPES[@]}" \) -print0 2>/dev/null)
    done

    # Print unik, batasi max 15, sisanya "...dan N lagi"
    local count=${#tmp_results[@]}
    if [ "$count" -eq 0 ]; then
        echo "0"
        return
    fi

    # Print jumlah total dulu (baris 1 = count, sisanya = file list)
    echo "$count"

    local shown=0
    # Dedupe & sort
    printf '%s\n' "${tmp_results[@]}" | sort -u | while IFS= read -r f; do
        if [ "$shown" -lt 15 ]; then
            echo "$f"
            shown=$((shown+1))
        fi
    done
}

# ---------- BUILD & INSTALL ----------
do_build_and_install() {
    local start_ts
    start_ts=$(date +%s)

    info "============================================="
    info "🔨  Build dimulai ($(date '+%H:%M:%S'))"
    info "============================================="

    ./gradlew clean --no-daemon

    if [ "$BUILD_TYPE" = "release" ]; then
        set -o pipefail
        ./gradlew assembleRelease --no-daemon 2>&1 | tail -40
        local build_status=$?
        set +o pipefail
        local apk_path="$PROJECT_ROOT/app/build/outputs/apk/release/app-release.apk"
    else
        set -o pipefail
        ./gradlew assembleDebug --no-daemon 2>&1 | tail -40
        local build_status=$?
        set +o pipefail
        local apk_path="$PROJECT_ROOT/app/build/outputs/apk/debug/app-debug.apk"
    fi

    if [ $build_status -ne 0 ]; then
        fail "❌ Build gagal! Periksa error di atas."
        return 1
    fi

    if [ ! -f "$apk_path" ]; then
        fail "❌ APK tidak ditemukan: $apk_path"
        return 1
    fi

    info "📦  Install ke emulator..."
    if ! adb install -r "$apk_path" 2>&1 | tail -5; then
        warn "Install gagal, coba uninstall dulu..."
        adb uninstall "$APP_PACKAGE" 2>/dev/null || true
        adb install -r "$apk_path"
    fi

    info "🚀  Restart aplikasi..."
    adb shell am force-stop "$APP_PACKAGE" 2>/dev/null || true
    adb shell am start -n "$APP_PACKAGE/.MainActivity" 2>/dev/null || \
        adb shell monkey -p "$APP_PACKAGE" -c android.intent.category.LAUNCHER 1 &>/dev/null

    local end_ts
    end_ts=$(date +%s)
    local duration=$(( end_ts - start_ts ))

    ok "✅ Build & install selesai ($(date '+%H:%M:%S')) — ${duration}s"
    ok "   APK: $(du -h "$apk_path" 2>/dev/null | cut -f1) | $(basename "$apk_path")"
    muted "Cooldown ${COOLDOWN_AFTER_BUILD}s sebelum bisa build lagi..."
    log ""
    return 0
}

# ---------- MAIN LOOP ----------
clear
log "👀  Watch mode AKTIF (v2 — debounced + cooldown)"
log "   Build type     : $BUILD_TYPE"
log "   Poll interval  : ${INTERVAL}s"
log "   Debounce       : ${DEBOUNCE_SECONDS}s (file harus diam dulu)"
log "   Cooldown       : ${COOLDOWN_AFTER_BUILD}s sesudah build"
log ""
log "   Direktori yang dipantau:"
for dir in "${WATCH_DIRS[@]}"; do
    if [ -e "$PROJECT_ROOT/$dir" ]; then
        muted "• $dir"
    fi
done
log ""
log "   File yang dipantau: *.kt, *.java, *.xml, *.kts, *.gradle, *.properties"
log "   File DIABAINKAN : .DS_Store, .swp, .tmp, .idea/, .git/, build/"
log ""
log "⌨️  Shortcut:"
log "    b + ENTER  → manual trigger build"
log "    q + ENTER  → keluar"
log "    Ctrl+C     → keluar"
log ""
log "🚀  Auto-build di startup..."
do_build_and_install || true
log ""

# State variables
LAST_HASH=$(hash_files)
LAST_BUILD_TS=$(date +%s)
LAST_SUCCESSFUL_BUILD_TS=$LAST_BUILD_TS
PENDING_CHANGE_TS=0             # timestamp saat perubahan pertama terdeteksi (untuk debounce)

# Helper: print summary daftar file yang berubah
print_changed_summary() {
    local ref="$1"
    # Panggil list_changed_files — baris 1 = count, sisanya = path file
    local raw
    raw=$(list_changed_files "$ref")
    local total
    total=$(echo "$raw" | head -1)

    if [ -z "$total" ] || [ "$total" -le 0 ] 2>/dev/null; then
        muted "Tidak ada perubahan file (referensi mtime tidak ditemukan)"
        return
    fi

    # Hitung berdasarkan ekstensi untuk ringkasan
    local kotlin=0 java=0 xml=0 gradle=0 other=0
    while IFS= read -r line; do
        case "$line" in
            *.kt|*.kts) kotlin=$((kotlin+1)) ;;
            *.java) java=$((java+1)) ;;
            *.xml) xml=$((xml+1)) ;;
            *.gradle|*.properties) gradle=$((gradle+1)) ;;
            *) other=$((other+1)) ;;
        esac
    done < <(echo "$raw" | tail -n +2 | head -200)

    # Print ringkasan singkat
    local parts=()
    [ "$kotlin" -gt 0 ] && parts+=("${kotlin}x Kotlin")
    [ "$java"   -gt 0 ] && parts+=("${java}x Java")
    [ "$xml"    -gt 0 ] && parts+=("${xml}x XML")
    [ "$gradle" -gt 0 ] && parts+=("${gradle}x Gradle")
    [ "$other"  -gt 0 ] && parts+=("${other}x lain")

    if [ ${#parts[@]} -gt 0 ]; then
        log "📊  Summary: ${total} file berubah → $(IFS=', '; echo "${parts[*]}")"
    else
        log "📊  ${total} file berubah"
    fi

    # Print list detail (maks 10)
    local shown=0
    while IFS= read -r line; do
        if [ "$shown" -ge 10 ]; then
            muted "…dan $(( total - shown )) file lainnya"
            break
        fi
        muted "• $line"
        shown=$((shown+1))
    done < <(echo "$raw" | tail -n +2)
}

# ---------- NON-BLOCKING KEYBOARD INPUT ----------
# Baca input secara non-blocking via stdin with timeout
read_key() {
    local saved_stty
    saved_stty=$(stty -g 2>/dev/null || true)
    stty -icanon -echo min 0 time 0 2>/dev/null || true
    local key=""
    IFS= read -r -t 0 -n 1 key 2>/dev/null || true
    stty "$saved_stty" 2>/dev/null || true
    echo "$key"
}

# ---------- LOOP ----------
while true; do
    NOW=$(date +%s)
    CURRENT_HASH=$(hash_files)

    # ----- DETEKSI PERUBAHAN -----
    if [ "$CURRENT_HASH" != "$LAST_HASH" ]; then
        # Ada perubahan. Catet kapan mulainya (DEBOUNCE).
        if [ "$PENDING_CHANGE_TS" -eq 0 ]; then
            PENDING_CHANGE_TS="$NOW"
            muted "📝  Perubahan file terdeteksi — menunggu file stabil (${DEBOUNCE_SECONDS}s debounce)..."
        fi

        # Tunggu sampai file "diam" selama DEBOUNCE_SECONDS
        TIME_SINCE_CHANGE=$(( NOW - PENDING_CHANGE_TS ))

        if [ "$TIME_SINCE_CHANGE" -ge "$DEBOUNCE_SECONDS" ]; then
            # Cek cooldown — JIKA SUDAH PERNAH BUILD (LAST_BUILD_TS > 0)
            if [ "$LAST_BUILD_TS" -gt 0 ]; then
                TIME_SINCE_LAST_BUILD=$(( NOW - LAST_BUILD_TS ))
                if [ "$TIME_SINCE_LAST_BUILD" -lt "$COOLDOWN_AFTER_BUILD" ]; then
                    muted "⏳ Masih cooldown (sisa $(( COOLDOWN_AFTER_BUILD - TIME_SINCE_LAST_BUILD ))s) — pending diabaikan"
                    PENDING_CHANGE_TS=0
                    LAST_HASH="$CURRENT_HASH"
                    continue
                fi
            fi
            # ✅ Ready untuk build — belum pernah build ATAU cooldown sudah lewat
            log "🔄 File stabil. Menampilkan perubahan..."
            print_changed_summary "$LAST_SUCCESSFUL_BUILD_TS"
            LAST_HASH="$CURRENT_HASH"
            PENDING_CHANGE_TS=0
            do_build_and_install || true
            LAST_BUILD_TS=$(date +%s)
            LAST_SUCCESSFUL_BUILD_TS=$LAST_BUILD_TS
        fi
    else
        # Tidak ada perubahan baru — reset pending tracker
        if [ "$PENDING_CHANGE_TS" -gt 0 ]; then
            PENDING_CHANGE_TS=0
        fi
    fi

    # ----- KEYBOARD CHECK (non-blocking) -----
    # Gunakan read dengan timeout INTEGER (bash 3.2 macOS TIDAK support pecahan detik)
    # read -t 1 = tunggu maks 1 detik untuk 1 karakter, jika tidak ada → return false
    if read -r -t 1 -n 1 key 2>/dev/null; then
        case "$key" in
            b|B)
                log "⌨️  Manual build trigger (key: b)"
                # Cek cooldown juga (hanya jika SUDAH PERNAH build)
                NOW2=$(date +%s)
                if [ "$LAST_BUILD_TS" -gt 0 ]; then
                    TIME_SINCE_LAST_BUILD=$(( NOW2 - LAST_BUILD_TS ))
                    if [ "$TIME_SINCE_LAST_BUILD" -lt "$COOLDOWN_AFTER_BUILD" ]; then
                        muted "⏳ Cooldown masih aktif (sisa $(( COOLDOWN_AFTER_BUILD - TIME_SINCE_LAST_BUILD ))s) — build ditunda"
                        continue
                    fi
                fi
                print_changed_summary "$LAST_SUCCESSFUL_BUILD_TS"
                do_build_and_install || true
                LAST_BUILD_TS=$(date +%s)
                LAST_SUCCESSFUL_BUILD_TS=$LAST_BUILD_TS
                LAST_HASH=$(hash_files)
                ;;
            q|Q)
                log "👋  Quit (key: q). Bye!"
                exit 0
                ;;
            *)
                # Abaikan karakter lain
                ;;
        esac
    fi

    # Jeda polling utama (sesi tidur paling lama di loop)
    sleep "$INTERVAL"
done
