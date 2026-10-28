package com.example.motortracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import androidx.core.app.NotificationCompat

class LocationService : Service(), LocationListener {

    private lateinit var locationManager: LocationManager
    private var lastLocation: Location? = null
    private var totalDistance: Float = 0f

    // --- Sinkronisasi ke backend API ---
    private var tripId: Int? = null
    private lateinit var apiExecutor: java.util.concurrent.ThreadPoolExecutor
    // Buffer titik yang gagal terkirim (mis. sinyal hilang) — dicoba ulang tiap update
    private val pendingPoints = java.util.concurrent.ConcurrentLinkedQueue<JSONObject>()

    companion object {
        var currentSpeed: Float = 0f
        var currentDistance: Float = 0f
        const val CHANNEL_ID = "LocationServiceChannel"
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        apiExecutor = java.util.concurrent.ThreadPoolExecutor(
            1, 1, 30L, TimeUnit.SECONDS,
            java.util.concurrent.LinkedBlockingQueue()
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()

        startForeground(1, notification)

        // Buat trip di backend (async, tidak memblokir service)
        if (tripId == null) {
            val deviceId = TrackerApi.deviceId(this)
            apiExecutor.execute {
                val id = TrackerApi.createTrip(deviceId)
                if (id != null) {
                    tripId = id
                    updateNotification(
                        getString(R.string.notif_title),
                        "Trip #$id tersinkron ke server"
                    )
                }
            }
        }

        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L, // setiap 1 detik
                1f,    // setiap 1 meter
                this
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }

        return START_STICKY
    }

    /** Update isi notifikasi (masih id=1). */
    private fun updateNotification(title: String, text: String) {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOnlyAlertOnce(true)
            .build()
        runCatching {
            getSystemService(NotificationManager::class.java)?.notify(1, notification)
        }
    }

    override fun onLocationChanged(location: Location) {
        if (location.hasSpeed()) {
            // Konversi m/s ke km/h
            currentSpeed = location.speed * 3.6f
        }

        lastLocation?.let {
            totalDistance += it.distanceTo(location)
            currentDistance = totalDistance / 1000f // konversi ke km
        }
        lastLocation = location

        // Kirim *broadcast* ke app ini saja (lebih efisien dan aman)
        val intent = Intent("SpeedUpdateAction").apply {
            setPackage(packageName)
            putExtra("speed", currentSpeed)
            putExtra("distance", currentDistance)
            putExtra("lat", location.latitude)
            putExtra("lon", location.longitude)
            putExtra("bearing", if (location.hasBearing()) location.bearing else 0f)
        }
        sendBroadcast(intent)

        // Kirim titik GPS ke backend (jika trip sudah dibuat)
        val id = tripId
        if (id != null) {
            apiExecutor.execute {
                // Kirm titik terlebih dulu buffer lama (offline queue)
                while (pendingPoints.isNotEmpty()) {
                    if (!TrackerApi.sendPointRaw(id, pendingPoints.peek()!!)) break
                    pendingPoints.poll()
                }
                val point = JSONObject()
                    .put("lat", location.latitude)
                    .put("lon", location.longitude)
                    .put("speedKmh", currentSpeed.toDouble())
                    .put("accuracyM", location.accuracy.toDouble())
                    .put("heading", if (location.hasBearing()) location.bearing.toDouble() else JSONObject.NULL)
                if (!TrackerApi.sendPointRaw(id, point)) {
                    pendingPoints.add(point) // simpan untuk retry
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationManager.removeUpdates(this)

        // Tutup trip di backend, lalu matikan worker kirim data.
        val id = tripId
        if (id != null) {
            val meters = totalDistance
            apiExecutor.execute {
                TrackerApi.finishTrip(id, meters)
                tripId = null
            }
        }
        apiExecutor.execute { apiExecutor.shutdown() }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}