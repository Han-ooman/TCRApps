package com.example.motortracker

import android.Manifest
import android.os.Bundle
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.osmdroid.config.Configuration
import org.osmdroid.views.MapView
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
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
                speed <= 0.1f -> "💡 Mode hemat: 35–45 km/h • jaga tempo mulus"
                speed < 35f -> "🐢 Santai (di bawah zona eco) — tambah dikit gas biar efisien"
                speed <= 45f -> "✅ ECO MODE — zona paling hemat (35–45 km/h)"
                speed <= 55f -> "👍 Oke (45–55 km/h) — masih cukup hemat"
                else -> "⚠️ Kecepatan tinggi (>55 km/h) — bensin boros, turunkan dikit"
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
        // Konfigurasi osmdroid
        val osmdroidPrefs = getSharedPreferences("osmdroid", MODE_PRIVATE)
        Configuration.getInstance().apply {
            load(applicationContext, osmdroidPrefs)
            userAgentValue = packageName
        }
        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.mapView)
        tvSpeed = findViewById(R.id.tvSpeed)
        tvDistance = findViewById(R.id.tvDistance)
        btnToggleTracking = findViewById(R.id.btnToggleTracking)

        // Setup Peta — pakai penyimpanan pilihan provider (default: CARTO dark, tanpa API key)
        MapSources.applyTo(mapView, this)
        mapView.setMultiTouchControls(true)
        val mapController = mapView.controller
        mapController.setZoom(16.5)

        checkPermissions()

        btnToggleTracking.setOnClickListener {
            if (!isTracking) {
                startTrackingService()
            } else {
                stopTrackingService()
            }
        }

        // Navigasi layar Servis / Bensin / Pilih sumber peta
        findViewById<Button>(R.id.btnMaintenance).setOnClickListener {
            startActivity(Intent(this, MaintenanceActivity::class.java))
        }
        findViewById<Button>(R.id.btnFuel).setOnClickListener {
            startActivity(Intent(this, FuelActivity::class.java))
        }
        findViewById<Button>(R.id.btnMapSource).setOnClickListener {
            MapSources.showChooser(this) {
                Toast.makeText(this, "Ganti peta: ${PrefsStore.mapProviderId(this)}", Toast.LENGTH_SHORT).show()
                MapSources.applyTo(mapView, this)
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
}