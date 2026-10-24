package com.example.tracker.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentContainerView
import com.example.tracker.R
import com.example.tracker.service.LocationService
import com.example.tracker.util.GpxExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        addMapSafely()

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            if (ensureLocationPermission()) {
                LocationService.start(this)
                Toast.makeText(this, "Запись начата", Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<Button>(R.id.btnStop).setOnClickListener {
            LocationService.stop(this)
            Toast.makeText(this, "Запись остановлена", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnExport).setOnClickListener {
            scope.launch {
                try {
                    val file = GpxExporter.export(this@MainActivity)
                    Toast.makeText(this@MainActivity, "GPX: $file", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Ошибка экспорта: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        findViewById<Button>(R.id.btnLayer).setOnClickListener {
            (supportFragmentManager.findFragmentById(R.id.mapContainer) as? MapFragment)?.switchLayer()
        }

        showCrashLogIfAny()

        // Ask right away so the "me" dot can appear on the map.
        ensureLocationPermission(hint = false)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMS) {
            (supportFragmentManager.findFragmentById(R.id.mapContainer) as? MapFragment)?.refreshMyLocation()
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
            Toast.makeText(this, "Разрешите доступ к геолокации и нажмите «Старт» ещё раз", Toast.LENGTH_LONG).show()
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

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    companion object {
        private const val REQ_PERMS = 100
    }
}
