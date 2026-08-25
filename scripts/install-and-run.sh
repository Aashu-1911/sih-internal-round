#!/usr/bin/env bash
# install-and-run.sh — build a fresh Swarsetu debug APK (with bundled STT models),
# install it on the connected ADB device, launch it, and stream STT logs.
#
# Usage:
#   ./scripts/install-and-run.sh              # build locally + install + launch
#   ./scripts/install-and-run.sh --no-build   # skip build, reinstall last built APK
#
# Requirements:
#   - Device connected via USB with USB debugging enabled (`adb devices` shows it)
#   - Android SDK (adb auto-found at %LOCALAPPDATA%\Android\Sdk\platform-tools)
set -euo pipefail

PKG="app.swarsetu"
ACTIVITY="$PKG/.MainActivity"
APK="app/build/outputs/apk/debug/app-debug.apk"

# --- locate adb -------------------------------------------------------------
find_adb() {
    if command -v adb >/dev/null 2>&1; then echo "adb"; return; fi
    local candidates=(
        "$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
        "$HOME/AppData/Local/Android/Sdk/platform-tools/adb.exe"
        "/c/Android/platform-tools/adb.exe"
    )
    for c in "${candidates[@]}"; do
        [ -f "$c" ] && { echo "$c"; return; }
    done
    echo "" # not found
}

ADB="$(find_adb)"
if [ -z "$ADB" ]; then
    echo "ERROR: adb not found. Install Android platform-tools or add adb to PATH." >&2
    exit 1
fi
echo "== adb: $ADB"

# --- verify device ----------------------------------------------------------
if ! "$ADB" get-state >/dev/null 2>&1; then
    echo "ERROR: no device attached. Connect your phone via USB, enable" >&2
    echo "USB debugging, accept the prompt, then rerun this script." >&2
    "$ADB" devices -l
    exit 1
fi
DEVICE="$( "$ADB" devices | sed -n '2p' | awk '{print $1}' )"
ABI="$( "$ADB" shell getprop ro.product.cpu.abi | tr -d '\r' )"
MODEL="$( "$ADB" shell getprop ro.product.model | tr -d '\r' )"
echo "== device: $DEVICE ($MODEL, $ABI)"

# --- ensure models are on disk ---------------------------------------------
MISSING=0
for lang in hi gu te en bn mr ta kn ml; do
    [ -f "app/src/main/assets/stt-$lang/model.int8.onnx" ] || { MISSING=1; echo "missing model: stt-$lang"; }
done
if [ "$MISSING" = "1" ]; then
    echo "== models missing — downloading…"
    bash scripts/download-stt-models.sh
fi

# --- build ------------------------------------------------------------------
if [ "${1:-}" != "--no-build" ]; then
    echo "== building debug APK (first build takes several minutes; ~1.8 GB with models)…"
    ./gradlew :app:assembleDebug --console=plain -q || {
        echo "ERROR: Gradle build failed." >&2
        exit 1
    }
fi

if [ ! -f "$APK" ]; then
    echo "ERROR: $APK not found. Run without --no-build first." >&2
    exit 1
fi
echo "== APK: $(du -h "$APK" | cut -f1)"

# --- uninstall stale copy (avoids signature mismatch failures) ---------------
"$ADB" uninstall "$PKG" >/dev/null 2>&1 || true

# --- install -----------------------------------------------------------------
echo "== installing (this can take a minute — large APK)…"
"$ADB" install -r "$APK"

# --- grant mic permission up-front -------------------------------------------
echo "== granting RECORD_AUDIO…"
"$ADB" shell pm grant "$PKG" android.permission.RECORD_AUDIO 2>/dev/null || \
    echo "(runtime permission not pre-grantable — app will ask on first use)"

# --- launch & stream STT logs -------------------------------------------------
echo "== launching $ACTIVITY…"
"$ADB" logcat -c
"$ADB" shell am start -n "$ACTIVITY"

sleep 3
FOCUS="$( "$ADB" shell dumpsys window | grep mCurrentFocus || true )"
echo "== foreground: $FOCUS"

echo ""
echo "==============================================================="
echo " App is running. Watching logcat for STT activity (Ctrl+C to stop)."
echo " Test flow: open a chat → pick a language chip → tap the mic →"
echo " speak → watch PARTIAL/FINAL lines below."
echo "==============================================================="
"$ADB" logcat -v time SherpaEngine:* SttPipeline:* PcmCapture:* SttModelManager:* AndroidRuntime:E
