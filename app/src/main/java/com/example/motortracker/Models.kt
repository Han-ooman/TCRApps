package com.example.motortracker

/**
 * Model data lokal (tanpa Room — cukup disimpan di SharedPreferences sebagai JSON).
 */

data class MaintenanceItem(
    val name: String,
    val intervalKm: Int,
    val lastOdoKm: Double,   // odometer saat terakhir ganti
    val note: String = ""
) {
    fun nextDueKm(): Double = lastOdoKm + intervalKm

    enum class Status { OK, SOON, OVERDUE }

    fun status(currentOdoKm: Double): Status {
        val left = nextDueKm() - currentOdoKm
        return when {
            left <= 0 -> Status.OVERDUE
            left <= (intervalKm * 0.15) -> Status.SOON
            else -> Status.OK
        }
    }
}

data class FuelRecord(
    val odoKm: Double,          // odometer saat pengisian
    val liters: Double,         // jumlah bensin diisi
    val pricePerLiter: Double,  // harga per liter
    val dateMs: Long
) {
    val totalCost: Double get() = liters * pricePerLiter
}

/** Jadwal khas Honda Vario 125 Old (FI, ~2014–2017) — km interval nyaman & umum dipakai. */
object VarioMaintenanceDefaults {
    val defaults: List<MaintenanceItem> = listOf(
        MaintenanceItem("Ganti Oli Mesin 0.8L", 2_000, 0.0,
            "Semi-sintetis SAE 10W-30. Ganti lebih cepat kalau sering macet/short trip."),
        MaintenanceItem("Cek/Ganti Filter Udara", 12_000, 0.0,
            "Cek tiap 6.000 km; ganti kalau kotor berat — boros & brebet."),
        MaintenanceItem("V-Belt & Roller CVT", 24_000, 0.0,
            "Inspeksi belt tiap 8.000 km. Belt retak/melebar = tarikan berat & boros."),
        MaintenanceItem("Busi NGK CPR7EA-9", 8_000, 0.0,
            "Busi kotor/mati → galau, mogok, bensin boros."),
        MaintenanceItem("Kampas Rem Depan", 8_000, 0.0,
            "Cek tiap servis; ganti ketika sisa bahan < 1.5 mm."),
        MaintenanceItem("Kampas Rem Belakang", 16_000, 0.0,
            "Rata-rata pemakaian santai; bisa lebih awal kalau sering rem mendadak."),
        MaintenanceItem("Oli Gardan/Reduksi (jika ada)", 12_000, 0.0,
            "Vario 125 pakai CVT umumnya tidak punya oli gardan terpisah — skip bila tidak ada."),
        MaintenanceItem("Servis Injeksi/Throttle Body", 24_000, 0.0,
            "Cleaning servis kalau tarikan kasar atau idle tidak stabil.")
    )
}
