package com.example.tracker

import android.app.Application
import org.osmdroid.config.Configuration
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class TrackerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        installCrashHandler()
        initOsmdroid()
    }

    private fun initOsmdroid() {
        try {
            val cfg = Configuration.getInstance()
            cfg.userAgentValue = packageName
            val base = File(getExternalFilesDir(null), "osmdroid").apply { mkdirs() }
            cfg.osmdroidBasePath = base
            cfg.osmdroidTileCache = File(base, "tiles").apply { mkdirs() }
        } catch (t: Throwable) {
            // never let map config kill startup
        }
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val dir = getExternalFilesDir(null) ?: filesDir
                File(dir, "crash.txt").writeText(sw.toString())
            } catch (ignored: Throwable) {
            }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
