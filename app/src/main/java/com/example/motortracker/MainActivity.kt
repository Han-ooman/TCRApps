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
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
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

        // Setup Peta — CARTO dark basemap (CDN publik, tak membutuhkan API key, jarang diblokir).
        // Atribusi wajib: © OpenStreetMap contributors © CARTO (lihat layout bawah map).
        mapView.setTileSource(
            XYTileSource(
                "CartoDark",
                0, 20, 256, ".png",
                arrayOf(
                    "https://a.basemaps.cartocdn.com/dark_all/",
                    "https://b.basemaps.cartocdn.com/dark_all/",
                    "https://c.basemaps.cartocdn.com/dark_all/",
                    "https://d.basemaps.cartocdn.com/dark_all/"
                )
            )
        )
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