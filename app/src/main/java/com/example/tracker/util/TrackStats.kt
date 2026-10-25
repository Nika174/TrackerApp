package com.example.tracker.util

import com.example.tracker.data.TrackPoint
import java.util.Locale
import kotlin.math.*

object TrackStats {

    data class Stats(
        val distanceM: Double = 0.0,
        val durationMs: Long = 0L,
        val movingMs: Long = 0L,
        val avgSpeedKmh: Double = 0.0,
        val maxSpeedKmh: Double = 0.0,
        val points: Int = 0,
        val drivingShare: Double = 0.0
    )

    fun of(points: List<TrackPoint>): Stats {
        if (points.isEmpty()) return Stats()
        var distance = 0.0
        var moving = 0L
        var maxSpeed = 0.0
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val d = haversine(a.latitude, a.longitude, b.latitude, b.longitude)
            // skip GPS jumps and duplicated points
            if (d in 0.5..400.0) distance += d
            val dt = (b.timestamp - a.timestamp).coerceAtLeast(0L)
            if (b.speedMps > 0.5f) moving += dt
            if (b.speedMps > maxSpeed) maxSpeed = b.speedMps.toDouble()
        }
        val duration = (points.last().timestamp - points.first().timestamp).coerceAtLeast(0L)
        val avg = if (duration > 0) distance / (duration / 1000.0) * 3.6 else 0.0
        val driving = points.count { it.mode == 1 }.toDouble() / points.size
        return Stats(
            distanceM = distance,
            durationMs = duration,
            movingMs = moving,
            avgSpeedKmh = avg,
            maxSpeedKmh = maxSpeed * 3.6,
            points = points.size,
            drivingShare = driving
        )
    }

    fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    /** "820 м" / "4,27 км" */
    fun distance(meters: Double): String = when {
        meters < 950 -> "${meters.roundToInt()} м"
        else -> String.format(Locale("ru", "RU"), "%.2f км", meters / 1000.0)
    }

    /** "01:23:45" or "23:45" */
    fun duration(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%02d:%02d", m, s)
    }

    fun speed(kmh: Double): String = String.format(Locale("ru", "RU"), "%.1f", kmh)
}
