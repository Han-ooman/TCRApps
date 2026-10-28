package com.example.motortracker

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponent
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.engine.LocationEngineDefault
import org.maplibre.android.location.engine.LocationEngineRequest
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView // engine OpenFreeMap via MapLibre
    private lateinit var tvSpeed: TextView
    private lateinit var tvDistance: TextView
    private lateinit var btnToggleTracking: Button
    private var isTracking = false

    // ---- Peta live tracking state ----
    private var mapRef: MapLibreMap? = null
    private var locationComponent: LocationComponent? = null
    private val trackPoints = mutableListOf<LatLng>()     // jejak GPS live
    private var firstFixDone = false
    private var destLon = 0.0
    private var destLat = 0.0
    private var hasDestination = false
    private var lastRouteMs = 0L

    private val http = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            val speed = intent.getFloatExtra("speed", 0f)
            val distance = intent.getFloatExtra("distance", 0f)
            val lat = intent.getDoubleExtra("lat", 0.0)
            val lon = intent.getDoubleExtra("lon", 0.0)
            val bearing = intent.getFloatExtra("bearing", 0f)

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

            // Update peta live
            if (lat != 0.0 && lon != 0.0) {
                handleGpsFix(lat, lon, bearing, isTracking)
            }
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
                    http.newCall(req).execute().use { resp ->
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
                    mapRef = map
                    // Mulai di tengah Indonesia (zoom overview) - biar peta langsung terisi
                    map.moveCamera(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder()
                                .target(LatLng(-2.5, 118.0))
                                .zoom(4.8)
                                .build()
                        )
                    )
                    // Long-press peta = set tujuan
                    map.addOnMapLongClickListener { point ->
                        setDestination(point)
                        true
                    }
                    map.setStyle(Style.Builder().fromUri(url)) { style ->
                        setupStyleLayers(map, style)
                        activateLocationPuck(style)
                    }
                }
            }
        }.start()
    }

    /** Layer garis rute live + rute navigasi + pin tujuan. */
    private fun setupStyleLayers(map: MapLibreMap, style: Style) {
        // Jejak GPS (garis cyan muda)
        style.addSource(GeoJsonSource("tcr-track-src"))
        style.addLayer(
            LineLayer("tcr-track", "tcr-track-src").withProperties(
                PropertyFactory.lineColor(0xFF7DF9FF.toInt()),
                PropertyFactory.lineWidth(4f),
                PropertyFactory.lineOpacity(0.9f)
            )
        )

        // Rute ke tujuan (garis kuning)
        style.addSource(GeoJsonSource("tcr-route-src"))
        style.addLayer(
            LineLayer("tcr-route", "tcr-route-src").withProperties(
                PropertyFactory.lineColor(0xFFFFB703.toInt()),
                PropertyFactory.lineWidth(5f)
            )
        )

        // Pin tujuan (bulat merah)
        style.addSource(GeoJsonSource("tcr-dest-src"))
        style.addLayer(
            CircleLayer("tcr-dest", "tcr-dest-src").withProperties(
                PropertyFactory.circleColor(0xFFEB5757.toInt()),
                PropertyFactory.circleRadius(8f),
                PropertyFactory.circleStrokeWidth(2f),
                PropertyFactory.circleStrokeColor(0xFFFFFFFF.toInt())
            )
        )
    }

    /** Puck lokasi biru + kamera ikut posisi. */
    @SuppressLint("MissingPermission")
    private fun activateLocationPuck(style: Style) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return
        try {
            val map = mapRef ?: return
            val engine = LocationEngineDefault.getDefaultLocationEngine(this)
            val request = LocationEngineRequest.Builder(1000L)
                .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
                .build()
            val lc = map.locationComponent
            lc.activateLocationComponent(
                LocationComponentActivationOptions.Builder(this, style)
                    .locationEngine(engine)
                    .locationEngineRequest(request)
                    .useDefaultLocationEngine(false)
                    .build()
            )
            lc.isLocationComponentEnabled = true
            locationComponent = lc
        } catch (e: Exception) {
            Toast.makeText(this, "Puck lokasi gagal: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /** Setiap fix GPS dari service: update puck, garis jejak, kamera, rute. */
    @SuppressLint("MissingPermission")
    private fun handleGpsFix(lat: Double, lon: Double, bearing: Float, trackingActive: Boolean) {
        val loc = Location("tcr-broadcast").apply {
            latitude = lat
            longitude = lon
            this.bearing = bearing
        }

        // Fix pertama: zoom ke posisi + kamera follow
        if (!firstFixDone) {
            firstFixDone = true
            locationComponent?.setCameraMode(CameraMode.TRACKING)
            mapRef?.moveCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(LatLng(lat, lon))
                        .zoom(16.0)
                        .tilt(0.0)
                        .build()
                )
            )
        }
        locationComponent?.forceLocationUpdate(loc)

        if (trackingActive) {
            trackPoints.add(LatLng(lat, lon))
            updateTrackLine()

            // Rute ke tujuan: refresh tiap >30 detik saat tracking
            if (hasDestination && System.currentTimeMillis() - lastRouteMs > 30_000) {
                lastRouteMs = System.currentTimeMillis()
                requestRoute(lat, lon, showToast = false)
            }
        }
    }

    /** Long-press peta = set tujuan + hitung rute. */
    private fun setDestination(point: LatLng) {
        destLat = point.latitude
        destLon = point.longitude
        hasDestination = true
        lastRouteMs = 0L

        styleRef()?.addGeoJson("tcr-dest-src", Feature.fromGeometry(Point.fromLngLat(destLon, destLat)))

        Toast.makeText(this, "Tujuan dipilih - mencari rute...", Toast.LENGTH_SHORT).show()

        // Origin: posisi terakhir (bila ada), else pusat layar
        val origin = trackPoints.lastOrNull()
            ?: mapRef?.cameraPosition?.target
        if (origin != null) {
            requestRoute(origin.latitude, origin.longitude, showToast = true)
        }
    }

    /** Panggil OSRM (gratis, tanpa key). Garis kuning + info km/menit. */
    private fun requestRoute(origLat: Double, origLon: Double, showToast: Boolean) {
        Thread {
            // OSRM public endpoints (fallback chain)
            val endpoints = listOf(
                "https://router.project-osrm.org/route/v1/driving/",
                "https://routing.openstreetmap.de/routed-car/route/v1/driving/"
            )

            var success = false
            var lastError = ""

            for (baseUrl in endpoints) {
                val url = baseUrl +
                    "%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson&steps=false"
                        .format(origLon, origLat, destLon, destLat)

                try {
                    val req = Request.Builder()
                        .url(url)
                        .header("User-Agent", "TCRApps-alpha (Android; osrm)")
                        .build()

                    http.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            lastError = "HTTP ${resp.code}"
                            return@use
                        }
                        val bodyStr = resp.body?.string() ?: ""
                        val obj = org.json.JSONObject(bodyStr)
                        val code = obj.optString("code")
                        if (code != "Ok") {
                            val msg = obj.optString("message", "Unknown")
                            lastError = "OSRM code=$code: $msg"
                            return@use
                        }

                        val routes = obj.getJSONArray("routes")
                        if (routes.length() == 0) {
                            lastError = "Tidak ada rute tersedia"
                            return@use
                        }

                        val route = routes.getJSONObject(0)
                        val distM = route.getDouble("distance")
                        val durS = route.getDouble("duration")
                        val coords = route.getJSONObject("geometry").getJSONArray("coordinates")
                        val pts = mutableListOf<Point>()
                        for (i in 0 until coords.length()) {
                            val c = coords.getJSONArray(i)
                            pts.add(Point.fromLngLat(c.getDouble(0), c.getDouble(1)))
                        }

                        runOnUiThread {
                            val line = Feature.fromGeometry(LineString.fromLngLats(pts))
                            styleRef()?.addGeoJson("tcr-route-src", line)
                            if (showToast) {
                                val km = distM / 1000.0
                                val min = durS / 60.0
                                Toast.makeText(
                                    this@MainActivity,
                                    String.format(Locale.US, "Rute: %.1f km - estimasi %.0f menit", km, min),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                        success = true
                    }
                    if (success) break
                } catch (e: Exception) {
                    lastError = e.message ?: "Unknown error"
                }
            }

            if (!success) {
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Rute gagal: $lastError (cek koneksi internet)",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    /** Helper: update source geojson di style aktif (aman walau layer belum ada). */
    private fun styleRef(): MapLibreMap? = mapRef

    private fun MapLibreMap.addGeoJson(sourceId: String, feature: Feature) {
        getStyle()?.getSourceAs<GeoJsonSource>(sourceId)?.setGeoJson(feature)
    }

    private fun updateTrackLine() {
        if (trackPoints.isEmpty()) return
        val features = FeatureCollection.fromFeatures(
            listOf(Feature.fromGeometry(LineString.fromLngLats(trackPoints.map {
                Point.fromLngLat(it.longitude, it.latitude)
            })))
        )
        mapRef?.getStyle()?.getSourceAs<GeoJsonSource>("tcr-track-src")?.setGeoJson(features)
    }

    private fun clearTrack() {
        trackPoints.clear()
        updateTrackLine()
        firstFixDone = false
    }

    private fun btnButtons() {
        // Navigasi layar Servis / Bensin / Peta / Setelan
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
        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
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
        lastRouteMs = 0L
        clearTrack()
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
