package com.example.motortracker

import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Semua peta memakai OpenFreeMap (https://openfreemap.org):
 * gratis, tanpa API key. Pilihan terbatas pada gaya yang tersedia.
 * Gaya dicek dulu (HTTP 200 + ada "layers") supaya tidak ada style 404.
 */
object MapSources {

    // Style resmi OpenFreeMap yang stabil.
    val styles = listOf("dark", "liberty", "positron", "bright")

    private val STYLE_URLS = mapOf(
        "dark" to "https://tiles.openfreemap.org/styles/dark",
        "liberty" to "https://tiles.openfreemap.org/styles/liberty",
        "positron" to "https://tiles.openfreemap.org/styles/positron",
        "bright" to "https://tiles.openfreemap.org/styles/bright"
    )

    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    fun styleUrl(styleId: String): String = STYLE_URLS[styleId] ?: STYLE_URLS["dark"]!!

    fun storedStyle(ctx: Context): String {
        val id = PrefsStore.mapProviderId(ctx)
        return if (id in styles) id else "dark"
    }

    private fun probe(url: String, contains: String): Boolean = runCatching {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "TCRApps-alpha (Android; maplibre)")
            .build()
        probeClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use false
            (resp.body?.string() ?: "").contains(contains)
        }
    }.getOrDefault(false)

    /**
     * Cari gaya yang benar-benar bisa dipakai di jaringan hp:
     * coba gaya tersimpan dulu, lalu dark/liberty/positron/bright.
     * Return styleId yang OK, kalau tidak ada cek koneksi.
     */
    fun pickReachableStyle(ctx: Context): String? {
        val stored = storedStyle(ctx)
        val order = mutableListOf(stored)
        styles.forEach { if (it != stored) order.add(it) }
        for (id in order) {
            if (probe(styleUrl(id), "\"layers\"")) return id
        }
        return null
    }

    /** Dialog pilih gaya peta OpenFreeMap (dark/liberty/positron/bright). */
    fun showChooser(ctx: Context, onDone: (String) -> Unit) {
        val names = styles.map { "OpenFreeMap - $it" }.toTypedArray()
        val stored = storedStyle(ctx)
        val checked = styles.indexOf(stored)
        AlertDialog.Builder(ctx)
            .setTitle("Pilih gaya peta (OpenFreeMap)")
            .setSingleChoiceItems(names, checked) { dialog, which ->
                val selected = styles[which]
                dialog.dismiss()
                PrefsStore.setMapConfig(ctx, selected, PrefsStore.mapApiKey(ctx))
                onDone(selected)
            }
            .setNegativeButton("Tutup", null)
            .show()
    }
}
