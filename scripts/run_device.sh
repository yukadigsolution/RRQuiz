#!/usr/bin/env bash
# Install + jalankan RRQuiz ke HP Wahyu via ADB (tanpa hapus data).
# Pemakaian: ./scripts/run_device.sh [alamat-device]
#   Default: 10.200.200.3:44969 (port ADB HP Wahyu; kalau ganti, isi argumennya).
# Kalau APK belum ada, build.sh dijalankan dulu.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

DEVICE="${1:-10.200.200.3:44969}"
ADB="${ADB:-/opt/android-sdk/platform-tools/adb}"
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"

if [ ! -f "$APK" ]; then
  echo "APK belum ada, build dulu..."
  "$ROOT/scripts/build.sh"
fi

# Sambungkan kalau belum terhubung.
if ! "$ADB" devices | grep -q "${DEVICE}.*device$"; then
  echo "Menghubungkan ke $DEVICE ..."
  "$ADB" connect "$DEVICE"
fi

"$ADB" -s "$DEVICE" install -r "$APK"
"$ADB" -s "$DEVICE" shell am start -n com.yuka.rrquiz/.MainActivity
echo "RRQuiz ter-install dan dibuka di $DEVICE"
