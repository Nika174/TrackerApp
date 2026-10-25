package com.example.tracker.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tracker.R
import com.example.tracker.data.SessionManager
import com.example.tracker.data.TrackDatabase
import com.example.tracker.data.TrackSession
import com.example.tracker.util.GpxExporter
import com.example.tracker.util.ShareUtil
import com.example.tracker.util.TrackStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SessionsActivity : AppCompatActivity() {

    private val db by lazy { TrackDatabase.getInstance(this) }
    private lateinit var adapter: SessionAdapter
    private lateinit var tvEmpty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sessions)

        tvEmpty = findViewById(R.id.tvEmpty)
        val rv = findViewById<RecyclerView>(R.id.rvSessions)
        rv.layoutManager = LinearLayoutManager(this)

        adapter = SessionAdapter(
            onClick = { session ->
                SessionManager.setViewSession(this, session.id)
                finish()
            },
            onShare = { session -> shareGpx(session) },
            onLongClick = { session -> confirmDelete(session) }
        )
        rv.adapter = adapter
        load()
    }

    private fun load() {
        lifecycleScope.launch {
            val rows = withContext(Dispatchers.IO) {
                try {
                    val dao = db.trackDao()
                    dao.sessions().map { s ->
                        SessionAdapter.Row(s, TrackStats.of(dao.pointsFor(s.id)))
                    }
                } catch (t: Throwable) {
                    emptyList()
                }
            }
            adapter.submit(rows)
            tvEmpty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun shareGpx(session: TrackSession) {
        lifecycleScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    GpxExporter.export(this@SessionsActivity, session.id)
                }
                ShareUtil.share(this@SessionsActivity, file, "application/gpx+xml")
            } catch (e: Exception) {
                Toast.makeText(this@SessionsActivity, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun confirmDelete(session: TrackSession) {
        AlertDialog.Builder(this)
            .setTitle("Удалить трек?")
            .setMessage(session.name)
            .setPositiveButton("Удалить") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        try {
                            val dao = db.trackDao()
                            dao.deletePoints(session.id)
                            dao.deleteSession(session.id)
                        } catch (t: Throwable) {
                            t.printStackTrace()
                        }
                    }
                    if (SessionManager.viewSession(this@SessionsActivity) == session.id) {
                        SessionManager.setViewSession(this@SessionsActivity, 0)
                    }
                    load()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
}
