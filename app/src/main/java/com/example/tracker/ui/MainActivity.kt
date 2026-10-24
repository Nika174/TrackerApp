package com.example.tracker.ui

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.tracker.R
import com.example.tracker.service.LocationService
import com.example.tracker.util.GpxExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            LocationService.start(this)
        }
        findViewById<Button>(R.id.btnStop).setOnClickListener {
            LocationService.stop(this)
        }
        findViewById<Button>(R.id.btnExport).setOnClickListener {
            scope.launch {
                try {
                    val file = GpxExporter.export(this@MainActivity)
                    Toast.makeText(
                        this@MainActivity,
                        "GPX сохранён: $file",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(
                        this@MainActivity,
                        "Ошибка экспорта: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }
}