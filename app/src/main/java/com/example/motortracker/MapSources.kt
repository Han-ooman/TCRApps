package com.example.motortracker

import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView

/**
 * Pilihan penyedia tile peta. Beberapa penyedia butuh API key (bebas daftar),
 * lainnya tidak. Config tersimpan via PrefsStore.
 */
object MapSources {

    private val CARTO_TILE_URLS = arrayOf(
        "https://a.basemaps.cartocdn.com/",
        "https://b.basemaps.cartocdn.com/",
        "https://c.basemaps.cartocdn.com/",
        "https://d.basemaps.cartocdn.com/"
    )

    data class Provider(
        val id: String,
        val label: String,
        val needsKey: Boolean,
        val apply: (MapView, Context) -> Unit
    )

    val providers = listOf(
        Provider("carto_dark", "CARTO Dark (tanpa API key)", false) { mv, _ ->
            mv.setTileSource(
                XYTileSource(
                    "CartoDark", 0, 20, 256, ".png",
                    CARTO_TILE_URLS.map { "${it}dark_all/" }.toTypedArray()
                )
            )
        },
        Provider("carto_light", "CARTO Light (tanpa API key)", false) { mv, _ ->
            mv.setTileSource(
                XYTileSource(
                    "CartoLight", 0, 20, 256, ".png",
                    CARTO_TILE_URLS.map { "${it}light_all/" }.toTypedArray()
                )
            )
        },
        Provider("osm_mapnik", "OpenStreetMap Mapnik (butuh key via provider)", false) { mv, _ ->
            mv.setTileSource(TileSourceFactory.MAPNIK)
        },
        Provider("opentopo", "OpenTopoMap (tanpa API key)", false) { mv, _ ->
            mv.setTileSource(
                XYTileSource(
                    "OpenTopoMap", 0, 17, 256, ".png",
                    arrayOf(
                        "https://a.tile.opentopomap.org/",
                        "https://b.tile.opentopomap.org/",
                        "https://c.tile.opentopomap.org/"
                    )
                )
            )
        },
        Provider("maptiler", "MapTiler (daftar API key gratis)", true) { mv, ctx ->
            val key = PrefsStore.mapApiKey(ctx)
            if (key.isBlank()) {
                mv.setTileSource(
                    XYTileSource(
                        "CartoDark", 0, 20, 256, ".png",
                        CARTO_TILE_URLS.map { "${it}dark_all/" }.toTypedArray()
                    )
                )
            } else {
                mv.setTileSource(
                    ApiKeyTileSource(
                        "MapTiler/$key",
                        0, 19, 256,
                        "https://api.maptiler.com/maps/streets/{z}/{x}/{y}.png?key=$key"
                    )
                )
            }
        }
    )

    /** Tile source dengan URL template ({z}/{x}/{y} + query key). */
    class ApiKeyTileSource(
        name: String,
        minZoom: Int, maxZoom: Int, tileSize: Int,
        private val urlTemplate: String
    ) : OnlineTileSourceBase(
        name, minZoom, maxZoom, tileSize, ".png",
        arrayOf(urlTemplate)
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            return urlTemplate
                .replace("{z}", MapTileIndex.getZoom(pMapTileIndex).toString())
                .replace("{x}", MapTileIndex.getX(pMapTileIndex).toString())
                .replace("{y}", MapTileIndex.getY(pMapTileIndex).toString())
        }
    }

    fun currentProvider(ctx: Context): Provider {
        val id = PrefsStore.mapProviderId(ctx)
        return providers.firstOrNull { it.id == id } ?: providers.first()
    }

    fun applyTo(mapView: MapView, ctx: Context) {
        val p = currentProvider(ctx)
        if (p.needsKey && PrefsStore.mapApiKey(ctx).isBlank()) {
            Toast.makeText(
                ctx,
                "Provider butuh API key — pakai fallback tanpa key dulu",
                Toast.LENGTH_SHORT
            ).show()
        }
        p.apply(mapView, ctx)
    }

    /** Dialog pilih provider (+ kotak key bila dibutuhkan). */
    fun showChooser(ctx: Context, onApplied: () -> Unit) {
        val names = providers.map { it.label }.toTypedArray()
        val currentId = PrefsStore.mapProviderId(ctx)
        val checked = providers.indexOfFirst { it.id == currentId }
        AlertDialog.Builder(ctx)
            .setTitle("Pilih tile provider peta")
            .setSingleChoiceItems(names, checked) { dialog, which ->
                val selected = providers[which]
                dialog.dismiss()
                if (selected.needsKey) {
                    val input = android.widget.EditText(ctx)
                    input.hint = "tempel API key di sini"
                    input.setText(PrefsStore.mapApiKey(ctx))
                    AlertDialog.Builder(ctx)
                        .setTitle("API Key MapTiler")
                        .setMessage("Daftar gratis di maptiler.com lalu tempel key-nya. Kosong = pakai fallback tanpa key.")
                        .setView(input)
                        .setPositiveButton("OK") { _, _ ->
                            PrefsStore.setMapConfig(ctx, selected.id, input.text.toString().trim())
                        }
                        .setNegativeButton("Batal", null)
                        .show()
                } else {
                    PrefsStore.setMapConfig(ctx, selected.id, PrefsStore.mapApiKey(ctx))
                }
                onApplied()
            }
            .setNegativeButton("Tutup", null)
            .show()
    }
}
