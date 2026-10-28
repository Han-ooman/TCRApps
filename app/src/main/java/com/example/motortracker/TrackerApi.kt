package com.example.motortracker

import android.content.Context
import android.provider.Settings
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Klien API untuk TCRApps backend (Vercel + Neon).
 * Semua request dijalankan dari thread background (Executor di LocationService).
 */
object TrackerApi {

    const val BASE_URL = "https://tcrapps-api-shankara-dev.vercel.app"
    const val API_TOKEN = "4AiSjpYDaUhxlckf0er1KLwJ7O8zREP9"

    private val jsonMediaType = "application/json".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    /** ID unik perangkat (untuk identifikasi device di server). */
    fun deviceId(context: Context): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown"
    }

    /** POST /trips → id trip baru, atau null kalau gagal. */
    fun createTrip(deviceId: String): Int? = run {
        val body = JSONObject().put("deviceId", deviceId)
        val req = Request.Builder()
            .url("$BASE_URL/trips")
            .header("x-api-key", API_TOKEN)
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()
        runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@run null
                JSONObject(resp.body?.string() ?: "").optInt("id", -1).takeIf { it > 0 }
            }
        }.getOrNull()
    }

    /** POST /trips/:id/points → true kalau sukses. */
    fun sendPoint(
        tripId: Int,
        lat: Double,
        lon: Double,
        speedKmh: Float?,
        accuracyM: Float?,
        heading: Float?
    ): Boolean {
        val body = JSONObject()
            .put("lat", lat)
            .put("lon", lon)
            .put("speedKmh", speedKmh ?: JSONObject.NULL)
            .put("accuracyM", accuracyM ?: JSONObject.NULL)
            .put("heading", heading ?: JSONObject.NULL)
        return sendPointRaw(tripId, body)
    }

    /** Kirim titik berupa objek JSON (dipakai juga oleh offline buffer). */
    fun sendPointRaw(tripId: Int, body: JSONObject): Boolean {
        val req = Request.Builder()
            .url("$BASE_URL/trips/$tripId/points")
            .header("x-api-key", API_TOKEN)
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()
        return runCatching {
            client.newCall(req).execute().use { resp -> resp.isSuccessful }
        }.getOrDefault(false)
    }

    /** POST /trips/:id/finish → true kalau sukses. */
    fun finishTrip(tripId: Int, distanceMeters: Float): Boolean {
        val body = JSONObject().put("distanceMeters", distanceMeters.toDouble())
        val req = Request.Builder()
            .url("$BASE_URL/trips/$tripId/finish")
            .header("x-api-key", API_TOKEN)
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()
        return runCatching {
            client.newCall(req).execute().use { resp -> resp.isSuccessful }
        }.getOrDefault(false)
    }
}
