package com.example.motortracker

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText

/**
 * Analisa servis Vario 125 Old.
 * Interval dapat diubah satu per satu, disimpan lokal (SharedPreferences).
 */
class MaintenanceActivity : AppCompatActivity() {

    private lateinit var listContainer: LinearLayout
    private lateinit var tvOdo: TextView
    private var items: MutableList<MaintenanceItem> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maintenance)

        listContainer = findViewById(R.id.listMaint)
        tvOdo = findViewById(R.id.tvMaintOdo)

        items = PrefsStore.loadMaintenance(this)

        findViewById<Button>(R.id.btnEditOdo).setOnClickListener {
            val input = TextInputEditText(this)
            input.hint = "km sekarang"
            input.setText(PrefsStore.currentOdoKm(this).let { if (it == 0.0) "" else it.toString() })
            AlertDialog.Builder(this)
                .setTitle("Set odometer")
                .setView(input)
                .setPositiveButton("Simpan") { _, _ ->
                    val km = input.text.toString().toDoubleOrNull()
                    if (km != null && km >= 0) {
                        PrefsStore.setCurrentOdoKm(this, km)
                        render()
                    }
                }
                .setNegativeButton("Batal", null)
                .show()
        }

        render()
    }

    private fun render() {
        listContainer.removeAllViews()
        val odo = PrefsStore.currentOdoKm(this)
        tvOdo.text = "Odometer: %.0f km".format(odo)
        val inflater = LayoutInflater.from(this)

        items.forEach { item ->
            val row = inflater.inflate(
                R.layout.item_maintenance, listContainer, false
            ) as LinearLayout

            row.findViewById<TextView>(R.id.tvName).text = item.name
            row.findViewById<TextView>(R.id.tvInterval).text =
                "interval %,.0f km".format(item.intervalKm.toDouble())

            val status = item.status(odo)
            val due = item.nextDueKm()
            val left = due - odo
            val tvStatus = row.findViewById<TextView>(R.id.tvStatus)

            when (status) {
                MaintenanceItem.Status.OK -> {
                    tvStatus.text = "✅ Aman (sisa %,.0f km)".format(left)
                    tvStatus.setTextColor(0xFF9ADB97.toInt())
                }
                MaintenanceItem.Status.SOON -> {
                    tvStatus.text = "⚠️ Segera (%,.0f km)".format(left)
                    tvStatus.setTextColor(0xFFF2C94C.toInt())
                }
                MaintenanceItem.Status.OVERDUE -> {
                    tvStatus.text = "❌ Terlewat %,.0f km!".format(-left)
                    tvStatus.setTextColor(0xFFEB5757.toInt())
                }
            }

            val lastIndex = item.lastOdoKm
            val label = if (lastIndex <= 0.0) "belum pernah dicatat" else "ganti @ %,.0f km".format(lastIndex)
            row.findViewById<TextView>(R.id.tvLast).text =
                "Terakhir: $label • Due @ %,.0f km".format(due)

            // Progress: pakai fraksi nearest-window (0..interval)
            val pb = row.findViewById<ProgressBar>(R.id.pbItem)
            val fraction = ((odo - item.lastOdoKm) / item.intervalKm).coerceIn(0.0, 1.0)
            pb.progress = (fraction * 100).toInt()

            row.findViewById<TextView>(R.id.tvNote).text = item.note

            val btn = row.findViewById<Button>(R.id.btnMarkChanged)
            btn.setOnClickListener {
                val input = TextInputEditText(this)
                input.hint = "km saat ganti hari ini"
                input.setText(odo.let { if (it == 0.0) "" else "%.0f".format(it) })
                AlertDialog.Builder(this)
                    .setTitle("Tandai selesai: ${item.name}")
                    .setView(input)
                    .setPositiveButton("Simpan") { _, _ ->
                        val km = input.text.toString().toDoubleOrNull()
                        if (km != null) {
                            val idx = items.indexOfFirst { it.name == item.name }
                            if (idx >= 0)
                                items[idx] = items[idx].copy(lastOdoKm = km)
                            PrefsStore.saveMaintenance(this, items)
                            render()
                            Toast.makeText(this, "Tercatat ✅", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Batal", null)
                    .show()
            }

            listContainer.addView(row)
        }
    }
}
