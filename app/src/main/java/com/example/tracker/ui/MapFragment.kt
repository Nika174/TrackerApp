package com.example.tracker.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.example.tracker.R
import com.example.tracker.data.TrackDatabase
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import kotlinx.coroutines.*

class MapFragment : Fragment() {

    private var mapView: MapView? = null
    private val db by lazy { TrackDatabase.getInstance(requireContext()) }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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
        Configuration.getInstance().load(requireContext(), prefs)

        map.setBuiltInZoomControls(true)
        map.setMultiTouchControls(true)
        map.controller.setZoom(15.0)
        map.controller.setCenter(GeoPoint(55.751244, 37.618423))
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
}
