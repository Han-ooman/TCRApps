package com.example.motortracker

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView // engine OpenFreeMap via MapLibre
    private lateinit var tvSpeed: TextView
    private lateinit var tvDistance: TextView
    private lateinit var btnToggleTracking: Button
    private var isTracking = false

    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val speed = intent?.getFloatExtra("speed", 0f) ?: 0f
            val distance = intent?.getFloatExtra("distance", 0f) ?: 0f

            tvSpeed.text = String.format(Locale.getDefault(), "%.1f", speed)
            tvDistance.text = String.format(Locale.getDefault(), "%.2f", distance)

            // Analisa kecepatan hemat (live)
            val tip = findViewById<TextView>(R.id.tvEcoTip)
            tip.text = when {
                speed <= 0.1f -> "Mode hemat: 35-45 km/h, jaga tempo mulus"
                speed < 35f -> "Santai (di bawah zona eco) - tambah dikit gas biar efisien"
                speed <= 45f -> "ECO MODE - zona paling hemat (35-45 km/h)"
                speed <= 55f -> "Oke (45-55 km/h) - masih cukup hemat"
                else -> "Kecepatan tinggi (>55 km/h) - bensin boros, turunkan dikit"
            }
            tip.setTextColor(
                when {
                    speed in 35f..45f -> 0xFF9ADB97.toInt()
                    speed > 55f -> 0xFFEB5757.toInt()
                    else -> 0xFFF2C94C.toInt()
                }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)

        tvSpeed = findViewById(R.id.tvSpeed)
        tvDistance = findViewById(R.id.tvDistance)
        btnToggleTracking = findViewById(R.id.btnToggleTracking)

        loadOpenFreeMapStyle()

        checkPermissions()

        btnToggleTracking.setOnClickListener {
            if (!isTracking) {
                startTrackingService()
            } else {
                stopTrackingService()
            }
        }

        btnButtons()
    }

    /**
     * Muat style OpenFreeMap (tanpa API key).
     * Coba gaya tersimpan dulu, lalu dark/liberty/positron/bright.
     * Validasi URL style (HTTP 200 + punya "layers") agar tidak style 404.
     */
    private fun loadOpenFreeMapStyle(styleOverride: String? = null) {
        Thread {
            val client = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .build()

            val order = mutableListOf<String>()
            if (styleOverride != null) order.add(styleOverride) else order.add(MapSources.storedStyle(this))
            MapSources.styles.forEach { if (it !in order) order.add(it) }

            var chosen: String? = null
            for (id in order) {
                val url = MapSources.styleUrl(id)
                val ok = runCatching {
                    val req = Request.Builder()
                        .url(url)
                        .header("User-Agent", "TCRApps-alpha (Android; maplibre)")
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@use false
                        (resp.body?.string() ?: "").contains("\"layers\"")
                    }
                }.getOrDefault(false)
                if (ok) {
                    chosen = id
                    break
                }
            }

            runOnUiThread {
                if (chosen == null) {
                    Toast.makeText(
                        this,
                        "Server OpenFreeMap tidak bisa diakses - cek koneksi",
                        Toast.LENGTH_LONG
                    ).show()
                    return@runOnUiThread
                }
                val url = MapSources.styleUrl(chosen)
                Toast.makeText(this, "Peta: OpenFreeMap [$chosen]", Toast.LENGTH_SHORT).show()
                mapView.getMapAsync { map ->
                    map.setStyle(Style.Builder().fromUri(url)) { /* style loaded callback opsional */ }
                    // Mulai di tengah Indonesia (zoom overview) — biar peta langsung terisi
                    map.moveCamera(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder()
                                .target(LatLng(-2.5, 118.0))
                                .zoom(4.8)
                                .build()
                        )
                    )
                }
            }
        }.start()
    }

    private fun btnButtons() {
        // Navigasi layar Servis / Bensin / Ganti gaya peta
        findViewById<Button>(R.id.btnMaintenance).setOnClickListener {
            startActivity(Intent(this, MaintenanceActivity::class.java))
        }
        findViewById<Button>(R.id.btnFuel).setOnClickListener {
            startActivity(Intent(this, FuelActivity::class.java))
        }
        findViewById<Button>(R.id.btnMapSource).setOnClickListener {
            MapSources.showChooser(this) { styleId ->
                loadOpenFreeMapStyle(styleOverride = styleId)
            }
        }
    }

    private fun startTrackingService() {
        val serviceIntent = Intent(this, LocationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        isTracking = true
        btnToggleTracking.text = getString(R.string.btn_stop_tracking)
        btnToggleTracking.setBackgroundColor(ContextCompat.getColor(this, R.color.button_danger))
        Toast.makeText(this, getString(R.string.toast_tracking_started), Toast.LENGTH_SHORT).show()
    }

    private fun stopTrackingService() {
        val serviceIntent = Intent(this, LocationService::class.java)
        stopService(serviceIntent)
        isTracking = false
        btnToggleTracking.text = getString(R.string.btn_start_tracking)
        btnToggleTracking.setBackgroundColor(ContextCompat.getColor(this, R.color.button_primary))
        Toast.makeText(this, getString(R.string.toast_tracking_stopped), Toast.LENGTH_SHORT).show()
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 101)
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        ContextCompat.registerReceiver(
            this,
            locationReceiver,
            IntentFilter("SpeedUpdateAction"),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        unregisterReceiver(locationReceiver)
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroy() {
        mapView.onDestroy()
        super.onDestroy()
    }
}
