package com.example.tracker.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.lifecycleScope
import com.example.tracker.R
import com.example.tracker.data.SessionManager
import com.example.tracker.data.TrackDatabase
import com.example.tracker.service.LocationService
import com.example.tracker.util.GpxExporter
import com.example.tracker.util.ShareUtil
import com.example.tracker.util.TrackStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private val db by lazy { TrackDatabase.getInstance(this) }

    private lateinit var tvDistance: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvTime: TextView
    private lateinit var tvAvg: TextView
    private lateinit var tvMax: TextView
    private lateinit var tvPoints: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvDistance = findViewById(R.id.tvDistance)
        tvStatus = findViewById(R.id.tvStatus)
        tvTime = findViewById(R.id.tvTime)
        tvAvg = findViewById(R.id.tvAvg)
        tvMax = findViewById(R.id.tvMax)
        tvPoints = findViewById(R.id.tvPoints)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)

        addMapSafely()

        btnStart.setOnClickListener {
            if (ensureLocationPermission()) {
                LocationService.start(this)
                Toast.makeText(this, "Запись начата", Toast.LENGTH_SHORT).show()
                lifecycleScope.launch {
                    delay(600)
                    refreshStats()
                }
            }
        }

        btnStop.setOnClickListener {
            LocationService.stop(this)
            Toast.makeText(this, "Запись остановлена", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                delay(600)
                refreshStats()
                mapFragment()?.refreshTrackNow()
            }
        }

        findViewById<Button>(R.id.btnSessions).setOnClickListener {
            startActivity(Intent(this, SessionsActivity::class.java))
        }

        findViewById<Button>(R.id.btnExport).setOnClickListener { exportAndShare() }

        findViewById<Button>(R.id.btnLayer).setOnClickListener {
            mapFragment()?.switchLayer()
        }

        startStatsLoop()
        showCrashLogIfAny()
        ensureLocationPermission(hint = false)
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { refreshStats() }
        mapFragment()?.refreshTrackNow()
    }

    private fun mapFragment(): MapFragment? =
        supportFragmentManager.findFragmentById(R.id.mapContainer) as? MapFragment

    private fun startStatsLoop() {
        lifecycleScope.launch {
            while (isActive) {
                refreshStats()
                delay(2000)
            }
        }
    }

    private suspend fun refreshStats() {
        val ctx = this
        val current = SessionManager.current(ctx)
        val recording = current > 0
        val viewId = if (recording) current else SessionManager.viewSession(ctx)

        val points = withContext(Dispatchers.IO) {
            try {
                if (viewId > 0) db.trackDao().pointsFor(viewId) else emptyList()
            } catch (t: Throwable) {
                emptyList()
            }
        }
        val st = TrackStats.of(points)

        tvDistance.text = TrackStats.distance(st.distanceM)
        tvTime.text = TrackStats.duration(st.durationMs)
        tvAvg.text = TrackStats.speed(st.avgSpeedKmh)
        tvMax.text = TrackStats.speed(st.maxSpeedKmh)
        tvPoints.text = st.points.toString()

        val modeLabel = if (st.drivingShare > 0.5) "в машине" else "пешком"
        tvStatus.text = when {
            recording -> "● Запись идёт · $modeLabel"
            st.points > 0 -> "Последний трек · $modeLabel"
            else -> "Готов к записи"
        }
        tvStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (recording) R.color.primary else R.color.text_secondary
            )
        )

        btnStart.isEnabled = !recording
        btnStop.isEnabled = recording
    }

    private fun exportAndShare() {
        lifecycleScope.launch {
            try {
                val current = SessionManager.current(this@MainActivity)
                val target = if (current > 0) current else {
                    SessionManager.viewSession(this@MainActivity).takeIf { it > 0 }
                }
                val file: File = withContext(Dispatchers.IO) {
                    GpxExporter.export(this@MainActivity, target)
                }
                val st = withContext(Dispatchers.IO) {
                    val dao = db.trackDao()
                    val pts = if (target != null) dao.pointsFor(target) else dao.getAll()
                    TrackStats.of(pts)
                }
                if (st.points == 0) {
                    Toast.makeText(
                        this@MainActivity,
                        "Пока нет точек — нажмите «Старт» и пройдите немного",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }
                Toast.makeText(
                    this@MainActivity,
                    "GPX: ${TrackStats.distance(st.distanceM)}, ${st.points} точек",
                    Toast.LENGTH_SHORT
                ).show()
                ShareUtil.share(this@MainActivity, file, "application/gpx+xml")
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Ошибка экспорта: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMS) {
            mapFragment()?.refreshMyLocation()
        }
    }

    /** Adds the map fragment programmatically so a map failure can never kill the app. */
    private fun addMapSafely() {
        val container = findViewById<FragmentContainerView>(R.id.mapContainer)
        try {
            supportFragmentManager.beginTransaction()
                .replace(container.id, MapFragment())
                .commitNow()
        } catch (t: Throwable) {
            val msg = t.stackTraceToString()
            container.visibility = android.view.View.GONE
            AlertDialog.Builder(this)
                .setTitle("Карта недоступна")
                .setMessage(msg.take(1500))
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun ensureLocationPermission(hint: Boolean = true): Boolean {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
            needed.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isEmpty()) return true
        ActivityCompat.requestPermissions(this, needed.toTypedArray(), REQ_PERMS)
        if (hint) {
            Toast.makeText(this, "Разрешите геолокацию и нажмите «Старт» ещё раз", Toast.LENGTH_LONG).show()
        }
        return false
    }

    /** Shows the stack trace of the last crash, so it can be copied without adb. */
    private fun showCrashLogIfAny() {
        val file = File(getExternalFilesDir(null) ?: filesDir, "crash.txt")
        if (!file.exists()) return
        val text = try { file.readText() } catch (e: Throwable) { return }
        file.delete()
        AlertDialog.Builder(this)
            .setTitle("Прошлый запуск завершился ошибкой")
            .setMessage(text.take(4000))
            .setPositiveButton("OK", null)
            .setNeutralButton("Скопировать") { _, _ ->
                val cm = getSystemService(android.content.ClipboardManager::class.java)
                cm.setPrimaryClip(android.content.ClipData.newPlainText("crash", text))
                Toast.makeText(this, "Скопировано — вставьте в чат", Toast.LENGTH_LONG).show()
            }
            .show()
    }

    companion object {
        private const val REQ_PERMS = 100
    }
}
