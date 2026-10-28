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
import androidx.core.app.NotificationCompat

class LocationService : Service(), LocationListener {

    private lateinit var locationManager: LocationManager
    private var lastLocation: Location? = null
    private var totalDistance: Float = 0f

    companion object {
        var currentSpeed: Float = 0f
        var currentDistance: Float = 0f
        const val CHANNEL_ID = "LocationServiceChannel"
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Motor Tracker Aktif")
            .setContentText("Merekam kecepatan dan rute perjalanan...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()

        startForeground(1, notification)

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

        // Kirim *broadcast* jika ingin memperbarui UI secara real-time
        val intent = Intent("SpeedUpdateAction")
        intent.putExtra("speed", currentSpeed)
        intent.putExtra("distance", currentDistance)
        sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Location Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationManager.removeUpdates(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}