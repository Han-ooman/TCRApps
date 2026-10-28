# Aturan proguard rilis default sudah menyertakan proguard-android-optimize.txt.
# Tambahkan aturan khusus di sini jika perlu.

# osmdroid
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**
