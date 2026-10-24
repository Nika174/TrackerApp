package com.example.tracker.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.example.tracker.R
import com.example.tracker.data.TrackDatabase
import kotlinx.coroutines.*

class MapFragment : Fragment(), OnMapReadyCallback {

    private lateinit var gMap: GoogleMap
    private val db by lazy { TrackDatabase.getInstance(requireContext()) }
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_map, container, false)
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        return view
    }

    override fun onMapReady(map: GoogleMap) {
        gMap = map
        gMap.uiSettings.isZoomControlsEnabled = true
        refreshTrack()
    }

    private fun refreshTrack() {
        scope.launch {
            val points = db.trackDao().getAll().takeLast(200)
            if (points.isEmpty()) return@launch
            val options = PolylineOptions().width(8f)
            points.forEach {
                options.add(LatLng(it.latitude, it.longitude))
                options.color(if (it.mode == 1) android.graphics.Color.RED else android.graphics.Color.BLUE)
            }
            withContext(Dispatchers.Main) {
                gMap.clear()
                gMap.addPolyline(options)
                val last = points.last()
                gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                    LatLng(last.latitude, last.longitude), 16f
                ))
            }
        }
    }
}