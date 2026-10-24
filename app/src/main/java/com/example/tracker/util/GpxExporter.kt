package com.example.tracker.util

import android.content.Context
import com.example.tracker.data.TrackDatabase
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.Locale
import java.util.TimeZone

object GpxExporter {
    suspend fun export(context: Context): File {
        val dao = TrackDatabase.getInstance(context).trackDao()
        val points = dao.getAll()
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.append("\n<gpx version=\"1.1\" creator=\"AndroidTracker\">")
        sb.append("\n<trk><name>MyTrack</name><trkseg>")
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        for (p in points) {
            sb.append("\n<trkpt lat=\"${p.latitude}\" lon=\"${p.longitude}\">")
            sb.append("\n<time>${fmt.format(Date(p.timestamp))}</time>")
            sb.append("\n<speed>${p.speedMps}</speed>")
            sb.append("\n<type>${if (p.mode == 1) "driving" else "walking"}</type>")
            sb.append("\n</trkpt>")
        }
        sb.append("\n</trkseg></trk></gpx>")
        val dir = context.getExternalFilesDir(null)!!
        val file = File(dir, "track_${System.currentTimeMillis()}.gpx")
        file.writeText(sb.toString())
        return file
    }
}