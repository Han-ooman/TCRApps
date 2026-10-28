plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.motortracker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.motortracker"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "0.1.0-alpha07"
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Alpha testing: pakai key debug supaya APK bisa langsung di-install,
            // tanpa perlu keystore rilis resmi (ganti sebelum publish ke Play Store).
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // OpenFreeMap peta (vektor, tanpa API key) — engine MapLibre
    implementation("org.maplibre.gl:android-sdk:11.8.0")

    // HTTP client untuk upload ke backend API
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
