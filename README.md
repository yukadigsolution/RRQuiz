# RRQuiz

Android WebView app untuk RRQuiz.

- Header di atas: tulisan **RRQuiz** di kiri, tombol gear (SVG) di kanan.
- Klik gear → halaman Settings, masukkan URL WebView, tekan Simpan.
- Di bawah header: WebView yang memuat URL tersebut.
- URL tersimpan di SharedPreferences, jadi tetap dipakai saat app dibuka lagi.
- Tombol Back HP: mundur di riwayat WebView dulu, kalau sudah habis baru keluar app.

## URL default

`https://alfalearning.sat.co.id/`

Ubah dari halaman Settings di dalam app, tanpa perlu rebuild.

## Build

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Persyaratan: Android Studio / JDK 17, Android SDK 34, Gradle 8.5.

## Struktur

```
app/src/main/
  AndroidManifest.xml
  java/com/yuka/rrquiz/
    MainActivity.kt      # Header + WebView
    SettingsActivity.kt  # Input URL + simpan ke SharedPreferences
    UrlPrefs.kt          # Helper SharedPreferences + normalisasi URL
  res/layout/
    activity_main.xml
    activity_settings.xml
  res/drawable/ic_settings.xml   # Ikon gear (vector, dari SVG Material Settings)
```
