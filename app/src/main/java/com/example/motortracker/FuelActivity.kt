package com.example.motortracker

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Analisa bensin: log pengisian, hitung km/L rata-rata, biaya per km,
 * dan indikasi kesehatan konsumsi untuk Vario 125 Old.
 */
class FuelActivity : AppCompatActivity() {

    private lateinit var listContainer: LinearLayout
    private lateinit var tvSummary: TextView
    private lateinit var etOdo: EditText
    private lateinit var etLiters: EditText
    private lateinit var etPrice: EditText

    private val dateFormat = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fuel)

        // Setup toolbar back button
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbarFuel)
        toolbar.setNavigationOnClickListener { finish() }

        listContainer = findViewById(R.id.listFuel)
        tvSummary = findViewById(R.id.tvFuelSummary)
        etOdo = findViewById(R.id.etOdo)
        etLiters = findViewById(R.id.etLiters)
        etPrice = findViewById(R.id.etPrice)

        findViewById<Button>(R.id.btnSaveFuel).setOnClickListener { addRecord() }

        render()
    }

    private fun addRecord() {
        val odo = etOdo.text.toString().replace(",", ".").toDoubleOrNull()
        val liters = etLiters.text.toString().replace(",", ".").toDoubleOrNull()
        val price = etPrice.text.toString().replace(",", ".").toDoubleOrNull()

        if (odo == null || liters == null || price == null || liters <= 0 || odo < 0) {
            android.widget.Toast.makeText(this, "Isi semua kolom dengan benar", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        val records = PrefsStore.loadFuel(this).toMutableList()
        records.add(FuelRecord(odo, liters, price, System.currentTimeMillis()))
        records.sortBy { it.odoKm }
        PrefsStore.saveFuel(this, records)

        // Prefill odometer berikutnya
        etOdo.setText("")
        etLiters.setText("")
        etPrice.setText("")
        render()
    }

    private fun render() {
        listContainer.removeAllViews()
        val records = PrefsStore.loadFuel(this)
        val odoNow = PrefsStore.currentOdoKm(this)

        if (records.isEmpty()) return

        // Konsumsi per leg (dibanding isi sebelumnya)
        val legs = mutableListOf<Triple<FuelRecord, FuelRecord, Double>>() // (prev, cur, kmPerL)
        records.zipWithNext().forEach { (prev, cur) ->
            val km = cur.odoKm - prev.odoKm
            if (km > 5) legs.add(Triple(prev, cur, km / cur.liters))
        }

        // Summary
        val avgKmPerL = if (legs.isNotEmpty()) legs.map { it.third }.average() else 0.0
        val totalSpend = records.sumOf { it.totalCost }
        val totalLiters = records.sumOf { it.liters }
        val costPerKm = if (avgKmPerL > 0) (records.last().pricePerLiter / avgKmPerL) else 0.0

        val sb = StringBuilder()
        sb.append("📊 RATA-RATA: ", "").append("%.1f km/L".format(avgKmPerL))
            .append("\n💰 Biaya berkendara: Rp%,.0f/km".format(costPerKm))
            .append("\n⛽ Total: %,.1f L • Rp%,.0f".format(totalLiters, totalSpend))

        var verdict = ""
        if (avgKmPerL > 0) {
            when {
                avgKmPerL >= 45 -> verdict = "\n✅ Konsumsi SEHAT (Vario 125 ideal 45–50 km/L)"
                avgKmPerL >= 38 -> verdict = "\n⚠️ Standar tapi bisa lebih hemat — cek ban & posisi roller"
                else -> verdict = "\n❌ BOROS — cek filter udara, tekanan ban, dan cara berkendara"
            }
        }
        sb.append(verdict)
        tvSummary.text = sb.toString()

        // Daftar riwayat (terbaru di atas)
        records.asReversed().forEach { rec ->
            val row = TextView(this)
            row.setPadding(0, 8, 0, 8)
            row.textSize = 12f
            row.setTextColor(0xFFFFFFFF.toInt())

            val idx = records.indexOf(rec)
            val leg = legs.firstOrNull { it.first == rec && records.indexOf(it.second) == idx + 1 }
                ?: legs.firstOrNull { it.second == rec && records.indexOf(it.first) == idx - 1 }

            val consumptionInfo = leg?.let { (prev, cur, kpl) ->
                val km = cur.odoKm - prev.odoKm
                val who = if (cur == rec) "(leg ini)" else "(leg sebelumnya)"
                " • %.1f km/L $who — %.0f km".format(kpl, km)
            } ?: ""

            row.text = "%s • %.0f km • %.2f L • Rp%,.0f%s".format(
                dateFormat.format(Date(rec.dateMs)), rec.odoKm, rec.liters, rec.totalCost, consumptionInfo
            )
            listContainer.addView(row)
        }
    }
}
