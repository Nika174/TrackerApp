package com.example.tracker.util

import android.content.Context
import com.example.tracker.data.TrackDatabase
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object GpxExporter {

    /** Exports one session (or everything when sessionId is null) and returns the file. */
    suspend fun export(context: Context, sessionId: Long? = null): File {
        val dao = TrackDatabase.getInstance(context).trackDao()
        val points = if (sessionId != null) dao.pointsFor(sessionId) else dao.getAll()
        val name = if (sessionId != null) {
            dao.session(sessionId)?.name ?: "Track"
        } else {
            "All tracks"
        }

        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        sb.append("\n<gpx version=\"1.1\" creator=\"Tracker\" xmlns=\"http://www.topografix.com/GPX/1/1\">")
        sb.append("\n<metadata><name>${esc(name)}</name></metadata>")
        sb.append("\n<trk><name>${esc(name)}</name><trkseg>")
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        for (p in points) {
            sb.append("\n<trkpt lat=\"${p.latitude}\" lon=\"${p.longitude}\">")
            sb.append("<time>${fmt.format(Date(p.timestamp))}</time>")
            sb.append("<speed>${p.speedMps}</speed>")
            sb.append("</trkpt>")
        }
        sb.append("\n</trkseg></trk></gpx>")

        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val safeName = name.replace(Regex("[^A-Za-z0-9а-яА-Я _-]"), "_").take(40)
        val file = File(dir, "${safeName}_${System.currentTimeMillis()}.gpx")
        file.writeText(sb.toString())
        return file
    }

    /** CSV with every point — handy for Excel. */
    suspend fun exportCsv(context: Context, sessionId: Long? = null): File {
        val dao = TrackDatabase.getInstance(context).trackDao()
        val points = if (sessionId != null) dao.pointsFor(sessionId) else dao.getAll()
        val sb = StringBuilder("time,lat,lon,speed_mps,mode\n")
        for (p in points) {
            sb.append("${p.timestamp},${p.latitude},${p.longitude},${p.speedMps},")
            sb.append(if (p.mode == 1) "driving" else "walking")
            sb.append("\n")
        }
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(dir, "track_${System.currentTimeMillis()}.csv")
        file.writeText(sb.toString())
        return file
    }

    private fun esc(s: String) = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
