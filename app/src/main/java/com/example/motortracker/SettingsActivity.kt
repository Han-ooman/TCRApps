package com.example.motortracker

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.io.File

/**
 * Pengaturan aplikasi: tema, satuan, notifikasi, backup data.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private var switchDarkMode: SwitchMaterial? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme sebelum setContentView
        prefs = getSharedPreferences("tcrapps_settings", Context.MODE_PRIVATE)
        val isDark = prefs.getBoolean("dark_mode", false)
        AppCompatDelegate.setDefaultNightMode(if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarSettings)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        switchDarkMode = findViewById(R.id.switchDarkMode)
        switchDarkMode?.isChecked = isDark
        switchDarkMode?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("dark_mode", isChecked).apply()
            AppCompatDelegate.setDefaultNightMode(if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
            recreate()
        }

        // Unit preference
        val unitLayout = findViewById<TextInputLayout>(R.id.layoutUnitDistance)
        val currentUnit = prefs.getString("unit_distance", "km") ?: "km"
        unitLayout.editText?.setText(currentUnit)
        unitLayout.setEndIconOnClickListener {
            showUnitPicker()
        }

        // Export data
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnExportData).setOnClickListener {
            exportData()
        }

        // Clear all data
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnClearData).setOnClickListener {
            showClearDataDialog()
        }

        // About
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAbout).setOnClickListener {
            showAboutDialog()
        }
    }

    private fun showUnitPicker() {
        val options = arrayOf("km", "mi")
        val current = prefs.getString("unit_distance", "km") ?: "km"
        val checked = options.indexOf(current)
        AlertDialog.Builder(this)
            .setTitle("Satuan Jarak")
            .setSingleChoiceItems(options, checked) { dialog, which ->
                prefs.edit().putString("unit_distance", options[which]).apply()
                dialog.dismiss()
                Toast.makeText(this, "Satuan: ${options[which]}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun exportData() {
        val data = PrefsStore.exportAllJson(this)
        val fileName = "tcrapps_backup_${System.currentTimeMillis()}.json"
        val file = File(filesDir, fileName)
        file.writeText(data)
        Toast.makeText(this, "Diekspor ke: ${file.absolutePath}", Toast.LENGTH_LONG).show()
    }

    private fun showClearDataDialog() {
        AlertDialog.Builder(this)
            .setTitle("Hapus Semua Data?")
            .setMessage("Ini akan menghapus odometer, servis, bensin, dan preferensi peta. Tindakan ini tidak bisa dibatalkan.")
            .setPositiveButton("Hapus") { _, _ ->
                PrefsStore.clearAll(this)
                Toast.makeText(this, "Semua data dihapus", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("TCRApps v0.1.1")
            .setMessage("Motor Speed Tracker untuk Honda Vario 125 Old\n\n" +
                "Fitur:\n" +
                "• Live tracking GPS + rute di peta\n" +
                "• Analisa servis (oli, busi, V-belt, dll.)\n" +
                "• Log pengisian bensin + km/L\n" +
                "• Eco speed tips (35-45 km/h)\n" +
                "• Peta OpenFreeMap (tanpa API key)\n" +
                "• Backend sync ke Neon/Postgres\n\n" +
                "Open source - Built with Kotlin, MapLibre, OkHttp")
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}