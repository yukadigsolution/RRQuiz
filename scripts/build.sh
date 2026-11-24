#!/usr/bin/env bash
# Build APK debug RRQuiz.
# Jalankan dari mana saja: ./scripts/build.sh
# Memakai Gradle sistem 8.7 + JDK 17 di Cloud D (jangan ./gradlew, sesuai aturan single-version).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
if [ -z "${JAVA_HOME:-}" ] && [ -d /usr/lib/jvm/java-17-openjdk-amd64 ]; then
  export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
fi
GRADLE="${GRADLE:-/opt/gradle-8.7/bin/gradle}"

# Cek ruang disk (Cloud D sering mepet, jangan build kalau sisa < 1 GB).
FREE_MB="$(df --output=avail -m . | tail -1 | tr -d ' ')"
if [ "$FREE_MB" -lt 1024 ]; then
  echo "Ruang disk tinggal ${FREE_MB} MB (< 1 GB), build dibatalkan." >&2
  exit 1
fi

if [ ! -x "$GRADLE" ]; then
  echo "Gradle tidak ditemukan di $GRADLE" >&2
  exit 1
fi

"$GRADLE" assembleDebug

APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
echo "APK: $APK"
ls -lh "$APK"
sha256sum "$APK"
