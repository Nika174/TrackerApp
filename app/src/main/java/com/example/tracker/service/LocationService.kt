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
import com.example.tracker.data.SessionManager
import com.example.tracker.data.TrackDatabase
import com.example.tracker.data.TrackPoint
import com.example.tracker.data.TrackSession
import com.example.tracker.ui.MainActivity
import com.example.tracker.util.TrackStats
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class LocationService : Service() {

    private lateinit var fusedClient: FusedLocationProviderClient
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val db by lazy { TrackDatabase.getInstance(this) }

    @Volatile private var sessionId: Long = 0L
    @Volatile private var lastPointTimestamp: Long = 0L

    override fun onCreate() {
        super.onCreate()
        try {
            createChannel()
            val notif = buildNotification("Запись маршрута", "Отслеживание включено")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIF_ID, notif)
            }
            fusedClient = LocationServices.getFusedLocationProviderClient(this)
            scope.launch { ensureSession() }
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

    private suspend fun ensureSession(): Long {
        if (sessionId > 0) return sessionId
        val existing = SessionManager.current(this)
        if (existing > 0) {
            sessionId = existing
            return existing
        }
        val name = SimpleDateFormat("d MMM, HH:mm", Locale("ru", "RU")).format(Date())
        val id = db.trackDao().insertSession(
            TrackSession(name = name, startedAt = System.currentTimeMillis())
        )
        SessionManager.setCurrent(this, id)
        sessionId = id
        return id
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
            try {
                val sid = ensureSession()
                db.trackDao().insert(point.copy(sessionId = sid))
                lastPointTimestamp = point.timestamp
                updateNotification(sid)
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

    private fun updateNotification(sid: Long) {
        try {
            val dao = db.trackDao()
            val points = runBlocking { dao.pointsFor(sid) }
            val st = TrackStats.of(points)
            val text = "${TrackStats.distance(st.distanceM)} • ${TrackStats.duration(st.durationMs)}"
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIF_ID, buildNotification("Запись идёт", text))
        } catch (t: Throwable) {
            t.printStackTrace()
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

    private fun buildNotification(title: String, text: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try { fusedClient.removeLocationUpdates(locationCallback) } catch (t: Throwable) {}
        closeCurrentSession()
        scope.cancel()
        super.onDestroy()
    }

    /** Marks the running session as finished and forgets it. */
    private fun closeCurrentSession() {
        val sid = SessionManager.current(this)
        if (sid <= 0) return
        runBlocking(Dispatchers.IO) {
            try {
                val dao = db.trackDao()
                val end = dao.pointsFor(sid).lastOrNull()?.timestamp ?: System.currentTimeMillis()
                dao.closeSession(sid, end)
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
        SessionManager.clearCurrent(this)
        SessionManager.setViewSession(this, sid)
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
