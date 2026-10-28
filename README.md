# TCRApps — Motor Speed Tracker

Aplikasi Android untuk merekam kecepatan dan jarak tempuh motor secara real-time,
dengan peta OpenStreetMap (osmdroid) dan layanan foreground untuk tracking GPS.

## Fitur
- 📍 Tracking GPS real-time (foreground service, tetap jalan saat app diminimalkan)
- 🚀 Display kecepatan (km/h) & jarak tempuh (km)
- 🗺️ Peta OpenStreetMap dengan kontrol zoom & multi-touch
- 🔔 Notifikasi persisten selama tracking aktif

## Teknologi
- Kotlin, AndroidX, Material Components
- osmdroid 6.1.18 (peta OpenStreetMap)
- Gradle Kotlin DSL (AGP 9.1.0)

## Struktur Project
```
TCRApps/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/motortracker/
│   │   │   ├── MainActivity.kt
│   │   │   └── LocationService.kt
│   │   ├── res/
│   │   │   ├── layout/activity_main.xml
│   │   │   ├── values/ (strings, colors, themes)
│   │   │   └── mipmap-*/, drawable/ (ikon launcher)
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## Build
1. Buka project ini di Android Studio (Ladybug+ / terbaru).
2. `local.properties` (berisi `sdk.dir`) akan dibuat otomatis — file ini tidak di-commit.
3. Jalankan konfigurasi `app` atau:
   ```bash
   ./gradlew assembleDebug
   ```

## Izin (Permissions)
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` — tracking lokasi
- `FOREGROUND_SERVICE_LOCATION` — service lokasi di latar belakang
- `POST_NOTIFICATIONS` — notifikasi status tracking (Android 13+)
- `INTERNET` — memuat tile peta OpenStreetMap

## Catatan
- Tracking dimulai lewat tombol **Mulai Tracking** di layar utama.
- Kamera peta muncul dengan zoom 16.5; kecepatan & jarak diperbarui tiap detik.
