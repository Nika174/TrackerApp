package com.example.tracker.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
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
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class MapFragment : Fragment() {

    private var mapView: MapView? = null
    private var myLocationOverlay: MyLocationNewOverlay? = null
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

        setupMyLocation(map)
        centerOnLastKnown(map)
    }

    /** Blue "you are here" dot with follow mode. */
    private fun setupMyLocation(map: MapView) {
        if (!hasLocationPermission()) return
        try {
            val provider = GpsMyLocationProvider(requireContext()).apply {
                addLocationSource(LocationManager.NETWORK_PROVIDER)
                addLocationSource(LocationManager.GPS_PROVIDER)
            }
            val overlay = MyLocationNewOverlay(provider, map)
            overlay.enableMyLocation()
            overlay.enableFollowLocation()
            map.overlays.add(overlay)
            myLocationOverlay = overlay
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    /** Re-checks permission (called after the system dialog) and enables the dot. */
    fun refreshMyLocation() {
        val map = mapView ?: return
        if (myLocationOverlay == null) {
            setupMyLocation(map)
        } else {
            try { myLocationOverlay?.enableMyLocation() } catch (t: Throwable) {}
        }
        centerOnLastKnown(map)
        try { map.invalidate() } catch (t: Throwable) {}
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    /** Jumps to the last position the device knows about, so the map isn't blank. */
    private fun centerOnLastKnown(map: MapView) {
        try {
            val lm = requireContext().getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
            if (!hasLocationPermission()) return
            val last = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
                .maxByOrNull { it.time }
                ?: return
            map.controller.setCenter(GeoPoint(last.latitude, last.longitude))
            map.controller.setZoom(16.0)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
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
        if (myLocationOverlay == null && mapView != null) {
            mapView?.let { setupMyLocation(it) }
        }
        try { myLocationOverlay?.enableMyLocation() } catch (t: Throwable) {}
    }

    override fun onPause() {
        super.onPause()
        try { myLocationOverlay?.disableMyLocation() } catch (t: Throwable) {}
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
