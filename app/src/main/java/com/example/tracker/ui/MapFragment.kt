package com.example.tracker.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.tracker.R
import com.example.tracker.data.TrackDatabase
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import kotlinx.coroutines.*

class MapFragment : Fragment() {

    private lateinit var mapView: MapView
    private val db by lazy { TrackDatabase.getInstance(requireContext()) }
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_map, container, false)
        mapView = view.findViewById(R.id.map)
        setupMap()
        refreshTrack()
        return view
    }

    private fun setupMap() {
        // Initialize osmdroid configuration
        Configuration.getInstance().load(requireContext(), Configuration.DEFAULT_PREFS_NAME)

        mapView.builtInZoomControls = true
        mapView.multiTouchControls = true
        mapView.controller.setZoom(16.0)
    }

    private fun refreshTrack() {
        scope.launch {
            val points = db.trackDao().getAll().takeLast(200)
            if (points.isEmpty()) return@launch

            withContext(Dispatchers.Main) {
                mapView.overlays.clear()
                
                val geoPoints = mutableListOf<GeoPoint>()
                points.forEach {
                    geoPoints.add(GeoPoint(it.latitude, it.longitude))
                }

                if (geoPoints.size >= 2) {
                    for (i in 0 until geoPoints.size - 1) {
                        val segment = Polyline(mapView)
                        segment.points = arrayListOf(geoPoints[i], geoPoints[i + 1])
                        segment.width = 8f
                        segment.color = if (points[i].mode == 1) android.graphics.Color.RED else android.graphics.Color.BLUE
                        mapView.overlays.add(segment)
                    }
                }

                // Move camera to last point
                val last = points.last()
                mapView.controller.animateTo(GeoPoint(last.latitude, last.longitude))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
}