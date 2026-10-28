package com.example.motortracker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Penyimpanan lokal sederhana (SharedPreferences + JSON).
 * Cukup untuk alpha; nanti bisa dipindah ke Room/server.
 */
object PrefsStore {

    private const val PREFS_NAME = "tcrapps_store"
    private const val KEY_ODOMETER = "odometer_km"
    private const val KEY_MAINT = "maintenance_items"
    private const val KEY_FUEL = "fuel_records"
    private const val KEY_MAP_PROVIDER = "map_provider"
    private const val KEY_MAP_API_KEY = "map_api_key"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ---- Odometer sekarang ----
    fun currentOdoKm(ctx: Context): Double = prefs(ctx).getFloat(KEY_ODOMETER, 0f).toDouble()

    fun setCurrentOdoKm(ctx: Context, km: Double) {
        prefs(ctx).edit().putFloat(KEY_ODOMETER, km.toFloat()).apply()
    }

    // ---- Maintenance ----
    fun loadMaintenance(ctx: Context): MutableList<MaintenanceItem> {
        val list = mutableListOf<MaintenanceItem>()
        val raw = prefs(ctx).getString(KEY_MAINT, null)
        if (raw != null) {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    MaintenanceItem(
                        o.getString("name"),
                        o.getInt("intervalKm"),
                        o.getDouble("lastOdoKm"),
                        o.optString("note", "")
                    )
                )
            }
        }
        if (list.isEmpty()) {
            list.addAll(VarioMaintenanceDefaults.defaults)
            saveMaintenance(ctx, list)
        }
        return list
    }

    fun saveMaintenance(ctx: Context, items: List<MaintenanceItem>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(
                JSONObject()
                    .put("name", it.name)
                    .put("intervalKm", it.intervalKm)
                    .put("lastOdoKm", it.lastOdoKm)
                    .put("note", it.note)
            )
        }
        prefs(ctx).edit().putString(KEY_MAINT, arr.toString()).apply()
    }

    // ---- Fuel records ----
    fun loadFuel(ctx: Context): MutableList<FuelRecord> {
        val list = mutableListOf<FuelRecord>()
        val raw = prefs(ctx).getString(KEY_FUEL, null)
        if (raw != null) {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    FuelRecord(
                        o.getDouble("odoKm"),
                        o.getDouble("liters"),
                        o.getDouble("pricePerLiter"),
                        o.getLong("dateMs")
                    )
                )
            }
        }
        return list
    }

    fun saveFuel(ctx: Context, records: List<FuelRecord>) {
        val arr = JSONArray()
        records.forEach {
            arr.put(
                JSONObject()
                    .put("odoKm", it.odoKm)
                    .put("liters", it.liters)
                    .put("pricePerLiter", it.pricePerLiter)
                    .put("dateMs", it.dateMs)
            )
        }
        prefs(ctx).edit().putString(KEY_FUEL, arr.toString()).apply()
    }

    // ---- Peta ----
    fun mapProviderId(ctx: Context): String = prefs(ctx).getString(KEY_MAP_PROVIDER, "wikimedia")!!

    fun mapApiKey(ctx: Context): String = prefs(ctx).getString(KEY_MAP_API_KEY, "") ?: ""

    fun setMapConfig(ctx: Context, providerId: String, apiKey: String) {
        prefs(ctx).edit()
            .putString(KEY_MAP_PROVIDER, providerId)
            .putString(KEY_MAP_API_KEY, apiKey)
            .apply()
    }

    // ---- Export / Backup ----
    fun exportAllJson(ctx: Context): String {
        val root = JSONObject()
        root.put("odometer_km", currentOdoKm(ctx))
        root.put("maintenance_items", JSONArray().apply {
            loadMaintenance(ctx).forEach {
                put(JSONObject()
                    .put("name", it.name)
                    .put("intervalKm", it.intervalKm)
                    .put("lastOdoKm", it.lastOdoKm)
                    .put("note", it.note))
            }
        })
        root.put("fuel_records", JSONArray().apply {
            loadFuel(ctx).forEach {
                put(JSONObject()
                    .put("odoKm", it.odoKm)
                    .put("liters", it.liters)
                    .put("pricePerLiter", it.pricePerLiter)
                    .put("dateMs", it.dateMs))
            }
        })
        root.put("map_provider", mapProviderId(ctx))
        root.put("map_api_key", mapApiKey(ctx))
        root.put("exported_at", System.currentTimeMillis())
        return root.toString(2)
    }

    fun clearAll(ctx: Context) {
        prefs(ctx).edit().clear().apply()
        // Re-init defaults
        loadMaintenance(ctx)
    }
}
