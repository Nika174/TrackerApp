package com.example.tracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.tracker.data.TrackDatabase
import com.example.tracker.data.TrackPoint
import com.example.tracker.ui.MainActivity
import com.google.android.gms.location.*
import kotlinx.coroutines.*

class LocationService : Service() {

    private lateinit var fusedClient: FusedLocationProviderClient
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val db by lazy { TrackDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        try {
            createChannel()
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
            ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), type)

            fusedClient = LocationServices.getFusedLocationProviderClient(this)
            requestUpdates()
        } catch (t: Throwable) {
            t.printStackTrace()
            stopSelf()
        }
    }

    private fun requestUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateIntervalMillis(2000L)
            .setMaxUpdateDelayMillis(7000L)
            .build()
        try {
            fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { saveLocation(it) }
        }
    }

    private fun saveLocation(loc: android.location.Location) {
        val mode = if (loc.speed > 5f) 1 else 0
        val point = TrackPoint(
            latitude = loc.latitude,
            longitude = loc.longitude,
            timestamp = loc.time,
            speedMps = loc.speed,
            mode = mode
        )
        scope.launch {
            try { db.trackDao().insert(point) } catch (t: Throwable) { t.printStackTrace() }
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                CHANNEL_ID,
                "Трекер локаций",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(chan)
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Запись маршрута")
            .setContentText("Отслеживание включено")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try { fusedClient.removeLocationUpdates(locationCallback) } catch (t: Throwable) {}
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val NOTIF_ID = 1001
        private const val CHANNEL_ID = "tracker_channel"

        fun start(context: Context) {
            val i = Intent(context, LocationService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(i)
                } else {
                    context.startService(i)
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LocationService::class.java))
        }
    }
}
