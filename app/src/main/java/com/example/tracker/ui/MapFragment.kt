package com.example.tracker.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.example.tracker.R
import com.example.tracker.data.TrackDatabase
import kotlinx.coroutines.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline

class MapFragment : Fragment() {

    private var mapView: MapView? = null
    private val db by lazy { TrackDatabase.getInstance(requireContext()) }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Keyless OSM-compatible providers verified to return real tiles:
     * - OSM France (standard Carto style, z0-19)
     * - OpenTopoMap (topographic, z0-17)
     * OSM's own tile server answers 403 "Access blocked" for app traffic
     * and CARTO now returns "API key required" placeholders.
     */
    private val sources: List<XYTileSource> by lazy {
        listOf(
            XYTileSource(
                "OSMFrance", 0, 19, 256, ".png",
                arrayOf(
                    "https://a.tile.openstreetmap.fr/osmfr/",
                    "https://b.tile.openstreetmap.fr/osmfr/",
                    "https://c.tile.openstreetmap.fr/osmfr/"
                ),
                "© OpenStreetMap contributors, OSM France"
            ),
            XYTileSource(
                "OpenTopoMap", 0, 17, 256, ".png",
                arrayOf(
                    "https://a.tile.opentopomap.org/",
                    "https://b.tile.opentopomap.org/",
                    "https://c.tile.opentopomap.org/"
                ),
                "© OpenTopoMap (CC-BY-SA), © OpenStreetMap contributors"
            )
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_map, container, false)
        val map = view.findViewById<MapView>(R.id.map)
        mapView = map
        try {
            setupMap(map)
        } catch (t: Throwable) {
            // map problems must never crash the app
            t.printStackTrace()
        }
        refreshTrack()
        return view
    }

    private fun setupMap(map: MapView) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val cfg = Configuration.getInstance()
        cfg.load(requireContext(), prefs)
        cfg.userAgentValue = "TrackerApp/1.0 (personal Android app; contact: ${requireContext().packageName})"

        map.setTileSource(currentSource())
        map.setUseDataConnection(true)
        map.setBuiltInZoomControls(true)
        map.setMultiTouchControls(true)
        map.controller.setZoom(15.0)
        map.controller.setCenter(GeoPoint(55.751244, 37.618423))
    }

    private fun prefs() = PreferenceManager.getDefaultSharedPreferences(requireContext())

    private fun currentSource(): XYTileSource {
        val idx = prefs().getInt(KEY_SOURCE, 0).coerceIn(0, sources.size - 1)
        return sources[idx]
    }

    /** Cycles the base layer; wired to the "Карта" button. */
    fun switchLayer() {
        val next = (prefs().getInt(KEY_SOURCE, 0) + 1) % sources.size
        prefs().edit().putInt(KEY_SOURCE, next).apply()
        val map = mapView
        val src = sources[next]
        if (map != null) {
            try {
                map.setTileSource(src)
                map.invalidate()
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
        Toast.makeText(requireContext(), "Слой: ${src.name()}", Toast.LENGTH_SHORT).show()
    }

    private fun refreshTrack() {
        scope.launch {
            val points = try {
                db.trackDao().getAll().takeLast(300)
            } catch (t: Throwable) {
                emptyList()
            }
            if (points.isEmpty()) return@launch
            withContext(Dispatchers.Main) {
                val map = mapView ?: return@withContext
                try {
                    map.overlays.clear()
                    val geoPoints = points.map { GeoPoint(it.latitude, it.longitude) }
                    for (i in 0 until geoPoints.size - 1) {
                        val segment = Polyline(map)
                        segment.addPoint(geoPoints[i])
                        segment.addPoint(geoPoints[i + 1])
                        segment.width = 8f
                        segment.color = if (points[i].mode == 1) 0xFFE53935.toInt() else 0xFF1E88E5.toInt()
                        map.overlays.add(segment)
                    }
                    map.controller.setCenter(geoPoints.last())
                } catch (t: Throwable) {
                    t.printStackTrace()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try { mapView?.onResume() } catch (t: Throwable) {}
    }

    override fun onPause() {
        super.onPause()
        try { mapView?.onPause() } catch (t: Throwable) {}
    }

    override fun onDestroyView() {
        scope.cancel()
        super.onDestroyView()
    }

    companion object {
        private const val KEY_SOURCE = "map_source_index"
    }
}
